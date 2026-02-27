package com.morphus.app.manager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.morphus.app.utils.Constants

/**
 * Singleton helper for broadcasting and receiving location updates
 * within the app process using [LocalBroadcastManager].
 *
 * **Producer side** (called from [MorphusService]):
 * ```kotlin
 * LocationBroadcaster.send(context, locationData)
 * ```
 *
 * **Consumer side** (called from a Fragment / Activity):
 * ```kotlin
 * // In onStart / onResume:
 * LocationBroadcaster.registerReceiver(requireContext(), myReceiver)
 *
 * // In onStop / onPause:
 * LocationBroadcaster.unregisterReceiver(requireContext(), myReceiver)
 *
 * // Receiver implementation:
 * private val myReceiver = LocationBroadcaster.asReceiver { data ->
 *     binding.tvCoords.text = data.toFormattedString()
 * }
 * ```
 */
object LocationBroadcaster {

    // ── Send ─────────────────────────────────────────────────────────────────

    /**
     * Broadcasts a [LocationTracker.LocationData] fix to all registered receivers
     * within this process.
     */
    fun send(context: Context, data: LocationTracker.LocationData) {
        val intent = Intent(Constants.LOCATION_BROADCAST_ACTION).apply {
            putExtra(Constants.EXTRA_LATITUDE,  data.latitude)
            putExtra(Constants.EXTRA_LONGITUDE, data.longitude)
            putExtra(Constants.EXTRA_SPEED,     data.speed)
            putExtra(Constants.EXTRA_BEARING,   data.bearing)
            putExtra(Constants.EXTRA_ACCURACY,  data.accuracy)
            putExtra(Constants.EXTRA_TIMESTAMP, data.timestamp)
        }
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
    }

    // ── Receive ───────────────────────────────────────────────────────────────

    /**
     * Registers a [BroadcastReceiver] to listen for location updates.
     * Should be called in [onStart] / [onResume].
     */
    fun registerReceiver(context: Context, receiver: BroadcastReceiver) {
        LocalBroadcastManager.getInstance(context)
            .registerReceiver(receiver, IntentFilter(Constants.LOCATION_BROADCAST_ACTION))
    }

    /**
     * Unregisters a previously registered [BroadcastReceiver].
     * Should be called in [onStop] / [onPause].
     */
    fun unregisterReceiver(context: Context, receiver: BroadcastReceiver) {
        LocalBroadcastManager.getInstance(context).unregisterReceiver(receiver)
    }

    /**
     * Factory helper that creates a typed [BroadcastReceiver] with the
     * Intent already unpacked into a [LocationTracker.LocationData] object.
     *
     * ```kotlin
     * private val locationReceiver = LocationBroadcaster.asReceiver { data ->
     *     Log.d("UI", data.toFormattedString())
     * }
     * ```
     */
    fun asReceiver(onReceive: (LocationTracker.LocationData) -> Unit): BroadcastReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != Constants.LOCATION_BROADCAST_ACTION) return
                val data = LocationTracker.LocationData(
                    latitude  = intent.getDoubleExtra(Constants.EXTRA_LATITUDE,  0.0),
                    longitude = intent.getDoubleExtra(Constants.EXTRA_LONGITUDE, 0.0),
                    speed     = intent.getFloatExtra(Constants.EXTRA_SPEED,     0f),
                    bearing   = intent.getFloatExtra(Constants.EXTRA_BEARING,   0f),
                    accuracy  = intent.getFloatExtra(Constants.EXTRA_ACCURACY,  0f),
                    timestamp = intent.getLongExtra(Constants.EXTRA_TIMESTAMP,   0L)
                )
                onReceive(data)
            }
        }
}
