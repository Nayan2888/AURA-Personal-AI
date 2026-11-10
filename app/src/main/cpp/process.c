#define _GNU_SOURCE
#include "process.h"
#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <sys/wait.h>
#include <unistd.h>

pid_t hermes_spawn(const char *shell, const char *cwd, const char *command,
                   const char *out_path, const char *err_path) {
    int out = open(out_path, O_CREAT | O_TRUNC | O_WRONLY | O_CLOEXEC, 0600);
    if (out < 0) return -errno;
    int err = open(err_path, O_CREAT | O_TRUNC | O_WRONLY | O_CLOEXEC, 0600);
    if (err < 0) { int error = errno; close(out); return -error; }
    int input = open("/dev/null", O_RDONLY | O_CLOEXEC);
    if (input < 0) { int error = errno; close(out); close(err); return -error; }
    // All strings and descriptors exist before fork. No JNI, allocation or locks in child.
    char *const args[] = {(char *)shell, "-c", (char *)command, NULL};
    pid_t pid = fork();
    int error = errno;
    if (pid == 0) {
        if (setpgid(0, 0) != 0 || chdir(cwd) != 0 || dup2(input, 0) < 0 ||
            dup2(out, 1) < 0 || dup2(err, 2) < 0) _exit(126);
        close(input); close(out); close(err);
        execv(shell, args);
        static const char message[] = "Hermes: shell exec failed\n";
        write(2, message, sizeof(message) - 1);
        _exit(127);
    }
    close(input); close(out); close(err);
    if (pid < 0) return -error;
    // Child also calls setpgid before exec, covering the parent/child scheduling race.
    if (setpgid(pid, pid) < 0 && errno != EACCES && errno != ESRCH) {
        int group_error = errno;
        kill(pid, SIGKILL);
        while (waitpid(pid, NULL, 0) < 0 && errno == EINTR) {}
        return -group_error;
    }
    return pid;
}

int hermes_poll(pid_t pid) {
    siginfo_t info = {0};
    int observed = waitid(P_PID, (id_t)pid, &info, WEXITED | WNOHANG | WNOWAIT);
    if ((observed < 0 && errno == EINTR) || (observed == 0 && info.si_pid == 0)) return -1000;
    if (observed < 0) return -errno;
    // Keep the leader as a zombie while terminating descendants, preventing PID
    // reuse between observing completion and process-group cleanup.
    hermes_kill_group(pid, SIGKILL);
    int status = 0;
    pid_t result;
    do { result = waitpid(pid, &status, 0); } while (result < 0 && errno == EINTR);
    if (result < 0) return -errno;
    if (WIFEXITED(status)) return WEXITSTATUS(status);
    if (WIFSIGNALED(status)) return 128 + WTERMSIG(status);
    return -1000;
}

int hermes_kill_group(pid_t pid, int signal_number) {
    if (pid <= 1 || pid == getpgrp()) return -EINVAL;
    if (kill(-pid, signal_number) == 0 || errno == ESRCH) return 0;
    return -errno;
}

int hermes_own_group(void) {
    if (setpgid(0, 0) == 0 || getpgrp() == getpid()) return 0;
    return -errno;
}
