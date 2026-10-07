package com.example.core.model

import java.util.Locale

/**
 * Immutable monetary representation stored in minor units (scale = 2).
 * 100 minor units = 1.00 major currency unit.
 * Floating point (Float, Double) is strictly forbidden for all financial calculations.
 */
data class Money(
    val minor: Long,
    val currency: CurrencyCode
) : Comparable<Money> {

    init {
        // Enforce consistent currency scale
        require(currency.scale == 2) { "Only scale 2 currencies are supported in v1" }
    }

    operator fun plus(other: Money): Money {
        requireSameCurrency(other)
        return Money(Math.addExact(this.minor, other.minor), currency)
    }

    operator fun minus(other: Money): Money {
        requireSameCurrency(other)
        return Money(Math.subtractExact(this.minor, other.minor), currency)
    }

    operator fun times(quantity: Long): Money {
        return Money(Math.multiplyExact(this.minor, quantity), currency)
    }

    operator fun unaryMinus(): Money {
        return Money(-this.minor, currency)
    }

    override fun compareTo(other: Money): Int {
        requireSameCurrency(other)
        return this.minor.compareTo(other.minor)
    }

    val isZero: Boolean get() = minor == 0L
    val isPositive: Boolean get() = minor > 0L
    val isNegative: Boolean get() = minor < 0L

    fun abs(): Money = Money(kotlin.math.abs(minor), currency)

    /**
     * Formats the monetary amount into a human-readable display string.
     * E.g. 150050 minor -> "1,500.50 ر.ي"
     */
    fun format(includeSymbol: Boolean = true, useArabicNumerals: Boolean = false): String {
        val isNeg = minor < 0L
        val absMinor = kotlin.math.abs(minor)
        val wholePart = absMinor / 100L
        val fractionPart = absMinor % 100L

        // Format whole part with thousands separators using pure integer string manipulation
        val wholeStr = String.format(Locale.US, "%,d", wholePart)
        val fracStr = String.format(Locale.US, "%02d", fractionPart)
        val rawNum = "${if (isNeg) "-" else ""}$wholeStr.$fracStr"

        val formattedNum = if (useArabicNumerals) {
            toArabicDigits(rawNum)
        } else {
            rawNum
        }

        return if (includeSymbol) {
            "$formattedNum ${currency.symbol}"
        } else {
            formattedNum
        }
    }

    private fun requireSameCurrency(other: Money) {
        require(this.currency == other.currency) {
            "Currency mismatch: cannot perform arithmetic between ${this.currency} and ${other.currency}"
        }
    }

    companion object {
        fun zero(currency: CurrencyCode): Money = Money(0L, currency)

        fun ofMajor(major: Long, currency: CurrencyCode): Money {
            return Money(Math.multiplyExact(major, 100L), currency)
        }

        fun parseFromUserInput(input: String, currency: CurrencyCode): Money? {
            val clean = input.trim()
                .replace("،", "")
                .replace("٬", "")
                .replace(",", "")
                .replace(" ", "")
                .replace("٫", ".")
            if (clean.isEmpty()) return null

            // Support both western and eastern arabic digits
            val normalized = normalizeDigits(clean)
            val parts = normalized.split(".")
            if (parts.size > 2) return null

            val whole = parts[0].toLongOrNull() ?: return null
            val frac = if (parts.size == 2) {
                if (parts[1].length > 2) return null // Disallow more than 2 decimal digits without warning
                val fracStr = parts[1].padEnd(2, '0')
                fracStr.toLongOrNull() ?: return null
            } else {
                0L
            }

            val sign = if (whole < 0 || normalized.startsWith("-")) -1L else 1L
            val absWhole = kotlin.math.abs(whole)
            val totalMinor = (absWhole * 100L + frac) * sign
            return Money(totalMinor, currency)
        }

        private fun normalizeDigits(str: String): String {
            val sb = java.lang.StringBuilder()
            for (ch in str) {
                when (ch) {
                    in '٠'..'٩' -> sb.append(ch - '٠')
                    in '0'..'9' -> sb.append(ch)
                    '.', '٫' -> sb.append('.')
                    '-' -> sb.append('-')
                    else -> {}
                }
            }
            return sb.toString()
        }

        private fun toArabicDigits(str: String): String {
            val sb = java.lang.StringBuilder()
            for (ch in str) {
                if (ch in '0'..'9') {
                    sb.append((ch.code - '0'.code + 0x0660).toChar())
                } else {
                    sb.append(ch)
                }
            }
            return sb.toString()
        }
    }
}
