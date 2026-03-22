package com.wfsdiy.wfs_control_2

import android.view.MotionEvent
import androidx.compose.foundation.background
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
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

            Spacer(modifier = Modifier.height(24.dp))

            // Pressure Calibration
            PressureCalibrationSection()

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

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun PressureCalibrationSection() {
    val context = LocalContext.current
    var isCalibrating by remember { mutableStateOf(false) }
    var calMin by remember { mutableFloatStateOf(0.1f) }
    var calMax by remember { mutableFloatStateOf(5.0f) }
    var livePressure by remember { mutableFloatStateOf(0f) }
    var liveTouchMajor by remember { mutableFloatStateOf(0f) }
    var sessionMin by remember { mutableFloatStateOf(Float.MAX_VALUE) }
    var sessionMax by remember { mutableFloatStateOf(0f) }

    // Load saved calibration
    LaunchedEffect(Unit) {
        val (savedMin, savedMax) = loadPressureCalibration(context)
        calMin = savedMin
        calMax = savedMax
    }

    Column(
        modifier = Modifier.fillMaxWidth(0.8f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = loc("remote.settings.pressureCalibration"),
            style = MaterialTheme.typography.titleSmall,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (isCalibrating) {
            // Calibration touch area
            Text(
                text = loc("remote.settings.pressureCalibrationInstruction"),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Touch area with pressure bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(Color(0xFF333333), RoundedCornerShape(8.dp))
                    .pointerInteropFilter { event ->
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                                val tm = event.touchMajor
                                liveTouchMajor = tm
                                if (tm > 0f) {
                                    if (tm < sessionMin) sessionMin = tm
                                    if (tm > sessionMax) sessionMax = tm
                                    val range = (sessionMax - sessionMin).coerceAtLeast(0.1f)
                                    livePressure = ((tm - sessionMin) / range).coerceIn(0f, 1f)
                                }
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                livePressure = 0f
                            }
                        }
                        true
                    },
                contentAlignment = Alignment.Center
            ) {
                // Pressure bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(24.dp)
                        .background(Color(0xFF222222), RoundedCornerShape(4.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(livePressure)
                            .fillMaxHeight()
                            .background(
                                Color(
                                    red = livePressure,
                                    green = 0.6f * (1f - livePressure),
                                    blue = 0.2f
                                ),
                                RoundedCornerShape(4.dp)
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "touchMajor: %.2f  range: %.2f – %.2f".format(
                    liveTouchMajor, sessionMin.takeIf { it < Float.MAX_VALUE } ?: 0f, sessionMax
                ),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        if (sessionMin < Float.MAX_VALUE && sessionMax > sessionMin) {
                            calMin = sessionMin
                            calMax = sessionMax
                            savePressureCalibration(context, calMin, calMax)
                        }
                        isCalibrating = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF338C33)),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(loc("remote.settings.save"))
                }

                Button(
                    onClick = { isCalibrating = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF555555)),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(loc("remote.settings.cancel"))
                }
            }
        } else {
            // Show current calibration and Calibrate/Reset buttons
            Text(
                text = "Min: %.2f  Max: %.2f".format(calMin, calMax),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        sessionMin = Float.MAX_VALUE
                        sessionMax = 0f
                        livePressure = 0f
                        isCalibrating = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A90D9)),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(loc("remote.settings.calibrate"))
                }

                Button(
                    onClick = {
                        calMin = 0.1f
                        calMax = 5.0f
                        savePressureCalibration(context, calMin, calMax)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF555555)),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(loc("remote.settings.reset"))
                }
            }
        }
    }
}
