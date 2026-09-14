package com.wfsdiy.wfs_control_2

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * /remote/array/mute against packets from a small local OSC encoder (kept apart from
 * the app's send helpers), each handed over positioned at its type tags as the
 * receive path does it.
 */
class ArrayMuteProtocolTest {

    private fun padded(s: String): ByteArray {
        val raw = s.toByteArray(Charsets.UTF_8)
        return raw.copyOf((raw.size + 1 + 3) and -4)
    }

    private fun packet(tags: String, args: List<Number>, dropTail: Int = 0): ByteBuffer {
        val out = ByteArrayOutputStream()
        out.write(padded(ArrayMuteProtocol.INCOMING))
        val tagsStart = out.size()
        out.write(padded(tags))
        val arg = ByteBuffer.allocate(4)
        for (a in args) {
            arg.clear()
            when (a) {
                is Int -> arg.putInt(a)
                is Float -> arg.putFloat(a)
                else -> error("unsupported argument $a")
            }
            out.write(arg.array())
        }
        val bytes = out.toByteArray()
        return ByteBuffer.wrap(bytes.copyOf(bytes.size - dropTail)).also { it.position(tagsStart) }
    }

    private fun ints(vararg v: Int) = ",i" + "i".repeat(v.size - 1)

    @Test
    fun decodesTheDesktopsTenStates() {
        val args = intArrayOf(10, 0, 0, 1, 0, 0, 0, 0, 0, 0, 1)
        assertArrayEquals(intArrayOf(0, 0, 1, 0, 0, 0, 0, 0, 0, 1),
            ArrayMuteProtocol.decode(packet(ints(*args), args.toList())))
    }

    @Test
    fun anyNonZeroIsMutedAndUnlistedArraysReadUnmuted() {
        val args = intArrayOf(3, 2, 0, -1)
        assertArrayEquals(intArrayOf(1, 0, 1, 0, 0, 0, 0, 0, 0, 0),
            ArrayMuteProtocol.decode(packet(ints(*args), args.toList())))
    }

    @Test
    fun ignoresArraysPastTen() {
        val args = IntArray(13) { if (it == 0) 12 else 1 }
        assertArrayEquals(IntArray(10) { 1 },
            ArrayMuteProtocol.decode(packet(ints(*args), args.toList())))
    }

    @Test
    fun rejectsMalformedMessages() {
        // arity: 10 announced, 9 tagged
        assertNull(ArrayMuteProtocol.decode(packet(",i" + "i".repeat(9), listOf(10) + List(9) { 0 })))
        // a float state
        assertNull(ArrayMuteProtocol.decode(packet(",if", listOf(1, 1f))))
        // truncated payload
        val args = IntArray(11) { if (it == 0) 10 else 0 }
        assertNull(ArrayMuteProtocol.decode(packet(ints(*args), args.toList(), dropTail = 4)))
        // absurd count, no tags
        assertNull(ArrayMuteProtocol.decode(packet(",i", listOf(-1))))
        assertNull(ArrayMuteProtocol.decode(packet(",", emptyList())))
    }
}
