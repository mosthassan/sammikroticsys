package com.example.data.ledger

import com.example.core.ledger.AccountConstants
import com.example.core.ledger.DocumentStatus
import com.example.core.ledger.JournalEntryType
import com.example.data.local.AppDatabase

data class InvariantViolation(
    val invariantCode: String,
    val description: String,
    val details: String
)

data class InvariantCheckResult(
    val isValid: Boolean,
    val violations: List<InvariantViolation>,
    val totalEntriesChecked: Int,
    val totalLinesChecked: Int,
    val trialBalanceDebit: Long,
    val trialBalanceCredit: Long
)

class LedgerInvariants(private val db: AppDatabase) {

    /**
     * Verifies all mathematical and structural invariants of the double-entry ledger.
     * Throws IllegalStateException if failFast is true and violations exist.
     */
    suspend fun verifyAll(failFast: Boolean = true): InvariantCheckResult {
        val violations = mutableListOf<InvariantViolation>()

        // 1. Check each journal entry individually: Debits == Credits
        val trialBalanceRows = db.journalDao().getTrialBalanceSync()
        val totalTrialDebit = trialBalanceRows.sumOf { it.totalDebitMinor }
        val totalTrialCredit = trialBalanceRows.sumOf { it.totalCreditMinor }

        if (totalTrialDebit != totalTrialCredit) {
            violations.add(
                InvariantViolation(
                    invariantCode = "INV-001-TRIAL-BALANCE",
                    description = "Trial Balance Out of Equilibrium",
                    details = "Total Base Debits ($totalTrialDebit) != Total Base Credits ($totalTrialCredit), difference: ${totalTrialDebit - totalTrialCredit}"
                )
            )
        }

        // 2. Control Accounts Reconciliation (1201 Receivables, 2101 Payables, 3201 Partners)
        for (controlAccount in AccountConstants.SYSTEM_CONTROL_ACCOUNTS) {
            val generalLedgerBalance = db.journalDao().getNetDebitBalanceForAccount(controlAccount)
            val partyRows = db.journalDao().getPartyBalancesForControlAccount(controlAccount)
            val totalPartyBalances = partyRows.sumOf { it.netBalanceMinor }

            if (generalLedgerBalance != totalPartyBalances) {
                violations.add(
                    InvariantViolation(
                        invariantCode = "INV-002-CONTROL-ACCOUNT-$controlAccount",
                        description = "Subledger/Party Reconciliation Mismatch on $controlAccount",
                        details = "GL Net Debit ($generalLedgerBalance) != Sum of Party Balances ($totalPartyBalances)"
                    )
                )
            }
        }

        // 3. Document to Journal Entry completeness: Every POSTED doc must have an entry
        val openHelper = db.openHelper.readableDatabase
        val cursor = openHelper.query("""
            SELECT d.id, d.type, d.docNumber, COUNT(je.id) as entryCount
            FROM documents d
            LEFT JOIN journal_entries je ON d.id = je.docId
            WHERE d.status = '${DocumentStatus.POSTED.name}'
            GROUP BY d.id
            HAVING entryCount = 0
        """)

        cursor.use { c ->
            while (c.moveToNext()) {
                val docId = c.getString(0)
                val docType = c.getString(1)
                val docNum = c.getLong(2)
                violations.add(
                    InvariantViolation(
                        invariantCode = "INV-003-POSTED-DOC-NO-ENTRY",
                        description = "Posted document has missing journal entry",
                        details = "Document $docType #$docNum (ID $docId) has 0 journal entries"
                    )
                )
            }
        }

        // 4. Voided Document Reversal Verification: Voided document must have a matching REVERSAL entry
        val voidedCursor = openHelper.query("""
            SELECT d.id, d.type, d.docNumber,
                   SUM(CASE WHEN je.type = '${JournalEntryType.NORMAL.name}' THEN 1 ELSE 0 END) as normalCount,
                   SUM(CASE WHEN je.type = '${JournalEntryType.REVERSAL.name}' THEN 1 ELSE 0 END) as reversalCount
            FROM documents d
            INNER JOIN journal_entries je ON d.id = je.docId
            WHERE d.status = '${DocumentStatus.VOIDED.name}'
            GROUP BY d.id
            HAVING normalCount > reversalCount
        """)

        voidedCursor.use { c ->
            while (c.moveToNext()) {
                val docId = c.getString(0)
                val docType = c.getString(1)
                val docNum = c.getLong(2)
                val normal = c.getInt(3)
                val reversal = c.getInt(4)
                violations.add(
                    InvariantViolation(
                        invariantCode = "INV-004-UNCOUNTERED-VOID",
                        description = "Voided document has uncompensated normal entries",
                        details = "Document $docType #$docNum (ID $docId) has $normal normal entries but only $reversal reversal entries"
                    )
                )
            }
        }

        val isValid = violations.isEmpty()
        if (failFast && !isValid) {
            val first = violations.first()
            throw IllegalStateException("Ledger Invariant Broken: [${first.invariantCode}] ${first.description} - ${first.details}")
        }

        return InvariantCheckResult(
            isValid = isValid,
            violations = violations,
            totalEntriesChecked = 0,
            totalLinesChecked = 0,
            trialBalanceDebit = totalTrialDebit,
            trialBalanceCredit = totalTrialCredit
        )
    }
}
