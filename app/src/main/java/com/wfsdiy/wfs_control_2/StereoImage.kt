package com.wfsdiy.wfs_control_2

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * A stereo input's image: where its two legs sit around the position the user placed,
 * and how a second-finger pinch/twist turns into a width and an axis offset.
 *
 * Ported from the desktop: the legs from Helpers/StereoImageGeometry.h, the gesture law
 * from the StereoImage case of MapTab.h's applySecondaryTouch. The two apps must agree.
 * A spread bar that is not the image the renderer produces misleads more than no bar at
 * all, and a pinch has to land on the value the desktop's own gesture would have set.
 * Pure JVM (no Android classes), so the tests run exactly what the map uses.
 *
 * Stage metres, origin-relative, +X stage right and +Y upstage (screen up). Angles in
 * degrees, positive counter-clockwise seen from above, like inputRotation. The image is
 * planar: nothing here touches Z.
 */
object StereoImage {

    /** inputStereoWidth's maximum, in metres (the FULL left-to-right distance). */
    const val WIDTH_MAX = 50f

    /** Anchor distance from the origin below which there is no bearing to follow. */
    const val AXIS_MIN_RADIUS = 0.01f

    /**
     * The desktop draws no bar whose legs are 1 cm or less apart: its two end dots would
     * overlap into a blob that reads as a defect rather than as a collapsed image.
     */
    const val MIN_DRAW_SPAN = 0.01f

    /** A unit axis, or a point in stage metres. */
    data class Vec(val x: Float, val y: Float)

    /**
     * House left/right: the locked axis, and the one a pair that has never had a bearing
     * spreads along (the desktop's freshly built follower).
     */
    val HOUSE_AXIS = Vec(1f, 0f)

    // Float constants as JUCE builds them, so degree/radian conversions round alike.
    private val PI_F = PI.toFloat()
    private val TWO_PI_F = 2f * PI_F
    private val DEG_TO_RAD = PI_F / 180f
    private val RAD_TO_DEG = 180f / PI_F

    /**
     * The spread axis before the offset, for an anchor at ([ax], [ay]).
     *
     * Locked (inputStereoAxisLock = 1): [HOUSE_AXIS] wherever the anchor is, so the offset
     * reads as an absolute bearing. Otherwise perpendicular to the origin-to-anchor
     * bearing, oriented so the right leg lands audience-right for an upstage anchor. A
     * flipped channel mirrors its anchor and so its axis: no sign rule anywhere.
     *
     * Null at the origin, where no bearing exists. The caller keeps the axis it last drew,
     * as the desktop's follower holds its own. That follower also caps the turn rate at
     * 360 °/s; the cap is not ported, because this map redraws only when something
     * changes, so an axis still gliding would freeze part-turned once the source stopped.
     * Snapping to the tangent equals the desktop at rest and differs only while a source
     * moves within about 16 cm of the origin.
     */
    fun baseAxis(ax: Float, ay: Float, locked: Boolean): Vec? {
        if (locked) return HOUSE_AXIS
        val r = hypot(ax, ay)
        // A NaN anchor has no bearing either; holding keeps it out of the caller's axis.
        if (r.isNaN() || r < AXIS_MIN_RADIUS) return null
        return Vec(ay / r, -ax / r)
    }

    /**
     * [axis] turned [offsetDegrees] counter-clockwise; ±180 swaps the legs.
     *
     * Zero hands the axis back untouched instead of trusting cos/sin to return exactly
     * 1 and 0, as the desktop does: the automatic axis stays bit-exact when no offset is
     * dialled. The offset must therefore arrive as the desktop stores it, a whole number
     * of degrees; the parameter formula's float round trip misses 0 by a few ULPs.
     */
    fun rotate(axis: Vec, offsetDegrees: Float): Vec {
        if (offsetDegrees == 0f) return axis
        val a = offsetDegrees * DEG_TO_RAD
        val c = cos(a)
        val s = sin(a)
        return Vec(axis.x * c - axis.y * s, axis.x * s + axis.y * c)
    }

    /**
     * The (left, right) legs of a pair anchored at ([ax], [ay]), in stage metres. [width]
     * is the full left-to-right distance, clamped to the parameter's range, so each leg
     * travels half of it: the left one (azimuth -1) against [axis], the right one
     * (azimuth +1) along it. At (0, 8), 4 m apart, they sit at (-2, 8) and (2, 8).
     */
    fun legs(ax: Float, ay: Float, width: Float, axis: Vec): Pair<Vec, Vec> {
        val h = width.coerceIn(0f, WIDTH_MAX) * 0.5f
        return Vec(ax - h * axis.x, ay - h * axis.y) to Vec(ax + h * axis.x, ay + h * axis.y)
    }

    /**
     * A bearing wrapped into the (-179, 180] range inputStereoAxisOffset accepts
     * (WFSParameterDefaults::wrapAxisDegrees). In-range values pass through. -180 folds
     * onto 180, the same bearing: the parameter would clamp it back to -179, so a twist
     * down through the boundary would stick there instead of crossing into +180.
     */
    fun wrapAxisDegrees(degrees: Int): Int {
        val wrapped = if (degrees in -180..180) degrees
                      else ((degrees + 180) % 360 + 360) % 360 - 180
        return if (wrapped == -180) 180 else wrapped
    }

    /** An angle in radians folded into [-π, π], as the desktop folds a twist's turn. */
    fun foldPi(radians: Float): Float {
        // No twist rather than a loop that never ends: infinity minus 2π is infinity.
        if (!radians.isFinite()) return 0f
        var a = radians
        while (a > PI_F) a -= TWO_PI_F
        while (a < -PI_F) a += TWO_PI_F
        return a
    }

    /**
     * The width a pinch gives when the finger's distance from the marker goes from
     * [startDistancePx] to [distancePx], starting at [startWidth] metres. The desktop's
     * law, the same as its height pinch: a ratio from a (near) zero width would stay
     * stuck at zero, so below 0.1 m the change in distance is added, one metre per
     * [pxPerMetre] of travel (the desktop hard-codes 50 px). Otherwise the width scales
     * by the distance ratio, left alone while the start distance is within
     * [minDistancePx] of the marker, where that ratio is noise. Clamped to 0..50 m.
     */
    fun pinchWidth(
        startWidth: Float, startDistancePx: Float, distancePx: Float,
        pxPerMetre: Float, minDistancePx: Float
    ): Float {
        val width = if (startWidth < 0.1f) {
            if (pxPerMetre > 0f) startWidth + (distancePx - startDistancePx) / pxPerMetre else startWidth
        } else {
            startWidth * (if (startDistancePx > minDistancePx) distancePx / startDistancePx else 1f)
        }
        return width.coerceIn(0f, WIDTH_MAX)
    }

    /**
     * The axis offset a twist gives: [startAxis] plus the finger's turn around the marker.
     * [screenDeltaRad] is measured on screen, where Y points down, so a counter-clockwise
     * twist comes in negative and raises the axis, the same sign as inputRotation.
     * Math.rint rounds half to even, as juce::roundToInt does, so a half degree lands
     * where the desktop's gesture lands it; the sum is then wrapped into range.
     */
    fun twistAxis(startAxis: Int, screenDeltaRad: Float): Int {
        val degrees = -(foldPi(screenDeltaRad) * RAD_TO_DEG)
        return wrapAxisDegrees(startAxis + Math.rint(degrees.toDouble()).toInt())
    }
}
