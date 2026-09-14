package com.wfsdiy.wfs_control_2

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * The /remote/vis/… decoders against packets from a small local OSC encoder, kept apart
 * from the app's own send helpers so that one encoding bug cannot hide another. Every
 * buffer is handed over positioned at its type tags, as the receive path does it.
 */
class VisProtocolTest {

    // --- Minimal OSC encoder: NUL-terminated strings padded to 4, big-endian args ---

    private fun padded(s: String): ByteArray {
        val raw = s.toByteArray(Charsets.UTF_8)
        return raw.copyOf((raw.size + 1 + 3) and -4)
    }

    /** One message, positioned past its address; [args] are Int or Float. */
    private fun packet(address: String, tags: String, args: List<Number>, dropTail: Int = 0): ByteBuffer {
        val out = ByteArrayOutputStream()
        out.write(padded(address))
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

    private fun assertRejected(d: VisDecoded) =
        assertTrue("expected a rejection, got $d", d is VisDecoded.Rejected)

    // --- /remote/vis/config ---

    private fun config(numOutputs: Int, numReverbs: Int) =
        VisDecoder.config(packet("/remote/vis/config", ",ii", listOf(numOutputs, numReverbs)))

    @Test
    fun configAcceptsTheOldCapsTheDesktopMaximaAndTheSanityCaps() {
        assertEquals(VisDecoded.Config(64, 16), config(64, 16))
        assertEquals(VisDecoded.Config(128, 32), config(128, 32))
        assertEquals(VisDecoded.Config(512, 128), config(512, 128))
        assertEquals(VisDecoded.Config(0, 0), config(0, 0))
    }

    @Test
    fun configRejectsCountsOutsideTheSanityCaps() {
        assertRejected(config(513, 16))
        assertRejected(config(-1, 16))
        assertRejected(config(64, 129))
        assertRejected(config(64, -1))
    }

    @Test
    fun configRejectsWrongTags() {
        assertRejected(VisDecoder.config(packet("/remote/vis/config", ",if", listOf(64, 16f))))
        assertRejected(VisDecoder.config(packet("/remote/vis/config", ",i", listOf(64))))
        assertRejected(VisDecoder.config(packet("/remote/vis/config", ",iii", listOf(64, 16, 0))))
    }

    @Test
    fun configRejectsTruncatedAndTaglessPacketsWithoutThrowing() {
        assertRejected(VisDecoder.config(packet("/remote/vis/config", ",ii", listOf(64, 16), dropTail = 4)))
        // The packet ends right after the address: no type-tag string at all.
        val address = padded("/remote/vis/config")
        assertRejected(VisDecoder.config(ByteBuffer.wrap(address).also { it.position(address.size) }))
        // Unterminated tags whose 4-byte padding would run past the end of the packet.
        val unterminated = address + ",ii".toByteArray(Charsets.UTF_8)
        assertRejected(VisDecoder.config(ByteBuffer.wrap(unterminated).also { it.position(address.size) }))
    }

    // --- /remote/vis/outputArrays ---

    private fun outputArraysPacket(count: Int, ids: List<Int>, dropTail: Int = 0) = packet(
        "/remote/vis/outputArrays", "," + "i".repeat(1 + ids.size), listOf<Number>(count) + ids, dropTail)

    @Test
    fun outputArraysAcceptsAFullDesktopRig() {
        val ids = List(128) { it % 11 }  // 0 = Single, 1..10 = arrays
        val d = VisDecoder.outputArrays(outputArraysPacket(128, ids))
        assertTrue("got $d", d is VisDecoded.OutputArrays)
        assertArrayEquals(ids.toIntArray(), (d as VisDecoded.OutputArrays).arrays)
    }

    @Test
    fun outputArraysAcceptsUpToTheSanityCapAndNoMore() {
        assertTrue(VisDecoder.outputArrays(outputArraysPacket(512, List(512) { 1 })) is VisDecoded.OutputArrays)
        assertTrue(VisDecoder.outputArrays(outputArraysPacket(0, emptyList())) is VisDecoded.OutputArrays)
        assertRejected(VisDecoder.outputArrays(outputArraysPacket(513, List(513) { 1 })))
        assertRejected(VisDecoder.outputArrays(outputArraysPacket(-1, emptyList())))
    }

    @Test
    fun outputArraysRejectsALengthMismatch() {
        assertRejected(VisDecoder.outputArrays(outputArraysPacket(128, List(127) { 1 })))
        assertRejected(VisDecoder.outputArrays(outputArraysPacket(127, List(128) { 1 })))
    }

    @Test
    fun outputArraysRejectsATruncatedPacket() {
        assertRejected(VisDecoder.outputArrays(outputArraysPacket(128, List(128) { 1 }, dropTail = 4)))
    }

    @Test
    fun outputArraysRejectsNonIntTags() {
        assertRejected(VisDecoder.outputArrays(
            packet("/remote/vis/outputArrays", ",iif", listOf(2, 1, 1f))))
    }

    // --- /remote/vis/delays and /remote/vis/levels rows ---

    private fun rowPacket(
        channel: Int, numOutputs: Int, numReverbs: Int, values: List<Float>,
        tags: String = ",iii" + "f".repeat(values.size), dropTail: Int = 0
    ) = packet("/remote/vis/levels", tags, listOf<Number>(channel, numOutputs, numReverbs) + values, dropTail)

    @Test
    fun rowAcceptsAFullDesktopRig() {
        val values = List(128 + 32) { -0.25f * it }
        val d = VisDecoder.row(rowPacket(7, 128, 32, values))
        assertTrue("got $d", d is VisDecoded.Row)
        d as VisDecoded.Row
        assertEquals(7, d.channel)
        assertEquals(128, d.numOutputs)
        assertEquals(32, d.numReverbs)
        assertArrayEquals(values.toFloatArray(), d.values, 0f)
    }

    @Test
    fun rowAcceptsTheSanityCapsAndTheLastChannel() {
        assertTrue(VisDecoder.row(rowPacket(MAX_INPUTS, 512, 128, List(640) { 0f })) is VisDecoded.Row)
        assertTrue(VisDecoder.row(rowPacket(1, 0, 0, emptyList())) is VisDecoded.Row)
    }

    @Test
    fun rowRejectsAChannelOutsideTheInputRange() {
        assertRejected(VisDecoder.row(rowPacket(0, 64, 16, List(80) { 0f })))
        assertRejected(VisDecoder.row(rowPacket(MAX_INPUTS + 1, 64, 16, List(80) { 0f })))
    }

    @Test
    fun rowRejectsCountsOutsideTheSanityCaps() {
        assertRejected(VisDecoder.row(rowPacket(1, 513, 0, List(513) { 0f })))
        assertRejected(VisDecoder.row(rowPacket(1, 0, 129, List(129) { 0f })))
    }

    @Test
    fun rowRejectsATagCountMismatch() {
        assertRejected(VisDecoder.row(rowPacket(1, 128, 32, List(159) { 0f })))
        assertRejected(VisDecoder.row(rowPacket(1, 128, 31, List(160) { 0f })))
    }

    @Test
    fun rowRejectsAShortBuffer() {
        assertRejected(VisDecoder.row(rowPacket(1, 128, 32, List(160) { 0f }, dropTail = 4)))
        // Only the channel made it in.
        assertRejected(VisDecoder.row(packet("/remote/vis/delays", ",iii", listOf(1))))
    }

    @Test
    fun rowRejectsMalformedTags() {
        assertRejected(VisDecoder.row(rowPacket(1, 1, 0, listOf(0f), tags = ",iif")))
        assertRejected(VisDecoder.row(rowPacket(1, 2, 0, listOf(0f, 0f), tags = ",iiifi")))
    }

    // --- /remote/vis/selection ---

    private fun selectionPacket(primary: Int, clusterId: Int, ids: List<Int>, count: Int = ids.size, dropTail: Int = 0) =
        packet("/remote/vis/selection", ",iii" + "i".repeat(ids.size),
            listOf<Number>(primary, clusterId, count) + ids, dropTail)

    @Test
    fun selectionAcceptsPrimaryZero() {
        assertEquals(VisDecoded.Selection(0, 3, listOf(2, 5)),
            VisDecoder.selection(selectionPacket(0, 3, listOf(2, 5))))
        assertEquals(VisDecoded.Selection(0, 0, emptyList()),
            VisDecoder.selection(selectionPacket(0, 0, emptyList())))
    }

    @Test
    fun selectionAcceptsTheRangeLimits() {
        val all = List(MAX_INPUTS) { it + 1 }
        assertEquals(VisDecoded.Selection(MAX_INPUTS, VisLimits.MAX_CLUSTER, all),
            VisDecoder.selection(selectionPacket(MAX_INPUTS, VisLimits.MAX_CLUSTER, all)))
    }

    @Test
    fun selectionRejectsOutOfRangeFields() {
        assertRejected(VisDecoder.selection(selectionPacket(1, 11, listOf(1))))
        assertRejected(VisDecoder.selection(selectionPacket(1, -1, listOf(1))))
        assertRejected(VisDecoder.selection(selectionPacket(MAX_INPUTS + 1, 0, emptyList())))
        assertRejected(VisDecoder.selection(selectionPacket(-1, 0, emptyList())))
        assertRejected(VisDecoder.selection(selectionPacket(1, 0, List(MAX_INPUTS + 1) { 1 })))
    }

    @Test
    fun selectionRejectsACountMismatchAndATruncatedPacket() {
        assertRejected(VisDecoder.selection(selectionPacket(1, 0, listOf(1, 2), count = 3)))
        assertRejected(VisDecoder.selection(selectionPacket(1, 0, listOf(1, 2), dropTail = 4)))
        assertRejected(VisDecoder.selection(packet("/remote/vis/selection", ",iif", listOf(1, 0, 0f))))
    }
}
