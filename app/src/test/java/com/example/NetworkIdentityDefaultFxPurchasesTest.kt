package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.ledger.AccountConstants
import com.example.core.ledger.DocumentType
import com.example.core.model.CurrencyCode
import com.example.core.model.ExchangeRate
import com.example.core.model.RateSource
import com.example.core.model.RateZone
import com.example.data.ledger.LedgerInvariants
import com.example.data.ledger.LedgerWriter
import com.example.data.ledger.PurchaseItemSpec
import com.example.data.local.AppDatabase
import com.example.data.local.entity.FiscalPeriodEntity
import com.example.data.local.entity.PartyEntity
import com.example.data.network.NetworkConfig
import com.example.data.network.NetworkRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verification Test Suite for:
 * Network Identity Default Currency & Auto-FX Logic across Purchasing & Operations
 *
 * Verifies:
 * 1. Network Profile persistence of rateZone, defaultUsdRate, defaultSarRate, and updatedAt.
 * 2. Auto-fill FX logic with fallback and manual overrides.
 * 3. Strict IAS 21 snapshot rate preservation (historical invoices never mutate on global rate changes).
 * 4. Local functional currency YER parity lock (1.0).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NetworkIdentityDefaultFxPurchasesTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var writer: LedgerWriter
    private lateinit var invariants: LedgerInvariants
    private lateinit var networkRepository: NetworkRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.createInMemory(context)
        AppDatabase.installTriggers(db.openHelper.writableDatabase)
        AppDatabase.seedDefaultData(db.openHelper.writableDatabase)

        writer = LedgerWriter(db, enableInvariantValidation = true)
        invariants = LedgerInvariants(db)
        networkRepository = NetworkRepository(context)

        runBlocking {
            db.fiscalPeriodDao().insertPeriod(
                FiscalPeriodEntity(
                    id = "FP_2026_01",
                    year = 2026,
                    month = 1,
                    isClosed = false
                )
            )
            db.partyDao().insertParty(
                PartyEntity(
                    id = "vendor_mikrotik_ye",
                    name = "وكيل ميكروتك اليمن المعتمد",
                    isVendor = true,
                    isCustomer = false
                )
            )
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    /**
     * Requirement 1: Network Profile persists rateZone, defaultUsdRate, defaultSarRate, and updatedAt.
     */
    @Test
    fun test01_networkProfileFxPersistence() {
        runBlocking {
            val customConfig = NetworkConfig(
                networkName = "شبكة الأمل اللاسلكية",
                rateZone = RateZone.ADEN,
                defaultUsdRateMicros = 1_685_000_000L,
                defaultSarRateMicros = 442_500_000L,
                updatedAt = 1770000000000L
            )

            networkRepository.saveConfig(customConfig)

            // Reload via new repository instance from file
            val reloadedRepository = NetworkRepository(context)
            val loaded = reloadedRepository.config.value

            assertEquals(RateZone.ADEN, loaded.rateZone)
            assertEquals(1_685_000_000L, loaded.defaultUsdRateMicros)
            assertEquals(442_500_000L, loaded.defaultSarRateMicros)
            assertEquals(1770000000000L, loaded.updatedAt)
            assertEquals(1685.0, loaded.defaultUsdRate, 0.001)
            assertEquals(442.5, loaded.defaultSarRate, 0.001)
        }
    }

    /**
     * Requirement 2: Real-time calculation and conversion for YER, USD, and SAR.
     */
    @Test
    fun test02_realTimeFxConversionMath() {
        val usdRate = ExchangeRate(CurrencyCode.USD, CurrencyCode.FUNCTIONAL, 535_000_000L) // 535 YER/USD
        val sarRate = ExchangeRate(CurrencyCode.SAR, CurrencyCode.FUNCTIONAL, 140_500_000L) // 140.50 YER/SAR
        val yerRate = ExchangeRate.parity(CurrencyCode.FUNCTIONAL)

        // Purchase item: 2 routers @ $120.00 each -> $240.00 (24,000 minor)
        val usdTotalMinor = 24_000L
        val yerEquivalent = usdRate.convert(usdTotalMinor)
        // 240.00 * 535 = 128,400.00 YER -> 12,840,000 minor
        assertEquals(12_840_000L, yerEquivalent)

        // SAR purchase: 100 PoE adapters @ 25.00 SAR each -> 2,500.00 SAR (250,000 minor)
        val sarTotalMinor = 250_000L
        val sarYerEquivalent = sarRate.convert(sarTotalMinor)
        // 2500.00 * 140.50 = 351,250.00 YER -> 35,125,000 minor
        assertEquals(35_125_000L, sarYerEquivalent)

        // Local YER: parity 1:1 conversion
        val yerAmountMinor = 500_000L
        assertEquals(500_000L, yerRate.convert(yerAmountMinor))
    }

    /**
     * Requirement 3: Strict IAS 21 Snapshot Guarantee.
     * Historical posted purchase invoices retain their locked snapshot exchange rate and
     * equivalent base YER amount even when global network profile rates change later.
     */
    @Test
    fun test03_ias21HistoricalRateSnapshotGuarantee() {
        runBlocking {
            // Step 1: Initial network configuration in Sana'a
            val initialConfig = NetworkConfig(
                rateZone = RateZone.SANAA,
                defaultUsdRateMicros = 535_000_000L,
                defaultSarRateMicros = 140_500_000L
            )
            networkRepository.saveConfig(initialConfig)

            val appliedSnapshotRate = ExchangeRate(
                fromCurrency = CurrencyCode.USD,
                toCurrency = CurrencyCode.FUNCTIONAL,
                rateMicros = initialConfig.defaultUsdRateMicros
            )

            // Step 2: Post invoice in USD at the snapshot rate
            val items = listOf(
                PurchaseItemSpec(
                    description = "راوتر سيرفر MikroTik CCR2004",
                    accountCode = AccountConstants.FIXED_ASSETS_NETWORK,
                    quantity = 1,
                    unitPriceMinor = 45_000L, // $450.00
                    isAsset = true,
                    usefulLifeMonths = 36
                )
            )

            val postedDoc = writer.postPurchaseInvoice(
                vendorPartyId = "vendor_mikrotik_ye",
                fiscalYear = 2026,
                dateEpochDay = 20450L,
                currency = CurrencyCode.USD,
                exchangeRate = appliedSnapshotRate,
                items = items,
                notes = "شراء سيرفر رئيسي بسعر الصرف المعتمد",
                rateZone = initialConfig.rateZone
            )

            val expectedHistoricalBaseMinor = appliedSnapshotRate.convert(45_000L) // 450 * 535 = 240,750 YER -> 24,075,000 minor
            assertEquals(45_000L, postedDoc.totalMinor)
            assertEquals(expectedHistoricalBaseMinor, postedDoc.totalBaseMinor)
            assertEquals(535_000_000L, postedDoc.exchangeRateMicros)
            assertEquals("SANAA", postedDoc.rateZone)

            // Step 3: Later date - Network operator switches identity to Aden zone and updates USD rate to 1,680 YER
            val updatedConfig = initialConfig.copy(
                rateZone = RateZone.ADEN,
                defaultUsdRateMicros = 1_680_000_000L,
                updatedAt = System.currentTimeMillis()
            )
            networkRepository.saveConfig(updatedConfig)

            // Step 4: Verify historical invoice document is NOT modified
            val fetchedDoc = db.documentDao().getDocumentById(postedDoc.id)
            assertNotNull(fetchedDoc)
            assertEquals("Historical exchangeRateMicros must remain locked at 535_000_000L", 535_000_000L, fetchedDoc!!.exchangeRateMicros)
            assertEquals("Historical totalBaseMinor must remain locked at 24,075,000L", expectedHistoricalBaseMinor, fetchedDoc.totalBaseMinor)
            assertEquals("Historical totalMinor must remain locked at 45,000L", 45_000L, fetchedDoc.totalMinor)
            assertEquals("Historical rate zone must remain SANAA", "SANAA", fetchedDoc.rateZone)

            // Step 5: Verify double-entry ledger lines for this document are strictly invariant
            val entries = db.journalDao().getEntriesForDocument(postedDoc.id)
            val lines = db.journalDao().getLinesForEntry(entries.first().id)
            val debitAssetLine = lines.first { it.accountCode == AccountConstants.FIXED_ASSETS_NETWORK }
            val creditPayableLine = lines.first { it.accountCode == AccountConstants.ACCOUNTS_PAYABLE }

            assertEquals(expectedHistoricalBaseMinor, debitAssetLine.baseDebitMinor)
            assertEquals(expectedHistoricalBaseMinor, creditPayableLine.baseCreditMinor)

            // Validate full ledger invariants still pass
            val checkResult = invariants.verifyAll()
            assertTrue("Ledger invariants must be valid", checkResult.isValid)
        }
    }

    /**
     * Requirement 4: Ad-hoc manual rate override during purchase entry without modifying global profile.
     */
    @Test
    fun test04_adHocManualRateOverrideDoesNotMutateGlobalProfile() {
        runBlocking {
            val globalConfig = NetworkConfig(
                rateZone = RateZone.SANAA,
                defaultUsdRateMicros = 535_000_000L
            )
            networkRepository.saveConfig(globalConfig)

            // Ad-hoc transaction exception rate (e.g. 542.00 YER from a specific exchanger)
            val adHocOverrideRate = ExchangeRate(CurrencyCode.USD, CurrencyCode.FUNCTIONAL, 542_000_000L)

            val items = listOf(
                PurchaseItemSpec(
                    description = "كيبل شبكة Cat6 خارجي",
                    accountCode = AccountConstants.OPERATING_EXPENSES,
                    quantity = 2,
                    unitPriceMinor = 6_000L, // $60.00 each = $120.00 total (12,000 minor)
                    isAsset = false
                )
            )

            val doc = writer.postPurchaseInvoice(
                vendorPartyId = "vendor_mikrotik_ye",
                fiscalYear = 2026,
                dateEpochDay = 20450L,
                currency = CurrencyCode.USD,
                exchangeRate = adHocOverrideRate,
                items = items,
                notes = "شراء كابلات بسعر صراف استثنائي",
                rateZone = RateZone.SANAA,
                rateSource = RateSource.MANUAL_OVERRIDE
            )

            assertEquals(542_000_000L, doc.exchangeRateMicros)
            val expectedBase = adHocOverrideRate.convert(12_000L) // 120 * 542 = 65,040 YER -> 6,504,000 minor
            assertEquals(expectedBase, doc.totalBaseMinor)

            // Global network profile rate must remain completely unaffected
            val currentProfile = networkRepository.config.value
            assertEquals("Global USD rate must not be overwritten by ad-hoc transaction override", 535_000_000L, currentProfile.defaultUsdRateMicros)
        }
    }

    /**
     * Requirement 5: Cash Vouchers (Receipt & Payment) inherit global RateZone and FX configuration,
     * recording correctly into the ledger with strict invariants.
     */
    @Test
    fun test05_cashVouchersInheritGlobalRateZoneAndFx() {
        runBlocking {
            // Configure Aden network profile with specific rates
            val adenConfig = NetworkConfig(
                networkName = "شبكة خليج عدن",
                rateZone = RateZone.ADEN,
                defaultUsdRateMicros = 1_620_000_000L,
                defaultSarRateMicros = 425_000_000L
            )
            networkRepository.saveConfig(adenConfig)

            val config = networkRepository.config.value
            assertEquals(RateZone.ADEN, config.rateZone)

            val usdRate = ExchangeRate(CurrencyCode.USD, CurrencyCode.FUNCTIONAL, config.defaultUsdRateMicros)

            // Test 1: Post customer receipt in USD treasury
            val receiptDoc = writer.postCustomerReceipt(
                partyId = "party_customer_1",
                treasuryId = "TR_USD_VAULT",
                fiscalYear = 2026,
                dateEpochDay = 20450L,
                amountOrigMinor = 50_00L, // $50.00
                currency = CurrencyCode.USD,
                exchangeRate = usdRate,
                rateZone = config.rateZone,
                notes = "سند قبض دولار مرتبط بهوية شبكة عدن"
            )

            assertNotNull(receiptDoc)
            assertEquals("USD", receiptDoc.currency)
            assertEquals(1_620_000_000L, receiptDoc.exchangeRateMicros)
            assertEquals(RateZone.ADEN.name, receiptDoc.rateZone)
            val expectedReceiptBase = usdRate.convert(50_00L) // 50 * 1620 = 81,000 YER
            assertEquals(expectedReceiptBase, receiptDoc.totalBaseMinor)

            // Test 2: Post payment voucher in USD treasury
            val paymentDoc = writer.postPaymentVoucher(
                recipientPartyId = "vendor_mikrotik_ye",
                treasuryId = "TR_USD_VAULT",
                fiscalYear = 2026,
                dateEpochDay = 20450L,
                amountOrigMinor = 30_00L, // $30.00
                currency = CurrencyCode.USD,
                exchangeRate = usdRate,
                paymentType = com.example.data.ledger.PaymentVoucherType.DIRECT_ISP_SERVICE,
                rateZone = config.rateZone,
                notes = "سند صرف دولار اشتراك إنترنت عدن"
            )

            assertNotNull(paymentDoc)
            assertEquals("USD", paymentDoc.currency)
            assertEquals(1_620_000_000L, paymentDoc.exchangeRateMicros)
            assertEquals(RateZone.ADEN.name, paymentDoc.rateZone)
            val expectedPaymentBase = usdRate.convert(30_00L)
            assertEquals(expectedPaymentBase, paymentDoc.totalBaseMinor)

            // Validate invariants
            val invariantResult = invariants.verifyAll(failFast = false)
            assertTrue(invariantResult.violations.joinToString { "[${it.invariantCode}] ${it.description} -> ${it.details}" }, invariantResult.isValid)
        }
    }

    /**
     * Requirement 6: Cash Voucher manual rate override overrides for this specific voucher
     * without modifying the global network profile.
     */
    @Test
    fun test06_cashVouchersAdHocOverrideDoesNotMutateGlobalProfile() {
        runBlocking {
            val globalConfig = NetworkConfig(
                rateZone = RateZone.SANAA,
                defaultUsdRateMicros = 530_000_000L
            )
            networkRepository.saveConfig(globalConfig)

            // Specific voucher override rate (e.g. 535 YER)
            val voucherOverrideRate = ExchangeRate(CurrencyCode.USD, CurrencyCode.FUNCTIONAL, 535_000_000L)

            val receiptDoc = writer.postCustomerReceipt(
                partyId = "party_customer_1",
                treasuryId = "TR_USD_VAULT",
                fiscalYear = 2026,
                dateEpochDay = 20450L,
                amountOrigMinor = 100_00L, // $100.00
                currency = CurrencyCode.USD,
                exchangeRate = voucherOverrideRate,
                rateZone = RateZone.SANAA,
                rateSource = RateSource.MANUAL_OVERRIDE,
                notes = "سند قبض بسعر صراف استثنائي"
            )

            assertEquals(535_000_000L, receiptDoc.exchangeRateMicros)
            assertEquals(RateSource.MANUAL_OVERRIDE.name, receiptDoc.rateSource)

            // Global profile must remain at 530.00
            val configAfter = networkRepository.config.value
            assertEquals(530_000_000L, configAfter.defaultUsdRateMicros)
            assertEquals(RateZone.SANAA, configAfter.rateZone)

            val invariantResult = invariants.verifyAll(failFast = false)
            assertTrue(invariantResult.violations.joinToString { "[${it.invariantCode}] ${it.description} -> ${it.details}" }, invariantResult.isValid)
        }
    }
}
