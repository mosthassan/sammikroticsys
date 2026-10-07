package com.example.domain.usecase

import com.example.core.model.CurrencyCode
import com.example.core.model.ExchangeRate
import com.example.core.model.MissingExchangeRateException
import com.example.core.model.RateZone
import com.example.core.model.SignificantRateChangeException
import com.example.core.model.UuidUtils
import com.example.data.local.AppDatabase
import com.example.data.local.entity.CurrencyRateEntity
import java.time.LocalDate

/**
 * ExchangeRateResolver: Central engine for resolving historical and current exchange rates
 * per zone and date, and appending immutable exchange rate records with audit controls.
 */
class ExchangeRateResolver(
    private val db: AppDatabase
) {
    /**
     * Resolves the exchange rate for [currency] relative to the functional currency (YER)
     * on or before [dateEpochDay] in the specified [zone].
     *
     * - Returns parity (1:1) for YER.
     * - Returns the latest recorded rate where effectiveDateEpochDay <= dateEpochDay for the given zone.
     * - Throws [MissingExchangeRateException] if no rate matches (NO fallback to 1.0 or static numbers).
     */
    suspend fun resolve(
        currency: CurrencyCode,
        dateEpochDay: Long,
        zone: RateZone = RateZone.DEFAULT
    ): ExchangeRate {
        if (currency == CurrencyCode.FUNCTIONAL) {
            return ExchangeRate.parity(CurrencyCode.FUNCTIONAL)
        }

        val rateEntity = db.currencyRateDao().getLatestRate(
            currency = currency.name,
            zone = zone.name,
            dateEpochDay = dateEpochDay
        ) ?: throw MissingExchangeRateException(currency, zone, dateEpochDay)

        return ExchangeRate(
            fromCurrency = currency,
            toCurrency = CurrencyCode.FUNCTIONAL,
            rateMicros = rateEntity.rateMicros
        )
    }

    /**
     * Resolves exchange rate or null if not found.
     */
    suspend fun resolveOrNull(
        currency: CurrencyCode,
        dateEpochDay: Long,
        zone: RateZone = RateZone.DEFAULT
    ): ExchangeRate? {
        if (currency == CurrencyCode.FUNCTIONAL) {
            return ExchangeRate.parity(CurrencyCode.FUNCTIONAL)
        }

        val rateEntity = db.currencyRateDao().getLatestRate(
            currency = currency.name,
            zone = zone.name,
            dateEpochDay = dateEpochDay
        ) ?: return null

        return ExchangeRate(
            fromCurrency = currency,
            toCurrency = CurrencyCode.FUNCTIONAL,
            rateMicros = rateEntity.rateMicros
        )
    }

    /**
     * Appends a new immutable exchange rate entry with validation:
     * 1. Rate must be strictly positive (> 0).
     * 2. Cannot add exchange rate for functional currency (YER is parity).
     * 3. Cannot add rate in a closed fiscal period.
     * 4. Throws [SignificantRateChangeException] if change exceeds 10% compared to previous rate unless [confirmSignificantChange] is true.
     */
    suspend fun addRate(
        currency: CurrencyCode,
        zone: RateZone,
        rateMicros: Long,
        effectiveDateEpochDay: Long,
        createdBy: String = "USER",
        reason: String = "",
        confirmSignificantChange: Boolean = false
    ): CurrencyRateEntity {
        require(rateMicros > 0L) { "Exchange rate must be strictly positive" }
        require(currency != CurrencyCode.FUNCTIONAL) { "Cannot add exchange rate for functional currency (${CurrencyCode.FUNCTIONAL.name})" }

        // Check closed fiscal period
        val localDate = LocalDate.ofEpochDay(effectiveDateEpochDay)
        val period = db.fiscalPeriodDao().getPeriod(localDate.year, localDate.monthValue)
        if (period != null && period.isClosed) {
            throw IllegalStateException("Cannot add exchange rate in closed fiscal period ${localDate.year}/${localDate.monthValue}")
        }

        // Check significant change (> 10%)
        if (!confirmSignificantChange) {
            val previousRate = db.currencyRateDao().getLatestRate(currency.name, zone.name, effectiveDateEpochDay)
            if (previousRate != null && previousRate.rateMicros > 0L) {
                val diff = kotlin.math.abs(rateMicros - previousRate.rateMicros)
                // diff / prev > 0.10 => diff * 10 > prev
                if (diff * 10L > previousRate.rateMicros) {
                    val pct = (diff.toDouble() / previousRate.rateMicros.toDouble()) * 100.0
                    throw SignificantRateChangeException(previousRate.rateMicros, rateMicros, pct)
                }
            }
        }

        val entity = CurrencyRateEntity(
            id = UuidUtils.newTimeOrderedId(),
            currency = currency.name,
            zone = zone.name,
            rateMicros = rateMicros,
            effectiveDateEpochDay = effectiveDateEpochDay,
            createdAt = System.currentTimeMillis(),
            createdBy = createdBy,
            reason = reason
        )

        db.currencyRateDao().insertRate(entity)
        return entity
    }
}
