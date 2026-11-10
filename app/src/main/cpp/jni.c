#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include "process.h"

JNIEXPORT jint JNICALL Java_dev_nayan_hermes_NativeProcess_spawn(
    JNIEnv *env, jobject instance, jbyteArray shell, jbyteArray cwd, jbyteArray command,
    jbyteArray stdout_path, jbyteArray stderr_path) {
    (void) instance;
    jbyteArray inputs[] = {shell, cwd, command, stdout_path, stderr_path};
    char *values[5] = {0};
    for (int i = 0; i < 5; ++i) {
        jsize size = (*env)->GetArrayLength(env, inputs[i]);
        values[i] = malloc((size_t)size + 1);
        if (values[i] == NULL) {
            for (int j = 0; j < i; ++j) free(values[j]);
            return -12;
        }
        (*env)->GetByteArrayRegion(env, inputs[i], 0, size, (jbyte *)values[i]);
        values[i][size] = '\0';
        if ((*env)->ExceptionCheck(env) || memchr(values[i], '\0', (size_t)size)) {
            for (int j = 0; j <= i; ++j) free(values[j]);
            return -22;
        }
    }
    int result = hermes_spawn(values[0], values[1], values[2], values[3], values[4]);
    for (int i = 0; i < 5; ++i) free(values[i]);
    return result;
}
JNIEXPORT jint JNICALL Java_dev_nayan_hermes_NativeProcess_poll(JNIEnv *env, jobject self, jint pid) {
    (void)env; (void)self; return hermes_poll(pid);
}
JNIEXPORT jint JNICALL Java_dev_nayan_hermes_NativeProcess_killGroup(JNIEnv *env, jobject self, jint pid, jint signal) {
    (void)env; (void)self; return hermes_kill_group(pid, signal);
}
JNIEXPORT jint JNICALL Java_dev_nayan_hermes_NativeProcess_ownGroup(JNIEnv *env, jobject self) {
    (void)env; (void)self; return hermes_own_group();
}
