package com.example.domain.usecase

import com.example.core.ledger.AccountConstants
import com.example.core.model.CurrencyCode
import com.example.core.model.ExchangeRate
import com.example.core.model.RateZone
import com.example.data.ledger.LedgerWriter
import com.example.data.local.AppDatabase
import com.example.data.local.entity.DocumentEntity

/**
 * IAS 21 Periodic Revaluation Engine (D.1):
 * Revalues Monetary Items only:
 * - Foreign Treasuries (1101/1102)
 * - Foreign Receivables (1201)
 * - Foreign Payables (2101)
 *
 * Strict Invariant: Non-monetary items (Fixed Assets 1501, Inventory 1401, Partner Capital 3101)
 * MUST NEVER be revalued.
 */
class PeriodicRevaluationUseCase(
    private val db: AppDatabase,
    private val writer: LedgerWriter,
    private val resolver: ExchangeRateResolver
) {
    suspend fun revalueMonetaryItem(
        accountCode: String,
        partyId: String? = null,
        treasuryId: String? = null,
        fiscalYear: Int,
        dateEpochDay: Long,
        foreignCurrency: CurrencyCode,
        exchangeRate: ExchangeRate,
        rateZone: RateZone = RateZone.SANAA,
        notes: String? = null
    ): DocumentEntity? {
        return writer.postPeriodicRevaluation(
            accountCode = accountCode,
            partyId = partyId,
            treasuryId = treasuryId,
            fiscalYear = fiscalYear,
            dateEpochDay = dateEpochDay,
            foreignCurrency = foreignCurrency,
            exchangeRate = exchangeRate,
            rateZone = rateZone,
            notes = notes
        )
    }

    suspend fun executePeriodicRevaluationRun(
        fiscalYear: Int,
        dateEpochDay: Long,
        rateZone: RateZone = RateZone.SANAA,
        notes: String? = null
    ): List<DocumentEntity> {
        val postedDocs = mutableListOf<DocumentEntity>()

        // 1. Revalue all foreign treasuries (1101 / 1102)
        val treasuries = db.treasuryDao().getAllTreasuriesSync()
        for (tr in treasuries) {
            if (tr.currency != CurrencyCode.FUNCTIONAL.name) {
                val curr = CurrencyCode.fromString(tr.currency)
                val rate = resolver.resolve(curr, dateEpochDay, rateZone)
                val doc = writer.postPeriodicRevaluation(
                    accountCode = tr.glAccountCode,
                    treasuryId = tr.id,
                    fiscalYear = fiscalYear,
                    dateEpochDay = dateEpochDay,
                    foreignCurrency = curr,
                    exchangeRate = rate,
                    rateZone = rateZone,
                    notes = notes ?: "إعادة تقييم دوري - ${tr.name}"
                )
                if (doc != null) {
                    postedDocs.add(doc)
                }
            }
        }

        // 2. Revalue all foreign receivables (1201)
        val foreignReceivableParties = db.journalDao().getPartiesWithForeignBalance(AccountConstants.ACCOUNTS_RECEIVABLE)
        for (item in foreignReceivableParties) {
            val curr = CurrencyCode.fromString(item.currency)
            val rate = resolver.resolve(curr, dateEpochDay, rateZone)
            val doc = writer.postPeriodicRevaluation(
                accountCode = AccountConstants.ACCOUNTS_RECEIVABLE,
                partyId = item.partyId,
                fiscalYear = fiscalYear,
                dateEpochDay = dateEpochDay,
                foreignCurrency = curr,
                exchangeRate = rate,
                rateZone = rateZone,
                notes = notes ?: "إعادة تقييم دوري لذمم مدينة - العميل ${item.partyId}"
            )
            if (doc != null) {
                postedDocs.add(doc)
            }
        }

        // 3. Revalue all foreign payables (2101)
        val foreignPayableParties = db.journalDao().getPartiesWithForeignBalance(AccountConstants.ACCOUNTS_PAYABLE)
        for (item in foreignPayableParties) {
            val curr = CurrencyCode.fromString(item.currency)
            val rate = resolver.resolve(curr, dateEpochDay, rateZone)
            val doc = writer.postPeriodicRevaluation(
                accountCode = AccountConstants.ACCOUNTS_PAYABLE,
                partyId = item.partyId,
                fiscalYear = fiscalYear,
                dateEpochDay = dateEpochDay,
                foreignCurrency = curr,
                exchangeRate = rate,
                rateZone = rateZone,
                notes = notes ?: "إعادة تقييم دوري لذمم دائنة - المورد ${item.partyId}"
            )
            if (doc != null) {
                postedDocs.add(doc)
            }
        }

        return postedDocs
    }
}
