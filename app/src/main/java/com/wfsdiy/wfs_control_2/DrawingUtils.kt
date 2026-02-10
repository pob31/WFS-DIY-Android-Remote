package com.wfsdiy.wfs_control_2

import android.graphics.Paint
import android.graphics.Typeface

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

// Function to generate distinct colors for markers
fun getMarkerColor(id: Int, isClusterMarker: Boolean = false): Color {
    val totalMarkers = if (isClusterMarker) 10 else 32
    val hue = (id * 360f / totalMarkers) % 360f
    return Color.hsl(hue, if (isClusterMarker) 0.7f else 0.9f, if (isClusterMarker) 0.7f else 0.6f)
}

// Data class for Stage Coordinate drawing information
internal data class StagePointInfo(
    val stageX: Float,
    val stageY: Float,
    val align: Paint.Align,
    val isTopAnchor: Boolean // True if the label is for a top coordinate (TL, TC, TR)
)

// Function to draw stage coordinate labels on the canvas with pan/zoom support
fun DrawScope.drawStageCornerLabels(
    currentStageW: Float,
    currentStageD: Float,
    currentStageOriginX: Float,
    currentStageOriginY: Float,
    canvasPixelW: Float,
    canvasPixelH: Float,
    markerRadius: Float = 0f,
    panOffsetX: Float = 0f,
    panOffsetY: Float = 0f,
    actualViewWidth: Float = currentStageW,
    actualViewHeight: Float = currentStageD
) {
    if (currentStageW <= 0f || currentStageD <= 0f || canvasPixelW <= 0f || canvasPixelH <= 0f) return

    val paint = Paint().apply {
        color = android.graphics.Color.GRAY
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        textSize = if (canvasPixelH > 0f) canvasPixelH / 45f else 20f
    }
    val padding = if (canvasPixelH > 0f) canvasPixelH / 60f else 10f

    val effectiveCanvasWidth = canvasPixelW - (markerRadius * 2f)
    val effectiveCanvasHeight = canvasPixelH - (markerRadius * 2f)

    val viewWidth = if (actualViewWidth > 0f) actualViewWidth else currentStageW
    val viewHeight = if (actualViewHeight > 0f) actualViewHeight else currentStageD

    // Helper to convert physical stage position to canvas pixels
    fun physicalToCanvas(physX: Float, physY: Float): Offset {
        val viewX = physX - panOffsetX
        val viewY = physY - panOffsetY
        val canvasX = (viewX / viewWidth + 0.5f) * effectiveCanvasWidth + markerRadius
        val canvasY = (0.5f - viewY / viewHeight) * effectiveCanvasHeight + markerRadius
        return Offset(canvasX, canvasY)
    }

    // Corner coordinates in origin-relative terms
    val halfW = currentStageW / 2f
    val halfD = currentStageD / 2f

    val bottomLeftX = -halfW - currentStageOriginX
    val bottomLeftY = -halfD - currentStageOriginY
    val bottomRightX = halfW - currentStageOriginX
    val bottomRightY = -halfD - currentStageOriginY
    val topLeftX = -halfW - currentStageOriginX
    val topLeftY = halfD - currentStageOriginY
    val topRightX = halfW - currentStageOriginX
    val topRightY = halfD - currentStageOriginY
    val bottomCenterY = -halfD - currentStageOriginY
    val topCenterY = halfD - currentStageOriginY

    // Data class for label info with physical position
    data class LabelInfo(
        val stageX: Float,
        val stageY: Float,
        val physX: Float,
        val physY: Float,
        val align: Paint.Align
    )

    val labelsToDraw = listOf(
        LabelInfo(bottomLeftX, bottomLeftY, -halfW, -halfD, Paint.Align.LEFT),
        LabelInfo(-currentStageOriginX, bottomCenterY, 0f, -halfD, Paint.Align.CENTER),
        LabelInfo(bottomRightX, bottomRightY, halfW, -halfD, Paint.Align.RIGHT),
        LabelInfo(topLeftX, topLeftY, -halfW, halfD, Paint.Align.LEFT),
        LabelInfo(-currentStageOriginX, topCenterY, 0f, halfD, Paint.Align.CENTER),
        LabelInfo(topRightX, topRightY, halfW, halfD, Paint.Align.RIGHT)
    )

    labelsToDraw.forEach { label ->
        val canvasPos = physicalToCanvas(label.physX, label.physY)

        // Only draw if the label position is visible on canvas
        if (canvasPos.x >= markerRadius - 50f && canvasPos.x <= canvasPixelW - markerRadius + 50f &&
            canvasPos.y >= markerRadius - 20f && canvasPos.y <= canvasPixelH - markerRadius + 20f) {

            val labelText = "(${String.format(Locale.US, "%.1f", label.stageX)}, ${String.format(Locale.US, "%.1f", label.stageY)})"
            paint.textAlign = label.align

            // Position text near the corner with some offset
            val textX = when (label.align) {
                Paint.Align.LEFT -> canvasPos.x + padding
                Paint.Align.RIGHT -> canvasPos.x - padding
                else -> canvasPos.x
            }
            val textY = if (label.physY > 0) {
                canvasPos.y + abs(paint.fontMetrics.ascent) + padding
            } else {
                canvasPos.y - padding
            }

            drawContext.canvas.nativeCanvas.drawText(labelText, textX, textY, paint)
        }
    }
}

