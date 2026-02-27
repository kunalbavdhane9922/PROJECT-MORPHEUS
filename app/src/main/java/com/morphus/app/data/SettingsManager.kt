package com.morphus.app.data

import android.content.Context
import android.content.SharedPreferences
import com.morphus.app.utils.Constants

/**
 * Manages application settings using SharedPreferences.
 * Handles SOS configuration, PIN management, and feature toggles.
 */
class SettingsManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        Constants.PREFS_NAME,
        Context.MODE_PRIVATE
    )

    // ── PIN Management ──────────────────────────────────────────────────────

    var sosPin: String
        get() = prefs.getString(Constants.KEY_SOS_PIN, Constants.DEFAULT_SOS_PIN) ?: Constants.DEFAULT_SOS_PIN
        set(value) = prefs.edit().putString(Constants.KEY_SOS_PIN, value).apply()

    fun verifyPin(input: String): Boolean = input == sosPin

    // ── SOS Behaviour Toggles ───────────────────────────────────────────────

    var isShakeEnabled: Boolean
        get() = prefs.getBoolean(Constants.KEY_SHAKE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(Constants.KEY_SHAKE_ENABLED, value).apply()

    var isPowerButtonEnabled: Boolean
        get() = prefs.getBoolean(Constants.KEY_POWER_BUTTON_ENABLED, true)
        set(value) = prefs.edit().putBoolean(Constants.KEY_POWER_BUTTON_ENABLED, value).apply()

    var isAutoCallEnabled: Boolean
        get() = prefs.getBoolean(Constants.KEY_AUTO_CALL_ENABLED, false)
        set(value) = prefs.edit().putBoolean(Constants.KEY_AUTO_CALL_ENABLED, value).apply()

    var isAudioRecordEnabled: Boolean
        get() = prefs.getBoolean(Constants.KEY_AUDIO_RECORD_ENABLED, true)
        set(value) = prefs.edit().putBoolean(Constants.KEY_AUDIO_RECORD_ENABLED, value).apply()

    // ── Tracking Settings ───────────────────────────────────────────────────

    var movementThreshold: Int
        get() = prefs.getInt(Constants.KEY_MOVEMENT_THRESHOLD, Constants.DEFAULT_MOVEMENT_THRESHOLD)
        set(value) = prefs.edit().putInt(Constants.KEY_MOVEMENT_THRESHOLD, value).apply()

    var updateInterval: Int
        get() = prefs.getInt(Constants.KEY_UPDATE_INTERVAL, Constants.DEFAULT_UPDATE_INTERVAL)
        set(value) = prefs.edit().putInt(Constants.KEY_UPDATE_INTERVAL, value).apply()

    // ── Message Template ────────────────────────────────────────────────────

    var messageTemplate: String
        get() = prefs.getString(Constants.KEY_MESSAGE_TEMPLATE, Constants.DEFAULT_MESSAGE_TEMPLATE) ?: Constants.DEFAULT_MESSAGE_TEMPLATE
        set(value) = prefs.edit().putString(Constants.KEY_MESSAGE_TEMPLATE, value).apply()
}
