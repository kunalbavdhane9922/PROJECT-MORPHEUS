package com.morphus.app.data

import android.content.Context

/**
 * Central data repository — single source of truth for app data.
 * Coordinates between local storage, preferences, and remote sources.
 */
class AppRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences(
        com.morphus.app.utils.Constants.PREFS_NAME,
        Context.MODE_PRIVATE
    )

    /**
     * Retrieves the list of emergency contacts from SharedPreferences.
     */
    fun getEmergencyContacts(): List<String> {
        val contacts = prefs.getStringSet(
            com.morphus.app.utils.Constants.KEY_EMERGENCY_CONTACTS,
            null
        )
        return contacts?.toList() ?: emptyList()
    }

    /**
     * Saves an emergency contact.
     */
    fun saveEmergencyContact(phone: String) {
        val current = getEmergencyContacts().toMutableSet()
        current.add(phone)
        prefs.edit().putStringSet(
            com.morphus.app.utils.Constants.KEY_EMERGENCY_CONTACTS,
            current
        ).apply()
    }

    /**
     * Removes an emergency contact.
     */
    fun removeEmergencyContact(phone: String) {
        val current = getEmergencyContacts().toMutableSet()
        current.remove(phone)
        prefs.edit().putStringSet(
            com.morphus.app.utils.Constants.KEY_EMERGENCY_CONTACTS,
            current
        ).apply()
    }
}
