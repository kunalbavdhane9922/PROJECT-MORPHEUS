package com.morphus.app.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log

/**
 * Monitors battery level and triggers critical battery handling at <= 5%.
 * Registered as a sticky broadcast receiver — no manifest entry needed.
 */
class BatteryGuardian(private val context: Context) {

    companion object {
        private const val TAG = "MORPHUS_BATTERY"
        private const val CRITICAL_THRESHOLD = 5
    }

    fun startMonitoring() {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)

        context.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: return
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level < 0 || scale <= 0) return

                val percent = (level * 100) / scale

                Log.d(TAG, "Battery=$percent%")

                if (percent <= CRITICAL_THRESHOLD) {
                    Log.w(TAG, "CRITICAL BATTERY DETECTED")
                    com.morphus.app.manager.CriticalBatteryManager.handleCriticalBattery(context)
                }
            }
        }, filter)

        Log.i(TAG, "Battery monitoring started (threshold=${CRITICAL_THRESHOLD}%)")
    }
}
