package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "organizations")
data class OrganizationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val taxNumber: String = "",
    val functionalCurrency: String = "YER",
    val fiscalYearStartMonth: Int = 1,
    val isInitialized: Boolean = false,
    val primaryRateZone: String = "SANAA",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val code: String,
    val name: String,
    val type: String, // ASSET, LIABILITY, EQUITY, REVENUE, EXPENSE
    val isDebitNormal: Boolean,
    val isLocked: Boolean = true,
    val isActive: Boolean = true
)

@Entity(
    tableName = "parties",
    indices = [Index(value = ["name"]), Index(value = ["isCustomer"]), Index(value = ["isVendor"]), Index(value = ["isPartner"])]
)
data class PartyEntity(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String = "",
    val isCustomer: Boolean = false,
    val isVendor: Boolean = false,
    val isPartner: Boolean = false,
    val equityPercentageBasisPoints: Int = 0, // e.g. 5000 = 50.00%
    val creditLimitMinor: Long = 0L,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "treasury_accounts")
data class TreasuryAccountEntity(
    @PrimaryKey val id: String,
    val name: String,
    val glAccountCode: String, // 1101 or 1102
    val currency: String, // YER, USD, SAR
    val isActive: Boolean = true
)

@Entity(
    tableName = "fiscal_periods",
    indices = [Index(value = ["year", "month"], unique = true)]
)
data class FiscalPeriodEntity(
    @PrimaryKey val id: String,
    val year: Int,
    val month: Int,
    val isClosed: Boolean = false,
    val closedAt: Long? = null
)

@Entity(
    tableName = "currency_rates",
    indices = [
        Index(value = ["currency", "zone", "effectiveDateEpochDay"], unique = true),
        Index(value = ["currency", "zone", "effectiveDateEpochDay", "createdAt"])
    ]
)
data class CurrencyRateEntity(
    @PrimaryKey val id: String,
    val currency: String,
    val zone: String = "SANAA", // SANAA | ADEN
    val rateMicros: Long,
    val effectiveDateEpochDay: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val createdBy: String = "SYSTEM",
    val reason: String = ""
)

@Entity(
    tableName = "documents",
    indices = [
        Index(value = ["type", "fiscalYear", "docNumber"], unique = true),
        Index(value = ["partyId"]),
        Index(value = ["status"]),
        Index(value = ["dateEpochDay"])
    ]
)
data class DocumentEntity(
    @PrimaryKey val id: String, // UUIDv7
    val type: String, // SalesInvoice, ReceiptVoucher, etc.
    val fiscalYear: Int,
    val docNumber: Long,
    val partyId: String,
    val dateEpochDay: Long,
    val currency: String,
    val exchangeRateMicros: Long,
    val rateZone: String = "SANAA",
    val rateSource: String = "SYSTEM_DAILY",
    val totalMinor: Long,
    val totalBaseMinor: Long,
    val status: String, // DRAFT, POSTED, VOIDED
    val notes: String = "",
    val reversalOfDocId: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "document_items",
    indices = [Index(value = ["docId"])]
)
data class DocumentItemEntity(
    @PrimaryKey val id: String,
    val docId: String,
    val itemIndex: Int,
    val packageId: String? = null,
    val description: String,
    val accountCode: String,
    val quantity: Int,
    val unitPriceMinor: Long,
    val totalMinor: Long,
    val isAsset: Boolean = false
)

@Entity(
    tableName = "journal_entries",
    indices = [
        Index(value = ["docId"]),
        Index(value = ["entryDateEpochDay"]),
        Index(value = ["type"])
    ]
)
data class JournalEntryEntity(
    @PrimaryKey val id: String, // UUIDv7
    val docId: String,
    val entryNumber: Long,
    val entryDateEpochDay: Long,
    val type: String, // NORMAL, REVERSAL, CLOSING
    val memo: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "journal_lines",
    indices = [
        Index(value = ["entryId"]),
        Index(value = ["accountCode"]),
        Index(value = ["partyId"]),
        Index(value = ["treasuryId"])
    ]
)
data class JournalLineEntity(
    @PrimaryKey val id: String, // UUIDv7
    val entryId: String,
    val lineNo: Int,
    val accountCode: String,
    val partyId: String? = null,
    val treasuryId: String? = null,
    val origMinor: Long,
    val currency: String,
    val exchangeRateMicros: Long,
    val baseDebitMinor: Long,
    val baseCreditMinor: Long,
    val memo: String
)

@Entity(
    tableName = "allocations",
    indices = [
        Index(value = ["paymentDocId"]),
        Index(value = ["invoiceDocId"])
    ]
)
data class AllocationEntity(
    @PrimaryKey val id: String,
    val paymentDocId: String,
    val invoiceDocId: String,
    val allocatedOrigMinor: Long,
    val allocatedBaseMinor: Long,
    val exchangeGainLossMinor: Long = 0L,
    val isVoided: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "assets",
    indices = [Index(value = ["docId"])]
)
data class AssetEntity(
    @PrimaryKey val id: String,
    val docId: String,
    val name: String,
    val purchaseDateEpochDay: Long,
    val purchaseCostMinor: Long,
    val salvageValueMinor: Long = 0L,
    val usefulLifeMonths: Int,
    val accumulatedDepreciationMinor: Long = 0L,
    val isDisposed: Boolean = false
)

@Entity(
    tableName = "depreciation_runs",
    indices = [Index(value = ["periodYear", "periodMonth", "assetId"], unique = true)]
)
data class DepreciationRunEntity(
    @PrimaryKey val id: String,
    val periodYear: Int,
    val periodMonth: Int,
    val assetId: String,
    val journalEntryId: String,
    val depreciationAmountMinor: Long
)

@Entity(tableName = "card_packages")
data class CardPackageEntity(
    @PrimaryKey val id: String,
    val name: String,
    val durationOrQuota: String,
    val wholesalePriceMinor: Long,
    val retailPriceMinor: Long,
    val isActive: Boolean = true
)

@Entity(
    tableName = "stock_movements",
    indices = [Index(value = ["packageId"]), Index(value = ["docId"])]
)
data class StockMovementEntity(
    @PrimaryKey val id: String,
    val packageId: String,
    val docId: String?,
    val type: String, // RECEIVE, SELL, RETURN, ADJUST
    val quantity: Int,
    val movementDateEpochDay: Long,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "number_sequences",
    primaryKeys = ["docType", "fiscalYear"]
)
data class NumberSequenceEntity(
    val docType: String,
    val fiscalYear: Int,
    val nextValue: Long
)

@Entity(tableName = "audit_log")
data class AuditLogEntity(
    @PrimaryKey val id: String,
    val entityType: String,
    val entityId: String,
    val action: String,
    val beforeJson: String? = null,
    val afterJson: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "idempotency_keys")
data class IdempotencyKeyEntity(
    @PrimaryKey val key: String,
    val docId: String,
    val createdAt: Long = System.currentTimeMillis()
)
