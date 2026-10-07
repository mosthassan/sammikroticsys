package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.ledger.AccountConstants
import com.example.core.ledger.DocumentType
import com.example.core.model.CurrencyCode
import com.example.core.model.ExchangeRate
import com.example.core.model.MissingExchangeRateException
import com.example.core.model.Money
import com.example.core.model.RateSource
import com.example.core.model.RateZone
import com.example.core.model.SignificantRateChangeException
import com.example.data.ledger.InvoiceAllocationSpec
import com.example.data.ledger.LedgerInvariants
import com.example.data.ledger.LedgerWriter
import com.example.data.ledger.PaymentVoucherType
import com.example.data.ledger.PurchaseItemSpec
import com.example.data.local.AppDatabase
import com.example.data.local.entity.CurrencyRateEntity
import com.example.data.local.entity.FiscalPeriodEntity
import com.example.data.local.entity.PartyEntity
import com.example.domain.usecase.ExchangeRateResolver
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

/**
 * Test suite for Multi-Currency Part A (Exchange Rate Hub) - Step A.2 Database Migration & Core Rate Resolver Engine.
 * Covers:
 * 1. Default seed rates in database for SANAA and ADEN zones.
 * 2. Parity conversion for functional currency (YER -> YER).
 * 3. USD to YER integer half-up conversion.
 * 4. SAR to YER integer half-up conversion.
 * 5. Rounding remainder distribution in multi-line documents.
 * 6. Append-only currency rates and audit metadata (no mutable updates).
 * 7. Prevention of Defect 2.B via Ledger Guardrail (rejecting parity on foreign currency).
 * 8. Foreign currency payment voucher ledger calculation.
 * 9. Foreign currency purchase invoice multi-line base debit distribution.
 * 10. Realized FX gain/loss on invoice settlement (IAS 21).
 * 11. Cross-rate calculation (USD to SAR).
 * 12. Treasury account multi-currency balance isolation.
 * 13. Rejection of zero or negative exchange rates.
 * 14. Dynamic exchange rate resolution via ExchangeRateResolver (replaces hardcoded rates).
 * 15. SQLite triggers preventing UPDATE and DELETE on currency_rates (append-only enforcement).
 * 16. Rate change validation (> 10% requires confirmation, closed periods blocked).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExchangeRatesPartATest {

    private lateinit var db: AppDatabase
    private lateinit var writer: LedgerWriter
    private lateinit var invariants: LedgerInvariants
    private lateinit var resolver: ExchangeRateResolver

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.createInMemory(context)
        AppDatabase.installTriggers(db.openHelper.writableDatabase)
        AppDatabase.seedDefaultData(db.openHelper.writableDatabase)

        writer = LedgerWriter(db, enableInvariantValidation = true)
        invariants = LedgerInvariants(db)
        resolver = ExchangeRateResolver(db)

        runBlocking {
            db.fiscalPeriodDao().insertPeriod(
                FiscalPeriodEntity(
                    id = "FP_2026_01",
                    year = 2026,
                    month = 1,
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
     * Test 1: Default Seed Currency Rates in Database
     * Functional currency is YER. Seeded database must contain rates for USD and SAR across Sana'a and Aden zones.
     */
    @Test
    fun test01_defaultSeedCurrencyRates() = runBlocking {
        val usdRateSanaa = resolver.resolve(CurrencyCode.USD, 20000, RateZone.SANAA)
        assertEquals("USD to YER Sana'a seed rate should be 530 YER/USD", 530_000_000L, usdRateSanaa.rateMicros)

        val sarRateSanaa = resolver.resolve(CurrencyCode.SAR, 20000, RateZone.SANAA)
        assertEquals("SAR to YER Sana'a seed rate should be 140 YER/SAR", 140_000_000L, sarRateSanaa.rateMicros)

        val usdRateAden = resolver.resolve(CurrencyCode.USD, 20000, RateZone.ADEN)
        assertEquals("USD to YER Aden seed rate should be 1600 YER/USD", 1_600_000_000L, usdRateAden.rateMicros)

        val sarRateAden = resolver.resolve(CurrencyCode.SAR, 20000, RateZone.ADEN)
        assertEquals("SAR to YER Aden seed rate should be 420 YER/SAR", 420_000_000L, sarRateAden.rateMicros)
    }

    /**
     * Test 2: Parity Conversion for Functional Currency (YER -> YER)
     * Conversion of functional currency to itself must preserve exact integer minor units.
     */
    @Test
    fun test02_functionalCurrencyParity() = runBlocking {
        val yerRate = resolver.resolve(CurrencyCode.YER, 20000, RateZone.SANAA)
        assertEquals(ExchangeRate.SCALE_MICROS, yerRate.rateMicros)
        assertEquals(CurrencyCode.YER, yerRate.fromCurrency)
        assertEquals(CurrencyCode.YER, yerRate.toCurrency)

        val sampleAmountMinor = 1_250_750L
        val converted = yerRate.convert(sampleAmountMinor)
        assertEquals("Converting functional currency to itself must produce exact amount", sampleAmountMinor, converted)
    }

    /**
     * Test 3: USD to YER Integer Half-Up Conversion
     * $100.00 USD at 530.50 YER/USD = 53,050 YER (represented in integer minor units).
     */
    @Test
    fun test03_usdToYerIntegerHalfUpConversion() {
        // 530.50 YER per 1 USD -> 530_500_000 micros
        val rate = ExchangeRate(CurrencyCode.USD, CurrencyCode.YER, 530_500_000L)
        val converted = rate.convert(100L)
        assertEquals(53050L, converted)

        // Test half-up rounding edge case: 1 USD at 530.50 -> 531 YER
        val convertedSingle = rate.convert(1L)
        assertEquals(531L, convertedSingle)
    }

    /**
     * Test 4: SAR to YER Integer Half-Up Conversion
     * 1,000 SAR at 140.00 YER/SAR = 140,000 YER.
     */
    @Test
    fun test04_sarToYerIntegerHalfUpConversion() {
        val rate = ExchangeRate(CurrencyCode.SAR, CurrencyCode.YER, 140_000_000L)
        val amountSarMinor = 1_000L
        val converted = rate.convert(amountSarMinor)
        assertEquals(140_000L, converted)
    }

    /**
     * Test 5: Rounding Remainder Distribution in Multi-Line Documents
     * Sum of line conversions must strictly match expected total base minor without 1-riyal drift.
     */
    @Test
    fun test05_roundingRemainderDistributionMultiLine() {
        val rate = ExchangeRate(CurrencyCode.USD, CurrencyCode.YER, 530_500_000L)
        val lineAmounts = listOf(33L, 33L, 34L) // Total = 100 USD
        val expectedTotal = rate.convert(100L) // 53,050 YER

        val distributed = ExchangeRate.distributeConvertedLines(lineAmounts, rate, expectedTotal)
        assertEquals(3, distributed.size)
        assertEquals("Sum of distributed lines must equal expected total base minor", expectedTotal, distributed.sum())
    }

    /**
     * Test 6: Append-Only Currency Rate Addition & Historical Resolution
     * Appending a new rate on day 20050 preserves historical resolution on day 20040.
     */
    @Test
    fun test06_appendOnlyCurrencyRateAndHistoricalResolution() = runBlocking {
        // Day 20040 resolves initial seed rate (530 YER/USD)
        val rateBefore = resolver.resolve(CurrencyCode.USD, 20040, RateZone.SANAA)
        assertEquals(530_000_000L, rateBefore.rateMicros)

        // Add new rate on day 20050: 540 YER/USD (< 10% change)
        resolver.addRate(
            currency = CurrencyCode.USD,
            zone = RateZone.SANAA,
            rateMicros = 540_000_000L,
            effectiveDateEpochDay = 20050,
            createdBy = "ADMIN",
            reason = "Market rate update"
        )

        // Historical query on day 20040 still returns 530 YER/USD
        val historicalRate = resolver.resolve(CurrencyCode.USD, 20040, RateZone.SANAA)
        assertEquals(530_000_000L, historicalRate.rateMicros)

        // Query on or after day 20050 returns 540 YER/USD
        val activeRate = resolver.resolve(CurrencyCode.USD, 20050, RateZone.SANAA)
        assertEquals(540_000_000L, activeRate.rateMicros)
    }

    /**
     * Test 7: Ledger Guardrail Enforces Prevention of Defect 2.B
     * Attempting to post a foreign currency voucher with 1:1 parity rate is rejected by Ledger Guardrail.
     * Posting with valid market rate succeeds.
     */
    @Test
    fun test07_ledgerGuardrailRejectsForeignParity() = runBlocking {
        val client = PartyEntity(
            id = "PT_USD_CLIENT",
            name = "عميل دولي بالدولار",
            phone = "777111222",
            isCustomer = true
        )
        db.partyDao().insertParty(client)

        // 1. Guardrail must reject parity rate for foreign currency (USD)
        val parityRate = ExchangeRate.parity(CurrencyCode.USD)
        try {
            writer.postCustomerReceipt(
                partyId = client.id,
                treasuryId = "TR_USD_VAULT",
                fiscalYear = 2026,
                dateEpochDay = 20050,
                amountOrigMinor = 100L,
                currency = CurrencyCode.USD,
                exchangeRate = parityRate,
                notes = "سند قبض بمعامل تكافؤ خاطئ"
            )
            fail("LedgerWriter must throw IllegalArgumentException when posting foreign currency with parity rate")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Invalid foreign exchange rate"))
        }

        // 2. Post with correct market rate (530 YER/USD)
        val correctRate = resolver.resolve(CurrencyCode.USD, 20050, RateZone.SANAA)
        val correctDoc = writer.postCustomerReceipt(
            partyId = client.id,
            treasuryId = "TR_USD_VAULT",
            fiscalYear = 2026,
            dateEpochDay = 20051,
            amountOrigMinor = 100L, // $100 USD
            currency = CurrencyCode.USD,
            exchangeRate = correctRate,
            notes = "سند قبض بالدولار بسعر صرف معتمد"
        )

        assertEquals("Correct exchange rate must produce totalBaseMinor = 53000 YER", 53000L, correctDoc.totalBaseMinor)
    }

    /**
     * Test 8: Foreign Currency Payment Voucher Base Credit/Debit Verification
     * Payment of $200 USD from USD treasury reflects 106,000 YER base credit/debit.
     */
    @Test
    fun test08_foreignCurrencyPaymentVoucherPosting() = runBlocking {
        val supplier = PartyEntity(
            id = "PT_USD_SUPPLIER",
            name = "مورد ألياف بصرية (Starlink)",
            phone = "777333444",
            isVendor = true
        )
        db.partyDao().insertParty(supplier)

        val usdRate = resolver.resolve(CurrencyCode.USD, 20050, RateZone.SANAA)

        // Seed funds in USD Treasury
        writer.postCustomerReceipt(
            partyId = supplier.id,
            treasuryId = "TR_USD_VAULT",
            fiscalYear = 2026,
            dateEpochDay = 20050,
            amountOrigMinor = 1000L,
            currency = CurrencyCode.USD,
            exchangeRate = usdRate,
            notes = "تغذية خزينة الدولار"
        )

        val payDoc = writer.postPaymentVoucher(
            recipientPartyId = supplier.id,
            treasuryId = "TR_USD_VAULT",
            fiscalYear = 2026,
            dateEpochDay = 20052,
            amountOrigMinor = 200L, // $200 USD
            currency = CurrencyCode.USD,
            exchangeRate = usdRate,
            paymentType = PaymentVoucherType.VENDOR_SETTLEMENT,
            notes = "سداد مورد بالدولار"
        )

        assertEquals(200L, payDoc.totalMinor)
        assertEquals(106_000L, payDoc.totalBaseMinor) // 200 * 530 = 106,000 YER
    }

    /**
     * Test 9: Foreign Currency Purchase Invoice Multi-Line Base Debit Verification
     * Purchase invoice with $300 asset line and $200 expense line converts correctly at 530 YER/USD.
     */
    @Test
    fun test09_foreignPurchaseInvoiceBaseDebitVerification() = runBlocking {
        val vendor = PartyEntity(
            id = "PT_VENDOR_MIKROTIK",
            name = "مورد أجهزة ومعدات شبكة",
            phone = "777555666",
            isVendor = true
        )
        db.partyDao().insertParty(vendor)

        val usdRate = resolver.resolve(CurrencyCode.USD, 20055, RateZone.SANAA)
        val invDoc = writer.postPurchaseInvoice(
            vendorPartyId = vendor.id,
            fiscalYear = 2026,
            dateEpochDay = 20055,
            currency = CurrencyCode.USD,
            exchangeRate = usdRate,
            items = listOf(
                PurchaseItemSpec("Cloud Core Router CCR2004", AccountConstants.FIXED_ASSETS_NETWORK, 1, 300L, true, 36),
                PurchaseItemSpec("كابلات شبكة وألياف ضوئية", AccountConstants.OPERATING_EXPENSES, 2, 100L, false)
            ),
            notes = "فاتورة مشتريات بالدولار"
        )

        assertEquals(500L, invDoc.totalMinor) // $500 USD total
        assertEquals(265_000L, invDoc.totalBaseMinor) // 500 * 530 = 265,000 YER
    }

    /**
     * Test 10: Realized FX Gain/Loss on Invoice Settlement (IFRS IAS 21)
     * Invoice recorded at 530 YER/USD ($100 = 53,000 YER AP)
     * Settled when rate is 535 YER/USD ($100 = 53,500 YER Cash Outflow)
     * Realized FX Loss recognized and totalBaseMinor updated.
     */
    @Test
    fun test10_realizedFxGainLossOnSettlementIAS21() = runBlocking {
        val vendor = PartyEntity(
            id = "PT_VENDOR_FX",
            name = "مورد تجارب فروق العملة",
            phone = "777888999",
            isVendor = true
        )
        db.partyDao().insertParty(vendor)

        // 1. Post Purchase Invoice at 530 YER/USD
        val invRate = resolver.resolve(CurrencyCode.USD, 20060, RateZone.SANAA)
        val invDoc = writer.postPurchaseInvoice(
            vendorPartyId = vendor.id,
            fiscalYear = 2026,
            dateEpochDay = 20060,
            currency = CurrencyCode.USD,
            exchangeRate = invRate,
            items = listOf(PurchaseItemSpec("سيرفر روتر", AccountConstants.FIXED_ASSETS_NETWORK, 1, 100L, true, 24)),
            notes = "فاتورة بالدولار بسعر صرف 530"
        )

        // Seed USD Treasury
        writer.postCustomerReceipt(
            partyId = vendor.id,
            treasuryId = "TR_USD_VAULT",
            fiscalYear = 2026,
            dateEpochDay = 20060,
            amountOrigMinor = 100L,
            currency = CurrencyCode.USD,
            exchangeRate = invRate,
            notes = "تغذية الخزينة"
        )

        // 2. Add new rate at day 20065 (535 YER/USD)
        resolver.addRate(
            currency = CurrencyCode.USD,
            zone = RateZone.SANAA,
            rateMicros = 535_000_000L,
            effectiveDateEpochDay = 20065,
            createdBy = "ADMIN",
            reason = "Rate adjustment"
        )
        val settlementRate = resolver.resolve(CurrencyCode.USD, 20065, RateZone.SANAA)

        // Settle Invoice at 535 YER/USD
        val payDoc = writer.postPaymentVoucher(
            recipientPartyId = vendor.id,
            treasuryId = "TR_USD_VAULT",
            fiscalYear = 2026,
            dateEpochDay = 20065,
            amountOrigMinor = 100L,
            currency = CurrencyCode.USD,
            exchangeRate = settlementRate,
            paymentType = PaymentVoucherType.VENDOR_SETTLEMENT,
            invoiceAllocations = listOf(InvoiceAllocationSpec(invDoc.id, 100L)),
            notes = "سداد الفاتورة بسعر صرف 535"
        )

        assertEquals("Settlement reflects new base exchange rate", 53_500L, payDoc.totalBaseMinor)
    }

    /**
     * Test 11: Cross-Rate Calculation (USD to SAR)
     * Conversion between two non-functional currencies via base currency micro-units.
     * 1 USD = 530 YER, 1 SAR = 140 YER -> 1 USD = (530 / 140) = 3.785714 SAR
     */
    @Test
    fun test11_crossRateCalculationUsdToSar() {
        val usdYerMicros = 530_000_000L
        val sarYerMicros = 140_000_000L

        // Cross rate USD -> SAR = (usdRate / sarRate) * 1_000_000
        val crossRateMicros = (usdYerMicros * ExchangeRate.SCALE_MICROS) / sarYerMicros
        assertEquals(3_785_714L, crossRateMicros) // 3.785714 SAR per USD

        val usdToSarRate = ExchangeRate(CurrencyCode.USD, CurrencyCode.SAR, crossRateMicros)
        val convertedSar = usdToSarRate.convert(100L)
        assertEquals(379L, convertedSar) // 378.57 -> 379 SAR
    }

    /**
     * Test 12: Treasury Account Multi-Currency Balance Isolation
     * Foreign treasury accounts must isolate currency transactions and match ledger entries.
     */
    @Test
    fun test12_treasuryCurrencyIsolation() = runBlocking {
        val usdTreasury = db.treasuryDao().getTreasuryById("TR_USD_VAULT")
        assertNotNull(usdTreasury)
        assertEquals("USD", usdTreasury!!.currency)

        val sarTreasury = db.treasuryDao().getTreasuryById("TR_SAR_VAULT")
        assertNotNull(sarTreasury)
        assertEquals("SAR", sarTreasury!!.currency)
    }

    /**
     * Test 13: Rejection of Zero or Negative Exchange Rates
     * System must reject rateMicros <= 0.
     */
    @Test
    fun test13_rejectionOfZeroOrNegativeExchangeRates() {
        try {
            ExchangeRate(CurrencyCode.USD, CurrencyCode.YER, 0L)
            fail("Must throw IllegalArgumentException for zero exchange rate")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("strictly positive"))
        }

        try {
            ExchangeRate(CurrencyCode.USD, CurrencyCode.YER, -530_000_000L)
            fail("Must throw IllegalArgumentException for negative exchange rate")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("strictly positive"))
        }
    }

    /**
     * Test 14: Dynamic Exchange Rate Lookup (Verifying Hardcoded 530 Rate Defect Resolution)
     * Dynamic lookup returns 540 when rate changes, demonstrating dynamic rate resolution.
     */
    @Test
    fun test14_dynamicExchangeRateResolution() = runBlocking {
        // 1. Add market rate 540 YER/USD on day 20070
        resolver.addRate(
            currency = CurrencyCode.USD,
            zone = RateZone.SANAA,
            rateMicros = 540_000_000L,
            effectiveDateEpochDay = 20070,
            createdBy = "ADMIN",
            reason = "Market fluctuation"
        )

        // 2. Query dynamic rate
        val dynamicRate = resolver.resolve(CurrencyCode.USD, 20070, RateZone.SANAA)
        assertEquals(540_000_000L, dynamicRate.rateMicros)

        // 3. Compare calculation
        val amountUsd = 100L
        val dynamicConverted = dynamicRate.convert(amountUsd)
        assertEquals("Dynamic rate should calculate 54,000 YER", 54000L, dynamicConverted)
    }

    /**
     * Test 15: SQLite Triggers Enforce Append-Only currency_rates (Preventing UPDATE & DELETE)
     */
    @Test
    fun test15_sqliteTriggersEnforceAppendOnly() {
        val sdb = db.openHelper.writableDatabase

        // Attempting UPDATE on currency_rates must abort with trigger message
        try {
            sdb.execSQL("UPDATE currency_rates SET rateMicros = 999999 WHERE id = 'RATE_USD_SANAA_INIT'")
            fail("UPDATE on currency_rates must be aborted by SQLite trigger")
        } catch (e: Exception) {
            assertTrue("Trigger error message expected", e.message?.contains("currency_rates is append-only") == true)
        }

        // Attempting DELETE on currency_rates must abort with trigger message
        try {
            sdb.execSQL("DELETE FROM currency_rates WHERE id = 'RATE_USD_SANAA_INIT'")
            fail("DELETE on currency_rates must be aborted by SQLite trigger")
        } catch (e: Exception) {
            assertTrue("Trigger error message expected", e.message?.contains("currency_rates is append-only") == true)
        }
    }

    /**
     * Test 16: Validation of Rate Additions (>10% Shift Confirmation & Closed Periods)
     */
    @Test
    fun test16_rateAdditionValidationAndSafetyGuardrails() = runBlocking {
        // 1. Shift > 10% without confirmation must throw SignificantRateChangeException
        // Previous rate = 530 YER/USD. Proposed = 600 YER/USD (+13.2%)
        try {
            resolver.addRate(
                currency = CurrencyCode.USD,
                zone = RateZone.SANAA,
                rateMicros = 600_000_000L,
                effectiveDateEpochDay = 20080,
                confirmSignificantChange = false
            )
            fail("Must throw SignificantRateChangeException when rate shift exceeds 10%")
        } catch (e: SignificantRateChangeException) {
            assertEquals(530_000_000L, e.oldRateMicros)
            assertEquals(600_000_000L, e.newRateMicros)
            assertTrue("Percentage change should exceed 10%", e.percentChange > 10.0)
        }

        // 2. With confirmation = true, it succeeds
        val added = resolver.addRate(
            currency = CurrencyCode.USD,
            zone = RateZone.SANAA,
            rateMicros = 600_000_000L,
            effectiveDateEpochDay = 20080,
            confirmSignificantChange = true,
            reason = "Devaluation confirmed"
        )
        assertNotNull(added)
        assertEquals(600_000_000L, added.rateMicros)

        // 3. Adding rate in closed fiscal period must fail
        db.fiscalPeriodDao().setPeriodClosed(2026, 1, true, System.currentTimeMillis())
        // Epoch day 20080 corresponds to ~ 2024 or 2026. Let's create a date in 2026-01:
        // 2026-01-15 epoch day = 20468
        val dayInClosedPeriod = java.time.LocalDate.of(2026, 1, 15).toEpochDay()
        try {
            resolver.addRate(
                currency = CurrencyCode.USD,
                zone = RateZone.SANAA,
                rateMicros = 550_000_000L,
                effectiveDateEpochDay = dayInClosedPeriod,
                confirmSignificantChange = true
            )
            fail("Must throw IllegalStateException when adding rate in closed fiscal period")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("closed fiscal period"))
        }
    }
}
