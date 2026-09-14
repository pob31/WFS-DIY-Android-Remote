package com.wfsdiy.wfs_control_2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The map's second-finger orientation against the desktop's own gesture (MapTab.h,
 * applySecondaryTouch, InputRotation case): the same wrap into -179..180 and the same
 * truncation, so the desktop, which rejects anything outside that range, takes every value.
 */
class SecondFingerRotationTest {

    /** MapTab.h's InputRotation case, transcribed with its while loops (degrees, not radians). */
    private fun desktop(startRotation: Float, screenDeltaDegrees: Float): Float {
        var delta = screenDeltaDegrees
        while (delta > 180f) delta -= 360f
        while (delta < -180f) delta += 360f
        var rotation = startRotation + (-delta)
        while (rotation > 180f) rotation -= 360f
        while (rotation < -179f) rotation += 360f
        return rotation.toInt().toFloat()
    }

    @Test
    fun aFingerHeldStillKeepsTheStart() {
        assertEquals(37f, secondFingerRotation(37f, 0f), 0f)
        assertEquals(180f, secondFingerRotation(180f, 0f), 0f)
        assertEquals(-179f, secondFingerRotation(-179f, 0f), 0f)
    }

    @Test
    fun aCounterClockwiseTwistRaisesIt() {
        // Screen Y points down, so a counter-clockwise quarter turn comes in as -90.
        assertEquals(90f, secondFingerRotation(0f, -90f), 0f)
        assertEquals(-90f, secondFingerRotation(0f, 90f), 0f)
    }

    @Test
    fun aTwistPastTheBoundaryWrapsInsteadOfLeavingTheRange() {
        assertEquals(-170f, secondFingerRotation(170f, -20f), 0f)
        assertEquals(170f, secondFingerRotation(-170f, 20f), 0f)
        assertEquals(-179f, secondFingerRotation(180f, -1f), 0f)
        // -180 is the same bearing as 180, which the range holds.
        assertEquals(180f, secondFingerRotation(-179f, 1f), 0f)
    }

    @Test
    fun aTurnOfMoreThanHalfARevolutionLandsOnTheSameBearing() {
        // 350 degrees clockwise on screen is 10 counter-clockwise.
        assertEquals(10f, secondFingerRotation(0f, 350f), 0f)
    }

    @Test
    fun fractionsAreTruncatedTowardZeroAsTheDesktopCastsThem() {
        assertEquals(0f, secondFingerRotation(0f, -0.6f), 0f)
        assertEquals(0f, secondFingerRotation(0f, 0.6f), 0f)
        assertEquals(44f, secondFingerRotation(45f, 0.5f), 0f)
        // Just under -179 the wrap gives 180.5; the truncation brings it back onto 180.
        assertEquals(180f, secondFingerRotation(-179f, 0.5f), 0f)
    }

    @Test
    fun everyValueMatchesTheDesktopAndStaysInRange() {
        var start = -179
        while (start <= 180) {
            var delta = -360f
            while (delta <= 360f) {
                val tablet = secondFingerRotation(start.toFloat(), delta)
                assertEquals("start $start, twist $delta", desktop(start.toFloat(), delta), tablet, 0f)
                assertTrue("start $start, twist $delta -> $tablet", tablet >= -179f && tablet <= 180f)
                delta += 0.25f
            }
            start += 7
        }
    }

    @Test
    fun aNonFiniteSumPassesThrough() {
        // Sent as it was before, and rejected by the desktop's non-finite check as before;
        // no loop to spin.
        assertTrue(secondFingerRotation(Float.NaN, 0f).isNaN())
        assertEquals(Float.POSITIVE_INFINITY, secondFingerRotation(0f, Float.NEGATIVE_INFINITY), 0f)
    }
}
