/*
 * Copyright (C) 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nothing.assistkey

import android.view.KeyEvent

object Constants {
    const val TAG = "AssistKey"

    // Hardware scancode and keycode definitions
    const val SCANCODE_ESSENTIAL_KEY = 250
    const val KEYCODE_ESSENTIAL_KEY = KeyEvent.KEYCODE_ASSIST
    const val KEYCODE_ESSENTIAL_KEY_BUTTON1 = KeyEvent.KEYCODE_BUTTON_1
    const val DEVICE_NAME_GPIO = "gpio-keys"

    // Actions
    const val ACTION_NONE = 0
    const val ACTION_VOICE_ASSISTANT = 1
    const val ACTION_TORCH = 2
    const val ACTION_CAMERA = 3
    const val ACTION_SCREENSHOT = 4
    const val ACTION_RINGER_MODE = 5
    const val ACTION_DND = 6
    const val ACTION_VOICE_RECORDER = 7
    const val ACTION_MEDIA_PLAY_PAUSE = 8
    const val ACTION_MEDIA_NEXT = 9
    const val ACTION_MEDIA_PREV = 10
    const val ACTION_LAUNCH_APP = 12
    const val ACTION_AUTO_ROTATE = 13
    const val ACTION_MUTE_MIC = 15
    const val ACTION_LOCKDOWN = 16
    const val ACTION_WALLET = 17
    const val ACTION_CAMERA_SHUTTER = 18

    // Preference keys
    const val SHARED_PREFERENCES_NAME = "com.nothing.assistkey_preferences"
    const val PREF_KEY_ENABLED = "assist_key_enabled"
    const val PREF_SINGLE_PRESS_ACTION = "assist_key_single_press_action"
    const val PREF_DOUBLE_PRESS_ACTION = "assist_key_double_press_action"
    const val PREF_LONG_PRESS_ACTION = "assist_key_long_press_action"

    const val PREF_CUSTOM_APP_SINGLE = "assist_key_custom_app_single"
    const val PREF_CUSTOM_APP_DOUBLE = "assist_key_custom_app_double"
    const val PREF_CUSTOM_APP_LONG = "assist_key_custom_app_long"

    const val PREF_HAPTIC_FEEDBACK = "assist_key_haptic_feedback"
    const val PREF_MISTOUCH_PREVENTION = "assist_key_mistouch_prevention"
    const val PREF_SCREEN_OFF_ALLOWED = "assist_key_screen_off_allowed"

    const val PREF_SMART_CALL_SILENCE = "assist_key_smart_call_silence"
    const val PREF_SMART_CAMERA_SHUTTER = "assist_key_smart_camera_shutter"

    // Timeouts (milliseconds)
    const val DEFAULT_DOUBLE_PRESS_TIMEOUT_MS = 300L
    const val DEFAULT_LONG_PRESS_TIMEOUT_MS = 500L
    const val PROXIMITY_TIMEOUT_MS = 150L
    const val WAKELOCK_DURATION_MS = 3000L

    // Default actions
    const val DEFAULT_SINGLE_PRESS = "1"
    const val DEFAULT_DOUBLE_PRESS = "3"
    const val DEFAULT_LONG_PRESS = "2"
}
