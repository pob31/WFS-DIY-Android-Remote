package com.wfsdiy.wfs_control_2

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class SliderOrientation {
    HORIZONTAL,
    VERTICAL
}

@Composable
private fun isPhoneDevice(): Boolean {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    val screenWidthDp = configuration.screenWidthDp.dp
    val screenHeightDp = configuration.screenHeightDp.dp

    val physicalWidthInches = screenWidthDp.value / 160f
    val physicalHeightInches = screenHeightDp.value / 160f
    val diagonalInches = sqrt(physicalWidthInches * physicalWidthInches + physicalHeightInches * physicalHeightInches)

    return diagonalInches < 6.0f
}

@Composable
private fun getResponsiveSliderSizes(): ResponsiveSliderSizes {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    
    val screenWidthDp = configuration.screenWidthDp.dp
    val screenHeightDp = configuration.screenHeightDp.dp
    val screenDensity = density.density
    
    // Calculate responsive sizes based on screen dimensions and density
    val baseThumbSize = (16.dp * screenDensity).coerceIn(12.dp, 32.dp)
    val baseTrackThickness = (3.dp * screenDensity).coerceIn(2.dp, 8.dp)
    
    // Adjust for screen size
    val screenSizeFactor = min(screenWidthDp.value, screenHeightDp.value) / 400f // Normalize to 400dp baseline
    val adjustedThumbSize = (baseThumbSize.value * screenSizeFactor).coerceIn(12f, 32f).dp
    val adjustedTrackThickness = (baseTrackThickness.value * screenSizeFactor).coerceIn(2f, 8f).dp
    
    return ResponsiveSliderSizes(
        thumbSize = DpSize(adjustedThumbSize, adjustedThumbSize),
        trackThickness = adjustedTrackThickness
    )
}

