package com.wfsdiy.wfs_control_2

import kotlin.math.*

/**
 * Coordinate conversion utilities for WFS position display/input.
 * Kotlin port of JUCE CoordinateConverter.h (WFSCoordinates namespace).
 *
 * Supports three coordinate systems:
 * - Cartesian (X, Y, Z) in meters - internal storage format
 * - Cylindrical (r, theta, Z) - radius in meters, azimuth in degrees, height in meters
 * - Spherical (r, theta, phi) - radius in meters, azimuth and elevation in degrees
 *
 * Angle Conventions (WFS Stage Coordinate System):
 * - Azimuth (theta): 0° = upstage (+Y), 90° = stage right (+X), -90° = stage left (-X), ±180° = audience (-Y)
 * - Elevation (phi): 0° = horizontal, 90° = up (+Z), -90° = down (-Z)
 */
object CoordinateConverter {

    const val MODE_CARTESIAN = 0
    const val MODE_CYLINDRICAL = 1
    const val MODE_SPHERICAL = 2

    private const val EPSILON = 0.0001f

    /** Normalize angle to -180 to 180 degrees range (matching JUCE normalizeAngle) */
    fun normalizeAngle(degrees: Float): Float {
        var d = degrees
        while (d > 180f) d -= 360f
        while (d <= -180f) d += 360f
        return d
    }

    /** Clamp elevation to -90 to 90 degrees range */
    private fun clampElevation(degrees: Float): Float = degrees.coerceIn(-90f, 90f)

    // =========================================================================
    // Cartesian <-> Cylindrical
    // =========================================================================

    /**
     * Cartesian -> Cylindrical.
     * theta = atan2(x, y): 0° = +Y (upstage), 90° = +X (stage right)
     * @return Triple(r, theta, z)
     */
    fun cartesianToCylindrical(x: Float, y: Float, z: Float): Triple<Float, Float, Float> {
        val r = sqrt(x * x + y * y)
        val theta = if (r > EPSILON) {
            Math.toDegrees(atan2(x.toDouble(), y.toDouble())).toFloat()
        } else {
            0f
        }
        return Triple(r, normalizeAngle(theta), z)
    }

    /**
     * Cylindrical -> Cartesian.
     * x = r * sin(theta), y = r * cos(theta)
     * @return Triple(x, y, z)
     */
    fun cylindricalToCartesian(r: Float, theta: Float, z: Float): Triple<Float, Float, Float> {
        val thetaRad = Math.toRadians(theta.toDouble())
        val x = r * sin(thetaRad).toFloat()
        val y = r * cos(thetaRad).toFloat()
        return Triple(x, y, z)
    }

    // =========================================================================
    // Cartesian <-> Spherical
    // =========================================================================

    /**
     * Cartesian -> Spherical.
     * @return Triple(r, theta, phi)
     */
    fun cartesianToSpherical(x: Float, y: Float, z: Float): Triple<Float, Float, Float> {
        val r = sqrt(x * x + y * y + z * z)
        var theta = 0f
        var phi = 0f
        if (r > EPSILON) {
            phi = Math.toDegrees(asin((z / r).toDouble().coerceIn(-1.0, 1.0))).toFloat()
            val rHoriz = sqrt(x * x + y * y)
            if (rHoriz > EPSILON) {
                theta = Math.toDegrees(atan2(x.toDouble(), y.toDouble())).toFloat()
            }
        }
        return Triple(r, normalizeAngle(theta), clampElevation(phi))
    }

    /**
     * Spherical -> Cartesian.
     * @return Triple(x, y, z)
     */
    fun sphericalToCartesian(r: Float, theta: Float, phi: Float): Triple<Float, Float, Float> {
        val thetaRad = Math.toRadians(theta.toDouble())
        val phiRad = Math.toRadians(phi.toDouble())
        val rHoriz = r * cos(phiRad).toFloat()
        val x = rHoriz * sin(thetaRad).toFloat()
        val y = rHoriz * cos(thetaRad).toFloat()
        val z = r * sin(phiRad).toFloat()
        return Triple(x, y, z)
    }

    // =========================================================================
    // Display Helpers
    // =========================================================================

    /**
     * Convert Cartesian X/Y/Z to display values based on coordinate mode.
     * @return Triple(v1, v2, v3) - meaning depends on mode:
     *   Cartesian:   (X, Y, Z) in meters
     *   Cylindrical: (radius, azimuth, height) in (m, °, m)
     *   Spherical:   (radius, azimuth, elevation) in (m, °, °)
     */
    fun cartesianToDisplay(mode: Int, x: Float, y: Float, z: Float): Triple<Float, Float, Float> {
        return when (mode) {
            MODE_CYLINDRICAL -> cartesianToCylindrical(x, y, z)
            MODE_SPHERICAL -> cartesianToSpherical(x, y, z)
            else -> Triple(x, y, z)
        }
    }

    /**
     * Convert display values back to Cartesian X/Y/Z based on coordinate mode.
     * @return Triple(x, y, z)
     */
    fun displayToCartesian(mode: Int, v1: Float, v2: Float, v3: Float): Triple<Float, Float, Float> {
        return when (mode) {
            MODE_CYLINDRICAL -> cylindricalToCartesian(abs(v1), normalizeAngle(v2), v3)
            MODE_SPHERICAL -> sphericalToCartesian(abs(v1), normalizeAngle(v2), clampElevation(v3))
            else -> Triple(v1, v2, v3)
        }
    }
}
