package com.wfsdiy.wfs_control_2

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder

import androidx.compose.ui.geometry.Offset
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

class OscService : Service() {

    private val binder = OscBinder()
    private val job = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + job)
    
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
    
    data class OscClusterZUpdate(
        val clusterId: Int,
        val normalizedZ: Float,
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

    // Buffers for incoming OSC data
    private val markerUpdates = ConcurrentLinkedQueue<OscMarkerUpdate>()
    private val normalizedMarkerUpdates = ConcurrentLinkedQueue<OscNormalizedMarkerUpdate>()
    private val stageUpdates = ConcurrentLinkedQueue<OscStageUpdate>()
    private val inputsUpdates = ConcurrentLinkedQueue<OscInputsUpdate>()
    private val clusterZUpdates = ConcurrentLinkedQueue<OscClusterZUpdate>()
    private val inputParameterUpdates = ConcurrentLinkedQueue<OscInputParameterUpdate>()
    private val clusterConfigUpdates = ConcurrentLinkedQueue<OscClusterConfigUpdate>()
    
    // StateFlows for real-time data (when MainActivity is active)
    private val _markers = MutableStateFlow<List<Marker>>(emptyList())
    val markers: StateFlow<List<Marker>> = _markers.asStateFlow()
    
    private val _clusterMarkers = MutableStateFlow<List<ClusterMarker>>(emptyList())
    val clusterMarkers: StateFlow<List<ClusterMarker>> = _clusterMarkers.asStateFlow()
    
    private val _clusterNormalizedHeights = MutableStateFlow<List<Float>>(List(10) { 0.2f })
    val clusterNormalizedHeights: StateFlow<List<Float>> = _clusterNormalizedHeights.asStateFlow()
    
    private val _stageWidth = MutableStateFlow(16.0f)
    val stageWidth: StateFlow<Float> = _stageWidth.asStateFlow()
    
    private val _stageDepth = MutableStateFlow(10.0f)
    val stageDepth: StateFlow<Float> = _stageDepth.asStateFlow()
    
    private val _stageHeight = MutableStateFlow(7.0f)
    val stageHeight: StateFlow<Float> = _stageHeight.asStateFlow()
    
    private val _stageOriginX = MutableStateFlow(8.0f) // 0.5 * stageWidth (16.0f)
    val stageOriginX: StateFlow<Float> = _stageOriginX.asStateFlow()
    
    private val _stageOriginY = MutableStateFlow(0.0f)
    val stageOriginY: StateFlow<Float> = _stageOriginY.asStateFlow()
    
    private val _stageOriginZ = MutableStateFlow(0.0f)
    val stageOriginZ: StateFlow<Float> = _stageOriginZ.asStateFlow()
    
    private val _numberOfInputs = MutableStateFlow(64)
    val numberOfInputs: StateFlow<Int> = _numberOfInputs.asStateFlow()
    
    private val _inputParametersState = MutableStateFlow(InputParametersState())
    val inputParametersState: StateFlow<InputParametersState> = _inputParametersState.asStateFlow()

    private val _clusterConfigs = MutableStateFlow(List(10) { index -> ClusterConfig(id = index + 1) })
    val clusterConfigs: StateFlow<List<ClusterConfig>> = _clusterConfigs.asStateFlow()

    // Store screen dimensions once at startup
    private var screenWidth: Float = 0f
    private var screenHeight: Float = 0f

    inner class OscBinder : Binder() {
        fun getService(): OscService = this@OscService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        
        // Initialize screen dimensions once at startup
        initializeScreenDimensions()
        
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
                    onNumberOfInputsChanged = { newCount ->
                        inputsUpdates.offer(OscInputsUpdate(newCount))
                        _numberOfInputs.value = newCount
                    },
                    onClusterZChanged = { clusterId, newNormalizedZ ->
                        clusterZUpdates.offer(OscClusterZUpdate(clusterId, newNormalizedZ))
                        val index = clusterId - 1
                        if (index >= 0 && index < _clusterNormalizedHeights.value.size) {
                            val updatedHeights = _clusterNormalizedHeights.value.toMutableList()
                            updatedHeights[index] = newNormalizedZ.coerceIn(0f, 1f)
                            _clusterNormalizedHeights.value = updatedHeights
                        }
                    },
                    onInputParameterIntReceived = { oscPath, inputId, value ->
                        inputParameterUpdates.offer(OscInputParameterUpdate(oscPath, inputId, intValue = value))
                        updateInputParameterFromOsc(oscPath, inputId, intValue = value)
                    },
                    onInputParameterFloatReceived = { oscPath, inputId, value ->
                        inputParameterUpdates.offer(OscInputParameterUpdate(oscPath, inputId, floatValue = value))
                        updateInputParameterFromOsc(oscPath, inputId, floatValue = value)
                    },
                    onInputParameterStringReceived = { oscPath, inputId, value ->
                        inputParameterUpdates.offer(OscInputParameterUpdate(oscPath, inputId, stringValue = value))
                        updateInputParameterFromOsc(oscPath, inputId, stringValue = value)
                    },
                    onClusterReferenceModeChanged = { clusterId, mode ->
                        clusterConfigUpdates.offer(OscClusterConfigUpdate(clusterId, referenceMode = mode))
                        val index = clusterId - 1
                        if (index >= 0 && index < _clusterConfigs.value.size) {
                            val updatedConfigs = _clusterConfigs.value.toMutableList()
                            updatedConfigs[index] = updatedConfigs[index].copy(referenceMode = mode)
                            _clusterConfigs.value = updatedConfigs
                        }
                    },
                    onClusterTrackedInputChanged = { clusterId, inputId ->
                        clusterConfigUpdates.offer(OscClusterConfigUpdate(clusterId, trackedInputId = inputId))
                        val index = clusterId - 1
                        if (index >= 0 && index < _clusterConfigs.value.size) {
                            val updatedConfigs = _clusterConfigs.value.toMutableList()
                            updatedConfigs[index] = updatedConfigs[index].copy(trackedInputId = inputId)
                            _clusterConfigs.value = updatedConfigs
                        }
                    }
                )
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isServerRunning = false
            }
        }
    }

    fun sendMarkerPosition(markerId: Int, x: Float, y: Float, isCluster: Boolean) {
        serviceScope.launch {
            sendOscPosition(this@OscService, markerId, x, y, isCluster)
        }
    }

    fun sendClusterZ(clusterId: Int, normalizedZ: Float) {
        serviceScope.launch {
            sendOscClusterZ(this@OscService, clusterId, normalizedZ)
        }
    }
    
    fun sendInputParameterInt(oscPath: String, inputId: Int, value: Int) {
        serviceScope.launch {
            sendOscInputParameterInt(this@OscService, oscPath, inputId, value)
        }
    }
    
    fun sendInputParameterFloat(oscPath: String, inputId: Int, value: Float) {
        serviceScope.launch {
            sendOscInputParameterFloat(this@OscService, oscPath, inputId, value)
        }
    }
    
    fun sendInputParameterString(oscPath: String, inputId: Int, value: String) {
        serviceScope.launch {
            sendOscInputParameterString(this@OscService, oscPath, inputId, value)
        }
    }

    fun sendInputParameterIncDec(oscPath: String, inputId: Int, direction: String, value: Float) {
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

        val paramValue = when {
            stringValue != null -> {
                InputParameterValue(
                    normalizedValue = 0f,
                    stringValue = stringValue,
                    displayValue = stringValue
                )
            }
            intValue != null -> {
                // Check if this is an ON/OFF switch that needs value inversion
                val isOnOffSwitch = definition.enumValues != null &&
                                   definition.enumValues.size == 2 &&
                                   definition.enumValues[0] == "ON" &&
                                   definition.enumValues[1] == "OFF"

                // Invert for ON/OFF switches: OSC 1 (ON) -> UI index 0, OSC 0 (OFF) -> UI index 1
                val uiValue = if (isOnOffSwitch) 1 - intValue else intValue

                // For dropdowns and text buttons, don't normalize - store the integer directly
                // For direction dials, we need special handling to normalize with proper range coercion
                val shouldNotNormalize = definition.uiType == UIComponentType.DROPDOWN ||
                                        definition.uiType == UIComponentType.TEXT_BUTTON ||
                                        (definition.formula == "x*360" && definition.dataType == ParameterType.INT)

                val normalized = if (shouldNotNormalize) {
                    uiValue.toFloat()
                } else if (definition.uiType == UIComponentType.DIRECTION_DIAL) {
                    // Special handling for direction dials: coerce to range and normalize
                    val coercedValue = when {
                        definition.formula == "(x*360)-180" -> {
                            // For rotation (-179 to 180): coerce using modulo and normalize
                            val coerced = ((uiValue % 360) + 360) % 360
                            val rangeValue = if (coerced > 180) coerced - 360 else coerced
                            (rangeValue + 180f) / 360f
                        }
                        else -> {
                            // For other direction dials, use standard reverse formula
                            InputParameterDefinitions.reverseFormula(definition, uiValue.toFloat())
                        }
                    }
                    coercedValue
                } else {
                    InputParameterDefinitions.reverseFormula(definition, uiValue.toFloat())
                }

                val actualValue = if (shouldNotNormalize) {
                    uiValue.toFloat()
                } else {
                    InputParameterDefinitions.applyFormula(definition, normalized)
                }

                val displayText = if (definition.enumValues != null && uiValue >= 0 && uiValue < definition.enumValues.size) {
                    definition.enumValues[uiValue]
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
                val normalized = InputParameterDefinitions.reverseFormula(definition, floatValue)
                val actualValue = InputParameterDefinitions.applyFormula(definition, normalized)
                InputParameterValue(
                    normalizedValue = normalized,
                    stringValue = "",
                    displayValue = "${String.format(Locale.US, "%.2f", actualValue)}${definition.unit ?: ""}"
                )
            }
            else -> return
        }

        // Get current state and update the parameter
        val currentState = _inputParametersState.value
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
        _inputParametersState.value = InputParametersState(
            channels = updatedChannels,
            selectedInputId = currentState.selectedInputId,
            revision = currentState.revision + 1  // Increment to force Compose change detection
        )

        // Special handling for cluster assignment: update marker's clusterId
        if (oscPath == "/remoteInput/cluster" && intValue != null) {
            val updatedMarkers = _markers.value.map { marker ->
                if (marker.id == inputId) {
                    marker.copy(clusterId = intValue)
                } else {
                    marker
                }
            }
            _markers.value = updatedMarkers
        }
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
    
    fun restartOscServer() {
        serverJob?.cancel()
        isServerRunning = false
        startServer()
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
    
    fun getBufferedClusterZUpdates(): List<OscClusterZUpdate> {
        val updates = mutableListOf<OscClusterZUpdate>()
        while (clusterZUpdates.isNotEmpty()) {
            clusterZUpdates.poll()?.let { updates.add(it) }
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
    
    // Methods for MainActivity to sync current state
    fun syncMarkers(markers: List<Marker>) {
        _markers.value = markers
    }
    
    fun syncClusterMarkers(clusterMarkers: List<ClusterMarker>) {
        _clusterMarkers.value = clusterMarkers
    }
    
    fun syncClusterHeights(heights: List<Float>) {
        _clusterNormalizedHeights.value = heights
    }
    
    fun syncStageDimensions(width: Float, depth: Float, height: Float, originX: Float = -1f, originY: Float = 0f, originZ: Float = 0f) {
        _stageWidth.value = width
        _stageDepth.value = depth
        _stageHeight.value = height
        _stageOriginX.value = if (originX == -1f) width * 0.5f else originX
        _stageOriginY.value = originY
        _stageOriginZ.value = originZ
    }
    
    fun syncNumberOfInputs(count: Int) {
        _numberOfInputs.value = count
    }
    
    fun syncInputParametersState(state: InputParametersState) {
        _inputParametersState.value = state
    }
    
    fun setSelectedInput(inputId: Int) {
        val currentState = _inputParametersState.value
        _inputParametersState.value = currentState.copy(
            selectedInputId = inputId,
            revision = currentState.revision + 1
        )
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
                "OSC Service", 
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background service for OSC communication"
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
                .setContentTitle("WFS Control")
                .setContentText("OSC Service is running.")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setAutoCancel(false)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build()
        } catch (e: Exception) {
            NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("WFS Control")
                .setContentText("OSC Service is running.")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build()
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_CHANNEL_ID = "OscServiceChannel"
    }
}