internal fun distance(p1: Offset, p2: Offset): Float {
    return sqrt((p1.x - p2.x).pow(2) + (p1.y - p2.y).pow(2))
}

fun <T> DrawScope.drawMarker(
    markerInstance: T,
    isBeingDragged: Boolean,
    textPaint: Paint,
    isClusterMarker: Boolean,
    currentStageW: Float,
    currentStageD: Float,
    currentStageOriginX: Float,
    currentStageOriginY: Float,
    canvasPixelW: Float,
    canvasPixelH: Float,
    isTablet: Boolean = false
) where T : Any {
    val id: Int
    val position: Offset
    val radius: Float
    var markerName: String = ""
    var markerIsLocked: Boolean = false
    var markerIsVisible: Boolean = true

    when (markerInstance) {
        is Marker -> {
            id = markerInstance.id
            position = markerInstance.position
            radius = markerInstance.radius
            markerName = markerInstance.name
            markerIsLocked = markerInstance.isLocked
            markerIsVisible = markerInstance.isVisible
        }
        else -> {
            return
        }
    }

    if (!isClusterMarker && !markerIsVisible) return

    val baseColor = getMarkerColor(id, isClusterMarker)
    val finalOuterColor: Color
    val labelColor: Int

    if (!isClusterMarker && markerIsLocked) {
        finalOuterColor = Color.LightGray
        labelColor = Color.Red.toArgb()
    } else {
        finalOuterColor = if (isBeingDragged) Color.White else baseColor
        labelColor = android.graphics.Color.WHITE
    }

    val innerRadius = radius * 0.6f
    drawCircle(color = finalOuterColor, radius = radius, center = position)
    drawCircle(color = Color.Black, radius = innerRadius, center = position)

    val referenceDimension = min(size.width, size.height)
    val baseTextSize = referenceDimension / (if (isClusterMarker) 40f else 45f) // Larger text (was 45f/52.5f)
    val dynamicBaseTextSizePx = if (isTablet) baseTextSize * 0.9f else baseTextSize // 10% smaller on tablets

    textPaint.textAlign = Paint.Align.CENTER
    textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    textPaint.color = labelColor
    val idText = id.toString()

    if (!isClusterMarker && markerName.isNotBlank()) {
        textPaint.textSize = dynamicBaseTextSizePx * 0.9f
        val idTextMetrics = textPaint.fontMetrics
        val idTextY = position.y - (dynamicBaseTextSizePx * 0.35f) - (idTextMetrics.ascent + idTextMetrics.descent) / 2f
        drawContext.canvas.nativeCanvas.drawText(idText, position.x, idTextY, textPaint)

        textPaint.textSize = dynamicBaseTextSizePx * 0.7f
        val nameTextMetrics = textPaint.fontMetrics
        val nameTextY = position.y + (dynamicBaseTextSizePx * 0.45f) - (nameTextMetrics.ascent + nameTextMetrics.descent) / 2f
        drawContext.canvas.nativeCanvas.drawText(markerName, position.x, nameTextY, textPaint)
    } else {
        textPaint.textSize = dynamicBaseTextSizePx
        val idTextMetrics = textPaint.fontMetrics
        val idTextY = position.y - (idTextMetrics.ascent + idTextMetrics.descent) / 2f
        drawContext.canvas.nativeCanvas.drawText(idText, position.x, idTextY, textPaint)
    }

}

