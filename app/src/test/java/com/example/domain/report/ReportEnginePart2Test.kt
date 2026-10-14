package com.example.domain.report

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.ledger.AccountConstants
import com.example.core.model.CurrencyCode
import com.example.core.model.ExchangeRate
import com.example.data.ledger.InvoiceAllocationSpec
import com.example.data.ledger.LedgerWriter
import com.example.data.ledger.PaymentVoucherType
import com.example.data.ledger.PurchaseItemSpec
import com.example.data.local.AppDatabase
import com.example.data.local.entity.AssetEntity
import com.example.data.local.entity.CardPackageEntity
import com.example.data.local.entity.FiscalPeriodEntity
import com.example.data.local.entity.PartyEntity
import com.example.data.local.entity.StockMovementEntity
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
class ReportEnginePart2Test {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var writer: LedgerWriter
    private lateinit var engine: ReportsEngine

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.createInMemory(context)
        AppDatabase.installTriggers(db.openHelper.writableDatabase)
        AppDatabase.seedDefaultData(db.openHelper.writableDatabase)
        writer = LedgerWriter(db, enableInvariantValidation = true)
        engine = ReportsEngine(db)

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

            // Insert Customer / Agent
            db.partyDao().insertParty(
                PartyEntity(
                    id = "agent_amal_01",
                    name = "بقالة الأمل",
                    isCustomer = true,
                    creditLimitMinor = 200_000L,
                    isActive = true
                )
            )

            // Insert Upstream Vendor
            db.partyDao().insertParty(
                PartyEntity(
                    id = "vendor_starlink",
                    name = "مزود ستارلينك الدولي",
                    isVendor = true,
                    isActive = true
                )
            )

            // Insert Treasuries with allowNegative = true for testing
            db.treasuryDao().insertTreasury(
                TreasuryAccountEntity(
                    id = "treasury_main_yer",
                    name = "صندوق النقد الرئيسي",
                    glAccountCode = AccountConstants.CASH_VAULT,
                    currency = "YER",
                    isActive = true,
                    allowNegative = true
                )
            )
            db.treasuryDao().insertTreasury(
                TreasuryAccountEntity(
                    id = "treasury_sar_vault",
                    name = "خزينة الريال السعودي",
                    glAccountCode = AccountConstants.CASH_VAULT,
                    currency = "SAR",
                    isActive = true,
                    allowNegative = true
                )
            )

            // Insert Card Packages
            db.cardPackageDao().insertPackage(
                CardPackageEntity(
                    id = "pkg_1hour",
                    name = "باقة 1 ساعة",
                    durationOrQuota = "1 hour",
                    wholesalePriceMinor = 800L,
                    retailPriceMinor = 1_000L,
                    isActive = true
                )
            )
            db.cardPackageDao().insertPackage(
                CardPackageEntity(
                    id = "pkg_3hours",
                    name = "باقة 3 ساعات",
                    durationOrQuota = "3 hours",
                    wholesalePriceMinor = 2_200L,
                    retailPriceMinor = 2_500L,
                    isActive = true
                )
            )

            // Seed initial stock inventory so sales validation passes
            db.cardPackageDao().insertStockMovement(
                StockMovementEntity(
                    id = "init_stock_1h",
                    packageId = "pkg_1hour",
                    docId = null,
                    type = "RECEIVE",
                    quantity = 1000,
                    movementDateEpochDay = 20455L
                )
            )
            db.cardPackageDao().insertStockMovement(
                StockMovementEntity(
                    id = "init_stock_3h",
                    packageId = "pkg_3hours",
                    docId = null,
                    type = "RECEIVE",
                    quantity = 1000,
                    movementDateEpochDay = 20455L
                )
            )
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    /**
     * Test 2.1: SALES_ANALYTICS (تحليل المبيعات)
     */
    @Test
    fun test01_salesAnalyticsReport() {
        runBlocking {
            writer.postQuickSale(
                partyId = "agent_amal_01",
                packageId = "pkg_1hour",
                description = "باقة 1 ساعة",
                quantity = 100,
                unitPriceMinor = 900L,
                cashPaidMinor = 90_000L,
                treasuryId = "treasury_main_yer",
                fiscalYear = 2026,
                dateEpochDay = 20490L
            )

            val req = ReportRequest(
                type = ReportType.SALES_ANALYTICS,
                fromEpochDay = 20485L,
                toEpochDay = 20512L
            )

            val res = engine.generateReport(req)
            assertNotNull(res)
            assertTrue(res.table.rows.isNotEmpty())
            assertTrue(res.footer.reconciled)

            // Verify package card sales are present
            val pkgRow = res.table.rows.find { it.cells.any { c -> (c as? ReportCell.TextCell)?.text?.contains("باقة 1 ساعة") == true } }
            assertNotNull("Should contain package 1 hour", pkgRow)

            // Verify total row
            val totalRow = res.table.rows.find { it.type == RowType.TOTAL }
            assertNotNull(totalRow)
            val moneyCell = totalRow!!.cells.find { it is ReportCell.MoneyCell } as ReportCell.MoneyCell
            assertEquals(90_000L, moneyCell.amountMinor)
        }
    }

