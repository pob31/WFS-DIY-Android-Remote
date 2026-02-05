package com.wfsdiy.wfs_control_2

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainActivityViewModel(private val oscService: OscService) : ViewModel() {

    val markers: StateFlow<List<Marker>> = oscService.markers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    val stageWidth: StateFlow<Float> = oscService.stageWidth
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 20.0f)

    val stageDepth: StateFlow<Float> = oscService.stageDepth
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 10.0f)

    val stageHeight: StateFlow<Float> = oscService.stageHeight
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 8.0f)

    val stageOriginX: StateFlow<Float> = oscService.stageOriginX
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0f) // Center-referenced

    val stageOriginY: StateFlow<Float> = oscService.stageOriginY
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), -5.0f) // Downstage center

    val stageOriginZ: StateFlow<Float> = oscService.stageOriginZ
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0f)

    // Stage shape: 0=box, 1=cylinder, 2=dome
    val stageShape: StateFlow<Int> = oscService.stageShape
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val stageDiameter: StateFlow<Float> = oscService.stageDiameter
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 20.0f)

    val domeElevation: StateFlow<Float> = oscService.domeElevation
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 180.0f)

    val numberOfInputs: StateFlow<Int> = oscService.numberOfInputs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 64)
    
    val inputParametersState: StateFlow<InputParametersState> = oscService.inputParametersState
        .stateIn(viewModelScope, SharingStarted.Eagerly, InputParametersState())

    val connectionState: StateFlow<OscService.RemoteConnectionState> = oscService.connectionState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OscService.RemoteConnectionState.DISCONNECTED)

    fun sendMarkerPosition(markerId: Int, x: Float, y: Float, isCluster: Boolean) {
        oscService.sendMarkerPosition(markerId, x, y, isCluster)
    }
    
    fun sendArrayAdjustCommand(oscAddress: String, arrayId: Int, value: Float) {
        oscService.sendArrayAdjustCommand(oscAddress, arrayId, value)
    }
    
    fun startOscServerWithCanvasDimensions(canvasWidth: Float, canvasHeight: Float) {
        oscService.startOscServerWithCanvasDimensions(canvasWidth, canvasHeight)
    }
    
    fun startOscServer() {
        oscService.startOscServer()
    }
    
    fun isOscServerRunning(): Boolean = oscService.isOscServerRunning()
    
    fun restartOscServer() {
        oscService.restartOscServer()
    }
    
    fun updateNetworkParameters() {
        oscService.updateNetworkParameters()
    }
    
    // Methods to get buffered data from service
    fun getBufferedMarkerUpdates(): List<OscService.OscMarkerUpdate> {
        return oscService.getBufferedMarkerUpdates()
    }
    
    fun getBufferedStageUpdates(): List<OscService.OscStageUpdate> {
        return oscService.getBufferedStageUpdates()
    }
    
    fun getBufferedInputsUpdates(): List<OscService.OscInputsUpdate> {
        return oscService.getBufferedInputsUpdates()
    }

    // Methods to sync current state with service
    fun syncMarkers(markers: List<Marker>) {
        oscService.syncMarkers(markers)
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
        oscService.syncStageDimensions(width, depth, height, originX, originY, originZ, shape, diameter, domeElev)
    }
    
    fun syncNumberOfInputs(count: Int) {
        oscService.syncNumberOfInputs(count)
    }
    
    // Input parameter methods
    fun sendInputParameterInt(oscPath: String, inputId: Int, value: Int) {
        oscService.sendInputParameterInt(oscPath, inputId, value)
    }
    
    fun sendInputParameterFloat(oscPath: String, inputId: Int, value: Float) {
        oscService.sendInputParameterFloat(oscPath, inputId, value)
    }
    
    fun sendInputParameterString(oscPath: String, inputId: Int, value: String) {
        oscService.sendInputParameterString(oscPath, inputId, value)
    }

    fun sendInputParameterIncDec(oscPath: String, inputId: Int, direction: String, value: Float) {
        oscService.sendInputParameterIncDec(oscPath, inputId, direction, value)
    }

    fun requestInputParameters(inputId: Int) {
        oscService.requestInputParameters(inputId)
    }
    
    fun setSelectedInput(inputId: Int) {
        oscService.setSelectedInput(inputId)
    }
    
    fun getBufferedInputParameterUpdates(): List<OscService.OscInputParameterUpdate> {
        return oscService.getBufferedInputParameterUpdates()
    }
    
    fun syncInputParametersState(state: InputParametersState) {
        oscService.syncInputParametersState(state)
    }

    fun sendClusterMove(clusterId: Int, deltaX: Float, deltaY: Float) {
        oscService.sendClusterMove(clusterId, deltaX, deltaY)
    }

    fun sendBarycenterMove(clusterId: Int, deltaX: Float, deltaY: Float) {
        oscService.sendBarycenterMove(clusterId, deltaX, deltaY)
    }

    fun sendClusterScale(clusterId: Int, scaleFactor: Float) {
        oscService.sendClusterScale(clusterId, scaleFactor)
    }

    fun sendClusterRotation(clusterId: Int, angleDegrees: Float) {
        oscService.sendClusterRotation(clusterId, angleDegrees)
    }

    /**
     * Send combined XY position for atomic position updates.
     * This ensures both X and Y are processed together on the server,
     * preventing jagged diagonal movements when speed limiting is enabled.
     */
    fun sendInputPositionXY(inputId: Int, posX: Float, posY: Float) {
        oscService.sendInputPositionXY(inputId, posX, posY)
    }

    fun getBufferedClusterConfigUpdates(): List<OscService.OscClusterConfigUpdate> {
        return oscService.getBufferedClusterConfigUpdates()
    }

    val clusterConfigs: StateFlow<List<ClusterConfig>> = oscService.clusterConfigs

    // Composite deltas: inputId -> (deltaX, deltaY) in stage meters
    // Delta is the difference between composite position (after transformations) and target position
    val compositePositions: StateFlow<Map<Int, Pair<Float, Float>>> = oscService.compositePositions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // Factory for creating the ViewModel with the OscService dependency
    class Factory(private val oscService: OscService) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainActivityViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return MainActivityViewModel(oscService) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

