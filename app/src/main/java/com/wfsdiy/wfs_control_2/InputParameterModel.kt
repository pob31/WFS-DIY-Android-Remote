package com.wfsdiy.wfs_control_2

import kotlin.math.log10
import kotlin.math.pow

/**
 * Represents the type of data for a parameter
 */
enum class ParameterType {
    INT,
    FLOAT,
    STRING
}

/**
 * Represents the UI component type for a parameter
 */
enum class UIComponentType {
    TEXT_BOX,
    NUMBER_BOX,
    V_SLIDER,
    H_SLIDER,
    V_BIDIRECTIONAL_SLIDER,
    H_BIDIRECTIONAL_SLIDER,
    DIAL,
    DIRECTION_DIAL,
    TEXT_BUTTON,
    DROPDOWN,
    JOYSTICK_XY,
    AUTO_CENTER_SLIDER,
    NONE
}

/**
 * Definition of a single parameter (static configuration)
 */
data class InputParameterDefinition(
    val group: String,
    val label: String,
    val variableName: String,
    val oscPath: String,
    val isIncoming: Boolean,
    val isOutgoing: Boolean,
    val uiType: UIComponentType,
    val dataType: ParameterType,
    val minValue: Float,
    val maxValue: Float,
    val formula: String? = null,  // Formula to convert normalized (0-1) to actual value
    val unit: String? = null,
    val enumValues: List<String>? = null,  // For dropdowns and text buttons
    val note: String? = null,
    val conditionalEnable: String? = null  // Condition for greying out
)

/**
 * Runtime value for a single parameter (for one input channel)
 */
data class InputParameterValue(
    val normalizedValue: Float = 0f,  // Always stored as 0.0 to 1.0
    val stringValue: String = "",      // For string parameters like inputName
    val displayValue: String = ""      // Formatted display value with unit
)

/**
 * State for all parameters of a single input channel
 */
data class InputChannelState(
    val inputId: Int,
    val parameters: MutableMap<String, InputParameterValue> = mutableMapOf()
) {
    fun getParameter(variableName: String): InputParameterValue {
        return parameters.getOrPut(variableName) { InputParameterValue() }
    }
    
    fun setParameter(variableName: String, value: InputParameterValue) {
        parameters[variableName] = value
    }
}

/**
 * Complete state for all 64 input channels
 */
data class InputParametersState(
    val channels: MutableMap<Int, InputChannelState> = mutableMapOf(),
    val selectedInputId: Int = 1,  // Currently selected input channel (1-64)
    val revision: Long = 0  // Incremented on each update to force Compose change detection
) {
    fun getChannel(inputId: Int): InputChannelState {
        return channels.getOrPut(inputId) { InputChannelState(inputId) }
    }
    
    fun getSelectedChannel(): InputChannelState {
        return getChannel(selectedInputId)
    }
}

/**
 * Utility object to hold all parameter definitions from the CSV
 */
object InputParameterDefinitions {
    
