/*
 * Copyright (C) 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nothing.assistkey

import android.content.Context
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import com.android.internal.os.DeviceKeyHandler

class KeyHandler(private val context: Context) : DeviceKeyHandler {
    private val actionExecutor = ActionExecutor(context)
    private val powerManager = context.getSystemService(PowerManager::class.java)
    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)

    private val mainHandler = Handler(Looper.getMainLooper())

    private val packageContext: Context = try {
        val pkgCtx = context.createPackageContext("com.nothing.assistkey", Context.CONTEXT_IGNORE_SECURITY)
        if (pkgCtx.isDeviceProtectedStorage) pkgCtx else pkgCtx.createDeviceProtectedStorageContext()
    } catch (_: Exception) {
        if (context.isDeviceProtectedStorage) context else context.createDeviceProtectedStorageContext()
    }

    private val sharedPreferences: SharedPreferences
        get() = packageContext.getSharedPreferences(
            Constants.SHARED_PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )

    private var isLongPressTriggered = false
    private var pendingSinglePressRunnable: Runnable? = null
    private var longPressRunnable: Runnable? = null

    private var isPocketNear = false
    private var proximityWakeLock: PowerManager.WakeLock? = null
    private var proximityListener: SensorEventListener? = null
    private var proximityTimeoutRunnable: Runnable? = null

    private fun getPrefBoolean(key: String, default: Boolean): Boolean {
        return try {
            Settings.System.getInt(context.contentResolver, key) == 1
        } catch (_: Settings.SettingNotFoundException) {
            sharedPreferences.getBoolean(key, default)
        } catch (_: Exception) {
            default
        }
    }

    private fun getPrefInt(key: String, default: Int): Int {
        return try {
            Settings.System.getInt(context.contentResolver, key)
        } catch (_: Settings.SettingNotFoundException) {
            sharedPreferences.getString(key, default.toString())?.toIntOrNull()
                ?: sharedPreferences.getInt(key, default)
        } catch (_: Exception) {
            default
        }
    }

    private fun getPrefString(key: String, default: String? = null): String? {
        return try {
            Settings.System.getString(context.contentResolver, key) ?: sharedPreferences.getString(key, default)
        } catch (_: Exception) {
            sharedPreferences.getString(key, default)
        }
    }

    override fun handleKeyEvent(event: KeyEvent): KeyEvent? {
        if (!isEssentialKey(event)) {
            return event
        }

        val enabled = getPrefBoolean(Constants.PREF_KEY_ENABLED, true)
        if (!enabled) {
            return event
        }

        val isInteractive = powerManager?.isInteractive == true
        val allowScreenOff = getPrefBoolean(Constants.PREF_SCREEN_OFF_ALLOWED, true)
        if (!isInteractive && !allowScreenOff) {
            return null
        }

        val mistouchPrevention = getPrefBoolean(Constants.PREF_MISTOUCH_PREVENTION, true)

        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount == 0) {
                    if (!isInteractive && mistouchPrevention) {
                        startProximityCheck()
                    }
                    onKeyDown()
                }
            }
            KeyEvent.ACTION_UP -> {
                onKeyUp()
            }
        }

        // Consume the key event so OS doesn't launch default assistant
        return null
    }

    private fun isEssentialKey(event: KeyEvent): Boolean {
        if (event.scanCode == Constants.SCANCODE_ESSENTIAL_KEY) {
            return true
        }

        if (event.keyCode == Constants.KEYCODE_ESSENTIAL_KEY) {
            val dev = event.device
            if (dev != null && dev.name.contains(Constants.DEVICE_NAME_GPIO)) {
                return true
            }
        }

        return false
    }

    private fun startProximityCheck() {
        val sensor = proximitySensor ?: return
        isPocketNear = false

        stopProximityCheck()

        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "${Constants.TAG}:ProximityCheck"
        )
        wakeLock?.acquire(Constants.PROXIMITY_TIMEOUT_MS + 100)
        proximityWakeLock = wakeLock

        val timeoutRunnable = Runnable {
            stopProximityCheck()
        }
        proximityTimeoutRunnable = timeoutRunnable
        mainHandler.postDelayed(timeoutRunnable, Constants.PROXIMITY_TIMEOUT_MS)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val maxRange = sensor.maximumRange
                if (event.values[0] < maxRange) {
                    isPocketNear = true
                    Log.d(Constants.TAG, "Essential Key press ignored due to proximity near")
                    cancelPendingActions()
                }
                stopProximityCheck()
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        proximityListener = listener

        sensorManager?.registerListener(
            listener,
            sensor,
            SensorManager.SENSOR_DELAY_FASTEST
        )
    }

    private fun stopProximityCheck() {
        proximityTimeoutRunnable?.let {
            mainHandler.removeCallbacks(it)
            proximityTimeoutRunnable = null
        }
        proximityListener?.let {
            sensorManager?.unregisterListener(it)
            proximityListener = null
        }
        proximityWakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
            proximityWakeLock = null
        }
    }

    private fun cancelPendingActions() {
        longPressRunnable?.let {
            mainHandler.removeCallbacks(it)
            longPressRunnable = null
        }
        pendingSinglePressRunnable?.let {
            mainHandler.removeCallbacks(it)
            pendingSinglePressRunnable = null
        }
        isLongPressTriggered = false
    }

    private fun onKeyDown() {
        if (isPocketNear) return
        isLongPressTriggered = false

        val longPressAction = getPrefInt(
            Constants.PREF_LONG_PRESS_ACTION,
            Constants.ACTION_TORCH
        )

        if (longPressAction != Constants.ACTION_NONE) {
            val customApp = getPrefString(Constants.PREF_CUSTOM_APP_LONG)
            val runnable = Runnable {
                if (isPocketNear) return@Runnable
                isLongPressTriggered = true
                pendingSinglePressRunnable?.let {
                    mainHandler.removeCallbacks(it)
                    pendingSinglePressRunnable = null
                }
                actionExecutor.execute(longPressAction, customApp)
            }
            longPressRunnable = runnable
            mainHandler.postDelayed(runnable, Constants.DEFAULT_LONG_PRESS_TIMEOUT_MS)
        }
    }

    private fun onKeyUp() {
        longPressRunnable?.let {
            mainHandler.removeCallbacks(it)
            longPressRunnable = null
        }

        if (isPocketNear) {
            isPocketNear = false
            return
        }

        if (isLongPressTriggered) {
            isLongPressTriggered = false
            return
        }

        val doublePressAction = getPrefInt(
            Constants.PREF_DOUBLE_PRESS_ACTION,
            Constants.ACTION_CAMERA
        )

        if (doublePressAction != Constants.ACTION_NONE) {
            if (pendingSinglePressRunnable != null) {
                // Second click arrived within timeout -> Double press!
                mainHandler.removeCallbacks(pendingSinglePressRunnable!!)
                pendingSinglePressRunnable = null
                val customApp = getPrefString(Constants.PREF_CUSTOM_APP_DOUBLE)
                actionExecutor.execute(doublePressAction, customApp)
            } else {
                // First click -> schedule single press
                val singlePressAction = getPrefInt(
                    Constants.PREF_SINGLE_PRESS_ACTION,
                    Constants.ACTION_VOICE_ASSISTANT
                )
                val customApp = getPrefString(Constants.PREF_CUSTOM_APP_SINGLE)

                val runnable = Runnable {
                    if (isPocketNear) return@Runnable
                    pendingSinglePressRunnable = null
                    actionExecutor.execute(singlePressAction, customApp)
                }
                pendingSinglePressRunnable = runnable
                mainHandler.postDelayed(runnable, Constants.DEFAULT_DOUBLE_PRESS_TIMEOUT_MS)
            }
        } else {
            // Double press disabled -> execute single press immediately
            val singlePressAction = getPrefInt(
                Constants.PREF_SINGLE_PRESS_ACTION,
                Constants.ACTION_VOICE_ASSISTANT
            )
            val customApp = getPrefString(Constants.PREF_CUSTOM_APP_SINGLE)
            actionExecutor.execute(singlePressAction, customApp)
        }
    }
}
