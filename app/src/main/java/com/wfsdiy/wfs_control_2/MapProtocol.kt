package com.wfsdiy.wfs_control_2

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Tablet map geometry from the desktop (still protocol v4, additive): the speakers and
 * the outline shapes drawn on the desktop Map. Read-only here; drawn under the inputs.
 *
 * All coordinates are origin-relative stage metres, like the input positions.
 *
 * - [SPEAKERS] ",iiii" rev chunk chunkCount total + n x "iifff" (number, array, x, y,
 *   orientation deg). Only the outputs the desktop shows; at most 32 per chunk.
 * - [SHAPES] ",ii" rev count: the manifest of a shape set.
 * - [SHAPE] ",iiiiiiifffs" rev index part partCount closed colourARGB dashed lineWidth
 *   labelX labelY label + k x "ff": one part of one flattened outline (label in part 0).
 * - [REV] ",ii" speakersRev shapesRev, after every push and every 2 s.
 * - [REQUEST] (out) ",i" mask: 1 speakers, 2 shapes. Sent when a [REV] names a set we
 *   do not hold (a lost chunk, or a change pushed while we were away).
 *
 * A set is committed only once every chunk / part of one revision is in, so a lost
 * datagram never shows half a rig; the previous set stays on screen meanwhile.
 * Pure JVM (no Android classes) and never throws, so it is unit-tested directly.
 */
data class MapSpeaker(val number: Int, val array: Int, val x: Float, val y: Float, val orientation: Float)

class MapShape(
    val closed: Boolean,
    val argb: Int,
    val dashed: Boolean,
    val lineWidth: Float,
    val labelX: Float,
    val labelY: Float,
    val label: String,
    /** x0, y0, x1, y1, ... in stage metres. */
    val points: FloatArray
) {
    val pointCount: Int get() = points.size / 2

    override fun equals(other: Any?): Boolean =
        other is MapShape && closed == other.closed && argb == other.argb && dashed == other.dashed &&
            lineWidth == other.lineWidth && labelX == other.labelX && labelY == other.labelY &&
            label == other.label && points.contentEquals(other.points)

    override fun hashCode(): Int = points.contentHashCode() * 31 + label.hashCode()
}

data class MapGeometry(
    val speakers: List<MapSpeaker> = emptyList(),
    val shapes: List<MapShape> = emptyList()
)

sealed class MapDecoded {
    data class Rev(val speakersRev: Int, val shapesRev: Int) : MapDecoded()
    class SpeakersChunk(val rev: Int, val chunk: Int, val chunkCount: Int, val total: Int,
                        val speakers: List<MapSpeaker>) : MapDecoded()
    data class ShapesManifest(val rev: Int, val count: Int) : MapDecoded()
    class ShapePart(val rev: Int, val index: Int, val part: Int, val partCount: Int,
                    val closed: Boolean, val argb: Int, val dashed: Boolean, val lineWidth: Float,
                    val labelX: Float, val labelY: Float, val label: String,
                    val points: FloatArray) : MapDecoded()
}

object MapLimits {
    // Sanity caps, far above the desktop's own (128 outputs, 256 shapes, 256 vertices):
    // the arity checks are the real guard. Never mirror the desktop maxima here.
    const val MAX_SPEAKERS = 512
    const val MAX_CHUNKS = 64
    const val MAX_SHAPES = 1024
    const val MAX_PARTS = 64
    const val MAX_POINTS_PER_PART = 512
}

object MapProtocol {
    const val REV = "/remote/map/rev"
    const val SPEAKERS = "/remote/map/speakers"
    const val SHAPES = "/remote/map/shapes"
    const val SHAPE = "/remote/map/shape"
    const val REQUEST = "/remote/map/request"

    const val MASK_SPEAKERS = 1
    const val MASK_SHAPES = 2

    fun isMapAddress(address: String) =
        address == REV || address == SPEAKERS || address == SHAPES || address == SHAPE

    /** Decode the message at [address], the buffer positioned at its type tags. */
    fun decode(address: String, b: ByteBuffer): MapDecoded? = try {
        b.order(ByteOrder.BIG_ENDIAN)
        when (address) {
            REV -> decodeRev(b)
            SPEAKERS -> decodeSpeakers(b)
            SHAPES -> decodeManifest(b)
            SHAPE -> decodeShape(b)
            else -> null
        }
    } catch (e: Exception) {
        null
    }