private data class ResponsiveSliderSizes(
    val thumbSize: DpSize,
    val trackThickness: Dp
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BidirectionalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    orientation: SliderOrientation = SliderOrientation.HORIZONTAL,
    sliderColor: Color = Color.Blue,
    trackBackgroundColor: Color = sliderColor.copy(alpha = 0.24f),
    valueRange: ClosedFloatingPointRange<Float> = -1f..1f,
    thumbSize: DpSize? = null,
    trackThickness: Dp? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    onValueChangeFinished: (() -> Unit)? = null,
    enabled: Boolean = true,
    displayedValue: String = "",
    isValueEditable: Boolean = false,
    onDisplayedValueChange: (String) -> Unit = {},
    onValueCommit: (String) -> Unit = {},
    valueUnit: String = "",
    valueTextColor: Color = Color.White
) {
    val responsiveSizes = getResponsiveSliderSizes()
    val finalThumbSize = thumbSize ?: responsiveSizes.thumbSize
    val finalTrackThickness = trackThickness ?: responsiveSizes.trackThickness
    
    if (isValueEditable && displayedValue.isNotEmpty()) {
        // Slider with editable number box
        if (orientation == SliderOrientation.HORIZONTAL) {
            Row(
                modifier = modifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BidirectionalSliderCore(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f),
                    orientation = orientation,
                    sliderColor = sliderColor,
                    trackBackgroundColor = trackBackgroundColor,
                    valueRange = valueRange,
                    thumbSize = finalThumbSize,
                    trackThickness = finalTrackThickness,
                    interactionSource = interactionSource,
                    onValueChangeFinished = onValueChangeFinished,
                    enabled = true // Always enabled for interaction, greying is visual only
                )
                EditableValueBox(
                    displayedValue = displayedValue,
                    valueUnit = valueUnit,
                    valueTextColor = valueTextColor,
                    onDisplayedValueChange = onDisplayedValueChange,
                    onValueCommit = onValueCommit,
                    enabled = enabled, // Visual greying only
                    modifier = Modifier.width(100.dp)
                )
            }
        } else {
            Column(
                modifier = modifier.wrapContentWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EditableValueBox(
                    displayedValue = displayedValue,
                    valueUnit = valueUnit,
                    valueTextColor = valueTextColor,
                    onDisplayedValueChange = onDisplayedValueChange,
                    onValueCommit = onValueCommit,
                    enabled = enabled, // Visual greying only
                    modifier = Modifier.width(90.dp) // Compact number box for vertical sliders
                )
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.TopCenter
                ) {
                    BidirectionalSliderCore(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier.fillMaxHeight().width(40.dp), // Constrain track width
                        orientation = orientation,
                        sliderColor = sliderColor,
                        trackBackgroundColor = trackBackgroundColor,
                        valueRange = valueRange,
                        thumbSize = finalThumbSize,
                        trackThickness = finalTrackThickness,
                        interactionSource = interactionSource,
                        onValueChangeFinished = onValueChangeFinished,
                        enabled = true // Always enabled for interaction, greying is visual only
                    )
                }
            }
        }
    } else {
        // Slider without editable box (backward compatibility)
        BidirectionalSliderCore(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            orientation = orientation,
            sliderColor = sliderColor,
            trackBackgroundColor = trackBackgroundColor,
            valueRange = valueRange,
            thumbSize = finalThumbSize,
            trackThickness = finalTrackThickness,
            interactionSource = interactionSource,
            onValueChangeFinished = onValueChangeFinished,
            enabled = true // Always enabled
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BidirectionalSliderCore(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier,
    orientation: SliderOrientation,
    sliderColor: Color,
    trackBackgroundColor: Color,
    valueRange: ClosedFloatingPointRange<Float>,
    thumbSize: DpSize?,
    trackThickness: Dp?,
    interactionSource: MutableInteractionSource,
    onValueChangeFinished: (() -> Unit)?,
    enabled: Boolean
) {
    val responsiveSizes = getResponsiveSliderSizes()
    val actualThumbSize = thumbSize ?: responsiveSizes.thumbSize
    val actualTrackThickness = trackThickness ?: responsiveSizes.trackThickness
    
    val baseModifier = if (orientation == SliderOrientation.VERTICAL) {
        modifier
            .graphicsLayer { rotationZ = -90f }
            .layout { measurable, constraints ->
                val placeable = measurable.measure(
                    constraints.copy(
                        minWidth = constraints.minHeight,
                        maxWidth = constraints.maxHeight,
                        minHeight = constraints.minWidth,
                        maxHeight = constraints.maxWidth
                    )
                )
                layout(placeable.width, placeable.height) {
                    placeable.placeRelative(0, 0)
                }
            }
    } else {
        modifier
    }

    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = baseModifier,
        valueRange = valueRange,
        interactionSource = interactionSource,
        onValueChangeFinished = onValueChangeFinished,
        enabled = enabled,
        colors = SliderDefaults.colors(
            thumbColor = Color.White,
            activeTrackColor = Color.Transparent,
            inactiveTrackColor = Color.Transparent,
            disabledThumbColor = Color.White.copy(alpha = ContentAlpha.disabled),
            disabledActiveTrackColor = Color.Transparent,
            disabledInactiveTrackColor = Color.Transparent
        ),
        thumb = {
            // Empty/invisible thumb - we'll draw our own
            Box(modifier = Modifier.size(0.dp))
        },
        track = { sliderState ->
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .height(actualTrackThickness)
            ) {
                val valueRangeStart = sliderState.valueRange.start
                val valueRangeEnd = sliderState.valueRange.endInclusive
                val totalValueSpan = valueRangeEnd - valueRangeStart

                val normalizedCurrentValue = (sliderState.value - valueRangeStart) / totalValueSpan
                val normalizedZeroPoint = (0f - valueRangeStart) / totalValueSpan

                val activeTrackStartFraction = min(normalizedCurrentValue, normalizedZeroPoint)
                val activeTrackWidthFraction = abs(normalizedCurrentValue - normalizedZeroPoint)

                // Inactive track (dimmer)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(if (enabled) sliderColor.copy(alpha = 0.3f) else sliderColor.copy(alpha = ContentAlpha.disabled * 0.3f))
                )
                // Active track (brighter)
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(maxWidth * activeTrackWidthFraction)
                        .offset(x = maxWidth * activeTrackStartFraction)
                        .background(if (enabled) sliderColor.copy(alpha = 0.75f) else sliderColor.copy(alpha = ContentAlpha.disabled * 0.75f))
                )

                // Draw custom thumb
                val isPhone = isPhoneDevice()
                Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val thumbX = size.width * normalizedCurrentValue
                    val thumbWidth = 3.dp.toPx()
                    val thumbHeightFraction = if (isPhone) 1.0f else 0.7f // 100% on phone, 70% on tablet
                    val thumbHeight = size.height * thumbHeightFraction
                    val thumbOffsetY = (size.height - thumbHeight) / 2f // Center vertically

                    // Thumb - vertical line
                    drawRect(
                        color = if (enabled) Color.White else Color.White.copy(alpha = ContentAlpha.disabled),
                        topLeft = Offset(thumbX - thumbWidth / 2f, thumbOffsetY),
                        size = Size(thumbWidth, thumbHeight)
                    )
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoCenterBidirectionalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    orientation: SliderOrientation = SliderOrientation.HORIZONTAL,
    sliderColor: Color = Color.Magenta,
    trackBackgroundColor: Color = sliderColor.copy(alpha = 0.24f),
    valueRange: ClosedFloatingPointRange<Float> = -1f..1f,
    centerValue: Float = 0f,
    thumbSize: DpSize? = null,
    trackThickness: Dp? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    onActualValueChangeFinished: (() -> Unit)? = null,
    enabled: Boolean = true,
    displayedValue: String = "",
    isValueEditable: Boolean = false,
    onDisplayedValueChange: (String) -> Unit = {},
    onValueCommit: (String) -> Unit = {},
    valueUnit: String = "",
    valueTextColor: Color = Color.White
) {
    val responsiveSizes = getResponsiveSliderSizes()
    val actualThumbSize = thumbSize ?: responsiveSizes.thumbSize
    val actualTrackThickness = trackThickness ?: responsiveSizes.trackThickness
    val baseModifier = if (orientation == SliderOrientation.VERTICAL) {
        modifier
            .graphicsLayer { rotationZ = -90f }
            .layout { measurable, constraints ->
                val placeable = measurable.measure(
                    constraints.copy(
                        minWidth = constraints.minHeight,
                        maxWidth = constraints.maxHeight,
                        minHeight = constraints.minWidth,
                        maxHeight = constraints.maxWidth
                    )
                )
                layout(placeable.width, placeable.height) {
                    placeable.placeRelative(0, 0)
                }
            }
    } else {
        modifier
    }

    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = baseModifier,
        valueRange = valueRange,
        interactionSource = interactionSource,
        onValueChangeFinished = {
            onValueChange(centerValue)
            onActualValueChangeFinished?.invoke()
        },
        enabled = enabled,
        colors = SliderDefaults.colors(
            thumbColor = Color.White,
            activeTrackColor = Color.Transparent,
            inactiveTrackColor = Color.Transparent,
            disabledThumbColor = Color.White.copy(alpha = ContentAlpha.disabled),
            disabledActiveTrackColor = Color.Transparent,
            disabledInactiveTrackColor = Color.Transparent
        ),
        thumb = {
            // Empty/invisible thumb - we'll draw our own
            Box(modifier = Modifier.size(0.dp))
        },
        track = { sliderState ->
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .height(actualTrackThickness)
            ) {
                val valueRangeStart = sliderState.valueRange.start
                val valueRangeEnd = sliderState.valueRange.endInclusive
                val totalValueSpan = valueRangeEnd - valueRangeStart

                val normalizedCurrentValue = (sliderState.value - valueRangeStart) / totalValueSpan
                val normalizedVisualCenter = (centerValue.coerceIn(valueRangeStart, valueRangeEnd) - valueRangeStart) / totalValueSpan

                val activeTrackStartFraction = min(normalizedCurrentValue, normalizedVisualCenter)
                val activeTrackWidthFraction = abs(normalizedCurrentValue - normalizedVisualCenter)

                // Inactive track (dimmer)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(if (enabled) sliderColor.copy(alpha = 0.3f) else sliderColor.copy(alpha = ContentAlpha.disabled * 0.3f))
                )
                // Active track (brighter)
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(maxWidth * activeTrackWidthFraction)
                        .offset(x = maxWidth * activeTrackStartFraction)
                        .background(if (enabled) sliderColor.copy(alpha = 0.75f) else sliderColor.copy(alpha = ContentAlpha.disabled * 0.75f))
                )

                // Draw custom thumb
                val isPhone = isPhoneDevice()
                Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val thumbX = size.width * normalizedCurrentValue
                    val thumbWidth = 3.dp.toPx()
                    val thumbHeightFraction = if (isPhone) 1.0f else 0.7f // 100% on phone, 70% on tablet
                    val thumbHeight = size.height * thumbHeightFraction
                    val thumbOffsetY = (size.height - thumbHeight) / 2f // Center vertically

                    // Thumb - vertical line
                    drawRect(
                        color = if (enabled) Color.White else Color.White.copy(alpha = ContentAlpha.disabled),
                        topLeft = Offset(thumbX - thumbWidth / 2f, thumbOffsetY),
                        size = Size(thumbWidth, thumbHeight)
                    )
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidthExpansionSlider(
    value: Float, // Expected to be 0f to 1f, represents the proportion of expansion
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    orientation: SliderOrientation = SliderOrientation.HORIZONTAL,
    sliderColor: Color = Color.Cyan,
    trackBackgroundColor: Color = sliderColor.copy(alpha = 0.24f),
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    thumbSize: DpSize? = null,
    trackThickness: Dp? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    onValueChangeFinished: (() -> Unit)? = null,
    enabled: Boolean = true,
    hideThumbOnRelease: Boolean = false,
    displayedValue: String = "",
    isValueEditable: Boolean = false,
    onDisplayedValueChange: (String) -> Unit = {},
    onValueCommit: (String) -> Unit = {},
    valueUnit: String = "",
    valueTextColor: Color = Color.White
) {
    val responsiveSizes = getResponsiveSliderSizes()
    val finalThumbSize = thumbSize ?: responsiveSizes.thumbSize
    val finalTrackThickness = trackThickness ?: responsiveSizes.trackThickness
    
    if (isValueEditable && displayedValue.isNotEmpty()) {
        // Slider with editable number box
        if (orientation == SliderOrientation.HORIZONTAL) {
            Row(
                modifier = modifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WidthExpansionSliderCore(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f),
                    orientation = orientation,
                    sliderColor = sliderColor,
                    trackBackgroundColor = trackBackgroundColor,
                    valueRange = valueRange,
                    thumbSize = finalThumbSize,
                    trackThickness = finalTrackThickness,
                    interactionSource = interactionSource,
                    onValueChangeFinished = onValueChangeFinished,
                    enabled = true, // Always enabled for interaction, greying is visual only
                    hideThumbOnRelease = hideThumbOnRelease
                )
                EditableValueBox(
                    displayedValue = displayedValue,
                    valueUnit = valueUnit,
                    valueTextColor = valueTextColor,
                    onDisplayedValueChange = onDisplayedValueChange,
                    onValueCommit = onValueCommit,
                    enabled = enabled, // Visual greying only
                    modifier = Modifier.width(100.dp)
                )
            }
        } else {
            Column(
                modifier = modifier.wrapContentWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EditableValueBox(
                    displayedValue = displayedValue,
                    valueUnit = valueUnit,
                    valueTextColor = valueTextColor,
                    onDisplayedValueChange = onDisplayedValueChange,
                    onValueCommit = onValueCommit,
                    enabled = enabled, // Visual greying only
                    modifier = Modifier.width(90.dp) // Compact number box for vertical sliders
                )
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.TopCenter
                ) {
                    WidthExpansionSliderCore(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier.fillMaxHeight().width(40.dp), // Constrain track width
                        orientation = orientation,
                        sliderColor = sliderColor,
                        trackBackgroundColor = trackBackgroundColor,
                        valueRange = valueRange,
                        thumbSize = finalThumbSize,
                        trackThickness = finalTrackThickness,
                        interactionSource = interactionSource,
                        onValueChangeFinished = onValueChangeFinished,
                        enabled = true, // Always enabled for interaction, greying is visual only
                        hideThumbOnRelease = hideThumbOnRelease
                    )
                }
            }
        }
    } else {
        // Slider without editable box (backward compatibility)
        WidthExpansionSliderCore(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            orientation = orientation,
            sliderColor = sliderColor,
            trackBackgroundColor = trackBackgroundColor,
            valueRange = valueRange,
            thumbSize = finalThumbSize,
            trackThickness = finalTrackThickness,
            interactionSource = interactionSource,
            onValueChangeFinished = onValueChangeFinished,
            enabled = true, // Always enabled
            hideThumbOnRelease = hideThumbOnRelease
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WidthExpansionSliderCore(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier,
    orientation: SliderOrientation,
    sliderColor: Color,
    trackBackgroundColor: Color,
    valueRange: ClosedFloatingPointRange<Float>,
    thumbSize: DpSize?,
    trackThickness: Dp?,
    interactionSource: MutableInteractionSource,
    onValueChangeFinished: (() -> Unit)?,
    enabled: Boolean,
    hideThumbOnRelease: Boolean
) {
    val responsiveSizes = getResponsiveSliderSizes()
    val actualThumbSize = thumbSize ?: responsiveSizes.thumbSize
    val actualTrackThickness = trackThickness ?: responsiveSizes.trackThickness
    
    // Convert width expansion value (0-1) to raw slider position (0-1)
    // Inverse formula: if widthExpansion = 2 * abs(0.5 - raw), then raw = 0.5 ± (widthExpansion / 2)
    val rawSliderValue = if (value <= 0.5f) {
        0.5f - (value / 2f)
    } else {
        0.5f + (value / 2f)
    }

    val baseModifier = if (orientation == SliderOrientation.VERTICAL) {
        modifier
            .graphicsLayer { rotationZ = -90f }
            .layout { measurable, constraints ->
                val placeable = measurable.measure(
                    constraints.copy(
                        minWidth = constraints.minHeight,
                        maxWidth = constraints.maxHeight,
                        minHeight = constraints.minWidth,
                        maxHeight = constraints.maxWidth
                    )
                )
                layout(placeable.width, placeable.height) {
                    placeable.placeRelative(0, 0)
                }
            }
    } else {
        modifier
    }

    Slider(
        value = rawSliderValue,
        onValueChange = { rawValue ->
            // Convert raw slider value (0-1) to width expansion value (0-1)
            // Formula: 2 * abs(0.5 - rawValue)
            val widthExpansionValue = 2f * abs(0.5f - rawValue)
            onValueChange(widthExpansionValue)
        },
        modifier = baseModifier,
        valueRange = valueRange,
        interactionSource = interactionSource,
        onValueChangeFinished = onValueChangeFinished,
        enabled = enabled,
        colors = SliderDefaults.colors(
            thumbColor = Color.Transparent, // Invisible thumb
            activeTrackColor = Color.Transparent,
            inactiveTrackColor = Color.Transparent,
            disabledThumbColor = Color.Transparent,
            disabledActiveTrackColor = Color.Transparent,
            disabledInactiveTrackColor = Color.Transparent
        ),
        thumb = {
            // Empty/invisible thumb
            Box(modifier = Modifier.size(0.dp))
        },
        track = { sliderState ->
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .height(actualTrackThickness)
            ) {
                val valueRangeStart = sliderState.valueRange.start
                val valueRangeEnd = sliderState.valueRange.endInclusive
                val totalValueSpan = valueRangeEnd - valueRangeStart

                val normalizedCurrentValue = (sliderState.value - valueRangeStart) / totalValueSpan
                val normalizedCenter = 0.5f // Center of the slider (0.5 = middle)

                // Calculate the active track that grows from center
                val activeTrackWidthFraction = abs(normalizedCurrentValue - normalizedCenter) * 2f // *2 because we want full width at extremes
                val activeTrackStartFraction = normalizedCenter - (activeTrackWidthFraction / 2f)

                // Inactive track (dimmer)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(if (enabled) sliderColor.copy(alpha = 0.3f) else sliderColor.copy(alpha = ContentAlpha.disabled * 0.3f))
                )
                // Active track (brighter)
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(maxWidth * activeTrackWidthFraction)
                        .offset(x = maxWidth * activeTrackStartFraction)
                        .background(if (enabled) sliderColor.copy(alpha = 0.75f) else sliderColor.copy(alpha = ContentAlpha.disabled * 0.75f))
                )

                // Draw thumbs on both ends of the active track as vertical lines
                val isPhone = isPhoneDevice()
                Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val leftThumbX = size.width * activeTrackStartFraction
                    val rightThumbX = size.width * (activeTrackStartFraction + activeTrackWidthFraction)
                    val thumbWidth = 3.dp.toPx()
                    val thumbHeightFraction = if (isPhone) 1.0f else 0.7f // 100% on phone, 70% on tablet
                    val thumbHeight = size.height * thumbHeightFraction
                    val thumbOffsetY = (size.height - thumbHeight) / 2f // Center vertically

                    // Left thumb - vertical line
                    drawRect(
                        color = if (enabled) Color.White else Color.White.copy(alpha = ContentAlpha.disabled),
                        topLeft = Offset(leftThumbX - thumbWidth / 2f, thumbOffsetY),
                        size = Size(thumbWidth, thumbHeight)
                    )

                    // Right thumb - vertical line
                    drawRect(
                        color = if (enabled) Color.White else Color.White.copy(alpha = ContentAlpha.disabled),
                        topLeft = Offset(rightThumbX - thumbWidth / 2f, thumbOffsetY),
                        size = Size(thumbWidth, thumbHeight)
                    )
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StandardSlider(
    value: Float, // Expected to be 0f to 1f
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    orientation: SliderOrientation = SliderOrientation.HORIZONTAL,
    sliderColor: Color = Color.Red,
    trackBackgroundColor: Color = sliderColor.copy(alpha = 0.24f),
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    thumbSize: DpSize? = null,
    trackThickness: Dp? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    onValueChangeFinished: (() -> Unit)? = null,
    enabled: Boolean = true,
    displayedValue: String = "",
    isValueEditable: Boolean = false,
    onDisplayedValueChange: (String) -> Unit = {},
    onValueCommit: (String) -> Unit = {},
    valueUnit: String = "",
    valueTextColor: Color = Color.White
) {
    val responsiveSizes = getResponsiveSliderSizes()
    val finalThumbSize = thumbSize ?: responsiveSizes.thumbSize
    val finalTrackThickness = trackThickness ?: responsiveSizes.trackThickness
    
    if (isValueEditable && displayedValue.isNotEmpty()) {
        // Slider with editable number box
        if (orientation == SliderOrientation.HORIZONTAL) {
            Row(
                modifier = modifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StandardSliderCore(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f),
                    orientation = orientation,
                    sliderColor = sliderColor,
                    trackBackgroundColor = trackBackgroundColor,
                    valueRange = valueRange,
                    thumbSize = finalThumbSize,
                    trackThickness = finalTrackThickness,
                    interactionSource = interactionSource,
                    onValueChangeFinished = onValueChangeFinished,
                    enabled = true // Always enabled for interaction, greying is visual only
                )
                EditableValueBox(
                    displayedValue = displayedValue,
                    valueUnit = valueUnit,
                    valueTextColor = valueTextColor,
                    onDisplayedValueChange = onDisplayedValueChange,
                    onValueCommit = onValueCommit,
                    enabled = enabled, // Visual greying only
                    modifier = Modifier.width(100.dp)
                )
            }
        } else {
            Column(
                modifier = modifier.wrapContentWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EditableValueBox(
                    displayedValue = displayedValue,
                    valueUnit = valueUnit,
                    valueTextColor = valueTextColor,
                    onDisplayedValueChange = onDisplayedValueChange,
                    onValueCommit = onValueCommit,
                    enabled = enabled, // Visual greying only
                    modifier = Modifier.width(90.dp) // Compact number box for vertical sliders
                )
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.TopCenter
                ) {
                    StandardSliderCore(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier.fillMaxHeight().width(40.dp), // Constrain track width
                        orientation = orientation,
                        sliderColor = sliderColor,
                        trackBackgroundColor = trackBackgroundColor,
                        valueRange = valueRange,
                        thumbSize = finalThumbSize,
                        trackThickness = finalTrackThickness,
                        interactionSource = interactionSource,
                        onValueChangeFinished = onValueChangeFinished,
                        enabled = true // Always enabled for interaction, greying is visual only
                    )
                }
            }
        }
    } else {
        // Slider without editable box (backward compatibility)
        StandardSliderCore(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            orientation = orientation,
            sliderColor = sliderColor,
            trackBackgroundColor = trackBackgroundColor,
            valueRange = valueRange,
            thumbSize = finalThumbSize,
            trackThickness = finalTrackThickness,
            interactionSource = interactionSource,
            onValueChangeFinished = onValueChangeFinished,
            enabled = true // Always enabled
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StandardSliderCore(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier,
    orientation: SliderOrientation,
    sliderColor: Color,
    trackBackgroundColor: Color,
    valueRange: ClosedFloatingPointRange<Float>,
    thumbSize: DpSize?,
    trackThickness: Dp?,
    interactionSource: MutableInteractionSource,
    onValueChangeFinished: (() -> Unit)?,
    enabled: Boolean
) {
    val responsiveSizes = getResponsiveSliderSizes()
    val actualThumbSize = thumbSize ?: responsiveSizes.thumbSize
    val actualTrackThickness = trackThickness ?: responsiveSizes.trackThickness
    
    val baseModifier = if (orientation == SliderOrientation.VERTICAL) {
        modifier
            .graphicsLayer { rotationZ = -90f }
            .layout { measurable, constraints ->
                val placeable = measurable.measure(
                    constraints.copy(
                        minWidth = constraints.minHeight,
                        maxWidth = constraints.maxHeight,
                        minHeight = constraints.minWidth,
                        maxHeight = constraints.maxWidth
                    )
                )
                layout(placeable.width, placeable.height) {
                    placeable.placeRelative(0, 0)
                }
            }
    } else {
        modifier
    }

    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = baseModifier,
        valueRange = valueRange,
        interactionSource = interactionSource,
        onValueChangeFinished = onValueChangeFinished,
        enabled = enabled,
        colors = SliderDefaults.colors(
            thumbColor = Color.White,
            activeTrackColor = Color.Transparent,
            inactiveTrackColor = Color.Transparent,
            disabledThumbColor = Color.White.copy(alpha = ContentAlpha.disabled),
            disabledActiveTrackColor = Color.Transparent,
            disabledInactiveTrackColor = Color.Transparent
        ),
        thumb = {
            // Empty/invisible thumb - we'll draw our own
            Box(modifier = Modifier.size(0.dp))
        },
        track = { sliderState ->
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .height(actualTrackThickness)
            ) {
                val valueRangeStart = sliderState.valueRange.start
                val valueRangeEnd = sliderState.valueRange.endInclusive
                val totalValueSpan = valueRangeEnd - valueRangeStart

                val normalizedCurrentValue = (sliderState.value - valueRangeStart) / totalValueSpan

                // Standard slider: active track goes from start to current position
                val activeTrackWidthFraction = normalizedCurrentValue
                val activeTrackStartFraction = 0f

                // Inactive track (dimmer)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(if (enabled) sliderColor.copy(alpha = 0.3f) else sliderColor.copy(alpha = ContentAlpha.disabled * 0.3f))
                )
                // Active track (brighter)
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(maxWidth * activeTrackWidthFraction)
                        .offset(x = maxWidth * activeTrackStartFraction)
                        .background(if (enabled) sliderColor.copy(alpha = 0.75f) else sliderColor.copy(alpha = ContentAlpha.disabled * 0.75f))
                )

                // Draw custom thumb
                val isPhone = isPhoneDevice()
                Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val thumbX = size.width * normalizedCurrentValue
                    val thumbWidth = 3.dp.toPx()
                    val thumbHeightFraction = if (isPhone) 1.0f else 0.7f // 100% on phone, 70% on tablet
                    val thumbHeight = size.height * thumbHeightFraction
                    val thumbOffsetY = (size.height - thumbHeight) / 2f // Center vertically

                    // Thumb - vertical line
                    drawRect(
                        color = if (enabled) Color.White else Color.White.copy(alpha = ContentAlpha.disabled),
                        topLeft = Offset(thumbX - thumbWidth / 2f, thumbOffsetY),
                        size = Size(thumbWidth, thumbHeight)
                    )
                }
            }
        }
    )
}