fun DrawScope.drawOriginMarker(
    currentStageW: Float,
    currentStageD: Float,
    currentStageOriginX: Float,
    currentStageOriginY: Float,
    canvasPixelW: Float,
    canvasPixelH: Float,
    markerRadius: Float = 0f,
    panOffsetX: Float = 0f,
    panOffsetY: Float = 0f,
    actualViewWidth: Float = currentStageW,
    actualViewHeight: Float = currentStageD
) {
    if (canvasPixelW <= 0f || canvasPixelH <= 0f) return

    val effectiveCanvasWidth = canvasPixelW - (markerRadius * 2f)
    val effectiveCanvasHeight = canvasPixelH - (markerRadius * 2f)

    val viewWidth = if (actualViewWidth > 0f) actualViewWidth else currentStageW
    val viewHeight = if (actualViewHeight > 0f) actualViewHeight else currentStageD

    // The origin (0, 0) in origin-relative coords is at physical (stageOriginX, stageOriginY)
    // Apply pan offset
    val viewX = currentStageOriginX - panOffsetX
    val viewY = currentStageOriginY - panOffsetY

    // Convert to canvas pixels with uniform scale
    val canvasX = (viewX / viewWidth + 0.5f) * effectiveCanvasWidth + markerRadius
    val canvasY = (0.5f - viewY / viewHeight) * effectiveCanvasHeight + markerRadius

    // Only draw if origin is visible
    if (canvasX < markerRadius - 20f || canvasX > canvasPixelW - markerRadius + 20f ||
        canvasY < markerRadius - 20f || canvasY > canvasPixelH - markerRadius + 20f) {
        return
    }

    // Draw origin marker: circle with crosshairs
    val originRadius = 15f
    val crosshairLength = 20f

    // Draw circle
    drawCircle(
        color = Color.White,
        radius = originRadius,
        center = Offset(canvasX, canvasY)
    )

    // Draw crosshairs
    drawLine(
        color = Color.White,
        start = Offset(canvasX - crosshairLength, canvasY),
        end = Offset(canvasX + crosshairLength, canvasY),
        strokeWidth = 2f
    )
    drawLine(
        color = Color.White,
        start = Offset(canvasX, canvasY - crosshairLength),
        end = Offset(canvasX, canvasY + crosshairLength),
        strokeWidth = 2f
    )
}

/**
 * Draw cluster relationship lines between cluster members and their reference point.
 * For clusters with referenceMode=0 (First Input), lines connect to the first input.
 * For clusters with referenceMode=1 (Barycenter), lines connect to the calculated center
 * and a draggable barycenter marker is drawn.
 */
