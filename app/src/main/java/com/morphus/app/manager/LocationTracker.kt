package com.morphus.app.manager

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.morphus.app.service.EmergencyService
import com.morphus.app.utils.Constants

/**
 * Continuous location tracker powered by [FusedLocationProviderClient].
 *
 * Features:
 *  - 10-second update interval (fastest: 5 s).
 *  - Captures latitude, longitude, speed, and bearing.
 *  - Runs callbacks on a dedicated background [HandlerThread] — safe for use
 *    inside a foreground service without touching the main looper.
 *  - Falls back to last-known location when GPS is disabled; refuses stale
 *    fixes older than [Constants.LOCATION_STALE_THRESHOLD_MS].
 *  - Optional [onGpsDisabled] callback to prompt the user to enable GPS.
 *
 * Usage:
 * ```kotlin
 * val tracker = LocationTracker(
 *     context       = this,
 *     onLocation    = { data -> /* handle fix */ },
 *     onGpsDisabled = { /* show GPS settings dialog */ }
 * )
 * tracker.startTracking()
 * // ... later
 * tracker.stopTracking()
 * ```
 */
class LocationTracker(
    private val context: Context,
    /** Invoked on every new location fix (background thread). */
    private val onLocation: ((LocationData) -> Unit)? = null,
    /** Invoked once when tracking starts and GPS is found to be disabled. */
    private val onGpsDisabled: (() -> Unit)? = null
) {

    companion object {
        private const val TAG                 = "LocationTracker"
        private const val UPDATE_INTERVAL_MS  = 10_000L   // 10 seconds
        private const val FASTEST_INTERVAL_MS  = 5_000L   //  5 seconds
        private const val HANDLER_THREAD_NAME = "LocationTrackerThread"
    }

    // ── Data model ────────────────────────────────────────────────────────────

    /** Lightweight snapshot of a single location fix. */
    data class LocationData(
        val latitude:  Double,
        val longitude: Double,
        val speed:     Float,     // m/s — 0 if unavailable
        val bearing:   Float,     // degrees — 0 if unavailable
        val accuracy:  Float,     // metres
        val timestamp: Long       // UTC epoch millis
    ) {
        /** Human-readable string suitable for SMS / Logcat. */
        fun toFormattedString(): String =
            "Lat: $latitude, Lon: $longitude, " +
            "Speed: ${"%.1f".format(speed)} m/s, " +
            "Bearing: ${"%.0f".format(bearing)}°, " +
            "Accuracy: ${"%.0f".format(accuracy)} m"

        /** Google Maps deep-link for quick sharing. */
        fun toMapsUrl(): String = "https://maps.google.com/?q=$latitude,$longitude"
    }

    // ── Internal state ────────────────────────────────────────────────────────

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    /**
     * Background [HandlerThread] that receives callbacks from the Fused API.
     * Using a dedicated thread avoids any work on the main looper, which is
     * critical when this tracker runs inside a foreground service.
     */
    private var handlerThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    /** Most recent fix. Thread-safe via @Volatile — written on handler thread,
     *  read from any thread. */
    @Volatile
    var lastLocation: LocationData? = null
        private set

    /** Whether the tracker is currently requesting updates. Read from any thread. */
    @Volatile
    var isTracking: Boolean = false
        private set

    // ── First-fix callback ────────────────────────────────────────────────────

    /** Invoked exactly once when the first valid GPS fix arrives. */
    var firstFixListener: (() -> Unit)? = null

    /** Guards single delivery of the first-fix callback. */
    @Volatile
    private var firstFixDelivered = false

    /** Returns true if at least one valid location has been received. */
    fun hasValidLocation(): Boolean = lastLocation != null

    // ── Location callback ─────────────────────────────────────────────────────

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            val data = LocationData(
                latitude  = loc.latitude,
                longitude = loc.longitude,
                speed     = loc.speed,
                bearing   = loc.bearing,
                accuracy  = loc.accuracy,
                timestamp = loc.time
            )
            lastLocation = data

            // ── First-fix notification ──
            if (!firstFixDelivered) {
                firstFixDelivered = true
                Log.d("MORPHUS_LOCATION", "First GPS fix received")
                firstFixListener?.invoke()
            }

            onLocation?.invoke(data)
            Log.d(TAG, "📍 ${data.toFormattedString()}")
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Starts continuous location updates on a background [HandlerThread].
     *
     * - No-ops if already tracking.
     * - Returns immediately if location permission is not granted.
     * - If GPS is disabled, invokes [onGpsDisabled] and falls back to the last
     *   cached fix before registering for future provider updates.
     */
    @SuppressLint("MissingPermission")
    fun startTracking() {
        if (isTracking) {
            Log.w(TAG, "Already tracking — ignoring duplicate start()")
            return
        }
        if (!hasLocationPermission()) {
            Log.e(TAG, "ACCESS_FINE_LOCATION not granted — cannot start tracking")
            return
        }

        // Start the background thread that will handle location callbacks.
        handlerThread = HandlerThread(HANDLER_THREAD_NAME).also { it.start() }
        backgroundHandler = Handler(handlerThread!!.looper)

        // Notify callers if GPS hardware is currently off.
        if (!isGpsEnabled()) {
            Log.w(TAG, "GPS disabled → invoking onGpsDisabled callback")
            onGpsDisabled?.invoke()
            // Attempt a last-known fix as an immediate data point.
            fetchLastKnownLocation()
        }

        val interval = if (EmergencyService.powerSavingMode) 30_000L else UPDATE_INTERVAL_MS
        val fastestInterval = if (EmergencyService.powerSavingMode) 15_000L else FASTEST_INTERVAL_MS
        Log.d("MORPHUS_POWER", "GPS interval=${interval}ms (powerSaving=${EmergencyService.powerSavingMode})")

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, interval)
            .setMinUpdateIntervalMillis(fastestInterval)
            .setWaitForAccurateLocation(false)   // don't stall; emit best available
            .build()

        // Register the callback on the background looper — NOT the main looper.
        fusedClient.requestLocationUpdates(request, locationCallback, handlerThread!!.looper)
        isTracking = true
        Log.i(TAG, "Tracking started (interval=${interval} ms, looper=${HANDLER_THREAD_NAME})")

        // Immediately fetch the last cached fix for a snappy first data point.
        fetchLastKnownLocation()
    }

    /**
     * Stops location updates and tears down the background [HandlerThread].
     * Safe to call even if tracking was never started.
     */
    fun stopTracking() {
        if (!isTracking) return
        try {
            fusedClient.removeLocationUpdates(locationCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Error removing location updates: ${e.message}")
        }
        isTracking = false
        // Quit the handler thread cleanly.
        try {
            handlerThread?.quitSafely()
        } catch (e: Exception) {
            Log.e(TAG, "Error quitting handler thread: ${e.message}")
        }
        handlerThread = null
        backgroundHandler = null
        Log.i(TAG, "Tracking stopped")
    }

    /**
     * Returns the last known location as a formatted string, or `null` if none
     * has been received yet.
     */
    fun getLastKnownLocationString(): String? = lastLocation?.toFormattedString()

    /**
     * Returns a Google Maps URL for the last known location, or `null`.
     */
    fun getLastKnownLocationUrl(): String? = lastLocation?.toMapsUrl()

    /**
     * Fetches a single high-accuracy location fix with constraints.
     *
     * Used by [OfflineSosManager] to obtain a reliable GPS position before
     * sending the emergency SMS.
     *
     * @param maxAccuracyMeters Maximum acceptable accuracy (metres). Fixes
     *   coarser than this are rejected in favour of the cached [lastLocation].
     * @param timeoutMs How long (ms) to wait for a qualifying fix before
     *   giving up and returning the best available location (or null).
     * @param callback Invoked exactly once with the result.
     */
    @SuppressLint("MissingPermission")
    fun getAccurateLocation(
        maxAccuracyMeters: Float = 50f,
        timeoutMs: Long = 10_000L,
        callback: (LocationData?) -> Unit
    ) {
        if (!hasLocationPermission()) {
            Log.e(TAG, "getAccurateLocation: permission not granted")
            callback(lastLocation)
            return
        }

        // Track whether we've already delivered a result.
        var delivered = false
        val lock = Any()

        // Timeout handler — returns best available after deadline.
        val timeoutHandler = Handler(Looper.getMainLooper())
        val timeoutRunnable = Runnable {
            synchronized(lock) {
                if (!delivered) {
                    delivered = true
                    Log.w(TAG, "getAccurateLocation: TIMEOUT after ${timeoutMs}ms — returning lastLocation")
                    callback(lastLocation)
                }
            }
        }
        timeoutHandler.postDelayed(timeoutRunnable, timeoutMs)

        // Request a fresh fix using getCurrentLocation (API 30+) or
        // fall back to the ongoing tracking updates.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            fusedClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY, null
            ).addOnSuccessListener { loc ->
                synchronized(lock) {
                    if (delivered) return@addOnSuccessListener
                    if (loc != null && loc.accuracy <= maxAccuracyMeters) {
                        delivered = true
                        timeoutHandler.removeCallbacks(timeoutRunnable)
                        val data = LocationData(
                            latitude = loc.latitude,
                            longitude = loc.longitude,
                            speed = loc.speed,
                            bearing = loc.bearing,
                            accuracy = loc.accuracy,
                            timestamp = loc.time
                        )
                        lastLocation = data
                        Log.d(TAG, "getAccurateLocation: fresh fix — ${data.toFormattedString()}")
                        callback(data)
                    } else if (loc != null) {
                        Log.d(TAG, "getAccurateLocation: fix too coarse (${"%.0f".format(loc.accuracy)}m) — waiting for timeout/fallback")
                        // Let the timeout fallback handle it.
                    }
                }
            }.addOnFailureListener { e ->
                Log.e(TAG, "getAccurateLocation: getCurrentLocation failed: ${e.message}")
                // Timeout will handle fallback.
            }
        } else {
            // Pre-API 30: use getLastLocation as an immediate attempt.
            fusedClient.lastLocation.addOnSuccessListener { loc ->
                synchronized(lock) {
                    if (delivered) return@addOnSuccessListener
                    if (loc != null && loc.accuracy <= maxAccuracyMeters) {
                        val ageMs = System.currentTimeMillis() - loc.time
                        if (ageMs <= Constants.LOCATION_STALE_THRESHOLD_MS) {
                            delivered = true
                            timeoutHandler.removeCallbacks(timeoutRunnable)
                            val data = LocationData(
                                latitude = loc.latitude,
                                longitude = loc.longitude,
                                speed = loc.speed,
                                bearing = loc.bearing,
                                accuracy = loc.accuracy,
                                timestamp = loc.time
                            )
                            lastLocation = data
                            Log.d(TAG, "getAccurateLocation: lastLocation OK — ${data.toFormattedString()}")
                            callback(data)
                        }
                    }
                }
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Fetches the last cached location from the Fused API.
     *
     * Emits the fix via [onLocation] only if:
     *  1. No live fix has been received yet ([lastLocation] is null).
     *  2. The cached fix is not stale (age < [Constants.LOCATION_STALE_THRESHOLD_MS]).
     */
    @SuppressLint("MissingPermission")
    private fun fetchLastKnownLocation() {
        if (!hasLocationPermission()) return

        fusedClient.lastLocation.addOnSuccessListener { loc ->
            if (loc == null) {
                Log.d(TAG, "No last-known location available from Fused API")
                return@addOnSuccessListener
            }

            val ageMs = System.currentTimeMillis() - loc.time
            if (ageMs > Constants.LOCATION_STALE_THRESHOLD_MS) {
                Log.w(TAG, "Last-known location is stale (age=${ageMs / 1000}s) — ignoring")
                return@addOnSuccessListener
            }

            // Only emit if we don't yet have a live fix (avoid back-filling stale data).
            if (lastLocation == null) {
                val data = LocationData(
                    latitude  = loc.latitude,
                    longitude = loc.longitude,
                    speed     = loc.speed,
                    bearing   = loc.bearing,
                    accuracy  = loc.accuracy,
                    timestamp = loc.time
                )
                lastLocation = data
                onLocation?.invoke(data)
                Log.d(TAG, "Last-known fallback: ${data.toFormattedString()}")
            }
        }.addOnFailureListener { e ->
            Log.e(TAG, "Failed to fetch last known location: ${e.message}")
        }
    }

    /** Returns true if ACCESS_FINE_LOCATION permission is granted. */
    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    /** Returns true if the GPS hardware provider is currently enabled. */
    private fun isGpsEnabled(): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return false
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }
}