    val allParameters: List<InputParameterDefinition> = listOf(
        // Input group
        InputParameterDefinition(
            group = "Input",
            label = "Input Number",
            variableName = "inputNumber",
            oscPath = "/remoteInput/inputNumber",
            isIncoming = false,
            isOutgoing = true,
            uiType = UIComponentType.NONE,
            dataType = ParameterType.INT,
            minValue = 1f,
            maxValue = 64f,
            note = "max is numberOfInputs"
        ),
        InputParameterDefinition(
            group = "Input",
            label = "Input Name",
            variableName = "inputName",
            oscPath = "/remoteInput/inputName",
            isIncoming = true,
            isOutgoing = true,
            uiType = UIComponentType.TEXT_BOX,
            dataType = ParameterType.STRING,
            minValue = 0f,
            maxValue = 0f
        ),
        InputParameterDefinition(
            group = "Input",
            label = "Attenuation",
            variableName = "attenuation",
            oscPath = "/remoteInput/attenuation",
            isIncoming = true,
            isOutgoing = true,
            uiType = UIComponentType.V_SLIDER,
            dataType = ParameterType.FLOAT,
            minValue = -92f,
            maxValue = 0f,
            formula = "20*log10(pow(10,-92./20.)+((1-pow(10,-92./20.))*pow(x,2)))",
            unit = "dB"
        ),
        InputParameterDefinition(
            group = "Input",
            label = "Delay/Latency comp.",
            variableName = "delayLatency",
            oscPath = "/remoteInput/delayLatency",
            isIncoming = true,
            isOutgoing = true,
            uiType = UIComponentType.H_BIDIRECTIONAL_SLIDER,
            dataType = ParameterType.FLOAT,
            minValue = -100f,
            maxValue = 100f,
            formula = "(x*200.0)-100.0",
            unit = "ms"
        ),
        InputParameterDefinition(
            group = "Input",
            label = "Minimal Latency",
            variableName = "minimalLatency",
            oscPath = "/remoteInput/minimalLatency",
            isIncoming = true,
            isOutgoing = true,
            uiType = UIComponentType.TEXT_BUTTON,
            dataType = ParameterType.INT,
            minValue = 0f,
            maxValue = 1f,
            enumValues = listOf("Acoustic Precedence", "Minimal Latency")
        ),
        InputParameterDefinition(
            group = "Input",
            label = "Position X",
            variableName = "positionX",
            oscPath = "/remoteInput/positionX",
            isIncoming = true,
            isOutgoing = true,
            uiType = UIComponentType.NUMBER_BOX,
            dataType = ParameterType.FLOAT,
            minValue = -50f,
            maxValue = 50f,
            unit = "m",
            note = "incremented or decremented by horizontal axis of JoystickXY"
        ),
        InputParameterDefinition(
            group = "Input",
            label = "Position Y",
            variableName = "positionY",
            oscPath = "/remoteInput/positionY",
            isIncoming = true,
            isOutgoing = true,
            uiType = UIComponentType.NUMBER_BOX,
            dataType = ParameterType.FLOAT,
            minValue = -50f,
            maxValue = 50f,
            unit = "m",
            note = "incremented or decremented by vertical axis of JoystickXY"
        ),
        InputParameterDefinition(
            group = "Input",
            label = "Position Z",
            variableName = "positionZ",
            oscPath = "/remoteInput/positionZ",
            isIncoming = true,
            isOutgoing = true,
            uiType = UIComponentType.NUMBER_BOX,
            dataType = ParameterType.FLOAT,
            minValue = -50f,
            maxValue = 50f,
            unit = "m",
            note = "incremented or decremented by V slider with return to zero"
        ),
        InputParameterDefinition(
            group = "Input",
            label = "Offset X",
            variableName = "offsetX",
            oscPath = "/remoteInput/offsetX",
            isIncoming = true,
            isOutgoing = true,
            uiType = UIComponentType.NUMBER_BOX,
            dataType = ParameterType.FLOAT,
            minValue = -50f,
            maxValue = 50f,
            unit = "m",
            note = "Offset applied to Position X"
        ),
        InputParameterDefinition(
            group = "Input",
            label = "Offset Y",
            variableName = "offsetY",
            oscPath = "/remoteInput/offsetY",
            isIncoming = true,
            isOutgoing = true,
            uiType = UIComponentType.NUMBER_BOX,
            dataType = ParameterType.FLOAT,
            minValue = -50f,
            maxValue = 50f,
            unit = "m",
            note = "Offset applied to Position Y"
        ),
        InputParameterDefinition(
            group = "Input",
            label = "Offset Z",
            variableName = "offsetZ",
            oscPath = "/remoteInput/offsetZ",
            isIncoming = true,
            isOutgoing = true,
            uiType = UIComponentType.NUMBER_BOX,
            dataType = ParameterType.FLOAT,
            minValue = -50f,
            maxValue = 50f,
            unit = "m",
            note = "Offset applied to Position Z"
        ),
        InputParameterDefinition(
            group = "Input",
            label = "Cluster",
            variableName = "cluster",
            oscPath = "/remoteInput/cluster",
            isIncoming = true,
            isOutgoing = true,
            uiType = UIComponentType.DROPDOWN,
            dataType = ParameterType.INT,
            minValue = 0f,
            maxValue = 10f,
            enumValues = listOf("none", "Cluster 1", "Cluster 2", "Cluster 3", "Cluster 4", "Cluster 5", "Cluster 6", "Cluster 7", "Cluster 8", "Cluster 9", "Cluster 10")
        ),
        InputParameterDefinition(
            group = "Input",
            label = "Max Speed Active",
            variableName = "maxSpeedActive",
            oscPath = "/remoteInput/maxSpeedActive",
            isIncoming = true,
            isOutgoing = true,
            uiType = UIComponentType.TEXT_BUTTON,
            dataType = ParameterType.INT,
            minValue = 0f,
            maxValue = 1f,
            enumValues = listOf("ON", "OFF")
        ),
        InputParameterDefinition(
            group = "Input",
            label = "Max Speed",
            variableName = "maxSpeed",
            oscPath = "/remoteInput/maxSpeed",
            isIncoming = true,
            isOutgoing = true,
            uiType = UIComponentType.DIAL,
            dataType = ParameterType.FLOAT,
            minValue = 0.01f,
            maxValue = 20f,
            formula = "x*19.99+0.01",
            unit = "m/s"
        ),
        InputParameterDefinition(
            group = "Input",
            label = "Height Factor",
            variableName = "heightFactor",
            oscPath = "/remoteInput/heightFactor",
            isIncoming = true,
            isOutgoing = true,
            uiType = UIComponentType.DIAL,
            dataType = ParameterType.INT,
            minValue = 0f,
            maxValue = 100f,
            formula = "x*100",
            unit = "%"
        ),
        InputParameterDefinition(
            group = "Input",
            label = "Attenuation Law",
            variableName = "attenuationLaw",
            oscPath = "/remoteInput/attenuationLaw",
            isIncoming = true,
            isOutgoing = true,
            uiType = UIComponentType.TEXT_BUTTON,
            dataType = ParameterType.INT,
            minValue = 0f,
            maxValue = 1f,
            enumValues = listOf("Log", "1/d²")
        ),
        InputParameterDefinition(
            group = "Input",
            label = "Distance Attenuation",
            variableName = "distanceAttenuation",
            oscPath = "/remoteInput/distanceAttenuation",
            isIncoming = true,
            isOutgoing = true,
            uiType = UIComponentType.DIAL,
            dataType = ParameterType.FLOAT,
            minValue = -6f,
            maxValue = 0f,
            formula = "(x*6.0)-6.0",
            unit = "dB/m",
            note = "visible if AttenuationLaw == 0 (shares the same position as Dial distanceRatio)"
        ),
        InputParameterDefinition(
            group = "Input",
            label = "Distance Ratio",
            variableName = "distanceRatio",
            oscPath = "/remoteInput/distanceRatio",
            isIncoming = true,
            isOutgoing = true,
            uiType = UIComponentType.DIAL,
            dataType = ParameterType.FLOAT,
            minValue = 0.1f,
            maxValue = 10f,
            formula = "pow(10.0,(x*2.0)-1.0)",
            unit = "x",
            note = "visible if AttenuationLaw == 1 (shares the same position as Dial distanceAttenuation)"
        ),
        InputParameterDefinition(
            group = "Input",
            label = "Common Attenuation",
            variableName = "commonAtten",
            oscPath = "/remoteInput/commonAtten",
            isIncoming = true,
            isOutgoing = true,
            uiType = UIComponentType.DIAL,
            dataType = ParameterType.INT,
            minValue = 0f,
            maxValue = 100f,
            formula = "x*100",
            unit = "%"
        ),
        
        // Directivity group
        InputParameterDefinition(
            group = "Directivity",
            label = "Directivity",
            variableName = "directivity",
            oscPath = "/remoteInput/directivity",
            isIncoming = true,
            isOutgoing = true,
            uiType = UIComponentType.H_BIDIRECTIONAL_SLIDER,
            dataType = ParameterType.INT,
            minValue = 2f,
            maxValue = 360f,
            formula = "(x*358)+2",
            unit = "°"
        ),
        InputParameterDefinition(
            group = "Directivity",
            label = "Rotation",
            variableName = "rotation",
            oscPath = "/remoteInput/rotation",
            isIncoming = true,
            isOutgoing = true,
            uiType = UIComponentType.DIRECTION_DIAL,
            dataType = ParameterType.INT,
            minValue = -179f,
            maxValue = 180f,
            formula = "(x*360)-180",
            unit = "°"
        ),
        InputParameterDefinition(
            group = "Directivity",
            label = "Tilt",
            variableName = "tilt",
            oscPath = "/remoteInput/tilt",
            isIncoming = true,
            isOutgoing = true,
            uiType = UIComponentType.V_BIDIRECTIONAL_SLIDER,
            dataType = ParameterType.INT,
            minValue = -90f,
            maxValue = 90f,
            formula = "(x*180)-90",
            unit = "°"
        ),
        InputParameterDefinition(
            group = "Directivity",
            label = "HF Shelf",
            variableName = "HFshelf",
            oscPath = "/remoteInput/HFshelf",
            isIncoming = true,
            isOutgoing = true,
            uiType = UIComponentType.V_SLIDER,
            dataType = ParameterType.FLOAT,
            minValue = -24f,
            maxValue = 0f,
            formula = "20*log10(pow(10,-24./20.)+((1-pow(10,-24./20.))*pow(x,2)))",
            unit = "dB"
        ),
        
        // Live Source Attenuation group
        InputParameterDefinition(
            group = "Live Source Attenuation",
            label = "Active",
            variableName = "liveSourceActive",
            oscPath = "/remoteInput/liveSourceActive",
            isIncoming = true,
            isOutgoing = true,
            uiType = UIComponentType.TEXT_BUTTON,
            dataType = ParameterType.INT,
            minValue = 0f,
            maxValue = 1f,
            enumValues = listOf("ON", "OFF")
        ),
        InputParameterDefinition(
            group = "Live Source Attenuation",
            label = "Radius",
            variableName = "liveSourceRadius",
            oscPath = "/remoteInput/liveSourceRadius",
            isIncoming = true,
            isOutgoing = true,
            uiType = UIComponentType.H_SLIDER,
            dataType = ParameterType.FLOAT,
            minValue = 0f,
            maxValue = 50f,
            formula = "x*50.0",
            unit = "m",
            conditionalEnable = "liveSourceActive==1"
        ),
        InputParameterDefinition(
            group = "Live Source Attenuation",
            label = "Shape",
            variableName = "liveSourceShape",
            oscPath = "/remoteInput/liveSourceShape",
            isIncoming = true,
            isOutgoing = true,
            uiType = UIComponentType.DROPDOWN,
            dataType = ParameterType.INT,
            minValue = 0f,
            maxValue = 3f,
            enumValues = listOf("linear", "log", "square d²", "sine"),
            conditionalEnable = "liveSourceActive==1"
        ),
        InputParameterDefinition(
            group = "Live Source Attenuation",
            label = "Attenuation",
            variableName = "liveSourceAttenuation",
            oscPath = "/remoteInput/liveSourceAttenuation",
            isIncoming = true,
            isOutgoing = true,
            uiType = UIComponentType.V_SLIDER,
            dataType = ParameterType.FLOAT,
            minValue = -24f,
            maxValue = 0f,
            formula = "20*log10(pow(10,-24./20.)+((1-pow(10,-24./20.))*pow(x,2)))",
            unit = "dB",
            conditionalEnable = "liveSourceActive==1"
        ),
        InputParameterDefinition(
            group = "Live Source Attenuation",
            label = "Peak Threshold",
            variableName = "liveSourcePeakThreshold",
            oscPath = "/remoteInput/liveSourcePeakThreshold",
            isIncoming = true,
            isOutgoing = true,
            uiType = UIComponentType.V_SLIDER,
            dataType = ParameterType.FLOAT,
            minValue = -48f,
            maxValue = 0f,
            formula = "20*log10(pow(10,-48./20.)+((1-pow(10,-48./20.))*pow(x,2)))",
            unit = "dB",
            conditionalEnable = "liveSourceActive==1"
        ),
        InputParameterDefinition(
            group = "Live Source Attenuation",
            label = "Peak Ratio",
            variableName = "liveSourcePeakRatio",
            oscPath = "/remoteInput/liveSourcePeakRatio",
            isIncoming = true,
            isOutgoing = true,
            uiType = UIComponentType.DIAL,
            dataType = ParameterType.FLOAT,
            minValue = 1f,
            maxValue = 10f,
            formula = "(x*9.0)+1",
            conditionalEnable = "liveSourceActive==1"
        ),
        InputParameterDefinition(
            group = "Live Source Attenuation",
            label = "Slow Threshold",
            variableName = "liveSourceSlowThreshold",
            oscPath = "/remoteInput/liveSourceSlowThreshold",
            isIncoming = true,
            isOutgoing = true,
            uiType = UIComponentType.V_SLIDER,
            dataType = ParameterType.FLOAT,
            minValue = -48f,
            maxValue = 0f,
            formula = "20*log10(pow(10,-48./20.)+((1-pow(10,-48./20.))*pow(x,2)))",
            unit = "dB",
            conditionalEnable = "liveSourceActive==1"
        ),
        InputParameterDefinition(
            group = "Live Source Attenuation",
            label = "Slow Ratio",
            variableName = "liveSourceSlowRatio",
            oscPath = "/remoteInput/liveSourceSlowRatio",
            isIncoming = true,
            isOutgoing = true,
            uiType = UIComponentType.DIAL,
            dataType = ParameterType.FLOAT,
            minValue = 1f,
            maxValue = 10f,
            formula = "(x*9.0)+1",
            conditionalEnable = "liveSourceActive==1"
        ),
        
        // Floor Reflections group
        InputParameterDefinition(
            group = "Floor Reflections",
            label = "Active",
            variableName = "FRactive",
            oscPath = "/remoteInput/FRactive",
            isIncoming = true,
            isOutgoing = true,
            uiType = UIComponentType.TEXT_BUTTON,
            dataType = ParameterType.INT,
            minValue = 0f,
            maxValue = 1f,
            enumValues = listOf("ON", "OFF")
        ),
        InputParameterDefinition(
            group = "Floor Reflections",
            label = "Attenuation",
            variableName = "FRattenuation",
            oscPath = "/remoteInput/FRattenuation",
            isIncoming = true,
            isOutgoing = true,
            uiType = UIComponentType.V_SLIDER,
            dataType = ParameterType.FLOAT,
            minValue = -60f,
            maxValue = 0f,
            formula = "20*log10(pow(10,-60./20.)+((1-pow(10,-60./20.))*pow(x,2)))",
            unit = "dB",
            conditionalEnable = "FRactive==1"
        ),
        InputParameterDefinition(
            group = "Floor Reflections",
            label = "Low Cut Active",
            variableName = "FRlowCutActive",
            oscPath = "/remoteInput/FRlowCutActive",
            isIncoming = true,
            isOutgoing = true,
            uiType = UIComponentType.TEXT_BUTTON,
            dataType = ParameterType.INT,
            minValue = 0f,
            maxValue = 1f,
            enumValues = listOf("ON", "OFF"),
            conditionalEnable = "FRactive==1"
        ),
        InputParameterDefinition(
            group = "Floor Reflections",
            label = "Low Cut Freq",
            variableName = "FRlowCutFreq",
            oscPath = "/remoteInput/FrlowCutFreq",
            isIncoming = true,
            isOutgoing = true,
            uiType = UIComponentType.H_SLIDER,
            dataType = ParameterType.INT,
            minValue = 20f,
            maxValue = 20000f,
            formula = "20*pow(10,4*x)",
            unit = "Hz",
            conditionalEnable = "FRactive==1&&FRlowCutActive==1"
        ),
        InputParameterDefinition(
            group = "Floor Reflections",
            label = "High Shelf Active",
            variableName = "FRhighShelfActive",
            oscPath = "/remoteInput/FRhighShelfActive",
            isIncoming = true,
            isOutgoing = true,
            uiType = UIComponentType.TEXT_BUTTON,
            dataType = ParameterType.INT,
            minValue = 0f,
            maxValue = 1f,
            enumValues = listOf("ON", "OFF"),
            conditionalEnable = "FRactive==1"
        ),
        InputParameterDefinition(
            group = "Floor Reflections",
            label = "High Shelf Freq",
            variableName = "FRhighShelfFreq",
            oscPath = "/remoteInput/FRhighShelfFreq",
            isIncoming = true,
            isOutgoing = true,
            uiType = UIComponentType.H_SLIDER,
            dataType = ParameterType.INT,
            minValue = 20f,
            maxValue = 20000f,
            formula = "20*pow(10,4*x)",
            unit = "Hz",
            conditionalEnable = "FRactive==1&&FRhighShelfActive==1"
        ),
        InputParameterDefinition(
            group = "Floor Reflections",
            label = "High Shelf Gain",
            variableName = "FRhighShelfGain",
            oscPath = "/remoteInput/FRhighShelfGain",
            isIncoming = true,
            isOutgoing = true,
            uiType = UIComponentType.V_SLIDER,
            dataType = ParameterType.FLOAT,
            minValue = -24f,
            maxValue = 0f,
            formula = "20*log10(pow(10,-24./20.)+((1-pow(10,-24./20.))*pow(x,2)))",
            unit = "dB",
            conditionalEnable = "FRactive==1&&FRhighShelfActive==1"
        ),
        InputParameterDefinition(
            group = "Floor Reflections",
            label = "High Shelf Slope",
            variableName = "FRhighShelfSlope",
            oscPath = "/remoteInput/FRhighShelfSlope",
            isIncoming = true,
            isOutgoing = true,
            uiType = UIComponentType.H_SLIDER,
            dataType = ParameterType.FLOAT,
            minValue = 0.1f,
            maxValue = 0.9f,
            formula = "(x*0.8)+0.1",
            conditionalEnable = "FRactive==1&&FRhighShelfActive==1"
        ),
        InputParameterDefinition(
            group = "Floor Reflections",
            label = "Diffusion",
            variableName = "FRdiffusion",
            oscPath = "/remoteInput/FRdiffusion",
            isIncoming = true,
            isOutgoing = true,
            uiType = UIComponentType.DIAL,
            dataType = ParameterType.INT,
            minValue = 0f,
            maxValue = 100f,
            formula = "x*100",
            unit = "%",
            conditionalEnable = "FRactive==1"
        ),
        
        // Jitter group
        InputParameterDefinition(
            group = "Jitter",
            label = "Jitter",
            variableName = "jitter",
            oscPath = "/remoteInput/jitter",
            isIncoming = true,
            isOutgoing = true,
            uiType = UIComponentType.H_SLIDER,
            dataType = ParameterType.FLOAT,
            minValue = 0f,
            maxValue = 10f,
            formula = "10*pow(x,2)",
            unit = "m"
        ),
        
        // LFO group
        InputParameterDefinition(
            group = "LFO",
            label = "Active",
            variableName = "LFOactive",
            oscPath = "/remoteInput/LFOactive",
            isIncoming = true,
            isOutgoing = true,
            uiType = UIComponentType.TEXT_BUTTON,
            dataType = ParameterType.INT,
            minValue = 0f,
            maxValue = 1f,
            enumValues = listOf("ON", "OFF")
        ),
        InputParameterDefinition(
            group = "LFO",
            label = "Period",
            variableName = "LFOperiod",
            oscPath = "/remoteInput/LFOperiod",
            isIncoming = true,
            isOutgoing = true,
            uiType = UIComponentType.DIAL,
            dataType = ParameterType.FLOAT,
            minValue = 0.01f,
            maxValue = 100f,
            formula = "pow(10.0,sqrt(x)*4.0-2.0)",
            unit = "s",
            conditionalEnable = "LFOactive==1"
        ),
        InputParameterDefinition(
            group = "LFO",
            label = "Phase",
            variableName = "LFOphase",
            oscPath = "/remoteInput/LFOphase",
            isIncoming = true,
            isOutgoing = true,
            uiType = UIComponentType.DIRECTION_DIAL,
            dataType = ParameterType.INT,
            minValue = 0f,
            maxValue = 360f,
            formula = "x*360",
            unit = "°",
            conditionalEnable = "LFOactive==1"
        ),
        InputParameterDefinition(
            group = "LFO",
            label = "Shape X",
            variableName = "LFOshapeX",
            oscPath = "/remoteInput/LFOshapeX",
            isIncoming = true,
            isOutgoing = true,
            uiType = UIComponentType.DROPDOWN,
            dataType = ParameterType.INT,
            minValue = 0f,
            maxValue = 8f,
            enumValues = listOf("OFF", "sine", "square", "sawtooth", "triangle", "keystone", "log", "exp", "random"),
            conditionalEnable = "LFOactive==1"
        ),
        InputParameterDefinition(
            group = "LFO",
            label = "Shape Y",
            variableName = "LFOshapeY",
            oscPath = "/remoteInput/LFOshapeY",
            isIncoming = true,
            isOutgoing = true,
            uiType = UIComponentType.DROPDOWN,
            dataType = ParameterType.INT,
            minValue = 0f,
            maxValue = 8f,
            enumValues = listOf("OFF", "sine", "square", "sawtooth", "triangle", "keystone", "log", "exp", "random"),
            conditionalEnable = "LFOactive==1"
        ),
        InputParameterDefinition(
            group = "LFO",
            label = "Shape Z",
            variableName = "LFOshapeZ",
            oscPath = "/remoteInput/LFOshapeZ",
            isIncoming = true,
            isOutgoing = true,
            uiType = UIComponentType.DROPDOWN,
            dataType = ParameterType.INT,
            minValue = 0f,
            maxValue = 8f,
            enumValues = listOf("OFF", "sine", "square", "sawtooth", "triangle", "keystone", "log", "exp", "random"),
            conditionalEnable = "LFOactive==1"
        ),
        InputParameterDefinition(
            group = "LFO",
            label = "Rate X",
            variableName = "LFOrateX",
            oscPath = "/remoteInput/LFOrateX",
            isIncoming = true,
            isOutgoing = true,
            uiType = UIComponentType.H_SLIDER,
            dataType = ParameterType.FLOAT,
            minValue = 0.01f,
            maxValue = 100f,
            formula = "pow(10.0,(x*4.0)-2.0)",
            unit = "x",
            conditionalEnable = "LFOactive==1&&LFOshapeX>0"
        ),
        InputParameterDefinition(
            group = "LFO",
            label = "Rate Y",
            variableName = "LFOrateY",
            oscPath = "/remoteInput/LFOrateY",
            isIncoming = true,
            isOutgoing = true,
            uiType = UIComponentType.H_SLIDER,
            dataType = ParameterType.FLOAT,
            minValue = 0.01f,
            maxValue = 100f,
            formula = "pow(10.0,(x*4.0)-2.0)",
            unit = "x",
            conditionalEnable = "LFOactive==1&&LFOshapeY>0"
        ),
        InputParameterDefinition(
            group = "LFO",
            label = "Rate Z",
            variableName = "LFOrateZ",
            oscPath = "/remoteInput/LFOrateZ",
            isIncoming = true,
            isOutgoing = true,
            uiType = UIComponentType.H_SLIDER,
            dataType = ParameterType.FLOAT,
            minValue = 0.01f,
            maxValue = 100f,
            formula = "pow(10.0,(x*4.0)-2.0)",
            unit = "x",
            conditionalEnable = "LFOactive==1&&LFOshapeZ>0"
        ),
        InputParameterDefinition(
            group = "LFO",
            label = "Amplitude X",
            variableName = "LFOamplitudeX",
            oscPath = "/remoteInput/LFOamplitudeX",
            isIncoming = true,
            isOutgoing = true,
            uiType = UIComponentType.H_BIDIRECTIONAL_SLIDER,
            dataType = ParameterType.FLOAT,
            minValue = 0f,
            maxValue = 50f,
            formula = "x*50.0",
            unit = "m",
            conditionalEnable = "LFOactive==1&&LFOshapeX>0"
        ),
        InputParameterDefinition(
            group = "LFO",
            label = "Amplitude Y",
            variableName = "LFOamplitudeY",
            oscPath = "/remoteInput/LFOamplitudeY",
            isIncoming = true,
            isOutgoing = true,
            uiType = UIComponentType.V_BIDIRECTIONAL_SLIDER,
            dataType = ParameterType.FLOAT,
            minValue = 0f,
            maxValue = 50f,
            formula = "x*50.0",
            unit = "m",
            conditionalEnable = "LFOactive==1&&LFOshapeY>0"
        ),
        InputParameterDefinition(
            group = "LFO",
            label = "Amplitude Z",
            variableName = "LFOamplitudeZ",
            oscPath = "/remoteInput/LFOamplitudeZ",
            isIncoming = true,
            isOutgoing = true,
            uiType = UIComponentType.V_BIDIRECTIONAL_SLIDER,
            dataType = ParameterType.FLOAT,
            minValue = 0f,
            maxValue = 50f,
            formula = "x*50.0",
            unit = "m",
            conditionalEnable = "LFOactive==1&&LFOshapeZ>0"
        ),
        InputParameterDefinition(
            group = "LFO",
            label = "Phase X",
            variableName = "LFOphaseX",
            oscPath = "/remoteInput/LFOphaseX",
            isIncoming = true,
            isOutgoing = true,
            uiType = UIComponentType.DIRECTION_DIAL,
            dataType = ParameterType.INT,
            minValue = 0f,
            maxValue = 360f,
            formula = "x*360",
            unit = "°",
            conditionalEnable = "LFOactive==1&&LFOshapeX>0"
        ),
        InputParameterDefinition(
            group = "LFO",
            label = "Phase Y",
            variableName = "LFOphaseY",
            oscPath = "/remoteInput/LFOphaseY",
            isIncoming = true,
            isOutgoing = true,
            uiType = UIComponentType.DIRECTION_DIAL,
            dataType = ParameterType.INT,
            minValue = 0f,
            maxValue = 360f,
            formula = "x*360",
            unit = "°",
            conditionalEnable = "LFOactive==1&&LFOshapeY>0"
        ),
        InputParameterDefinition(
            group = "LFO",
            label = "Phase Z",
            variableName = "LFOphaseZ",
            oscPath = "/remoteInput/LFOphaseZ",
            isIncoming = true,
            isOutgoing = true,
            uiType = UIComponentType.DIRECTION_DIAL,
            dataType = ParameterType.INT,
            minValue = 0f,
            maxValue = 360f,
            formula = "x*360",
            unit = "°",
            conditionalEnable = "LFOactive==1&&LFOshapeZ>0"
        ),
        InputParameterDefinition(
            group = "LFO",
            label = "Gyrophone",
            variableName = "LFOgyrophone",
            oscPath = "/remoteInput/LFOgyrophone",
            isIncoming = true,
            isOutgoing = true,
            uiType = UIComponentType.DROPDOWN,
            dataType = ParameterType.INT,
            minValue = -1f,
            maxValue = 1f,
            enumValues = listOf("Anti-Clockwise", "OFF", "Clockwise"),
            conditionalEnable = "LFOactive==1"
        )
    )
    
