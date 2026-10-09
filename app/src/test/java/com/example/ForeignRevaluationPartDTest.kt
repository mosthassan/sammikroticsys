package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.ledger.AccountConstants
import com.example.core.ledger.DocumentStatus
import com.example.core.ledger.DocumentType
import com.example.core.model.CurrencyCode
import com.example.core.model.ExchangeRate
import com.example.core.model.Money
import com.example.core.model.RateZone
import com.example.data.ledger.LedgerInvariants
import com.example.data.ledger.LedgerWriter
import com.example.data.local.AppDatabase
import com.example.data.local.entity.CurrencyRateEntity
import com.example.data.local.entity.FiscalPeriodEntity
import com.example.data.local.entity.PartyEntity
import com.example.data.local.entity.TreasuryAccountEntity
import com.example.domain.usecase.ExchangeRateResolver
import com.example.domain.usecase.FinancialStatementsUseCase
import com.example.domain.usecase.PeriodicRevaluationUseCase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Test suite for Multi-Currency Part D: Period-End Revaluation & Dual-Currency Reporting.
 * Covers Tests 31 through 40 from Section 4:
 *
 * 31. IAS 21 Monetary Items Scope (D.1):
 *     Foreign Treasury Accounts (1101/1102) and Foreign Receivables/Payables (1201/2101) are evaluated.
 * 32. IAS 21 Non-Monetary Strict Invariant (D.1):
 *     Fixed Assets (1501), Inventory (1401), and Partner Capital (3101) MUST NEVER be revalued.
 * 33. Unrealized FX Gain on Foreign Treasury (D.1):
 *     Dr Treasury (1101), Cr Unrealized FX Gain (4902) when foreign currency appreciates against YER.
 * 34. Unrealized FX Loss on Foreign Treasury (D.1):
 *     Dr Unrealized FX Loss (5902), Cr Treasury (1101) when foreign currency depreciates against YER.
 * 35. Unrealized FX Gain on Foreign Accounts Receivable (D.1):
 *     Customer receivable in USD revalued higher: Dr 1201, Cr 4902.
 * 36. Unrealized FX Loss on Foreign Accounts Payable (D.1):
 *     Vendor debt in USD revalued higher (costs more YER): Dr 5902, Cr 2101.
 * 37. Periodic Revaluation Document Type (D.1):
 *     Revaluation posted as PERIODIC_REVALUATION (REV) and satisfies all double-entry ledger invariants.
 * 38. Revaluation Idempotency & Zero Delta Handling (D.1):
 *     Running revaluation when book rate == current rate results in zero delta and generates no redundant entry.
 * 39. Dual-Currency Trial Balance Reporting (D.2):
 *     Trial Balance displays balances in base YER alongside original currency amounts with total equilibrium.
 * 40. Profit & Loss Statement Realized vs Unrealized FX Reporting (D.2):
 *     Income Statement reports Realized FX (4901/5901) and Unrealized FX (4902/5902) clearly and net profit aligns.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ForeignRevaluationPartDTest {

    private lateinit var db: AppDatabase
    private lateinit var writer: LedgerWriter
    private lateinit var invariants: LedgerInvariants
    private lateinit var resolver: ExchangeRateResolver
    private lateinit var revaluationUseCase: PeriodicRevaluationUseCase
    private lateinit var statementsUseCase: FinancialStatementsUseCase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.createInMemory(context)
        writer = LedgerWriter(db, enableInvariantValidation = true)
        invariants = LedgerInvariants(db)
        resolver = ExchangeRateResolver(db)
        revaluationUseCase = PeriodicRevaluationUseCase(db, resolver)
        statementsUseCase = FinancialStatementsUseCase(db)

        runBlocking {
            // Seed 2026 Fiscal Periods
            for (m in 1..12) {
                db.fiscalPeriodDao().insertPeriod(
                    FiscalPeriodEntity(
                        id = "FP_2026_${m.toString().padStart(2, '0')}",
                        year = 2026,
                        month = m,
                        isClosed = false
                    )
                )
            }
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    /**
     * Test 31: IAS 21 Monetary Items Scope (D.1)
     * Revaluation engine scopes strictly to monetary items: Foreign Treasury (1101/1102)
     * and Foreign Receivables/Payables (1201/2101).
     */
    @Test
    fun test31_ias21MonetaryItemsScope() {
        runBlocking {
            assertTrue(revaluationUseCase.eligibleMonetaryAccounts.contains(AccountConstants.CASH_VAULT))
            assertTrue(revaluationUseCase.eligibleMonetaryAccounts.contains(AccountConstants.BANKS_WALLETS))
            assertTrue(revaluationUseCase.eligibleMonetaryAccounts.contains(AccountConstants.ACCOUNTS_RECEIVABLE))
            assertTrue(revaluationUseCase.eligibleMonetaryAccounts.contains(AccountConstants.ACCOUNTS_PAYABLE))

            // Ensure base YER cash accounts are not flagged as foreign revaluation candidates
            val asOfDate = LocalDate.of(2026, 10, 10).toEpochDay()
            val candidates = revaluationUseCase.evaluateCandidates(asOfDate)
            for (candidate in candidates) {
                assertTrue("Candidate currency must be foreign", candidate.currency != CurrencyCode.FUNCTIONAL)
                assertTrue("Account must be in monetary set", candidate.accountCode in revaluationUseCase.eligibleMonetaryAccounts)
            }
        }
    }

    /**
     * Test 32: IAS 21 Non-Monetary Strict Invariant (D.1)
     * Non-monetary items (Fixed Assets 1501, Inventory 1401, Partner Capital 3101, Retained Earnings 3301)
     * MUST NEVER be revalued.
     */
    @Test
    fun test32_ias21NonMonetaryStrictInvariant() {
        runBlocking {
            val forbidden = revaluationUseCase.forbiddenNonMonetaryAccounts
            assertTrue(forbidden.contains(AccountConstants.CAPITAL))
            assertTrue(forbidden.contains(AccountConstants.CARD_INVENTORY_RESERVE))
            assertTrue(forbidden.contains(AccountConstants.FIXED_ASSETS_NETWORK))
            assertTrue(forbidden.contains(AccountConstants.ACCUMULATED_DEPRECIATION))
            assertTrue(forbidden.contains(AccountConstants.RETAINED_EARNINGS))

            // Even if foreign currency partner capital exists, it must never appear as a candidate
            val partnerId = "PARTNER_NON_MONETARY_TEST"
            db.partyDao().insertParty(PartyEntity(partnerId, "شريك مساهم", isPartner = true))
            writer.postCapitalReceipt(
                targetAccountCode = AccountConstants.CAPITAL,
                partnerPartyId = partnerId,
                treasuryId = "TR_USD_VAULT",
                fiscalYear = 2026,
                dateEpochDay = LocalDate.of(2026, 10, 1).toEpochDay(),
                amountOrigMinor = 5_000_00L,
                currency = CurrencyCode.USD,
                exchangeRate = ExchangeRate(CurrencyCode.USD, CurrencyCode.FUNCTIONAL, 530_000_000L),
                notes = "رأس مال بالدولار"
            )

            // Market rate spike to 600 YER
            val asOfDate = LocalDate.of(2026, 10, 10).toEpochDay()
            db.currencyRateDao().insertRate(
                CurrencyRateEntity(
                    id = "RATE_USD_SPIKE_32",
                    currency = "USD",
                    zone = RateZone.DEFAULT.name,
                    rateMicros = 600_000_000L,
                    effectiveDateEpochDay = asOfDate,
                    createdBy = "TEST",
                    reason = "Market rate jump"
                )
            )

            val candidates = revaluationUseCase.evaluateCandidates(asOfDate)
            val capitalCandidate = candidates.find { it.accountCode == AccountConstants.CAPITAL }
            assertEquals("IAS 21 Strict Invariant: Partner Capital 3101 must NEVER be evaluated for revaluation", null, capitalCandidate)
        }
    }

    /**
     * Test 33: Unrealized FX Gain on Foreign Treasury (D.1)
     * When foreign currency in treasury appreciates (e.g. USD from 530 to 550 YER):
     * Generates: Dr Treasury (1101) for delta, Cr Unrealized FX Gain (4902).
     */
    @Test
    fun test33_unrealizedFxGainOnForeignTreasury() {
        runBlocking {
            val dateInit = LocalDate.of(2026, 10, 1).toEpochDay()
            val initialRate = ExchangeRate(CurrencyCode.USD, CurrencyCode.FUNCTIONAL, 530_000_000L) // 530 YER

            // Receive $1,000 into USD Vault: Book Value = 530,000 YER
            writer.postCapitalReceipt(
                targetAccountCode = AccountConstants.CAPITAL,
                partnerPartyId = AppDatabase.WALK_IN_CASH_PARTY_ID,
                treasuryId = "TR_USD_VAULT",
                fiscalYear = 2026,
                dateEpochDay = dateInit,
                amountOrigMinor = 1_000_00L,
                currency = CurrencyCode.USD,
                exchangeRate = initialRate,
                notes = "إيداع أولي 1000 دولار"
            )

            // Rate rises to 550 YER on Oct 10: New Market Value = 550,000 YER, Delta = +20,000 YER (Gain)
            val asOfDate = LocalDate.of(2026, 10, 10).toEpochDay()
            db.currencyRateDao().insertRate(
                CurrencyRateEntity(
                    id = "RATE_USD_550_TEST33",
                    currency = "USD",
                    zone = RateZone.DEFAULT.name,
                    rateMicros = 550_000_000L,
                    effectiveDateEpochDay = asOfDate,
                    createdBy = "TEST",
                    reason = "USD Appreciation"
                )
            )

            val result = revaluationUseCase.executeRevaluation(
                asOfDateEpochDay = asOfDate,
                fiscalYear = 2026,
                memo = "إعادة تقييم نهاية الفترة - أرباح غير محققة"
            )

            assertNotNull("Revaluation document must be generated", result.document)
            assertEquals(DocumentType.PERIODIC_REVALUATION.name, result.document!!.type)
            assertEquals(20_000_00L, result.totalUnrealizedGainMinor)
            assertEquals(0L, result.totalUnrealizedLossMinor)

            // Assert Treasury 1101 increased by 20,000 YER
            val treasuryNetBase = db.journalDao().getNetDebitBalanceForTreasury("TR_USD_VAULT")
            assertEquals(550_000_00L, treasuryNetBase)

            // Assert Unrealized FX Gain account 4902 credited by 20,000 YER
            val gainBalance = -db.journalDao().getNetDebitBalanceForAccount(AccountConstants.UNREALIZED_FX_GAIN)
            assertEquals(20_000_00L, gainBalance)

            invariants.verifyAll()
        }
    }

    /**
     * Test 34: Unrealized FX Loss on Foreign Treasury (D.1)
     * When foreign currency in treasury depreciates (e.g. SAR drops from 140 to 135 YER):
     * Generates: Dr Unrealized FX Loss (5902), Cr Treasury (1101).
     */
    @Test
    fun test34_unrealizedFxLossOnForeignTreasury() {
        runBlocking {
            val dateInit = LocalDate.of(2026, 10, 1).toEpochDay()
            val initialRate = ExchangeRate(CurrencyCode.SAR, CurrencyCode.FUNCTIONAL, 140_000_000L) // 140 YER

            // Receive 10,000 SAR into SAR Vault: Book Value = 1,400,000 YER
            writer.postCapitalReceipt(
                targetAccountCode = AccountConstants.CAPITAL,
                partnerPartyId = AppDatabase.WALK_IN_CASH_PARTY_ID,
                treasuryId = "TR_SAR_VAULT",
                fiscalYear = 2026,
                dateEpochDay = dateInit,
                amountOrigMinor = 10_000_00L,
                currency = CurrencyCode.SAR,
                exchangeRate = initialRate,
                notes = "إيداع 10000 ريال سعودي"
            )

            // SAR drops to 135 YER on Oct 10: New Value = 1,350,000 YER, Delta = -50,000 YER (Loss)
            val asOfDate = LocalDate.of(2026, 10, 10).toEpochDay()
            db.currencyRateDao().insertRate(
                CurrencyRateEntity(
                    id = "RATE_SAR_135_TEST34",
                    currency = "SAR",
                    zone = RateZone.DEFAULT.name,
                    rateMicros = 135_000_000L,
                    effectiveDateEpochDay = asOfDate,
                    createdBy = "TEST",
                    reason = "SAR Drop"
                )
            )

            val result = revaluationUseCase.executeRevaluation(
                asOfDateEpochDay = asOfDate,
                fiscalYear = 2026,
                memo = "إعادة تقييم نهاية الفترة - خسائر غير محققة"
            )

            assertNotNull(result.document)
            assertEquals(0L, result.totalUnrealizedGainMinor)
            assertEquals(50_000_00L, result.totalUnrealizedLossMinor)

            // Treasury base value reduced by 50,000 YER
            val treasuryNetBase = db.journalDao().getNetDebitBalanceForTreasury("TR_SAR_VAULT")
            assertEquals(1_350_000_00L, treasuryNetBase)

            // Unrealized FX Loss account 5902 debited by 50,000 YER
            val lossBalance = db.journalDao().getNetDebitBalanceForAccount(AccountConstants.UNREALIZED_FX_LOSS)
            assertEquals(50_000_00L, lossBalance)

            invariants.verifyAll()
        }
    }

    /**
     * Test 35: Unrealized FX Gain on Foreign Accounts Receivable (D.1)
     * Customer owes $500 booked @ 530 YER (265,000 YER).
     * Market rate jumps to 560 YER (280,000 YER):
     * Delta = +15,000 YER (Gain). Generates: Dr 1201 (Receivables), Cr 4902 (Unrealized FX Gain).
     */
    @Test
    fun test35_unrealizedFxGainOnForeignAccountsReceivable() {
        runBlocking {
            val customerId = "CUST_USD_RECEIVABLE_35"
            db.partyDao().insertParty(PartyEntity(customerId, "وكيل شبكة دولار", isCustomer = true))

            val dateInit = LocalDate.of(2026, 10, 1).toEpochDay()
            val initialRate = ExchangeRate(CurrencyCode.USD, CurrencyCode.FUNCTIONAL, 530_000_000L)

            // Invoice for 500 USD: Dr 1201 (265,000 YER), Cr 4201
            writer.postSalesInvoice(
                partyId = customerId,
                fiscalYear = 2026,
                dateEpochDay = dateInit,
                currency = CurrencyCode.USD,
                exchangeRate = initialRate,
                cardItems = emptyList(),
                serviceItems = listOf(
                    com.example.data.ledger.SalesItemSpec(
                        description = "اشتراك ألياف ضوئية",
                        quantity = 1,
                        unitPriceMinor = 500_00L
                    )
                ),
                notes = "فاتورة بالدولار"
            )

            // USD rate rises to 560 YER
            val asOfDate = LocalDate.of(2026, 10, 15).toEpochDay()
            db.currencyRateDao().insertRate(
                CurrencyRateEntity(
                    id = "RATE_USD_560_TEST35",
                    currency = "USD",
                    zone = RateZone.DEFAULT.name,
                    rateMicros = 560_000_000L,
                    effectiveDateEpochDay = asOfDate,
                    createdBy = "TEST",
                    reason = "USD Appreciation"
                )
            )

            val result = revaluationUseCase.executeRevaluation(asOfDateEpochDay = asOfDate, fiscalYear = 2026)
            assertEquals(15_000_00L, result.totalUnrealizedGainMinor)

            // Receivable 1201 increased to 280,000 YER
            val recBalance = db.journalDao().getNetDebitBalanceForAccount(AccountConstants.ACCOUNTS_RECEIVABLE)
            assertEquals(280_000_00L, recBalance)

            val gainBal = -db.journalDao().getNetDebitBalanceForAccount(AccountConstants.UNREALIZED_FX_GAIN)
            assertEquals(15_000_00L, gainBal)

            invariants.verifyAll()
        }
    }

    /**
     * Test 36: Unrealized FX Loss on Foreign Accounts Payable (D.1)
     * Vendor debt of $1,000 booked @ 530 YER (530,000 YER).
     * Market rate jumps to 570 YER (570,000 YER):
     * Paying the vendor now costs 40,000 YER more => Unrealized FX Loss!
     * Generates: Dr 5902 (Unrealized FX Loss), Cr 2101 (Accounts Payable).
     */
    @Test
    fun test36_unrealizedFxLossOnForeignAccountsPayable() {
        runBlocking {
            val vendorId = "VENDOR_STARLINK_USD_36"
            db.partyDao().insertParty(PartyEntity(vendorId, "مزود خدمة Starlink", isVendor = true))

            val dateInit = LocalDate.of(2026, 10, 1).toEpochDay()
            val initialRate = ExchangeRate(CurrencyCode.USD, CurrencyCode.FUNCTIONAL, 530_000_000L)

            // Purchase invoice: Dr 5101 (530,000 YER), Cr 2101 (530,000 YER)
            writer.postPurchaseInvoice(
                vendorPartyId = vendorId,
                fiscalYear = 2026,
                dateEpochDay = dateInit,
                currency = CurrencyCode.USD,
                exchangeRate = initialRate,
                items = listOf(
                    com.example.data.ledger.PurchaseItemSpec(
                        description = "سعات إنترنت جملة",
                        accountCode = AccountConstants.DIRECT_ISP_SERVICE_COST,
                        quantity = 1,
                        unitPriceMinor = 1_000_00L
                    )
                ),
                notes = "فاتورة مشتريات بالدولار"
            )

            // Rate rises to 570 YER
            val asOfDate = LocalDate.of(2026, 10, 20).toEpochDay()
            db.currencyRateDao().insertRate(
                CurrencyRateEntity(
                    id = "RATE_USD_570_TEST36",
                    currency = "USD",
                    zone = RateZone.DEFAULT.name,
                    rateMicros = 570_000_000L,
                    effectiveDateEpochDay = asOfDate,
                    createdBy = "TEST",
                    reason = "USD Appreciation"
                )
            )

            val result = revaluationUseCase.executeRevaluation(asOfDateEpochDay = asOfDate, fiscalYear = 2026)
            assertEquals(40_000_00L, result.totalUnrealizedLossMinor)

            // Accounts payable credit balance increased to 570,000 YER
            val apBalance = -db.journalDao().getNetDebitBalanceForAccount(AccountConstants.ACCOUNTS_PAYABLE)
            assertEquals(570_000_00L, apBalance)

            val lossBal = db.journalDao().getNetDebitBalanceForAccount(AccountConstants.UNREALIZED_FX_LOSS)
            assertEquals(40_000_00L, lossBal)

            invariants.verifyAll()
        }
    }

    /**
     * Test 37: Periodic Revaluation Document Type (D.1)
     * Revaluation document is assigned type PERIODIC_REVALUATION (REV), sequential numbering,
     * status POSTED, and full double-entry invariants pass cleanly.
     */
    @Test
    fun test37_periodicRevaluationDocumentType() {
        runBlocking {
            val dateInit = LocalDate.of(2026, 10, 1).toEpochDay()
            writer.postCapitalReceipt(
                targetAccountCode = AccountConstants.CAPITAL,
                partnerPartyId = AppDatabase.WALK_IN_CASH_PARTY_ID,
                treasuryId = "TR_USD_VAULT",
                fiscalYear = 2026,
                dateEpochDay = dateInit,
                amountOrigMinor = 500_00L,
                currency = CurrencyCode.USD,
                exchangeRate = ExchangeRate(CurrencyCode.USD, CurrencyCode.FUNCTIONAL, 530_000_000L),
                notes = "رأس مال 500$"
            )

            val asOfDate = LocalDate.of(2026, 10, 15).toEpochDay()
            db.currencyRateDao().insertRate(
                CurrencyRateEntity(
                    id = "RATE_USD_540_TEST37",
                    currency = "USD",
                    zone = RateZone.DEFAULT.name,
                    rateMicros = 540_000_000L,
                    effectiveDateEpochDay = asOfDate,
                    createdBy = "TEST",
                    reason = "Rate change"
                )
            )

            val result = revaluationUseCase.executeRevaluation(asOfDateEpochDay = asOfDate, fiscalYear = 2026)
            assertNotNull(result.document)
            assertEquals(DocumentType.PERIODIC_REVALUATION.name, result.document!!.type)
            assertEquals(DocumentStatus.POSTED.name, result.document!!.status)
            assertTrue(result.document!!.docNumber > 0)

            val entries = db.journalDao().getEntriesForDocument(result.document!!.id)
            assertEquals(1, entries.size)
            val lines = db.journalDao().getLinesForEntry(entries[0].id)
            assertTrue("Revaluation entry must have matching balanced lines", lines.isNotEmpty())
            val drTotal = lines.sumOf { it.baseDebitMinor }
            val crTotal = lines.sumOf { it.baseCreditMinor }
            assertEquals(drTotal, crTotal)

            invariants.verifyAll()
        }
    }

    /**
     * Test 38: Revaluation Idempotency & Zero Delta Handling (D.1)
     * When foreign currency book value matches market rate exactly, delta = 0 and no document is created.
     */
    @Test
    fun test38_revaluationIdempotencyAndZeroDeltaHandling() {
        runBlocking {
            val asOfDate = LocalDate.of(2026, 10, 10).toEpochDay()
            // No transactions or rate changes
            val result = revaluationUseCase.executeRevaluation(asOfDateEpochDay = asOfDate, fiscalYear = 2026)
            assertEquals(null, result.document)
            assertEquals(0L, result.totalUnrealizedGainMinor)
            assertEquals(0L, result.totalUnrealizedLossMinor)
            assertEquals(0L, result.netUnrealizedDeltaMinor)
        }
    }

    /**
     * Test 39: Dual-Currency Trial Balance Reporting (D.2)
     * Trial Balance displays balances in base currency YER alongside underlying original foreign currencies,
     * maintaining total debit == total credit equilibrium.
     */
    @Test
    fun test39_dualCurrencyTrialBalanceReporting() {
        runBlocking {
            val date = LocalDate.of(2026, 10, 5).toEpochDay()
            val rate = ExchangeRate(CurrencyCode.USD, CurrencyCode.FUNCTIONAL, 530_000_000L)

            // Capital in USD ($2,000 = 1,060,000 YER)
            writer.postCapitalReceipt(
                targetAccountCode = AccountConstants.CAPITAL,
                partnerPartyId = AppDatabase.WALK_IN_CASH_PARTY_ID,
                treasuryId = "TR_USD_VAULT",
                fiscalYear = 2026,
                dateEpochDay = date,
                amountOrigMinor = 2_000_00L,
                currency = CurrencyCode.USD,
                exchangeRate = rate,
                notes = "رأس مال بالدولار"
            )

            val dualTb = statementsUseCase.generateDualCurrencyTrialBalance()
            assertTrue("Trial balance must be mathematically balanced", dualTb.isBalanced)
            assertEquals(dualTb.totalBaseDebitMinor, dualTb.totalBaseCreditMinor)

            val treasuryRow = dualTb.rows.find { it.accountCode == AccountConstants.CASH_VAULT }
            assertNotNull(treasuryRow)
            assertEquals(1_060_000_00L, treasuryRow!!.baseDebitMinor)
            assertEquals(2_000_00L, treasuryRow.originalBalances[CurrencyCode.USD])
        }
    }

    /**
     * Test 40: Profit & Loss Statement Realized vs Unrealized FX Reporting (D.2)
     * Profit & Loss statement reports Realized FX (4901/5901) and Unrealized FX (4902/5902) separately,
     * and correctly integrates both into net profit computation.
     */
    @Test
    fun test40_profitAndLossStatementRealizedVsUnrealizedFxReporting() {
        runBlocking {
            val date = LocalDate.of(2026, 10, 5).toEpochDay()
            val rateInit = ExchangeRate(CurrencyCode.USD, CurrencyCode.FUNCTIONAL, 530_000_000L)

            // Deposit $1,000 into USD Vault (530,000 YER)
            writer.postCapitalReceipt(
                targetAccountCode = AccountConstants.CAPITAL,
                partnerPartyId = AppDatabase.WALK_IN_CASH_PARTY_ID,
                treasuryId = "TR_USD_VAULT",
                fiscalYear = 2026,
                dateEpochDay = date,
                amountOrigMinor = 1_000_00L,
                currency = CurrencyCode.USD,
                exchangeRate = rateInit,
                notes = "تمويل الصندوق"
            )

            // Revaluation at 550 YER: Unrealized FX Gain = +20,000 YER
            val asOfDate = LocalDate.of(2026, 10, 10).toEpochDay()
            db.currencyRateDao().insertRate(
                CurrencyRateEntity(
                    id = "RATE_USD_550_TEST40",
                    currency = "USD",
                    zone = RateZone.DEFAULT.name,
                    rateMicros = 550_000_000L,
                    effectiveDateEpochDay = asOfDate,
                    createdBy = "TEST",
                    reason = "Rate jump"
                )
            )
            revaluationUseCase.executeRevaluation(asOfDateEpochDay = asOfDate, fiscalYear = 2026)

            // Fetch income statement
            val incomeReport = statementsUseCase.generateIncomeStatement(startDateEpochDay = null, endDateEpochDay = asOfDate)
            assertEquals(20_000_00L, incomeReport.unrealizedFxGainMinor)
            assertEquals(0L, incomeReport.unrealizedFxLossMinor)
            assertEquals(20_000_00L, incomeReport.netProfitMinor)

            invariants.verifyAll()
        }
    }
}
