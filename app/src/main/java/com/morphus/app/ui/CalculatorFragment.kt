package com.morphus.app.ui

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.view.GestureDetectorCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.morphus.app.databinding.FragmentCalculatorBinding
import com.morphus.app.manager.SituationType
import com.morphus.app.manager.SosManager

/**
 * Calculator fragment — fully functional calculator with a hidden SOS trigger.
 *
 * HIDDEN TRIGGER (invisible to casual use):
 *   • Double-tap the display area  → triggers SOS
 *   • Long-press the display area  → triggers SOS
 *   Vibration feedback confirms activation. No visible UI change occurs.
 *
 * Implementation uses [GestureDetectorCompat] with
 * [GestureDetector.SimpleOnGestureListener] for both gestures.
 */
class CalculatorFragment : Fragment() {

    private var _binding: FragmentCalculatorBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CalculatorViewModel by viewModels()

    private lateinit var gestureDetector: GestureDetectorCompat

    /** True when the calculator is in hidden emergency message mode. */
    private var emergencyMode = false

    // ══════════════════════════
    //  Lifecycle
    // ══════════════════════════

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCalculatorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeViewModel()
        setupButtons()
        setupHiddenTrigger()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ══════════════════════════
    //  ViewModel Observation
    // ══════════════════════════

    private fun observeViewModel() {
        viewModel.expression.observe(viewLifecycleOwner) { expr ->
            binding.tvExpression.text = expr
        }
        viewModel.result.observe(viewLifecycleOwner) { res ->
            binding.tvResult.text = res
        }
    }

    // ══════════════════════════
    //  Button Wiring
    // ══════════════════════════

    private fun setupButtons() {
        // Digits — buttons 1-5 are intercepted in emergency mode
        binding.btn0.setOnClickListener { viewModel.onDigit("0") }
        binding.btn1.setOnClickListener {
            if (emergencyMode) triggerSituation(SituationType.IN_CAB)
            else viewModel.onDigit("1")
        }
        binding.btn2.setOnClickListener {
            if (emergencyMode) triggerSituation(SituationType.FOLLOWED)
            else viewModel.onDigit("2")
        }
        binding.btn3.setOnClickListener {
            if (emergencyMode) triggerSituation(SituationType.UNSAFE_LOCATION)
            else viewModel.onDigit("3")
        }
        binding.btn4.setOnClickListener {
            if (emergencyMode) triggerSituation(SituationType.MEDICAL)
            else viewModel.onDigit("4")
        }
        binding.btn5.setOnClickListener {
            if (emergencyMode) triggerSituation(SituationType.MISBEHAVIOR)
            else viewModel.onDigit("5")
        }
        binding.btn6.setOnClickListener {
            if (emergencyMode) triggerSituation(SituationType.ABUSE)
            else viewModel.onDigit("6")
        }
        binding.btn7.setOnClickListener { viewModel.onDigit("7") }
        binding.btn8.setOnClickListener { viewModel.onDigit("8") }
        binding.btn9.setOnClickListener { viewModel.onDigit("9") }

        // Operators
        binding.btnAdd.setOnClickListener { viewModel.onOperator("+") }
        binding.btnSubtract.setOnClickListener { viewModel.onOperator("−") }
        binding.btnMultiply.setOnClickListener { viewModel.onOperator("×") }
        binding.btnDivide.setOnClickListener { viewModel.onOperator("÷") }

        // Actions
        binding.btnEquals.setOnClickListener {
            try {
                val settingsManager = com.morphus.app.data.SettingsManager(requireContext())

                // Get the raw digits from the result display
                val displayText = binding.tvResult.text?.toString() ?: ""
                val currentInput = displayText
                    .replace(",", "")
                    .replace(" ", "")

                if (currentInput.length == 4 && currentInput.all { it.isDigit() }
                    && settingsManager.verifyPin(currentInput)) {
                    // Secret path: launch settings
                    vibrateSubtle()
                    val intent = android.content.Intent(requireContext(), SOSSettingsActivity::class.java)
                    startActivity(intent)

                    // Clear calculator for stealth
                    viewModel.clear()
                } else {
                    // Normal arithmetic
                    viewModel.onEquals()
                }
            } catch (e: Exception) {
                // Fallback: just do normal equals if anything fails
                viewModel.onEquals()
            }
        }
        binding.btnClear.setOnClickListener {
            if (emergencyMode) {
                exitEmergencyMode()
            } else {
                viewModel.clear()
            }
        }

        // Long-press AC → toggle emergency message mode
        binding.btnClear.setOnLongClickListener {
            if (emergencyMode) exitEmergencyMode() else enterEmergencyMode()
            true
        }

        binding.btnBackspace.setOnClickListener {
            if (emergencyMode) {
                exitEmergencyMode()
            } else {
                viewModel.onBackspace()
            }
        }
        binding.btnDecimal.setOnClickListener { viewModel.onDecimal() }
        binding.btnPercent.setOnClickListener { viewModel.onPercent() }
        binding.btnNegate.setOnClickListener { viewModel.onNegate() }
    }