    val parametersByGroup: Map<String, List<InputParameterDefinition>> = allParameters.groupBy { it.group }
    
    val parametersByVariableName: Map<String, InputParameterDefinition> = allParameters.associateBy { it.variableName }
    
    /**
     * Apply formula to convert normalized value (0-1) to actual value
     */
    fun applyFormula(definition: InputParameterDefinition, normalizedValue: Float): Float {
        val x = normalizedValue.coerceIn(0f, 1f)
        
        return when {
            definition.formula == null -> {
                // Linear mapping
                definition.minValue + (x * (definition.maxValue - definition.minValue))
            }
            else -> {
                // Apply the formula
                try {
                    when (definition.formula) {
                        "20*log10(pow(10,-92./20.)+((1-pow(10,-92./20.))*pow(x,2)))" -> {
                            20f * log10(10f.pow(-92f/20f) + ((1f - 10f.pow(-92f/20f)) * x.pow(2)))
                        }
                        "20*log10(pow(10,-24./20.)+((1-pow(10,-24./20.))*pow(x,2)))" -> {
                            20f * log10(10f.pow(-24f/20f) + ((1f - 10f.pow(-24f/20f)) * x.pow(2)))
                        }
                        "20*log10(pow(10,-48./20.)+((1-pow(10,-48./20.))*pow(x,2)))" -> {
                            20f * log10(10f.pow(-48f/20f) + ((1f - 10f.pow(-48f/20f)) * x.pow(2)))
                        }
                        "20*log10(pow(10,-60./20.)+((1-pow(10,-60./20.))*pow(x,2)))" -> {
                            20f * log10(10f.pow(-60f/20f) + ((1f - 10f.pow(-60f/20f)) * x.pow(2)))
                        }
                        "(x*200.0)-100.0" -> (x * 200f) - 100f
                        "(x*358)+2" -> (x * 358f) + 2f
                        "(x*360)-180" -> (x * 360f) - 180f
                        "(x*180)-90" -> (x * 180f) - 90f
                        "x*50.0" -> x * 50f
                        "x*19.99+0.01" -> (x * 19.99f) + 0.01f
                        "x*100" -> x * 100f
                        "(x*6.0)-0.6" -> (x * 6f) - 0.6f
                        "pow(10.0,(x*2.0)-1.0)" -> 10f.pow((x * 2f) - 1f)
                        "(x*9.0)+1" -> (x * 9f) + 1f
                        "20*pow(10,4*x)" -> 20f * 10f.pow(4f * x)
                        "(x*0.8)+0.1" -> (x * 0.8f) + 0.1f
                        "10*pow(x,2)" -> 10f * x.pow(2)
                        "pow(10.0,sqrt(x)*4.0-2.0)" -> 10f.pow(kotlin.math.sqrt(x) * 4f - 2f)
                        "x*360" -> x * 360f
                        "pow(10.0,(x*4.0)-2.0)" -> 10f.pow((x * 4f) - 2f)
                        else -> definition.minValue + (x * (definition.maxValue - definition.minValue))
                    }
                } catch (e: Exception) {
                    definition.minValue + (x * (definition.maxValue - definition.minValue))
                }
            }
        }
    }
    
