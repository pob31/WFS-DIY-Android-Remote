package com.wfsdiy.wfs_control_2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CoordinateConverter.normalizeAngle against the desktop's WFSCoordinates::normalizeAngle
 * (CoordinateConverter.h). The subtract-360 loop both used to have never ended above about
 * 8.6e9 degrees, so a ten-digit azimuth typed in cylindrical or spherical mode froze the app.
 */
class CoordinateConverterTest {

    private val eps = 1e-4f

    @Test
    fun aHugeAngleWrapsInsteadOfHanging() {
        for (huge in floatArrayOf(1e10f, -1e10f, 9_999_999_999f, 3.4e38f)) {
            val a = CoordinateConverter.normalizeAngle(huge)
            assertTrue("$huge -> $a", a > -180f && a <= 180f)
        }
    }

    @Test
    fun ordinaryWrapsLandWhereTheLoopPutThem() {
        assertEquals(180f, CoordinateConverter.normalizeAngle(540f), eps)
        assertEquals(180f, CoordinateConverter.normalizeAngle(-180f), eps)
        assertEquals(180f, CoordinateConverter.normalizeAngle(180f), eps)
        assertEquals(-170f, CoordinateConverter.normalizeAngle(190f), eps)
        assertEquals(170f, CoordinateConverter.normalizeAngle(-190f), eps)
        assertEquals(0f, CoordinateConverter.normalizeAngle(-3600f), eps)
        assertEquals(45f, CoordinateConverter.normalizeAngle(45f), eps)
    }

    @Test
    fun anAngleThatIsNotFiniteReadsAsZero() {
        assertEquals(0f, CoordinateConverter.normalizeAngle(Float.POSITIVE_INFINITY), 0f)
        assertEquals(0f, CoordinateConverter.normalizeAngle(Float.NEGATIVE_INFINITY), 0f)
        assertEquals(0f, CoordinateConverter.normalizeAngle(Float.NaN), 0f)
    }
}
