package com.example.domain.usecase

import androidx.room.withTransaction
import com.example.core.ledger.AccountConstants
import com.example.core.ledger.DocumentStatus
import com.example.core.ledger.DocumentType
import com.example.core.ledger.JournalDraft
import com.example.core.ledger.JournalDraftLine
import com.example.core.ledger.JournalEntryType
import com.example.core.model.CurrencyCode
import com.example.core.model.ExchangeRate
import com.example.core.model.Money
import com.example.core.model.RateZone
import com.example.core.model.UuidUtils
import com.example.data.ledger.LedgerInvariants
import com.example.data.ledger.LedgerWriter
import com.example.data.local.AppDatabase
import com.example.data.local.entity.DocumentEntity
import com.example.data.local.entity.JournalEntryEntity
import com.example.data.local.entity.JournalLineEntity

/**
 * Item specification for periodic revaluation candidates.
 */
data class RevaluationCandidate(
    val accountCode: String,
    val accountName: String,
    val treasuryId: String? = null,
    val partyId: String? = null,
    val currency: CurrencyCode,
    val origBalanceMinor: Long,
    val bookBaseBalanceMinor: Long,
    val currentRate: ExchangeRate,
    val revaluedBaseBalanceMinor: Long,
    val deltaBaseMinor: Long // revaluedBaseBalanceMinor - bookBaseBalanceMinor (+ = gain, - = loss)
)

data class RevaluationResult(
    val document: DocumentEntity?,
    val candidates: List<RevaluationCandidate>,
    val totalUnrealizedGainMinor: Long,
    val totalUnrealizedLossMinor: Long,
    val netUnrealizedDeltaMinor: Long
)

/**
 * IAS 21 Periodic Revaluation Engine (D.1)
 *
 * Rules:
 * 1. Scope strictly limited to Monetary Items in foreign currency:
 *    - Foreign Treasury Accounts (Cash / Bank: 1101 / 1102)
 *    - Foreign Accounts Receivable (1201)
 *    - Foreign Accounts Payable (2101)
 * 2. Strict Invariant: Non-monetary items (Fixed Assets 1501, Inventory 1401, Partner Capital 3101) MUST NEVER be revalued.
 * 3. Delta calculation:
 *    delta = (Current Market Rate in YER * foreign balance) - Book YER Balance
 * 4. Auto-generate Revaluation Journal Entry:
 *    - If gain (delta > 0): Dr Monetary Account, Cr Unrealized FX Gain (4902)
 *    - If loss (delta < 0): Dr Unrealized FX Loss (5902), Cr Monetary Account
 * 5. Revaluation document type: PERIODIC_REVALUATION (REV).
 */
