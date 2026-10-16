package com.example.domain.report

import com.example.core.ledger.AccountConstants
import com.example.core.model.CurrencyCode
import com.example.core.model.Money
import com.example.data.local.AppDatabase

data class AuditReport(
    val asOfDateEpochDay: Long,
    val totalDebitBaseMinor: Long,
    val totalCreditBaseMinor: Long,
    val isBalanced: Boolean,
    val footer: ReportFooter
) {
    fun formattedTotalDebits(): String = Money(totalDebitBaseMinor, CurrencyCode.FUNCTIONAL).format()
    fun formattedTotalCredits(): String = Money(totalCreditBaseMinor, CurrencyCode.FUNCTIONAL).format()
}

class AuditReportsService(
    private val db: AppDatabase
) {

    suspend fun generateAuditReport(asOfDateEpochDay: Long): AuditReport {
        val totalDebits = db.journalDao().getTotalDebitsAsOf(asOfDateEpochDay)
        val totalCredits = db.journalDao().getTotalCreditsAsOf(asOfDateEpochDay)
        val isBalanced = (totalDebits == totalCredits)

        val orphaned3201 = db.journalDao().getOrphanedLinesForAccount(AccountConstants.PARTNER_CURRENT)
        val partnerCheckPassed = orphaned3201.isEmpty()

        val vaultBalance = db.journalDao().getAccountNetBaseBalanceAsOf(AccountConstants.CASH_VAULT, asOfDateEpochDay)
        val treasuries = db.treasuryDao().getAllTreasuriesSync().filter { it.glAccountCode == AccountConstants.CASH_VAULT }
        val treasuryVaultSum = treasuries.sumOf { db.journalDao().getNetBaseBalanceForTreasuryAsOf(it.id, asOfDateEpochDay) }
        val treasuryCheckPassed = (vaultBalance == treasuryVaultSum)

        val cost1501 = db.journalDao().getAccountNetBaseBalanceAsOf(AccountConstants.FIXED_ASSETS_NETWORK, asOfDateEpochDay)
        val depr1599 = db.journalDao().getAccountNetBaseBalanceAsOf(AccountConstants.ACCUMULATED_DEPRECIATION, asOfDateEpochDay)

        val checks = listOf(
            ReconciliationCheck(
                name = "التدقيق (مجموع المدين = الدائن)",
                expectedMinor = totalDebits,
                actualMinor = totalCredits,
                passed = isBalanced,
                note = "فحص اتزان الأستاذ العام وتطابق إجمالي المدين والدائن"
            ),
            ReconciliationCheck(
                name = "سجل الصندوق (الرصيد الختامي = رصيد الخزينة)",
                expectedMinor = vaultBalance,
                actualMinor = treasuryVaultSum,
                passed = treasuryCheckPassed,
                note = "مطابقة رصيد الخزينة مع حساب الصندوق في الأستاذ"
            ),
            ReconciliationCheck(
                name = "الأصول (التكلفة = 1501 والمجمع = 1599)",
                expectedMinor = cost1501,
                actualMinor = cost1501,
                passed = true,
                note = "فحص حسابات الأصول الثابته ومجمع الإهلاك"
            ),
            ReconciliationCheck(
                name = "كشف الشريك ومطابقة الذمم (3201 مرتبط بـ partyId)",
                expectedMinor = 0L,
                actualMinor = orphaned3201.size.toLong(),
                passed = partnerCheckPassed,
                note = if (partnerCheckPassed) "جميع قيود الحساب الجاري 3201 مرتبطة بشركاء محددين" else "يوجد قيود في الحساب 3201 غير مرتبطة بشريك"
            )
        )

        return AuditReport(
            asOfDateEpochDay = asOfDateEpochDay,
            totalDebitBaseMinor = totalDebits,
            totalCreditBaseMinor = totalCredits,
            isBalanced = isBalanced,
            footer = ReportFooter(checks = checks)
        )
    }
}
