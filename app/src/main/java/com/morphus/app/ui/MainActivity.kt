package com.morphus.app.ui

import android.Manifest
import android.content.IntentFilter
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.navigation.fragment.NavHostFragment
import com.morphus.app.R
import com.morphus.app.databinding.ActivityMainBinding
import com.morphus.app.trigger.PowerButtonGuardian
import com.morphus.app.trigger.ShakeDetector
import com.morphus.app.utils.PermissionUtils

/**
 * Single Activity entry point for the Morphus application.
 * Hosts a NavHostFragment for fragment-based navigation.
 *
 * On launch, checks whether onboarding is already complete.
 * If so, navigates directly to the calculator (skipping onboarding).
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            skipOnboardingIfComplete()
        }

        requestAllRuntimePermissions()

        // Start battery monitoring for auto SOS at critical levels
        com.morphus.app.system.BatteryGuardian(this).startMonitoring()

        // Register double power button SOS trigger
        registerPowerButtonTrigger()

        // Register shake SOS trigger
        registerShakeTrigger()
    }

    private fun requestAllRuntimePermissions() {
        val permissions = mutableListOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= 33) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        Log.d("MORPHUS_DEBUG", "Requesting runtime permissions: $permissions")
        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 1001)
    }

    override fun onSupportNavigateUp(): Boolean {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        return navHostFragment.navController.navigateUp() || super.onSupportNavigateUp()
    }

    /**
     * If onboarding was already completed in a prior session, jump
     * straight to the calculator without showing onboarding again.
     */
    private fun skipOnboardingIfComplete() {
        if (PermissionUtils.isOnboardingComplete(this)) {
            val navHostFragment = supportFragmentManager
                .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
            val navController = navHostFragment.navController
            navController.navigate(R.id.action_onboarding_to_calculator)
        }
    }

    // ── SOS Trigger Registration ──

    private fun registerPowerButtonTrigger() {
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(PowerButtonGuardian(applicationContext), filter, RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(PowerButtonGuardian(applicationContext), filter)
            }
            Log.d("MORPHUS_TRIGGER", "PowerButtonGuardian registered")
        } catch (e: Exception) {
            Log.e("MORPHUS_TRIGGER", "Failed to register PowerButtonGuardian: ${e.message}")
        }
    }

    private fun registerShakeTrigger() {
        try {
            val sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
            val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

            if (accelerometer != null) {
                sensorManager.registerListener(
                    ShakeDetector(applicationContext),
                    accelerometer,
                    SensorManager.SENSOR_DELAY_NORMAL
                )
                Log.d("MORPHUS_TRIGGER", "Shake detector registered")
            } else {
                Log.w("MORPHUS_TRIGGER", "No accelerometer available — shake trigger disabled")
            }
        } catch (e: Exception) {
            Log.e("MORPHUS_TRIGGER", "Failed to register shake detector: ${e.message}")
        }
    }
}