class PeriodicRevaluationUseCase(
    private val db: AppDatabase,
    private val resolver: ExchangeRateResolver
) {
    private val invariants = LedgerInvariants(db)

    /**
     * Set of monetary account codes eligible for periodic revaluation under IAS 21.
     */
    val eligibleMonetaryAccounts = setOf(
        AccountConstants.CASH_VAULT,          // 1101
        AccountConstants.BANKS_WALLETS,        // 1102
        AccountConstants.ACCOUNTS_RECEIVABLE,  // 1201
        AccountConstants.ACCOUNTS_PAYABLE      // 2101
    )

    /**
     * Non-monetary accounts strictly forbidden from revaluation under IAS 21.
     */
    val forbiddenNonMonetaryAccounts = setOf(
        AccountConstants.CAPITAL,               // 3101
        AccountConstants.CARD_INVENTORY_RESERVE,// 1401
        AccountConstants.FIXED_ASSETS_NETWORK,  // 1501
        AccountConstants.ACCUMULATED_DEPRECIATION, // 1599
        AccountConstants.RETAINED_EARNINGS      // 3301
    )

    /**
     * Evaluates all monetary accounts and determines the revaluation candidates and delta amounts.
     */
    suspend fun evaluateCandidates(
        asOfDateEpochDay: Long,
        rateZone: RateZone = RateZone.DEFAULT
    ): List<RevaluationCandidate> {
        val candidates = mutableListOf<RevaluationCandidate>()

        // 1. Foreign Treasury Accounts (1101 / 1102)
        val treasuries = db.treasuryDao().getAllTreasuriesSync().filter { it.isActive }
        for (treasury in treasuries) {
            val currency = CurrencyCode.fromString(treasury.currency)
            if (currency == CurrencyCode.FUNCTIONAL) continue // Skip base currency YER

            val origBalance = db.journalDao().getNetOrigBalanceForTreasury(treasury.id)
            val bookBaseBalance = db.journalDao().getNetDebitBalanceForTreasury(treasury.id)

            if (origBalance == 0L && bookBaseBalance == 0L) continue

            val currentRate = resolver.resolveOrNull(currency, asOfDateEpochDay, rateZone)
                ?: continue

            val revaluedBase = currentRate.convert(origBalance)
            val delta = revaluedBase - bookBaseBalance

            candidates.add(
                RevaluationCandidate(
                    accountCode = treasury.glAccountCode,
                    accountName = treasury.name,
                    treasuryId = treasury.id,
                    partyId = null,
                    currency = currency,
                    origBalanceMinor = origBalance,
                    bookBaseBalanceMinor = bookBaseBalance,
                    currentRate = currentRate,
                    revaluedBaseBalanceMinor = revaluedBase,
                    deltaBaseMinor = delta
                )
            )
        }

        // 2. Foreign Accounts Receivable (1201) & Accounts Payable (2101) per Party
        val allLines = db.journalDao().getAllLinesSync()
        val foreignPartyLines = allLines.filter { line ->
            (line.accountCode == AccountConstants.ACCOUNTS_RECEIVABLE || line.accountCode == AccountConstants.ACCOUNTS_PAYABLE) &&
                    line.partyId != null &&
                    line.currency != CurrencyCode.FUNCTIONAL.name
        }

        val grouped = foreignPartyLines.groupBy { Pair(it.accountCode, Pair(it.partyId!!, it.currency)) }
        for ((key, lines) in grouped) {
            val accountCode = key.first
            val partyId = key.second.first
            val currencyStr = key.second.second
            val currency = CurrencyCode.fromString(currencyStr)
            val party = db.partyDao().getPartyById(partyId)

            val isReceivable = accountCode == AccountConstants.ACCOUNTS_RECEIVABLE

            // For Receivables (Debit normal): debit - credit
            // For Payables (Credit normal): credit - debit
            val origBalance = lines.sumOf { line ->
                if (isReceivable) {
                    if (line.baseDebitMinor > 0) line.origMinor else -line.origMinor
                } else {
                    if (line.baseCreditMinor > 0) line.origMinor else -line.origMinor
                }
            }

            val bookBaseBalance = lines.sumOf { line ->
                if (isReceivable) {
                    line.baseDebitMinor - line.baseCreditMinor
                } else {
                    line.baseCreditMinor - line.baseDebitMinor
                }
            }

            if (origBalance == 0L && bookBaseBalance == 0L) continue

            val currentRate = resolver.resolveOrNull(currency, asOfDateEpochDay, rateZone)
                ?: continue

            val revaluedBase = currentRate.convert(origBalance)
            val delta = revaluedBase - bookBaseBalance

            val accName = if (isReceivable) "مدينون - ${party?.name ?: partyId}" else "دائنون - ${party?.name ?: partyId}"

            candidates.add(
                RevaluationCandidate(
                    accountCode = accountCode,
                    accountName = accName,
                    treasuryId = null,
                    partyId = partyId,
                    currency = currency,
                    origBalanceMinor = origBalance,
                    bookBaseBalanceMinor = bookBaseBalance,
                    currentRate = currentRate,
                    revaluedBaseBalanceMinor = revaluedBase,
                    deltaBaseMinor = delta
                )
            )
        }

        return candidates
    }

    /**
     * Executes the periodic revaluation:
     * - Generates the PERIODIC_REVALUATION (REV) document.
     * - Auto-generates Journal Entry lines:
     *   * Gain (delta > 0):
     *     - If Asset/Receivable: DR Account, CR Unrealized FX Gain 4902
     *     - If Liability/Payable: DR Unrealized FX Loss 5902, CR Account (or vice-versa depending on normal balance)
     *   * Loss (delta < 0):
     *     - If Asset/Receivable: DR Unrealized FX Loss 5902, CR Account
     *     - If Liability/Payable: DR Account, CR Unrealized FX Gain 4902
     */
    suspend fun executeRevaluation(
        asOfDateEpochDay: Long,
        fiscalYear: Int,
        rateZone: RateZone = RateZone.DEFAULT,
        memo: String = "",
        enableInvariantValidation: Boolean = true
    ): RevaluationResult = db.withTransaction {
        // Assert IAS 21 invariant: never revalue non-monetary items
        val candidates = evaluateCandidates(asOfDateEpochDay, rateZone).filter { it.deltaBaseMinor != 0L }

        for (candidate in candidates) {
            check(candidate.accountCode in eligibleMonetaryAccounts) {
                "IAS 21 Violation: Account ${candidate.accountCode} is not an eligible monetary item"
            }
            check(candidate.accountCode !in forbiddenNonMonetaryAccounts) {
                "IAS 21 Strict Violation: Attempted to revalue non-monetary account ${candidate.accountCode}"
            }
        }

        if (candidates.isEmpty()) {
            return@withTransaction RevaluationResult(
                document = null,
                candidates = emptyList(),
                totalUnrealizedGainMinor = 0L,
                totalUnrealizedLossMinor = 0L,
                netUnrealizedDeltaMinor = 0L
            )
        }

        val draftLines = mutableListOf<JournalDraftLine>()
        var lineNo = 1
        var totalGain = 0L
        var totalLoss = 0L

        for (cand in candidates) {
            val delta = cand.deltaBaseMinor
            val absDelta = kotlin.math.abs(delta)
            val isAsset = cand.accountCode == AccountConstants.CASH_VAULT ||
                    cand.accountCode == AccountConstants.BANKS_WALLETS ||
                    cand.accountCode == AccountConstants.ACCOUNTS_RECEIVABLE

            if (isAsset) {
                if (delta > 0L) {
                    // Gain on asset: Dr Asset, Cr Unrealized FX Gain (4902)
                    totalGain += absDelta
                    draftLines.add(
                        JournalDraftLine(
                            lineNo = lineNo++,
                            accountCode = cand.accountCode,
                            partyId = cand.partyId,
                            treasuryId = cand.treasuryId,
                            origMinor = 0L, // revaluation adjustment in base YER only
                            currency = cand.currency,
                            exchangeRateMicros = cand.currentRate.rateMicros,
                            baseDebitMinor = absDelta,
                            baseCreditMinor = 0L,
                            memo = "إعادة تقييم عملة دورية: زيادة أصول نقدية"
                        )
                    )
                    draftLines.add(
                        JournalDraftLine(
                            lineNo = lineNo++,
                            accountCode = AccountConstants.UNREALIZED_FX_GAIN,
                            partyId = null,
                            treasuryId = null,
                            origMinor = 0L,
                            currency = CurrencyCode.FUNCTIONAL,
                            exchangeRateMicros = ExchangeRate.SCALE_MICROS,
                            baseDebitMinor = 0L,
                            baseCreditMinor = absDelta,
                            memo = "أرباح غير محققة لإعادة تقييم ${cand.accountName}"
                        )
                    )
                } else {
                    // Loss on asset: Dr Unrealized FX Loss (5902), Cr Asset
                    totalLoss += absDelta
                    draftLines.add(
                        JournalDraftLine(
                            lineNo = lineNo++,
                            accountCode = AccountConstants.UNREALIZED_FX_LOSS,
                            partyId = null,
                            treasuryId = null,
                            origMinor = 0L,
                            currency = CurrencyCode.FUNCTIONAL,
                            exchangeRateMicros = ExchangeRate.SCALE_MICROS,
                            baseDebitMinor = absDelta,
                            baseCreditMinor = 0L,
                            memo = "خسائر غير محققة لإعادة تقييم ${cand.accountName}"
                        )
                    )
                    draftLines.add(
                        JournalDraftLine(
                            lineNo = lineNo++,
                            accountCode = cand.accountCode,
                            partyId = cand.partyId,
                            treasuryId = cand.treasuryId,
                            origMinor = 0L,
                            currency = cand.currency,
                            exchangeRateMicros = cand.currentRate.rateMicros,
                            baseDebitMinor = 0L,
                            baseCreditMinor = absDelta,
                            memo = "إعادة تقييم عملة دورية: انخفاض أصول نقدية"
                        )
                    )
                }
            } else {
                // Liability (Payables 2101): Credit normal
                // If revalued base > book base, debt increased => Loss
                if (delta > 0L) {
                    // Increase in liability => Loss: Dr Unrealized FX Loss (5902), Cr Payable
                    totalLoss += absDelta
                    draftLines.add(
                        JournalDraftLine(
                            lineNo = lineNo++,
                            accountCode = AccountConstants.UNREALIZED_FX_LOSS,
                            partyId = null,
                            treasuryId = null,
                            origMinor = 0L,
                            currency = CurrencyCode.FUNCTIONAL,
                            exchangeRateMicros = ExchangeRate.SCALE_MICROS,
                            baseDebitMinor = absDelta,
                            baseCreditMinor = 0L,
                            memo = "خسائر غير محققة لزيادة الالتزامات ${cand.accountName}"
                        )
                    )
                    draftLines.add(
                        JournalDraftLine(
                            lineNo = lineNo++,
                            accountCode = cand.accountCode,
                            partyId = cand.partyId,
                            treasuryId = cand.treasuryId,
                            origMinor = 0L,
                            currency = cand.currency,
                            exchangeRateMicros = cand.currentRate.rateMicros,
                            baseDebitMinor = 0L,
                            baseCreditMinor = absDelta,
                            memo = "إعادة تقييم عملة دورية: زيادة التزامات الموردين"
                        )
                    )
                } else {
                    // Decrease in liability => Gain: Dr Payable, Cr Unrealized FX Gain (4902)
                    totalGain += absDelta
                    draftLines.add(
                        JournalDraftLine(
                            lineNo = lineNo++,
                            accountCode = cand.accountCode,
                            partyId = cand.partyId,
                            treasuryId = cand.treasuryId,
                            origMinor = 0L,
                            currency = cand.currency,
                            exchangeRateMicros = cand.currentRate.rateMicros,
                            baseDebitMinor = absDelta,
                            baseCreditMinor = 0L,
                            memo = "إعادة تقييم عملة دورية: انخفاض التزامات الموردين"
                        )
                    )
                    draftLines.add(
                        JournalDraftLine(
                            lineNo = lineNo++,
                            accountCode = AccountConstants.UNREALIZED_FX_GAIN,
                            partyId = null,
                            treasuryId = null,
                            origMinor = 0L,
                            currency = CurrencyCode.FUNCTIONAL,
                            exchangeRateMicros = ExchangeRate.SCALE_MICROS,
                            baseDebitMinor = 0L,
                            baseCreditMinor = absDelta,
                            memo = "أرباح غير محققة لانخفاض الالتزامات ${cand.accountName}"
                        )
                    )
                }
            }
        }

        val totalDebit = draftLines.sumOf { it.baseDebitMinor }
        val totalCredit = draftLines.sumOf { it.baseCreditMinor }
        check(totalDebit == totalCredit) {
            "Revaluation Draft Balancing Error: Debits ($totalDebit) != Credits ($totalCredit)"
        }

        // Allocate sequence and create DocumentEntity
        val nextSeq = (db.documentDao().getAllDocumentsSync()
            .filter { it.type == DocumentType.PERIODIC_REVALUATION.name && it.fiscalYear == fiscalYear }
            .maxOfOrNull { it.docNumber } ?: 0L) + 1L

        val docId = UuidUtils.newTimeOrderedId()
        val docEntity = DocumentEntity(
            id = docId,
            type = DocumentType.PERIODIC_REVALUATION.name,
            fiscalYear = fiscalYear,
            docNumber = nextSeq,
            partyId = AppDatabase.WALK_IN_CASH_PARTY_ID,
            dateEpochDay = asOfDateEpochDay,
            currency = CurrencyCode.FUNCTIONAL.name,
            exchangeRateMicros = ExchangeRate.SCALE_MICROS,
            rateZone = rateZone.name,
            totalMinor = totalDebit,
            totalBaseMinor = totalDebit,
            status = DocumentStatus.POSTED.name,
            notes = if (memo.isBlank()) "إعادة تقييم العملات الدورية IAS 21 بتاريخ $asOfDateEpochDay" else memo
        )

        db.documentDao().insertDocument(docEntity)

        // Insert Journal Entry & Lines
        val entryId = UuidUtils.newTimeOrderedId()
        val entryEntity = JournalEntryEntity(
            id = entryId,
            docId = docId,
            entryNumber = nextSeq,
            entryDateEpochDay = asOfDateEpochDay,
            type = JournalEntryType.NORMAL.name,
            memo = docEntity.notes
        )
        db.journalDao().insertEntry(entryEntity)

        val lineEntities = draftLines.map { dl ->
            JournalLineEntity(
                id = UuidUtils.newTimeOrderedId(),
                entryId = entryId,
                lineNo = dl.lineNo,
                accountCode = dl.accountCode,
                partyId = dl.partyId,
                treasuryId = dl.treasuryId,
                origMinor = dl.origMinor,
                currency = dl.currency.name,
                exchangeRateMicros = dl.exchangeRateMicros,
                baseDebitMinor = dl.baseDebitMinor,
                baseCreditMinor = dl.baseCreditMinor,
                memo = dl.memo
            )
        }
        db.journalDao().insertLines(lineEntities)

        if (enableInvariantValidation) {
            invariants.verifyAll()
        }

        RevaluationResult(
            document = docEntity,
            candidates = candidates,
            totalUnrealizedGainMinor = totalGain,
            totalUnrealizedLossMinor = totalLoss,
            netUnrealizedDeltaMinor = totalGain - totalLoss
        )
    }
}
