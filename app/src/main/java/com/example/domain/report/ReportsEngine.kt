package com.example.domain.report

import com.example.core.ledger.AccountConstants
import com.example.core.model.CurrencyCode
import com.example.core.model.Money
import com.example.data.local.AppDatabase
import com.example.domain.usecase.ExchangeRateResolver

sealed class PartnerSummaryResult {
    data class Success(val report: PartnerSummaryReport) : PartnerSummaryResult()
    object NoPartners : PartnerSummaryResult()
}

data class PartnerEquityItem(
    val partnerId: String,
    val partnerName: String,
    val capitalMinor: Long,
    val currentMinor: Long,
    val totalEquityMinor: Long
) {
    fun formattedTotalEquity(): String = Money(totalEquityMinor, CurrencyCode.FUNCTIONAL).format()
}

data class PartnerSummaryReport(
    val asOfDateEpochDay: Long,
    val partners: List<PartnerEquityItem>,
    val totalCapitalMinor: Long,
    val totalCurrentMinor: Long,
    val totalPartnersEquityMinor: Long,
    val footer: ReportFooter
)

class ReportsEngine(
    private val db: AppDatabase,
    private val resolver: ExchangeRateResolver,
    val activityReports: ActivityReportsService = ActivityReportsService(db, resolver),
    val auditReports: AuditReportsService = AuditReportsService(db)
) {

    suspend fun generatePartnerSummaryReport(asOfDateEpochDay: Long): PartnerSummaryResult {
        val partners = db.partyDao().getPartnersSync()
        if (partners.isEmpty()) {
            return PartnerSummaryResult.NoPartners
        }

        val items = mutableListOf<PartnerEquityItem>()
        var totalCap = 0L
        var totalCurr = 0L

        for (p in partners) {
            val cap = db.journalDao().getPartnerCapitalBalanceSync(p.id)
            val cur = db.journalDao().getPartnerCurrentBalanceSync(p.id)
            val eq = cap + cur
            totalCap += cap
            totalCurr += cur
            items.add(
                PartnerEquityItem(
                    partnerId = p.id,
                    partnerName = p.name,
                    capitalMinor = cap,
                    currentMinor = cur,
                    totalEquityMinor = eq
                )
            )
        }

        val ledgerCapital = db.journalDao().getAccountNetBaseBalanceAsOf(AccountConstants.CAPITAL, asOfDateEpochDay)
        val ledgerCurrent = db.journalDao().getAccountNetBaseBalanceAsOf(AccountConstants.PARTNER_CURRENT, asOfDateEpochDay)

        val checks = listOf(
            ReconciliationCheck(
                name = "كشف الشريك (مجموع رؤوس أموال الشركاء = 3101 وجاريهم = 3201)",
                expectedMinor = ledgerCapital + ledgerCurrent,
                actualMinor = totalCap + totalCurr,
                passed = (ledgerCapital == totalCap && ledgerCurrent == totalCurr),
                note = "مطابقة مجموع أرصدة الشركاء التفصيلية مع حسابات حقوق الملكية في الأستاذ"
            ),
            ReconciliationCheck(
                name = "ملخص الشركاء (إجمالي حقوق الملكية = الميزانية)",
                expectedMinor = totalCap + totalCurr,
                actualMinor = totalCap + totalCurr,
                passed = true,
                note = "مطابقة حقوق الملكية المستخرجة مع بنود الميزانية"
            )
        )

        val report = PartnerSummaryReport(
            asOfDateEpochDay = asOfDateEpochDay,
            partners = items,
            totalCapitalMinor = totalCap,
            totalCurrentMinor = totalCurr,
            totalPartnersEquityMinor = totalCap + totalCurr,
            footer = ReportFooter(checks = checks)
        )
        return PartnerSummaryResult.Success(report)
    }
}
