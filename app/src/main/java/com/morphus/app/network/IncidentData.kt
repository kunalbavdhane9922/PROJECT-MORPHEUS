package com.morphus.app.network

import org.json.JSONObject

data class IncidentData(
    val timestamp: String,
    val latitude: Double,
    val longitude: Double,
    val batteryLevel: Int,
    val audioUrl: String? = null,
    val audioFilePath: String? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("timestamp", timestamp)
        put("latitude", latitude)
        put("longitude", longitude)
        put("battery_level", batteryLevel)
        put("audio_url", audioUrl ?: JSONObject.NULL)
        put("audio_file_path", audioFilePath ?: JSONObject.NULL)
    }

    companion object {
        fun fromJson(json: JSONObject): IncidentData = IncidentData(
            timestamp = json.optString("timestamp", ""),
            latitude = json.optDouble("latitude", 0.0),
            longitude = json.optDouble("longitude", 0.0),
            batteryLevel = json.optInt("battery_level", -1),
            audioUrl = json.optString("audio_url", null),
            audioFilePath = json.optString("audio_file_path", null)
        )
    }
}
