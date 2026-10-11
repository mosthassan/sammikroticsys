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
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PaymentVouchersPostingUnitTest {

    private lateinit var db: AppDatabase
    private lateinit var writer: LedgerWriter
    private lateinit var repository: AccountingRepository
    private lateinit var invariants: LedgerInvariants

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.createInMemory(context)
        AppDatabase.installTriggers(db.openHelper.writableDatabase)
        AppDatabase.seedDefaultData(db.openHelper.writableDatabase)

        writer = LedgerWriter(db, enableInvariantValidation = true)
        repository = AccountingRepository(db)
        invariants = LedgerInvariants(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testAllPaymentVoucherTypesPostSuccessfullyToGeneralLedger() = runBlocking {
        val today = LocalDate.of(2026, 4, 1).toEpochDay()
        val fiscalYear = 2026

        // 1. Initial funding via customer receipt into TR_MAIN_YER (1,000,000 YER)
        writer.postCustomerReceipt(
            partyId = AppDatabase.WALK_IN_CASH_PARTY_ID,
            treasuryId = "TR_MAIN_YER",
            fiscalYear = fiscalYear,
            dateEpochDay = today,
            amountOrigMinor = 1_000_000_00L,
            currency = CurrencyCode.YER,
            exchangeRate = ExchangeRate.parity(CurrencyCode.FUNCTIONAL),
            notes = "إيداع مبيعات نقدية لتمويل الصندوق"
        )

        // Seed a vendor and a partner
        val vendor = PartyEntity(
            id = "VEND_FIBER_01",
            name = "شركة يمن نت / ستارلينك",
            isVendor = true,
            isCustomer = false,
            isPartner = false
        )
        val partner = PartyEntity(
            id = "PARTNER_01",
            name = "المهندس سام (شريك مؤسس)",
            isVendor = false,
            isCustomer = false,
            isPartner = true
        )
        db.partyDao().insertParty(vendor)
        db.partyDao().insertParty(partner)

        // Allow treasury disbursement if negative or verify balance
        db.treasuryDao().setAllowNegative("TR_MAIN_YER", true)

        // 2. Direct ISP Service payment (5101 Starlink / Fiber)
        val ispDoc = writer.postPaymentVoucher(
            recipientPartyId = vendor.id,
            treasuryId = "TR_MAIN_YER",
            fiscalYear = fiscalYear,
            dateEpochDay = today,
            amountOrigMinor = 250_000_00L,
            currency = CurrencyCode.YER,
            exchangeRate = ExchangeRate.parity(CurrencyCode.FUNCTIONAL),
            paymentType = PaymentVoucherType.DIRECT_ISP_SERVICE,
            notes = "سداد اشتراك إنترنت رئيسي ستارلينك شهر أبريل"
        )
        assertEquals("PAYMENT_VOUCHER", ispDoc.type)
        assertEquals("POSTED", ispDoc.status)

        // 3. Operating Expense payment (5201 Diesel / Electricity)
        val expDoc = writer.postPaymentVoucher(
            recipientPartyId = AppDatabase.WALK_IN_CASH_PARTY_ID,
            treasuryId = "TR_MAIN_YER",
            fiscalYear = fiscalYear,
            dateEpochDay = today,
            amountOrigMinor = 50_000_00L,
            currency = CurrencyCode.YER,
            exchangeRate = ExchangeRate.parity(CurrencyCode.FUNCTIONAL),
            paymentType = PaymentVoucherType.OPERATING_EXPENSE,
            notes = "سداد قيمة ديزل للمولد الاحتياطي"
        )
        assertEquals("PAYMENT_VOUCHER", expDoc.type)
        assertEquals("POSTED", expDoc.status)

        // 4. Vendor Settlement payment (2101 Accounts Payable)
        val vendDoc = writer.postPaymentVoucher(
            recipientPartyId = vendor.id,
            treasuryId = "TR_MAIN_YER",
            fiscalYear = fiscalYear,
            dateEpochDay = today,
            amountOrigMinor = 100_000_00L,
            currency = CurrencyCode.YER,
            exchangeRate = ExchangeRate.parity(CurrencyCode.FUNCTIONAL),
            paymentType = PaymentVoucherType.VENDOR_SETTLEMENT,
            notes = "دفعة على حساب المورد"
        )
        assertEquals("PAYMENT_VOUCHER", vendDoc.type)
        assertEquals("POSTED", vendDoc.status)

        // 5. Partner Drawings payment (3201 Partner Current)
        val drawDoc = writer.postPaymentVoucher(
            recipientPartyId = partner.id,
            treasuryId = "TR_MAIN_YER",
            fiscalYear = fiscalYear,
            dateEpochDay = today,
            amountOrigMinor = 80_000_00L,
            currency = CurrencyCode.YER,
            exchangeRate = ExchangeRate.parity(CurrencyCode.FUNCTIONAL),
            paymentType = PaymentVoucherType.PARTNER_DRAWINGS,
            notes = "مسحوبات شخصية للشريك سام"
        )
        assertEquals("PAYMENT_VOUCHER", drawDoc.type)
        assertEquals("POSTED", drawDoc.status)

        // Check Remaining Treasury Balance: 1,000,000 - 250,000 - 50,000 - 100,000 - 80,000 = 520,000 YER
        val finalBalance = repository.getTreasuryBalance("TR_MAIN_YER")
        assertEquals(520_000_00L, finalBalance)

        // Invariants must be 100% valid
        val inv = invariants.verifyAll()
        assertTrue("Double-entry invariants must hold", inv.isValid)
    }
}
