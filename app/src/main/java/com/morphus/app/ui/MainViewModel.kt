package com.morphus.app.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

/**
 * ViewModel for [MainFragment].
 * Holds UI-related data that survives configuration changes.
 */
class MainViewModel : ViewModel() {

    private val _statusText = MutableLiveData("Morphus Ready")
    val statusText: LiveData<String> = _statusText

    fun updateStatus(status: String) {
        _statusText.value = status
    }
}
