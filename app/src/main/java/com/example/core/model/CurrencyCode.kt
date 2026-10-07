package com.example.core.model

enum class CurrencyCode(val symbol: String, val arabicName: String, val scale: Int = 2) {
    YER("ر.ي", "ريال يمني"),
    USD("$", "دولار أمريكي"),
    SAR("ر.س", "ريال سعودي");

    companion object {
        val FUNCTIONAL = YER

        fun fromString(value: String): CurrencyCode {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: YER
        }
    }
}
