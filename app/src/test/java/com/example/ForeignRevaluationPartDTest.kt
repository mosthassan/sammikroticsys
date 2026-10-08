package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.ledger.AccountConstants
import com.example.core.ledger.DocumentStatus
import com.example.core.ledger.DocumentType
import com.example.core.model.CurrencyCode
import com.example.core.model.ExchangeRate
import com.example.core.model.Money
import com.example.core.model.RateSource
import com.example.core.model.RateZone
import com.example.data.ledger.LedgerInvariants
import com.example.data.ledger.LedgerWriter
import com.example.data.ledger.PurchaseItemSpec
import com.example.data.local.AppDatabase
import com.example.data.local.entity.CurrencyRateEntity
import com.example.data.local.entity.FiscalPeriodEntity
import com.example.data.local.entity.PartyEntity
import com.example.domain.usecase.ExchangeRateResolver
import com.example.domain.usecase.FinancialStatementsUseCase
import com.example.domain.usecase.PeriodicRevaluationUseCase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
 * 31. Foreign Treasury Account Revaluation - Unrealized FX Gain (D.1):
 *     USD Treasury book YER balance adjusts upward on exchange rate appreciation,
 *     crediting Unrealized FX Gain 4902 while USD cash balance remains constant.
 * 32. Foreign Treasury Account Revaluation - Unrealized FX Loss (D.1):
 *     USD Treasury book YER balance adjusts downward on exchange rate depreciation,
 *     debiting Unrealized FX Loss 5902 while USD cash balance remains constant.
 * 33. Foreign Accounts Receivable Revaluation (Asset Monetary Item 1201) (D.1):
 *     Customer foreign currency receivables revalued to current closing rate with partyId
 *     maintaining full subledger control reconciliation (Invariant INV-002).
 * 34. Foreign Accounts Payable Revaluation (Liability Monetary Item 2101) (D.1):
 *     Vendor foreign currency payables revalued to current closing rate with partyId;
 *     exchange rate increase produces liability increase and debit to 5902.
 * 35. Strict Non-Monetary Item Invariant Enforcement (D.1):
 *     Non-monetary items (Fixed Assets 1501, Inventory 1401, Partner Capital 3101)
 *     MUST NEVER be revalued under IAS 21; engine strictly rejects revaluation.
 * 36. Zero-Delta Exemption / Unchanged Exchange Rates (D.1):
 *     When closing market rate equals existing book rate, zero delta is detected
 *     and no empty or zero-amount journal lines are posted.
 * 37. Periodic Revaluation Document Type (PERIODIC_REVALUATION / REV) & Numbering (D.1):
 *     Revaluation creates a document with type PERIODIC_REVALUATION (code prefix REV)
 *     and atomic sequential numbering.
 * 38. Dual-Currency Trial Balance & Ledger Reporting (D.2):
 *     Trial Balance displays original foreign currency balance alongside base currency YER.
 * 39. Dual FX Disclosure in Profit & Loss Statement (IAS 21) (D.2):
 *     Income Statement reports Realized FX (4901/5901) and Unrealized FX (4902/5902)
 *     separately, factoring both into Net Profit.
 * 40. Dual-Currency Balance Sheet Equilibrium & Revaluation Voiding / Reversal (D.1 / D.2):
 *     Balance Sheet stays in perfect equilibrium. Voiding a revaluation creates a clean
 *     reversal entry, restores previous book balance, and satisfies all ledger invariants.
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
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.createInMemory(context)
        AppDatabase.installTriggers(db.openHelper.writableDatabase)
        AppDatabase.seedDefaultData(db.openHelper.writableDatabase)

        writer = LedgerWriter(db, enableInvariantValidation = true)
        invariants = LedgerInvariants(db)
        resolver = ExchangeRateResolver(db)
        revaluationUseCase = PeriodicRevaluationUseCase(db, writer, resolver)
        statementsUseCase = FinancialStatementsUseCase(db)

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
     * Test 31: Foreign Treasury Account Revaluation - Unrealized FX Gain (D.1)
     * - Deposit 1,000 USD into TR_USD_VAULT at rate 530 YER = 530,000 YER base (53,000,000 minor).
     * - Rate jumps to 600 YER (600,000,000 micros).
     * - Expected Market Value = 1,000 * 600 = 600,000 YER.
     * - Delta = 600,000 - 530,000 = +70,000 YER (+7,000,000 minor).
     * - Auto-generated Journal Entry:
     *   DR TR_USD_VAULT (70,000 YER base, orig = 0),
     *   CR 4902 Unrealized FX Gain (70,000 YER base).
     * - Treasury book YER balance becomes 600,000 YER; physical USD balance remains 1,000 USD.
     */
    @Test
    fun test31_foreignTreasuryRevaluationUnrealizedFxGain() {
        runBlocking {
            val partnerId = "PARTNER_D31"
            db.partyDao().insertParty(PartyEntity(id = partnerId, name = "شريك تمويل د31", isPartner = true))

            val usdAmountMinor = 1_000_00L // 1,000.00 USD
            val initialRate = ExchangeRate(CurrencyCode.USD, CurrencyCode.FUNCTIONAL, 530_000_000L)
            val dateEpoch = LocalDate.of(2026, 10, 5).toEpochDay()

            // Seed 1,000 USD into TR_USD_VAULT
            writer.postCapitalReceipt(
                targetAccountCode = AccountConstants.CAPITAL,
                partnerPartyId = partnerId,
                treasuryId = "TR_USD_VAULT",
                fiscalYear = 2026,
                dateEpochDay = dateEpoch,
                amountOrigMinor = usdAmountMinor,
                currency = CurrencyCode.USD,
                exchangeRate = initialRate,
                notes = "إيداع أولي بالدولار"
            )

            val usdCashBefore = db.journalDao().getNetOrigBalanceForTreasury("TR_USD_VAULT")
            val yerBookBefore = db.journalDao().getNetDebitBalanceForTreasury("TR_USD_VAULT")
            assertEquals(1_000_00L, usdCashBefore)
            assertEquals(530_000_00L, yerBookBefore)

            // Market rate appreciates to 600 YER
            val appreciatedRate = ExchangeRate(CurrencyCode.USD, CurrencyCode.FUNCTIONAL, 600_000_000L)
            val revalDate = LocalDate.of(2026, 10, 31).toEpochDay()

            val doc = revaluationUseCase.revalueMonetaryItem(
                accountCode = AccountConstants.CASH_VAULT,
                treasuryId = "TR_USD_VAULT",
                fiscalYear = 2026,
                dateEpochDay = revalDate,
                foreignCurrency = CurrencyCode.USD,
                exchangeRate = appreciatedRate,
                notes = "إعادة تقييم نهاية الفترة - أرباح غير محققة"
            )

            assertNotNull(doc)
            assertEquals(DocumentType.PERIODIC_REVALUATION.name, doc!!.type)
            assertEquals(DocumentStatus.POSTED.name, doc.status)
            assertEquals(70_000_00L, doc.totalBaseMinor)

            // Verify journal entries
            val entries = db.journalDao().getEntriesForDocument(doc.id)
            assertEquals(1, entries.size)
            val lines = db.journalDao().getLinesForEntry(entries[0].id)
            assertEquals(2, lines.size)

            val drLine = lines.first { it.baseDebitMinor > 0 }
            val crLine = lines.first { it.baseCreditMinor > 0 }

            assertEquals(AccountConstants.CASH_VAULT, drLine.accountCode)
            assertEquals("TR_USD_VAULT", drLine.treasuryId)
            assertEquals(0L, drLine.origMinor) // USD physical quantity untouched
            assertEquals(70_000_00L, drLine.baseDebitMinor)

            assertEquals(AccountConstants.UNREALIZED_FX_GAIN, crLine.accountCode)
            assertEquals(70_000_00L, crLine.baseCreditMinor)

            // Verify new balances
            val usdCashAfter = db.journalDao().getNetOrigBalanceForTreasury("TR_USD_VAULT")
            val yerBookAfter = db.journalDao().getNetDebitBalanceForTreasury("TR_USD_VAULT")
            assertEquals("Physical USD quantity must remain frozen at 1,000 USD", 1_000_00L, usdCashAfter)
            assertEquals("Book YER value must adjust to 600,000 YER", 600_000_00L, yerBookAfter)

            invariants.verifyAll()
        }
    }

    /**
     * Test 32: Foreign Treasury Account Revaluation - Unrealized FX Loss (D.1)
     * - Deposit 1,000 USD at initial rate 600 YER = 600,000 YER base (60,000,000 minor).
     * - Rate drops to 530 YER (530,000,000 micros).
     * - Expected Market Value = 530,000 YER.
     * - Delta = 530,000 - 600,000 = -70,000 YER (Unrealized FX Loss).
     * - Auto-generated Journal Entry:
     *   DR 5902 Unrealized FX Loss (70,000 YER base),
     *   CR TR_USD_VAULT (70,000 YER base, orig = 0).
     * - Treasury book YER balance becomes 530,000 YER; physical USD balance remains 1,000 USD.
     */
    @Test
    fun test32_foreignTreasuryRevaluationUnrealizedFxLoss() {
        runBlocking {
            val partnerId = "PARTNER_D32"
            db.partyDao().insertParty(PartyEntity(id = partnerId, name = "شريك تمويل د32", isPartner = true))

            val usdAmountMinor = 1_000_00L
            val highRate = ExchangeRate(CurrencyCode.USD, CurrencyCode.FUNCTIONAL, 600_000_000L)
            val dateEpoch = LocalDate.of(2026, 10, 5).toEpochDay()

            writer.postCapitalReceipt(
                targetAccountCode = AccountConstants.CAPITAL,
                partnerPartyId = partnerId,
                treasuryId = "TR_USD_VAULT",
                fiscalYear = 2026,
                dateEpochDay = dateEpoch,
                amountOrigMinor = usdAmountMinor,
                currency = CurrencyCode.USD,
                exchangeRate = highRate,
                notes = "إيداع أولي بسعر مرتفع"
            )

            // Market rate drops to 530 YER
            val droppedRate = ExchangeRate(CurrencyCode.USD, CurrencyCode.FUNCTIONAL, 530_000_000L)
            val revalDate = LocalDate.of(2026, 10, 31).toEpochDay()

            val doc = revaluationUseCase.revalueMonetaryItem(
                accountCode = AccountConstants.CASH_VAULT,
                treasuryId = "TR_USD_VAULT",
                fiscalYear = 2026,
                dateEpochDay = revalDate,
                foreignCurrency = CurrencyCode.USD,
                exchangeRate = droppedRate,
                notes = "إعادة تقييم نهاية الفترة - خسائر غير محققة"
            )

            assertNotNull(doc)
            assertEquals(DocumentType.PERIODIC_REVALUATION.name, doc!!.type)
            assertEquals(70_000_00L, doc.totalBaseMinor)

            val entries = db.journalDao().getEntriesForDocument(doc.id)
            val lines = db.journalDao().getLinesForEntry(entries[0].id)
            val drLine = lines.first { it.baseDebitMinor > 0 }
            val crLine = lines.first { it.baseCreditMinor > 0 }

            assertEquals(AccountConstants.UNREALIZED_FX_LOSS, drLine.accountCode)
            assertEquals(70_000_00L, drLine.baseDebitMinor)

            assertEquals(AccountConstants.CASH_VAULT, crLine.accountCode)
            assertEquals("TR_USD_VAULT", crLine.treasuryId)
            assertEquals(0L, crLine.origMinor)
            assertEquals(70_000_00L, crLine.baseCreditMinor)

            val usdCashAfter = db.journalDao().getNetOrigBalanceForTreasury("TR_USD_VAULT")
            val yerBookAfter = db.journalDao().getNetDebitBalanceForTreasury("TR_USD_VAULT")
            assertEquals(1_000_00L, usdCashAfter)
            assertEquals(530_000_00L, yerBookAfter)

            invariants.verifyAll()
        }
    }

    /**
     * Test 33: Foreign Accounts Receivable Revaluation (Asset Monetary Item 1201) (D.1)
     * - Customer invoice for 500 USD @ 530 YER = 265,000 YER base (26,500,000 minor).
     * - Rate rises to 550 YER = 275,000 YER base (27,500,000 minor).
     * - Delta = +10,000 YER (+1,000,000 minor) Unrealized FX Gain.
     * - Auto-generated Journal Entry:
     *   DR 1201 (partyId = customerId, baseDebit = 10,000 YER, orig = 0),
     *   CR 4902 Unrealized FX Gain (10,000 YER).
     * - Subledger control account reconciliation (INV-002) passes.
     */
    @Test
    fun test33_foreignAccountsReceivableRevaluation() {
        runBlocking {
            val customerId = "CUSTOMER_USD_D33"
            db.partyDao().insertParty(PartyEntity(id = customerId, name = "وكيل الجملة بالدولار", isCustomer = true))

            val usdInvoiceMinor = 500_00L // 500 USD
            val initialRate = ExchangeRate(CurrencyCode.USD, CurrencyCode.FUNCTIONAL, 530_000_000L)
            val dateEpoch = LocalDate.of(2026, 10, 5).toEpochDay()

            writer.postSalesInvoice(
                partyId = customerId,
                fiscalYear = 2026,
                dateEpochDay = dateEpoch,
                currency = CurrencyCode.USD,
                exchangeRate = initialRate,
                cardItems = listOf(com.example.data.ledger.SalesItemSpec("كروت بالدولار", 1, usdInvoiceMinor)),
                serviceItems = emptyList(),
                notes = "فاتورة كروت بالدولار"
            )

            val origRecBefore = db.journalDao().getPartyReceivableOrigBalance(customerId, "USD")
            val baseRecBefore = db.journalDao().getPartyReceivableBaseBalanceByCurrency(customerId, "USD")
            assertEquals(500_00L, origRecBefore)
            assertEquals(265_000_00L, baseRecBefore)

            // Market rate rises to 550 YER
            val appreciatedRate = ExchangeRate(CurrencyCode.USD, CurrencyCode.FUNCTIONAL, 550_000_000L)
            val revalDate = LocalDate.of(2026, 10, 31).toEpochDay()

            val doc = revaluationUseCase.revalueMonetaryItem(
                accountCode = AccountConstants.ACCOUNTS_RECEIVABLE,
                partyId = customerId,
                fiscalYear = 2026,
                dateEpochDay = revalDate,
                foreignCurrency = CurrencyCode.USD,
                exchangeRate = appreciatedRate,
                notes = "إعادة تقييم ذمم عميل بالدولار"
            )

            assertNotNull(doc)
            assertEquals(10_000_00L, doc!!.totalBaseMinor)

            val baseRecAfter = db.journalDao().getPartyReceivableBaseBalanceByCurrency(customerId, "USD")
            assertEquals(275_000_00L, baseRecAfter)

            // Subledger / Control account reconciliation must remain strictly equal
            val glReceivable = db.journalDao().getNetDebitBalanceForAccount(AccountConstants.ACCOUNTS_RECEIVABLE)
            val partyReceivable = db.journalDao().getPartyBalancesForControlAccount(AccountConstants.ACCOUNTS_RECEIVABLE).sumOf { it.netBalanceMinor }
            assertEquals(glReceivable, partyReceivable)

            invariants.verifyAll()
        }
    }

    /**
     * Test 34: Foreign Accounts Payable Revaluation (Liability Monetary Item 2101) (D.1)
     * - Vendor purchase invoice for 1,000 USD @ 530 YER = 530,000 YER base (53,000,000 minor).
     * - Rate rises to 600 YER = 600,000 YER base (60,000,000 minor).
     * - Liability increases by 70,000 YER -> Unrealized FX Loss!
     * - Auto-generated Journal Entry:
     *   DR 5902 Unrealized FX Loss (70,000 YER),
     *   CR 2101 (partyId = vendorId, baseCredit = 70,000 YER, orig = 0).
     * - Vendor book payable becomes 600,000 YER; subledger control reconciliation (INV-002) passes.
     */
    @Test
    fun test34_foreignAccountsPayableRevaluation() {
        runBlocking {
            val vendorId = "VENDOR_STARLINK_D34"
            db.partyDao().insertParty(PartyEntity(id = vendorId, name = "مزود ستارلينك الدولي", isVendor = true))

            val usdInvoiceMinor = 1_000_00L // 1,000 USD
            val initialRate = ExchangeRate(CurrencyCode.USD, CurrencyCode.FUNCTIONAL, 530_000_000L)
            val dateEpoch = LocalDate.of(2026, 10, 5).toEpochDay()

            writer.postPurchaseInvoice(
                vendorPartyId = vendorId,
                fiscalYear = 2026,
                dateEpochDay = dateEpoch,
                currency = CurrencyCode.USD,
                exchangeRate = initialRate,
                items = listOf(PurchaseItemSpec("اشتراك ستارلينك", AccountConstants.DIRECT_ISP_SERVICE_COST, 1, usdInvoiceMinor)),
                notes = "فاتورة ستارلينك بالدولار"
            )

            val basePayBefore = db.journalDao().getPartyPayableBaseBalanceByCurrency(vendorId, "USD")
            assertEquals(530_000_00L, basePayBefore)

            // Market rate rises to 600 YER -> liability increases, resulting in Unrealized Loss
            val higherRate = ExchangeRate(CurrencyCode.USD, CurrencyCode.FUNCTIONAL, 600_000_000L)
            val revalDate = LocalDate.of(2026, 10, 31).toEpochDay()

            val doc = revaluationUseCase.revalueMonetaryItem(
                accountCode = AccountConstants.ACCOUNTS_PAYABLE,
                partyId = vendorId,
                fiscalYear = 2026,
                dateEpochDay = revalDate,
                foreignCurrency = CurrencyCode.USD,
                exchangeRate = higherRate,
                notes = "إعادة تقييم ذمم مورد بالدولار"
            )

            assertNotNull(doc)
            assertEquals(70_000_00L, doc!!.totalBaseMinor)

            val entries = db.journalDao().getEntriesForDocument(doc.id)
            val lines = db.journalDao().getLinesForEntry(entries[0].id)
            val drLine = lines.first { it.baseDebitMinor > 0 }
            val crLine = lines.first { it.baseCreditMinor > 0 }

            assertEquals(AccountConstants.UNREALIZED_FX_LOSS, drLine.accountCode)
            assertEquals(70_000_00L, drLine.baseDebitMinor)

            assertEquals(AccountConstants.ACCOUNTS_PAYABLE, crLine.accountCode)
            assertEquals(vendorId, crLine.partyId)
            assertEquals(70_000_00L, crLine.baseCreditMinor)

            val basePayAfter = db.journalDao().getPartyPayableBaseBalanceByCurrency(vendorId, "USD")
            assertEquals(600_000_00L, basePayAfter)

            // Subledger reconciliation verification
            val glPayables = db.journalDao().getNetDebitBalanceForAccount(AccountConstants.ACCOUNTS_PAYABLE)
            val partyPayables = db.journalDao().getPartyBalancesForControlAccount(AccountConstants.ACCOUNTS_PAYABLE).sumOf { it.netBalanceMinor }
            assertEquals(glPayables, partyPayables)

            invariants.verifyAll()
        }
    }

    /**
     * Test 35: Strict Non-Monetary Item Invariant Enforcement (D.1)
     * - Under IAS 21, Non-monetary items (Fixed Assets 1501, Inventory 1401, Partner Capital 3101)
     *   MUST NEVER be revalued at period-end.
     * - Attempting to invoke revaluation for non-monetary accounts must immediately throw IllegalArgumentException.
     */
    @Test
    fun test35_strictNonMonetaryItemInvariantEnforcement() {
        runBlocking {
            val dateEpoch = LocalDate.of(2026, 10, 31).toEpochDay()
            val usdRate = ExchangeRate(CurrencyCode.USD, CurrencyCode.FUNCTIONAL, 600_000_000L)

            // 1. Fixed Assets (1501)
            try {
                writer.postPeriodicRevaluation(
                    accountCode = AccountConstants.FIXED_ASSETS_NETWORK,
                    fiscalYear = 2026,
                    dateEpochDay = dateEpoch,
                    foreignCurrency = CurrencyCode.USD,
                    exchangeRate = usdRate
                )
                fail("Expected IllegalArgumentException when revaluing Fixed Assets (1501)")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message?.contains("non-monetary", ignoreCase = true) == true)
            }

            // 2. Card Inventory (1401 / 1301)
            try {
                writer.postPeriodicRevaluation(
                    accountCode = AccountConstants.CARD_INVENTORY_RESERVE,
                    fiscalYear = 2026,
                    dateEpochDay = dateEpoch,
                    foreignCurrency = CurrencyCode.USD,
                    exchangeRate = usdRate
                )
                fail("Expected IllegalArgumentException when revaluing Inventory Reserve (1301)")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message?.contains("non-monetary", ignoreCase = true) == true)
            }

            // 3. Partner Capital (3101)
            try {
                writer.postPeriodicRevaluation(
                    accountCode = AccountConstants.CAPITAL,
                    partyId = "SOME_PARTNER",
                    fiscalYear = 2026,
                    dateEpochDay = dateEpoch,
                    foreignCurrency = CurrencyCode.USD,
                    exchangeRate = usdRate
                )
                fail("Expected IllegalArgumentException when revaluing Partner Capital (3101)")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message?.contains("non-monetary", ignoreCase = true) == true)
            }
        }
    }

    /**
     * Test 36: Zero-Delta Exemption / Unchanged Exchange Rates (D.1)
     * - When closing market rate equals book rate (delta = 0), no journal entry is created,
     *   preventing polluting the ledger with 0-amount entries.
     */
    @Test
    fun test36_zeroDeltaExemptionAndUnchangedRates() {
        runBlocking {
            val partnerId = "PARTNER_D36"
            db.partyDao().insertParty(PartyEntity(id = partnerId, name = "شريك تمويل د36", isPartner = true))

            val usdAmountMinor = 1_000_00L
            val rate530 = ExchangeRate(CurrencyCode.USD, CurrencyCode.FUNCTIONAL, 530_000_000L)
            val dateEpoch = LocalDate.of(2026, 10, 5).toEpochDay()

            writer.postCapitalReceipt(
                targetAccountCode = AccountConstants.CAPITAL,
                partnerPartyId = partnerId,
                treasuryId = "TR_USD_VAULT",
                fiscalYear = 2026,
                dateEpochDay = dateEpoch,
                amountOrigMinor = usdAmountMinor,
                currency = CurrencyCode.USD,
                exchangeRate = rate530
            )

            // Revalue with unchanged rate (530 YER)
            val revalDoc = revaluationUseCase.revalueMonetaryItem(
                accountCode = AccountConstants.CASH_VAULT,
                treasuryId = "TR_USD_VAULT",
                fiscalYear = 2026,
                dateEpochDay = dateEpoch,
                foreignCurrency = CurrencyCode.USD,
                exchangeRate = rate530
            )

            // Must return null and create zero documents
            assertNull("Zero delta revaluation must return null and post no document", revalDoc)

            val totalDocs = db.documentDao().getAllDocumentsSync()
            assertEquals("Only the capital receipt should exist in the document store", 1, totalDocs.size)

            invariants.verifyAll()
        }
    }

    /**
     * Test 37: Periodic Revaluation Document Type (PERIODIC_REVALUATION / REV) & Numbering (D.1)
     * - Revaluation creates document with type PERIODIC_REVALUATION (prefix REV)
     * - Sequential docNumber increments atomically per fiscal year.
     */
    @Test
    fun test37_periodicRevaluationDocumentTypeAndSequentialNumbering() {
        runBlocking {
            val partnerId = "PARTNER_D37"
            db.partyDao().insertParty(PartyEntity(id = partnerId, name = "شريك د37", isPartner = true))

            val initialRate = ExchangeRate(CurrencyCode.USD, CurrencyCode.FUNCTIONAL, 530_000_000L)
            val dateEpoch = LocalDate.of(2026, 10, 5).toEpochDay()

            writer.postCapitalReceipt(
                targetAccountCode = AccountConstants.CAPITAL,
                partnerPartyId = partnerId,
                treasuryId = "TR_USD_VAULT",
                fiscalYear = 2026,
                dateEpochDay = dateEpoch,
                amountOrigMinor = 2_000_00L, // 2,000 USD
                currency = CurrencyCode.USD,
                exchangeRate = initialRate
            )

            // First revaluation run: rate 550 YER
            val rate550 = ExchangeRate(CurrencyCode.USD, CurrencyCode.FUNCTIONAL, 550_000_000L)
            val doc1 = revaluationUseCase.revalueMonetaryItem(
                accountCode = AccountConstants.CASH_VAULT,
                treasuryId = "TR_USD_VAULT",
                fiscalYear = 2026,
                dateEpochDay = dateEpoch,
                foreignCurrency = CurrencyCode.USD,
                exchangeRate = rate550
            )
            assertNotNull(doc1)
            assertEquals("PERIODIC_REVALUATION", doc1!!.type)
            assertEquals(DocumentType.PERIODIC_REVALUATION.codePrefix, "REV")
            assertEquals(1L, doc1.docNumber)

            // Second revaluation run: rate 570 YER
            val rate570 = ExchangeRate(CurrencyCode.USD, CurrencyCode.FUNCTIONAL, 570_000_000L)
            val doc2 = revaluationUseCase.revalueMonetaryItem(
                accountCode = AccountConstants.CASH_VAULT,
                treasuryId = "TR_USD_VAULT",
                fiscalYear = 2026,
                dateEpochDay = dateEpoch + 1,
                foreignCurrency = CurrencyCode.USD,
                exchangeRate = rate570
            )
            assertNotNull(doc2)
            assertEquals(2L, doc2!!.docNumber)

            invariants.verifyAll()
        }
    }

    /**
     * Test 38: Dual-Currency Trial Balance & Ledger Reporting (D.2)
     * - Accounts report balances in original foreign currency alongside base currency YER.
     * - USD Treasury reports orig balance 1,000.00 USD and base balance in YER.
     * - Total debits equal credits in base YER.
     */
    @Test
    fun test38_dualCurrencyTrialBalanceReporting() {
        runBlocking {
            val partnerId = "PARTNER_D38"
            db.partyDao().insertParty(PartyEntity(id = partnerId, name = "شريك د38", isPartner = true))

            val dateEpoch = LocalDate.of(2026, 10, 5).toEpochDay()
            val usdRate = ExchangeRate(CurrencyCode.USD, CurrencyCode.FUNCTIONAL, 530_000_000L)

            // 1,000 USD to TR_USD_VAULT
            writer.postCapitalReceipt(
                targetAccountCode = AccountConstants.CAPITAL,
                partnerPartyId = partnerId,
                treasuryId = "TR_USD_VAULT",
                fiscalYear = 2026,
                dateEpochDay = dateEpoch,
                amountOrigMinor = 1_000_00L,
                currency = CurrencyCode.USD,
                exchangeRate = usdRate
            )

            // 100,000 YER to TR_MAIN_YER
            writer.postCapitalReceipt(
                targetAccountCode = AccountConstants.CAPITAL,
                partnerPartyId = partnerId,
                treasuryId = "TR_MAIN_YER",
                fiscalYear = 2026,
                dateEpochDay = dateEpoch,
                amountOrigMinor = 100_000_00L,
                currency = CurrencyCode.YER,
                exchangeRate = ExchangeRate.parity(CurrencyCode.YER)
            )

            val trialBalance = statementsUseCase.generateDualCurrencyTrialBalance(dateEpoch)
            assertTrue("Trial balance must be in equilibrium", trialBalance.isBalanced)
            assertEquals(trialBalance.totalBaseDebitMinor, trialBalance.totalBaseCreditMinor)

            // Verify dual-currency cash vault account row
            val cashVaultRow = trialBalance.rows.first { it.accountCode == AccountConstants.CASH_VAULT }
            assertTrue(cashVaultRow.baseDebitMinor > 0)
            assertEquals(1_000_00L + 100_000_00L, cashVaultRow.origDebitMinor)

            invariants.verifyAll()
        }
    }

    /**
     * Test 39: Dual FX Disclosure in Profit & Loss Statement (IAS 21) (D.2)
     * - Income Statement accurately separates and reports:
     *   * Realized FX Gains (4901) and Losses (5901)
     *   * Unrealized FX Gains (4902) and Losses (5902)
     * - Net profit reflects the combined impact of operating profits and total net FX.
     */
    @Test
    fun test39_dualFxDisclosureInProfitAndLossStatement() {
        runBlocking {
            val partnerId = "PARTNER_D39"
            val customerId = "CUSTOMER_D39"
            db.partyDao().insertParty(PartyEntity(id = partnerId, name = "شريك د39", isPartner = true))
            db.partyDao().insertParty(PartyEntity(id = customerId, name = "عميل د39", isCustomer = true))

            val dateEpoch = LocalDate.of(2026, 10, 5).toEpochDay()
            val initialRate = ExchangeRate(CurrencyCode.USD, CurrencyCode.FUNCTIONAL, 530_000_000L)

            // 1. Initial USD capital: 1,000 USD @ 530 YER = 530,000 YER
            writer.postCapitalReceipt(
                targetAccountCode = AccountConstants.CAPITAL,
                partnerPartyId = partnerId,
                treasuryId = "TR_USD_VAULT",
                fiscalYear = 2026,
                dateEpochDay = dateEpoch,
                amountOrigMinor = 1_000_00L,
                currency = CurrencyCode.USD,
                exchangeRate = initialRate
            )

            // 2. Sales invoice: 100 USD @ 530 YER = 53,000 YER
            val inv = writer.postSalesInvoice(
                partyId = customerId,
                fiscalYear = 2026,
                dateEpochDay = dateEpoch,
                currency = CurrencyCode.USD,
                exchangeRate = initialRate,
                cardItems = listOf(com.example.data.ledger.SalesItemSpec("كروت بالدولار", 1, 100_00L)),
                serviceItems = emptyList()
            )

            // 3. Customer pays invoice at 540 YER rate -> Realized FX Gain of 100 * 10 = 1,000 YER (100,000 minor)
            val paymentRate = ExchangeRate(CurrencyCode.USD, CurrencyCode.FUNCTIONAL, 540_000_000L)
            writer.postCustomerReceipt(
                partyId = customerId,
                treasuryId = "TR_USD_VAULT",
                fiscalYear = 2026,
                dateEpochDay = dateEpoch + 1,
                amountOrigMinor = 100_00L,
                currency = CurrencyCode.USD,
                exchangeRate = paymentRate,
                allocations = listOf(com.example.data.ledger.InvoiceAllocationSpec(inv.id, 100_00L))
            )

            // 4. Period-end revaluation of TR_USD_VAULT at 600 YER:
            // Vault has 1,100 USD.
            // Book balance: 530,000 + 54,000 = 584,000 YER.
            // Market value: 1,100 * 600 = 660,000 YER.
            // Unrealized FX Gain: 660,000 - 584,000 = 76,000 YER (7,600,000 minor).
            val revalRate = ExchangeRate(CurrencyCode.USD, CurrencyCode.FUNCTIONAL, 600_000_000L)
            revaluationUseCase.revalueMonetaryItem(
                accountCode = AccountConstants.CASH_VAULT,
                treasuryId = "TR_USD_VAULT",
                fiscalYear = 2026,
                dateEpochDay = dateEpoch + 2,
                foreignCurrency = CurrencyCode.USD,
                exchangeRate = revalRate
            )

            // Generate Income Statement
            val pnl = statementsUseCase.generateIncomeStatement(dateEpoch, dateEpoch + 10)

            assertEquals("Card Revenue must be 53,000 YER", 53_000_00L, pnl.cardRevenueMinor)
            assertEquals("Realized FX Gain (4901) must be 1,000 YER", 1_000_00L, pnl.realizedFxGainMinor)
            assertEquals("Realized FX Loss (5901) must be 0", 0L, pnl.realizedFxLossMinor)
            assertEquals("Unrealized FX Gain (4902) must be 76,000 YER", 76_000_00L, pnl.unrealizedFxGainMinor)
            assertEquals("Unrealized FX Loss (5902) must be 0", 0L, pnl.unrealizedFxLossMinor)

            val expectedNetProfit = 53_000_00L + 1_000_00L + 76_000_00L
            assertEquals(expectedNetProfit, pnl.netProfitMinor)
            assertEquals(77_000_00L, pnl.totalFxNetMinor)

            invariants.verifyAll()
        }
    }

    /**
     * Test 40: Dual-Currency Balance Sheet Equilibrium & Revaluation Voiding Reversal (D.1 / D.2)
     * - Balance Sheet stays in perfect equilibrium before and after revaluation.
     * - Voiding the revaluation document cleanly generates a REVERSAL entry, restores original book YER balance,
     *   sets document status to VOIDED, and satisfies all ledger invariants.
     */
    @Test
    fun test40_balanceSheetEquilibriumAndRevaluationVoidingReversal() {
        runBlocking {
            val partnerId = "PARTNER_D40"
            db.partyDao().insertParty(PartyEntity(id = partnerId, name = "شريك د40", isPartner = true))

            val dateEpoch = LocalDate.of(2026, 10, 5).toEpochDay()
            val initialRate = ExchangeRate(CurrencyCode.USD, CurrencyCode.FUNCTIONAL, 530_000_000L)

            // 1,000 USD capital @ 530 YER = 530,000 YER
            writer.postCapitalReceipt(
                targetAccountCode = AccountConstants.CAPITAL,
                partnerPartyId = partnerId,
                treasuryId = "TR_USD_VAULT",
                fiscalYear = 2026,
                dateEpochDay = dateEpoch,
                amountOrigMinor = 1_000_00L,
                currency = CurrencyCode.USD,
                exchangeRate = initialRate
            )

            // Revalue at 600 YER (+70,000 YER gain)
            val higherRate = ExchangeRate(CurrencyCode.USD, CurrencyCode.FUNCTIONAL, 600_000_000L)
            val revalDoc = revaluationUseCase.revalueMonetaryItem(
                accountCode = AccountConstants.CASH_VAULT,
                treasuryId = "TR_USD_VAULT",
                fiscalYear = 2026,
                dateEpochDay = dateEpoch + 1,
                foreignCurrency = CurrencyCode.USD,
                exchangeRate = higherRate
            )
            assertNotNull(revalDoc)

            // Balance sheet must be balanced after revaluation
            val bsAfterReval = statementsUseCase.generateBalanceSheet(dateEpoch + 2)
            assertTrue("Balance sheet must be balanced after revaluation", bsAfterReval.isBalanced)
            assertEquals(600_000_00L, bsAfterReval.totalAssetsMinor)
            assertEquals(600_000_00L, bsAfterReval.totalLiabilitiesAndEquityMinor)

            // Now void the revaluation document
            val voidSuccess = writer.voidDocument(
                docId = revalDoc!!.id,
                reversalDateEpochDay = dateEpoch + 3,
                reason = "إلغاء قيد إعادة التقييم لخطأ في سعر الصرف المعتمد"
            )
            assertTrue("Voiding revaluation document must succeed", voidSuccess)

            val updatedDoc = db.documentDao().getDocumentById(revalDoc.id)
            assertEquals(DocumentStatus.VOIDED.name, updatedDoc?.status)

            // Treasury book balance must revert back to 530,000 YER
            val bookYerReverted = db.journalDao().getNetDebitBalanceForTreasury("TR_USD_VAULT")
            assertEquals(530_000_00L, bookYerReverted)

            // Reversal entry created
            val entries = db.journalDao().getEntriesForDocument(revalDoc.id)
            assertEquals(2, entries.size)
            assertTrue(entries.any { it.type == "REVERSAL" })

            // Balance sheet remains balanced after voiding
            val bsAfterVoid = statementsUseCase.generateBalanceSheet(dateEpoch + 4)
            assertTrue("Balance sheet must remain balanced after voiding", bsAfterVoid.isBalanced)
            assertEquals(530_000_00L, bsAfterVoid.totalAssetsMinor)
            assertEquals(530_000_00L, bsAfterVoid.totalLiabilitiesAndEquityMinor)

            // Comprehensive ledger invariant audit passes cleanly
            invariants.verifyAll()
        }
    }
}
