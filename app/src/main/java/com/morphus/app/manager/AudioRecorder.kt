package com.morphus.app.manager

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Handles background audio recording for evidence capture during emergencies.
 * Saves recordings to app-private external storage for reliability.
 */
class AudioRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null

    /**
     * The file currently being recorded to, or the last recorded file.
     * Exposed for the EmergencyService to package with incident reports.
     */
    var currentFile: File? = null
        private set

    var isRecording: Boolean = false
        private set

    companion object {
        private const val TAG = "MORPHUS_RECORD"
    }

    /**
     * Starts audio recording and saves to app-private external storage.
     * Uses AAC and MPEG_4 for good quality and compatibility.
     */
    fun startRecording() {
        if (isRecording) {
            Log.w(TAG, "Recording already in progress")
            return
        }

        // Permission guard — MUST check before MediaRecorder
        if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "RECORD_AUDIO permission missing")
            return
        }

        Log.d(TAG, "Recorder initializing")

        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())

            // Safe directory: external Music if available, else internal files dir
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
                ?: context.filesDir
            if (!dir.exists()) dir.mkdirs()

            val file = File(dir, "SOS_REC_${timestamp}.m4a")
            currentFile = file
            val filePath = file.absolutePath
            Log.d(TAG, "Output file: $filePath")

            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            Log.d(TAG, "Preparing recorder")
            recorder!!.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(filePath)

                try {
                    prepare()
                } catch (e: Exception) {
                    Log.e(TAG, "Recorder prepare() failure", e)
                    throw e
                }

                try {
                    start()
                } catch (e: Exception) {
                    Log.e(TAG, "Recorder start() failure", e)
                    throw e
                }
            }

            isRecording = true
            Log.d(TAG, "Recording started -> $filePath")
            Log.i(TAG, "✅ AUDIO RECORDING ACTIVE")
        } catch (e: Exception) {
            Log.e(TAG, "Recorder failure", e)
            currentFile = null
            stopRecording()
        }
    }

    /**
     * Stops the current audio recording and releases resources.
     */
    fun stopRecording() {
        try {
            recorder?.apply {
                try {
                    stop()
                } catch (e: IllegalStateException) {
                    Log.w(TAG, "Recorder already stopped or not started")
                }
                release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recorder: ${e.message}")
        } finally {
            recorder = null
            isRecording = false
            val f = currentFile
            if (f != null) {
                Log.d(TAG, "Recording stopped. Exists=${f.exists()} Size=${f.length()}")
            }
            Log.d(TAG, "Recording stopped")
        }
    }
}
