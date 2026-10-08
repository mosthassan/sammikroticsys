package com.example.data.ledger

import androidx.room.withTransaction
import com.example.core.ledger.AccountConstants
import com.example.core.ledger.DocumentStatus
import com.example.core.ledger.DocumentType
import com.example.core.ledger.JournalDraft
import com.example.core.ledger.JournalDraftLine
import com.example.core.ledger.JournalEntryType
import com.example.core.ledger.PostingRules
import com.example.core.ledger.PurchaseItemDraft
import com.example.core.model.CurrencyCode
import com.example.core.model.ExchangeRate
import com.example.core.model.InsufficientTreasuryFundsException
import com.example.core.model.MissingExchangeRateException
import com.example.core.model.RateSource
import com.example.core.model.RateZone
import com.example.core.model.UuidUtils
import com.example.data.local.AppDatabase
import com.example.data.local.entity.AllocationEntity
import com.example.data.local.entity.AssetEntity
import com.example.data.local.entity.AuditLogEntity
import com.example.data.local.entity.DepreciationRunEntity
import com.example.data.local.entity.DocumentEntity
import com.example.data.local.entity.DocumentItemEntity
import com.example.data.local.entity.FiscalPeriodEntity
import com.example.data.local.entity.IdempotencyKeyEntity
import com.example.data.local.entity.JournalEntryEntity
import com.example.data.local.entity.JournalLineEntity
import com.example.data.local.entity.NumberSequenceEntity
import com.example.data.local.entity.StockMovementEntity

