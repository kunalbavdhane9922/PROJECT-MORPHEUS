package com.morphus.app.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import java.text.DecimalFormat

/**
 * ViewModel for the Calculator screen.
 * Handles all arithmetic logic and maintains display state.
 */
class CalculatorViewModel : ViewModel() {

    private val _expression = MutableLiveData("")
    val expression: LiveData<String> = _expression

    private val _result = MutableLiveData("0")
    val result: LiveData<String> = _result

    private var currentNumber = StringBuilder()
    private var pendingOperator: String? = null
    private var operand1: Double? = null
    private var lastResult: Double? = null
    private var hasDecimal = false
    private var isResultDisplayed = false

    private val formatter = DecimalFormat("#,##0.##########")

    /**
     * Appends a digit to the current input.
     */
    fun onDigit(digit: String) {
        if (isResultDisplayed) {
            // Start a fresh expression after pressing a digit post-result
            clear()
        }
        // Prevent leading zeros (allow "0.")
        if (currentNumber.toString() == "0" && digit == "0") return
        if (currentNumber.toString() == "0" && digit != ".") {
            currentNumber.clear()
        }
        currentNumber.append(digit)
        updateDisplay()
    }

    /**
     * Appends a decimal point.
     */
    fun onDecimal() {
        if (isResultDisplayed) {
            clear()
        }
        if (hasDecimal) return
        if (currentNumber.isEmpty()) {
            currentNumber.append("0")
        }
        currentNumber.append(".")
        hasDecimal = true
        updateDisplay()
    }

    /**
     * Handles an arithmetic operator press.
     */
    fun onOperator(operator: String) {
        if (isResultDisplayed) {
            // Chain from previous result
            operand1 = lastResult
            isResultDisplayed = false
            currentNumber.clear()
            pendingOperator = operator
            _expression.value = formatNumber(operand1!!) + " " + operator
            return
        }

        if (currentNumber.isNotEmpty()) {
            val currentVal = currentNumber.toString().toDoubleOrNull() ?: return
            if (operand1 != null && pendingOperator != null) {
                // Evaluate intermediate result
                operand1 = evaluate(operand1!!, currentVal, pendingOperator!!)
                _result.value = formatNumber(operand1!!)
            } else {
                operand1 = currentVal
            }
            currentNumber.clear()
            hasDecimal = false
        }

        if (operand1 != null) {
            pendingOperator = operator
            _expression.value = formatNumber(operand1!!) + " " + operator
        }
    }

    /**
     * Evaluates the full expression and displays the result.
     */
    fun onEquals() {
        if (operand1 == null || pendingOperator == null) return
        val currentVal = if (currentNumber.isNotEmpty()) {
            currentNumber.toString().toDoubleOrNull() ?: return
        } else {
            // Pressing "=" again without entering a new number
            return
        }

        val expr = formatNumber(operand1!!) + " " + pendingOperator + " " + formatNumber(currentVal)
        val result = evaluate(operand1!!, currentVal, pendingOperator!!)

        _expression.value = expr + " ="
        _result.value = formatNumber(result)

        lastResult = result
        operand1 = null
        pendingOperator = null
        currentNumber.clear()
        hasDecimal = false
        isResultDisplayed = true
    }

    /**
     * Clears all state.
     */
    fun clear() {
        currentNumber.clear()
        pendingOperator = null
        operand1 = null
        lastResult = null
        hasDecimal = false
        isResultDisplayed = false
        _expression.value = ""
        _result.value = "0"
    }

    /**
     * Deletes the last character of the current input.
     */
    fun onBackspace() {
        if (isResultDisplayed) return
        if (currentNumber.isNotEmpty()) {
            val removed = currentNumber.last()
            currentNumber.deleteCharAt(currentNumber.length - 1)
            if (removed == '.') hasDecimal = false
            updateDisplay()
        }
    }

    /**
     * Converts the current number to a percentage (÷ 100).
     */
    fun onPercent() {
        val num = if (isResultDisplayed && lastResult != null) {
            clear()
            lastResult!!
        } else {
            currentNumber.toString().toDoubleOrNull() ?: return
        }
        val pct = num / 100.0
        currentNumber.clear()
        currentNumber.append(pct.toBigDecimal().stripTrailingZeros().toPlainString())
        hasDecimal = currentNumber.contains(".")
        isResultDisplayed = false
        updateDisplay()
    }

    /**
     * Negates the current number.
     */
    fun onNegate() {
        if (isResultDisplayed && lastResult != null) {
            val negated = -(lastResult!!)
            lastResult = negated
            _result.value = formatNumber(negated)
            return
        }
        if (currentNumber.isEmpty()) return
        val num = currentNumber.toString().toDoubleOrNull() ?: return
        currentNumber.clear()
        currentNumber.append((-num).toBigDecimal().stripTrailingZeros().toPlainString())
        hasDecimal = currentNumber.contains(".")
        updateDisplay()
    }

    // -- Private helpers --

    private fun updateDisplay() {
        val text = currentNumber.toString()
        _result.value = if (text.isEmpty() || text == "-") "0" else {
            // Format only the integer portion while typing
            if (text.contains(".")) {
                val parts = text.split(".")
                val intPart = parts[0].toLongOrNull()?.let { formatter.format(it) } ?: parts[0]
                "$intPart.${parts.getOrElse(1) { "" }}"
            } else {
                text.toLongOrNull()?.let { formatter.format(it) } ?: text
            }
        }
    }

    private fun evaluate(a: Double, b: Double, op: String): Double {
        return when (op) {
            "+" -> a + b
            "−" -> a - b
            "×" -> a * b
            "÷" -> if (b != 0.0) a / b else Double.NaN
            else -> b
        }
    }

    private fun formatNumber(value: Double): String {
        if (value.isNaN()) return "Error"
        if (value.isInfinite()) return "Error"
        return if (value == value.toLong().toDouble() && !value.isNaN()) {
            formatter.format(value.toLong())
        } else {
            formatter.format(value)
        }
    }
}
