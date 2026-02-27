package com.morphus.app.network

import android.util.Log
import com.morphus.app.utils.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

object SupabaseClient {

    private const val TAG = "MORPHUS_UPLOAD"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Uploads an audio file to Supabase Storage.
     * Returns the public URL on success, or null on failure.
     * NEVER throws — all errors are caught and logged.
     */
    suspend fun uploadAudio(file: File): String? = withContext(Dispatchers.IO) {
        try {
            if (!file.exists() || file.length() == 0L) {
                Log.w(TAG, "Audio file missing or empty: ${file.name}")
                return@withContext null
            }

            val timestamp = System.currentTimeMillis()
            val storagePath = "evidence/${timestamp}.m4a"
            val uploadUrl =
                "${Constants.SUPABASE_URL}/storage/v1/object/${Constants.SUPABASE_BUCKET}/$storagePath"

            val requestBody = file.asRequestBody("audio/mp4".toMediaType())

            val request = Request.Builder()
                .url(uploadUrl)
                .post(requestBody)
                .addHeader("apikey", Constants.SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer ${Constants.SUPABASE_ANON_KEY}")
                .addHeader("Content-Type", "audio/mp4")
                .addHeader("x-upsert", "true")
                .build()

            Log.d(TAG, "Uploading to Supabase: ${file.name} (${file.length()} bytes)")
            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val publicUrl =
                    "${Constants.SUPABASE_URL}/storage/v1/object/public/${Constants.SUPABASE_BUCKET}/$storagePath"
                Log.d(TAG, "Upload SUCCESS")
                Log.i(TAG, "Audio uploaded: $publicUrl")
                return@withContext publicUrl
            } else {
                Log.e(TAG, "Upload FAILED [${response.code}]: ${response.body?.string()}")
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Upload FAILED: ${e.message}")
            return@withContext null
        }
    }

    /**
     * Inserts an incident record into the Supabase "incidents" table.
     * Returns true on success, false on failure.
     * NEVER throws — all errors are caught and logged.
     */
    suspend fun insertIncident(data: IncidentData): Boolean = withContext(Dispatchers.IO) {
        try {
            val restUrl = "${Constants.SUPABASE_URL}/rest/v1/incidents"

            val json = JSONObject().apply {
                put("timestamp", data.timestamp)
                put("latitude", data.latitude)
                put("longitude", data.longitude)
                put("battery_level", data.batteryLevel)
                put("audio_url", data.audioUrl ?: JSONObject.NULL)
            }

            val requestBody = json.toString()
                .toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(restUrl)
                .post(requestBody)
                .addHeader("apikey", Constants.SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer ${Constants.SUPABASE_ANON_KEY}")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=minimal")
                .build()

            Log.d(TAG, "Uploading to Supabase: insert incident")
            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                Log.d(TAG, "Upload SUCCESS")
                Log.i(TAG, "Incident inserted successfully")
                return@withContext true
            } else {
                Log.e(TAG, "Upload FAILED [${response.code}]: ${response.body?.string()}")
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Upload FAILED: "+e.message)
            return@withContext false
        }
    }
}
