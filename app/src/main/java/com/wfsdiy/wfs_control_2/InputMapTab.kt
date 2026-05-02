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
import com.wfsdiy.wfs_control_2.localization.loc
import com.wfsdiy.wfs_control_2.localization.locStatic
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

    val clusterMembers = markers.filter { it.clusterId == clusterId }
    if (clusterMembers.size < 2) return null

    val sumX = clusterMembers.sumOf { it.positionX.toDouble() }.toFloat()
    val sumY = clusterMembers.sumOf { it.positionY.toDouble() }.toFloat()
    return Offset(sumX / clusterMembers.size, sumY / clusterMembers.size)
}

/**
 * Find the hidden reference marker position for a cluster if it's in First Input mode
 * and the reference input is hidden.
 * Returns the cluster ID and position if found, null otherwise.
 */
fun findHiddenClusterReference(
    clusterId: Int,
    markers: List<Marker>,
    clusterConfigs: List<ClusterConfig>
): Offset? {
    val config = clusterConfigs.find { it.id == clusterId } ?: return null

    // Only for First Input mode (referenceMode == 0) with no tracked input
    if (config.referenceMode != 0 || config.trackedInputId != 0) return null

    val clusterMembers = markers.filter { it.clusterId == clusterId }
    if (clusterMembers.size < 2) return null

    // Find the reference marker (first by ID)
    val referenceMarker = clusterMembers.minByOrNull { it.id } ?: return null

    // Only return position if the reference is hidden
    return if (!referenceMarker.isVisible) referenceMarker.position else null
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
    clusterConfigs: List<ClusterConfig> = emptyList(),
    onClusterMove: ((clusterId: Int, deltaX: Float, deltaY: Float) -> Unit)? = null,
    onBarycenterMove: ((clusterId: Int, deltaX: Float, deltaY: Float) -> Unit)? = null,
    onClusterPositionChanged: ((clusterId: Int, stageX: Float, stageY: Float) -> Unit)? = null,
    inputParametersState: InputParametersState? = null,
    onPositionChanged: ((inputId: Int, positionX: Float, positionY: Float) -> Unit)? = null,
    onInputHeightChanged: ((inputId: Int, newZ: Float) -> Unit)? = null,
    onInputRotationChanged: ((inputId: Int, newRotation: Float) -> Unit)? = null,
    onClusterScale: ((clusterId: Int, scaleFactor: Float) -> Unit)? = null,
    onClusterRotation: ((clusterId: Int, angleDegrees: Float) -> Unit)? = null,
    onClusterScaleRotation: ((clusterId: Int, cumulativeScale: Float, cumulativeRotation: Float) -> Unit)? = null,
    onClusterDragStart: ((clusterId: Int) -> Unit)? = null,
    onClusterDragEnd: ((clusterId: Int) -> Unit)? = null,
    compositePositions: Map<Int, Pair<Float, Float>> = emptyMap(),  // inputId -> (deltaX, deltaY) in stage meters
    samplerPlaying: Map<Int, Boolean> = emptyMap()  // inputId -> true while a sampler cell is playing on that input
) {
    val context = LocalContext.current
    val draggingMarkers = remember { mutableStateMapOf<Long, Int>() }
    val draggingBarycenters = remember { mutableStateMapOf<Long, Int>() }  // pointerId -> clusterId
    val draggingHiddenRefs = remember { mutableStateMapOf<Long, Int>() }   // pointerId -> clusterId (for hidden reference markers in mode 0)
    val currentMarkersState by rememberUpdatedState(markers)
    val currentClusterConfigs by rememberUpdatedState(clusterConfigs)

    // Local state for smooth dragging without blocking global updates
    val localMarkerPositions = remember { mutableStateMapOf<Int, Offset>() }

    // Store marker positions in stage meters (true position, independent of view)
    // This allows proper recalculation when pan/zoom changes
    // Only markers with "real" positions (from server or dragging) are stored here
    val markerStagePositions = remember { mutableStateMapOf<Int, Pair<Float, Float>>() }

    // Version counter to trigger canvas position recalculation when stage positions change
    var markerStagePositionsVersion by remember { mutableIntStateOf(0) }

    // Track the last known view parameters for markers without stage positions
    // This allows us to properly transform their canvas positions when view changes
    // Using a simple data holder (4 floats: panX, panY, viewW, viewH)
    var lastViewPanX by remember { mutableFloatStateOf(0f) }
    var lastViewPanY by remember { mutableFloatStateOf(0f) }
    var lastViewWidth by remember { mutableFloatStateOf(0f) }
    var lastViewHeight by remember { mutableFloatStateOf(0f) }
    var lastViewInitialized by remember { mutableStateOf(false) }

    // Vector control state for secondary touches
    // Target type: 0 = input (height/rotation), 1 = cluster (scale/rotation)
    data class VectorControl(
        val markerId: Int,           // inputId for input targets, or reference markerId for clusters
        val clusterId: Int = 0,      // clusterId for cluster targets (0 for inputs)
        val targetType: Int = 0,     // 0 = input, 1 = cluster
        val initialMarkerPosition: Offset,
        val initialTouchPosition: Offset,
        val currentTouchPosition: Offset,
        val startZ: Float = 2f,           // For inputs: initial positionZ in meters
        val startRotation: Float = 0f,    // For inputs: initial inputRotation, for clusters: cumulative rotation
        val previousDistance: Float = 0f,  // For clusters: previous frame distance (incremental scale)
        val previousAngle: Float = 0f     // For clusters: previous frame angle (incremental rotation)
    )
    val vectorControls = remember { mutableStateMapOf<Long, VectorControl>() }
    var vectorControlsUpdateTrigger: Int by remember { mutableIntStateOf(0) }

    // Local cluster gesture state. While a cluster is in this map, the InputMapTab
    // computes non-reference member positions locally each touch frame using a
    // snapshot of (memberId, dx, dy) offsets from the gesture-start pivot, plus
    // the live drag delta. The tablet sends OSC to JUCE as today, but does not
    // wait for JUCE echoes to update its display — this is the rigid-body
    // translation optimization (Plan B).
    data class ClusterTranslationState(
        val pivotMode: Int,                 // 0 = ref input, 1 = barycenter
        val refStartStageX: Float,           // pivot stage X at gesture start
        val refStartStageY: Float,           // pivot stage Y at gesture start
        val memberSnapshot: Map<Int, Pair<Float, Float>>  // inputId -> (stageX, stageY) at gesture start
    )
    val activeClusterTranslations = remember { mutableStateMapOf<Int, ClusterTranslationState>() }

    // Live transform applied on top of the snapshotted ClusterTranslationState each
    // frame: currentPivot is updated by the primary finger's drag, scale/rotation
    // are updated by the secondary finger's pinch/twist. Generalizes the
    // translation-only case (scale=1, rotation=0).
    data class ClusterGestureTransform(
        val currentPivotStageX: Float,
        val currentPivotStageY: Float,
        val scale: Float = 1f,
        val rotationStageDeg: Float = 0f
    )
    val clusterGestureTransforms = remember { mutableStateMapOf<Int, ClusterGestureTransform>() }

    val clusterReleaseScope = rememberCoroutineScope()
    // Counter bumped after a cluster gesture's delayed release clears its snapshot,
    // to force the inputParametersState -> markerStagePositions LaunchedEffect to
    // re-sync cluster members against authoritative positions even if no further
    // OSC traffic increments inputParametersState.revision.
    var clusterSyncTrigger by remember { mutableIntStateOf(0) }

    fun beginClusterTranslationIfNeeded(
        clusterId: Int,
        pivotMode: Int,
        pivotStageX: Float,
        pivotStageY: Float
    ) {
        if (clusterId !in 1..10) return
        if (activeClusterTranslations.containsKey(clusterId)) return
        val members = currentMarkersState.take(numberOfInputs).filter { it.clusterId == clusterId }
        val snapshot = members.mapNotNull { m ->
            val pos = markerStagePositions[m.id] ?: return@mapNotNull null
            m.id to pos
        }.toMap()
        if (snapshot.isEmpty()) return
        activeClusterTranslations[clusterId] = ClusterTranslationState(
            pivotMode = pivotMode,
            refStartStageX = pivotStageX,
            refStartStageY = pivotStageY,
            memberSnapshot = snapshot
        )
        clusterGestureTransforms[clusterId] = ClusterGestureTransform(
            currentPivotStageX = pivotStageX,
            currentPivotStageY = pivotStageY
        )
        onClusterDragStart?.invoke(clusterId)
    }

    fun applyClusterRigidTransform(clusterId: Int) {
        val state = activeClusterTranslations[clusterId] ?: return
        val transform = clusterGestureTransforms[clusterId] ?: return
        val rad = (transform.rotationStageDeg * (Math.PI / 180.0)).toFloat()
        val cosA = cos(rad)
        val sinA = sin(rad)
        val s = transform.scale
        state.memberSnapshot.forEach { (memberId, pos) ->
            val dx = pos.first - state.refStartStageX
            val dy = pos.second - state.refStartStageY
            val newDx = (dx * cosA - dy * sinA) * s
            val newDy = (dx * sinA + dy * cosA) * s
            markerStagePositions[memberId] = Pair(
                transform.currentPivotStageX + newDx,
                transform.currentPivotStageY + newDy
            )
        }
        markerStagePositionsVersion++
    }

    fun applyClusterTranslation(
        clusterId: Int,
        currentPivotStageX: Float,
        currentPivotStageY: Float
    ) {
        if (!activeClusterTranslations.containsKey(clusterId)) return
        val existing = clusterGestureTransforms[clusterId]
        clusterGestureTransforms[clusterId] = ClusterGestureTransform(
            currentPivotStageX = currentPivotStageX,
            currentPivotStageY = currentPivotStageY,
            scale = existing?.scale ?: 1f,
            rotationStageDeg = existing?.rotationStageDeg ?: 0f
        )
        applyClusterRigidTransform(clusterId)
    }

    // Apply cumulative scale/rotation from the secondary-finger gesture. The
    // primary finger continues to drive currentPivot via applyClusterTranslation;
    // we only update scale/rotation here.
    fun applyClusterScaleRotation(
        clusterId: Int,
        scale: Float,
        rotationStageDeg: Float
    ) {
        val state = activeClusterTranslations[clusterId] ?: return
        val existing = clusterGestureTransforms[clusterId]
        clusterGestureTransforms[clusterId] = ClusterGestureTransform(
            currentPivotStageX = existing?.currentPivotStageX ?: state.refStartStageX,
            currentPivotStageY = existing?.currentPivotStageY ?: state.refStartStageY,
            scale = scale,
            rotationStageDeg = rotationStageDeg
        )
        applyClusterRigidTransform(clusterId)
    }

    // Bake the current rotated/scaled member positions into a fresh snapshot and
    // reset the transform to identity. Called when the secondary finger releases
    // while the primary is still down — the cluster keeps translating from the
    // current configuration without snapping back to the pre-rotation snapshot.
    fun bakeClusterScaleRotation(clusterId: Int) {
        val state = activeClusterTranslations[clusterId] ?: return
        val transform = clusterGestureTransforms[clusterId] ?: return
        if (transform.scale == 1f && transform.rotationStageDeg == 0f) return
        val newSnapshot = state.memberSnapshot.mapValues { (memberId, oldPos) ->
            markerStagePositions[memberId] ?: oldPos
        }
        activeClusterTranslations[clusterId] = state.copy(
            refStartStageX = transform.currentPivotStageX,
            refStartStageY = transform.currentPivotStageY,
            memberSnapshot = newSnapshot
        )
        clusterGestureTransforms[clusterId] = ClusterGestureTransform(
            currentPivotStageX = transform.currentPivotStageX,
            currentPivotStageY = transform.currentPivotStageY,
            scale = 1f,
            rotationStageDeg = 0f
        )
    }

    fun endClusterTranslation(clusterId: Int) {
        if (!activeClusterTranslations.containsKey(clusterId)) return
        // Clear OscService suppression immediately so the authoritative bundle from
        // JUCE can update inputParametersState. But keep the local snapshot entry
        // alive briefly so the LaunchedEffect that syncs markerStagePositions from
        // inputParametersState skips this cluster until the bundle has actually
        // landed — otherwise we'd snap back to the pre-gesture position for the
        // brief window between release and bundle arrival.
        onClusterDragEnd?.invoke(clusterId)
        clusterReleaseScope.launch {
            delay(400L)
            activeClusterTranslations.remove(clusterId)
            clusterGestureTransforms.remove(clusterId)
            // Force the inputParametersState -> markerStagePositions sync to re-run
            // for this cluster's members so the locally extrapolated positions
            // are replaced by the JUCE-authoritative values that landed during the
            // delay window above.
            clusterSyncTrigger++
        }
    }

    // Throttle cluster sends directly in touch handler to avoid overloading JUCE
    var lastClusterGestureSendTime by remember { mutableStateOf(0L) }
    var lastClusterPositionSendTime by remember { mutableStateOf(0L) }
    
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
            derivedStateOf { draggingMarkers.isNotEmpty() || draggingBarycenters.isNotEmpty() || draggingHiddenRefs.isNotEmpty() }
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
        LaunchedEffect(inputParametersState?.revision, refreshTrigger, stageWidth, stageDepth, stageOriginX, stageOriginY, numberOfInputs, clusterSyncTrigger) {
            if (inputParametersState != null && stageWidth > 0f && stageDepth > 0f && numberOfInputs > 0) {
                (1..numberOfInputs).forEach { inputId ->
                    // Skip cluster members during a local cluster gesture: the gesture
                    // handler is writing extrapolated positions to markerStagePositions
                    // at touch rate; reading stale positionX/Y from inputParametersState
                    // would clobber that extrapolation.
                    val markerForInput = currentMarkersState.find { it.id == inputId }
                    val markerCluster = markerForInput?.clusterId ?: 0
                    if (markerCluster in 1..10 && activeClusterTranslations.containsKey(markerCluster)) {
                        return@forEach
                    }

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
                // Increment version to trigger canvas position recalculation
                markerStagePositionsVersion++
            }
        }

        // Recalculate canvas positions when view changes (pan/zoom) or stage positions change
        // Only affects markers that have stored stage positions (from server or dragging)
        // Markers without stage positions keep their canvas positions and get transformed relative to view changes
        LaunchedEffect(panOffsetX, panOffsetY, actualViewWidth, actualViewHeight, canvasWidth, canvasHeight, markerRadius, stageOriginX, stageOriginY, numberOfInputs, markerStagePositionsVersion) {
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

        // Note: marker names and clusterIds are synced in MainActivity's LaunchedEffect
        // (inputParametersState?.revision, numberOfInputs) to avoid race conditions.

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
                                if (!draggingMarkers.containsKey(pointerValue) && !draggingBarycenters.containsKey(pointerValue) && !draggingHiddenRefs.containsKey(pointerValue)) {
                                    // Check if this pointer has a vector control first
                                    if (vectorControls.containsKey(pointerValue)) {
                                        // Handle secondary finger movement
                                        val vectorControl = vectorControls[pointerValue]
                                        if (vectorControl != null) {
                                            // Update the current touch position
                                            val updatedVectorControl = vectorControl.copy(currentTouchPosition = change.position)
                                            vectorControls[pointerValue] = updatedVectorControl
                                            vectorControlsUpdateTrigger++ // Trigger recomposition

                                            // Calculate and send updates based on target type
                                            if (initialLayoutDone) {
                                                if (vectorControl.targetType == 1) {
                                                    // Cluster secondary touch: cumulative scale and rotation from gesture start
                                                    // Use fixed initial reference position (not live barycenter) so
                                                    // distance/angle only depend on finger movement, not echo latency.
                                                    val refPos = vectorControl.initialMarkerPosition

                                                    val currentAngle = calculateAngle(refPos, change.position)
                                                    val currentDistance = calculateDistance(refPos, change.position)

                                                    // Cumulative rotation from gesture start
                                                    var cumulativeRotation = currentAngle - vectorControl.previousAngle + vectorControl.startRotation
                                                    // Normalize to -180..180
                                                    while (cumulativeRotation > 180f) cumulativeRotation -= 360f
                                                    while (cumulativeRotation < -180f) cumulativeRotation += 360f

                                                    // Cumulative scale from gesture start
                                                    val cumulativeScale = if (vectorControl.previousDistance > 10f) {
                                                        currentDistance / vectorControl.previousDistance
                                                    } else 1f

                                                    // Apply rotation/scale locally on every frame so the cluster
                                                    // members visibly track the gesture without waiting for the
                                                    // JUCE echo. Stage rotation is the negation of canvas-space
                                                    // rotation (Y axes are flipped).
                                                    applyClusterScaleRotation(
                                                        vectorControl.clusterId,
                                                        cumulativeScale,
                                                        -cumulativeRotation
                                                    )

                                                    // Throttle directly in touch handler — skip frames to avoid overloading JUCE
                                                    val now = System.currentTimeMillis()
                                                    if (now - lastClusterGestureSendTime >= 50L) { // 20Hz max
                                                        lastClusterGestureSendTime = now
                                                        // Send combined cumulative values (absolute from gesture start)
                                                        // Negate rotation: screen Y is down, stage Y is up
                                                        onClusterScaleRotation?.invoke(
                                                            vectorControl.clusterId, cumulativeScale, -cumulativeRotation
                                                        )
                                                    }

                                                    // Don't update previousAngle/previousDistance — keep initial values
                                                    // so cumulative computation is always from gesture start
                                                    vectorControls[pointerValue] = updatedVectorControl
                                                } else {
                                                    // Input secondary touch: height and rotation
                                                    val currentMarkerPosition = if (localMarkerPositions.containsKey(vectorControl.markerId)) {
                                                        localMarkerPositions[vectorControl.markerId]!!
                                                    } else {
                                                        currentMarkersState.find { it.id == vectorControl.markerId }?.position
                                                    }

                                                    if (currentMarkerPosition != null) {
                                                        // Rotation: angle change from initial to current
                                                        val initialAngle = calculateAngle(vectorControl.initialMarkerPosition, vectorControl.initialTouchPosition)
                                                        val currentAngle = calculateAngle(currentMarkerPosition, change.position)
                                                        val angleChange = currentAngle - initialAngle
                                                        val newRotation = vectorControl.startRotation - angleChange
                                                        onInputRotationChanged?.invoke(vectorControl.markerId, newRotation)

                                                        // Height: pinch distance change maps to height delta (additive)
                                                        val initialDistance = calculateDistance(vectorControl.initialMarkerPosition, vectorControl.initialTouchPosition)
                                                        val currentDistance = calculateDistance(currentMarkerPosition, change.position)
                                                        if (initialDistance > 10f) {
                                                            val distanceRatio = currentDistance / initialDistance
                                                            val heightDelta = (distanceRatio - 1f) * 3f  // 3m per doubling of pinch distance
                                                            val newHeight = (vectorControl.startZ + heightDelta).coerceIn(0f, 20f)
                                                            onInputHeightChanged?.invoke(vectorControl.markerId, newHeight)
                                                        }
                                                    }
                                                }
                                            }
                                            change.consume()
                                        }
                                    } else if (!pointersThatAttemptedGrab.contains(pointerId)) {
                                        pointersThatAttemptedGrab.add(pointerId)
                                        val touchPosition = change.position

                                        // Check for barycenter or hidden reference FIRST
                                        // (barycenters overlap with markers, so must be checked before markers)
                                        var clusterTargetFound = false
                                        if (clusterConfigs.isNotEmpty()) {
                                            for (clusterId in 1..10) {
                                                if (draggingBarycenters.containsValue(clusterId) || draggingHiddenRefs.containsValue(clusterId)) continue

                                                // Check for barycenter (mode 1)
                                                val barycenter = findClusterBarycenter(clusterId, currentMarkersState.take(numberOfInputs), clusterConfigs)
                                                if (barycenter != null && distance(touchPosition, barycenter) <= markerRadius * pickupRadiusMultiplier) {
                                                    if (draggingBarycenters.size + draggingMarkers.size + draggingHiddenRefs.size < 10) {
                                                        draggingBarycenters[pointerValue] = clusterId
                                                        pointerIdToCurrentLogicalPosition[pointerId] = barycenter
                                                        clusterTargetFound = true
                                                        break
                                                    }
                                                }

                                                // Check for hidden reference (mode 0)
                                                val hiddenRef = findHiddenClusterReference(clusterId, currentMarkersState.take(numberOfInputs), clusterConfigs)
                                                if (hiddenRef != null && distance(touchPosition, hiddenRef) <= markerRadius * pickupRadiusMultiplier) {
                                                    if (draggingBarycenters.size + draggingMarkers.size + draggingHiddenRefs.size < 10) {
                                                        draggingHiddenRefs[pointerValue] = clusterId
                                                        pointerIdToCurrentLogicalPosition[pointerId] = hiddenRef
                                                        clusterTargetFound = true
                                                        break
                                                    }
                                                }
                                            }
                                        }

                                        if (!clusterTargetFound) {
                                            // No cluster target - check for regular marker
                                            val candidateMarkers =
                                                activeMarkersSnapshot.filterIndexed { _, m ->
                                                    val originalMarker = currentMarkersState.getOrNull(m.id -1)
                                                    originalMarker != null && originalMarker.isVisible &&
                                                            !originalMarker.isLocked &&
                                                            !draggingMarkers.containsValue(m.id) &&
                                                            distance(
                                                                touchPosition,
                                                                m.position
                                                            ) <= m.radius * pickupRadiusMultiplier
                                                }

                                            if (candidateMarkers.isNotEmpty()) {
                                                val markerToDrag = candidateMarkers.minWithOrNull(
                                                    compareBy<Marker> { marker -> distance(touchPosition, marker.position) }
                                                        .thenBy { marker -> marker.id }
                                                )
                                                markerToDrag?.let {
                                                    if (draggingMarkers.size < 10) {
                                                        draggingMarkers[pointerValue] = it.id
                                                        pointerIdToCurrentLogicalPosition[pointerId] = it.position
                                                    }
                                                }
                                            } else {
                                                // No cluster target and no marker found - check for vector control (secondary touch)
                                                var vectorControlCreated = false

                                                // Check for secondary touch on dragged cluster targets (barycenters or hidden refs)
                                                val draggedClusterIds = (draggingBarycenters.values + draggingHiddenRefs.values).toSet()
                                                val clustersWithVectorControl = vectorControls.values.filter { it.targetType == 1 }.map { it.clusterId }.toSet()
                                                val availableClusters = draggedClusterIds - clustersWithVectorControl

                                                if (availableClusters.isNotEmpty()) {
                                                    // Create vector control for cluster
                                                    val closestClusterId = availableClusters.firstOrNull()
                                                    closestClusterId?.let { clusterId ->
                                                        // Find the reference position (barycenter or hidden ref)
                                                        val barycenterPos = findClusterBarycenter(clusterId, currentMarkersState.take(numberOfInputs), clusterConfigs)
                                                        val hiddenRefPos = if (barycenterPos == null)
                                                            findHiddenClusterReference(clusterId, currentMarkersState.take(numberOfInputs), clusterConfigs)
                                                        else null
                                                        val referencePos = barycenterPos ?: hiddenRefPos
                                                        val pivotMode = if (barycenterPos != null) 1 else 0

                                                        referencePos?.let { refPos ->
                                                            val initDist = calculateDistance(refPos, touchPosition)
                                                            val initAngle = calculateAngle(refPos, touchPosition)
                                                            vectorControls[pointerValue] = VectorControl(
                                                                markerId = 0,  // Not tracking a specific marker
                                                                clusterId = clusterId,
                                                                targetType = 1,  // Cluster target
                                                                initialMarkerPosition = refPos,
                                                                initialTouchPosition = touchPosition,
                                                                currentTouchPosition = touchPosition,
                                                                startZ = 0f,
                                                                startRotation = 0f,  // Cluster rotation is cumulative
                                                                previousDistance = initDist,
                                                                previousAngle = initAngle
                                                            )
                                                            // Lazy-init the cluster translation snapshot so that local
                                                            // rotation/scale has positions to apply against, even if the
                                                            // primary finger has not moved yet.
                                                            if (initialLayoutDone &&
                                                                !activeClusterTranslations.containsKey(clusterId)) {
                                                                val (pivotStageX, pivotStageY) = canvasToStagePosition(
                                                                    canvasX = refPos.x,
                                                                    canvasY = refPos.y,
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
                                                                beginClusterTranslationIfNeeded(
                                                                    clusterId = clusterId,
                                                                    pivotMode = pivotMode,
                                                                    pivotStageX = pivotStageX,
                                                                    pivotStageY = pivotStageY
                                                                )
                                                            }
                                                            vectorControlsUpdateTrigger++
                                                            vectorControlCreated = true
                                                        }
                                                    }
                                                }

                                                // Check for secondary touch on dragged input markers
                                                if (!vectorControlCreated) {
                                                    val draggedMarkers = draggingMarkers.values.toSet()
                                                    val markersWithVectorControl = (vectorControls.values.filter { it.targetType == 0 }.map { it.markerId } +
                                                        vectorControls.values.filter { it.targetType == 1 }.map { it.markerId }).toSet()
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
                                                                // Check if this marker is a cluster reference — if so, use cluster scale/rotation
                                                                val markerClusterId = it.clusterId
                                                                val clusterConfig = if (markerClusterId > 0) clusterConfigs.find { c -> c.id == markerClusterId } else null
                                                                val isClusterReference = clusterConfig != null && (
                                                                    clusterConfig.trackedInputId == markerId ||
                                                                    (clusterConfig.referenceMode == 0 && clusterConfig.trackedInputId == 0 &&
                                                                        currentMarkersState.filter { m -> m.clusterId == markerClusterId }.minByOrNull { m -> m.id }?.id == markerId)
                                                                )

                                                                if (isClusterReference) {
                                                                    // Cluster reference: create cluster vector control (scale/rotation)
                                                                    val initDist = calculateDistance(it.position, touchPosition)
                                                                    val initAngle = calculateAngle(it.position, touchPosition)
                                                                    vectorControls[pointerValue] = VectorControl(
                                                                        markerId = markerId,
                                                                        clusterId = markerClusterId,
                                                                        targetType = 1,  // Cluster target
                                                                        initialMarkerPosition = it.position,
                                                                        initialTouchPosition = touchPosition,
                                                                        currentTouchPosition = touchPosition,
                                                                        startZ = 0f,
                                                                        startRotation = 0f,
                                                                        previousDistance = initDist,
                                                                        previousAngle = initAngle
                                                                    )
                                                                    // Lazy-init cluster translation so local rotation/scale
                                                                    // can apply even if the reference marker has not moved.
                                                                    if (initialLayoutDone &&
                                                                        !activeClusterTranslations.containsKey(markerClusterId)) {
                                                                        val (pivotStageX, pivotStageY) = canvasToStagePosition(
                                                                            canvasX = it.position.x,
                                                                            canvasY = it.position.y,
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
                                                                        beginClusterTranslationIfNeeded(
                                                                            clusterId = markerClusterId,
                                                                            pivotMode = 0,
                                                                            pivotStageX = pivotStageX,
                                                                            pivotStageY = pivotStageY
                                                                        )
                                                                    }
                                                                } else {
                                                                    // Regular input: create input vector control (height/rotation)
                                                                    val channel = inputParametersState?.getChannel(markerId)
                                                                    val posZDef = InputParameterDefinitions.allParameters.find { p -> p.variableName == "positionZ" }
                                                                    val rotDef = InputParameterDefinitions.allParameters.find { p -> p.variableName == "rotation" }

                                                                    val initialZ = channel?.parameters?.get("positionZ")?.let { param ->
                                                                        posZDef?.let { def -> InputParameterDefinitions.applyFormula(def, param.normalizedValue) } ?: param.normalizedValue * 10f
                                                                    } ?: 2f

                                                                    val initialRotation = channel?.parameters?.get("rotation")?.let { param ->
                                                                        rotDef?.let { def -> InputParameterDefinitions.applyFormula(def, param.normalizedValue) } ?: param.normalizedValue * 360f
                                                                    } ?: 0f

                                                                    vectorControls[pointerValue] = VectorControl(
                                                                        markerId = markerId,
                                                                        clusterId = 0,
                                                                        targetType = 0,  // Input target
                                                                        initialMarkerPosition = it.position,
                                                                        initialTouchPosition = touchPosition,
                                                                        currentTouchPosition = touchPosition,
                                                                        startZ = initialZ,
                                                                        startRotation = initialRotation
                                                                    )
                                                                }
                                                                vectorControlsUpdateTrigger++
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

                                                        // For cluster reference drags (mode 0), snapshot the gesture-start
                                                        // pivot from the OLD markerStagePositions value (before we overwrite
                                                        // it below) so non-reference members can be locally extrapolated
                                                        // by the same delta at touch rate.
                                                        if (isClusterReference && clusterConfig.referenceMode == 0) {
                                                            val pivotPos = markerStagePositions[updatedMarker.id]
                                                            if (pivotPos != null) {
                                                                beginClusterTranslationIfNeeded(
                                                                    clusterId = markerClusterId,
                                                                    pivotMode = 0,
                                                                    pivotStageX = pivotPos.first,
                                                                    pivotStageY = pivotPos.second
                                                                )
                                                            }
                                                        }

                                                        // Store the stage position for view change recalculations
                                                        markerStagePositions[updatedMarker.id] = Pair(stageX, stageY)

                                                        if (isClusterReference && clusterConfig.referenceMode == 0) {
                                                            // Local rigid-body translation: move all non-reference cluster
                                                            // members by the same delta as the reference, at touch rate.
                                                            applyClusterTranslation(markerClusterId, stageX, stageY)

                                                            // Moving reference in First Input mode - send absolute position
                                                            // Throttle to avoid overloading JUCE during two-finger gestures
                                                            val nowRef = System.currentTimeMillis()
                                                            if (nowRef - lastClusterPositionSendTime >= 40L) {
                                                                lastClusterPositionSendTime = nowRef
                                                                onClusterPositionChanged?.invoke(markerClusterId, stageX, stageY)
                                                            }
                                                        } else {
                                                            // Individual marker move
                                                            onPositionChanged?.invoke(updatedMarker.id, stageX, stageY)
                                                        }

                                                        // Check if this marker has vector control (secondary touch)
                                                        // When primary finger moves, update secondary touch calculations
                                                        vectorControls.values.forEach { vectorControl ->
                                                            if (vectorControl.targetType == 0 && vectorControl.markerId == updatedMarker.id) {
                                                                // Use local position for consistent calculations
                                                                val currentMarkerPosition = localMarkerPositions[updatedMarker.id] ?: updatedMarker.position

                                                                // Rotation: angle change from initial to current
                                                                val initialAngle = calculateAngle(vectorControl.initialMarkerPosition, vectorControl.initialTouchPosition)
                                                                val currentAngle = calculateAngle(currentMarkerPosition, vectorControl.currentTouchPosition)
                                                                val angleChange = currentAngle - initialAngle
                                                                val newRotation = vectorControl.startRotation - angleChange
                                                                onInputRotationChanged?.invoke(vectorControl.markerId, newRotation)

                                                                // Height: pinch distance change maps to height delta (additive)
                                                                val initialDistance = calculateDistance(vectorControl.initialMarkerPosition, vectorControl.initialTouchPosition)
                                                                val currentDistance = calculateDistance(currentMarkerPosition, vectorControl.currentTouchPosition)
                                                                if (initialDistance > 10f) {  // Avoid division by small numbers
                                                                    val distanceRatio = currentDistance / initialDistance
                                                                    val heightDelta = (distanceRatio - 1f) * 3f  // 3m per doubling of pinch distance
                                                                    val newHeight = (vectorControl.startZ + heightDelta).coerceIn(0f, 20f)
                                                                    onInputHeightChanged?.invoke(vectorControl.markerId, newHeight)
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

                                                if (initialLayoutDone) {
                                                    // Compute live stage position from the new pointer position
                                                    val (stageX, stageY) = canvasToStagePosition(
                                                        canvasX = newLogicalPosition.x,
                                                        canvasY = newLogicalPosition.y,
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

                                                    // Lazy-init the cluster translation snapshot using the pre-move
                                                    // (oldLogicalPosition) as gesture-start pivot.
                                                    if (!activeClusterTranslations.containsKey(clusterIdBeingDragged)) {
                                                        val (oldStageX, oldStageY) = canvasToStagePosition(
                                                            canvasX = oldLogicalPosition.x,
                                                            canvasY = oldLogicalPosition.y,
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
                                                        beginClusterTranslationIfNeeded(
                                                            clusterId = clusterIdBeingDragged,
                                                            pivotMode = 1,
                                                            pivotStageX = oldStageX,
                                                            pivotStageY = oldStageY
                                                        )
                                                    }
                                                    // Local rigid-body translation at touch rate
                                                    applyClusterTranslation(clusterIdBeingDragged, stageX, stageY)

                                                    // Throttle OSC send to avoid overloading JUCE during gestures
                                                    val now2 = System.currentTimeMillis()
                                                    if (now2 - lastClusterPositionSendTime >= 40L) {
                                                        lastClusterPositionSendTime = now2
                                                        onClusterPositionChanged?.invoke(clusterIdBeingDragged, stageX, stageY)
                                                    }
                                                }

                                                change.consume()
                                            }
                                        } else {
                                            // Check if we're dragging a hidden reference marker
                                            val hiddenRefClusterId = draggingHiddenRefs[pointerValue]
                                            if (hiddenRefClusterId != null) {
                                                val oldLogicalPosition = pointerIdToCurrentLogicalPosition[pointerId]
                                                if (oldLogicalPosition != null && change.positionChanged()) {
                                                    val dragDelta = change.position - change.previousPosition

                                                    val newLogicalPosition = Offset(
                                                        x = (oldLogicalPosition.x + dragDelta.x).coerceIn(markerRadius, canvasWidth - markerRadius),
                                                        y = (oldLogicalPosition.y + dragDelta.y).coerceIn(markerRadius, canvasHeight - markerRadius)
                                                    )
                                                    pointerIdToCurrentLogicalPosition[pointerId] = newLogicalPosition

                                                    if (initialLayoutDone) {
                                                        // Compute live stage position from the new pointer position
                                                        val (stageX, stageY) = canvasToStagePosition(
                                                            canvasX = newLogicalPosition.x,
                                                            canvasY = newLogicalPosition.y,
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

                                                        // Lazy-init snapshot from oldLogicalPosition (pre-move)
                                                        if (!activeClusterTranslations.containsKey(hiddenRefClusterId)) {
                                                            val (oldStageX, oldStageY) = canvasToStagePosition(
                                                                canvasX = oldLogicalPosition.x,
                                                                canvasY = oldLogicalPosition.y,
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
                                                            beginClusterTranslationIfNeeded(
                                                                clusterId = hiddenRefClusterId,
                                                                pivotMode = 0,
                                                                pivotStageX = oldStageX,
                                                                pivotStageY = oldStageY
                                                            )
                                                        }
                                                        // Local rigid-body translation at touch rate
                                                        applyClusterTranslation(hiddenRefClusterId, stageX, stageY)

                                                        // Throttle OSC send to avoid overloading JUCE during gestures
                                                        val now3 = System.currentTimeMillis()
                                                        if (now3 - lastClusterPositionSendTime >= 40L) {
                                                            lastClusterPositionSendTime = now3
                                                            onClusterPositionChanged?.invoke(hiddenRefClusterId, stageX, stageY)
                                                        }
                                                    }

                                                    change.consume()
                                                }
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

                                    // End any local cluster translation that was driven by this marker
                                    // (it was being dragged as the cluster reference in mode 0).
                                    val releasedMarker = currentMarkersState.find { it.id == releasedMarkerId }
                                    val releasedClusterIdForMarker = releasedMarker?.clusterId ?: 0
                                    if (releasedClusterIdForMarker in 1..10 &&
                                        activeClusterTranslations.containsKey(releasedClusterIdForMarker)) {
                                        endClusterTranslation(releasedClusterIdForMarker)
                                    }

                                    // Send gesture-end for any cluster vector controls on this marker
                                    vectorControls.values.forEach { vc ->
                                        if (vc.markerId == releasedMarkerId && vc.targetType == 1 && initialLayoutDone) {
                                            onClusterScaleRotation?.invoke(vc.clusterId, 0f, 0f)
                                        }
                                    }
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
                                    // Send gesture-end for cluster vector controls
                                    val releasedVc = vectorControls[pointerValue]
                                    if (releasedVc != null && releasedVc.targetType == 1 && initialLayoutDone) {
                                        // Bake the current rotation/scale into the snapshot so the
                                        // cluster keeps its rotated configuration as the primary
                                        // finger continues to drag it.
                                        bakeClusterScaleRotation(releasedVc.clusterId)
                                        // Send scale=0 to signal gesture end (JUCE clears snapshot)
                                        onClusterScaleRotation?.invoke(releasedVc.clusterId, 0f, 0f)
                                    }
                                    // Remove vector control when secondary touch is released
                                    vectorControls.remove(pointerValue)
                                    vectorControlsUpdateTrigger++ // Trigger recomposition
                                } else if (draggingBarycenters.containsKey(pointerValue)) {
                                    // Remove barycenter drag when released
                                    val releasedClusterId = draggingBarycenters.remove(pointerValue)
                                    pointerIdToCurrentLogicalPosition.remove(pointerId)
                                    // End local cluster translation
                                    if (releasedClusterId != null) {
                                        if (activeClusterTranslations.containsKey(releasedClusterId)) {
                                            endClusterTranslation(releasedClusterId)
                                        }
                                        // Send gesture-end for any cluster vector controls and clean up
                                        val hasClusterVc = vectorControls.values.any { it.targetType == 1 && it.clusterId == releasedClusterId }
                                        if (hasClusterVc && initialLayoutDone) {
                                            onClusterScaleRotation?.invoke(releasedClusterId, 0f, 0f)
                                        }
                                        vectorControls.entries.removeAll { (_, vc) ->
                                            vc.targetType == 1 && vc.clusterId == releasedClusterId
                                        }
                                        vectorControlsUpdateTrigger++
                                    }
                                } else if (draggingHiddenRefs.containsKey(pointerValue)) {
                                    // Remove hidden reference drag when released
                                    val releasedClusterId = draggingHiddenRefs.remove(pointerValue)
                                    pointerIdToCurrentLogicalPosition.remove(pointerId)
                                    // End local cluster translation
                                    if (releasedClusterId != null) {
                                        if (activeClusterTranslations.containsKey(releasedClusterId)) {
                                            endClusterTranslation(releasedClusterId)
                                        }
                                        // Send gesture-end for any cluster vector controls and clean up
                                        val hasClusterVc = vectorControls.values.any { it.targetType == 1 && it.clusterId == releasedClusterId }
                                        if (hasClusterVc && initialLayoutDone) {
                                            onClusterScaleRotation?.invoke(releasedClusterId, 0f, 0f)
                                        }
                                        vectorControls.entries.removeAll { (_, vc) ->
                                            vc.targetType == 1 && vc.clusterId == releasedClusterId
                                        }
                                        vectorControlsUpdateTrigger++
                                    }
                                }
                                pointersThatAttemptedGrab.remove(pointerId)
                                change.consume()
                            }
                        }

                        // Pan/zoom handling: check for 2+ pointers not used for markers/barycenters/vectors/hiddenRefs
                        val activePointers = event.changes.filter { it.pressed }
                        val freePointers = activePointers.filter { change ->
                            val pv = change.id.value
                            !draggingMarkers.containsKey(pv) &&
                                    !draggingBarycenters.containsKey(pv) &&
                                    !draggingHiddenRefs.containsKey(pv) &&
                                    !vectorControls.containsKey(pv)
                        }

                        if (freePointers.size >= 2 && draggingMarkers.isEmpty() && draggingBarycenters.isEmpty() && draggingHiddenRefs.isEmpty()) {
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

            // Draw secondary touch info above the grid
            val secondaryTouchText = locStatic("remote.map.secondaryTouchInfo")

            val headerPaint = Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = canvasHeight / 60f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
            drawContext.canvas.nativeCanvas.drawText(
                secondaryTouchText,
                canvasWidth / 2f,
                canvasHeight / 40f + 8f, // Position above the grid
                headerPaint
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

            // Draw vector control lines for secondary touches
            vectorControls.values.forEach { vectorControl ->
                val currentReferencePosition = if (vectorControl.targetType == 0) {
                    // Input target: use local position if available, otherwise global
                    if (localMarkerPositions.containsKey(vectorControl.markerId)) {
                        localMarkerPositions[vectorControl.markerId]!!
                    } else {
                        currentMarkersState.find { it.id == vectorControl.markerId }?.position
                    }
                } else {
                    // Cluster target: recalculate barycenter or hidden ref position
                    findClusterBarycenter(vectorControl.clusterId, currentMarkersState.take(numberOfInputs), currentClusterConfigs)
                        ?: findHiddenClusterReference(vectorControl.clusterId, currentMarkersState.take(numberOfInputs), currentClusterConfigs)
                }

                if (currentReferencePosition != null) {
                    // Calculate initial vector (from initial marker position to initial touch position)
                    val initialVector = vectorControl.initialTouchPosition - vectorControl.initialMarkerPosition

                    // Draw grey reference line: same length and direction as initial vector, translated to current position
                    val greyLineEnd = currentReferencePosition + initialVector
                    drawLine(
                        color = Color.Gray,
                        start = currentReferencePosition,
                        end = greyLineEnd,
                        strokeWidth = 2f
                    )

                    // Draw active line (current position to current touch position)
                    // For clusters, use cyan to distinguish from input white
                    val lineColor = if (vectorControl.targetType == 1) Color.Cyan else Color.White
                    drawLine(
                        color = lineColor,
                        start = currentReferencePosition,
                        end = vectorControl.currentTouchPosition,
                        strokeWidth = 2f
                    )

                    // Draw height/rotation label for input targets
                    if (vectorControl.targetType == 0) {
                        val initialAngle = calculateAngle(vectorControl.initialMarkerPosition, vectorControl.initialTouchPosition)
                        val currentAngle = calculateAngle(currentReferencePosition, vectorControl.currentTouchPosition)
                        val newRotation = vectorControl.startRotation - (currentAngle - initialAngle)

                        val initialDistance = calculateDistance(vectorControl.initialMarkerPosition, vectorControl.initialTouchPosition)
                        val currentDistance = calculateDistance(currentReferencePosition, vectorControl.currentTouchPosition)
                        val newHeight = if (initialDistance > 10f) {
                            val distanceRatio = currentDistance / initialDistance
                            (vectorControl.startZ + (distanceRatio - 1f) * 3f).coerceIn(0f, 20f)
                        } else {
                            vectorControl.startZ
                        }

                        drawHeightRotationLabel(
                            anchorPosition = greyLineEnd,
                            height = newHeight,
                            rotationDegrees = newRotation,
                            canvasWidth = canvasWidth,
                            textPaint = textPaint
                        )
                    }
                }
            }

            // Draw cluster relationship lines (behind markers)
            if (currentClusterConfigs.isNotEmpty()) {
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
                    clusterConfigs = currentClusterConfigs,
                    barycenterRadius = markerRadius * 0.6f,
                    textPaint = textPaint
                )
            }

            // Draw composite position indicators (behind markers)
            // compositePositions contains delta values (composite - target) in stage meters
            // Also draw for inputs that have a sampler playing even if the delta is zero,
            // so the orange playing ring still appears (at the target position in that case).
            val inputsNeedingCompositeDraw = buildSet {
                addAll(compositePositions.keys)
                for ((id, playing) in samplerPlaying) if (playing) add(id)
            }
            inputsNeedingCompositeDraw.forEach { inputId ->
                val marker = currentMarkersState.find { it.id == inputId }
                if (marker != null && marker.isVisible && inputId <= numberOfInputs) {
                    // Get the target canvas position (use local position if dragging)
                    val targetCanvasPos = if (localMarkerPositions.containsKey(marker.id)) {
                        localMarkerPositions[marker.id]!!
                    } else {
                        marker.position
                    }

                    val delta = compositePositions[inputId]
                    // Convert delta from stage meters to canvas pixels using actual view dimensions
                    val effWidth = canvasWidth - (markerRadius * 2f)
                    val effHeight = canvasHeight - (markerRadius * 2f)
                    val deltaCanvasX = if (delta != null && actualViewWidth > 0f) (delta.first / actualViewWidth) * effWidth else 0f
                    val deltaCanvasY = if (delta != null && actualViewHeight > 0f) -(delta.second / actualViewHeight) * effHeight else 0f  // Negative because Y is inverted

                    // Composite canvas position = target + delta
                    val compositeCanvasPos = Offset(
                        targetCanvasPos.x + deltaCanvasX,
                        targetCanvasPos.y + deltaCanvasY
                    )

                    val isPlaying = samplerPlaying[inputId] == true

                    // Draw the grey composite dot whenever the composite differs from target
                    // by more than 5 pixels. When a sampler is playing, we also want the dot
                    // at the target position (effective = target), so force it.
                    val compositeDiffersVisibly = distance(targetCanvasPos, compositeCanvasPos) > 5f
                    if (compositeDiffersVisibly || isPlaying) {
                        drawCompositePosition(targetCanvasPos, compositeCanvasPos, markerRadius)
                    }

                    // Orange ring around the compound marker when a sampler is playing
                    if (isPlaying) {
                        val ringRadius = markerRadius * 0.8f
                        drawCircle(
                            color = androidx.compose.ui.graphics.Color(0xFFFF9800), // Material Orange 500
                            radius = ringRadius,
                            center = compositeCanvasPos,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = markerRadius * 0.12f)
                        )
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

            // Draw drag coordinates for markers being dragged
            draggingMarkers.values.toSet().forEach { markerId ->
                val canvasPos = localMarkerPositions[markerId]
                val stagePos = markerStagePositions[markerId]
                if (canvasPos != null && stagePos != null) {
                    drawDragCoordinates(
                        position = canvasPos,
                        stageX = stagePos.first,
                        stageY = stagePos.second,
                        canvasWidth = canvasWidth,
                        markerRadius = markerRadius,
                        textPaint = textPaint
                    )
                }
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
                        contentDescription = loc("remote.map.fitStage"),
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
                        contentDescription = loc("remote.map.fitAllInputs"),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }  // End Box (Canvas + floating buttons)
    }
}
