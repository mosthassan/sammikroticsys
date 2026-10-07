package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.ledger.AccountConstants
import com.example.core.ledger.DocumentType
import com.example.core.model.CurrencyCode
import com.example.core.model.ExchangeRate
import com.example.core.model.Money
import com.example.data.ledger.InvoiceAllocationSpec
import com.example.data.ledger.LedgerInvariants
import com.example.data.ledger.LedgerWriter
import com.example.data.ledger.PurchaseItemSpec
import com.example.data.ledger.SalesItemSpec
import com.example.data.local.AppDatabase
import com.example.data.local.entity.FiscalPeriodEntity
import com.example.data.local.entity.PartyEntity
import com.example.domain.usecase.BackupRestoreUseCase
import com.example.domain.usecase.FinancialStatementsUseCase
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuditDefectsFixIntegrationTest {

    private lateinit var db: AppDatabase
    private lateinit var writer: LedgerWriter
    private lateinit var invariants: LedgerInvariants
    private lateinit var statementsUseCase: FinancialStatementsUseCase
    private lateinit var backupRestoreUseCase: BackupRestoreUseCase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.createInMemory(context)
        writer = LedgerWriter(db, enableInvariantValidation = true)
        invariants = LedgerInvariants(db)
        statementsUseCase = FinancialStatementsUseCase(db)
        backupRestoreUseCase = BackupRestoreUseCase(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testExactDateInClosedPeriodIsRejected() = runBlocking {
        // Test December 31, 2025
        val dec31 = LocalDate.of(2025, 12, 31)
        db.fiscalPeriodDao().insertPeriod(
            FiscalPeriodEntity(
                id = "FP_2025_12",
                year = 2025,
                month = 12,
                isClosed = true,
                closedAt = System.currentTimeMillis()
            )
        )

        try {
            writer.postSalesInvoice(
                partyId = AppDatabase.WALK_IN_CASH_PARTY_ID,
                fiscalYear = 2025,
                dateEpochDay = dec31.toEpochDay(),
                currency = CurrencyCode.YER,
                exchangeRate = ExchangeRate.parity(CurrencyCode.YER),
                cardItems = listOf(SalesItemSpec("Test Card", 1, 100000L)),
                serviceItems = emptyList()
            )
            fail("Expected exception when posting on 2025-12-31 in closed period")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("Fiscal period 2025/12 is closed") == true)
        }

        // Test January 10, 2026
        val jan10 = LocalDate.of(2026, 1, 10)
        db.fiscalPeriodDao().insertPeriod(
            FiscalPeriodEntity(
                id = "FP_2026_01",
                year = 2026,
                month = 1,
                isClosed = true,
                closedAt = System.currentTimeMillis()
            )
        )

        try {
            writer.postSalesInvoice(
                partyId = AppDatabase.WALK_IN_CASH_PARTY_ID,
                fiscalYear = 2026,
                dateEpochDay = jan10.toEpochDay(),
                currency = CurrencyCode.YER,
                exchangeRate = ExchangeRate.parity(CurrencyCode.YER),
                cardItems = listOf(SalesItemSpec("Test Card", 1, 100000L)),
                serviceItems = emptyList()
            )
            fail("Expected exception when posting on 2026-01-10 in closed period")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("Fiscal period 2026/1 is closed") == true)
        }
    }

    @Test
    fun testBalanceSheetHistoricalDateFiltering() = runBlocking {
        val day100 = LocalDate.of(2026, 4, 10).toEpochDay()
        val day200 = LocalDate.of(2026, 7, 19).toEpochDay()

        // Sale 1 on Day 100
        writer.postSalesInvoice(
            partyId = AppDatabase.WALK_IN_CASH_PARTY_ID,
            fiscalYear = 2026,
            dateEpochDay = day100,
            currency = CurrencyCode.YER,
            exchangeRate = ExchangeRate.parity(CurrencyCode.YER),
            cardItems = listOf(SalesItemSpec("Card 1", 1, 100000L)),
            serviceItems = emptyList()
        )

        // Sale 2 on Day 200
        writer.postSalesInvoice(
            partyId = AppDatabase.WALK_IN_CASH_PARTY_ID,
            fiscalYear = 2026,
            dateEpochDay = day200,
            currency = CurrencyCode.YER,
            exchangeRate = ExchangeRate.parity(CurrencyCode.YER),
            cardItems = listOf(SalesItemSpec("Card 2", 1, 50000L)),
            serviceItems = emptyList()
        )

        // As of Day 150: Day 200 must be completely excluded
        val day150 = LocalDate.of(2026, 5, 30).toEpochDay()
        val bsDay150 = statementsUseCase.generateBalanceSheet(asOfDateEpochDay = day150)

        assertEquals("Day 150 must only show 100,000 YER in assets", 100000L, bsDay150.totalAssetsMinor)
        assertEquals("Day 150 must show exactly 100,000 YER current profit", 100000L, bsDay150.currentPeriodNetProfitMinor)
        assertTrue("Balance sheet must be balanced", bsDay150.isBalanced)

        // As of Day 250: Both sales must be included
        val day250 = LocalDate.of(2026, 9, 7).toEpochDay()
        val bsDay250 = statementsUseCase.generateBalanceSheet(asOfDateEpochDay = day250)
        assertEquals("Day 250 must show all 150,000 YER in assets", 150000L, bsDay250.totalAssetsMinor)
        assertEquals("Day 250 must show 150,000 YER profit", 150000L, bsDay250.currentPeriodNetProfitMinor)
        assertTrue("Balance sheet must be balanced", bsDay250.isBalanced)
    }

    @Test
    fun testYearEndClosingClosesAll12PeriodsAndMaintainsBalanceSheet() = runBlocking {
        val yr2025Date = LocalDate.of(2025, 6, 15).toEpochDay()
        writer.postSalesInvoice(
            partyId = AppDatabase.WALK_IN_CASH_PARTY_ID,
            fiscalYear = 2025,
            dateEpochDay = yr2025Date,
            currency = CurrencyCode.YER,
            exchangeRate = ExchangeRate.parity(CurrencyCode.YER),
            cardItems = listOf(SalesItemSpec("Year 2025 Cards", 1, 500000L)),
            serviceItems = emptyList()
        )

        // Execute Year-End Closing on Dec 31, 2025
        val closingDate = LocalDate.of(2025, 12, 31).toEpochDay()
        writer.executeYearEndClosing(
            fiscalYear = 2025,
            closingDateEpochDay = closingDate,
            memo = "Annual Closing 2025"
        )

        // Assert all 12 periods of 2025 are closed
        for (m in 1..12) {
            val period = db.fiscalPeriodDao().getPeriod(2025, m)
            assertNotNull("Period 2025/$m must exist", period)
            assertTrue("Period 2025/$m must be closed", period!!.isClosed)
        }

        // Post a sale in 2026
        val yr2026Date = LocalDate.of(2026, 2, 1).toEpochDay()
        writer.postSalesInvoice(
            partyId = AppDatabase.WALK_IN_CASH_PARTY_ID,
            fiscalYear = 2026,
            dateEpochDay = yr2026Date,
            currency = CurrencyCode.YER,
            exchangeRate = ExchangeRate.parity(CurrencyCode.YER),
            cardItems = listOf(SalesItemSpec("Year 2026 Cards", 1, 100000L)),
            serviceItems = emptyList()
        )

        // Check Balance Sheet as of Feb 28, 2026
        val asOf2026 = LocalDate.of(2026, 2, 28).toEpochDay()
        val bs = statementsUseCase.generateBalanceSheet(asOfDateEpochDay = asOf2026)

        assertEquals("Total Assets = 500,000 (from 2025) + 100,000 (from 2026)", 600000L, bs.totalAssetsMinor)
        assertEquals("Retained earnings should contain the closed 2025 profit", 500000L, bs.retainedEarningsMinor)
        assertEquals("Current period net profit should only contain 2026 profit", 100000L, bs.currentPeriodNetProfitMinor)
        assertEquals("Total Equity should be 600,000 without doubling", 600000L, bs.totalEquityMinor)
        assertTrue("Balance sheet must be perfectly balanced after closing", bs.isBalanced)
    }

    @Test
    fun testSecureBackupAndRestoreWithQuotesAndSequences() = runBlocking {
        // Customer name with apostrophe
        val partyId = "PARTY_QUOTE_1"
        val partyName = "محل ال'أمانة للاتصالات O'Connor"
        db.partyDao().insertParty(
            PartyEntity(
                id = partyId,
                name = partyName,
                isCustomer = true
            )
        )

        // Post 2 invoices to advance sequence
        val doc1 = writer.postSalesInvoice(
            partyId = partyId,
            fiscalYear = 2026,
            dateEpochDay = LocalDate.of(2026, 1, 1).toEpochDay(),
            currency = CurrencyCode.YER,
            exchangeRate = ExchangeRate.parity(CurrencyCode.YER),
            cardItems = listOf(SalesItemSpec("Card Batch", 10, 5000L)),
            serviceItems = emptyList()
        )
        assertEquals(1L, doc1.docNumber)

        val doc2 = writer.postSalesInvoice(
            partyId = partyId,
            fiscalYear = 2026,
            dateEpochDay = LocalDate.of(2026, 1, 2).toEpochDay(),
            currency = CurrencyCode.YER,
            exchangeRate = ExchangeRate.parity(CurrencyCode.YER),
            cardItems = listOf(SalesItemSpec("Card Batch 2", 10, 5000L)),
            serviceItems = emptyList()
        )
        assertEquals(2L, doc2.docNumber)

        // Export JSON
        val exportedJson = backupRestoreUseCase.exportDatabaseToJson()
        assertTrue(exportedJson.contains("sha256"))
        assertTrue(exportedJson.contains(partyName))

        // Create fresh database
        val context = ApplicationProvider.getApplicationContext<Context>()
        val newDb = AppDatabase.createInMemory(context)
        val newRestore = BackupRestoreUseCase(newDb)
        val newWriter = LedgerWriter(newDb, enableInvariantValidation = true)

        // Restore into new database
        val restoreResult = newRestore.restoreDatabaseFromJson(exportedJson)
        assertTrue("Restore must succeed: ${restoreResult.exceptionOrNull()?.message}\n${restoreResult.exceptionOrNull()?.stackTraceToString()}", restoreResult.isSuccess)

        // Assert party restored with exact single quotes intact
        val restoredParty = newDb.partyDao().getPartyById(partyId)
        assertNotNull(restoredParty)
        assertEquals(partyName, restoredParty!!.name)

        // Post a third invoice in the restored database: must get docNumber = 3 without sequence collision!
        val doc3 = newWriter.postSalesInvoice(
            partyId = partyId,
            fiscalYear = 2026,
            dateEpochDay = LocalDate.of(2026, 1, 3).toEpochDay(),
            currency = CurrencyCode.YER,
            exchangeRate = ExchangeRate.parity(CurrencyCode.YER),
            cardItems = listOf(SalesItemSpec("Card Batch 3", 10, 5000L)),
            serviceItems = emptyList()
        )
        assertEquals(3L, doc3.docNumber)
        newDb.close()
    }

    @Test
    fun testVoidPurchaseInvoiceDisposesAsset() = runBlocking {
        val vendorId = "VENDOR_1"
        db.partyDao().insertParty(PartyEntity(id = vendorId, name = "مورد أجهزة الشبكة", isVendor = true))

        val purchaseDoc = writer.postPurchaseInvoice(
            vendorPartyId = vendorId,
            fiscalYear = 2026,
            dateEpochDay = LocalDate.of(2026, 1, 1).toEpochDay(),
            currency = CurrencyCode.YER,
            exchangeRate = ExchangeRate.parity(CurrencyCode.YER),
            items = listOf(
                PurchaseItemSpec(
                    description = "راوتر سيرفر رئيسي CCR",
                    accountCode = AccountConstants.FIXED_ASSETS_NETWORK,
                    quantity = 1,
                    unitPriceMinor = 15000000L,
                    isAsset = true,
                    usefulLifeMonths = 36
                )
            )
        )

        // Asset must be active
        val assetsBefore = db.assetDao().getAllActiveAssetsSync()
        assertEquals(1, assetsBefore.size)
        assertFalse(assetsBefore[0].isDisposed)

        // Void the purchase document
        val voided = writer.voidDocument(purchaseDoc.id, LocalDate.of(2026, 1, 2).toEpochDay(), "Wrong purchase order")
        assertTrue(voided)

        // Active assets must now be 0, and the asset disposed flag must be true
        val activeAssetsAfter = db.assetDao().getAllActiveAssetsSync()
        assertEquals(0, activeAssetsAfter.size)

        val allAssets = db.assetDao().getAllAssetsSync()
        assertTrue(allAssets[0].isDisposed)
    }

    @Test
    fun testAllocationValidationRejectsInvalidAmounts() = runBlocking {
        val customerId = "CUSTOMER_1"
        db.partyDao().insertParty(PartyEntity(id = customerId, name = "وكيل حي الأمل", isCustomer = true))

        // Invoice of 50,000 YER
        val invoice = writer.postSalesInvoice(
            partyId = customerId,
            fiscalYear = 2026,
            dateEpochDay = LocalDate.of(2026, 1, 1).toEpochDay(),
            currency = CurrencyCode.YER,
            exchangeRate = ExchangeRate.parity(CurrencyCode.YER),
            cardItems = listOf(SalesItemSpec("Cards", 1, 50000L)),
            serviceItems = emptyList()
        )

        // Attempt receipt with allocated amount > receipt total
        try {
            writer.postCustomerReceipt(
                partyId = customerId,
                treasuryId = "TR_MAIN_YER",
                fiscalYear = 2026,
                dateEpochDay = LocalDate.of(2026, 1, 2).toEpochDay(),
                amountOrigMinor = 30000L,
                currency = CurrencyCode.YER,
                exchangeRate = ExchangeRate.parity(CurrencyCode.YER),
                allocations = listOf(InvoiceAllocationSpec(invoice.id, 40000L))
            )
            fail("Expected exception: allocation exceeds receipt amount")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("exceeds receipt amount") == true)
        }

        // Attempt receipt with allocation > invoice remaining balance (invoice total is 50k, allocating 60k with 60k cash)
        try {
            writer.postCustomerReceipt(
                partyId = customerId,
                treasuryId = "TR_MAIN_YER",
                fiscalYear = 2026,
                dateEpochDay = LocalDate.of(2026, 1, 2).toEpochDay(),
                amountOrigMinor = 60000L,
                currency = CurrencyCode.YER,
                exchangeRate = ExchangeRate.parity(CurrencyCode.YER),
                allocations = listOf(InvoiceAllocationSpec(invoice.id, 60000L))
            )
            fail("Expected exception: allocation exceeds invoice balance")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("exceeds remaining invoice balance") == true)
        }
    }

    @Test
    fun testMoneyParseArabicDecimal() {
        // Arabic decimal comma ٫
        val m1 = Money.parseFromUserInput("١٫٥", CurrencyCode.YER)
        assertNotNull(m1)
        assertEquals("١٫٥ must equal 150 minor units (1.50)", 150L, m1!!.minor)

        // Arabic thousands separator ٬ with dot decimal
        val m2 = Money.parseFromUserInput("١٬٥٠٠.٢٥", CurrencyCode.YER)
        assertNotNull(m2)
        assertEquals(150025L, m2!!.minor)

        // Disallow more than 2 decimal digits without warning
        val m3 = Money.parseFromUserInput("15.123", CurrencyCode.YER)
        assertEquals(null, m3)
    }
}
