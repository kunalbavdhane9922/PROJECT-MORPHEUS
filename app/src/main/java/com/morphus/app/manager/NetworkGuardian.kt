package com.morphus.app.manager

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.morphus.app.data.AppRepository
import com.morphus.app.utils.Constants

/**
 * Advanced network health monitor for emergency scenarios.
 *
 * **Trigger logic (prevents false positives):**
 * 1. [ConnectivityManager.NetworkCallback] detects `onLost`.
 * 2. A 30-second debounce timer starts. If connectivity returns within
 *    that window the timer is cancelled — no trigger.
 * 3. After 30 s the current cellular signal strength is checked via
 *    [TelephonyManager]. If signal is weak (< −110 dBm) OR the network
 *    type is [TelephonyManager.NETWORK_TYPE_UNKNOWN], the offline
 *    condition is confirmed.
 * 4. The [isOfflineModeActive] flag is set so the trigger fires only once.
 * 5. When `onAvailable` fires, the flag resets and [Listener.onOnlineModeRestored]
 *    is invoked.
 *
 * **Low Network Alert (new):**
 * - `onCapabilitiesChanged` detects when the network loses `NET_CAPABILITY_VALIDATED`.
 * - Sends an SMS alert with last known location to emergency contacts.
 * - 10-minute cooldown between alerts to prevent spam.
 * - `onLost` sends an immediate "Network lost" SMS.
 *
 * Usage inside [EmergencyService]:
 * ```kotlin
 * val guardian = NetworkGuardian(context, smsHandler, repository)
 * guardian.start(true, null)
 * // …later…
 * guardian.stop()
 * ```
 */
