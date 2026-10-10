package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.ledger.AccountConstants
import com.example.core.model.CurrencyCode
import com.example.core.model.ExchangeRate
import com.example.data.ledger.LedgerInvariants
import com.example.data.ledger.LedgerWriter
import com.example.data.ledger.PaymentVoucherType
import com.example.data.local.AppDatabase
import com.example.data.local.entity.PartyEntity
import com.example.data.repository.AccountingRepository
import com.example.domain.usecase.FinancialStatementsUseCase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CashVaultForeignBalanceUnitTest {

    private lateinit var db: AppDatabase
    private lateinit var writer: LedgerWriter
    private lateinit var repository: AccountingRepository
    private lateinit var invariants: LedgerInvariants
    private lateinit var statementsUseCase: FinancialStatementsUseCase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.createInMemory(context)
        AppDatabase.installTriggers(db.openHelper.writableDatabase)
        AppDatabase.seedDefaultData(db.openHelper.writableDatabase)

        writer = LedgerWriter(db, enableInvariantValidation = true)
        repository = AccountingRepository(db)
        invariants = LedgerInvariants(db)
        statementsUseCase = FinancialStatementsUseCase(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testSarVaultReceiptVoucherDisplaysNativeBalanceNotBaseYer() {
        runBlocking {
            val today = LocalDate.of(2026, 3, 15).toEpochDay()
            val fiscalYear = 2026

            // Create customer
            val customer = PartyEntity(
                id = "CUST_SAR_01",
                name = "عميل سداد سعودي",
                phone = "770000001",
                isCustomer = true,
                isVendor = false,
                isPartner = false
            )
            db.partyDao().insertParty(customer)

            // Rate: 400.00 YER / SAR = 400_000_000 micro-units
            val rateSar = ExchangeRate(
                fromCurrency = CurrencyCode.SAR,
                toCurrency = CurrencyCode.FUNCTIONAL,
                rateMicros = 400_000_000L
            )

            // Post Receipt Voucher of 9,800 SAR (980,000 minor units) into TR_SAR_VAULT
            val receiptAmountSarMinor = 9_800_00L // 9,800.00 SAR
            val expectedBaseYerMinor = rateSar.convert(receiptAmountSarMinor) // 3,920,000.00 YER (392,000,000 minor)

            writer.postCustomerReceipt(
                partyId = "CUST_SAR_01",
                treasuryId = "TR_SAR_VAULT",
                fiscalYear = fiscalYear,
                dateEpochDay = today,
                amountOrigMinor = receiptAmountSarMinor,
                currency = CurrencyCode.SAR,
                exchangeRate = rateSar,
                notes = "سند قبض سداد سعودي 9,800 ر.س"
            )

            // 1. Direct DAO verification: Native balance is 9,800.00 SAR, NOT base YER
            val nativeOrigBal = db.journalDao().getNetOrigBalanceForTreasury("TR_SAR_VAULT")
            assertEquals("SAR Vault native balance must equal 9,800 SAR minor", receiptAmountSarMinor, nativeOrigBal)

            // 2. Base balance in functional currency remains 3,920,000 YER for IFRS
            val baseYerBal = db.journalDao().getNetDebitBalanceForTreasury("TR_SAR_VAULT")
            assertEquals("SAR Vault base book value must equal 3,920,000 YER minor", expectedBaseYerMinor, baseYerBal)

            // 3. Repository verification: getTreasuryBalance returns native 9,800 SAR
            val repoBal = repository.getTreasuryBalance("TR_SAR_VAULT")
            assertEquals("AccountingRepository.getTreasuryBalance must return native units", receiptAmountSarMinor, repoBal)

            val repoBaseBal = repository.getTreasuryBaseBalance("TR_SAR_VAULT")
            assertEquals("AccountingRepository.getTreasuryBaseBalance must return YER base", expectedBaseYerMinor, repoBaseBal)

            val cashByCurr = repository.getCashBalanceByCurrency("SAR")
            assertEquals("getCashBalanceByCurrency must return native units", receiptAmountSarMinor, cashByCurr)

            // 4. IFRS Balance Sheet verification: Cash asset in functional balance sheet is 3,920,000 YER
            val balanceSheet = statementsUseCase.generateBalanceSheet(today)
            val cashVaultLine = balanceSheet.lineDetails.firstOrNull { it.accountCode == AccountConstants.CASH_VAULT }
            assertNotNull("Cash Vault line must exist in balance sheet", cashVaultLine)
            assertEquals(expectedBaseYerMinor, cashVaultLine?.netBalanceMinor)

            // Invariants verified
            invariants.verifyAll()
        }
    }

    @Test
    fun testUsdAndSarMultiCurrencyVaultsAndDisbursements() {
        runBlocking {
            val today = LocalDate.of(2026, 3, 20).toEpochDay()
            val fiscalYear = 2026

            val customer = PartyEntity(id = "CUST_FX", name = "عميل عملات", phone = "770000002", isCustomer = true)
            val vendor = PartyEntity(id = "VEND_FX", name = "مورد اتصالات", phone = "770000003", isVendor = true)
            db.partyDao().insertParty(customer)
            db.partyDao().insertParty(vendor)

            val rateUsd = ExchangeRate(CurrencyCode.USD, CurrencyCode.FUNCTIONAL, 530_000_000L) // 530 YER/USD
            val rateSar = ExchangeRate(CurrencyCode.SAR, CurrencyCode.FUNCTIONAL, 400_000_000L) // 400 YER/SAR

            // Receive 1,000 USD into USD Vault
            writer.postCustomerReceipt(
                partyId = "CUST_FX",
                treasuryId = "TR_USD_VAULT",
                fiscalYear = fiscalYear,
                dateEpochDay = today,
                amountOrigMinor = 1_000_00L,
                currency = CurrencyCode.USD,
                exchangeRate = rateUsd
            )

            // Receive 10,000 SAR into SAR Vault
            writer.postCustomerReceipt(
                partyId = "CUST_FX",
                treasuryId = "TR_SAR_VAULT",
                fiscalYear = fiscalYear,
                dateEpochDay = today,
                amountOrigMinor = 10_000_00L,
                currency = CurrencyCode.SAR,
                exchangeRate = rateSar
            )

            // Receive 150,000 YER into Main YER Box
            writer.postCustomerReceipt(
                partyId = "CUST_FX",
                treasuryId = "TR_MAIN_YER",
                fiscalYear = fiscalYear,
                dateEpochDay = today,
                amountOrigMinor = 150_000_00L,
                currency = CurrencyCode.YER,
                exchangeRate = ExchangeRate.parity(CurrencyCode.FUNCTIONAL)
            )

            // Verify initial balances in native units
            assertEquals(1_000_00L, repository.getTreasuryBalance("TR_USD_VAULT"))
            assertEquals(10_000_00L, repository.getTreasuryBalance("TR_SAR_VAULT"))
            assertEquals(150_000_00L, repository.getTreasuryBalance("TR_MAIN_YER"))

            // Disburse 300 USD from USD Vault
            writer.postPaymentVoucher(
                recipientPartyId = "VEND_FX",
                treasuryId = "TR_USD_VAULT",
                fiscalYear = fiscalYear,
                dateEpochDay = today,
                amountOrigMinor = 300_00L,
                currency = CurrencyCode.USD,
                exchangeRate = rateUsd,
                paymentType = PaymentVoucherType.OPERATING_EXPENSE,
                notes = "سداد مصاريف دولار"
            )

            // Disburse 2,500 SAR from SAR Vault
            writer.postPaymentVoucher(
                recipientPartyId = "VEND_FX",
                treasuryId = "TR_SAR_VAULT",
                fiscalYear = fiscalYear,
                dateEpochDay = today,
                amountOrigMinor = 2_500_00L,
                currency = CurrencyCode.SAR,
                exchangeRate = rateSar,
                paymentType = PaymentVoucherType.OPERATING_EXPENSE,
                notes = "سداد مصاريف سعودي"
            )

            // Verify remaining balances in native units
            assertEquals("USD Vault must have 700 USD remaining", 700_00L, repository.getTreasuryBalance("TR_USD_VAULT"))
            assertEquals("SAR Vault must have 7,500 SAR remaining", 7_500_00L, repository.getTreasuryBalance("TR_SAR_VAULT"))
            assertEquals("YER Box must still have 150,000 YER", 150_000_00L, repository.getTreasuryBalance("TR_MAIN_YER"))

            invariants.verifyAll()
        }
    }
}
