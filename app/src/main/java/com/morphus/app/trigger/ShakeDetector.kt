package com.morphus.app.trigger

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import com.morphus.app.manager.SosManager
import kotlin.math.sqrt

/**
 * Shake detection sensor listener.
 * Triggers SOS when device acceleration exceeds threshold (strong shake).
 * Has a 1500ms cooldown to prevent rapid re-triggering.
 */
class ShakeDetector(
    private val context: Context
) : SensorEventListener {

    companion object {
        private const val TAG = "MORPHUS_TRIGGER"
        private const val SHAKE_THRESHOLD = 12f     // g-force threshold
        private const val COOLDOWN_MS = 1500L        // prevent rapid re-trigger
    }

    private var lastShakeTime = 0L

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val gForce = sqrt((x * x + y * y + z * z).toDouble()).toFloat() /
            SensorManager.GRAVITY_EARTH

        if (gForce > SHAKE_THRESHOLD) {
            val now = System.currentTimeMillis()

            if (now - lastShakeTime > COOLDOWN_MS) {
                lastShakeTime = now

                Log.i(TAG, "SHAKE DETECTED (gForce=${"%.1f".format(gForce)})")
                try {
                    SosManager(context).activate()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to activate SOS from shake: ${e.message}")
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }
}
