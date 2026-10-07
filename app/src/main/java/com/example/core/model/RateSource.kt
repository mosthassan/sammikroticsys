package com.example.core.model

/**
 * Origin source of exchange rate applied to a financial document.
 */
enum class RateSource {
    SYSTEM_DAILY,
    MANUAL_OVERRIDE,
    CUSTOM_OVERRIDE,
    PARITY
}
