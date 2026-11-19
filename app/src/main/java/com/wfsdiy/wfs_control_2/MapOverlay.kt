package com.wfsdiy.wfs_control_2

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * The desktop Map's speakers and drawn shapes on the tablet map, read-only
 * (MapProtocol). Both are drawn under the input markers, as on the desktop.
 *
 * [toCanvas] maps origin-relative stage metres to canvas pixels (the map's own
 * stageToCanvasPosition with the current pan and zoom).
 */

/** Outline shapes: colour, width (dp, the desktop's screen pixels) and dashes as drawn
 *  on the desktop (dash max(4, 3w), gap max(3, 2w)), plus the label above. */
fun DrawScope.drawMapShapes(
    shapes: List<MapShape>,
    toCanvas: (Float, Float) -> Offset
) {
    if (shapes.isEmpty()) return
    val dp = density
    val labelPaint = Paint().apply {
        isAntiAlias = true
        textSize = 12f * dp
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT
    }

    for (shape in shapes) {
        val n = shape.pointCount
        if (n < 2) continue
        val path = Path()
        val first = toCanvas(shape.points[0], shape.points[1])
        path.moveTo(first.x, first.y)
        for (i in 1 until n) {
            val p = toCanvas(shape.points[2 * i], shape.points[2 * i + 1])
            path.lineTo(p.x, p.y)
        }
        if (shape.closed) path.close()

        val widthDp = shape.lineWidth.coerceIn(0.5f, 10f)
        val strokePx = widthDp * dp
        val effect = if (shape.dashed)
            PathEffect.dashPathEffect(floatArrayOf(max(4f, 3f * widthDp) * dp, max(3f, 2f * widthDp) * dp), 0f)
        else null
        val colour = Color(shape.argb)
        drawPath(path, colour, style = Stroke(width = strokePx, cap = StrokeCap.Butt,
            join = StrokeJoin.Miter, pathEffect = effect))

        if (shape.label.isNotEmpty()) {
            val anchor = toCanvas(shape.labelX, shape.labelY)
            labelPaint.color = shape.argb
            drawContext.canvas.nativeCanvas.drawText(shape.label, anchor.x, anchor.y - 4f * dp, labelPaint)
        }
    }
}

/** Speakers as the desktop draws them: a keystone (wide back, narrow front toward where
 *  the speaker points), a membrane triangle in the array colour (light grey when single)
 *  and the output number. Sized from the input marker, as the desktop's 24 px keystone
 *  is to its 14 px marker. */
fun DrawScope.drawMapSpeakers(
    speakers: List<MapSpeaker>,
    markerRadius: Float,
    toCanvas: (Float, Float) -> Offset
) {
    if (speakers.isEmpty()) return
    val height = max(24f, markerRadius * 1.7f)
    val backWidth = height * (21f / 24f)
    val frontWidth = height * (11f / 24f)
    val numberPaint = Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.BLACK
        textSize = height * 0.5f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val outline = Stroke(width = max(1.5f, height / 16f))
    val membraneOutline = Stroke(width = max(1f, height / 24f))

    for (sp in speakers) {
        val c = toCanvas(sp.x, sp.y)
        val rad = Math.toRadians(sp.orientation.toDouble())
        // Speaker faces (sin, cos) on screen; dir = the narrow tip's side.
        val dirX = (-sin(rad)).toFloat()
        val dirY = (-cos(rad)).toFloat()
        val perpX = -dirY
        val perpY = dirX

        val front = Offset(c.x + dirX * height * 0.5f, c.y + dirY * height * 0.5f)
        val back = Offset(c.x - dirX * height * 0.5f, c.y - dirY * height * 0.5f)
        val frontLeft = Offset(front.x + perpX * frontWidth * 0.5f, front.y + perpY * frontWidth * 0.5f)
        val frontRight = Offset(front.x - perpX * frontWidth * 0.5f, front.y - perpY * frontWidth * 0.5f)
        val backLeft = Offset(back.x + perpX * backWidth * 0.5f, back.y + perpY * backWidth * 0.5f)
        val backRight = Offset(back.x - perpX * backWidth * 0.5f, back.y - perpY * backWidth * 0.5f)

        val keystone = Path().apply {
            moveTo(backLeft.x, backLeft.y)
            lineTo(frontLeft.x, frontLeft.y)
            lineTo(frontRight.x, frontRight.y)
            lineTo(backRight.x, backRight.y)
            close()
        }
        drawPath(keystone, Color.Black)
        drawPath(keystone, Color.White, style = outline)

        val tip = Offset(back.x + dirX * height * 0.55f, back.y + dirY * height * 0.55f)
        val membrane = Path().apply {
            moveTo(backLeft.x, backLeft.y)
            lineTo(tip.x, tip.y)
            lineTo(backRight.x, backRight.y)
            close()
        }
        val membraneColour = if (sp.array == 0) Color.LightGray else getMarkerColor(sp.array, isClusterMarker = true)
        drawPath(membrane, membraneColour)
        drawPath(membrane, Color.White, style = membraneOutline)

        val cx = (backLeft.x + backRight.x + tip.x) / 3f
        val cy = (backLeft.y + backRight.y + tip.y) / 3f
        val metrics = numberPaint.fontMetrics
        drawContext.canvas.nativeCanvas.drawText(sp.number.toString(), cx,
            cy - (metrics.ascent + metrics.descent) / 2f, numberPaint)
    }
}
