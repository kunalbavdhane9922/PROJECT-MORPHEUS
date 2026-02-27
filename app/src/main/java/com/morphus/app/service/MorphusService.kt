package com.morphus.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.morphus.app.R
import com.morphus.app.manager.LocationBroadcaster
import com.morphus.app.manager.LocationTracker
import com.morphus.app.ui.MainActivity
import com.morphus.app.utils.Constants

/**
 * Core foreground service for Morphus.
 *
 * Owns the lifecycle of [LocationTracker] so that continuous location updates
 * run for as long as the service is alive. Each fix is:
 *  1. Stored via [LocationTracker.lastLocation] (accessible anywhere that has
 *     a reference to the tracker, e.g. the SOS subsystem).
 *  2. Broadcast app-wide via [LocationBroadcaster] so any Fragment / Activity
 *     can observe it without coupling to this service directly.
 *  3. Reflected in the persistent foreground notification for transparency.
 *
 * Start / stop via:
 * ```kotlin
 * MorphusService.start(context)
 * MorphusService.stop(context)
 * ```
 */
class MorphusService : Service() {

    companion object {
        private const val TAG             = "MorphusService"
        const val CHANNEL_ID              = "morphus_service_channel"
        const val NOTIFICATION_ID         = 1001

        // ── Public start/stop helpers ─────────────────────────────────────

        fun start(context: Context) {
            context.startForegroundService(Intent(context, MorphusService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MorphusService::class.java))
        }
    }

    private var locationTracker: LocationTracker? = null

    // ── Service lifecycle ─────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.d(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "Service started")

        // Move to foreground immediately with the initial notification.
        startForeground(NOTIFICATION_ID, buildNotification(locationLine = null))

        startLocationTracking()

        // START_STICKY → OS will restart the service if it is killed.
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopLocationTracking()
        Log.i(TAG, "Service destroyed")
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.w(TAG, "Task removed — service will be re-created (START_STICKY)")
        super.onTaskRemoved(rootIntent)
    }

    // ── Location tracking ─────────────────────────────────────────────────────

    private fun startLocationTracking() {
        if (locationTracker != null) return   // already running

        locationTracker = LocationTracker(
            context = this,
            onLocation = { data ->
                Log.d(TAG, "📍 ${data.toFormattedString()}")

                // 1. Broadcast within the process for any UI observers.
                LocationBroadcaster.send(this, data)

                // 2. Refresh notification so the user can see the live position.
                updateNotification(data)
            },
            onGpsDisabled = {
                Log.w(TAG, "GPS is currently disabled — showing last-known fix until GPS returns")
                // Optionally: surface a notification action to open GPS settings.
                // For now, LocationTracker will still emit a last-known fix if fresh enough.
            }
        )
        locationTracker?.startTracking()
        Log.i(TAG, "Location tracking started")
    }

    private fun stopLocationTracking() {
        locationTracker?.stopTracking()
        locationTracker = null
        Log.i(TAG, "Location tracking stopped")
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Morphus Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Morphus background service — location tracking active"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    /**
     * Rebuilds and posts the notification with the latest location line.
     * Called from the location callback thread; [NotificationManager.notify]
     * is thread-safe.
     */
    private fun updateNotification(data: LocationTracker.LocationData) {
        val locationLine =
            "%.5f, %.5f  •  %.1f m/s".format(data.latitude, data.longitude, data.speed)
        val notification = buildNotification(locationLine)
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, notification)
    }

    /**
     * Builds the persistent foreground notification.
     *
     * @param locationLine Optional second content line showing live coords.
     *                     `null` → shows "Waiting for GPS fix…".
     */
    private fun buildNotification(locationLine: String?): Notification {
        val openApp = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            this, 0, openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_location)
            .setContentTitle("Morphus Active")
            .setContentText(locationLine ?: "Waiting for GPS fix…")
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .build()
    }
}
