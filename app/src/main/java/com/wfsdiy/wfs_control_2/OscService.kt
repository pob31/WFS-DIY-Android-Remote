package com.wfsdiy.wfs_control_2

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.SystemClock

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.wfsdiy.wfs_control_2.localization.locStatic
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * One channel's visualisation rows (protocol v3, /remote/vis/delays + /remote/vis/levels).
 * Values are output channels first, then reverb feeds. Delays in ms; levels in
 * display-ready dB (clamped [-60, 0] server-side). The revision counter makes every
 * update distinct so Compose recomposes even when values are numerically identical.
 *
 * The two halves travel as separate datagrams, and a half that has not arrived is
 * filled with placeholder values so the row can still be drawn. [hasDelays] and
 * [hasLevels] say which halves are real, so a row with one half lost reads as
 * incomplete rather than as a delay or a level of nothing. [receivedAtMs]
 * (SystemClock.elapsedRealtime) is when either half last arrived, which tells the
 * Visualisation tab that the desktop stopped refreshing it.
 */
data class VisRow(
    val delaysMs: FloatArray,
    val levelsDb: FloatArray,
    val numOutputs: Int,
    val numReverbs: Int,
    val revision: Long,
    val hasDelays: Boolean,
    val hasLevels: Boolean,
    val receivedAtMs: Long
)

/**
 * Mirrored desktop visualisation state: channel counts, per-output array assignments,
 * the desktop's current selection, and the delay/level rows received so far.
 *
 * [primaryConfirmed] says [primaryChannel] is one the desktop itself named, which it
 * only ever does for a live channel. False for the default 1 and for a previous primary
 * kept when a /remote/vis/selection carried 0; only those may give way to another live
 * channel (displayedVisChannels), since this tablet's inventory can lag the desktop's.
 */
data class VisualisationState(
    val primaryChannel: Int = 1,
    val primaryConfirmed: Boolean = false,
    val clusterId: Int = 0,
    val selectionSet: List<Int> = emptyList(),
    val numOutputs: Int = 0,
    val numReverbs: Int = 0,
    val outputArrays: IntArray = IntArray(0),
    val rows: Map<Int, VisRow> = emptyMap()
)

class OscService : Service() {

