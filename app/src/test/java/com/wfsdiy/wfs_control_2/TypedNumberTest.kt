package com.wfsdiy.wfs_control_2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The one reader behind every typed value box, and the two laws the review found
 * out of step with the desktop (the Floor Reflections frequencies, the tracking IDs).
 */
class TypedNumberTest {

    private val eps = 1e-5f

    @Test
    fun aCommaIsTheDecimalPoint() {
        assertEquals(-2.5f, TypedNumber.parse("-2,5")!!, eps)
        assertEquals(0.25f, TypedNumber.parse(",25")!!, eps)
    }

    @Test
    fun unitsSpacesAndTheMinusSignAreRead() {
        assertEquals(-12.5f, TypedNumber.parse("  -12.5 dB ")!!, eps)
        assertEquals(3f, TypedNumber.parse("−3")!!.let { -it }, eps)
        assertEquals(250f, TypedNumber.parse("250Hz")!!, eps)
        assertEquals(45f, TypedNumber.parse("45°")!!, eps)
        assertEquals(1500f, TypedNumber.parse("1.5e3")!!, eps)
    }

    @Test
    fun nothingToReadIsNull() {
        for (text in listOf("", "   ", "-", "abc", "dB", "NaN", "Infinity", "-Infinity", "1e40", "1,234.5"))
            assertNull("\"$text\"", TypedNumber.parse(text))
    }

    @Test
    fun typingKeepsACommaAsThePoint() {
        assertEquals("-2.5", TypedNumber.filterTyping("-2,5", allowDecimal = true))
        assertEquals("-25", TypedNumber.filterTyping("-2,5", allowDecimal = false))
        assertEquals("-3.0", TypedNumber.filterTyping("−3.0 m", allowDecimal = true))
    }

    @Test
    fun floorReflectionFrequenciesSpanTwentyToTwentyThousandHertz() {
        for (name in listOf("FRlowCutFreq", "FRhighShelfFreq")) {
            val definition = InputParameterDefinitions.parametersByVariableName[name]!!
            assertEquals("$name at 0", 20f, InputParameterDefinitions.applyFormula(definition, 0f), 1e-2f)
            assertEquals("$name at 1", 20000f, InputParameterDefinitions.applyFormula(definition, 1f), 1f)
            // 2 kHz two thirds along, as on the desktop's slider (20 * 10^(3x)).
            assertEquals("$name 2 kHz", 2f / 3f, InputParameterDefinitions.reverseFormula(definition, 2000f), 1e-4f)
        }
    }

    @Test
    fun trackingIdsStopAtTheDesktopsThirtyTwo() {
        val definition = InputParameterDefinitions.parametersByVariableName["trackingID"]!!
        assertEquals(32f, definition.maxValue, 0f)
        assertEquals((1..32).map { it.toString() }, definition.enumValues)
    }
}
