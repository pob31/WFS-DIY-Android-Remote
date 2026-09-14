package com.wfsdiy.wfs_control_2

import android.os.SystemClock
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wfsdiy.wfs_control_2.localization.loc
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

// Bar colours mirror the desktop Input Visualisation sub-tab
private val VIS_DELAY_COLOR = Color(0xFFD4A017)  // yellow
private val VIS_LEVEL_COLOR = Color(0xFF4A90D9)  // blue
private val VIS_BAR_BACKGROUND = Color(0xFF1A1A1A)
// Amber like the protocol-mismatch banner: a setup problem, not a wait
private val VIS_WARNING_COLOR = Color(0xFFFFA000)

private const val VIS_DELAY_MAX_MS = 350f
private const val VIS_LEVEL_MIN_DB = -60f

// Bar value labels. Sized in dp, not sp: a label has to fit its bar, and the bar's width
// does not follow the system font scale, so an sp floor would hide the values on bars
// that have room for them whenever the font scale is raised.
private val VIS_LABEL_MAX_SIZE = 13.dp
private val VIS_LABEL_MIN_SIZE = 8.dp     // legibility floor: below it, bars without values
private const val VIS_LABEL_FILL = 0.9f   // share of a bar's slot a label may span
private val VIS_LABEL_STRIP_PAD = 3.dp    // above and below the text, in its own strip

