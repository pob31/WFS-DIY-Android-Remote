package com.wfsdiy.wfs_control_2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

/**
 * Interop: the /remote/map/ messages a real WFS-DIY desktop sent in its state dump,
 * captured byte for byte (test resources map_dump/NNN.osc, one OSC message each, in
 * arrival order). The desktop project was the control-replay fixture patched by
 * remote_tablet_mock.py's seed_map_shapes: 16 outputs with output 3 hidden on the map,
 * a dashed amber rectangle labelled "Stage edge", an ellipse and a 150-point open line.
 */
class MapProtocolDesktopBytesTest {

    private fun messages(): List<ByteArray> {
        val out = mutableListOf<ByteArray>()
        var i = 0
        while (true) {
            val name = "map_dump/%03d.osc".format(i++)
            val stream = javaClass.classLoader?.getResourceAsStream(name) ?: break
            out += stream.use { it.readBytes() }
        }
        return out
    }

    /** The address, and the buffer left at the type tags, as the receive path does it. */
    private fun split(bytes: ByteArray): Pair<String, ByteBuffer> {
        val end = bytes.indexOf(0.toByte())
        val address = String(bytes, 0, end, Charsets.UTF_8)
        val buffer = ByteBuffer.wrap(bytes)
        buffer.position((end + 1 + 3) and -4)
        return address to buffer
    }

    @Test
    fun aDesktopDumpAssemblesIntoTheDrawnMap() {
        val all = messages()
        assertTrue("captured messages present", all.size >= 4)

        val assembler = MapAssembler()
        var rev: MapDecoded.Rev? = null
        for (bytes in all) {
            val (address, buffer) = split(bytes)
            assertTrue(address, MapProtocol.isMapAddress(address))
            val decoded = MapProtocol.decode(address, buffer)
            requireNotNull(decoded) { "desktop message $address did not decode" }
            if (decoded is MapDecoded.Rev) rev = decoded else assembler.accept(decoded, 0)
        }

        val speakers = assembler.geometry.speakers
        assertEquals((1..16).filter { it != 3 }, speakers.map { it.number })
        assertTrue(speakers.all { it.array == 0 })

        val shapes = assembler.geometry.shapes
        assertEquals(3, shapes.size)
        val (rect, ellipse, line) = shapes
        assertTrue(rect.closed)
        assertTrue(rect.dashed)
        assertEquals(4, rect.pointCount)
        assertEquals("Stage edge", rect.label)
        assertEquals(0xFFFFC107.toInt(), rect.argb)
        assertEquals(2.0f, rect.lineWidth)
        // origin-relative metres: centre (1, 2), half extents (2, 1)
        assertEquals(-1.0f, rect.points.filterIndexed { i, _ -> i % 2 == 0 }.min(), 1e-4f)
        assertEquals(3.0f, rect.points.filterIndexed { i, _ -> i % 2 == 1 }.max(), 1e-4f)
        assertEquals(1.0f, rect.labelY - 2.0f, 1e-4f)   // label on the top edge (y max = 3)

        assertTrue(ellipse.closed)
        assertEquals(48, ellipse.pointCount)
        assertFalse(line.closed)
        assertEquals(150, line.pointCount)

        val r = requireNotNull(rev) { "the dump closes with /remote/map/rev" }
        assertEquals(assembler.speakersRev, r.speakersRev)
        assertEquals(assembler.shapesRev, r.shapesRev)
        assertEquals(0, assembler.requestMask(r.speakersRev, r.shapesRev, 0))
    }
}
