package com.morphus.app.manager

import android.content.Context

/**
 * Manages various SOS trigger mechanisms
 * (e.g., shake detection, power button presses, keyword detection).
 */
class TriggerManager(private val context: Context) {

    /**
     * Starts listening for trigger events.
     */
    fun startListening() {
        // TODO: Register sensors, observers, etc.
    }

    /**
     * Stops listening for trigger events.
     */
    fun stopListening() {
        // TODO: Unregister sensors, observers, etc.
    }
}
