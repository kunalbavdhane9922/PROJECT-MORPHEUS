package com.morphus.app.network

import android.content.Context
import android.util.Log
import com.morphus.app.utils.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Offline-first upload queue for SOS incident data.
 *
 * When internet is unavailable, incidents are queued locally in SharedPreferences.
 * When connectivity is restored (via NetworkGuardian), processQueue() is called
 * to upload all pending items silently in the background.
 *
 * Guarantees:
 * - NEVER blocks the main thread or EmergencyService
 * - NEVER crashes if Supabase is unreachable
 * - Retries silently on next network availability
 */
class UploadQueueManager(private val context: Context) {

    companion object {
        private const val TAG = "MORPHUS_UPLOAD"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val prefs by lazy {
        context.getSharedPreferences(Constants.UPLOAD_QUEUE_PREFS, Context.MODE_PRIVATE)
    }

    /**
     * Add an incident to the offline upload queue.
     * Safe to call from any thread.
     */
    fun enqueue(data: IncidentData) {
        try {
            // Validate audio file — strip path if invalid, but still enqueue incident
            var incidentToQueue = data
            val path = data.audioFilePath
            if (path != null) {
                val file = File(path)
                if (!file.exists() || file.length() == 0L) {
                    Log.w(TAG, "Audio file invalid: $path (exists=${file.exists()}, size=${file.length()}) — enqueuing without audio")
                    incidentToQueue = data.copy(audioFilePath = null)
                } else {
                    Log.d(TAG, "Audio file OK: ${file.length()} bytes at $path")
                }
            }

            val queue = loadQueue()
            queue.put(incidentToQueue.toJson())
            saveQueue(queue)
            Log.d(TAG, "Queued upload (${queue.length()} pending)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enqueue: ${e.message}")
        }
    }

    /**
     * Process all queued uploads on a background coroutine.
     * Items are removed only after successful upload + insert.
     * Failed items stay in queue for next retry.
     */
    fun processQueue() {
        scope.launch {
            try {
                val queue = loadQueue()
                if (queue.length() == 0) return@launch

                Log.i(TAG, "Processing ${queue.length()} queued uploads")

                val remaining = JSONArray()

                for (i in 0 until queue.length()) {
                    val json = queue.optJSONObject(i) ?: continue
                    val item = IncidentData.fromJson(json)

                    val success = uploadItem(item)
                    if (!success) {
                        remaining.put(json)
                    }
                }

                saveQueue(remaining)

                if (remaining.length() > 0) {
                    Log.w(TAG, "${remaining.length()} items still pending")
                } else {
                    Log.i(TAG, "All queued uploads completed")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Queue processing error: ${e.message}")
            }
        }
    }

    /**
     * Upload a single queued item:
     * 1. Upload audio file → get URL
     * 2. Insert incident row with audio URL
     * Returns true only if both steps succeed (or audio file doesn't exist).
     */
    private suspend fun uploadItem(item: IncidentData): Boolean {
        return try {
            var audioUrl: String? = item.audioUrl

            // Step 1: Upload audio if file path exists and file is present
            val filePath = item.audioFilePath
            if (filePath != null && audioUrl == null) {
                val audioFile = File(filePath)
                if (!audioFile.exists()) {
                    Log.e(TAG, "File missing: $filePath")
                    Log.w(TAG, "Audio file not found: $filePath (proceeding without audio)")
                } else {
                    Log.d(TAG, "Uploading file size=${audioFile.length()}")
                    audioUrl = SupabaseClient.uploadAudio(audioFile)
                    if (audioUrl == null) {
                        Log.w(TAG, "Audio upload failed — will retry later")
                        return false
                    }
                }
            }

            // Step 2: Insert incident record
            val incidentWithUrl = item.copy(audioUrl = audioUrl)
            val inserted = SupabaseClient.insertIncident(incidentWithUrl)

            if (inserted) {
                Log.i(TAG, "Upload complete: ${item.timestamp}")
            }

            inserted
        } catch (e: Exception) {
            Log.e(TAG, "Upload item failed: ${e.message}")
            false
        }
    }

    private fun loadQueue(): JSONArray {
        val raw = prefs.getString(Constants.UPLOAD_QUEUE_KEY, null)
        return if (raw != null) JSONArray(raw) else JSONArray()
    }

    private fun saveQueue(queue: JSONArray) {
        prefs.edit().putString(Constants.UPLOAD_QUEUE_KEY, queue.toString()).apply()
    }
}
