package com.morphus.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.morphus.app.utils.PermissionUtils

/**
 * Manages the sequential state of the onboarding permission flow.
 *
 * Each [Step] maps to one permission group. The ViewModel exposes the current
 * step and whether the flow is complete. The fragment drives UI updates by
 * observing [currentStep] and [isComplete].
 */
class OnboardingViewModel(application: Application) : AndroidViewModel(application) {

    /** Permission steps in presentation order. */
    enum class Step {
        LOCATION,
        BACKGROUND_LOCATION,
        SMS,
        CALL_PHONE,
        MICROPHONE,
        ACCESSIBILITY
    }

    private val steps = Step.values().toList()

    private val _currentIndex = MutableLiveData(0)

    private val _currentStep = MutableLiveData(steps[0])
    val currentStep: LiveData<Step> = _currentStep

    private val _isComplete = MutableLiveData(false)
    val isComplete: LiveData<Boolean> = _isComplete

    val totalSteps: Int get() = steps.size

    val currentStepIndex: Int get() = _currentIndex.value ?: 0

    /**
     * Advances to the next step. If the current step is the last one,
     * marks the flow as complete.
     */
    fun advanceToNext() {
        val nextIndex = (_currentIndex.value ?: 0) + 1
        if (nextIndex >= steps.size) {
            PermissionUtils.setOnboardingComplete(getApplication())
            _isComplete.value = true
        } else {
            _currentIndex.value = nextIndex
            _currentStep.value = steps[nextIndex]
        }
    }

    /**
     * Checks whether the permission for the current step is already granted,
     * and if so, auto-advances past it.
     */
    fun skipAlreadyGranted() {
        val ctx = getApplication<Application>()
        while ((_currentIndex.value ?: 0) < steps.size) {
            val granted = when (steps[_currentIndex.value ?: 0]) {
                Step.LOCATION -> PermissionUtils.isLocationGranted(ctx)
                Step.BACKGROUND_LOCATION -> PermissionUtils.isBackgroundLocationGranted(ctx)
                Step.SMS -> PermissionUtils.isSmsGranted(ctx)
                Step.CALL_PHONE -> PermissionUtils.isCallPhoneGranted(ctx)
                Step.MICROPHONE -> PermissionUtils.isMicrophoneGranted(ctx)
                Step.ACCESSIBILITY -> PermissionUtils.isAccessibilityEnabled(ctx)
            }
            if (granted) {
                val nextIndex = (_currentIndex.value ?: 0) + 1
                if (nextIndex >= steps.size) {
                    PermissionUtils.setOnboardingComplete(ctx)
                    _isComplete.value = true
                    return
                }
                _currentIndex.value = nextIndex
                _currentStep.value = steps[nextIndex]
            } else {
                break
            }
        }
    }
}
