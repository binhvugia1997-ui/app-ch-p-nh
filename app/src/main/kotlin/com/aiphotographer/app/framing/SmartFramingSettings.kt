package com.aiphotographer.app.framing

import android.content.Context
import androidx.core.content.edit

/**
 * The single source of truth for the Smart Framing toggle (owner decision D3; `docs/ai-smart-framing-design.md`
 * §3.4). Local `SharedPreferences`, one boolean key, default **on** (D2). No DataStore, no settings module,
 * no data leaving the device.
 */
class SmartFramingSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY, DEFAULT_ENABLED)
        set(value) {
            prefs.edit { putBoolean(KEY, value) }
        }

    companion object {
        const val KEY = "smart_framing_enabled_v1"
        const val DEFAULT_ENABLED = true
        private const val FILE = "smart_framing"
    }
}
