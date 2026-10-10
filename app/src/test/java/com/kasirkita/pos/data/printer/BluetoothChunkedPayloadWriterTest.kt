package com.kasirkita.pos.data.printer

import java.io.ByteArrayOutputStream
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothChunkedPayloadWriterTest {
    @Test
    fun `writes complete ordered payload in bounded chunks and drains after flush`() = runTest {
        val events = mutableListOf<String>()
        val output = object : ByteArrayOutputStream() {
            override fun write(bytes: ByteArray, offset: Int, length: Int) {
                events += "write:$length"
                super.write(bytes, offset, length)
            }

            override fun flush() {
                events += "flush"
                super.flush()
            }
        }
        val payload = ByteArray(11) { it.toByte() }
        val writer = ChunkedBluetoothPayloadWriter(
            chunkSize = 5,
            interChunkDelayMs = 0,
            drainDelayMs = 1,
            wait = { events += "wait" },
        )

        val stats = writer.write(output, payload)

        assertArrayEquals(payload, output.toByteArray())
        assertEquals(listOf("write:5", "write:5", "write:1", "flush", "wait"), events)
        assertEquals(3, stats.chunkCount)
        assertTrue(stats.flushed)
        assertTrue(stats.drained)
    }

    @Test
    fun `empty payload still flushes once and never retries`() = runTest {
        var flushes = 0
        val output = object : ByteArrayOutputStream() {
            override fun flush() {
                flushes++
                super.flush()
            }
        }
        val writer = ChunkedBluetoothPayloadWriter(
            interChunkDelayMs = 0,
            drainDelayMs = 0,
            wait = {},
        )
        val stats = writer.write(output, byteArrayOf())
        assertEquals(0, stats.chunkCount)
        assertTrue(stats.flushed)
        assertTrue(stats.drained)
        assertEquals(1, flushes)
    }

    @Test(expected = CancellationException::class)
    fun `cancellation during pacing propagates without retry`() = runTest {
        val writer = ChunkedBluetoothPayloadWriter(
            chunkSize = 5,
            interChunkDelayMs = 1,
            drainDelayMs = 0,
            wait = { throw CancellationException("cancelled") },
        )
        writer.write(ByteArrayOutputStream(), ByteArray(6) { it.toByte() })
    }

    @Test
    fun `boundary payloads preserve bytes and keep commands atomic`() = runTest {
        listOf(255, 256, 257, 511, 512, 513).forEach { length ->
            val payload = ByteArray(length) { 'A'.code.toByte() }
            val commandAt = minOf(255, length - 3)
            payload[commandAt] = 0x1B
            payload[commandAt + 1] = 'a'.code.toByte()
            payload[commandAt + 2] = 1
            val chunks = safeChunks(payload, 256)
            assertTrue(chunks.all { it.size <= 256 })
            assertArrayEquals(payload, chunks.fold(ByteArrayOutputStream()) { out, chunk -> out.apply { write(chunk) } }.toByteArray())
            chunks.forEach { chunk ->
                chunk.indices.forEach { index ->
                    if (chunk[index] == 0x1B.toByte()) {
                        assertTrue(index + 2 < chunk.size)
                        assertEquals('a'.code.toByte(), chunk[index + 1])
                    }
                }
            }
        }
    }

    @Test
    fun `utf8 text is not split in the middle of a code point`() {
        val payload = "é".repeat(200).toByteArray(Charsets.UTF_8)
        val chunks = safeChunks(payload, 256)
        val joined = chunks.fold(ByteArrayOutputStream()) { out, chunk -> out.apply { write(chunk) } }.toByteArray()
        assertArrayEquals(payload, joined)
        assertEquals("é".repeat(200), String(joined, Charsets.UTF_8))
    }

    @Test
    fun `all formatter and drawer command forms remain atomic`() {
        val payload = byteArrayOf(
            0x1B, '@'.code.toByte(),
            0x1B, 'a'.code.toByte(), 1,
            0x1B, 'E'.code.toByte(), 1,
            0x1B, 'd'.code.toByte(), 5,
            0x1B, 'm'.code.toByte(),
            0x1B, 'p'.code.toByte(), 0, 50, 0xFA.toByte(),
        )
        val chunks = safeChunks(payload, 256)
        assertEquals(6, chunks.size)
        chunks.forEach { chunk -> assertTrue(chunk.size <= 256) }
        assertArrayEquals(payload, chunks.fold(ByteArrayOutputStream()) { out, chunk -> out.apply { write(chunk) } }.toByteArray())
    }
}
