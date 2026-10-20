package com.example.domain.report

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.ledger.AccountConstants
import com.example.core.model.CurrencyCode
import com.example.core.model.ExchangeRate
import com.example.data.ledger.LedgerWriter
import com.example.data.ledger.PurchaseItemSpec
import com.example.data.local.AppDatabase
import com.example.data.local.entity.FiscalPeriodEntity
import com.example.data.local.entity.PartyEntity
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
import java.nio.charset.StandardCharsets

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReportEnginePart0Test {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var writer: LedgerWriter

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.createInMemory(context)
        AppDatabase.installTriggers(db.openHelper.writableDatabase)
        AppDatabase.seedDefaultData(db.openHelper.writableDatabase)
        writer = LedgerWriter(db, enableInvariantValidation = true)

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
                    id = "vendor_part0",
                    name = "مورد تجريبي",
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
     * Requirement: Report Table SHA-256 fingerprint must be strictly deterministic
     * and identical for identical table content.
     */
    @Test
    fun test01_sha256HashDeterministicStability() {
        val columns = listOf(
            ReportColumn("col1", "البند"),
            ReportColumn("col2", "المبلغ", ColumnAlignment.END)
        )
        val rows = listOf(
            ReportRow(
                type = RowType.DATA,
                cells = listOf(
                    ReportCell.TextCell("إيراد كروت"),
                    ReportCell.MoneyCell(100_000L, CurrencyCode.FUNCTIONAL)
                )
            ),
            ReportRow(
                type = RowType.TOTAL,
                cells = listOf(
                    ReportCell.TextCell("الإجمالي", isBold = true),
                    ReportCell.MoneyCell(100_000L, CurrencyCode.FUNCTIONAL)
                )
            )
        )
        val table1 = ReportTable("تقرير تجريبي", "يناير 2026", columns, rows)
        val table2 = ReportTable("تقرير تجريبي", "يناير 2026", columns, rows)

        val hash1 = ReportHashCalculator.computeSha256(table1)
        val hash2 = ReportHashCalculator.computeSha256(table2)

        assertNotNull(hash1)
        assertEquals(64, hash1.length)
        assertEquals(hash1, hash2)

        // Modified table must yield different hash
        val modifiedRows = rows.toMutableList()
        modifiedRows[0] = ReportRow(
            type = RowType.DATA,
            cells = listOf(
                ReportCell.TextCell("إيراد كروت"),
                ReportCell.MoneyCell(105_000L, CurrencyCode.FUNCTIONAL)
            )
        )
        val modifiedTable = ReportTable("تقرير تجريبي", "يناير 2026", columns, modifiedRows)
        val modifiedHash = ReportHashCalculator.computeSha256(modifiedTable)
        assertTrue(hash1 != modifiedHash)
    }

    /**
     * Requirement: Generating any report MUST NOT modify journal_lines, journal_entries,
     * documents, or audit_log rows.
     */
    @Test
    fun test02_reportGenerationIsPureReadOnly() = runBlocking<Unit> {
        // Post a real purchase invoice to have records
            writer.postPurchaseInvoice(
                vendorPartyId = "vendor_part0",
                fiscalYear = 2026,
                dateEpochDay = 20455L,
                currency = CurrencyCode.FUNCTIONAL,
                exchangeRate = ExchangeRate.parity(CurrencyCode.FUNCTIONAL),
                items = listOf(
                    PurchaseItemSpec(
                        description = "سويتش شبكة",
                        accountCode = AccountConstants.OPERATING_EXPENSES,
                        quantity = 2,
                        unitPriceMinor = 50_000L,
                        isAsset = false
                    )
                ),
                notes = "فاتورة للتأكد من القراءة فقط"
            )

            val initialDocsCount = db.documentDao().getAllDocumentsSync().size
            val initialLinesCount = db.journalDao().getAllLinesSync().size
            val initialAuditCount = db.auditLogDao().getAllLogsSync().size

            assertTrue(initialDocsCount > 0)
            assertTrue(initialLinesCount > 0)

            // Execute ReportsEngine generator
            val engine = ReportsEngine(db)
            val request = ReportRequest(
                type = ReportType.INCOME_STATEMENT,
                fromEpochDay = 20450L,
                toEpochDay = 20480L
            )
            val result = engine.generateReport(request)

            assertNotNull(result)
            assertTrue(result.table.rows.isNotEmpty())

            // Assert exact zero mutation
            val finalDocsCount = db.documentDao().getAllDocumentsSync().size
            val finalLinesCount = db.journalDao().getAllLinesSync().size
            val finalAuditCount = db.auditLogDao().getAllLogsSync().size

            assertEquals(initialDocsCount, finalDocsCount)
            assertEquals(initialLinesCount, finalLinesCount)
            assertEquals(initialAuditCount, finalAuditCount)
    }

    /**
     * Requirement: Drill-down lines sum MUST EXACTLY equal the drillable cell's amountMinor.
     */
    @Test
    fun test03_drillDownSumExactEquality() {
        runBlocking {
            writer.postPurchaseInvoice(
                vendorPartyId = "vendor_part0",
                fiscalYear = 2026,
                dateEpochDay = 20456L,
                currency = CurrencyCode.FUNCTIONAL,
                exchangeRate = ExchangeRate.parity(CurrencyCode.FUNCTIONAL),
                items = listOf(
                    PurchaseItemSpec(
                        description = "كيابل ومصروفات",
                        accountCode = AccountConstants.OPERATING_EXPENSES,
                        quantity = 1,
                        unitPriceMinor = 35_000L,
                        isAsset = false
                    )
                ),
                notes = "مصروف تشغيلي للغوص"
            )

            val drillService = DrillDownService(db)
            val drillTarget = DrillTarget.AccountLines(
                accountCode = AccountConstants.OPERATING_EXPENSES,
                fromEpochDay = 20450L,
                toEpochDay = 20480L
            )

            val drillLines = drillService.fetchDrillDownLines(drillTarget)
            assertTrue(drillLines.isNotEmpty())

            val sumDebit = drillLines.sumOf { it.baseDebitMinor }
            val sumCredit = drillLines.sumOf { it.baseCreditMinor }
            val netDebit = sumDebit - sumCredit

            assertEquals(35_000L, netDebit)
        }
    }

    /**
     * Requirement: CSV Export must include UTF-8 BOM, Arabic headers, and unformatted decimal numbers.
     */
    @Test
    fun test04_csvExportFormatAndBOM() {
        val columns = listOf(
            ReportColumn("item", "البند"),
            ReportColumn("amount", "المبلغ بالريال", ColumnAlignment.END)
        )
        val rows = listOf(
            ReportRow(
                type = RowType.DATA,
                cells = listOf(
                    ReportCell.TextCell("إيراد شبكة"),
                    ReportCell.MoneyCell(123450L, CurrencyCode.FUNCTIONAL) // 1,234.50
                )
            )
        )
        val table = ReportTable("تقرير تجريبي", "الفترة", columns, rows)
        val csvBytes = ReportCsvExporter.exportToBytes(table)

        // UTF-8 BOM is 0xEF, 0xBB, 0xBF
        assertEquals(0xEF.toByte(), csvBytes[0])
        assertEquals(0xBB.toByte(), csvBytes[1])
        assertEquals(0xBF.toByte(), csvBytes[2])

        val csvString = String(csvBytes, StandardCharsets.UTF_8)
        assertTrue(csvString.contains("البند"))
        assertTrue(csvString.contains("المبلغ بالريال"))
        assertTrue(csvString.contains("إيراد شبكة"))
        // Check decimal number format: 1234.50 without thousand comma separators
        assertTrue(csvString.contains("1234.50"))
    }
}
