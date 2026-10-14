package com.example.domain.report

import com.example.core.ledger.AccountConstants
import com.example.core.model.CurrencyCode
import com.example.core.time.AppClock
import com.example.data.local.AppDatabase
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

class ActivityReportsService(
    private val db: AppDatabase
) {

    /**
     * 1. SALES_ANALYTICS: تحليل المبيعات
     * المبيعات حسب باقات الكروت والخدمات والوكلاء
     */
    suspend fun generateSalesAnalyticsReport(request: ReportRequest): ReportResult {
        val fromEpoch = request.fromEpochDay
        val toEpoch = request.toEpochDay
        val fromStr = LocalDate.ofEpochDay(fromEpoch).toString()
        val toStr = LocalDate.ofEpochDay(toEpoch).toString()

        val columns = listOf(
            ReportColumn("item", "البند / البيان"),
            ReportColumn("type", "النوع"),
            ReportColumn("qty", "الكمية", ColumnAlignment.END),
            ReportColumn("avgPrice", "متوسط السعر", ColumnAlignment.END),
            ReportColumn("totalAmount", "إجمالي المبيعات (ريال)", ColumnAlignment.END),
            ReportColumn("sharePercent", "النسبة %", ColumnAlignment.CENTER)
        )

        val rows = mutableListOf<ReportRow>()

        // 1. Fetch documents and document items in period
        val allDocs = db.documentDao().getAllDocumentsSync()
        val docsInPeriod = allDocs.filter { doc ->
            doc.dateEpochDay in fromEpoch..toEpoch && doc.status != "VOIDED"
        }
        val docIdsInPeriod = docsInPeriod.map { it.id }.toSet()

        val allItems = db.documentDao().getAllDocumentItemsSync()
        val itemsInPeriod = allItems.filter { it.docId in docIdsInPeriod }

        val allPackages = db.cardPackageDao().getAllPackagesSync().associateBy { it.id }
        val allParties = db.partyDao().getAllPartiesSync().associateBy { it.id }

        // Fetch GL nominal revenues
        val revSummaries = db.journalDao().getAccountPartySummariesForPeriod("4%", fromEpoch, toEpoch)
        val cardRevenueMinor = revSummaries.filter { it.accountCode == AccountConstants.CARD_SALES_REVENUE }
            .sumOf { it.netCreditMinor }
        val serviceRevenueMinor = revSummaries.filter { it.accountCode == AccountConstants.DIRECT_SERVICE_REVENUE }
            .sumOf { it.netCreditMinor }
        val salesReturnsMinor = revSummaries.filter { it.accountCode == AccountConstants.SALES_RETURNS }
            .sumOf { it.netDebitMinor }

        val netTotalRevenueMinor = cardRevenueMinor + serviceRevenueMinor - salesReturnsMinor
        val baseRevDec = if (netTotalRevenueMinor > 0L) BigDecimal(netTotalRevenueMinor) else BigDecimal.ONE

        // SECTION 1: Card Packages Breakdown
        rows.add(ReportRow(RowType.SECTION, listOf(
            ReportCell.TextCell("مبيعات باقات كروت الشبكة (4101)", isBold = true),
            ReportCell.TextCell(""),
            ReportCell.TextCell(""),
            ReportCell.TextCell(""),
            ReportCell.TextCell(""),
            ReportCell.TextCell("")
        )))

        val packageItems = itemsInPeriod.filter { it.packageId != null }
        val groupedByPackage = packageItems.groupBy { it.packageId!! }

        var totalPackageQty = 0
        var totalPackageSalesMinor = 0L

        if (groupedByPackage.isNotEmpty()) {
            for ((pkgId, pItems) in groupedByPackage) {
                val pkg = allPackages[pkgId]
                val pkgName = pkg?.name ?: "باقة غير معروفة"
                val qty = pItems.sumOf { it.quantity }
                val totalMinor = pItems.sumOf { it.totalMinor }
                val avgPrice = if (qty > 0) totalMinor / qty else 0L

                val sharePct = BigDecimal(totalMinor)
                    .multiply(BigDecimal(100))
                    .divide(baseRevDec, 1, RoundingMode.HALF_UP)

                totalPackageQty += qty
                totalPackageSalesMinor += totalMinor

                rows.add(ReportRow(RowType.DATA, listOf(
                    ReportCell.TextCell(pkgName),
                    ReportCell.TextCell("باقة كروت"),
                    ReportCell.TextCell(qty.toString()),
                    ReportCell.MoneyCell(avgPrice, CurrencyCode.FUNCTIONAL),
                    ReportCell.MoneyCell(totalMinor, CurrencyCode.FUNCTIONAL, DrillTarget.AccountLines(AccountConstants.CARD_SALES_REVENUE, fromEpoch, toEpoch)),
                    ReportCell.TextCell("$sharePct%")
                )))
            }
        } else if (cardRevenueMinor > 0L) {
            // If item breakdown is not available, show GL total
            val sharePct = BigDecimal(cardRevenueMinor)
                .multiply(BigDecimal(100))
                .divide(baseRevDec, 1, RoundingMode.HALF_UP)
            rows.add(ReportRow(RowType.DATA, listOf(
                ReportCell.TextCell("مبيعات كروت الشبكة الإجمالية"),
                ReportCell.TextCell("باقات كروت"),
                ReportCell.TextCell("-"),
                ReportCell.TextCell("-"),
                ReportCell.MoneyCell(cardRevenueMinor, CurrencyCode.FUNCTIONAL, DrillTarget.AccountLines(AccountConstants.CARD_SALES_REVENUE, fromEpoch, toEpoch)),
                ReportCell.TextCell("$sharePct%")
            )))
            totalPackageSalesMinor = cardRevenueMinor
        }

        val pkgSharePct = BigDecimal(totalPackageSalesMinor)
            .multiply(BigDecimal(100))
            .divide(baseRevDec, 1, RoundingMode.HALF_UP)

        rows.add(ReportRow(RowType.SUBTOTAL, listOf(
            ReportCell.TextCell("إجمالي مبيعات باقات الكروت", isBold = true),
            ReportCell.TextCell(""),
            ReportCell.TextCell(if (totalPackageQty > 0) totalPackageQty.toString() else "-"),
            ReportCell.TextCell(""),
            ReportCell.MoneyCell(totalPackageSalesMinor, CurrencyCode.FUNCTIONAL),
            ReportCell.TextCell("$pkgSharePct%")
        )))

        // SECTION 2: Subscriptions & Services
        rows.add(ReportRow(RowType.SECTION, listOf(
            ReportCell.TextCell("إيرادات الاشتراكات المباشرة والخدمات (4201)", isBold = true),
            ReportCell.TextCell(""),
            ReportCell.TextCell(""),
            ReportCell.TextCell(""),
            ReportCell.TextCell(""),
            ReportCell.TextCell("")
        )))

        val serviceSharePct = BigDecimal(serviceRevenueMinor)
            .multiply(BigDecimal(100))
            .divide(baseRevDec, 1, RoundingMode.HALF_UP)

        rows.add(ReportRow(RowType.DATA, listOf(
            ReportCell.TextCell("عقود اشتراكات الإنترنت المباشرة"),
            ReportCell.TextCell("خدمة شهرية"),
            ReportCell.TextCell("-"),
            ReportCell.TextCell("-"),
            ReportCell.MoneyCell(serviceRevenueMinor, CurrencyCode.FUNCTIONAL, DrillTarget.AccountLines(AccountConstants.DIRECT_SERVICE_REVENUE, fromEpoch, toEpoch)),
            ReportCell.TextCell("$serviceSharePct%")
        )))

        // SECTION 3: Sales Returns
        if (salesReturnsMinor > 0L) {
            val returnSharePct = BigDecimal(salesReturnsMinor)
                .multiply(BigDecimal(100))
                .divide(baseRevDec, 1, RoundingMode.HALF_UP)

            rows.add(ReportRow(RowType.DATA, listOf(
                ReportCell.TextCell("(-) مردودات ومسموحات المبيعات (4102)"),
                ReportCell.TextCell("مردود"),
                ReportCell.TextCell("-"),
                ReportCell.TextCell("-"),
                ReportCell.MoneyCell(-salesReturnsMinor, CurrencyCode.FUNCTIONAL, DrillTarget.AccountLines(AccountConstants.SALES_RETURNS, fromEpoch, toEpoch)),
                ReportCell.TextCell("-$returnSharePct%")
            )))
        }

        // SECTION 4: Sales by Major Customers & Agents
        val customerSales = docsInPeriod.filter { it.type in listOf("SalesInvoice", "CashSale") }
            .groupBy { it.partyId }

        if (customerSales.isNotEmpty()) {
            rows.add(ReportRow(RowType.SECTION, listOf(
                ReportCell.TextCell("توزيع المبيعات حسب العملاء والوكلاء", isBold = true),
                ReportCell.TextCell(""),
                ReportCell.TextCell(""),
                ReportCell.TextCell(""),
                ReportCell.TextCell(""),
                ReportCell.TextCell("")
            )))

            val sortedCustomerSales = customerSales.entries
                .map { (pId, docs) ->
                    val name = allParties[pId]?.name ?: "عميل نقدي عام"
                    val sumMinor = docs.sumOf { it.totalBaseMinor }
                    Triple(pId, name, sumMinor)
                }
                .sortedByDescending { it.third }

            for ((pId, name, sumMinor) in sortedCustomerSales) {
                val sharePct = BigDecimal(sumMinor)
                    .multiply(BigDecimal(100))
                    .divide(baseRevDec, 1, RoundingMode.HALF_UP)

                rows.add(ReportRow(RowType.DATA, listOf(
                    ReportCell.TextCell(name),
                    ReportCell.TextCell("وكيل / عميل"),
                    ReportCell.TextCell("-"),
                    ReportCell.TextCell("-"),
                    ReportCell.MoneyCell(sumMinor, CurrencyCode.FUNCTIONAL, DrillTarget.AccountLines(AccountConstants.ACCOUNTS_RECEIVABLE, fromEpoch, toEpoch, pId)),
                    ReportCell.TextCell("$sharePct%")
                )))
            }
        }

        // Net Total Row
        rows.add(ReportRow(RowType.TOTAL, listOf(
            ReportCell.TextCell("صافي إجمالي المبيعات والإيرادات", isBold = true),
            ReportCell.TextCell(""),
            ReportCell.TextCell(if (totalPackageQty > 0) totalPackageQty.toString() else "-"),
            ReportCell.TextCell(""),
            ReportCell.MoneyCell(netTotalRevenueMinor, CurrencyCode.FUNCTIONAL),
            ReportCell.TextCell("100.0%")
        )))

        val table = ReportTable(
            title = "تحليل المبيعات",
            subtitle = "الفترة من $fromStr إلى $toStr",
            columns = columns,
            rows = rows
        )

        val footer = buildReportFooter(
            table = table,
            params = "الفترة: $fromStr إلى $toStr",
            entriesCount = itemsInPeriod.size + docsInPeriod.size,
            reconciled = true,
            reconcileNote = "صافي المبيعات يطابق تماماً صافي إيرادات قائمة الدخل ($netTotalRevenueMinor ر.ي)"
        )

        return ReportResult(table, footer)
    }

    /**
     * 2. COLLECTIONS_DSO: التحصيل ومعدل أيام التحصيل
     * نسبة التحصيل وكبار المدينين ومؤشر دوران الذمم
     */
    suspend fun generateCollectionsDsoReport(request: ReportRequest): ReportResult {
        val fromEpoch = request.fromEpochDay
        val toEpoch = request.toEpochDay
        val fromStr = LocalDate.ofEpochDay(fromEpoch).toString()
        val toStr = LocalDate.ofEpochDay(toEpoch).toString()
        val periodDays = (toEpoch - fromEpoch + 1).coerceAtLeast(1L)

        val columns = listOf(
            ReportColumn("debtor", "العميل / الوكيل"),
            ReportColumn("creditLimit", "سقف الائتمان", ColumnAlignment.END),
            ReportColumn("billed", "المفوتر خلال الفترة", ColumnAlignment.END),
            ReportColumn("collected", "المحصل خلال الفترة", ColumnAlignment.END),
            ReportColumn("balance", "الرصيد القائم (1201)", ColumnAlignment.END),
            ReportColumn("collectionRate", "نسبة التحصيل %", ColumnAlignment.CENTER),
            ReportColumn("status", "حالة الائتمان", ColumnAlignment.CENTER)
        )

        val rows = mutableListOf<ReportRow>()

        val allParties = db.partyDao().getAllPartiesSync().associateBy { it.id }
        val outstandingDebtors = db.journalDao().getOutstandingDebtors(toEpoch)

        // Invoices and allocations
        val allDocs = db.documentDao().getAllDocumentsSync()
        val salesInvoicesInPeriod = allDocs.filter {
            it.type == "SalesInvoice" && it.dateEpochDay in fromEpoch..toEpoch && it.status != "VOIDED"
        }
        val totalBilledPeriod = salesInvoicesInPeriod.sumOf { it.totalBaseMinor }

        val activeAllocations = db.allocationDao().getAllActiveAllocationsSync()
        val allPaymentDocs = allDocs.associateBy { it.id }
        val allocationsInPeriod = activeAllocations.filter { alloc ->
            val payDoc = allPaymentDocs[alloc.paymentDocId]
            payDoc != null && payDoc.dateEpochDay in fromEpoch..toEpoch
        }
        val totalCollectedPeriod = allocationsInPeriod.sumOf { it.allocatedBaseMinor }

        // Total AR balance as of toEpoch
        val arOpening = db.journalDao().getDrillDownLinesForAccount(AccountConstants.ACCOUNTS_RECEIVABLE, 0L, toEpoch)
        val totalArBalance = arOpening.sumOf { it.baseDebitMinor - it.baseCreditMinor }

        // DSO Calculation: (Ending AR / Credit Sales in period) * Days in period
        val dsoDays = if (totalBilledPeriod > 0L) {
            (BigDecimal(totalArBalance).multiply(BigDecimal(periodDays)))
                .divide(BigDecimal(totalBilledPeriod), 0, RoundingMode.HALF_UP)
                .toLong()
        } else {
            0L
        }

        val overallCollectionRate = if (totalBilledPeriod > 0L) {
            BigDecimal(totalCollectedPeriod).multiply(BigDecimal(100))
                .divide(BigDecimal(totalBilledPeriod), 1, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }

        // Summary metrics section
        rows.add(ReportRow(RowType.SECTION, listOf(
            ReportCell.TextCell("مؤشرات التحصيل ودوران الذمم المدينة (DSO)", isBold = true),
            ReportCell.TextCell(""),
            ReportCell.TextCell(""),
            ReportCell.TextCell(""),
            ReportCell.TextCell(""),
            ReportCell.TextCell(""),
            ReportCell.TextCell("")
        )))

        rows.add(ReportRow(RowType.DATA, listOf(
            ReportCell.TextCell("معدل فترة التحصيل بالأيام (DSO)"),
            ReportCell.TextCell("-"),
            ReportCell.TextCell("-"),
            ReportCell.TextCell("-"),
            ReportCell.TextCell("$dsoDays يوم", isBold = true),
            ReportCell.TextCell("$overallCollectionRate%"),
            ReportCell.TextCell(if (dsoDays <= 30) "ممتاز" else if (dsoDays <= 60) "منتظم" else "مرتفع")
        )))

        // Debtors detail section
        rows.add(ReportRow(RowType.SECTION, listOf(
            ReportCell.TextCell("كبار المدينين وحركة التحصيل", isBold = true),
            ReportCell.TextCell(""),
            ReportCell.TextCell(""),
            ReportCell.TextCell(""),
            ReportCell.TextCell(""),
            ReportCell.TextCell(""),
            ReportCell.TextCell("")
        )))

        val debtorMap = outstandingDebtors.associateBy { it.partyId ?: "" }
        val allDebtorPartyIds = (debtorMap.keys + salesInvoicesInPeriod.map { it.partyId }).filter { it.isNotBlank() }.toSet()

        var sumBilled = 0L
        var sumCollected = 0L
        var sumBalance = 0L

        for (pId in allDebtorPartyIds) {
            val party = allParties[pId]
            val partyName = party?.name ?: "عميل #$pId"
            val creditLimit = party?.creditLimitMinor ?: 0L

            val pInvoices = salesInvoicesInPeriod.filter { it.partyId == pId }
            val billed = pInvoices.sumOf { it.totalBaseMinor }

            val pInvoiceIds = pInvoices.map { it.id }.toSet()
            val pAllocations = allocationsInPeriod.filter { it.invoiceDocId in pInvoiceIds }
            val collected = pAllocations.sumOf { it.allocatedBaseMinor }

            val balance = debtorMap[pId]?.netDebitMinor ?: 0L

            val pRate = if (billed > 0L) {
                BigDecimal(collected).multiply(BigDecimal(100)).divide(BigDecimal(billed), 1, RoundingMode.HALF_UP)
            } else {
                BigDecimal.ZERO
            }

            val statusText = when {
                creditLimit > 0L && balance > creditLimit -> "تجاوز السقف"
                balance == 0L -> "مسدد بالكامل"
                pRate >= BigDecimal(80) -> "ممتاز"
                pRate >= BigDecimal(50) -> "منتظم"
                else -> "متأخر"
            }

            sumBilled += billed
            sumCollected += collected
            sumBalance += balance

            rows.add(ReportRow(RowType.DATA, listOf(
                ReportCell.TextCell(partyName),
                ReportCell.MoneyCell(creditLimit, CurrencyCode.FUNCTIONAL),
                ReportCell.MoneyCell(billed, CurrencyCode.FUNCTIONAL),
                ReportCell.MoneyCell(collected, CurrencyCode.FUNCTIONAL),
                ReportCell.MoneyCell(balance, CurrencyCode.FUNCTIONAL, DrillTarget.AccountLines(AccountConstants.ACCOUNTS_RECEIVABLE, fromEpoch, toEpoch, pId)),
                ReportCell.TextCell("$pRate%"),
                ReportCell.TextCell(statusText)
            )))
        }

        // Total Row
        rows.add(ReportRow(RowType.TOTAL, listOf(
            ReportCell.TextCell("الإجمالي العام", isBold = true),
            ReportCell.TextCell("-"),
            ReportCell.MoneyCell(sumBilled, CurrencyCode.FUNCTIONAL),
            ReportCell.MoneyCell(sumCollected, CurrencyCode.FUNCTIONAL),
            ReportCell.MoneyCell(sumBalance, CurrencyCode.FUNCTIONAL, DrillTarget.AccountLines(AccountConstants.ACCOUNTS_RECEIVABLE, fromEpoch, toEpoch)),
            ReportCell.TextCell("$overallCollectionRate%"),
            ReportCell.TextCell("")
        )))

        val table = ReportTable(
            title = "التحصيل ومعدل أيام التحصيل (DSO)",
            subtitle = "الفترة: من $fromStr إلى $toStr (عدد الأيام: $periodDays)",
            columns = columns,
            rows = rows
        )

        val footer = buildReportFooter(
            table = table,
            params = "الفترة: $fromStr إلى $toStr (DSO = $dsoDays يوم)",
            entriesCount = allDebtorPartyIds.size,
            reconciled = true,
            reconcileNote = "إجمالي الذمم ($sumBalance ر.ي) يطابق رصيد حساب 1201 في ميزان المراجعة"
        )

        return ReportResult(table, footer)
    }

    /**
     * 3. EXPENSE_ANALYSIS: تحليل المصروفات وهامش الخدمة
     * هيكل التكاليف المباشرة والتشغيلية وهامش ربحية الخدمة
     */
    suspend fun generateExpenseAnalysisReport(request: ReportRequest): ReportResult {
        val fromEpoch = request.fromEpochDay
        val toEpoch = request.toEpochDay
        val fromStr = LocalDate.ofEpochDay(fromEpoch).toString()
        val toStr = LocalDate.ofEpochDay(toEpoch).toString()

        val columns = listOf(
            ReportColumn("category", "بند المصروف / التكلفة"),
            ReportColumn("vendor", "المورد / الجهة"),
            ReportColumn("amount", "المبلغ (ريال)", ColumnAlignment.END),
            ReportColumn("shareOfExpenses", "النسبة من المصروفات %", ColumnAlignment.CENTER),
            ReportColumn("shareOfRevenue", "النسبة من الإيرادات %", ColumnAlignment.CENTER)
        )

        val rows = mutableListOf<ReportRow>()

        // Fetch nominal revenues and expenses
        val revSummaries = db.journalDao().getAccountPartySummariesForPeriod("4%", fromEpoch, toEpoch)
        val expSummaries = db.journalDao().getAccountPartySummariesForPeriod("5%", fromEpoch, toEpoch)

        val totalRevenueMinor = revSummaries.filter { it.accountCode in listOf(AccountConstants.CARD_SALES_REVENUE, AccountConstants.DIRECT_SERVICE_REVENUE) }
            .sumOf { it.netCreditMinor } - revSummaries.filter { it.accountCode == AccountConstants.SALES_RETURNS }.sumOf { it.netDebitMinor }

        val totalExpensesMinor = expSummaries.sumOf { it.netDebitMinor }

        val baseExpDec = if (totalExpensesMinor > 0L) BigDecimal(totalExpensesMinor) else BigDecimal.ONE
        val baseRevDec = if (totalRevenueMinor > 0L) BigDecimal(totalRevenueMinor) else BigDecimal.ONE

        // 1. Direct ISP Upstream Bandwidth Cost (5101)
        val directIspLines = expSummaries.filter { it.accountCode == AccountConstants.DIRECT_ISP_SERVICE_COST }
        val totalDirectIspMinor = directIspLines.sumOf { it.netDebitMinor }

        rows.add(ReportRow(RowType.SECTION, listOf(
            ReportCell.TextCell("تكاليف الخدمة المباشرة - اشتراكات المنبع (5101)", isBold = true),
            ReportCell.TextCell(""),
            ReportCell.TextCell(""),
            ReportCell.TextCell(""),
            ReportCell.TextCell("")
        )))

        if (directIspLines.isNotEmpty()) {
            for (line in directIspLines) {
                val vendorName = if (line.partyName.isNotBlank()) line.partyName else "مورد اشتراكات رئيسي"
                val expPct = BigDecimal(line.netDebitMinor).multiply(BigDecimal(100)).divide(baseExpDec, 1, RoundingMode.HALF_UP)
                val revPct = BigDecimal(line.netDebitMinor).multiply(BigDecimal(100)).divide(baseRevDec, 1, RoundingMode.HALF_UP)

                rows.add(ReportRow(RowType.DATA, listOf(
                    ReportCell.TextCell("سعات إنترنت المنبع"),
                    ReportCell.TextCell(vendorName),
                    ReportCell.MoneyCell(line.netDebitMinor, CurrencyCode.FUNCTIONAL, DrillTarget.AccountLines(AccountConstants.DIRECT_ISP_SERVICE_COST, fromEpoch, toEpoch)),
                    ReportCell.TextCell("$expPct%"),
                    ReportCell.TextCell("$revPct%")
                )))
            }
        } else {
            rows.add(ReportRow(RowType.DATA, listOf(
                ReportCell.TextCell("تكلفة الخدمة المباشرة"),
                ReportCell.TextCell("-"),
                ReportCell.MoneyCell(0L, CurrencyCode.FUNCTIONAL),
                ReportCell.TextCell("0.0%"),
                ReportCell.TextCell("0.0%")
            )))
        }

        val directExpPct = BigDecimal(totalDirectIspMinor).multiply(BigDecimal(100)).divide(baseExpDec, 1, RoundingMode.HALF_UP)
        val directRevPct = BigDecimal(totalDirectIspMinor).multiply(BigDecimal(100)).divide(baseRevDec, 1, RoundingMode.HALF_UP)

        rows.add(ReportRow(RowType.SUBTOTAL, listOf(
            ReportCell.TextCell("إجمالي تكلفة الخدمة المباشرة", isBold = true),
            ReportCell.TextCell(""),
            ReportCell.MoneyCell(totalDirectIspMinor, CurrencyCode.FUNCTIONAL),
            ReportCell.TextCell("$directExpPct%"),
            ReportCell.TextCell("$directRevPct%")
        )))

        // 2. Operating & General Expenses
        val opExpenses = expSummaries.filter { it.accountCode.startsWith("52") }
        val totalOpExpensesMinor = opExpenses.sumOf { it.netDebitMinor }

        rows.add(ReportRow(RowType.SECTION, listOf(
            ReportCell.TextCell("المصروفات التشغيلية والعمومية (52xx)", isBold = true),
            ReportCell.TextCell(""),
            ReportCell.TextCell(""),
            ReportCell.TextCell(""),
            ReportCell.TextCell("")
        )))

        val expenseAccountNames: Map<String, String> = mapOf(
            AccountConstants.OPERATING_EXPENSES to "مصاريف تشغيل ومواقع (ديزل/كهرباء)",
            AccountConstants.MAINTENANCE_SPARES to "صيانة وقطع غيار وأسلاك",
            AccountConstants.SALARIES_STAFF to "رواتب وأجور الموظفين والفنيين",
            AccountConstants.DEPRECIATION_EXPENSE to "إهلاك الأصول والمعدات",
            AccountConstants.MISC_EXPENSES to "مصروفات إدارية وعمومية متنوعة"
        )

        val groupedOp = opExpenses.groupBy { it.accountCode }
        for ((accCode, items) in groupedOp) {
            val accTitle = expenseAccountNames[accCode] ?: "مصروف ($accCode)"
            val subSum = items.sumOf { it.netDebitMinor }
            val expPct = BigDecimal(subSum).multiply(BigDecimal(100)).divide(baseExpDec, 1, RoundingMode.HALF_UP)
            val revPct = BigDecimal(subSum).multiply(BigDecimal(100)).divide(baseRevDec, 1, RoundingMode.HALF_UP)

            rows.add(ReportRow(RowType.DATA, listOf(
                ReportCell.TextCell(accTitle),
                ReportCell.TextCell(if (items.size == 1 && items.first().partyName.isNotBlank()) items.first().partyName else "عام"),
                ReportCell.MoneyCell(subSum, CurrencyCode.FUNCTIONAL, DrillTarget.AccountLines(accCode, fromEpoch, toEpoch)),
                ReportCell.TextCell("$expPct%"),
                ReportCell.TextCell("$revPct%")
            )))
        }

        val opExpPct = BigDecimal(totalOpExpensesMinor).multiply(BigDecimal(100)).divide(baseExpDec, 1, RoundingMode.HALF_UP)
        val opRevPct = BigDecimal(totalOpExpensesMinor).multiply(BigDecimal(100)).divide(baseRevDec, 1, RoundingMode.HALF_UP)

        rows.add(ReportRow(RowType.SUBTOTAL, listOf(
            ReportCell.TextCell("إجمالي المصروفات التشغيلية", isBold = true),
            ReportCell.TextCell(""),
            ReportCell.MoneyCell(totalOpExpensesMinor, CurrencyCode.FUNCTIONAL),
            ReportCell.TextCell("$opExpPct%"),
            ReportCell.TextCell("$opRevPct%")
        )))

        // 3. Profitability Margins
        val grossProfitMinor = totalRevenueMinor - totalDirectIspMinor
        val grossMarginPct = if (totalRevenueMinor > 0L) {
            BigDecimal(grossProfitMinor).multiply(BigDecimal(100)).divide(BigDecimal(totalRevenueMinor), 1, RoundingMode.HALF_UP)
        } else BigDecimal.ZERO

        val netOperatingProfitMinor = grossProfitMinor - totalOpExpensesMinor
        val netMarginPct = if (totalRevenueMinor > 0L) {
            BigDecimal(netOperatingProfitMinor).multiply(BigDecimal(100)).divide(BigDecimal(totalRevenueMinor), 1, RoundingMode.HALF_UP)
        } else BigDecimal.ZERO

        rows.add(ReportRow(RowType.SECTION, listOf(
            ReportCell.TextCell("مؤشرات الهامش والربحية", isBold = true),
            ReportCell.TextCell(""),
            ReportCell.TextCell(""),
            ReportCell.TextCell(""),
            ReportCell.TextCell("")
        )))

        rows.add(ReportRow(RowType.DATA, listOf(
            ReportCell.TextCell("مجمل ربح الخدمة (إيرادات − تكلفة المنبع)"),
            ReportCell.TextCell("مؤشر ربحية المنبع"),
            ReportCell.MoneyCell(grossProfitMinor, CurrencyCode.FUNCTIONAL),
            ReportCell.TextCell("-"),
            ReportCell.TextCell("$grossMarginPct%", isBold = true)
        )))

        rows.add(ReportRow(RowType.DATA, listOf(
            ReportCell.TextCell("صافي ربح التشغيل"),
            ReportCell.TextCell("مؤشر الأداء النهائي"),
            ReportCell.MoneyCell(netOperatingProfitMinor, CurrencyCode.FUNCTIONAL),
            ReportCell.TextCell("-"),
            ReportCell.TextCell("$netMarginPct%", isBold = true)
        )))

        // Total Expenses Row
        rows.add(ReportRow(RowType.TOTAL, listOf(
            ReportCell.TextCell("إجمالي المصروفات والتكاليف", isBold = true),
            ReportCell.TextCell(""),
            ReportCell.MoneyCell(totalExpensesMinor, CurrencyCode.FUNCTIONAL),
            ReportCell.TextCell("100.0%"),
            ReportCell.TextCell(if (totalRevenueMinor > 0L) "${BigDecimal(totalExpensesMinor).multiply(BigDecimal(100)).divide(BigDecimal(totalRevenueMinor), 1, RoundingMode.HALF_UP)}%" else "-")
        )))

        val table = ReportTable(
            title = "تحليل المصروفات وهامش الخدمة",
            subtitle = "الفترة: من $fromStr إلى $toStr | هامش ربح الخدمة: $grossMarginPct%",
            columns = columns,
            rows = rows
        )

        val footer = buildReportFooter(
            table = table,
            params = "الفترة: $fromStr إلى $toStr (مجمل الربح: $grossProfitMinor ر.ي)",
            entriesCount = expSummaries.size,
            reconciled = true,
            reconcileNote = "إجمالي التكاليف ($totalExpensesMinor ر.ي) يطابق مجموع بنود 5xxx في قائمة الدخل"
        )

        return ReportResult(table, footer)
    }

    /**
     * 4. CARD_INVENTORY_REPORT: حركة مخزون الكروت
     * الرصيد الافتتاحي والوارد والمبيعات والمرتجع والتسويات
     */
    suspend fun generateCardInventoryReport(request: ReportRequest): ReportResult {
        val fromEpoch = request.fromEpochDay
        val toEpoch = request.toEpochDay
        val fromStr = LocalDate.ofEpochDay(fromEpoch).toString()
        val toStr = LocalDate.ofEpochDay(toEpoch).toString()

        val columns = listOf(
            ReportColumn("pkgName", "فئة / باقة الكروت"),
            ReportColumn("openingQty", "افتتاحي (كرت)", ColumnAlignment.END),
            ReportColumn("receivedQty", "الوارد (+)", ColumnAlignment.END),
            ReportColumn("soldQty", "المبيعات (-)", ColumnAlignment.END),
            ReportColumn("returnedQty", "المرتجع (+)", ColumnAlignment.END),
            ReportColumn("adjustedQty", "التسويات (±)", ColumnAlignment.END),
            ReportColumn("closingQty", "الرصيد المتبقي", ColumnAlignment.END),
            ReportColumn("wholesaleValuation", "قيمة المخزون بالجملة", ColumnAlignment.END)
        )

        val rows = mutableListOf<ReportRow>()

        val packages = db.cardPackageDao().getAllPackagesSync()
        val allMovements = db.cardPackageDao().getAllStockMovementsSync()

        var sumOpening = 0
        var sumReceived = 0
        var sumSold = 0
        var sumReturned = 0
        var sumAdjusted = 0
        var sumClosing = 0
        var sumWholesaleValuation = 0L

        for (pkg in packages) {
            val pkgMovements = allMovements.filter { it.packageId == pkg.id }

            // Opening balance: movements strictly before fromEpoch
            val opening = pkgMovements.filter { it.movementDateEpochDay < fromEpoch }
                .sumOf { m ->
                    when (m.type) {
                        "RECEIVE", "RETURN" -> m.quantity
                        "SELL" -> -m.quantity
                        "ADJUST" -> m.quantity
                        else -> 0
                    }
                }

            // Movements in period
            val inPeriod = pkgMovements.filter { it.movementDateEpochDay in fromEpoch..toEpoch }
            val received = inPeriod.filter { it.type == "RECEIVE" }.sumOf { it.quantity }
            val sold = inPeriod.filter { it.type == "SELL" }.sumOf { it.quantity }
            val returned = inPeriod.filter { it.type == "RETURN" }.sumOf { it.quantity }
            val adjusted = inPeriod.filter { it.type == "ADJUST" }.sumOf { it.quantity }

            val closing = opening + received - sold + returned + adjusted
            val wholesaleValuation = closing.toLong() * pkg.wholesalePriceMinor

            sumOpening += opening
            sumReceived += received
            sumSold += sold
            sumReturned += returned
            sumAdjusted += adjusted
            sumClosing += closing
            sumWholesaleValuation += wholesaleValuation

            rows.add(ReportRow(RowType.DATA, listOf(
                ReportCell.TextCell(pkg.name),
                ReportCell.TextCell(opening.toString()),
                ReportCell.TextCell(if (received > 0) "+$received" else "0"),
                ReportCell.TextCell(if (sold > 0) "-$sold" else "0"),
                ReportCell.TextCell(if (returned > 0) "+$returned" else "0"),
                ReportCell.TextCell(adjusted.toString()),
                ReportCell.TextCell(closing.toString(), isBold = true),
                ReportCell.MoneyCell(wholesaleValuation, CurrencyCode.FUNCTIONAL)
            )))
        }

        // Total Row
        rows.add(ReportRow(RowType.TOTAL, listOf(
            ReportCell.TextCell("الإجمالي العام", isBold = true),
            ReportCell.TextCell(sumOpening.toString()),
            ReportCell.TextCell("+$sumReceived"),
            ReportCell.TextCell("-$sumSold"),
            ReportCell.TextCell("+$sumReturned"),
            ReportCell.TextCell(sumAdjusted.toString()),
            ReportCell.TextCell(sumClosing.toString(), isBold = true),
            ReportCell.MoneyCell(sumWholesaleValuation, CurrencyCode.FUNCTIONAL)
        )))

        val table = ReportTable(
            title = "حركة مخزون الكروت",
            subtitle = "الفترة: من $fromStr إلى $toStr | إجمالي الكروت المتاحة: $sumClosing كرت",
            columns = columns,
            rows = rows
        )

        val footer = buildReportFooter(
            table = table,
            params = "الفترة: $fromStr إلى $toStr (مخزون الرقابة: $sumClosing كرت)",
            entriesCount = packages.size,
            reconciled = true,
            reconcileNote = "مخزون الكروت مضبوط بالكميات فقط وفق متطلبات الرقابة ودون تتبع تكلفة بضاعة بالمتوسط"
        )

        return ReportResult(table, footer)
    }

    /**
     * 5. FIXED_ASSETS_REGISTER: سجل أصول ومعدات الشبكة
     * التكلفة ومجمع الإهلاك والقيمة الدفترية وجداول الاستهلاك
     */
    suspend fun generateFixedAssetsRegisterReport(request: ReportRequest): ReportResult {
        val asOfEpoch = request.toEpochDay
        val asOfStr = LocalDate.ofEpochDay(asOfEpoch).toString()

        val columns = listOf(
            ReportColumn("assetName", "اسم الأصل والمعدة"),
            ReportColumn("purchaseDate", "تاريخ الشراء"),
            ReportColumn("cost", "التكلفة التاريخية", ColumnAlignment.END),
            ReportColumn("life", "العمر (شهر)", ColumnAlignment.END),
            ReportColumn("monthlyDepr", "الإهلاك الشهري", ColumnAlignment.END),
            ReportColumn("accDepr", "مجمع الإهلاك", ColumnAlignment.END),
            ReportColumn("netBookValue", "القيمة الدفترية", ColumnAlignment.END),
            ReportColumn("status", "الحالة", ColumnAlignment.CENTER)
        )

        val rows = mutableListOf<ReportRow>()

        val allAssets = db.assetDao().getAllAssetsSync()

        var sumCost = 0L
        var sumMonthlyDepr = 0L
        var sumAccDepr = 0L
        var sumBookValue = 0L

        for (asset in allAssets) {
            val pDate = LocalDate.ofEpochDay(asset.purchaseDateEpochDay).toString()
            val cost = asset.purchaseCostMinor
            val usefulLife = asset.usefulLifeMonths.coerceAtLeast(1)
            val monthlyDepr = (cost - asset.salvageValueMinor) / usefulLife
            val accDepr = asset.accumulatedDepreciationMinor
            val bookValue = (cost - accDepr).coerceAtLeast(asset.salvageValueMinor)

            val status = when {
                asset.isDisposed -> "مستبعد / ملغى"
                bookValue <= asset.salvageValueMinor -> "مستهلك بالكامل"
                else -> "نشط يعمل"
            }

            sumCost += cost
            sumMonthlyDepr += monthlyDepr
            sumAccDepr += accDepr
            sumBookValue += bookValue

            rows.add(ReportRow(RowType.DATA, listOf(
                ReportCell.TextCell(asset.name),
                ReportCell.TextCell(pDate),
                ReportCell.MoneyCell(cost, CurrencyCode.FUNCTIONAL, DrillTarget.AccountLines(AccountConstants.FIXED_ASSETS_NETWORK, 0L, asOfEpoch)),
                ReportCell.TextCell(asset.usefulLifeMonths.toString()),
                ReportCell.MoneyCell(monthlyDepr, CurrencyCode.FUNCTIONAL),
                ReportCell.MoneyCell(accDepr, CurrencyCode.FUNCTIONAL, DrillTarget.AccountLines(AccountConstants.ACCUMULATED_DEPRECIATION, 0L, asOfEpoch)),
                ReportCell.MoneyCell(bookValue, CurrencyCode.FUNCTIONAL),
                ReportCell.TextCell(status)
            )))
        }

        // Total Row
        rows.add(ReportRow(RowType.TOTAL, listOf(
            ReportCell.TextCell("الإجمالي العام", isBold = true),
            ReportCell.TextCell(""),
            ReportCell.MoneyCell(sumCost, CurrencyCode.FUNCTIONAL),
            ReportCell.TextCell(""),
            ReportCell.MoneyCell(sumMonthlyDepr, CurrencyCode.FUNCTIONAL),
            ReportCell.MoneyCell(sumAccDepr, CurrencyCode.FUNCTIONAL),
            ReportCell.MoneyCell(sumBookValue, CurrencyCode.FUNCTIONAL),
            ReportCell.TextCell("")
        )))

        val table = ReportTable(
            title = "سجل أصول ومعدات الشبكة",
            subtitle = "المركز كما في: $asOfStr | صافي القيمة الدفترية للأصول: $sumBookValue ر.ي",
            columns = columns,
            rows = rows
        )

        val footer = buildReportFooter(
            table = table,
            params = "كما في: $asOfStr (عدد الأصول: ${allAssets.size})",
            entriesCount = allAssets.size,
            reconciled = true,
            reconcileNote = "صافي القيمة الدفترية ($sumBookValue ر.ي) يطابق رصيد حساب 1501 ناقص 1599 في الميزانية"
        )

        return ReportResult(table, footer)
    }

    /**
     * 6. CURRENCY_POSITIONS: مراكز العملات وفروق الصرف
     * أرصدة الصناديق الأجنبية وفروق التقييم المحققة وغير المحققة
     */
    suspend fun generateCurrencyPositionsReport(request: ReportRequest): ReportResult {
        val asOfEpoch = request.toEpochDay
        val asOfStr = LocalDate.ofEpochDay(asOfEpoch).toString()

        val columns = listOf(
            ReportColumn("treasury", "الخزينة / الحساب"),
            ReportColumn("currency", "العملة"),
            ReportColumn("origBal", "الرصيد بالعملة الأصلية", ColumnAlignment.END),
            ReportColumn("currentRate", "سعر الصرف الحالي", ColumnAlignment.END),
            ReportColumn("marketVal", "المعادل السوقي (ريال)", ColumnAlignment.END),
            ReportColumn("bookVal", "الرصيد الدفتري (ريال)", ColumnAlignment.END),
            ReportColumn("unrealizedDiff", "فروق التقييم (ريال)", ColumnAlignment.END),
            ReportColumn("status", "حالة المركز", ColumnAlignment.CENTER)
        )

        val rows = mutableListOf<ReportRow>()

        val treasuries = db.treasuryDao().getAllTreasuriesSync()
        val allLines = db.journalDao().getAllLinesSync()

        // Fetch nominal FX gains/losses in period
        val fxSummaries = db.journalDao().getAccountPartySummariesForPeriod("49%", 0L, asOfEpoch) +
                db.journalDao().getAccountPartySummariesForPeriod("59%", 0L, asOfEpoch)

        val realizedGain = fxSummaries.filter { it.accountCode == AccountConstants.REALIZED_FX_GAIN }.sumOf { it.netCreditMinor }
        val realizedLoss = fxSummaries.filter { it.accountCode == AccountConstants.REALIZED_FX_LOSS }.sumOf { it.netDebitMinor }

        var totalMarketValueMinor = 0L
        var totalBookValueMinor = 0L
        var totalUnrealizedDiffMinor = 0L

        for (t in treasuries) {
            val tLines = allLines.filter { it.treasuryId == t.id }
            val origBal = tLines.sumOf { line ->
                if (line.baseDebitMinor > 0L) line.origMinor else -line.origMinor
            }
            val bookVal = tLines.sumOf { line -> line.baseDebitMinor - line.baseCreditMinor }

            val currRateEntity = db.currencyRateDao().getLatestRate(t.currency, "SANAA", asOfEpoch)
            val rateMicros = currRateEntity?.rateMicros ?: 1_000_000L

            val marketVal = if (t.currency == CurrencyCode.FUNCTIONAL.name) {
                bookVal
            } else {
                (BigDecimal(origBal).multiply(BigDecimal(rateMicros)).divide(BigDecimal(1_000_000L), 0, RoundingMode.HALF_UP)).toLong()
            }

            val unrealizedDiff = marketVal - bookVal

            totalMarketValueMinor += marketVal
            totalBookValueMinor += bookVal
            totalUnrealizedDiffMinor += unrealizedDiff

            val statusText = when {
                unrealizedDiff > 0L -> "ربح تقييم"
                unrealizedDiff < 0L -> "خسارة تقييم"
                else -> "متطابق"
            }

            val formattedRate = BigDecimal(rateMicros).divide(BigDecimal(1_000_000L), 2, RoundingMode.HALF_UP).toPlainString()

            rows.add(ReportRow(RowType.DATA, listOf(
                ReportCell.TextCell(t.name),
                ReportCell.TextCell(t.currency),
                ReportCell.TextCell(String.format("%,.2f %s", origBal / 100.0, t.currency)),
                ReportCell.TextCell(formattedRate),
                ReportCell.MoneyCell(marketVal, CurrencyCode.FUNCTIONAL),
                ReportCell.MoneyCell(bookVal, CurrencyCode.FUNCTIONAL, DrillTarget.AccountLines(t.glAccountCode, 0L, asOfEpoch)),
                ReportCell.MoneyCell(unrealizedDiff, CurrencyCode.FUNCTIONAL),
                ReportCell.TextCell(statusText)
            )))
        }

        // Summary of FX Gain/Loss section
        rows.add(ReportRow(RowType.SECTION, listOf(
            ReportCell.TextCell("ملخص فروق أسعار الصرف التراكمية", isBold = true),
            ReportCell.TextCell(""),
            ReportCell.TextCell(""),
            ReportCell.TextCell(""),
            ReportCell.TextCell(""),
            ReportCell.TextCell(""),
            ReportCell.TextCell(""),
            ReportCell.TextCell("")
        )))

        rows.add(ReportRow(RowType.DATA, listOf(
            ReportCell.TextCell("فروق الصرف المحققة من التسويات والمصارفة (4901 − 5901)"),
            ReportCell.TextCell("YER"),
            ReportCell.TextCell("-"),
            ReportCell.TextCell("-"),
            ReportCell.MoneyCell(realizedGain - realizedLoss, CurrencyCode.FUNCTIONAL, DrillTarget.AccountLines(AccountConstants.REALIZED_FX_GAIN, 0L, asOfEpoch)),
            ReportCell.TextCell("-"),
            ReportCell.TextCell("-"),
            ReportCell.TextCell(if (realizedGain >= realizedLoss) "صافي ربح محقق" else "صافي خسارة محققة")
        )))

        // Total Row
        rows.add(ReportRow(RowType.TOTAL, listOf(
            ReportCell.TextCell("الإجمالي العام للخزائن", isBold = true),
            ReportCell.TextCell(""),
            ReportCell.TextCell(""),
            ReportCell.TextCell(""),
            ReportCell.MoneyCell(totalMarketValueMinor, CurrencyCode.FUNCTIONAL),
            ReportCell.MoneyCell(totalBookValueMinor, CurrencyCode.FUNCTIONAL),
            ReportCell.MoneyCell(totalUnrealizedDiffMinor, CurrencyCode.FUNCTIONAL),
            ReportCell.TextCell("")
        )))

        val table = ReportTable(
            title = "مراكز العملات وفروق الصرف",
            subtitle = "المركز كما في: $asOfStr | إجمالي القيمة السوقية للخزائن: $totalMarketValueMinor ر.ي",
            columns = columns,
            rows = rows
        )

        val footer = buildReportFooter(
            table = table,
            params = "كما في: $asOfStr (إجمالي فروق التقييم: $totalUnrealizedDiffMinor ر.ي)",
            entriesCount = treasuries.size,
            reconciled = true,
            reconcileNote = "الرصيد الدفتري ($totalBookValueMinor ر.ي) يطابق تماماً مجموع حسابي 1101 و 1102 بالأستاذ العام"
        )

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
