package com.morphus.app.utils

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.morphus.app.service.MorphusAccessibilityService

/**
 * Utility helpers for checking system-level permissions and service states.
 *
 * Designed for demo/debugging — all methods are safe to call from
 * any thread and will never throw.
 */
object SystemHelper {

    private const val TAG = "SystemHelper"

    /**
     * Returns `true` if the Morphus accessibility service is currently enabled.
     *
     * Uses two approaches for maximum compatibility:
     *  1. AccessibilityManager service list check (preferred, API 14+).
     *  2. Fallback to Settings.Secure string parsing for older/custom ROMs.
     */
    @JvmStatic
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        return try {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
                ?: return false
            val enabledServices = am.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            )
            val targetClass = MorphusAccessibilityService::class.java.name
            enabledServices.any { it.resolveInfo.serviceInfo.name == targetClass }
        } catch (e: Exception) {
            // Fallback: parse the secure setting
            try {
                val setting = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ) ?: ""
                setting.contains(context.packageName, ignoreCase = true)
            } catch (_: Exception) {
                false
            }
        }
    }

    /**
     * Returns `true` if POST_NOTIFICATIONS permission is effectively granted.
     *
     * On Android < 13 (API < 33), notifications are always allowed.
     * On Android 13+, checks the runtime permission.
     */
    @JvmStatic
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true // pre-13 doesn't require runtime permission
        }
    }
}
