package com.exteragram.messenger.math

import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.abs

object MathFormat {

    private const val MAX_ABS_VALUE = 1e12
    private const val MIN_ABS_VALUE = 1e-9
    private const val PRECISION = 12
    private const val MAX_LENGTH = 24

    fun format(value: Double, decimalSeparator: Char): String? {
        if (value.isNaN() || value.isInfinite()) {
            return null
        }
        val absValue = abs(value)
        if (absValue >= MAX_ABS_VALUE || (absValue != 0.0 && absValue < MIN_ABS_VALUE)) {
            return null
        }
        val rounded = BigDecimal.valueOf(value).round(MathContext(PRECISION))
        val stripped = if (rounded.signum() == 0) BigDecimal.ZERO else rounded.stripTrailingZeros()
        var text = stripped.toPlainString()
        if (text == "-0") {
            text = "0"
        }
        if (text.length > MAX_LENGTH) {
            return null
        }
        return if (decimalSeparator == '.') text else text.replace('.', decimalSeparator)
    }
}
