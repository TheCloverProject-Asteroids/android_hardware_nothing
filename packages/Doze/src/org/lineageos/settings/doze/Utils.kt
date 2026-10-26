/*
 * Copyright (C) 2021-2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.doze

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.UserHandle
import android.util.Log
import androidx.preference.PreferenceManager

object Utils {
    private const val TAG = "DozeUtils"

    const val GESTURE_POCKET_KEY = "gesture_pocket"

    private fun startService(context: Context) {
        Log.d(TAG, "Starting service")
        context.startServiceAsUser(Intent(context, DozeService::class.java), UserHandle.CURRENT)
    }

    private fun stopService(context: Context) {
        Log.d(TAG, "Stopping service")
        context.stopServiceAsUser(Intent(context, DozeService::class.java), UserHandle.CURRENT)
    }

    fun checkDozeService(context: Context) {
        if (isPocketEnabled(context)) {
            startService(context)
        } else {
            stopService(context)
        }
    }

    fun isPocketEnabled(context: Context): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(GESTURE_POCKET_KEY, true)
    }

    fun setPocketEnabled(context: Context, enable: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putBoolean(GESTURE_POCKET_KEY, enable)
            .apply()
    }

    fun getSensor(sm: SensorManager, type: String?): Sensor? {
        if (type.isNullOrEmpty()) return null
        return sm.getSensorList(Sensor.TYPE_ALL).find { it.stringType == type && it.isWakeUpSensor }
            ?: sm.getSensorList(Sensor.TYPE_ALL).find { it.stringType == type }
    }
}
