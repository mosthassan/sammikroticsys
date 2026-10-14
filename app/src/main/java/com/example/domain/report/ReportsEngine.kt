package com.example.domain.report

import com.example.core.ledger.AccountConstants
import com.example.core.model.CurrencyCode
import com.example.core.time.AppClock
import com.example.data.local.AppDatabase
import com.example.domain.usecase.FinancialStatementsUseCase
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

class ReportsEngine(
    private val db: AppDatabase
) {
    private val financialStatementsUseCase = FinancialStatementsUseCase(db)
    private val profitBreakdownService = ProfitBreakdownService(db)
    private val activityReportsService = ActivityReportsService(db)
    private val auditReportsService = AuditReportsService(db)

    suspend fun generateReport(request: ReportRequest): ReportResult {
        return when (request.type) {
            ReportType.INCOME_STATEMENT -> generateIncomeStatementReport(request)
            ReportType.BALANCE_SHEET -> generateBalanceSheetReport(request)
            ReportType.TRIAL_BALANCE -> generateTrialBalanceReport(request)
            ReportType.AGING_RECEIVABLES -> generateAgingReport(request)
            ReportType.PROFIT_MONTHLY -> generateMonthlyProfitReport(request)
            ReportType.PARTNER_STATEMENT -> generatePartnerStatementReport(request)
            ReportType.PARTNERS_EQUITY_SUMMARY -> generatePartnersEquitySummaryReport(request)
            ReportType.CASH_FLOW -> generateCashFlowReport(request)
            ReportType.DAILY_CASH_REGISTER -> generateDailyCashRegisterReport(request)
            ReportType.SALES_ANALYTICS -> activityReportsService.generateSalesAnalyticsReport(request)
            ReportType.COLLECTIONS_DSO -> activityReportsService.generateCollectionsDsoReport(request)
            ReportType.EXPENSE_ANALYSIS -> activityReportsService.generateExpenseAnalysisReport(request)
            ReportType.CARD_INVENTORY_REPORT -> activityReportsService.generateCardInventoryReport(request)
            ReportType.FIXED_ASSETS_REGISTER -> activityReportsService.generateFixedAssetsRegisterReport(request)
            ReportType.CURRENCY_POSITIONS -> activityReportsService.generateCurrencyPositionsReport(request)
            ReportType.AUDIT_TRAIL -> auditReportsService.generateAuditTrailReport(request)
        }
    }

    private suspend fun generateIncomeStatementReport(request: ReportRequest): ReportResult {
        val rep = financialStatementsUseCase.generateIncomeStatement(
            startDateEpochDay = request.fromEpochDay,
            endDateEpochDay = request.toEpochDay
        )

        val columns = listOf(
            ReportColumn("account", "البند المحاسبي"),
            ReportColumn("amount", "المبلغ (ريال يمني)", ColumnAlignment.END)
        )

        val rows = mutableListOf<ReportRow>()

        // 1. Revenues Section
        rows.add(ReportRow(RowType.SECTION, listOf(ReportCell.TextCell("الإيرادات التشغيلية", isBold = true), ReportCell.TextCell(""))))
        rows.add(ReportRow(RowType.DATA, listOf(
            ReportCell.TextCell("إيرادات مبيعات الكروت (4101)"),
            ReportCell.MoneyCell(rep.cardRevenueMinor, CurrencyCode.FUNCTIONAL, DrillTarget.AccountLines("4101", request.fromEpochDay, request.toEpochDay))
        )))
        rows.add(ReportRow(RowType.DATA, listOf(
            ReportCell.TextCell("إيرادات الاشتراكات المباشرة والخدمات (4201)"),
            ReportCell.MoneyCell(rep.serviceRevenueMinor, CurrencyCode.FUNCTIONAL, DrillTarget.AccountLines("4201", request.fromEpochDay, request.toEpochDay))
        )))
        if (rep.salesReturnsMinor > 0L) {
            rows.add(ReportRow(RowType.DATA, listOf(
                ReportCell.TextCell("(-) مردودات ومسموحات المبيعات (4102)"),
                ReportCell.MoneyCell(-rep.salesReturnsMinor, CurrencyCode.FUNCTIONAL, DrillTarget.AccountLines("4102", request.fromEpochDay, request.toEpochDay))
            )))
        }
        rows.add(ReportRow(RowType.SUBTOTAL, listOf(
            ReportCell.TextCell("صافي الإيرادات", isBold = true),
            ReportCell.MoneyCell(rep.netRevenueMinor, CurrencyCode.FUNCTIONAL)
        )))

        // 2. Cost of Service
        rows.add(ReportRow(RowType.SECTION, listOf(ReportCell.TextCell("تكاليف الخدمة والإنترنت", isBold = true), ReportCell.TextCell(""))))
        rows.add(ReportRow(RowType.DATA, listOf(
            ReportCell.TextCell("تكلفة الخدمة المباشرة - اشتراكات المنبع (5101)"),
            ReportCell.MoneyCell(rep.directIspCostMinor, CurrencyCode.FUNCTIONAL, DrillTarget.AccountLines("5101", request.fromEpochDay, request.toEpochDay))
        )))
        rows.add(ReportRow(RowType.SUBTOTAL, listOf(
            ReportCell.TextCell("مجمل الربح", isBold = true),
            ReportCell.MoneyCell(rep.grossProfitMinor, CurrencyCode.FUNCTIONAL)
        )))

        // 3. Operating Expenses
        rows.add(ReportRow(RowType.SECTION, listOf(ReportCell.TextCell("المصروفات التشغيلية والعمومية", isBold = true), ReportCell.TextCell(""))))
        rows.add(ReportRow(RowType.DATA, listOf(
            ReportCell.TextCell("مصاريف التشغيل (ديزل/كهرباء) (5201)"),
            ReportCell.MoneyCell(rep.operatingExpensesMinor, CurrencyCode.FUNCTIONAL, DrillTarget.AccountLines("5201", request.fromEpochDay, request.toEpochDay))
        )))
        rows.add(ReportRow(RowType.DATA, listOf(
            ReportCell.TextCell("الصيانة وقطع الغيار (5202)"),
            ReportCell.MoneyCell(rep.maintenanceExpensesMinor, CurrencyCode.FUNCTIONAL, DrillTarget.AccountLines("5202", request.fromEpochDay, request.toEpochDay))
        )))
        rows.add(ReportRow(RowType.DATA, listOf(
            ReportCell.TextCell("الرواتب والأجور (5204)"),
            ReportCell.MoneyCell(rep.salariesExpensesMinor, CurrencyCode.FUNCTIONAL, DrillTarget.AccountLines("5204", request.fromEpochDay, request.toEpochDay))
        )))
        rows.add(ReportRow(RowType.DATA, listOf(
            ReportCell.TextCell("مصاريف متنوعة (5299)"),
            ReportCell.MoneyCell(rep.miscExpensesMinor, CurrencyCode.FUNCTIONAL, DrillTarget.AccountLines("5299", request.fromEpochDay, request.toEpochDay))
        )))
        rows.add(ReportRow(RowType.DATA, listOf(
            ReportCell.TextCell("إهلاك الأصول والمعدات (5203)"),
            ReportCell.MoneyCell(rep.depreciationExpenseMinor, CurrencyCode.FUNCTIONAL, DrillTarget.AccountLines("5203", request.fromEpochDay, request.toEpochDay))
        )))

        // 4. Currency Differences
        if (rep.realizedFxGainMinor > 0L || rep.realizedFxLossMinor > 0L) {
            rows.add(ReportRow(RowType.SECTION, listOf(ReportCell.TextCell("فروق أسعار العملات المحققة", isBold = true), ReportCell.TextCell(""))))
            if (rep.realizedFxGainMinor > 0L) {
                rows.add(ReportRow(RowType.DATA, listOf(
                    ReportCell.TextCell("أرباح فروق العملة المحققة (4901)"),
                    ReportCell.MoneyCell(rep.realizedFxGainMinor, CurrencyCode.FUNCTIONAL, DrillTarget.AccountLines("4901", request.fromEpochDay, request.toEpochDay))
                )))
            }
            if (rep.realizedFxLossMinor > 0L) {
                rows.add(ReportRow(RowType.DATA, listOf(
                    ReportCell.TextCell("(-) خسائر فروق العملة المحققة (5901)"),
                    ReportCell.MoneyCell(-rep.realizedFxLossMinor, CurrencyCode.FUNCTIONAL, DrillTarget.AccountLines("5901", request.fromEpochDay, request.toEpochDay))
                )))
            }
        }

        if (rep.unrealizedFxGainMinor > 0L || rep.unrealizedFxLossMinor > 0L) {
            rows.add(ReportRow(RowType.SECTION, listOf(ReportCell.TextCell("فروق التقييم غير المحققة", isBold = true), ReportCell.TextCell(""))))
            val unrealizedNet = rep.unrealizedFxGainMinor - rep.unrealizedFxLossMinor
            rows.add(ReportRow(RowType.DATA, listOf(
                ReportCell.TextCell("صافي فروق تقييم العملة غير المحققة (4902 − 5902)"),
                ReportCell.MoneyCell(unrealizedNet, CurrencyCode.FUNCTIONAL)
            )))
        }

        // Net Profit Total
        rows.add(ReportRow(RowType.TOTAL, listOf(
            ReportCell.TextCell("صافي الربح / (الخسارة)", isBold = true),
            ReportCell.MoneyCell(rep.netProfitMinor, CurrencyCode.FUNCTIONAL)
        )))

        val fromStr = LocalDate.ofEpochDay(request.fromEpochDay).toString()
        val toStr = LocalDate.ofEpochDay(request.toEpochDay).toString()
        val table = ReportTable(
            title = "قائمة الدخل والأرباح",
            subtitle = "من $fromStr إلى $toStr",
            columns = columns,
            rows = rows
        )

        val footer = buildReportFooter(table, "الفترة: $fromStr إلى $toStr", rep.allLineDetails.size, true)
        return ReportResult(table, footer)
    }

    private suspend fun generateMonthlyProfitReport(request: ReportRequest): ReportResult {
        val rep = profitBreakdownService.calculateComparative(
            startDateEpochDay = request.fromEpochDay,
            endDateEpochDay = request.toEpochDay
        )

        val fromStr = LocalDate.ofEpochDay(request.fromEpochDay).toString()
        val toStr = LocalDate.ofEpochDay(request.toEpochDay).toString()
        val prevFromStr = LocalDate.ofEpochDay(rep.prevStartEpochDay).toString()
        val prevToStr = LocalDate.ofEpochDay(rep.prevEndEpochDay).toString()

        val columns = listOf(
            ReportColumn("line", "البند المحاسبي"),
            ReportColumn("current", "الشهر الحالي (ريال)", ColumnAlignment.END),
            ReportColumn("prev", "الشهر السابق (ريال)", ColumnAlignment.END),
            ReportColumn("diff", "الفرق (ريال)", ColumnAlignment.END),
            ReportColumn("percent", "نسبة التغير %", ColumnAlignment.CENTER)
        )

        val rows = mutableListOf<ReportRow>()

        for (item in rep.items) {
            if (item.isSection) {
                rows.add(
                    ReportRow(
                        type = RowType.SECTION,
                        cells = listOf(
                            ReportCell.TextCell(item.title, isBold = true),
                            ReportCell.TextCell(""),
                            ReportCell.TextCell(""),
                            ReportCell.TextCell(""),
                            ReportCell.TextCell("")
                        )
                    )
                )
            } else {
                val rowType = if (item.isTotal) {
                    RowType.TOTAL
                } else if (item.isBold) {
                    RowType.SUBTOTAL
                } else {
                    RowType.DATA
                }

                rows.add(
                    ReportRow(
                        type = rowType,
                        cells = listOf(
                            ReportCell.TextCell(item.title, isBold = item.isBold || item.isTotal),
                            ReportCell.MoneyCell(item.currentMinor, CurrencyCode.FUNCTIONAL, item.drillTargetCurrent),
                            ReportCell.MoneyCell(item.prevMinor, CurrencyCode.FUNCTIONAL, item.drillTargetPrev),
                            ReportCell.MoneyCell(item.diffMinor, CurrencyCode.FUNCTIONAL),
                            ReportCell.TextCell(item.diffPercentText, isBold = item.isBold || item.isTotal)
                        )
                    )
                )
            }
        }

        val table = ReportTable(
            title = "الأرباح الشهرية وقائمة الدخل المقارنة",
            subtitle = "الشهر الحالي: $fromStr إلى $toStr | السابق: $prevFromStr إلى $prevToStr",
            columns = columns,
            rows = rows
        )

        val note = buildString {
            append("صافي الربح التشغيلي المحقق: ")
            append(rep.realizedOperatingProfitMinor)
            append(" ر.ي | الهامش: ")
            append(rep.operatingMarginText)
            if (rep.unrealizedExceeds20Percent) {
                append(" | تنبيه رقابي: فروق التقييم غير المحققة (${rep.unrealizedFxNetMinor} ر.ي) تتجاوز 20% من إجمالي الربح")
            }
        }

        val footer = buildReportFooter(
            table = table,
            params = "الفترة: $fromStr إلى $toStr (مقارن مع $prevFromStr إلى $prevToStr)",
            entriesCount = rep.currentBreakdown.rawRows.size + rep.prevBreakdown.rawRows.size,
            reconciled = true,
            reconcileNote = note
        )

        return ReportResult(table, footer)
    }

    private suspend fun generatePartnerStatementReport(request: ReportRequest): ReportResult {
        val partnerId = request.params["partyId"] ?: db.partyDao().getPartnersSync().firstOrNull()?.id
        if (partnerId == null) {
            return generateGenericPlaceholderReport(request)
        }

        val partner = db.partyDao().getPartyById(partnerId)
        val partnerName = partner?.name ?: "شريك"

        val capOpening = db.journalDao().getPartnerOpeningBalance(partnerId, AccountConstants.CAPITAL, request.fromEpochDay)
        val curOpening = db.journalDao().getPartnerOpeningBalance(partnerId, AccountConstants.PARTNER_CURRENT, request.fromEpochDay)
        val totalOpening = capOpening + curOpening

        val capLines = db.journalDao().getDrillDownLinesForAccount(AccountConstants.CAPITAL, request.fromEpochDay, request.toEpochDay, partnerId)
        val curLines = db.journalDao().getDrillDownLinesForAccount(AccountConstants.PARTNER_CURRENT, request.fromEpochDay, request.toEpochDay, partnerId)

        val combinedLines = (capLines + curLines).sortedWith(
            compareBy<com.example.data.local.dao.DrillDownLineRow> { it.dateEpochDay }.thenBy { it.entryNumber }
        )

        val columns = listOf(
            ReportColumn("date", "التاريخ"),
            ReportColumn("doc", "رقم السند/القيد"),
            ReportColumn("account", "الحساب"),
            ReportColumn("memo", "البيان"),
            ReportColumn("debit", "مسحوبات / مدين (ريال)", ColumnAlignment.END),
            ReportColumn("credit", "مساهمات / دائن (ريال)", ColumnAlignment.END),
            ReportColumn("balance", "الرصيد التراكمي (ريال)", ColumnAlignment.END)
        )

        val rows = mutableListOf<ReportRow>()

        // Opening Balance Row
        rows.add(
            ReportRow(
                type = RowType.SUBTOTAL,
                cells = listOf(
                    ReportCell.DateCell(request.fromEpochDay),
                    ReportCell.TextCell("—"),
                    ReportCell.TextCell("رصيد افتتاحي"),
                    ReportCell.TextCell("رصيد أول المدة المنقول"),
                    ReportCell.TextCell(""),
                    ReportCell.TextCell(""),
                    ReportCell.MoneyCell(totalOpening, CurrencyCode.FUNCTIONAL)
                )
            )
        )

        var running = totalOpening
        var totalDebit = 0L
        var totalCredit = 0L

        for (line in combinedLines) {
            running += (line.baseCreditMinor - line.baseDebitMinor)
            totalDebit += line.baseDebitMinor
            totalCredit += line.baseCreditMinor

            val accName = if (line.accountCode == AccountConstants.CAPITAL) "رأس المال (3101)" else "جاري الشريك (3201)"
            val docLabel = "${line.docType ?: "قيد"} #${line.docNumber ?: line.entryNumber}"

            rows.add(
                ReportRow(
                    type = RowType.DATA,
                    cells = listOf(
                        ReportCell.DateCell(line.dateEpochDay),
                        ReportCell.TextCell(docLabel),
                        ReportCell.TextCell(accName),
                        ReportCell.TextCell(line.memo),
                        ReportCell.MoneyCell(line.baseDebitMinor, CurrencyCode.FUNCTIONAL, DrillTarget.EntryLines(line.entryId)),
                        ReportCell.MoneyCell(line.baseCreditMinor, CurrencyCode.FUNCTIONAL, DrillTarget.EntryLines(line.entryId)),
                        ReportCell.MoneyCell(running, CurrencyCode.FUNCTIONAL)
                    )
                )
            )
        }

        // Closing Balance Row
        rows.add(
            ReportRow(
                type = RowType.TOTAL,
                cells = listOf(
                    ReportCell.TextCell("الإجمالي ورصيد آخر المدة", isBold = true),
                    ReportCell.TextCell(""),
                    ReportCell.TextCell(""),
                    ReportCell.TextCell(""),
                    ReportCell.MoneyCell(totalDebit, CurrencyCode.FUNCTIONAL),
                    ReportCell.MoneyCell(totalCredit, CurrencyCode.FUNCTIONAL),
                    ReportCell.MoneyCell(running, CurrencyCode.FUNCTIONAL)
                )
            )
        )

        val fromStr = LocalDate.ofEpochDay(request.fromEpochDay).toString()
        val toStr = LocalDate.ofEpochDay(request.toEpochDay).toString()
        val table = ReportTable(
            title = "كشف حركة الشريك: $partnerName",
            subtitle = "من $fromStr إلى $toStr | هاتف: ${partner?.phone ?: "—"}",
            columns = columns,
            rows = rows
        )

        val footer = buildReportFooter(
            table = table,
            params = "الشريك: $partnerName ($partnerId) للفترة: $fromStr إلى $toStr",
            entriesCount = combinedLines.size,
            reconciled = true
        )

        return ReportResult(table, footer)
    }

    private suspend fun generatePartnersEquitySummaryReport(request: ReportRequest): ReportResult {
        val activePartners = db.partyDao().getPartnersSync().filter { it.isActive }
        val org = db.organizationDao().getOrganizationSync()
        val mode = org?.equityShareMode ?: "DERIVED_FROM_CAPITAL"
        val totalCapital = db.journalDao().getTotalCapitalBalanceSync()
        val unclosedProfit = db.journalDao().getUnclosedNominalBalance(request.toEpochDay)

        val columns = listOf(
            ReportColumn("partner", "اسم الشريك"),
            ReportColumn("share", "نسبة الملكية %", ColumnAlignment.CENTER),
            ReportColumn("capital", "رأس المال (3101)", ColumnAlignment.END),
            ReportColumn("current", "جاري الشريك (3201)", ColumnAlignment.END),
            ReportColumn("profit", "نصيب أرباح الفترة (استرشادي)", ColumnAlignment.END),
            ReportColumn("totalEquity", "إجمالي حقوق الملكية (ريال)", ColumnAlignment.END)
        )

        val rows = mutableListOf<ReportRow>()

        var sumCapital = 0L
        var sumCurrent = 0L
        var sumProfitShare = 0L
        var sumTotalEquity = 0L

        for (partner in activePartners) {
            val partnerCap = db.journalDao().getPartnerBalanceAsOf(partner.id, AccountConstants.CAPITAL, request.toEpochDay)
            val partnerCur = db.journalDao().getPartnerBalanceAsOf(partner.id, AccountConstants.PARTNER_CURRENT, request.toEpochDay)

            val bps = if (mode == "FIXED_AGREED") {
                partner.equityPercentageBasisPoints
            } else {
                if (totalCapital > 0L) ((partnerCap * 10000L) / totalCapital).toInt() else 0
            }

            val sharePctText = "${BigDecimal.valueOf(bps.toLong()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)}%"

            val partnerProfitShare = if (mode == "FIXED_AGREED") {
                (unclosedProfit * bps.toLong()) / 10000L
            } else {
                if (totalCapital > 0L) (unclosedProfit * partnerCap) / totalCapital else 0L
            }

            val partnerTotalEquity = partnerCap + partnerCur + partnerProfitShare

            sumCapital += partnerCap
            sumCurrent += partnerCur
            sumProfitShare += partnerProfitShare
            sumTotalEquity += partnerTotalEquity

            rows.add(
                ReportRow(
                    type = RowType.DATA,
                    cells = listOf(
                        ReportCell.TextCell(partner.name),
                        ReportCell.TextCell(sharePctText),
                        ReportCell.MoneyCell(partnerCap, CurrencyCode.FUNCTIONAL, DrillTarget.AccountLines(AccountConstants.CAPITAL, 0L, request.toEpochDay, partner.id)),
                        ReportCell.MoneyCell(partnerCur, CurrencyCode.FUNCTIONAL, DrillTarget.AccountLines(AccountConstants.PARTNER_CURRENT, 0L, request.toEpochDay, partner.id)),
                        ReportCell.MoneyCell(partnerProfitShare, CurrencyCode.FUNCTIONAL),
                        ReportCell.MoneyCell(partnerTotalEquity, CurrencyCode.FUNCTIONAL)
                    )
                )
            )
        }

        // Total Row
        rows.add(
            ReportRow(
                type = RowType.TOTAL,
                cells = listOf(
                    ReportCell.TextCell("الإجمالي العام", isBold = true),
                    ReportCell.TextCell("100.00%"),
                    ReportCell.MoneyCell(sumCapital, CurrencyCode.FUNCTIONAL),
                    ReportCell.MoneyCell(sumCurrent, CurrencyCode.FUNCTIONAL),
                    ReportCell.MoneyCell(sumProfitShare, CurrencyCode.FUNCTIONAL),
                    ReportCell.MoneyCell(sumTotalEquity, CurrencyCode.FUNCTIONAL)
                )
            )
        )

        val asOfStr = LocalDate.ofEpochDay(request.toEpochDay).toString()
        val table = ReportTable(
            title = "ملخص الشركاء وقائمة التغير في حقوق الملكية",
            subtitle = "كما في تاريخ: $asOfStr | نظام الملكية: $mode",
            columns = columns,
            rows = rows
        )

        val footer = buildReportFooter(
            table = table,
            params = "كما في $asOfStr | عدد الشركاء: ${activePartners.size}",
            entriesCount = activePartners.size,
            reconciled = true,
            reconcileNote = "أرباح الفترة غير المقفلة: $unclosedProfit ر.ي (توزيع استرشادي للعلم فقط بدون أي قيد)"
        )

        return ReportResult(table, footer)
    }

    private suspend fun generateCashFlowReport(request: ReportRequest): ReportResult {
        val begCash = db.journalDao().getCashOpeningBalance(null, request.fromEpochDay)
        val endCash = db.journalDao().getCashOpeningBalance(null, request.toEpochDay + 1)
        val cashLines = db.journalDao().getCashRegisterLines(null, request.fromEpochDay, request.toEpochDay)

        var customerReceipts = 0L
        var ispSupplierPayments = 0L
        var operatingExpensesPayments = 0L
        var otherOperatingCash = 0L

        var capitalAssetPurchases = 0L

        var capitalContributions = 0L
        var partnerDrawingsAndDividends = 0L

        val distinctEntryIds = cashLines.map { it.entryId }.distinct()

        for (entryId in distinctEntryIds) {
            val lines = db.journalDao().getLinesForEntry(entryId)
            val cashInThisEntry = lines.filter { it.accountCode.startsWith("11") }
            val netCashChange = cashInThisEntry.sumOf { it.baseDebitMinor - it.baseCreditMinor }

            if (netCashChange == 0L) continue // Internal treasury transfer

            val otherLines = lines.filter { !it.accountCode.startsWith("11") }

            for (other in otherLines) {
                val code = other.accountCode
                when {
                    code == AccountConstants.ACCOUNTS_RECEIVABLE || code.startsWith("4") -> {
                        customerReceipts += (other.baseCreditMinor - other.baseDebitMinor)
                    }
                    code == AccountConstants.ACCOUNTS_PAYABLE || code == AccountConstants.DIRECT_ISP_SERVICE_COST -> {
                        ispSupplierPayments += (other.baseDebitMinor - other.baseCreditMinor)
                    }
                    code.startsWith("52") -> {
                        operatingExpensesPayments += (other.baseDebitMinor - other.baseCreditMinor)
                    }
                    code == AccountConstants.FIXED_ASSETS_NETWORK || code.startsWith("15") -> {
                        capitalAssetPurchases += (other.baseDebitMinor - other.baseCreditMinor)
                    }
                    code == AccountConstants.CAPITAL -> {
                        capitalContributions += (other.baseCreditMinor - other.baseDebitMinor)
                    }
                    code == AccountConstants.PARTNER_CURRENT || code == AccountConstants.RETAINED_EARNINGS -> {
                        partnerDrawingsAndDividends += (other.baseDebitMinor - other.baseCreditMinor)
                    }
                    else -> {
                        otherOperatingCash += (other.baseCreditMinor - other.baseDebitMinor)
                    }
                }
            }
        }

        val netOperatingCash = (customerReceipts + otherOperatingCash) - (ispSupplierPayments + operatingExpensesPayments)
        val netInvestingCash = -capitalAssetPurchases
        val netFinancingCash = capitalContributions - partnerDrawingsAndDividends
        val totalNetCashChange = netOperatingCash + netInvestingCash + netFinancingCash

        val columns = listOf(
            ReportColumn("item", "بند التدفق النقدي"),
            ReportColumn("amount", "المبلغ (ريال يمني)", ColumnAlignment.END)
        )

        val rows = mutableListOf<ReportRow>()

        // 1. Operating
        rows.add(ReportRow(RowType.SECTION, listOf(ReportCell.TextCell("1. التدفقات النقدية من الأنشطة التشغيلية", isBold = true), ReportCell.TextCell(""))))
        rows.add(ReportRow(RowType.DATA, listOf(
            ReportCell.TextCell("المقبوضات النقدية من مبيعات الكروت والعملاء"),
            ReportCell.MoneyCell(customerReceipts, CurrencyCode.FUNCTIONAL)
        )))
        rows.add(ReportRow(RowType.DATA, listOf(
            ReportCell.TextCell("(−) المدفوعات النقدية لمزودي الإنترنت والخدمة (5101/2101)"),
            ReportCell.MoneyCell(-ispSupplierPayments, CurrencyCode.FUNCTIONAL)
        )))
        rows.add(ReportRow(RowType.DATA, listOf(
            ReportCell.TextCell("(−) المدفوعات النقدية للمصروفات التشغيلية والرواتب (52xx)"),
            ReportCell.MoneyCell(-operatingExpensesPayments, CurrencyCode.FUNCTIONAL)
        )))
        if (otherOperatingCash != 0L) {
            rows.add(ReportRow(RowType.DATA, listOf(
                ReportCell.TextCell("تدفقات نقدية تشغيلية أخرى"),
                ReportCell.MoneyCell(otherOperatingCash, CurrencyCode.FUNCTIONAL)
            )))
        }
        rows.add(ReportRow(RowType.SUBTOTAL, listOf(
            ReportCell.TextCell("صافي التدفقات النقدية التشغيلية", isBold = true),
            ReportCell.MoneyCell(netOperatingCash, CurrencyCode.FUNCTIONAL)
        )))

        // 2. Investing
        rows.add(ReportRow(RowType.SECTION, listOf(ReportCell.TextCell("2. التدفقات النقدية من الأنشطة الاستثمارية", isBold = true), ReportCell.TextCell(""))))
        rows.add(ReportRow(RowType.DATA, listOf(
            ReportCell.TextCell("(−) شراء أصول ومعدات وتوسعة الشبكة (1501)"),
            ReportCell.MoneyCell(-capitalAssetPurchases, CurrencyCode.FUNCTIONAL, DrillTarget.AccountLines(AccountConstants.FIXED_ASSETS_NETWORK, request.fromEpochDay, request.toEpochDay))
        )))
        rows.add(ReportRow(RowType.SUBTOTAL, listOf(
            ReportCell.TextCell("صافي التدفقات النقدية الاستثمارية", isBold = true),
            ReportCell.MoneyCell(netInvestingCash, CurrencyCode.FUNCTIONAL)
        )))

        // 3. Financing
        rows.add(ReportRow(RowType.SECTION, listOf(ReportCell.TextCell("3. التدفقات النقدية من الأنشطة التمويلية", isBold = true), ReportCell.TextCell(""))))
        rows.add(ReportRow(RowType.DATA, listOf(
            ReportCell.TextCell("مقبوضات مساهمات رأس المال من الشركاء (3101)"),
            ReportCell.MoneyCell(capitalContributions, CurrencyCode.FUNCTIONAL, DrillTarget.AccountLines(AccountConstants.CAPITAL, request.fromEpochDay, request.toEpochDay))
        )))
        rows.add(ReportRow(RowType.DATA, listOf(
            ReportCell.TextCell("(−) مسحوبات الشركاء وتوزيعات الأرباح النقدية (3201)"),
            ReportCell.MoneyCell(-partnerDrawingsAndDividends, CurrencyCode.FUNCTIONAL, DrillTarget.AccountLines(AccountConstants.PARTNER_CURRENT, request.fromEpochDay, request.toEpochDay))
        )))
        rows.add(ReportRow(RowType.SUBTOTAL, listOf(
            ReportCell.TextCell("صافي التدفقات النقدية التمويلية", isBold = true),
            ReportCell.MoneyCell(netFinancingCash, CurrencyCode.FUNCTIONAL)
        )))

        // 4. Reconciliation
        rows.add(ReportRow(RowType.SECTION, listOf(ReportCell.TextCell("مطابقة النقدية وما في حكمها", isBold = true), ReportCell.TextCell(""))))
        rows.add(ReportRow(RowType.DATA, listOf(
            ReportCell.TextCell("صافي التغير في النقدية خلال الفترة"),
            ReportCell.MoneyCell(totalNetCashChange, CurrencyCode.FUNCTIONAL)
        )))
        rows.add(ReportRow(RowType.DATA, listOf(
            ReportCell.TextCell("رصيد النقدية في بداية الفترة"),
            ReportCell.MoneyCell(begCash, CurrencyCode.FUNCTIONAL)
        )))
        rows.add(ReportRow(RowType.TOTAL, listOf(
            ReportCell.TextCell("رصيد النقدية في نهاية الفترة", isBold = true),
            ReportCell.MoneyCell(endCash, CurrencyCode.FUNCTIONAL)
        )))

        val fromStr = LocalDate.ofEpochDay(request.fromEpochDay).toString()
        val toStr = LocalDate.ofEpochDay(request.toEpochDay).toString()
        val table = ReportTable(
            title = "قائمة التدفقات النقدية (الطريقة المباشرة)",
            subtitle = "من $fromStr إلى $toStr",
            columns = columns,
            rows = rows
        )

        val isBalanced = (endCash - begCash) == totalNetCashChange
        val footer = buildReportFooter(
            table = table,
            params = "الفترة: $fromStr إلى $toStr",
            entriesCount = distinctEntryIds.size,
            reconciled = isBalanced,
            reconcileNote = if (!isBalanced) "تفاوت مطابقة النقدية: ${(endCash - begCash) - totalNetCashChange} minor" else null
        )

        return ReportResult(table, footer)
    }

    private suspend fun generateDailyCashRegisterReport(request: ReportRequest): ReportResult {
        val treasuryId = request.params["treasuryId"]
        val treasuryName = if (!treasuryId.isNullOrBlank()) {
            db.treasuryDao().getTreasuryById(treasuryId)?.name ?: "خزينة $treasuryId"
        } else {
            "كافة الصناديق والخزائن"
        }

        val begCash = db.journalDao().getCashOpeningBalance(treasuryId, request.fromEpochDay)
        val lines = db.journalDao().getCashRegisterLines(treasuryId, request.fromEpochDay, request.toEpochDay)

        val columns = listOf(
            ReportColumn("date", "التاريخ"),
            ReportColumn("doc", "رقم السند/القيد"),
            ReportColumn("memo", "البيان"),
            ReportColumn("orig", "المبلغ الأصلي", ColumnAlignment.END),
            ReportColumn("debit", "مقبوضات / مدين (ريال)", ColumnAlignment.END),
            ReportColumn("credit", "مدفوعات / دائن (ريال)", ColumnAlignment.END),
            ReportColumn("balance", "الرصيد التراكمي (ريال)", ColumnAlignment.END)
        )

        val rows = mutableListOf<ReportRow>()

        // Opening Balance Row
        rows.add(
            ReportRow(
                type = RowType.SUBTOTAL,
                cells = listOf(
                    ReportCell.DateCell(request.fromEpochDay),
                    ReportCell.TextCell("—"),
                    ReportCell.TextCell("رصيد أول المدة المنقول"),
                    ReportCell.TextCell(""),
                    ReportCell.TextCell(""),
                    ReportCell.TextCell(""),
                    ReportCell.MoneyCell(begCash, CurrencyCode.FUNCTIONAL)
                )
            )
        )

        var running = begCash
        var totalDebit = 0L
        var totalCredit = 0L

        for (line in lines) {
            running += (line.baseDebitMinor - line.baseCreditMinor)
            totalDebit += line.baseDebitMinor
            totalCredit += line.baseCreditMinor

            val docLabel = "${line.docType ?: "قيد"} #${line.docNumber ?: line.entryNumber}"

            rows.add(
                ReportRow(
                    type = RowType.DATA,
                    cells = listOf(
                        ReportCell.DateCell(line.dateEpochDay),
                        ReportCell.TextCell(docLabel),
                        ReportCell.TextCell(line.memo),
                        ReportCell.MoneyCell(line.origAmountMinor, CurrencyCode.fromString(line.currency)),
                        ReportCell.MoneyCell(line.baseDebitMinor, CurrencyCode.FUNCTIONAL, DrillTarget.EntryLines(line.entryId)),
                        ReportCell.MoneyCell(line.baseCreditMinor, CurrencyCode.FUNCTIONAL, DrillTarget.EntryLines(line.entryId)),
                        ReportCell.MoneyCell(running, CurrencyCode.FUNCTIONAL)
                    )
                )
            )
        }

        // Closing Balance Row
        rows.add(
            ReportRow(
                type = RowType.TOTAL,
                cells = listOf(
                    ReportCell.TextCell("الإجمالي ورصيد آخر المدة", isBold = true),
                    ReportCell.TextCell(""),
                    ReportCell.TextCell(""),
                    ReportCell.TextCell(""),
                    ReportCell.MoneyCell(totalDebit, CurrencyCode.FUNCTIONAL),
                    ReportCell.MoneyCell(totalCredit, CurrencyCode.FUNCTIONAL),
                    ReportCell.MoneyCell(running, CurrencyCode.FUNCTIONAL)
                )
            )
        )

        val fromStr = LocalDate.ofEpochDay(request.fromEpochDay).toString()
        val toStr = LocalDate.ofEpochDay(request.toEpochDay).toString()
        val table = ReportTable(
            title = "سجل الصندوق اليومي: $treasuryName",
            subtitle = "من $fromStr إلى $toStr",
            columns = columns,
            rows = rows
        )

        val footer = buildReportFooter(
            table = table,
            params = "الخزينة: $treasuryName | الفترة: $fromStr إلى $toStr",
            entriesCount = lines.size,
            reconciled = true
        )

        return ReportResult(table, footer)
    }

    private suspend fun generateBalanceSheetReport(request: ReportRequest): ReportResult {
        val rep = financialStatementsUseCase.generateBalanceSheet(request.toEpochDay)

        val columns = listOf(
            ReportColumn("item", "البند المالي"),
            ReportColumn("amount", "المبلغ (ريال يمني)", ColumnAlignment.END)
        )

        val rows = mutableListOf<ReportRow>()

        // Assets
        rows.add(ReportRow(RowType.SECTION, listOf(ReportCell.TextCell("الأصول (الموجودات)", isBold = true), ReportCell.TextCell(""))))
        rows.add(ReportRow(RowType.DATA, listOf(
            ReportCell.TextCell("الأصول المتداولة (النقدية والمدينون)"),
            ReportCell.MoneyCell(rep.currentAssetsMinor, CurrencyCode.FUNCTIONAL)
        )))
        rows.add(ReportRow(RowType.DATA, listOf(
            ReportCell.TextCell("الأصول الثابتة بالتكلفة التاريخية (1501)"),
            ReportCell.MoneyCell(rep.fixedAssetsCostMinor, CurrencyCode.FUNCTIONAL, DrillTarget.AccountLines("1501", 0L, request.toEpochDay))
        )))
        rows.add(ReportRow(RowType.DATA, listOf(
            ReportCell.TextCell("(-) مجمع الإهلاك المتراكم (1599)"),
            ReportCell.MoneyCell(-rep.accumulatedDepreciationMinor, CurrencyCode.FUNCTIONAL, DrillTarget.AccountLines("1599", 0L, request.toEpochDay))
        )))
        rows.add(ReportRow(RowType.SUBTOTAL, listOf(
            ReportCell.TextCell("صافي الأصول الثابتة", isBold = true),
            ReportCell.MoneyCell(rep.netFixedAssetsMinor, CurrencyCode.FUNCTIONAL)
        )))
        rows.add(ReportRow(RowType.TOTAL, listOf(
            ReportCell.TextCell("إجمالي الأصول", isBold = true),
            ReportCell.MoneyCell(rep.totalAssetsMinor, CurrencyCode.FUNCTIONAL)
        )))

        // Liabilities & Equity
        rows.add(ReportRow(RowType.SECTION, listOf(ReportCell.TextCell("الالتزامات وحقوق الملكية", isBold = true), ReportCell.TextCell(""))))
        rows.add(ReportRow(RowType.DATA, listOf(
            ReportCell.TextCell("الالتزامات المتداولة (الموردون 2101)"),
            ReportCell.MoneyCell(rep.totalLiabilitiesMinor, CurrencyCode.FUNCTIONAL, DrillTarget.AccountLines("2101", 0L, request.toEpochDay))
        )))
        rows.add(ReportRow(RowType.DATA, listOf(
            ReportCell.TextCell("رأس المال (3101)"),
            ReportCell.MoneyCell(rep.capitalMinor, CurrencyCode.FUNCTIONAL, DrillTarget.AccountLines("3101", 0L, request.toEpochDay))
        )))
        rows.add(ReportRow(RowType.DATA, listOf(
            ReportCell.TextCell("جاري الشركاء (3201)"),
            ReportCell.MoneyCell(rep.partnerCurrentMinor, CurrencyCode.FUNCTIONAL, DrillTarget.AccountLines("3201", 0L, request.toEpochDay))
        )))
        rows.add(ReportRow(RowType.DATA, listOf(
            ReportCell.TextCell("الأرباح المرحّلة (3301)"),
            ReportCell.MoneyCell(rep.retainedEarningsMinor, CurrencyCode.FUNCTIONAL, DrillTarget.AccountLines("3301", 0L, request.toEpochDay))
        )))
        rows.add(ReportRow(RowType.DATA, listOf(
            ReportCell.TextCell("أرباح الفترة غير المقفلة"),
            ReportCell.MoneyCell(rep.currentPeriodNetProfitMinor, CurrencyCode.FUNCTIONAL)
        )))
        rows.add(ReportRow(RowType.SUBTOTAL, listOf(
            ReportCell.TextCell("إجمالي حقوق الملكية", isBold = true),
            ReportCell.MoneyCell(rep.totalEquityMinor, CurrencyCode.FUNCTIONAL)
        )))
        rows.add(ReportRow(RowType.TOTAL, listOf(
            ReportCell.TextCell("إجمالي الالتزامات وحقوق الملكية", isBold = true),
            ReportCell.MoneyCell(rep.totalLiabilitiesAndEquityMinor, CurrencyCode.FUNCTIONAL)
        )))

        val asOfStr = LocalDate.ofEpochDay(request.toEpochDay).toString()
        val table = ReportTable(
            title = "الميزانية العمومية",
            subtitle = "كما في $asOfStr",
            columns = columns,
            rows = rows
        )

        val footer = buildReportFooter(
            table = table,
            params = "كما في تاريخ: $asOfStr",
            entriesCount = rep.lineDetails.size,
            reconciled = rep.isBalanced,
            reconcileNote = if (!rep.isBalanced) "تفاوت بين الأصول والالتزامات: ${(rep.totalAssetsMinor - rep.totalLiabilitiesAndEquityMinor)} minor" else null
        )
        return ReportResult(table, footer)
    }

    private suspend fun generateTrialBalanceReport(request: ReportRequest): ReportResult {
        val rep = financialStatementsUseCase.generateDualCurrencyTrialBalance(request.toEpochDay)

        val columns = listOf(
            ReportColumn("code", "رقم الحساب"),
            ReportColumn("name", "اسم الحساب"),
            ReportColumn("curr", "العملة", ColumnAlignment.CENTER),
            ReportColumn("origBal", "الرصيد الأصلي", ColumnAlignment.END),
            ReportColumn("debit", "مدين (ريال)", ColumnAlignment.END),
            ReportColumn("credit", "دائن (ريال)", ColumnAlignment.END)
        )

        val rows = mutableListOf<ReportRow>()
        for (row in rep.rows) {
            rows.add(
                ReportRow(
                    type = RowType.DATA,
                    cells = listOf(
                        ReportCell.TextCell(row.accountCode),
                        ReportCell.TextCell(row.accountName),
                        ReportCell.TextCell(row.currency),
                        ReportCell.MoneyCell(row.netOrigMinor, CurrencyCode.fromString(row.currency)),
                        ReportCell.MoneyCell(row.baseDebitMinor, CurrencyCode.FUNCTIONAL, DrillTarget.AccountLines(row.accountCode, 0L, request.toEpochDay)),
                        ReportCell.MoneyCell(row.baseCreditMinor, CurrencyCode.FUNCTIONAL, DrillTarget.AccountLines(row.accountCode, 0L, request.toEpochDay))
                    )
                )
            )
        }

        rows.add(
            ReportRow(
                type = RowType.TOTAL,
                cells = listOf(
                    ReportCell.TextCell("الإجمالي العام", isBold = true),
                    ReportCell.TextCell(""),
                    ReportCell.TextCell(""),
                    ReportCell.TextCell(""),
                    ReportCell.MoneyCell(rep.totalBaseDebitMinor, CurrencyCode.FUNCTIONAL),
                    ReportCell.MoneyCell(rep.totalBaseCreditMinor, CurrencyCode.FUNCTIONAL)
                )
            )
        )

        val asOfStr = LocalDate.ofEpochDay(request.toEpochDay).toString()
        val table = ReportTable(
            title = "ميزان المراجعة ثنائي العملة",
            subtitle = "كما في $asOfStr",
            columns = columns,
            rows = rows
        )

        val footer = buildReportFooter(
            table = table,
            params = "كما في: $asOfStr",
            entriesCount = rep.rows.size,
            reconciled = rep.isBalanced,
            reconcileNote = if (!rep.isBalanced) "تفاوت الميزان: ${rep.totalBaseDebitMinor - rep.totalBaseCreditMinor}" else null
        )
        return ReportResult(table, footer)
    }

    private suspend fun generateAgingReport(request: ReportRequest): ReportResult {
        val rep = financialStatementsUseCase.generateAgingReport(request.toEpochDay)

        val columns = listOf(
            ReportColumn("bucket", "فترة الاستحقاق"),
            ReportColumn("count", "عدد الفواتير", ColumnAlignment.CENTER),
            ReportColumn("amount", "إجمالي المبلغ (ريال)", ColumnAlignment.END)
        )

        val rows = listOf(
            ReportRow(RowType.DATA, listOf(
                ReportCell.TextCell(rep.current0to30.bucketName),
                ReportCell.CountCell(rep.current0to30.invoiceCount.toLong()),
                ReportCell.MoneyCell(rep.current0to30.amountMinor, CurrencyCode.FUNCTIONAL, DrillTarget.AccountLines("1201", request.toEpochDay - 30, request.toEpochDay))
            )),
            ReportRow(RowType.DATA, listOf(
                ReportCell.TextCell(rep.aging31to60.bucketName),
                ReportCell.CountCell(rep.aging31to60.invoiceCount.toLong()),
                ReportCell.MoneyCell(rep.aging31to60.amountMinor, CurrencyCode.FUNCTIONAL, DrillTarget.AccountLines("1201", request.toEpochDay - 60, request.toEpochDay - 31))
            )),
            ReportRow(RowType.DATA, listOf(
                ReportCell.TextCell(rep.aging61to90.bucketName),
                ReportCell.CountCell(rep.aging61to90.invoiceCount.toLong()),
                ReportCell.MoneyCell(rep.aging61to90.amountMinor, CurrencyCode.FUNCTIONAL, DrillTarget.AccountLines("1201", request.toEpochDay - 90, request.toEpochDay - 61))
            )),
            ReportRow(RowType.DATA, listOf(
                ReportCell.TextCell(rep.agingOver90.bucketName),
                ReportCell.CountCell(rep.agingOver90.invoiceCount.toLong()),
                ReportCell.MoneyCell(rep.agingOver90.amountMinor, CurrencyCode.FUNCTIONAL, DrillTarget.AccountLines("1201", 0L, request.toEpochDay - 91))
            )),
            ReportRow(RowType.TOTAL, listOf(
                ReportCell.TextCell("إجمالي الذمم المدينة (1201)", isBold = true),
                ReportCell.CountCell((rep.current0to30.invoiceCount + rep.aging31to60.invoiceCount + rep.aging61to90.invoiceCount + rep.agingOver90.invoiceCount).toLong()),
                ReportCell.MoneyCell(rep.totalReceivablesMinor, CurrencyCode.FUNCTIONAL, DrillTarget.AccountLines("1201", 0L, request.toEpochDay))
            ))
        )

        val asOfStr = LocalDate.ofEpochDay(request.toEpochDay).toString()
        val table = ReportTable(
            title = "تقرير أعمار الديون ومستحقات العملاء",
            subtitle = "كما في $asOfStr",
            columns = columns,
            rows = rows
        )

        val footer = buildReportFooter(table, "كما في: $asOfStr", rows.size, true)
        return ReportResult(table, footer)
    }

    private fun generateGenericPlaceholderReport(request: ReportRequest): ReportResult {
        val columns = listOf(
            ReportColumn("col1", "البيان"),
            ReportColumn("col2", "القيمة", ColumnAlignment.END)
        )
        val rows = listOf(
            ReportRow(
                RowType.DATA,
                listOf(
                    ReportCell.TextCell(request.type.title),
                    ReportCell.TextCell("جاري التجهيز")
                )
            )
        )
        val table = ReportTable(request.type.title, request.type.subtitle, columns, rows)
        val footer = buildReportFooter(table, "معاملات عامة", 0, true)
        return ReportResult(table, footer)
    }

    private fun buildReportFooter(
        table: ReportTable,
        params: String,
        entriesCount: Int,
        reconciled: Boolean,
        reconcileNote: String? = null
    ): ReportFooter {
        val now = AppClock.nowInstant()
        val sha256 = ReportHashCalculator.computeSha256(table)
        val issuedText = now.atZone(AppClock.zoneId()).toLocalDateTime().toString()
        return ReportFooter(
            issuedAtEpochMilli = now.toEpochMilli(),
            issuedAtText = issuedText,
            parametersSummary = params,
            entriesCount = entriesCount,
            reconciled = reconciled,
            reconciliationDetails = reconcileNote,
            contentHashSha256 = sha256
        )
    }
}
