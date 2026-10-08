package com.example.core.ledger

import com.example.core.model.CurrencyCode
import com.example.core.model.ExchangeRate

/**
 * Pure functions generating balanced JournalDraft objects for every document type.
 * Zero Android dependencies, zero side-effects, 100% unit-testable on JVM.
 */
object PostingRules {

    /**
     * Sales Invoice:
     * DR 1201 (Customer/Agent) for full invoice total
     * CR 4101 (Card Sales) for card lines
     * CR 4201 (Service Sales) for service lines
     */
    fun createSalesInvoiceDraft(
        partyId: String,
        cardTotalOrigMinor: Long,
        serviceTotalOrigMinor: Long,
        currency: CurrencyCode,
        exchangeRate: ExchangeRate,
        dateEpochDay: Long,
        memo: String
    ): JournalDraft {
        require(currency == CurrencyCode.FUNCTIONAL || (exchangeRate.rateMicros > 0L && exchangeRate.toCurrency == CurrencyCode.FUNCTIONAL && exchangeRate.fromCurrency == currency && exchangeRate.rateMicros != ExchangeRate.SCALE_MICROS)) {
            "Invalid foreign exchange rate supplied for functional ledger posting."
        }
        require(cardTotalOrigMinor >= 0L && serviceTotalOrigMinor >= 0L) { "Revenue amounts must be non-negative" }
        val totalOrigMinor = cardTotalOrigMinor + serviceTotalOrigMinor
        require(totalOrigMinor > 0L) { "Invoice total must be strictly positive" }

        val totalBaseMinor = exchangeRate.convert(totalOrigMinor)
        val origAmounts = mutableListOf<Long>()
        if (cardTotalOrigMinor > 0L) origAmounts.add(cardTotalOrigMinor)
        if (serviceTotalOrigMinor > 0L) origAmounts.add(serviceTotalOrigMinor)

        val convertedCredits = ExchangeRate.distributeConvertedLines(origAmounts, exchangeRate, totalBaseMinor)

        val lines = mutableListOf<JournalDraftLine>()
        // 1. DR Accounts Receivable (1201)
        lines.add(
            JournalDraftLine(
                lineNo = 1,
                accountCode = AccountConstants.ACCOUNTS_RECEIVABLE,
                partyId = partyId,
                origMinor = totalOrigMinor,
                currency = currency,
                exchangeRateMicros = exchangeRate.rateMicros,
                baseDebitMinor = totalBaseMinor,
                baseCreditMinor = 0L,
                memo = memo
            )
        )

        var creditIdx = 0
        var currentLineNo = 2
        if (cardTotalOrigMinor > 0L) {
            lines.add(
                JournalDraftLine(
                    lineNo = currentLineNo++,
                    accountCode = AccountConstants.CARD_SALES_REVENUE,
                    origMinor = cardTotalOrigMinor,
                    currency = currency,
                    exchangeRateMicros = exchangeRate.rateMicros,
                    baseDebitMinor = 0L,
                    baseCreditMinor = convertedCredits[creditIdx++],
                    memo = "مبيعات كروت إنترنت: $memo"
                )
            )
        }

        if (serviceTotalOrigMinor > 0L) {
            lines.add(
                JournalDraftLine(
                    lineNo = currentLineNo,
                    accountCode = AccountConstants.DIRECT_SERVICE_REVENUE,
                    origMinor = serviceTotalOrigMinor,
                    currency = currency,
                    exchangeRateMicros = exchangeRate.rateMicros,
                    baseDebitMinor = 0L,
                    baseCreditMinor = convertedCredits[creditIdx],
                    memo = "إيرادات خدمات واشتراكات: $memo"
                )
            )
        }

        return JournalDraft(
            type = JournalEntryType.NORMAL,
            entryDateEpochDay = dateEpochDay,
            memo = memo,
            lines = lines
        )
    }

    /**
     * Customer Receipt Voucher:
     * DR Treasury (1101/1102)
     * CR 1201 (Customer/Agent)
     */
    fun createCustomerReceiptDraft(
        treasuryGlCode: String,
        treasuryId: String,
        partyId: String,
        amountOrigMinor: Long,
        currency: CurrencyCode,
        exchangeRate: ExchangeRate,
        dateEpochDay: Long,
        memo: String
    ): JournalDraft {
        require(amountOrigMinor > 0L) { "Receipt amount must be positive" }
        val baseMinor = exchangeRate.convert(amountOrigMinor)

        val lines = listOf(
            JournalDraftLine(
                lineNo = 1,
                accountCode = treasuryGlCode,
                treasuryId = treasuryId,
                origMinor = amountOrigMinor,
                currency = currency,
                exchangeRateMicros = exchangeRate.rateMicros,
                baseDebitMinor = baseMinor,
                baseCreditMinor = 0L,
                memo = memo
            ),
            JournalDraftLine(
                lineNo = 2,
                accountCode = AccountConstants.ACCOUNTS_RECEIVABLE,
                partyId = partyId,
                origMinor = amountOrigMinor,
                currency = currency,
                exchangeRateMicros = exchangeRate.rateMicros,
                baseDebitMinor = 0L,
                baseCreditMinor = baseMinor,
                memo = memo
            )
        )

        return JournalDraft(
            type = JournalEntryType.NORMAL,
            entryDateEpochDay = dateEpochDay,
            memo = memo,
            lines = lines
        )
    }

    /**
     * Capital / Partner Contribution Receipt Voucher:
     * DR Treasury
     * CR 3101 (Capital) or 3201 (Partner Current)
     */
    fun createCapitalContributionDraft(
        treasuryGlCode: String,
        treasuryId: String,
        targetAccountCode: String,
        partyId: String?,
        amountOrigMinor: Long,
        currency: CurrencyCode,
        exchangeRate: ExchangeRate,
        dateEpochDay: Long,
        memo: String
    ): JournalDraft {
        require(amountOrigMinor > 0L) { "Contribution amount must be positive" }
        require(targetAccountCode == AccountConstants.CAPITAL || targetAccountCode == AccountConstants.PARTNER_CURRENT) {
            "Capital receipt target account must be 3101 or 3201"
        }
        val baseMinor = exchangeRate.convert(amountOrigMinor)

        val lines = listOf(
            JournalDraftLine(
                lineNo = 1,
                accountCode = treasuryGlCode,
                treasuryId = treasuryId,
                origMinor = amountOrigMinor,
                currency = currency,
                exchangeRateMicros = exchangeRate.rateMicros,
                baseDebitMinor = baseMinor,
                baseCreditMinor = 0L,
                memo = memo
            ),
            JournalDraftLine(
                lineNo = 2,
                accountCode = targetAccountCode,
                partyId = partyId,
                origMinor = amountOrigMinor,
                currency = currency,
                exchangeRateMicros = exchangeRate.rateMicros,
                baseDebitMinor = 0L,
                baseCreditMinor = baseMinor,
                memo = memo
            )
        )

        return JournalDraft(
            type = JournalEntryType.NORMAL,
            entryDateEpochDay = dateEpochDay,
            memo = memo,
            lines = lines
        )
    }

