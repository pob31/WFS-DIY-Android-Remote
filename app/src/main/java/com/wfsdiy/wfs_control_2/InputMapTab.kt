package com.wfsdiy.wfs_control_2

import android.graphics.Paint
import android.graphics.Typeface
import android.annotation.SuppressLint
import androidx.compose.foundation.Canvas
import kotlin.math.pow
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Helper functions for vector control calculations
fun calculateAngle(from: Offset, to: Offset): Float {
    return Math.toDegrees(atan2((to.y - from.y).toDouble(), (to.x - from.x).toDouble())).toFloat()
}

fun calculateDistance(from: Offset, to: Offset): Float {
    val dx = to.x - from.x
    val dy = to.y - from.y
    return sqrt(dx * dx + dy * dy)
}

fun calculateRelativeDistanceChange(initialDistance: Float, currentDistance: Float): Float {
    return if (initialDistance > 0f) (currentDistance - initialDistance) / initialDistance else 0f
}

/**
 * Find the barycenter position for a cluster if it's in barycenter mode.
 * Returns null if the cluster is not in barycenter mode or has no tracked input.
 */
fun findClusterBarycenter(
    clusterId: Int,
    markers: List<Marker>,
    clusterConfigs: List<ClusterConfig>
): Offset? {
    val config = clusterConfigs.find { it.id == clusterId } ?: return null

    // Only return barycenter if in barycenter mode (referenceMode == 1) and no tracked input
    if (config.referenceMode != 1 || config.trackedInputId != 0) return null

    val clusterMembers = markers.filter { it.clusterId == clusterId && it.isVisible }
    if (clusterMembers.size < 2) return null

    val sumX = clusterMembers.sumOf { it.positionX.toDouble() }.toFloat()
    val sumY = clusterMembers.sumOf { it.positionY.toDouble() }.toFloat()
    return Offset(sumX / clusterMembers.size, sumY / clusterMembers.size)
}

// Assuming drawMarker is in MapElements.kt or accessible
// internal fun DrawScope.drawMarker( ... )

fun DrawScope.drawStageCoordinates(
    stageWidth: Float,
    stageDepth: Float,
    canvasWidthPx: Float,
    canvasHeightPx: Float,
    markerRadius: Float = 0f,
    // Pan/zoom parameters for view transformation
    panOffsetX: Float = 0f,
    panOffsetY: Float = 0f,
    pixelsPerMeter: Float = 0f,  // Uniform scale
    actualViewWidth: Float = stageWidth,
    actualViewHeight: Float = stageDepth
) {
    if (stageWidth <= 0f || stageDepth <= 0f) return

    val effectiveCanvasWidth = canvasWidthPx - (markerRadius * 2f)
    val effectiveCanvasHeight = canvasHeightPx - (markerRadius * 2f)

    // Use provided pixelsPerMeter or calculate from stage dimensions
    val ppm = if (pixelsPerMeter > 0f) pixelsPerMeter else {
        min(effectiveCanvasWidth / stageWidth, effectiveCanvasHeight / stageDepth)
    }

    val viewWidth = if (actualViewWidth > 0f) actualViewWidth else stageWidth
    val viewHeight = if (actualViewHeight > 0f) actualViewHeight else stageDepth

    val lineColor = Color.DarkGray
    val lineStrokeWidth = 1f

    // Calculate visible range in physical stage meters
    val viewCenterX = panOffsetX
    val viewCenterY = panOffsetY
    val viewMinX = viewCenterX - viewWidth / 2f
    val viewMaxX = viewCenterX + viewWidth / 2f
    val viewMinY = viewCenterY - viewHeight / 2f
    val viewMaxY = viewCenterY + viewHeight / 2f

    // Helper to convert physical stage position to canvas pixels
    fun physicalToCanvas(physX: Float, physY: Float): Offset {
        val viewX = physX - panOffsetX
        val viewY = physY - panOffsetY
        val canvasX = (viewX / viewWidth + 0.5f) * effectiveCanvasWidth + markerRadius
        val canvasY = (0.5f - viewY / viewHeight) * effectiveCanvasHeight + markerRadius
        return Offset(canvasX, canvasY)
    }

    // Calculate grid line spacing - always 1m
    val gridSpacing = 1f

    // Clamp grid line count to avoid performance issues
    val maxGridLines = 200

    // Horizontal lines (constant Y in physical coords)
    val startY = ceil(viewMinY / gridSpacing) * gridSpacing
    val endY = floor(viewMaxY / gridSpacing) * gridSpacing
    var lineCount = 0
    var yPhys = startY
    while (yPhys <= endY && lineCount < maxGridLines) {
        val leftPoint = physicalToCanvas(viewMinX, yPhys)
        val rightPoint = physicalToCanvas(viewMaxX, yPhys)
        if (leftPoint.y >= markerRadius && leftPoint.y <= canvasHeightPx - markerRadius) {
            drawLine(
                color = lineColor,
                start = Offset(markerRadius, leftPoint.y),
                end = Offset(canvasWidthPx - markerRadius, leftPoint.y),
                strokeWidth = lineStrokeWidth
            )
        }
        yPhys += gridSpacing
        lineCount++
    }

    // Vertical lines (constant X in physical coords)
    val startX = ceil(viewMinX / gridSpacing) * gridSpacing
    val endX = floor(viewMaxX / gridSpacing) * gridSpacing
    lineCount = 0
    var xPhys = startX
    while (xPhys <= endX && lineCount < maxGridLines) {
        val topPoint = physicalToCanvas(xPhys, viewMaxY)
        val bottomPoint = physicalToCanvas(xPhys, viewMinY)
        if (topPoint.x >= markerRadius && topPoint.x <= canvasWidthPx - markerRadius) {
            drawLine(
                color = lineColor,
                start = Offset(topPoint.x, markerRadius),
                end = Offset(topPoint.x, canvasHeightPx - markerRadius),
                strokeWidth = lineStrokeWidth
            )
        }
        xPhys += gridSpacing
        lineCount++
    }
}


/**
 * Convert stage coordinates (meters) to canvas pixel position with pan/zoom support.
 * @param stageX X position in meters (relative to displayed origin)
 * @param stageY Y position in meters (relative to displayed origin)
 * @param stageOriginX Stage origin X offset in meters
 * @param stageOriginY Stage origin Y offset in meters
 * @param canvasWidth Canvas width in pixels
 * @param canvasHeight Canvas height in pixels
 * @param markerRadius Marker radius in pixels (for effective area calculation)
 * @param panOffsetX Pan offset X in physical stage meters (default 0)
 * @param panOffsetY Pan offset Y in physical stage meters (default 0)
 * @param actualViewWidth Visible width in meters after zoom (default uses stageWidth for backward compat)
 * @param actualViewHeight Visible height in meters after zoom (default uses stageDepth for backward compat)
 * @param stageWidth Stage width in meters (used as default for actualViewWidth)
 * @param stageDepth Stage depth in meters (used as default for actualViewHeight)
 * @return Offset with canvas X and Y in pixels
 */
