/*
 * Copyright (C) 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nothing.assistkey

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.os.UserHandle
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import java.util.concurrent.Executors

class ActionExecutor(private val context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val cameraManager = context.getSystemService(CameraManager::class.java)
    private val keyguardManager = context.getSystemService(KeyguardManager::class.java)
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val powerManager = context.getSystemService(PowerManager::class.java)
    private val vibrator = context.getSystemService(Vibrator::class.java)

    private val packageContext: Context = try {
        val pkgCtx = context.createPackageContext("com.nothing.assistkey", Context.CONTEXT_IGNORE_SECURITY)
        if (pkgCtx.isDeviceProtectedStorage) pkgCtx else pkgCtx.createDeviceProtectedStorageContext()
    } catch (_: Exception) {
        if (context.isDeviceProtectedStorage) context else context.createDeviceProtectedStorageContext()
    }

    private val executorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var rearCameraId: String? = null
    private var isTorchOn = false

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == rearCameraId) {
                isTorchOn = enabled
            }
        }

        override fun onTorchModeUnavailable(cameraId: String) {
            if (cameraId == rearCameraId) {
                isTorchOn = false
            }
        }
    }

    init {
        try {
            cameraManager?.let { manager ->
                getOrFindRearCameraId()
                manager.registerTorchCallback(torchCallback, mainHandler)
            }
        } catch (e: Exception) {
            Log.e(Constants.TAG, "Failed to initialize CameraManager for torch", e)
        }
    }

    private fun getOrFindRearCameraId(): String? {
        if (rearCameraId != null) return rearCameraId
        try {
            cameraManager?.let { manager ->
                rearCameraId = manager.cameraIdList.firstOrNull { id ->
                    val characteristics = manager.getCameraCharacteristics(id)
                    val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                    val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                    facing == CameraCharacteristics.LENS_FACING_BACK && hasFlash
                } ?: manager.cameraIdList.firstOrNull { id ->
                    manager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                }
            }
        } catch (e: Exception) {
            Log.e(Constants.TAG, "Failed to find rear camera ID", e)
        }
        return rearCameraId
    }

    fun vibrate() {
        val hapticEnabled = try {
            Settings.System.getInt(context.contentResolver, Constants.PREF_HAPTIC_FEEDBACK) == 1
        } catch (_: Settings.SettingNotFoundException) {
            val sp = packageContext.getSharedPreferences(
                Constants.SHARED_PREFERENCES_NAME,
                Context.MODE_PRIVATE
            )
            sp.getBoolean(Constants.PREF_HAPTIC_FEEDBACK, true)
        } catch (_: Exception) {
            true
        }
        if (!hapticEnabled) return

        try {
            vibrator?.let {
                val effect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                val attrs = VibrationAttributes.createForUsage(VibrationAttributes.USAGE_HARDWARE_FEEDBACK)
                it.vibrate(effect, attrs)
            }
        } catch (e: Exception) {
            Log.w(Constants.TAG, "Vibration failed", e)
        }
    }

    fun execute(action: Int, customAppPackage: String? = null) {
        if (action == Constants.ACTION_NONE) return

        vibrate()

        executorService.submit {
            try {
                when (action) {
                    Constants.ACTION_VOICE_ASSISTANT -> launchVoiceAssistant()
                    Constants.ACTION_TORCH -> toggleTorch()
                    Constants.ACTION_CAMERA -> launchCamera()
                    Constants.ACTION_SCREENSHOT -> takeScreenshot()
                    Constants.ACTION_RINGER_MODE -> cycleRingerMode()
                    Constants.ACTION_DND -> toggleDoNotDisturb()
                    Constants.ACTION_VOICE_RECORDER -> launchVoiceRecorder()
                    Constants.ACTION_MEDIA_PLAY_PAUSE -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                    Constants.ACTION_MEDIA_NEXT -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
                    Constants.ACTION_MEDIA_PREV -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                    Constants.ACTION_SCREEN_OFF -> goToSleep()
                    Constants.ACTION_LAUNCH_APP -> launchCustomApp(customAppPackage)
                    Constants.ACTION_AUTO_ROTATE -> toggleAutoRotate()
                    else -> Log.w(Constants.TAG, "Unknown action: $action")
                }
            } catch (e: Exception) {
                Log.e(Constants.TAG, "Error executing action $action", e)
            }
        }
    }

    private fun wakeUpIfNeeded() {
        powerManager?.let { pm ->
            if (!pm.isInteractive) {
                val wakeLock = pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "${Constants.TAG}:WakeLock"
                )
                wakeLock.acquire(Constants.WAKELOCK_DURATION_MS)
                pm.wakeUp(SystemClock.uptimeMillis(), PowerManager.WAKE_REASON_GESTURE, "AssistKey:Wake")
            }
        }
    }

    private fun launchVoiceAssistant() {
        wakeUpIfNeeded()
        val intent = Intent(Intent.ACTION_VOICE_ASSIST).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        if (context.packageManager.resolveActivity(intent, 0) == null) {
            intent.action = Intent.ACTION_VOICE_COMMAND
        }
        startActivitySafely(intent)
    }

    private fun toggleTorch() {
        val camId = getOrFindRearCameraId() ?: return
        try {
            cameraManager?.setTorchMode(camId, !isTorchOn)
        } catch (e: Exception) {
            Log.e(Constants.TAG, "Failed to toggle torch", e)
        }
    }

    private fun launchCamera() {
        wakeUpIfNeeded()
        val isLocked = keyguardManager?.isKeyguardLocked == true
        val action = if (isLocked) {
            MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE
        } else {
            MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA
        }
        val intent = Intent(action).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivitySafely(intent)
    }

    private fun takeScreenshot() {
        wakeUpIfNeeded()
        try {
            val helperClass = Class.forName("com.android.internal.util.ScreenshotHelper")
            val constructor = helperClass.getConstructor(Context::class.java)
            val helper = constructor.newInstance(context)
            try {
                val method = helperClass.getMethod(
                    "takeScreenshot",
                    Int::class.javaPrimitiveType,
                    Handler::class.java,
                    java.util.function.Consumer::class.java
                )
                // WindowManager.ScreenshotSource.SCREENSHOT_KEY_CHORD = 1
                method.invoke(helper, 1, mainHandler, null)
                return
            } catch (_: NoSuchMethodException) {
                val method = helperClass.getMethod(
                    "takeScreenshot",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Handler::class.java,
                    java.util.function.Consumer::class.java
                )
                // TAKE_SCREENSHOT_FULLSCREEN = 1, source = 1
                method.invoke(helper, 1, 1, mainHandler, null)
                return
            }
        } catch (_: Exception) {}

        try {
            val intent = Intent("com.android.internal.intent.action.TAKE_SCREENSHOT")
            context.sendBroadcastAsUser(intent, UserHandle.CURRENT)
        } catch (e: Exception) {
            Log.e(Constants.TAG, "Failed to take screenshot", e)
        }
    }

    private fun cycleRingerMode() {
        audioManager?.let { am ->
            try {
                val newMode = when (am.ringerModeInternal) {
                    AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
                    AudioManager.RINGER_MODE_VIBRATE -> AudioManager.RINGER_MODE_SILENT
                    AudioManager.RINGER_MODE_SILENT -> AudioManager.RINGER_MODE_NORMAL
                    else -> AudioManager.RINGER_MODE_NORMAL
                }
                am.ringerModeInternal = newMode
            } catch (_: Exception) {
                try {
                    val newMode = when (am.ringerMode) {
                        AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
                        AudioManager.RINGER_MODE_VIBRATE -> AudioManager.RINGER_MODE_SILENT
                        AudioManager.RINGER_MODE_SILENT -> AudioManager.RINGER_MODE_NORMAL
                        else -> AudioManager.RINGER_MODE_NORMAL
                    }
                    am.ringerMode = newMode
                } catch (e: Exception) {
                    Log.e(Constants.TAG, "Failed to set ringer mode", e)
                }
            }
        }
    }

    private fun toggleDoNotDisturb() {
        notificationManager?.let { nm ->
            val currentMode = nm.zenMode
            val newMode = if (currentMode == Settings.Global.ZEN_MODE_OFF) {
                Settings.Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS
            } else {
                Settings.Global.ZEN_MODE_OFF
            }
            nm.setZenMode(newMode, null, Constants.TAG)
        }
    }

    private fun launchVoiceRecorder() {
        wakeUpIfNeeded()
        val pm = context.packageManager
        val recorderPackages = listOf(
            "org.lineageos.recorder",
            "com.nothing.soundrecorder",
            "com.android.soundrecorder",
            "com.google.android.apps.recorder"
        )

        for (pkg in recorderPackages) {
            val intent = pm.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                startActivitySafely(intent)
                return
            }
        }

        val genericIntent = Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivitySafely(genericIntent)
    }

    private fun sendMediaKey(keyCode: Int) {
        audioManager?.let { am ->
            val downTime = SystemClock.uptimeMillis()
            am.dispatchMediaKeyEvent(KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, keyCode, 0))
            am.dispatchMediaKeyEvent(KeyEvent(downTime, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, keyCode, 0))
        }
    }

    private fun goToSleep() {
        powerManager?.let { pm ->
            if (pm.isInteractive) {
                pm.goToSleep(SystemClock.uptimeMillis())
            }
        }
    }

    private fun launchCustomApp(packageName: String?) {
        if (packageName.isNullOrBlank()) return
        wakeUpIfNeeded()
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivitySafely(intent)
        } else {
            Log.w(Constants.TAG, "Could not find launch intent for $packageName")
        }
    }

    private fun toggleAutoRotate() {
        val resolver = context.contentResolver
        val current = Settings.System.getInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 0)
        Settings.System.putInt(resolver, Settings.System.ACCELEROMETER_ROTATION, if (current == 0) 1 else 0)
    }

    private fun startActivitySafely(intent: Intent) {
        try {
            context.startActivityAsUser(intent, UserHandle.CURRENT)
        } catch (e: ActivityNotFoundException) {
            Log.w(Constants.TAG, "Activity not found for intent: $intent")
        } catch (e: Exception) {
            Log.e(Constants.TAG, "Failed to start activity", e)
        }
    }
}
