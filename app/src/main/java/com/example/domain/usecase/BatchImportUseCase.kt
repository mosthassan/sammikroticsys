package com.example.domain.usecase

import com.example.core.model.CurrencyCode
import com.example.core.model.ExchangeRate
import com.example.data.ledger.LedgerWriter
import com.example.data.ledger.SalesItemSpec
import com.example.data.local.AppDatabase
import com.example.data.local.entity.PartyEntity
import org.json.JSONArray
import org.json.JSONObject

data class ImportItemResult(
    val rowIndex: Int,
    val type: String,
    val sourceRef: String,
    val status: ImportRowStatus,
    val message: String
)

enum class ImportRowStatus {
    SUCCESS,
    DUPLICATE_SKIPPED,
    ERROR
}

data class ImportBatchReport(
    val totalRows: Int,
    val successCount: Int,
    val duplicateSkippedCount: Int,
    val errorCount: Int,
    val rowResults: List<ImportItemResult>
)

class BatchImportUseCase(
    private val db: AppDatabase,
    private val writer: LedgerWriter
) {

    suspend fun importInvoicesBatch(jsonString: String): ImportBatchReport {
        val root = JSONObject(jsonString)
        val schemaVer = root.optInt("schemaVersion", -1)
        require(schemaVer == 1) { "Invalid import schema version: $schemaVer" }

        val rowsArr = root.optJSONArray("invoices") ?: JSONArray()
        val results = mutableListOf<ImportItemResult>()
        var success = 0
        var duplicates = 0
        var errors = 0

        for (i in 0 until rowsArr.length()) {
            val row = rowsArr.getJSONObject(i)
            val sourceRef = row.optString("sourceRef", "ROW_${i + 1}")
            val partyId = row.optString("partyId", AppDatabase.WALK_IN_CASH_PARTY_ID)
            val dateEpochDay = row.optLong("dateEpochDay", System.currentTimeMillis() / 86400000L)
            val fiscalYear = row.optInt("fiscalYear", 2026)
            val currencyStr = row.optString("currency", "YER")
            val currency = CurrencyCode.fromString(currencyStr)
            val rateMicros = row.optLong("exchangeRateMicros", ExchangeRate.SCALE_MICROS)
            val exchangeRate = ExchangeRate(currency, CurrencyCode.FUNCTIONAL, rateMicros)

            val naturalDeduplicationKey = "IMPORT_INV_${sourceRef}_${partyId}_${dateEpochDay}"

            // Check natural deduplication
            val existingDoc = db.idempotencyDao().getDocIdForKey(naturalDeduplicationKey)
            if (existingDoc != null) {
                duplicates++
                results.add(
                    ImportItemResult(
                        rowIndex = i + 1,
                        type = "SALES_INVOICE",
                        sourceRef = sourceRef,
                        status = ImportRowStatus.DUPLICATE_SKIPPED,
                        message = "تخطي: الفاتورة مستوردة مسبقاً برقم مرجعي $sourceRef"
                    )
                )
                continue
            }

            try {
                val itemsArr = row.optJSONArray("items") ?: JSONArray()
                val salesItems = mutableListOf<SalesItemSpec>()
                for (j in 0 until itemsArr.length()) {
                    val itm = itemsArr.getJSONObject(j)
                    salesItems.add(
                        SalesItemSpec(
                            description = itm.getString("description"),
                            quantity = itm.getInt("quantity"),
                            unitPriceMinor = itm.getLong("unitPriceMinor"),
                            packageId = if (itm.isNull("packageId")) null else itm.getString("packageId")
                        )
                    )
                }

                if (salesItems.isEmpty()) {
                    throw IllegalArgumentException("الفاتورة لا تحتوي على أي بنود")
                }

                // Post row in its own individual transaction through LedgerWriter
                val doc = writer.postSalesInvoice(
                    partyId = partyId,
                    fiscalYear = fiscalYear,
                    dateEpochDay = dateEpochDay,
                    currency = currency,
                    exchangeRate = exchangeRate,
                    cardItems = salesItems,
                    serviceItems = emptyList(),
                    notes = "استيراد دفعة: $sourceRef",
                    idempotencyKey = naturalDeduplicationKey
                )

                success++
                results.add(
                    ImportItemResult(
                        rowIndex = i + 1,
                        type = "SALES_INVOICE",
                        sourceRef = sourceRef,
                        status = ImportRowStatus.SUCCESS,
                        message = "تم الترحيل بنجاح برقم فاتورة #${doc.docNumber}"
                    )
                )
            } catch (e: Exception) {
                errors++
                results.add(
                    ImportItemResult(
                        rowIndex = i + 1,
                        type = "SALES_INVOICE",
                        sourceRef = sourceRef,
                        status = ImportRowStatus.ERROR,
                        message = "فشل الترحيل: ${e.message}"
                    )
                )
            }
        }

        return ImportBatchReport(
            totalRows = rowsArr.length(),
            successCount = success,
            duplicateSkippedCount = duplicates,
            errorCount = errors,
            rowResults = results
        )
    }
}
