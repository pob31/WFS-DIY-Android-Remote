package com.wfsdiy.wfs_control_2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * InputParameterDefinitions.inboundValue: what one /remoteInput argument stores. The
 * numeric-text cases are the ",is" echoes of desktops before 1.0.0beta50 (a value a
 * recall, a load or an undo left as a string in their tree), which used to store 0.
 */
class InboundValueTest {

    private fun def(name: String) = requireNotNull(InputParameterDefinitions.parametersByVariableName[name]) {
        "no definition for $name"
    }

    // --- Numeric text reads as the number it holds ---

    @Test
    fun numericTextOnAFloatParameterMatchesTheFloat() {
        val attenuation = def("attenuation")
        val fromText = InputParameterDefinitions.inboundValue(attenuation, stringValue = "-6.0")
        assertEquals(InputParameterDefinitions.inboundValue(attenuation, floatValue = -6f), fromText)
        assertEquals(InputParameterDefinitions.reverseFormula(attenuation, -6f), fromText!!.normalizedValue, 1e-6f)
    }

    @Test
    fun numericTextOnAnIntParameterTakesTheIntPath() {
        // Dropdowns store the raw index; the text "3" is LFO shape 3 (square), not 3/8
        val shape = def("LFOshapeX")
        assertEquals(InputParameterDefinitions.inboundValue(shape, intValue = 3),
                     InputParameterDefinitions.inboundValue(shape, stringValue = "3"))
        assertEquals(3f, InputParameterDefinitions.inboundValue(shape, stringValue = " 3.0 ")!!.normalizedValue, 0f)

        val cluster = def("cluster")
        assertEquals(InputParameterDefinitions.inboundValue(cluster, intValue = 4),
                     InputParameterDefinitions.inboundValue(cluster, stringValue = "4"))

        val rotation = def("rotation")
        assertEquals(InputParameterDefinitions.inboundValue(rotation, intValue = -90),
                     InputParameterDefinitions.inboundValue(rotation, stringValue = "-90"))
    }

    @Test
    fun textThatIsNotANumberIsIgnoredOnANumericParameter() {
        val attenuation = def("attenuation")
        assertNull(InputParameterDefinitions.inboundValue(attenuation, stringValue = "abc"))
        assertNull(InputParameterDefinitions.inboundValue(attenuation, stringValue = ""))
        assertNull(InputParameterDefinitions.inboundValue(attenuation, stringValue = "NaN"))
        assertNull(InputParameterDefinitions.inboundValue(attenuation, stringValue = "Infinity"))
    }

    // --- Text parameters stay text ---

    @Test
    fun aNameStaysText() {
        val name = def("inputName")
        val kick = InputParameterDefinitions.inboundValue(name, stringValue = "Kick")
        assertNotNull(kick)
        assertEquals("Kick", kick!!.stringValue)

        // Even when it looks like a number
        val twelve = InputParameterDefinitions.inboundValue(name, stringValue = "12")
        assertEquals("12", twelve!!.stringValue)
        assertEquals(0f, twelve.normalizedValue, 0f)
    }

    // --- The typed paths are unchanged ---

    @Test
    fun aFloatIsNormalisedThroughItsFormula() {
        val attenuation = def("attenuation")
        val value = InputParameterDefinitions.inboundValue(attenuation, floatValue = -6f)!!
        assertEquals(InputParameterDefinitions.reverseFormula(attenuation, -6f), value.normalizedValue, 1e-6f)
        assertEquals("-6.00dB", value.displayValue)
    }

    @Test
    fun anIntOnAToggleIsStoredRaw() {
        val minimalLatency = def("minimalLatency")
        val value = InputParameterDefinitions.inboundValue(minimalLatency, intValue = 1)!!
        assertEquals(1f, value.normalizedValue, 0f)
        assertEquals("Minimal Latency", value.displayValue)
    }

    @Test
    fun noArgumentStoresNothing() {
        assertNull(InputParameterDefinitions.inboundValue(def("attenuation")))
    }
}
