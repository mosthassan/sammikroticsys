package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.ledger.AccountConstants
import com.example.core.ledger.DocumentStatus
import com.example.core.ledger.DocumentType
import com.example.core.model.CurrencyCode
import com.example.core.model.EquityShareMode
import com.example.core.model.ExchangeRate
import com.example.core.model.Money
import com.example.core.model.RateSource
import com.example.core.model.RateZone
import com.example.data.ledger.LedgerInvariants
import com.example.data.ledger.LedgerWriter
import com.example.data.local.AppDatabase
import com.example.data.local.entity.CurrencyRateEntity
import com.example.data.local.entity.FiscalPeriodEntity
import com.example.data.local.entity.PartyEntity
import com.example.domain.usecase.ExchangeRateResolver
import com.example.domain.usecase.PartnerEquityUseCase
import com.example.domain.usecase.PartnerWeight
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
 * Test suite for Multi-Currency Part C: Multi-Currency Partner Capital & Equity Ratios.
 * Covers Tests 25 through 30 from Section 4:
 *
 * 25. Historical Cost Principle (C.1): Multi-currency capital contribution recorded at historical rate
 *     Dr Destination Treasury, Cr Partner Capital 3101 for the YER base value on contribution date.
 * 26. IAS 21 Non-Revaluation of Equity (C.1): Foreign currency equity is NEVER revalued under IAS 21;
 *     historical capital remains frozen at historical cost even when subsequent exchange rates change.
 * 27. Dynamic Partner Share Calculation in DERIVED_FROM_CAPITAL Mode (C.2):
 *     Compute partner share % dynamically from the ledger:
 *     (Partner Total Historical Capital in YER / All Partners Total Historical Capital).
 * 28. Fixed Agreed Partner Share Validation in FIXED_AGREED Mode (C.2):
 *     Validate sum of percentages == 100.00% (10,000 basis points). Rejects invalid sums.
 * 29. Profit Distribution Invariant via Largest Remainder Method (Hare-Niemeyer Method) (C.2):
 *     Compute profit shares using integer math guaranteeing zero rounding residue down to 1 Rial.
 *     Profits distribute in YER to Partner Current Accounts 3201 (DR 3301, CR 3201).
 * 30. Multi-Currency Capital Contribution Voiding & Reversal (C.1 / C.3):
 *     Voiding a multi-currency capital contribution restores treasury balances, reverses 3101 capital,
 *     and satisfies all double-entry ledger invariants.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PartnerCapitalPartCTest {

    private lateinit var db: AppDatabase
    private lateinit var writer: LedgerWriter
    private lateinit var invariants: LedgerInvariants
    private lateinit var resolver: ExchangeRateResolver
    private lateinit var equityUseCase: PartnerEquityUseCase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.createInMemory(context)
        AppDatabase.installTriggers(db.openHelper.writableDatabase)
        AppDatabase.seedDefaultData(db.openHelper.writableDatabase)

        writer = LedgerWriter(db, enableInvariantValidation = true)
        invariants = LedgerInvariants(db)
        resolver = ExchangeRateResolver(db)
        equityUseCase = PartnerEquityUseCase(db, writer)

        runBlocking {
            db.fiscalPeriodDao().insertPeriod(
                FiscalPeriodEntity(
                    id = "FP_2026_10",
                    year = 2026,
                    month = 10,
                    isClosed = false
                )
            )
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    /**
     * Test 25: Multi-Currency Capital Contribution Recorded at Historical Rate (C.1)
     * - Partner contributes 10,000 USD to USD Treasury (TR_USD_VAULT) at historical rate of 530.00 YER/USD.
     * - Total Base YER value = 5,300,000 YER.
     * - Journal Entry: DR TR_USD_VAULT (10,000 USD / 5,300,000 YER base), CR 3101 (5,300,000 YER base).
     * - DocumentEntity correctly stores currency="USD", rateMicros=530_000_000, rateZone="SANAA".
     */
    @Test
    fun test25_multiCurrencyCapitalContributionAtHistoricalRate() {
        runBlocking {
            val partnerId = "PARTNER_USD_01"
            db.partyDao().insertParty(
                PartyEntity(
                    id = partnerId,
                    name = "الشريك رياض (مساهمة بالدولار)",
                    isPartner = true
                )
            )

            val usdAmountMinor = 10_000_00L // 10,000.00 USD
            val rateMicros = 530_000_000L // 1 USD = 530 YER
            val expectedBaseYerMinor = 5_300_000_00L // 5,300,000 YER
            val dateEpoch = LocalDate.of(2026, 10, 5).toEpochDay()
            val usdRate = ExchangeRate(CurrencyCode.USD, CurrencyCode.FUNCTIONAL, rateMicros)

            val doc = writer.postCapitalReceipt(
                targetAccountCode = AccountConstants.CAPITAL,
                partnerPartyId = partnerId,
                treasuryId = "TR_USD_VAULT",
                fiscalYear = 2026,
                dateEpochDay = dateEpoch,
                amountOrigMinor = usdAmountMinor,
                currency = CurrencyCode.USD,
                exchangeRate = usdRate,
                rateZone = RateZone.SANAA,
                rateSource = RateSource.SYSTEM_DAILY,
                notes = "مساهمة رأسمالية بالدولار الأمريكي بسعر 530"
            )

            assertNotNull(doc)
            assertEquals(DocumentStatus.POSTED.name, doc.status)
            assertEquals("USD", doc.currency)
            assertEquals(rateMicros, doc.exchangeRateMicros)
            assertEquals("SANAA", doc.rateZone)
            assertEquals(usdAmountMinor, doc.totalMinor)
            assertEquals(expectedBaseYerMinor, doc.totalBaseMinor)

            // Verify partner dynamic capital balance in base YER is exactly 5,300,000 YER
            val dynamicCapital = db.journalDao().getPartnerCapitalBalanceSync(partnerId)
            assertEquals(expectedBaseYerMinor, dynamicCapital)

            // Verify journal lines
            val entries = db.journalDao().getEntriesForDocument(doc.id)
            assertEquals(1, entries.size)
            val lines = db.journalDao().getLinesForEntry(entries[0].id)
            assertEquals(2, lines.size)

            val debitLine = lines.first { it.baseDebitMinor > 0 }
            val creditLine = lines.first { it.baseCreditMinor > 0 }

            assertEquals(AccountConstants.CASH_VAULT, debitLine.accountCode)
            assertEquals("TR_USD_VAULT", debitLine.treasuryId)
            assertEquals(usdAmountMinor, debitLine.origMinor)
            assertEquals(expectedBaseYerMinor, debitLine.baseDebitMinor)

            assertEquals(AccountConstants.CAPITAL, creditLine.accountCode)
            assertEquals(partnerId, creditLine.partyId)
            assertEquals(usdAmountMinor, creditLine.origMinor)
            assertEquals(expectedBaseYerMinor, creditLine.baseCreditMinor)

            // Verify double-entry ledger invariants
            invariants.verifyAll()
        }
    }

    /**
     * Test 26: IAS 21 Non-Revaluation of Equity (C.1)
     * - Under IAS 21, foreign currency equity is NEVER revalued; historical capital remains frozen at historical cost.
     * - After partner contributes USD capital, we introduce a new fluctuating exchange rate (USD jumps to 600 or 1,600 YER).
     * - Partner capital balance (account 3101) must remain strictly frozen at its historical YER cost (5,300,000 YER),
     *   without generating any spurious unrealized FX gain or loss on equity.
     */
    @Test
    fun test26_ias21NonRevaluationOfForeignCurrencyEquity() {
        runBlocking {
            val partnerId = "PARTNER_IAS21_FROZEN"
            db.partyDao().insertParty(
                PartyEntity(
                    id = partnerId,
                    name = "الشريك جمال (حساب رأس مال مجمد)",
                    isPartner = true
                )
            )

            val usdAmountMinor = 10_000_00L // 10,000.00 USD
            val initialRateMicros = 530_000_000L // 530 YER
            val historicalCostBase = 5_300_000_00L
            val dateEpoch = LocalDate.of(2026, 10, 5).toEpochDay()

            writer.postCapitalReceipt(
                targetAccountCode = AccountConstants.CAPITAL,
                partnerPartyId = partnerId,
                treasuryId = "TR_USD_VAULT",
                fiscalYear = 2026,
                dateEpochDay = dateEpoch,
                amountOrigMinor = usdAmountMinor,
                currency = CurrencyCode.USD,
                exchangeRate = ExchangeRate(CurrencyCode.USD, CurrencyCode.FUNCTIONAL, initialRateMicros),
                rateZone = RateZone.SANAA,
                notes = "مساهمة تأسيسية بالتكلفة التاريخية"
            )

            // Verify initial frozen capital balance
            val capitalBefore = db.journalDao().getPartnerCapitalBalanceSync(partnerId)
            assertEquals(historicalCostBase, capitalBefore)

            // Introduce dramatic market rate spike: 1 USD = 1,600 YER on day 6
            val dayAfter = LocalDate.of(2026, 10, 6).toEpochDay()
            db.currencyRateDao().insertRate(
                CurrencyRateEntity(
                    id = "RATE_USD_SPIKE_TEST26",
                    currency = "USD",
                    zone = "SANAA",
                    rateMicros = 1_600_000_000L,
                    effectiveDateEpochDay = dayAfter,
                    createdBy = "TEST",
                    reason = "Market fluctuation spike"
                )
            )

            // Query dynamic capital balance after rate spike:
            // Under IAS 21 non-monetary items measured at historical cost are NOT revalued at closing rates.
            val capitalAfterSpike = db.journalDao().getPartnerCapitalBalanceSync(partnerId)
            assertEquals(
                "IAS 21 Violation: Historical equity must remain frozen at historical cost despite exchange rate fluctuations",
                historicalCostBase,
                capitalAfterSpike
            )

            // Verify no FX gain/loss entries were posted to account 3101
            val lines3101 = db.journalDao().getAllLinesSync().filter { it.accountCode == AccountConstants.CAPITAL && it.partyId == partnerId }
            assertEquals(1, lines3101.size)
            assertEquals(historicalCostBase, lines3101[0].baseCreditMinor)

            invariants.verifyAll()
        }
    }

    /**
     * Test 27: Dynamic Partner Share in DERIVED_FROM_CAPITAL Mode (C.2)
     * - Organization setting equityShareMode = 'DERIVED_FROM_CAPITAL'.
     * - Partner A contributes 6,000,000 YER (60%).
     * - Partner B contributes 3,000,000 YER (30%).
     * - Partner C contributes 1,000,000 YER (10%).
     * - Total Capital = 10,000,000 YER.
     * - PartnerEquityUseCase dynamically calculates exact basis points from ledger account 3101:
     *   Partner A = 6,000 bps (60.00%), Partner B = 3,000 bps (30.00%), Partner C = 1,000 bps (10.00%).
     */
    @Test
    fun test27_dynamicPartnerShareDerivedFromCapitalMode() {
        runBlocking {
            // Set organization mode to DERIVED_FROM_CAPITAL
            equityUseCase.setEquityShareMode(EquityShareMode.DERIVED_FROM_CAPITAL)
            assertEquals(EquityShareMode.DERIVED_FROM_CAPITAL, equityUseCase.getEquityShareMode())

            val partnerA = "PARTNER_A_60"
            val partnerB = "PARTNER_B_30"
            val partnerC = "PARTNER_C_10"

            db.partyDao().insertParty(PartyEntity(id = partnerA, name = "شريك أ", isPartner = true))
            db.partyDao().insertParty(PartyEntity(id = partnerB, name = "شريك ب", isPartner = true))
            db.partyDao().insertParty(PartyEntity(id = partnerC, name = "شريك ج", isPartner = true))

            val dateEpoch = LocalDate.of(2026, 10, 5).toEpochDay()
            val parityRate = ExchangeRate.parity(CurrencyCode.YER)

            // Partner A: 6,000,000 YER
            writer.postCapitalReceipt(
                targetAccountCode = AccountConstants.CAPITAL,
                partnerPartyId = partnerA,
                treasuryId = "TR_MAIN_YER",
                fiscalYear = 2026,
                dateEpochDay = dateEpoch,
                amountOrigMinor = 6_000_000_00L,
                currency = CurrencyCode.YER,
                exchangeRate = parityRate
            )

            // Partner B: 3,000,000 YER
            writer.postCapitalReceipt(
                targetAccountCode = AccountConstants.CAPITAL,
                partnerPartyId = partnerB,
                treasuryId = "TR_MAIN_YER",
                fiscalYear = 2026,
                dateEpochDay = dateEpoch,
                amountOrigMinor = 3_000_000_00L,
                currency = CurrencyCode.YER,
                exchangeRate = parityRate
            )

            // Partner C: 1,000,000 YER
            writer.postCapitalReceipt(
                targetAccountCode = AccountConstants.CAPITAL,
                partnerPartyId = partnerC,
                treasuryId = "TR_MAIN_YER",
                fiscalYear = 2026,
                dateEpochDay = dateEpoch,
                amountOrigMinor = 1_000_000_00L,
                currency = CurrencyCode.YER,
                exchangeRate = parityRate
            )

            val summary = equityUseCase.getEquitySummary()
            assertEquals(EquityShareMode.DERIVED_FROM_CAPITAL, summary.mode)
            assertEquals(10_000_000_00L, summary.totalHistoricalCapitalMinor)
            assertEquals(3, summary.partners.size)

            val shareA = summary.partners.first { it.partnerId == partnerA }
            val shareB = summary.partners.first { it.partnerId == partnerB }
            val shareC = summary.partners.first { it.partnerId == partnerC }

            assertEquals(6_000_000_00L, shareA.historicalCapitalMinor)
            assertEquals(6000, shareA.equityBasisPoints) // 60.00%
            assertEquals("60.00%", shareA.sharePercentText)

            assertEquals(3_000_000_00L, shareB.historicalCapitalMinor)
            assertEquals(3000, shareB.equityBasisPoints) // 30.00%
            assertEquals("30.00%", shareB.sharePercentText)

            assertEquals(1_000_000_00L, shareC.historicalCapitalMinor)
            assertEquals(1000, shareC.equityBasisPoints) // 10.00%
            assertEquals("10.00%", shareC.sharePercentText)

            // Sum of basis points must equal 10,000 (100.00%)
            assertEquals(10000, shareA.equityBasisPoints + shareB.equityBasisPoints + shareC.equityBasisPoints)

            invariants.verifyAll()
        }
    }

    /**
     * Test 28: Fixed Agreed Partner Share Validation in FIXED_AGREED Mode (C.2)
     * - When equityShareMode is FIXED_AGREED:
     *   - Validation succeeds when sum of basis points == 10,000 (100.00%).
     *   - Validation throws IllegalStateException when sum != 10,000 (e.g. 9,000 or 11,000).
     */
    @Test
    fun test28_fixedAgreedPartnerShareValidation() {
        runBlocking {
            equityUseCase.setEquityShareMode(EquityShareMode.FIXED_AGREED)
            assertEquals(EquityShareMode.FIXED_AGREED, equityUseCase.getEquityShareMode())

            // 1. Valid configuration: 60.00% + 40.00% = 100.00% (10,000 bps)
            val validPartners = listOf(
                PartyEntity(id = "P1", name = "شريك 1", isPartner = true, equityPercentageBasisPoints = 6000),
                PartyEntity(id = "P2", name = "شريك 2", isPartner = true, equityPercentageBasisPoints = 4000)
            )
            // Must not throw
            equityUseCase.validateFixedAgreedShares(validPartners)

            // 2. Under-allocated configuration: 50.00% + 40.00% = 90.00% (9,000 bps)
            val underAllocatedPartners = listOf(
                PartyEntity(id = "P1", name = "شريك 1", isPartner = true, equityPercentageBasisPoints = 5000),
                PartyEntity(id = "P2", name = "شريك 2", isPartner = true, equityPercentageBasisPoints = 4000)
            )
            try {
                equityUseCase.validateFixedAgreedShares(underAllocatedPartners)
                fail("Expected IllegalStateException for under-allocated equity percentages (9,000 bps)")
            } catch (e: IllegalStateException) {
                assertTrue(e.message?.contains("10,000") == true)
            }

            // 3. Over-allocated configuration: 70.00% + 40.00% = 110.00% (11,000 bps)
            val overAllocatedPartners = listOf(
                PartyEntity(id = "P1", name = "شريك 1", isPartner = true, equityPercentageBasisPoints = 7000),
                PartyEntity(id = "P2", name = "شريك 2", isPartner = true, equityPercentageBasisPoints = 4000)
            )
            try {
                equityUseCase.validateFixedAgreedShares(overAllocatedPartners)
                fail("Expected IllegalStateException for over-allocated equity percentages (11,000 bps)")
            } catch (e: IllegalStateException) {
                assertTrue(e.message?.contains("10,000") == true)
            }
        }
    }

    /**
     * Test 29: Profit Distribution Invariant via Largest Remainder Method (Hare-Niemeyer Method) (C.2)
     * - Distribute an uneven profit amount: 100,000 YER and 1,000,001 minor units across 3 partners with equal 1/3 weight.
     * - Standard division would create 333,333.333... causing 1 Rial rounding residue.
     * - Largest Remainder Method (Hare-Niemeyer) allocates integer parts, ranks remainders,
     *   and assigns the remaining rials to guarantee zero residue down to 1 Rial.
     * - Invariant: Sum of partner shares EXACTLY equals totalProfitMinor down to the single Rial.
     * - Ledger Entry: DR 3301 Retained Earnings, CR 3201 Partner Current Accounts in YER.
     */
    @Test
    fun test29_profitDistributionLargestRemainderMethodZeroResidue() {
        runBlocking {
            // Setup 3 partners with equal 1/3 shares
            val p1 = "PARTNER_HN_01"
            val p2 = "PARTNER_HN_02"
            val p3 = "PARTNER_HN_03"

            db.partyDao().insertParty(PartyEntity(id = p1, name = "شريك 1", isPartner = true, equityPercentageBasisPoints = 3334))
            db.partyDao().insertParty(PartyEntity(id = p2, name = "شريك 2", isPartner = true, equityPercentageBasisPoints = 3333))
            db.partyDao().insertParty(PartyEntity(id = p3, name = "شريك 3", isPartner = true, equityPercentageBasisPoints = 3333))

            equityUseCase.setEquityShareMode(EquityShareMode.FIXED_AGREED)

            // Uneven profit amount that cannot be divided evenly: 1,000,001 minor units (10,000.01 YER)
            val unevenProfitMinor = 1_000_001L

            val weights = listOf(
                PartnerWeight(p1, "شريك 1", 3334L),
                PartnerWeight(p2, "شريك 2", 3333L),
                PartnerWeight(p3, "شريك 3", 3333L)
            )

            val shares = equityUseCase.calculateHareNiemeyerDistribution(unevenProfitMinor, weights)
            assertEquals(3, shares.size)

            // Invariant: sum of distributed shares must EXACTLY match unevenProfitMinor (zero residue!)
            val totalDistributed = shares.sumOf { it.amountMinor }
            assertEquals(
                "Hare-Niemeyer Invariant Violation: Sum of shares must equal total profit with zero residue",
                unevenProfitMinor,
                totalDistributed
            )

            // Post into the ledger via executeProfitDistribution
            val dateEpoch = LocalDate.of(2026, 10, 5).toEpochDay()
            val doc = equityUseCase.executeProfitDistribution(
                fiscalYear = 2026,
                dateEpochDay = dateEpoch,
                totalProfitMinor = unevenProfitMinor,
                notes = "توزيع أرباح ربع سنوي بدون فواقد"
            )

            assertNotNull(doc)
            assertEquals(DocumentType.DIVIDEND_DISTRIBUTION.name, doc.type)
            assertEquals(DocumentStatus.POSTED.name, doc.status)

            // Verify Journal Lines:
            // Line 1: DR 3301 Retained Earnings (1,000,001 YER)
            // Lines 2-4: CR 3201 Partner Current Accounts (exact shares summing to 1,000,001 YER)
            val entries = db.journalDao().getEntriesForDocument(doc.id)
            assertEquals(1, entries.size)
            val lines = db.journalDao().getLinesForEntry(entries[0].id)
            assertEquals(4, lines.size)

            val debitLine = lines.first { it.baseDebitMinor > 0 }
            assertEquals(AccountConstants.RETAINED_EARNINGS, debitLine.accountCode)
            assertEquals(unevenProfitMinor, debitLine.baseDebitMinor)

            val creditLines = lines.filter { it.baseCreditMinor > 0 }
            assertEquals(3, creditLines.size)
            assertTrue(creditLines.all { it.accountCode == AccountConstants.PARTNER_CURRENT })
            assertEquals(unevenProfitMinor, creditLines.sumOf { it.baseCreditMinor })

            // Verify partner current balances increased by exact distributed shares
            val bal1 = db.journalDao().getPartnerCurrentBalanceSync(p1)
            val bal2 = db.journalDao().getPartnerCurrentBalanceSync(p2)
            val bal3 = db.journalDao().getPartnerCurrentBalanceSync(p3)
            assertEquals(unevenProfitMinor, bal1 + bal2 + bal3)

            invariants.verifyAll()
        }
    }

    /**
     * Test 30: Multi-Currency Capital Contribution Voiding & Reversal (C.1 / C.3)
     * - Multi-currency capital contribution (e.g. 5,000 USD @ 530 YER/USD = 2,650,000 YER).
     * - Voiding the voucher creates a compensatory reversal entry that resets partner capital (3101) to 0.
     * - Restores foreign treasury balance and maintains full ledger invariants.
     */
    @Test
    fun test30_multiCurrencyCapitalContributionVoidingAndReversal() {
        runBlocking {
            val partnerId = "PARTNER_VOID_TEST30"
            db.partyDao().insertParty(
                PartyEntity(
                    id = partnerId,
                    name = "الشريك طارق (اختبار الإلغاء)",
                    isPartner = true
                )
            )

            val usdAmountMinor = 5_000_00L // 5,000 USD
            val rateMicros = 530_000_000L
            val baseCostMinor = 2_650_000_00L
            val dateEpoch = LocalDate.of(2026, 10, 5).toEpochDay()
            val usdRate = ExchangeRate(CurrencyCode.USD, CurrencyCode.FUNCTIONAL, rateMicros)

            val doc = writer.postCapitalReceipt(
                targetAccountCode = AccountConstants.CAPITAL,
                partnerPartyId = partnerId,
                treasuryId = "TR_USD_VAULT",
                fiscalYear = 2026,
                dateEpochDay = dateEpoch,
                amountOrigMinor = usdAmountMinor,
                currency = CurrencyCode.USD,
                exchangeRate = usdRate,
                rateZone = RateZone.SANAA,
                notes = "مساهمة سيتم إلغاؤها"
            )

            // Capital before void: 2,650,000 YER
            val capBefore = db.journalDao().getPartnerCapitalBalanceSync(partnerId)
            assertEquals(baseCostMinor, capBefore)

            // Void the document
            val voidSuccess = writer.voidDocument(
                docId = doc.id,
                reversalDateEpochDay = dateEpoch,
                reason = "إلغاء السند بطلب الشريك"
            )
            assertTrue(voidSuccess)

            // Document status is VOIDED
            val updatedDoc = db.documentDao().getDocumentById(doc.id)
            assertEquals(DocumentStatus.VOIDED.name, updatedDoc?.status)

            // Capital after void must drop back to exactly 0
            val capAfter = db.journalDao().getPartnerCapitalBalanceSync(partnerId)
            assertEquals(0L, capAfter)

            // Reversal entry created
            val entries = db.journalDao().getEntriesForDocument(doc.id)
            assertEquals(2, entries.size)
            val reversalEntry = entries.first { it.type == "REVERSAL" }
            assertNotNull(reversalEntry)

            // Treasury balance is reverted to 0
            val treasuryBalance = db.journalDao().getNetDebitBalanceForTreasury("TR_USD_VAULT")
            assertEquals(0L, treasuryBalance)

            // Full invariant audit passes
            invariants.verifyAll()
        }
    }
}
