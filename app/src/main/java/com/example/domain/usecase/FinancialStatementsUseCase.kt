package com.example.domain.usecase

import com.example.core.ledger.AccountConstants
import com.example.core.model.CurrencyCode
import com.example.core.model.Money
import com.example.data.local.AppDatabase
import com.example.data.local.dao.BalanceSheetRow
import com.example.data.local.dao.IncomeStatementRow

data class IncomeStatementReport(
    val startDateEpochDay: Long?,
    val endDateEpochDay: Long?,
    val cardRevenueMinor: Long,
    val serviceRevenueMinor: Long,
    val salesReturnsMinor: Long,
    val netRevenueMinor: Long,
    val directIspCostMinor: Long,
    val grossProfitMinor: Long,
    val operatingExpensesMinor: Long,
    val maintenanceExpensesMinor: Long,
    val salariesExpensesMinor: Long,
    val miscExpensesMinor: Long,
    val totalOperatingExpensesMinor: Long,
    val depreciationExpenseMinor: Long,
    val realizedFxGainMinor: Long,
    val realizedFxLossMinor: Long,
    val netProfitMinor: Long,
    val allLineDetails: List<IncomeStatementRow>
)

data class BalanceSheetReport(
    val asOfDateEpochDay: Long,
    val currentAssetsMinor: Long,
    val fixedAssetsCostMinor: Long,
    val accumulatedDepreciationMinor: Long,
    val netFixedAssetsMinor: Long,
    val totalAssetsMinor: Long,
    val currentLiabilitiesMinor: Long,
    val totalLiabilitiesMinor: Long,
    val capitalMinor: Long,
    val partnerCurrentMinor: Long,
    val retainedEarningsMinor: Long,
    val currentPeriodNetProfitMinor: Long,
    val totalEquityMinor: Long,
    val totalLiabilitiesAndEquityMinor: Long,
    val isBalanced: Boolean,
    val lineDetails: List<BalanceSheetRow>
)

data class AgingBucket(
    val bucketName: String,
    val amountMinor: Long,
    val invoiceCount: Int
)

data class AgingReport(
    val asOfDateEpochDay: Long,
    val totalReceivablesMinor: Long,
    val current0to30: AgingBucket,
    val aging31to60: AgingBucket,
    val aging61to90: AgingBucket,
    val agingOver90: AgingBucket
)

class FinancialStatementsUseCase(private val db: AppDatabase) {

    suspend fun generateIncomeStatement(
        startDateEpochDay: Long? = null,
        endDateEpochDay: Long? = null
    ): IncomeStatementReport {
        val rows = db.journalDao().getIncomeStatementLines(startDateEpochDay, endDateEpochDay)

        var cardRevenue = 0L
        var serviceRevenue = 0L
        var salesReturns = 0L
        var directIspCost = 0L
        var operatingExp = 0L
        var maintenanceExp = 0L
        var salariesExp = 0L
        var miscExp = 0L
        var depreciationExp = 0L
        var realizedFxGain = 0L
        var realizedFxLoss = 0L

        rows.forEach { row ->
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
                else -> {
                    // Include any custom 4xxx or 5xxx accounts
                    if (row.accountCode.startsWith("4")) {
                        serviceRevenue += row.netAmountMinor
                    } else if (row.accountCode.startsWith("5")) {
                        miscExp += row.netAmountMinor
                    }
                }
            }
        }

        val netRevenue = (cardRevenue + serviceRevenue) - salesReturns
        val grossProfit = netRevenue - directIspCost
        val totalOperatingExp = operatingExp + maintenanceExp + salariesExp + miscExp
        val netProfit = grossProfit - totalOperatingExp - depreciationExp + realizedFxGain - realizedFxLoss

