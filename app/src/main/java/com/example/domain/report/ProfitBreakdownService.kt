package com.example.domain.report

import com.example.core.ledger.AccountConstants
import com.example.core.model.CurrencyCode
import com.example.core.time.FiscalCalendar
import com.example.data.local.AppDatabase
import com.example.data.local.dao.IncomeStatementRow
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

data class ProfitBreakdownData(
    val startDateEpochDay: Long,
    val endDateEpochDay: Long,
    val cardRevenueMinor: Long,
    val serviceRevenueMinor: Long,
    val salesReturnsMinor: Long,
    val otherRevenueMinor: Long,
    val netRevenueMinor: Long,
    val directIspCostMinor: Long,
    val otherCostMinor: Long,
    val totalCostOfServiceMinor: Long,
    val grossProfitMinor: Long,
    val grossMarginBps: Long,
    val operatingExpensesMinor: Long,
    val maintenanceExpensesMinor: Long,
    val salariesExpensesMinor: Long,
    val miscExpensesMinor: Long,
    val otherOperatingExpensesMinor: Long,
    val totalOperatingExpensesMinor: Long,
    val depreciationExpenseMinor: Long,
    val realizedFxGainMinor: Long,
    val realizedFxLossMinor: Long,
    val realizedFxNetMinor: Long,
    val realizedOperatingProfitMinor: Long,
    val unrealizedFxGainMinor: Long,
    val unrealizedFxLossMinor: Long,
    val unrealizedFxNetMinor: Long,
    val netProfitMinor: Long,
    val rawRows: List<IncomeStatementRow>
)

data class ComparativeProfitItem(
    val accountCode: String?,
    val title: String,
    val currentMinor: Long,
    val prevMinor: Long,
    val diffMinor: Long = currentMinor - prevMinor,
    val diffPercentText: String,
    val isDeduction: Boolean = false,
    val isBold: Boolean = false,
    val isTotal: Boolean = false,
    val isSection: Boolean = false,
    val isRevenueOrProfit: Boolean = false,
    val drillTargetCurrent: DrillTarget? = null,
    val drillTargetPrev: DrillTarget? = null
)

data class ComparativeProfitReport(
    val currentStartEpochDay: Long,
    val currentEndEpochDay: Long,
    val prevStartEpochDay: Long,
    val prevEndEpochDay: Long,
    val items: List<ComparativeProfitItem>,
    val currentBreakdown: ProfitBreakdownData,
    val prevBreakdown: ProfitBreakdownData,
    val cumulativeFiscalProfitMinor: Long,
    val realizedOperatingProfitMinor: Long,
    val unrealizedFxNetMinor: Long,
    val netProfitMinor: Long,
    val operatingMarginBps: Long,
    val operatingMarginText: String,
    val unrealizedExceeds20Percent: Boolean
)

