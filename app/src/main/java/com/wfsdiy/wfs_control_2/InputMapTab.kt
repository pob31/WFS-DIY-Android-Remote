package com.wfsdiy.wfs_control_2

import android.graphics.Paint
import android.graphics.Typeface
import android.annotation.SuppressLint
import androidx.compose.foundation.Canvas
import kotlin.math.pow
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
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
import kotlin.math.floor
import kotlin.math.atan2
import kotlin.math.cos
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
    markerRadius: Float = 0f
) {
    if (stageWidth <= 0f || stageDepth <= 0f) return

    // Adjust canvas boundaries to account for marker radius
    val effectiveCanvasWidth = canvasWidthPx - (markerRadius * 2f)
    val effectiveCanvasHeight = canvasHeightPx - (markerRadius * 2f)
    
    val pixelsPerMeterX = effectiveCanvasWidth / stageWidth
    val pixelsPerMeterY = effectiveCanvasHeight / stageDepth

    val originXPx = canvasWidthPx / 2f
    val originYPx = canvasHeightPx - markerRadius // Bottom of effective canvas

    val lineColor = Color.DarkGray
    val lineStrokeWidth = 1f // Use 1 pixel for thin grid lines

    // Horizontal lines for depth (from bottom up)
    for (depthStep in 1..floor(stageDepth).toInt()) {
        val yPx = originYPx - (depthStep * pixelsPerMeterY)
        if (yPx >= markerRadius && yPx <= canvasHeightPx - markerRadius) { // Draw only if within effective canvas bounds
            drawLine(
                color = lineColor,
                start = Offset(markerRadius, yPx),
                end = Offset(canvasWidthPx - markerRadius, yPx),
                strokeWidth = lineStrokeWidth
            )
        }
    }

    // Vertical lines for width (from center out)
    // Center line (0m)
    drawLine(
        color = lineColor,
        start = Offset(originXPx, markerRadius),
        end = Offset(originXPx, canvasHeightPx - markerRadius),
        strokeWidth = lineStrokeWidth
    )
    // Lines to the right and left of center
    for (widthStep in 1..floor(stageWidth / 2f).toInt()) {
        // Right side
        val xPxPositive = originXPx + (widthStep * pixelsPerMeterX)
        if (xPxPositive >= markerRadius && xPxPositive <= canvasWidthPx - markerRadius) {
            drawLine(
                color = lineColor,
                start = Offset(xPxPositive, markerRadius),
                end = Offset(xPxPositive, canvasHeightPx - markerRadius),
                strokeWidth = lineStrokeWidth
            )
        }
        // Left side
        val xPxNegative = originXPx - (widthStep * pixelsPerMeterX)
        if (xPxNegative >= markerRadius && xPxNegative <= canvasWidthPx - markerRadius) {
            drawLine(
                color = lineColor,
                start = Offset(xPxNegative, markerRadius),
                end = Offset(xPxNegative, canvasHeightPx - markerRadius),
                strokeWidth = lineStrokeWidth
            )
        }
    }
}


