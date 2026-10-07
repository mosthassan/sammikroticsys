package com.example.core.model

/**
 * Thrown when an exchange rate is requested for a currency/zone/date combination
 * and no matching historical or active rate exists in the database.
 * Fallbacks to 1.0 or arbitrary static numbers are strictly prohibited.
 */
class MissingExchangeRateException(
    val currency: CurrencyCode,
    val zone: RateZone,
    val dateEpochDay: Long,
    message: String = "No exchange rate found for currency ${currency.name} in zone ${zone.name} on or before day $dateEpochDay"
) : IllegalStateException(message)

/**
 * Thrown when attempting to insert an exchange rate that shifts by more than 10%
 * compared to the previous recorded rate without explicit confirmation.
 */
class SignificantRateChangeException(
    val oldRateMicros: Long,
    val newRateMicros: Long,
    val percentChange: Double,
    message: String = "Rate change exceeds 10% (from $oldRateMicros to $newRateMicros). Confirmation required."
) : IllegalArgumentException(message)
