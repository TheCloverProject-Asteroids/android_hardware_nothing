/*
 * Copyright (C) 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nothing.assistkey

import android.content.Context
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class AssistKeyTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val sp = getSharedPreferences(Constants.SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
        val current = isKeyEnabled()
        val newState = !current
        sp.edit().putBoolean(Constants.PREF_KEY_ENABLED, newState).apply()
        try {
            Settings.System.putInt(contentResolver, Constants.PREF_KEY_ENABLED, if (newState) 1 else 0)
        } catch (_: Exception) {}
        updateTileState()
    }

    private fun isKeyEnabled(): Boolean {
        return try {
            Settings.System.getInt(contentResolver, Constants.PREF_KEY_ENABLED) == 1
        } catch (_: Settings.SettingNotFoundException) {
            val sp = getSharedPreferences(Constants.SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
            sp.getBoolean(Constants.PREF_KEY_ENABLED, true)
        } catch (_: Exception) {
            true
        }
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val enabled = isKeyEnabled()

        tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.assist_key_title)
        tile.subtitle = if (enabled) getString(R.string.status_enabled) else getString(R.string.status_disabled)
        tile.updateTile()
    }
}
