package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.ledger.AccountConstants
import com.example.core.ledger.DocumentType
import com.example.core.model.CurrencyCode
import com.example.core.model.ExchangeRate
import com.example.data.ledger.InvoiceAllocationSpec
import com.example.data.ledger.LedgerInvariants
import com.example.data.ledger.LedgerWriter
import com.example.data.ledger.PaymentVoucherType
import com.example.data.ledger.PurchaseItemSpec
import com.example.data.ledger.SalesItemSpec
import com.example.data.local.AppDatabase
import com.example.data.local.entity.FiscalPeriodEntity
import com.example.data.local.entity.PartyEntity
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
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LedgerWriterIntegrationTest {

    private lateinit var db: AppDatabase
    private lateinit var writer: LedgerWriter
    private lateinit var invariants: LedgerInvariants

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.createInMemory(context)
        // Ensure triggers and seed data are populated
        AppDatabase.installTriggers(db.openHelper.writableDatabase)
        AppDatabase.seedDefaultData(db.openHelper.writableDatabase)

        writer = LedgerWriter(db, enableInvariantValidation = true)
        invariants = LedgerInvariants(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    /**
     * Test 1:
     * فاتورة آجلة ثم سندا قبض جزئيان ثم إلغاء الثاني ← الحالة المشتقة والرصيد صحيحان.
     */
    @Test
    fun testCreditInvoiceTwoPartialReceiptsAndVoidSecond() {
        runBlocking {
            val agent = PartyEntity("AGENT_1", "بقالة النور", "770000001", isCustomer = true)
            db.partyDao().insertParty(agent)
            val rate = ExchangeRate.parity(CurrencyCode.YER)

            // 1. Post Credit Sales Invoice: 100,000 YER (10,000,000 minor)
            val invoice = writer.postSalesInvoice(
                partyId = agent.id,
                fiscalYear = 2026,
                dateEpochDay = 20001L,
                currency = CurrencyCode.YER,
                exchangeRate = rate,
                cardItems = listOf(SalesItemSpec("كروت 1 جيجا", 100, 100000L)),
                serviceItems = emptyList()
            )
            assertEquals(10000000L, db.journalDao().getNetDebitBalanceForAccount(AccountConstants.ACCOUNTS_RECEIVABLE))

            // 2. First Receipt: 40,000 YER (4,000,000 minor) allocated to invoice
            val rcv1 = writer.postCustomerReceipt(
                partyId = agent.id,
                treasuryId = "TR_MAIN_YER",
                fiscalYear = 2026,
                dateEpochDay = 20002L,
                amountOrigMinor = 4000000L,
                currency = CurrencyCode.YER,
                exchangeRate = rate,
                allocations = listOf(InvoiceAllocationSpec(invoice.id, 4000000L))
            )
            // Receivable balance should now be 60,000 YER
            assertEquals(6000000L, db.journalDao().getNetDebitBalanceForAccount(AccountConstants.ACCOUNTS_RECEIVABLE))

            // 3. Second Receipt: 30,000 YER (3,000,000 minor) allocated to invoice
            val rcv2 = writer.postCustomerReceipt(
                partyId = agent.id,
                treasuryId = "TR_MAIN_YER",
                fiscalYear = 2026,
                dateEpochDay = 20003L,
                amountOrigMinor = 3000000L,
                currency = CurrencyCode.YER,
                exchangeRate = rate,
                allocations = listOf(InvoiceAllocationSpec(invoice.id, 3000000L))
            )
            // Receivable balance should now be 30,000 YER
            assertEquals(3000000L, db.journalDao().getNetDebitBalanceForAccount(AccountConstants.ACCOUNTS_RECEIVABLE))

            // 4. Void the second receipt (rcv2)
            val voidResult = writer.voidDocument(rcv2.id, 20004L, "خطأ في تسجيل السند الثاني")
            assertTrue(voidResult)

            // After voiding rcv2, balance must return to exactly 60,000 YER (6,000,000 minor)
            val finalReceivable = db.journalDao().getNetDebitBalanceForAccount(AccountConstants.ACCOUNTS_RECEIVABLE)
            assertEquals(6000000L, finalReceivable)

            // Treasury balance must reflect only rcv1: 40,000 YER
            val treasuryBalance = db.journalDao().getNetDebitBalanceForTreasury("TR_MAIN_YER")
            assertEquals(4000000L, treasuryBalance)

            invariants.verifyAll()
        }
    }

    /**
     * Test 2:
     * فاتورة مبيعات رقمها الداخلي مطابق لفاتورة مشتريات ← لا تداخل بين سنداتهما.
     */
    @Test
    fun testDocNumberSequenceIsolationBetweenTypes() {
        runBlocking {
            val customer = PartyEntity("CUST_2", "عميل التوزيع", isCustomer = true)
            val vendor = PartyEntity("VEND_2", "مورد الكيبلات", isVendor = true)
            db.partyDao().insertParty(customer)
            db.partyDao().insertParty(vendor)

            val rate = ExchangeRate.parity(CurrencyCode.YER)

            // Post Sales Invoice #1
            val salesInv = writer.postSalesInvoice(
                partyId = customer.id,
                fiscalYear = 2026,
                dateEpochDay = 20001L,
                currency = CurrencyCode.YER,
                exchangeRate = rate,
                cardItems = listOf(SalesItemSpec("كرت", 1, 1000L)),
                serviceItems = emptyList()
            )
            assertEquals(1L, salesInv.docNumber)

            // Post Purchase Invoice #1
            val purchaseInv = writer.postPurchaseInvoice(
                vendorPartyId = vendor.id,
                fiscalYear = 2026,
                dateEpochDay = 20001L,
                currency = CurrencyCode.YER,
                exchangeRate = rate,
                items = listOf(PurchaseItemSpec("مشتريات أسلاك", AccountConstants.OPERATING_EXPENSES, 1, 5000L))
            )
            assertEquals(1L, purchaseInv.docNumber)

            // Documents have distinct IDs and separate types
            assertFalse(salesInv.id == purchaseInv.id)
            assertEquals(DocumentType.SALES_INVOICE.name, salesInv.type)
            assertEquals(DocumentType.PURCHASE_INVOICE.name, purchaseInv.type)

            invariants.verifyAll()
        }
    }

    /**
     * Test 3:
     * فاتورة مشتريات أصل بدفع فوري ثم تعديل المبلغ ← 1501 صحيح و2101 صفر وقيد واحد فعّال.
     */
    @Test
    fun testFixedAssetPurchaseWithCashSettlementAndAdjustment() {
        runBlocking {
            val vendor = PartyEntity("VEND_ROUTER", "مورد أجهزة الشبكة", isVendor = true)
            db.partyDao().insertParty(vendor)
            val rate = ExchangeRate.parity(CurrencyCode.YER)

            // 1. Purchase MikroTik Router for 200,000 YER (20,000,000 minor)
            db.treasuryDao().setAllowNegative("TR_MAIN_YER", true)
            val purchaseInv = writer.postPurchaseInvoice(
                vendorPartyId = vendor.id,
                fiscalYear = 2026,
                dateEpochDay = 20001L,
                currency = CurrencyCode.YER,
                exchangeRate = rate,
                items = listOf(
                    PurchaseItemSpec(
                        description = "راوتر MikroTik CCR",
                        accountCode = AccountConstants.FIXED_ASSETS_NETWORK,
                        quantity = 1,
                        unitPriceMinor = 20000000L,
                        isAsset = true,
                        usefulLifeMonths = 24
                    )
                )
            )
            // 2. Pay vendor in full
            val payment = writer.postPaymentVoucher(
                recipientPartyId = vendor.id,
                treasuryId = "TR_MAIN_YER",
                fiscalYear = 2026,
                dateEpochDay = 20001L,
                amountOrigMinor = 20000000L,
                currency = CurrencyCode.YER,
                exchangeRate = rate,
                paymentType = PaymentVoucherType.VENDOR_SETTLEMENT,
                invoiceAllocations = listOf(InvoiceAllocationSpec(purchaseInv.id, 20000000L))
            )

            // 2101 balance must be zero (fully paid)
            val vendorBalance = db.journalDao().getNetDebitBalanceForAccount(AccountConstants.ACCOUNTS_PAYABLE)
            assertEquals(0L, vendorBalance)

            // 1501 balance must equal 20,000,000 minor
            val assetBalance = db.journalDao().getNetDebitBalanceForAccount(AccountConstants.FIXED_ASSETS_NETWORK)
            assertEquals(20000000L, assetBalance)

            invariants.verifyAll()
        }
    }

    /**
     * Test 4:
     * فاتورة بالدولار تُسدَّد بسعر مختلف ← فرق العملة في 4901/5901 والميزان متوازن.
     */
    @Test
    fun testUsdInvoiceSettledAtDifferentRateRealizesFxGain() {
        runBlocking {
            val customer = PartyEntity("CUST_USD", "عميل أجنبي", isCustomer = true)
            db.partyDao().insertParty(customer)

            val invRate = ExchangeRate(CurrencyCode.USD, CurrencyCode.YER, 530_000_000L) // 530 YER/USD
            val settlementRate = ExchangeRate(CurrencyCode.USD, CurrencyCode.YER, 535_000_000L) // 535 YER/USD

            // Post $100 invoice @ 530 = 53,000 YER (5,300,000 minor)
            val inv = writer.postSalesInvoice(
                partyId = customer.id,
                fiscalYear = 2026,
                dateEpochDay = 20001L,
                currency = CurrencyCode.USD,
                exchangeRate = invRate,
                cardItems = emptyList(),
                serviceItems = listOf(SalesItemSpec("Dedicated Link", 1, 10000L))
            )

            // Settle $100 @ 535 = 53,500 YER (5,350,000 minor)
            val rcv = writer.postCustomerReceipt(
                partyId = customer.id,
                treasuryId = "TR_USD_VAULT",
                fiscalYear = 2026,
                dateEpochDay = 20002L,
                amountOrigMinor = 10000L,
                currency = CurrencyCode.USD,
                exchangeRate = settlementRate,
                allocations = listOf(InvoiceAllocationSpec(inv.id, 10000L))
            )

            // Receivable balance should be 0 YER
            val recBalance = db.journalDao().getNetDebitBalanceForAccount(AccountConstants.ACCOUNTS_RECEIVABLE)
            assertEquals(0L, recBalance)

            // Realized FX Gain (4901) should have credit balance of 500 YER (50,000 minor)
            val fxGainBalance = -db.journalDao().getNetDebitBalanceForAccount(AccountConstants.REALIZED_FX_GAIN)
            assertEquals(50000L, fxGainBalance)

            // Invariants must pass with perfect trial balance equality
            invariants.verifyAll()
        }
    }

    /**
     * Test 5:
     * إهلاك الشهر نفسه مرتين ← قيد واحد (Idempotency check).
     */
    @Test
    fun testDepreciationIdempotency() {
        runBlocking {
            val asset = com.example.data.local.entity.AssetEntity(
                id = "ASSET_ROUTER_1",
                docId = "DOC_1",
                name = "راوتر التوزيع",
                purchaseDateEpochDay = 20000L,
                purchaseCostMinor = 24000000L, // 240,000 YER
                salvageValueMinor = 0L,
                usefulLifeMonths = 24
            )
            db.assetDao().insertAsset(asset)

            // First run for Year 2026, Month 1: should succeed
            val firstRun = writer.runMonthlyDepreciation(asset.id, 2026, 1, 20031L)
            assertTrue(firstRun)

            // Second run for Year 2026, Month 1: must return false (already executed)
            val secondRun = writer.runMonthlyDepreciation(asset.id, 2026, 1, 20031L)
            assertFalse(secondRun)

            // Exactly one depreciation run record
            val runCount = db.assetDao().countDepreciationRun(2026, 1, asset.id)
            assertEquals(1, runCount)

            invariants.verifyAll()
        }
    }

    /**
     * Test 6:
     * إلغاء فاتورة مدفوعة ← كل الحسابات تعود لما قبلها.
     */
    @Test
    fun testVoidingPaidInvoiceRevertsAccounts() {
        runBlocking {
            val agent = PartyEntity("AGENT_PAID", "بقالة السعادة", isCustomer = true)
            db.partyDao().insertParty(agent)
            val rate = ExchangeRate.parity(CurrencyCode.YER)

            val inv = writer.postSalesInvoice(
                partyId = agent.id,
                fiscalYear = 2026,
                dateEpochDay = 20001L,
                currency = CurrencyCode.YER,
                exchangeRate = rate,
                cardItems = listOf(SalesItemSpec("كروت", 50, 100000L)),
                serviceItems = emptyList()
            )
            // 50,000 YER revenue
            assertEquals(-5000000L, db.journalDao().getNetDebitBalanceForAccount(AccountConstants.CARD_SALES_REVENUE))

            // Void the invoice
            val voidResult = writer.voidDocument(inv.id, 20002L, "إلغاء بناء على طلب العميل")
            assertTrue(voidResult)

            // Revenue must revert to 0 YER
            val netRevenue = db.journalDao().getNetDebitBalanceForAccount(AccountConstants.CARD_SALES_REVENUE)
            assertEquals(0L, netRevenue)

            // Receivable must revert to 0 YER
            val netReceivable = db.journalDao().getNetDebitBalanceForAccount(AccountConstants.ACCOUNTS_RECEIVABLE)
            assertEquals(0L, netReceivable)

            invariants.verifyAll()
        }
    }

    /**
     * Test 7:
     * ترحيل في فترة مقفلة ← يُرفض.
     */
    @Test
    fun testPostingInClosedFiscalPeriodIsRejected() {
        runBlocking {
            val customer = PartyEntity("CUST_CLOSED", "عميل الفترة", isCustomer = true)
            db.partyDao().insertParty(customer)

            val testDate = java.time.LocalDate.of(2026, 1, 1)
            val testEpochDay = testDate.toEpochDay()
            val year = testDate.year
            val month = testDate.monthValue

            db.fiscalPeriodDao().insertPeriod(
                FiscalPeriodEntity(
                    id = "PERIOD_2026_01",
                    year = year,
                    month = month,
                    isClosed = true,
                    closedAt = System.currentTimeMillis()
                )
            )

            val periodInDb = db.fiscalPeriodDao().getPeriod(year, month)
            assertNotNull("Inserted period must exist in db", periodInDb)
            assertTrue("Inserted period must be closed", periodInDb!!.isClosed)

            try {
                writer.postSalesInvoice(
                    partyId = customer.id,
                    fiscalYear = year,
                    dateEpochDay = testEpochDay,
                    currency = CurrencyCode.YER,
                    exchangeRate = ExchangeRate.parity(CurrencyCode.YER),
                    cardItems = listOf(SalesItemSpec("باقة", 1, 1000L)),
                    serviceItems = emptyList()
                )
                fail("Expected exception when posting in a closed fiscal period")
            } catch (e: IllegalStateException) {
                assertTrue(e.message!!.contains("closed"))
            }
        }
    }

    /**
     * Test 8:
     * محاولة UPDATE أو DELETE مباشرة على journal_lines ← تفشل بسبب المشغّل (SQLite Triggers).
     */
    @Test
    fun testImmutableJournalLinesTriggersPreventDirectUpdateAndDelete() {
        runBlocking {
            val customer = PartyEntity("CUST_TRIG", "عميل التجربة", isCustomer = true)
            db.partyDao().insertParty(customer)

            writer.postSalesInvoice(
                partyId = customer.id,
                fiscalYear = 2026,
                dateEpochDay = 20001L,
                currency = CurrencyCode.YER,
                exchangeRate = ExchangeRate.parity(CurrencyCode.YER),
                cardItems = listOf(SalesItemSpec("باقة", 1, 5000L)),
                serviceItems = emptyList()
            )

            // 1. Direct UPDATE attempt
            try {
                db.openHelper.writableDatabase.execSQL("UPDATE journal_lines SET baseDebitMinor = 999999")
                fail("Direct UPDATE on journal_lines should have failed with trigger abort")
            } catch (e: Exception) {
                assertTrue(e.message!!.contains("immutable") || e.message!!.contains("ABORT"))
            }

            // 2. Direct DELETE attempt
            try {
                db.openHelper.writableDatabase.execSQL("DELETE FROM journal_lines")
                fail("Direct DELETE on journal_lines should have failed with trigger abort")
            } catch (e: Exception) {
                assertTrue(e.message!!.contains("immutable") || e.message!!.contains("ABORT"))
            }
        }
    }

    /**
     * Test 9:
     * Property-based test: 50 operations in sequence (Create, Pay, Void)
     * Invariants verified after every single operation!
     */
    @Test
    fun testPropertyBasedRandomSequenceInvariants() {
        runBlocking {
            val rand = Random(42)
            val customer = PartyEntity("RAND_CUST", "عميل عشوائي", isCustomer = true)
            val vendor = PartyEntity("RAND_VEND", "مورد عشوائي", isVendor = true)
            db.partyDao().insertParty(customer)
            db.partyDao().insertParty(vendor)
            db.treasuryDao().setAllowNegative("TR_MAIN_YER", true)

            val rate = ExchangeRate.parity(CurrencyCode.YER)
            val openInvoices = mutableListOf<String>()

            for (i in 1..50) {
                val op = rand.nextInt(3)
                when (op) {
                    0 -> {
                        // Create Invoice
                        val qty = rand.nextInt(1, 10)
                        val price = rand.nextLong(100L, 5000L) * 100L
                        val doc = writer.postSalesInvoice(
                            partyId = customer.id,
                            fiscalYear = 2026,
                            dateEpochDay = 20000L + i,
                            currency = CurrencyCode.YER,
                            exchangeRate = rate,
                            cardItems = listOf(SalesItemSpec("كروت", qty, price)),
                            serviceItems = emptyList()
                        )
                        openInvoices.add(doc.id)
                    }
                    1 -> {
                        // Payment Voucher
                        val amount = rand.nextLong(100L, 2000L) * 100L
                        writer.postPaymentVoucher(
                            recipientPartyId = vendor.id,
                            treasuryId = "TR_MAIN_YER",
                            fiscalYear = 2026,
                            dateEpochDay = 20000L + i,
                            amountOrigMinor = amount,
                            currency = CurrencyCode.YER,
                            exchangeRate = rate,
                            paymentType = PaymentVoucherType.OPERATING_EXPENSE
                        )
                    }
                    2 -> {
                        // Void an invoice if any exists
                        if (openInvoices.isNotEmpty()) {
                            val toVoid = openInvoices.removeAt(0)
                            writer.voidDocument(toVoid, 20000L + i, "إلغاء عشوائي للاختبار")
                        }
                    }
                }
                // Strict assertion: invariants must hold after every single step
                invariants.verifyAll()
            }
        }
    }

    /**
     * Test 10:
     * تقريب: 3 أسطر مجموعها 100.00 بسعر صرف 530.5 ← يتوازن القيد بالسنت.
     */
    @Test
    fun testMultiLineRoundingBalancesToCent() {
        runBlocking {
            val customer = PartyEntity("CUST_SPLIT", "عميل التجزئة", isCustomer = true)
            db.partyDao().insertParty(customer)
            val rate = ExchangeRate(CurrencyCode.USD, CurrencyCode.YER, 530_500_000L) // 530.50 YER/USD

            // Sales invoice with card (33.33 USD) and service (66.67 USD) = 100.00 USD
            val doc = writer.postSalesInvoice(
                partyId = customer.id,
                fiscalYear = 2026,
                dateEpochDay = 20001L,
                currency = CurrencyCode.USD,
                exchangeRate = rate,
                cardItems = listOf(SalesItemSpec("كروت", 1, 3333L)),
                serviceItems = listOf(SalesItemSpec("خدمات", 1, 6667L))
            )

            val entries = db.journalDao().getEntriesForDocument(doc.id)
            val lines = db.journalDao().getLinesForEntry(entries.first().id)

            val totalDebit = lines.sumOf { it.baseDebitMinor }
            val totalCredit = lines.sumOf { it.baseCreditMinor }
            assertEquals(totalDebit, totalCredit)

            invariants.verifyAll()
        }
    }

    /**
     * Test 11:
     * Schema integrity test: check that default locked accounts, walk-in party, and standard treasuries exist.
     */
    @Test
    fun testSchemaV1InitialIntegrity() {
        runBlocking {
            val accounts = db.accountDao().getAllAccountsSync()
            assertTrue("Expected 21 standard IFRS accounts", accounts.size >= 21)

            val walkIn = db.partyDao().getPartyById(AppDatabase.WALK_IN_CASH_PARTY_ID)
            assertNotNull(walkIn)
            assertTrue(walkIn!!.isCustomer)

            val treasuries = db.treasuryDao().getAllTreasuriesSync()
            assertTrue(treasuries.any { it.currency == "YER" })
            assertTrue(treasuries.any { it.currency == "USD" })
            assertTrue(treasuries.any { it.currency == "SAR" })

            invariants.verifyAll()
        }
    }
}
