package com.wfsdiy.wfs_control_2

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wfsdiy.wfs_control_2.localization.loc
import kotlin.math.min
import kotlin.math.roundToInt

// Bar colours mirror the desktop Input Visualisation sub-tab
private val VIS_DELAY_COLOR = Color(0xFFD4A017)  // yellow
private val VIS_LEVEL_COLOR = Color(0xFF4A90D9)  // blue
private val VIS_BAR_BACKGROUND = Color(0xFF1A1A1A)

private const val VIS_DELAY_MAX_MS = 350f
private const val VIS_LEVEL_MIN_DB = -60f

/**
 * Mirrors the desktop app's Input Visualisation bargraph (per-output delays and
 * levels for the selected input). Follows the desktop selection by default; a
 * pinned channel (view-only, never moves the desktop selection) can be chosen
 * from the channel grid. With a desktop multi-selection, shows one metric
 * (delays OR levels) across all selected channels.
 */
@Composable
fun VisualisationTab(
    viewModel: MainActivityViewModel,
    numberOfInputs: Int,
    inputParametersState: InputParametersState,
    serverProtocolVersion: Int,
    connected: Boolean
) {
    val visState by viewModel.visState.collectAsState()
    val pinnedChannel by viewModel.visPinnedChannel.collectAsState()
    var showChannelPicker by remember { mutableStateOf(false) }
    var showDelaysInMulti by rememberSaveable { mutableStateOf(true) }

    // Server too old for /remote/vis/* — show a hint instead of empty bars
    if (serverProtocolVersion in 1..2) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text(loc("remote.vis.requiresServerUpdate"), color = Color.LightGray, fontSize = 16.sp)
        }
        return
    }

    val displayedChannels = when {
        pinnedChannel > 0 -> listOf(pinnedChannel)
        visState.selectionSet.size > 1 -> visState.selectionSet
        else -> listOf(visState.primaryChannel)
    }
    val multiMode = displayedChannels.size > 1

    val configuration = LocalConfiguration.current
    val headerFontSize = (configuration.screenWidthDp / 55f).coerceIn(12f, 20f).sp

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            // Header: follow/pin state + channel picker + metric toggle (multi mode)
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val headerChannel = if (pinnedChannel > 0) pinnedChannel else visState.primaryChannel
                val headerName = inputParametersState.getChannel(headerChannel)
                    .getParameter("inputName").stringValue
                val headerLabel = if (pinnedChannel > 0)
                    "${loc("remote.vis.pinned")} $pinnedChannel" else
                    "${loc("remote.vis.follow")} $headerChannel"
                val headerColor = if (pinnedChannel > 0)
                    getMarkerColor(pinnedChannel, isClusterMarker = false) else Color(0xFF333333)

                Box(
                    modifier = Modifier
                        .background(headerColor, shape = RoundedCornerShape(4.dp))
                        .clickable { showChannelPicker = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = if (headerName.isNotEmpty()) "$headerLabel — $headerName" else headerLabel,
                        color = Color.White,
                        fontSize = headerFontSize,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (pinnedChannel > 0) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF333333), shape = RoundedCornerShape(4.dp))
                            .clickable { viewModel.setVisPin(0) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(loc("remote.vis.unpin"), color = Color.White, fontSize = headerFontSize)
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                if (multiMode) {
                    MetricToggleButton(loc("remote.vis.metricDelays"), showDelaysInMulti,
                        VIS_DELAY_COLOR, headerFontSize) { showDelaysInMulti = true }
                    MetricToggleButton(loc("remote.vis.metricLevels"), !showDelaysInMulti,
                        VIS_LEVEL_COLOR, headerFontSize) { showDelaysInMulti = false }
                }

                if (!connected) {
                    Text(loc("remote.vis.noData"), color = Color.Gray, fontSize = headerFontSize)
                }
            }

            if (visState.numOutputs <= 0) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(loc("remote.vis.noData"), color = Color.Gray, fontSize = 16.sp)
                }
            } else if (!multiMode) {
                // Single channel: delays on top, levels below (like the desktop sub-tab)
                val channel = displayedChannels.first()
                val row = visState.rows[channel]
                Column(modifier = Modifier.fillMaxSize()) {
                    BargraphRow(
                        title = "${loc("remote.vis.delays")} — $channel",
                        color = VIS_DELAY_COLOR,
                        row = row,
                        useDelays = true,
                        outputArrays = visState.outputArrays,
                        modifier = Modifier.fillMaxWidth().weight(1f).padding(bottom = 8.dp)
                    )
                    BargraphRow(
                        title = "${loc("remote.vis.levels")} — $channel",
                        color = VIS_LEVEL_COLOR,
                        row = row,
                        useDelays = false,
                        outputArrays = visState.outputArrays,
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    )
                }
            } else {
                // Multi selection: one row per channel in the chosen metric
                val rowColor = if (showDelaysInMulti) VIS_DELAY_COLOR else VIS_LEVEL_COLOR
                val metricLabel = if (showDelaysInMulti)
                    loc("remote.vis.delays") else loc("remote.vis.levels")
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(displayedChannels) { channel ->
                        val name = inputParametersState.getChannel(channel)
                            .getParameter("inputName").stringValue
                        val title = if (name.isNotEmpty())
                            "$metricLabel — $channel ($name)" else "$metricLabel — $channel"
                        BargraphRow(
                            title = title,
                            color = rowColor,
                            row = visState.rows[channel],
                            useDelays = showDelaysInMulti,
                            outputArrays = visState.outputArrays,
                            modifier = Modifier.fillMaxWidth().height(140.dp)
                        )
                    }
                }
            }
        }

        if (showChannelPicker) {
            InputChannelGridOverlay(
                selectedInputId = if (pinnedChannel > 0) pinnedChannel else visState.primaryChannel,
                maxInputs = numberOfInputs,
                inputParametersState = inputParametersState,
                onInputSelected = { inputId ->
                    viewModel.setVisPin(inputId)
                    showChannelPicker = false
                },
                onDismiss = { showChannelPicker = false }
            )
        }
    }
}

