package com.morphus.app.ui

import android.Manifest
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Space
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.morphus.app.R
import com.morphus.app.databinding.FragmentOnboardingBinding
import com.morphus.app.ui.OnboardingViewModel.Step

/**
 * Full-screen onboarding fragment that sequentially requests every
 * permission Morphus needs.
 *
 * The fragment observes [OnboardingViewModel.currentStep] and updates
 * the icon, title, description, and button text accordingly. Each step
 * uses a dedicated [ActivityResultLauncher] (except Accessibility, which
 * opens system Settings).
 */
class OnboardingFragment : Fragment() {

    private var _binding: FragmentOnboardingBinding? = null
    private val binding get() = _binding!!

    private val viewModel: OnboardingViewModel by viewModels()

    // ── Permission launchers ──

    private val locationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            viewModel.skipAlreadyGranted()
        }

    private val backgroundLocationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            viewModel.skipAlreadyGranted()
        }

    private val smsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            viewModel.skipAlreadyGranted()
        }

    private val callLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            viewModel.skipAlreadyGranted()
        }

    private val micLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            viewModel.skipAlreadyGranted()
        }

    // ══════════════════════════
    //  Lifecycle
    // ══════════════════════════

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOnboardingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        buildDotIndicators()

        // Skip past already-granted permissions on first load
        viewModel.skipAlreadyGranted()

        viewModel.currentStep.observe(viewLifecycleOwner) { step ->
            updateUi(step)
        }

        viewModel.isComplete.observe(viewLifecycleOwner) { complete ->
            if (complete) navigateToCalculator()
        }

        binding.btnGrant.setOnClickListener { onGrantClicked() }
        binding.btnSkip.setOnClickListener { viewModel.advanceToNext() }
    }

    override fun onResume() {
        super.onResume()
        // Re-check after returning from system Settings (e.g. accessibility)
        viewModel.skipAlreadyGranted()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ══════════════════════════
    //  UI Updates
    // ══════════════════════════

    private fun updateUi(step: Step) {
        val (iconRes, titleRes, descRes, isSettings) = stepConfig(step)

        binding.ivIcon.setImageResource(iconRes)
        binding.tvTitle.setText(titleRes)
        binding.tvDescription.setText(descRes)

        binding.btnGrant.setText(
            if (isSettings) R.string.onboarding_open_settings else R.string.onboarding_grant
        )

        updateDotIndicators(viewModel.currentStepIndex)
    }

    /**
     * Returns (iconRes, titleRes, descRes, isSettingsStep) for each step.
     */
    private fun stepConfig(step: Step): StepConfig = when (step) {
        Step.LOCATION -> StepConfig(
            R.drawable.ic_location,
            R.string.onboarding_location_title,
            R.string.onboarding_location_desc,
            false
        )
        Step.BACKGROUND_LOCATION -> StepConfig(
            R.drawable.ic_location,
            R.string.onboarding_bg_location_title,
            R.string.onboarding_bg_location_desc,
            false
        )
        Step.SMS -> StepConfig(
            R.drawable.ic_sms,
            R.string.onboarding_sms_title,
            R.string.onboarding_sms_desc,
            false
        )
        Step.CALL_PHONE -> StepConfig(
            R.drawable.ic_phone,
            R.string.onboarding_call_title,
            R.string.onboarding_call_desc,
            false
        )
        Step.MICROPHONE -> StepConfig(
            R.drawable.ic_mic,
            R.string.onboarding_mic_title,
            R.string.onboarding_mic_desc,
            false
        )
        Step.ACCESSIBILITY -> StepConfig(
            R.drawable.ic_accessibility,
            R.string.onboarding_accessibility_title,
            R.string.onboarding_accessibility_desc,
            true
        )
    }

    private data class StepConfig(
        val iconRes: Int,
        val titleRes: Int,
        val descRes: Int,
        val isSettings: Boolean
    )

    // ══════════════════════════
    //  Grant Button
    // ══════════════════════════

    private fun onGrantClicked() {
        when (viewModel.currentStep.value) {
            Step.LOCATION -> {
                locationLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
            Step.BACKGROUND_LOCATION -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                } else {
                    viewModel.advanceToNext()
                }
            }
            Step.SMS -> {
                smsLauncher.launch(Manifest.permission.SEND_SMS)
            }
            Step.CALL_PHONE -> {
                callLauncher.launch(Manifest.permission.CALL_PHONE)
            }
            Step.MICROPHONE -> {
                micLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            Step.ACCESSIBILITY -> {
                openAccessibilitySettings()
            }
            null -> { /* no-op */ }
        }
    }

    private fun openAccessibilitySettings() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (_: Exception) {
            // Fallback: open app details
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", requireContext().packageName, null)
                }
            )
        }
    }

    // ══════════════════════════
    //  Dot Indicators
    // ══════════════════════════

    private fun buildDotIndicators() {
        val container = binding.llDots
        container.removeAllViews()

        for (i in 0 until viewModel.totalSteps) {
            val dot = View(requireContext()).apply {
                val sizeDp = 10
                val sizePx = (sizeDp * resources.displayMetrics.density).toInt()
                layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply {
                    marginStart = 6
                    marginEnd = 6
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(
                        ContextCompat.getColor(
                            requireContext(),
                            if (i == 0) R.color.onboarding_dot_active else R.color.onboarding_dot_inactive
                        )
                    )
                }
            }
            container.addView(dot)
        }
    }

    private fun updateDotIndicators(activeIndex: Int) {
        val container = binding.llDots
        for (i in 0 until container.childCount) {
            val dot = container.getChildAt(i)
            val bg = dot.background as? GradientDrawable ?: continue
            bg.setColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (i == activeIndex) R.color.onboarding_dot_active else R.color.onboarding_dot_inactive
                )
            )
        }
    }

    // ══════════════════════════
    //  Navigation
    // ══════════════════════════

    private fun navigateToCalculator() {
        findNavController().navigate(R.id.action_onboarding_to_calculator)
    }
}
