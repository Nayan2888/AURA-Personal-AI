package dev.nayan.hermes

object NativeProcess {
    init { System.loadLibrary("hermes_process") }
    external fun spawn(shell: ByteArray, cwd: ByteArray, command: ByteArray, out: ByteArray, err: ByteArray): Int
    external fun poll(pid: Int): Int
    external fun killGroup(pid: Int, signal: Int): Int
    external fun ownGroup(): Int
}
