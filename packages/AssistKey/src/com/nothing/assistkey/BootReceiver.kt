/*
 * Copyright (C) 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nothing.assistkey

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) {
            Log.d(Constants.TAG, "Boot completed received - ensuring settings sync")
            syncDefaults(context)
        }
    }

    private fun syncDefaults(context: Context) {
        val dpContext = if (context.isDeviceProtectedStorage) context else context.createDeviceProtectedStorageContext()
        val sp = dpContext.getSharedPreferences(Constants.SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
        val cr = dpContext.contentResolver

        fun syncInt(key: String, default: Int) {
            try {
                Settings.System.getInt(cr, key)
            } catch (_: Settings.SettingNotFoundException) {
                val all = sp.all
                val value = when (val v = all[key]) {
                    is Int -> v
                    is String -> v.toIntOrNull() ?: default
                    is Number -> v.toInt()
                    else -> default
                }
                try {
                    Settings.System.putInt(cr, key, value)
                } catch (_: Exception) {}
            }
        }

        fun syncBoolean(key: String, default: Boolean) {
            try {
                Settings.System.getInt(cr, key)
            } catch (_: Settings.SettingNotFoundException) {
                val all = sp.all
                val value = when (val v = all[key]) {
                    is Boolean -> v
                    is Int -> v == 1
                    is String -> v == "true" || v == "1"
                    else -> default
                }
                try {
                    Settings.System.putInt(cr, key, if (value) 1 else 0)
                } catch (_: Exception) {}
            }
        }

        fun syncString(key: String) {
            try {
                val existing = Settings.System.getString(cr, key)
                if (existing == null) {
                    val spVal = when (val v = sp.all[key]) {
                        is String -> v
                        null -> null
                        else -> v.toString()
                    }
                    if (spVal != null) {
                        Settings.System.putString(cr, key, spVal)
                    }
                }
            } catch (_: Exception) {}
        }

        syncBoolean(Constants.PREF_KEY_ENABLED, true)
        syncInt(Constants.PREF_SINGLE_PRESS_ACTION, Constants.ACTION_VOICE_ASSISTANT)
        syncInt(Constants.PREF_DOUBLE_PRESS_ACTION, Constants.ACTION_CAMERA)
        syncInt(Constants.PREF_LONG_PRESS_ACTION, Constants.ACTION_TORCH)
        syncString(Constants.PREF_CUSTOM_APP_SINGLE)
        syncString(Constants.PREF_CUSTOM_APP_DOUBLE)
        syncString(Constants.PREF_CUSTOM_APP_LONG)
        syncBoolean(Constants.PREF_MISTOUCH_PREVENTION, true)
        syncBoolean(Constants.PREF_SCREEN_OFF_ALLOWED, true)
        syncBoolean(Constants.PREF_HAPTIC_FEEDBACK, true)
        syncBoolean(Constants.PREF_SMART_CALL_SILENCE, true)
        syncBoolean(Constants.PREF_SMART_CAMERA_SHUTTER, true)
    }
}
