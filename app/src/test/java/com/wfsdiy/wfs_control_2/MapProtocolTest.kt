package com.wfsdiy.wfs_control_2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * The /remote/map/ messages (speakers + drawn shapes) against packets from a small local OSC
 * encoder, handed over positioned at their type tags as the receive path does it, and
 * the assembler that turns chunks and parts into whole sets.
 */
class MapProtocolTest {

    private fun padded(s: String): ByteArray {
        val raw = s.toByteArray(Charsets.UTF_8)
        return raw.copyOf((raw.size + 1 + 3) and -4)
    }

    /** Args: Int -> i, Float -> f, String -> s; tags derived unless given. */
    private fun packet(address: String, args: List<Any>, tags: String? = null, dropTail: Int = 0): ByteBuffer {
        val out = ByteArrayOutputStream()
        out.write(padded(address))
        val tagsStart = out.size()
        out.write(padded(tags ?: ("," + args.joinToString("") {
            when (it) { is Int -> "i"; is Float -> "f"; is String -> "s"; else -> error("arg $it") }
        })))
        val arg = ByteBuffer.allocate(4)
        for (a in args) {
            when (a) {
                is Int -> { arg.clear(); arg.putInt(a); out.write(arg.array()) }
                is Float -> { arg.clear(); arg.putFloat(a); out.write(arg.array()) }
                is String -> out.write(padded(a))
            }
        }
        val bytes = out.toByteArray()
        return ByteBuffer.wrap(bytes.copyOf(bytes.size - dropTail)).also { it.position(tagsStart) }
    }

    private fun decode(address: String, args: List<Any>, tags: String? = null, dropTail: Int = 0) =
        MapProtocol.decode(address, packet(address, args, tags, dropTail))

    private fun speakersChunk(rev: Int, chunk: Int, count: Int, total: Int, numbers: IntRange): MapDecoded.SpeakersChunk {
        val args = mutableListOf<Any>(rev, chunk, count, total)
        for (n in numbers) args.addAll(listOf(n, n % 3, n * 1.0f, 2.0f, 180.0f))
        return decode(MapProtocol.SPEAKERS, args) as MapDecoded.SpeakersChunk
    }

    private fun shapePart(rev: Int, index: Int, part: Int, partCount: Int, points: FloatArray,
                          label: String = "", closed: Int = 1): MapDecoded.ShapePart {
        val args = mutableListOf<Any>(rev, index, part, partCount, closed, 0xFFFFC107.toInt(), 1,
            2.0f, 0.5f, 3.0f, label)
        points.forEach { args.add(it) }
        return decode(MapProtocol.SHAPE, args) as MapDecoded.ShapePart
    }

    @Test
    fun decodesRev() {
        val d = decode(MapProtocol.REV, listOf(7, 9)) as MapDecoded.Rev
        assertEquals(7, d.speakersRev)
        assertEquals(9, d.shapesRev)
    }

    @Test
    fun decodesASpeakerChunk() {
        val c = speakersChunk(5, 0, 1, 2, 1..2)
        assertEquals(2, c.speakers.size)
        assertEquals(MapSpeaker(2, 2, 2.0f, 2.0f, 180.0f), c.speakers[1])
    }

    @Test
    fun rejectsMalformedSpeakers() {
        // a speaker cut short in the tags
        assertNull(decode(MapProtocol.SPEAKERS, listOf(1, 0, 1, 1, 3, 0, 1.0f, 2.0f), tags = ",iiiiiiff"))
        // more speakers than the announced total
        assertNull(decode(MapProtocol.SPEAKERS, listOf(1, 0, 1, 0, 3, 0, 1.0f, 2.0f, 0.0f)))
        // chunk past chunkCount
        assertNull(decode(MapProtocol.SPEAKERS, listOf(1, 2, 2, 0)))
        // truncated payload
        assertNull(decode(MapProtocol.SPEAKERS, listOf(1, 0, 1, 1, 3, 0, 1.0f, 2.0f, 0.0f), dropTail = 4))
        // a NaN position
        assertNull(decode(MapProtocol.SPEAKERS, listOf(1, 0, 1, 1, 3, 0, Float.NaN, 2.0f, 0.0f)))
    }

    @Test
    fun decodesAShapePartWithItsLabel() {
        val p = shapePart(4, 0, 0, 1, floatArrayOf(-1f, 1f, 3f, 1f, 3f, 3f, -1f, 3f), label = "Scène")
        assertEquals("Scène", p.label)
        assertEquals(8, p.points.size)
        assertTrue(p.closed)
        assertTrue(p.dashed)
        assertEquals(0xFFFFC107.toInt(), p.argb)
    }

    @Test
    fun rejectsMalformedShapes() {
        // odd number of coordinates
        assertNull(decode(MapProtocol.SHAPE, listOf(1, 0, 0, 1, 1, 0, 0, 1f, 0f, 0f, "", 1f)))
        // part past partCount
        assertNull(decode(MapProtocol.SHAPE, listOf(1, 0, 1, 1, 1, 0, 0, 1f, 0f, 0f, "", 1f, 2f)))
        // truncated points
        assertNull(decode(MapProtocol.SHAPE, listOf(1, 0, 0, 1, 1, 0, 0, 1f, 0f, 0f, "", 1f, 2f), dropTail = 4))
        // a manifest with a negative count
        assertNull(decode(MapProtocol.SHAPES, listOf(1, -1)))
        // wrong tags
        assertNull(decode(MapProtocol.REV, listOf(1.0f, 2.0f)))
    }