    /**
     * Test 2.2: COLLECTIONS_DSO (التحصيل ومعدل أيام التحصيل)
     */
    @Test
    fun test02_collectionsDsoReport() {
        runBlocking {
            // Post credit sale invoice to agent_amal_01 (0 cash paid)
            val saleResult = writer.postQuickSale(
                partyId = "agent_amal_01",
                packageId = "pkg_3hours",
                description = "باقة 3 ساعات",
                quantity = 50,
                unitPriceMinor = 2_400L,
                cashPaidMinor = 0L,
                treasuryId = "treasury_main_yer",
                fiscalYear = 2026,
                dateEpochDay = 20490L
            )

            // Partial collection of 40,000 YER
            writer.postCustomerReceipt(
                partyId = "agent_amal_01",
                treasuryId = "treasury_main_yer",
                fiscalYear = 2026,
                dateEpochDay = 20495L,
                amountOrigMinor = 40_000L,
                currency = CurrencyCode.FUNCTIONAL,
                exchangeRate = ExchangeRate.parity(CurrencyCode.FUNCTIONAL),
                allocations = listOf(
                    InvoiceAllocationSpec(saleResult.invoice.id, 40_000L)
                )
            )

            val req = ReportRequest(
                type = ReportType.COLLECTIONS_DSO,
                fromEpochDay = 20485L,
                toEpochDay = 20512L
            )

            val res = engine.generateReport(req)
            assertNotNull(res)
            assertTrue(res.footer.reconciled)

            // Debtors should include agent_amal_01 with 80,000 YER remaining balance
            val amalRow = res.table.rows.find { it.cells.any { c -> (c as? ReportCell.TextCell)?.text?.contains("بقالة الأمل") == true } }
            assertNotNull("Should find agent row", amalRow)

            val balanceCell = amalRow!!.cells.find { c -> (c as? ReportCell.MoneyCell)?.amountMinor == 80_000L }
            assertNotNull("Should have 80,000 YER remaining balance", balanceCell)
        }
    }

    /**
     * Test 2.3: EXPENSE_ANALYSIS (تحليل المصروفات وهامش الخدمة)
     */
    @Test
    fun test03_expenseAnalysisReport() {
        runBlocking {
            // Direct ISP purchase cost
            writer.postPurchaseInvoice(
                vendorPartyId = "vendor_starlink",
                fiscalYear = 2026,
                dateEpochDay = 20490L,
                currency = CurrencyCode.FUNCTIONAL,
                exchangeRate = ExchangeRate.parity(CurrencyCode.FUNCTIONAL),
                items = listOf(
                    PurchaseItemSpec(
                        description = "اشتراك سعات ستارلينك 1 جيجا",
                        accountCode = AccountConstants.DIRECT_ISP_SERVICE_COST,
                        quantity = 1,
                        unitPriceMinor = 150_000L,
                        isAsset = false
                    )
                ),
                notes = "سعات المنبع"
            )

            // Operating Expense (Maintenance 5202)
            writer.postPaymentVoucher(
                recipientPartyId = "agent_amal_01",
                treasuryId = "treasury_main_yer",
                fiscalYear = 2026,
                dateEpochDay = 20492L,
                amountOrigMinor = 25_000L,
                currency = CurrencyCode.FUNCTIONAL,
                exchangeRate = ExchangeRate.parity(CurrencyCode.FUNCTIONAL),
                paymentType = PaymentVoucherType.OPERATING_EXPENSE,
                customExpenseCode = AccountConstants.MAINTENANCE_SPARES,
                notes = "صيانة كوابل المحطة"
            )

            val req = ReportRequest(
                type = ReportType.EXPENSE_ANALYSIS,
                fromEpochDay = 20485L,
                toEpochDay = 20512L
            )

            val res = engine.generateReport(req)
            assertNotNull(res)
            assertTrue(res.footer.reconciled)

            // Total expenses should equal 175,000 YER
            val totalRow = res.table.rows.find { it.type == RowType.TOTAL }
            assertNotNull(totalRow)
            val moneyCell = totalRow!!.cells.find { it is ReportCell.MoneyCell } as ReportCell.MoneyCell
            assertEquals(175_000L, moneyCell.amountMinor)
        }
    }

