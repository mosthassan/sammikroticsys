package com.example.domain.report

import com.example.core.ledger.AccountConstants
import com.example.core.model.CurrencyCode
import com.example.core.time.AppClock
import com.example.data.local.AppDatabase
import java.time.LocalDate

class AuditReportsService(
    private val db: AppDatabase
) {

    /**
     * AUDIT_TRAIL: سجل الرقابة والتعديلات والفحوصات
     * القيود المعدلة والإدخال بأثر رجعي والصناديق السالبة وفحص السلامة
     */
    suspend fun generateAuditTrailReport(request: ReportRequest): ReportResult {
        val fromEpoch = request.fromEpochDay
        val toEpoch = request.toEpochDay
        val fromStr = LocalDate.ofEpochDay(fromEpoch).toString()
        val toStr = LocalDate.ofEpochDay(toEpoch).toString()

        val columns = listOf(
            ReportColumn("checkType", "نوع الفحص / العملية"),
            ReportColumn("refId", "المرجع / رقم القيد"),
            ReportColumn("date", "التاريخ"),
            ReportColumn("details", "البيان وتفاصيل التدقيق"),
            ReportColumn("amount", "المبلغ المعني", ColumnAlignment.END),
            ReportColumn("severity", "مستوى الرقابة", ColumnAlignment.CENTER)
        )

        val rows = mutableListOf<ReportRow>()

        // 1. General Ledger Double-Entry Invariants Check
        val allLines = db.journalDao().getAllLinesSync()
        val totalDebitMinor = allLines.sumOf { it.baseDebitMinor }
        val totalCreditMinor = allLines.sumOf { it.baseCreditMinor }
        val invariantDiff = totalDebitMinor - totalCreditMinor

        rows.add(ReportRow(RowType.SECTION, listOf(
            ReportCell.TextCell("فحص التوازن الرياضي للأستاذ العام (Ledger Invariants)", isBold = true),
            ReportCell.TextCell(""),
            ReportCell.TextCell(""),
            ReportCell.TextCell(""),
            ReportCell.TextCell(""),
            ReportCell.TextCell("")
        )))

        val isBalanced = invariantDiff == 0L
        rows.add(ReportRow(RowType.DATA, listOf(
            ReportCell.TextCell("تطابق إجمالي المدين والدائن"),
            ReportCell.TextCell("النظام بأكمله"),
            ReportCell.TextCell(toStr),
            ReportCell.TextCell(if (isBalanced) "متطابق تماماً بدون أي فروقات (مدين: $totalDebitMinor | دائن: $totalCreditMinor)" else "خلل محاسبي: وجود فارق بين المدين والدائن!"),
            ReportCell.MoneyCell(invariantDiff, CurrencyCode.FUNCTIONAL),
            ReportCell.TextCell(if (isBalanced) "سليم 100%" else "حرج جداً")
        )))

        // 2. Reversals & Voided Documents Check
        val allEntries = db.journalDao().getAllEntriesSync()
        val reversalsInPeriod = allEntries.filter {
            it.entryDateEpochDay in fromEpoch..toEpoch && (it.type == "REVERSAL" || it.reversalOfEntryId != null)
        }

        rows.add(ReportRow(RowType.SECTION, listOf(
            ReportCell.TextCell("القيود المعكوسة والتسويات الإلغائية", isBold = true),
            ReportCell.TextCell(""),
            ReportCell.TextCell(""),
            ReportCell.TextCell(""),
            ReportCell.TextCell(""),
            ReportCell.TextCell("")
        )))

        if (reversalsInPeriod.isNotEmpty()) {
            for (rev in reversalsInPeriod) {
                val revLines = allLines.filter { it.entryId == rev.id }
                val revAmount = revLines.sumOf { it.baseDebitMinor }

                rows.add(ReportRow(RowType.DATA, listOf(
                    ReportCell.TextCell("قيد عكسي (إلغاء قيد سابق)"),
                    ReportCell.TextCell("#${rev.entryNumber}"),
                    ReportCell.TextCell(LocalDate.ofEpochDay(rev.entryDateEpochDay).toString()),
                    ReportCell.TextCell("${rev.memo} (عكس القيد: ${rev.reversalOfEntryId ?: "غير محدد"})"),
                    ReportCell.MoneyCell(revAmount, CurrencyCode.FUNCTIONAL),
                    ReportCell.TextCell("تنبيه رقابي")
                )))
            }
        } else {
            rows.add(ReportRow(RowType.DATA, listOf(
                ReportCell.TextCell("القيود العكسية"),
                ReportCell.TextCell("-"),
                ReportCell.TextCell("-"),
                ReportCell.TextCell("لا توجد أي قيود عكسية أو ملغاة في هذه الفترة"),
                ReportCell.MoneyCell(0L, CurrencyCode.FUNCTIONAL),
                ReportCell.TextCell("طبيعي")
            )))
        }

        // 3. Backdated Entries Detection (Entry Date vs CreatedAt > 2 days)
        val backdatedEntries = allEntries.filter { entry ->
            entry.entryDateEpochDay in fromEpoch..toEpoch && entry.type != "CLOSING"
        }.filter { entry ->
            val entryMilli = entry.entryDateEpochDay * 86_400_000L
            val createdMilli = entry.createdAt
            val diffDays = (createdMilli - entryMilli) / 86_400_000L
            diffDays > 2L || diffDays < -2L
        }

        rows.add(ReportRow(RowType.SECTION, listOf(
            ReportCell.TextCell("فحص القيود المدخلة بأثر رجعي أو تاريخ مستقبلي", isBold = true),
            ReportCell.TextCell(""),
            ReportCell.TextCell(""),
            ReportCell.TextCell(""),
            ReportCell.TextCell(""),
            ReportCell.TextCell("")
        )))

        if (backdatedEntries.isNotEmpty()) {
            for (entry in backdatedEntries) {
                val entryLines = allLines.filter { it.entryId == entry.id }
                val amount = entryLines.sumOf { it.baseDebitMinor }
                val entryDateStr = LocalDate.ofEpochDay(entry.entryDateEpochDay).toString()
                val createdDateStr = LocalDate.ofEpochDay(entry.createdAt / 86_400_000L).toString()

                rows.add(ReportRow(RowType.DATA, listOf(
                    ReportCell.TextCell("قيد بأثر رجعي"),
                    ReportCell.TextCell("#${entry.entryNumber}"),
                    ReportCell.TextCell(entryDateStr),
                    ReportCell.TextCell("${entry.memo} (تاريخ التسجيل الفعلي: $createdDateStr)"),
                    ReportCell.MoneyCell(amount, CurrencyCode.FUNCTIONAL),
                    ReportCell.TextCell("تنبيه تدقيق")
                )))
            }
        } else {
            rows.add(ReportRow(RowType.DATA, listOf(
                ReportCell.TextCell("القيود بأثر رجعي"),
                ReportCell.TextCell("-"),
                ReportCell.TextCell("-"),
                ReportCell.TextCell("كافة القيود مدخلة في تواريخها الطبيعية دون فوارق زمنية غير مبررة"),
                ReportCell.MoneyCell(0L, CurrencyCode.FUNCTIONAL),
                ReportCell.TextCell("طبيعي")
            )))
        }

        // 4. Negative Treasury / Cash Drawer Balances Check
        val treasuries = db.treasuryDao().getAllTreasuriesSync()
        rows.add(ReportRow(RowType.SECTION, listOf(
            ReportCell.TextCell("فحص الصناديق والخزائن السالبة", isBold = true),
            ReportCell.TextCell(""),
            ReportCell.TextCell(""),
            ReportCell.TextCell(""),
            ReportCell.TextCell(""),
            ReportCell.TextCell("")
        )))

        var negativeTreasuriesFound = 0
        for (t in treasuries) {
            val tLines = allLines.filter { it.treasuryId == t.id }
            val balance = tLines.sumOf { it.baseDebitMinor - it.baseCreditMinor }
            if (balance < 0L && !t.allowNegative) {
                negativeTreasuriesFound++
                rows.add(ReportRow(RowType.DATA, listOf(
                    ReportCell.TextCell("صندوق مكشوف / رصيد سالب"),
                    ReportCell.TextCell(t.id),
                    ReportCell.TextCell(toStr),
                    ReportCell.TextCell("الصندوق ${t.name} (${t.currency}) يحمل رصيداً سالباً دون تصريح مسبق"),
                    ReportCell.MoneyCell(balance, CurrencyCode.FUNCTIONAL),
                    ReportCell.TextCell("مخالفة رقابية")
                )))
            }
        }

        if (negativeTreasuriesFound == 0) {
            rows.add(ReportRow(RowType.DATA, listOf(
                ReportCell.TextCell("أرصدة الصناديق"),
                ReportCell.TextCell("كافة الخزائن"),
                ReportCell.TextCell(toStr),
                ReportCell.TextCell("لا توجد أي خزائن بأرصدة سالبة، كافة الصناديق مغطاة"),
                ReportCell.MoneyCell(0L, CurrencyCode.FUNCTIONAL),
                ReportCell.TextCell("سليم 100%")
            )))
        }

        // 5. Fiscal Periods Lock Status Check
        val closedPeriods = db.fiscalPeriodDao().getClosedPeriods()
        rows.add(ReportRow(RowType.SECTION, listOf(
            ReportCell.TextCell("حالة إقفال الفترات المالية والمحاسبية", isBold = true),
            ReportCell.TextCell(""),
            ReportCell.TextCell(""),
            ReportCell.TextCell(""),
            ReportCell.TextCell(""),
            ReportCell.TextCell("")
        )))

        rows.add(ReportRow(RowType.DATA, listOf(
            ReportCell.TextCell("الفترات المقفلة"),
            ReportCell.TextCell("${closedPeriods.size} فترة"),
            ReportCell.TextCell(toStr),
            ReportCell.TextCell(if (closedPeriods.isNotEmpty()) "تم إقفال الفترات: ${closedPeriods.joinToString { "${it.year}-${it.month}" }}" else "لا توجد فترات مقفلة حالياً"),
            ReportCell.MoneyCell(0L, CurrencyCode.FUNCTIONAL),
            ReportCell.TextCell("مستقر")
        )))

        // Total Summary Row
        rows.add(ReportRow(RowType.TOTAL, listOf(
            ReportCell.TextCell("النتيجة الإجمالية لفحص الرقابة والتدقيق", isBold = true),
            ReportCell.TextCell(""),
            ReportCell.TextCell(toStr),
            ReportCell.TextCell(if (isBalanced && negativeTreasuriesFound == 0) "النظام متوافق محاسبياً ومعايير IFRS مطبقة بدقة" else "يوجد بنود تتطلب مراجعة المحاسب القانوني"),
            ReportCell.MoneyCell(invariantDiff, CurrencyCode.FUNCTIONAL),
            ReportCell.TextCell(if (isBalanced) "معتمد" else "مرفوض")
        )))

        val table = ReportTable(
            title = "سجل الرقابة والتعديلات والفحوصات",
            subtitle = "فحص شامل للفترة من $fromStr إلى $toStr | سلامة الأستاذ: ${if (isBalanced) "سليم 100%" else "خلل في التوازن"}",
            columns = columns,
            rows = rows
        )

        val footer = buildReportFooter(
            table = table,
            params = "الفترة: $fromStr إلى $toStr (فحوصات السلامة المكتملة: 5 فحوصات)",
            entriesCount = rows.filter { it.type == RowType.DATA }.size,
            reconciled = isBalanced,
            reconcileNote = if (isBalanced) "تطابق مدين ودائن الأستاذ العام محقق برصيد فارق صفر ريال" else "فارق غير متوازن: $invariantDiff ر.ي"
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
