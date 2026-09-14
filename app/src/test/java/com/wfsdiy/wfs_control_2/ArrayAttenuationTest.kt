package com.wfsdiy.wfs_control_2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * The Array Attenuation section against the desktop: ten definitions on the desktop's
 * addresses and range, the desktop's dial curve (InputsTab.h: linear = 0.001 + v^2 * 0.999,
 * dB = 20*log10(linear)), and the section's pure helpers.
 */
class ArrayAttenuationTest {

    private fun def(array: Int) = requireNotNull(InputParameterDefinitions.parametersByVariableName["arrayAtten$array"]) {
        "no definition for arrayAtten$array"
    }

    // The desktop's two directions (InputsTab.h onValueChanged / loadChannelParameters)
    private fun desktopDb(dial: Float) = 20f * log10(0.001f + dial * dial * (1f - 0.001f))
    private fun desktopDial(db: Float) = sqrt((10f.pow(db / 20f) - 0.001f) / (1f - 0.001f))

    @Test
    fun tenDefinitionsOnTheDesktopAddresses() {
        for (n in 1..10) {
            val d = def(n)
            assertEquals("/remoteInput/arrayAtten$n", d.oscPath)
            assertEquals(ParameterType.FLOAT, d.dataType)
            assertEquals(-60f, d.minValue, 0f)
            assertEquals(0f, d.maxValue, 0f)
            assertEquals("dB", d.unit)
            assertTrue(d.isIncoming && d.isOutgoing)
        }
        assertNull(InputParameterDefinitions.parametersByVariableName["arrayAtten0"])
        assertNull(InputParameterDefinitions.parametersByVariableName["arrayAtten11"])

        // The inbound path finds a definition by its address: a duplicate would shadow one
        val paths = InputParameterDefinitions.allParameters.map { it.oscPath }
        assertEquals(paths.size, paths.toSet().size)
    }

    @Test
    fun theCurveIsTheDesktopsNotALinearFallback() {
        // A formula string with no case of its own falls back to linear: -30 dB midway
        val d = def(3)
        assertEquals(desktopDb(0.5f), InputParameterDefinitions.applyFormula(d, 0.5f), 1e-4f)
        assertEquals(-12.015f, InputParameterDefinitions.applyFormula(d, 0.5f), 0.01f)
        assertEquals(-60f, InputParameterDefinitions.applyFormula(d, 0f), 1e-3f)
        assertEquals(0f, InputParameterDefinitions.applyFormula(d, 1f), 1e-4f)
    }

    @Test
    fun levelsRoundTripThroughTheDialLikeOnTheDesktop() {
        val d = def(7)
        for (db in listOf(-60f, -42.75f, -12.25f, -6.5f, 0f)) {
            val dial = InputParameterDefinitions.reverseFormula(d, db)
            assertEquals("dial position for $db dB", desktopDial(db), dial, 1e-4f)
            assertEquals("round trip of $db dB", db, InputParameterDefinitions.applyFormula(d, dial), 1e-3f)
        }
    }

    @Test
    fun anInboundLevelStoresItsDialPosition() {
        val d = def(1)
        val level = InputParameterDefinitions.inboundValue(d, floatValue = -12.25f)!!
        assertEquals(desktopDial(-12.25f), level.normalizedValue, 1e-4f)
        // The same value held as text on an older desktop, echoed as ",is"
        assertEquals(level, InputParameterDefinitions.inboundValue(d, stringValue = "-12.25"))
    }

    @Test
    fun typedLevels() {
        assertEquals(-6.5f, ArraySends.parseDbCommit("-6.5")!!, 0f)
        assertEquals(-6.5f, ArraySends.parseDbCommit(" -6,5 dB ")!!, 0f)
        assertEquals(-6.5f, ArraySends.parseDbCommit("-6.5dB")!!, 0f)
        assertEquals(-6.5f, ArraySends.parseDbCommit("−6.5")!!, 0f)
        // Clamped into the range, as the desktop's editable label does
        assertEquals(-60f, ArraySends.parseDbCommit("-75")!!, 0f)
        assertEquals(0f, ArraySends.parseDbCommit("3")!!, 0f)
        assertNull(ArraySends.parseDbCommit(""))
        assertNull(ArraySends.parseDbCommit("dB"))
        assertNull(ArraySends.parseDbCommit("loud"))
        assertNull(ArraySends.parseDbCommit("NaN"))
    }

    @Test
    fun formattedWithOneDecimal() {
        assertEquals("-12.3", ArraySends.formatDb(-12.26f))
        assertEquals("-60.0", ArraySends.formatDb(-60f))
        assertEquals("0.0", ArraySends.formatDb(0f))
        assertEquals("0.0", ArraySends.formatDb(-0.01f))
    }

    @Test
    fun arraysWithoutOutputsAreDimmed() {
        // Unknown assignment (nothing received yet): nothing dimmed
        assertEquals(emptySet<Int>(), ArraySends.dimmedArrays(IntArray(0)))
        // Outputs on arrays 1, 1 and 3 plus a Single: every other array is dimmed
        assertEquals((1..10).toSet() - setOf(1, 3), ArraySends.dimmedArrays(intArrayOf(1, 1, 3, 0)))
        // An all-Single rig dims every array
        assertEquals((1..10).toSet(), ArraySends.dimmedArrays(intArrayOf(0, 0, 0)))
        // Ids outside 1..10 are ignored
        assertEquals((1..10).toSet() - setOf(10), ArraySends.dimmedArrays(intArrayOf(10, 11, -1)))
    }
}