fun DrawScope.drawClusterLines(
    markers: List<Marker>,
    clusterConfigs: List<ClusterConfig>,
    barycenterRadius: Float = 20f,
    textPaint: Paint? = null
) {
    // Process each cluster configuration
    clusterConfigs.forEach { config ->
        // Get ALL markers in this cluster (for reference/barycenter calculation)
        val allClusterMembers = markers.filter { it.clusterId == config.id }
        // Get only visible markers (for line drawing)
        val visibleClusterMembers = allClusterMembers.filter { it.isVisible }

        // Need at least 2 total members for a valid cluster
        if (allClusterMembers.size < 2) return@forEach

        // Get the reference marker (first by ID in cluster)
        val referenceMarker = allClusterMembers.minByOrNull { it.id }

        // Determine the reference point (using ALL members for calculation)
        val referencePoint: Offset = when {
            // If a tracked input exists, it's the reference
            config.trackedInputId > 0 -> {
                allClusterMembers.find { it.id == config.trackedInputId }?.position
                    ?: calculateBarycenter(allClusterMembers)
            }
            // First Input mode: use first member by ID
            config.referenceMode == 0 -> {
                referenceMarker?.position ?: return@forEach
            }
            // Barycenter mode: calculate center from ALL members
            else -> {
                calculateBarycenter(allClusterMembers)
            }
        }

        // Get cluster color with alpha for lines (matching JUCE)
        val clusterColor = getMarkerColor(config.id, isClusterMarker = true).copy(alpha = 0.5f)

        // Draw lines from reference to each VISIBLE member only
        visibleClusterMembers.forEach { member ->
            val memberPosition = member.position
            // In First Input mode, don't draw line from reference to itself
            // In Barycenter mode, draw lines to all members
            val isReferenceInput = when {
                config.trackedInputId > 0 -> member.id == config.trackedInputId
                config.referenceMode == 0 -> member.id == referenceMarker?.id
                else -> false
            }

            if (!isReferenceInput || config.referenceMode == 1) {
                drawLine(
                    color = clusterColor,
                    start = referencePoint,
                    end = memberPosition,
                    strokeWidth = 3f
                )
            }
        }

        // Draw barycenter marker if in Barycenter mode (and no tracked input)
        if (config.referenceMode == 1 && config.trackedInputId == 0) {
            // Use ALL members for barycenter calculation
            val barycenter = calculateBarycenter(allClusterMembers)

            // Draw filled circle for barycenter
            drawCircle(
                color = clusterColor.copy(alpha = 0.8f),
                radius = barycenterRadius,
                center = barycenter
            )

            // Draw cluster number in center
            if (textPaint != null) {
                textPaint.color = android.graphics.Color.BLACK
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textPaint.textSize = barycenterRadius * 1.2f
                textPaint.textAlign = Paint.Align.CENTER
                val metrics = textPaint.fontMetrics
                val textY = barycenter.y - (metrics.ascent + metrics.descent) / 2f
                drawContext.canvas.nativeCanvas.drawText(config.id.toString(), barycenter.x, textY, textPaint)
            }
        }

        // Draw hidden reference marker if in First Input mode and reference is hidden
        if (config.referenceMode == 0 && config.trackedInputId == 0 && referenceMarker != null && !referenceMarker.isVisible) {
            // Draw a cluster marker at the hidden reference's position
            drawCircle(
                color = clusterColor.copy(alpha = 0.8f),
                radius = barycenterRadius,
                center = referenceMarker.position
            )

            // Draw cluster number in center
            if (textPaint != null) {
                textPaint.color = android.graphics.Color.BLACK
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textPaint.textSize = barycenterRadius * 1.2f
                textPaint.textAlign = Paint.Align.CENTER
                val metrics = textPaint.fontMetrics
                val textY = referenceMarker.position.y - (metrics.ascent + metrics.descent) / 2f
                drawContext.canvas.nativeCanvas.drawText(config.id.toString(), referenceMarker.position.x, textY, textPaint)
            }
        }
    }
}

/**
 * Calculate the barycenter (center of mass) of a list of markers.
 */
private fun calculateBarycenter(members: List<Marker>): Offset {
    if (members.isEmpty()) return Offset.Zero
    val sumX = members.sumOf { it.positionX.toDouble() }.toFloat()
    val sumY = members.sumOf { it.positionY.toDouble() }.toFloat()
    return Offset(sumX / members.size, sumY / members.size)
}

/**
 * Draw the stage boundary on the canvas with pan/zoom support.
 * For shape 0 (box): draws a rectangle
 * For shape 1 (cylinder) or 2 (dome): draws a circle
 *
 * @param stageShape 0=box, 1=cylinder, 2=dome
 * @param stageWidth Stage width in meters (used for box)
 * @param stageDepth Stage depth in meters (used for box)
 * @param stageDiameter Stage diameter in meters (used for cylinder/dome)
 * @param stageOriginX Origin X offset in meters
 * @param stageOriginY Origin Y offset in meters
 * @param canvasPixelW Canvas width in pixels
 * @param canvasPixelH Canvas height in pixels
 * @param markerRadius Marker radius for effective area calculation
 * @param panOffsetX Pan offset X in physical stage meters
 * @param panOffsetY Pan offset Y in physical stage meters
 * @param actualViewWidth Visible width in meters after zoom
 * @param actualViewHeight Visible height in meters after zoom
 */