    /**
     * In-Kind (Fixed Asset) Partner Capital Contribution:
     * DR 1501 (Fixed Assets)
     * CR 3101 (Capital - Partner Equity)
     */
    fun createInKindCapitalContributionDraft(
        assetGlCode: String = AccountConstants.FIXED_ASSETS_NETWORK,
        targetAccountCode: String = AccountConstants.CAPITAL,
        partnerPartyId: String,
        amountOrigMinor: Long,
        currency: CurrencyCode,
        exchangeRate: ExchangeRate,
        dateEpochDay: Long,
        memo: String
    ): JournalDraft {
        require(amountOrigMinor > 0L) { "Contribution amount must be positive" }
        require(targetAccountCode == AccountConstants.CAPITAL || targetAccountCode == AccountConstants.PARTNER_CURRENT) {
            "Capital target account must be 3101 or 3201"
        }
        val baseMinor = exchangeRate.convert(amountOrigMinor)

        val lines = listOf(
            JournalDraftLine(
                lineNo = 1,
                accountCode = assetGlCode,
                origMinor = amountOrigMinor,
                currency = currency,
                exchangeRateMicros = exchangeRate.rateMicros,
                baseDebitMinor = baseMinor,
                baseCreditMinor = 0L,
                memo = memo
            ),
            JournalDraftLine(
                lineNo = 2,
                accountCode = targetAccountCode,
                partyId = partnerPartyId,
                origMinor = amountOrigMinor,
                currency = currency,
                exchangeRateMicros = exchangeRate.rateMicros,
                baseDebitMinor = 0L,
                baseCreditMinor = baseMinor,
                memo = memo
            )
        )

        return JournalDraft(
            type = JournalEntryType.NORMAL,
            entryDateEpochDay = dateEpochDay,
            memo = memo,
            lines = lines
        )
    }

    /**
     * Credit Note (Sales Return):
     * DR 4102 (Sales Returns)
     * CR 1201 (Customer/Agent)
     */
    fun createCreditNoteDraft(
        partyId: String,
        amountOrigMinor: Long,
        currency: CurrencyCode,
        exchangeRate: ExchangeRate,
        dateEpochDay: Long,
        memo: String
    ): JournalDraft {
        require(amountOrigMinor > 0L) { "Credit note amount must be positive" }
        val baseMinor = exchangeRate.convert(amountOrigMinor)

        val lines = listOf(
            JournalDraftLine(
                lineNo = 1,
                accountCode = AccountConstants.SALES_RETURNS,
                origMinor = amountOrigMinor,
                currency = currency,
                exchangeRateMicros = exchangeRate.rateMicros,
                baseDebitMinor = baseMinor,
                baseCreditMinor = 0L,
                memo = memo
            ),
            JournalDraftLine(
                lineNo = 2,
                accountCode = AccountConstants.ACCOUNTS_RECEIVABLE,
                partyId = partyId,
                origMinor = amountOrigMinor,
                currency = currency,
                exchangeRateMicros = exchangeRate.rateMicros,
                baseDebitMinor = 0L,
                baseCreditMinor = baseMinor,
                memo = memo
            )
        )

        return JournalDraft(
            type = JournalEntryType.NORMAL,
            entryDateEpochDay = dateEpochDay,
            memo = memo,
            lines = lines
        )
    }

    /**
     * Purchase Invoice:
     * DR 1501 for Fixed Assets or 5xxx for Expenses
     * CR 2101 (Vendor) in full
     */
    fun createPurchaseInvoiceDraft(
        vendorPartyId: String,
        items: List<PurchaseItemDraft>,
        currency: CurrencyCode,
        exchangeRate: ExchangeRate,
        dateEpochDay: Long,
        memo: String
    ): JournalDraft {
        require(items.isNotEmpty()) { "Purchase invoice must have at least one line item" }
        val totalOrigMinor = items.sumOf { it.origMinor }
        require(totalOrigMinor > 0L) { "Purchase total must be positive" }

        val totalBaseMinor = exchangeRate.convert(totalOrigMinor)
        val origAmounts = items.map { it.origMinor }
        val convertedDebits = ExchangeRate.distributeConvertedLines(origAmounts, exchangeRate, totalBaseMinor)

        val lines = mutableListOf<JournalDraftLine>()
        var lineNo = 1
        items.forEachIndexed { index, item ->
            lines.add(
                JournalDraftLine(
                    lineNo = lineNo++,
                    accountCode = item.accountCode,
                    origMinor = item.origMinor,
                    currency = currency,
                    exchangeRateMicros = exchangeRate.rateMicros,
                    baseDebitMinor = convertedDebits[index],
                    baseCreditMinor = 0L,
                    memo = item.description
                )
            )
        }

        // CR 2101 Accounts Payable
        lines.add(
            JournalDraftLine(
                lineNo = lineNo,
                accountCode = AccountConstants.ACCOUNTS_PAYABLE,
                partyId = vendorPartyId,
                origMinor = totalOrigMinor,
                currency = currency,
                exchangeRateMicros = exchangeRate.rateMicros,
                baseDebitMinor = 0L,
                baseCreditMinor = totalBaseMinor,
                memo = memo
            )
        )

        return JournalDraft(
            type = JournalEntryType.NORMAL,
            entryDateEpochDay = dateEpochDay,
            memo = memo,
            lines = lines
        )
    }

    /**
     * Payment Voucher: Vendor Settlement
     * DR 2101 (Vendor)
     * CR Treasury (1101/1102)
     */
    fun createVendorPaymentDraft(
        treasuryGlCode: String,
        treasuryId: String,
        vendorPartyId: String,
        amountOrigMinor: Long,
        currency: CurrencyCode,
        exchangeRate: ExchangeRate,
        dateEpochDay: Long,
        memo: String
    ): JournalDraft {
        require(amountOrigMinor > 0L) { "Payment amount must be positive" }
        val baseMinor = exchangeRate.convert(amountOrigMinor)

        val lines = listOf(
            JournalDraftLine(
                lineNo = 1,
                accountCode = AccountConstants.ACCOUNTS_PAYABLE,
                partyId = vendorPartyId,
                origMinor = amountOrigMinor,
                currency = currency,
                exchangeRateMicros = exchangeRate.rateMicros,
                baseDebitMinor = baseMinor,
                baseCreditMinor = 0L,
                memo = memo
            ),
            JournalDraftLine(
                lineNo = 2,
                accountCode = treasuryGlCode,
                treasuryId = treasuryId,
                origMinor = amountOrigMinor,
                currency = currency,
                exchangeRateMicros = exchangeRate.rateMicros,
                baseDebitMinor = 0L,
                baseCreditMinor = baseMinor,
                memo = memo
            )
        )

        return JournalDraft(
            type = JournalEntryType.NORMAL,
            entryDateEpochDay = dateEpochDay,
            memo = memo,
            lines = lines
        )
    }