@Composable
private fun MetricToggleButton(
    label: String,
    selected: Boolean,
    activeColor: Color,
    fontSize: androidx.compose.ui.unit.TextUnit,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .background(
                if (selected) activeColor else Color(0xFF333333),
                shape = RoundedCornerShape(4.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(label, color = if (selected) Color.Black else Color.LightGray,
            fontSize = fontSize, fontWeight = FontWeight.Bold)
    }
}

/**
 * One bargraph row: a bar per output channel plus a bar per reverb feed (after a
 * double-width gap), filled bottom-up, with the numeric value drawn at the top of
 * each bar. Value labels are tinted by the output's array assignment like the
 * desktop component. Delays span 0-350 ms; levels span -60-0 dB.
 */
@Composable
private fun BargraphRow(
    title: String,
    color: Color,
    row: VisRow?,
    useDelays: Boolean,
    outputArrays: IntArray,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(title, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 2.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .border(1.dp, color.copy(alpha = 0.6f))
                .padding(2.dp)
        ) {
            if (row == null) return@Canvas

            val values = if (useDelays) row.delaysMs else row.levelsDb
            val totalBars = row.numOutputs + row.numReverbs
            if (totalBars <= 0 || values.size < totalBars) return@Canvas

            // Double-width gap between output bars and reverb bars
            val gapUnits = if (row.numReverbs > 0) 2f else 0f
            val unitWidth = size.width / (totalBars + gapUnits)
            val barWidth = unitWidth * 0.85f

            val textPaint = android.graphics.Paint().apply {
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.CENTER
                textSize = min(unitWidth * 0.55f, 26f)
            }
            val drawLabels = unitWidth >= 14f

            for (i in 0 until totalBars) {
                val x = if (i < row.numOutputs) i * unitWidth
                        else (i + gapUnits) * unitWidth
                val value = values[i]
                val fraction = if (useDelays)
                    (value / VIS_DELAY_MAX_MS).coerceIn(0f, 1f)
                else
                    ((value - VIS_LEVEL_MIN_DB) / -VIS_LEVEL_MIN_DB).coerceIn(0f, 1f)

                drawRect(
                    color = VIS_BAR_BACKGROUND,
                    topLeft = Offset(x, 0f),
                    size = Size(barWidth, size.height)
                )
                val fillHeight = size.height * fraction
                drawRect(
                    color = color,
                    topLeft = Offset(x, size.height - fillHeight),
                    size = Size(barWidth, fillHeight)
                )

                if (drawLabels) {
                    val labelColor = when {
                        i >= row.numOutputs -> Color.LightGray  // reverb feeds
                        i < outputArrays.size && outputArrays[i] > 0 ->
                            getMarkerColor(outputArrays[i], isClusterMarker = true)
                        else -> Color.White
                    }
                    textPaint.color = labelColor.toArgb()
                    drawContext.canvas.nativeCanvas.drawText(
                        value.roundToInt().toString(),
                        x + barWidth / 2f,
                        textPaint.textSize + 2f,
                        textPaint
                    )
                }
            }
        }
    }
}
