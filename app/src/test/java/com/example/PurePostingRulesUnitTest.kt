package com.example

import com.example.core.ledger.AccountConstants
import com.example.core.ledger.JournalEntryType
import com.example.core.ledger.PostingRules
import com.example.core.ledger.PurchaseItemDraft
import com.example.core.model.CurrencyCode
import com.example.core.model.ExchangeRate
import com.example.core.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PurePostingRulesUnitTest {

    @Test
    fun testMoneyOperationsAndFormatting() {
        val m1 = Money(15050L, CurrencyCode.YER) // 150.50 YER
        val m2 = Money(4950L, CurrencyCode.YER)  // 49.50 YER

        val sum = m1 + m2
        assertEquals(20000L, sum.minor)
        assertEquals("200.00 ر.ي", sum.format())

        val diff = m1 - m2
        assertEquals(10100L, diff.minor)
        assertEquals("101.00 ر.ي", diff.format())

        val multiplied = m2 * 3L
        assertEquals(14850L, multiplied.minor)

        val negative = -m1
        assertEquals(-15050L, negative.minor)
        assertEquals("-150.50 ر.ي", negative.format())

        val parsed = Money.parseFromUserInput("1,250.75", CurrencyCode.USD)
        assertNotNull(parsed)
        assertEquals(125075L, parsed!!.minor)
        assertEquals("$", parsed.currency.symbol)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testCurrencyMismatchThrows() {
        val yer = Money(1000L, CurrencyCode.YER)
        val usd = Money(1000L, CurrencyCode.USD)
        val unused = yer + usd
    }

    @Test
    fun testCentLevelRoundingDistributionAcrossLines() {
        // 3 lines summing to 100.00 USD at rate 530.50 YER/USD
        // Line 1: 33.33 USD -> 33.33 * 530.5 = 17681.565 -> 17682 YER
        // Line 2: 33.33 USD -> 17682 YER
        // Line 3: 33.34 USD -> 33.34 * 530.5 = 17686.87 -> 17687 YER
        // Sum of unadjusted = 17682 + 17682 + 17687 = 53051 YER
        // Target total: 100.00 * 530.5 = 53050 YER (remainder is -1 YER)
        // Remainder must be distributed to the last line to guarantee exact balance
        val rate = ExchangeRate(CurrencyCode.USD, CurrencyCode.YER, 530_500_000L) // 530.50
        val lines = listOf(3333L, 3333L, 3334L)
        val expectedTotal = rate.convert(10000L) // 5305000 minor -> 53,050 YER

        val converted = ExchangeRate.distributeConvertedLines(lines, rate, expectedTotal)
        assertEquals(expectedTotal, converted.sum())
    }

    @Test
    fun testSalesInvoicePostingRule() {
        val rate = ExchangeRate.parity(CurrencyCode.YER)
        val draft = PostingRules.createSalesInvoiceDraft(
            partyId = "CUSTOMER_1",
            cardTotalOrigMinor = 1000000L, // 10,000 YER
            serviceTotalOrigMinor = 1500000L, // 15,000 YER
            currency = CurrencyCode.YER,
            exchangeRate = rate,
            dateEpochDay = 20000L,
            memo = "فاتورة بيع كروت واشتراك"
        )

        assertEquals(2500000L, draft.totalDebitMinor)
        assertEquals(2500000L, draft.totalCreditMinor)
        assertEquals(3, draft.lines.size)

        // Line 1: DR 1201 (25,000 YER)
        assertEquals(AccountConstants.ACCOUNTS_RECEIVABLE, draft.lines[0].accountCode)
        assertEquals(2500000L, draft.lines[0].baseDebitMinor)
        assertEquals(0L, draft.lines[0].baseCreditMinor)

        // Line 2: CR 4101 (10,000 YER)
        assertEquals(AccountConstants.CARD_SALES_REVENUE, draft.lines[1].accountCode)
        assertEquals(1000000L, draft.lines[1].baseCreditMinor)

        // Line 3: CR 4201 (15,000 YER)
        assertEquals(AccountConstants.DIRECT_SERVICE_REVENUE, draft.lines[2].accountCode)
        assertEquals(1500000L, draft.lines[2].baseCreditMinor)
    }

    @Test
    fun testDirectISPServicePaymentVoucherRule() {
        // Direct Starlink subscription payment: $250.00 @ 530 YER/USD
        val rate = ExchangeRate(CurrencyCode.USD, CurrencyCode.YER, 530_000_000L)
        val draft = PostingRules.createDirectExpensePaymentDraft(
            treasuryGlCode = AccountConstants.CASH_VAULT,
            treasuryId = "TR_USD_VAULT",
            expenseAccountCode = AccountConstants.DIRECT_ISP_SERVICE_COST,
            amountOrigMinor = 25000L, // $250.00
            currency = CurrencyCode.USD,
            exchangeRate = rate,
            dateEpochDay = 20000L,
            memo = "اشتراك Starlink الشهري"
        )

        val expectedBase = 13250000L // 132,500 YER
        assertEquals(expectedBase, draft.totalDebitMinor)
        assertEquals(expectedBase, draft.totalCreditMinor)
        assertEquals(AccountConstants.DIRECT_ISP_SERVICE_COST, draft.lines[0].accountCode)
        assertEquals(AccountConstants.CASH_VAULT, draft.lines[1].accountCode)
    }

    @Test
    fun testCustomerReceiptWithRealizedFxGainIAS21() {
        // Invoice was posted at rate 530 YER/USD ($100 = 53,000 YER)
        // Customer pays $100 when market/treasury rate is 535 YER/USD ($100 = 53,500 YER)
        // Cash received: DR 1101 53,500 YER
        // Receivable cleared: CR 1201 53,000 YER
        // Realized FX Gain: CR 4901 500 YER
        val invoiceRate = ExchangeRate(CurrencyCode.USD, CurrencyCode.YER, 530_000_000L)
        val settlementRate = ExchangeRate(CurrencyCode.USD, CurrencyCode.YER, 535_000_000L)

        val draft = PostingRules.createCustomerReceiptWithFxDraft(
            treasuryGlCode = AccountConstants.CASH_VAULT,
            treasuryId = "TR_USD",
            partyId = "CUSTOMER_1",
            amountOrigMinor = 10000L, // $100.00
            currency = CurrencyCode.USD,
            settlementRate = settlementRate,
            invoiceRate = invoiceRate,
            dateEpochDay = 20000L,
            memo = "سداد فاتورة بالدولار مع فارق صرف"
        )

        assertEquals(5350000L, draft.totalDebitMinor)
        assertEquals(5350000L, draft.totalCreditMinor)
        assertEquals(3, draft.lines.size)

        // Line 1: DR Treasury 53,500
        assertEquals(5350000L, draft.lines[0].baseDebitMinor)
        // Line 2: CR 1201 53,000
        assertEquals(AccountConstants.ACCOUNTS_RECEIVABLE, draft.lines[1].accountCode)
        assertEquals(5300000L, draft.lines[1].baseCreditMinor)
        // Line 3: CR 4901 500
        assertEquals(AccountConstants.REALIZED_FX_GAIN, draft.lines[2].accountCode)
        assertEquals(50000L, draft.lines[2].baseCreditMinor)
    }

    @Test
    fun testStraightLineDepreciationDraft() {
        val draft = PostingRules.createDepreciationDraft(
            depreciationAmountMinor = 500000L, // 5,000 YER
            dateEpochDay = 20000L,
            memo = "إهلاك أجهزة الشبكة"
        )

        assertEquals(500000L, draft.totalDebitMinor)
        assertEquals(500000L, draft.totalCreditMinor)
        assertEquals(AccountConstants.DEPRECIATION_EXPENSE, draft.lines[0].accountCode)
        assertEquals(AccountConstants.ACCUMULATED_DEPRECIATION, draft.lines[1].accountCode)
    }

    @Test
    fun testReversalDraftInversion() {
        val rate = ExchangeRate.parity(CurrencyCode.YER)
        val originalDraft = PostingRules.createSalesInvoiceDraft(
            partyId = "CUSTOMER_1",
            cardTotalOrigMinor = 100000L,
            serviceTotalOrigMinor = 0L,
            currency = CurrencyCode.YER,
            exchangeRate = rate,
            dateEpochDay = 20000L,
            memo = "فاتورة أصلية"
        )

        val reversalDraft = PostingRules.createReversalDraft(
            originalLines = originalDraft.lines,
            reversalDateEpochDay = 20001L,
            reversalMemo = "عكس فاتورة أصلية"
        )

        assertEquals(JournalEntryType.REVERSAL, reversalDraft.type)
        assertEquals(originalDraft.totalDebitMinor, reversalDraft.totalDebitMinor)
        // Line 1 was DR 1201, now CR 1201
        assertEquals(originalDraft.lines[0].baseDebitMinor, reversalDraft.lines[0].baseCreditMinor)
        assertEquals(0L, reversalDraft.lines[0].baseDebitMinor)
    }

    @Test
    fun testYearEndClosingDraft() {
        val revenues = mapOf(AccountConstants.CARD_SALES_REVENUE to 1000000L)
        val expenses = mapOf(AccountConstants.DIRECT_ISP_SERVICE_COST to 600000L)
        // Net profit = 400,000 YER -> CR 3301
        val draft = PostingRules.createClosingDraft(
            revenueBalances = revenues,
            expenseBalances = expenses,
            dateEpochDay = 20365L,
            memo = "إقفال سنوي 2026"
        )

        assertEquals(JournalEntryType.CLOSING, draft.type)
        assertEquals(1000000L, draft.totalDebitMinor)
        assertEquals(1000000L, draft.totalCreditMinor)
        // CR 3301 with net profit 400,000
        val retainedLine = draft.lines.first { it.accountCode == AccountConstants.RETAINED_EARNINGS }
        assertEquals(400000L, retainedLine.baseCreditMinor)
    }
}