    /**
     * Payment Voucher: Vendor Settlement with Realized FX Gain/Loss (IAS 21):
     * DR 2101 Accounts Payable (relieved at invoice book base value)
     * DR 5901 FX Loss (if cash paid base > liability relieved base) OR CR 4901 FX Gain (if cash paid base < liability relieved base)
     * CR Treasury (paid at settlement rate)
     */
    fun createVendorPaymentWithFxDraft(
        treasuryGlCode: String,
        treasuryId: String,
        vendorPartyId: String,
        paidAmountOrigMinor: Long,
        paymentCurrency: CurrencyCode,
        paymentRate: ExchangeRate,
        invoiceRelievedOrigMinor: Long,
        invoiceCurrency: CurrencyCode,
        invoiceRate: ExchangeRate,
        dateEpochDay: Long,
        memo: String
    ): JournalDraft {
        require(paidAmountOrigMinor > 0L) { "Payment amount must be positive" }
        require(invoiceRelievedOrigMinor > 0L) { "Invoice relieved amount must be positive" }

        val cashPaidBase = paymentRate.convert(paidAmountOrigMinor)
        val payableRelievedBase = invoiceRate.convert(invoiceRelievedOrigMinor)

        val lines = mutableListOf<JournalDraftLine>()
        var lineNo = 1

        // DR Accounts Payable (2101) with book value of the invoice
        lines.add(
            JournalDraftLine(
                lineNo = lineNo++,
                accountCode = AccountConstants.ACCOUNTS_PAYABLE,
                partyId = vendorPartyId,
                origMinor = invoiceRelievedOrigMinor,
                currency = invoiceCurrency,
                exchangeRateMicros = invoiceRate.rateMicros,
                baseDebitMinor = payableRelievedBase,
                baseCreditMinor = 0L,
                memo = memo
            )
        )

        if (cashPaidBase > payableRelievedBase) {
            // Paid more base currency than booked invoice -> FX Loss (DR 5901)
            val lossMinor = cashPaidBase - payableRelievedBase
            lines.add(
                JournalDraftLine(
                    lineNo = lineNo++,
                    accountCode = AccountConstants.REALIZED_FX_LOSS,
                    origMinor = lossMinor,
                    currency = CurrencyCode.FUNCTIONAL,
                    exchangeRateMicros = ExchangeRate.SCALE_MICROS,
                    baseDebitMinor = lossMinor,
                    baseCreditMinor = 0L,
                    memo = "خسارة تسوية فروق عملة: $memo"
                )
            )
        }

        // CR Treasury with actual cash paid at payment rate
        lines.add(
            JournalDraftLine(
                lineNo = lineNo++,
                accountCode = treasuryGlCode,
                treasuryId = treasuryId,
                origMinor = paidAmountOrigMinor,
                currency = paymentCurrency,
                exchangeRateMicros = paymentRate.rateMicros,
                baseDebitMinor = 0L,
                baseCreditMinor = cashPaidBase,
                memo = memo
            )
        )

        if (cashPaidBase < payableRelievedBase) {
            // Paid less base currency than booked invoice -> FX Gain (CR 4901)
            val gainMinor = payableRelievedBase - cashPaidBase
            lines.add(
                JournalDraftLine(
                    lineNo = lineNo,
                    accountCode = AccountConstants.REALIZED_FX_GAIN,
                    origMinor = gainMinor,
                    currency = CurrencyCode.FUNCTIONAL,
                    exchangeRateMicros = ExchangeRate.SCALE_MICROS,
                    baseDebitMinor = 0L,
                    baseCreditMinor = gainMinor,
                    memo = "أرباح تسوية فروق عملة: $memo"
                )
            )
        }

        return JournalDraft(
            type = JournalEntryType.NORMAL,
            entryDateEpochDay = dateEpochDay,
            memo = memo,
            lines = lines
        )
    }

    /**
     * Partner Paid Out-of-Pocket (B.5):
     * DR 2101 Accounts Payable (relieved at invoice rate or transaction rate)
     * DR 5901 FX Loss (if paid base > relieved base) OR CR 4901 FX Gain (if paid base < relieved base)
     * CR 3201 Partner Current (paying partner) at transaction-date rate.
     * Cash boxes remain untouched.
     */
    fun createPartnerPersonalPaymentDraft(
        payingPartnerPartyId: String,
        vendorPartyId: String,
        paidAmountOrigMinor: Long,
        paymentCurrency: CurrencyCode,
        paymentRate: ExchangeRate,
        invoiceRelievedOrigMinor: Long,
        invoiceCurrency: CurrencyCode,
        invoiceRate: ExchangeRate,
        dateEpochDay: Long,
        memo: String
    ): JournalDraft {
        require(paidAmountOrigMinor > 0L) { "Payment amount must be positive" }
        require(invoiceRelievedOrigMinor > 0L) { "Invoice relieved amount must be positive" }

        val partnerPaidBase = paymentRate.convert(paidAmountOrigMinor)
        val payableRelievedBase = invoiceRate.convert(invoiceRelievedOrigMinor)

        val lines = mutableListOf<JournalDraftLine>()
        var lineNo = 1

        // DR Accounts Payable (2101) with book value of the invoice
        lines.add(
            JournalDraftLine(
                lineNo = lineNo++,
                accountCode = AccountConstants.ACCOUNTS_PAYABLE,
                partyId = vendorPartyId,
                origMinor = invoiceRelievedOrigMinor,
                currency = invoiceCurrency,
                exchangeRateMicros = invoiceRate.rateMicros,
                baseDebitMinor = payableRelievedBase,
                baseCreditMinor = 0L,
                memo = memo
            )
        )

        if (partnerPaidBase > payableRelievedBase) {
            // Paid more base than booked liability -> FX Loss (DR 5901)
            val lossMinor = partnerPaidBase - payableRelievedBase
            lines.add(
                JournalDraftLine(
                    lineNo = lineNo++,
                    accountCode = AccountConstants.REALIZED_FX_LOSS,
                    origMinor = lossMinor,
                    currency = CurrencyCode.FUNCTIONAL,
                    exchangeRateMicros = ExchangeRate.SCALE_MICROS,
                    baseDebitMinor = lossMinor,
                    baseCreditMinor = 0L,
                    memo = "خسارة تسوية فروق عملة: $memo"
                )
            )
        }

        // CR Partner Current (3201) with actual value paid by the partner
        lines.add(
            JournalDraftLine(
                lineNo = lineNo++,
                accountCode = AccountConstants.PARTNER_CURRENT,
                partyId = payingPartnerPartyId,
                origMinor = paidAmountOrigMinor,
                currency = paymentCurrency,
                exchangeRateMicros = paymentRate.rateMicros,
                baseDebitMinor = 0L,
                baseCreditMinor = partnerPaidBase,
                memo = memo
            )
        )

        if (partnerPaidBase < payableRelievedBase) {
            // Paid less base than booked liability -> FX Gain (CR 4901)
            val gainMinor = payableRelievedBase - partnerPaidBase
            lines.add(
                JournalDraftLine(
                    lineNo = lineNo,
                    accountCode = AccountConstants.REALIZED_FX_GAIN,
                    origMinor = gainMinor,
                    currency = CurrencyCode.FUNCTIONAL,
                    exchangeRateMicros = ExchangeRate.SCALE_MICROS,
                    baseDebitMinor = 0L,
                    baseCreditMinor = gainMinor,
                    memo = "أرباح تسوية فروق عملة: $memo"
                )
            )
        }

        return JournalDraft(
            type = JournalEntryType.NORMAL,
            entryDateEpochDay = dateEpochDay,
            memo = memo,
            lines = lines
        )
    }

