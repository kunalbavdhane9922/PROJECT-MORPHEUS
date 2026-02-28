package com.morphus.app.manager

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.morphus.app.data.AppRepository
import com.morphus.app.utils.Constants
import java.text.SimpleDateFormat
import java.util.*

/**
 * Manages the offline emergency SMS workflow.
 *
 * Activated by [NetworkGuardian] when the device enters a sustained
 * low-/no-network zone. The lifecycle is:
 *
 * 1. [activate] → captures accurate GPS → sends initial alert SMS.
 * 2. Periodic updates every [Constants.OFFLINE_UPDATE_INTERVAL_MS] (5 min).
 * 3. Movement updates when the user moves > [Constants.OFFLINE_MOVEMENT_THRESHOLD] (50 m).
 * 4. Battery critical alert at ≤ [Constants.OFFLINE_BATTERY_CRITICAL] (5 %).
 * 5. [deactivate] → stops all timers and resets state.
 *
 * This class sends SMS directly (bypassing [SmsHandler] cooldown) because
 * offline alerts must never be rate-limited.
 */
class OfflineSosManager(private val context: Context) {

    companion object {
        private const val TAG = "OfflineSosManager"
    }

    // ── State ────────────────────────────────────────────────────────────────

    /** Whether the offline SMS workflow is currently running. */
    @Volatile
    var isActive: Boolean = false
        private set

    private var locationTracker: LocationTracker? = null
    private var repository: AppRepository? = null

    /** Last location that was included in an SMS. Used for movement detection. */
    private var lastSmsLocation: LocationTracker.LocationData? = null

    /** Epoch millis when the last SMS was sent. */
    private var lastSmsTime: Long = 0L

    /** Whether the critical-battery SMS has already been sent this session. */
    private var batteryCriticalSent = false

    /** Handler for periodic and movement-check runnables. */
    private val handler = Handler(Looper.getMainLooper())

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Enters offline SOS mode.
     *
     * 1. Fetches an accurate location (≤ 50 m, 10 s timeout).
     * 2. Sends the initial alert SMS to all emergency contacts.
     * 3. Starts the periodic update timer (5 min).
     * 4. Starts movement monitoring.
     */
    fun activate(
        tracker: LocationTracker,
        repo: AppRepository
    ) {
        if (isActive) {
            Log.w(TAG, "Already active — ignoring duplicate activate()")
            return
        }

        locationTracker = tracker
        repository = repo
        isActive = true
        batteryCriticalSent = false
        lastSmsLocation = null
        lastSmsTime = 0L

        Log.i(TAG, "🚨 OFFLINE SOS MODE ACTIVATED")

        // Step 1: Fetch an accurate location, then send the initial SMS.
        tracker.getAccurateLocation(
            maxAccuracyMeters = Constants.OFFLINE_LOCATION_ACCURACY_THRESHOLD,
            timeoutMs = Constants.OFFLINE_LOCATION_RETRY_MS
        ) { location ->
            if (!isActive) return@getAccurateLocation  // deactivated while waiting

            val loc = location ?: tracker.lastLocation
            if (loc != null) {
                sendAlertSms(loc, isInitial = true)
                lastSmsLocation = loc
                lastSmsTime = System.currentTimeMillis()
            } else {
                // No location at all — send alert without coordinates.
                Log.w(TAG, "No location available — sending SMS without coordinates")
                sendAlertSmsNoLocation()
            }

            // Step 2: Start periodic + movement checks.
            schedulePeriodicUpdate()
            scheduleMovementCheck()
        }
    }

    /**
     * Exits offline SOS mode. Stops all timers and resets state.
     * Safe to call even if [activate] was never called.
     */
    fun deactivate() {
        if (!isActive) return

        isActive = false
        handler.removeCallbacksAndMessages(null)  // cancel all pending runnables
        lastSmsLocation = null
        locationTracker = null
        repository = null

        Log.i(TAG, "✅ OFFLINE SOS MODE DEACTIVATED — online mode restored")
    }

    // ── SMS Sending ──────────────────────────────────────────────────────────

    /**
     * Sends the initial or update MORPHEUS SAFETY ALERT SMS.
     *
     * @param location Current GPS fix.
     * @param isInitial True for the first alert, false for subsequent updates.
     */
    private fun sendAlertSms(location: LocationTracker.LocationData, isInitial: Boolean) {
        val contacts = repository?.getEmergencyContacts() ?: return
        if (contacts.isEmpty()) {
            Log.w(TAG, "No emergency contacts configured — cannot send SMS")
            return
        }

        if (!hasSmsPermission()) {
            Log.e(TAG, "SEND_SMS permission not granted — cannot send alert")
            return
        }

        val mapsUrl = location.toMapsUrl()
        val time = SimpleDateFormat("HH:mm:ss dd-MMM-yyyy", Locale.getDefault())
            .format(Date(location.timestamp))
        val battery = getBatteryLevel()

        val message = if (isInitial) {
            """
            |⚠️ MORPHEUS SAFETY ALERT
            |
            |User entered low network area.
            |
            |Last Known Location:
            |$mapsUrl
            |
            |Time: $time
            |Battery: $battery%
            |Accuracy: ${"%.0f".format(location.accuracy)}m
            |
            |Tracking continues via SMS updates.
            """.trimMargin()
        } else {
            """
            |📍 MORPHEUS LOCATION UPDATE
            |
            |Updated Location:
            |$mapsUrl
            |
            |Time: $time
            |Battery: $battery%
            |Accuracy: ${"%.0f".format(location.accuracy)}m
            """.trimMargin()
        }

        sendRawSms(contacts, message)

        val label = if (isInitial) "INITIAL alert" else "UPDATE"
        Log.i(TAG, "$label SMS sent to ${contacts.size} contacts")
    }

