package com.wfsdiy.wfs_control_2

/**
 * The one reader for a number typed into a value box.
 *
 * Each box used to parse with toFloatOrNull on its own. A comma made a slider box ignore
 * the entry while still showing it, the position boxes dropped the comma as it was typed
 * ("-2,5" became -25 m), and "NaN" typed on a hardware keyboard reached roundToInt and
 * crashed the app (desktop re-audit 2026-09-29, F8, and its tablet review). So: trimmed,
 * "−" and "," read as "-" and ".", a trailing unit (dB, m, Hz, %, °...) ignored, and a
 * number only when it is finite.
 */
object TypedNumber {

    fun parse(text: String): Float? {
        val cleaned = text.trim()
            .replace('−', '-')
            .replace(',', '.')
            .trimEnd { !(it.isDigit() || it == '.') }
            .trim()
        if (cleaned.isEmpty()) return null
        return cleaned.toFloatOrNull()?.takeIf { it.isFinite() }
    }

    /** What a number box may hold while it is typed: a comma becomes a point instead of vanishing. */
    fun filterTyping(text: String, allowDecimal: Boolean): String =
        text.map {
            when (it) {
                ',' -> '.'
                '−' -> '-'
                else -> it
            }
        }.filter { it.isDigit() || it == '-' || (allowDecimal && it == '.') }
            .joinToString("")
}