// Pull path (/remote/vis/request). The tab checks its data every 2 s and asks at most
// 6 times before it waits for fresh rows. A desktop that answers the request also
// repeats its vis state at least every 2 s, so shown rows that have not moved for 6 s
// mean it has lost our pin or stopped sending to us.
private const val VIS_WATCHDOG_INTERVAL_MS = 2000L
private const val VIS_REQUEST_MAX_ATTEMPTS = 6
private const val VIS_ROW_STALE_MS = 6000L

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
    // Which channels the picker may offer. Not a count: a pinnable channel can be
    // numbered above the channel count, and 1..count would both omit it and offer
    // numbers that were deleted.
    inventory: ChannelInventory,
    inputParametersState: InputParametersState,
    serverProtocolVersion: Int,
    connected: Boolean,
    // Hoisted to WFSControlApp so the multi-mode metric choice survives tab
    // switches (this composable is disposed whenever another tab is shown)
    showDelaysInMulti: Boolean,
    onShowDelaysInMultiChange: (Boolean) -> Unit
) {
    val visState by viewModel.visState.collectAsState()
    val pinnedChannel by viewModel.visPinnedChannel.collectAsState()
    val desktopNotHearing by viewModel.desktopNotHearing.collectAsState()
    var showChannelPicker by remember { mutableStateOf(false) }

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

    // Pull path. The desktop pushes vis on change, so a tablet that lost the push, or
    // whose pin the desktop dropped, had nothing to recover from on a static scene. Ask
    // on entry, on every reconnect while shown, and whenever the watchdog finds the
    // shown data missing, half there or stale. Keyed on `connected` only: the loop reads
    // the latest values, so a data update neither restarts it nor resets its budget.
    val latestVisState by rememberUpdatedState(visState)
    val latestPinnedChannel by rememberUpdatedState(pinnedChannel)
    val latestInventory by rememberUpdatedState(inventory)
    LaunchedEffect(connected) {
        if (!connected) return@LaunchedEffect
        val watchdog = VisRefreshWatchdog()
        while (isActive) {
            val state = latestVisState
            val displayed = displayedVisChannels(state, latestPinnedChannel, latestInventory)
            when (watchdog.next(state, displayed, SystemClock.elapsedRealtime())) {
                VisRefreshWatchdog.Action.REQUEST -> viewModel.requestVisRefresh()
                VisRefreshWatchdog.Action.RESYNC -> viewModel.requestVisFallbackResync()
                VisRefreshWatchdog.Action.NONE -> {}
            }
            delay(VIS_WATCHDOG_INTERVAL_MS)
        }
    }

    // Pings arrive (green dot) but the desktop never hears our pongs, so it sends this
    // tablet nothing: say what to check instead of "Waiting for data…".
    val notHeard = connected && desktopNotHearing

    val displayedChannels = displayedVisChannels(visState, pinnedChannel, inventory)
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
                val headerChannel = when {
                    pinnedChannel > 0 -> pinnedChannel
                    !multiMode -> displayedChannels.first()
                    else -> visState.primaryChannel
                }
                val headerName = inputParametersState.getChannel(headerChannel)
                    .getParameter("inputName").stringValue
                val headerLabel = if (pinnedChannel > 0)
                    "${loc("remote.vis.pinned")} $pinnedChannel" else
                    "${loc("remote.vis.follow")} $headerChannel"
                // The pinned channel's colour as the map shows it: the one picked on the
                // desktop, else the derived hue
                val headerColor = if (pinnedChannel > 0)
                    resolveInputColor(
                        inputParametersState.getChannel(pinnedChannel)
                            .parameters["inputColour"]?.normalizedValue?.toInt(),
                        pinnedChannel
                    ) else Color(0xFF333333)
                // A picked colour can be light. Switch where black and white contrast
                // equally, like the desktop's channel selector (getContrastingTextColor).
                val headerTextColor =
                    if (headerColor.luminance() > 0.179f) Color.Black else Color.White

                Box(
                    modifier = Modifier
                        .background(headerColor, shape = RoundedCornerShape(4.dp))
                        .clickable { showChannelPicker = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = if (headerName.isNotEmpty()) "$headerLabel — $headerName" else headerLabel,
                        color = headerTextColor,
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

                // The long hint takes the flexible slot, so it wraps there instead of
                // squeezing the buttons.
                if (notHeard) {
                    Text(
                        loc("remote.vis.desktopNotHearing"),
                        color = VIS_WARNING_COLOR,
                        fontSize = headerFontSize,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                if (multiMode) {
                    MetricToggleButton(loc("remote.vis.metricDelays"), showDelaysInMulti,
                        VIS_DELAY_COLOR, headerFontSize) { onShowDelaysInMultiChange(true) }
                    MetricToggleButton(loc("remote.vis.metricLevels"), !showDelaysInMulti,
                        VIS_LEVEL_COLOR, headerFontSize) { onShowDelaysInMultiChange(false) }
                }

                if (!connected) {
                    Text(loc("remote.vis.noData"), color = Color.Gray, fontSize = headerFontSize)
                }
            }

            if (visState.numOutputs <= 0) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (notHeard) {
                        Text(loc("remote.vis.desktopNotHearing"), color = VIS_WARNING_COLOR,
                            fontSize = 16.sp, textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp))
                    } else {
                        Text(loc("remote.vis.noData"), color = Color.Gray, fontSize = 16.sp)
                    }
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
                inventory = inventory,
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

/**
 * The channels the tab shows rows for: the pinned one, else the desktop's selection,
 * else its primary. A primary the desktop has not vouched for (the default 1 before any
 * selection arrived, or one kept when an older desktop sent 0 for a deleted channel)
 * gives way to the first live channel if it is not live, instead of the bars waiting on
 * rows that can never come. One the desktop named is always shown: it is live there,
 * and this tablet's inventory may simply not have caught up (a lost /remote/channelList
 * after the channel was added). An inventory that is not known yet vetoes nothing.
 */
internal fun displayedVisChannels(
    state: VisualisationState,
    pinnedChannel: Int,
    inventory: ChannelInventory
): List<Int> = when {
    pinnedChannel > 0 -> listOf(pinnedChannel)
    state.selectionSet.isNotEmpty() -> state.selectionSet
    state.primaryConfirmed || inventory.isEmpty || inventory.contains(state.primaryChannel) ->
        listOf(state.primaryChannel)
    else -> listOf(inventory.numbers.first())
}

/**
 * Decides, on each pass of the tab's 2 s check while it is shown and connected, whether
 * to ask the desktop for its vis state again. One instance per visit and connection:
 * the effect that owns it restarts on every reconnect. Pure, with the clock passed in,
 * so the policy can be unit-tested.
 */
internal class VisRefreshWatchdog(
    private val maxAttempts: Int = VIS_REQUEST_MAX_ATTEMPTS,
    private val staleMs: Long = VIS_ROW_STALE_MS
) {
    enum class Action { NONE, REQUEST, RESYNC }

    private var entered = false
    private var attempts = 0
    private var newestRevisionSeen = Long.MIN_VALUE
    private var resyncAsked = false

    fun next(state: VisualisationState, displayed: List<Int>, nowMs: Long): Action {
        // Fresh rows for what is shown: the desktop is answering, so a later gap is a
        // new episode with a fresh budget. Without the budget, a desktop that cannot
        // answer (older than the request, or not hearing us) would be asked forever.
        val newestRevision = displayed.maxOfOrNull { state.rows[it]?.revision ?: Long.MIN_VALUE }
            ?: Long.MIN_VALUE
        if (newestRevision > newestRevisionSeen) {
            newestRevisionSeen = newestRevision
            attempts = 0
        }
        // Always on entry: the state can be old in ways no check sees (a lost selection
        // change), and the request also restates our pin.
        val wanted = !entered || needsRefresh(state, displayed, nowMs)
        entered = true
        if (!wanted) return Action.NONE
        if (attempts < maxAttempts) {
            attempts++
            return Action.REQUEST
        }
        // Still no config after the last attempt: the desktop drops the request, or
        // every reply was lost. Its full dump carries the config. The service also
        // keeps this to once per connection, across visits.
        if (state.numOutputs <= 0 && !resyncAsked) {
            resyncAsked = true
            return Action.RESYNC
        }
        return Action.NONE
    }

    /** No config yet, a shown row missing or only half there, or every shown row stale. */
    fun needsRefresh(state: VisualisationState, displayed: List<Int>, nowMs: Long): Boolean {
        if (state.numOutputs <= 0) return true
        var newestReceivedMs = Long.MIN_VALUE
        for (channel in displayed) {
            val row = state.rows[channel] ?: return true
            if (!row.hasDelays || !row.hasLevels) return true
            newestReceivedMs = maxOf(newestReceivedMs, row.receivedAtMs)
        }
        return displayed.isNotEmpty() && nowMs - newestReceivedMs > staleMs
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
 * double-width gap), filled bottom-up, with the numeric value drawn in a strip above
 * the fill. Value labels are tinted by the output's array assignment like the
 * desktop component. Delays span 0-350 ms; levels span -60-0 dB. Every bar always
 * fits the width; on a rig too wide for legible values the bars are drawn alone.
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
    // Bold like the desktop's values, and one Paint for the row's lifetime rather than
    // one per frame. Only its size and colour change while drawing.
    val labelPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
    }

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

            // One text size for the whole row, fitted to the widest value this metric
            // normally shows, so the values line up and never run into their neighbours.
            // Below the floor they would be unreadable, so the row shows bars alone.
            val maxTextPx = VIS_LABEL_MAX_SIZE.toPx()
            val minTextPx = VIS_LABEL_MIN_SIZE.toPx()
            val labelRoom = unitWidth * VIS_LABEL_FILL
            labelPaint.textSize = maxTextPx
            val templateWidth = labelPaint.measureText(if (useDelays) "888" else "-88")
            val textPx = min(maxTextPx, maxTextPx * labelRoom / templateWidth)
            val drawLabels = textPx >= minTextPx

            // The values get a strip of their own and the fill stays below it, as on the
            // desktop: a full bar used to paint over its own value.
            val stripHeight = if (drawLabels) textPx + 2f * VIS_LABEL_STRIP_PAD.toPx() else 0f
            val fillArea = (size.height - stripHeight).coerceAtLeast(0f)
            labelPaint.textSize = textPx
            val metrics = labelPaint.fontMetrics
            // Baseline offset that centres the text on the strip's middle; font metrics
            // scale with the size, so a shrunk label scales it too.
            val centringOffset = -(metrics.ascent + metrics.descent) / 2f

            for (i in 0 until totalBars) {
                val x = if (i < row.numOutputs) i * unitWidth
                        else (i + gapUnits) * unitWidth
                // A NaN made the rounding throw. Any non-finite value now draws an empty
                // bar and no label: a fraction of 0 for both metrics, where a stand-in
                // value of 0 would read as a full 0 dB level.
                val raw = values[i]
                val fraction = when {
                    !raw.isFinite() -> 0f
                    useDelays -> (raw / VIS_DELAY_MAX_MS).coerceIn(0f, 1f)
                    else -> ((raw - VIS_LEVEL_MIN_DB) / -VIS_LEVEL_MIN_DB).coerceIn(0f, 1f)
                }

                drawRect(
                    color = VIS_BAR_BACKGROUND,
                    topLeft = Offset(x, 0f),
                    size = Size(barWidth, size.height)
                )
                val fillHeight = fillArea * fraction
                drawRect(
                    color = color,
                    topLeft = Offset(x, size.height - fillHeight),
                    size = Size(barWidth, fillHeight)
                )

                if (!drawLabels || !raw.isFinite()) continue

                val label = roundHalfAwayFromZero(raw).toString()
                val labelColor = when {
                    i >= row.numOutputs -> Color.White  // reverb feeds: text colour, as on the desktop
                    i < outputArrays.size && outputArrays[i] > 0 ->
                        getMarkerColor(outputArrays[i], isClusterMarker = true)
                    else -> Color.White
                }
                labelPaint.color = labelColor.toArgb()

                // A value wider than the template (a 4-digit delay, a level below -99 dB)
                // shrinks on its own instead of shrinking the whole row, and is left out
                // if that would take it under the floor.
                var labelPx = textPx
                val labelWidth = labelPaint.measureText(label)
                if (labelWidth > labelRoom) {
                    labelPx = textPx * labelRoom / labelWidth
                    if (labelPx < minTextPx) continue
                    labelPaint.textSize = labelPx
                }
                drawContext.canvas.nativeCanvas.drawText(
                    label,
                    x + barWidth / 2f,
                    stripHeight / 2f + centringOffset * labelPx / textPx,
                    labelPaint
                )
                labelPaint.textSize = textPx
            }
        }
    }
}

/**
 * Rounds halves away from zero like the desktop's std::round, so both apps show the
 * same integer: roundToInt() rounds halves up, which turns -12.5 dB into -12, not -13.
 */
private fun roundHalfAwayFromZero(value: Float): Int {
    val magnitude = abs(value).roundToInt()
    return if (value < 0f) -magnitude else magnitude
}
