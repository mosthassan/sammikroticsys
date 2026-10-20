package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.ledger.AccountConstants
import com.example.core.model.CurrencyCode
import com.example.core.model.ExchangeRate
import com.example.core.model.RateZone
import com.example.core.model.UuidUtils
import com.example.data.ledger.LedgerWriter
import com.example.data.local.AppDatabase
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.CurrencyRateEntity
import com.example.data.local.entity.FiscalPeriodEntity
import com.example.data.local.entity.PartyEntity
import com.example.data.local.entity.TreasuryAccountEntity
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

        // Seed core organization, walk-in party, and accounts without seeding default exchange rates
        db.openHelper.writableDatabase.execSQL("""
            INSERT OR IGNORE INTO organizations (id, name, taxNumber, functionalCurrency, fiscalYearStartMonth, isInitialized, primaryRateZone, equityShareMode, createdAt)
            VALUES ('DEFAULT_ORG', 'شبكة سام اللاسلكية', '', 'YER', 1, 1, 'SANAA', 'DERIVED_FROM_CAPITAL', strftime('%s','now') * 1000)
        """)
        db.openHelper.writableDatabase.execSQL("""
            INSERT OR IGNORE INTO parties (id, name, phone, isCustomer, isVendor, isPartner, equityPercentageBasisPoints, creditLimitMinor, isActive, createdAt)
            VALUES ('${AppDatabase.WALK_IN_CASH_PARTY_ID}', 'عميل نقدي (مبيعات فورية)', '', 1, 0, 0, 0, 0, 1, strftime('%s','now') * 1000)
        """)

        val accounts = listOf(
            AccountEntity(AccountConstants.CASH_VAULT, "النقدية بالصندوق", "ASSET", isDebitNormal = true, isLocked = true),
            AccountEntity(AccountConstants.BANKS_WALLETS, "البنوك والمحافظ الإلكترونية", "ASSET", isDebitNormal = true, isLocked = true),
            AccountEntity(AccountConstants.ACCOUNTS_RECEIVABLE, "العملاء والوكلاء", "ASSET", isDebitNormal = true, isLocked = true),
            AccountEntity(AccountConstants.CARD_INVENTORY_RESERVE, "مخزون الكروت (محجوز)", "ASSET", isDebitNormal = true, isLocked = true, isActive = false),
            AccountEntity(AccountConstants.FIXED_ASSETS_NETWORK, "أجهزة ومعدات الشبكة", "ASSET", isDebitNormal = true, isLocked = true),
            AccountEntity(AccountConstants.ACCUMULATED_DEPRECIATION, "مجمع الإهلاك", "ASSET", isDebitNormal = false, isLocked = true),
            AccountEntity(AccountConstants.ACCOUNTS_PAYABLE, "الموردون", "LIABILITY", isDebitNormal = false, isLocked = true),
            AccountEntity(AccountConstants.CAPITAL, "رأس المال", "EQUITY", isDebitNormal = false, isLocked = true),
            AccountEntity(AccountConstants.PARTNER_CURRENT, "جاري الشركاء", "EQUITY", isDebitNormal = false, isLocked = true),
            AccountEntity(AccountConstants.RETAINED_EARNINGS, "الأرباح المرحّلة", "EQUITY", isDebitNormal = false, isLocked = true),
            AccountEntity(AccountConstants.CARD_SALES_REVENUE, "إيرادات مبيعات الكروت", "REVENUE", isDebitNormal = false, isLocked = true),
            AccountEntity(AccountConstants.SALES_RETURNS, "مردودات ومسموحات المبيعات", "REVENUE", isDebitNormal = true, isLocked = true),
            AccountEntity(AccountConstants.DIRECT_SERVICE_REVENUE, "إيرادات الاشتراكات المباشرة والخدمات", "REVENUE", isDebitNormal = false, isLocked = true),
            AccountEntity(AccountConstants.REALIZED_FX_GAIN, "أرباح فروق العملة المحققة", "REVENUE", isDebitNormal = false, isLocked = true),
            AccountEntity(AccountConstants.UNREALIZED_FX_GAIN, "أرباح فروق العملة غير المحققة (إعادة التقييم)", "REVENUE", isDebitNormal = false, isLocked = true),
            AccountEntity(AccountConstants.DIRECT_ISP_SERVICE_COST, "تكلفة الخدمة المباشرة (اشتراكات الإنترنت)", "EXPENSE", isDebitNormal = true, isLocked = true),
            AccountEntity(AccountConstants.OPERATING_EXPENSES, "مصاريف تشغيل وعمومية (ديزل/كهرباء)", "EXPENSE", isDebitNormal = true, isLocked = true),
            AccountEntity(AccountConstants.MAINTENANCE_SPARES, "صيانة وقطع غيار", "EXPENSE", isDebitNormal = true, isLocked = true),
            AccountEntity(AccountConstants.DEPRECIATION_EXPENSE, "إهلاك الأصول", "EXPENSE", isDebitNormal = true, isLocked = true),
            AccountEntity(AccountConstants.SALARIES_STAFF, "رواتب وأجور وفنيون", "EXPENSE", isDebitNormal = true, isLocked = true),
            AccountEntity(AccountConstants.MISC_EXPENSES, "مصاريف متنوعة", "EXPENSE", isDebitNormal = true, isLocked = true),
            AccountEntity(AccountConstants.REALIZED_FX_LOSS, "خسائر فروق العملة المحققة", "EXPENSE", isDebitNormal = true, isLocked = true),
            AccountEntity(AccountConstants.UNREALIZED_FX_LOSS, "خسائر فروق العملة غير المحققة (إعادة التقييم)", "EXPENSE", isDebitNormal = true, isLocked = true)
        )
        accounts.forEach { acc ->
            db.openHelper.writableDatabase.execSQL("""
                INSERT OR IGNORE INTO accounts (code, name, type, isDebitNormal, isLocked, isActive)
                VALUES ('${acc.code}', '${acc.name}', '${acc.type}', ${if (acc.isDebitNormal) 1 else 0}, ${if (acc.isLocked) 1 else 0}, ${if (acc.isActive) 1 else 0})
            """)
        }

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
                    isClosed = false
                )
            )
            db.partyDao().insertParty(
                PartyEntity(
                    id = "P_FOUNDER",
                    name = "شريك مؤسس",
                    phone = "770000000",
                    isPartner = true,
                    isActive = true
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
            TreasuryAccountEntity(
                id = treasuryId,
                name = "صندوق النقدية بالدولار",
                currency = "USD",
                glAccountCode = "1101",
                allowNegative = false,
                isActive = true
            )
        )

        // Post opening deposit: 1,000 USD @ 530 = 530,000 YER (53,000,000 minor)
        writer.postCapitalReceipt(
            targetAccountCode = AccountConstants.CAPITAL,
            partnerPartyId = "P_FOUNDER",
            treasuryId = treasuryId,
            fiscalYear = 2026,
            dateEpochDay = asOf,
            amountOrigMinor = 100_000L, // $1,000.00
            currency = CurrencyCode.USD,
            exchangeRate = ExchangeRate(CurrencyCode.USD, CurrencyCode.FUNCTIONAL, 530_000_000L),
            notes = "إيداع صندوق دولار",
            rateZone = RateZone.SANAA
        )

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
            TreasuryAccountEntity(
                id = treasuryId,
                name = "خزينة دولار بدون تسعيرة",
                currency = "USD",
                glAccountCode = "1101",
                allowNegative = false,
                isActive = true
            )
        )

        // Post 500 USD @ 530
        writer.postCapitalReceipt(
            targetAccountCode = AccountConstants.CAPITAL,
            partnerPartyId = "P_FOUNDER",
            treasuryId = treasuryId,
            fiscalYear = 2026,
            dateEpochDay = asOf,
            amountOrigMinor = 50_000L,
            currency = CurrencyCode.USD,
            exchangeRate = ExchangeRate(CurrencyCode.USD, CurrencyCode.FUNCTIONAL, 530_000_000L),
            notes = "إيداع",
            rateZone = RateZone.SANAA
        )

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
            TreasuryAccountEntity(
                id = treasuryId,
                name = "صندوق عدن",
                currency = "USD",
                glAccountCode = "1101",
                allowNegative = false,
                isActive = true
            )
        )

        writer.postCapitalReceipt(
            targetAccountCode = AccountConstants.CAPITAL,
            partnerPartyId = "P_FOUNDER",
            treasuryId = treasuryId,
            fiscalYear = 2026,
            dateEpochDay = asOf,
            amountOrigMinor = 100_000L, // $1,000
            currency = CurrencyCode.USD,
            exchangeRate = ExchangeRate(CurrencyCode.USD, CurrencyCode.FUNCTIONAL, 1_500_000_000L),
            notes = "إيداع عدن",
            rateZone = RateZone.ADEN
        )

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
            TreasuryAccountEntity(
                id = treasuryId,
                name = "صندوق التقييد الزمني",
                currency = "YER",
                glAccountCode = "1101",
                allowNegative = false,
                isActive = true
            )
        )

        // Entry within asOf (Day 20000): 50,000 YER
        writer.postCapitalReceipt(
            targetAccountCode = AccountConstants.CAPITAL,
            partnerPartyId = "P_FOUNDER",
            treasuryId = treasuryId,
            fiscalYear = 2026,
            dateEpochDay = asOf,
            amountOrigMinor = 5_000_000L,
            currency = CurrencyCode.FUNCTIONAL,
            exchangeRate = ExchangeRate.parity(CurrencyCode.FUNCTIONAL),
            notes = "حالي"
        )

        // Entry after asOf (Day 20005): 30,000 YER
        writer.postCapitalReceipt(
            targetAccountCode = AccountConstants.CAPITAL,
            partnerPartyId = "P_FOUNDER",
            treasuryId = treasuryId,
            fiscalYear = 2026,
            dateEpochDay = futureDate,
            amountOrigMinor = 3_000_000L,
            currency = CurrencyCode.FUNCTIONAL,
            exchangeRate = ExchangeRate.parity(CurrencyCode.FUNCTIONAL),
            notes = "مستقبلي"
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
        val entryId = UuidUtils.newTimeOrderedId()
        val docId = UuidUtils.newTimeOrderedId()
        db.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO documents (id, type, fiscalYear, docNumber, partyId, dateEpochDay, currency, exchangeRateMicros, totalMinor, totalBaseMinor, status)
            VALUES ('$docId', 'MANUAL_JV', 2026, 99, 'P_FOUNDER', $asOf, 'YER', 1000000, 100000, 100000, 'POSTED')
            """
        )
        db.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO journal_entries (id, docId, entryNumber, entryDateEpochDay, type, memo, createdAt)
            VALUES ('$entryId', '$docId', 99, $asOf, 'NORMAL', 'قيد شريك غير صالح', 1000)
            """
        )
        db.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO journal_lines (id, entryId, lineNo, accountCode, partyId, treasuryId, currency, exchangeRateMicros, origMinor, baseDebitMinor, baseCreditMinor, memo)
            VALUES ('${UuidUtils.newTimeOrderedId()}', '$entryId', 1, '3201', NULL, NULL, 'YER', 1000000, 100000, 100000, 0, 'شريك بلا معرّف')
            """
        )
        db.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO journal_lines (id, entryId, lineNo, accountCode, partyId, treasuryId, currency, exchangeRateMicros, origMinor, baseDebitMinor, baseCreditMinor, memo)
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
        val treasuryId = "TR_CLEAN"
        db.treasuryDao().insertTreasury(
            TreasuryAccountEntity(
                id = treasuryId,
                name = "صندوق نظيف",
                currency = "YER",
                glAccountCode = "1101",
                allowNegative = false,
                isActive = true
            )
        )

        // Seed balanced transaction via real LedgerWriter
        writer.postCapitalReceipt(
            targetAccountCode = AccountConstants.CAPITAL,
            partnerPartyId = "P_FOUNDER",
            treasuryId = treasuryId,
            fiscalYear = 2026,
            dateEpochDay = asOf,
            amountOrigMinor = 10_000_000L,
            currency = CurrencyCode.FUNCTIONAL,
            exchangeRate = ExchangeRate.parity(CurrencyCode.FUNCTIONAL),
            notes = "قيد سليم متزن"
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
        // Delete all partners to test no partners state
        db.openHelper.writableDatabase.execSQL("DELETE FROM parties WHERE isPartner = 1")
        val result = reportsEngine.generatePartnerSummaryReport(asOf)
        assertTrue("Result must be NoPartners state when no partners exist", result is PartnerSummaryResult.NoPartners)
    }
}
