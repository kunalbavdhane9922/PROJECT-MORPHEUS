package com.morphus.app.manager

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import androidx.core.content.ContextCompat
import com.morphus.app.data.AppRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Monitors network health during an emergency.
 * Sends a single "SIGNAL LOST" SMS if connectivity drops, then resumes
 * monitoring automatically when the network comes back.
 */
class NetworkGuardian(
    private val context: Context,
    private val smsHandler: SmsHandler,
    private val repository: AppRepository
) {

    companion object {
        private const val TAG = "NetworkGuardian"
    }

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private var isEmergencyMode = false
    private var lastLocation: LocationTracker.LocationData? = null
    private var signalLostSent = false
    private var isRegistered = false

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onLost(network: Network) {
            super.onLost(network)
            Log.d("MORPHUS_NETWORK", "Internet LOST")
            if (isEmergencyMode && !signalLostSent) {
                Log.w(TAG, "Network LOST during emergency — sending Signal Lost SMS")
                sendSignalLostSms()
                signalLostSent = true
            }
        }

        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            Log.d("MORPHUS_NETWORK", "Internet AVAILABLE")
            onInternetAvailable()
        }
    }

    // ── Public API ───────────────────────────────────────────────────────────

    fun start(active: Boolean, location: LocationTracker.LocationData?) {
        isEmergencyMode = active
        lastLocation = location
        signalLostSent = false

        if (isRegistered) return // guard against duplicate registration

        // Permission safety check
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_NETWORK_STATE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("MORPHUS_NETWORK", "ACCESS_NETWORK_STATE missing")
            return
        }

        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, networkCallback)
            isRegistered = true
            Log.d("MORPHUS_NETWORK", "Network monitoring started")
        } catch (e: Exception) {
            Log.e("MORPHUS_NETWORK", "Failed to register network callback: ${e.message}")
        }
    }

    fun stop() {
        isEmergencyMode = false
        if (!isRegistered) return
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            // already unregistered — safe to ignore
        }
        isRegistered = false
        Log.d(TAG, "Network monitoring stopped")
    }

    fun updateLocation(location: LocationTracker.LocationData) {
        lastLocation = location
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private fun onInternetAvailable() {
        Log.d("MORPHUS_NETWORK", "Triggering upload queue")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                com.morphus.app.network.UploadQueueManager(context).processQueue()
            } catch (e: Exception) {
                Log.e(TAG, "Queue flush failed: ${e.message}")
            }
        }
    }

    private fun sendSignalLostSms() {
        val contacts = repository.getEmergencyContacts()
        if (contacts.isEmpty()) return

        val lat = lastLocation?.latitude ?: 0.0
        val lon = lastLocation?.longitude ?: 0.0
        val mapsUrl = lastLocation?.toMapsUrl() ?: "Unknown"

        val message =
            "⚠ SIGNAL LOST! Last known location: $lat, $lon. $mapsUrl. Connection dropped."

        try {
            smsHandler.sendGenericSms(contacts, message)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send signal-lost SMS: ${e.message}")
        }
    }
}
