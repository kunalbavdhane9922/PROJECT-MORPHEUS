package com.morphus.app.service

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.morphus.app.R
import com.morphus.app.data.SettingsManager
import com.morphus.app.manager.AudioRecorder
import com.morphus.app.manager.CallManager
import com.morphus.app.manager.LocationTracker
import com.morphus.app.manager.NetworkGuardian
import com.morphus.app.manager.SmsHandler
import com.morphus.app.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Foreground SOS Emergency Service
 *
 * Designed for maximum survival:
 * - Foreground service (Android requirement)
 * - Partial WakeLock (CPU stays alive)
 * - START_STICKY recovery
 * - Safe restart handling
 */
class EmergencyService : Service() {

    companion object {

        private const val TAG = "EmergencyService"

        private const val CHANNEL_ID = "morphus_emergency_channel"
        private const val NOTIFICATION_ID = 3001
        private const val WAKE_LOCK_TAG = "morphus:emergency_wakelock"

        private const val ACTION_START =
            "com.morphus.app.action.START_EMERGENCY"

        private const val ACTION_STOP =
            "com.morphus.app.action.STOP_EMERGENCY"

        private const val BATTERY_CRITICAL_NOTIFICATION_ID = 3002

        /** Internal power saving mode — reduces GPS frequency, disables non-essential features. */
        @JvmStatic
        @Volatile
        var powerSavingMode = false
            private set

        // ───────── PUBLIC API ─────────

        @JvmStatic
        fun start(context: Context) {
            val intent = Intent(context, EmergencyService::class.java).apply {
                action = ACTION_START
            }
            Log.d("MORPHUS_DEBUG", "EmergencyService.start() called — launching foreground service")
            ContextCompat.startForegroundService(context, intent)
        }

        @JvmStatic
        fun stop(context: Context) {
            val intent = Intent(context, EmergencyService::class.java).apply {
                action = ACTION_STOP
            }
            ContextCompat.startForegroundService(context, intent)
        }

        /**
         * Enables internal power saving mode.
         * Reduces GPS frequency and shows persistent battery-critical notification.
         */
        @JvmStatic
        fun enablePowerSavingMode(context: Context) {
            powerSavingMode = true
            Log.i("MORPHUS_POWER", "Power Saving Mode ENABLED")

            // Show persistent emergency notification
            try {
                val channelId = "morphus_emergency_channel"
                val notification = NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(R.drawable.ic_backspace)
                    .setContentTitle("Emergency Mode Active")
                    .setContentText("Battery critical — safety tracking enabled")
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_STATUS)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setOngoing(true)
                    .build()

                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.notify(BATTERY_CRITICAL_NOTIFICATION_ID, notification)
                Log.d("MORPHUS_POWER", "Battery critical notification shown")
            } catch (e: Exception) {
                Log.e("MORPHUS_POWER", "Failed to show battery notification: ${e.message}")
            }
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var locationTracker: LocationTracker? = null
    private var emergencyActive = false

    private lateinit var settingsManager: SettingsManager
    private lateinit var repository: com.morphus.app.data.AppRepository

    private lateinit var smsHandler: SmsHandler
    private lateinit var audioRecorder: AudioRecorder
    private lateinit var callManager: CallManager
    private lateinit var networkGuardian: NetworkGuardian

    private var sosSmsSent = false
    private var lastSmsLocation: LocationTracker.LocationData? = null
    private var lastSmsTime = 0L
    private var isBatteryCritical = false

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            if (emergencyActive) {
                checkAndSendHeartbeat()
                val interval = settingsManager.updateInterval * 60 * 1000L
                handler.postDelayed(this, if (isBatteryCritical) interval * 2 else interval)
            }
        }
    }

    private val batteryReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            if (level in 0..5 && !isBatteryCritical) {
                isBatteryCritical = true
                sendBatteryCriticalSms()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        settingsManager = SettingsManager(this)
        repository = com.morphus.app.data.AppRepository(this)
        smsHandler = SmsHandler(this)
        audioRecorder = AudioRecorder(this)
        callManager = CallManager(this)
        networkGuardian = NetworkGuardian(this, smsHandler, repository)

        val intentFilter = android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(batteryReceiver, intentFilter)
        Log.d(TAG, "Service created")
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        // System restart case — must call startForeground immediately
        if (intent == null || intent.action == null) {
            Log.w(TAG, "Service restarted by system — re-enter foreground")
            startForeground(NOTIFICATION_ID, buildNotification())
            if (!emergencyActive) startEmergency()
            return START_STICKY
        }

        when (intent.action) {

            ACTION_STOP -> {
                Log.i(TAG, "Emergency STOP received")
                stopEmergency()
                return START_NOT_STICKY
            }

            ACTION_START -> {
                if (!emergencyActive) {
                    Log.i(TAG, "🚨 Emergency STARTED")
                    Log.d("MORPHUS_DEBUG", "EmergencyService started")
                    startForeground(
                        NOTIFICATION_ID,
                        buildNotification()
                    )
                    startEmergency()
                }
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopEmergency()
        releaseWakeLock()
        try { unregisterReceiver(batteryReceiver) } catch (_: Exception) {}
        Log.i(TAG, "Service destroyed")
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.w(TAG, "Task removed — service survives (START_STICKY)")
        super.onTaskRemoved(rootIntent)
    }

    // ═══════════════════════════════
    // Emergency Logic
    // ═══════════════════════════════

    private fun startEmergency() {

        emergencyActive = true
        sosSmsSent = false
        isBatteryCritical = false

        acquireWakeLock()

        // Start location tracking
        locationTracker = LocationTracker(
            context = this,
            onLocation = { data ->
                processLocationUpdate(data)
            },
            onGpsDisabled = {
                Log.w(TAG, "GPS disabled during emergency")
            }
        )

        locationTracker?.startTracking()

        if (settingsManager.isAudioRecordEnabled) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
                audioRecorder.startRecording()
                Log.d("MORPHUS_RECORD", "Audio recording initiated from service")
            } else {
                Log.e("MORPHUS_RECORD", "RECORD_AUDIO permission not granted — skipping recording")
            }
        }

        networkGuardian.start(true, null)
        handler.post(heartbeatRunnable)
        
        // Initial call escalation — sequential through all contacts
        if (settingsManager.isAutoCallEnabled) {
            val contacts = repository.getEmergencyContacts()
            if (contacts.isNotEmpty() && ContextCompat.checkSelfPermission(
                    this, Manifest.permission.CALL_PHONE
                ) == PackageManager.PERMISSION_GRANTED) {
                Log.i("MORPHUS_CALL", "Emergency call queue started")
                callManager.startCallEscalation(contacts)
            }
        }
    }

    private fun processLocationUpdate(data: LocationTracker.LocationData?) {
        if (data == null) return
        Log.d(TAG, "📍 ${data.toFormattedString()}")
        networkGuardian.updateLocation(data)

        val contacts = repository.getEmergencyContacts()
        if (contacts.isEmpty()) return

        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.SEND_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        // 1. Initial SOS SMS
        if (!sosSmsSent) {
            smsHandler.sendSosSms(data, contacts)
            sosSmsSent = true
            lastSmsLocation = data
            lastSmsTime = System.currentTimeMillis()
            return
        }

        // 2. Movement-based tracking
        val lastLoc = lastSmsLocation
        if (lastLoc != null) {
            val results = FloatArray(1)
            android.location.Location.distanceBetween(
                lastLoc.latitude, lastLoc.longitude,
                data.latitude, data.longitude,
                results
            )
            val threshold = settingsManager.movementThreshold.toFloat()
            if (results[0] > threshold) {
                Log.i(TAG, "Movement detected: ${results[0]}m > ${threshold}m. Sending update.")
                smsHandler.sendSosSms(data, contacts)
                lastSmsLocation = data
                lastSmsTime = System.currentTimeMillis()
            }
        }
    }

    private fun checkAndSendHeartbeat() {
        val now = System.currentTimeMillis()
        val intervalMs = settingsManager.updateInterval * 60 * 1000L
        
        if (now - lastSmsTime >= intervalMs) {
            val contacts = repository.getEmergencyContacts()
            val lastKnown = lastSmsLocation
            if (contacts.isNotEmpty() && lastKnown != null) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "Heartbeat interval reached (${settingsManager.updateInterval} min).")
                    smsHandler.sendSosSms(lastKnown, contacts)
                    lastSmsTime = now
                }
            }
        }
    }

    private fun sendBatteryCriticalSms() {
        val contacts = repository.getEmergencyContacts()
        val lastKnown = lastSmsLocation
        if (contacts.isNotEmpty()) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                val lat = lastKnown?.latitude ?: 0.0
                val lon = lastKnown?.longitude ?: 0.0
                val maps = lastKnown?.toMapsUrl() ?: "Unknown"
                val message = "BATTERY CRITICAL (<=5%). SOS potentially going dead. Last location: $lat, $lon. $maps"
                
                smsHandler.sendGenericSms(contacts, message)
            }
        }
    }

    private fun getBatteryLevel(): Int {
        val bm = getSystemService(BatteryManager::class.java)
        return bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
    }

    private fun stopEmergency() {

        if (!emergencyActive) return

        emergencyActive = false
        handler.removeCallbacks(heartbeatRunnable)
        
        if (audioRecorder.isRecording) {
            audioRecorder.stopRecording()
        }

        // ── Supabase: Queue incident for upload (offline-safe) ──
        try {
            val audioFile = audioRecorder.currentFile
            val lastLoc = lastSmsLocation
            val battery = getBatteryLevel()
            val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                .format(Date())

            val incident = com.morphus.app.network.IncidentData(
                timestamp = timestamp,
                latitude = lastLoc?.latitude ?: 0.0,
                longitude = lastLoc?.longitude ?: 0.0,
                batteryLevel = battery,
                audioFilePath = audioFile?.absolutePath
            )
            val queueManager = com.morphus.app.network.UploadQueueManager(this)
            queueManager.enqueue(incident)
            CoroutineScope(Dispatchers.IO).launch {
                queueManager.processQueue()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Supabase enqueue failed (non-fatal)", e)
        }
        // ── End Supabase block ──
        
        networkGuardian.stop()
        locationTracker?.stopTracking()
        locationTracker = null

        // Send 'I am safe' message
        val contacts = repository.getEmergencyContacts()
        if (contacts.isNotEmpty()) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                try {
                    smsHandler.sendGenericSms(contacts, "I AM SAFE. The SOS emergency has been deactivated.")
                } catch (e: SecurityException) {
                    Log.e(TAG, "SecurityException sending safe SMS", e)
                }
            }
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()

        Log.i(TAG, "Emergency stopped & SAFE message sent")
    }

    // ═══════════════════════════════
    // Notification
    // ═══════════════════════════════

    private fun createNotificationChannel() {

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Emergency Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Active emergency protection"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_SECRET
        }

        val manager =
            getSystemService(NotificationManager::class.java)

        manager?.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {

        val intent = Intent(this, MainActivity::class.java).apply {
            flags =
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_location)
            .setContentTitle("Morphus")
            .setContentText("Protection active")
            .setOngoing(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setContentIntent(pendingIntent)
            .build()
    }

    // ═══════════════════════════════
    // WakeLock Handling
    // ═══════════════════════════════

    private fun acquireWakeLock() {

        if (wakeLock?.isHeld == true) return

        val pm = getSystemService(PowerManager::class.java)

        wakeLock = pm?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            WAKE_LOCK_TAG
        )

        wakeLock?.acquire(60 * 60 * 1000L) // 1-hour timeout safety

        Log.d(TAG, "WakeLock acquired")
    }

    private fun releaseWakeLock() {

        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d(TAG, "WakeLock released")
            }
        }

        wakeLock = null
    }
}
