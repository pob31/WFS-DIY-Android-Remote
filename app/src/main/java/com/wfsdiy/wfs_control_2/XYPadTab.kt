package com.wfsdiy.wfs_control_2

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import android.view.MotionEvent
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Zone configuration received from JUCE for a single pad.
 */
data class PadZoneConfig(
    val zoneId: Int,
    val inputChannel: Int,  // 1-based, 0 = unassigned
    val color: Color
)

/**
 * Pad grid layout: columns × rows. JUCE selects the layout.
 * @param columns Number of columns (3 or 5)
 * @param rows Number of rows (2 or 3)
 */
data class PadGridLayout(val columns: Int, val rows: Int) {
    val totalPads: Int get() = columns * rows

    companion object {
        val GRID_3x2 = PadGridLayout(3, 2)   // 6 pads
        val GRID_5x3 = PadGridLayout(5, 3)   // 15 pads
    }
}

/**
 * XY Pad tab — virtual Lightpad for sampler control.
 * Displays touch-sensitive pads in a configurable grid (3×2 or 5×3).
 * Each pad acts as a joystick: deflection from initial touch point
 * generates dx/dy, and contact area approximates pressure.
 */
@Composable
fun XYPadTab(
    padZones: List<PadZoneConfig>,
    gridLayout: PadGridLayout,
    sensitivity: Float,
    onPadTouch: (zoneId: Int, touchState: Int, dx: Float, dy: Float, pressure: Float) -> Unit
) {
    val cols = gridLayout.columns
    val totalPads = gridLayout.totalPads

    // Default gray pads when no config received yet
    val zones = remember(padZones, totalPads) {
        if (padZones.isEmpty()) {
            List(totalPads) { PadZoneConfig(zoneId = it, inputChannel = 0, color = Color(0xFF444444)) }
        } else {
            // Pad or trim to match grid size
            val list = padZones.take(totalPads).toMutableList()
            while (list.size < totalPads) {
                list.add(PadZoneConfig(zoneId = list.size, inputChannel = 0, color = Color(0xFF444444)))
            }
            list
        }
    }

    // Arrange into rows
    val rows = zones.chunked(cols)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        rows.forEach { rowZones ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowZones.forEach { zone ->
                    XYPad(
                        zone = zone,
                        onPadTouch = onPadTouch,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
                // Fill remaining columns if row isn't full
                repeat(cols - rowZones.size) {
                    Spacer(modifier = Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

/**
 * Normalize pressure from Compose's pressure property or a touchMajor fallback.
 * @param composePressure The value from PointerInputChange.pressure
 * @param touchMajor The native MotionEvent touchMajor (contact ellipse major axis in px)
 */
// Default calibration range for touchMajor (device-dependent).
// Lenovo Tab P12 reports ~0.1f (light fingertip) to ~5f (palm).
// TODO: replace with user calibration
private var touchMajorMin = 0.1f
private var touchMajorMax = 5f

private fun normalizePressure(composePressure: Float, touchMajor: Float): Float {
    // Prefer touchMajor — it correlates with contact area and actually varies
    // on most capacitive touchscreens. MotionEvent.pressure is often constant.
    if (touchMajor > 0f) {
        val range = (touchMajorMax - touchMajorMin).coerceAtLeast(0.1f)
        return ((touchMajor - touchMajorMin) / range).coerceIn(0f, 1f)
    }

    // Fallback to framework pressure if touchMajor unavailable
    if (composePressure > 0.01f && composePressure < 0.99f) return composePressure

    return 0.5f
}

/**
 * A single touch-sensitive XY pad.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun XYPad(
    zone: PadZoneConfig,
    onPadTouch: (zoneId: Int, touchState: Int, dx: Float, dy: Float, pressure: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    // Single source of truth for all touch state — written by pointerInteropFilter,
    // read by both drawBehind (for the dot) and composable content (for debug text).
    val touchPosState = remember { mutableStateOf<Offset?>(null) }
    val touchPressureState = remember { mutableFloatStateOf(0f) }
    var touchActive by remember { mutableStateOf(false) }
    var debugComposePressure by remember { mutableFloatStateOf(0f) }
    var debugTouchMajor by remember { mutableFloatStateOf(0f) }
    var debugTouchMinor by remember { mutableFloatStateOf(0f) }
    var debugDx by remember { mutableFloatStateOf(0f) }
    var debugDy by remember { mutableFloatStateOf(0f) }

    // Start position for joystick deflection
    var startPos by remember { mutableStateOf(Offset.Zero) }

    // Track pad pixel dimensions for deflection normalization
    var padWidthPx by remember { mutableFloatStateOf(1f) }
    var padHeightPx by remember { mutableFloatStateOf(1f) }

    val isUnassigned = zone.inputChannel == 0
    val bgColor = if (isUnassigned) Color(0xFF333333) else zone.color.copy(alpha = 0.3f)
    val borderColor = if (isUnassigned) Color(0xFF555555) else zone.color

    Box(
        modifier = modifier
            .border(2.dp, borderColor, RoundedCornerShape(8.dp))
            .background(bgColor, RoundedCornerShape(8.dp))
            .onSizeChanged { intSize ->
                padWidthPx = intSize.width.toFloat().coerceAtLeast(1f)
                padHeightPx = intSize.height.toFloat().coerceAtLeast(1f)
            }
            .pointerInteropFilter { motionEvent ->
                val action = motionEvent.actionMasked
                val meX = motionEvent.x
                val meY = motionEvent.y
                val mePressure = motionEvent.pressure
                val meTouchMajor = motionEvent.touchMajor
                val meTouchMinor = motionEvent.touchMinor

                when (action) {
                    MotionEvent.ACTION_DOWN -> {
                        val normP = normalizePressure(mePressure, meTouchMajor)
                        Snapshot.withMutableSnapshot {
                            startPos = Offset(meX, meY)
                            touchPosState.value = Offset(meX, meY)
                            touchPressureState.floatValue = normP
                            touchActive = true
                            debugComposePressure = mePressure
                            debugTouchMajor = meTouchMajor
                            debugTouchMinor = meTouchMinor
                            debugDx = 0f
                            debugDy = 0f
                        }
                        onPadTouch(zone.zoneId, 1, 0f, 0f, normP)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val normP = normalizePressure(mePressure, meTouchMajor)
                        val dx = (meX - startPos.x) / padWidthPx
                        val dy = -(meY - startPos.y) / padHeightPx
                        Snapshot.withMutableSnapshot {
                            touchPosState.value = Offset(meX, meY)
                            touchPressureState.floatValue = normP
                            debugComposePressure = mePressure
                            debugTouchMajor = meTouchMajor
                            debugTouchMinor = meTouchMinor
                            debugDx = dx
                            debugDy = dy
                        }

                        val throttleKey = "pad_touch_${zone.zoneId}"
                        if (OscThrottleManager.shouldSend(throttleKey)) {
                            onPadTouch(zone.zoneId, 2, dx, dy, normP)
                        } else {
                            OscThrottleManager.storePending(throttleKey) {
                                onPadTouch(zone.zoneId, 2, dx, dy, normP)
                            }
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        Snapshot.withMutableSnapshot {
                            touchPosState.value = null
                            touchPressureState.floatValue = 0f
                            touchActive = false
                            debugDx = 0f
                            debugDy = 0f
                        }
                        onPadTouch(zone.zoneId, 0, 0f, 0f, 0f)
                    }
                }
                true
            }
    ) {
        // Layer 1: Touch feedback dot — sized directly from debugTouchMajor
        // which we know updates (the Maj: debug text proves it).
        val density = LocalDensity.current
        val dotPos = touchPosState.value
        // Use debugTouchMajor directly — bypass normalization entirely for the dot
        val currentMajor = debugTouchMajor

        if (dotPos != null) {
            // Dot diameter from normalized pressure (0-1 from touchMajor range).
            // 16% of pad at lightest touch, 80% at firmest.
            val minDim = minOf(padWidthPx, padHeightPx)
            val normP = normalizePressure(0f, currentMajor) // force touchMajor path
            val targetDiameterPx = (minDim * (0.16f + normP * 0.64f))
                .coerceIn(40f, minDim * 0.8f)
            // Smooth the diameter transitions to reduce jitter from discrete touchMajor steps
            val dotDiameterPx by animateFloatAsState(
                targetValue = targetDiameterPx,
                animationSpec = tween(durationMillis = 80, easing = FastOutSlowInEasing),
                label = "dotSize"
            )
            val dotSizeDp = with(density) { dotDiameterPx.toDp() }
            val offsetXDp = with(density) { (dotPos.x - dotDiameterPx / 2f).toDp() }
            val offsetYDp = with(density) { (dotPos.y - dotDiameterPx / 2f).toDp() }

            key(currentMajor, dotPos) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                ) {
                    Box(
                        modifier = Modifier
                            .offset(x = offsetXDp, y = offsetYDp)
                            .size(dotSizeDp)
                            .background(
                                Color.White.copy(alpha = 0.7f),
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                    )
                    // Debug: show diameter next to dot
                    Text(
                        text = "d:%.0f m:%.0f".format(dotDiameterPx, currentMajor),
                        color = Color.Red,
                        fontSize = 10.sp,
                        modifier = Modifier.offset(
                            x = with(density) { (dotPos.x + dotDiameterPx / 2f + 4f).toDp() },
                            y = with(density) { (dotPos.y - 8f).toDp() }
                        )
                    )
                }
            }
        }

        // Layer 2: Text labels — centered
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isUnassigned) "—" else zone.inputChannel.toString(),
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            // Debug: show raw values from digitizer
            if (touchActive) {
                Text(
                    text = "P: %.3f".format(debugComposePressure),
                    color = Color.Yellow,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Maj: %.1f  Min: %.1f".format(debugTouchMajor, debugTouchMinor),
                    color = Color.Yellow,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Norm: %.2f".format(touchPressureState.floatValue),
                    color = Color.Cyan,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "X: %.2f  Y: %.2f".format(debugDx, debugDy),
                    color = Color.Green,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }
            if (!isUnassigned) {
                Text(
                    text = "Zone ${zone.zoneId}",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
