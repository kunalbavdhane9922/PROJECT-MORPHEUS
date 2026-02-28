package com.morphus.app.manager

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.morphus.app.data.AppRepository

/**
 * Schedules periodic location SMS updates every 5 minutes after the
 * initial SOS message has been sent.
 *
 * - [start] begins the 5-minute repeating cycle.
 * - [stop] cancels all pending updates (called on SOS deactivation).
 *
 * This replaces the old movement-based + heartbeat SMS approach with a
 * simple, predictable 5-minute interval.
 */
object LocationUpdateScheduler {

    private const val TAG = "MORPHUS_SOS"

    /** Interval between periodic location SMS updates. */
    private const val UPDATE_INTERVAL = 5 * 60 * 1000L // 5 minutes

    private var handler: Handler? = null
    private var runnable: Runnable? = null

    /** Whether the scheduler is currently running. */
    @Volatile
    var isRunning: Boolean = false
        private set

    /**
     * Starts the periodic location update scheduler.
     *
     * The first update fires after [UPDATE_INTERVAL] (5 min) since the
     * initial SOS SMS was already sent immediately.
     *
     * @param context Application or service context.
     * @param smsHandler The SMS handler instance to use for sending.
     * @param repository The data repository for emergency contacts.
     * @param locationTracker The tracker to read latest GPS fix from.
     */
    fun start(
        context: Context,
        smsHandler: SmsHandler,
        repository: AppRepository,
        locationTracker: LocationTracker?
    ) {
        // Clean up any existing scheduler first.
        stop()

        handler = Handler(Looper.getMainLooper())

        runnable = object : Runnable {
            override fun run() {
                Log.d(TAG, "Sending periodic location update")

                sendLocationUpdate(context, smsHandler, repository, locationTracker)

                // Schedule next update.
                handler?.postDelayed(this, UPDATE_INTERVAL)
            }
        }

        // First periodic update fires after 5 minutes.
        handler?.postDelayed(runnable!!, UPDATE_INTERVAL)
        isRunning = true

        Log.d(TAG, "Location update scheduler STARTED (interval=${UPDATE_INTERVAL / 1000}s)")
    }

    /**
     * Stops the periodic scheduler and cancels all pending updates.
     * Safe to call even if [start] was never called.
     */
    fun stop() {
        runnable?.let { handler?.removeCallbacks(it) }
        runnable = null
        handler = null
        isRunning = false

        Log.d(TAG, "Location updates stopped")
    }

    /**
     * Sends a single periodic location update SMS to all emergency contacts.
     */
    private fun sendLocationUpdate(
        context: Context,
        smsHandler: SmsHandler,
        repository: AppRepository,
        locationTracker: LocationTracker?
    ) {
        // Permission check
        if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.SEND_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "SEND_SMS not granted — skipping periodic update")
            return
        }

        val contacts = repository.getEmergencyContacts()
        if (contacts.isEmpty()) {
            Log.w(TAG, "No emergency contacts — skipping periodic update")
            return
        }

        val location = locationTracker?.lastLocation
        if (location != null) {
            smsHandler.sendSosSms(location, contacts)
            Log.d(TAG, "Periodic location SMS sent")
        } else {
            Log.w(TAG, "No location available for periodic update")
        }
    }
}
