package com.morphus.app.trigger

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.morphus.app.manager.SosManager

/**
 * Detects double power button press within 700ms window.
 * Listens for SCREEN_ON / SCREEN_OFF intents and counts rapid presses.
 * Triggers SOS activation on 2 consecutive presses.
 */
class PowerButtonGuardian(
    private val appContext: Context
) : BroadcastReceiver() {

    companion object {
        private const val TAG = "MORPHUS_TRIGGER"
        private const val DOUBLE_PRESS_WINDOW_MS = 700L
        private const val REQUIRED_PRESSES = 2
    }

    private var lastPressTime = 0L
    private var pressCount = 0

    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action ?: return

        if (action == Intent.ACTION_SCREEN_OFF ||
            action == Intent.ACTION_SCREEN_ON
        ) {
            val now = System.currentTimeMillis()

            if (now - lastPressTime < DOUBLE_PRESS_WINDOW_MS) {
                pressCount++
            } else {
                pressCount = 1
            }

            lastPressTime = now

            Log.d(TAG, "Power press count=$pressCount")

            if (pressCount >= REQUIRED_PRESSES) {
                Log.i(TAG, "DOUBLE POWER PRESS DETECTED")
                try {
                    SosManager(appContext).activate()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to activate SOS from power press: ${e.message}")
                }
                pressCount = 0
            }
        }
    }
}
