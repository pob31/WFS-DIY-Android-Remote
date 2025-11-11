package com.wfsdiy.wfs_control_2

import android.content.Context

import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.compareTo
import kotlin.times

fun getPaddedBytes(input: String, charsets: java.nio.charset.Charset = Charsets.UTF_8): ByteArray {
    val stringBytes = input.toByteArray(charsets)
    val lenWithNull = stringBytes.size + 1
    val paddedLen = (lenWithNull + 3) and (-4)
    val result = ByteArray(paddedLen)
    stringBytes.copyInto(result)
    return result
}

fun Int.toBytesBigEndian(): ByteArray {
    return byteArrayOf(
        (this shr 24).toByte(),
        (this shr 16).toByte(),
        (this shr 8).toByte(),
        this.toByte()
    )
}

fun Float.toBytesBigEndian(): ByteArray {
    return this.toBits().toBytesBigEndian()
}

fun sendOscPosition(context: Context, markerId: Int, x: Float, y: Float, isCluster: Boolean = false) {
    val throttleKey = OscThrottleManager.markerPositionKey(markerId, isCluster)

    // Check if we should send based on throttle
    if (!OscThrottleManager.shouldSend(throttleKey)) {
        // Store this as a pending send
        OscThrottleManager.storePending(throttleKey) {
            sendOscPosition(context, markerId, x, y, isCluster)
        }
        return
    }

    CoroutineScope(Dispatchers.IO).launch {
        try {
            val (_, outgoingPortStr, ipAddressStr) = loadNetworkParameters(context)
            val outgoingPort = outgoingPortStr.toIntOrNull()
            if (outgoingPort == null || !isValidPort(outgoingPortStr)) {
                return@launch
            }
            if (ipAddressStr.isBlank() || !isValidIpAddress(ipAddressStr)) {
                return@launch
            }
            val (canvasWidth, canvasHeight) = CanvasDimensions.getCurrentDimensions()
            val markerRadius = CanvasDimensions.getCurrentMarkerRadius()
            // Map 0.0-1.0 range to effective drawing area (canvas minus marker diameter)
            val effectiveWidth = canvasWidth - (markerRadius * 2f)
            val effectiveHeight = canvasHeight - (markerRadius * 2f)
            val normalizedX = if (effectiveWidth > 0f) (x - markerRadius) / effectiveWidth else 0f
            val normalizedY = if (effectiveHeight > 0f) 1f - ((y - markerRadius) / effectiveHeight) else 0f
            val addressPattern = if (isCluster) "/cluster/positionXY" else "/marker/positionXY"
            val addressPatternBytes = getPaddedBytes(addressPattern)
            val typeTagBytes = getPaddedBytes(",iff")
            val markerIdBytes = markerId.toBytesBigEndian()
            val normalizedXBytes = normalizedX.toBytesBigEndian()
            val normalizedYBytes = normalizedY.toBytesBigEndian()
            val oscPacketBytes = addressPatternBytes + typeTagBytes + markerIdBytes + normalizedXBytes + normalizedYBytes
            DatagramSocket().use { socket ->
                val address = InetAddress.getByName(ipAddressStr)
                val packet = DatagramPacket(oscPacketBytes, oscPacketBytes.size, address, outgoingPort)
                socket.send(packet)
            }

            // After successful send, check if there's a pending update
            val pendingAction = OscThrottleManager.getPendingAndClear(throttleKey)
            if (pendingAction != null) {
                // Schedule the pending update to run after the minimum interval
                kotlinx.coroutines.delay(20)
                pendingAction()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

fun sendOscClusterZ(context: Context, ClusterId: Int, normalizedZ: Float) {
    val throttleKey = OscThrottleManager.clusterZKey(ClusterId)

    if (!OscThrottleManager.shouldSend(throttleKey)) {
        OscThrottleManager.storePending(throttleKey) {
            sendOscClusterZ(context, ClusterId, normalizedZ)
        }
        return
    }

    CoroutineScope(Dispatchers.IO).launch {
        try {
            val (_, outgoingPortStr, ipAddressStr) = loadNetworkParameters(context)
            val outgoingPort = outgoingPortStr.toIntOrNull()

            if (outgoingPort == null || !isValidPort(outgoingPortStr)) {
                return@launch
            }
            if (ipAddressStr.isBlank() || !isValidIpAddress(ipAddressStr)) {
                return@launch
            }

            val addressPattern = "/cluster/positionZ"
            val addressPatternBytes = getPaddedBytes(addressPattern)
            // OSC type tag for an integer (clusterID) followed by a float (normalizedZ)
            val typeTagBytes = getPaddedBytes(",if")
            val ClusterIdBytes = ClusterId.toBytesBigEndian()
            val normalizedZBytes = normalizedZ.toBytesBigEndian()

            val oscPacketBytes = addressPatternBytes + typeTagBytes + ClusterIdBytes + normalizedZBytes

            DatagramSocket().use { socket ->
                val address = InetAddress.getByName(ipAddressStr)
                val packet = DatagramPacket(oscPacketBytes, oscPacketBytes.size, address, outgoingPort)
                socket.send(packet)
            }

            val pendingAction = OscThrottleManager.getPendingAndClear(throttleKey)
            if (pendingAction != null) {
                kotlinx.coroutines.delay(20)
                pendingAction()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

fun sendOscArrayAdjustCommand(context: Context, oscAddress: String, arrayId: Int, value: Float) {
    val throttleKey = OscThrottleManager.arrayAdjustKey(oscAddress, arrayId)

    if (!OscThrottleManager.shouldSend(throttleKey)) {
        OscThrottleManager.storePending(throttleKey) {
            sendOscArrayAdjustCommand(context, oscAddress, arrayId, value)
        }
        return
    }

    CoroutineScope(Dispatchers.IO).launch {
        try {
            val (_, outgoingPortStr, ipAddressStr) = loadNetworkParameters(context)
            val outgoingPort = outgoingPortStr.toIntOrNull()

            if (outgoingPort == null || !isValidPort(outgoingPortStr)) {
                return@launch
            }
            if (ipAddressStr.isBlank() || !isValidIpAddress(ipAddressStr)) {
                return@launch
            }

            val addressPatternBytes = getPaddedBytes(oscAddress)
            // Type tag for an integer (arrayId) followed by a float (value)
            val typeTagBytes = getPaddedBytes(",if")
            val arrayIdBytes = arrayId.toBytesBigEndian()
            val valueBytes = value.toBytesBigEndian()

            val oscPacketBytes = addressPatternBytes + typeTagBytes + arrayIdBytes + valueBytes

            DatagramSocket().use { socket ->
                val inetAddress = InetAddress.getByName(ipAddressStr)
                val packet = DatagramPacket(oscPacketBytes, oscPacketBytes.size, inetAddress, outgoingPort)
                socket.send(packet)
            }

            val pendingAction = OscThrottleManager.getPendingAndClear(throttleKey)
            if (pendingAction != null) {
                kotlinx.coroutines.delay(20)
                pendingAction()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

fun sendOscMarkerOrientation(context: Context, markerId: Int, angle: Int) {
    android.util.Log.d("OSC", "sendOscMarkerOrientation called: markerId=$markerId, angle=$angle")
    val throttleKey = OscThrottleManager.markerOrientationKey(markerId)

    if (!OscThrottleManager.shouldSend(throttleKey)) {
        OscThrottleManager.storePending(throttleKey) {
            sendOscMarkerOrientation(context, markerId, angle)
        }
        return
    }

    CoroutineScope(Dispatchers.IO).launch {
        try {
            val (_, outgoingPortStr, ipAddressStr) = loadNetworkParameters(context)
            val outgoingPort = outgoingPortStr.toIntOrNull()

            if (outgoingPort == null || !isValidPort(outgoingPortStr)) {
                return@launch
            }
            if (ipAddressStr.isBlank() || !isValidIpAddress(ipAddressStr)) {
                return@launch
            }

            val addressPattern = "/marker/orientation"
            val addressPatternBytes = getPaddedBytes(addressPattern)
            // Type tag for two integers (markerId, angle)
            val typeTagBytes = getPaddedBytes(",ii")
            val markerIdBytes = markerId.toBytesBigEndian()
            val angleBytes = angle.toBytesBigEndian()

            val oscPacketBytes = addressPatternBytes + typeTagBytes + markerIdBytes + angleBytes

            DatagramSocket().use { socket ->
                val inetAddress = InetAddress.getByName(ipAddressStr)
                val packet = DatagramPacket(oscPacketBytes, oscPacketBytes.size, inetAddress, outgoingPort)
                socket.send(packet)
            }

            val pendingAction = OscThrottleManager.getPendingAndClear(throttleKey)
            if (pendingAction != null) {
                kotlinx.coroutines.delay(20)
                pendingAction()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

fun sendOscMarkerDirectivity(context: Context, markerId: Int, directivity: Float) {
    android.util.Log.d("OSC", "sendOscMarkerDirectivity called: markerId=$markerId, directivity=$directivity")
    val throttleKey = OscThrottleManager.markerDirectivityKey(markerId)

    if (!OscThrottleManager.shouldSend(throttleKey)) {
        OscThrottleManager.storePending(throttleKey) {
            sendOscMarkerDirectivity(context, markerId, directivity)
        }
        return
    }

    CoroutineScope(Dispatchers.IO).launch {
        try {
            val (_, outgoingPortStr, ipAddressStr) = loadNetworkParameters(context)
            val outgoingPort = outgoingPortStr.toIntOrNull()

            if (outgoingPort == null || !isValidPort(outgoingPortStr)) {
                return@launch
            }
            if (ipAddressStr.isBlank() || !isValidIpAddress(ipAddressStr)) {
                return@launch
            }

            val addressPattern = "/marker/directivity"
            val addressPatternBytes = getPaddedBytes(addressPattern)
            // Type tag for integer and float (markerId, directivity)
            val typeTagBytes = getPaddedBytes(",if")
            val markerIdBytes = markerId.toBytesBigEndian()
            val directivityBytes = directivity.toBytesBigEndian()

            val oscPacketBytes = addressPatternBytes + typeTagBytes + markerIdBytes + directivityBytes

            DatagramSocket().use { socket ->
                val inetAddress = InetAddress.getByName(ipAddressStr)
                val packet = DatagramPacket(oscPacketBytes, oscPacketBytes.size, inetAddress, outgoingPort)
                socket.send(packet)
            }

            val pendingAction = OscThrottleManager.getPendingAndClear(throttleKey)
            if (pendingAction != null) {
                kotlinx.coroutines.delay(20)
                pendingAction()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

fun sendOscClusterRotation(context: Context, clusterId: Int, angle: Float) {
    val throttleKey = OscThrottleManager.clusterRotationKey(clusterId)

    if (!OscThrottleManager.shouldSend(throttleKey)) {
        OscThrottleManager.storePending(throttleKey) {
            sendOscClusterRotation(context, clusterId, angle)
        }
        return
    }

    CoroutineScope(Dispatchers.IO).launch {
        try {
            val (_, outgoingPortStr, ipAddressStr) = loadNetworkParameters(context)
            val outgoingPort = outgoingPortStr.toIntOrNull()

            if (outgoingPort == null || !isValidPort(outgoingPortStr)) {
                return@launch
            }
            if (ipAddressStr.isBlank() || !isValidIpAddress(ipAddressStr)) {
                return@launch
            }

            val addressPattern = "/cluster/rotation"
            val addressPatternBytes = getPaddedBytes(addressPattern)
            // Type tag for an integer (clusterId) followed by a float (angle)
            val typeTagBytes = getPaddedBytes(",if")
            val clusterIdBytes = clusterId.toBytesBigEndian()
            val angleBytes = angle.toBytesBigEndian()

            val oscPacketBytes = addressPatternBytes + typeTagBytes + clusterIdBytes + angleBytes

            DatagramSocket().use { socket ->
                val inetAddress = InetAddress.getByName(ipAddressStr)
                val packet = DatagramPacket(oscPacketBytes, oscPacketBytes.size, inetAddress, outgoingPort)
                socket.send(packet)
            }

            val pendingAction = OscThrottleManager.getPendingAndClear(throttleKey)
            if (pendingAction != null) {
                kotlinx.coroutines.delay(20)
                pendingAction()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

fun sendOscClusterScale(context: Context, clusterId: Int, factor: Float) {
    val throttleKey = OscThrottleManager.clusterScaleKey(clusterId)

    if (!OscThrottleManager.shouldSend(throttleKey)) {
        OscThrottleManager.storePending(throttleKey) {
            sendOscClusterScale(context, clusterId, factor)
        }
        return
    }

    CoroutineScope(Dispatchers.IO).launch {
        try {
            val (_, outgoingPortStr, ipAddressStr) = loadNetworkParameters(context)
            val outgoingPort = outgoingPortStr.toIntOrNull()

            if (outgoingPort == null || !isValidPort(outgoingPortStr)) {
                return@launch
            }
            if (ipAddressStr.isBlank() || !isValidIpAddress(ipAddressStr)) {
                return@launch
            }

            val addressPattern = "/cluster/scale"
            val addressPatternBytes = getPaddedBytes(addressPattern)
            // Type tag for an integer (clusterId) followed by a float (factor)
            val typeTagBytes = getPaddedBytes(",if")
            val clusterIdBytes = clusterId.toBytesBigEndian()
            val factorBytes = factor.toBytesBigEndian()

            val oscPacketBytes = addressPatternBytes + typeTagBytes + clusterIdBytes + factorBytes

            DatagramSocket().use { socket ->
                val inetAddress = InetAddress.getByName(ipAddressStr)
                val packet = DatagramPacket(oscPacketBytes, oscPacketBytes.size, inetAddress, outgoingPort)
                socket.send(packet)
            }

            val pendingAction = OscThrottleManager.getPendingAndClear(throttleKey)
            if (pendingAction != null) {
                kotlinx.coroutines.delay(20)
                pendingAction()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

fun sendOscMarkerAngleChange(context: Context, markerId: Int, modeNumber: Int, angleChange: Float) {
    android.util.Log.d("OSC", "sendOscMarkerAngleChange called: markerId=$markerId, modeNumber=$modeNumber, angleChange=$angleChange")
    val throttleKey = OscThrottleManager.markerAngleChangeKey(markerId, modeNumber)

    if (!OscThrottleManager.shouldSend(throttleKey)) {
        OscThrottleManager.storePending(throttleKey) {
            sendOscMarkerAngleChange(context, markerId, modeNumber, angleChange)
        }
        return
    }

    CoroutineScope(Dispatchers.IO).launch {
        try {
            val (_, outgoingPortStr, ipAddressStr) = loadNetworkParameters(context)
            val outgoingPort = outgoingPortStr.toIntOrNull()

            if (outgoingPort == null || !isValidPort(outgoingPortStr)) {
                return@launch
            }
            if (ipAddressStr.isBlank() || !isValidIpAddress(ipAddressStr)) {
                return@launch
            }

            val addressPattern = "/marker/angleChange"
            val addressPatternBytes = getPaddedBytes(addressPattern)
            // Type tag for two integers (markerId, modeNumber) followed by a float (angleChange)
            val typeTagBytes = getPaddedBytes(",iif")
            val markerIdBytes = markerId.toBytesBigEndian()
            val modeNumberBytes = modeNumber.toBytesBigEndian()
            val angleChangeBytes = angleChange.toBytesBigEndian()

            val oscPacketBytes = addressPatternBytes + typeTagBytes + markerIdBytes + modeNumberBytes + angleChangeBytes

            DatagramSocket().use { socket ->
                val inetAddress = InetAddress.getByName(ipAddressStr)
                val packet = DatagramPacket(oscPacketBytes, oscPacketBytes.size, inetAddress, outgoingPort)
                socket.send(packet)
            }

            val pendingAction = OscThrottleManager.getPendingAndClear(throttleKey)
            if (pendingAction != null) {
                kotlinx.coroutines.delay(20)
                pendingAction()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

fun sendOscMarkerRadialChange(context: Context, markerId: Int, modeNumber: Int, radialChange: Float) {
    android.util.Log.d("OSC", "sendOscMarkerRadialChange called: markerId=$markerId, modeNumber=$modeNumber, radialChange=$radialChange")
    val throttleKey = OscThrottleManager.markerRadialChangeKey(markerId, modeNumber)

    if (!OscThrottleManager.shouldSend(throttleKey)) {
        OscThrottleManager.storePending(throttleKey) {
            sendOscMarkerRadialChange(context, markerId, modeNumber, radialChange)
        }
        return
    }

    CoroutineScope(Dispatchers.IO).launch {
        try {
            val (_, outgoingPortStr, ipAddressStr) = loadNetworkParameters(context)
            val outgoingPort = outgoingPortStr.toIntOrNull()

            if (outgoingPort == null || !isValidPort(outgoingPortStr)) {
                return@launch
            }
            if (ipAddressStr.isBlank() || !isValidIpAddress(ipAddressStr)) {
                return@launch
            }

            val addressPattern = "/marker/radialChange"
            val addressPatternBytes = getPaddedBytes(addressPattern)
            // Type tag for two integers (markerId, modeNumber) followed by a float (radialChange)
            val typeTagBytes = getPaddedBytes(",iif")
            val markerIdBytes = markerId.toBytesBigEndian()
            val modeNumberBytes = modeNumber.toBytesBigEndian()
            val radialChangeBytes = radialChange.toBytesBigEndian()

            val oscPacketBytes = addressPatternBytes + typeTagBytes + markerIdBytes + modeNumberBytes + radialChangeBytes

            DatagramSocket().use { socket ->
                val inetAddress = InetAddress.getByName(ipAddressStr)
                val packet = DatagramPacket(oscPacketBytes, oscPacketBytes.size, inetAddress, outgoingPort)
                socket.send(packet)
            }

            val pendingAction = OscThrottleManager.getPendingAndClear(throttleKey)
            if (pendingAction != null) {
                kotlinx.coroutines.delay(20)
                pendingAction()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

/**
 * Send input parameter value via OSC
 * All parameters send inputId first, then the value
 */
fun sendOscInputParameterInt(context: Context, oscPath: String, inputId: Int, value: Int) {
    val throttleKey = OscThrottleManager.inputParameterKey(oscPath, inputId)

    if (!OscThrottleManager.shouldSend(throttleKey)) {
        OscThrottleManager.storePending(throttleKey) {
            sendOscInputParameterInt(context, oscPath, inputId, value)
        }
        return
    }

    CoroutineScope(Dispatchers.IO).launch {
        try {
            val (_, outgoingPortStr, ipAddressStr) = loadNetworkParameters(context)
            val outgoingPort = outgoingPortStr.toIntOrNull()

            if (outgoingPort == null || !isValidPort(outgoingPortStr)) {
                return@launch
            }
            if (ipAddressStr.isBlank() || !isValidIpAddress(ipAddressStr)) {
                return@launch
            }

            val addressPatternBytes = getPaddedBytes(oscPath)
            // Type tag for two integers (inputId, value)
            val typeTagBytes = getPaddedBytes(",ii")
            val inputIdBytes = inputId.toBytesBigEndian()
            val valueBytes = value.toBytesBigEndian()

            val oscPacketBytes = addressPatternBytes + typeTagBytes + inputIdBytes + valueBytes

            DatagramSocket().use { socket ->
                val inetAddress = InetAddress.getByName(ipAddressStr)
                val packet = DatagramPacket(oscPacketBytes, oscPacketBytes.size, inetAddress, outgoingPort)
                socket.send(packet)
            }

            val pendingAction = OscThrottleManager.getPendingAndClear(throttleKey)
            if (pendingAction != null) {
                kotlinx.coroutines.delay(20)
                pendingAction()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

fun sendOscInputParameterFloat(context: Context, oscPath: String, inputId: Int, value: Float) {
    val throttleKey = OscThrottleManager.inputParameterKey(oscPath, inputId)

    if (!OscThrottleManager.shouldSend(throttleKey)) {
        OscThrottleManager.storePending(throttleKey) {
            sendOscInputParameterFloat(context, oscPath, inputId, value)
        }
        return
    }

    CoroutineScope(Dispatchers.IO).launch {
        try {
            val (_, outgoingPortStr, ipAddressStr) = loadNetworkParameters(context)
            val outgoingPort = outgoingPortStr.toIntOrNull()

            if (outgoingPort == null || !isValidPort(outgoingPortStr)) {
                return@launch
            }
            if (ipAddressStr.isBlank() || !isValidIpAddress(ipAddressStr)) {
                return@launch
            }

            val addressPatternBytes = getPaddedBytes(oscPath)
            // Type tag for integer (inputId) and float (value)
            val typeTagBytes = getPaddedBytes(",if")
            val inputIdBytes = inputId.toBytesBigEndian()
            val valueBytes = value.toBytesBigEndian()

            val oscPacketBytes = addressPatternBytes + typeTagBytes + inputIdBytes + valueBytes

            DatagramSocket().use { socket ->
                val inetAddress = InetAddress.getByName(ipAddressStr)
                val packet = DatagramPacket(oscPacketBytes, oscPacketBytes.size, inetAddress, outgoingPort)
                socket.send(packet)
            }

            val pendingAction = OscThrottleManager.getPendingAndClear(throttleKey)
            if (pendingAction != null) {
                kotlinx.coroutines.delay(20)
                pendingAction()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

fun sendOscInputParameterString(context: Context, oscPath: String, inputId: Int, value: String) {
    val throttleKey = OscThrottleManager.inputParameterKey(oscPath, inputId)

    if (!OscThrottleManager.shouldSend(throttleKey)) {
        OscThrottleManager.storePending(throttleKey) {
            sendOscInputParameterString(context, oscPath, inputId, value)
        }
        return
    }

    CoroutineScope(Dispatchers.IO).launch {
        try {
            val (_, outgoingPortStr, ipAddressStr) = loadNetworkParameters(context)
            val outgoingPort = outgoingPortStr.toIntOrNull()

            if (outgoingPort == null || !isValidPort(outgoingPortStr)) {
                return@launch
            }
            if (ipAddressStr.isBlank() || !isValidIpAddress(ipAddressStr)) {
                return@launch
            }

            val addressPatternBytes = getPaddedBytes(oscPath)
            // Type tag for integer (inputId) and string (value)
            val typeTagBytes = getPaddedBytes(",is")
            val inputIdBytes = inputId.toBytesBigEndian()
            val valueBytes = getPaddedBytes(value)

            val oscPacketBytes = addressPatternBytes + typeTagBytes + inputIdBytes + valueBytes

            DatagramSocket().use { socket ->
                val inetAddress = InetAddress.getByName(ipAddressStr)
                val packet = DatagramPacket(oscPacketBytes, oscPacketBytes.size, inetAddress, outgoingPort)
                socket.send(packet)
            }

            val pendingAction = OscThrottleManager.getPendingAndClear(throttleKey)
            if (pendingAction != null) {
                kotlinx.coroutines.delay(20)
                pendingAction()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

/**
 * Request input parameter updates from the server
 * Sends only the inputId (outgoing only)
 */
fun sendOscRequestInputParameters(context: Context, inputId: Int) {
    val throttleKey = OscThrottleManager.requestInputParametersKey(inputId)

    if (!OscThrottleManager.shouldSend(throttleKey)) {
        OscThrottleManager.storePending(throttleKey) {
            sendOscRequestInputParameters(context, inputId)
        }
        return
    }

    CoroutineScope(Dispatchers.IO).launch {
        try {
            val (_, outgoingPortStr, ipAddressStr) = loadNetworkParameters(context)
            val outgoingPort = outgoingPortStr.toIntOrNull()

            if (outgoingPort == null || !isValidPort(outgoingPortStr)) {
                return@launch
            }
            if (ipAddressStr.isBlank() || !isValidIpAddress(ipAddressStr)) {
                return@launch
            }

            val addressPattern = "/remoteInput/inputNumber"
            val addressPatternBytes = getPaddedBytes(addressPattern)
            // Type tag for single integer (inputId)
            val typeTagBytes = getPaddedBytes(",i")
            val inputIdBytes = inputId.toBytesBigEndian()

            val oscPacketBytes = addressPatternBytes + typeTagBytes + inputIdBytes

            DatagramSocket().use { socket ->
                val inetAddress = InetAddress.getByName(ipAddressStr)
                val packet = DatagramPacket(oscPacketBytes, oscPacketBytes.size, inetAddress, outgoingPort)
                socket.send(packet)
            }

            val pendingAction = OscThrottleManager.getPendingAndClear(throttleKey)
            if (pendingAction != null) {
                kotlinx.coroutines.delay(20)
                pendingAction()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}


fun parseOscString(buffer: ByteBuffer): String {
    val bytes = mutableListOf<Byte>()
    while (buffer.hasRemaining()) {
        val byte = buffer.get()
        if (byte == 0.toByte()) break
        bytes.add(byte)
    }
    val currentPos = buffer.position()
    val align = (currentPos + 3) and -4
    if (align > currentPos) buffer.position(align)
    return String(bytes.toByteArray(), Charsets.UTF_8)
}

fun parseOscInt(buffer: ByteBuffer): Int {
    return buffer.int
}

fun parseOscFloat(buffer: ByteBuffer): Float {
    return buffer.float
}

typealias OscDataCallback = (id: Int, name: String?, position: Offset?, isCluster: Boolean) -> Unit
typealias OscStageDimensionCallback = (value: Float) -> Unit
typealias OscNumberOfInputsCallback = (count: Int) -> Unit
typealias OscClusterZCallback = (ClusterId: Int, normalizedZ: Float) -> Unit
typealias OscInputParameterIntCallback = (oscPath: String, inputId: Int, value: Int) -> Unit
typealias OscInputParameterFloatCallback = (oscPath: String, inputId: Int, value: Float) -> Unit
typealias OscInputParameterStringCallback = (oscPath: String, inputId: Int, value: String) -> Unit

fun parseAndProcessOscPacket(
    context: Context,
    data: ByteArray,
    canvasWidth: Float,
    canvasHeight: Float,
    onOscDataReceived: OscDataCallback,
    onStageWidthChanged: OscStageDimensionCallback? = null,
    onStageDepthChanged: OscStageDimensionCallback? = null,
    onStageHeightChanged: OscStageDimensionCallback? = null,
    onStageOriginXChanged: OscStageDimensionCallback? = null,
    onStageOriginYChanged: OscStageDimensionCallback? = null,
    onStageOriginZChanged: OscStageDimensionCallback? = null,
    onNumberOfInputsChanged: OscNumberOfInputsCallback? = null,
    onClusterZChanged: OscClusterZCallback? = null,
    onInputParameterIntReceived: OscInputParameterIntCallback? = null,
    onInputParameterFloatReceived: OscInputParameterFloatCallback? = null,
    onInputParameterStringReceived: OscInputParameterStringCallback? = null
) {
    if (data.isEmpty()) {
        return
    }
    val buffer = ByteBuffer.wrap(data)
    buffer.order(ByteOrder.BIG_ENDIAN)

    try {
        val address = parseOscString(buffer)

        when {
            address == "/findDevice" -> {
                if (!buffer.hasRemaining() || parseOscString(buffer) != ",s") {
                    return
                }
                val receivedPassword = parseOscString(buffer)
                val storedPassword = loadFindDevicePassword(context)

                // If password is set and matches, or if no password is set, launch the flash screen
                if (storedPassword.isEmpty() || receivedPassword == storedPassword) {
                    val intent = android.content.Intent(context, FlashScreenActivity::class.java)
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
            }
            address == "/inputs" -> {
                if (!buffer.hasRemaining() || parseOscString(buffer) != ",i") {
                    return
                }
                if (buffer.remaining() < 4) {
                    return
                }
                val count = parseOscInt(buffer)
                if (count >= 0 && count <= MAX_INPUTS) { // Allow 0 to effectively disable inputs
                    onNumberOfInputsChanged?.invoke(count)
                } else {
                }
            }
            address == "/stage/width" -> {
                if (!buffer.hasRemaining() || parseOscString(buffer) != ",f") {
                    return
                }
                if (buffer.remaining() < 4) {
                    return
                }
                val width = parseOscFloat(buffer)
                onStageWidthChanged?.invoke(width)
            }
            address == "/stage/depth" -> {
                if (!buffer.hasRemaining() || parseOscString(buffer) != ",f") {
                    return
                }
                if (buffer.remaining() < 4) {
                    return
                }
                val depth = parseOscFloat(buffer)
                onStageDepthChanged?.invoke(depth)
            }
            address == "/stage/height" -> {
                if (!buffer.hasRemaining() || parseOscString(buffer) != ",f") {

                    return
                }
                if (buffer.remaining() < 4) {

                    return
                }
                val height = parseOscFloat(buffer)

                onStageHeightChanged?.invoke(height)
            }
            address == "/stage/originX" -> {
                if (!buffer.hasRemaining() || parseOscString(buffer) != ",f") {
                    return
                }
                if (buffer.remaining() < 4) {
                    return
                }
                val originX = parseOscFloat(buffer)
                onStageOriginXChanged?.invoke(originX)
            }
            address == "/stage/originY" -> {
                if (!buffer.hasRemaining() || parseOscString(buffer) != ",f") {
                    return
                }
                if (buffer.remaining() < 4) {
                    return
                }
                val originY = parseOscFloat(buffer)
                onStageOriginYChanged?.invoke(originY)
            }
            address == "/stage/originZ" -> {
                if (!buffer.hasRemaining() || parseOscString(buffer) != ",f") {
                    return
                }
                if (buffer.remaining() < 4) {
                    return
                }
                val originZ = parseOscFloat(buffer)
                onStageOriginZChanged?.invoke(originZ)
            }
            address.startsWith("/marker/") || address.startsWith("/cluster/") -> {
                val isClusterMessage = address.startsWith("/cluster/")
                val baseAddress = if(isClusterMessage) address.removePrefix("/cluster/") else address.removePrefix("/marker/")

                when (baseAddress) {
                    "positionXY" -> {
                        if (!buffer.hasRemaining() || parseOscString(buffer) != ",iff") {

                            return
                        }
                        if (buffer.remaining() < 12) {

                            return
                        }
                        val id = parseOscInt(buffer)
                        val normX = parseOscFloat(buffer)
                        val normY = parseOscFloat(buffer)

                        if (canvasWidth > 0f && canvasHeight > 0f) {
                            // Map 0.0-1.0 range back to effective drawing area (canvas minus marker diameter)
                            val markerRadius = CanvasDimensions.getCurrentMarkerRadius()
                            val effectiveWidth = canvasWidth - (markerRadius * 2f)
                            val effectiveHeight = canvasHeight - (markerRadius * 2f)
                            val denormalizedX = (normX * effectiveWidth + markerRadius).coerceIn(markerRadius, canvasWidth - markerRadius)
                            val denormalizedY = ((1f - normY) * effectiveHeight + markerRadius).coerceIn(markerRadius, canvasHeight - markerRadius)
                            onOscDataReceived(id, null, Offset(denormalizedX, denormalizedY), isClusterMessage)
                        } else {

                        }
                    }
                    "name" -> {
                        if (isClusterMessage) {

                            return // ClusterMarkers don't have names
                        }
                        if (!buffer.hasRemaining()) {

                            return
                        }
                        val typeTags = parseOscString(buffer)


                        if (typeTags.startsWith(",i")) {
                            if (buffer.remaining() < 4) {

                                return
                            }
                            val id = parseOscInt(buffer)

                            if (typeTags == ",is") {
                                var newName = parseOscString(buffer)
                                newName = newName.take(24).trimEnd()

                                onOscDataReceived(id, newName, null, isClusterMessage)
                            } else if (typeTags == ",i") {

                                onOscDataReceived(id, "", null, isClusterMessage)
                            } else {

                            }
                        } else {

                        }
                    }
                    "positionZ" -> {
                        if (!isClusterMessage) {

                            return
                        }
                        // Expecting Type Tag: ",if" (integer for cluster ID, float for normalized Z)
                        if (!buffer.hasRemaining() || parseOscString(buffer) != ",if") {

                            return
                        }
                        if (buffer.remaining() < 8) { // 4 bytes for int (ID) + 4 bytes for float (Z)

                            return
                        }
                        val clusterId = parseOscInt(buffer)
                        val normalizedZ = parseOscFloat(buffer)

                        onClusterZChanged?.invoke(clusterId, normalizedZ) // <-- CALL THE NEW CALLBACK
                    }
                    else -> {

                    }
                }
            }
            address.startsWith("/remoteInput/") -> {
                // Handle input parameter messages
                val parameterName = address.removePrefix("/remoteInput/")
                
                if (!buffer.hasRemaining()) {
                    return
                }
                
                val typeTags = parseOscString(buffer)
                
                when {
                    typeTags == ",ii" -> {
                        // Integer parameter: inputId + int value
                        if (buffer.remaining() < 8) return
                        val inputId = parseOscInt(buffer)
                        val value = parseOscInt(buffer)
                        onInputParameterIntReceived?.invoke(address, inputId, value)
                    }
                    typeTags == ",if" -> {
                        // Float parameter: inputId + float value
                        if (buffer.remaining() < 8) return
                        val inputId = parseOscInt(buffer)
                        val value = parseOscFloat(buffer)
                        onInputParameterFloatReceived?.invoke(address, inputId, value)
                    }
                    typeTags == ",is" -> {
                        // String parameter: inputId + string value
                        if (buffer.remaining() < 4) return
                        val inputId = parseOscInt(buffer)
                        val value = parseOscString(buffer)
                        onInputParameterStringReceived?.invoke(address, inputId, value)
                    }
                    else -> {
                        // Unknown type tag
                    }
                }
            }
            else -> {

            }
        }
    } catch (e: Exception) {

        e.printStackTrace()
    }
}

fun startOscServer(
    context: Context,
    onOscDataReceived: OscDataCallback,
    onStageWidthChanged: OscStageDimensionCallback? = null,
    onStageDepthChanged: OscStageDimensionCallback? = null,
    onStageHeightChanged: OscStageDimensionCallback? = null,
    onStageOriginXChanged: OscStageDimensionCallback? = null,
    onStageOriginYChanged: OscStageDimensionCallback? = null,
    onStageOriginZChanged: OscStageDimensionCallback? = null,
    onNumberOfInputsChanged: OscNumberOfInputsCallback? = null,
    onClusterZChanged: OscClusterZCallback? = null,
    onInputParameterIntReceived: OscInputParameterIntCallback? = null,
    onInputParameterFloatReceived: OscInputParameterFloatCallback? = null,
    onInputParameterStringReceived: OscInputParameterStringCallback? = null
) {
    CoroutineScope(Dispatchers.IO).launch {
        var serverSocket: DatagramSocket? = null
        try {
            val (incomingPortStr, _, _) = loadNetworkParameters(context)
            val incomingPort = incomingPortStr.toIntOrNull()
            if (incomingPort == null || !isValidPort(incomingPortStr)) {

                return@launch
            }
            serverSocket = DatagramSocket(incomingPort)
            serverSocket.soTimeout = 1000 // Set 1 second timeout to prevent blocking




            val buffer = ByteArray(1024)
            while (isActive) {
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    serverSocket.receive(packet)
                    val receivedData = packet.data.copyOf(packet.length)
                    val remoteAddress = packet.address.hostAddress

                    val (canvasWidth, canvasHeight) = CanvasDimensions.getCurrentDimensions()
                    parseAndProcessOscPacket(
                        context,
                        receivedData,
                        canvasWidth,
                        canvasHeight,
                        onOscDataReceived,
                        onStageWidthChanged,
                        onStageDepthChanged,
                        onStageHeightChanged,
                        onStageOriginXChanged,
                        onStageOriginYChanged,
                        onStageOriginZChanged,
                        onNumberOfInputsChanged,
                        onClusterZChanged,
                        onInputParameterIntReceived,
                        onInputParameterFloatReceived,
                        onInputParameterStringReceived
                    )
                } catch (e: java.net.SocketTimeoutException) {

                    continue
                } catch (e: IOException) {

                    break
                } catch (e: Exception) {

                }
            }
        } catch (e: java.net.SocketException) {

        } catch (e: Exception) {

            e.printStackTrace()
        } finally {
            serverSocket?.close()

        }
    }
}

fun isValidPort(port: String): Boolean {
    val portNumber = port.toIntOrNull()
    return portNumber != null && portNumber in 1..65535
}

fun isValidIpAddress(ip: String): Boolean {
    val ipRegex = Regex("""^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$""")
    return ipRegex.matches(ip)
}