    /**
     * Fallback SMS when no GPS location is available at all.
     */
    private fun sendAlertSmsNoLocation() {
        val contacts = repository?.getEmergencyContacts() ?: return
        if (contacts.isEmpty() || !hasSmsPermission()) return

        val time = SimpleDateFormat("HH:mm:ss dd-MMM-yyyy", Locale.getDefault())
            .format(Date())

        val message = """
            |⚠️ MORPHEUS SAFETY ALERT
            |
            |User entered low network area.
            |
            |Location: UNAVAILABLE (GPS acquiring)
            |
            |Time: $time
            |Battery: ${getBatteryLevel()}%
            |
            |Tracking continues via SMS updates.
        """.trimMargin()

        sendRawSms(contacts, message)
        Log.w(TAG, "INITIAL alert SMS sent WITHOUT location")
    }

    /**
     * Sends a critical battery warning SMS.
     */
    private fun sendBatteryCriticalSms() {
        if (batteryCriticalSent) return
        batteryCriticalSent = true

        val contacts = repository?.getEmergencyContacts() ?: return
        if (contacts.isEmpty() || !hasSmsPermission()) return

        val loc = locationTracker?.lastLocation
        val mapsUrl = loc?.toMapsUrl() ?: "Location unavailable"
        val time = SimpleDateFormat("HH:mm:ss dd-MMM-yyyy", Locale.getDefault())
            .format(Date())

        val message = """
            |🔋 MORPHEUS BATTERY CRITICAL
            |
            |Battery at ${getBatteryLevel()}%. Device may shut down soon.
            |
            |Last Known Location:
            |$mapsUrl
            |
            |Time: $time
        """.trimMargin()

        sendRawSms(contacts, message)
        Log.w(TAG, "BATTERY CRITICAL SMS sent")
    }

    /**
     * Low-level SMS dispatch. Bypasses [SmsHandler] to avoid cooldown
     * restrictions — offline alerts must always go through.
     */
    private fun sendRawSms(contacts: List<String>, message: String) {
        val smsManager: SmsManager = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
                    ?: @Suppress("DEPRECATION") SmsManager.getDefault()
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to obtain SmsManager: ${e.message}")
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }

        for (phone in contacts) {
            try {
                // Split long messages into multi-part SMS.
                val parts = smsManager.divideMessage(message)
                if (parts.size > 1) {
                    smsManager.sendMultipartTextMessage(phone, null, parts, null, null)
                } else {
                    smsManager.sendTextMessage(phone, null, message, null, null)
                }
                Log.d(TAG, "SMS sent to $phone")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send SMS to $phone: ${e.message}")
            }
        }
    }

    // ── Periodic & Movement Checks ───────────────────────────────────────────

    /**
     * Schedules a repeating update SMS every [Constants.OFFLINE_UPDATE_INTERVAL_MS].
     */
    private fun schedulePeriodicUpdate() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (!isActive) return

                val loc = locationTracker?.lastLocation
                if (loc != null) {
                    sendAlertSms(loc, isInitial = false)
                    lastSmsLocation = loc
                    lastSmsTime = System.currentTimeMillis()
                }

                // Check battery and send critical SMS if needed.
                val battery = getBatteryLevel()
                if (battery in 0..Constants.OFFLINE_BATTERY_CRITICAL) {
                    sendBatteryCriticalSms()
                }

                // Re-schedule.
                handler.postDelayed(this, Constants.OFFLINE_UPDATE_INTERVAL_MS)
            }
        }, Constants.OFFLINE_UPDATE_INTERVAL_MS)
    }

    /**
     * Checks every 30 seconds whether the user has moved more than
     * [Constants.OFFLINE_MOVEMENT_THRESHOLD] metres since the last SMS.
     */
    private fun scheduleMovementCheck() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (!isActive) return

                val currentLoc = locationTracker?.lastLocation
                val previousLoc = lastSmsLocation

                if (currentLoc != null && previousLoc != null) {
                    val results = FloatArray(1)
                    android.location.Location.distanceBetween(
                        previousLoc.latitude, previousLoc.longitude,
                        currentLoc.latitude, currentLoc.longitude,
                        results
                    )

                    if (results[0] > Constants.OFFLINE_MOVEMENT_THRESHOLD) {
                        Log.i(TAG, "Movement detected: ${"%.0f".format(results[0])}m " +
                                "> ${Constants.OFFLINE_MOVEMENT_THRESHOLD}m — sending update")
                        sendAlertSms(currentLoc, isInitial = false)
                        lastSmsLocation = currentLoc
                        lastSmsTime = System.currentTimeMillis()
                    }
                }

                // Re-check every 30 seconds.
                handler.postDelayed(this, 30_000L)
            }
        }, 30_000L)
    }

    // ── Utility ──────────────────────────────────────────────────────────────

    private fun hasSmsPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

    private fun getBatteryLevel(): Int {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
            context.registerReceiver(null, filter)
        }
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) (level * 100 / scale) else -1
    }
}
