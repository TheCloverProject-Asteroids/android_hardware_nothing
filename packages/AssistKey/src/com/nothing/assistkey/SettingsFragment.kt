/*
 * Copyright (C) 2024-2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nothing.assistkey

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.CompoundButton
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.SwitchPreferenceCompat
import com.android.settingslib.widget.MainSwitchPreference
import com.android.settingslib.widget.SettingsBasePreferenceFragment

class SettingsFragment : SettingsBasePreferenceFragment(),
    Preference.OnPreferenceChangeListener,
    CompoundButton.OnCheckedChangeListener {

    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var enabledPref: MainSwitchPreference
    private lateinit var singlePressPref: ListPreference
    private lateinit var doublePressPref: ListPreference
    private lateinit var longPressPref: ListPreference

    private lateinit var singleAppPref: Preference
    private lateinit var doubleAppPref: Preference
    private lateinit var longAppPref: Preference

    private var actionsCategory: PreferenceCategory? = null
    private var smartContextCategory: PreferenceCategory? = null
    private var mistouchCategory: PreferenceCategory? = null
    private var feedbackCategory: PreferenceCategory? = null

    private var mistouchPref: SwitchPreferenceCompat? = null
    private var screenOffPref: SwitchPreferenceCompat? = null
    private var hapticPref: SwitchPreferenceCompat? = null
    private var smartCallSilencePref: SwitchPreferenceCompat? = null
    private var smartCameraShutterPref: SwitchPreferenceCompat? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = Constants.SHARED_PREFERENCES_NAME
        preferenceManager.setStorageDeviceProtected()
        addPreferencesFromResource(R.xml.assist_key_settings)

        enabledPref = findPreference(Constants.PREF_KEY_ENABLED)!!
        singlePressPref = findPreference(Constants.PREF_SINGLE_PRESS_ACTION)!!
        doublePressPref = findPreference(Constants.PREF_DOUBLE_PRESS_ACTION)!!
        longPressPref = findPreference(Constants.PREF_LONG_PRESS_ACTION)!!

        singleAppPref = findPreference(Constants.PREF_CUSTOM_APP_SINGLE)!!
        doubleAppPref = findPreference(Constants.PREF_CUSTOM_APP_DOUBLE)!!
        longAppPref = findPreference(Constants.PREF_CUSTOM_APP_LONG)!!

        actionsCategory = findPreference("category_actions")
        smartContextCategory = findPreference("category_smart_context")
        mistouchCategory = findPreference("category_mistouch")
        feedbackCategory = findPreference("category_feedback")

        mistouchPref = findPreference(Constants.PREF_MISTOUCH_PREVENTION)
        screenOffPref = findPreference(Constants.PREF_SCREEN_OFF_ALLOWED)
        hapticPref = findPreference(Constants.PREF_HAPTIC_FEEDBACK)
        smartCallSilencePref = findPreference(Constants.PREF_SMART_CALL_SILENCE)
        smartCameraShutterPref = findPreference(Constants.PREF_SMART_CAMERA_SHUTTER)

        val isEnabled = preferenceManager.sharedPreferences?.getBoolean(Constants.PREF_KEY_ENABLED, true) ?: true
        enabledPref.isChecked = isEnabled
        enabledPref.addOnSwitchChangeListener(this)

        singlePressPref.onPreferenceChangeListener = this
        doublePressPref.onPreferenceChangeListener = this
        longPressPref.onPreferenceChangeListener = this
        mistouchPref?.onPreferenceChangeListener = this
        screenOffPref?.onPreferenceChangeListener = this
        hapticPref?.onPreferenceChangeListener = this
        smartCallSilencePref?.onPreferenceChangeListener = this
        smartCameraShutterPref?.onPreferenceChangeListener = this

        setupAppPickerPreference(singleAppPref, Constants.PREF_CUSTOM_APP_SINGLE)
        setupAppPickerPreference(doubleAppPref, Constants.PREF_CUSTOM_APP_DOUBLE)
        setupAppPickerPreference(longAppPref, Constants.PREF_CUSTOM_APP_LONG)

        updateEnabledState(isEnabled)
        updateAppPreferencesVisibility()
        updateAppSummaries()

        // Sync initial values to Settings.System
        syncInitialPreferences()
    }

    override fun onResume() {
        super.onResume()
        updateAppSummaries()
    }

    override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
        preferenceManager.sharedPreferences?.edit()?.putBoolean(Constants.PREF_KEY_ENABLED, isChecked)?.apply()
        syncPrefToSettings(Constants.PREF_KEY_ENABLED, isChecked)
        updateEnabledState(isChecked)
    }

    private fun updateEnabledState(isEnabled: Boolean) {
        actionsCategory?.isEnabled = isEnabled
        smartContextCategory?.isEnabled = isEnabled
        mistouchCategory?.isEnabled = isEnabled
        feedbackCategory?.isEnabled = isEnabled
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
        syncPrefToSettings(preference.key, newValue)
        when (preference.key) {
            Constants.PREF_SINGLE_PRESS_ACTION,
            Constants.PREF_DOUBLE_PRESS_ACTION,
            Constants.PREF_LONG_PRESS_ACTION -> {
                mainHandler.post {
                    updateAppPreferencesVisibility()
                    updateAppSummaries()
                }
            }
        }
        return true
    }

    private fun setupAppPickerPreference(pref: Preference, key: String) {
        pref.setOnPreferenceClickListener {
            val intent = Intent(requireContext(), AppPickerActivity::class.java).apply {
                putExtra(AppPickerActivity.EXTRA_PREF_KEY, key)
            }
            startActivity(intent)
            true
        }
    }

    private fun updateAppPreferencesVisibility() {
        val singleVal = singlePressPref.value?.toIntOrNull() ?: Constants.ACTION_VOICE_ASSISTANT
        val doubleVal = doublePressPref.value?.toIntOrNull() ?: Constants.ACTION_CAMERA
        val longVal = longPressPref.value?.toIntOrNull() ?: Constants.ACTION_TORCH

        singleAppPref.isVisible = (singleVal == Constants.ACTION_LAUNCH_APP)
        doubleAppPref.isVisible = (doubleVal == Constants.ACTION_LAUNCH_APP)
        longAppPref.isVisible = (longVal == Constants.ACTION_LAUNCH_APP)
    }

    private fun updateAppSummaries() {
        val sp = preferenceManager.sharedPreferences ?: return
        val pm = requireContext().packageManager

        fun getAppLabel(pkg: String?): String {
            if (pkg.isNullOrBlank()) return getString(R.string.no_app_selected)
            return try {
                val appInfo = pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0L))
                pm.getApplicationLabel(appInfo).toString()
            } catch (_: PackageManager.NameNotFoundException) {
                getString(R.string.no_app_selected)
            }
        }

        val all = sp.all
        val singlePkg = when (val v = all[Constants.PREF_CUSTOM_APP_SINGLE]) {
            is String -> v
            else -> null
        }
        val doublePkg = when (val v = all[Constants.PREF_CUSTOM_APP_DOUBLE]) {
            is String -> v
            else -> null
        }
        val longPkg = when (val v = all[Constants.PREF_CUSTOM_APP_LONG]) {
            is String -> v
            else -> null
        }

        singleAppPref.summary = getAppLabel(singlePkg)
        doubleAppPref.summary = getAppLabel(doublePkg)
        longAppPref.summary = getAppLabel(longPkg)
    }

    private fun syncPrefToSettings(key: String, value: Any?) {
        val cr = activity?.contentResolver ?: return
        try {
            when (value) {
                is Boolean -> Settings.System.putInt(cr, key, if (value) 1 else 0)
                is Int -> Settings.System.putInt(cr, key, value)
                is String -> {
                    val intVal = value.toIntOrNull()
                    if (intVal != null) {
                        Settings.System.putInt(cr, key, intVal)
                    } else {
                        Settings.System.putString(cr, key, value)
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun syncInitialPreferences() {
        val sp = preferenceManager.sharedPreferences ?: return
        syncPrefToSettings(Constants.PREF_KEY_ENABLED, sp.getBoolean(Constants.PREF_KEY_ENABLED, true))
        syncPrefToSettings(
            Constants.PREF_SINGLE_PRESS_ACTION,
            sp.getString(Constants.PREF_SINGLE_PRESS_ACTION, Constants.DEFAULT_SINGLE_PRESS)
        )
        syncPrefToSettings(
            Constants.PREF_DOUBLE_PRESS_ACTION,
            sp.getString(Constants.PREF_DOUBLE_PRESS_ACTION, Constants.DEFAULT_DOUBLE_PRESS)
        )
        syncPrefToSettings(
            Constants.PREF_LONG_PRESS_ACTION,
            sp.getString(Constants.PREF_LONG_PRESS_ACTION, Constants.DEFAULT_LONG_PRESS)
        )
        syncPrefToSettings(
            Constants.PREF_CUSTOM_APP_SINGLE,
            sp.getString(Constants.PREF_CUSTOM_APP_SINGLE, null)
        )
        syncPrefToSettings(
            Constants.PREF_CUSTOM_APP_DOUBLE,
            sp.getString(Constants.PREF_CUSTOM_APP_DOUBLE, null)
        )
        syncPrefToSettings(
            Constants.PREF_CUSTOM_APP_LONG,
            sp.getString(Constants.PREF_CUSTOM_APP_LONG, null)
        )
        syncPrefToSettings(
            Constants.PREF_MISTOUCH_PREVENTION,
            sp.getBoolean(Constants.PREF_MISTOUCH_PREVENTION, true)
        )
        syncPrefToSettings(
            Constants.PREF_SCREEN_OFF_ALLOWED,
            sp.getBoolean(Constants.PREF_SCREEN_OFF_ALLOWED, true)
        )
        syncPrefToSettings(
            Constants.PREF_HAPTIC_FEEDBACK,
            sp.getBoolean(Constants.PREF_HAPTIC_FEEDBACK, true)
        )
        syncPrefToSettings(
            Constants.PREF_SMART_CALL_SILENCE,
            sp.getBoolean(Constants.PREF_SMART_CALL_SILENCE, true)
        )
        syncPrefToSettings(
            Constants.PREF_SMART_CAMERA_SHUTTER,
            sp.getBoolean(Constants.PREF_SMART_CAMERA_SHUTTER, true)
        )
    }
}