    /**
     * Payment Voucher: Direct Expense (e.g. 5101 Upstream ISP or 5201 Operating)
     * DR Expense Account (5xxx)
     * CR Treasury
     */
    fun createDirectExpensePaymentDraft(
        treasuryGlCode: String,
        treasuryId: String,
        expenseAccountCode: String,
        amountOrigMinor: Long,
        currency: CurrencyCode,
        exchangeRate: ExchangeRate,
        dateEpochDay: Long,
        memo: String
    ): JournalDraft {
        require(amountOrigMinor > 0L) { "Expense amount must be positive" }
        require(expenseAccountCode.startsWith("5")) { "Expense account code must start with 5xxx" }
        val baseMinor = exchangeRate.convert(amountOrigMinor)

        val lines = listOf(
            JournalDraftLine(
                lineNo = 1,
                accountCode = expenseAccountCode,
                origMinor = amountOrigMinor,
                currency = currency,
                exchangeRateMicros = exchangeRate.rateMicros,
                baseDebitMinor = baseMinor,
                baseCreditMinor = 0L,
                memo = memo
            ),
            JournalDraftLine(
                lineNo = 2,
                accountCode = treasuryGlCode,
                treasuryId = treasuryId,
                origMinor = amountOrigMinor,
                currency = currency,
                exchangeRateMicros = exchangeRate.rateMicros,
                baseDebitMinor = 0L,
                baseCreditMinor = baseMinor,
                memo = memo
            )
        )

        return JournalDraft(
            type = JournalEntryType.NORMAL,
            entryDateEpochDay = dateEpochDay,
            memo = memo,
            lines = lines
        )
    }

    /**
     * Payment Voucher: Partner Drawings
     * DR 3201 (Partner Current)
     * CR Treasury
     */
    fun createPartnerDrawingsDraft(
        treasuryGlCode: String,
        treasuryId: String,
        partnerPartyId: String,
        amountOrigMinor: Long,
        currency: CurrencyCode,
        exchangeRate: ExchangeRate,
        dateEpochDay: Long,
        memo: String
    ): JournalDraft {
        require(amountOrigMinor > 0L) { "Drawing amount must be positive" }
        val baseMinor = exchangeRate.convert(amountOrigMinor)

        val lines = listOf(
            JournalDraftLine(
                lineNo = 1,
                accountCode = AccountConstants.PARTNER_CURRENT,
                partyId = partnerPartyId,
                origMinor = amountOrigMinor,
                currency = currency,
                exchangeRateMicros = exchangeRate.rateMicros,
                baseDebitMinor = baseMinor,
                baseCreditMinor = 0L,
                memo = memo
            ),
            JournalDraftLine(
                lineNo = 2,
                accountCode = treasuryGlCode,
                treasuryId = treasuryId,
                origMinor = amountOrigMinor,
                currency = currency,
                exchangeRateMicros = exchangeRate.rateMicros,
                baseDebitMinor = 0L,
                baseCreditMinor = baseMinor,
                memo = memo
            )
        )

        return JournalDraft(
            type = JournalEntryType.NORMAL,
            entryDateEpochDay = dateEpochDay,
            memo = memo,
            lines = lines
        )
    }

    /**
     * Treasury Transfer (with optional foreign exchange gain/loss)
     * DR Dest Treasury (destBaseMinor)
     * CR Source Treasury (sourceBaseMinor)
     * Balanced by CR 4901 (Gain) or DR 5901 (Loss) if sourceBaseMinor != destBaseMinor
     */
    fun createTreasuryTransferDraft(
        sourceTreasuryGlCode: String,
        sourceTreasuryId: String,
        sourceAmountOrigMinor: Long,
        sourceCurrency: CurrencyCode,
        sourceRate: ExchangeRate,
        destTreasuryGlCode: String,
        destTreasuryId: String,
        destAmountOrigMinor: Long,
        destCurrency: CurrencyCode,
        destRate: ExchangeRate,
        dateEpochDay: Long,
        memo: String
    ): JournalDraft {
        require(sourceCurrency == destCurrency) {
            "Treasury transfer only supports single-currency transfers between accounts of the same currency. Use CURRENCY_EXCHANGE for cross-currency transfers."
        }
        require(sourceAmountOrigMinor == destAmountOrigMinor) {
            "Single-currency transfer amounts must be equal"
        }
        val sourceBase = sourceRate.convert(sourceAmountOrigMinor)
        val destBase = destRate.convert(destAmountOrigMinor)
        val lines = mutableListOf<JournalDraftLine>()

        var lineNo = 1
        lines.add(
            JournalDraftLine(
                lineNo = lineNo++,
                accountCode = destTreasuryGlCode,
                treasuryId = destTreasuryId,
                origMinor = destAmountOrigMinor,
                currency = destCurrency,
                exchangeRateMicros = destRate.rateMicros,
                baseDebitMinor = destBase,
                baseCreditMinor = 0L,
                memo = "إيداع تحويل: $memo"
            )
        )

        if (destBase < sourceBase) {
            // Outflow was higher than inflow value -> Realized FX Loss (DR 5901)
            val lossMinor = sourceBase - destBase
            lines.add(
                JournalDraftLine(
                    lineNo = lineNo++,
                    accountCode = AccountConstants.REALIZED_FX_LOSS,
                    origMinor = lossMinor,
                    currency = CurrencyCode.FUNCTIONAL,
                    exchangeRateMicros = ExchangeRate.SCALE_MICROS,
                    baseDebitMinor = lossMinor,
                    baseCreditMinor = 0L,
                    memo = "خسارة فروق عملة: $memo"
                )
            )
        }

        lines.add(
            JournalDraftLine(
                lineNo = lineNo++,
                accountCode = sourceTreasuryGlCode,
                treasuryId = sourceTreasuryId,
                origMinor = sourceAmountOrigMinor,
                currency = sourceCurrency,
                exchangeRateMicros = sourceRate.rateMicros,
                baseDebitMinor = 0L,
                baseCreditMinor = sourceBase,
                memo = "سحب تحويل: $memo"
            )
        )

        if (destBase > sourceBase) {
            // Inflow was higher than outflow value -> Realized FX Gain (CR 4901)
            val gainMinor = destBase - sourceBase
            lines.add(
                JournalDraftLine(
                    lineNo = lineNo,
                    accountCode = AccountConstants.REALIZED_FX_GAIN,
                    origMinor = gainMinor,
                    currency = CurrencyCode.FUNCTIONAL,
                    exchangeRateMicros = ExchangeRate.SCALE_MICROS,
                    baseDebitMinor = 0L,
                    baseCreditMinor = gainMinor,
                    memo = "أرباح فروق عملة: $memo"
                )
            )
        }

        return JournalDraft(
            type = JournalEntryType.NORMAL,
            entryDateEpochDay = dateEpochDay,
            memo = memo,
            lines = lines
        )
    }