    @Test
    fun outOfOrderChunksCommitOnceComplete() {
        val a = MapAssembler()
        assertFalse(a.accept(speakersChunk(3, 1, 2, 40, 33..40), 0))
        assertTrue(a.accept(speakersChunk(3, 0, 2, 40, 1..32), 10))
        assertEquals(40, a.geometry.speakers.size)
        assertEquals(1, a.geometry.speakers.first().number)
        assertEquals(3, a.speakersRev)
    }

    @Test
    fun aMissingChunkCommitsNothing() {
        val a = MapAssembler()
        assertFalse(a.accept(speakersChunk(3, 0, 2, 40, 1..32), 0))
        assertTrue(a.geometry.speakers.isEmpty())
        assertEquals(null, a.speakersRev)
    }

    @Test
    fun aNewRevisionSupersedesAPartialOne() {
        val a = MapAssembler()
        a.accept(speakersChunk(3, 0, 2, 40, 1..32), 0)
        assertTrue(a.accept(speakersChunk(4, 0, 1, 5, 1..5), 10))
        assertEquals(5, a.geometry.speakers.size)
        // the late chunk of revision 3 starts a new (incomplete) assembly, never mixes in
        assertFalse(a.accept(speakersChunk(3, 1, 2, 40, 33..40), 20))
        assertEquals(5, a.geometry.speakers.size)
    }

    @Test
    fun anEmptySpeakerSetCommits() {
        val a = MapAssembler()
        assertTrue(a.accept(speakersChunk(8, 0, 1, 0, IntRange.EMPTY), 0))
        assertTrue(a.geometry.speakers.isEmpty())
        assertEquals(8, a.speakersRev)
    }

    @Test
    fun shapesAssembleFromPartsInAnyOrder() {
        val a = MapAssembler()
        val first = FloatArray(160) { it.toFloat() }
        val second = FloatArray(140) { 1000f + it }
        assertFalse(a.accept(shapePart(6, 1, 0, 1, floatArrayOf(0f, 0f, 1f, 1f), closed = 0), 0))
        assertFalse(a.accept(shapePart(6, 0, 1, 2, second), 0))
        assertFalse(a.accept(decode(MapProtocol.SHAPES, listOf(6, 2))!!, 0))
        assertTrue(a.accept(shapePart(6, 0, 0, 2, first, label = "Line"), 0))
        val shapes = a.geometry.shapes
        assertEquals(2, shapes.size)
        assertEquals(150, shapes[0].pointCount)
        assertEquals("Line", shapes[0].label)
        assertEquals(1000f, shapes[0].points[160])
        assertFalse(shapes[1].closed)
    }

    @Test
    fun anEmptyShapeManifestCommitsAnEmptySet() {
        val a = MapAssembler()
        a.accept(decode(MapProtocol.SHAPES, listOf(1, 1))!!, 0)
        assertTrue(a.accept(shapePart(1, 0, 0, 1, floatArrayOf(0f, 0f, 1f, 1f)), 0))
        assertEquals(1, a.geometry.shapes.size)
        assertTrue(a.accept(decode(MapProtocol.SHAPES, listOf(2, 0))!!, 0))
        assertTrue(a.geometry.shapes.isEmpty())
        // the speakers were never touched
        assertNull(a.speakersRev)
    }

    @Test
    fun requestsOnlyWhatItDoesNotHold() {
        val a = MapAssembler()
        // nothing held: ask for both
        assertEquals(3, a.requestMask(10, 20, 0))
        a.accept(speakersChunk(10, 0, 1, 1, 1..1), 0)
        assertEquals(MapProtocol.MASK_SHAPES, a.requestMask(10, 20, 0))
        a.accept(decode(MapProtocol.SHAPES, listOf(20, 0))!!, 0)
        assertEquals(0, a.requestMask(10, 20, 0))
    }

    @Test
    fun aSetAlreadyArrivingIsNotRequestedUntilItsGraceRunsOut() {
        val a = MapAssembler()
        a.accept(speakersChunk(11, 0, 2, 40, 1..32), 1000)
        a.accept(decode(MapProtocol.SHAPES, listOf(21, 0))!!, 1000)
        assertEquals(0, a.requestMask(11, 21, 1500))
        assertEquals(MapProtocol.MASK_SPEAKERS, a.requestMask(11, 21, 2500))
    }

    @Test
    fun resetForgetsEverything() {
        val a = MapAssembler()
        a.accept(speakersChunk(10, 0, 1, 1, 1..1), 0)
        a.reset()
        assertNull(a.speakersRev)
        assertTrue(a.geometry.speakers.isEmpty())
        assertNotNull(a.requestMask(10, 0, 0))
    }
}
