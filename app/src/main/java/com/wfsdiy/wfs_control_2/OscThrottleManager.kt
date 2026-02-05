package com.wfsdiy.wfs_control_2

import java.util.concurrent.ConcurrentHashMap

/**
 * Manages throttling of OSC messages to prevent excessive network traffic.
 * Each parameter is throttled independently at 50Hz (20ms minimum interval).
 */
object OscThrottleManager {
    // Minimum interval between sends for the same parameter (20ms = 50Hz)
    private const val MIN_SEND_INTERVAL_MS = 20L

    // Store last send time for each unique parameter key
    private val lastSendTimes = ConcurrentHashMap<String, Long>()

    // Store pending values for parameters that were throttled
    private val pendingValues = ConcurrentHashMap<String, PendingOscMessage>()

    /**
     * Check if a message should be sent based on throttling rules.
     * Returns true if enough time has passed since the last send for this parameter.
     *
     * @param parameterKey Unique key identifying the parameter (e.g., "/marker/positionXY_1")
     * @return true if the message should be sent, false if it should be throttled
     */
    fun shouldSend(parameterKey: String): Boolean {
        val currentTime = System.currentTimeMillis()
        val lastSendTime = lastSendTimes[parameterKey] ?: 0L
        val timeSinceLastSend = currentTime - lastSendTime

        return if (timeSinceLastSend >= MIN_SEND_INTERVAL_MS) {
            lastSendTimes[parameterKey] = currentTime
            true
        } else {
            false
        }
    }

    /**
     * Store a pending message that was throttled.
     * This allows us to send the most recent value once the throttle period expires.
     *
     * @param parameterKey Unique key identifying the parameter
     * @param sendAction Lambda function to execute when sending the message
     */
    fun storePending(parameterKey: String, sendAction: () -> Unit) {
        pendingValues[parameterKey] = PendingOscMessage(
            timestamp = System.currentTimeMillis(),
            sendAction = sendAction
        )
    }

    /**
     * Get and clear any pending message for a parameter.
     * This should be called after a successful send to check if there's a newer value waiting.
     *
     * @param parameterKey Unique key identifying the parameter
     * @return The pending message action, or null if none exists
     */
    fun getPendingAndClear(parameterKey: String): (() -> Unit)? {
        return pendingValues.remove(parameterKey)?.sendAction
    }

    /**
     * Clear all throttling state (useful for testing or resetting)
     */
    fun clear() {
        lastSendTimes.clear()
        pendingValues.clear()
    }

    /**
     * Generate a unique key for marker position messages
     */
    fun markerPositionKey(markerId: Int, isCluster: Boolean): String {
        return if (isCluster) "/cluster/positionXY_$markerId" else "/marker/positionXY_$markerId"
    }

    /**
     * Generate a unique key for input parameter messages
     */
    fun inputParameterKey(oscPath: String, inputId: Int): String {
        return "${oscPath}_$inputId"
    }

    /**
     * Generate a unique key for marker angle change messages
     */
    fun markerAngleChangeKey(markerId: Int, modeNumber: Int): String {
        return "/marker/angleChange_${markerId}_$modeNumber"
    }

    /**
     * Generate a unique key for marker radial change messages
     */
    fun markerRadialChangeKey(markerId: Int, modeNumber: Int): String {
        return "/marker/radialChange_${markerId}_$modeNumber"
    }

    /**
     * Generate a unique key for cluster rotation messages
     */
    fun clusterRotationKey(clusterId: Int): String {
        return "/cluster/rotation_$clusterId"
    }

    /**
     * Generate a unique key for cluster scale messages
     */
    fun clusterScaleKey(clusterId: Int): String {
        return "/cluster/scale_$clusterId"
    }

    /**
     * Generate a unique key for cluster move messages
     */
    fun clusterMoveKey(clusterId: Int): String {
        return "/cluster/move_$clusterId"
    }

    /**
     * Generate a unique key for barycenter move messages
     */
    fun barycenterMoveKey(clusterId: Int): String {
        return "/cluster/barycenter/move_$clusterId"
    }

    /**
     * Generate a unique key for array adjust command messages
     */
    fun arrayAdjustKey(oscAddress: String, arrayId: Int): String {
        return "${oscAddress}_$arrayId"
    }

    /**
     * Generate a unique key for marker orientation messages
     */
    fun markerOrientationKey(markerId: Int): String {
        return "/marker/orientation_$markerId"
    }

    /**
     * Generate a unique key for marker directivity messages
     */
    fun markerDirectivityKey(markerId: Int): String {
        return "/marker/directivity_$markerId"
    }

    /**
     * Generate a unique key for request input parameters messages
     */
    fun requestInputParametersKey(inputId: Int): String {
        return "/remoteInput/inputNumber_$inputId"
    }
}

/**
 * Data class to hold a pending OSC message
 */
private data class PendingOscMessage(
    val timestamp: Long,
    val sendAction: () -> Unit
)