class ProfitBreakdownService(
    private val db: AppDatabase
) {
    suspend fun calculateBreakdown(
        startDateEpochDay: Long,
        endDateEpochDay: Long
    ): ProfitBreakdownData {
        val rows = db.journalDao().getIncomeStatementLines(startDateEpochDay, endDateEpochDay)

        var cardRevenue = 0L
        var serviceRevenue = 0L
        var salesReturns = 0L
        var otherRevenue = 0L

        var directIspCost = 0L
        var otherCost = 0L

        var operatingExp = 0L
        var maintenanceExp = 0L
        var salariesExp = 0L
        var miscExp = 0L
        var otherOperatingExp = 0L

        var depreciationExp = 0L

        var realizedFxGain = 0L
        var realizedFxLoss = 0L
        var unrealizedFxGain = 0L
        var unrealizedFxLoss = 0L

        for (row in rows) {
            when (row.accountCode) {
                AccountConstants.CARD_SALES_REVENUE -> cardRevenue += row.netAmountMinor
                AccountConstants.DIRECT_SERVICE_REVENUE -> serviceRevenue += row.netAmountMinor
                AccountConstants.SALES_RETURNS -> salesReturns += row.netAmountMinor
                AccountConstants.DIRECT_ISP_SERVICE_COST -> directIspCost += row.netAmountMinor
                AccountConstants.OPERATING_EXPENSES -> operatingExp += row.netAmountMinor
                AccountConstants.MAINTENANCE_SPARES -> maintenanceExp += row.netAmountMinor
                AccountConstants.SALARIES_STAFF -> salariesExp += row.netAmountMinor
                AccountConstants.MISC_EXPENSES -> miscExp += row.netAmountMinor
                AccountConstants.DEPRECIATION_EXPENSE -> depreciationExp += row.netAmountMinor
                AccountConstants.REALIZED_FX_GAIN -> realizedFxGain += row.netAmountMinor
                AccountConstants.REALIZED_FX_LOSS -> realizedFxLoss += row.netAmountMinor
                AccountConstants.UNREALIZED_FX_GAIN -> unrealizedFxGain += row.netAmountMinor
                AccountConstants.UNREALIZED_FX_LOSS -> unrealizedFxLoss += row.netAmountMinor
                else -> {
                    // Any 4xxx or 5xxx account not matched above
                    if (row.accountCode.startsWith("4")) {
                        otherRevenue += row.netAmountMinor
                    } else if (row.accountCode.startsWith("51")) {
                        otherCost += row.netAmountMinor
                    } else if (row.accountCode.startsWith("5")) {
                        otherOperatingExp += row.netAmountMinor
                    }
                }
            }
        }

        val netRevenue = (cardRevenue + serviceRevenue + otherRevenue) - salesReturns
        val totalCostOfService = directIspCost + otherCost
        val grossProfit = netRevenue - totalCostOfService
        val grossMarginBps = if (netRevenue != 0L) {
            (grossProfit * 10000L) / netRevenue
        } else {
            0L
        }

        val totalOperatingExp = operatingExp + maintenanceExp + salariesExp + miscExp + otherOperatingExp
        val realizedFxNet = realizedFxGain - realizedFxLoss
        val realizedOperatingProfit = grossProfit - totalOperatingExp - depreciationExp + realizedFxNet

        val unrealizedFxNet = unrealizedFxGain - unrealizedFxLoss
        val netProfit = realizedOperatingProfit + unrealizedFxNet

        return ProfitBreakdownData(
            startDateEpochDay = startDateEpochDay,
            endDateEpochDay = endDateEpochDay,
            cardRevenueMinor = cardRevenue,
            serviceRevenueMinor = serviceRevenue,
            salesReturnsMinor = salesReturns,
            otherRevenueMinor = otherRevenue,
            netRevenueMinor = netRevenue,
            directIspCostMinor = directIspCost,
            otherCostMinor = otherCost,
            totalCostOfServiceMinor = totalCostOfService,
            grossProfitMinor = grossProfit,
            grossMarginBps = grossMarginBps,
            operatingExpensesMinor = operatingExp,
            maintenanceExpensesMinor = maintenanceExp,
            salariesExpensesMinor = salariesExp,
            miscExpensesMinor = miscExp,
            otherOperatingExpensesMinor = otherOperatingExp,
            totalOperatingExpensesMinor = totalOperatingExp,
            depreciationExpenseMinor = depreciationExp,
            realizedFxGainMinor = realizedFxGain,
            realizedFxLossMinor = realizedFxLoss,
            realizedFxNetMinor = realizedFxNet,
            realizedOperatingProfitMinor = realizedOperatingProfit,
            unrealizedFxGainMinor = unrealizedFxGain,
            unrealizedFxLossMinor = unrealizedFxLoss,
            unrealizedFxNetMinor = unrealizedFxNet,
            netProfitMinor = netProfit,
            rawRows = rows
        )
    }

    suspend fun calculateComparative(
        startDateEpochDay: Long,
        endDateEpochDay: Long
    ): ComparativeProfitReport {
        val (prevStart, prevEnd) = calculatePreviousPeriod(startDateEpochDay, endDateEpochDay)

        val current = calculateBreakdown(startDateEpochDay, endDateEpochDay)
        val prev = calculateBreakdown(prevStart, prevEnd)

        // Cumulative from start of fiscal year
        val fy = FiscalCalendar.DEFAULT.fiscalYearOf(endDateEpochDay)
        val fyStart = FiscalCalendar.DEFAULT.yearStart(fy)
        val cumulativeFiscalProfit = if (fyStart <= endDateEpochDay) {
            calculateBreakdown(fyStart, endDateEpochDay).netProfitMinor
        } else {
            current.netProfitMinor
        }

        val operatingMarginBps = if (current.netRevenueMinor != 0L) {
            (current.realizedOperatingProfitMinor * 10000L) / current.netRevenueMinor
        } else {
            0L
        }
        val operatingMarginText = formatBasisPoints(operatingMarginBps)

        // Unrealized exceeds 20% of absolute total net profit
        val absTotalProfit = Math.abs(current.netProfitMinor)
        val absUnrealized = Math.abs(current.unrealizedFxNetMinor)
        val unrealizedExceeds20Percent = if (absTotalProfit > 0L) {
            (absUnrealized * 100L) / absTotalProfit > 20L
        } else {
            absUnrealized > 0L
        }

        val items = mutableListOf<ComparativeProfitItem>()

        fun addItem(
            code: String?,
            title: String,
            currentVal: Long,
            prevVal: Long,
            isDeduction: Boolean = false,
            isBold: Boolean = false,
            isTotal: Boolean = false,
            isSection: Boolean = false,
            isRevenue: Boolean = false
        ) {
            val drillCurrent = if (code != null && !isTotal && !isSection) {
                DrillTarget.AccountLines(code, startDateEpochDay, endDateEpochDay)
            } else null
            val drillPrev = if (code != null && !isTotal && !isSection) {
                DrillTarget.AccountLines(code, prevStart, prevEnd)
            } else null

            items.add(
                ComparativeProfitItem(
                    accountCode = code,
                    title = title,
                    currentMinor = currentVal,
                    prevMinor = prevVal,
                    diffPercentText = formatPercentChange(currentVal, prevVal),
                    isDeduction = isDeduction,
                    isBold = isBold,
                    isTotal = isTotal,
                    isSection = isSection,
                    isRevenueOrProfit = isRevenue,
                    drillTargetCurrent = drillCurrent,
                    drillTargetPrev = drillPrev
                )
            )
        }

        // 1. Revenues Section
        addItem(null, "الإيرادات التشغيلية", 0L, 0L, isSection = true)
        addItem(AccountConstants.CARD_SALES_REVENUE, "إيرادات الكروت (4101)", current.cardRevenueMinor, prev.cardRevenueMinor, isRevenue = true)
        addItem(AccountConstants.DIRECT_SERVICE_REVENUE, "إيرادات الاشتراكات والخدمات (4201)", current.serviceRevenueMinor, prev.serviceRevenueMinor, isRevenue = true)
        if (current.salesReturnsMinor > 0L || prev.salesReturnsMinor > 0L) {
            addItem(AccountConstants.SALES_RETURNS, "(−) المردودات والمسموحات (4102)", -current.salesReturnsMinor, -prev.salesReturnsMinor, isDeduction = true)
        }
        if (current.otherRevenueMinor > 0L || prev.otherRevenueMinor > 0L) {
            addItem("4999", "إيرادات تشغيلية أخرى (4xxx)", current.otherRevenueMinor, prev.otherRevenueMinor, isRevenue = true)
        }
        addItem(null, "صافي الإيرادات", current.netRevenueMinor, prev.netRevenueMinor, isBold = true, isRevenue = true)

        // 2. Cost of Service Section
        addItem(null, "تكاليف الخدمة والإنترنت", 0L, 0L, isSection = true)
        addItem(AccountConstants.DIRECT_ISP_SERVICE_COST, "تكلفة الخدمة (اشتراكات المنبع 5101)", current.directIspCostMinor, prev.directIspCostMinor)
        if (current.otherCostMinor > 0L || prev.otherCostMinor > 0L) {
            addItem("5199", "تكاليف خدمات أخرى (51xx)", current.otherCostMinor, prev.otherCostMinor)
        }
        addItem(null, "مجمل الربح (${formatBasisPoints(current.grossMarginBps)})", current.grossProfitMinor, prev.grossProfitMinor, isBold = true, isRevenue = true)

        // 3. Operating Expenses Section
        addItem(null, "المصروفات التشغيلية والعمومية", 0L, 0L, isSection = true)
        addItem(AccountConstants.OPERATING_EXPENSES, "مصاريف التشغيل (5201)", current.operatingExpensesMinor, prev.operatingExpensesMinor)
        addItem(AccountConstants.MAINTENANCE_SPARES, "الصيانة وقطع الغيار (5202)", current.maintenanceExpensesMinor, prev.maintenanceExpensesMinor)
        addItem(AccountConstants.SALARIES_STAFF, "الرواتب والأجور (5204)", current.salariesExpensesMinor, prev.salariesExpensesMinor)
        addItem(AccountConstants.MISC_EXPENSES, "مصاريف متنوعة (5299)", current.miscExpensesMinor, prev.miscExpensesMinor)
        if (current.otherOperatingExpensesMinor > 0L || prev.otherOperatingExpensesMinor > 0L) {
            addItem("5298", "مصروفات تشغيلية أخرى (5xxx)", current.otherOperatingExpensesMinor, prev.otherOperatingExpensesMinor)
        }
        addItem(AccountConstants.DEPRECIATION_EXPENSE, "الإهلاك (5203)", current.depreciationExpenseMinor, prev.depreciationExpenseMinor)

        // Realized FX Net
        addItem(null, "فروق العملة المحققة (صافي: 4901 − 5901)", current.realizedFxNetMinor, prev.realizedFxNetMinor, isRevenue = current.realizedFxNetMinor >= 0L)

        // Realized Operating Profit
        addItem(null, "الربح التشغيلي المحقق", current.realizedOperatingProfitMinor, prev.realizedOperatingProfitMinor, isBold = true, isRevenue = true)

        // 4. Unrealized FX in standalone row
        addItem(null, "فروق التقييم غير المحققة (صافي: 4902 − 5902)", current.unrealizedFxNetMinor, prev.unrealizedFxNetMinor, isBold = false, isRevenue = current.unrealizedFxNetMinor >= 0L)

        // 5. Net Profit
        addItem(null, "صافي الربح", current.netProfitMinor, prev.netProfitMinor, isTotal = true, isRevenue = true)

        // 6. Cumulative Fiscal Profit
        addItem(null, "الربح التراكمي من بداية السنة المالية", cumulativeFiscalProfit, 0L, isBold = true, isRevenue = true)

        return ComparativeProfitReport(
            currentStartEpochDay = startDateEpochDay,
            currentEndEpochDay = endDateEpochDay,
            prevStartEpochDay = prevStart,
            prevEndEpochDay = prevEnd,
            items = items,
            currentBreakdown = current,
            prevBreakdown = prev,
            cumulativeFiscalProfitMinor = cumulativeFiscalProfit,
            realizedOperatingProfitMinor = current.realizedOperatingProfitMinor,
            unrealizedFxNetMinor = current.unrealizedFxNetMinor,
            netProfitMinor = current.netProfitMinor,
            operatingMarginBps = operatingMarginBps,
            operatingMarginText = operatingMarginText,
            unrealizedExceeds20Percent = unrealizedExceeds20Percent
        )
    }

    companion object {
        fun calculatePreviousPeriod(fromEpochDay: Long, toEpochDay: Long): Pair<Long, Long> {
            val startDate = LocalDate.ofEpochDay(fromEpochDay)
            val endDate = LocalDate.ofEpochDay(toEpochDay)
            return if (startDate.dayOfMonth == 1 &&
                endDate.dayOfMonth == endDate.lengthOfMonth() &&
                startDate.year == endDate.year &&
                startDate.month == endDate.month
            ) {
                val prevMonth = startDate.minusMonths(1)
                val prevStart = prevMonth.withDayOfMonth(1).toEpochDay()
                val prevEnd = prevMonth.withDayOfMonth(prevMonth.lengthOfMonth()).toEpochDay()
                Pair(prevStart, prevEnd)
            } else {
                val length = toEpochDay - fromEpochDay + 1
                val prevEnd = fromEpochDay - 1
                val prevStart = prevEnd - length + 1
                Pair(prevStart, prevEnd)
            }
        }

        fun formatBasisPoints(bps: Long): String {
            val bd = BigDecimal.valueOf(bps).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
            return "$bd%"
        }

        fun formatPercentChange(currentVal: Long, prevVal: Long): String {
            if (prevVal == 0L) {
                return if (currentVal == 0L) {
                    "0.00%"
                } else if (currentVal > 0L) {
                    "+100.00%"
                } else {
                    "-100.00%"
                }
            }
            val diff = currentVal - prevVal
            val percentBd = BigDecimal.valueOf(diff)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(Math.abs(prevVal)), 2, RoundingMode.HALF_UP)
            val sign = if (percentBd > BigDecimal.ZERO) "+" else ""
            return "$sign$percentBd%"
        }
    }
}