fun stageToCanvasPosition(
    stageX: Float,
    stageY: Float,
    stageWidth: Float,
    stageDepth: Float,
    stageOriginX: Float,
    stageOriginY: Float,
    canvasWidth: Float,
    canvasHeight: Float,
    markerRadius: Float,
    panOffsetX: Float = 0f,
    panOffsetY: Float = 0f,
    actualViewWidth: Float = stageWidth,
    actualViewHeight: Float = stageDepth
): Offset {
    if (canvasWidth <= 0f || canvasHeight <= 0f) {
        return Offset(canvasWidth / 2f, canvasHeight / 2f)
    }

    val effectiveWidth = canvasWidth - (markerRadius * 2f)
    val effectiveHeight = canvasHeight - (markerRadius * 2f)

    val viewWidth = if (actualViewWidth > 0f) actualViewWidth else stageWidth
    val viewHeight = if (actualViewHeight > 0f) actualViewHeight else stageDepth

    // Convert origin-relative stage position to physical stage position
    val physicalX = stageX + stageOriginX
    val physicalY = stageY + stageOriginY

    // Apply pan offset (pan is in physical stage meters)
    val viewX = physicalX - panOffsetX
    val viewY = physicalY - panOffsetY

    // Convert to canvas pixels with uniform scale
    // Center of view maps to center of canvas
    val canvasX = (viewX / viewWidth + 0.5f) * effectiveWidth + markerRadius
    val canvasY = (0.5f - viewY / viewHeight) * effectiveHeight + markerRadius

    return Offset(canvasX, canvasY)
}

/**
 * Convert canvas pixel position to stage coordinates (meters) with pan/zoom support.
 * @param canvasX X position in canvas pixels
 * @param canvasY Y position in canvas pixels
 * @param stageOriginX Stage origin X offset in meters
 * @param stageOriginY Stage origin Y offset in meters
 * @param canvasWidth Canvas width in pixels
 * @param canvasHeight Canvas height in pixels
 * @param markerRadius Marker radius in pixels (for effective area calculation)
 * @param panOffsetX Pan offset X in physical stage meters (default 0)
 * @param panOffsetY Pan offset Y in physical stage meters (default 0)
 * @param actualViewWidth Visible width in meters after zoom (default uses stageWidth for backward compat)
 * @param actualViewHeight Visible height in meters after zoom (default uses stageDepth for backward compat)
 * @param stageWidth Stage width in meters (used as default for actualViewWidth)
 * @param stageDepth Stage depth in meters (used as default for actualViewHeight)
 * @return Pair of (stageX, stageY) in meters
 */