class LedgerWriter(
    private val db: AppDatabase,
    private val enableInvariantValidation: Boolean = true
) {
    private val invariants = LedgerInvariants(db)

    /**
     * Enforces the Zero-Overdraft Invariant (B.1):
     * Calculates the treasury's net balance in its original currency prior to any disbursement.
     * If the resulting balance would be < 0 and allowNegative == false, throws InsufficientTreasuryFundsException.
     */
    private suspend fun validateTreasuryDisbursement(treasuryId: String, requiredMinor: Long) {
        val treasury = db.treasuryDao().getTreasuryById(treasuryId)
            ?: error("Treasury account $treasuryId not found")
        if (!treasury.allowNegative) {
            val availableMinor = db.journalDao().getNetOrigBalanceForTreasury(treasuryId)
            if (availableMinor < requiredMinor) {
                throw InsufficientTreasuryFundsException(treasuryId, availableMinor, requiredMinor)
            }
        }
    }

    /**
     * Posts a Sales Invoice (Credit or Cash):
     * DR 1201 (Receivables), CR 4101 (Card sales), CR 4201 (Service sales).
     */
    suspend fun postSalesInvoice(
        partyId: String,
        fiscalYear: Int,
        dateEpochDay: Long,
        currency: CurrencyCode,
        exchangeRate: ExchangeRate,
        cardItems: List<SalesItemSpec>,
        serviceItems: List<SalesItemSpec>,
        notes: String = "",
        rateZone: RateZone = RateZone.DEFAULT,
        rateSource: RateSource = RateSource.SYSTEM_DAILY,
        idempotencyKey: String? = null
    ): DocumentEntity = db.withTransaction {
        idempotencyKey?.let { key ->
            val existingDocId = db.idempotencyDao().getDocIdForKey(key)
            if (existingDocId != null) {
                return@withTransaction db.documentDao().getDocumentById(existingDocId)!!
            }
        }

        validatePeriodIsOpen(dateEpochDay)
        validateExchangeRateGuardrail(currency, exchangeRate)

        val cardTotalOrig = cardItems.sumOf { it.quantity * it.unitPriceMinor }
        val serviceTotalOrig = serviceItems.sumOf { it.quantity * it.unitPriceMinor }
        val totalOrig = cardTotalOrig + serviceTotalOrig
        val totalBase = exchangeRate.convert(totalOrig)

        val docNumber = allocateNextDocNumber(DocumentType.SALES_INVOICE.name, fiscalYear)
        val docId = UuidUtils.newTimeOrderedId()

        val docEntity = DocumentEntity(
            id = docId,
            type = DocumentType.SALES_INVOICE.name,
            fiscalYear = fiscalYear,
            docNumber = docNumber,
            partyId = partyId,
            dateEpochDay = dateEpochDay,
            currency = currency.name,
            exchangeRateMicros = exchangeRate.rateMicros,
            rateZone = rateZone.name,
            rateSource = rateSource.name,
            totalMinor = totalOrig,
            totalBaseMinor = totalBase,
            status = DocumentStatus.POSTED.name,
            notes = notes
        )
        db.documentDao().insertDocument(docEntity)

        // Insert items
        val allItems = mutableListOf<DocumentItemEntity>()
        var itemIdx = 0
        cardItems.forEach { item ->
            allItems.add(
                DocumentItemEntity(
                    id = UuidUtils.newTimeOrderedId(),
                    docId = docId,
                    itemIndex = itemIdx++,
                    packageId = item.packageId,
                    description = item.description,
                    accountCode = AccountConstants.CARD_SALES_REVENUE,
                    quantity = item.quantity,
                    unitPriceMinor = item.unitPriceMinor,
                    totalMinor = item.quantity * item.unitPriceMinor,
                    isAsset = false
                )
            )
            // Stock movement (SELL)
            item.packageId?.let { pkgId ->
                db.cardPackageDao().insertStockMovement(
                    StockMovementEntity(
                        id = UuidUtils.newTimeOrderedId(),
                        packageId = pkgId,
                        docId = docId,
                        type = "SELL",
                        quantity = item.quantity,
                        movementDateEpochDay = dateEpochDay
                    )
                )
            }
        }

        serviceItems.forEach { item ->
            allItems.add(
                DocumentItemEntity(
                    id = UuidUtils.newTimeOrderedId(),
                    docId = docId,
                    itemIndex = itemIdx++,
                    packageId = null,
                    description = item.description,
                    accountCode = AccountConstants.DIRECT_SERVICE_REVENUE,
                    quantity = item.quantity,
                    unitPriceMinor = item.unitPriceMinor,
                    totalMinor = item.quantity * item.unitPriceMinor,
                    isAsset = false
                )
            )
        }
        db.documentDao().insertItems(allItems)

        // Generate and post journal entry
        val draft = PostingRules.createSalesInvoiceDraft(
            partyId = partyId,
            cardTotalOrigMinor = cardTotalOrig,
            serviceTotalOrigMinor = serviceTotalOrig,
            currency = currency,
            exchangeRate = exchangeRate,
            dateEpochDay = dateEpochDay,
            memo = "فاتورة مبيعات #$docNumber"
        )
        persistJournalDraft(docId, docNumber, draft)

        recordAuditLog("DOCUMENT", docId, "POST_SALES_INVOICE", null, "Posted Sales Invoice #$docNumber")
        idempotencyKey?.let { db.idempotencyDao().insertKey(IdempotencyKeyEntity(it, docId)) }

        if (enableInvariantValidation) invariants.verifyAll()
        docEntity
    }

    /**
     * Posts a Customer Receipt Voucher (سند قبض):
     * DR Treasury, CR 1201 (Receivables).
     * Optional allocation against open sales invoices with automatic IAS 21 realized FX gain/loss.
     */
    suspend fun postCustomerReceipt(
        partyId: String,
        treasuryId: String,
        fiscalYear: Int,
        dateEpochDay: Long,
        amountOrigMinor: Long,
        currency: CurrencyCode,
        exchangeRate: ExchangeRate,
        allocations: List<InvoiceAllocationSpec> = emptyList(),
        notes: String = "",
        rateZone: RateZone = RateZone.DEFAULT,
        rateSource: RateSource = RateSource.SYSTEM_DAILY,
        idempotencyKey: String? = null
    ): DocumentEntity = db.withTransaction {
        idempotencyKey?.let { key ->
            val existingDocId = db.idempotencyDao().getDocIdForKey(key)
            if (existingDocId != null) {
                return@withTransaction db.documentDao().getDocumentById(existingDocId)!!
            }
        }

        validatePeriodIsOpen(dateEpochDay)
        validateExchangeRateGuardrail(currency, exchangeRate)
        val treasury = db.treasuryDao().getTreasuryById(treasuryId)
            ?: error("Treasury account $treasuryId not found")

        // Validate allocations
        if (allocations.isNotEmpty()) {
            val totalAllocated = allocations.sumOf { it.allocatedOrigMinor }
            require(totalAllocated <= amountOrigMinor) {
                "Total allocated amount ($totalAllocated) exceeds receipt amount ($amountOrigMinor)"
            }
            allocations.forEach { alloc ->
                val invDoc = db.documentDao().getDocumentById(alloc.invoiceDocId)
                    ?: error("Allocated invoice ${alloc.invoiceDocId} not found")
                require(invDoc.status == DocumentStatus.POSTED.name) {
                    "Cannot allocate to unposted invoice ${invDoc.docNumber}"
                }
                require(invDoc.partyId == partyId) {
                    "Allocated invoice party (${invDoc.partyId}) does not match receipt party ($partyId)"
                }
                val activeAllocs = db.allocationDao().getActiveAllocationsForInvoice(alloc.invoiceDocId)
                val previouslyAllocated = activeAllocs.sumOf { it.allocatedOrigMinor }
                val remainingInvoiceBalance = invDoc.totalMinor - previouslyAllocated
                require(alloc.allocatedOrigMinor <= remainingInvoiceBalance) {
                    "Allocated amount (${alloc.allocatedOrigMinor}) exceeds remaining invoice balance ($remainingInvoiceBalance)"
                }
            }
        }

        val docNumber = allocateNextDocNumber(DocumentType.RECEIPT_VOUCHER.name, fiscalYear)
        val docId = UuidUtils.newTimeOrderedId()
        val totalBase = exchangeRate.convert(amountOrigMinor)

        val docEntity = DocumentEntity(
            id = docId,
            type = DocumentType.RECEIPT_VOUCHER.name,
            fiscalYear = fiscalYear,
            docNumber = docNumber,
            partyId = partyId,
            dateEpochDay = dateEpochDay,
            currency = currency.name,
            exchangeRateMicros = exchangeRate.rateMicros,
            rateZone = rateZone.name,
            rateSource = rateSource.name,
            totalMinor = amountOrigMinor,
            totalBaseMinor = totalBase,
            status = DocumentStatus.POSTED.name,
            notes = notes
        )
        db.documentDao().insertDocument(docEntity)

        // Check if there are allocations with FX differences
        if (allocations.isNotEmpty()) {
            val firstAlloc = allocations.first()
            val invDoc = db.documentDao().getDocumentById(firstAlloc.invoiceDocId)
            val invCurrency = if (invDoc != null) CurrencyCode.fromString(invDoc.currency) else currency
            val invRate = if (invDoc != null) ExchangeRate(invCurrency, CurrencyCode.FUNCTIONAL, invDoc.exchangeRateMicros) else exchangeRate
            val totalRelievedOrig = allocations.sumOf { it.allocatedOrigMinor }
            val cashReceivedBase = exchangeRate.convert(amountOrigMinor)
            val receivableRelievedBase = invRate.convert(totalRelievedOrig)

            if (invDoc != null && (cashReceivedBase != receivableRelievedBase || invCurrency != currency)) {
                val draft = PostingRules.createCustomerReceiptWithFxDraft(
                    treasuryGlCode = treasury.glAccountCode,
                    treasuryId = treasuryId,
                    partyId = partyId,
                    amountOrigMinor = amountOrigMinor,
                    currency = currency,
                    settlementRate = exchangeRate,
                    invoiceRate = invRate,
                    dateEpochDay = dateEpochDay,
                    memo = "سند قبض عميل #$docNumber (مع تسوية فروق صرف)",
                    invoiceCurrency = invCurrency,
                    invoiceRelievedOrigMinor = totalRelievedOrig
                )
                persistJournalDraft(docId, docNumber, draft)
            } else {
                val draft = PostingRules.createCustomerReceiptDraft(
                    treasuryGlCode = treasury.glAccountCode,
                    treasuryId = treasuryId,
                    partyId = partyId,
                    amountOrigMinor = amountOrigMinor,
                    currency = currency,
                    exchangeRate = exchangeRate,
                    dateEpochDay = dateEpochDay,
                    memo = "سند قبض عميل #$docNumber"
                )
                persistJournalDraft(docId, docNumber, draft)
            }
        } else {
            val draft = PostingRules.createCustomerReceiptDraft(
                treasuryGlCode = treasury.glAccountCode,
                treasuryId = treasuryId,
                partyId = partyId,
                amountOrigMinor = amountOrigMinor,
                currency = currency,
                exchangeRate = exchangeRate,
                dateEpochDay = dateEpochDay,
                memo = "سند قبض عميل #$docNumber"
            )
            persistJournalDraft(docId, docNumber, draft)
        }

        // Record allocations
        allocations.forEach { alloc ->
            val invDoc = db.documentDao().getDocumentById(alloc.invoiceDocId)
            val invRate = if (invDoc != null) {
                ExchangeRate(CurrencyCode.fromString(invDoc.currency), CurrencyCode.FUNCTIONAL, invDoc.exchangeRateMicros)
            } else exchangeRate
            val allocBase = invRate.convert(alloc.allocatedOrigMinor)
            db.allocationDao().insertAllocation(
                AllocationEntity(
                    id = UuidUtils.newTimeOrderedId(),
                    paymentDocId = docId,
                    invoiceDocId = alloc.invoiceDocId,
                    allocatedOrigMinor = alloc.allocatedOrigMinor,
                    allocatedBaseMinor = allocBase
                )
            )
        }

        recordAuditLog("DOCUMENT", docId, "POST_RECEIPT_VOUCHER", null, "Posted Customer Receipt #$docNumber")
        idempotencyKey?.let { db.idempotencyDao().insertKey(IdempotencyKeyEntity(it, docId)) }

        if (enableInvariantValidation) invariants.verifyAll()
        docEntity
    }

    /**
     * Posts a Capital or Partner Contribution Receipt Voucher:
     * DR Treasury, CR 3101 (Capital) or 3201 (Partner Current).
     */
    suspend fun postCapitalReceipt(
        targetAccountCode: String,
        partnerPartyId: String?,
        treasuryId: String,
        fiscalYear: Int,
        dateEpochDay: Long,
        amountOrigMinor: Long,
        currency: CurrencyCode,
        exchangeRate: ExchangeRate,
        notes: String = "",
        rateZone: RateZone = RateZone.DEFAULT,
        rateSource: RateSource = RateSource.SYSTEM_DAILY,
        idempotencyKey: String? = null
    ): DocumentEntity = db.withTransaction {
        validatePeriodIsOpen(dateEpochDay)
        validateExchangeRateGuardrail(currency, exchangeRate)
        val treasury = db.treasuryDao().getTreasuryById(treasuryId)
            ?: error("Treasury account $treasuryId not found")
        require(treasury.currency == currency.name) {
            "Treasury currency (${treasury.currency}) must match contribution currency (${currency.name})"
        }

        val docNumber = allocateNextDocNumber(DocumentType.RECEIPT_VOUCHER.name, fiscalYear)
        val docId = UuidUtils.newTimeOrderedId()
        val totalBase = exchangeRate.convert(amountOrigMinor)

        val docEntity = DocumentEntity(
            id = docId,
            type = DocumentType.RECEIPT_VOUCHER.name,
            fiscalYear = fiscalYear,
            docNumber = docNumber,
            partyId = partnerPartyId ?: AppDatabase.WALK_IN_CASH_PARTY_ID,
            dateEpochDay = dateEpochDay,
            currency = currency.name,
            exchangeRateMicros = exchangeRate.rateMicros,
            rateZone = rateZone.name,
            rateSource = rateSource.name,
            totalMinor = amountOrigMinor,
            totalBaseMinor = totalBase,
            status = DocumentStatus.POSTED.name,
            notes = notes
        )
        db.documentDao().insertDocument(docEntity)

        val draft = PostingRules.createCapitalContributionDraft(
            treasuryGlCode = treasury.glAccountCode,
            treasuryId = treasuryId,
            targetAccountCode = targetAccountCode,
            partyId = partnerPartyId,
            amountOrigMinor = amountOrigMinor,
            currency = currency,
            exchangeRate = exchangeRate,
            dateEpochDay = dateEpochDay,
            memo = "سند قبض مساهمة رأسمالية #$docNumber"
        )
        persistJournalDraft(docId, docNumber, draft)

        recordAuditLog("DOCUMENT", docId, "POST_CAPITAL_RECEIPT", null, "Posted Capital Receipt #$docNumber")
        idempotencyKey?.let { db.idempotencyDao().insertKey(IdempotencyKeyEntity(it, docId)) }

        if (enableInvariantValidation) invariants.verifyAll()
        docEntity
    }

    /**
     * Posts an In-Kind (Fixed Asset) Capital Contribution from a Partner:
     * DR 1501 (Fixed Assets)
     * CR 3101 (Capital - Partner Equity)
     * Also registers the asset in the Asset Register.
     */
    suspend fun postInKindCapitalContribution(
        partnerPartyId: String,
        assetName: String,
        fiscalYear: Int,
        dateEpochDay: Long,
        amountOrigMinor: Long,
        currency: CurrencyCode,
        exchangeRate: ExchangeRate,
        usefulLifeMonths: Int = 36,
        notes: String = "",
        rateZone: RateZone = RateZone.DEFAULT,
        rateSource: RateSource = RateSource.SYSTEM_DAILY,
        idempotencyKey: String? = null
    ): DocumentEntity = db.withTransaction {
        validatePeriodIsOpen(dateEpochDay)
        validateExchangeRateGuardrail(currency, exchangeRate)
        val docNumber = allocateNextDocNumber(DocumentType.RECEIPT_VOUCHER.name, fiscalYear)
        val docId = UuidUtils.newTimeOrderedId()
        val totalBase = exchangeRate.convert(amountOrigMinor)

        val docEntity = DocumentEntity(
            id = docId,
            type = DocumentType.RECEIPT_VOUCHER.name,
            fiscalYear = fiscalYear,
            docNumber = docNumber,
            partyId = partnerPartyId,
            dateEpochDay = dateEpochDay,
            currency = currency.name,
            exchangeRateMicros = exchangeRate.rateMicros,
            rateZone = rateZone.name,
            rateSource = rateSource.name,
            totalMinor = amountOrigMinor,
            totalBaseMinor = totalBase,
            status = DocumentStatus.POSTED.name,
            notes = if (notes.isBlank()) "مساهمة عينية رأسمالية (أصل ثابت: $assetName)" else notes
        )
        db.documentDao().insertDocument(docEntity)

        val draft = PostingRules.createInKindCapitalContributionDraft(
            assetGlCode = AccountConstants.FIXED_ASSETS_NETWORK,
            targetAccountCode = AccountConstants.CAPITAL,
            partnerPartyId = partnerPartyId,
            amountOrigMinor = amountOrigMinor,
            currency = currency,
            exchangeRate = exchangeRate,
            dateEpochDay = dateEpochDay,
            memo = "مساهمة عينية رأسمالية (أصل ثابت: $assetName) #$docNumber"
        )
        persistJournalDraft(docId, docNumber, draft)

        // Register asset in Asset Register
        val assetId = UuidUtils.newTimeOrderedId()
        val assetEntity = AssetEntity(
            id = assetId,
            docId = docId,
            name = assetName,
            purchaseDateEpochDay = dateEpochDay,
            purchaseCostMinor = totalBase,
            salvageValueMinor = 0L,
            usefulLifeMonths = usefulLifeMonths,
            accumulatedDepreciationMinor = 0L,
            isDisposed = false
        )
        db.assetDao().insertAsset(assetEntity)

        recordAuditLog("DOCUMENT", docId, "POST_IN_KIND_CAPITAL", null, "Posted In-Kind Capital Contribution #$docNumber: $assetName")
        idempotencyKey?.let { db.idempotencyDao().insertKey(IdempotencyKeyEntity(it, docId)) }

        if (enableInvariantValidation) invariants.verifyAll()
        docEntity
    }

    /**
     * Posts a Purchase Invoice:
     * DR 1501 (Network Equipment) or 5xxx (Direct ISP or Operating Expenses), CR 2101 (Payables).
     * If asset items are included, creates records in the asset register.
     */
    suspend fun postPurchaseInvoice(
        vendorPartyId: String,
        fiscalYear: Int,
        dateEpochDay: Long,
        currency: CurrencyCode,
        exchangeRate: ExchangeRate,
        items: List<PurchaseItemSpec>,
        notes: String = "",
        rateZone: RateZone = RateZone.DEFAULT,
        rateSource: RateSource = RateSource.SYSTEM_DAILY,
        idempotencyKey: String? = null
    ): DocumentEntity = db.withTransaction {
        validatePeriodIsOpen(dateEpochDay)
        validateExchangeRateGuardrail(currency, exchangeRate)

        val totalOrig = items.sumOf { it.totalMinor }
        val totalBase = exchangeRate.convert(totalOrig)
        val docNumber = allocateNextDocNumber(DocumentType.PURCHASE_INVOICE.name, fiscalYear)
        val docId = UuidUtils.newTimeOrderedId()

        val docEntity = DocumentEntity(
            id = docId,
            type = DocumentType.PURCHASE_INVOICE.name,
            fiscalYear = fiscalYear,
            docNumber = docNumber,
            partyId = vendorPartyId,
            dateEpochDay = dateEpochDay,
            currency = currency.name,
            exchangeRateMicros = exchangeRate.rateMicros,
            rateZone = rateZone.name,
            rateSource = rateSource.name,
            totalMinor = totalOrig,
            totalBaseMinor = totalBase,
            status = DocumentStatus.POSTED.name,
            notes = notes
        )
        db.documentDao().insertDocument(docEntity)

        // Items and asset registration
        val itemEntities = items.mapIndexed { idx, itm ->
            DocumentItemEntity(
                id = UuidUtils.newTimeOrderedId(),
                docId = docId,
                itemIndex = idx,
                description = itm.description,
                accountCode = itm.accountCode,
                quantity = itm.quantity,
                unitPriceMinor = itm.unitPriceMinor,
                totalMinor = itm.totalMinor,
                isAsset = itm.isAsset
            )
        }
        db.documentDao().insertItems(itemEntities)

        items.filter { it.isAsset }.forEach { assetItem ->
            val assetBaseCost = exchangeRate.convert(assetItem.totalMinor)
            db.assetDao().insertAsset(
                AssetEntity(
                    id = UuidUtils.newTimeOrderedId(),
                    docId = docId,
                    name = assetItem.description,
                    purchaseDateEpochDay = dateEpochDay,
                    purchaseCostMinor = assetBaseCost,
                    salvageValueMinor = 0L,
                    usefulLifeMonths = assetItem.usefulLifeMonths ?: 36
                )
            )
        }

        val draftItems = items.map {
            PurchaseItemDraft(
                accountCode = it.accountCode,
                description = it.description,
                origMinor = it.totalMinor,
                isAsset = it.isAsset
            )
        }
        val draft = PostingRules.createPurchaseInvoiceDraft(
            vendorPartyId = vendorPartyId,
            items = draftItems,
            currency = currency,
            exchangeRate = exchangeRate,
            dateEpochDay = dateEpochDay,
            memo = "فاتورة مشتريات #$docNumber"
        )
        persistJournalDraft(docId, docNumber, draft)

        recordAuditLog("DOCUMENT", docId, "POST_PURCHASE_INVOICE", null, "Posted Purchase Invoice #$docNumber")
        idempotencyKey?.let { db.idempotencyDao().insertKey(IdempotencyKeyEntity(it, docId)) }

        if (enableInvariantValidation) invariants.verifyAll()
        docEntity
    }

    /**
     * Posts a Payment Voucher:
     * DR 2101 (Vendor Settlement) OR DR 5101 (Direct ISP Subscription) OR DR 3201 (Partner Drawing),
     * CR Treasury (or CR Partner Current 3201 if paymentSource == PARTNER_PERSONAL).
     */
    suspend fun postPaymentVoucher(
        recipientPartyId: String,
        treasuryId: String? = null,
        fiscalYear: Int,
        dateEpochDay: Long,
        amountOrigMinor: Long,
        currency: CurrencyCode,
        exchangeRate: ExchangeRate,
        paymentType: PaymentVoucherType,
        customExpenseCode: String? = null,
        invoiceAllocations: List<InvoiceAllocationSpec> = emptyList(),
        notes: String = "",
        rateZone: RateZone = RateZone.DEFAULT,
        rateSource: RateSource = RateSource.SYSTEM_DAILY,
        paymentSource: PaymentSource = PaymentSource.TREASURY,
        payingPartnerPartyId: String? = null,
        idempotencyKey: String? = null
    ): DocumentEntity = db.withTransaction {
        idempotencyKey?.let { key ->
            val existingDocId = db.idempotencyDao().getDocIdForKey(key)
            if (existingDocId != null) {
                return@withTransaction db.documentDao().getDocumentById(existingDocId)!!
            }
        }

        validatePeriodIsOpen(dateEpochDay)
        validateExchangeRateGuardrail(currency, exchangeRate)

        val treasury = if (paymentSource == PaymentSource.TREASURY) {
            require(!treasuryId.isNullOrBlank()) { "Treasury account ID is required for treasury disbursement" }
            val t = db.treasuryDao().getTreasuryById(treasuryId)
                ?: error("Treasury account $treasuryId not found")
            // Zero-overdraft invariant check prior to allocating sequence number
            validateTreasuryDisbursement(treasuryId, amountOrigMinor)
            t
        } else {
            require(!payingPartnerPartyId.isNullOrBlank()) { "Paying partner party ID is required for partner out-of-pocket payment" }
            val partner = db.partyDao().getPartyById(payingPartnerPartyId)
                ?: error("Paying partner $payingPartnerPartyId not found")
            require(partner.isPartner) { "Party $payingPartnerPartyId is not registered as a partner" }
            null
        }

        // Validate vendor allocations (cross-currency supported, legacy currency match removed)
        if (invoiceAllocations.isNotEmpty()) {
            val firstAllocDoc = db.documentDao().getDocumentById(invoiceAllocations.first().invoiceDocId)
            if (firstAllocDoc != null && firstAllocDoc.currency == currency.name) {
                val totalAllocated = invoiceAllocations.sumOf { it.allocatedOrigMinor }
                require(totalAllocated <= amountOrigMinor) {
                    "Total allocated amount ($totalAllocated) exceeds payment amount ($amountOrigMinor)"
                }
            }
            invoiceAllocations.forEach { alloc ->
                val invDoc = db.documentDao().getDocumentById(alloc.invoiceDocId)
                    ?: error("Allocated purchase invoice ${alloc.invoiceDocId} not found")
                require(invDoc.status == DocumentStatus.POSTED.name) {
                    "Cannot allocate to unposted purchase invoice ${invDoc.docNumber}"
                }
                require(invDoc.partyId == recipientPartyId) {
                    "Allocated purchase invoice party (${invDoc.partyId}) does not match vendor ($recipientPartyId)"
                }
                val activeAllocs = db.allocationDao().getActiveAllocationsForInvoice(alloc.invoiceDocId)
                val previouslyAllocated = activeAllocs.sumOf { it.allocatedOrigMinor }
                val remainingInvoiceBalance = invDoc.totalMinor - previouslyAllocated
                require(alloc.allocatedOrigMinor <= remainingInvoiceBalance) {
                    "Allocated amount (${alloc.allocatedOrigMinor}) exceeds remaining purchase invoice balance ($remainingInvoiceBalance)"
                }
            }
        }

        val docNumber = allocateNextDocNumber(DocumentType.PAYMENT_VOUCHER.name, fiscalYear)
        val docId = UuidUtils.newTimeOrderedId()
        val totalBase = exchangeRate.convert(amountOrigMinor)

        val docEntity = DocumentEntity(
            id = docId,
            type = DocumentType.PAYMENT_VOUCHER.name,
            fiscalYear = fiscalYear,
            docNumber = docNumber,
            partyId = recipientPartyId,
            dateEpochDay = dateEpochDay,
            currency = currency.name,
            exchangeRateMicros = exchangeRate.rateMicros,
            rateZone = rateZone.name,
            rateSource = rateSource.name,
            totalMinor = amountOrigMinor,
            totalBaseMinor = totalBase,
            status = DocumentStatus.POSTED.name,
            notes = notes
        )
        db.documentDao().insertDocument(docEntity)

        val draft = if (paymentSource == PaymentSource.PARTNER_PERSONAL) {
            val totalRelievedOrig = if (invoiceAllocations.isNotEmpty()) invoiceAllocations.sumOf { it.allocatedOrigMinor } else amountOrigMinor
            val invDoc = if (invoiceAllocations.isNotEmpty()) db.documentDao().getDocumentById(invoiceAllocations.first().invoiceDocId) else null
            val invCurrency = if (invDoc != null) CurrencyCode.fromString(invDoc.currency) else currency
            val invRate = if (invDoc != null) ExchangeRate(invCurrency, CurrencyCode.FUNCTIONAL, invDoc.exchangeRateMicros) else exchangeRate
            PostingRules.createPartnerPersonalPaymentDraft(
                payingPartnerPartyId = payingPartnerPartyId!!,
                vendorPartyId = recipientPartyId,
                paidAmountOrigMinor = amountOrigMinor,
                paymentCurrency = currency,
                paymentRate = exchangeRate,
                invoiceRelievedOrigMinor = totalRelievedOrig,
                invoiceCurrency = invCurrency,
                invoiceRate = invRate,
                dateEpochDay = dateEpochDay,
                memo = if (notes.isNotBlank()) notes else "سداد مورد من حساب الشريك الشخصي #$docNumber"
            )
        } else {
            val t = treasury!!
            when (paymentType) {
                PaymentVoucherType.VENDOR_SETTLEMENT -> {
                    if (invoiceAllocations.isNotEmpty()) {
                        val firstAlloc = invoiceAllocations.first()
                        val invDoc = db.documentDao().getDocumentById(firstAlloc.invoiceDocId)!!
                        val invCurrency = CurrencyCode.fromString(invDoc.currency)
                        val invRate = ExchangeRate(invCurrency, CurrencyCode.FUNCTIONAL, invDoc.exchangeRateMicros)
                        val totalRelievedOrig = invoiceAllocations.sumOf { it.allocatedOrigMinor }
                        val cashPaidBase = exchangeRate.convert(amountOrigMinor)
                        val payableRelievedBase = invRate.convert(totalRelievedOrig)
                        if (cashPaidBase != payableRelievedBase || invCurrency != currency) {
                            PostingRules.createVendorPaymentWithFxDraft(
                                treasuryGlCode = t.glAccountCode,
                                treasuryId = treasuryId!!,
                                vendorPartyId = recipientPartyId,
                                paidAmountOrigMinor = amountOrigMinor,
                                paymentCurrency = currency,
                                paymentRate = exchangeRate,
                                invoiceRelievedOrigMinor = totalRelievedOrig,
                                invoiceCurrency = invCurrency,
                                invoiceRate = invRate,
                                dateEpochDay = dateEpochDay,
                                memo = "سند صرف سداد مورد #$docNumber (مع تسوية فروق صرف)"
                            )
                        } else {
                            PostingRules.createVendorPaymentDraft(
                                treasuryGlCode = t.glAccountCode,
                                treasuryId = treasuryId!!,
                                vendorPartyId = recipientPartyId,
                                amountOrigMinor = amountOrigMinor,
                                currency = currency,
                                exchangeRate = exchangeRate,
                                dateEpochDay = dateEpochDay,
                                memo = "سند صرف سداد مورد #$docNumber"
                            )
                        }
                    } else {
                        PostingRules.createVendorPaymentDraft(
                            treasuryGlCode = t.glAccountCode,
                            treasuryId = treasuryId!!,
                            vendorPartyId = recipientPartyId,
                            amountOrigMinor = amountOrigMinor,
                            currency = currency,
                            exchangeRate = exchangeRate,
                            dateEpochDay = dateEpochDay,
                            memo = "سند صرف سداد مورد #$docNumber"
                        )
                    }
                }
                PaymentVoucherType.DIRECT_ISP_SERVICE -> PostingRules.createDirectExpensePaymentDraft(
                    treasuryGlCode = t.glAccountCode,
                    treasuryId = treasuryId!!,
                    expenseAccountCode = AccountConstants.DIRECT_ISP_SERVICE_COST,
                    amountOrigMinor = amountOrigMinor,
                    currency = currency,
                    exchangeRate = exchangeRate,
                    dateEpochDay = dateEpochDay,
                    memo = "سند صرف اشتراك إنترنت رئيسي (Starlink/Fiber) #$docNumber"
                )
                PaymentVoucherType.OPERATING_EXPENSE -> PostingRules.createDirectExpensePaymentDraft(
                    treasuryGlCode = t.glAccountCode,
                    treasuryId = treasuryId!!,
                    expenseAccountCode = customExpenseCode ?: AccountConstants.OPERATING_EXPENSES,
                    amountOrigMinor = amountOrigMinor,
                    currency = currency,
                    exchangeRate = exchangeRate,
                    dateEpochDay = dateEpochDay,
                    memo = "سند صرف مصاريف تشغيل #$docNumber"
                )
                PaymentVoucherType.PARTNER_DRAWINGS -> PostingRules.createPartnerDrawingsDraft(
                    treasuryGlCode = t.glAccountCode,
                    treasuryId = treasuryId!!,
                    partnerPartyId = recipientPartyId,
                    amountOrigMinor = amountOrigMinor,
                    currency = currency,
                    exchangeRate = exchangeRate,
                    dateEpochDay = dateEpochDay,
                    memo = "سند صرف مسحوبات شريك #$docNumber"
                )
            }
        }
        persistJournalDraft(docId, docNumber, draft)

        invoiceAllocations.forEach { alloc ->
            val invDoc = db.documentDao().getDocumentById(alloc.invoiceDocId)
            val invRate = if (invDoc != null) {
                ExchangeRate(CurrencyCode.fromString(invDoc.currency), CurrencyCode.FUNCTIONAL, invDoc.exchangeRateMicros)
            } else exchangeRate
            val allocBase = invRate.convert(alloc.allocatedOrigMinor)
            db.allocationDao().insertAllocation(
                AllocationEntity(
                    id = UuidUtils.newTimeOrderedId(),
                    paymentDocId = docId,
                    invoiceDocId = alloc.invoiceDocId,
                    allocatedOrigMinor = alloc.allocatedOrigMinor,
                    allocatedBaseMinor = allocBase
                )
            )
        }

        recordAuditLog("DOCUMENT", docId, "POST_PAYMENT_VOUCHER", null, "Posted Payment Voucher #$docNumber")
        idempotencyKey?.let { db.idempotencyDao().insertKey(IdempotencyKeyEntity(it, docId)) }

        if (enableInvariantValidation) invariants.verifyAll()
        docEntity
    }

    /**
     * Posts a Treasury Transfer:
     * Enforces single-currency movements only between accounts of the same currency.
     * DR Destination Treasury, CR Source Treasury.
     */
    suspend fun postTreasuryTransfer(
        sourceTreasuryId: String,
        sourceAmountOrigMinor: Long,
        sourceCurrency: CurrencyCode,
        sourceRate: ExchangeRate,
        destTreasuryId: String,
        destAmountOrigMinor: Long,
        destCurrency: CurrencyCode,
        destRate: ExchangeRate,
        fiscalYear: Int,
        dateEpochDay: Long,
        notes: String = "",
        rateZone: RateZone = RateZone.DEFAULT,
        rateSource: RateSource = RateSource.SYSTEM_DAILY
    ): DocumentEntity = db.withTransaction {
        validatePeriodIsOpen(dateEpochDay)
        validateExchangeRateGuardrail(sourceCurrency, sourceRate)
        validateExchangeRateGuardrail(destCurrency, destRate)

        require(sourceCurrency == destCurrency) {
            "Treasury transfer only supports single-currency transfers between accounts of the same currency. Use CURRENCY_EXCHANGE for cross-currency transfers."
        }
        require(sourceAmountOrigMinor == destAmountOrigMinor) {
            "Single-currency transfer amounts must be equal"
        }

        val sourceTreasury = db.treasuryDao().getTreasuryById(sourceTreasuryId) ?: error("Source treasury not found")
        val destTreasury = db.treasuryDao().getTreasuryById(destTreasuryId) ?: error("Dest treasury not found")

        // Zero-overdraft invariant check prior to allocating sequence number
        validateTreasuryDisbursement(sourceTreasuryId, sourceAmountOrigMinor)

        val docNumber = allocateNextDocNumber(DocumentType.TREASURY_TRANSFER.name, fiscalYear)
        val docId = UuidUtils.newTimeOrderedId()
        val baseMinor = sourceRate.convert(sourceAmountOrigMinor)

        val docEntity = DocumentEntity(
            id = docId,
            type = DocumentType.TREASURY_TRANSFER.name,
            fiscalYear = fiscalYear,
            docNumber = docNumber,
            partyId = AppDatabase.WALK_IN_CASH_PARTY_ID,
            dateEpochDay = dateEpochDay,
            currency = sourceCurrency.name,
            exchangeRateMicros = sourceRate.rateMicros,
            rateZone = rateZone.name,
            rateSource = rateSource.name,
            totalMinor = sourceAmountOrigMinor,
            totalBaseMinor = baseMinor,
            status = DocumentStatus.POSTED.name,
            notes = notes
        )
        db.documentDao().insertDocument(docEntity)

        val draft = PostingRules.createTreasuryTransferDraft(
            sourceTreasuryGlCode = sourceTreasury.glAccountCode,
            sourceTreasuryId = sourceTreasuryId,
            sourceAmountOrigMinor = sourceAmountOrigMinor,
            sourceCurrency = sourceCurrency,
            sourceRate = sourceRate,
            destTreasuryGlCode = destTreasury.glAccountCode,
            destTreasuryId = destTreasuryId,
            destAmountOrigMinor = destAmountOrigMinor,
            destCurrency = destCurrency,
            destRate = destRate,
            dateEpochDay = dateEpochDay,
            memo = "تحويل بين خزائن #$docNumber"
        )
        persistJournalDraft(docId, docNumber, draft)

        recordAuditLog("DOCUMENT", docId, "POST_TREASURY_TRANSFER", null, "Posted Treasury Transfer #$docNumber")
        if (enableInvariantValidation) invariants.verifyAll()
        docEntity
    }

    /**
     * Posts a Currency Exchange document (CURRENCY_EXCHANGE) (B.3):
     * Source Treasury paid exact amount -> Destination Treasury received exact amount.
     * Exchange rate is derived: (sourceMinor / destMinor), never manually entered.
     * DR Dest Treasury (Original = Received, Base YER = Source Base YER)
     * CR Source Treasury (Original = Paid, Base YER = Source Base YER)
     * Zero FX gain/loss at exchange time.
     */
    suspend fun postCurrencyExchange(
        sourceTreasuryId: String,
        sourceAmountOrigMinor: Long,
        destTreasuryId: String,
        destAmountOrigMinor: Long,
        fiscalYear: Int,
        dateEpochDay: Long,
        notes: String = "",
        rateZone: RateZone = RateZone.DEFAULT,
        rateSource: RateSource = RateSource.SYSTEM_DAILY,
        idempotencyKey: String? = null
    ): DocumentEntity = db.withTransaction {
        idempotencyKey?.let { key ->
            val existingDocId = db.idempotencyDao().getDocIdForKey(key)
            if (existingDocId != null) {
                return@withTransaction db.documentDao().getDocumentById(existingDocId)!!
            }
        }

        validatePeriodIsOpen(dateEpochDay)
        require(sourceAmountOrigMinor > 0L) { "Source amount must be positive" }
        require(destAmountOrigMinor > 0L) { "Destination amount must be positive" }
        require(sourceTreasuryId != destTreasuryId) { "Source and destination treasuries must be different" }

        val sourceTreasury = db.treasuryDao().getTreasuryById(sourceTreasuryId)
            ?: error("Source treasury $sourceTreasuryId not found")
        val destTreasury = db.treasuryDao().getTreasuryById(destTreasuryId)
            ?: error("Dest treasury $destTreasuryId not found")

        val sourceCurrency = CurrencyCode.fromString(sourceTreasury.currency)
        val destCurrency = CurrencyCode.fromString(destTreasury.currency)
        require(sourceCurrency != destCurrency) {
            "Currency exchange must be between different currencies. Use TREASURY_TRANSFER for same-currency transfers."
        }

        // Zero-overdraft invariant check on source treasury prior to allocating sequence number
        validateTreasuryDisbursement(sourceTreasuryId, sourceAmountOrigMinor)

        // Calculate Source Base YER
        val sourceBaseMinor: Long = when {
            sourceCurrency == CurrencyCode.FUNCTIONAL -> sourceAmountOrigMinor
            destCurrency == CurrencyCode.FUNCTIONAL -> destAmountOrigMinor
            else -> {
                // Both are foreign currencies: resolve source rate to base YER
                val rateEntity = db.currencyRateDao().getLatestRate(sourceCurrency.name, rateZone.name, dateEpochDay)
                    ?: throw MissingExchangeRateException(sourceCurrency, rateZone, dateEpochDay)
                val rate = ExchangeRate(sourceCurrency, CurrencyCode.FUNCTIONAL, rateEntity.rateMicros)
                rate.convert(sourceAmountOrigMinor)
            }
        }

        // Derived exchange rate (sourceMinor / destMinor) scaled to micros
        val derivedRateMicros: Long = if (destAmountOrigMinor > 0L) {
            (sourceAmountOrigMinor * ExchangeRate.SCALE_MICROS) / destAmountOrigMinor
        } else {
            ExchangeRate.SCALE_MICROS
        }

        val docNumber = allocateNextDocNumber(DocumentType.CURRENCY_EXCHANGE.name, fiscalYear)
        val docId = UuidUtils.newTimeOrderedId()

        val docEntity = DocumentEntity(
            id = docId,
            type = DocumentType.CURRENCY_EXCHANGE.name,
            fiscalYear = fiscalYear,
            docNumber = docNumber,
            partyId = AppDatabase.WALK_IN_CASH_PARTY_ID,
            dateEpochDay = dateEpochDay,
            currency = sourceCurrency.name,
            exchangeRateMicros = derivedRateMicros,
            rateZone = rateZone.name,
            rateSource = rateSource.name,
            totalMinor = sourceAmountOrigMinor,
            totalBaseMinor = sourceBaseMinor,
            status = DocumentStatus.POSTED.name,
            notes = notes
        )
        db.documentDao().insertDocument(docEntity)

        val draft = PostingRules.createCurrencyExchangeDraft(
            sourceTreasuryGlCode = sourceTreasury.glAccountCode,
            sourceTreasuryId = sourceTreasuryId,
            sourceAmountOrigMinor = sourceAmountOrigMinor,
            sourceCurrency = sourceCurrency,
            destTreasuryGlCode = destTreasury.glAccountCode,
            destTreasuryId = destTreasuryId,
            destAmountOrigMinor = destAmountOrigMinor,
            destCurrency = destCurrency,
            sourceBaseMinor = sourceBaseMinor,
            dateEpochDay = dateEpochDay,
            memo = if (notes.isNotBlank()) notes else "مصارفة عملات #$docNumber"
        )
        persistJournalDraft(docId, docNumber, draft)

        recordAuditLog("DOCUMENT", docId, "POST_CURRENCY_EXCHANGE", null, "Posted Currency Exchange #$docNumber")
        idempotencyKey?.let { db.idempotencyDao().insertKey(IdempotencyKeyEntity(it, docId)) }

        if (enableInvariantValidation) invariants.verifyAll()
        docEntity
    }

    /**
     * Executes monthly straight-line depreciation for an active asset:
     * DR 5203 (Depreciation Expense), CR 1599 (Accumulated Depreciation).
     * Strictly idempotent per (periodYear, periodMonth, assetId).
     */
    suspend fun runMonthlyDepreciation(
        assetId: String,
        periodYear: Int,
        periodMonth: Int,
        dateEpochDay: Long
    ): Boolean = db.withTransaction {
        validatePeriodIsOpen(dateEpochDay)

        // Idempotency check: unique run per asset and period
        val count = db.assetDao().countDepreciationRun(periodYear, periodMonth, assetId)
        if (count > 0) return@withTransaction false

        val asset = db.assetDao().getAssetById(assetId) ?: error("Asset $assetId not found")
        if (asset.isDisposed) return@withTransaction false

        val depreciableBase = asset.purchaseCostMinor - asset.salvageValueMinor
        if (depreciableBase <= 0L || asset.accumulatedDepreciationMinor >= depreciableBase) {
            return@withTransaction false
        }

        val monthlyAmount = depreciableBase / asset.usefulLifeMonths.coerceAtLeast(1)
        val remaining = depreciableBase - asset.accumulatedDepreciationMinor
        val amountToDepreciate = minOf(monthlyAmount, remaining)
        if (amountToDepreciate <= 0L) return@withTransaction false

        val docNumber = allocateNextDocNumber(DocumentType.DEPRECIATION_RUN.name, periodYear)
        val docId = UuidUtils.newTimeOrderedId()

        val docEntity = DocumentEntity(
            id = docId,
            type = DocumentType.DEPRECIATION_RUN.name,
            fiscalYear = periodYear,
            docNumber = docNumber,
            partyId = AppDatabase.WALK_IN_CASH_PARTY_ID,
            dateEpochDay = dateEpochDay,
            currency = CurrencyCode.FUNCTIONAL.name,
            exchangeRateMicros = ExchangeRate.SCALE_MICROS,
            totalMinor = amountToDepreciate,
            totalBaseMinor = amountToDepreciate,
            status = DocumentStatus.POSTED.name,
            notes = "إهلاك الأصل ${asset.name} لشهر $periodMonth/$periodYear"
        )
        db.documentDao().insertDocument(docEntity)

        val draft = PostingRules.createDepreciationDraft(
            depreciationAmountMinor = amountToDepreciate,
            dateEpochDay = dateEpochDay,
            memo = "إهلاك شهري لأصل ${asset.name}"
        )
        val entryId = persistJournalDraft(docId, docNumber, draft)

        db.assetDao().insertDepreciationRun(
            DepreciationRunEntity(
                id = UuidUtils.newTimeOrderedId(),
                periodYear = periodYear,
                periodMonth = periodMonth,
                assetId = assetId,
                journalEntryId = entryId,
                depreciationAmountMinor = amountToDepreciate
            )
        )
        db.assetDao().incrementAccumulatedDepreciation(assetId, amountToDepreciate)

        recordAuditLog("ASSET", assetId, "DEPRECIATION_RUN", null, "Depreciated $amountToDepreciate YER for $periodYear/$periodMonth")
        if (enableInvariantValidation) invariants.verifyAll()
        true
    }

    /**
     * Voids a posted document by generating an exact compensatory REVERSAL entry.
     * Original entry remains forever in the ledger.
     */
    suspend fun voidDocument(
        docId: String,
        reversalDateEpochDay: Long,
        reason: String
    ): Boolean = db.withTransaction {
        validatePeriodIsOpen(reversalDateEpochDay)

        val doc = db.documentDao().getDocumentById(docId) ?: error("Document $docId not found")
        if (doc.status == DocumentStatus.VOIDED.name) return@withTransaction false

        val entries = db.journalDao().getEntriesForDocument(docId)
        val normalEntry = entries.firstOrNull { it.type == JournalEntryType.NORMAL.name }
            ?: error("Normal journal entry for document $docId not found")

        val originalLines = db.journalDao().getLinesForEntry(normalEntry.id)
        val draftLines = originalLines.map { line ->
            JournalDraftLine(
                lineNo = line.lineNo,
                accountCode = line.accountCode,
                partyId = line.partyId,
                treasuryId = line.treasuryId,
                origMinor = line.origMinor,
                currency = CurrencyCode.fromString(line.currency),
                exchangeRateMicros = line.exchangeRateMicros,
                baseDebitMinor = line.baseDebitMinor,
                baseCreditMinor = line.baseCreditMinor,
                memo = line.memo
            )
        }

        val reversalDraft = PostingRules.createReversalDraft(
            originalLines = draftLines,
            reversalDateEpochDay = reversalDateEpochDay,
            reversalMemo = "قيد عكسي للمستند ${doc.type} #${doc.docNumber}: $reason"
        )

        // Persist reversal entry with the next sequence entry number
        persistJournalDraft(docId, doc.docNumber, reversalDraft)

        // Update document status to VOIDED
        val updatedDoc = doc.copy(status = DocumentStatus.VOIDED.name)
        db.documentDao().updateDocument(updatedDoc)

        // If purchase invoice had linked payment vouchers (e.g. cash settlement from treasury), void them too
        if (doc.type == DocumentType.PURCHASE_INVOICE.name) {
            val activeAllocs = db.allocationDao().getActiveAllocationsForInvoice(docId)
            activeAllocs.forEach { alloc ->
                val paymentDoc = db.documentDao().getDocumentById(alloc.paymentDocId)
                if (paymentDoc != null && paymentDoc.status == DocumentStatus.POSTED.name) {
                    val payEntries = db.journalDao().getEntriesForDocument(paymentDoc.id)
                    val payNormalEntry = payEntries.firstOrNull { it.type == JournalEntryType.NORMAL.name }
                    if (payNormalEntry != null) {
                        val payOriginalLines = db.journalDao().getLinesForEntry(payNormalEntry.id)
                        val payDraftLines = payOriginalLines.map { line ->
                            JournalDraftLine(
                                lineNo = line.lineNo,
                                accountCode = line.accountCode,
                                partyId = line.partyId,
                                treasuryId = line.treasuryId,
                                origMinor = line.origMinor,
                                currency = CurrencyCode.fromString(line.currency),
                                exchangeRateMicros = line.exchangeRateMicros,
                                baseDebitMinor = line.baseDebitMinor,
                                baseCreditMinor = line.baseCreditMinor,
                                memo = line.memo
                            )
                        }
                        val payReversalDraft = PostingRules.createReversalDraft(
                            originalLines = payDraftLines,
                            reversalDateEpochDay = reversalDateEpochDay,
                            reversalMemo = "قيد عكسي لسند الصرف #${paymentDoc.docNumber} لإلغاء فاتورة المشتريات #${doc.docNumber}: $reason"
                        )
                        persistJournalDraft(paymentDoc.id, paymentDoc.docNumber, payReversalDraft)
                        db.documentDao().updateDocument(paymentDoc.copy(status = DocumentStatus.VOIDED.name))
                        recordAuditLog("DOCUMENT", paymentDoc.id, "VOID", paymentDoc.status, "Voided payment voucher #${paymentDoc.docNumber} linked to voided purchase invoice #${doc.docNumber}")
                    }
                }
            }
        }

        // Void allocations linked to this document
        db.allocationDao().voidAllocationsForDoc(docId)

        // If card sales invoice was voided, return stock
        if (doc.type == DocumentType.SALES_INVOICE.name) {
            val items = db.documentDao().getItemsForDocument(docId)
            items.forEach { itm ->
                itm.packageId?.let { pkgId ->
                    db.cardPackageDao().insertStockMovement(
                        StockMovementEntity(
                            id = UuidUtils.newTimeOrderedId(),
                            packageId = pkgId,
                            docId = docId,
                            type = "RETURN",
                            quantity = itm.quantity,
                            movementDateEpochDay = reversalDateEpochDay
                        )
                    )
                }
            }
        }

        // If purchase invoice containing assets was voided, remove un-depreciated assets or dispose them
        if (doc.type == DocumentType.PURCHASE_INVOICE.name) {
            val assets = db.assetDao().getAllAssetsSync().filter { it.docId == docId }
            assets.forEach { ast ->
                db.assetDao().setAssetDisposed(ast.id, true)
                recordAuditLog("ASSET", ast.id, "VOID_PURCHASE", "isDisposed=false", "Disposed asset ${ast.name} due to voided purchase invoice $docId")
            }
        }

        // If credit note was voided, reverse the stock return
        if (doc.type == DocumentType.CREDIT_NOTE.name) {
            val movements = db.cardPackageDao().getAllStockMovementsSync().filter { it.docId == docId && it.type == "RETURN" }
            movements.forEach { mvt ->
                db.cardPackageDao().insertStockMovement(
                    StockMovementEntity(
                        id = UuidUtils.newTimeOrderedId(),
                        packageId = mvt.packageId,
                        docId = docId,
                        type = "ADJUST",
                        quantity = -mvt.quantity,
                        movementDateEpochDay = reversalDateEpochDay
                    )
                )
            }
        }

        recordAuditLog("DOCUMENT", docId, "VOID", doc.status, "Voided: $reason")
        if (enableInvariantValidation) invariants.verifyAll()
        true
    }

    /**
     * Quick Sale (بيع سريع):
     * Field agent workflow creating a Sales Invoice + Receipt Voucher + Allocation in a single atomic transaction.
     */
    suspend fun postQuickSale(
        partyId: String,
        packageId: String?,
        description: String,
        quantity: Int,
        unitPriceMinor: Long,
        cashPaidMinor: Long,
        treasuryId: String,
        fiscalYear: Int,
        dateEpochDay: Long,
        currency: CurrencyCode = CurrencyCode.YER,
        exchangeRate: ExchangeRate = ExchangeRate.parity(CurrencyCode.YER),
        notes: String = "",
        idempotencyKey: String? = null
    ): QuickSaleResult = db.withTransaction {
        // 1. Post Sales Invoice
        val invoice = postSalesInvoice(
            partyId = partyId,
            fiscalYear = fiscalYear,
            dateEpochDay = dateEpochDay,
            currency = currency,
            exchangeRate = exchangeRate,
            cardItems = listOf(SalesItemSpec(description, quantity, unitPriceMinor, packageId)),
            serviceItems = emptyList(),
            notes = if (notes.isBlank()) "بيع سريع: $description" else notes,
            idempotencyKey = idempotencyKey?.let { "${it}_inv" }
        )

        // 2. If cash paid, post customer receipt allocated to this invoice
        val receipt = if (cashPaidMinor > 0L) {
            val allocatedMinor = minOf(cashPaidMinor, invoice.totalMinor)
            postCustomerReceipt(
                partyId = partyId,
                treasuryId = treasuryId,
                fiscalYear = fiscalYear,
                dateEpochDay = dateEpochDay,
                amountOrigMinor = cashPaidMinor,
                currency = currency,
                exchangeRate = exchangeRate,
                allocations = listOf(InvoiceAllocationSpec(invoice.id, allocatedMinor)),
                notes = "سند قبض بيع فوري لفاتورة #${invoice.docNumber}",
                idempotencyKey = idempotencyKey?.let { "${it}_rcv" }
            )
        } else {
            null
        }

        QuickSaleResult(invoice = invoice, receipt = receipt)
    }

    /**
     * Credit Note (مرتجع مبيعات):
     * DR 4102 (Sales Returns), CR 1201 (Receivables).
     * Optional card stock return movement.
     */
    suspend fun postCreditNote(
        partyId: String,
        fiscalYear: Int,
        dateEpochDay: Long,
        currency: CurrencyCode,
        exchangeRate: ExchangeRate,
        amountOrigMinor: Long,
        returnPackageId: String? = null,
        returnQty: Int = 0,
        notes: String = "",
        rateZone: RateZone = RateZone.DEFAULT,
        rateSource: RateSource = RateSource.SYSTEM_DAILY
    ): DocumentEntity = db.withTransaction {
        validatePeriodIsOpen(dateEpochDay)
        validateExchangeRateGuardrail(currency, exchangeRate)
        val docNumber = allocateNextDocNumber(DocumentType.CREDIT_NOTE.name, fiscalYear)
        val docId = UuidUtils.newTimeOrderedId()
        val baseMinor = exchangeRate.convert(amountOrigMinor)

        val docEntity = DocumentEntity(
            id = docId,
            type = DocumentType.CREDIT_NOTE.name,
            fiscalYear = fiscalYear,
            docNumber = docNumber,
            partyId = partyId,
            dateEpochDay = dateEpochDay,
            currency = currency.name,
            exchangeRateMicros = exchangeRate.rateMicros,
            rateZone = rateZone.name,
            rateSource = rateSource.name,
            totalMinor = amountOrigMinor,
            totalBaseMinor = baseMinor,
            status = DocumentStatus.POSTED.name,
            notes = notes
        )
        db.documentDao().insertDocument(docEntity)

        val draft = PostingRules.createCreditNoteDraft(
            partyId = partyId,
            amountOrigMinor = amountOrigMinor,
            currency = currency,
            exchangeRate = exchangeRate,
            dateEpochDay = dateEpochDay,
            memo = "إشعار دائن مرتجع مبيعات #$docNumber"
        )
        persistJournalDraft(docId, docNumber, draft)

        if (returnPackageId != null && returnQty > 0) {
            db.cardPackageDao().insertStockMovement(
                StockMovementEntity(
                    id = UuidUtils.newTimeOrderedId(),
                    packageId = returnPackageId,
                    docId = docId,
                    type = "RETURN",
                    quantity = returnQty,
                    movementDateEpochDay = dateEpochDay
                )
            )
        }

        recordAuditLog("DOCUMENT", docId, "POST_CREDIT_NOTE", null, "Posted Credit Note #$docNumber")
        if (enableInvariantValidation) invariants.verifyAll()
        docEntity
    }

    /**
     * Opening Balance Wizard Entry:
     * Balanced single compound entry for initial setup.
     */
    suspend fun postOpeningBalance(
        fiscalYear: Int,
        dateEpochDay: Long,
        lines: List<com.example.core.ledger.OpeningBalanceLineSpec>,
        notes: String = ""
    ): DocumentEntity = db.withTransaction {
        validatePeriodIsOpen(dateEpochDay)
        val docNumber = allocateNextDocNumber(DocumentType.OPENING_BALANCE.name, fiscalYear)
        val docId = UuidUtils.newTimeOrderedId()

        val draft = PostingRules.createOpeningBalanceDraft(
            initialLines = lines,
            dateEpochDay = dateEpochDay,
            memo = if (notes.isBlank()) "قيد افتتاحي عام متوازن #$docNumber" else notes
        )
        val totalBase = draft.totalDebitMinor

        val docEntity = DocumentEntity(
            id = docId,
            type = DocumentType.OPENING_BALANCE.name,
            fiscalYear = fiscalYear,
            docNumber = docNumber,
            partyId = AppDatabase.WALK_IN_CASH_PARTY_ID,
            dateEpochDay = dateEpochDay,
            currency = CurrencyCode.FUNCTIONAL.name,
            exchangeRateMicros = ExchangeRate.SCALE_MICROS,
            totalMinor = totalBase,
            totalBaseMinor = totalBase,
            status = DocumentStatus.POSTED.name,
            notes = notes
        )
        db.documentDao().insertDocument(docEntity)
        persistJournalDraft(docId, docNumber, draft)

        recordAuditLog("DOCUMENT", docId, "POST_OPENING_BALANCE", null, "Posted Opening Balance #$docNumber")
        if (enableInvariantValidation) invariants.verifyAll()
        docEntity
    }

    /**
     * Asset Disposal:
     * Retires or sells a fixed asset, updating 1501, 1599, Treasury, and gain/loss.
     */
    suspend fun disposeAsset(
        assetId: String,
        disposalDateEpochDay: Long,
        salvageProceedsMinor: Long = 0L,
        treasuryId: String? = null,
        notes: String = ""
    ): DocumentEntity = db.withTransaction {
        validatePeriodIsOpen(disposalDateEpochDay)
        val asset = db.assetDao().getAssetById(assetId) ?: error("Asset $assetId not found")
        require(!asset.isDisposed) { "Asset $assetId is already disposed" }

        val treasury = treasuryId?.let { db.treasuryDao().getTreasuryById(it) }
        val fiscalYear = java.time.LocalDate.ofEpochDay(disposalDateEpochDay).year
        val docNumber = allocateNextDocNumber("ASSET_DISPOSAL", fiscalYear)
        val docId = UuidUtils.newTimeOrderedId()

        val draft = PostingRules.createAssetDisposalDraft(
            costMinor = asset.purchaseCostMinor,
            accumulatedDepreciationMinor = asset.accumulatedDepreciationMinor,
            salvageProceedsMinor = salvageProceedsMinor,
            treasuryGlCode = treasury?.glAccountCode,
            treasuryId = treasury?.id,
            dateEpochDay = disposalDateEpochDay,
            memo = "استبعاد أصل شبكة: ${asset.name}"
        )

        val docEntity = DocumentEntity(
            id = docId,
            type = "ASSET_DISPOSAL",
            fiscalYear = fiscalYear,
            docNumber = docNumber,
            partyId = AppDatabase.WALK_IN_CASH_PARTY_ID,
            dateEpochDay = disposalDateEpochDay,
            currency = CurrencyCode.FUNCTIONAL.name,
            exchangeRateMicros = ExchangeRate.SCALE_MICROS,
            totalMinor = asset.purchaseCostMinor,
            totalBaseMinor = asset.purchaseCostMinor,
            status = DocumentStatus.POSTED.name,
            notes = notes
        )
        db.documentDao().insertDocument(docEntity)
        persistJournalDraft(docId, docNumber, draft)

        db.assetDao().setAssetDisposed(assetId, true)
        recordAuditLog("ASSET", assetId, "DISPOSE", "isDisposed=false", "isDisposed=true, proceeds=$salvageProceedsMinor")
        if (enableInvariantValidation) invariants.verifyAll()
        docEntity
    }

    /**
     * Cash Reconciliation (جرد الخزينة):
     * Reconciles physical count against book ledger balance.
     */
    suspend fun reconcileTreasuryCash(
        treasuryId: String,
        actualCountMinor: Long,
        fiscalYear: Int,
        dateEpochDay: Long,
        notes: String = "",
        rateZone: RateZone = RateZone.DEFAULT
    ): DocumentEntity? = db.withTransaction {
        validatePeriodIsOpen(dateEpochDay)
        val treasury = db.treasuryDao().getTreasuryById(treasuryId) ?: error("Treasury $treasuryId not found")
        val bookDebitBalance = db.journalDao().getNetDebitBalanceForTreasury(treasuryId)
        val discrepancyMinor = actualCountMinor - bookDebitBalance

        if (discrepancyMinor == 0L) return@withTransaction null // Perfectly matched, no entry needed

        val currency = CurrencyCode.fromString(treasury.currency)
        val rate = if (currency == CurrencyCode.FUNCTIONAL) {
            ExchangeRate.parity(CurrencyCode.FUNCTIONAL)
        } else {
            val rateEntity = db.currencyRateDao().getLatestRate(currency.name, rateZone.name, dateEpochDay)
            rateEntity?.let { ExchangeRate(currency, CurrencyCode.FUNCTIONAL, it.rateMicros) }
                ?: throw MissingExchangeRateException(currency, rateZone, dateEpochDay)
        }

        val docNumber = allocateNextDocNumber("CASH_RECONCILIATION", fiscalYear)
        val docId = UuidUtils.newTimeOrderedId()
        val absOrig = kotlin.math.abs(discrepancyMinor)
        val absBase = rate.convert(absOrig)

        val draft = PostingRules.createCashReconciliationDraft(
            treasuryGlCode = treasury.glAccountCode,
            treasuryId = treasuryId,
            discrepancyMinor = discrepancyMinor,
            currency = currency,
            exchangeRate = rate,
            dateEpochDay = dateEpochDay,
            memo = "تسوية جرد صندوق ${treasury.name}"
        )

        val docEntity = DocumentEntity(
            id = docId,
            type = "CASH_RECONCILIATION",
            fiscalYear = fiscalYear,
            docNumber = docNumber,
            partyId = AppDatabase.WALK_IN_CASH_PARTY_ID,
            dateEpochDay = dateEpochDay,
            currency = currency.name,
            exchangeRateMicros = rate.rateMicros,
            totalMinor = absOrig,
            totalBaseMinor = absBase,
            status = DocumentStatus.POSTED.name,
            notes = notes
        )
        db.documentDao().insertDocument(docEntity)
        persistJournalDraft(docId, docNumber, draft)

        recordAuditLog("TREASURY", treasuryId, "RECONCILE", "book=$bookDebitBalance", "actual=$actualCountMinor, diff=$discrepancyMinor")
        if (enableInvariantValidation) invariants.verifyAll()
        docEntity
    }

    /**
     * Distribute Dividends to Partners:
     * DR 3301 (Retained Earnings), CR 3201 (Partner Current).
     */
    suspend fun distributeDividends(
        totalDividendMinor: Long,
        partnerShares: List<com.example.core.ledger.PartnerDividendSpec>,
        fiscalYear: Int,
        dateEpochDay: Long,
        notes: String = ""
    ): DocumentEntity = db.withTransaction {
        validatePeriodIsOpen(dateEpochDay)
        val docNumber = allocateNextDocNumber(DocumentType.DIVIDEND_DISTRIBUTION.name, fiscalYear)
        val docId = UuidUtils.newTimeOrderedId()

        val draft = PostingRules.createDividendDistributionDraft(
            totalDividendMinor = totalDividendMinor,
            partnerShares = partnerShares,
            dateEpochDay = dateEpochDay,
            memo = "توزيع أرباح الشركاء لسنة $fiscalYear"
        )

        val docEntity = DocumentEntity(
            id = docId,
            type = DocumentType.DIVIDEND_DISTRIBUTION.name,
            fiscalYear = fiscalYear,
            docNumber = docNumber,
            partyId = AppDatabase.WALK_IN_CASH_PARTY_ID,
            dateEpochDay = dateEpochDay,
            currency = CurrencyCode.FUNCTIONAL.name,
            exchangeRateMicros = ExchangeRate.SCALE_MICROS,
            totalMinor = totalDividendMinor,
            totalBaseMinor = totalDividendMinor,
            status = DocumentStatus.POSTED.name,
            notes = notes
        )
        db.documentDao().insertDocument(docEntity)
        persistJournalDraft(docId, docNumber, draft)

        recordAuditLog("DIVIDEND", docId, "DISTRIBUTE_DIVIDENDS", null, "Distributed $totalDividendMinor YER")
        if (enableInvariantValidation) invariants.verifyAll()
        docEntity
    }

    /**
     * Year-End Closing Entry:
     * Clears all Revenue (4xxx) balances with Debits and Expense (5xxx) balances with Credits
     * Net transferred to 3301 (Retained Earnings).
     * Entry is of type CLOSING so income statements can filter it out.
     */
    suspend fun executeYearEndClosing(
        fiscalYear: Int,
        closingDateEpochDay: Long,
        memo: String = ""
    ): DocumentEntity = db.withTransaction {
        validatePeriodIsOpen(closingDateEpochDay)

        // Read all 4xxx and 5xxx net balances
        val accounts = db.accountDao().getAllAccountsSync()
        val revenueBalances = mutableMapOf<String, Long>()
        val expenseBalances = mutableMapOf<String, Long>()

        accounts.forEach { acc ->
            val netDebit = db.journalDao().getNetDebitBalanceForAccount(acc.code)
            if (acc.code.startsWith("4")) {
                // Revenue has credit normal: credit balance = -netDebit
                val creditBal = -netDebit
                if (creditBal > 0L) revenueBalances[acc.code] = creditBal
            } else if (acc.code.startsWith("5")) {
                // Expense has debit normal: debit balance = netDebit
                if (netDebit > 0L) expenseBalances[acc.code] = netDebit
            }
        }

        val draft = PostingRules.createClosingDraft(
            revenueBalances = revenueBalances,
            expenseBalances = expenseBalances,
            dateEpochDay = closingDateEpochDay,
            memo = if (memo.isBlank()) "قيد إقفال سنوي للسنة المالية $fiscalYear" else memo
        )

        val docNumber = allocateNextDocNumber(DocumentType.CLOSING_ENTRY.name, fiscalYear)
        val docId = UuidUtils.newTimeOrderedId()
        val totalBase = draft.totalDebitMinor

        val docEntity = DocumentEntity(
            id = docId,
            type = DocumentType.CLOSING_ENTRY.name,
            fiscalYear = fiscalYear,
            docNumber = docNumber,
            partyId = AppDatabase.WALK_IN_CASH_PARTY_ID,
            dateEpochDay = closingDateEpochDay,
            currency = CurrencyCode.FUNCTIONAL.name,
            exchangeRateMicros = ExchangeRate.SCALE_MICROS,
            totalMinor = totalBase,
            totalBaseMinor = totalBase,
            status = DocumentStatus.POSTED.name,
            notes = memo
        )
        db.documentDao().insertDocument(docEntity)
        persistJournalDraft(docId, docNumber, draft)

        // Close all 12 periods of the fiscal year
        for (m in 1..12) {
            val p = db.fiscalPeriodDao().getPeriod(fiscalYear, m)
            if (p == null) {
                db.fiscalPeriodDao().insertPeriod(
                    FiscalPeriodEntity(
                        id = "FP_${fiscalYear}_${m.toString().padStart(2, '0')}",
                        year = fiscalYear,
                        month = m,
                        isClosed = true,
                        closedAt = System.currentTimeMillis()
                    )
                )
            } else {
                db.fiscalPeriodDao().setPeriodClosed(fiscalYear, m, isClosed = true, closedAt = System.currentTimeMillis())
            }
        }

        recordAuditLog("DOCUMENT", docId, "EXECUTE_CLOSING", null, "Executed Year End Closing for $fiscalYear")
        if (enableInvariantValidation) invariants.verifyAll()
        docEntity
    }

    /**
     * Stock Receive (استلام دفعة كروت جديدة):
     * Increases card quantity count. No financial journal entry (quantities only).
     */
    suspend fun receiveCardStock(
        packageId: String,
        quantity: Int,
        dateEpochDay: Long,
        notes: String = ""
    ) = db.withTransaction {
        require(quantity > 0) { "Received quantity must be positive" }
        db.cardPackageDao().insertStockMovement(
            StockMovementEntity(
                id = UuidUtils.newTimeOrderedId(),
                packageId = packageId,
                docId = null,
                type = "RECEIVE",
                quantity = quantity,
                movementDateEpochDay = dateEpochDay
            )
        )
        recordAuditLog("CARD_PACKAGE", packageId, "RECEIVE_STOCK", null, "Received $quantity cards. Notes: $notes")
    }

    /**
     * Stock Inventory Adjustment (تسوية جرد كميات كروت):
     */
    suspend fun adjustCardStock(
        packageId: String,
        adjustmentQty: Int,
        dateEpochDay: Long,
        reason: String
    ) = db.withTransaction {
        require(adjustmentQty != 0) { "Adjustment quantity cannot be zero" }
        require(reason.isNotBlank()) { "Adjustment reason is mandatory" }
        db.cardPackageDao().insertStockMovement(
            StockMovementEntity(
                id = UuidUtils.newTimeOrderedId(),
                packageId = packageId,
                docId = null,
                type = "ADJUST",
                quantity = adjustmentQty,
                movementDateEpochDay = dateEpochDay
            )
        )
        recordAuditLog("CARD_PACKAGE", packageId, "ADJUST_STOCK", null, "Adjusted by $adjustmentQty cards. Reason: $reason")
    }

    private suspend fun validatePeriodIsOpen(dateEpochDay: Long) {
        val date = java.time.LocalDate.ofEpochDay(dateEpochDay)
        val year = date.year
        val month = date.monthValue

        val period = db.fiscalPeriodDao().getPeriod(year, month)
        if (period != null && period.isClosed) {
            throw IllegalStateException("Fiscal period $year/$month is closed. Cannot post or modify entries.")
        }
    }

    private fun validateExchangeRateGuardrail(currency: CurrencyCode, exchangeRate: ExchangeRate) {
        require(
            currency == CurrencyCode.FUNCTIONAL || (
                exchangeRate.rateMicros > 0L &&
                exchangeRate.toCurrency == CurrencyCode.FUNCTIONAL &&
                exchangeRate.fromCurrency == currency &&
                exchangeRate.rateMicros != ExchangeRate.SCALE_MICROS
            )
        ) {
            "Invalid foreign exchange rate supplied for functional ledger posting."
        }
    }

    private suspend fun allocateNextDocNumber(docType: String, fiscalYear: Int): Long {
        val currentSeq = db.numberSequenceDao().getSequence(docType, fiscalYear)
        val nextVal = if (currentSeq == null) {
            db.numberSequenceDao().insertSequence(NumberSequenceEntity(docType, fiscalYear, 2L))
            1L
        } else {
            val allocated = currentSeq.nextValue
            db.numberSequenceDao().updateNextValue(docType, fiscalYear, allocated + 1L)
            allocated
        }
        return nextVal
    }

    private suspend fun persistJournalDraft(
        docId: String,
        entryNumber: Long,
        draft: JournalDraft
    ): String {
        draft.assertBalanced()
        val entryId = UuidUtils.newTimeOrderedId()

        val entryEntity = JournalEntryEntity(
            id = entryId,
            docId = docId,
            entryNumber = entryNumber,
            entryDateEpochDay = draft.entryDateEpochDay,
            type = draft.type.name,
            memo = draft.memo
        )
        db.journalDao().insertEntry(entryEntity)

        val lineEntities = draft.lines.map { line ->
            JournalLineEntity(
                id = UuidUtils.newTimeOrderedId(),
                entryId = entryId,
                lineNo = line.lineNo,
                accountCode = line.accountCode,
                partyId = line.partyId,
                treasuryId = line.treasuryId,
                origMinor = line.origMinor,
                currency = line.currency.name,
                exchangeRateMicros = line.exchangeRateMicros,
                baseDebitMinor = line.baseDebitMinor,
                baseCreditMinor = line.baseCreditMinor,
                memo = line.memo
            )
        }
        db.journalDao().insertLines(lineEntities)
        return entryId
    }

    private suspend fun recordAuditLog(entityType: String, entityId: String, action: String, before: String?, after: String?) {
        db.auditLogDao().insertLog(
            AuditLogEntity(
                id = UuidUtils.newTimeOrderedId(),
                entityType = entityType,
                entityId = entityId,
                action = action,
                beforeJson = before,
                afterJson = after
            )
        )
    }
}

data class SalesItemSpec(
    val description: String,
    val quantity: Int,
    val unitPriceMinor: Long,
    val packageId: String? = null
)

data class PurchaseItemSpec(
    val description: String,
    val accountCode: String,
    val quantity: Int,
    val unitPriceMinor: Long,
    val isAsset: Boolean = false,
    val usefulLifeMonths: Int? = null
) {
    val totalMinor: Long get() = quantity * unitPriceMinor
}

data class InvoiceAllocationSpec(
    val invoiceDocId: String,
    val allocatedOrigMinor: Long
)

enum class PaymentVoucherType {
    VENDOR_SETTLEMENT,
    DIRECT_ISP_SERVICE,
    OPERATING_EXPENSE,
    PARTNER_DRAWINGS
}

enum class PaymentSource {
    TREASURY,
    PARTNER_PERSONAL
}

data class QuickSaleResult(
    val invoice: DocumentEntity,
    val receipt: DocumentEntity?
)
