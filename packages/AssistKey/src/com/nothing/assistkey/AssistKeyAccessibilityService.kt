/*
 * Copyright (C) 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nothing.assistkey

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

class AssistKeyAccessibilityService : AccessibilityService() {
    private lateinit var actionExecutor: ActionExecutor
    private val mainHandler = Handler(Looper.getMainLooper())

    private val sharedPreferences: SharedPreferences
        get() = getSharedPreferences(
            Constants.SHARED_PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )

    private var isLongPressTriggered = false
    private var pendingSinglePressRunnable: Runnable? = null
    private var longPressRunnable: Runnable? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        actionExecutor = ActionExecutor(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    private fun getPrefBoolean(key: String, default: Boolean): Boolean {
        return try {
            Settings.System.getInt(contentResolver, key) == 1
        } catch (_: Settings.SettingNotFoundException) {
            sharedPreferences.getBoolean(key, default)
        } catch (_: Exception) {
            default
        }
    }

    private fun getPrefInt(key: String, default: Int): Int {
        return try {
            Settings.System.getInt(contentResolver, key)
        } catch (_: Settings.SettingNotFoundException) {
            sharedPreferences.getString(key, default.toString())?.toIntOrNull()
                ?: sharedPreferences.getInt(key, default)
        } catch (_: Exception) {
            default
        }
    }

    private fun getPrefString(key: String, default: String? = null): String? {
        return try {
            Settings.System.getString(contentResolver, key) ?: sharedPreferences.getString(key, default)
        } catch (_: Exception) {
            sharedPreferences.getString(key, default)
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!isEssentialKey(event)) {
            return super.onKeyEvent(event)
        }

        if (!getPrefBoolean(Constants.PREF_KEY_ENABLED, true)) {
            return super.onKeyEvent(event)
        }

        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount == 0) {
                    onKeyDown()
                }
            }
            KeyEvent.ACTION_UP -> {
                onKeyUp()
            }
        }

        // Return true to consume the key event
        return true
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

    private fun onKeyDown() {
        isLongPressTriggered = false

        val longPressAction = getPrefInt(
            Constants.PREF_LONG_PRESS_ACTION,
            Constants.ACTION_TORCH
        )

        if (longPressAction != Constants.ACTION_NONE) {
            val customApp = getPrefString(Constants.PREF_CUSTOM_APP_LONG)
            val runnable = Runnable {
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
                mainHandler.removeCallbacks(pendingSinglePressRunnable!!)
                pendingSinglePressRunnable = null
                val customApp = getPrefString(Constants.PREF_CUSTOM_APP_DOUBLE)
                actionExecutor.execute(doublePressAction, customApp)
            } else {
                val singlePressAction = getPrefInt(
                    Constants.PREF_SINGLE_PRESS_ACTION,
                    Constants.ACTION_VOICE_ASSISTANT
                )
                val customApp = getPrefString(Constants.PREF_CUSTOM_APP_SINGLE)

                val runnable = Runnable {
                    pendingSinglePressRunnable = null
                    actionExecutor.execute(singlePressAction, customApp)
                }
                pendingSinglePressRunnable = runnable
                mainHandler.postDelayed(runnable, Constants.DEFAULT_DOUBLE_PRESS_TIMEOUT_MS)
            }
        } else {
            val singlePressAction = getPrefInt(
                Constants.PREF_SINGLE_PRESS_ACTION,
                Constants.ACTION_VOICE_ASSISTANT
            )
            val customApp = getPrefString(Constants.PREF_CUSTOM_APP_SINGLE)
            actionExecutor.execute(singlePressAction, customApp)
        }
    }
}
