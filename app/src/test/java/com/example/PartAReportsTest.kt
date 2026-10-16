package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.ledger.AccountConstants
import com.example.core.ledger.JournalDraft
import com.example.core.ledger.JournalDraftLine
import com.example.core.ledger.JournalEntryType
import com.example.core.model.CurrencyCode
import com.example.core.model.ExchangeRate
import com.example.core.model.Money
import com.example.core.model.RateZone
import com.example.core.model.UuidUtils
import com.example.data.ledger.LedgerWriter
import com.example.data.local.AppDatabase
import com.example.data.local.entity.CurrencyRateEntity
import com.example.data.local.entity.FiscalPeriodEntity
import com.example.data.local.entity.PartyEntity
import com.example.data.local.entity.TreasuryEntity
import com.example.domain.report.ActivityReportsService
import com.example.domain.report.AuditReportsService
import com.example.domain.report.PartnerSummaryResult
import com.example.domain.report.ReportsEngine
import com.example.domain.usecase.ExchangeRateResolver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PartAReportsTest {

    private lateinit var db: AppDatabase
    private lateinit var writer: LedgerWriter
    private lateinit var resolver: ExchangeRateResolver
    private lateinit var activityReports: ActivityReportsService
    private lateinit var auditReports: AuditReportsService
    private lateinit var reportsEngine: ReportsEngine

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.createInMemory(context)
        AppDatabase.installTriggers(db.openHelper.writableDatabase)
        AppDatabase.seedDefaultData(db.openHelper.writableDatabase)

        writer = LedgerWriter(db, enableInvariantValidation = false)
        resolver = ExchangeRateResolver(db)
        activityReports = ActivityReportsService(db, resolver)
        auditReports = AuditReportsService(db)
        reportsEngine = ReportsEngine(db, resolver, activityReports, auditReports)

        runBlocking {
            db.fiscalPeriodDao().insertPeriod(
                FiscalPeriodEntity(
                    id = "FP_2026_10",
                    year = 2026,
                    month = 10,
                    startDateEpochDay = 19990L,
                    endDateEpochDay = 20020L,
                    isClosed = false
                )
            )
        }
    }

    /**
     * Test 1: Run reports on Fixture; no string in notes/subtitles/footers contains
     * 7 or more consecutive digits without commas/formatting (e.g., raw minor units).
     */
    @Test
    fun testNoRawUnformattedMinorAmountsInReportStrings() = runBlocking {
        val today = 20000L
        val currencyReport = activityReports.generateCurrencyPositionsReport(today, RateZone.SANAA)
        val sevenDigitRegex = Regex("""\b\d{7,}\b""")

        currencyReport.positions.forEach { pos ->
            val desc = "${pos.treasuryName} ${pos.currency.name}"
            assertFalse("Description must not contain raw 7-digit numbers: $desc", sevenDigitRegex.containsMatchIn(desc))
        }

        currencyReport.footer.checks.forEach { check ->
            assertFalse("Check note must not contain raw 7-digit numbers: ${check.note}", sevenDigitRegex.containsMatchIn(check.note))
        }
    }

    /**
     * Test 2: Treasury with 1,000 USD at book value 530,000.00 YER and market rate 540 YER/USD:
     * Market value = 540,000.00 YER, Unrealized diff = +10,000.00 YER.
     */
    @Test
    fun testTreasuryPositionWithValidRateCalculatesMarketValueAndDiff() = runBlocking {
        val asOf = 20000L
        val treasuryId = "TR_USD_MAIN"
        db.treasuryDao().insertTreasury(
            TreasuryEntity(
                id = treasuryId,
                name = "صندوق النقدية بالدولار",
                currency = "USD",
                glAccountCode = "1101",
                isActive = true
            )
        )

        // Post opening deposit: 1,000 USD @ 530 = 530,000 YER (53,000,000 minor)
        val entry = JournalDraft(
            type = JournalEntryType.NORMAL,
            entryDateEpochDay = asOf,
            memo = "إيداع افتتاحي بالدولار",
            lines = listOf(
                JournalDraftLine(
                    lineNo = 1,
                    accountCode = "1101",
                    treasuryId = treasuryId,
                    partyId = null,
                    currency = CurrencyCode.USD,
                    exchangeRateMicros = 530_000_000L,
                    origMinor = 100_000L, // $1,000.00
                    baseDebitMinor = 53_000_000L, // 530,000.00 YER
                    baseCreditMinor = 0L,
                    memo = "إيداع صندوق دولار"
                ),
                JournalDraftLine(
                    lineNo = 2,
                    accountCode = "3101",
                    treasuryId = null,
                    partyId = null,
                    currency = CurrencyCode.FUNCTIONAL,
                    exchangeRateMicros = 1_000_000L,
                    origMinor = 53_000_000L,
                    baseDebitMinor = 0L,
                    baseCreditMinor = 53_000_000L,
                    memo = "رأس مال الشركاء"
                )
            )
        )
        writer.postJournalEntry(entry)

        // Seed closing market rate at 540 YER/USD
        db.currencyRateDao().insertRate(
            CurrencyRateEntity(
                id = UuidUtils.newTimeOrderedId(),
                currency = "USD",
                rateMicros = 540_000_000L,
                effectiveDateEpochDay = asOf,
                zone = RateZone.SANAA.name
            )
        )

        val report = activityReports.generateCurrencyPositionsReport(asOf, RateZone.SANAA)
        val usdPosition = report.positions.firstOrNull { it.treasuryId == treasuryId }

        assertNotNull("USD Treasury position must exist", usdPosition)
        assertEquals(100_000L, usdPosition!!.origBalanceMinor)
        assertEquals(53_000_000L, usdPosition.bookValueBaseMinor)
        assertTrue(usdPosition.hasRate)
        assertNotNull(usdPosition.marketValueBaseMinor)
        assertEquals(54_000_000L, usdPosition.marketValueBaseMinor) // 540,000.00 YER
        assertEquals(1_000_000L, usdPosition.unrealizedDiffMinor) // +10,000.00 YER
    }

    /**
     * Test 3: Same treasury without recorded rate -> "No Rate" state, no equivalent/diff,
     * not included in totals, alert triggered.
     */
    @Test
    fun testTreasuryPositionWithoutRateDisplaysNoRateAndOmitsFromTotals() = runBlocking {
        val asOf = 20000L
        val treasuryId = "TR_USD_NO_RATE"
        db.treasuryDao().insertTreasury(
            TreasuryEntity(
                id = treasuryId,
                name = "خزينة دولار بدون تسعيرة",
                currency = "USD",
                glAccountCode = "1101",
                isActive = true
            )
        )

        // Post 500 USD @ 530
        val entry = JournalDraft(
            type = JournalEntryType.NORMAL,
            entryDateEpochDay = asOf,
            memo = "إيداع دولار",
            lines = listOf(
                JournalDraftLine(
                    lineNo = 1,
                    accountCode = "1101",
                    treasuryId = treasuryId,
                    partyId = null,
                    currency = CurrencyCode.USD,
                    exchangeRateMicros = 530_000_000L,
                    origMinor = 50_000L,
                    baseDebitMinor = 26_500_000L,
                    baseCreditMinor = 0L,
                    memo = "إيداع"
                ),
                JournalDraftLine(
                    lineNo = 2,
                    accountCode = "3101",
                    treasuryId = null,
                    partyId = null,
                    currency = CurrencyCode.FUNCTIONAL,
                    exchangeRateMicros = 1_000_000L,
                    origMinor = 26_500_000L,
                    baseDebitMinor = 0L,
                    baseCreditMinor = 26_500_000L,
                    memo = "رأس مال"
                )
            )
        )
        writer.postJournalEntry(entry)

        // No rate inserted for USD in SANAA zone
        val report = activityReports.generateCurrencyPositionsReport(asOf, RateZone.SANAA)
        val pos = report.positions.firstOrNull { it.treasuryId == treasuryId }

        assertNotNull(pos)
        assertFalse("Position should have hasRate = false", pos!!.hasRate)
        assertNull("Market value must be null when no rate exists", pos.marketValueBaseMinor)
        assertNull("Unrealized diff must be null when no rate exists", pos.unrealizedDiffMinor)
        assertTrue("Report must indicate missing rates", report.hasMissingRates)
        assertEquals("Market value total must omit unrated positions", 0L, report.totalMarketValueBaseMinor)
        assertEquals("Unrealized diff total must omit unrated positions", 0L, report.totalUnrealizedDiffMinor)
    }

    /**
     * Test 4: Multi-zone rate resolution: ADEN zone uses ADEN rate when facility zone is ADEN.
     */
    @Test
    fun testCurrencyPositionsUsesFacilityPrimaryRateZone() = runBlocking {
        val asOf = 20000L
        val treasuryId = "TR_USD_ADEN"
        db.treasuryDao().insertTreasury(
            TreasuryEntity(
                id = treasuryId,
                name = "صندوق عدن",
                currency = "USD",
                glAccountCode = "1101",
                isActive = true
            )
        )

        val entry = JournalDraft(
            type = JournalEntryType.NORMAL,
            entryDateEpochDay = asOf,
            memo = "إيداع عدن",
            lines = listOf(
                JournalDraftLine(
                    lineNo = 1,
                    accountCode = "1101",
                    treasuryId = treasuryId,
                    partyId = null,
                    currency = CurrencyCode.USD,
                    exchangeRateMicros = 1_500_000_000L,
                    origMinor = 100_000L, // $1,000
                    baseDebitMinor = 150_000_000L,
                    baseCreditMinor = 0L,
                    memo = "إيداع عدن"
                ),
                JournalDraftLine(
                    lineNo = 2,
                    accountCode = "3101",
                    treasuryId = null,
                    partyId = null,
                    currency = CurrencyCode.FUNCTIONAL,
                    exchangeRateMicros = 1_000_000L,
                    origMinor = 150_000_000L,
                    baseDebitMinor = 0L,
                    baseCreditMinor = 150_000_000L,
                    memo = "رأس مال"
                )
            )
        )
        writer.postJournalEntry(entry)

        // Seed SANAA = 535, ADEN = 1600
        db.currencyRateDao().insertRate(
            CurrencyRateEntity(
                id = UuidUtils.newTimeOrderedId(),
                currency = "USD",
                rateMicros = 535_000_000L,
                effectiveDateEpochDay = asOf,
                zone = RateZone.SANAA.name
            )
        )
        db.currencyRateDao().insertRate(
            CurrencyRateEntity(
                id = UuidUtils.newTimeOrderedId(),
                currency = "USD",
                rateMicros = 1_600_000_000L,
                effectiveDateEpochDay = asOf,
                zone = RateZone.ADEN.name
            )
        )

        val reportAden = activityReports.generateCurrencyPositionsReport(asOf, RateZone.ADEN)
        val posAden = reportAden.positions.first { it.treasuryId == treasuryId }
        assertEquals(1_600_000_000L, posAden.exchangeRate?.rateMicros)
        assertEquals(160_000_000L, posAden.marketValueBaseMinor) // $1,000 * 1,600 = 160,000,000 minor
    }

    /**
     * Test 5: Entry posted after asOf date does not appear in balance.
     */
    @Test
    fun testTransactionsAfterAsOfDateAreExcludedFromReport() = runBlocking {
        val asOf = 20000L
        val futureDate = 20005L
        val treasuryId = "TR_DATE_BOUND"
        db.treasuryDao().insertTreasury(
            TreasuryEntity(
                id = treasuryId,
                name = "صندوق التقييد الزمني",
                currency = "YER",
                glAccountCode = "1101",
                isActive = true
            )
        )

        // Entry within asOf (Day 20000): 50,000 YER
        writer.postJournalEntry(
            JournalDraft(
                type = JournalEntryType.NORMAL,
                entryDateEpochDay = asOf,
                memo = "قيد حالي",
                lines = listOf(
                    JournalDraftLine(1, "1101", null, treasuryId, 5_000_000L, CurrencyCode.FUNCTIONAL, 1_000_000L, 5_000_000L, 0L, "حالي"),
                    JournalDraftLine(2, "3101", null, null, 5_000_000L, CurrencyCode.FUNCTIONAL, 1_000_000L, 0L, 5_000_000L, "رأس مال")
                )
            )
        )

        // Entry after asOf (Day 20005): 30,000 YER
        writer.postJournalEntry(
            JournalDraft(
                type = JournalEntryType.NORMAL,
                entryDateEpochDay = futureDate,
                memo = "قيد مستقبلي",
                lines = listOf(
                    JournalDraftLine(1, "1101", null, treasuryId, 3_000_000L, CurrencyCode.FUNCTIONAL, 1_000_000L, 3_000_000L, 0L, "مستقبلي"),
                    JournalDraftLine(2, "3101", null, null, 3_000_000L, CurrencyCode.FUNCTIONAL, 1_000_000L, 0L, 3_000_000L, "رأس مال")
                )
            )
        )

        val reportAsOf = activityReports.generateCurrencyPositionsReport(asOf, RateZone.SANAA)
        val pos = reportAsOf.positions.first { it.treasuryId == treasuryId }

        assertEquals("Balance as of day 20000 must exclude future entries", 5_000_000L, pos.origBalanceMinor)
        assertEquals("Book value as of day 20000 must exclude future entries", 5_000_000L, pos.bookValueBaseMinor)
    }

    /**
     * Test 6: Creating 3201 line without partyId -> reconciled = false and failing check name noted.
     */
    @Test
    fun testOrphanedPartnerLineFailsReconciliation() = runBlocking {
        val asOf = 20000L
        // Insert invalid line directly into journal_lines for 3201 without partyId
        val entryId = UuidUtils.newTimeOrderedId()
        db.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO journal_entries (id, fiscalYear, periodMonth, entryDateEpochDay, docType, docNumber, memo, createdAt)
            VALUES ('$entryId', 2026, 10, $asOf, 'JV', 99, 'قيد شريك غير صالح', 1000)
            """
        )
        db.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO journal_lines (id, entryId, lineIndex, accountCode, partyId, treasuryId, currency, exchangeRateMicros, origMinor, baseDebitMinor, baseCreditMinor, memo)
            VALUES ('${UuidUtils.newTimeOrderedId()}', '$entryId', 1, '3201', NULL, NULL, 'YER', 1000000, 100000, 100000, 0, 'شريك بلا معرّف')
            """
        )
        db.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO journal_lines (id, entryId, lineIndex, accountCode, partyId, treasuryId, currency, exchangeRateMicros, origMinor, baseDebitMinor, baseCreditMinor, memo)
            VALUES ('${UuidUtils.newTimeOrderedId()}', '$entryId', 2, '1101', NULL, NULL, 'YER', 1000000, 100000, 0, 100000, 'سداد نقدي')
            """
        )

        val auditReport = auditReports.generateAuditReport(asOf)
        assertFalse("Audit report must NOT be reconciled when orphaned 3201 line exists", auditReport.footer.reconciled)

        val partnerCheck = auditReport.footer.checks.firstOrNull { it.name.contains("3201") }
        assertNotNull("Partner check for 3201 must exist", partnerCheck)
        assertFalse("Partner check must fail", partnerCheck!!.passed)
    }

    /**
     * Test 7: On clean balanced fixture -> all checks passed for all reports.
     */
    @Test
    fun testCleanFixtureReconcilesAllChecks() = runBlocking {
        val asOf = 20000L
        val partnerId = "PARTNER_1"
        db.partyDao().insertParty(
            PartyEntity(id = partnerId, name = "شريك مؤسس", phone = "770000000", isPartner = true)
        )

        // Seed balanced transactions
        writer.postJournalEntry(
            JournalDraft(
                type = JournalEntryType.NORMAL,
                entryDateEpochDay = asOf,
                memo = "قيد سليم متزن",
                lines = listOf(
                    JournalDraftLine(1, "1101", null, null, 10_000_000L, CurrencyCode.FUNCTIONAL, 1_000_000L, 10_000_000L, 0L, "صندوق"),
                    JournalDraftLine(2, "3101", partnerId, null, 10_000_000L, CurrencyCode.FUNCTIONAL, 1_000_000L, 0L, 10_000_000L, "رأس مال")
                )
            )
        )

        val auditReport = auditReports.generateAuditReport(asOf)
        assertTrue("All audit checks must pass on clean fixture", auditReport.footer.reconciled)
        assertTrue("Every check in footer must pass", auditReport.footer.checks.all { it.passed })
    }

    /**
     * Test 8: No partners exist -> returns explicit NoPartners state.
     */
    @Test
    fun testNoPartnersStateHandledExplicitly() = runBlocking {
        val asOf = 20000L
        val result = reportsEngine.generatePartnerSummaryReport(asOf)
        assertTrue("Result must be NoPartners state when no partners exist", result is PartnerSummaryResult.NoPartners)
    }
}