    /**
     * Currency Exchange (B.3):
     * DR Destination Treasury (Original = Received, Base YER = Source Base YER)
     * CR Source Treasury (Original = Paid, Base YER = Source Base YER)
     * Zero FX gain/loss at exchange time.
     */
    fun createCurrencyExchangeDraft(
        sourceTreasuryGlCode: String,
        sourceTreasuryId: String,
        sourceAmountOrigMinor: Long,
        sourceCurrency: CurrencyCode,
        destTreasuryGlCode: String,
        destTreasuryId: String,
        destAmountOrigMinor: Long,
        destCurrency: CurrencyCode,
        sourceBaseMinor: Long,
        dateEpochDay: Long,
        memo: String
    ): JournalDraft {
        require(sourceAmountOrigMinor > 0L) { "Source amount must be positive" }
        require(destAmountOrigMinor > 0L) { "Destination amount must be positive" }
        require(sourceBaseMinor > 0L) { "Base amount must be positive" }

        val destRateMicros = if (destCurrency == CurrencyCode.FUNCTIONAL) {
            ExchangeRate.SCALE_MICROS
        } else {
            (sourceBaseMinor * ExchangeRate.SCALE_MICROS) / destAmountOrigMinor
        }

        val sourceRateMicros = if (sourceCurrency == CurrencyCode.FUNCTIONAL) {
            ExchangeRate.SCALE_MICROS
        } else {
            (sourceBaseMinor * ExchangeRate.SCALE_MICROS) / sourceAmountOrigMinor
        }

        val lines = listOf(
            JournalDraftLine(
                lineNo = 1,
                accountCode = destTreasuryGlCode,
                treasuryId = destTreasuryId,
                origMinor = destAmountOrigMinor,
                currency = destCurrency,
                exchangeRateMicros = destRateMicros,
                baseDebitMinor = sourceBaseMinor,
                baseCreditMinor = 0L,
                memo = "إيداع مصارفة: $memo"
            ),
            JournalDraftLine(
                lineNo = 2,
                accountCode = sourceTreasuryGlCode,
                treasuryId = sourceTreasuryId,
                origMinor = sourceAmountOrigMinor,
                currency = sourceCurrency,
                exchangeRateMicros = sourceRateMicros,
                baseDebitMinor = 0L,
                baseCreditMinor = sourceBaseMinor,
                memo = "سحب مصارفة: $memo"
            )
        )

        return JournalDraft(
            type = JournalEntryType.NORMAL,
            entryDateEpochDay = dateEpochDay,
            memo = memo,
            lines = lines
        )
    }

    /**
     * Straight-line monthly depreciation:
     * DR 5203 (Depreciation Expense)
     * CR 1599 (Accumulated Depreciation)
     */
    fun createDepreciationDraft(
        depreciationAmountMinor: Long,
        dateEpochDay: Long,
        memo: String
    ): JournalDraft {
        require(depreciationAmountMinor > 0L) { "Depreciation amount must be positive" }

        val lines = listOf(
            JournalDraftLine(
                lineNo = 1,
                accountCode = AccountConstants.DEPRECIATION_EXPENSE,
                origMinor = depreciationAmountMinor,
                currency = CurrencyCode.FUNCTIONAL,
                exchangeRateMicros = ExchangeRate.SCALE_MICROS,
                baseDebitMinor = depreciationAmountMinor,
                baseCreditMinor = 0L,
                memo = memo
            ),
            JournalDraftLine(
                lineNo = 2,
                accountCode = AccountConstants.ACCUMULATED_DEPRECIATION,
                origMinor = depreciationAmountMinor,
                currency = CurrencyCode.FUNCTIONAL,
                exchangeRateMicros = ExchangeRate.SCALE_MICROS,
                baseDebitMinor = 0L,
                baseCreditMinor = depreciationAmountMinor,
                memo = memo
            )
        )

        return JournalDraft(
            type = JournalEntryType.NORMAL,
            entryDateEpochDay = dateEpochDay,
            memo = memo,
            lines = lines
        )
    }

    /**
     * Reversal Entry (Compensating entry flipping debits and credits)
     */
    fun createReversalDraft(
        originalLines: List<JournalDraftLine>,
        reversalDateEpochDay: Long,
        reversalMemo: String
    ): JournalDraft {
        require(originalLines.isNotEmpty()) { "Cannot reverse an empty journal entry" }

        val invertedLines = originalLines.mapIndexed { idx, line ->
            JournalDraftLine(
                lineNo = idx + 1,
                accountCode = line.accountCode,
                partyId = line.partyId,
                treasuryId = line.treasuryId,
                origMinor = line.origMinor,
                currency = line.currency,
                exchangeRateMicros = line.exchangeRateMicros,
                baseDebitMinor = line.baseCreditMinor, // Flip
                baseCreditMinor = line.baseDebitMinor, // Flip
                memo = "عكس: ${line.memo}"
            )
        }

        return JournalDraft(
            type = JournalEntryType.REVERSAL,
            entryDateEpochDay = reversalDateEpochDay,
            memo = reversalMemo,
            lines = invertedLines
        )
    }

    /**
     * Customer Receipt with FX settlement gain/loss (IAS 21):
     * Settling an invoice where original rate != settlement rate.
     */
    fun createCustomerReceiptWithFxDraft(
        treasuryGlCode: String,
        treasuryId: String,
        partyId: String,
        amountOrigMinor: Long,
        currency: CurrencyCode,
        settlementRate: ExchangeRate,
        invoiceRate: ExchangeRate,
        dateEpochDay: Long,
        memo: String,
        invoiceCurrency: CurrencyCode = currency,
        invoiceRelievedOrigMinor: Long = amountOrigMinor
    ): JournalDraft {
        val cashReceivedBase = settlementRate.convert(amountOrigMinor)
        val receivableRelievedBase = invoiceRate.convert(invoiceRelievedOrigMinor)

        val lines = mutableListOf<JournalDraftLine>()
        var lineNo = 1

        // DR Treasury with actual cash received at settlement rate
        lines.add(
            JournalDraftLine(
                lineNo = lineNo++,
                accountCode = treasuryGlCode,
                treasuryId = treasuryId,
                origMinor = amountOrigMinor,
                currency = currency,
                exchangeRateMicros = settlementRate.rateMicros,
                baseDebitMinor = cashReceivedBase,
                baseCreditMinor = 0L,
                memo = memo
            )
        )

        if (cashReceivedBase < receivableRelievedBase) {
            // Received less base currency than booked invoice -> FX Loss (DR 5901)
            val lossMinor = receivableRelievedBase - cashReceivedBase
            lines.add(
                JournalDraftLine(
                    lineNo = lineNo++,
                    accountCode = AccountConstants.REALIZED_FX_LOSS,
                    origMinor = lossMinor,
                    currency = CurrencyCode.FUNCTIONAL,
                    exchangeRateMicros = ExchangeRate.SCALE_MICROS,
                    baseDebitMinor = lossMinor,
                    baseCreditMinor = 0L,
                    memo = "خسارة تسوية فروق عملة: $memo"
                )
            )
        }

        // CR Accounts Receivable (1201) with book value of the invoice
        lines.add(
            JournalDraftLine(
                lineNo = lineNo++,
                accountCode = AccountConstants.ACCOUNTS_RECEIVABLE,
                partyId = partyId,
                origMinor = invoiceRelievedOrigMinor,
                currency = invoiceCurrency,
                exchangeRateMicros = invoiceRate.rateMicros,
                baseDebitMinor = 0L,
                baseCreditMinor = receivableRelievedBase,
                memo = memo
            )
        )

        if (cashReceivedBase > receivableRelievedBase) {
            // Received more base currency than booked invoice -> FX Gain (CR 4901)
            val gainMinor = cashReceivedBase - receivableRelievedBase
            lines.add(
                JournalDraftLine(
                    lineNo = lineNo,
                    accountCode = AccountConstants.REALIZED_FX_GAIN,
                    origMinor = gainMinor,
                    currency = CurrencyCode.FUNCTIONAL,
                    exchangeRateMicros = ExchangeRate.SCALE_MICROS,
                    baseDebitMinor = 0L,
                    baseCreditMinor = gainMinor,
                    memo = "أرباح تسوية فروق عملة: $memo"
                )
            )
        }

        return JournalDraft(
            type = JournalEntryType.NORMAL,
            entryDateEpochDay = dateEpochDay,
            memo = memo,
            lines = lines
        )
    }

