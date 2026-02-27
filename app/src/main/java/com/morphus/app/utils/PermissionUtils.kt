package com.morphus.app.utils

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import com.morphus.app.service.MorphusAccessibilityService

/**
 * Centralised permission-checking utilities used by the onboarding flow
 * and any other component that needs to verify permission state at runtime.
 */
object PermissionUtils {

    private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"

    // ═══════════════════════════
    //  Individual Checks
    // ═══════════════════════════

    fun isLocationGranted(context: Context): Boolean =
        has(context, Manifest.permission.ACCESS_FINE_LOCATION)

    fun isBackgroundLocationGranted(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            has(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        else true   // not required below Q

    fun isSmsGranted(context: Context): Boolean =
        has(context, Manifest.permission.SEND_SMS)

    fun isCallPhoneGranted(context: Context): Boolean =
        has(context, Manifest.permission.CALL_PHONE)

    fun isMicrophoneGranted(context: Context): Boolean =
        has(context, Manifest.permission.RECORD_AUDIO)

    /**
     * Checks whether [MorphusAccessibilityService] is currently enabled
     * by enumerating running accessibility services.
     */
    fun isAccessibilityEnabled(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            ?: return false
        val enabled = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        val targetClass = MorphusAccessibilityService::class.java.name
        return enabled.any { it.resolveInfo.serviceInfo.name == targetClass }
    }

    /** Returns `true` only when every required permission is granted. */
    fun areAllGranted(context: Context): Boolean =
        isLocationGranted(context) &&
        isBackgroundLocationGranted(context) &&
        isSmsGranted(context) &&
        isCallPhoneGranted(context) &&
        isMicrophoneGranted(context) &&
        isAccessibilityEnabled(context)

    // ═══════════════════════════
    //  Onboarding Flag
    // ═══════════════════════════

    fun isOnboardingComplete(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ONBOARDING_COMPLETE, false)

    fun setOnboardingComplete(context: Context, complete: Boolean = true) {
        prefs(context).edit().putBoolean(KEY_ONBOARDING_COMPLETE, complete).apply()
    }

    // ═══════════════════════════
    //  Helpers
    // ═══════════════════════════

    private fun has(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun prefs(context: Context) =
        context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
}
