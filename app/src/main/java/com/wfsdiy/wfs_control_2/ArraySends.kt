package com.wfsdiy.wfs_control_2

import java.util.Locale

/**
 * An input's ten array attenuations (the desktop's inputArrayAtten1..10): how much of
 * the input each speaker array plays, 0 dB down to -60 dB, one level per array.
 *
 * Pure logic for the Input Parameters tab's Array Attenuation section, kept free of
 * Android and Compose so it runs in JVM tests.
 */
object ArraySends {
    const val ARRAY_COUNT = 10
    const val MIN_DB = -60f
    const val MAX_DB = 0f

    /**
     * The desktop's dial curve, dB = 20*log10(0.001 + x^2 * 0.999) (InputsTab.h), as a
     * formula string InputParameterDefinitions already maps both ways.
     */
    const val FORMULA = "20*log10(pow(10,-60./20.)+((1-pow(10,-60./20.))*pow(x,2)))"

    fun variableName(array: Int) = "arrayAtten$array"

    fun oscPath(array: Int) = "/remoteInput/arrayAtten$array"

    /**
     * The arrays no output belongs to, from /remote/vis/outputArrays (one array number
     * per output, 0 = Single). The desktop dims their dials: a level for an array with
     * no speakers does nothing. Nothing is dimmed while the assignment is unknown (an
     * empty list, before the first dump or after a disconnect), so a missing message
     * never greys out a rig.
     */
    fun dimmedArrays(outputArrays: IntArray): Set<Int> {
        if (outputArrays.isEmpty()) return emptySet()
        val used = outputArrays.filter { it in 1..ARRAY_COUNT }.toSet()
        return (1..ARRAY_COUNT).filterNot { it in used }.toSet()
    }

    /**
     * A typed-in level: the number with or without its unit, a comma accepted as the
     * decimal point, clamped into -60..0 dB. Null when there is no number to read.
     */
    fun parseDbCommit(text: String): Float? {
        var cleaned = text.trim().replace('−', '-').replace(',', '.')
        if (cleaned.endsWith("db", ignoreCase = true))
            cleaned = cleaned.dropLast(2).trim()
        val value = cleaned.toFloatOrNull() ?: return null
        if (!value.isFinite()) return null
        return value.coerceIn(MIN_DB, MAX_DB)
    }

    /** One decimal, like the desktop's value labels; never "-0.0". */
    fun formatDb(db: Float): String {
        val text = String.format(Locale.US, "%.1f", db)
        return if (text == "-0.0") "0.0" else text
    }
}
