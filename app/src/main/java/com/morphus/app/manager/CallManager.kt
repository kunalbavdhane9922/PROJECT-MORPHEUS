package com.morphus.app.manager

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat

/**
 * Handles sequential call escalation to trusted contacts during an emergency.
 */
class CallManager(private val context: Context) {

    companion object {
        private const val TAG = "CallManager"
    }

    /**
     * Initiates a call to the specified phone number.
     * Note: This requires the CALL_PHONE permission and is intentional for SOS.
     */
    @RequiresPermission(Manifest.permission.CALL_PHONE)
    fun makeEmergencyCall(phoneNumber: String) {
        // Permission guard — prevent SecurityException crash
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "CALL_PHONE permission not granted — skipping call to $phoneNumber")
            return
        }

        try {
            Log.i(TAG, "Initiating emergency call to $phoneNumber")
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$phoneNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initiate call: ${e.message}")
        }
    }

    /**
     * Escalates calls through all contacts sequentially.
     * Uses EmergencyCallQueue to call each contact with a 25-second timeout.
     */
    @RequiresPermission(Manifest.permission.CALL_PHONE)
    fun startCallEscalation(contacts: List<String>) {
        if (contacts.isEmpty()) {
            Log.w(TAG, "No contacts for escalation")
            return
        }

        Log.i("MORPHUS_CALL", "Emergency call queue started")
        val callQueue = EmergencyCallQueue(context, contacts)
        callQueue.start()
    }
}
