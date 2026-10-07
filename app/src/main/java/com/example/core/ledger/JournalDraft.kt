package com.example.core.ledger

import com.example.core.model.CurrencyCode

data class JournalDraftLine(
    val lineNo: Int,
    val accountCode: String,
    val partyId: String? = null,
    val treasuryId: String? = null,
    val origMinor: Long,
    val currency: CurrencyCode,
    val exchangeRateMicros: Long,
    val baseDebitMinor: Long,
    val baseCreditMinor: Long,
    val memo: String
) {
    init {
        require(origMinor >= 0L) { "Line original minor amount must be non-negative (got $origMinor)" }
        require(baseDebitMinor >= 0L) { "Base debit must be non-negative" }
        require(baseCreditMinor >= 0L) { "Base credit must be non-negative" }
        require(baseDebitMinor > 0L || baseCreditMinor > 0L) { "Line must have either debit or credit amount" }
        require(!(baseDebitMinor > 0L && baseCreditMinor > 0L)) { "Line cannot have both debit and credit amounts" }

        // Require partyId on control accounts
        if (AccountConstants.SYSTEM_CONTROL_ACCOUNTS.contains(accountCode)) {
            require(!partyId.isNullOrBlank()) {
                "Control account $accountCode requires an explicit partyId"
            }
        }
    }
}

data class JournalDraft(
    val type: JournalEntryType,
    val entryDateEpochDay: Long,
    val memo: String,
    val lines: List<JournalDraftLine>
) {
    init {
        require(lines.size >= 2) { "A valid double-entry journal draft must have at least 2 lines" }
        assertBalanced()
    }

    val totalDebitMinor: Long get() = lines.sumOf { it.baseDebitMinor }
    val totalCreditMinor: Long get() = lines.sumOf { it.baseCreditMinor }

    fun assertBalanced() {
        val debitSum = lines.sumOf { it.baseDebitMinor }
        val creditSum = lines.sumOf { it.baseCreditMinor }
        check(debitSum == creditSum) {
            "Out of balance: total debits ($debitSum) does not match total credits ($creditSum) for entry '$memo'"
        }
    }
}
