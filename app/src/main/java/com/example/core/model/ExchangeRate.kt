package com.example.core.model

/**
 * Exchange rate between currencies represented in micro-units (scale = 6).
 * 1.0 = 1_000_000L micros.
 * Example: 530.50 YER per 1 USD -> rateMicros = 530_500_000L.
 * Conversions perform pure integer half-up rounding without any floating-point numbers.
 */
data class ExchangeRate(
    val fromCurrency: CurrencyCode,
    val toCurrency: CurrencyCode,
    val rateMicros: Long
) {
    init {
        require(rateMicros > 0L) { "Exchange rate must be strictly positive" }
    }

    /**
     * Converts a minor amount from [fromCurrency] to [toCurrency] using half-up rounding.
     */
    fun convert(origMinor: Long): Long {
        if (fromCurrency == toCurrency) return origMinor

        val sign = if (origMinor < 0L) -1L else 1L
        val absMinor = kotlin.math.abs(origMinor)

        // (absMinor * rateMicros + 500_000L) / 1_000_000L
        val product = Math.multiplyExact(absMinor, rateMicros)
        val rounded = (product + HALF_MICROS) / SCALE_MICROS
        return rounded * sign
    }

    companion object {
        const val SCALE_MICROS = 1_000_000L
        const val HALF_MICROS = 500_000L

        fun parity(currency: CurrencyCode): ExchangeRate {
            return ExchangeRate(currency, currency, SCALE_MICROS)
        }

        fun fromMajorDecimal(from: CurrencyCode, to: CurrencyCode, whole: Long, decimal4Digits: Long): ExchangeRate {
            // E.g. whole = 530, decimal4Digits = 5000 -> 530.5000 -> 530_500_000L
            val rateMicros = whole * SCALE_MICROS + decimal4Digits * 100L
            return ExchangeRate(from, to, rateMicros)
        }

        /**
         * Converts multiple lines and ensures their sum equals [expectedTotalBaseMinor] exactly.
         * The rounding remainder (if any) is assigned to the last line.
         */
        fun distributeConvertedLines(
            lineOrigAmounts: List<Long>,
            rate: ExchangeRate,
            expectedTotalBaseMinor: Long
        ): List<Long> {
            if (lineOrigAmounts.isEmpty()) return emptyList()

            val converted = lineOrigAmounts.map { rate.convert(it) }.toMutableList()
            val currentSum = converted.sum()
            val remainder = expectedTotalBaseMinor - currentSum

            if (remainder != 0L) {
                // Adjust the last line to absorb rounding difference
                val lastIdx = converted.lastIndex
                converted[lastIdx] = converted[lastIdx] + remainder
            }

            return converted
        }
    }
}
