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

// Function to draw stage coordinate labels on the canvas
fun DrawScope.drawStageCornerLabels(
    currentStageW: Float,
    currentStageD: Float,
    currentStageOriginX: Float,
    currentStageOriginY: Float,
    canvasPixelW: Float,
    canvasPixelH: Float,
    markerRadius: Float = 0f
) {
    if (currentStageW <= 0f || currentStageD <= 0f || canvasPixelW <= 0f || canvasPixelH <= 0f) return

    val paint = Paint().apply {
        color = android.graphics.Color.GRAY
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        textSize = if (canvasPixelH > 0f) canvasPixelH / 45f else 20f // Default if canvasPixelH is 0
    }
    val padding = if (canvasPixelH > 0f) canvasPixelH / 60f else 10f

    // Adjust canvas boundaries to account for marker radius
    val effectiveCanvasWidth = canvasPixelW - (markerRadius * 2f)
    val effectiveCanvasHeight = canvasPixelH - (markerRadius * 2f)

    // Corner coordinates in origin-relative terms
    // Canvas shows the physical stage, so we convert physical positions to origin-relative
    // Physical edges: left=-stageW/2, right=+stageW/2, front=-stageD/2, back=+stageD/2
    // Origin-relative = physical - origin
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

    // Center labels show X=0 for bottom/top center (relative to origin)
    val bottomCenterY = -halfD - currentStageOriginY
    val topCenterY = halfD - currentStageOriginY

    val pointsToDraw = listOf(
        StagePointInfo(bottomLeftX, bottomLeftY, Paint.Align.LEFT, false), // Bottom-Left (front-left)
        StagePointInfo(-currentStageOriginX, bottomCenterY, Paint.Align.CENTER, false), // Bottom-Center (front-center, X at origin)
        StagePointInfo(bottomRightX, bottomRightY, Paint.Align.RIGHT, false), // Bottom-Right (front-right)
        StagePointInfo(topLeftX, topLeftY, Paint.Align.LEFT, true),         // Top-Left (back-left)
        StagePointInfo(-currentStageOriginX, topCenterY, Paint.Align.CENTER, true), // Top-Center (back-center, X at origin)
        StagePointInfo(topRightX, topRightY, Paint.Align.RIGHT, true)      // Top-Right (back-right)
    )

    pointsToDraw.forEach { point ->
        // Display the stage coordinates based on origin
        val labelText = "(${String.format(Locale.US, "%.1f", point.stageX)}, ${String.format(Locale.US, "%.1f", point.stageY)})"
        paint.textAlign = point.align

        // Fixed canvas positions - corners and center of top/bottom edges
        val canvasX = when (point.align) {
            Paint.Align.LEFT -> markerRadius + padding
            Paint.Align.RIGHT -> effectiveCanvasWidth + markerRadius - padding
            else -> (effectiveCanvasWidth / 2f) + markerRadius // CENTER
        }
        
        val canvasY = if (point.isTopAnchor) {
            markerRadius + padding + abs(paint.fontMetrics.ascent)
        } else {
            effectiveCanvasHeight + markerRadius - padding
        }

        drawContext.canvas.nativeCanvas.drawText(labelText, canvasX, canvasY, paint)
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
        is ClusterMarker -> {
            id = markerInstance.id
            position = markerInstance.position
            radius = markerInstance.radius
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
    markerRadius: Float = 0f
) {
    if (currentStageW <= 0f || currentStageD <= 0f || canvasPixelW <= 0f || canvasPixelH <= 0f) return

    // Adjust canvas boundaries to account for marker radius
    val effectiveCanvasWidth = canvasPixelW - (markerRadius * 2f)
    val effectiveCanvasHeight = canvasPixelH - (markerRadius * 2f)

    // The origin (0, 0) in origin-relative coords is at physical (stageOriginX, stageOriginY)
    // Convert physical position to normalized canvas position
    val normalizedX = (currentStageOriginX + currentStageW / 2f) / currentStageW
    val normalizedY = (currentStageOriginY + currentStageD / 2f) / currentStageD

    val canvasX = normalizedX * effectiveCanvasWidth + markerRadius
    val canvasY = (1f - normalizedY) * effectiveCanvasHeight + markerRadius

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
    barycenterRadius: Float = 20f
) {
    // Process each cluster configuration
    clusterConfigs.forEach { config ->
        // Get all markers in this cluster
        val clusterMembers = markers.filter { it.clusterId == config.id && it.isVisible }

        // Need at least 2 members to draw lines
        if (clusterMembers.size < 2) return@forEach

        // Determine the reference point
        val referencePoint: Offset = when {
            // If a tracked input exists, it's the reference
            config.trackedInputId > 0 -> {
                clusterMembers.find { it.id == config.trackedInputId }?.position
                    ?: calculateBarycenter(clusterMembers)
            }
            // First Input mode: use first member by ID
            config.referenceMode == 0 -> {
                clusterMembers.minByOrNull { it.id }?.position ?: return@forEach
            }
            // Barycenter mode: calculate center
            else -> {
                calculateBarycenter(clusterMembers)
            }
        }

        // Get cluster color with alpha for lines (matching JUCE)
        val clusterColor = getMarkerColor(config.id, isClusterMarker = true).copy(alpha = 0.5f)

        // Draw lines from reference to each member
        clusterMembers.forEach { member ->
            val memberPosition = member.position
            // In First Input mode, don't draw line from reference to itself
            // In Barycenter mode, draw lines to all members
            val isReferenceInput = when {
                config.trackedInputId > 0 -> member.id == config.trackedInputId
                config.referenceMode == 0 -> member.id == clusterMembers.minByOrNull { it.id }?.id
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
            val barycenter = calculateBarycenter(clusterMembers)

            // Draw filled circle for barycenter
            drawCircle(
                color = clusterColor.copy(alpha = 0.8f),
                radius = barycenterRadius,
                center = barycenter
            )

            // Draw cluster number in center
            // Note: For text we'd need native canvas, keep it simple with just the circle
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