    /**
     * Year-end closing draft:
     * Clears all Revenue (4xxx) balances with Debits
     * Clears all Expense (5xxx) balances with Credits
     * Net difference transferred to 3301 (Retained Earnings)
     */
    fun createClosingDraft(
        revenueBalances: Map<String, Long>, // accountCode -> credit balance minor
        expenseBalances: Map<String, Long>, // accountCode -> debit balance minor
        dateEpochDay: Long,
        memo: String
    ): JournalDraft {
        val totalRevenue = revenueBalances.values.sum()
        val totalExpense = expenseBalances.values.sum()
        val netIncome = totalRevenue - totalExpense

        val lines = mutableListOf<JournalDraftLine>()
        var lineNo = 1

        // Debit revenues to zero them out
        revenueBalances.filter { it.value > 0L }.forEach { (account, balance) ->
            lines.add(
                JournalDraftLine(
                    lineNo = lineNo++,
                    accountCode = account,
                    origMinor = balance,
                    currency = CurrencyCode.FUNCTIONAL,
                    exchangeRateMicros = ExchangeRate.SCALE_MICROS,
                    baseDebitMinor = balance,
                    baseCreditMinor = 0L,
                    memo = "إقفال إيراد $account"
                )
            )
        }

        // Credit expenses to zero them out
        expenseBalances.filter { it.value > 0L }.forEach { (account, balance) ->
            lines.add(
                JournalDraftLine(
                    lineNo = lineNo++,
                    accountCode = account,
                    origMinor = balance,
                    currency = CurrencyCode.FUNCTIONAL,
                    exchangeRateMicros = ExchangeRate.SCALE_MICROS,
                    baseDebitMinor = 0L,
                    baseCreditMinor = balance,
                    memo = "إقفال مصروف $account"
                )
            )
        }

        // Net income/loss to Retained Earnings (3301)
        if (netIncome > 0L) {
            // Net profit: Credit 3301
            lines.add(
                JournalDraftLine(
                    lineNo = lineNo,
                    accountCode = AccountConstants.RETAINED_EARNINGS,
                    origMinor = netIncome,
                    currency = CurrencyCode.FUNCTIONAL,
                    exchangeRateMicros = ExchangeRate.SCALE_MICROS,
                    baseDebitMinor = 0L,
                    baseCreditMinor = netIncome,
                    memo = "صافي ربح العام المحول للأرباح المبقاة"
                )
            )
        } else if (netIncome < 0L) {
            // Net loss: Debit 3301
            val absLoss = kotlin.math.abs(netIncome)
            lines.add(
                JournalDraftLine(
                    lineNo = lineNo,
                    accountCode = AccountConstants.RETAINED_EARNINGS,
                    origMinor = absLoss,
                    currency = CurrencyCode.FUNCTIONAL,
                    exchangeRateMicros = ExchangeRate.SCALE_MICROS,
                    baseDebitMinor = absLoss,
                    baseCreditMinor = 0L,
                    memo = "صافي خسارة العام المحولة للأرباح المبقاة"
                )
            )
        }

        return JournalDraft(
            type = JournalEntryType.CLOSING,
            entryDateEpochDay = dateEpochDay,
            memo = memo,
            lines = lines
        )
    }

    /**
     * Opening Balance Compound Entry:
     * Accepts a list of initial debit/credit lines.
     * Difference between debits and credits is balanced into 3301 (Retained Earnings / Equity).
     */
    fun createOpeningBalanceDraft(
        initialLines: List<OpeningBalanceLineSpec>,
        dateEpochDay: Long,
        memo: String
    ): JournalDraft {
        require(initialLines.isNotEmpty()) { "Opening balance must contain lines" }

        val lines = mutableListOf<JournalDraftLine>()
        var lineNo = 1
        initialLines.forEach { spec ->
            if (spec.debitMinor > 0L || spec.creditMinor > 0L) {
                lines.add(
                    JournalDraftLine(
                        lineNo = lineNo++,
                        accountCode = spec.accountCode,
                        partyId = spec.partyId,
                        treasuryId = spec.treasuryId,
                        origMinor = if (spec.debitMinor > 0L) spec.debitMinor else spec.creditMinor,
                        currency = CurrencyCode.FUNCTIONAL,
                        exchangeRateMicros = ExchangeRate.SCALE_MICROS,
                        baseDebitMinor = spec.debitMinor,
                        baseCreditMinor = spec.creditMinor,
                        memo = spec.memo
                    )
                )
            }
        }

        val totalDebit = lines.sumOf { it.baseDebitMinor }
        val totalCredit = lines.sumOf { it.baseCreditMinor }
        val discrepancy = totalDebit - totalCredit

        if (discrepancy > 0L) {
            // Debits exceed credits: Balance with Credit to 3301
            lines.add(
                JournalDraftLine(
                    lineNo = lineNo,
                    accountCode = AccountConstants.RETAINED_EARNINGS,
                    origMinor = discrepancy,
                    currency = CurrencyCode.FUNCTIONAL,
                    exchangeRateMicros = ExchangeRate.SCALE_MICROS,
                    baseDebitMinor = 0L,
                    baseCreditMinor = discrepancy,
                    memo = "رصيد افتتاحي متمم في الأرباح المرحّلة"
                )
            )
        } else if (discrepancy < 0L) {
            // Credits exceed debits: Balance with Debit to 3301
            val absDiff = kotlin.math.abs(discrepancy)
            lines.add(
                JournalDraftLine(
                    lineNo = lineNo,
                    accountCode = AccountConstants.RETAINED_EARNINGS,
                    origMinor = absDiff,
                    currency = CurrencyCode.FUNCTIONAL,
                    exchangeRateMicros = ExchangeRate.SCALE_MICROS,
                    baseDebitMinor = absDiff,
                    baseCreditMinor = 0L,
                    memo = "عجز رصيد افتتاحي متمم في الأرباح المرحّلة"
                )
            )
        }

        return JournalDraft(
            type = JournalEntryType.NORMAL,
            entryDateEpochDay = dateEpochDay,
            memo = memo,
            lines = lines
        )
    }

