package com.example.domain.report

import com.example.core.model.CurrencyCode
import com.example.core.model.Money

data class ReconciliationCheck(
    val name: String,
    val expectedMinor: Long,
    val actualMinor: Long,
    val passed: Boolean,
    val note: String = ""
) {
    val discrepancyMinor: Long
        get() = expectedMinor - actualMinor

    fun formattedDiscrepancy(currency: CurrencyCode = CurrencyCode.FUNCTIONAL): String {
        return Money(discrepancyMinor, currency).format()
    }
}

data class ReportFooter(
    val checks: List<ReconciliationCheck> = emptyList(),
    val notes: String = ""
) {
    val reconciled: Boolean
        get() = checks.isNotEmpty() && checks.all { it.passed }

    val failingChecks: List<ReconciliationCheck>
        get() = checks.filterNot { it.passed }
}
