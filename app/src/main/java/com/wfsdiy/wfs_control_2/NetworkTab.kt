package com.wfsdiy.wfs_control_2

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.widget.Toast
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

    var incomingPortError by remember { mutableStateOf(false) }
    var outgoingPortError by remember { mutableStateOf(false) }
    var ipAddressError by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val (loadedIncoming, loadedOutgoing, loadedIp) = loadNetworkParameters(context)
        incomingPort = loadedIncoming
        outgoingPort = loadedOutgoing
        ipAddress = loadedIp
        findDevicePassword = loadFindDevicePassword(context)
        incomingPortError = !isValidPort(loadedIncoming)
        outgoingPortError = !isValidPort(loadedOutgoing)
        ipAddressError = !isValidIpAddress(loadedIp)

        // Get current device IP address
        currentIpAddress = getCurrentIpAddress()
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Network Configuration",
            style = MaterialTheme.typography.headlineSmall.copy(color = Color.White),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Current Device IP Address Display
        Row(
            modifier = Modifier.fillMaxWidth(0.8f),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Current Device IP:",
                style = TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            )
            Text(
                currentIpAddress,
                style = TextStyle(color = Color.Cyan, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

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
            unfocusedLabelColor = Color.LightGray
        )
        val textStyle = TextStyle(color = Color.White, fontSize = 18.sp)

        OutlinedTextField(
            value = incomingPort,
            onValueChange = {
                incomingPort = it
                incomingPortError = !isValidPort(it)
            },
            label = { Text("Incoming Port") },
            textStyle = textStyle,
            colors = textFieldColors,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onDone = { focusManager.clearFocus() }
            ),
            modifier = Modifier.fillMaxWidth(0.8f),
            isError = incomingPortError,
            supportingText = {
                if (incomingPortError) Text("Port must be a number from 1 to 65535")
            }
        )

        OutlinedTextField(
            value = outgoingPort,
            onValueChange = {
                outgoingPort = it
                outgoingPortError = !isValidPort(it)
            },
            label = { Text("Outgoing Port") },
            textStyle = textStyle,
            colors = textFieldColors,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onDone = { focusManager.clearFocus() }
            ),
            modifier = Modifier.fillMaxWidth(0.8f),
            isError = outgoingPortError,
            supportingText = {
                if (outgoingPortError) Text("Port must be a number from 1 to 65535")
            }
        )

        OutlinedTextField(
            value = ipAddress,
            onValueChange = {
                ipAddress = it
                ipAddressError = !isValidIpAddress(it)
            },
            label = { Text("IP Address (IPv4)") },
            textStyle = textStyle,
            colors = textFieldColors,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onDone = { focusManager.clearFocus() }
            ),
            modifier = Modifier.fillMaxWidth(0.8f),
            isError = ipAddressError,
            supportingText = {
                if (ipAddressError) Text("Invalid IPv4 address format (e.g., 192.168.1.100)")
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = findDevicePassword,
            onValueChange = { findDevicePassword = it },
            label = { Text("Find Device Password (Optional)") },
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
                Text("Password for /findDevice OSC command. Leave empty to disable.")
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Calculate Input Map marker 23 color for the button
        val networkButtonColor = run {
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
                    onNetworkParametersChanged?.invoke()
                    Toast.makeText(context, "Network settings saved", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Please correct the errors in the network fields", Toast.LENGTH_LONG).show()
                }
            },
            modifier = Modifier.fillMaxWidth(0.8f),
            colors = ButtonDefaults.buttonColors(
                containerColor = networkButtonColor,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(4.dp)
        ) {
            Text("Apply Network Settings")
        }
    }
}