fun DrawScope.drawStageBoundary(
    stageShape: Int,
    stageWidth: Float,
    stageDepth: Float,
    stageDiameter: Float,
    stageOriginX: Float,
    stageOriginY: Float,
    canvasPixelW: Float,
    canvasPixelH: Float,
    markerRadius: Float = 0f,
    panOffsetX: Float = 0f,
    panOffsetY: Float = 0f,
    actualViewWidth: Float = stageWidth,
    actualViewHeight: Float = stageDepth
) {
    if (canvasPixelW <= 0f || canvasPixelH <= 0f) return

    val effectiveCanvasWidth = canvasPixelW - (markerRadius * 2f)
    val effectiveCanvasHeight = canvasPixelH - (markerRadius * 2f)

    val viewWidth = if (actualViewWidth > 0f) actualViewWidth else stageWidth
    val viewHeight = if (actualViewHeight > 0f) actualViewHeight else stageDepth

    val boundaryColor = Color.DarkGray
    val strokeWidth = 2f

    // Helper to convert physical stage position to canvas pixels
    fun physicalToCanvas(physX: Float, physY: Float): Offset {
        val viewX = physX - panOffsetX
        val viewY = physY - panOffsetY
        val canvasX = (viewX / viewWidth + 0.5f) * effectiveCanvasWidth + markerRadius
        val canvasY = (0.5f - viewY / viewHeight) * effectiveCanvasHeight + markerRadius
        return Offset(canvasX, canvasY)
    }

    when (stageShape) {
        0 -> {
            // Box shape - draw rectangle boundary
            if (stageWidth <= 0f || stageDepth <= 0f) return

            // Calculate the four corners of the stage in physical coords
            val halfW = stageWidth / 2f
            val halfD = stageDepth / 2f

            val topLeft = physicalToCanvas(-halfW, halfD)
            val topRight = physicalToCanvas(halfW, halfD)
            val bottomLeft = physicalToCanvas(-halfW, -halfD)
            val bottomRight = physicalToCanvas(halfW, -halfD)

            // Draw the four edges of the rectangle
            drawLine(color = boundaryColor, start = topLeft, end = topRight, strokeWidth = strokeWidth)
            drawLine(color = boundaryColor, start = topRight, end = bottomRight, strokeWidth = strokeWidth)
            drawLine(color = boundaryColor, start = bottomRight, end = bottomLeft, strokeWidth = strokeWidth)
            drawLine(color = boundaryColor, start = bottomLeft, end = topLeft, strokeWidth = strokeWidth)
        }
        1, 2 -> {
            // Cylinder or Dome shape - draw circle boundary
            if (stageDiameter <= 0f) return

            // Circle center is at physical (0, 0)
            val center = physicalToCanvas(0f, 0f)

            // Calculate radius in canvas pixels using uniform scale
            val pixelsPerMeter = effectiveCanvasWidth / viewWidth
            val circleRadius = (stageDiameter / 2f) * pixelsPerMeter

            drawCircle(
                color = boundaryColor,
                radius = circleRadius,
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
            )
        }
    }
}

/**
 * Draw stage coordinate labels appropriate for the stage shape with pan/zoom support.
 * For box shapes: draws corner coordinates
 * For circular shapes: draws diameter info and cardinal points
 */
