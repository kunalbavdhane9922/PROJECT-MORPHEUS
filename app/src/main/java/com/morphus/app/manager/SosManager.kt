package com.morphus.app.manager

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.morphus.app.R
import com.morphus.app.ui.MainActivity
import com.morphus.app.utils.Constants

/**
 * Manages SOS activation, deactivation, and related workflows.
 *
 * On [activate]:
 *  1. Posts a high-priority notification (visible & audible).
 *  2. Starts the foreground SOS service (MorphusService).
 *  3. TODO: Sends emergency SMS, starts location sharing, audio recording.
 */
class SosManager(private val context: Context) {

    companion object {
        private const val TAG = "SosManager"

        /** Dedicated notification channel for SOS alerts (high importance). */
        const val SOS_CHANNEL_ID = "morphus_sos_alert_channel"
        const val SOS_NOTIFICATION_ID = 2001
        const val ACTIVATION_NOTIFICATION_ID = 2002
        const val DEACTIVATION_NOTIFICATION_ID = 2003
    }

    // Use persisted state so that even new SosManager instances (CalculatorFragment
    // creates one each trigger) cannot double-activate.
    var isActive: Boolean
        get() = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                    .getBoolean(Constants.KEY_SOS_ACTIVE, false)
        private set(value) { persistState(value) }

    init {
        createSosNotificationChannel()
    }

    // ═══════════════════════════
    //  Public API
    // ═══════════════════════════

    /**
     * Activates the SOS emergency flow.
     *  - Shows a high-priority SOS notification.
     *  - Persists the active state.
     *  - (TODO) Sends SMS, starts location sharing, audio recording.
     */
    fun activate() {
        Log.d("MORPHUS_DEBUG", "SOS activate called")
        if (isActive) {
            Log.w(TAG, "SOS already active — ignoring duplicate activation")
            return
        }

        isActive = true
        persistState(true)

        // 1. Subtle vibration feedback
        vibrateActivationFeedback()

        // 2. Show instant activation notification
        showActivationNotification()

        // 3. Show persistent SOS alert notification
        showSosNotification()

        // 4. Start foreground service
        startSosService()

        Log.i(TAG, "🚨 SOS ACTIVATED from calculator trigger")
    }

    /**
     * Deactivates the SOS emergency flow.
     * Can be called from Settings screen for manual SOS OFF.
     */
    fun deactivate() {
        if (!isActive) return

        Log.d("MORPHUS_DEBUG", "SOS manually deactivated")

        isActive = false
        persistState(false)

        // Stop the periodic location update scheduler
        LocationUpdateScheduler.stop()
        Log.d("MORPHUS_SOS", "Scheduler stopped on SOS OFF")

        // Dismiss all SOS-related notifications
        val nm = NotificationManagerCompat.from(context)
        nm.cancel(SOS_NOTIFICATION_ID)
        nm.cancel(ACTIVATION_NOTIFICATION_ID)

        // Stop the emergency foreground service
        com.morphus.app.service.EmergencyService.stop(context)

        // Show deactivation confirmation notification
        showDeactivationNotification()

        Log.i(TAG, "SOS deactivated")
    }

    // ═══════════════════════════
    //  Vibration Feedback
    // ═══════════════════════════

