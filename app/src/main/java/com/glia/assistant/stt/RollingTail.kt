package com.glia.assistant.stt

/**
 * Buffer circular puro que mantém os últimos [keepOnRotate] bytes de um fluxo.
 * Usado para sobrepor as janelas de áudio sem perder fala na borda.
 */
class RollingTail(
    private val capacity: Int,
    private val keepOnRotate: Int = capacity / 2
) {
    private val buffer = ByteArray(capacity)

    var length: Int = 0
        private set

    fun append(bytes: ByteArray, size: Int) {
        var offset = 0
        while (offset < size) {
            val room = capacity - length
            val take = minOf(room, size - offset)
            System.arraycopy(bytes, offset, buffer, length, take)
            length += take
            offset += take
            if (length == capacity) {
                System.arraycopy(buffer, capacity - keepOnRotate, buffer, 0, keepOnRotate)
                length = keepOnRotate
            }
        }
    }

    fun snapshot(): ByteArray = buffer.copyOf(length)
}