fun DrawScope.drawStageLabels(
    stageShape: Int,
    stageWidth: Float,
    stageDepth: Float,
    stageDiameter: Float,
    stageOriginX: Float,
    stageOriginY: Float,
    canvasPixelW: Float,
    canvasPixelH: Float,
    markerRadius: Float = 0f,
    panOffsetX: Float = 0f,
    panOffsetY: Float = 0f,
    actualViewWidth: Float = stageWidth,
    actualViewHeight: Float = stageDepth
) {
    when (stageShape) {
        0 -> {
            // Box shape - use existing corner labels
            drawStageCornerLabels(
                stageWidth, stageDepth,
                stageOriginX, stageOriginY,
                canvasPixelW, canvasPixelH,
                markerRadius,
                panOffsetX, panOffsetY,
                actualViewWidth, actualViewHeight
            )
        }
        1, 2 -> {
            // Circular shape - draw cardinal point labels
            drawCircularStageLabels(
                stageDiameter,
                stageOriginX, stageOriginY,
                canvasPixelW, canvasPixelH,
                markerRadius,
                panOffsetX, panOffsetY,
                actualViewWidth, actualViewHeight
            )
        }
    }
}

/**
 * Draw labels for circular stage shapes showing cardinal directions and diameter.
 */
/**
 * Draw composite position indicator showing the final DSP position after all transformations.
 * Draws a thin grey line from target to composite position and a small grey filled circle.
 *
 * @param targetCanvasPos The canvas position of the target (user-controlled) marker
 * @param compositeCanvasPos The canvas position of the composite (DSP-computed) position
 * @param markerRadius The radius of the main marker for scaling the composite indicator
 */
fun DrawScope.drawCompositePosition(
    targetCanvasPos: Offset,
    compositeCanvasPos: Offset,
    markerRadius: Float
) {
    val compositeRadius = markerRadius * 0.4f

    // Draw thin grey line from target to composite
    drawLine(
        color = Color.Gray,
        start = targetCanvasPos,
        end = compositeCanvasPos,
        strokeWidth = 1.5f
    )

    // Draw small grey filled circle at composite position
    drawCircle(
        color = Color.Gray,
        radius = compositeRadius,
        center = compositeCanvasPos
    )
}

private fun DrawScope.drawCircularStageLabels(
    stageDiameter: Float,
    stageOriginX: Float,
    stageOriginY: Float,
    canvasPixelW: Float,
    canvasPixelH: Float,
    markerRadius: Float = 0f,
    panOffsetX: Float = 0f,
    panOffsetY: Float = 0f,
    actualViewWidth: Float = stageDiameter,
    actualViewHeight: Float = stageDiameter
) {
    if (stageDiameter <= 0f || canvasPixelW <= 0f || canvasPixelH <= 0f) return

    val paint = Paint().apply {
        color = android.graphics.Color.GRAY
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        textSize = if (canvasPixelH > 0f) canvasPixelH / 45f else 20f
    }
    val padding = if (canvasPixelH > 0f) canvasPixelH / 60f else 10f

    val effectiveCanvasWidth = canvasPixelW - (markerRadius * 2f)
    val effectiveCanvasHeight = canvasPixelH - (markerRadius * 2f)

    val viewWidth = if (actualViewWidth > 0f) actualViewWidth else stageDiameter
    val viewHeight = if (actualViewHeight > 0f) actualViewHeight else stageDiameter

    val radius = stageDiameter / 2f

    // Helper to convert physical stage position to canvas pixels
    fun physicalToCanvas(physX: Float, physY: Float): Offset {
        val viewX = physX - panOffsetX
        val viewY = physY - panOffsetY
        val canvasX = (viewX / viewWidth + 0.5f) * effectiveCanvasWidth + markerRadius
        val canvasY = (0.5f - viewY / viewHeight) * effectiveCanvasHeight + markerRadius
        return Offset(canvasX, canvasY)
    }

    // Cardinal points in origin-relative coordinates
    val northY = radius - stageOriginY
    val southY = -radius - stageOriginY
    val eastX = radius - stageOriginX
    val westX = -radius - stageOriginX

    // Draw labels at four cardinal positions (only if visible)
    // Top center (North/Back)
    val topPos = physicalToCanvas(0f, radius)
    if (topPos.y >= markerRadius - 20f && topPos.y <= canvasPixelH - markerRadius + 20f) {
        paint.textAlign = Paint.Align.CENTER
        val topLabel = "(0, ${String.format(Locale.US, "%.1f", northY)})"
        val topCanvasY = topPos.y + abs(paint.fontMetrics.ascent) + padding
        drawContext.canvas.nativeCanvas.drawText(topLabel, topPos.x, topCanvasY, paint)
    }

    // Bottom center (South/Front)
    val bottomPos = physicalToCanvas(0f, -radius)
    if (bottomPos.y >= markerRadius - 20f && bottomPos.y <= canvasPixelH - markerRadius + 20f) {
        paint.textAlign = Paint.Align.CENTER
        val bottomLabel = "(0, ${String.format(Locale.US, "%.1f", southY)})"
        val bottomCanvasY = bottomPos.y - padding
        drawContext.canvas.nativeCanvas.drawText(bottomLabel, bottomPos.x, bottomCanvasY, paint)
    }

    // Left center (West)
    val leftPos = physicalToCanvas(-radius, 0f)
    if (leftPos.x >= markerRadius - 50f && leftPos.x <= canvasPixelW - markerRadius + 50f) {
        paint.textAlign = Paint.Align.LEFT
        val leftLabel = "(${String.format(Locale.US, "%.1f", westX)}, 0)"
        val leftCanvasX = leftPos.x + padding
        drawContext.canvas.nativeCanvas.drawText(leftLabel, leftCanvasX, leftPos.y, paint)
    }

    // Right center (East)
    val rightPos = physicalToCanvas(radius, 0f)
    if (rightPos.x >= markerRadius - 50f && rightPos.x <= canvasPixelW - markerRadius + 50f) {
        paint.textAlign = Paint.Align.RIGHT
        val rightLabel = "(${String.format(Locale.US, "%.1f", eastX)}, 0)"
        val rightCanvasX = rightPos.x - padding
        drawContext.canvas.nativeCanvas.drawText(rightLabel, rightCanvasX, rightPos.y, paint)
    }
}

