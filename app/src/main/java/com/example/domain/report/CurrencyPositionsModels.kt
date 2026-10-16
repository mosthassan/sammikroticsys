package com.example.domain.report

import com.example.core.model.CurrencyCode
import com.example.core.model.ExchangeRate
import com.example.core.model.Money

data class CurrencyPositionRow(
    val treasuryId: String,
    val treasuryName: String,
    val currency: CurrencyCode,
    val origBalanceMinor: Long,
    val bookValueBaseMinor: Long,
    val exchangeRate: ExchangeRate?,
    val hasRate: Boolean,
    val marketValueBaseMinor: Long?,
    val unrealizedDiffMinor: Long?
) {
    fun formattedOrigBalance(): String {
        return Money(origBalanceMinor, currency).format()
    }

    fun formattedBookValue(): String {
        return Money(bookValueBaseMinor, CurrencyCode.FUNCTIONAL).format()
    }

    fun formattedMarketValue(): String {
        return if (marketValueBaseMinor != null) {
            Money(marketValueBaseMinor, CurrencyCode.FUNCTIONAL).format()
        } else {
            "لا سعر"
        }
    }

    fun formattedUnrealizedDiff(): String {
        return if (unrealizedDiffMinor != null) {
            Money(unrealizedDiffMinor, CurrencyCode.FUNCTIONAL).format()
        } else {
            "غير متاح"
        }
    }
}

data class CurrencyPositionsReport(
    val asOfDateEpochDay: Long,
    val positions: List<CurrencyPositionRow>,
    val totalBookValueBaseMinor: Long,
    val totalMarketValueBaseMinor: Long,
    val totalUnrealizedDiffMinor: Long,
    val hasMissingRates: Boolean,
    val footer: ReportFooter
)
