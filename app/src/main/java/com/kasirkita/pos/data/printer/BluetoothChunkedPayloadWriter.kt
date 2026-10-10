package com.kasirkita.pos.data.printer

import kotlinx.coroutines.delay
import java.io.OutputStream

internal data class BluetoothWriteStats(
    val payloadBytes: Int,
    val chunkCount: Int,
    val flushed: Boolean,
    val drained: Boolean,
)

internal fun interface BluetoothPayloadWriter {
    suspend fun write(output: OutputStream, payload: ByteArray): BluetoothWriteStats
}

/** Writes one logical print job in bounded SPP chunks and drains before return. */
internal class ChunkedBluetoothPayloadWriter(
    private val chunkSize: Int = DEFAULT_CHUNK_SIZE,
    private val interChunkDelayMs: Long = DEFAULT_INTER_CHUNK_DELAY_MS,
    private val drainDelayMs: Long = DEFAULT_DRAIN_DELAY_MS,
    private val wait: suspend (Long) -> Unit = { delay(it) },
) : BluetoothPayloadWriter {
    init {
        require(chunkSize >= 5)
        require(interChunkDelayMs >= 0)
        require(drainDelayMs >= 0)
    }

    override suspend fun write(output: OutputStream, payload: ByteArray): BluetoothWriteStats {
        var chunks = 0
        val safePayloadChunks = safeChunks(payload, chunkSize)
        safePayloadChunks.forEachIndexed { index, chunk ->
            output.write(chunk)
            chunks++
            if (index < safePayloadChunks.lastIndex && interChunkDelayMs > 0) {
                wait(interChunkDelayMs)
            }
        }
        output.flush()
        if (drainDelayMs > 0) wait(drainDelayMs)
        return BluetoothWriteStats(
            payloadBytes = payload.size,
            chunkCount = chunks,
            flushed = true,
            drained = true,
        )
    }

    internal companion object {
        const val DEFAULT_CHUNK_SIZE = 256
        const val DEFAULT_INTER_CHUNK_DELAY_MS = 8L
        const val DEFAULT_DRAIN_DELAY_MS = 150L
    }
}

/**
 * Keeps ESC/POS commands and CR/LF pairs intact while allowing printable text
 * to span chunks. UTF-8 continuation bytes are never used as a chunk boundary.
 */
internal fun safeChunks(payload: ByteArray, chunkSize: Int): List<ByteArray> {
    if (payload.isEmpty()) return emptyList()
    val chunks = mutableListOf<ByteArray>()
    var index = 0
    while (index < payload.size) {
        val commandLength = escPosCommandLength(payload, index)
        if (commandLength != null) {
            chunks += payload.copyOfRange(index, index + commandLength)
            index += commandLength
            continue
        }
        if (payload[index] == '\r'.code.toByte() && index + 1 < payload.size && payload[index + 1] == '\n'.code.toByte()) {
            chunks += payload.copyOfRange(index, index + 2)
            index += 2
            continue
        }

        val start = index
        while (index < payload.size && escPosCommandLength(payload, index) == null &&
            !(payload[index] == '\r'.code.toByte() && index + 1 < payload.size && payload[index + 1] == '\n'.code.toByte())
        ) {
            index++
        }
        var cursor = start
        while (cursor < index) {
            var end = minOf(cursor + chunkSize, index)
            while (end > cursor && end < index && isUtf8Continuation(payload[end])) end--
            if (end == cursor) {
                // A valid UTF-8 code point is at most four bytes; chunkSize is
                // validated accordingly above.
                end = minOf(cursor + 4, index)
            }
            chunks += payload.copyOfRange(cursor, end)
            cursor = end
        }
    }
    return chunks
}

private fun escPosCommandLength(payload: ByteArray, index: Int): Int? {
    if (payload[index] != 0x1B.toByte() || index + 1 >= payload.size) return null
    return when (payload[index + 1].toInt() and 0xFF) {
        '@'.code, 'm'.code -> 2
        'a'.code, 'E'.code, 'd'.code -> 3
        'p'.code -> 5
        else -> null
    }?.takeIf { index + it <= payload.size }
}

private fun isUtf8Continuation(value: Byte): Boolean = (value.toInt() and 0xC0) == 0x80