/**
 * Convert stage coordinates (meters) to canvas pixel position.
 * @param stageX X position in meters (relative to displayed origin)
 * @param stageY Y position in meters (relative to displayed origin)
 * @param stageWidth Stage width in meters
 * @param stageDepth Stage depth in meters
 * @param stageOriginX Stage origin X offset in meters
 * @param stageOriginY Stage origin Y offset in meters
 * @param canvasWidth Canvas width in pixels
 * @param canvasHeight Canvas height in pixels
 * @param markerRadius Marker radius in pixels (for effective area calculation)
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
    markerRadius: Float
): Offset {
    if (stageWidth <= 0f || stageDepth <= 0f || canvasWidth <= 0f || canvasHeight <= 0f) {
        return Offset(canvasWidth / 2f, canvasHeight / 2f)
    }

    val effectiveWidth = canvasWidth - (markerRadius * 2f)
    val effectiveHeight = canvasHeight - (markerRadius * 2f)

    // Convert origin-relative stage position to physical stage position, then normalize
    // Physical position = stage position + origin offset
    // Canvas shows physical stage: left=-stageWidth/2, right=+stageWidth/2
    val physicalX = stageX + stageOriginX
    val physicalY = stageY + stageOriginY
    val normalizedX = (physicalX + stageWidth / 2f) / stageWidth
    val normalizedY = (physicalY + stageDepth / 2f) / stageDepth

    // Convert normalized to canvas pixels (Y is inverted)
    val canvasX = (normalizedX * effectiveWidth + markerRadius).coerceIn(markerRadius, canvasWidth - markerRadius)
    val canvasY = ((1f - normalizedY) * effectiveHeight + markerRadius).coerceIn(markerRadius, canvasHeight - markerRadius)

    return Offset(canvasX, canvasY)
}

/**
 * Convert canvas pixel position to stage coordinates (meters).
 * @param canvasX X position in canvas pixels
 * @param canvasY Y position in canvas pixels
 * @param stageWidth Stage width in meters
 * @param stageDepth Stage depth in meters
 * @param stageOriginX Stage origin X offset in meters
 * @param stageOriginY Stage origin Y offset in meters
 * @param canvasWidth Canvas width in pixels
 * @param canvasHeight Canvas height in pixels
 * @param markerRadius Marker radius in pixels (for effective area calculation)
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
    markerRadius: Float
): Pair<Float, Float> {
    if (stageWidth <= 0f || stageDepth <= 0f || canvasWidth <= 0f || canvasHeight <= 0f) {
        return Pair(0f, 0f)
    }

    val effectiveWidth = canvasWidth - (markerRadius * 2f)
    val effectiveHeight = canvasHeight - (markerRadius * 2f)

    // Convert canvas pixels to normalized (0-1) range
    val normalizedX = (canvasX - markerRadius) / effectiveWidth
    val normalizedY = 1f - ((canvasY - markerRadius) / effectiveHeight)

    // Convert normalized to physical stage position, then to origin-relative
    // Inverse of: normalizedX = (physicalX + stageWidth/2) / stageWidth
    val physicalX = normalizedX * stageWidth - stageWidth / 2f
    val physicalY = normalizedY * stageDepth - stageDepth / 2f
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
    onPositionChanged: ((inputId: Int, positionX: Float, positionY: Float) -> Unit)? = null
) {
    val context = LocalContext.current
    val draggingMarkers = remember { mutableStateMapOf<Long, Int>() }
    val draggingBarycenters = remember { mutableStateMapOf<Long, Int>() }  // pointerId -> clusterId
    val currentMarkersState by rememberUpdatedState(markers)
    
    // Local state for smooth dragging without blocking global updates
    val localMarkerPositions = remember { mutableStateMapOf<Int, Offset>() }
    
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

        // Update shared canvas dimensions and call onCanvasSizeChanged
        LaunchedEffect(canvasWidth, canvasHeight, markerRadius) {
            if (canvasWidth > 0f && canvasHeight > 0f) {
                CanvasDimensions.updateDimensions(canvasWidth, canvasHeight)
                CanvasDimensions.updateMarkerRadius(markerRadius)
                onCanvasSizeChanged(canvasWidth, canvasHeight)
            } else {

            }
        }

        // Update marker positions from server inputParametersState
        // refreshTrigger forces update when returning to this tab
        LaunchedEffect(inputParametersState?.revision, refreshTrigger, canvasWidth, canvasHeight, stageWidth, stageDepth, stageOriginX, stageOriginY, numberOfInputs, markerRadius) {
            if (inputParametersState != null && canvasWidth > 0f && canvasHeight > 0f && stageWidth > 0f && stageDepth > 0f && numberOfInputs > 0) {
                val updatedMarkers = currentMarkersState.mapIndexed { index, marker ->
                    if (index < numberOfInputs) {
                        val inputId = marker.id
                        val channel = inputParametersState.getChannel(inputId)

                        // Get position values from input parameters
                        val posXParam = channel.parameters["positionX"]
                        val posYParam = channel.parameters["positionY"]

                        // Allow update if at least one position parameter exists
                        // Use current marker position for missing axis to allow incremental updates
                        if (posXParam != null || posYParam != null) {
                            // Convert normalized values (0-1) back to actual meters
                            // Position parameters have minValue=-50, maxValue=50
                            // actualValue = normalizedValue * (max - min) + min
                            val posXDef = InputParameterDefinitions.allParameters.find { it.variableName == "positionX" }
                            val posYDef = InputParameterDefinitions.allParameters.find { it.variableName == "positionY" }

                            // If a position parameter is missing, convert current canvas position back to meters
                            val currentStagePos = if (posXParam == null || posYParam == null) {
                                canvasToStagePosition(
                                    canvasX = marker.positionX,
                                    canvasY = marker.positionY,
                                    stageWidth = stageWidth,
                                    stageDepth = stageDepth,
                                    stageOriginX = stageOriginX,
                                    stageOriginY = stageOriginY,
                                    canvasWidth = canvasWidth,
                                    canvasHeight = canvasHeight,
                                    markerRadius = markerRadius
                                )
                            } else null

                            val posXMeters = if (posXParam != null && posXDef != null) {
                                InputParameterDefinitions.applyFormula(posXDef, posXParam.normalizedValue)
                            } else if (posXParam != null) {
                                posXParam.normalizedValue * 100f - 50f // Fallback
                            } else {
                                currentStagePos?.first ?: 0f // Use current position if X not received
                            }
                            val posYMeters = if (posYParam != null && posYDef != null) {
                                InputParameterDefinitions.applyFormula(posYDef, posYParam.normalizedValue)
                            } else if (posYParam != null) {
                                posYParam.normalizedValue * 100f - 50f // Fallback
                            } else {
                                currentStagePos?.second ?: 0f // Use current position if Y not received
                            }

                            // Convert from stage coordinates (meters) to canvas pixels
                            val canvasPos = stageToCanvasPosition(
                                stageX = posXMeters,
                                stageY = posYMeters,
                                stageWidth = stageWidth,
                                stageDepth = stageDepth,
                                stageOriginX = stageOriginX,
                                stageOriginY = stageOriginY,
                                canvasWidth = canvasWidth,
                                canvasHeight = canvasHeight,
                                markerRadius = markerRadius
                            )

                            // Server correction detection: if this marker is being dragged locally
                            // and the server sent a different position, clear the local position
                            // to snap to the server's corrected position
                            val localPos = localMarkerPositions[inputId]
                            if (localPos != null) {
                                val correctionThreshold = 5f // pixels - if server position differs by more than this, it's a correction
                                val distanceFromLocal = sqrt(
                                    (canvasPos.x - localPos.x).pow(2) + (canvasPos.y - localPos.y).pow(2)
                                )
                                if (distanceFromLocal > correctionThreshold) {
                                    // Server sent a corrected position - clear local dragging state
                                    localMarkerPositions.remove(inputId)
                                }
                            }

                            marker.copy(positionX = canvasPos.x, positionY = canvasPos.y)
                        } else {
                            marker
                        }
                    } else {
                        marker
                    }
                }

                // Only update if positions have actually changed
                val hasChanges = updatedMarkers.zip(currentMarkersState).any { (new, old) ->
                    new.positionX != old.positionX || new.positionY != old.positionY
                }

                if (hasChanges) {
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

        Canvas(modifier = Modifier
            .fillMaxSize()
            .pointerInput(stageWidth, stageDepth) {
                awaitEachGesture {
                    val pointerIdToCurrentLogicalPosition = mutableMapOf<PointerId, Offset>()
                    val pointersThatAttemptedGrab = mutableSetOf<PointerId>()
                    
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

                                                        if (isClusterReference && clusterConfig != null && clusterConfig.referenceMode == 0) {
                                                            // Moving reference in First Input mode - send cluster move
                                                            onClusterMove?.invoke(markerClusterId, deltaXMeters, deltaYMeters)
                                                        } else {
                                                            // Individual marker move - convert canvas position to stage meters
                                                            val (stageX, stageY) = canvasToStagePosition(
                                                                canvasX = updatedMarker.position.x,
                                                                canvasY = updatedMarker.position.y,
                                                                stageWidth = stageWidth,
                                                                stageDepth = stageDepth,
                                                                stageOriginX = stageOriginX,
                                                                stageOriginY = stageOriginY,
                                                                canvasWidth = canvasWidth,
                                                                canvasHeight = canvasHeight,
                                                                markerRadius = markerRadius
                                                            )
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
                                            markerRadius = markerRadius
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
                markerRadius = markerRadius
            )

            // Draw the stage grid lines (only for box shape)
            if (stageShape == 0) {
                drawStageCoordinates(stageWidth, stageDepth, canvasWidth, canvasHeight, markerRadius)
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
                markerRadius = markerRadius
            )

            // Draw origin marker at position where displayed coordinates would be (0.0, 0.0)
            drawOriginMarker(stageWidth, stageDepth, stageOriginX, stageOriginY, canvasWidth, canvasHeight, markerRadius)

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
    }
}
