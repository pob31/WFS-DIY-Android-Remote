package com.wfsdiy.wfs_control_2

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Whole-array output mute, shown and toggled on the Array Adjust tab.
 *
 * The mute is desktop SESSION state (never saved in the project, cleared when one is
 * loaded), so the tablet keeps no opinion of its own: a tap sends the wanted state
 * and the display follows what the desktop echoes.
 *
 * - out: [OUTGOING] ",ii" (array 1..10, 0/1). An absolute state, beside the
 *   /arrayAdjust/ deltas.
 * - in:  [INCOMING] ",i" + N ints: count N, then 0/1 per array. It arrives in the
 *   state dump, after every change (the sending tablet included) and every 2 s.
 *
 * Still protocol v4: a desktop that predates it never sends [INCOMING], which is how
 * the tab knows to keep the mute column disabled, and it counts [OUTGOING] as a
 * parse error with no side effect.
 */
object ArrayMuteProtocol {
    const val NUM_ARRAYS = 10
    const val OUTGOING = "/arrayAdjust/mute"
    const val INCOMING = "/remote/array/mute"

    // Sanity cap on the announced count: the arity check is the real guard.
    private const val MAX_COUNT = 64

    /**
     * Decode [INCOMING], the buffer positioned at its type tags. Returns exactly
     * [NUM_ARRAYS] states (1 = muted; arrays the desktop did not list read 0), or
     * null when the message is malformed. Pure JVM, never throws.
     */
    fun decode(b: ByteBuffer): IntArray? {
        b.order(ByteOrder.BIG_ENDIAN)
        val tags = readTags(b) ?: return null
        if (tags.length < 2 || tags[0] != ',' || tags.drop(1).any { it != 'i' }) return null
        if (b.remaining() < 4) return null
        val count = b.int
        if (count !in 0..MAX_COUNT || tags.length != 2 + count) return null
        if (b.remaining() < count * 4) return null
        val listed = IntArray(count) { b.int }
        return IntArray(NUM_ARRAYS) { i -> if (i < count && listed[i] != 0) 1 else 0 }
    }

    /** The type-tag string read as parseOscString reads it; null past the end. */
    private fun readTags(b: ByteBuffer): String? {
        if (!b.hasRemaining()) return null
        val bytes = ByteArrayOutputStream()
        while (b.hasRemaining()) {
            val byte = b.get()
            if (byte == 0.toByte()) break
            bytes.write(byte.toInt())
        }
        val aligned = (b.position() + 3) and -4
        if (aligned > b.limit()) return null
        b.position(aligned)
        return String(bytes.toByteArray(), Charsets.UTF_8)
    }
}
