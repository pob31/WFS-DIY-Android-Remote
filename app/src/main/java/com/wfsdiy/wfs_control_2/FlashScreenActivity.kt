package com.wfsdiy.wfs_control_2

import android.content.Context
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class FlashScreenActivity : ComponentActivity() {

    private var cameraManager: CameraManager? = null
    private var cameraId: String? = null
    private var vibrator: Vibrator? = null
    private var toneGenerator: ToneGenerator? = null
    private var isFlashOn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Make the activity work over the lock screen and turn on the screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }

        // Keep screen on during the flash sequence
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Hide system UI for full screen effect
        hideSystemUI()

        // Initialize hardware components
        initializeHardware()

        setContent {
            FlashScreen(
                onDismiss = { finish() }
            )
        }
    }

    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
        }
    }

    private fun initializeHardware() {
        // Initialize camera for flashlight
        try {
            cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cameraId = cameraManager?.cameraIdList?.firstOrNull { id ->
                cameraManager?.getCameraCharacteristics(id)
                    ?.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Initialize vibrator
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        // Initialize tone generator for sound
        try {
            toneGenerator = ToneGenerator(
                android.media.AudioManager.STREAM_ALARM,
                ToneGenerator.MAX_VOLUME
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setFlashlight(enabled: Boolean) {
        try {
            cameraId?.let { id ->
                cameraManager?.setTorchMode(id, enabled)
                isFlashOn = enabled
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun vibrate() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(200)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun playBeep() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 200)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Composable
    fun FlashScreen(onDismiss: () -> Unit) {
        var flashCount by remember { mutableStateOf(0) }
        val maxFlashes = 10
        val flashDuration = 500L // milliseconds

        // Animate the flash
        val infiniteTransition = rememberInfiniteTransition(label = "flash")
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(flashDuration.toInt(), easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "flashAlpha"
        )

        val scope = rememberCoroutineScope()

        // Start the flash sequence
        LaunchedEffect(Unit) {
            scope.launch {
                repeat(maxFlashes) { count ->
                    flashCount = count + 1

                    // Turn on flashlight
                    setFlashlight(true)
                    vibrate()
                    playBeep()

                    delay(flashDuration)

                    // Turn off flashlight
                    setFlashlight(false)

                    delay(flashDuration)
                }

                // Auto-dismiss after all flashes
                delay(500)
                onDismiss()
            }
        }

        // Clickable white screen
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = alpha))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    onDismiss()
                }
        )
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event?.action == MotionEvent.ACTION_DOWN) {
            finish()
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Dismiss on power button or back button
        if (keyCode == KeyEvent.KEYCODE_POWER || keyCode == KeyEvent.KEYCODE_BACK) {
            finish()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()

        // Clean up resources
        if (isFlashOn) {
            setFlashlight(false)
        }
        toneGenerator?.release()
        toneGenerator = null
    }
}
