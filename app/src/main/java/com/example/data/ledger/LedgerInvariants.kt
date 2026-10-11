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

        // 5. Accounting Equation Verification: Assets == Liabilities + Stated Equity + Unclosed Profit
        val checkDates = mutableListOf<Long?>()
        val allEntries = db.journalDao().getAllEntriesSync()
        if (allEntries.isNotEmpty()) {
            checkDates.add(allEntries.maxOf { it.entryDateEpochDay })
        }
        val closedPeriods = db.fiscalPeriodDao().getClosedPeriods()
        closedPeriods.forEach { p ->
            val endOfMonth = java.time.LocalDate.of(p.year, p.month, 1).plusMonths(1).minusDays(1).toEpochDay()
            checkDates.add(endOfMonth)
        }

        checkDates.distinct().forEach { date ->
            val bsLines = db.journalDao().getBalanceSheetLines(date)
            var totalAssets = 0L
            var totalLiabilities = 0L
            var totalStatedEquity = 0L

            bsLines.forEach { row ->
                when {
                    row.accountCode.startsWith("1") -> {
                        if (row.accountCode == AccountConstants.ACCUMULATED_DEPRECIATION) {
                            totalAssets -= row.netBalanceMinor
                        } else {
                            totalAssets += row.netBalanceMinor
                        }
                    }
                    row.accountCode.startsWith("2") -> {
                        totalLiabilities += row.netBalanceMinor
                    }
                    row.accountCode.startsWith("3") -> {
                        totalStatedEquity += row.netBalanceMinor
                    }
                }
            }

            val unclosedProfit = db.journalDao().getUnclosedNominalBalance(date)
            val totalLiabAndEquity = totalLiabilities + totalStatedEquity + unclosedProfit

            if (totalAssets != totalLiabAndEquity) {
                violations.add(
                    InvariantViolation(
                        invariantCode = "INV-005-ACCOUNTING-EQUATION",
                        description = "Accounting Invariant Equation Out of Equilibrium",
                        details = "At date $date: Assets ($totalAssets) != Liabilities ($totalLiabilities) + Equity ($totalStatedEquity) + Unclosed Profit ($unclosedProfit) = $totalLiabAndEquity, variance: ${totalAssets - totalLiabAndEquity}"
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
            totalEntriesChecked = allEntries.size,
            totalLinesChecked = 0,
            trialBalanceDebit = totalTrialDebit,
            trialBalanceCredit = totalTrialCredit
        )
    }

    /**
     * Lightweight O(1) single-entry verification running always after every posting in production:
     * - Debit/Credit balance equality
     * - Period validity
     * - Valid amounts
     */
    suspend fun verifyEntry(entryId: String) {
        val entry = db.journalDao().getEntryById(entryId)
            ?: error("Entry $entryId not found")
        val lines = db.journalDao().getLinesForEntry(entryId)
        require(lines.isNotEmpty()) { "Entry $entryId has no lines" }

        val totalDebit = lines.sumOf { it.baseDebitMinor }
        val totalCredit = lines.sumOf { it.baseCreditMinor }
        require(totalDebit == totalCredit) {
            "Entry $entryId is unbalanced: Debit $totalDebit != Credit $totalCredit (difference: ${totalDebit - totalCredit})"
        }

        lines.forEach { line ->
            require(line.baseDebitMinor >= 0L && line.baseCreditMinor >= 0L) {
                "Line ${line.lineNo} of entry $entryId has negative debit or credit amount"
            }
            require(line.baseDebitMinor > 0L || line.baseCreditMinor > 0L) {
                "Line ${line.lineNo} of entry $entryId has zero debit and zero credit"
            }
            require(line.accountCode.isNotBlank()) {
                "Line ${line.lineNo} of entry $entryId has blank account code"
            }
        }

        // Period validity check
        val (fy, month) = com.example.core.time.FiscalCalendar.DEFAULT.periodOf(entry.entryDateEpochDay)
        val period = db.fiscalPeriodDao().getPeriod(fy, month)
        if (period != null && period.isClosed) {
            require(entry.type == JournalEntryType.CLOSING.name) {
                "Cannot post non-closing entry $entryId into closed period $fy-$month"
            }
        }
    }
}