    /**
     * Reverse formula to convert actual value to normalized (0-1)
     */
    fun reverseFormula(definition: InputParameterDefinition, actualValue: Float): Float {
        val y = actualValue.coerceIn(definition.minValue, definition.maxValue)
        
        return when {
            definition.formula == null -> {
                // Linear mapping reverse
                (y - definition.minValue) / (definition.maxValue - definition.minValue)
            }
            else -> {
                try {
                    when (definition.formula) {
                        "20*log10(pow(10,-92./20.)+((1-pow(10,-92./20.))*pow(x,2)))" -> {
                            // Reverse: x = sqrt((10^(y/20) - 10^(-92/20)) / (1 - 10^(-92/20)))
                            val minDb = -92f
                            val base = 10f.pow(minDb / 20f)
                            val numerator = 10f.pow(y / 20f) - base
                            val denominator = 1f - base
                            kotlin.math.sqrt(numerator / denominator).coerceIn(0f, 1f)
                        }
                        "20*log10(pow(10,-24./20.)+((1-pow(10,-24./20.))*pow(x,2)))" -> {
                            val minDb = -24f
                            val base = 10f.pow(minDb / 20f)
                            val numerator = 10f.pow(y / 20f) - base
                            val denominator = 1f - base
                            kotlin.math.sqrt(numerator / denominator).coerceIn(0f, 1f)
                        }
                        "20*log10(pow(10,-48./20.)+((1-pow(10,-48./20.))*pow(x,2)))" -> {
                            val minDb = -48f
                            val base = 10f.pow(minDb / 20f)
                            val numerator = 10f.pow(y / 20f) - base
                            val denominator = 1f - base
                            kotlin.math.sqrt(numerator / denominator).coerceIn(0f, 1f)
                        }
                        "20*log10(pow(10,-60./20.)+((1-pow(10,-60./20.))*pow(x,2)))" -> {
                            val minDb = -60f
                            val base = 10f.pow(minDb / 20f)
                            val numerator = 10f.pow(y / 20f) - base
                            val denominator = 1f - base
                            kotlin.math.sqrt(numerator / denominator).coerceIn(0f, 1f)
                        }
                        "(x*200.0)-100.0" -> (y + 100f) / 200f
                        "(x*358)+2" -> (y - 2f) / 358f
                        "(x*360)-180" -> (y + 180f) / 360f
                        "(x*180)-90" -> (y + 90f) / 180f
                        "x*50.0" -> y / 50f
                        "x*19.99+0.01" -> (y - 0.01f) / 19.99f
                        "x*100" -> y / 100f
                        "(x*6.0)-0.6" -> (y + 0.6f) / 6f
                        "pow(10.0,(x*2.0)-1.0)" -> (log10(y) + 1f) / 2f
                        "(x*9.0)+1" -> (y - 1f) / 9f
                        "20*pow(10,4*x)" -> log10(y / 20f) / 4f
                        "(x*0.8)+0.1" -> (y - 0.1f) / 0.8f
                        "10*pow(x,2)" -> kotlin.math.sqrt(y / 10f)
                        "pow(10.0,sqrt(x)*4.0-2.0)" -> ((log10(y) + 2f) / 4f).pow(2)
                        "x*360" -> y / 360f
                        "pow(10.0,(x*4.0)-2.0)" -> (log10(y) + 2f) / 4f
                        else -> (y - definition.minValue) / (definition.maxValue - definition.minValue)
                    }
                } catch (e: Exception) {
                    (y - definition.minValue) / (definition.maxValue - definition.minValue)
                }
            }
        }.coerceIn(0f, 1f)
    }
}