        return IncomeStatementReport(
            startDateEpochDay = startDateEpochDay,
            endDateEpochDay = endDateEpochDay,
            cardRevenueMinor = cardRevenue,
            serviceRevenueMinor = serviceRevenue,
            salesReturnsMinor = salesReturns,
            netRevenueMinor = netRevenue,
            directIspCostMinor = directIspCost,
            grossProfitMinor = grossProfit,
            operatingExpensesMinor = operatingExp,
            maintenanceExpensesMinor = maintenanceExp,
            salariesExpensesMinor = salariesExp,
            miscExpensesMinor = miscExp,
            totalOperatingExpensesMinor = totalOperatingExp,
            depreciationExpenseMinor = depreciationExp,
            realizedFxGainMinor = realizedFxGain,
            realizedFxLossMinor = realizedFxLoss,
            netProfitMinor = netProfit,
            allLineDetails = rows
        )
    }

    suspend fun generateBalanceSheet(asOfDateEpochDay: Long): BalanceSheetReport {
        val rows = db.journalDao().getBalanceSheetLines(asOfDateEpochDay)
        val asOfDate = java.time.LocalDate.ofEpochDay(asOfDateEpochDay)
        val startOfFiscalYearEpochDay = java.time.LocalDate.of(asOfDate.year, 1, 1).toEpochDay()
        val incomeReport = generateIncomeStatement(startDateEpochDay = startOfFiscalYearEpochDay, endDateEpochDay = asOfDateEpochDay)

        var cashVault = 0L
        var banksWallets = 0L
        var receivables = 0L
        var otherCurrentAssets = 0L
        var fixedAssetsCost = 0L
        var accumulatedDepreciation = 0L
        var payables = 0L
        var otherLiabilities = 0L
        var capital = 0L
        var partnerCurrent = 0L
        var retainedEarnings = 0L

        rows.forEach { row ->
            when (row.accountCode) {
                AccountConstants.CASH_VAULT -> cashVault += row.netBalanceMinor
                AccountConstants.BANKS_WALLETS -> banksWallets += row.netBalanceMinor
                AccountConstants.ACCOUNTS_RECEIVABLE -> receivables += row.netBalanceMinor
                AccountConstants.FIXED_ASSETS_NETWORK -> fixedAssetsCost += row.netBalanceMinor
                AccountConstants.ACCUMULATED_DEPRECIATION -> accumulatedDepreciation += row.netBalanceMinor
                AccountConstants.ACCOUNTS_PAYABLE -> payables += row.netBalanceMinor
                AccountConstants.CAPITAL -> capital += row.netBalanceMinor
                AccountConstants.PARTNER_CURRENT -> partnerCurrent += row.netBalanceMinor
                AccountConstants.RETAINED_EARNINGS -> retainedEarnings += row.netBalanceMinor
                else -> {
                    if (row.accountCode.startsWith("1")) otherCurrentAssets += row.netBalanceMinor
                    else if (row.accountCode.startsWith("2")) otherLiabilities += row.netBalanceMinor
                }
            }
        }

        val currentAssets = cashVault + banksWallets + receivables + otherCurrentAssets
        val netFixedAssets = fixedAssetsCost - accumulatedDepreciation
        val totalAssets = currentAssets + netFixedAssets

        val currentLiabilities = payables + otherLiabilities
        val totalLiabilities = currentLiabilities

        val currentPeriodProfit = incomeReport.netProfitMinor
        val totalEquity = capital + partnerCurrent + retainedEarnings + currentPeriodProfit
        val totalLiabAndEquity = totalLiabilities + totalEquity

        return BalanceSheetReport(
            asOfDateEpochDay = asOfDateEpochDay,
            currentAssetsMinor = currentAssets,
            fixedAssetsCostMinor = fixedAssetsCost,
            accumulatedDepreciationMinor = accumulatedDepreciation,
            netFixedAssetsMinor = netFixedAssets,
            totalAssetsMinor = totalAssets,
            currentLiabilitiesMinor = currentLiabilities,
            totalLiabilitiesMinor = totalLiabilities,
            capitalMinor = capital,
            partnerCurrentMinor = partnerCurrent,
            retainedEarningsMinor = retainedEarnings,
            currentPeriodNetProfitMinor = currentPeriodProfit,
            totalEquityMinor = totalEquity,
            totalLiabilitiesAndEquityMinor = totalLiabAndEquity,
            isBalanced = (totalAssets == totalLiabAndEquity),
            lineDetails = rows
        )
    }

    suspend fun generateAgingReport(asOfDateEpochDay: Long): AgingReport {
        val docs = db.documentDao().getAllDocumentsSync()
            .filter { it.type == "SALES_INVOICE" && it.status == "POSTED" && it.dateEpochDay <= asOfDateEpochDay }
        val allocations = db.allocationDao().getAllActiveAllocationsSync()
        val allocByInvoice = allocations.groupBy { it.invoiceDocId }

        var b0to30Amt = 0L
        var b0to30Count = 0
        var b31to60Amt = 0L
        var b31to60Count = 0
        var b61to90Amt = 0L
        var b61to90Count = 0
        var b90PlusAmt = 0L
        var b90PlusCount = 0
        var totalOutstanding = 0L

        docs.forEach { inv ->
            val paidMinor = allocByInvoice[inv.id]?.sumOf { it.allocatedBaseMinor } ?: 0L
            val unpaidMinor = inv.totalBaseMinor - paidMinor
            if (unpaidMinor > 0L) {
                totalOutstanding += unpaidMinor
                val ageDays = (asOfDateEpochDay - inv.dateEpochDay).coerceAtLeast(0)
                when {
                    ageDays <= 30 -> {
                        b0to30Amt += unpaidMinor
                        b0to30Count++
                    }
                    ageDays <= 60 -> {
                        b31to60Amt += unpaidMinor
                        b31to60Count++
                    }
                    ageDays <= 90 -> {
                        b61to90Amt += unpaidMinor
                        b61to90Count++
                    }
                    else -> {
                        b90PlusAmt += unpaidMinor
                        b90PlusCount++
                    }
                }
            }
        }

        return AgingReport(
            asOfDateEpochDay = asOfDateEpochDay,
            totalReceivablesMinor = totalOutstanding,
            current0to30 = AgingBucket("0 - 30 يوم (حالي)", b0to30Amt, b0to30Count),
            aging31to60 = AgingBucket("31 - 60 يوم", b31to60Amt, b31to60Count),
            aging61to90 = AgingBucket("61 - 90 يوم", b61to90Amt, b61to90Count),
            agingOver90 = AgingBucket("أكثر من 90 يوماً", b90PlusAmt, b90PlusCount)
        )
    }
}
