package com.morphus.app.utils

/**
 * Application-wide constants.
 */
object Constants {

    /** SharedPreferences file name. */
    const val PREFS_NAME = "morphus_prefs"

    /** Key for SOS active state. */
    const val KEY_SOS_ACTIVE = "sos_active"

    /** Default emergency message template. */
    const val DEFAULT_EMERGENCY_MESSAGE =
        "EMERGENCY! I need help. My current location: %s"

    /** Notification channel ID for the foreground service. */
    const val SERVICE_CHANNEL_ID = "morphus_service_channel"

    // ── Location Broadcast ──────────────────────────────────────────────────

    /**
     * LocalBroadcast action fired by [MorphusService] every time a new
     * location fix is received. Receivers (e.g. Fragments) should call
     * LocationBroadcaster.registerReceiver / unregisterReceiver.
     */
    const val LOCATION_BROADCAST_ACTION = "com.morphus.app.action.LOCATION_UPDATE"

    /** Bundle key → latitude (Double). */
    const val EXTRA_LATITUDE  = "extra_latitude"
    /** Bundle key → longitude (Double). */
    const val EXTRA_LONGITUDE = "extra_longitude"
    /** Bundle key → speed in m/s (Float). */
    const val EXTRA_SPEED     = "extra_speed"
    /** Bundle key → bearing in degrees (Float). */
    const val EXTRA_BEARING   = "extra_bearing"
    /** Bundle key → horizontal accuracy in metres (Float). */
    const val EXTRA_ACCURACY  = "extra_accuracy"
    /** Bundle key → UTC epoch millis (Long). */
    const val EXTRA_TIMESTAMP = "extra_timestamp"

    /**
     * How old (in ms) a last-known location can be before we treat it as
     * stale and refuse to broadcast it as a live fix.
     * Default: 5 minutes.
     */
    const val LOCATION_STALE_THRESHOLD_MS = 5 * 60 * 1_000L

    // ── Emergency Data ──────────────────────────────────────────────────────

    /** SharedPreferences key for emergency contacts (Set<String>). */
    const val KEY_EMERGENCY_CONTACTS = "emergency_contacts"

    // ── SMS Configuration ───────────────────────────────────────────────────

    /** Max number of retries if SMS sending fails. */
    const val SMS_MAX_RETRIES = 2

    /** Delay between SMS retries (ms). */
    const val SMS_RETRY_DELAY_MS = 30_000L

    // ── SOS Settings ────────────────────────────────────────────────────────

    /** SharedPreferences key for the secret SOS PIN. */
    const val KEY_SOS_PIN = "sos_pin"
    const val DEFAULT_SOS_PIN = "1234"

    /** SharedPreferences keys for SOS feature toggles. */
    const val KEY_SHAKE_ENABLED = "shake_enabled"
    const val KEY_POWER_BUTTON_ENABLED = "power_enabled"
    const val KEY_AUTO_CALL_ENABLED = "auto_call_enabled"
    const val KEY_AUDIO_RECORD_ENABLED = "audio_record_enabled"

    /** SharedPreferences keys for tracking thresholds. */
    const val KEY_MOVEMENT_THRESHOLD = "movement_threshold"
    const val DEFAULT_MOVEMENT_THRESHOLD = 50 // meters

    const val KEY_UPDATE_INTERVAL = "update_interval"
    const val DEFAULT_UPDATE_INTERVAL = 5 // minutes

    /** SharedPreferences key for SOS message template. */
    const val KEY_MESSAGE_TEMPLATE = "sos_message_template"
    const val DEFAULT_MESSAGE_TEMPLATE = "EMERGENCY SOS! I need help."

    // ── Supabase Configuration ──────────────────────────────────────────────

    const val SUPABASE_URL = "https://qyqzmznepunvbjnyuvav.supabase.co"
    const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InF5cXptem5lcHVudmJqbnl1dmF2Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzIyMTkwMzMsImV4cCI6MjA4Nzc5NTAzM30.NBF7MHx06Rcq3OneC-vlnN-035TD6Kc_xqbpVwVhPfU"
    const val SUPABASE_BUCKET = "sos-evidence"
    const val UPLOAD_QUEUE_PREFS = "morphus_upload_queue"
    const val UPLOAD_QUEUE_KEY = "pending_uploads"
}
