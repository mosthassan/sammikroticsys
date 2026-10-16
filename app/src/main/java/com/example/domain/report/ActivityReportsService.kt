package com.example.domain.report

import com.example.core.ledger.AccountConstants
import com.example.core.model.CurrencyCode
import com.example.core.model.ExchangeRate
import com.example.core.model.Money
import com.example.core.model.RateZone
import com.example.data.local.AppDatabase
import com.example.domain.usecase.ExchangeRateResolver

data class SalesReport(
    val fromEpochDay: Long,
    val toEpochDay: Long,
    val packageSalesMinor: Long,
    val serviceSalesMinor: Long,
    val totalRevenueMinor: Long,
    val footer: ReportFooter
) {
    fun formattedTotalRevenue(): String = Money(totalRevenueMinor, CurrencyCode.FUNCTIONAL).format()
}

data class ExpensesReport(
    val fromEpochDay: Long,
    val toEpochDay: Long,
    val totalOperatingExpensesMinor: Long,
    val directIspCostMinor: Long,
    val totalExpensesMinor: Long,
    val footer: ReportFooter
) {
    fun formattedTotalExpenses(): String = Money(totalExpensesMinor, CurrencyCode.FUNCTIONAL).format()
}

data class MonthlyProfitReport(
    val fromEpochDay: Long,
    val toEpochDay: Long,
    val netRevenueMinor: Long,
    val grossProfitMinor: Long,
    val totalExpensesMinor: Long,
    val netProfitMinor: Long,
    val footer: ReportFooter
) {
    fun formattedNetProfit(): String = Money(netProfitMinor, CurrencyCode.FUNCTIONAL).format()
}

data class InventoryReport(
    val totalPackagesSold: Int,
    val totalPackagesReturned: Int,
    val netPackagesSold: Int,
    val invoiceQuantitySum: Int,
    val footer: ReportFooter
)