    /**
     * Asset Disposal Entry:
     * CR 1501 (Cost)
     * DR 1599 (Accumulated Depreciation)
     * DR Treasury (Proceeds received)
     * Balance to 5299 (Loss on disposal) or 4201 (Gain on disposal)
     */
    fun createAssetDisposalDraft(
        costMinor: Long,
        accumulatedDepreciationMinor: Long,
        salvageProceedsMinor: Long,
        treasuryGlCode: String?,
        treasuryId: String?,
        dateEpochDay: Long,
        memo: String
    ): JournalDraft {
        val lines = mutableListOf<JournalDraftLine>()
        var lineNo = 1

        // 1. Clear Accumulated Depreciation: DR 1599
        if (accumulatedDepreciationMinor > 0L) {
            lines.add(
                JournalDraftLine(
                    lineNo = lineNo++,
                    accountCode = AccountConstants.ACCUMULATED_DEPRECIATION,
                    origMinor = accumulatedDepreciationMinor,
                    currency = CurrencyCode.FUNCTIONAL,
                    exchangeRateMicros = ExchangeRate.SCALE_MICROS,
                    baseDebitMinor = accumulatedDepreciationMinor,
                    baseCreditMinor = 0L,
                    memo = "إقفال مجمع إهلاك الأصل المستبعد"
                )
            )
        }

        // 2. Record cash proceeds if sold: DR Treasury
        if (salvageProceedsMinor > 0L && treasuryGlCode != null && treasuryId != null) {
            lines.add(
                JournalDraftLine(
                    lineNo = lineNo++,
                    accountCode = treasuryGlCode,
                    treasuryId = treasuryId,
                    origMinor = salvageProceedsMinor,
                    currency = CurrencyCode.FUNCTIONAL,
                    exchangeRateMicros = ExchangeRate.SCALE_MICROS,
                    baseDebitMinor = salvageProceedsMinor,
                    baseCreditMinor = 0L,
                    memo = "عائدات بيع الأصل المستبعد"
                )
            )
        }

        // 3. Remove Original Cost: CR 1501
        lines.add(
            JournalDraftLine(
                lineNo = lineNo++,
                accountCode = AccountConstants.FIXED_ASSETS_NETWORK,
                origMinor = costMinor,
                currency = CurrencyCode.FUNCTIONAL,
                exchangeRateMicros = ExchangeRate.SCALE_MICROS,
                baseDebitMinor = 0L,
                baseCreditMinor = costMinor,
                memo = "استبعاد التكلفة التاريخية للأصل"
            )
        )

        // 4. Net book value vs proceeds: Loss or Gain
        val currentDebits = lines.sumOf { it.baseDebitMinor }
        val currentCredits = lines.sumOf { it.baseCreditMinor }
        val diff = currentCredits - currentDebits

        if (diff > 0L) {
            // Loss on disposal: DR 5299
            lines.add(
                JournalDraftLine(
                    lineNo = lineNo,
                    accountCode = AccountConstants.MISC_EXPENSES,
                    origMinor = diff,
                    currency = CurrencyCode.FUNCTIONAL,
                    exchangeRateMicros = ExchangeRate.SCALE_MICROS,
                    baseDebitMinor = diff,
                    baseCreditMinor = 0L,
                    memo = "خسارة استبعاد/تخريد أصل شبكة"
                )
            )
        } else if (diff < 0L) {
            // Gain on disposal: CR 4201
            val gain = kotlin.math.abs(diff)
            lines.add(
                JournalDraftLine(
                    lineNo = lineNo,
                    accountCode = AccountConstants.DIRECT_SERVICE_REVENUE,
                    origMinor = gain,
                    currency = CurrencyCode.FUNCTIONAL,
                    exchangeRateMicros = ExchangeRate.SCALE_MICROS,
                    baseDebitMinor = 0L,
                    baseCreditMinor = gain,
                    memo = "أرباح رأسمالية من بيع أصل شبكة"
                )
            )
        }

        return JournalDraft(
            type = JournalEntryType.NORMAL,
            entryDateEpochDay = dateEpochDay,
            memo = memo,
            lines = lines
        )
    }

    /**
     * Cash Count Discrepancy Adjustment:
     * Actual < Book: DR 5299 (Shortage), CR Treasury
     * Actual > Book: DR Treasury, CR 4201 (Surplus)
     */
    fun createCashReconciliationDraft(
        treasuryGlCode: String,
        treasuryId: String,
        discrepancyMinor: Long, // Positive = Surplus, Negative = Shortage
        currency: CurrencyCode,
        exchangeRate: ExchangeRate,
        dateEpochDay: Long,
        memo: String
    ): JournalDraft {
        require(discrepancyMinor != 0L) { "Discrepancy must be non-zero" }
        val absOrig = kotlin.math.abs(discrepancyMinor)
        val absBase = exchangeRate.convert(absOrig)

        val lines = if (discrepancyMinor < 0L) {
            // Shortage: DR 5299, CR Treasury
            listOf(
                JournalDraftLine(
                    lineNo = 1,
                    accountCode = AccountConstants.MISC_EXPENSES,
                    origMinor = absOrig,
                    currency = currency,
                    exchangeRateMicros = exchangeRate.rateMicros,
                    baseDebitMinor = absBase,
                    baseCreditMinor = 0L,
                    memo = "عجز جرد صندوق: $memo"
                ),
                JournalDraftLine(
                    lineNo = 2,
                    accountCode = treasuryGlCode,
                    treasuryId = treasuryId,
                    origMinor = absOrig,
                    currency = currency,
                    exchangeRateMicros = exchangeRate.rateMicros,
                    baseDebitMinor = 0L,
                    baseCreditMinor = absBase,
                    memo = "تسوية عجز جرد الصندوق"
                )
            )
        } else {
            // Surplus: DR Treasury, CR 4201
            listOf(
                JournalDraftLine(
                    lineNo = 1,
                    accountCode = treasuryGlCode,
                    treasuryId = treasuryId,
                    origMinor = absOrig,
                    currency = currency,
                    exchangeRateMicros = exchangeRate.rateMicros,
                    baseDebitMinor = absBase,
                    baseCreditMinor = 0L,
                    memo = "تسوية فائض جرد الصندوق"
                ),
                JournalDraftLine(
                    lineNo = 2,
                    accountCode = AccountConstants.DIRECT_SERVICE_REVENUE,
                    origMinor = absOrig,
                    currency = currency,
                    exchangeRateMicros = exchangeRate.rateMicros,
                    baseDebitMinor = 0L,
                    baseCreditMinor = absBase,
                    memo = "فائض جرد صندوق: $memo"
                )
            )
        }

        return JournalDraft(
            type = JournalEntryType.NORMAL,
            entryDateEpochDay = dateEpochDay,
            memo = memo,
            lines = lines
        )
    }