    /**
     * Test 2.4: CARD_INVENTORY_REPORT (حركة مخزون الكروت)
     */
    @Test
    fun test04_cardInventoryReport() {
        runBlocking {
            // Movements: Receive 500 cards, sell 100
            db.cardPackageDao().insertStockMovement(
                StockMovementEntity(
                    id = "mov_01",
                    packageId = "pkg_1hour",
                    docId = null,
                    type = "RECEIVE",
                    quantity = 500,
                    movementDateEpochDay = 20486L
                )
            )
            db.cardPackageDao().insertStockMovement(
                StockMovementEntity(
                    id = "mov_02",
                    packageId = "pkg_1hour",
                    docId = null,
                    type = "SELL",
                    quantity = 100,
                    movementDateEpochDay = 20490L
                )
            )

            val req = ReportRequest(
                type = ReportType.CARD_INVENTORY_REPORT,
                fromEpochDay = 20485L,
                toEpochDay = 20512L
            )

            val res = engine.generateReport(req)
            assertNotNull(res)
            assertTrue(res.footer.reconciled)

            val pkgRow = res.table.rows.find { it.cells.any { c -> (c as? ReportCell.TextCell)?.text?.contains("باقة 1 ساعة") == true } }
            assertNotNull(pkgRow)

            // Closing quantity: initial 1000 + 500 - 100 = 1400
            val closingCell = pkgRow!!.cells.find { c -> (c as? ReportCell.TextCell)?.text == "1400" }
            assertNotNull("Closing quantity should be 1400", closingCell)
        }
    }

    /**
     * Test 2.5: FIXED_ASSETS_REGISTER (سجل أصول ومعدات الشبكة)
     */
    @Test
    fun test05_fixedAssetsRegisterReport() {
        runBlocking {
            db.assetDao().insertAsset(
                AssetEntity(
                    id = "asset_mikrotik_router",
                    docId = "doc_asset_01",
                    name = "راوتر مايكروتك CCR2004",
                    purchaseDateEpochDay = 20455L,
                    purchaseCostMinor = 360_000L,
                    salvageValueMinor = 36_000L,
                    usefulLifeMonths = 36,
                    accumulatedDepreciationMinor = 9_000L,
                    isDisposed = false
                )
            )

            val req = ReportRequest(
                type = ReportType.FIXED_ASSETS_REGISTER,
                fromEpochDay = 20485L,
                toEpochDay = 20512L
            )

            val res = engine.generateReport(req)
            assertNotNull(res)
            assertTrue(res.footer.reconciled)

            val assetRow = res.table.rows.find { it.cells.any { c -> (c as? ReportCell.TextCell)?.text?.contains("CCR2004") == true } }
            assertNotNull(assetRow)

            // Net book value should be 360,000 - 9,000 = 351,000
            val netBookCell = assetRow!!.cells.find { c -> (c as? ReportCell.MoneyCell)?.amountMinor == 351_000L }
            assertNotNull("Net book value should be 351,000 YER", netBookCell)
        }
    }

    /**
     * Test 2.6: CURRENCY_POSITIONS (مراكز العملات وفروق الصرف)
     */
    @Test
    fun test06_currencyPositionsReport() {
        runBlocking {
            val req = ReportRequest(
                type = ReportType.CURRENCY_POSITIONS,
                fromEpochDay = 20485L,
                toEpochDay = 20512L
            )

            val res = engine.generateReport(req)
            assertNotNull(res)
            assertTrue(res.footer.reconciled)
            assertTrue(res.table.rows.size >= 3)
        }
    }

    /**
     * Test 2.7: AUDIT_TRAIL (سجل الرقابة والتعديلات والفحوصات)
     */
    @Test
    fun test07_auditTrailReport() {
        runBlocking {
            val req = ReportRequest(
                type = ReportType.AUDIT_TRAIL,
                fromEpochDay = 20485L,
                toEpochDay = 20512L
            )

            val res = engine.generateReport(req)
            assertNotNull(res)
            assertTrue("Audit report must be reconciled and mathematically sound", res.footer.reconciled)

            val balanceRow = res.table.rows.find { it.cells.any { c -> (c as? ReportCell.TextCell)?.text?.contains("تطابق إجمالي المدين والدائن") == true } }
            assertNotNull(balanceRow)
        }
    }
}