    private fun decodeRev(b: ByteBuffer): MapDecoded? {
        val tags = readString(b) ?: return null
        if (tags != ",ii" || b.remaining() < 8) return null
        return MapDecoded.Rev(b.int, b.int)
    }

    private fun decodeSpeakers(b: ByteBuffer): MapDecoded? {
        val tags = readString(b) ?: return null
        if (!tags.startsWith(",iiii")) return null
        val body = tags.substring(5)
        if (body.length % 5 != 0) return null
        for (k in body.indices) if (body[k] != "iifff"[k % 5]) return null
        val n = body.length / 5
        if (b.remaining() < 16 + n * 20) return null
        val rev = b.int
        val chunk = b.int
        val chunkCount = b.int
        val total = b.int
        if (chunkCount !in 1..MapLimits.MAX_CHUNKS || chunk !in 0 until chunkCount) return null
        if (total !in 0..MapLimits.MAX_SPEAKERS || n > total) return null
        val speakers = List(n) {
            val number = b.int
            val array = b.int
            MapSpeaker(number, array, b.float, b.float, b.float)
        }
        if (speakers.any { !it.x.isFinite() || !it.y.isFinite() || !it.orientation.isFinite() }) return null
        return MapDecoded.SpeakersChunk(rev, chunk, chunkCount, total, speakers)
    }

    private fun decodeManifest(b: ByteBuffer): MapDecoded? {
        val tags = readString(b) ?: return null
        if (tags != ",ii" || b.remaining() < 8) return null
        val rev = b.int
        val count = b.int
        if (count !in 0..MapLimits.MAX_SHAPES) return null
        return MapDecoded.ShapesManifest(rev, count)
    }

    private fun decodeShape(b: ByteBuffer): MapDecoded? {
        val tags = readString(b) ?: return null
        val head = ",iiiiiiifffs"
        if (!tags.startsWith(head)) return null
        val rest = tags.substring(head.length)
        if (rest.length % 2 != 0 || rest.any { it != 'f' }) return null
        val k = rest.length / 2
        if (k > MapLimits.MAX_POINTS_PER_PART) return null
        if (b.remaining() < 7 * 4 + 3 * 4) return null
        val rev = b.int
        val index = b.int
        val part = b.int
        val partCount = b.int
        val closed = b.int != 0
        val argb = b.int
        val dashed = b.int != 0
        val lineWidth = b.float
        val labelX = b.float
        val labelY = b.float
        val label = readString(b) ?: return null
        if (index !in 0 until MapLimits.MAX_SHAPES) return null
        if (partCount !in 1..MapLimits.MAX_PARTS || part !in 0 until partCount) return null
        if (!lineWidth.isFinite() || !labelX.isFinite() || !labelY.isFinite()) return null
        if (b.remaining() < k * 8) return null
        val points = FloatArray(k * 2) { b.float }
        if (points.any { !it.isFinite() }) return null
        return MapDecoded.ShapePart(rev, index, part, partCount, closed, argb, dashed,
            lineWidth, labelX, labelY, label, points)
    }

    /** An OSC string (NUL-terminated, 4-aligned); null past the end. */
    private fun readString(b: ByteBuffer): String? {
        if (!b.hasRemaining()) return null
        val bytes = ByteArrayOutputStream()
        var terminated = false
        while (b.hasRemaining()) {
            val byte = b.get()
            if (byte == 0.toByte()) { terminated = true; break }
            bytes.write(byte.toInt())
        }
        if (!terminated) return null
        val aligned = (b.position() + 3) and -4
        if (aligned > b.limit()) return null
        b.position(aligned)
        return String(bytes.toByteArray(), Charsets.UTF_8)
    }
}

/**
 * Collects chunks and parts per revision and commits whole sets. One thread only (the
 * OSC processing loop).
 */
class MapAssembler {
    var speakersRev: Int? = null; private set
    var shapesRev: Int? = null; private set
    var geometry = MapGeometry(); private set

    // Speakers being assembled
    private var pendingSpeakersRev: Int? = null
    private var pendingSpeakersStartMs = 0L
    private var pendingChunkCount = 0
    private var pendingTotal = 0
    private val pendingChunks = HashMap<Int, List<MapSpeaker>>()

    // Shapes being assembled (parts may arrive before their manifest)
    private var pendingShapesRev: Int? = null
    private var pendingShapesStartMs = 0L
    private var pendingShapeCount: Int? = null
    private val pendingParts = HashMap<Int, HashMap<Int, MapDecoded.ShapePart>>()