private object ContentAlpha {
    const val disabled: Float = 0.38f
}

/**
 * BidirectionalSlider variant with number box positioned on the right side
 * for vertical sliders. The number box is vertically centered with the middle
 * of the slider, providing a visual hint for the center position.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BidirectionalSliderWithSideBox(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    sliderColor: Color = Color.Blue,
    trackBackgroundColor: Color = sliderColor.copy(alpha = 0.24f),
    valueRange: ClosedFloatingPointRange<Float> = -1f..1f,
    thumbSize: DpSize? = null,
    trackThickness: Dp? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    onValueChangeFinished: (() -> Unit)? = null,
    enabled: Boolean = true,
    displayedValue: String = "",
    onDisplayedValueChange: (String) -> Unit = {},
    onValueCommit: (String) -> Unit = {},
    valueUnit: String = "",
    valueTextColor: Color = Color.White,
    sliderHeight: Dp = 200.dp
) {
    val responsiveSizes = getResponsiveSliderSizes()
    val finalThumbSize = thumbSize ?: responsiveSizes.thumbSize
    val finalTrackThickness = trackThickness ?: responsiveSizes.trackThickness

    // Vertical slider with number box on the right, centered
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Slider on the left
        Box(
            modifier = Modifier.height(sliderHeight),
            contentAlignment = Alignment.Center
        ) {
            BidirectionalSliderCore(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxHeight().width(40.dp),
                orientation = SliderOrientation.VERTICAL,
                sliderColor = sliderColor,
                trackBackgroundColor = trackBackgroundColor,
                valueRange = valueRange,
                thumbSize = finalThumbSize,
                trackThickness = finalTrackThickness,
                interactionSource = interactionSource,
                onValueChangeFinished = onValueChangeFinished,
                enabled = true // Always enabled for interaction, greying is visual only
            )
        }

        // Number box on the right, vertically centered with slider
        EditableValueBox(
            displayedValue = displayedValue,
            valueUnit = valueUnit,
            valueTextColor = valueTextColor,
            onDisplayedValueChange = onDisplayedValueChange,
            onValueCommit = onValueCommit,
            enabled = enabled,
            modifier = Modifier.width(90.dp)
        )
    }
}

/**
 * Editable value box for sliders
 */
