package com.wfsdiy.wfs_control_2

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

import com.wfsdiy.wfs_control_2.localization.LocalizationManager
import com.wfsdiy.wfs_control_2.localization.loc

@Composable
fun SettingsTab(
    onResetToDefaults: () -> Unit,
    onShutdownApp: () -> Unit,
    onNetworkParametersChanged: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var showResetDialog by remember { mutableStateOf(false) }
    var showShutdownDialog by remember { mutableStateOf(false) }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(loc("remote.settings.confirmReset")) },
            text = { Text(loc("remote.settings.confirmResetMessage")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onResetToDefaults()
                        showResetDialog = false
                    }
                ) {
                    Text(loc("common.reset"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(loc("common.cancel"))
                }
            }
        )
    }

    if (showShutdownDialog) {
        AlertDialog(
            onDismissRequest = { showShutdownDialog = false },
            title = { Text(loc("remote.settings.confirmShutdown")) },
            text = { Text(loc("remote.settings.confirmShutdownMessage")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onShutdownApp()
                        showShutdownDialog = false
                    }
                ) {
                    Text(loc("remote.settings.shutdown"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showShutdownDialog = false }) {
                    Text(loc("common.cancel"))
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Language selector
            val langs = LocalizationManager.availableLanguages
            val currentLang by LocalizationManager.currentLanguage.collectAsState()
            val currentIdx = langs.indexOfFirst { it.first == currentLang }.coerceAtLeast(0)

            Row(
                modifier = Modifier.fillMaxWidth(0.8f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    loc("remote.settings.language"),
                    color = Color.White,
                    modifier = Modifier.padding(end = 8.dp)
                )
                ParameterDropdown(
                    label = "",
                    selectedIndex = currentIdx,
                    options = langs.map { it.second },
                    onSelectionChange = { idx ->
                        val code = langs[idx].first
                        LocalizationManager.loadLanguage(context, code)
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // NetworkTab content
            NetworkTab(onNetworkParametersChanged = onNetworkParametersChanged)

            Spacer(modifier = Modifier.height(32.dp))

            // Reset and Shutdown buttons side by side
            Row(
                modifier = Modifier.fillMaxWidth(0.8f),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Calculate Input Map marker 19 color for Reset button
                val resetButtonColor = run {
                    val hue = (19 * 360f / 32) % 360f
                    Color.hsl(hue, 0.9f, 0.6f)
                }

                Button(
                    onClick = { showResetDialog = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = resetButtonColor,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(loc("remote.settings.resetAppSettings"))
                }

                Button(
                    onClick = { showShutdownDialog = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Red,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(loc("remote.settings.shutdownApplication"))
                }
            }
        }
    }
}
