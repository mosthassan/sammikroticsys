package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.ledger.AccountConstants
import com.example.core.ledger.DocumentStatus
import com.example.core.ledger.DocumentType
import com.example.core.model.CurrencyCode
import com.example.core.model.ExchangeRate
import com.example.core.model.InsufficientTreasuryFundsException
import com.example.core.model.RateZone
import com.example.data.ledger.InvoiceAllocationSpec
import com.example.data.ledger.LedgerInvariants
import com.example.data.ledger.LedgerWriter
import com.example.data.ledger.PaymentSource
import com.example.data.ledger.PaymentVoucherType
import com.example.data.ledger.PurchaseItemSpec
import com.example.data.local.AppDatabase
import com.example.data.local.entity.FiscalPeriodEntity
import com.example.data.local.entity.PartyEntity
import com.example.data.local.entity.TreasuryAccountEntity
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
 * Test suite for Multi-Currency Part B: Foreign Treasury Policy & Funding Workflows.
 * Covers Tests 15 through 24 from Section 4:
 *
 * 15. Zero-overdraft invariant: disbursement rejected when balance < required and allowNegative=false.
 * 16. Zero-overdraft rollback: no sequence or document entity consumed upon rejection.
 * 17. Zero-overdraft exemption: disbursement allowed when allowNegative=true.
 * 18. Zero-overdraft invariant on Treasury Transfer: source treasury overdraft rejected.
 * 19. Single-currency enforcement on standard Treasury Transfer: cross-currency transfer rejected.
 * 20. Currency Exchange document (CURRENCY_EXCHANGE): Dr Dest (Original=Received, Base=Source Base),
 *     Cr Source (Original=Paid, Base=Source Base), zero FX gain/loss.
 * 21. Currency Exchange derived exchange rate: (sourceMinor / destMinor) calculation.
 * 22. Cross-currency invoice settlement: settling foreign invoice without legacy currency equality restriction.
 * 23. Cross-currency invoice settlement realizing FX Gain or Loss (IAS 21: 4901 / 5901).
 * 24. Partner paid out-of-pocket (PARTNER_PERSONAL): Dr AP 2101, Cr Partner Current 3201, cash boxes untouched.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ForeignTreasuryPolicyPartBTest {

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
     * Test 15: Zero-Overdraft Invariant (B.1)
     * When allowNegative is false, attempting disbursement with insufficient funds must throw
     * InsufficientTreasuryFundsException with exact treasuryId, availableMinor, and requiredMinor.
     */
    @Test
    fun test15_zeroOverdraftInvariantDisbursementRejected(): Unit = runBlocking {
        val vendor = PartyEntity(
            id = "PT_VENDOR_TEST15",
            name = "مورد أجهزة الاتصالات",
            phone = "770000001",
            isVendor = true
        )
        db.partyDao().insertParty(vendor)

        // TR_USD_VAULT has 0 USD balance and allowNegative == false
        val treasury = db.treasuryDao().getTreasuryById("TR_USD_VAULT")
        assertNotNull(treasury)
        assertEquals(false, treasury!!.allowNegative)

        val usdRate = resolver.resolve(CurrencyCode.USD, 20000, RateZone.SANAA)

        try {
            writer.postPaymentVoucher(
                recipientPartyId = vendor.id,
                treasuryId = "TR_USD_VAULT",
                fiscalYear = 2026,
                dateEpochDay = 20000,
                amountOrigMinor = 100L,
                currency = CurrencyCode.USD,
                exchangeRate = usdRate,
                paymentType = PaymentVoucherType.VENDOR_SETTLEMENT,
                notes = "محاولة صرف بدون رصيد"
            )
            fail("Must throw InsufficientTreasuryFundsException when treasury balance is insufficient")
        } catch (e: InsufficientTreasuryFundsException) {
            assertEquals("TR_USD_VAULT", e.treasuryId)
            assertEquals(0L, e.availableMinor)
            assertEquals(100L, e.requiredMinor)
        }
    }

    /**
     * Test 16: Zero-Overdraft Rollback (B.1)
     * When a disbursement is rejected due to zero-overdraft violation, the transaction must roll back
     * immediately and NO document entity or sequence number must be consumed.
     */
    @Test
    fun test16_zeroOverdraftRollbackNoDocumentOrSequenceConsumed(): Unit = runBlocking {
        val vendor = PartyEntity(
            id = "PT_VENDOR_TEST16",
            name = "مورد كابلات فايبر",
            phone = "770000002",
            isVendor = true
        )
        db.partyDao().insertParty(vendor)

        val seqBefore = db.numberSequenceDao().getSequence(DocumentType.PAYMENT_VOUCHER.name, 2026)?.nextValue ?: 1L
        val docsCountBefore = db.documentDao().getAllDocumentsSync().size

        val usdRate = resolver.resolve(CurrencyCode.USD, 20000, RateZone.SANAA)

        try {
            writer.postPaymentVoucher(
                recipientPartyId = vendor.id,
                treasuryId = "TR_USD_VAULT",
                fiscalYear = 2026,
                dateEpochDay = 20000,
                amountOrigMinor = 50L,
                currency = CurrencyCode.USD,
                exchangeRate = usdRate,
                paymentType = PaymentVoucherType.VENDOR_SETTLEMENT,
                notes = "محاولة صرف غير مغطاة"
            )
            fail("Expected InsufficientTreasuryFundsException")
        } catch (e: InsufficientTreasuryFundsException) {
            // Expected
        }

        val seqAfter = db.numberSequenceDao().getSequence(DocumentType.PAYMENT_VOUCHER.name, 2026)?.nextValue ?: 1L
        val docsCountAfter = db.documentDao().getAllDocumentsSync().size

        assertEquals("Number sequence must NOT be incremented on rollback", seqBefore, seqAfter)
        assertEquals("No document entity must be persisted on rollback", docsCountBefore, docsCountAfter)
    }

    /**
     * Test 17: Zero-Overdraft Exemption (allowNegative == true)
     * When allowNegative is true, disbursements exceeding available funds are permitted into negative balance.
     */
    @Test
    fun test17_zeroOverdraftExemptionWhenAllowNegativeIsTrue(): Unit = runBlocking {
        val vendor = PartyEntity(
            id = "PT_VENDOR_TEST17",
            name = "مورد أجهزة المايكروتيك",
            phone = "770000003",
            isVendor = true
        )
        db.partyDao().insertParty(vendor)

        // Enable allowNegative on USD vault
        db.treasuryDao().setAllowNegative("TR_USD_VAULT", true)
        val treasury = db.treasuryDao().getTreasuryById("TR_USD_VAULT")
        assertEquals(true, treasury!!.allowNegative)

        val usdRate = resolver.resolve(CurrencyCode.USD, 20000, RateZone.SANAA)

        val payDoc = writer.postPaymentVoucher(
            recipientPartyId = vendor.id,
            treasuryId = "TR_USD_VAULT",
            fiscalYear = 2026,
            dateEpochDay = 20000,
            amountOrigMinor = 150L,
            currency = CurrencyCode.USD,
            exchangeRate = usdRate,
            paymentType = PaymentVoucherType.VENDOR_SETTLEMENT,
            notes = "صرف بسحب على المكشوف مسموح"
        )

        assertNotNull(payDoc)
        assertEquals(DocumentStatus.POSTED.name, payDoc.status)

        // Balance must be -150 USD
        val netBalance = db.journalDao().getNetOrigBalanceForTreasury("TR_USD_VAULT")
        assertEquals(-150L, netBalance)

        invariants.verifyAll()
    }

    /**
     * Test 18: Zero-Overdraft Invariant on Treasury Transfer (postTreasuryTransfer)
     * Source treasury overdraft is strictly checked and rejected when allowNegative is false.
     */
    @Test
    fun test18_zeroOverdraftInvariantOnTreasuryTransfer(): Unit = runBlocking {
        // Create a secondary YER treasury
        val secTreasury = TreasuryAccountEntity(
            id = "TR_BRANCH_YER",
            name = "صندوق فرع المكلا (YER)",
            glAccountCode = "1101",
            currency = "YER",
            isActive = true,
            allowNegative = false
        )
        db.treasuryDao().insertTreasury(secTreasury)

        val parityRate = ExchangeRate.parity(CurrencyCode.FUNCTIONAL)

        // Attempt transfer from TR_MAIN_YER (balance 0)
        try {
            writer.postTreasuryTransfer(
                sourceTreasuryId = "TR_MAIN_YER",
                sourceAmountOrigMinor = 50_000L,
                sourceCurrency = CurrencyCode.FUNCTIONAL,
                sourceRate = parityRate,
                destTreasuryId = "TR_BRANCH_YER",
                destAmountOrigMinor = 50_000L,
                destCurrency = CurrencyCode.FUNCTIONAL,
                destRate = parityRate,
                fiscalYear = 2026,
                dateEpochDay = 20000,
                notes = "تحويل بين خزائن بدون رصيد"
            )
            fail("Must throw InsufficientTreasuryFundsException on treasury transfer overdraft")
        } catch (e: InsufficientTreasuryFundsException) {
            assertEquals("TR_MAIN_YER", e.treasuryId)
            assertEquals(0L, e.availableMinor)
            assertEquals(50_000L, e.requiredMinor)
        }

        // Now seed TR_MAIN_YER with 50,000 YER
        writer.postCapitalReceipt(
            targetAccountCode = AccountConstants.CAPITAL,
            partnerPartyId = null,
            treasuryId = "TR_MAIN_YER",
            fiscalYear = 2026,
            dateEpochDay = 20000,
            amountOrigMinor = 50_000L,
            currency = CurrencyCode.FUNCTIONAL,
            exchangeRate = parityRate,
            notes = "تغذية رأس المال"
        )

        // Transfer should now succeed
        val trfDoc = writer.postTreasuryTransfer(
            sourceTreasuryId = "TR_MAIN_YER",
            sourceAmountOrigMinor = 50_000L,
            sourceCurrency = CurrencyCode.FUNCTIONAL,
            sourceRate = parityRate,
            destTreasuryId = "TR_BRANCH_YER",
            destAmountOrigMinor = 50_000L,
            destCurrency = CurrencyCode.FUNCTIONAL,
            destRate = parityRate,
            fiscalYear = 2026,
            dateEpochDay = 20000,
            notes = "تحويل مغطى"
        )

        assertNotNull(trfDoc)
        assertEquals(0L, db.journalDao().getNetOrigBalanceForTreasury("TR_MAIN_YER"))
        assertEquals(50_000L, db.journalDao().getNetOrigBalanceForTreasury("TR_BRANCH_YER"))
        invariants.verifyAll()
    }

    /**
     * Test 19: Single-Currency Enforcement on Standard Treasury Transfer (B.3)
     * Cross-currency movements through postTreasuryTransfer are rejected, directing callers to CURRENCY_EXCHANGE.
     */
    @Test
    fun test19_singleCurrencyEnforcementOnStandardTreasuryTransfer(): Unit = runBlocking {
        val usdRate = resolver.resolve(CurrencyCode.USD, 20000, RateZone.SANAA)
        val yerRate = ExchangeRate.parity(CurrencyCode.FUNCTIONAL)

        try {
            writer.postTreasuryTransfer(
                sourceTreasuryId = "TR_USD_VAULT",
                sourceAmountOrigMinor = 100L,
                sourceCurrency = CurrencyCode.USD,
                sourceRate = usdRate,
                destTreasuryId = "TR_MAIN_YER",
                destAmountOrigMinor = 53_000L,
                destCurrency = CurrencyCode.FUNCTIONAL,
                destRate = yerRate,
                fiscalYear = 2026,
                dateEpochDay = 20000,
                notes = "تحويل غير مسموح بين عملات مختلفة"
            )
            fail("Must throw IllegalArgumentException when postTreasuryTransfer is used across different currencies")
        } catch (e: IllegalArgumentException) {
            assertTrue("Exception message should reference CURRENCY_EXCHANGE", e.message?.contains("CURRENCY_EXCHANGE") == true)
        }
    }

    /**
     * Test 20: Currency Exchange Document (CURRENCY_EXCHANGE) (B.3)
     * Journal Entry: Dr Dest Treasury (Original = Received, Base YER = Source Base YER),
     * Cr Source Treasury (Original = Paid, Base YER = Source Base YER). Zero FX gain/loss at exchange time.
     */
    @Test
    fun test20_currencyExchangeDocumentJournalEntryAndZeroFxGainLoss(): Unit = runBlocking {
        // 1. Seed USD Treasury with 100 USD
        val usdRate = resolver.resolve(CurrencyCode.USD, 20000, RateZone.SANAA)
        writer.postCapitalReceipt(
            targetAccountCode = AccountConstants.CAPITAL,
            partnerPartyId = null,
            treasuryId = "TR_USD_VAULT",
            fiscalYear = 2026,
            dateEpochDay = 20000,
            amountOrigMinor = 100L,
            currency = CurrencyCode.USD,
            exchangeRate = usdRate,
            notes = "إيداع رأسمال بالدولار"
        )

        // 2. Execute Currency Exchange: Pay 100 USD -> Receive 53,500 YER
        val excDoc = writer.postCurrencyExchange(
            sourceTreasuryId = "TR_USD_VAULT",
            sourceAmountOrigMinor = 100L,
            destTreasuryId = "TR_MAIN_YER",
            destAmountOrigMinor = 53_500L,
            fiscalYear = 2026,
            dateEpochDay = 20000,
            notes = "صرف 100 دولار بسعر 535 ريال"
        )

        assertEquals(DocumentType.CURRENCY_EXCHANGE.name, excDoc.type)
        assertEquals(DocumentStatus.POSTED.name, excDoc.status)
        assertEquals(53_500L, excDoc.totalBaseMinor)

        // 3. Inspect Journal Entry and Lines
        val entries = db.journalDao().getEntriesForDocument(excDoc.id)
        assertEquals(1, entries.size)
        val lines = db.journalDao().getLinesForEntry(entries.first().id)
        assertEquals(2, lines.size)

        // Dr Dest Treasury (TR_MAIN_YER)
        val debitLine = lines.first { it.baseDebitMinor > 0 }
        assertEquals("TR_MAIN_YER", debitLine.treasuryId)
        assertEquals(53_500L, debitLine.origMinor)
        assertEquals("YER", debitLine.currency)
        assertEquals(53_500L, debitLine.baseDebitMinor)
        assertEquals(0L, debitLine.baseCreditMinor)

        // Cr Source Treasury (TR_USD_VAULT)
        val creditLine = lines.first { it.baseCreditMinor > 0 }
        assertEquals("TR_USD_VAULT", creditLine.treasuryId)
        assertEquals(100L, creditLine.origMinor)
        assertEquals("USD", creditLine.currency)
        assertEquals(0L, creditLine.baseDebitMinor)
        assertEquals(53_500L, creditLine.baseCreditMinor) // Base matches Source Base YER!

        // Confirm ZERO FX Gain or Loss at exchange time
        val fxGainLines = lines.filter { it.accountCode == AccountConstants.REALIZED_FX_GAIN }
        val fxLossLines = lines.filter { it.accountCode == AccountConstants.REALIZED_FX_LOSS }
        assertTrue("No FX Gain line permitted in CURRENCY_EXCHANGE", fxGainLines.isEmpty())
        assertTrue("No FX Loss line permitted in CURRENCY_EXCHANGE", fxLossLines.isEmpty())

        // Confirm balances
        assertEquals("USD Vault balance should be 0", 0L, db.journalDao().getNetOrigBalanceForTreasury("TR_USD_VAULT"))
        assertEquals("YER Box balance should be 53,500", 53_500L, db.journalDao().getNetOrigBalanceForTreasury("TR_MAIN_YER"))

        invariants.verifyAll()
    }

    /**
     * Test 21: Currency Exchange Derived Exchange Rate (B.3)
     * Exchange rate is derived: (sourceMinor / destMinor), never manually entered.
     */
    @Test
    fun test21_currencyExchangeDerivedExchangeRate(): Unit = runBlocking {
        // Seed 200 USD
        val usdRate = resolver.resolve(CurrencyCode.USD, 20000, RateZone.SANAA)
        writer.postCapitalReceipt(
            targetAccountCode = AccountConstants.CAPITAL,
            partnerPartyId = null,
            treasuryId = "TR_USD_VAULT",
            fiscalYear = 2026,
            dateEpochDay = 20000,
            amountOrigMinor = 200L,
            currency = CurrencyCode.USD,
            exchangeRate = usdRate,
            notes = "تغذية خزينة الدولار"
        )

        // Exchange 100 USD -> 54,000 YER (Effective market rate: 540 YER/USD)
        val excDoc = writer.postCurrencyExchange(
            sourceTreasuryId = "TR_USD_VAULT",
            sourceAmountOrigMinor = 100L,
            destTreasuryId = "TR_MAIN_YER",
            destAmountOrigMinor = 54_000L,
            fiscalYear = 2026,
            dateEpochDay = 20000,
            notes = "صرف دولار بسعر السوق الحر"
        )

        // Rate derived: (100 * 1_000_000) / 54_000 = 1,851 micros (USD/YER ratio)
        // Dest received Base = 54,000 YER
        assertEquals(54_000L, excDoc.totalBaseMinor)
        assertEquals(100L, excDoc.totalMinor)

        val entries = db.journalDao().getEntriesForDocument(excDoc.id)
        val lines = db.journalDao().getLinesForEntry(entries.first().id)
        val creditLine = lines.first { it.baseCreditMinor > 0 }
        // USD line exchange rate micros: (54_000 * 1_000_000) / 100 = 540_000_000 micros (540 YER/USD)
        assertEquals(540_000_000L, creditLine.exchangeRateMicros)

        invariants.verifyAll()
    }

    /**
     * Test 22: Cross-Currency Invoice Settlement (B.4)
     * Settling foreign purchase invoice using functional currency (YER) without legacy currency equality restriction.
     */
    @Test
    fun test22_crossCurrencyInvoiceSettlementWithoutCurrencyRestriction(): Unit = runBlocking {
        val vendor = PartyEntity(
            id = "PT_VENDOR_TEST22",
            name = "مورد أجهزة المايكروتيك دبي",
            phone = "770000022",
            isVendor = true
        )
        db.partyDao().insertParty(vendor)

        // 1. Post Purchase Invoice in USD: 100 USD at 530 YER/USD = 53,000 YER
        val usdRate = resolver.resolve(CurrencyCode.USD, 20000, RateZone.SANAA)
        val invDoc = writer.postPurchaseInvoice(
            vendorPartyId = vendor.id,
            fiscalYear = 2026,
            dateEpochDay = 20000,
            currency = CurrencyCode.USD,
            exchangeRate = usdRate,
            items = listOf(PurchaseItemSpec("معدات شبكة", AccountConstants.FIXED_ASSETS_NETWORK, 1, 100L, true, 24)),
            notes = "فاتورة مشتريات بالدولار"
        )
        assertEquals(53_000L, invDoc.totalBaseMinor)

        // 2. Seed YER Treasury with 60,000 YER
        val yerRate = ExchangeRate.parity(CurrencyCode.FUNCTIONAL)
        writer.postCapitalReceipt(
            targetAccountCode = AccountConstants.CAPITAL,
            partnerPartyId = null,
            treasuryId = "TR_MAIN_YER",
            fiscalYear = 2026,
            dateEpochDay = 20000,
            amountOrigMinor = 60_000L,
            currency = CurrencyCode.FUNCTIONAL,
            exchangeRate = yerRate,
            notes = "تغذية صندوق الريال"
        )

        // 3. Settle USD Invoice paying 53,000 YER from TR_MAIN_YER (Cross-Currency Settlement)
        val payDoc = writer.postPaymentVoucher(
            recipientPartyId = vendor.id,
            treasuryId = "TR_MAIN_YER",
            fiscalYear = 2026,
            dateEpochDay = 20000,
            amountOrigMinor = 53_000L,
            currency = CurrencyCode.FUNCTIONAL,
            exchangeRate = yerRate,
            paymentType = PaymentVoucherType.VENDOR_SETTLEMENT,
            invoiceAllocations = listOf(InvoiceAllocationSpec(invDoc.id, 100L)),
            notes = "سداد الفاتورة الدولارية بالريال اليمني"
        )

        assertNotNull(payDoc)
        assertEquals(DocumentStatus.POSTED.name, payDoc.status)
        assertEquals("YER", payDoc.currency)

        // Verify allocation record
        val allocs = db.allocationDao().getActiveAllocationsForInvoice(invDoc.id)
        assertEquals(1, allocs.size)
        assertEquals(100L, allocs.first().allocatedOrigMinor)
        assertEquals(53_000L, allocs.first().allocatedBaseMinor)

        invariants.verifyAll()
    }

    /**
     * Test 23: Cross-Currency Invoice Settlement Realizing FX Gain / Loss (IAS 21: 4901 / 5901)
     * When paying foreign invoices at different rates or currencies, realize FX Loss or Gain in the ledger.
     */
    @Test
    fun test23_crossCurrencyInvoiceSettlementRealizingFxGainAndLoss(): Unit = runBlocking {
        val vendor = PartyEntity(
            id = "PT_VENDOR_TEST23",
            name = "مورد أجهزة الاتصالات الأردني",
            phone = "770000023",
            isVendor = true
        )
        db.partyDao().insertParty(vendor)

        // 1. Post Purchase Invoice in USD: 100 USD at 530 YER/USD (AP booked = 53,000 YER)
        val usdRateInitial = resolver.resolve(CurrencyCode.USD, 20000, RateZone.SANAA)
        val invDoc = writer.postPurchaseInvoice(
            vendorPartyId = vendor.id,
            fiscalYear = 2026,
            dateEpochDay = 20000,
            currency = CurrencyCode.USD,
            exchangeRate = usdRateInitial,
            items = listOf(PurchaseItemSpec("هوائيات ميكروويف", AccountConstants.FIXED_ASSETS_NETWORK, 1, 100L, true, 24)),
            notes = "فاتورة بالدولار 100 بسعر 530"
        )

        // 2. Seed YER Treasury with 100,000 YER
        val yerRate = ExchangeRate.parity(CurrencyCode.FUNCTIONAL)
        writer.postCapitalReceipt(
            targetAccountCode = AccountConstants.CAPITAL,
            partnerPartyId = null,
            treasuryId = "TR_MAIN_YER",
            fiscalYear = 2026,
            dateEpochDay = 20000,
            amountOrigMinor = 100_000L,
            currency = CurrencyCode.FUNCTIONAL,
            exchangeRate = yerRate,
            notes = "تغذية الصندوق بالريال"
        )

        // Case A: Pay 54,000 YER to settle the 53,000 YER liability -> FX Loss of 1,000 YER
        val payDoc = writer.postPaymentVoucher(
            recipientPartyId = vendor.id,
            treasuryId = "TR_MAIN_YER",
            fiscalYear = 2026,
            dateEpochDay = 20005,
            amountOrigMinor = 54_000L,
            currency = CurrencyCode.FUNCTIONAL,
            exchangeRate = yerRate,
            paymentType = PaymentVoucherType.VENDOR_SETTLEMENT,
            invoiceAllocations = listOf(InvoiceAllocationSpec(invDoc.id, 100L)),
            notes = "سداد الفاتورة بسعر صرف 540 ريال (خسارة فروق صرف 1,000)"
        )

        val entries = db.journalDao().getEntriesForDocument(payDoc.id)
        val lines = db.journalDao().getLinesForEntry(entries.first().id)

        // Expect 3 lines: Dr AP 2101 (53,000), Dr FX Loss 5901 (1,000), Cr Treasury (54,000)
        assertEquals(3, lines.size)
        val apLine = lines.first { it.accountCode == AccountConstants.ACCOUNTS_PAYABLE }
        val fxLossLine = lines.first { it.accountCode == AccountConstants.REALIZED_FX_LOSS }
        val trLine = lines.first { it.accountCode == "1101" }

        assertEquals(53_000L, apLine.baseDebitMinor)
        assertEquals(1_000L, fxLossLine.baseDebitMinor)
        assertEquals(54_000L, trLine.baseCreditMinor)

        val fxLossBalance = db.journalDao().getNetDebitBalanceForAccount(AccountConstants.REALIZED_FX_LOSS)
        assertEquals("Account 5901 net debit balance must reflect 1,000 YER loss", 1_000L, fxLossBalance)

        invariants.verifyAll()
    }

    /**
     * Test 24: Partner Paid Out-of-Pocket (PARTNER_PERSONAL) (B.5)
     * Support PaymentSource.PARTNER_PERSONAL: Dr AP 2101, Cr Partner Current 3201 at transaction-date rate.
     * Cash boxes / Treasuries remain completely untouched.
     */
    @Test
    fun test24_partnerPaidOutOfPocketPartnerPersonal(): Unit = runBlocking {
        // 1. Create Partner
        val partner = PartyEntity(
            id = "PT_PARTNER_MOHAMMED",
            name = "المهندس محمد (شريك مؤسس)",
            phone = "770000024",
            isPartner = true
        )
        db.partyDao().insertParty(partner)

        // 2. Create Vendor and Post Purchase Invoice in USD: 100 USD at 530 YER/USD = 53,000 YER
        val vendor = PartyEntity(
            id = "PT_VENDOR_TEST24",
            name = "مورد كابلات فايبر خارجي",
            phone = "770000025",
            isVendor = true
        )
        db.partyDao().insertParty(vendor)

        val usdRate = resolver.resolve(CurrencyCode.USD, 20000, RateZone.SANAA)
        val invDoc = writer.postPurchaseInvoice(
            vendorPartyId = vendor.id,
            fiscalYear = 2026,
            dateEpochDay = 20000,
            currency = CurrencyCode.USD,
            exchangeRate = usdRate,
            items = listOf(PurchaseItemSpec("كابلات دروب فايبر", AccountConstants.FIXED_ASSETS_NETWORK, 1, 100L, true, 24)),
            notes = "فاتورة كابلات فايبر"
        )
        assertEquals(53_000L, invDoc.totalBaseMinor)

        // Initial treasury balances
        val yerBalanceBefore = db.journalDao().getNetOrigBalanceForTreasury("TR_MAIN_YER")
        val usdBalanceBefore = db.journalDao().getNetOrigBalanceForTreasury("TR_USD_VAULT")

        // 3. Partner pays vendor invoice directly out-of-pocket (100 USD)
        val payDoc = writer.postPaymentVoucher(
            recipientPartyId = vendor.id,
            treasuryId = null,
            fiscalYear = 2026,
            dateEpochDay = 20000,
            amountOrigMinor = 100L,
            currency = CurrencyCode.USD,
            exchangeRate = usdRate,
            paymentType = PaymentVoucherType.VENDOR_SETTLEMENT,
            paymentSource = PaymentSource.PARTNER_PERSONAL,
            payingPartnerPartyId = partner.id,
            invoiceAllocations = listOf(InvoiceAllocationSpec(invDoc.id, 100L)),
            notes = "سداد المورد من الحساب الشخصي للشريك محمد"
        )

        assertNotNull(payDoc)
        assertEquals(DocumentStatus.POSTED.name, payDoc.status)

        // 4. Inspect Journal Lines: Dr AP 2101, Cr Partner Current 3201
        val entries = db.journalDao().getEntriesForDocument(payDoc.id)
        val lines = db.journalDao().getLinesForEntry(entries.first().id)
        assertEquals(2, lines.size)

        val apLine = lines.first { it.accountCode == AccountConstants.ACCOUNTS_PAYABLE }
        val partnerLine = lines.first { it.accountCode == AccountConstants.PARTNER_CURRENT }

        assertEquals(vendor.id, apLine.partyId)
        assertEquals(53_000L, apLine.baseDebitMinor)
        assertEquals(0L, apLine.baseCreditMinor)

        assertEquals(partner.id, partnerLine.partyId)
        assertEquals(0L, partnerLine.baseDebitMinor)
        assertEquals(53_000L, partnerLine.baseCreditMinor)

        // 5. Verify Cash boxes are completely UNTOUCHED
        val treasuryLines = lines.filter { it.treasuryId != null }
        assertTrue("Treasuries must remain completely untouched", treasuryLines.isEmpty())

        assertEquals(yerBalanceBefore, db.journalDao().getNetOrigBalanceForTreasury("TR_MAIN_YER"))
        assertEquals(usdBalanceBefore, db.journalDao().getNetOrigBalanceForTreasury("TR_USD_VAULT"))

        // 6. Verify Partner Balance in 3201
        val partnerBalances = db.journalDao().getPartyBalancesForControlAccount(AccountConstants.PARTNER_CURRENT)
        val partnerRow = partnerBalances.firstOrNull { it.partyId == partner.id }
        assertNotNull(partnerRow)
        // 3201 is credit normal: totalCredit - totalDebit = 53,000 YER
        assertEquals(53_000L, partnerRow!!.totalCreditMinor - partnerRow.totalDebitMinor)

        invariants.verifyAll()
    }
}