    fun reset() {
        speakersRev = null
        shapesRev = null
        geometry = MapGeometry()
        clearPendingSpeakers()
        clearPendingShapes()
    }

    /** Feed a decoded chunk / manifest / part. True when a whole set was just committed. */
    fun accept(d: MapDecoded, nowMs: Long): Boolean = when (d) {
        is MapDecoded.SpeakersChunk -> onSpeakers(d, nowMs)
        is MapDecoded.ShapesManifest -> onManifest(d, nowMs)
        is MapDecoded.ShapePart -> onShapePart(d, nowMs)
        is MapDecoded.Rev -> false
    }

    /**
     * Which sets to ask for, given the revisions the desktop just announced: those we
     * do not hold, unless a set of that very revision is already arriving (started less
     * than [graceMs] ago).
     */
    fun requestMask(announcedSpeakers: Int, announcedShapes: Int, nowMs: Long, graceMs: Long = 1000L): Int {
        var mask = 0
        if (announcedSpeakers != speakersRev &&
            !(pendingSpeakersRev == announcedSpeakers && nowMs - pendingSpeakersStartMs < graceMs))
            mask = mask or MapProtocol.MASK_SPEAKERS
        if (announcedShapes != shapesRev &&
            !(pendingShapesRev == announcedShapes && nowMs - pendingShapesStartMs < graceMs))
            mask = mask or MapProtocol.MASK_SHAPES
        return mask
    }

    private fun onSpeakers(c: MapDecoded.SpeakersChunk, nowMs: Long): Boolean {
        if (c.rev == speakersRev) return false   // a repeat of what we hold
        if (c.rev != pendingSpeakersRev || c.chunkCount != pendingChunkCount || c.total != pendingTotal) {
            clearPendingSpeakers()
            pendingSpeakersRev = c.rev
            pendingSpeakersStartMs = nowMs
            pendingChunkCount = c.chunkCount
            pendingTotal = c.total
        }
        pendingChunks[c.chunk] = c.speakers
        if (pendingChunks.size < pendingChunkCount) return false
        val all = (0 until pendingChunkCount).flatMap { pendingChunks[it] ?: return false }
        if (all.size != pendingTotal) {
            clearPendingSpeakers()
            return false
        }
        speakersRev = c.rev
        geometry = geometry.copy(speakers = all)
        clearPendingSpeakers()
        return true
    }

    private fun startShapes(rev: Int, nowMs: Long) {
        if (rev != pendingShapesRev) {
            clearPendingShapes()
            pendingShapesRev = rev
            pendingShapesStartMs = nowMs
        }
    }

    private fun onManifest(m: MapDecoded.ShapesManifest, nowMs: Long): Boolean {
        if (m.rev == shapesRev) return false
        startShapes(m.rev, nowMs)
        pendingShapeCount = m.count
        return tryCommitShapes()
    }

    private fun onShapePart(p: MapDecoded.ShapePart, nowMs: Long): Boolean {
        if (p.rev == shapesRev) return false
        startShapes(p.rev, nowMs)
        pendingParts.getOrPut(p.index) { HashMap() }[p.part] = p
        return tryCommitShapes()
    }

    private fun tryCommitShapes(): Boolean {
        val count = pendingShapeCount ?: return false
        val rev = pendingShapesRev ?: return false
        val shapes = ArrayList<MapShape>(count)
        for (index in 0 until count) {
            val parts = pendingParts[index] ?: return false
            val first = parts[0] ?: return false
            if (parts.size < first.partCount) return false
            var size = 0
            for (k in 0 until first.partCount) size += (parts[k] ?: return false).points.size
            val points = FloatArray(size)
            var at = 0
            for (k in 0 until first.partCount) {
                val src = parts[k]!!.points
                src.copyInto(points, at)
                at += src.size
            }
            if (points.size < 4) return false
            shapes += MapShape(first.closed, first.argb, first.dashed, first.lineWidth,
                first.labelX, first.labelY, first.label, points)
        }
        shapesRev = rev
        geometry = geometry.copy(shapes = shapes)
        clearPendingShapes()
        return true
    }

    private fun clearPendingSpeakers() {
        pendingSpeakersRev = null
        pendingChunkCount = 0
        pendingTotal = 0
        pendingChunks.clear()
    }

    private fun clearPendingShapes() {
        pendingShapesRev = null
        pendingShapeCount = null
        pendingParts.clear()
    }
}
