package com.wfsdiy.wfs_control_2

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wfsdiy.wfs_control_2.localization.loc
import com.wfsdiy.wfs_control_2.localization.locStatic
import java.net.InetAddress
import java.net.NetworkInterface

// Function to get the current device IP address
fun getCurrentIpAddress(): String {
    try {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        while (interfaces.hasMoreElements()) {
            val networkInterface = interfaces.nextElement()
            val addresses = networkInterface.inetAddresses
            while (addresses.hasMoreElements()) {
                val address = addresses.nextElement()
                if (!address.isLoopbackAddress && address.isSiteLocalAddress) {
                    return address.hostAddress ?: "Unknown"
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return "Unknown"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkTab(
    onNetworkParametersChanged: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var incomingPort by remember { mutableStateOf("") }
    var outgoingPort by remember { mutableStateOf("") }
    var ipAddress by remember { mutableStateOf("") }
    var findDevicePassword by remember { mutableStateOf("") }
    var currentIpAddress by remember { mutableStateOf("Loading...") }

    // Track original values to detect changes
    var originalIncomingPort by remember { mutableStateOf("") }
    var originalOutgoingPort by remember { mutableStateOf("") }
    var originalIpAddress by remember { mutableStateOf("") }
    var originalFindDevicePassword by remember { mutableStateOf("") }

    var incomingPortError by remember { mutableStateOf(false) }
    var outgoingPortError by remember { mutableStateOf(false) }
    var ipAddressError by remember { mutableStateOf(false) }

    // Check if any values have changed
    val hasChanges = incomingPort != originalIncomingPort ||
                     outgoingPort != originalOutgoingPort ||
                     ipAddress != originalIpAddress ||
                     findDevicePassword != originalFindDevicePassword

    LaunchedEffect(Unit) {
        val (loadedIncoming, loadedOutgoing, loadedIp) = loadNetworkParameters(context)
        val loadedPassword = loadFindDevicePassword(context)

        incomingPort = loadedIncoming
        outgoingPort = loadedOutgoing
        ipAddress = loadedIp
        findDevicePassword = loadedPassword

        // Store original values for change detection
        originalIncomingPort = loadedIncoming
        originalOutgoingPort = loadedOutgoing
        originalIpAddress = loadedIp
        originalFindDevicePassword = loadedPassword

        incomingPortError = !isValidPort(loadedIncoming)
        outgoingPortError = !isValidPort(loadedOutgoing)
        ipAddressError = !isValidIpAddress(loadedIp)

        // Get current device IP address
        currentIpAddress = getCurrentIpAddress()
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null // No ripple effect
        ) {
            // When tapping outside, revert to original values before clearing focus
            incomingPort = originalIncomingPort
            outgoingPort = originalOutgoingPort
            ipAddress = originalIpAddress
            findDevicePassword = originalFindDevicePassword
            incomingPortError = !isValidPort(originalIncomingPort)
            outgoingPortError = !isValidPort(originalOutgoingPort)
            ipAddressError = !isValidIpAddress(originalIpAddress)
            focusManager.clearFocus()
        }
    ) {
        Text(
            loc("remote.settings.networkConfig"),
            style = MaterialTheme.typography.headlineSmall.copy(color = Color.White),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        val textFieldColors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = Color.White,
            focusedBorderColor = Color.White,
            unfocusedBorderColor = Color.LightGray,
            errorBorderColor = MaterialTheme.colorScheme.error,
            errorCursorColor = MaterialTheme.colorScheme.error,
            errorLabelColor = MaterialTheme.colorScheme.error,
            errorSupportingTextColor = MaterialTheme.colorScheme.error,
            focusedLabelColor = Color.White,
            unfocusedLabelColor = Color.LightGray,
            disabledTextColor = Color.White,
            disabledBorderColor = Color.Gray,
            disabledLabelColor = Color.LightGray
        )
        val textStyle = TextStyle(color = Color.White, fontSize = 18.sp)

        // Network parameters in a row for side-by-side layout (including current device IP)
        Row(
            modifier = Modifier.fillMaxWidth(0.8f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Current Device IP (read-only)
            OutlinedTextField(
                value = currentIpAddress,
                onValueChange = { }, // No-op since it's read-only
                label = { Text(loc("remote.settings.deviceIp")) },
                textStyle = textStyle,
                colors = textFieldColors,
                readOnly = true,
                enabled = false,
                modifier = Modifier.weight(1f)
            )

            OutlinedTextField(
                value = incomingPort,
                onValueChange = {
                    incomingPort = it
                    incomingPortError = !isValidPort(it)
                },
                label = { Text(loc("remote.settings.incomingPort")) },
                textStyle = textStyle,
                colors = textFieldColors,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() }
                ),
                modifier = Modifier.weight(1f),
                isError = incomingPortError,
                supportingText = {
                    if (incomingPortError) Text(loc("common.invalid"), fontSize = 10.sp)
                }
            )

            OutlinedTextField(
                value = outgoingPort,
                onValueChange = {
                    outgoingPort = it
                    outgoingPortError = !isValidPort(it)
                },
                label = { Text(loc("remote.settings.outgoingPort")) },
                textStyle = textStyle,
                colors = textFieldColors,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() }
                ),
                modifier = Modifier.weight(1f),
                isError = outgoingPortError,
                supportingText = {
                    if (outgoingPortError) Text(loc("common.invalid"), fontSize = 10.sp)
                }
            )

            OutlinedTextField(
                value = ipAddress,
                onValueChange = {
                    ipAddress = it
                    ipAddressError = !isValidIpAddress(it)
                },
                label = { Text(loc("remote.settings.ipAddress")) },
                textStyle = textStyle,
                colors = textFieldColors,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() }
                ),
                modifier = Modifier.weight(1f),
                isError = ipAddressError,
                supportingText = {
                    if (ipAddressError) Text(loc("common.invalid"), fontSize = 10.sp)
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = findDevicePassword,
            onValueChange = { findDevicePassword = it },
            label = { Text(loc("remote.settings.findDevicePassword")) },
            textStyle = textStyle,
            colors = textFieldColors,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { focusManager.clearFocus() }
            ),
            modifier = Modifier.fillMaxWidth(0.8f),
            supportingText = {
                Text(loc("remote.settings.findDevicePasswordHint"))
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Show warning if there are unsaved changes
        if (hasChanges) {
            Text(
                "⚠ ${loc("common.unsavedChanges")}",
                style = TextStyle(
                    color = Color(0xFFFFAA00),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // Calculate button color based on whether there are changes
        val networkButtonColor = if (hasChanges) {
            Color(0xFFFF6B35) // Orange-red for pending changes
        } else {
            // Original Input Map marker 23 color
            val hue = (23 * 360f / 32) % 360f
            Color.hsl(hue, 0.9f, 0.6f)
        }

        Button(
            onClick = {
                val isIncomingPortValid = isValidPort(incomingPort)
                val isOutgoingPortValid = isValidPort(outgoingPort)
                val isIpAddressValid = isValidIpAddress(ipAddress)

                incomingPortError = !isIncomingPortValid
                outgoingPortError = !isOutgoingPortValid
                ipAddressError = !isIpAddressValid

                if (isIncomingPortValid && isOutgoingPortValid && isIpAddressValid) {
                    saveNetworkParameters(context, incomingPort, outgoingPort, ipAddress)
                    saveFindDevicePassword(context, findDevicePassword)

                    // Update original values after successful save
                    originalIncomingPort = incomingPort
                    originalOutgoingPort = outgoingPort
                    originalIpAddress = ipAddress
                    originalFindDevicePassword = findDevicePassword

                    onNetworkParametersChanged?.invoke()
                    Toast.makeText(context, locStatic("remote.settings.networkSettingsSaved"), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, locStatic("remote.settings.networkFieldsError"), Toast.LENGTH_LONG).show()
                }
            },
            modifier = Modifier.fillMaxWidth(0.8f),
            colors = ButtonDefaults.buttonColors(
                containerColor = networkButtonColor,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(4.dp)
        ) {
            Text(if (hasChanges) loc("remote.settings.applyChanges") else loc("remote.settings.applyNetworkSettings"))
        }
    }
}
