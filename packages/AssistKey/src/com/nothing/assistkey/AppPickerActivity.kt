/*
 * Copyright (C) 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nothing.assistkey

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity
import java.util.concurrent.Executors

class AppPickerActivity : CollapsingToolbarBaseActivity() {
    companion object {
        const val EXTRA_PREF_KEY = "extra_pref_key"
        const val EXTRA_SELECTED_PACKAGE = "extra_selected_package"
    }

    private data class AppItem(
        val label: String,
        val packageName: String,
        val icon: Drawable
    )

    private lateinit var listView: ListView
    private var prefKey: String? = null
    private val appList = mutableListOf<AppItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.select_app_title)

        prefKey = intent.getStringExtra(EXTRA_PREF_KEY)

        listView = ListView(this).apply {
            divider = null
            dividerHeight = 0
            clipToPadding = false
            setPadding(0, 8, 0, 16)
        }

        val contentFrame = findViewById<ViewGroup>(com.android.settingslib.collapsingtoolbar.R.id.content_frame)
        if (contentFrame != null) {
            contentFrame.addView(listView)
        } else {
            setContentView(listView)
        }

        loadApps()
    }

    private fun loadApps() {
        Executors.newSingleThreadExecutor().submit {
            val pm = packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = pm.queryIntentActivities(
                mainIntent,
                PackageManager.ResolveInfoFlags.of(0)
            )
            val items = resolveInfos.map {
                AppItem(
                    label = it.loadLabel(pm).toString(),
                    packageName = it.activityInfo.packageName,
                    icon = it.loadIcon(pm)
                )
            }.sortedBy { it.label.lowercase() }

            runOnUiThread {
                appList.clear()
                appList.addAll(items)
                listView.adapter = AppAdapter(this, appList)
                listView.setOnItemClickListener { _, _, position, _ ->
                    val selected = appList[position]
                    onAppSelected(selected.packageName)
                }
            }
        }
    }

    private fun onAppSelected(packageName: String) {
        prefKey?.let { key ->
            val dpContext = if (isDeviceProtectedStorage) this else createDeviceProtectedStorageContext()
            val sp = dpContext.getSharedPreferences(Constants.SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
            sp.edit().putString(key, packageName).commit()
            try {
                Settings.System.putString(contentResolver, key, packageName)
            } catch (_: Exception) {}
        }

        val resultIntent = Intent().apply {
            putExtra(EXTRA_SELECTED_PACKAGE, packageName)
        }
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private class AppAdapter(
        private val context: Context,
        private val items: List<AppItem>
    ) : BaseAdapter() {
        override fun getCount(): Int = items.size
        override fun getItem(position: Int): Any = items[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(context).inflate(
                R.layout.app_picker_item,
                parent,
                false
            )
            val item = items[position]
            view.findViewById<ImageView>(R.id.app_icon).setImageDrawable(item.icon)
            view.findViewById<TextView>(R.id.app_name).text = item.label
            view.findViewById<TextView>(R.id.app_package).text = item.packageName
            return view
        }
    }
}
