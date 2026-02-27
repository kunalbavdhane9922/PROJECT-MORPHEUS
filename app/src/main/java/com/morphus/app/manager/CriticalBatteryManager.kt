package com.morphus.app.manager

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.morphus.app.data.AppRepository
import com.morphus.app.service.EmergencyService

/**
 * Handles critical battery events.
 * When battery drops to critical level:
 *  1. Auto-activates SOS
 *  2. Enables internal power saving mode
 *  3. Sends final low-battery SMS to trusted contacts
 */
object CriticalBatteryManager {

    private const val TAG = "MORPHUS_BATTERY"
    private var triggered = false

    fun handleCriticalBattery(context: Context) {
        if (triggered) return
        triggered = true

        Log.i(TAG, "Auto SOS due to low battery")

        // 1. Activate SOS
        SosManager(context).activate()
        Log.i("MORPHUS_SOS", "Activated automatically")

        // 2. Enable internal power saving mode
        EmergencyService.enablePowerSavingMode(context)

        // 3. Send critical battery SMS
        sendCriticalBatterySms(context)
    }

    private fun sendCriticalBatterySms(context: Context) {
        try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w(TAG, "SEND_SMS permission missing — cannot send battery SMS")
                return
            }

            val contacts = AppRepository(context).getEmergencyContacts()
            if (contacts.isEmpty()) {
                Log.w(TAG, "No emergency contacts — skipping battery SMS")
                return
            }

            val message = "⚠ Battery Critical. Live tracking may stop soon."
            SmsHandler(context).sendGenericSms(contacts, message)
            Log.d(TAG, "Critical battery SMS sent to ${contacts.size} contacts")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send critical battery SMS: ${e.message}")
        }
    }

    /** Reset trigger flag (e.g., after device is plugged in). */
    fun reset() {
        triggered = false
    }
}
