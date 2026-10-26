/*
 * Copyright (C) 2021-2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.doze

import android.os.Bundle
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity

class DozeSettingsActivity : CollapsingToolbarBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
        val newState = !Utils.isPocketEnabled(this)
        Utils.setPocketEnabled(this, newState)
        Utils.checkDozeService(this)
        DozeContentProvider.notifyPocketChanged(this)
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    companion object {
        private const val TAG = "DozeSettingsActivity"
    }
}
