package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.ledger.DocumentStatus
import com.example.core.model.CurrencyCode
import com.example.core.model.ExchangeRate
import com.example.core.model.RateZone
import com.example.core.time.AppClock
import com.example.core.time.FiscalCalendar
import com.example.core.time.FixedAppClock
import com.example.data.ledger.LedgerInvariants
import com.example.data.ledger.LedgerWriter
import com.example.data.ledger.SalesItemSpec
import com.example.data.local.AppDatabase
import com.example.data.local.entity.FiscalPeriodEntity
import com.example.data.local.entity.PartyEntity
import com.example.data.local.entity.TreasuryAccountEntity
import com.example.domain.usecase.FinancialStatementsUseCase
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AccountingCoreFixesTest {

    private lateinit var db: AppDatabase
    private lateinit var writer: LedgerWriter
    private lateinit var invariants: LedgerInvariants
    private lateinit var financialStatementsUseCase: FinancialStatementsUseCase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.createInMemory(context)
        AppDatabase.installTriggers(db.openHelper.writableDatabase)
        AppDatabase.seedDefaultData(db.openHelper.writableDatabase)

        writer = LedgerWriter(db, enableInvariantValidation = true)
        invariants = LedgerInvariants(db)
        financialStatementsUseCase = FinancialStatementsUseCase(db)

        // Seed 2026 fiscal periods
        runBlocking {
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
            // Seed a test customer
            db.partyDao().insertParty(
                PartyEntity(
                    id = "CUST_TEST_01",
                    name = "عميل تجريبي للأنظمة",
                    isCustomer = true
                )
            )
            // Ensure test cash treasury exists
            db.treasuryDao().insertTreasury(
                TreasuryAccountEntity(
                    id = "TR_TEST_CASH",
                    name = "خزينة تجريبية",
                    glAccountCode = "1101",
                    currency = "YER",
                    allowNegative = true
                )
            )
        }
    }

    @After
    fun tearDown() {
        db.close()
        AppClock.resetToDefault()
    }

    @Test
    fun testFiscalCalendarCalculations() {
        // Standard Calendar (starts Month 1)
        val standardCal = FiscalCalendar(1)
        val date2026_05_11 = LocalDate.of(2026, 5, 11).toEpochDay()
        assertEquals(2026, standardCal.fiscalYearOf(date2026_05_11))
        val (fy, periodMonth) = standardCal.periodOf(date2026_05_11)
        assertEquals(2026, fy)
        assertEquals(5, periodMonth)
        assertEquals(LocalDate.of(2026, 1, 1).toEpochDay(), standardCal.yearStart(2026))
        assertEquals(LocalDate.of(2026, 12, 31).toEpochDay(), standardCal.yearEnd(2026))

        // Non-standard fiscal year (starts Month 7)
        val midYearCal = FiscalCalendar(7)
        val date2026_08_01 = LocalDate.of(2026, 8, 1).toEpochDay()
        assertEquals(2026, midYearCal.fiscalYearOf(date2026_08_01))
        val (fyMid, periodMid) = midYearCal.periodOf(date2026_08_01)
        assertEquals(2026, fyMid)
        assertEquals(2, periodMid) // Month 8 is month 2 of FY starting in July

        val date2026_02_01 = LocalDate.of(2026, 2, 1).toEpochDay()
        assertEquals(2025, midYearCal.fiscalYearOf(date2026_02_01))
    }

    @Test
    fun testAppClockTimezoneAccuracy() {
        // Test with Fixed clock across Asia/Aden UTC+3 boundary
        // 2026-10-10 22:30:00 UTC = 2026-10-11 01:30:00 in Asia/Aden (+3)
        val instantNearMidnightUtc = Instant.parse("2026-10-10T22:30:00Z")
        val fixedClock = FixedAppClock(instantNearMidnightUtc, ZoneId.of("Asia/Aden"))
        AppClock.setClock(fixedClock)

        val adenDate = AppClock.todayLocalDate()
        assertEquals(2026, adenDate.year)
        assertEquals(10, adenDate.monthValue)
        assertEquals(11, adenDate.dayOfMonth)
    }

    @Test
    fun testPostingDoesNotCollideOnEntryNumberAcrossDocumentTypes(): Unit = runBlocking {
        val rate = ExchangeRate.parity(CurrencyCode.FUNCTIONAL)
        val today = LocalDate.of(2026, 5, 1).toEpochDay()

        // 1. Post Sales Invoice (gets docNumber 1)
        val inv = writer.postSalesInvoice(
            partyId = "CUST_TEST_01",
            fiscalYear = 2026,
            dateEpochDay = today,
            currency = CurrencyCode.FUNCTIONAL,
            exchangeRate = rate,
            cardItems = emptyList(),
            serviceItems = listOf(SalesItemSpec("اشتراك شهري", 1, 50_000L)),
            notes = "فاتورة أولى"
        )
        assertEquals(1L, inv.docNumber)

        // 2. Post Receipt Voucher (also gets docNumber 1 for its type sequence)
        val receipt = writer.postCustomerReceipt(
            partyId = "CUST_TEST_01",
            treasuryId = "TR_TEST_CASH",
            fiscalYear = 2026,
            dateEpochDay = today,
            amountOrigMinor = 50_000L,
            currency = CurrencyCode.FUNCTIONAL,
            exchangeRate = rate,
            notes = "سند قبض أول"
        )
        assertEquals(1L, receipt.docNumber)

        // 3. Void the Sales Invoice (generating a REVERSAL entry)
        val voidSuccess = writer.voidDocument(inv.id, today, "إلغاء لاختبار القيد العكسي")
        assertTrue(voidSuccess)

        // Verify all entries in database have distinct sequential entry numbers
        val allEntries = db.journalDao().getAllEntriesSync()
        val entryNumbers = allEntries.map { it.entryNumber }
        assertEquals(entryNumbers.size, entryNumbers.distinct().size)
    }

    @Test
    fun testSalesInvoiceAmendmentFlow(): Unit = runBlocking {
        val rate = ExchangeRate.parity(CurrencyCode.FUNCTIONAL)
        val date1 = LocalDate.of(2026, 5, 1).toEpochDay()
        val date2 = LocalDate.of(2026, 5, 2).toEpochDay()

        // 1. Post original invoice
        val origInv = writer.postSalesInvoice(
            partyId = "CUST_TEST_01",
            fiscalYear = 2026,
            dateEpochDay = date1,
            currency = CurrencyCode.FUNCTIONAL,
            exchangeRate = rate,
            cardItems = emptyList(),
            serviceItems = listOf(SalesItemSpec("خدمة أصلية", 1, 20_000L)),
            notes = "فاتورة قبل التعديل"
        )
        assertEquals(0, origInv.revision)
        assertEquals(DocumentStatus.POSTED.name, origInv.status)

        // 2. Amend invoice
        val amendedInv = writer.amendSalesInvoice(
            originalDocId = origInv.id,
            newDateEpochDay = date2,
            newExchangeRate = rate,
            newCardItems = emptyList(),
            newServiceItems = listOf(SalesItemSpec("خدمة معدلة", 1, 30_000L)),
            newNotes = "فاتورة بعد التعديل",
            reason = "تصحيح قيمة الخدمة بناء على طلب العميل"
        )

        // 3. Verify original document is VOIDED
        val reloadedOrig = db.documentDao().getDocumentById(origInv.id)!!
        assertEquals(DocumentStatus.VOIDED.name, reloadedOrig.status)

        // 4. Verify amended document
        assertEquals(1, amendedInv.revision)
        assertEquals(origInv.id, amendedInv.reversalOfDocId)
        assertEquals(DocumentStatus.POSTED.name, amendedInv.status)
        assertEquals(30_000L, amendedInv.totalMinor)

        // 5. Invariants must verify perfectly
        invariants.verifyAll()
    }

    @Test
    fun testYearEndClosingWithContraAccountsAndBalanceSheetEquilibrium(): Unit = runBlocking {
        val rate = ExchangeRate.parity(CurrencyCode.FUNCTIONAL)
        val date = LocalDate.of(2026, 6, 15).toEpochDay()
        val closingDate = FiscalCalendar.DEFAULT.yearEnd(2026)

        // Post revenue: 100,000 YER (CR 4103)
        writer.postSalesInvoice(
            partyId = "CUST_TEST_01",
            fiscalYear = 2026,
            dateEpochDay = date,
            currency = CurrencyCode.FUNCTIONAL,
            exchangeRate = rate,
            cardItems = emptyList(),
            serviceItems = listOf(SalesItemSpec("خدمات شهرية", 1, 100_000L)),
            notes = "إيرادات"
        )

        val preClosingBS = financialStatementsUseCase.generateBalanceSheet(closingDate)
        assertTrue("Pre-closing Balance sheet must balance", preClosingBS.isBalanced)
        assertEquals(100_000L, preClosingBS.unclosedProfitMinor)

        // Execute Year-End Closing
        val closingDoc = writer.executeYearEndClosing(2026, closingDate, "إقفال 2026")
        assertNotNull(closingDoc)

        // Post-closing unclosed profit must be exactly 0
        val postClosingBS = financialStatementsUseCase.generateBalanceSheet(closingDate)
        assertEquals(0L, postClosingBS.unclosedProfitMinor)
        assertEquals(100_000L, postClosingBS.retainedEarningsMinor)
        assertTrue("Post-closing Balance sheet must balance", postClosingBS.isBalanced)

        // All 12 periods must be closed
        val closedCount = db.fiscalPeriodDao().getClosedPeriods().filter { it.year == 2026 }.size
        assertEquals(12, closedCount)
    }
}
