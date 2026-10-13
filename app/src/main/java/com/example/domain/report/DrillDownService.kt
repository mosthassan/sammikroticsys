package com.example.domain.report

import com.example.data.local.AppDatabase
import com.example.data.local.dao.DrillDownLineRow
import com.example.data.local.entity.DocumentEntity
import com.example.data.local.entity.DocumentItemEntity

data class DrillDownDocumentDetail(
    val document: DocumentEntity?,
    val items: List<DocumentItemEntity>
)

class DrillDownService(
    private val db: AppDatabase
) {
    suspend fun fetchDrillDownLines(target: DrillTarget): List<DrillDownLineRow> {
        return when (target) {
            is DrillTarget.AccountLines -> {
                db.journalDao().getDrillDownLinesForAccount(
                    accountCode = target.accountCode,
                    fromEpochDay = target.fromEpochDay,
                    toEpochDay = target.toEpochDay,
                    partyId = target.partyId
                )
            }
            is DrillTarget.EntryLines -> {
                val lines = db.journalDao().getLinesForEntry(target.entryId)
                val entry = db.journalDao().getEntryById(target.entryId)
                val doc = entry?.let { db.documentDao().getDocumentById(it.docId) }
                val result = mutableListOf<DrillDownLineRow>()
                for (l in lines) {
                    val partyName = if (l.partyId != null) db.partyDao().getPartyById(l.partyId)?.name ?: "" else ""
                    result.add(
                        DrillDownLineRow(
                            lineId = l.id,
                            entryId = l.entryId,
                            entryNumber = entry?.entryNumber ?: 0L,
                            dateEpochDay = entry?.entryDateEpochDay ?: 0L,
                            entryType = entry?.type ?: "NORMAL",
                            docId = entry?.docId,
                            docNumber = doc?.docNumber,
                            docType = doc?.type,
                            accountCode = l.accountCode,
                            partyId = l.partyId,
                            partyName = partyName,
                            origAmountMinor = l.origMinor,
                            currency = l.currency,
                            baseDebitMinor = l.baseDebitMinor,
                            baseCreditMinor = l.baseCreditMinor,
                            memo = l.memo.ifBlank { entry?.memo ?: "" }
                        )
                    )
                }
                result
            }
            is DrillTarget.Document -> {
                val entries = db.journalDao().getEntriesForDocument(target.documentId)
                val doc = db.documentDao().getDocumentById(target.documentId)
                val result = mutableListOf<DrillDownLineRow>()
                for (entry in entries) {
                    val lines = db.journalDao().getLinesForEntry(entry.id)
                    for (l in lines) {
                        val partyName = if (l.partyId != null) db.partyDao().getPartyById(l.partyId)?.name ?: "" else ""
                        result.add(
                            DrillDownLineRow(
                                lineId = l.id,
                                entryId = l.entryId,
                                entryNumber = entry.entryNumber,
                                dateEpochDay = entry.entryDateEpochDay,
                                entryType = entry.type,
                                docId = doc?.id,
                                docNumber = doc?.docNumber,
                                docType = doc?.type,
                                accountCode = l.accountCode,
                                partyId = l.partyId,
                                partyName = partyName,
                                origAmountMinor = l.origMinor,
                                currency = l.currency,
                                baseDebitMinor = l.baseDebitMinor,
                                baseCreditMinor = l.baseCreditMinor,
                                memo = l.memo.ifBlank { entry.memo }
                            )
                        )
                    }
                }
                result
            }
        }
    }

    suspend fun fetchDocumentDetails(docId: String): DrillDownDocumentDetail {
        val doc = db.documentDao().getDocumentById(docId)
        val items = db.documentDao().getItemsForDocument(docId)
        return DrillDownDocumentDetail(doc, items)
    }
}