    private val binder = OscBinder()
    private val job = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + job)

    // Remote connection state
    enum class RemoteConnectionState { DISCONNECTED, CONNECTED }

    private val _connectionState = MutableStateFlow(RemoteConnectionState.DISCONNECTED)
    val connectionState: StateFlow<RemoteConnectionState> = _connectionState.asStateFlow()

    // Protocol version the server reported in its v2 ping (0 = not yet known, 1 = legacy
    // server that sent a version-less ping). Used by the UI to flag a mismatch.
    private val _serverProtocolVersion = MutableStateFlow(0)
    val serverProtocolVersion: StateFlow<Int> = _serverProtocolVersion.asStateFlow()

    private var lastHeartbeatReceivedTime: Long = 0
    private var connectionTimeoutJob: kotlinx.coroutines.Job? = null

    // When our socket last (re)started (SystemClock.elapsedRealtime), and whether the
    // one follow-up /remote/disconnect for that start already went out (see the
    // heartbeat handler).
    @Volatile private var serverStartedAtMs = 0L
    @Volatile private var rehandshakeNudged = false

    // "The desktop cannot hear this tablet." The desktop pings only while its handshake
    // with us is still Connecting; once one of our pongs lands it sends a dump (opened by
    // /remote/dumpBegin) and heartbeats from then on. So pings that keep coming with
    // neither in between mean our pongs never reach it. The pings themselves prove its
    // Remote target IP and port right; the pongs go to this tablet's own IP Address and
    // Outgoing Port settings, so those come first, then the desktop's receive port, its
    // IP filter and any firewall. Meanwhile this side hears the pings, shows a green
    // dot, and the Visualisation tab would wait for data forever, since the desktop
    // sends a target nothing until the handshake completes.
    private val pingsWithoutHeartbeat = AtomicInteger(0)
    private val _desktopNotHearing = MutableStateFlow(false)
    val desktopNotHearing: StateFlow<Boolean> = _desktopNotHearing.asStateFlow()

    // --- Connection-time state-dump completeness tracking ---
    // The server sends ~2000 messages (~70 UDP bundles) right after connect. UDP can
    // drop some, leaving channels with missing positions/names. We track which channels
    // arrived complete (name + position) and re-request any that are missing.
    private val receivedNames = ConcurrentHashMap.newKeySet<Int>()
    private val receivedPositions = ConcurrentHashMap.newKeySet<Int>()
    @Volatile private var expectedChannelCount = 0
    @Volatile private var stateCompleteSeen = false
    // Dump-cycle sequence from /remote/dumpBegin (v2). -1 = no dump seen yet. If a
    // stateComplete arrives with an unknown seq, its dumpBegin was lost in transit and
    // we request a full re-dump (once per seq, guarded by lastFullResyncRequestedSeq).
    @Volatile private var currentDumpSeq = -1
    @Volatile private var lastFullResyncRequestedSeq = -1
    // Whether a /remote/channelList arrived in the current dump cycle. A v3 desktop
    // never sends one, so its absence at stateComplete is the trigger to infer the
    // inventory from the ids the dump did mention.
    @Volatile private var inventoryReceivedThisDump = false
    // Whether the inventory we hold was sent by the desktop we are talking to NOW.
    // Connection-scoped rather than dump-scoped: without it, a real inventory kept
    // from a previous session would veto the v3 inference forever after connecting
    // to an older desktop, which is exactly the case the inference exists for.
    @Volatile private var inventoryFromCurrentConnection = false
    private var resyncJob: kotlinx.coroutines.Job? = null
    private var resyncFallbackJob: kotlinx.coroutines.Job? = null
    private var inventoryRefreshJob: kotlinx.coroutines.Job? = null

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_CHANNEL_ID = "OscServiceChannel"
        private const val CONNECTION_TIMEOUT_MS = 6000L
        // Re-request tuning for a dropped state dump.
        private const val MAX_RESYNC_ATTEMPTS = 3
        private const val RESYNC_SETTLE_MS = 400L   // let trailing bundles land before first check
        private const val RESYNC_BACKOFF_MS = 1200L // wait for a resend to arrive before re-checking
        private const val RESYNC_FALLBACK_MS = 1500L // if stateComplete is itself lost, verify anyway
        // A count change walks one add/remove at a time and emits an /inputs per
        // step; collapse the storm into a single re-dump request.
        private const val INVENTORY_REFRESH_DEBOUNCE_MS = 700L
        // Pings with no heartbeat or dumpBegin between them before the desktop is
        // declared unable to hear us. It pings every 2 s, so this is 4-6 s; a working
        // handshake needs one ping, two if a pong is lost.
        private const val DESKTOP_NOT_HEARING_PINGS = 3
        // How long a map stereo gesture's final pair waits before going out: longer than
        // the 20 ms throttle replay, so any send still in flight lands first.
        private const val STEREO_FINAL_SEND_DELAY_MS = 60L
        // How long after our socket starts a heartbeat with no ping yet is taken as the
        // desktop having missed our /remote/disconnect. Its heartbeats are 2 s apart, and
        // one already in flight when that disconnect landed arrives within this.
        private const val REHANDSHAKE_GRACE_MS = 1000L
    }

    // Service state tracking
    private var isServerRunning = false
    private var serverJob: kotlinx.coroutines.Job? = null
    
    // Data classes for buffering OSC data
    data class OscMarkerUpdate(
        val id: Int,
        val name: String?,
        val position: Offset?,
        val isCluster: Boolean,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    data class OscNormalizedMarkerUpdate(
        val id: Int,
        val normalizedX: Float,
        val normalizedY: Float,
        val isCluster: Boolean,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    data class OscStageUpdate(
        val type: String, // "width", "depth", "height"
        val value: Float,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    data class OscInputsUpdate(
        val count: Int,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    data class OscInputParameterUpdate(
        val oscPath: String,
        val inputId: Int,
        val intValue: Int? = null,
        val floatValue: Float? = null,
        val stringValue: String? = null,
        val timestamp: Long = System.currentTimeMillis()
    )

    data class OscClusterConfigUpdate(
        val clusterId: Int,
        val referenceMode: Int? = null,
        val trackedInputId: Int? = null,
        val timestamp: Long = System.currentTimeMillis()
    )

    data class OscCompositePositionUpdate(
        val inputId: Int,
        val compositeX: Float,  // in stage meters
        val compositeY: Float,  // in stage meters
        val timestamp: Long = System.currentTimeMillis()
    )

    // Buffers for incoming OSC data
    private val markerUpdates = ConcurrentLinkedQueue<OscMarkerUpdate>()
    private val normalizedMarkerUpdates = ConcurrentLinkedQueue<OscNormalizedMarkerUpdate>()
    private val stageUpdates = ConcurrentLinkedQueue<OscStageUpdate>()
    private val inputsUpdates = ConcurrentLinkedQueue<OscInputsUpdate>()
    private val inputParameterUpdates = ConcurrentLinkedQueue<OscInputParameterUpdate>()
    private val clusterConfigUpdates = ConcurrentLinkedQueue<OscClusterConfigUpdate>()
    private val compositePositionUpdates = ConcurrentLinkedQueue<OscCompositePositionUpdate>()

    // Per-cluster suppression of inbound /remoteInput/positionXY updates while a
    // local cluster gesture is active on this tablet. Prevents echoes from JUCE
    // (or any other source) from clobbering the locally-extrapolated positions
    // the InputMapTab gesture handler is writing at touch rate.
    // Each entry maps clusterId -> last-set timestamp (ms) for the watchdog.
    private val suppressedClusters = java.util.concurrent.ConcurrentHashMap<Int, Long>()
    private var suppressionWatchdogJob: kotlinx.coroutines.Job? = null
    private val suppressionWatchdogTimeoutMs = 2_000L
    
    // StateFlows for real-time data (when MainActivity is active)
    private val _markers = MutableStateFlow<List<Marker>>(emptyList())
    val markers: StateFlow<List<Marker>> = _markers.asStateFlow()
    
    private val _stageWidth = MutableStateFlow(16.0f)
    val stageWidth: StateFlow<Float> = _stageWidth.asStateFlow()
    
    private val _stageDepth = MutableStateFlow(10.0f)
    val stageDepth: StateFlow<Float> = _stageDepth.asStateFlow()
    
    private val _stageHeight = MutableStateFlow(7.0f)
    val stageHeight: StateFlow<Float> = _stageHeight.asStateFlow()
    
    private val _stageOriginX = MutableStateFlow(0.0f) // Center-referenced (0 = center)
    val stageOriginX: StateFlow<Float> = _stageOriginX.asStateFlow()

    private val _stageOriginY = MutableStateFlow(-5.0f) // Downstage center (-stageDepth/2)
    val stageOriginY: StateFlow<Float> = _stageOriginY.asStateFlow()
    
    private val _stageOriginZ = MutableStateFlow(0.0f)
    val stageOriginZ: StateFlow<Float> = _stageOriginZ.asStateFlow()

    // Stage shape parameters: 0=box, 1=cylinder, 2=dome
    private val _stageShape = MutableStateFlow(0)
    val stageShape: StateFlow<Int> = _stageShape.asStateFlow()

    private val _stageDiameter = MutableStateFlow(20.0f)
    val stageDiameter: StateFlow<Float> = _stageDiameter.asStateFlow()

    private val _domeElevation = MutableStateFlow(180.0f)
    val domeElevation: StateFlow<Float> = _domeElevation.asStateFlow()

    private val _numberOfInputs = MutableStateFlow(64)
    val numberOfInputs: StateFlow<Int> = _numberOfInputs.asStateFlow()

    // Which channels exist, in display order, and which are stereo (protocol v4).
    // The source of truth for enumeration — numberOfInputs above is only a
    // "did I receive everything" signal, never a range to iterate. Empty until the
    // first dump completes; UI must treat empty as "not known yet", not "none".
    private val _channelInventory = MutableStateFlow(ChannelInventory())
    val channelInventory: StateFlow<ChannelInventory> = _channelInventory.asStateFlow()
    
    private val _inputParametersState = MutableStateFlow(InputParametersState())
    val inputParametersState: StateFlow<InputParametersState> = _inputParametersState.asStateFlow()

    private val _clusterConfigs = MutableStateFlow(List(10) { index -> ClusterConfig(id = index + 1) })
    val clusterConfigs: StateFlow<List<ClusterConfig>> = _clusterConfigs.asStateFlow()

    private fun saveClusterConfigs() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        for (c in _clusterConfigs.value) {
            prefs.putInt("cluster_${c.id}_refMode", c.referenceMode)
            prefs.putInt("cluster_${c.id}_tracked", c.trackedInputId)
        }
        prefs.apply()
    }
    private fun restoreClusterConfigs() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _clusterConfigs.value = List(10) { i ->
            val id = i + 1
            ClusterConfig(id, prefs.getInt("cluster_${id}_refMode", 0), prefs.getInt("cluster_${id}_tracked", 0))
        }
    }

    // Composite deltas: inputId -> (deltaX in meters, deltaY in meters)
    // Delta is the difference between composite position (after transformations) and target position
    // Used to show grey dot offset on Android map. JUCE sends (0,0) to clear when no offset.
    private val _compositePositions = MutableStateFlow<Map<Int, Pair<Float, Float>>>(emptyMap())
    val compositePositions: StateFlow<Map<Int, Pair<Float, Float>>> = _compositePositions.asStateFlow()

    // Sampler playing: inputId -> true when a sampler cell is playing on that input
    // JUCE sends transitions only (on start and on stop).
    private val _samplerPlaying = MutableStateFlow<Map<Int, Boolean>>(emptyMap())
    val samplerPlaying: StateFlow<Map<Int, Boolean>> = _samplerPlaying.asStateFlow()

    // XY Pad (virtual Lightpad) state — received from JUCE
    private val _padEnabled = MutableStateFlow(true)  // visible by default; JUCE hides if needed
    val padEnabled: StateFlow<Boolean> = _padEnabled.asStateFlow()

    private val _padZones = MutableStateFlow<List<PadZoneConfig>>(emptyList())
    val padZones: StateFlow<List<PadZoneConfig>> = _padZones.asStateFlow()

    private val _padSensitivity = MutableStateFlow(0.05f)
    val padSensitivity: StateFlow<Float> = _padSensitivity.asStateFlow()

    private val _padGridLayout = MutableStateFlow(PadGridLayout.GRID_3x2)
    val padGridLayout: StateFlow<PadGridLayout> = _padGridLayout.asStateFlow()

    // Cluster LFO state
    private val _clusterLFOActive = MutableStateFlow(IntArray(10) { 0 })
    val clusterLFOActive: StateFlow<IntArray> = _clusterLFOActive.asStateFlow()
    private val _clusterPresetNames = MutableStateFlow(Array(16) { "" })
    val clusterPresetNames: StateFlow<Array<String>> = _clusterPresetNames.asStateFlow()
    private val _clusterPresetPopulated = MutableStateFlow(BooleanArray(16) { false })
    val clusterPresetPopulated: StateFlow<BooleanArray> = _clusterPresetPopulated.asStateFlow()

    // Per-preset axis automation bitmask: X=1, Y=2, Z=4, R=8, S=16
    private val _clusterPresetAxes = MutableStateFlow(IntArray(16) { 0 })
    val clusterPresetAxes: StateFlow<IntArray> = _clusterPresetAxes.asStateFlow()

    // Visualisation mirroring (protocol v3). Rows arrive throttled to <=10 Hz
    // server-side, so direct StateFlow copy-replace is fine — no queue buffering
    // needed (unlike the high-rate position streams above).
    private val _visState = MutableStateFlow(VisualisationState())
    val visState: StateFlow<VisualisationState> = _visState.asStateFlow()

    // Channel pinned on the visualisation tab (0 = follow the desktop selection).
    // Service-scoped so the pin survives tab switches; re-sent to the server on
    // reconnect, at every dumpBegin and in every /remote/vis/request (the server
    // clears per-target pins on connect).
    private val _visPinnedChannel = MutableStateFlow(0)
    val visPinnedChannel: StateFlow<Int> = _visPinnedChannel.asStateFlow()

    private var visRowRevision = 0L

    // Whether the Visualisation tab's last-resort full re-dump already went out on this
    // connection (requestVisFallbackResync). Kept here rather than in the tab, which is
    // disposed on every tab switch: re-entering the tab must not re-dump again. Cleared
    // wherever a connection ends (endConnectionScopedVisState), so the next one starts
    // with its own.
    @Volatile private var visFallbackResyncSent = false

    // Store screen dimensions once at startup
    private var screenWidth: Float = 0f
    private var screenHeight: Float = 0f

    inner class OscBinder : Binder() {
        fun getService(): OscService = this@OscService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        initializeScreenDimensions()
        restoreClusterConfigs()
        createNotificationChannel()
    }
    
    private fun initializeScreenDimensions() {
        val displayMetrics = resources.displayMetrics
        screenWidth = displayMetrics.widthPixels.toFloat()
        screenHeight = displayMetrics.heightPixels.toFloat()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val notification = createNotification()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            startServer()

            return START_STICKY
        } catch (e: Exception) {
            return START_STICKY
        }
    }

    private fun startServer() {
        if (isServerRunning) {
            return
        }

        serverJob = serviceScope.launch {
            try {
                isServerRunning = true
                serverStartedAtMs = SystemClock.elapsedRealtime()
                rehandshakeNudged = false
                // Notify JUCE we're (re)starting so it resets connection state
                // and sends full state dump on next ping/pong handshake
                sendOscDisconnect(this@OscService)
                startOscServer(
                    context = this@OscService,
                    onOscDataReceived = { id, name, position, isCluster ->
                        // Buffer the data instead of immediately updating UI
                        markerUpdates.offer(OscMarkerUpdate(id, name, position, isCluster))
                    },
                    onStageWidthChanged = { newWidth ->
                        stageUpdates.offer(OscStageUpdate("width", newWidth))
                        _stageWidth.value = newWidth
                    },
                    onStageDepthChanged = { newDepth ->
                        stageUpdates.offer(OscStageUpdate("depth", newDepth))
                        _stageDepth.value = newDepth
                    },
                    onStageHeightChanged = { newHeight ->
                        stageUpdates.offer(OscStageUpdate("height", newHeight))
                        _stageHeight.value = newHeight
                    },
                    onStageOriginXChanged = { newOriginX ->
                        stageUpdates.offer(OscStageUpdate("originX", newOriginX))
                        _stageOriginX.value = newOriginX
                    },
                    onStageOriginYChanged = { newOriginY ->
                        stageUpdates.offer(OscStageUpdate("originY", newOriginY))
                        _stageOriginY.value = newOriginY
                    },
                    onStageOriginZChanged = { newOriginZ ->
                        stageUpdates.offer(OscStageUpdate("originZ", newOriginZ))
                        _stageOriginZ.value = newOriginZ
                    },
                    onStageShapeChanged = { newShape ->
                        stageUpdates.offer(OscStageUpdate("shape", newShape.toFloat()))
                        _stageShape.value = newShape
                    },
                    onStageDiameterChanged = { newDiameter ->
                        stageUpdates.offer(OscStageUpdate("diameter", newDiameter))
                        _stageDiameter.value = newDiameter
                    },
                    onDomeElevationChanged = { newElevation ->
                        stageUpdates.offer(OscStageUpdate("domeElevation", newElevation))
                        _domeElevation.value = newElevation
                    },
                    onNumberOfInputsChanged = { newCount ->
                        inputsUpdates.offer(OscInputsUpdate(newCount))
                        _numberOfInputs.value = newCount
                        // The /inputs message is part of the dump; remember the count so we
                        // can verify completeness even if the stateComplete marker is lost.
                        // It is a completeness signal only — the count says nothing about
                        // WHICH numbers exist, so the vis-pin check moved to
                        // applyChannelInventory().
                        if (newCount > 0) expectedChannelCount = newCount
                        scheduleInferredInventoryRefresh()
                    },
                    onInputParameterIntReceived = { oscPath, inputId, value ->
                        inputParameterUpdates.offer(OscInputParameterUpdate(oscPath, inputId, intValue = value))
                        updateInputParameterFromOsc(oscPath, inputId, intValue = value)
                    },
                    onInputParameterFloatReceived = { oscPath, inputId, value ->
                        inputParameterUpdates.offer(OscInputParameterUpdate(oscPath, inputId, floatValue = value))
                        updateInputParameterFromOsc(oscPath, inputId, floatValue = value)
                        if (oscPath == "/remoteInput/positionX" || oscPath == "/remoteInput/positionY")
                            receivedPositions.add(inputId)
                    },
                    onInputParameterStringReceived = { oscPath, inputId, value ->
                        inputParameterUpdates.offer(OscInputParameterUpdate(oscPath, inputId, stringValue = value))
                        updateInputParameterFromOsc(oscPath, inputId, stringValue = value)
                        if (oscPath == "/remoteInput/inputName")
                            receivedNames.add(inputId)
                    },
                    onClusterReferenceModeChanged = { clusterId, mode ->
                        clusterConfigUpdates.offer(OscClusterConfigUpdate(clusterId, referenceMode = mode))
                        val index = clusterId - 1
                        if (index >= 0 && index < _clusterConfigs.value.size) {
                            val updatedConfigs = _clusterConfigs.value.toMutableList()
                            updatedConfigs[index] = updatedConfigs[index].copy(referenceMode = mode)
                            _clusterConfigs.value = updatedConfigs
                            saveClusterConfigs()
                        }
                    },
                    onClusterTrackedInputChanged = { clusterId, inputId ->
                        clusterConfigUpdates.offer(OscClusterConfigUpdate(clusterId, trackedInputId = inputId))
                        val index = clusterId - 1
                        if (index >= 0 && index < _clusterConfigs.value.size) {
                            val updatedConfigs = _clusterConfigs.value.toMutableList()
                            updatedConfigs[index] = updatedConfigs[index].copy(trackedInputId = inputId)
                            _clusterConfigs.value = updatedConfigs
                            saveClusterConfigs()
                        }
                    },
                    onRemotePingReceived = { sequenceNumber, serverVersion ->
                        // Respond with pong and update connection state
                        sendOscPong(this@OscService, sequenceNumber)
                        _serverProtocolVersion.value = serverVersion
                        lastHeartbeatReceivedTime = System.currentTimeMillis()
                        // The desktop pings only until one of our pongs lands, so a run
                        // of them means it is not hearing us (desktopNotHearing above).
                        if (pingsWithoutHeartbeat.incrementAndGet() >= DESKTOP_NOT_HEARING_PINGS) {
                            _desktopNotHearing.value = true
                        }
                        val wasConnected = _connectionState.value == RemoteConnectionState.CONNECTED
                        _connectionState.value = RemoteConnectionState.CONNECTED
                        startConnectionTimeoutMonitor()
                        // A fresh connect triggers the server to (re)send the full dump, so
                        // reset completeness tracking and arm a fallback verifier in case the
                        // stateComplete marker itself is dropped. (Belt-and-braces: a v2
                        // server also announces every dump with /remote/dumpBegin.)
                        if (!wasConnected) {
                            resetSyncTracking()
                            // The peer may be a different (or downgraded) desktop, so
                            // whatever inventory we still hold is no longer proof that
                            // this one can send us a fresh one.
                            inventoryFromCurrentConnection = false
                            scheduleResyncFallback()
                            // The server clears per-target vis pins on connect; restore ours
                            if (_visPinnedChannel.value > 0) {
                                sendOscVisPin(this@OscService, _visPinnedChannel.value)
                            }
                        }
                    },
                    onRemoteHeartbeatReceived = { sequenceNumber ->
                        // Respond with heartbeat ack and update timestamp. Also treat the
                        // heartbeat as proof of connection: if the tablet timed out but the
                        // server still believes we're connected, it keeps sending heartbeats
                        // and never re-pings — without this, the tablet would stay
                        // DISCONNECTED forever (asymmetric-loss deadlock).
                        sendOscHeartbeatAck(this@OscService, sequenceNumber)
                        lastHeartbeatReceivedTime = System.currentTimeMillis()
                        // Heartbeats only go to a target whose pong landed.
                        clearDesktopNotHearing()
                        // No ping since our socket started, yet heartbeats: the desktop
                        // missed the /remote/disconnect startServer sent and still counts
                        // us as connected, so it will neither ping nor dump. The protocol
                        // version would stay unknown for the whole connection, hiding the
                        // mismatch banner and the Visualisation tab's update hint. Ask
                        // once more per start, after the grace period; the handshake that
                        // follows brings the version, a dump and, at its dumpBegin, our pin.
                        if (_serverProtocolVersion.value == 0 && !rehandshakeNudged &&
                            SystemClock.elapsedRealtime() - serverStartedAtMs >= REHANDSHAKE_GRACE_MS) {
                            rehandshakeNudged = true
                            android.util.Log.w("OscService",
                                "Heartbeat but no ping since the socket started - re-sending /remote/disconnect")
                            serviceScope.launch { sendOscDisconnect(this@OscService) }
                        }
                        if (_connectionState.value != RemoteConnectionState.CONNECTED) {
                            _connectionState.value = RemoteConnectionState.CONNECTED
                            startConnectionTimeoutMonitor()
                        }
                    },
                    onRemoteDisconnectReceived = {
                        // Server requested disconnect
                        _connectionState.value = RemoteConnectionState.DISCONNECTED
                        connectionTimeoutJob?.cancel()
                        resyncJob?.cancel()
                        endConnectionScopedVisState()
                    },
                    onRemoteDumpBeginReceived = { dumpSeq, expectedCount ->
                        // A fresh full dump is starting (connect, project load, or full
                        // resync). Reset completeness tracking so channels received in a
                        // PREVIOUS dump don't mask losses in this one, and arm the
                        // fallback verifier in case both end markers get lost.
                        android.util.Log.d("OscService", "dumpBegin seq=$dumpSeq channels=$expectedCount")
                        // The desktop dumps only to a target it hears.
                        clearDesktopNotHearing()
                        resetSyncTracking()
                        if (expectedCount > 0) expectedChannelCount = expectedCount
                        currentDumpSeq = dumpSeq
                        scheduleResyncFallback()
                        // Every handshake clears the desktop's per-target pins, including
                        // one this tablet never saw as a disconnect (a restart of our
                        // socket, a lost /remote/disconnect), and a dump always follows
                        // it. Restating here also works with desktops that predate
                        // /remote/vis/request, since the pin itself is v3.
                        if (_visPinnedChannel.value > 0) {
                            sendOscVisPin(this@OscService, _visPinnedChannel.value)
                        }
                    },
                    onRemoteStateCompleteReceived = { expectedCount, dumpSeq ->
                        // Server finished the full dump. Verify we got every channel and
                        // re-request any lost in transit.
                        if (dumpSeq >= 0 && dumpSeq != currentDumpSeq) {
                            // The dumpBegin for this cycle was lost: our tracking sets
                            // still reflect the previous dump, so completeness can't be
                            // judged. Request a full re-dump (it will carry a fresh
                            // dumpBegin), at most once per seq to avoid a loop.
                            android.util.Log.w("OscService",
                                "stateComplete seq=$dumpSeq without dumpBegin (have $currentDumpSeq)")
                            receivedNames.clear()
                            receivedPositions.clear()
                            currentDumpSeq = dumpSeq
                            if (lastFullResyncRequestedSeq != dumpSeq) {
                                lastFullResyncRequestedSeq = dumpSeq
                                sendOscRequestResync(this@OscService, emptyList())
                            }
                        } else {
                            if (expectedCount > 0) expectedChannelCount = expectedCount
                            stateCompleteSeen = true
                            // Before the verifier, so it can already work off the
                            // inventory rather than off 1..count.
                            inferInventoryIfMissing()
                            launchResyncVerifier()
                        }
                    },
                    onCompositePositionReceived = { inputId, deltaX, deltaY ->
                        // Delta values: JUCE sends (0,0) when there's no offset to display
                        val deltaThreshold = 0.01f  // 1cm threshold for considering delta significant
                        val deltaIsZero = kotlin.math.abs(deltaX) < deltaThreshold && kotlin.math.abs(deltaY) < deltaThreshold

                        val updated = _compositePositions.value.toMutableMap()
                        if (deltaIsZero) {
                            // Remove entry when delta is effectively zero (no offset to show)
                            updated.remove(inputId)
                        } else {
                            // Store non-zero delta
                            updated[inputId] = Pair(deltaX, deltaY)
                            compositePositionUpdates.offer(OscCompositePositionUpdate(inputId, deltaX, deltaY))
                        }
                        _compositePositions.value = updated
                    },
                    onSamplerPlayingReceived = { inputId, playing ->
                        val updated = _samplerPlaying.value.toMutableMap()
                        if (playing != 0) {
                            updated[inputId] = true
                        } else {
                            updated.remove(inputId)
                        }
                        _samplerPlaying.value = updated
                    },
                    onPadEnabledReceived = { enabled ->
                        _padEnabled.value = enabled != 0
                    },
                    onPadZoneConfigReceived = { zoneId, inputChannel, r, g, b ->
                        val color = Color(r / 255f, g / 255f, b / 255f)
                        val newConfig = PadZoneConfig(zoneId, inputChannel, color)
                        val current = _padZones.value.toMutableList()
                        val existingIndex = current.indexOfFirst { it.zoneId == zoneId }
                        if (existingIndex >= 0) {
                            current[existingIndex] = newConfig
                        } else {
                            current.add(newConfig)
                        }
                        // Keep sorted by zoneId
                        current.sortBy { it.zoneId }
                        _padZones.value = current
                    },
                    onPadZoneCountReceived = { count ->
                        // Trim zone list to requested count
                        val current = _padZones.value
                        if (current.size > count) {
                            _padZones.value = current.take(count)
                        }
                    },
                    onPadSensitivityReceived = { sensitivity ->
                        _padSensitivity.value = sensitivity
                    },
                    onPadGridLayoutReceived = { columns, rows ->
                        _padGridLayout.value = PadGridLayout(columns, rows)
                    },
                    onClusterLFOActiveReceived = { clusterId, active ->
                        val i = clusterId - 1
                        if (i in 0..9) { val u = _clusterLFOActive.value.copyOf(); u[i] = active; _clusterLFOActive.value = u }
                    },
                    onClusterPresetNameReceived = { presetNumber, name ->
                        val i = presetNumber - 1
                        if (i in 0..15) { val u = _clusterPresetNames.value.copyOf(); u[i] = name; _clusterPresetNames.value = u }
                    },
                    onClusterPresetPopulatedReceived = { presetNumber, populated ->
                        val i = presetNumber - 1
                        if (i in 0..15) { val u = _clusterPresetPopulated.value.copyOf(); u[i] = populated != 0; _clusterPresetPopulated.value = u }
                    },
                    onClusterPresetCountReceived = { _ -> },
                    onClusterPresetAxesReceived = { presetNumber, axesBitmask ->
                        val i = presetNumber - 1
                        if (i in 0..15) {
                            val u = _clusterPresetAxes.value.copyOf()
                            u[i] = axesBitmask
                            _clusterPresetAxes.value = u
                        }
                    },
                    onVisConfigReceived = { numOutputs, numReverbs ->
                        val current = _visState.value
                        // Channel-count change invalidates all cached rows
                        val rows = if (numOutputs != current.numOutputs || numReverbs != current.numReverbs)
                            emptyMap() else current.rows
                        _visState.value = current.copy(
                            numOutputs = numOutputs, numReverbs = numReverbs, rows = rows)
                    },
                    onVisOutputArraysReceived = { arrays ->
                        _visState.value = _visState.value.copy(outputArrays = arrays)
                    },
                    onVisSelectionReceived = { primary, clusterId, selection ->
                        val current = _visState.value
                        // Primary 0 means the desktop has no live selected channel (it
                        // was deleted, or the loaded session lacks it). Keep the one we
                        // show rather than pointing the bars at nothing; the cluster and
                        // the set still apply. A kept one is no longer vouched for, so it
                        // may give way to a live channel.
                        val effectivePrimary = if (primary >= 1) primary else current.primaryChannel
                        // Evict rows no longer displayed (selection ∪ primary ∪ pin)
                        val keep = selection.toMutableSet()
                        keep.add(effectivePrimary)
                        if (_visPinnedChannel.value > 0) keep.add(_visPinnedChannel.value)
                        _visState.value = current.copy(
                            primaryChannel = effectivePrimary,
                            primaryConfirmed = primary >= 1,
                            clusterId = clusterId,
                            selectionSet = selection,
                            rows = current.rows.filterKeys { it in keep })
                    },
                    onVisDelaysReceived = { channel, numOutputs, numReverbs, values ->
                        updateVisRow(channel, numOutputs, numReverbs, delays = values)
                    },
                    onVisLevelsReceived = { channel, numOutputs, numReverbs, values ->
                        updateVisRow(channel, numOutputs, numReverbs, levels = values)
                    },
                    onChannelListReceived = { channels ->
                        // Already validated in the parser (arity, ranges, uniqueness):
                        // reaching here means the snapshot is whole, so replacing
                        // outright is correct — including a snapshot with no channels.
                        inventoryReceivedThisDump = true
                        inventoryFromCurrentConnection = true
                        applyChannelInventory(ChannelInventory(channels, inferred = false))
                    }
                )
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isServerRunning = false
            }
        }

        // No staleness cleanup needed - JUCE explicitly sends (0,0) delta when transformations are disabled
    }

    /**
     * Merge one /remote/vis/delays or /remote/vis/levels message into the row map.
     * Rows whose counts mismatch the current config are dropped (they raced a
     * config change and a fresh pair follows).
     */
    private fun updateVisRow(channel: Int, numOutputs: Int, numReverbs: Int,
                             delays: FloatArray? = null, levels: FloatArray? = null) {
        val current = _visState.value
        if (current.numOutputs != 0 &&
            (numOutputs != current.numOutputs || numReverbs != current.numReverbs)) {
            return
        }
        val existing = current.rows[channel]
        val compatible = existing != null &&
                existing.numOutputs == numOutputs && existing.numReverbs == numReverbs
        val row = VisRow(
            delaysMs = delays ?: (if (compatible) existing.delaysMs else FloatArray(numOutputs + numReverbs)),
            levelsDb = levels ?: (if (compatible) existing.levelsDb else FloatArray(numOutputs + numReverbs) { -60f }),
            numOutputs = numOutputs,
            numReverbs = numReverbs,
            revision = ++visRowRevision,
            // The other half only counts if it was kept, i.e. it has the same shape
            // as this one; otherwise it is the placeholder filled in just above.
            hasDelays = delays != null || (compatible && existing.hasDelays),
            hasLevels = levels != null || (compatible && existing.hasLevels),
            receivedAtMs = SystemClock.elapsedRealtime()
        )
        _visState.value = current.copy(rows = current.rows + (channel to row))
    }

    /**
     * Pin (channel >= 1) or unpin (0) the visualisation channel. Updates local state
     * and notifies the server, which replies with that channel's rows. View-only:
     * never changes the desktop's selected channel.
     */
    fun setVisPin(channel: Int) {
        _visPinnedChannel.value = channel
        serviceScope.launch {
            sendOscVisPin(this@OscService, channel)
        }
    }

    /**
     * Ask the desktop for its whole visualisation state (/remote/vis/request), restating
     * our pin. Paced by the Visualisation tab; a desktop that predates the address
     * drops it.
     */
    fun requestVisRefresh() {
        val pinned = _visPinnedChannel.value
        serviceScope.launch {
            sendOscVisRequest(this@OscService, pinned)
        }
    }

    /**
     * The Visualisation tab's last resort when no config came back from any of its
     * requests: the desktop predates /remote/vis/request, or every reply was lost.
     * Every v3+ desktop's full dump carries the vis config and output arrays. At most
     * once per connection, so a tab left open, or shown again, never keeps re-dumping.
     */
    fun requestVisFallbackResync() {
        if (visFallbackResyncSent) return
        visFallbackResyncSent = true
        android.util.Log.w("OscService", "No vis config after every /remote/vis/request - requesting a full re-dump")
        requestFullResync()
    }

    fun sendMarkerPosition(markerId: Int, x: Float, y: Float, isCluster: Boolean) {
        serviceScope.launch {
            sendOscPosition(this@OscService, markerId, x, y, isCluster)
        }
    }
    
    fun sendInputParameterInt(oscPath: String, inputId: Int, value: Int) {
        // Update local state immediately to keep it in sync with what we're sending
        updateInputParameterFromOsc(oscPath, inputId, intValue = value)

        serviceScope.launch {
            sendOscInputParameterInt(this@OscService, oscPath, inputId, value)
        }
    }
    
    fun sendInputParameterFloat(oscPath: String, inputId: Int, value: Float) {
        // Update local state immediately to keep it in sync with what we're sending
        // This prevents the "jump back" issue when other parameters arrive from server
        updateInputParameterFromOsc(oscPath, inputId, floatValue = value)

        serviceScope.launch {
            sendOscInputParameterFloat(this@OscService, oscPath, inputId, value)
        }
    }

    /**
     * The final width and axis offset of a map stereo gesture, sent when it ends.
     *
     * The gesture's own sends go through the per-key throttle, which can lose the last
     * one: a value arriving within 20 ms of the previous send is parked as a pending that
     * only a LATER send on the same key flushes, so it strands when the finger lifts, and
     * a pending already picked up is replayed 20 ms after a send, possibly behind a newer
     * value. The desktop never echoes tablet edits, so nothing would put either right.
     * Runs in the service scope, so switching tabs cannot cancel it: wait until any send
     * still in flight has landed, drop what is pending, then send both unthrottled.
     */
    fun sendStereoImageFinal(inputId: Int, width: Float, axisOffset: Int) {
        serviceScope.launch {
            delay(STEREO_FINAL_SEND_DELAY_MS)
            val widthPath = "/remoteInput/stereoWidth"
            val axisPath = "/remoteInput/stereoAxisOffset"
            OscThrottleManager.clearPending(OscThrottleManager.inputParameterKey(widthPath, inputId))
            OscThrottleManager.clearPending(OscThrottleManager.inputParameterKey(axisPath, inputId))
            sendInputParameterFloatNow(widthPath, inputId, width)
            sendInputParameterIntNow(axisPath, inputId, axisOffset)
        }
    }

    // Unthrottled twins of sendInputParameterInt/Float, for a gesture's final value: the
    // same local update first, then a send that neither waits behind the throttle nor is
    // parked as a pending.
    private fun sendInputParameterIntNow(oscPath: String, inputId: Int, value: Int) {
        updateInputParameterFromOsc(oscPath, inputId, intValue = value)
        sendOscInputParameterInt(this, oscPath, inputId, value, throttled = false)
    }

    private fun sendInputParameterFloatNow(oscPath: String, inputId: Int, value: Float) {
        updateInputParameterFromOsc(oscPath, inputId, floatValue = value)
        sendOscInputParameterFloat(this, oscPath, inputId, value, throttled = false)
    }

    fun sendInputParameterString(oscPath: String, inputId: Int, value: String) {
        // Update local state immediately to keep it in sync with what we're sending
        updateInputParameterFromOsc(oscPath, inputId, stringValue = value)

        serviceScope.launch {
            sendOscInputParameterString(this@OscService, oscPath, inputId, value)
        }
    }

    fun sendInputParameterIncDec(oscPath: String, inputId: Int, direction: String, value: Float) {
        // Compute new value locally and update state immediately (like sendInputParameterFloat does)
        // This prevents stale number boxes since JUCE won't echo values back to the remote sender
        val paramName = oscPath.removePrefix("/remoteInput/")
        val definition = InputParameterDefinitions.parametersByVariableName[paramName]
        if (definition != null) {
            val currentState = _inputParametersState.value
            val channel = currentState.getChannel(inputId)
            val currentParam = channel.getParameter(paramName)
            val currentActual = InputParameterDefinitions.applyFormula(definition, currentParam.normalizedValue)
            val increment = if (direction == "inc") value else -value
            val newActual = (currentActual + increment).coerceIn(definition.minValue, definition.maxValue)
            updateInputParameterFromOsc(oscPath, inputId, floatValue = newActual)
        }

        serviceScope.launch {
            sendOscInputParameterIncDec(this@OscService, oscPath, inputId, direction, value)
        }
    }

    fun requestInputParameters(inputId: Int) {
        serviceScope.launch {
            sendOscRequestInputParameters(this@OscService, inputId)
        }
    }

    fun sendClusterMove(clusterId: Int, deltaX: Float, deltaY: Float) {
        serviceScope.launch {
            sendOscClusterMove(this@OscService, clusterId, deltaX, deltaY)
        }
    }

    fun sendBarycenterMove(clusterId: Int, deltaX: Float, deltaY: Float) {
        serviceScope.launch {
            sendOscBarycenterMove(this@OscService, clusterId, deltaX, deltaY)
        }
    }

    fun sendClusterPositionXY(clusterId: Int, stageX: Float, stageY: Float) {
        serviceScope.launch {
            sendOscClusterPositionXY(this@OscService, clusterId, stageX, stageY)
        }
    }

    fun sendClusterScale(clusterId: Int, scaleFactor: Float) {
        serviceScope.launch {
            sendOscClusterScale(this@OscService, clusterId, scaleFactor)
        }
    }

    fun sendClusterRotation(clusterId: Int, angleDegrees: Float) {
        serviceScope.launch {
            sendOscClusterRotation(this@OscService, clusterId, angleDegrees)
        }
    }

    fun sendClusterScaleRotation(clusterId: Int, cumulativeScale: Float, cumulativeRotation: Float) {
        serviceScope.launch {
            sendOscClusterScaleRotation(this@OscService, clusterId, cumulativeScale, cumulativeRotation)
        }
    }

    /**
     * Send combined XY position for atomic position updates.
     * Updates local state for both axes before sending to keep state in sync.
     */
    fun sendPadTouch(zoneId: Int, touchState: Int, dx: Float, dy: Float, pressure: Float) {
        serviceScope.launch {
            sendOscPadTouch(this@OscService, zoneId, touchState, dx, dy, pressure)
        }
    }

    /**
     * Set or clear per-cluster suppression of inbound /remoteInput/positionXY
     * updates. While a clusterId is suppressed, position updates for any input
     * whose current cluster equals that id are dropped before they reach
     * inputParametersState. Called by InputMapTab on local cluster gesture
     * start/end. A 2-second watchdog auto-clears stuck flags after a network
     * drop.
     */
    fun setClusterSuppression(clusterId: Int, suppressed: Boolean) {
        if (clusterId !in 1..10) return
        if (suppressed) {
            suppressedClusters[clusterId] = System.currentTimeMillis()
            startSuppressionWatchdog()
        } else {
            suppressedClusters.remove(clusterId)
        }
    }

    fun isClusterSuppressed(clusterId: Int): Boolean =
        clusterId in 1..10 && suppressedClusters.containsKey(clusterId)

    private fun startSuppressionWatchdog() {
        if (suppressionWatchdogJob?.isActive == true) return
        suppressionWatchdogJob = serviceScope.launch {
            while (isActive && suppressedClusters.isNotEmpty()) {
                delay(500L)
                val now = System.currentTimeMillis()
                val expired = suppressedClusters.entries
                    .filter { now - it.value > suppressionWatchdogTimeoutMs }
                    .map { it.key }
                if (expired.isNotEmpty()) {
                    expired.forEach { suppressedClusters.remove(it) }
                    android.util.Log.w("OscService",
                        "Cluster suppression watchdog cleared stuck clusters: $expired")
                }
            }
        }
    }

    fun sendInputPositionXY(inputId: Int, posX: Float, posY: Float) {
        // Update local state for BOTH axes atomically to prevent jump-back issues
        commitLocalInputPositionXY(inputId, posX, posY)

        serviceScope.launch {
            sendOscInputPositionXY(this@OscService, inputId, posX, posY)
        }
    }

    /**
     * Record a position in local state WITHOUT sending it. Used at cluster
     * gesture end for every member: the post-release sync copies
     * inputParametersState into the map markers, so pre-gesture values must not
     * survive there even if JUCE's authoritative echo is lost in transit.
     */
    fun commitLocalInputPositionXY(inputId: Int, posX: Float, posY: Float) {
        updateInputParameterFromOsc("/remoteInput/positionX", inputId, floatValue = posX)
        updateInputParameterFromOsc("/remoteInput/positionY", inputId, floatValue = posY)
    }

    fun getBufferedClusterConfigUpdates(): List<OscClusterConfigUpdate> {
        val updates = mutableListOf<OscClusterConfigUpdate>()
        while (clusterConfigUpdates.isNotEmpty()) {
            clusterConfigUpdates.poll()?.let { updates.add(it) }
        }
        return updates
    }

    private fun updateInputParameterFromOsc(oscPath: String, inputId: Int, intValue: Int? = null, floatValue: Float? = null, stringValue: String? = null) {
        // Find parameter definition by OSC path
        val definition = InputParameterDefinitions.allParameters.find { it.oscPath == oscPath } ?: return

        // Note: cluster gesture suppression is intentionally NOT applied here.
        // Letting inputParametersState reflect JUCE's most-recent state during a
        // local gesture means the post-release sync has fresh authoritative values
        // to write, instead of pre-gesture stale values. The visual override during
        // the gesture is handled by the activeClusterTranslations skip in
        // InputMapTab's LaunchedEffect, not by dropping updates here.

        val paramValue = when {
            stringValue != null -> {
                InputParameterValue(
                    normalizedValue = 0f,
                    stringValue = stringValue,
                    displayValue = stringValue
                )
            }
            intValue != null -> {
                // For dropdowns and text buttons, don't normalize - store the integer directly
                // ON/OFF switches use 0=OFF, 1=ON matching JUCE convention (no inversion needed)
                // For direction dials, we need special handling to normalize with proper range coercion
                // NONE covers values that are data rather than a control -- inputColour, a
                // 24-bit RGB -- where normalising to 0..1 and back would be lossy and
                // meaningless. A Float represents every integer up to 2^24 exactly and the
                // colour maxes at 2^24 - 1, so storing it raw here round-trips.
                val shouldNotNormalize = definition.uiType == UIComponentType.DROPDOWN ||
                                        definition.uiType == UIComponentType.TEXT_BUTTON ||
                                        definition.uiType == UIComponentType.NONE

                val normalized = if (shouldNotNormalize) {
                    intValue.toFloat()
                } else if (definition.uiType == UIComponentType.DIRECTION_DIAL) {
                    // Special handling for direction dials: coerce to range and normalize
                    val coercedValue = when {
                        definition.formula == "(x*360)-180" -> {
                            // For rotation (-179 to 180): coerce using modulo and normalize
                            val coerced = ((intValue % 360) + 360) % 360
                            val rangeValue = if (coerced > 180) coerced - 360 else coerced
                            (rangeValue + 180f) / 360f
                        }
                        definition.formula == "x*359-179" -> {
                            // For phase (-179 to 180): convert from any range and normalize
                            val rangeValue = if (intValue > 180) intValue - 360 else if (intValue < -179) intValue + 360 else intValue
                            (rangeValue.coerceIn(-179, 180) + 179f) / 359f
                        }
                        else -> {
                            // For other direction dials, use standard reverse formula
                            InputParameterDefinitions.reverseFormula(definition, intValue.toFloat())
                        }
                    }
                    coercedValue
                } else {
                    InputParameterDefinitions.reverseFormula(definition, intValue.toFloat())
                }

                val actualValue = if (shouldNotNormalize) {
                    intValue.toFloat()
                } else {
                    InputParameterDefinitions.applyFormula(definition, normalized)
                }

                val displayText = if (definition.enumValues != null && intValue >= 0 && intValue < definition.enumValues.size) {
                    definition.enumValues[intValue]
                } else {
                    "${actualValue.toInt()}${definition.unit ?: ""}"
                }
                InputParameterValue(
                    normalizedValue = normalized,
                    stringValue = "",
                    displayValue = displayText
                )
            }
            floatValue != null -> {
                // For phase dials, incoming floats from JUCE state dump may be in 0-360 range
                val adjustedFloat = if (definition.formula == "x*359-179" && floatValue > 180f) {
                    floatValue - 360f
                } else {
                    floatValue
                }
                val normalized = InputParameterDefinitions.reverseFormula(definition, adjustedFloat)
                val actualValue = InputParameterDefinitions.applyFormula(definition, normalized)
                InputParameterValue(
                    normalizedValue = normalized,
                    stringValue = "",
                    displayValue = "${String.format(Locale.US, "%.2f", actualValue)}${definition.unit ?: ""}"
                )
            }
            else -> return
        }

        // Read-modify-write as one compare-and-set loop. Two threads write here: the OSC
        // processing coroutine (every inbound value) and the UI thread (the local echo of
        // every tablet edit). A plain read-then-assign let one overwrite the state the
        // other had just published, and the lost value stayed stale until that
        // parameter next changed. update {} retries against the fresh state instead;
        // the lambda may therefore run more than once, so it only builds the new state.
        _inputParametersState.update { currentState ->
            val channel = currentState.getChannel(inputId)

            // Create a new parameter map for this channel with the updated parameter
            val updatedParameters = channel.parameters.toMutableMap()
            updatedParameters[definition.variableName] = paramValue

            // Create a new channel with the updated parameters
            val updatedChannel = InputChannelState(
                inputId = inputId,
                parameters = updatedParameters
            )

            // Create a new channels map with the updated channel
            val updatedChannels = currentState.channels.toMutableMap()
            updatedChannels[inputId] = updatedChannel

            // Force StateFlow emission by creating a completely new state object with incremented revision
            InputParametersState(
                channels = updatedChannels,
                selectedInputId = currentState.selectedInputId,
                revision = currentState.revision + 1  // Increment to force Compose change detection
            )
        }

        // Note: cluster assignment (clusterId) is synced to markers via
        // inputParametersState -> LaunchedEffect in MainActivity, not directly here.
        // Direct _markers updates here would race with syncMarkers() from MainActivity.
    }
    
    fun startOscServerWithCanvasDimensions(canvasWidth: Float, canvasHeight: Float) {
        // Update the shared canvas dimensions
        CanvasDimensions.updateDimensions(canvasWidth, canvasHeight)
        startServer()
    }
    
    fun startOscServer() {
        startServer()
    }
    
    fun sendArrayAdjustCommand(oscAddress: String, arrayId: Int, value: Float) {
        serviceScope.launch {
            sendOscArrayAdjustCommand(this@OscService, oscAddress, arrayId, value)
        }
    }
    
    fun isOscServerRunning(): Boolean {
        return isServerRunning
    }

    private fun startConnectionTimeoutMonitor() {
        // Cancel any existing monitor
        connectionTimeoutJob?.cancel()

        connectionTimeoutJob = serviceScope.launch {
            while (isActive && _connectionState.value == RemoteConnectionState.CONNECTED) {
                delay(1000) // Check every second

                val timeSinceLastHeartbeat = System.currentTimeMillis() - lastHeartbeatReceivedTime
                if (timeSinceLastHeartbeat >= CONNECTION_TIMEOUT_MS) {
                    _connectionState.value = RemoteConnectionState.DISCONNECTED
                    endConnectionScopedVisState()
                    android.util.Log.d("OscService", "Connection timeout - no heartbeat for ${timeSinceLastHeartbeat}ms")
                    break
                }
            }
        }
    }

    // Clear completeness tracking at the start of a fresh connection (the server resends
    // the full dump on connect).
    private fun resetSyncTracking() {
        resyncJob?.cancel()
        resyncFallbackJob?.cancel()
        // The dump starting now supersedes any re-dump we were about to ask for.
        inventoryRefreshJob?.cancel()
        receivedNames.clear()
        receivedPositions.clear()
        expectedChannelCount = 0
        stateCompleteSeen = false
        // Per dump cycle: an inventory from the PREVIOUS dump must not suppress the
        // v3 inference in this one. The stored inventory itself is kept — a stale
        // one is still better than none while the new dump streams in.
        inventoryReceivedThisDump = false
    }

    // The desktop sends heartbeats and dumps only to a target it hears, so the run of
    // pings that raised desktopNotHearing is over. Also cleared when the connection
    // ends: with no pings at all there is nothing left to diagnose.
    private fun clearDesktopNotHearing() {
        pingsWithoutHeartbeat.set(0)
        _desktopNotHearing.value = false
    }

    // The connection ended, or our socket restarted: what the Visualisation tab's
    // recovery learned about this connection does not carry over to the next one.
    private fun endConnectionScopedVisState() {
        visFallbackResyncSent = false
        clearDesktopNotHearing()
    }

    /**
     * Install a channel inventory that the server described, or that we inferred from
     * a dump. Both are knowledge about which channels exist, so a pin absent from it
     * is genuinely gone; the initial empty default never comes through here.
     */
    private fun applyChannelInventory(inventory: ChannelInventory) {
        _channelInventory.value = inventory
        // A pin on a channel that no longer exists can never receive rows again.
        val pinned = _visPinnedChannel.value
        if (pinned > 0 && !inventory.contains(pinned)) {
            setVisPin(0)
        }
    }

    /**
     * v3-desktop fallback: reconstruct the inventory from the ids the dump actually
     * mentioned when no /remote/channelList arrived in this cycle.
     *
     * A desktop that predates v4 still names and positions every live channel, so the
     * observed id set IS the real one, gaps included. Without this a v4 tablet facing
     * a v3 desktop would have no inventory at all and show nothing — worse than the
     * 1..count enumeration it replaces. What cannot be recovered is marked: display
     * order can only be ascending number, every channel is reported mono, and the
     * result carries inferred = true.
     */
    private fun inferInventoryIfMissing() {
        if (inventoryReceivedThisDump) return

        val current = _channelInventory.value
        // Never overwrite an inventory THIS desktop sent with a guess; one left over
        // from an earlier connection carries no such authority.
        if (!current.isEmpty && !current.inferred && inventoryFromCurrentConnection) return

        // Union of both sets, deduplicated through an explicit HashSet: a channel
        // that reported only a name or only a position still exists, and a number in
        // both must not end up twice in the inventory.
        val union = HashSet<Int>(receivedNames)
        union.addAll(receivedPositions)
        val observed = union.filter { it in 1..MAX_INPUTS }.sorted()
        if (observed.isEmpty() || observed == current.numbers) return

        applyChannelInventory(
            ChannelInventory(observed.map { ChannelInfo(it, isStereo = false) }, inferred = true)
        )
    }

    // True from /remote/dumpBegin until the verifier that closes the cycle has run.
    // A dump cycle ends in its own inference, so an /inputs that arrives inside one
    // needs nothing done about it.
    private fun dumpCycleInProgress(): Boolean =
        resyncFallbackJob?.isActive == true || resyncJob?.isActive == true

    /**
     * A v3 desktop announces a mid-session structural change with /inputs plus a
     * burst of names and positions, and never re-dumps. Without this an inferred
     * inventory would stay frozen at the channel set observed when the connection
     * came up: added channels unlistable and unpickable, deleted ones still drawn
     * on the Map and the Locking/Visibility tabs, and a pin on a dead channel never
     * cleared.
     *
     * Asks for a full dump rather than re-deriving from the tracking sets, because
     * those sets only ever grow — a deletion leaves no trace in them — and clearing
     * them first would throw away the burst, which is sent direct while the /inputs
     * that brought us here went through the desktop's rate limiter and can arrive
     * after it.
     */
    private fun scheduleInferredInventoryRefresh() {
        // A real /remote/channelList is authoritative and is itself re-sent on every
        // structural edit; only a guessed inventory can go stale unnoticed.
        if (!_channelInventory.value.inferred) return
        if (dumpCycleInProgress()) return

        inventoryRefreshJob?.cancel()
        inventoryRefreshJob = serviceScope.launch {
            delay(INVENTORY_REFRESH_DEBOUNCE_MS)
            // Re-checked after the delay: a dump whose dumpBegin merely arrived after
            // this /inputs ends in the same inference, making the request redundant.
            if (isActive && _channelInventory.value.inferred && !dumpCycleInProgress() &&
                _connectionState.value == RemoteConnectionState.CONNECTED) {
                android.util.Log.d("OscService",
                    "channel count now $expectedChannelCount with an inferred inventory - " +
                    "requesting a full re-dump")
                sendOscRequestResync(this@OscService, emptyList())
            }
        }
    }

    // If the stateComplete marker is itself dropped, still verify after a fixed delay
    // (using the channel count learned from the /inputs message in the dump).
    private fun scheduleResyncFallback() {
        resyncFallbackJob?.cancel()
        resyncFallbackJob = serviceScope.launch {
            delay(RESYNC_FALLBACK_MS)
            if (isActive && !stateCompleteSeen &&
                _connectionState.value == RemoteConnectionState.CONNECTED) {
                launchResyncVerifier()
            }
        }
    }

    // Channels missing a name and/or a position after the dump, by permanent number.
    // Driven by the inventory, never by 1..count: numbers are permanent and gapped,
    // so 1..count asks for channels that do not exist — and since the server cannot
    // answer for them, every retry fails identically and the dump is declared
    // permanently incomplete.
    private fun computeMissingChannels(): List<Int> {
        val inventory = _channelInventory.value
        // An INFERRED inventory is built out of the very arrivals this check counts,
        // so it can never name a channel that was lost outright. Against a v3 desktop
        // the 1..count guess remains the only way to notice one.
        val candidates =
            if (!inventory.isEmpty && !inventory.inferred) inventory.numbers
            else (1..expectedChannelCount).toList()
        return candidates.filter { it !in receivedNames || it !in receivedPositions }
    }

    // Verify the dump arrived complete; re-request missing channels with bounded retries.
    private fun launchResyncVerifier() {
        resyncJob?.cancel()
        resyncJob = serviceScope.launch {
            delay(RESYNC_SETTLE_MS) // let any trailing bundles land before the first check
            var attempt = 0
            var complete = false
            while (attempt < MAX_RESYNC_ATTEMPTS && isActive) {
                // Re-derived on every pass rather than once before the loop: the
                // retries below are what recover the channels lost in transit, so
                // an inventory inferred from the first pass alone would stay frozen
                // at those losses while the loop goes on to declare the dump
                // complete — and a channel missing from the inventory is filtered
                // out of the Map, Locking and Visibility tabs and the picker for the
                // rest of the connection. Also covers the path where stateComplete
                // itself was lost and the fallback timer brought us here. No-op once
                // a real inventory has arrived.
                inferInventoryIfMissing()
                val missing = computeMissingChannels()
                if (missing.isEmpty()) {
                    android.util.Log.d("OscService", "State dump complete ($expectedChannelCount channels)")
                    complete = true
                    break
                }
                attempt++
                android.util.Log.w("OscService",
                    "State dump incomplete: ${missing.size} channels missing, resync attempt $attempt/$MAX_RESYNC_ATTEMPTS")
                sendOscRequestResync(this@OscService, missing)
                delay(RESYNC_BACKOFF_MS) // wait for the resend to arrive before re-checking
            }
            if (!complete) {
                // The final attempt's arrivals never went through the loop head.
                inferInventoryIfMissing()
                val stillMissing = computeMissingChannels()
                if (stillMissing.isNotEmpty()) {
                    android.util.Log.e("OscService",
                        "State dump still incomplete after $MAX_RESYNC_ATTEMPTS attempts: missing $stillMissing")
                }
            }
            // Completeness tracking only covers name + position; the ~95 detailed params
            // of the channel currently shown on the Input Parameters tab could still have
            // gaps. Re-pull them once per dump cycle — cheap (2-3 bundles) and it keeps
            // the default-selected channel (1) from being the one channel that never
            // recovers, since the operator never re-selects it.
            sendOscRequestInputParameters(this@OscService, _inputParametersState.value.selectedInputId)
        }
    }

    // Ask the server for a complete re-dump (empty channel list = full state). Used on
    // reconnect: the tablet may have missed any number of changes while disconnected.
    fun requestFullResync() {
        serviceScope.launch {
            sendOscRequestResync(this@OscService, emptyList())
        }
    }

    fun restartOscServer() {
        val oldJob = serverJob
        serverJob = null
        isServerRunning = false
        // Clear composite deltas and sampler playing state on restart
        _compositePositions.value = emptyMap()
        _samplerPlaying.value = emptyMap()

        serviceScope.launch {
            // Wait for old server socket to fully close before rebinding
            oldJob?.cancelAndJoin()
            // New network parameters may point at another desktop, and what the old one
            // said would pass for its answer: counts and rows that satisfy the
            // Visualisation tab's checks, a protocol version for the mismatch flag.
            // Cleared only now that the old socket's processing loop has stopped, so
            // none of its messages can land after the reset. The pin is this tablet's
            // choice and is kept; the next dump restates it.
            _visState.value = VisualisationState()
            _serverProtocolVersion.value = 0
            endConnectionScopedVisState()
            startServer()
        }
    }
    
    fun updateNetworkParameters() {
        restartOscServer()
    }
    
    // Methods for MainActivity to get buffered data
    fun getBufferedMarkerUpdates(): List<OscMarkerUpdate> {
        val updates = mutableListOf<OscMarkerUpdate>()
        while (markerUpdates.isNotEmpty()) {
            markerUpdates.poll()?.let { updates.add(it) }
        }
        return updates
    }
    
    fun getBufferedStageUpdates(): List<OscStageUpdate> {
        val updates = mutableListOf<OscStageUpdate>()
        while (stageUpdates.isNotEmpty()) {
            stageUpdates.poll()?.let { updates.add(it) }
        }
        return updates
    }
    
    fun getBufferedInputsUpdates(): List<OscInputsUpdate> {
        val updates = mutableListOf<OscInputsUpdate>()
        while (inputsUpdates.isNotEmpty()) {
            inputsUpdates.poll()?.let { updates.add(it) }
        }
        return updates
    }

    fun getBufferedInputParameterUpdates(): List<OscInputParameterUpdate> {
        val updates = mutableListOf<OscInputParameterUpdate>()
        while (inputParameterUpdates.isNotEmpty()) {
            inputParameterUpdates.poll()?.let { updates.add(it) }
        }
        return updates
    }

    fun getBufferedCompositePositionUpdates(): List<OscCompositePositionUpdate> {
        val updates = mutableListOf<OscCompositePositionUpdate>()
        while (compositePositionUpdates.isNotEmpty()) {
            compositePositionUpdates.poll()?.let { updates.add(it) }
        }
        return updates
    }
    
    // Methods for MainActivity to sync current state
    fun syncMarkers(markers: List<Marker>) {
        _markers.value = markers
    }

    fun syncStageDimensions(
        width: Float,
        depth: Float,
        height: Float,
        originX: Float = -1f,
        originY: Float = 0f,
        originZ: Float = 0f,
        shape: Int = 0,
        diameter: Float = 20f,
        domeElev: Float = 180f
    ) {
        _stageWidth.value = width
        _stageDepth.value = depth
        _stageHeight.value = height
        _stageOriginX.value = originX
        _stageOriginY.value = originY
        _stageOriginZ.value = originZ
        _stageShape.value = shape
        _stageDiameter.value = diameter
        _domeElevation.value = domeElev
    }
    
    fun syncNumberOfInputs(count: Int) {
        _numberOfInputs.value = count
    }
    
    fun syncInputParametersState(state: InputParametersState) {
        _inputParametersState.value = state
    }
    
    fun setSelectedInput(inputId: Int) {
        // Same compare-and-set as updateInputParameterFromOsc: this runs on the UI thread
        // while inbound values land from the OSC coroutine, so a plain copy-and-assign
        // could drop a value that arrived in between, or be undone by an inbound write
        // that started from the state before the new selection.
        _inputParametersState.update { currentState ->
            currentState.copy(
                selectedInputId = inputId,
                revision = currentState.revision + 1
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        serverJob?.cancel()
        isServerRunning = false
        job.cancel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                locStatic("remote.notification.channelName"),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = locStatic("remote.notification.channelDescription")
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return try {
            val notificationIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                action = "com.wfsdiy.wfs_control_2.NOTIFICATION_TAP"
            }
            
            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            
            val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, pendingIntentFlags)

            NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle(locStatic("remote.notification.title"))
                .setContentText(locStatic("remote.notification.text"))
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setAutoCancel(false)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build()
        } catch (e: Exception) {
            NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle(locStatic("remote.notification.title"))
                .setContentText(locStatic("remote.notification.text"))
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build()
        }
    }
}

