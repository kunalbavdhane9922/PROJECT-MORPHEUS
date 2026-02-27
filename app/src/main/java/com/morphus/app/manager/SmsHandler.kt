package com.morphus.app.manager

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.SmsManager
import android.util.Log
import androidx.annotation.RequiresPermission
import com.morphus.app.utils.Constants
import java.text.SimpleDateFormat
import java.util.*


/**
 * Handles sending formatted SOS SMS messages with retries and battery status.
 */
class SmsHandler(private val context: Context) {

    private val handler = Handler(Looper.getMainLooper())

    companion object {
        private const val TAG = "SmsHandler"
    }

    /**
     * Sends an SOS message to all contacts.
     * Retries up to [Constants.SMS_MAX_RETRIES] times on failure.
     */
    @RequiresPermission(Manifest.permission.SEND_SMS)
    fun sendSosSms(
        location: LocationTracker.LocationData,
        contacts: List<String>,
        retryCount: Int = 0
    ) {
        if (contacts.isEmpty()) {
            Log.w(TAG, "No emergency contacts found — skip SMS")
            return
        }

        val message = formatSosMessage(location)
        sendGenericSms(contacts, message, retryCount)
    }

    /**
     * Sends a generic SMS message to multiple contacts with retry logic.
     */
    @RequiresPermission(Manifest.permission.SEND_SMS)
    fun sendGenericSms(
        contacts: List<String>,
        message: String,
        retryCount: Int = 0
    ) {
        if (contacts.isEmpty()) return

        val smsManager: SmsManager = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java) ?: @Suppress("DEPRECATION") SmsManager.getDefault()
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get SmsManager: ${e.message}")
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }


        Log.d("MORPHUS_DEBUG", "Sending SMS to ${contacts.size} contacts")
        Log.i(TAG, "Sending SMS to ${contacts.size} contacts (Attempt ${retryCount + 1})")

        var success = true
        for (phone in contacts) {
            try {
                smsManager.sendTextMessage(phone, null, message, null, null)
                Log.d(TAG, "SMS sent to $phone")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send SMS to $phone: ${e.message}")
                success = false
            }
        }

        if (!success && retryCount < Constants.SMS_MAX_RETRIES) {
            Log.w(TAG, "Some SMS failed — scheduling retry in ${Constants.SMS_RETRY_DELAY_MS}ms")
            handler.postDelayed({
                sendGenericSms(contacts, message, retryCount + 1)
            }, Constants.SMS_RETRY_DELAY_MS)
        }
    }

    /**
     * Formats the SOS message with location, battery, and time.
     */
    private fun formatSosMessage(loc: LocationTracker.LocationData): String {
        val battery = getBatteryLevel()
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(loc.timestamp))

        return """
            EMERGENCY SOS
            Lat: ${loc.latitude}
            Lon: ${loc.longitude}
            Battery: $battery%
            Time: $time
            Link: ${loc.toMapsUrl()}
        """.trimIndent()
    }

    private fun getBatteryLevel(): Int {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
            context.registerReceiver(null, filter)
        }
        val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) (level * 100 / scale) else -1
    }
}