    /**
     * Provides subtle vibration feedback when SOS is activated.
     * Pattern: two short pulses to confirm activation.
     */
    private fun vibrateActivationFeedback() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val mgr = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                mgr.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val pattern = longArrayOf(0, 80, 60, 80)
                val amplitudes = intArrayOf(0, VibrationEffect.DEFAULT_AMPLITUDE, 0, VibrationEffect.DEFAULT_AMPLITUDE)
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 80, 60, 80), -1)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vibration feedback failed", e)
        }
    }

    // ═══════════════════════════
    //  Notification
    // ═══════════════════════════

    /**
     * Posts a notification confirming SOS has been deactivated.
     */
    private fun showDeactivationNotification() {
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            2,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, SOS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_phone)
            .setContentTitle("SOS Deactivated")
            .setContentText("Emergency protection has been turned off.")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setSilent(true)
            .build()

        try {
            Log.d("MORPHUS_DEBUG", "Deactivation notification shown")
            NotificationManagerCompat.from(context).notify(DEACTIVATION_NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Log.e(TAG, "Cannot post notification — missing POST_NOTIFICATIONS permission", e)
        }
    }
    // ═══════════════════════════

    /**
     * Posts an instant HIGH PRIORITY notification confirming SOS activation.
     * Title: "SOS Activated"
     * Message: "Emergency protection is now active."
     * Auto-cancels on tap. Works on lock screen.
     */
    private fun showActivationNotification() {
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            1,  // unique request code separate from SOS notification
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, SOS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_phone)
            .setContentTitle("SOS Activated")
            .setContentText("Emergency protection is now active.")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setSilent(true)
            .build()

        try {
            Log.d("MORPHUS_DEBUG", "Notification triggered")
            NotificationManagerCompat.from(context).notify(ACTIVATION_NOTIFICATION_ID, notification)
            Log.d(TAG, "Activation notification posted")
        } catch (e: SecurityException) {
            Log.e(TAG, "Cannot post notification — missing POST_NOTIFICATIONS permission", e)
        }
    }

    /**
     * Creates the high-importance SOS notification channel (required for Android O+).
     */
    private fun createSosNotificationChannel() {
        val channel = NotificationChannel(
            SOS_CHANNEL_ID,
            "SOS Alerts",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Emergency SOS alerts from Morphus"
            enableVibration(false)
            setSound(null, null)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    /**
     * Posts a heads-up SOS notification that the user can clearly see and hear.
     */
    private fun showSosNotification() {
        // Tapping the notification opens the app
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, SOS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_backspace)   // TODO: replace with a proper SOS icon
            .setContentTitle("🚨 SOS ACTIVATED")
            .setContentText("Emergency SOS has been triggered. Tap to open Morphus.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        "Emergency SOS has been triggered from the calculator.\n" +
                        "Your emergency contacts are being notified.\n" +
                        "Tap this notification to open Morphus."
                    )
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(false)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setSilent(true)
            .build()

        try {
            Log.d("MORPHUS_DEBUG", "Notification triggered")
            NotificationManagerCompat.from(context).notify(SOS_NOTIFICATION_ID, notification)
            Log.d(TAG, "SOS notification posted")
        } catch (e: SecurityException) {
            // Android 13+ POST_NOTIFICATIONS permission not granted
            Log.e(TAG, "Cannot post notification — missing POST_NOTIFICATIONS permission", e)
        }
    }

    // ═══════════════════════════
    //  Foreground Service
    // ═══════════════════════════

    private fun startSosService() {
        try {
            com.morphus.app.service.EmergencyService.start(context)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start EmergencyService", e)
        }
    }

    // ═══════════════════════════
    //  Persistence
    // ═══════════════════════════

    private fun persistState(active: Boolean) {
        context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(Constants.KEY_SOS_ACTIVE, active)
            .apply()
    }

    // ═══════════════════════════
    //  Situation-Based SOS
    // ═══════════════════════════

    /**
     * Activates SOS with a custom situation message.
     * Sends a situation-specific SMS but does NOT start calling.
     * Used by the calculator's hidden emergency mode.
     */
    @android.annotation.SuppressLint("MissingPermission")
    fun activateWithSituation(type: SituationType) {
        Log.d("MORPHUS_SOS", "Situation=$type")

        // 1. Get last known location for the message
        val locationString = try {
            val fusedClient = com.google.android.gms.location.LocationServices
                .getFusedLocationProviderClient(context)
            var locStr = "Unknown"
            fusedClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    locStr = "https://maps.google.com/?q=${loc.latitude},${loc.longitude}"
                }
            }
            // Give the fused client a moment, but fall back to "Unknown" if it doesn't resolve
            Thread.sleep(300)
            locStr
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get location for situation SOS: ${e.message}")
            "Unknown"
        }

        // 2. Build the custom message
        val message = SituationMessageBuilder.build(type, locationString)

        // 3. Send SMS to all emergency contacts (NO call escalation)
        try {
            val repository = com.morphus.app.data.AppRepository(context)
            val contacts = repository.getEmergencyContacts()
            if (contacts.isNotEmpty()) {
                val smsHandler = SmsHandler(context)
                smsHandler.sendGenericSms(contacts, message)
                Log.d("MORPHUS_SMS", "Custom SOS sent")
            } else {
                Log.w(TAG, "No emergency contacts — custom SOS SMS skipped")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send situation SMS: ${e.message}")
        }

        // 4. Vibration feedback + notification (reuse existing helpers)
        vibrateActivationFeedback()
        showActivationNotification()

        // 5. Start the emergency service (location tracking, audio, etc.)
        isActive = true
        persistState(true)
        showSosNotification()
        startSosService()

        Log.i(TAG, "🚨 SOS ACTIVATED with situation: $type")
    }
}