/**
 * Draw stage coordinates next to a marker being dragged.
 * Yellow text "(X.X, Y.Y)" that flips left/right based on screen half.
 */
fun DrawScope.drawDragCoordinates(
    position: Offset,
    stageX: Float,
    stageY: Float,
    canvasWidth: Float,
    markerRadius: Float,
    textPaint: Paint
) {
    val label = "(${String.format(Locale.US, "%.1f", stageX)}, ${String.format(Locale.US, "%.1f", stageY)})"
    textPaint.color = 0xFFFFFF00.toInt()  // Yellow
    textPaint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    textPaint.textSize = min(size.width, size.height) / 40f * 0.6f

    val offset = markerRadius + 5f
    if (position.x < canvasWidth / 2f) {
        textPaint.textAlign = Paint.Align.LEFT
        drawContext.canvas.nativeCanvas.drawText(label, position.x + offset, position.y, textPaint)
    } else {
        textPaint.textAlign = Paint.Align.RIGHT
        drawContext.canvas.nativeCanvas.drawText(label, position.x - offset, position.y, textPaint)
    }
}

/**
 * Draw height and rotation values near the grey reference endpoint during secondary touch.
 * Yellow text "Z=X.XXm  R=XXX°" that flips left/right based on screen half.
 */
fun DrawScope.drawHeightRotationLabel(
    anchorPosition: Offset,
    height: Float,
    rotationDegrees: Float,
    canvasWidth: Float,
    textPaint: Paint
) {
    val label = "Z=${String.format(Locale.US, "%.2f", height)}m  R=${rotationDegrees.toInt()}\u00B0"
    textPaint.color = 0xFFFFFF00.toInt()  // Yellow
    textPaint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    textPaint.textSize = min(size.width, size.height) / 40f * 0.6f

    val offset = 8f
    if (anchorPosition.x < canvasWidth / 2f) {
        textPaint.textAlign = Paint.Align.LEFT
        drawContext.canvas.nativeCanvas.drawText(label, anchorPosition.x + offset, anchorPosition.y, textPaint)
    } else {
        textPaint.textAlign = Paint.Align.RIGHT
        drawContext.canvas.nativeCanvas.drawText(label, anchorPosition.x - offset, anchorPosition.y, textPaint)
    }
}
