package com.morphus.app.manager

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Sequential emergency call queue.
 * Calls each trusted contact in order, waiting 25 seconds between each.
 * If a call is not answered, moves to the next contact automatically.
 */
class EmergencyCallQueue(
    private val context: Context,
    private val numbers: List<String>
) {

    companion object {
        private const val TAG = "MORPHUS_CALL"
        private const val CALL_TIMEOUT_MS = 25_000L // 25 seconds per contact
    }

    private var index = 0
    private val handler = Handler(Looper.getMainLooper())

    fun start() {
        if (numbers.isEmpty()) {
            Log.w(TAG, "No contacts to call")
            return
        }
        Log.i(TAG, "Emergency call queue started (${numbers.size} contacts)")
        index = 0
        callNext()
    }

    private fun callNext() {
        if (index >= numbers.size) {
            Log.w(TAG, "All contacts attempted")
            return
        }

        val number = numbers[index]
        Log.d(TAG, "Calling $number (${index + 1}/${numbers.size})")

        // Permission guard
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "CALL_PHONE permission missing — cannot call")
            return
        }

        try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$number")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Call failed: ${e.message}")
        }

        // Schedule next contact after timeout
        handler.postDelayed({
            index++
            callNext()
        }, CALL_TIMEOUT_MS)
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        Log.d(TAG, "Call queue stopped")
    }
}
