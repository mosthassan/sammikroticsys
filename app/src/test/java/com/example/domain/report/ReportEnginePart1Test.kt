package com.example.domain.report

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.ledger.AccountConstants
import com.example.core.model.CurrencyCode
import com.example.core.model.ExchangeRate
import com.example.data.ledger.LedgerWriter
import com.example.data.ledger.PaymentVoucherType
import com.example.data.ledger.PurchaseItemSpec
import com.example.data.local.AppDatabase
import com.example.data.local.entity.FiscalPeriodEntity
import com.example.data.local.entity.PartyEntity
import com.example.data.local.entity.TreasuryAccountEntity
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReportEnginePart1Test {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var writer: LedgerWriter
    private lateinit var engine: ReportsEngine
    private lateinit var drillService: DrillDownService

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.createInMemory(context)
        AppDatabase.installTriggers(db.openHelper.writableDatabase)
        AppDatabase.seedDefaultData(db.openHelper.writableDatabase)
        writer = LedgerWriter(db, enableInvariantValidation = true)
        engine = ReportsEngine(db)
        drillService = DrillDownService(db)

        runBlocking {
            db.fiscalPeriodDao().insertPeriod(
                FiscalPeriodEntity(
                    id = "FP_2026_01",
                    year = 2026,
                    month = 1,
                    isClosed = false
                )
            )
            db.fiscalPeriodDao().insertPeriod(
                FiscalPeriodEntity(
                    id = "FP_2026_02",
                    year = 2026,
                    month = 2,
                    isClosed = false
                )
            )

            // Insert a Partner
            db.partyDao().insertParty(
                PartyEntity(
                    id = "partner_sam_01",
                    name = "المهندس سام",
                    isPartner = true,
                    equityPercentageBasisPoints = 5000,
                    isActive = true
                )
            )
            db.partyDao().insertParty(
                PartyEntity(
                    id = "partner_ali_02",
                    name = "الشريك علي",
                    isPartner = true,
                    equityPercentageBasisPoints = 5000,
                    isActive = true
                )
            )

            // Insert Vendor
            db.partyDao().insertParty(
                PartyEntity(
                    id = "vendor_starlink_isp",
                    name = "موزع ستارلينك الرئيسي",
                    isVendor = true,
                    isActive = true
                )
            )

            // Insert Customer
            db.partyDao().insertParty(
                PartyEntity(
                    id = "customer_agent_01",
                    name = "بقالة الأمل",
                    isCustomer = true,
                    isActive = true
                )
            )

            // Insert Treasuries
            db.treasuryDao().insertTreasury(
                TreasuryAccountEntity(
                    id = "treasury_main_box",
                    name = "الصندوق الرئيسي",
                    glAccountCode = AccountConstants.CASH_VAULT,
                    currency = "YER",
                    isActive = true
                )
            )
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    /**
     * Test 1.1: Monthly Profit Comparative Report (PROFIT_MONTHLY)
     * Verifies that the comparative report decomposes revenue, ISP service costing,
     * operating expenses, and realized FX into a comparative table between current and previous month.
     */
    @Test
    fun test01_monthlyProfitComparativeBreakdown() {
        runBlocking {
            // Jan 2026 transactions (Previous Month: 20454 - 20484)
            // Post an ISP service cost invoice in Jan 2026
            writer.postPurchaseInvoice(
                vendorPartyId = "vendor_starlink_isp",
                fiscalYear = 2026,
                dateEpochDay = 20460L,
                currency = CurrencyCode.FUNCTIONAL,
                exchangeRate = ExchangeRate.parity(CurrencyCode.FUNCTIONAL),
                items = listOf(
                    PurchaseItemSpec(
                        description = "اشتراك باقة ألياف شهر يناير",
                        accountCode = AccountConstants.DIRECT_ISP_SERVICE_COST,
                        quantity = 1,
                        unitPriceMinor = 100_000L,
                        isAsset = false
                    )
                ),
                notes = "تكلفة خدمة شهر يناير"
            )

            // Feb 2026 transactions (Current Month: 20485 - 20512)
            // Post ISP cost invoice in Feb 2026
            writer.postPurchaseInvoice(
                vendorPartyId = "vendor_starlink_isp",
                fiscalYear = 2026,
                dateEpochDay = 20490L,
                currency = CurrencyCode.FUNCTIONAL,
                exchangeRate = ExchangeRate.parity(CurrencyCode.FUNCTIONAL),
                items = listOf(
                    PurchaseItemSpec(
                        description = "اشتراك باقة ألياف شهر فبراير",
                        accountCode = AccountConstants.DIRECT_ISP_SERVICE_COST,
                        quantity = 1,
                        unitPriceMinor = 120_000L,
                        isAsset = false
                    )
                ),
                notes = "تكلفة خدمة شهر فبراير"
            )

            // Post Operating Expense in Feb 2026
            writer.postPurchaseInvoice(
                vendorPartyId = "vendor_starlink_isp",
                fiscalYear = 2026,
                dateEpochDay = 20495L,
                currency = CurrencyCode.FUNCTIONAL,
                exchangeRate = ExchangeRate.parity(CurrencyCode.FUNCTIONAL),
                items = listOf(
                    PurchaseItemSpec(
                        description = "ديزل المولد لشهر فبراير",
                        accountCode = AccountConstants.OPERATING_EXPENSES,
                        quantity = 1,
                        unitPriceMinor = 30_000L,
                        isAsset = false
                    )
                ),
                notes = "مصروف تشغيلي"
            )

            // Run comparative monthly profit report for Feb 2026
            val fromFeb = 20485L
            val toFeb = 20512L
            val request = ReportRequest(
                type = ReportType.PROFIT_MONTHLY,
                fromEpochDay = fromFeb,
                toEpochDay = toFeb
            )

            val result = engine.generateReport(request)
            assertNotNull(result)
            assertTrue(result.table.rows.isNotEmpty())

            // Find ISP cost row in the table
            val ispCostRow = result.table.rows.find { it.cells.any { c -> c is ReportCell.TextCell && c.text.contains("5101") } }
            assertNotNull(ispCostRow)

            val currentCell = ispCostRow!!.cells[1] as ReportCell.MoneyCell
            val prevCell = ispCostRow.cells[2] as ReportCell.MoneyCell
            val diffCell = ispCostRow.cells[3] as ReportCell.MoneyCell

            assertEquals(120_000L, currentCell.amountMinor)
            assertEquals(100_000L, prevCell.amountMinor)
            assertEquals(20_000L, diffCell.amountMinor)

            // Drill down verification for current month ISP cost
            val target = currentCell.drillTarget as DrillTarget.AccountLines
            val drillLines = drillService.fetchDrillDownLines(target)
            assertEquals(1, drillLines.size)
            assertEquals(120_000L, drillLines.sumOf { it.baseDebitMinor } - drillLines.sumOf { it.baseCreditMinor })
        }
    }

    /**
     * Test 1.2: Partner Movement Statement (PARTNER_STATEMENT)
     * Verifies partner capital contributions, drawings, running balance, and drill-downs.
     */
    @Test
    fun test02_partnerStatementMovementsAndRunningBalance() {
        runBlocking {
            val partnerId = "partner_sam_01"

            // Post partner capital contribution (1,000,000 YER)
            writer.postCapitalReceipt(
                targetAccountCode = AccountConstants.CAPITAL,
                partnerPartyId = partnerId,
                treasuryId = "treasury_main_box",
                fiscalYear = 2026,
                dateEpochDay = 20460L,
                amountOrigMinor = 1_000_000L,
                currency = CurrencyCode.FUNCTIONAL,
                exchangeRate = ExchangeRate.parity(CurrencyCode.FUNCTIONAL),
                notes = "حصة رأس المال الأولية"
            )

            // Post partner drawings (50,000 YER)
            writer.postPaymentVoucher(
                recipientPartyId = partnerId,
                treasuryId = "treasury_main_box",
                fiscalYear = 2026,
                dateEpochDay = 20470L,
                amountOrigMinor = 50_000L,
                currency = CurrencyCode.FUNCTIONAL,
                exchangeRate = ExchangeRate.parity(CurrencyCode.FUNCTIONAL),
                paymentType = PaymentVoucherType.PARTNER_DRAWINGS,
                notes = "مسحوبات شخصية"
            )

            val request = ReportRequest(
                type = ReportType.PARTNER_STATEMENT,
                fromEpochDay = 20450L,
                toEpochDay = 20500L,
                params = mapOf("partyId" to partnerId)
            )

            val result = engine.generateReport(request)
            assertNotNull(result)
            assertTrue(result.table.rows.size >= 4) // Opening + 2 lines + Total

            // Total Row
            val totalRow = result.table.rows.last()
            assertEquals(RowType.TOTAL, totalRow.type)

            val debitCell = totalRow.cells[4] as ReportCell.MoneyCell
            val creditCell = totalRow.cells[5] as ReportCell.MoneyCell
            val finalRunningCell = totalRow.cells[6] as ReportCell.MoneyCell

            assertEquals(50_000L, debitCell.amountMinor)
            assertEquals(1_000_000L, creditCell.amountMinor)
            assertEquals(950_000L, finalRunningCell.amountMinor) // 1,000,000 - 50,000 = 950,000
        }
    }

    /**
     * Test 1.3: Partners Equity Summary (PARTNERS_EQUITY_SUMMARY)
     * Verifies partner equity summary across all partners with capital, current account,
     * indicative profit distribution, and reconciliation.
     */
    @Test
    fun test03_partnersEquitySummaryReport() {
        runBlocking {
            // Contributed 600,000 for Sam and 400,000 for Ali
            writer.postCapitalReceipt(
                targetAccountCode = AccountConstants.CAPITAL,
                partnerPartyId = "partner_sam_01",
                treasuryId = "treasury_main_box",
                fiscalYear = 2026,
                dateEpochDay = 20460L,
                amountOrigMinor = 600_000L,
                currency = CurrencyCode.FUNCTIONAL,
                exchangeRate = ExchangeRate.parity(CurrencyCode.FUNCTIONAL),
                notes = "رأس مال سام"
            )
            writer.postCapitalReceipt(
                targetAccountCode = AccountConstants.CAPITAL,
                partnerPartyId = "partner_ali_02",
                treasuryId = "treasury_main_box",
                fiscalYear = 2026,
                dateEpochDay = 20460L,
                amountOrigMinor = 400_000L,
                currency = CurrencyCode.FUNCTIONAL,
                exchangeRate = ExchangeRate.parity(CurrencyCode.FUNCTIONAL),
                notes = "رأس مال علي"
            )

            val request = ReportRequest(
                type = ReportType.PARTNERS_EQUITY_SUMMARY,
                fromEpochDay = 20450L,
                toEpochDay = 20500L
            )

            val result = engine.generateReport(request)
            assertNotNull(result)

            val totalRow = result.table.rows.last()
            assertEquals(RowType.TOTAL, totalRow.type)

            val totalCapitalCell = totalRow.cells[2] as ReportCell.MoneyCell
            assertEquals(1_000_000L, totalCapitalCell.amountMinor)
            assertTrue(result.footer.reconciled)
        }
    }

    /**
     * Test 1.4: Direct Cash Flow Statement (CASH_FLOW)
     * Verifies operating cash flows, financing cash flows, net cash change, and cash reconciliation.
     */
    @Test
    fun test04_cashFlowDirectMethodStatement() {
        runBlocking {
            // Initial cash in treasury via capital contribution (Financing Inflow: 500,000)
            writer.postCapitalReceipt(
                targetAccountCode = AccountConstants.CAPITAL,
                partnerPartyId = "partner_sam_01",
                treasuryId = "treasury_main_box",
                fiscalYear = 2026,
                dateEpochDay = 20460L,
                amountOrigMinor = 500_000L,
                currency = CurrencyCode.FUNCTIONAL,
                exchangeRate = ExchangeRate.parity(CurrencyCode.FUNCTIONAL),
                notes = "تمويل رأسمالي"
            )

            // Direct payment voucher for fuel (Operating Outflow: 40,000)
            writer.postPaymentVoucher(
                recipientPartyId = "vendor_starlink_isp",
                treasuryId = "treasury_main_box",
                fiscalYear = 2026,
                dateEpochDay = 20465L,
                amountOrigMinor = 40_000L,
                currency = CurrencyCode.FUNCTIONAL,
                exchangeRate = ExchangeRate.parity(CurrencyCode.FUNCTIONAL),
                paymentType = PaymentVoucherType.OPERATING_EXPENSE,
                customExpenseCode = AccountConstants.OPERATING_EXPENSES,
                notes = "مصروف ديزل نقدي"
            )

            val request = ReportRequest(
                type = ReportType.CASH_FLOW,
                fromEpochDay = 20450L,
                toEpochDay = 20480L
            )

            val result = engine.generateReport(request)
            assertNotNull(result)

            // Ending cash must equal beginning cash + net change = 0 + 460,000 = 460,000
            val endingCashRow = result.table.rows.find { it.cells.any { c -> c is ReportCell.TextCell && c.text.contains("نهاية الفترة") } }
            assertNotNull(endingCashRow)
            val endingCashCell = endingCashRow!!.cells[1] as ReportCell.MoneyCell
            assertEquals(460_000L, endingCashCell.amountMinor)

            assertTrue(result.footer.reconciled)
        }
    }

    /**
     * Test 1.5: Daily Cash Register Report (DAILY_CASH_REGISTER)
     * Verifies chronological cash receipts and payments with running balance.
     */
    @Test
    fun test05_dailyCashRegisterChronologicalRunningBalance() {
        runBlocking {
            // Receipt: Capital 300,000
            writer.postCapitalReceipt(
                targetAccountCode = AccountConstants.CAPITAL,
                partnerPartyId = "partner_sam_01",
                treasuryId = "treasury_main_box",
                fiscalYear = 2026,
                dateEpochDay = 20460L,
                amountOrigMinor = 300_000L,
                currency = CurrencyCode.FUNCTIONAL,
                exchangeRate = ExchangeRate.parity(CurrencyCode.FUNCTIONAL),
                notes = "إيداع أولي"
            )

            // Payment: Expense 75,000
            writer.postPaymentVoucher(
                recipientPartyId = "vendor_starlink_isp",
                treasuryId = "treasury_main_box",
                fiscalYear = 2026,
                dateEpochDay = 20462L,
                amountOrigMinor = 75_000L,
                currency = CurrencyCode.FUNCTIONAL,
                exchangeRate = ExchangeRate.parity(CurrencyCode.FUNCTIONAL),
                paymentType = PaymentVoucherType.OPERATING_EXPENSE,
                customExpenseCode = AccountConstants.OPERATING_EXPENSES,
                notes = "صيانة نقداً"
            )

            val request = ReportRequest(
                type = ReportType.DAILY_CASH_REGISTER,
                fromEpochDay = 20450L,
                toEpochDay = 20480L,
                params = mapOf("treasuryId" to "treasury_main_box")
            )

            val result = engine.generateReport(request)
            assertNotNull(result)

            val totalRow = result.table.rows.last()
            assertEquals(RowType.TOTAL, totalRow.type)

            val debitCell = totalRow.cells[4] as ReportCell.MoneyCell // Inflow
            val creditCell = totalRow.cells[5] as ReportCell.MoneyCell // Outflow
            val runningCell = totalRow.cells[6] as ReportCell.MoneyCell

            assertEquals(300_000L, debitCell.amountMinor)
            assertEquals(75_000L, creditCell.amountMinor)
            assertEquals(225_000L, runningCell.amountMinor)
        }
    }

    /**
     * Test 1.6: Invariant Verification
     * Generating ANY Part 1 report must NEVER alter database state (read-only).
     */
    @Test
    fun test06_part1ReportsArePureReadOnly() {
        runBlocking {
            val docsBefore = db.documentDao().getAllDocumentsSync().size
            val linesBefore = db.journalDao().getAllLinesSync().size

            val types = listOf(
                ReportType.PROFIT_MONTHLY,
                ReportType.PARTNER_STATEMENT,
                ReportType.PARTNERS_EQUITY_SUMMARY,
                ReportType.CASH_FLOW,
                ReportType.DAILY_CASH_REGISTER
            )

            for (t in types) {
                val req = ReportRequest(t, 20450L, 20500L)
                val res = engine.generateReport(req)
                assertNotNull(res)
            }

            val docsAfter = db.documentDao().getAllDocumentsSync().size
            val linesAfter = db.journalDao().getAllLinesSync().size

            assertEquals(docsBefore, docsAfter)
            assertEquals(linesBefore, linesAfter)
        }
    }
}