@Composable
fun EditableValueBox(
    displayedValue: String,
    valueUnit: String,
    valueTextColor: Color,
    onDisplayedValueChange: (String) -> Unit,
    onValueCommit: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    var textFieldValue by remember { mutableStateOf(TextFieldValue(displayedValue)) }
    val focusManager = LocalFocusManager.current
    
    // Update text field when displayedValue changes (from slider movement)
    LaunchedEffect(displayedValue) {
        textFieldValue = TextFieldValue(
            displayedValue, 
            selection = TextRange(displayedValue.length)
        )
    }
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(48.dp)
            .background(Color.DarkGray, shape = RoundedCornerShape(4.dp))
            .border(1.dp, if (enabled) Color.White else Color.Gray, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp)
    ) {
        BasicTextField(
            value = textFieldValue,
            onValueChange = { newValue ->
                textFieldValue = newValue
                onDisplayedValueChange(newValue.text)
            },
            enabled = true, // Always enabled for editing, visual greying via color
            textStyle = TextStyle(
                color = if (enabled) valueTextColor else Color.Gray,
                fontSize = 14.sp,
                textAlign = TextAlign.End
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    onValueCommit(textFieldValue.text)
                    focusManager.clearFocus()
                }
            ),
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        
        if (valueUnit.isNotEmpty()) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = valueUnit,
                fontSize = 14.sp,
                color = if (enabled) valueTextColor else Color.Gray
            )
        }
    }
}