fun canvasToStagePosition(
    canvasX: Float,
    canvasY: Float,
    stageWidth: Float,
    stageDepth: Float,
    stageOriginX: Float,
    stageOriginY: Float,
    canvasWidth: Float,
    canvasHeight: Float,
    markerRadius: Float,
    panOffsetX: Float = 0f,
    panOffsetY: Float = 0f,
    actualViewWidth: Float = stageWidth,
    actualViewHeight: Float = stageDepth
): Pair<Float, Float> {
    if (canvasWidth <= 0f || canvasHeight <= 0f) {
        return Pair(0f, 0f)
    }

    val effectiveWidth = canvasWidth - (markerRadius * 2f)
    val effectiveHeight = canvasHeight - (markerRadius * 2f)

    val viewWidth = if (actualViewWidth > 0f) actualViewWidth else stageWidth
    val viewHeight = if (actualViewHeight > 0f) actualViewHeight else stageDepth

    // Canvas to view-relative
    val viewX = ((canvasX - markerRadius) / effectiveWidth - 0.5f) * viewWidth
    val viewY = (0.5f - (canvasY - markerRadius) / effectiveHeight) * viewHeight

    // Apply pan offset to get physical position
    val physicalX = viewX + panOffsetX
    val physicalY = viewY + panOffsetY

    // Convert to stage-relative (origin-relative)
    val stageX = physicalX - stageOriginX
    val stageY = physicalY - stageOriginY

    return Pair(stageX, stageY)
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun InputMapTab(
    numberOfInputs: Int,
    markers: List<Marker>,
    refreshTrigger: Int = 0,  // Increments when tab becomes visible to force position refresh
    onMarkersInitiallyPositioned: (List<Marker>) -> Unit,
    onCanvasSizeChanged: (width: Float, height: Float) -> Unit,
    initialLayoutDone: Boolean,
    onInitialLayoutDone: () -> Unit,
    stageWidth: Float,
    stageDepth: Float,
    stageOriginX: Float,
    stageOriginY: Float,
    stageShape: Int = 0,           // 0=box, 1=cylinder, 2=dome
    stageDiameter: Float = 20f,    // Used for cylinder/dome shapes
    domeElevation: Float = 180f,   // Used for dome shape
    inputSecondaryAngularMode: SecondaryTouchFunction = SecondaryTouchFunction.OFF,
    inputSecondaryRadialMode: SecondaryTouchFunction = SecondaryTouchFunction.OFF,
    clusterConfigs: List<ClusterConfig> = emptyList(),
    onClusterMove: ((clusterId: Int, deltaX: Float, deltaY: Float) -> Unit)? = null,
    onBarycenterMove: ((clusterId: Int, deltaX: Float, deltaY: Float) -> Unit)? = null,
    inputParametersState: InputParametersState? = null,
    onPositionChanged: ((inputId: Int, positionX: Float, positionY: Float) -> Unit)? = null,
    compositePositions: Map<Int, Pair<Float, Float>> = emptyMap()  // inputId -> (deltaX, deltaY) in stage meters
) {
    val context = LocalContext.current
    val draggingMarkers = remember { mutableStateMapOf<Long, Int>() }
    val draggingBarycenters = remember { mutableStateMapOf<Long, Int>() }  // pointerId -> clusterId
    val currentMarkersState by rememberUpdatedState(markers)

    // Local state for smooth dragging without blocking global updates
    val localMarkerPositions = remember { mutableStateMapOf<Int, Offset>() }

    // Store marker positions in stage meters (true position, independent of view)
    // This allows proper recalculation when pan/zoom changes
    // Only markers with "real" positions (from server or dragging) are stored here
    val markerStagePositions = remember { mutableStateMapOf<Int, Pair<Float, Float>>() }

    // Track the last known view parameters for markers without stage positions
    // This allows us to properly transform their canvas positions when view changes
    // Using a simple data holder (4 floats: panX, panY, viewW, viewH)
    var lastViewPanX by remember { mutableFloatStateOf(0f) }
    var lastViewPanY by remember { mutableFloatStateOf(0f) }
    var lastViewWidth by remember { mutableFloatStateOf(0f) }
    var lastViewHeight by remember { mutableFloatStateOf(0f) }
    var lastViewInitialized by remember { mutableStateOf(false) }

    // Vector control state for secondary touches
    data class VectorControl(
        val markerId: Int,
        val initialMarkerPosition: Offset,
        val initialTouchPosition: Offset,
        val currentTouchPosition: Offset
    )
    val vectorControls = remember { mutableStateMapOf<Long, VectorControl>() }
    var vectorControlsUpdateTrigger: Int by remember { mutableIntStateOf(0) }
    
    // Calculate responsive marker radius
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthDp = configuration.screenWidthDp.dp
    val screenHeightDp = configuration.screenHeightDp.dp
    val screenDensity = density.density
    
    // Use physical screen size instead of density-independent pixels
    val physicalWidthInches = screenWidthDp.value / 160f // Convert dp to inches (160 dp = 1 inch)
    val physicalHeightInches = screenHeightDp.value / 160f
    val diagonalInches = sqrt(physicalWidthInches * physicalWidthInches + physicalHeightInches * physicalHeightInches)
    
    // Consider devices with diagonal < 6 inches as phones (adjusted for modern phones)
    val isPhone = diagonalInches < 6.0f
    
    val baseMarkerRadius = if (isPhone) {
        // Small markers for phones
        2.75f
    } else {
        // Good size markers for tablets
        ((screenWidthDp.value / 50f) * 3f).coerceIn(24f, 52.5f)
    }
    val responsiveMarkerRadius = (baseMarkerRadius * screenDensity).coerceIn(0.5f, 17.5f)
    val markerRadius = responsiveMarkerRadius.dpToPx()

    val textPaint = remember {
        Paint().apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            // textSize will be set in drawMarker based on marker.isVisible and zoom
        }
    }
    
    // Clear vector controls when both secondary touch functions are OFF
    LaunchedEffect(inputSecondaryAngularMode, inputSecondaryRadialMode) {
        if (inputSecondaryAngularMode == SecondaryTouchFunction.OFF && inputSecondaryRadialMode == SecondaryTouchFunction.OFF) {
            vectorControls.clear()
            vectorControlsUpdateTrigger++
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val canvasWidth = constraints.maxWidth.toFloat()
        val canvasHeight = constraints.maxHeight.toFloat()
        val pickupRadiusMultiplier = 1.25f

        // Pan/zoom state variables
        // Pan offset in physical stage meters (center of view)
        var panOffsetX by remember { mutableFloatStateOf(0f) }
        var panOffsetY by remember { mutableFloatStateOf(0f) }

        // View size in meters (what's visible in the current zoom level)
        var viewWidthMeters by remember { mutableFloatStateOf(stageWidth) }
        var viewHeightMeters by remember { mutableFloatStateOf(stageDepth) }

        // Zoom limits
        val minViewSize = max(2f, min(stageWidth, stageDepth))  // Min zoom: show at least full stage
        val maxViewSize = 100f  // Max zoom out: 100m (±50m from origin)

        // Calculate uniform scale (same pixels-per-meter for X and Y)
        val effectiveCanvasWidth = canvasWidth - (markerRadius * 2f)
        val effectiveCanvasHeight = canvasHeight - (markerRadius * 2f)
        val canvasAspect = if (effectiveCanvasHeight > 0f) effectiveCanvasWidth / effectiveCanvasHeight else 1f
        val viewAspect = if (viewHeightMeters > 0f) viewWidthMeters / viewHeightMeters else 1f

        val pixelsPerMeter = if (effectiveCanvasWidth > 0f && effectiveCanvasHeight > 0f) {
            if (canvasAspect > viewAspect) {
                // Canvas is wider than view - height constrains
                effectiveCanvasHeight / viewHeightMeters
            } else {
                // Canvas is taller than view - width constrains
                effectiveCanvasWidth / viewWidthMeters
            }
        } else 1f

        // Actual visible meters (may be larger than requested on one axis due to uniform scale)
        val actualViewWidth = if (pixelsPerMeter > 0f) effectiveCanvasWidth / pixelsPerMeter else viewWidthMeters
        val actualViewHeight = if (pixelsPerMeter > 0f) effectiveCanvasHeight / pixelsPerMeter else viewHeightMeters

        // Track if any marker is being dragged (for gesture priority)
        val isDraggingAnyMarker by remember {
            derivedStateOf { draggingMarkers.isNotEmpty() || draggingBarycenters.isNotEmpty() }
        }

        // Fit stage to screen function
        fun fitStageToScreen() {
            panOffsetX = 0f
            panOffsetY = 0f
            val margin = 1.1f  // 10% margin
            viewWidthMeters = stageWidth * margin
            viewHeightMeters = stageDepth * margin
        }

        // Fit all inputs to screen function
        fun fitAllInputsToScreen() {
            val visibleMarkers = currentMarkersState.take(numberOfInputs).filter { it.isVisible }
            if (visibleMarkers.isEmpty()) {
                fitStageToScreen()
                return
            }

            var minPhysX = Float.MAX_VALUE
            var maxPhysX = Float.MIN_VALUE
            var minPhysY = Float.MAX_VALUE
            var maxPhysY = Float.MIN_VALUE

            visibleMarkers.forEach { marker ->
                // Use stored stage positions (in meters) - these are the true positions
                val stagePos = markerStagePositions[marker.id]
                if (stagePos != null) {
                    val physX = stagePos.first + stageOriginX
                    val physY = stagePos.second + stageOriginY
                    minPhysX = min(minPhysX, physX)
                    maxPhysX = max(maxPhysX, physX)
                    minPhysY = min(minPhysY, physY)
                    maxPhysY = max(maxPhysY, physY)
                }
            }

            // If no stored positions, fall back to fitting stage
            if (minPhysX == Float.MAX_VALUE) {
                fitStageToScreen()
                return
            }

            // Center on bounding box center
            panOffsetX = (minPhysX + maxPhysX) / 2f
            panOffsetY = (minPhysY + maxPhysY) / 2f

            // Set view to encompass all markers with margin
            val margin = 1.3f  // 30% margin for markers at edges
            val spanX = (maxPhysX - minPhysX) * margin
            val spanY = (maxPhysY - minPhysY) * margin
            viewWidthMeters = max(spanX, 2f).coerceIn(minViewSize, maxViewSize)
            viewHeightMeters = max(spanY, 2f).coerceIn(minViewSize, maxViewSize)
        }

        // Reset view when stage dimensions change
        LaunchedEffect(stageWidth, stageDepth) {
            if (stageWidth > 0f && stageDepth > 0f) {
                fitStageToScreen()
            }
        }

        // Update shared canvas dimensions and call onCanvasSizeChanged
        LaunchedEffect(canvasWidth, canvasHeight, markerRadius) {
            if (canvasWidth > 0f && canvasHeight > 0f) {
                CanvasDimensions.updateDimensions(canvasWidth, canvasHeight)
                CanvasDimensions.updateMarkerRadius(markerRadius)
                onCanvasSizeChanged(canvasWidth, canvasHeight)
            } else {

            }
        }

        // Update marker stage positions from server inputParametersState
        // refreshTrigger forces update when returning to this tab
        // This stores the TRUE positions in stage meters (independent of view)
        LaunchedEffect(inputParametersState?.revision, refreshTrigger, stageWidth, stageDepth, stageOriginX, stageOriginY, numberOfInputs) {
            if (inputParametersState != null && stageWidth > 0f && stageDepth > 0f && numberOfInputs > 0) {
                (1..numberOfInputs).forEach { inputId ->
                    val channel = inputParametersState.getChannel(inputId)
                    val posXParam = channel.parameters["positionX"]
                    val posYParam = channel.parameters["positionY"]

                    if (posXParam != null || posYParam != null) {
                        val posXDef = InputParameterDefinitions.allParameters.find { it.variableName == "positionX" }
                        val posYDef = InputParameterDefinitions.allParameters.find { it.variableName == "positionY" }

                        // Get existing stored position for fallback
                        val existingStagePos = markerStagePositions[inputId]

                        val posXMeters = if (posXParam != null && posXDef != null) {
                            InputParameterDefinitions.applyFormula(posXDef, posXParam.normalizedValue)
                        } else if (posXParam != null) {
                            posXParam.normalizedValue * 100f - 50f
                        } else {
                            existingStagePos?.first ?: 0f
                        }
                        val posYMeters = if (posYParam != null && posYDef != null) {
                            InputParameterDefinitions.applyFormula(posYDef, posYParam.normalizedValue)
                        } else if (posYParam != null) {
                            posYParam.normalizedValue * 100f - 50f
                        } else {
                            existingStagePos?.second ?: 0f
                        }

                        // Store the stage position (in meters)
                        markerStagePositions[inputId] = Pair(posXMeters, posYMeters)
                    }
                }
            }
        }

        // Recalculate canvas positions when view changes (pan/zoom)
        // Only affects markers that have stored stage positions (from server or dragging)
        // Markers without stage positions keep their canvas positions and get transformed relative to view changes
        LaunchedEffect(panOffsetX, panOffsetY, actualViewWidth, actualViewHeight, canvasWidth, canvasHeight, markerRadius, stageOriginX, stageOriginY, numberOfInputs) {
            if (canvasWidth > 0f && canvasHeight > 0f && numberOfInputs > 0 && actualViewWidth > 0f && actualViewHeight > 0f) {
                val hasPrevView = lastViewInitialized && lastViewWidth > 0f && lastViewHeight > 0f

                val updatedMarkers = currentMarkersState.mapIndexed { index, marker ->
                    if (index < numberOfInputs) {
                        val inputId = marker.id
                        val stagePos = markerStagePositions[inputId]

                        if (stagePos != null) {
                            // Convert stage position to canvas pixels using current view
                            val canvasPos = stageToCanvasPosition(
                                stageX = stagePos.first,
                                stageY = stagePos.second,
                                stageWidth = stageWidth,
                                stageDepth = stageDepth,
                                stageOriginX = stageOriginX,
                                stageOriginY = stageOriginY,
                                canvasWidth = canvasWidth,
                                canvasHeight = canvasHeight,
                                markerRadius = markerRadius,
                                panOffsetX = panOffsetX,
                                panOffsetY = panOffsetY,
                                actualViewWidth = actualViewWidth,
                                actualViewHeight = actualViewHeight
                            )

                            // Clear local position if it differs significantly (server correction)
                            val localPos = localMarkerPositions[inputId]
                            if (localPos != null) {
                                val correctionThreshold = 5f
                                val distanceFromLocal = sqrt(
                                    (canvasPos.x - localPos.x).pow(2) + (canvasPos.y - localPos.y).pow(2)
                                )
                                if (distanceFromLocal > correctionThreshold) {
                                    localMarkerPositions.remove(inputId)
                                }
                            }

                            marker.copy(positionX = canvasPos.x, positionY = canvasPos.y)
                        } else if (hasPrevView && marker.positionX > 0f && marker.positionY > 0f) {
                            // No stage position stored - transform canvas position based on view change
                            // First convert current canvas pos to stage using OLD view params
                            val (stageX, stageY) = canvasToStagePosition(
                                canvasX = marker.positionX,
                                canvasY = marker.positionY,
                                stageWidth = stageWidth,
                                stageDepth = stageDepth,
                                stageOriginX = stageOriginX,
                                stageOriginY = stageOriginY,
                                canvasWidth = canvasWidth,
                                canvasHeight = canvasHeight,
                                markerRadius = markerRadius,
                                panOffsetX = lastViewPanX,
                                panOffsetY = lastViewPanY,
                                actualViewWidth = lastViewWidth,
                                actualViewHeight = lastViewHeight
                            )
                            // Then convert stage pos to canvas using NEW view params
                            val newCanvasPos = stageToCanvasPosition(
                                stageX = stageX,
                                stageY = stageY,
                                stageWidth = stageWidth,
                                stageDepth = stageDepth,
                                stageOriginX = stageOriginX,
                                stageOriginY = stageOriginY,
                                canvasWidth = canvasWidth,
                                canvasHeight = canvasHeight,
                                markerRadius = markerRadius,
                                panOffsetX = panOffsetX,
                                panOffsetY = panOffsetY,
                                actualViewWidth = actualViewWidth,
                                actualViewHeight = actualViewHeight
                            )
                            marker.copy(positionX = newCanvasPos.x, positionY = newCanvasPos.y)
                        } else {
                            marker
                        }
                    } else {
                        marker
                    }
                }

                // Update lastView params for next frame
                lastViewPanX = panOffsetX
                lastViewPanY = panOffsetY
                lastViewWidth = actualViewWidth
                lastViewHeight = actualViewHeight
                lastViewInitialized = true

                val hasChanges = updatedMarkers.zip(currentMarkersState).any { (new, old) ->
                    new.positionX != old.positionX || new.positionY != old.positionY
                }

                if (hasChanges) {
                    onMarkersInitiallyPositioned(updatedMarkers)
                }
            }
        }

        // Initialize lastView params when view is first calculated
        LaunchedEffect(actualViewWidth, actualViewHeight) {
            if (actualViewWidth > 0f && actualViewHeight > 0f && !lastViewInitialized) {
                lastViewPanX = panOffsetX
                lastViewPanY = panOffsetY
                lastViewWidth = actualViewWidth
                lastViewHeight = actualViewHeight
                lastViewInitialized = true
            }
        }

        // Update marker names from server (separate from position updates)
        LaunchedEffect(inputParametersState?.revision, numberOfInputs) {
            if (inputParametersState != null && numberOfInputs > 0) {
                val updatedMarkers = currentMarkersState.mapIndexed { index, marker ->
                    if (index < numberOfInputs) {
                        val channel = inputParametersState.getChannel(marker.id)
                        val inputName = channel.parameters["inputName"]?.stringValue ?: ""
                        if (inputName.isNotEmpty() && inputName != marker.name) {
                            marker.copy(name = inputName)
                        } else {
                            marker
                        }
                    } else {
                        marker
                    }
                }

                val hasNameChanges = updatedMarkers.zip(currentMarkersState).any { (new, old) ->
                    new.name != old.name
                }

                if (hasNameChanges) {
                    onMarkersInitiallyPositioned(updatedMarkers)
                }
            }
        }

        LaunchedEffect(canvasWidth, canvasHeight, initialLayoutDone, numberOfInputs) {
            if (canvasWidth > 0f && canvasHeight > 0f && !initialLayoutDone && numberOfInputs > 0) {
                // Check if we have position data from the server before applying grid layout
                // If inputParametersState has position data for any input, skip grid layout
                val hasServerPositionData = inputParametersState?.let { state ->
                    (1..numberOfInputs).any { inputId ->
                        val channel = state.getChannel(inputId)
                        channel.parameters["positionX"] != null || channel.parameters["positionY"] != null
                    }
                } ?: false

                if (hasServerPositionData) {
                    // Server has sent positions, mark layout as done without applying grid
                    onInitialLayoutDone()
                    return@LaunchedEffect
                }

                val numCols = 8
                val numRows = (numberOfInputs + numCols - 1) / numCols

                // Calculate responsive spacing factor based on screen size
                val baseSpacingFactor = (screenWidthDp.value / 4f).coerceIn(60f, 100f) // 60-100dp range (more compact)
                val spacingFactor = baseSpacingFactor

                val contentWidthOfCenters = (numCols - 1) * spacingFactor
                val contentHeightOfCenters = (numRows - 1) * spacingFactor
                val totalVisualWidth = contentWidthOfCenters + markerRadius * 2f
                val totalVisualHeight = contentHeightOfCenters + markerRadius * 2f

                val centeredStartX = ((canvasWidth - totalVisualWidth) / 2f) + markerRadius
                val centeredStartY = ((canvasHeight - totalVisualHeight) / 2f) + markerRadius

                val newFullMarkersList = currentMarkersState.mapIndexed { originalIndex, marker ->
                    if (originalIndex < numberOfInputs) { // Only update positions for *active* markers
                        val indexForCalc = originalIndex // Use originalIndex for grid calculation
                        val logicalCol = indexForCalc % numCols
                        var logicalRow = indexForCalc / numCols
                        if (numRows > 1) { // Reverse row order for Y if multi-row
                            logicalRow = (numRows - 1) - logicalRow
                        }
                        val xPos = centeredStartX + logicalCol * spacingFactor
                        val yPos = centeredStartY + logicalRow * spacingFactor

                        marker.copy(
                            positionX = xPos.coerceIn(markerRadius, canvasWidth - markerRadius),
                            positionY = yPos.coerceIn(markerRadius, canvasHeight - markerRadius)
                        )
                    } else {
                        // For markers beyond numberOfInputs, return them unchanged
                        marker
                    }
                }
                onMarkersInitiallyPositioned(newFullMarkersList)
                onInitialLayoutDone()
            } else if (numberOfInputs == 0 && !initialLayoutDone) {
                // If numberOfInputs is 0, reset positions
                val resetMarkersList = currentMarkersState.map { it.copy(positionX = 0f, positionY = 0f) }
                onMarkersInitiallyPositioned(resetMarkersList)
                onInitialLayoutDone()
            }
        }

        // Wrap Canvas in Box for floating buttons overlay
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(modifier = Modifier
                .fillMaxSize()
                // Combined gesture handling for marker dragging and pan/zoom
                .pointerInput(stageWidth, stageDepth, panOffsetX, panOffsetY, actualViewWidth, actualViewHeight, pixelsPerMeter) {
                    awaitEachGesture {
                        val pointerIdToCurrentLogicalPosition = mutableMapOf<PointerId, Offset>()
                        val pointersThatAttemptedGrab = mutableSetOf<PointerId>()

                        // Track pointers for pan/zoom gesture
                        var previousPanZoomPointers = mapOf<Long, Offset>()
                        var isPanZoomActive = false

                    while (true) {
                        val event = awaitPointerEvent()
                        val activeMarkersSnapshot = currentMarkersState.take(numberOfInputs).toMutableList()

                        event.changes.forEach { change ->
                            val pointerId = change.id
                            val pointerValue = change.id.value // Using Long as key for draggingMarkers
                            
                            if (change.pressed) {
                                if (!draggingMarkers.containsKey(pointerValue)) {
                                    // Check if this pointer has a vector control first
                                    if (vectorControls.containsKey(pointerValue)) {
                                        // Handle secondary finger movement
                                        val vectorControl = vectorControls[pointerValue]
                                        if (vectorControl != null) {
                                            // Update the current touch position
                                            val updatedVectorControl = vectorControl.copy(currentTouchPosition = change.position)
                                            vectorControls[pointerValue] = updatedVectorControl
                                            vectorControlsUpdateTrigger++ // Trigger recomposition
                                            
                                            // Calculate and send OSC messages asynchronously only if at least one function is enabled
                                            if (initialLayoutDone && (inputSecondaryAngularMode != SecondaryTouchFunction.OFF || inputSecondaryRadialMode != SecondaryTouchFunction.OFF)) {
                                                CoroutineScope(Dispatchers.IO).launch {
                                                    // Use local position if available, otherwise use global position
                                                    val currentMarkerPosition = if (localMarkerPositions.containsKey(vectorControl.markerId)) {
                                                        localMarkerPositions[vectorControl.markerId]!!
                                                    } else {
                                                        currentMarkersState.find { it.id == vectorControl.markerId }?.position
                                                    }

                                                    if (currentMarkerPosition != null) {
                                                        val initialAngle = calculateAngle(vectorControl.initialMarkerPosition, vectorControl.initialTouchPosition)
                                                        val currentAngle = calculateAngle(currentMarkerPosition, change.position)
                                                        val angleChange = currentAngle - initialAngle

                                                        val initialDistance = calculateDistance(vectorControl.initialMarkerPosition, vectorControl.initialTouchPosition)
                                                        val currentDistance = calculateDistance(currentMarkerPosition, change.position)
                                                        val distanceChange = calculateRelativeDistanceChange(initialDistance, currentDistance)

                                                        if (inputSecondaryAngularMode != SecondaryTouchFunction.OFF) {
                                                            sendOscMarkerAngleChange(context, vectorControl.markerId, inputSecondaryAngularMode.modeNumber, angleChange)
                                                        }
                                                        if (inputSecondaryRadialMode != SecondaryTouchFunction.OFF) {
                                                            sendOscMarkerRadialChange(context, vectorControl.markerId, inputSecondaryRadialMode.modeNumber, distanceChange)
                                                        }
                                                    }
                                                }
                                            }
                                            change.consume()
                                        }
                                    } else if (!pointersThatAttemptedGrab.contains(pointerId)) {
                                        pointersThatAttemptedGrab.add(pointerId)
                                        val touchPosition = change.position
                                        val candidateMarkers =
                                            activeMarkersSnapshot.filterIndexed { _, m -> // m is from activeMarkersSnapshot
                                                // Get original marker from currentMarkersState to check lock/visibility
                                                val originalMarker = currentMarkersState.getOrNull(m.id -1)
                                                originalMarker != null && originalMarker.isVisible &&
                                                        !originalMarker.isLocked &&
                                                        !draggingMarkers.containsValue(m.id) && // Check against marker ID
                                                        distance(
                                                            touchPosition,
                                                            m.position // m.position is from activeMarkersSnapshot
                                                        ) <= m.radius * pickupRadiusMultiplier
                                            }

                                        if (candidateMarkers.isNotEmpty()) {
                                            val markerToDrag = candidateMarkers.minWithOrNull(
                                                compareBy<Marker> { marker -> distance(touchPosition, marker.position) }
                                                    .thenBy { marker -> marker.id }
                                            )
                                            markerToDrag?.let {
                                                if (draggingMarkers.size < 10) { // Limit concurrent drags
                                                    draggingMarkers[pointerValue] = it.id // Store marker ID
                                                    pointerIdToCurrentLogicalPosition[pointerId] = it.position
                                                }
                                            }
                                        } else {
                                            // No marker in pickup range - check for barycenter first
                                            var barycenterFound = false
                                            if (clusterConfigs.isNotEmpty()) {
                                                // Check each cluster in barycenter mode
                                                for (clusterId in 1..10) {
                                                    if (draggingBarycenters.containsValue(clusterId)) continue
                                                    val barycenter = findClusterBarycenter(clusterId, currentMarkersState.take(numberOfInputs), clusterConfigs)
                                                    if (barycenter != null && distance(touchPosition, barycenter) <= markerRadius * pickupRadiusMultiplier) {
                                                        if (draggingBarycenters.size + draggingMarkers.size < 10) {
                                                            draggingBarycenters[pointerValue] = clusterId
                                                            pointerIdToCurrentLogicalPosition[pointerId] = barycenter
                                                            barycenterFound = true
                                                            break
                                                        }
                                                    }
                                                }
                                            }

                                            // If no barycenter found, check for vector control
                                            if (!barycenterFound && (inputSecondaryAngularMode != SecondaryTouchFunction.OFF || inputSecondaryRadialMode != SecondaryTouchFunction.OFF)) {
                                                val draggedMarkers = draggingMarkers.values.toSet()
                                                val markersWithVectorControl = vectorControls.values.map { it.markerId }.toSet()
                                                val availableMarkers = draggedMarkers - markersWithVectorControl
                                                
                                                if (availableMarkers.isNotEmpty()) {
                                                    // Find the closest dragged marker without vector control
                                                    val closestMarkerId = availableMarkers.minByOrNull { markerId ->
                                                        val marker = currentMarkersState.find { it.id == markerId }
                                                        marker?.let { distance(touchPosition, it.position) } ?: Float.MAX_VALUE
                                                    }
                                                    
                                                    closestMarkerId?.let { markerId ->
                                                        val marker = currentMarkersState.find { it.id == markerId }
                                                        marker?.let {
                                                            vectorControls[pointerValue] = VectorControl(
                                                                markerId = markerId,
                                                                initialMarkerPosition = it.position,
                                                                initialTouchPosition = touchPosition,
                                                                currentTouchPosition = touchPosition
                                                            )
                                                            vectorControlsUpdateTrigger++ // Trigger recomposition
                                                            
                                                            // Send initial OSC messages for secondary touch asynchronously
                                                            CoroutineScope(Dispatchers.IO).launch {
                                                                if (inputSecondaryAngularMode != SecondaryTouchFunction.OFF) {
                                                                    sendOscMarkerAngleChange(context, markerId, inputSecondaryAngularMode.modeNumber, 0f)
                                                                }
                                                                if (inputSecondaryRadialMode != SecondaryTouchFunction.OFF) {
                                                                    sendOscMarkerRadialChange(context, markerId, inputSecondaryRadialMode.modeNumber, 0.0f)
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        change.consume()
                                    }
                                } else { // Pointer is pressed, but already dragging (associated with this pointerValue)
                                    val markerIdBeingDragged = draggingMarkers[pointerValue]
                                    if (markerIdBeingDragged != null) {
                                        val originalGlobalIndex = currentMarkersState.indexOfFirst { it.id == markerIdBeingDragged }

                                        if (originalGlobalIndex != -1) {
                                             // Check lock state from the main currentMarkersState using originalGlobalIndex
                                            if (!currentMarkersState[originalGlobalIndex].isLocked) {
                                                val oldLogicalPosition = pointerIdToCurrentLogicalPosition[pointerId]
                                                if (oldLogicalPosition != null && change.positionChanged()) {
                                                    val dragDelta = change.position - change.previousPosition
                                                    val markerToMove = currentMarkersState[originalGlobalIndex] // Get the most up-to-date marker state for radius

                                                    val newLogicalPosition = Offset(
                                                        x = (oldLogicalPosition.x + dragDelta.x).coerceIn(markerRadius, canvasWidth - markerRadius),
                                                        y = (oldLogicalPosition.y + dragDelta.y).coerceIn(markerRadius, canvasHeight - markerRadius)
                                                    )
                                                    pointerIdToCurrentLogicalPosition[pointerId] = newLogicalPosition
                                                    
                                                    val updatedMarker = markerToMove.copy(positionX = newLogicalPosition.x, positionY = newLogicalPosition.y)
                                                    
                                                    // Update local state immediately for smooth visual feedback
                                                    localMarkerPositions[updatedMarker.id] = newLogicalPosition
                                                    
                                                    // Update global state asynchronously to avoid blocking
                                                    CoroutineScope(Dispatchers.Default).launch {
                                                        val newFullListForWFS = currentMarkersState.toMutableList()
                                                        newFullListForWFS[originalGlobalIndex] = updatedMarker
                                                        
                                                        withContext(Dispatchers.Main) {
                                                            onMarkersInitiallyPositioned(newFullListForWFS.toList())
                                                        }
                                                    }

                                                    // Send OSC messages asynchronously to avoid blocking
                                                    if (initialLayoutDone) {
                                                        // Check if this marker is a cluster reference
                                                        val markerClusterId = updatedMarker.clusterId
                                                        val clusterConfig = if (markerClusterId > 0) clusterConfigs.find { it.id == markerClusterId } else null
                                                        val isClusterReference = clusterConfig != null && (
                                                            clusterConfig.trackedInputId == updatedMarker.id ||
                                                            (clusterConfig.referenceMode == 0 && clusterConfig.trackedInputId == 0 &&
                                                                currentMarkersState.filter { it.clusterId == markerClusterId }.minByOrNull { it.id }?.id == updatedMarker.id)
                                                        )

                                                        // Convert pixel delta to stage coordinate delta
                                                        val effectiveWidth = canvasWidth - (markerRadius * 2f)
                                                        val effectiveHeight = canvasHeight - (markerRadius * 2f)
                                                        val deltaXMeters = (dragDelta.x / effectiveWidth) * stageWidth
                                                        val deltaYMeters = -(dragDelta.y / effectiveHeight) * stageDepth // Invert Y

                                                        // Calculate stage position for the marker
                                                        val (stageX, stageY) = canvasToStagePosition(
                                                            canvasX = updatedMarker.position.x,
                                                            canvasY = updatedMarker.position.y,
                                                            stageWidth = stageWidth,
                                                            stageDepth = stageDepth,
                                                            stageOriginX = stageOriginX,
                                                            stageOriginY = stageOriginY,
                                                            canvasWidth = canvasWidth,
                                                            canvasHeight = canvasHeight,
                                                            markerRadius = markerRadius,
                                                            panOffsetX = panOffsetX,
                                                            panOffsetY = panOffsetY,
                                                            actualViewWidth = actualViewWidth,
                                                            actualViewHeight = actualViewHeight
                                                        )

                                                        // Store the stage position for view change recalculations
                                                        markerStagePositions[updatedMarker.id] = Pair(stageX, stageY)

                                                        if (isClusterReference && clusterConfig != null && clusterConfig.referenceMode == 0) {
                                                            // Moving reference in First Input mode - send cluster move
                                                            onClusterMove?.invoke(markerClusterId, deltaXMeters, deltaYMeters)
                                                        } else {
                                                            // Individual marker move
                                                            onPositionChanged?.invoke(updatedMarker.id, stageX, stageY)
                                                        }

                                                        // Check if this marker has vector control and send OSC only if at least one function is enabled
                                                        if (inputSecondaryAngularMode != SecondaryTouchFunction.OFF || inputSecondaryRadialMode != SecondaryTouchFunction.OFF) {
                                                            CoroutineScope(Dispatchers.IO).launch {
                                                                vectorControls.values.forEach { vectorControl ->
                                                                    if (vectorControl.markerId == updatedMarker.id) {
                                                                        // Use local position for consistent calculations
                                                                        val currentMarkerPosition = localMarkerPositions[updatedMarker.id] ?: updatedMarker.position

                                                                        val initialAngle = calculateAngle(vectorControl.initialMarkerPosition, vectorControl.initialTouchPosition)
                                                                        val currentAngle = calculateAngle(currentMarkerPosition, vectorControl.currentTouchPosition)
                                                                        val angleChange = currentAngle - initialAngle

                                                                        val initialDistance = calculateDistance(vectorControl.initialMarkerPosition, vectorControl.initialTouchPosition)
                                                                        val currentDistance = calculateDistance(currentMarkerPosition, vectorControl.currentTouchPosition)
                                                                        val distanceChange = calculateRelativeDistanceChange(initialDistance, currentDistance)

                                                                        if (inputSecondaryAngularMode != SecondaryTouchFunction.OFF) {
                                                                            sendOscMarkerAngleChange(context, vectorControl.markerId, inputSecondaryAngularMode.modeNumber, angleChange)
                                                                        }
                                                                        if (inputSecondaryRadialMode != SecondaryTouchFunction.OFF) {
                                                                            sendOscMarkerRadialChange(context, vectorControl.markerId, inputSecondaryRadialMode.modeNumber, distanceChange)
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                    change.consume()
                                                }
                                            }
                                        }
                                    } else {
                                        // Check if we're dragging a barycenter
                                        val clusterIdBeingDragged = draggingBarycenters[pointerValue]
                                        if (clusterIdBeingDragged != null) {
                                            val oldLogicalPosition = pointerIdToCurrentLogicalPosition[pointerId]
                                            if (oldLogicalPosition != null && change.positionChanged()) {
                                                val dragDelta = change.position - change.previousPosition

                                                val newLogicalPosition = Offset(
                                                    x = (oldLogicalPosition.x + dragDelta.x).coerceIn(markerRadius, canvasWidth - markerRadius),
                                                    y = (oldLogicalPosition.y + dragDelta.y).coerceIn(markerRadius, canvasHeight - markerRadius)
                                                )
                                                pointerIdToCurrentLogicalPosition[pointerId] = newLogicalPosition

                                                // Convert pixel delta to stage coordinate delta
                                                val effectiveWidth = canvasWidth - (markerRadius * 2f)
                                                val effectiveHeight = canvasHeight - (markerRadius * 2f)
                                                val deltaXMeters = (dragDelta.x / effectiveWidth) * stageWidth
                                                val deltaYMeters = -(dragDelta.y / effectiveHeight) * stageDepth // Invert Y

                                                // Send barycenter move OSC command
                                                if (initialLayoutDone) {
                                                    onBarycenterMove?.invoke(clusterIdBeingDragged, deltaXMeters, deltaYMeters)
                                                }

                                                change.consume()
                                            }
                                        }
                                    }
                                }
                            } else { // Pointer released
                                if (draggingMarkers.containsKey(pointerValue)) {
                                    val releasedMarkerId = draggingMarkers.remove(pointerValue)!!
                                    pointerIdToCurrentLogicalPosition.remove(pointerId)
                                    
                                    // Clean up local position
                                    localMarkerPositions.remove(releasedMarkerId)
                                    
                                    // Clean up any vector controls associated with this marker
                                    vectorControls.entries.removeAll { (_, vectorControl) ->
                                        vectorControl.markerId == releasedMarkerId
                                    }
                                    vectorControlsUpdateTrigger++ // Trigger recomposition
                                    
                                    // OSC for released marker (use its final position from currentMarkersState)
                                    val finalMarkerState = currentMarkersState.find { it.id == releasedMarkerId }
                                    if (finalMarkerState != null && !finalMarkerState.isLocked && initialLayoutDone) {
                                        // Convert canvas position to stage meters
                                        val (stageX, stageY) = canvasToStagePosition(
                                            canvasX = finalMarkerState.position.x,
                                            canvasY = finalMarkerState.position.y,
                                            stageWidth = stageWidth,
                                            stageDepth = stageDepth,
                                            stageOriginX = stageOriginX,
                                            stageOriginY = stageOriginY,
                                            canvasWidth = canvasWidth,
                                            canvasHeight = canvasHeight,
                                            markerRadius = markerRadius,
                                            panOffsetX = panOffsetX,
                                            panOffsetY = panOffsetY,
                                            actualViewWidth = actualViewWidth,
                                            actualViewHeight = actualViewHeight
                                        )
                                        onPositionChanged?.invoke(finalMarkerState.id, stageX, stageY)
                                    }
                                } else if (vectorControls.containsKey(pointerValue)) {
                                    // Remove vector control when secondary touch is released
                                    vectorControls.remove(pointerValue)
                                    vectorControlsUpdateTrigger++ // Trigger recomposition
                                } else if (draggingBarycenters.containsKey(pointerValue)) {
                                    // Remove barycenter drag when released
                                    draggingBarycenters.remove(pointerValue)
                                    pointerIdToCurrentLogicalPosition.remove(pointerId)
                                }
                                pointersThatAttemptedGrab.remove(pointerId)
                                change.consume()
                            }
                        }

                        // Pan/zoom handling: check for 2+ pointers not used for markers/barycenters/vectors
                        val activePointers = event.changes.filter { it.pressed }
                        val freePointers = activePointers.filter { change ->
                            val pv = change.id.value
                            !draggingMarkers.containsKey(pv) &&
                                    !draggingBarycenters.containsKey(pv) &&
                                    !vectorControls.containsKey(pv)
                        }

                        if (freePointers.size >= 2 && draggingMarkers.isEmpty() && draggingBarycenters.isEmpty()) {
                            // Build current pointer positions
                            val currentPointers = freePointers.associate { it.id.value to it.position }

                            if (isPanZoomActive && previousPanZoomPointers.size >= 2) {
                                // Calculate pan (centroid movement)
                                val prevCentroid = Offset(
                                    previousPanZoomPointers.values.map { it.x }.average().toFloat(),
                                    previousPanZoomPointers.values.map { it.y }.average().toFloat()
                                )
                                val currCentroid = Offset(
                                    currentPointers.values.map { it.x }.average().toFloat(),
                                    currentPointers.values.map { it.y }.average().toFloat()
                                )
                                val panDelta = currCentroid - prevCentroid

                                // Calculate zoom (distance change between first two pointers)
                                val prevPointersList = previousPanZoomPointers.values.toList()
                                val currPointersList = currentPointers.values.toList()
                                if (prevPointersList.size >= 2 && currPointersList.size >= 2) {
                                    val prevDist = calculateDistance(prevPointersList[0], prevPointersList[1])
                                    val currDist = calculateDistance(currPointersList[0], currPointersList[1])

                                    if (prevDist > 10f) {  // Avoid division by small numbers
                                        val zoomFactor = currDist / prevDist

                                        // Apply zoom
                                        if (zoomFactor != 1f) {
                                            val newViewWidth = (viewWidthMeters / zoomFactor).coerceIn(minViewSize, maxViewSize)
                                            val newViewHeight = (viewHeightMeters / zoomFactor).coerceIn(minViewSize, maxViewSize)
                                            viewWidthMeters = newViewWidth
                                            viewHeightMeters = newViewHeight
                                        }
                                    }
                                }

                                // Apply pan
                                if (pixelsPerMeter > 0f) {
                                    val panMetersX = -panDelta.x / pixelsPerMeter
                                    val panMetersY = panDelta.y / pixelsPerMeter
                                    panOffsetX = (panOffsetX + panMetersX).coerceIn(-50f, 50f)
                                    panOffsetY = (panOffsetY + panMetersY).coerceIn(-50f, 50f)
                                }

                                // Consume events to prevent other handlers
                                freePointers.forEach { it.consume() }
                            }

                            previousPanZoomPointers = currentPointers
                            isPanZoomActive = true
                        } else {
                            // Reset pan/zoom tracking when we don't have 2+ free pointers
                            if (freePointers.size < 2) {
                                previousPanZoomPointers = emptyMap()
                                isPanZoomActive = false
                            }
                        }

                        if (event.changes.all { !it.pressed } && draggingMarkers.isEmpty()) {
                            break
                        }
                    }
                }
            }
        ) { // DrawScope
            drawRect(Color.Black) // Background for the canvas
            
            // Draw secondary touch function names above the grid
            val angularText = "Angular: ${inputSecondaryAngularMode.displayName}"
            val radialText = "Radial: ${inputSecondaryRadialMode.displayName}"
            val fullText = "Secondary Touch Functions\n$angularText | $radialText"

            val textPaint = Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 20f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            drawContext.canvas.nativeCanvas.drawText(
                fullText,
                canvasWidth / 2f,
                40f, // Position above the grid
                textPaint
            )
            
            // Draw the stage boundary (rectangle for box, circle for cylinder/dome)
            drawStageBoundary(
                stageShape = stageShape,
                stageWidth = stageWidth,
                stageDepth = stageDepth,
                stageDiameter = stageDiameter,
                stageOriginX = stageOriginX,
                stageOriginY = stageOriginY,
                canvasPixelW = canvasWidth,
                canvasPixelH = canvasHeight,
                markerRadius = markerRadius,
                panOffsetX = panOffsetX,
                panOffsetY = panOffsetY,
                actualViewWidth = actualViewWidth,
                actualViewHeight = actualViewHeight
            )

            // Draw the stage grid lines (only for box shape)
            if (stageShape == 0) {
                drawStageCoordinates(
                    stageWidth = stageWidth,
                    stageDepth = stageDepth,
                    canvasWidthPx = canvasWidth,
                    canvasHeightPx = canvasHeight,
                    markerRadius = markerRadius,
                    panOffsetX = panOffsetX,
                    panOffsetY = panOffsetY,
                    pixelsPerMeter = pixelsPerMeter,
                    actualViewWidth = actualViewWidth,
                    actualViewHeight = actualViewHeight
                )
            }

            // Draw stage labels appropriate for the shape
            drawStageLabels(
                stageShape = stageShape,
                stageWidth = stageWidth,
                stageDepth = stageDepth,
                stageDiameter = stageDiameter,
                stageOriginX = stageOriginX,
                stageOriginY = stageOriginY,
                canvasPixelW = canvasWidth,
                canvasPixelH = canvasHeight,
                markerRadius = markerRadius,
                panOffsetX = panOffsetX,
                panOffsetY = panOffsetY,
                actualViewWidth = actualViewWidth,
                actualViewHeight = actualViewHeight
            )

            // Draw origin marker at position where displayed coordinates would be (0.0, 0.0)
            drawOriginMarker(
                currentStageW = stageWidth,
                currentStageD = stageDepth,
                currentStageOriginX = stageOriginX,
                currentStageOriginY = stageOriginY,
                canvasPixelW = canvasWidth,
                canvasPixelH = canvasHeight,
                markerRadius = markerRadius,
                panOffsetX = panOffsetX,
                panOffsetY = panOffsetY,
                actualViewWidth = actualViewWidth,
                actualViewHeight = actualViewHeight
            )

            // Draw vector control lines only if at least one function is enabled
            if (inputSecondaryAngularMode != SecondaryTouchFunction.OFF || inputSecondaryRadialMode != SecondaryTouchFunction.OFF) {
                vectorControls.values.forEach { vectorControl ->
                    // Use local position if available, otherwise use global position
                    val currentMarkerPosition = if (localMarkerPositions.containsKey(vectorControl.markerId)) {
                        localMarkerPositions[vectorControl.markerId]!!
                    } else {
                        currentMarkersState.find { it.id == vectorControl.markerId }?.position
                    }
                    
                    if (currentMarkerPosition != null) {
                        // Calculate initial vector (from initial marker position to initial touch position)
                        val initialVector = vectorControl.initialTouchPosition - vectorControl.initialMarkerPosition
                        
                        // Draw grey reference line: same length and direction as initial vector, translated to current marker position
                        val greyLineEnd = currentMarkerPosition + initialVector
                        drawLine(
                            color = Color.Gray,
                            start = currentMarkerPosition,
                            end = greyLineEnd,
                            strokeWidth = 2f
                        )
                        
                        // Draw white active line (current marker position to current touch position)
                        drawLine(
                            color = Color.White,
                            start = currentMarkerPosition,
                            end = vectorControl.currentTouchPosition,
                            strokeWidth = 2f
                        )
                    }
                }
            }

            // Draw cluster relationship lines (behind markers)
            if (clusterConfigs.isNotEmpty()) {
                // Build markers with current positions (including local drag positions)
                val displayMarkers = currentMarkersState.take(numberOfInputs).map { marker ->
                    if (localMarkerPositions.containsKey(marker.id)) {
                        marker.copy(
                            positionX = localMarkerPositions[marker.id]!!.x,
                            positionY = localMarkerPositions[marker.id]!!.y
                        )
                    } else {
                        marker
                    }
                }
                drawClusterLines(
                    markers = displayMarkers,
                    clusterConfigs = clusterConfigs,
                    barycenterRadius = markerRadius * 0.6f
                )
            }

            // Draw composite position indicators (behind markers)
            // compositePositions contains delta values (composite - target) in stage meters
            compositePositions.forEach { (inputId, delta) ->
                val marker = currentMarkersState.find { it.id == inputId }
                if (marker != null && marker.isVisible && inputId <= numberOfInputs) {
                    // Get the target canvas position (use local position if dragging)
                    val targetCanvasPos = if (localMarkerPositions.containsKey(marker.id)) {
                        localMarkerPositions[marker.id]!!
                    } else {
                        marker.position
                    }

                    // Convert delta from stage meters to canvas pixels using actual view dimensions
                    val effWidth = canvasWidth - (markerRadius * 2f)
                    val effHeight = canvasHeight - (markerRadius * 2f)
                    val deltaCanvasX = if (actualViewWidth > 0f) (delta.first / actualViewWidth) * effWidth else 0f
                    val deltaCanvasY = if (actualViewHeight > 0f) -(delta.second / actualViewHeight) * effHeight else 0f  // Negative because Y is inverted

                    // Composite canvas position = target + delta
                    val compositeCanvasPos = Offset(
                        targetCanvasPos.x + deltaCanvasX,
                        targetCanvasPos.y + deltaCanvasY
                    )

                    // Only draw if the composite position differs from target by more than 5 pixels
                    if (distance(targetCanvasPos, compositeCanvasPos) > 5f) {
                        drawCompositePosition(targetCanvasPos, compositeCanvasPos, markerRadius)
                    }
                }
            }

            // Draw markers on top of the grid and labels
            currentMarkersState.take(numberOfInputs).sortedByDescending { it.id }.forEach { marker ->
                // Use local position if available for smooth dragging, otherwise use global position
                val displayMarker = if (localMarkerPositions.containsKey(marker.id)) {
                    marker.copy(
                        positionX = localMarkerPositions[marker.id]!!.x,
                        positionY = localMarkerPositions[marker.id]!!.y
                    )
                } else {
                    marker
                }

                // Assuming drawMarker is defined elsewhere and handles its own textPaint settings for visibility/zoom
                drawMarker(displayMarker, draggingMarkers.containsValue(marker.id), textPaint, false, stageWidth, stageDepth, stageOriginX, stageOriginY, canvasWidth, canvasHeight, !isPhone)
            }
        }

            // Floating buttons for fit-to-screen (top right)
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FloatingActionButton(
                    onClick = { fitStageToScreen() },
                    modifier = Modifier.size(40.dp),
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        Icons.Default.Fullscreen,
                        contentDescription = "Fit Stage",
                        modifier = Modifier.size(24.dp)
                    )
                }
                FloatingActionButton(
                    onClick = { fitAllInputsToScreen() },
                    modifier = Modifier.size(40.dp),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(
                        Icons.Default.CenterFocusStrong,
                        contentDescription = "Fit All Inputs",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }  // End Box (Canvas + floating buttons)
    }
}
