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

/**
 * Thrown when a disbursement, transfer, or currency exchange would cause a treasury's
 * net balance in its original currency to fall below zero and allowNegative is false.
 * Zero-Overdraft Invariant (B.1).
 */
class InsufficientTreasuryFundsException(
    val treasuryId: String,
    val availableMinor: Long,
    val requiredMinor: Long,
    message: String = "Insufficient treasury funds in $treasuryId: available $availableMinor minor units, required $requiredMinor minor units"
) : IllegalStateException(message)
