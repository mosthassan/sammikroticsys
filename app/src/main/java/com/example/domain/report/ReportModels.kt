package com.example.domain.report

import com.example.core.model.CurrencyCode

enum class ReportCategory(val title: String) {
    FINANCIAL_STATEMENTS("القوائم المالية"),
    PROFITS("الأرباح والمقارنة"),
    PARTNERS("الشركاء وحقوق الملكية"),
    CASH("النقدية والخزائن"),
    SALES("المبيعات والعملاء"),
    EXPENSES("المصروفات والتشغيل"),
    INVENTORY_ASSETS("المخزون والأصول"),
    CURRENCIES("العملات والصرافة"),
    AUDIT("الرقابة والتدقيق")
}

enum class ReportType(
    val category: ReportCategory,
    val title: String,
    val subtitle: String
) {
    INCOME_STATEMENT(
        ReportCategory.FINANCIAL_STATEMENTS,
        "قائمة الدخل",
        "بيان الإيرادات والمصروفات وصافي الربح المحاسبي للفترة"
    ),
    BALANCE_SHEET(
        ReportCategory.FINANCIAL_STATEMENTS,
        "الميزانية العمومية",
        "المركز المالي والأصول والالتزامات وحقوق الملكية"
    ),
    TRIAL_BALANCE(
        ReportCategory.FINANCIAL_STATEMENTS,
        "ميزان المراجعة ثنائي العملة",
        "مطابقة الأرصدة المدينة والدائنة بالعملة الأصلية والوظيفية"
    ),
    PROFIT_MONTHLY(
        ReportCategory.PROFITS,
        "الأرباح الشهرية وقائمة الدخل المقارنة",
        "تفكيك الأرباح على مدى 12 شهراً مع شارات الثقة والنسب"
    ),
    PARTNER_STATEMENT(
        ReportCategory.PARTNERS,
        "كشف حركة الشريك",
        "حركة رأس المال والجاري والمساهمات والسحوبات لكل شريك"
    ),
    PARTNERS_EQUITY_SUMMARY(
        ReportCategory.PARTNERS,
        "ملخص الشركاء وقائمة التغير في حقوق الملكية",
        "تطور حصص الملكية والأرباح غير المقفلة وجاري الشركاء"
    ),
    CASH_FLOW(
        ReportCategory.CASH,
        "قائمة التدفقات النقدية (الطريقة المباشرة)",
        "التدفقات التشغيلية والاستثمارية والتمويلية من واقع الصناديق"
    ),
    DAILY_CASH_REGISTER(
        ReportCategory.CASH,
        "سجل الصندوق اليومي",
        "حركة المقبوضات والمدفوعات اليومية بالعملة الأصلية والمعادل"
    ),
    SALES_ANALYTICS(
        ReportCategory.SALES,
        "تحليل المبيعات",
        "المبيعات حسب باقات الكروت والخدمات والوكلاء"
    ),
    COLLECTIONS_DSO(
        ReportCategory.SALES,
        "التحصيل ومعدل أيام التحصيل (DSO)",
        "نسبة التحصيل وكبار المدينين ومؤشر دوران الذمم"
    ),
    AGING_RECEIVABLES(
        ReportCategory.SALES,
        "أعمار الديون",
        "توزيع مستحقات العملاء والوكلاء حسب فترات الاستحقاق"
    ),
    EXPENSE_ANALYSIS(
        ReportCategory.EXPENSES,
        "تحليل المصروفات وهامش الخدمة",
        "هيكل التكاليف المباشرة والتشغيلية وهامش ربحية الخدمة"
    ),
    CARD_INVENTORY_REPORT(
        ReportCategory.INVENTORY_ASSETS,
        "حركة مخزون الكروت",
        "الرصيد الافتتاحي والوارد والمبيعات والمرتجع والتسويات"
    ),
    FIXED_ASSETS_REGISTER(
        ReportCategory.INVENTORY_ASSETS,
        "سجل أصول ومعدات الشبكة",
        "التكلفة ومجمع الإهلاك والقيمة الدفترية وجداول الاستهلاك"
    ),
    CURRENCY_POSITIONS(
        ReportCategory.CURRENCIES,
        "مراكز العملات وفروق الصرف",
        "أرصدة الصناديق الأجنبية وفروق التقييم المحققة وغير المحققة"
    ),
    AUDIT_TRAIL(
        ReportCategory.AUDIT,
        "سجل الرقابة والتعديلات والفحوصات",
        "القيود المعدلة والإدخال بأثر رجعي والصناديق السالبة وفحص السلامة"
    )
}

data class ReportRequest(
    val type: ReportType,
    val fromEpochDay: Long,
    val toEpochDay: Long,
    val params: Map<String, String> = emptyMap()
)

enum class ColumnAlignment {
    START,
    CENTER,
    END
}

data class ReportColumn(
    val id: String,
    val title: String,
    val alignment: ColumnAlignment = ColumnAlignment.START
)

enum class RowType {
    DATA,
    SUBTOTAL,
    TOTAL,
    SECTION
}

sealed interface DrillTarget {
    data class AccountLines(
        val accountCode: String,
        val fromEpochDay: Long,
        val toEpochDay: Long,
        val partyId: String? = null
    ) : DrillTarget

    data class Document(
        val documentId: String
    ) : DrillTarget

    data class EntryLines(
        val entryId: String
    ) : DrillTarget
}

sealed class ReportCell(
    open val drillTarget: DrillTarget? = null
) {
    data class MoneyCell(
        val amountMinor: Long,
        val currency: CurrencyCode = CurrencyCode.FUNCTIONAL,
        override val drillTarget: DrillTarget? = null
    ) : ReportCell(drillTarget)

    data class TextCell(
        val text: String,
        val isBold: Boolean = false,
        override val drillTarget: DrillTarget? = null
    ) : ReportCell(drillTarget)

    data class PercentCell(
        val basisPoints: Int, // e.g. 10000 = 100.00%, -250 = -2.50%
        val isNegative: Boolean = false,
        override val drillTarget: DrillTarget? = null
    ) : ReportCell(drillTarget) {
        fun formatPercentText(): String {
            val isNeg = basisPoints < 0 || isNegative
            val absBps = if (basisPoints < 0) -basisPoints else basisPoints
            val integerPart = absBps / 100
            val fractionPart = absBps % 100
            val fracStr = if (fractionPart < 10) "0$fractionPart" else "$fractionPart"
            return if (isNeg) "-$integerPart.$fracStr%" else "$integerPart.$fracStr%"
        }
    }

    data class CountCell(
        val count: Long,
        override val drillTarget: DrillTarget? = null
    ) : ReportCell(drillTarget)

    data class DateCell(
        val epochDay: Long,
        override val drillTarget: DrillTarget? = null
    ) : ReportCell(drillTarget)
}

data class ReportRow(
    val type: RowType,
    val cells: List<ReportCell>,
    val id: String = ""
)

data class ReportTable(
    val title: String,
    val subtitle: String = "",
    val columns: List<ReportColumn>,
    val rows: List<ReportRow>
)

data class ReportFooter(
    val issuedAtEpochMilli: Long,
    val issuedAtText: String,
    val parametersSummary: String,
    val entriesCount: Int,
    val reconciled: Boolean,
    val reconciliationDetails: String? = null,
    val contentHashSha256: String
)

data class ReportResult(
    val table: ReportTable,
    val footer: ReportFooter
)
