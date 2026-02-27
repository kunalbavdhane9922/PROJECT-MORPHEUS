package com.morphus.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.morphus.app.service.EmergencyService
import com.morphus.app.utils.Constants

/**
 * Receives BOOT_COMPLETED broadcast to restart the emergency service after reboot.
 * Only restarts if SOS was active before the device powered off.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON") return

        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val wasActive = prefs.getBoolean(Constants.KEY_SOS_ACTIVE, false)

        if (wasActive) {
            Log.i(TAG, "Reboot detected — SOS was active. Restarting EmergencyService.")
            try {
                EmergencyService.start(context)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restart EmergencyService on boot: ${e.message}")
            }
        } else {
            Log.d(TAG, "Reboot detected — SOS was not active. No action needed.")
        }
    }
}