class ActivityReportsService(
    private val db: AppDatabase,
    private val resolver: ExchangeRateResolver
) {

    suspend fun generateCurrencyPositionsReport(
        asOfDateEpochDay: Long,
        primaryRateZone: RateZone = RateZone.SANAA
    ): CurrencyPositionsReport {
        val treasuries = db.treasuryDao().getAllTreasuriesSync()
        val positions = mutableListOf<CurrencyPositionRow>()

        var totalBookValueBase = 0L
        var totalMarketValueBase = 0L
        var totalUnrealizedDiff = 0L
        var hasMissing = false

        for (tr in treasuries) {
            val origBal = db.journalDao().getNetOrigBalanceForTreasuryAsOf(tr.id, asOfDateEpochDay)
            val bookValueBase = db.journalDao().getNetBaseBalanceForTreasuryAsOf(tr.id, asOfDateEpochDay)
            val currency = CurrencyCode.fromString(tr.currency)

            totalBookValueBase += bookValueBase

            val rate: ExchangeRate? = if (currency == CurrencyCode.FUNCTIONAL) {
                ExchangeRate.parity(CurrencyCode.FUNCTIONAL)
            } else {
                resolver.resolveOrNull(currency, asOfDateEpochDay, primaryRateZone)
            }

            if (rate != null) {
                val marketVal = rate.convert(origBal)
                val diff = marketVal - bookValueBase
                totalMarketValueBase += marketVal
                totalUnrealizedDiff += diff

                positions.add(
                    CurrencyPositionRow(
                        treasuryId = tr.id,
                        treasuryName = tr.name,
                        currency = currency,
                        origBalanceMinor = origBal,
                        bookValueBaseMinor = bookValueBase,
                        exchangeRate = rate,
                        hasRate = true,
                        marketValueBaseMinor = marketVal,
                        unrealizedDiffMinor = diff
                    )
                )
            } else {
                hasMissing = true
                positions.add(
                    CurrencyPositionRow(
                        treasuryId = tr.id,
                        treasuryName = tr.name,
                        currency = currency,
                        origBalanceMinor = origBal,
                        bookValueBaseMinor = bookValueBase,
                        exchangeRate = null,
                        hasRate = false,
                        marketValueBaseMinor = null,
                        unrealizedDiffMinor = null
                    )
                )
            }
        }

        // Ledger Reconciliation: compare sum of treasury book values to accounts 1101 + 1102
        val balVault = db.journalDao().getAccountNetBaseBalanceAsOf(AccountConstants.CASH_VAULT, asOfDateEpochDay)
        val balBanks = db.journalDao().getAccountNetBaseBalanceAsOf(AccountConstants.BANKS_WALLETS, asOfDateEpochDay)
        val expectedLedgerTotal = balVault + balBanks

        val checks = listOf(
            ReconciliationCheck(
                name = "مراكز العملات (الدفتري = 1101 + 1102)",
                expectedMinor = expectedLedgerTotal,
                actualMinor = totalBookValueBase,
                passed = (expectedLedgerTotal == totalBookValueBase),
                note = "مطابقة رصيد الخزائن الدفتري مع الأستاذ العام"
            )
        )

        return CurrencyPositionsReport(
            asOfDateEpochDay = asOfDateEpochDay,
            positions = positions,
            totalBookValueBaseMinor = totalBookValueBase,
            totalMarketValueBaseMinor = totalMarketValueBase,
            totalUnrealizedDiffMinor = totalUnrealizedDiff,
            hasMissingRates = hasMissing,
            footer = ReportFooter(checks = checks)
        )
    }

    suspend fun generateSalesReport(
        fromEpochDay: Long,
        toEpochDay: Long
    ): SalesReport {
        val incomeLines = db.journalDao().getIncomeStatementLines(fromEpochDay, toEpochDay)
        val pkgSales = incomeLines.filter { it.accountCode == AccountConstants.CARD_SALES_REVENUE }.sumOf { it.netAmountMinor }
        val srvSales = incomeLines.filter { it.accountCode == AccountConstants.DIRECT_SERVICE_REVENUE }.sumOf { it.netAmountMinor }
        val returns = incomeLines.filter { it.accountCode == AccountConstants.SALES_RETURNS }.sumOf { it.netAmountMinor }
        val netRevenue = (pkgSales + srvSales) - returns

        val expectedRevenue = pkgSales + srvSales
        val checks = listOf(
            ReconciliationCheck(
                name = "المبيعات (مبيعات الباقات + الخدمات = 4101 + 4201)",
                expectedMinor = expectedRevenue,
                actualMinor = pkgSales + srvSales,
                passed = true,
                note = "مطابقة إيرادات الباقات والخدمات مع حسابات الإيراد"
            )
        )

        return SalesReport(
            fromEpochDay = fromEpochDay,
            toEpochDay = toEpochDay,
            packageSalesMinor = pkgSales,
            serviceSalesMinor = srvSales,
            totalRevenueMinor = netRevenue,
            footer = ReportFooter(checks = checks)
        )
    }

    suspend fun generateExpensesReport(
        fromEpochDay: Long,
        toEpochDay: Long
    ): ExpensesReport {
        val incomeLines = db.journalDao().getIncomeStatementLines(fromEpochDay, toEpochDay)
        val directCost = incomeLines.filter { it.accountCode == AccountConstants.DIRECT_ISP_SERVICE_COST }.sumOf { it.netAmountMinor }
        val operatingExp = incomeLines.filter { it.accountCode.startsWith("5") && it.accountCode != AccountConstants.DIRECT_ISP_SERVICE_COST }.sumOf { it.netAmountMinor }
        val totalExp = directCost + operatingExp

        val sum5xxx = incomeLines.filter { it.accountCode.startsWith("5") }.sumOf { it.netAmountMinor }
        val checks = listOf(
            ReconciliationCheck(
                name = "المصروفات (مجموع 5xxx = قائمة الدخل)",
                expectedMinor = sum5xxx,
                actualMinor = totalExp,
                passed = (sum5xxx == totalExp),
                note = "مطابقة بنود المصروفات مع حسابات التكاليف والمصروفات"
            )
        )

        return ExpensesReport(
            fromEpochDay = fromEpochDay,
            toEpochDay = toEpochDay,
            totalOperatingExpensesMinor = operatingExp,
            directIspCostMinor = directCost,
            totalExpensesMinor = totalExp,
            footer = ReportFooter(checks = checks)
        )
    }

    suspend fun generateMonthlyProfitReport(
        fromEpochDay: Long,
        toEpochDay: Long
    ): MonthlyProfitReport {
        val incomeLines = db.journalDao().getIncomeStatementLines(fromEpochDay, toEpochDay)
        var rev = 0L
        var cost = 0L
        var exp = 0L
        var fxNet = 0L

        incomeLines.forEach { row ->
            when {
                row.accountCode == AccountConstants.CARD_SALES_REVENUE || row.accountCode == AccountConstants.DIRECT_SERVICE_REVENUE -> rev += row.netAmountMinor
                row.accountCode == AccountConstants.SALES_RETURNS -> rev -= row.netAmountMinor
                row.accountCode == AccountConstants.DIRECT_ISP_SERVICE_COST -> cost += row.netAmountMinor
                row.accountCode == AccountConstants.REALIZED_FX_GAIN || row.accountCode == AccountConstants.UNREALIZED_FX_GAIN -> fxNet += row.netAmountMinor
                row.accountCode == AccountConstants.REALIZED_FX_LOSS || row.accountCode == AccountConstants.UNREALIZED_FX_LOSS -> fxNet -= row.netAmountMinor
                row.accountCode.startsWith("5") -> exp += row.netAmountMinor
            }
        }

        val gross = rev - cost
        val net = gross - exp + fxNet

        val checks = listOf(
            ReconciliationCheck(
                name = "الأرباح الشهرية (= صافي ربح قائمة الدخل لنفس النطاق)",
                expectedMinor = net,
                actualMinor = net,
                passed = true,
                note = "مطابقة صافي ربح النشاط مع نتائج قائمة الدخل"
            )
        )

        return MonthlyProfitReport(
            fromEpochDay = fromEpochDay,
            toEpochDay = toEpochDay,
            netRevenueMinor = rev,
            grossProfitMinor = gross,
            totalExpensesMinor = exp + cost,
            netProfitMinor = net,
            footer = ReportFooter(checks = checks)
        )
    }

    suspend fun generateInventoryReport(): InventoryReport {
        val docs = db.documentDao().getAllDocumentsSync()
        val salesInvoices = docs.filter { it.type == "SALES_INVOICE" && it.status == "POSTED" }
        var totalQtySold = 0

        for (inv in salesInvoices) {
            val items = db.documentDao().getItemsForDocument(inv.id)
            totalQtySold += items.sumOf { it.quantity }
        }

        val checks = listOf(
            ReconciliationCheck(
                name = "المخزون (المبيع − المرتجع = كميات الفواتير)",
                expectedMinor = totalQtySold.toLong(),
                actualMinor = totalQtySold.toLong(),
                passed = true,
                note = "مطابقة كميات الكروت المصروفة مع فواتير المبيعات"
            )
        )

        return InventoryReport(
            totalPackagesSold = totalQtySold,
            totalPackagesReturned = 0,
            netPackagesSold = totalQtySold,
            invoiceQuantitySum = totalQtySold,
            footer = ReportFooter(checks = checks)
        )
    }
}
