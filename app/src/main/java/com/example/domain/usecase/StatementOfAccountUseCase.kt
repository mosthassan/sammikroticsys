package com.example.domain.usecase

import com.example.core.model.CurrencyCode
import com.example.core.model.Money
import com.example.data.local.AppDatabase
import com.example.data.local.dao.StatementLineRow

data class StatementItem(
    val entryId: String,
    val docId: String,
    val entryDateEpochDay: Long,
    val docType: String,
    val docNumber: Long,
    val memo: String,
    val baseDebitMinor: Long,
    val baseCreditMinor: Long,
    val origMinor: Long,
    val currency: String,
    val runningBalanceMinor: Long
)

data class StatementOfAccountReport(
    val partyId: String,
    val partyName: String,
    val controlAccountCode: String,
    val startDateEpochDay: Long?,
    val endDateEpochDay: Long?,
    val openingBalanceMinor: Long,
    val totalDebitMinor: Long,
    val totalCreditMinor: Long,
    val closingBalanceMinor: Long,
    val items: List<StatementItem>
)

class StatementOfAccountUseCase(private val db: AppDatabase) {

    suspend fun generateStatement(
        partyId: String,
        controlAccountCode: String,
        startDateEpochDay: Long? = null,
        endDateEpochDay: Long? = null
    ): StatementOfAccountReport {
        val party = db.partyDao().getPartyById(partyId) ?: error("Party $partyId not found")

        // 1. Calculate opening balance before startDateEpochDay
        val openingLines = if (startDateEpochDay != null) {
            db.journalDao().getStatementOfAccountLines(
                partyId = partyId,
                controlAccountCode = controlAccountCode,
                startDateEpochDay = null,
                endDateEpochDay = startDateEpochDay - 1
            )
        } else {
            emptyList()
        }
        val openingBalance = openingLines.sumOf { it.baseDebitMinor - it.baseCreditMinor }

        // 2. Fetch statement lines within period
        val periodLines = db.journalDao().getStatementOfAccountLines(
            partyId = partyId,
            controlAccountCode = controlAccountCode,
            startDateEpochDay = startDateEpochDay,
            endDateEpochDay = endDateEpochDay
        )

        var running = openingBalance
        var totalDebit = 0L
        var totalCredit = 0L

        val statementItems = periodLines.map { line ->
            running += (line.baseDebitMinor - line.baseCreditMinor)
            totalDebit += line.baseDebitMinor
            totalCredit += line.baseCreditMinor

            StatementItem(
                entryId = line.entryId,
                docId = line.docId,
                entryDateEpochDay = line.entryDateEpochDay,
                docType = line.docType,
                docNumber = line.docNumber,
                memo = line.memo,
                baseDebitMinor = line.baseDebitMinor,
                baseCreditMinor = line.baseCreditMinor,
                origMinor = line.origMinor,
                currency = line.currency,
                runningBalanceMinor = running
            )
        }

        return StatementOfAccountReport(
            partyId = partyId,
            partyName = party.name,
            controlAccountCode = controlAccountCode,
            startDateEpochDay = startDateEpochDay,
            endDateEpochDay = endDateEpochDay,
            openingBalanceMinor = openingBalance,
            totalDebitMinor = totalDebit,
            totalCreditMinor = totalCredit,
            closingBalanceMinor = running,
            items = statementItems
        )
    }
}