    // ══════════════════════════
    //  Hidden SOS Trigger
    // ══════════════════════════

    /**
     * Wires [GestureDetector] onto the display area.
     *
     * **Double-tap** — `onDoubleTap` fires SOS immediately.
     * **Long-press** — `onLongPress` fires SOS immediately.
     *
     * Either gesture triggers vibration confirmation + full SOS flow.
     */
    @Suppress("ClickableViewAccessibility")
    private fun setupHiddenTrigger() {
        gestureDetector = GestureDetectorCompat(
            requireContext(),
            object : GestureDetector.SimpleOnGestureListener() {

                override fun onDoubleTap(e: MotionEvent): Boolean {
                    onSosTriggered()
                    return true
                }

                override fun onLongPress(e: MotionEvent) {
                    onSosTriggered()
                }
            }
        )

        binding.displayArea.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            // Don't consume — calculator display remains interactive for single taps
            false
        }
    }

    // ══════════════════════════
    //  SOS Activation
    // ══════════════════════════

    /**
     * Called when the hidden SOS trigger gesture is detected.
     * Provides haptic confirmation and kicks off the emergency flow.
     */
    private fun onSosTriggered() {
        // Confirmation vibration — two short pulses (SOS "acknowledged")
        vibrateConfirmation()

        // Activate the full SOS flow (notification + service + SMS)
        SosManager(requireContext()).activate()
    }

    // ══════════════════════════
    //  Emergency Message Mode
    // ══════════════════════════

    /**
     * Enters the hidden emergency message mode.
     * Calculator buttons 1-5 become situation selectors.
     * Display shows instructions.
     */
    private fun enterEmergencyMode() {
        emergencyMode = true
        vibrateConfirmation()

        // Show instructions on the calculator display
        binding.tvExpression.text = "Emergency Mode"
        binding.tvResult.text = "Select situation below"

        // Relabel buttons 1-6 with emoji hints
        binding.btn1.text = "\uD83D\uDE95 Cab"
        binding.btn2.text = "\uD83D\uDC64 Follow"
        binding.btn3.text = "\uD83D\uDCCD Unsafe"
        binding.btn4.text = "\uD83E\uDE7A Med"
        binding.btn5.text = "\uD83D\uDEAB Misb."
        binding.btn6.text = "\uD83D\uDD34 Abuse"

        // Shrink text size so emoji labels fit the buttons
        binding.btn1.textSize = 12f
        binding.btn2.textSize = 12f
        binding.btn3.textSize = 12f
        binding.btn4.textSize = 12f
        binding.btn5.textSize = 12f
        binding.btn6.textSize = 12f

        Log.d("MORPHUS_UI", "Emergency Mode Enabled")
    }

    /**
     * Exits emergency mode and restores normal calculator state.
     */
    private fun exitEmergencyMode() {
        emergencyMode = false

        // Restore original button labels
        binding.btn1.text = "1"
        binding.btn2.text = "2"
        binding.btn3.text = "3"
        binding.btn4.text = "4"
        binding.btn5.text = "5"
        binding.btn6.text = "6"

        // Restore original text size
        val defaultSize = 24f
        binding.btn1.textSize = defaultSize
        binding.btn2.textSize = defaultSize
        binding.btn3.textSize = defaultSize
        binding.btn4.textSize = defaultSize
        binding.btn5.textSize = defaultSize
        binding.btn6.textSize = defaultSize

        viewModel.clear()
        Log.d("MORPHUS_UI", "Emergency Mode Disabled")
    }

    /**
     * Triggers a situation-based SOS.
     * Sends custom SMS without calling, then exits emergency mode.
     */
    private fun triggerSituation(type: SituationType) {
        vibrateConfirmation()
        SosManager(requireContext()).activateWithSituation(type)
        emergencyMode = false
        viewModel.clear()
    }

    // ══════════════════════════
    //  Vibration Helpers
    // ══════════════════════════

    /**
     * Produces a distinct two-pulse vibration pattern to silently confirm
     * SOS activation without any visual change on screen.
     */
    private fun vibrateConfirmation() {
        val vibrator = getVibrator()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Pattern: pause 0ms → vibrate 100ms → pause 80ms → vibrate 100ms
            val pattern = longArrayOf(0, 100, 80, 100)
            val amplitudes = intArrayOf(0, VibrationEffect.DEFAULT_AMPLITUDE, 0, VibrationEffect.DEFAULT_AMPLITUDE)
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, amplitudes, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 100, 80, 100), -1)
        }
    }

    /**
     * Produces a subtle, single short haptic pulse for the secret settings unlock.
     */
    private fun vibrateSubtle() {
        val vibrator = getVibrator()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(50)
        }
    }

    /** Resolves the system [Vibrator] in an API-safe way. */
    private fun getVibrator(): Vibrator {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val mgr = requireContext().getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            mgr.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }
}
