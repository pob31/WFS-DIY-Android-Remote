package com.wfsdiy.wfs_control_2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The map's stereo geometry against the desktop's own vectors: its stereo-image self-test
 * (MainComponent.cpp) for the legs, WFSParameterDefaults::wrapAxisDegrees for the wrap,
 * and MapTab.h's StereoImage gesture case for the pinch and the twist.
 */
class StereoImageTest {

    private val eps = 1e-5f

    private fun assertVec(x: Float, y: Float, v: StereoImage.Vec?) {
        requireNotNull(v) { "expected ($x, $y), got null" }
        assertEquals("x of $v", x, v.x, eps)
        assertEquals("y of $v", y, v.y, eps)
    }

    /** The draw pass's chain: base axis (house axis when there is none), offset, legs. */
    private fun legsAt(ax: Float, ay: Float, width: Float, offsetDeg: Int, locked: Boolean = false) =
        StereoImage.legs(ax, ay, width,
            StereoImage.rotate(StereoImage.baseAxis(ax, ay, locked) ?: StereoImage.HOUSE_AXIS, offsetDeg.toFloat()))

    private fun rad(degrees: Float) = (degrees * PI / 180.0).toFloat()

    // --- Legs ---

    @Test
    fun fourMetresUpstageSpreadAlongX() {
        val (l, r) = legsAt(0f, 8f, 4f, 0)
        assertVec(-2f, 8f, l)
        assertVec(2f, 8f, r)
    }

    @Test
    fun anOffsetOf90TurnsThePairCounterClockwise() {
        val (l, r) = legsAt(0f, 8f, 4f, 90)
        assertVec(0f, 6f, l)
        assertVec(0f, 10f, r)
    }

    @Test
    fun anOffsetOf180SwapsTheLegs() {
        val (l, r) = legsAt(0f, 8f, 4f, 180)
        assertVec(2f, 8f, l)
        assertVec(-2f, 8f, r)
    }

    @Test
    fun theLegsStayTheDialledWidthApartAtEveryBearing() {
        for (deg in 0 until 360 step 15) {
            val a = rad(deg.toFloat())
            val (l, r) = legsAt(8f * cos(a), 8f * sin(a), 4f, 0)
            assertEquals("span at $deg°", 4f, hypot(r.x - l.x, r.y - l.y), 1e-4f)
        }
    }

    @Test
    fun anXFlipMirrorsTheImage() {
        // The mirrored anchor carries its axis along, so the right leg of one image is the
        // mirror of the left leg of the other, with no sign rule anywhere.
        val (l, r) = legsAt(3f, 4f, 2f, 0)
        val (ml, mr) = legsAt(-3f, 4f, 2f, 0)
        assertVec(-r.x, r.y, ml)
        assertVec(-l.x, l.y, mr)
    }

    @Test
    fun theWidthIsClampedToTheParameterRange() {
        val (l, r) = legsAt(0f, 8f, 80f, 0)
        assertVec(-25f, 8f, l)
        assertVec(25f, 8f, r)
        val (zl, zr) = legsAt(0f, 8f, -1f, 0)
        assertVec(0f, 8f, zl)
        assertVec(0f, 8f, zr)
    }

    // --- Axis ---

    @Test
    fun aLockedAxisIsHouseLeftRightAtEveryAnchor() {
        for ((x, y) in listOf(0f to 8f, 8f to 0f, -3f to -7f, 0.05f to 0.05f, 0f to 0f))
            assertVec(1f, 0f, StereoImage.baseAxis(x, y, locked = true))
        // Locked, the offset is an absolute bearing off house left/right.
        val (l, r) = legsAt(8f, 0f, 4f, 90, locked = true)
        assertVec(8f, -2f, l)
        assertVec(8f, 2f, r)
    }

    @Test
    fun theFreeAxisIsTangentialToTheBearing() {
        assertVec(1f, 0f, StereoImage.baseAxis(0f, 8f, locked = false))
        assertVec(0f, -1f, StereoImage.baseAxis(8f, 0f, locked = false))
        assertVec(0.8f, -0.6f, StereoImage.baseAxis(3f, 4f, locked = false))
    }

    @Test
    fun thereIsNoBearingAtTheOrigin() {
        assertNull(StereoImage.baseAxis(0.005f, 0f, locked = false))
        assertNull(StereoImage.baseAxis(0f, 0f, locked = false))
        assertNull(StereoImage.baseAxis(Float.NaN, 1f, locked = false))
        // Just outside the radius the bearing is back.
        assertVec(0f, -1f, StereoImage.baseAxis(0.02f, 0f, locked = false))
    }

