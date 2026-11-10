#pragma once
#include <sys/types.h>
pid_t hermes_spawn(const char *shell, const char *cwd, const char *command,
                   const char *out_path, const char *err_path);
int hermes_poll(pid_t pid);
int hermes_kill_group(pid_t pid, int signal_number);
int hermes_own_group(void);
