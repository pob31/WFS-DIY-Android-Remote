package com.wfsdiy.wfs_control_2

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Receive-side bounds for the /remote/vis/… messages (protocol v3).
 *
 * Sanity caps, deliberately NOT the desktop's configuration maxima (128 outputs and
 * 32 reverbs today, and those have been raised before). The parser used to hard-code
 * 64/16, copied from desktop comments that had already gone stale: on any larger rig
 * it silently dropped the config, the output arrays and every row, so numOutputs
 * never left 0 and the Visualisation tab sat on "Waiting for data…" with every
 * resend dropped the same way. The real guard against a malformed packet is the
 * arity check (the type tags must match the counts the message describes); these
 * caps only refuse absurd counts. A 512 + 128 row is 640 floats, about 2.6 KB, well
 * inside the 8 KB receive buffer.
 */
object VisLimits {
    const val MAX_OUTPUTS = 512
    const val MAX_REVERBS = 128
    // Desktop cluster ids are 1..10; 0 means the selection is not a cluster.
    const val MAX_CLUSTER = 10
}

/** One decoded /remote/vis/… message: its payload, or why it was dropped. */
sealed interface VisDecoded {
    data class Config(val numOutputs: Int, val numReverbs: Int) : VisDecoded
    class OutputArrays(val arrays: IntArray) : VisDecoded
    data class Selection(val primary: Int, val clusterId: Int, val selection: List<Int>) : VisDecoded
    class Row(val channel: Int, val numOutputs: Int, val numReverbs: Int, val values: FloatArray) : VisDecoded
    data class Rejected(val reason: String) : VisDecoded
}

/**
 * Decoders for the /remote/vis/… payloads. Pure JVM (no Android classes), so the tests
 * run exactly what the receive path runs. Each takes the packet buffer positioned at
 * the type-tag string (the address already consumed) and never throws: anything
 * malformed comes back as [VisDecoded.Rejected], and nothing is returned half-read,
 * so a callback only ever sees a message that checked out whole.
 */
object VisDecoder {

    /** ",ii": output count, reverb count. */
    fun config(b: ByteBuffer): VisDecoded {
        val tags = readTags(b) ?: return noTags()
        if (tags != ",ii") return badTags(tags)
        if (b.remaining() < 8) return truncated(b, 8)
        val numOutputs = b.int
        val numReverbs = b.int
        countsError(numOutputs, numReverbs)?.let { return VisDecoded.Rejected(it) }
        return VisDecoded.Config(numOutputs, numReverbs)
    }

    /** ",i" + N ints: output count N, then each output's array id (0 = Single). */
    fun outputArrays(b: ByteBuffer): VisDecoded {
        val tags = readTags(b) ?: return noTags()
        if (!isIntTags(tags, minLength = 2)) return badTags(tags)
        if (b.remaining() < 4) return truncated(b, 4)
        val numOutputs = b.int
        if (numOutputs !in 0..VisLimits.MAX_OUTPUTS)
            return VisDecoded.Rejected("$numOutputs outputs, outside 0..${VisLimits.MAX_OUTPUTS}")
        if (tags.length != 2 + numOutputs)
            return VisDecoded.Rejected("${tags.length - 2} array ids tagged for $numOutputs outputs")
        if (b.remaining() < numOutputs * 4) return truncated(b, numOutputs * 4)
        return VisDecoded.OutputArrays(IntArray(numOutputs) { b.int })
    }

    /**
     * ",iii" + N ints: primary channel, cluster id (0 = none), N, then the N selected
     * channels. A primary of 0 is accepted: the desktop sends it when its selected
     * channel no longer exists, and refusing it threw away the cluster and set too.
     */
    fun selection(b: ByteBuffer): VisDecoded {
        val tags = readTags(b) ?: return noTags()
        if (!isIntTags(tags, minLength = 4)) return badTags(tags)
        if (b.remaining() < 12) return truncated(b, 12)
        val primary = b.int
        val clusterId = b.int
        val count = b.int
        if (primary !in 0..MAX_INPUTS)
            return VisDecoded.Rejected("primary $primary, outside 0..$MAX_INPUTS")
        if (clusterId !in 0..VisLimits.MAX_CLUSTER)
            return VisDecoded.Rejected("cluster $clusterId, outside 0..${VisLimits.MAX_CLUSTER}")
        if (count !in 0..MAX_INPUTS)
            return VisDecoded.Rejected("$count selected, outside 0..$MAX_INPUTS")
        if (tags.length != 4 + count)
            return VisDecoded.Rejected("${tags.length - 4} channels tagged for $count selected")
        if (b.remaining() < count * 4) return truncated(b, count * 4)
        return VisDecoded.Selection(primary, clusterId, List(count) { b.int })
    }

    /**
     * ",iii" + (N + R) floats: channel, output count N, reverb count R, then the N
     * output values followed by the R reverb values. /remote/vis/delays (ms) and
     * /remote/vis/levels (display-ready dB) share this layout.
     */
    fun row(b: ByteBuffer): VisDecoded {
        val tags = readTags(b) ?: return noTags()
        if (tags.length < 4 || tags[0] != ',' || tags.substring(1, 4) != "iii" ||
            tags.drop(4).any { it != 'f' }) return badTags(tags)
        if (b.remaining() < 12) return truncated(b, 12)
        val channel = b.int
        val numOutputs = b.int
        val numReverbs = b.int
        if (channel !in 1..MAX_INPUTS)
            return VisDecoded.Rejected("channel $channel, outside 1..$MAX_INPUTS")
        countsError(numOutputs, numReverbs)?.let { return VisDecoded.Rejected(it) }
        val valueCount = numOutputs + numReverbs
        if (tags.length != 4 + valueCount)
            return VisDecoded.Rejected("${tags.length - 4} values tagged for $numOutputs + $numReverbs")
        if (b.remaining() < valueCount * 4) return truncated(b, valueCount * 4)
        return VisDecoded.Row(channel, numOutputs, numReverbs, FloatArray(valueCount) { b.float })
    }

    /**
     * The OSC type-tag string, read the way parseOscString reads it: the bytes up to the
     * NUL, then the position rounded up to the next 4-byte boundary. Null when there is
     * no tag string, or when that padding would run past the end of the packet (there
     * parseOscString throws, which dropped the packet all the same).
     */
    private fun readTags(b: ByteBuffer): String? {
        b.order(ByteOrder.BIG_ENDIAN)  // OSC is big-endian whatever the caller left set
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

    private fun isIntTags(tags: String, minLength: Int): Boolean =
        tags.length >= minLength && tags[0] == ',' && tags.drop(1).all { it == 'i' }

    private fun countsError(numOutputs: Int, numReverbs: Int): String? =
        if (numOutputs in 0..VisLimits.MAX_OUTPUTS && numReverbs in 0..VisLimits.MAX_REVERBS) null
        else "counts $numOutputs/$numReverbs, outside " +
            "0..${VisLimits.MAX_OUTPUTS}/0..${VisLimits.MAX_REVERBS}"

    private fun noTags() = VisDecoded.Rejected("no type tags")

    // A row's tags run to 164 characters at 128 + 32, so only their head goes in the log.
    private fun badTags(tags: String) = VisDecoded.Rejected(
        "type tags '${if (tags.length > 12) tags.take(12) + "…" else tags}' (${tags.length})")

    private fun truncated(b: ByteBuffer, needed: Int) =
        VisDecoded.Rejected("truncated: ${b.remaining()} bytes left, $needed needed")
}