    @Test
    fun aZeroOffsetHandsTheAxisBackUntouched() {
        val axis = StereoImage.Vec(0.6f, 0.8f)
        assertSame(axis, StereoImage.rotate(axis, 0f))
    }

    @Test
    fun twoOffsetsInTurnEqualTheirSum() {
        val seed = StereoImage.Vec(0.6f, 0.8f)
        for (t in listOf(-179f, -45f, 30f, 137f, 180f))
            for (u in listOf(-90f, 45f, 90f)) {
                val once = StereoImage.rotate(seed, t + u)
                val twice = StereoImage.rotate(StereoImage.rotate(seed, t), u)
                assertVec(once.x, once.y, twice)
            }
    }

    // --- wrapAxisDegrees and foldPi ---

    @Test
    fun wrapAxisDegreesMatchesTheDesktop() {
        assertEquals(-179, StereoImage.wrapAxisDegrees(181))
        assertEquals(180, StereoImage.wrapAxisDegrees(-180))
        assertEquals(180, StereoImage.wrapAxisDegrees(540))
        assertEquals(179, StereoImage.wrapAxisDegrees(-181))
        assertEquals(-1, StereoImage.wrapAxisDegrees(359))
        assertEquals(0, StereoImage.wrapAxisDegrees(360))
        assertEquals(180, StereoImage.wrapAxisDegrees(180))
        assertEquals(-179, StereoImage.wrapAxisDegrees(-179))
        assertEquals(0, StereoImage.wrapAxisDegrees(0))
    }

    @Test
    fun foldPiFoldsIntoPlusMinusPi() {
        assertEquals(-PI.toFloat() / 2f, StereoImage.foldPi(3f * PI.toFloat() / 2f), eps)
        assertEquals(PI.toFloat() / 2f, StereoImage.foldPi(-3f * PI.toFloat() / 2f), eps)
        assertEquals(0.5f, StereoImage.foldPi(0.5f), 0f)
        assertEquals(0f, StereoImage.foldPi(Float.NaN), 0f)
        assertEquals(0f, StereoImage.foldPi(Float.POSITIVE_INFINITY), 0f)
    }

    // --- Twist ---

    @Test
    fun aCounterClockwiseTwistRaisesTheAxis() {
        // Screen Y points down, so a counter-clockwise quarter turn comes in as -π/2.
        assertEquals(90, StereoImage.twistAxis(0, -PI.toFloat() / 2f))
        assertEquals(-90, StereoImage.twistAxis(0, PI.toFloat() / 2f))
    }

    @Test
    fun aTwistWrapsThroughTheBoundary() {
        assertEquals(-170, StereoImage.twistAxis(170, rad(-20f)))
        // One degree counter-clockwise from 180 crosses to -179 instead of sticking.
        assertEquals(-179, StereoImage.twistAxis(180, rad(-1f)))
        assertEquals(180, StereoImage.twistAxis(-179, rad(1f)))
    }

    @Test
    fun aTwistPastHalfATurnIsFoldedFirst() {
        // 350° clockwise on screen is 10° counter-clockwise.
        assertEquals(10, StereoImage.twistAxis(0, rad(350f)))
    }

    // --- Pinch ---

    @Test
    fun aPinchScalesTheWidthByTheDistanceRatio() {
        assertEquals(8f, StereoImage.pinchWidth(4f, 100f, 200f, 100f, 20f), eps)
        assertEquals(2f, StereoImage.pinchWidth(4f, 100f, 50f, 100f, 20f), eps)
        // From 0.1 m up it is the ratio law.
        assertEquals(0.2f, StereoImage.pinchWidth(0.1f, 100f, 200f, 100f, 20f), eps)
    }

    @Test
    fun aPinchFromNearZeroAddsTheDistanceChange() {
        assertEquals(0.55f, StereoImage.pinchWidth(0.05f, 100f, 150f, 100f, 20f), eps)
        assertEquals(1f, StereoImage.pinchWidth(0f, 100f, 200f, 100f, 20f), eps)
    }

    @Test
    fun aStartTooCloseToTheMarkerLeavesTheWidthAlone() {
        assertEquals(4f, StereoImage.pinchWidth(4f, 5f, 200f, 100f, 20f), 0f)
    }

    @Test
    fun aPinchIsClampedToTheParameterRange() {
        assertEquals(50f, StereoImage.pinchWidth(40f, 100f, 200f, 100f, 20f), 0f)
        assertEquals(0f, StereoImage.pinchWidth(0.05f, 100f, 0f, 100f, 20f), 0f)
    }
}