    /**
     * Dividend Distribution:
     * DR 3301 (Retained Earnings)
     * CR 3201 (Partner Current Accounts)
     */
    fun createDividendDistributionDraft(
        totalDividendMinor: Long,
        partnerShares: List<PartnerDividendSpec>,
        dateEpochDay: Long,
        memo: String
    ): JournalDraft {
        require(totalDividendMinor > 0L) { "Dividend amount must be positive" }
        require(partnerShares.isNotEmpty()) { "Partner shares must be provided" }

        val sharesSum = partnerShares.sumOf { it.amountMinor }
        require(sharesSum == totalDividendMinor) {
            "Sum of partner dividend shares ($sharesSum) must equal total distribution ($totalDividendMinor)"
        }

        val lines = mutableListOf<JournalDraftLine>()
        // 1. DR Retained Earnings (3301)
        lines.add(
            JournalDraftLine(
                lineNo = 1,
                accountCode = AccountConstants.RETAINED_EARNINGS,
                origMinor = totalDividendMinor,
                currency = CurrencyCode.FUNCTIONAL,
                exchangeRateMicros = ExchangeRate.SCALE_MICROS,
                baseDebitMinor = totalDividendMinor,
                baseCreditMinor = 0L,
                memo = memo
            )
        )

        // 2. CR Partner Current (3201) per partner
        var lineNo = 2
        partnerShares.forEach { partner ->
            lines.add(
                JournalDraftLine(
                    lineNo = lineNo++,
                    accountCode = AccountConstants.PARTNER_CURRENT,
                    partyId = partner.partnerPartyId,
                    origMinor = partner.amountMinor,
                    currency = CurrencyCode.FUNCTIONAL,
                    exchangeRateMicros = ExchangeRate.SCALE_MICROS,
                    baseDebitMinor = 0L,
                    baseCreditMinor = partner.amountMinor,
                    memo = "توزيع أرباح للشريك ${partner.partnerName}"
                )
            )
        }

        return JournalDraft(
            type = JournalEntryType.NORMAL,
            entryDateEpochDay = dateEpochDay,
            memo = memo,
            lines = lines
        )
    }

    /**
     * IAS 21 Periodic Revaluation (D.1):
     * Revalues foreign monetary items:
     * - Foreign Treasury/Bank Accounts (1101/1102)
     * - Foreign Receivables (1201)
     * - Foreign Payables (2101)
     *
     * Invariant: Non-monetary items (Fixed Assets 1501, Inventory 1401, Partner Capital 3101) MUST NEVER be revalued.
     *
     * If gain (delta > 0):
     * DR Monetary Account (Treasury/Receivable/Payable)
     * CR 4902 Unrealized FX Gain
     *
     * If loss (delta < 0):
     * DR 5902 Unrealized FX Loss
     * CR Monetary Account (Treasury/Receivable/Payable)
     */
    fun createPeriodicRevaluationDraft(
        accountCode: String,
        partyId: String? = null,
        treasuryId: String? = null,
        deltaMinor: Long,
        currency: CurrencyCode,
        exchangeRate: ExchangeRate,
        dateEpochDay: Long,
        memo: String
    ): JournalDraft {
        val monetaryAccounts = setOf(
            AccountConstants.CASH_VAULT,
            AccountConstants.BANKS_WALLETS,
            AccountConstants.ACCOUNTS_RECEIVABLE,
            AccountConstants.ACCOUNTS_PAYABLE
        )
        require(accountCode in monetaryAccounts) {
            "IAS 21 Violation: Account $accountCode is non-monetary and cannot be revalued. Non-monetary items (Fixed Assets 1501, Inventory 1401, Partner Capital 3101) must never be revalued under IAS 21."
        }
        require(currency != CurrencyCode.FUNCTIONAL) {
            "Cannot revalue functional currency (${CurrencyCode.FUNCTIONAL.name})"
        }
        require(deltaMinor != 0L) {
            "Revaluation delta cannot be zero"
        }

        if (accountCode == AccountConstants.ACCOUNTS_RECEIVABLE || accountCode == AccountConstants.ACCOUNTS_PAYABLE) {
            require(!partyId.isNullOrBlank()) {
                "Monetary account $accountCode requires an explicit partyId for subledger reconciliation"
            }
        }
        if (accountCode == AccountConstants.CASH_VAULT || accountCode == AccountConstants.BANKS_WALLETS) {
            require(!treasuryId.isNullOrBlank()) {
                "Monetary account $accountCode requires an explicit treasuryId"
            }
        }

        val lines = mutableListOf<JournalDraftLine>()
        val absDelta = Math.abs(deltaMinor)

        if (deltaMinor > 0L) {
            // Gain: DR Monetary Account, CR 4902 Unrealized FX Gain
            lines.add(
                JournalDraftLine(
                    lineNo = 1,
                    accountCode = accountCode,
                    partyId = partyId,
                    treasuryId = treasuryId,
                    origMinor = 0L,
                    currency = currency,
                    exchangeRateMicros = exchangeRate.rateMicros,
                    baseDebitMinor = absDelta,
                    baseCreditMinor = 0L,
                    memo = memo
                )
            )
            lines.add(
                JournalDraftLine(
                    lineNo = 2,
                    accountCode = AccountConstants.UNREALIZED_FX_GAIN,
                    partyId = null,
                    treasuryId = null,
                    origMinor = absDelta,
                    currency = CurrencyCode.FUNCTIONAL,
                    exchangeRateMicros = ExchangeRate.SCALE_MICROS,
                    baseDebitMinor = 0L,
                    baseCreditMinor = absDelta,
                    memo = memo
                )
            )
        } else {
            // Loss: DR 5902 Unrealized FX Loss, CR Monetary Account
            lines.add(
                JournalDraftLine(
                    lineNo = 1,
                    accountCode = AccountConstants.UNREALIZED_FX_LOSS,
                    partyId = null,
                    treasuryId = null,
                    origMinor = absDelta,
                    currency = CurrencyCode.FUNCTIONAL,
                    exchangeRateMicros = ExchangeRate.SCALE_MICROS,
                    baseDebitMinor = absDelta,
                    baseCreditMinor = 0L,
                    memo = memo
                )
            )
            lines.add(
                JournalDraftLine(
                    lineNo = 2,
                    accountCode = accountCode,
                    partyId = partyId,
                    treasuryId = treasuryId,
                    origMinor = 0L,
                    currency = currency,
                    exchangeRateMicros = exchangeRate.rateMicros,
                    baseDebitMinor = 0L,
                    baseCreditMinor = absDelta,
                    memo = memo
                )
            )
        }

        return JournalDraft(
            type = JournalEntryType.NORMAL,
            entryDateEpochDay = dateEpochDay,
            memo = memo,
            lines = lines
        )
    }
}

data class OpeningBalanceLineSpec(
    val accountCode: String,
    val partyId: String? = null,
    val treasuryId: String? = null,
    val debitMinor: Long = 0L,
    val creditMinor: Long = 0L,
    val memo: String = ""
)

data class PartnerDividendSpec(
    val partnerPartyId: String,
    val partnerName: String,
    val amountMinor: Long
)

data class PurchaseItemDraft(
    val accountCode: String,
    val description: String,
    val origMinor: Long,
    val isAsset: Boolean = false
)