class NetworkGuardian(
    private val context: Context,
    private val smsHandler: SmsHandler,
    private val repository: AppRepository
) {

    // ── Listener contract ────────────────────────────────────────────────────

    /**
     * Callback interface implemented by [EmergencyService] to react to
     * offline/online transitions.
     */
    interface Listener {
        /** Called once when offline mode is confirmed (after debounce + signal check). */
        fun onOfflineModeTriggered()

        /** Called when internet connectivity is restored after an offline period. */
        fun onOnlineModeRestored()
    }

    companion object {
        private const val TAG = "NetworkGuardian"

        /** Signal strength (dBm) at or below which we consider the signal "weak". */
        private const val WEAK_SIGNAL_DBM = -110

        /** Minimum interval between low-network SMS alerts (10 minutes). */
        private const val ALERT_COOLDOWN = 10 * 60 * 1000L
    }

    // ── System services ──────────────────────────────────────────────────────

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val telephonyManager =
        context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

    // ── State ────────────────────────────────────────────────────────────────

    /** Whether the offline-mode SMS workflow is currently active. */
    @Volatile
    var isOfflineModeActive: Boolean = false
        private set

    /** Whether the NetworkCallback is currently registered. */
    private var isRegistered = false

    /** Latest signal strength reading in dBm (updated by telephony listener). */
    @Volatile
    private var lastSignalDbm: Int = 0

    /** Handler used for the debounce timer. Runs on main looper. */
    private val handler = Handler(Looper.getMainLooper())

    /** True while the debounce countdown is in flight. */
    private var debounceScheduled = false

    /** Optional listener for offline/online lifecycle events. */
    private var listener: Listener? = null

    // ── Low Network Alert state ──────────────────────────────────────────────

    /** Timestamp of the last low-network alert SMS sent. */
    @Volatile
    private var lastAlertSent = 0L

    /** Most recent location update received from LocationTracker via EmergencyService. */
    @Volatile
    private var lastLocation: LocationTracker.LocationData? = null

    // ── Telephony listener (signal strength) ─────────────────────────────────

    /**
     * Legacy [PhoneStateListener] for API < 31.
     * Reads the overall signal level and converts it to an approximate dBm.
     */
    @Suppress("DEPRECATION")
    private val legacySignalListener = object : PhoneStateListener() {
        override fun onSignalStrengthsChanged(signalStrength: SignalStrength?) {
            super.onSignalStrengthsChanged(signalStrength)
            signalStrength?.let { ss ->
                lastSignalDbm = extractDbm(ss)
                Log.d(TAG, "Signal strength updated (legacy): $lastSignalDbm dBm")
            }
        }
    }

    /**
     * Modern [TelephonyCallback] for API 31+.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private inner class SignalStrengthCallback : TelephonyCallback(),
        TelephonyCallback.SignalStrengthsListener {
        override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
            lastSignalDbm = extractDbm(signalStrength)
            Log.d(TAG, "Signal strength updated (modern): $lastSignalDbm dBm")
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private var modernCallback: SignalStrengthCallback? = null

    // ── Network callback ─────────────────────────────────────────────────────

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {

        override fun onCapabilitiesChanged(
            network: Network,
            capabilities: NetworkCapabilities
        ) {
            val isWeak =
                !capabilities.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_VALIDATED
                )

            Log.d("MORPHUS_NETWORK", "WeakNetwork=$isWeak")

            if (isWeak) {
                handleWeakNetwork()
            }
        }

        override fun onLost(network: Network) {
            super.onLost(network)
            Log.w("MORPHUS_NETWORK", "Network LOST")

            // ── Low Network Alert: immediate SMS on network loss ──
            sendNetworkLostAlert()

            // ── Existing offline mode debounce logic ──
            Log.w(TAG, "Network LOST — starting ${Constants.OFFLINE_DEBOUNCE_MS / 1000}s debounce")

            if (isOfflineModeActive) {
                Log.d(TAG, "Already in offline mode — ignoring duplicate onLost")
                return
            }

            // Start debounce countdown — only trigger if loss persists.
            if (!debounceScheduled) {
                debounceScheduled = true
                handler.postDelayed(debounceRunnable, Constants.OFFLINE_DEBOUNCE_MS)
            }
        }

        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            Log.i(TAG, "Network AVAILABLE")

            // Cancel pending debounce (brief drop — no false trigger).
            cancelDebounce()

            if (isOfflineModeActive) {
                Log.i(TAG, "Restoring ONLINE mode")
                isOfflineModeActive = false
                listener?.onOnlineModeRestored()
            }
        }
    }

    // ── Debounce runnable ────────────────────────────────────────────────────

    /**
     * Fired 30 seconds after connectivity loss. Confirms the offline
     * condition by cross-checking signal strength before triggering.
     */
    private val debounceRunnable = Runnable {
        debounceScheduled = false

        if (isOfflineModeActive) return@Runnable

        // Double-check: is connectivity still absent?
        val activeNetwork = connectivityManager.activeNetwork
        val caps = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
        val hasInternet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        if (hasInternet) {
            Log.d(TAG, "Debounce fired but internet has returned — aborting trigger")
            return@Runnable
        }

        // Check signal strength for additional confidence.
        val signalWeak = lastSignalDbm <= WEAK_SIGNAL_DBM || lastSignalDbm == 0
        val networkType = try {
            telephonyManager?.dataNetworkType ?: TelephonyManager.NETWORK_TYPE_UNKNOWN
        } catch (e: SecurityException) {
            TelephonyManager.NETWORK_TYPE_UNKNOWN
        }
        val networkUnavailable = networkType == TelephonyManager.NETWORK_TYPE_UNKNOWN

        Log.d(TAG, "Debounce check — signalDbm=$lastSignalDbm, weak=$signalWeak, " +
                "networkType=$networkType, unavailable=$networkUnavailable")

        if (signalWeak || networkUnavailable) {
            Log.w(TAG, "⚠️ OFFLINE MODE CONFIRMED — triggering listener")
            isOfflineModeActive = true
            listener?.onOfflineModeTriggered()
        } else {
            Log.d(TAG, "Signal still adequate ($lastSignalDbm dBm) — not triggering offline mode")
        }
    }

    // ── Low Network Alert functions ──────────────────────────────────────────

    /**
     * Handles weak network detection by sending an SMS alert to emergency
     * contacts with the last known location.
     *
     * Respects a 10-minute cooldown to prevent alert spam during
     * intermittent connectivity.
     */
    private fun handleWeakNetwork() {
        val now = System.currentTimeMillis()

        if (now - lastAlertSent < ALERT_COOLDOWN) return

        lastAlertSent = now

        val location = lastLocation?.let {
            "${it.toFormattedString()}\n${it.toMapsUrl()}"
        } ?: "Location unavailable"

        val message =
            "⚠ Network signal becoming weak.\n" +
            "Last known location:\n$location"

        sendAlertSms(message)

        Log.i("MORPHUS_NETWORK", "Low network alert SMS sent")
    }

    /**
     * Sends an immediate alert when network is completely lost.
     */
    private fun sendNetworkLostAlert() {
        val location = lastLocation?.let {
            "${it.toFormattedString()}\n${it.toMapsUrl()}"
        } ?: "Location unavailable"

        val message =
            "\uD83D\uDCF5 Network lost.\nLast known location:\n$location"

        sendAlertSms(message)
    }

    /**
     * Helper to send an alert SMS to all emergency contacts.
     * Checks SEND_SMS permission before sending.
     */
    private fun sendAlertSms(message: String) {
        if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.SEND_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "SEND_SMS not granted — cannot send network alert")
            return
        }

        val contacts = repository.getEmergencyContacts()
        if (contacts.isEmpty()) {
            Log.w(TAG, "No emergency contacts — skipping network alert SMS")
            return
        }

        try {
            smsHandler.sendGenericSms(contacts, message)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send network alert SMS: ${e.message}")
        }
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Updates the last known location from the LocationTracker.
     * Called by EmergencyService on each location fix.
     */
    fun updateLocation(data: LocationTracker.LocationData) {
        lastLocation = data
    }

    /**
     * Registers the network callback and starts monitoring signal strength.
     * Safe to call multiple times — duplicates are guarded.
     *
     * @param discoverable Whether the device should be discoverable (reserved for future use).
     * @param initialLocation Optional initial location to seed the tracker.
     */
    fun start(
        discoverable: Boolean = true,
        initialLocation: LocationTracker.LocationData? = null
    ) {
        if (isRegistered) {
            Log.w(TAG, "Already monitoring — ignoring duplicate start()")
            return
        }

        // Seed initial location if provided.
        initialLocation?.let { lastLocation = it }

        // Permission check for network state.
        if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_NETWORK_STATE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "ACCESS_NETWORK_STATE not granted — cannot monitor network")
            return
        }

        // ── Register network callback ──
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, networkCallback)
            isRegistered = true
            Log.i(TAG, "Network monitoring STARTED (discoverable=$discoverable)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register NetworkCallback: ${e.message}")
            return
        }

        // ── Register telephony signal listener ──
        registerSignalListener()
    }

    /**
     * Unregisters all callbacks, cancels timers, and resets state.
     * Safe to call even if [start] was never called.
     */
    fun stop() {
        cancelDebounce()

        if (isRegistered) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback)
            } catch (_: Exception) { /* already unregistered */ }
            isRegistered = false
        }

        unregisterSignalListener()

        isOfflineModeActive = false
        lastAlertSent = 0L
        lastLocation = null
        Log.i(TAG, "Network monitoring STOPPED")
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun cancelDebounce() {
        if (debounceScheduled) {
            handler.removeCallbacks(debounceRunnable)
            debounceScheduled = false
            Log.d(TAG, "Debounce timer CANCELLED (connectivity returned in time)")
        }
    }

    /**
     * Registers the appropriate telephony listener for signal strength
     * changes, depending on the API level.
     */
    @Suppress("DEPRECATION")
    private fun registerSignalListener() {
        if (telephonyManager == null) {
            Log.w(TAG, "TelephonyManager unavailable — signal monitoring skipped")
            return
        }

        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            Log.w(TAG, "READ_PHONE_STATE not granted — signal monitoring limited")
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                modernCallback = SignalStrengthCallback()
                telephonyManager.registerTelephonyCallback(
                    context.mainExecutor, modernCallback!!
                )
                Log.d(TAG, "Modern TelephonyCallback registered (API 31+)")
            } else {
                telephonyManager.listen(
                    legacySignalListener,
                    PhoneStateListener.LISTEN_SIGNAL_STRENGTHS
                )
                Log.d(TAG, "Legacy PhoneStateListener registered (API < 31)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register signal listener: ${e.message}")
        }
    }

    @Suppress("DEPRECATION")
    private fun unregisterSignalListener() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                modernCallback?.let { telephonyManager?.unregisterTelephonyCallback(it) }
                modernCallback = null
            } else {
                telephonyManager?.listen(
                    legacySignalListener,
                    PhoneStateListener.LISTEN_NONE
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering signal listener: ${e.message}")
        }
    }

    /**
     * Extracts the best available dBm reading from a [SignalStrength]
     * object across all available cell info groups.
     */
    private fun extractDbm(ss: SignalStrength): Int {
        // API 29+ provides per-cell-signal-strength objects.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val cellSignals = ss.cellSignalStrengths
            if (cellSignals.isNotEmpty()) {
                // Return the strongest (least negative) reading.
                return cellSignals.maxOf { it.dbm }
            }
        }

        // Fallback: derive from the 0-4 level.
        // Level 0 → roughly −113 dBm, Level 4 → roughly −51 dBm.
        val level = ss.level  // 0..4
        return when (level) {
            0    -> -113
            1    -> -103
            2    -> -93
            3    -> -73
            else -> -51
        }
    }
}
