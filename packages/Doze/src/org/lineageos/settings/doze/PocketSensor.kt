/*
 * Copyright (C) 2021-2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.doze

import android.app.KeyguardManager
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.SystemProperties
import android.util.Log

class PocketSensor(
    private val context: Context,
    sensorType: String,
    private val sensorValue: Float,
) : SensorEventListener {
    private val sensorManager = context.getSystemService(SensorManager::class.java)!!
    private val keyguardManager = context.getSystemService(KeyguardManager::class.java)
    private val sensor = Utils.getSensor(sensorManager, sensorType)
    private val handler = Handler(Looper.getMainLooper())
    private val touchBlocker = TouchBlocker(context)

    private var isCovered = false
    private var isRegistered = false
    private var isInteractive = true

    private val isDeviceLocked: Boolean
        get() = keyguardManager?.isKeyguardLocked ?: false

    private val uncoverRunnable = Runnable {
        if (isCovered) {
            isCovered = false
            Log.d(TAG, "Uncovered (debounce elapsed), resetting pocket mode to 0")
            touchBlocker.disable()
            try {
                SystemProperties.set("sys.touch.pocket_mode", "0")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reset pocket mode", e)
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val value = event.values[0]
        if (DEBUG) Log.d(TAG, "Got sensor event: $value (interactive=$isInteractive, locked=$isDeviceLocked)")

        // If screen is on and device is unlocked, pocket mode must never block touch or gestures
        if (isInteractive && !isDeviceLocked) {
            if (isCovered) {
                isCovered = false
                touchBlocker.disable()
                try {
                    SystemProperties.set("sys.touch.pocket_mode", "0")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to reset pocket mode", e)
                }
            }
            disable()
            return
        }

        val maxRange = sensor?.maximumRange ?: 5.0f
        val isNear = if (sensorValue > 0f) {
            value <= sensorValue
        } else {
            value < maxRange
        }

        if (isNear) {
            handler.removeCallbacks(uncoverRunnable)
            if (!isCovered) {
                isCovered = true
                Log.d(TAG, "Covered, setting pocket mode to 1")
                try {
                    SystemProperties.set("sys.touch.pocket_mode", "1")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to set pocket mode", e)
                }
                if (isInteractive && isDeviceLocked) {
                    touchBlocker.enable()
                }
            }
        } else {
            if (isCovered) {
                handler.removeCallbacks(uncoverRunnable)
                handler.postDelayed(uncoverRunnable, DEBOUNCE_DELAY_MS)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

    fun onDisplayOn() {
        isInteractive = true
        if (isDeviceLocked) {
            enable()
            if (isCovered) {
                touchBlocker.enable()
            }
        } else {
            onUserUnlocked()
        }
    }

    fun onDisplayOff() {
        isInteractive = false
        touchBlocker.disable()
        enable()
    }

    fun onUserUnlocked() {
        Log.d(TAG, "onUserUnlocked: disabling blocker and unregistering sensor")
        handler.removeCallbacks(uncoverRunnable)
        isCovered = false
        touchBlocker.disable()
        try {
            SystemProperties.set("sys.touch.pocket_mode", "0")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reset pocket mode", e)
        }
        if (isInteractive) {
            disable()
        }
    }

    fun onKeyguardLocked() {
        Log.d(TAG, "onKeyguardLocked: device locked")
        if (isInteractive) {
            enable()
            if (isCovered) {
                touchBlocker.enable()
            }
        }
    }

    fun enable() {
        if (sensor != null && !isRegistered) {
            Log.d(TAG, "Enabling sensor listener")
            isRegistered = true
            handler.removeCallbacks(uncoverRunnable)
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    fun disable() {
        if (sensor != null && isRegistered) {
            Log.d(TAG, "Disabling sensor listener")
            isRegistered = false
            handler.removeCallbacks(uncoverRunnable)
            sensorManager.unregisterListener(this, sensor)
            isCovered = false
            touchBlocker.disable()
            try {
                SystemProperties.set("sys.touch.pocket_mode", "0")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reset pocket mode", e)
            }
        }
    }

    companion object {
        private const val TAG = "PocketSensor"
        private const val DEBUG = true

        private const val DEBOUNCE_DELAY_MS = 400L
    }
}
