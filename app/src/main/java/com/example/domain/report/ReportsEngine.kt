package com.example.domain.report

import com.example.core.model.CurrencyCode
import com.example.core.time.AppClock
import com.example.data.local.AppDatabase
import com.example.domain.usecase.FinancialStatementsUseCase
import java.time.LocalDate

class ReportsEngine(
    private val db: AppDatabase
) {
    private val financialStatementsUseCase = FinancialStatementsUseCase(db)

    suspend fun generateReport(request: ReportRequest): ReportResult {
        return when (request.type) {
            ReportType.INCOME_STATEMENT -> generateIncomeStatementReport(request)
            ReportType.BALANCE_SHEET -> generateBalanceSheetReport(request)
            ReportType.TRIAL_BALANCE -> generateTrialBalanceReport(request)
            ReportType.AGING_RECEIVABLES -> generateAgingReport(request)
            else -> generateGenericPlaceholderReport(request)
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
            rows.add(ReportRow(RowType.SECTION, listOf(ReportCell.TextCell("فروق أسعار العملات", isBold = true), ReportCell.TextCell(""))))
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
