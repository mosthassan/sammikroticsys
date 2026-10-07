package com.example.core.model

/**
 * Exchange rate between currencies represented in micro-units (scale = 6).
 * 1.0 = 1_000_000L micros.
 * Example: 540.50 YER per 1 USD -> rateMicros = 540_500_000L.
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
            // E.g. whole = 540, decimal4Digits = 5000 -> 540.5000 -> 540_500_000L
            val rateMicros = whole * SCALE_MICROS + decimal4Digits * 100L
            return ExchangeRate(from, to, rateMicros)
        }

        /**
         * Precision Rate Parser: Pure integer math only (NO Float/Double).
         * Supports Arabic-Indic numerals (٠-٩) and Arabic decimal separator (٫).
         * Rejects inputs with more than 6 decimal places, non-digits, or values <= 0.
         */
        fun parseRateFromUserInput(input: String): Long? {
            val clean = input.trim()
                .replace("،", "")
                .replace("٬", "")
                .replace(",", "")
                .replace(" ", "")
                .replace("٫", ".")
            if (clean.isEmpty()) return null

            val sb = StringBuilder()
            for (ch in clean) {
                when (ch) {
                    in '٠'..'٩' -> sb.append(ch - '٠')
                    in '0'..'9' -> sb.append(ch)
                    '.' -> sb.append('.')
                    else -> return null
                }
            }
            val normalized = sb.toString()
            if (normalized.isEmpty()) return null

            val parts = normalized.split(".")
            if (parts.size > 2) return null

            val whole = parts[0].ifEmpty { "0" }.toLongOrNull() ?: return null
            if (whole < 0L) return null

            val frac = if (parts.size == 2) {
                if (parts[1].length > 6) return null // Reject > 6 decimals
                val fracStr = parts[1].padEnd(6, '0')
                fracStr.toLongOrNull() ?: return null
            } else {
                0L
            }

            val totalMicros = try {
                Math.addExact(Math.multiplyExact(whole, SCALE_MICROS), frac)
            } catch (e: ArithmeticException) {
                return null
            }

            return if (totalMicros > 0L) totalMicros else null
        }

        /**
         * Formats rate in micros (e.g. 540_000_000L -> "540.00", 540_250_000L -> "540.25").
         */
        fun formatRateMicros(rateMicros: Long): String {
            val whole = rateMicros / SCALE_MICROS
            val frac = rateMicros % SCALE_MICROS
            if (frac == 0L) {
                return String.format(java.util.Locale.US, "%,d.00", whole)
            }
            // Format 6-digit fraction and trim trailing zeros down to minimum 2 decimals
            var fracStr = String.format(java.util.Locale.US, "%06d", frac)
            while (fracStr.length > 2 && fracStr.endsWith("0")) {
                fracStr = fracStr.dropLast(1)
            }
            return String.format(java.util.Locale.US, "%,d.%s", whole, fracStr)
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
