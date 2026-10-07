package com.example.ui.inspector

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.ledger.AccountConstants
import com.example.core.model.CurrencyCode
import com.example.core.model.ExchangeRate
import com.example.data.ledger.InvariantCheckResult
import com.example.data.ledger.InvoiceAllocationSpec
import com.example.data.ledger.PaymentVoucherType
import com.example.data.ledger.PurchaseItemSpec
import com.example.data.ledger.SalesItemSpec
import com.example.data.local.AppDatabase
import com.example.data.local.dao.AccountBalanceRow
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.DocumentEntity
import com.example.data.local.entity.JournalEntryEntity
import com.example.data.local.entity.JournalLineEntity
import com.example.data.local.entity.PartyEntity
import com.example.data.repository.AccountingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class EntryWithLines(
    val entry: JournalEntryEntity,
    val lines: List<JournalLineEntity>
)

class InspectorViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    val repository = AccountingRepository(db)

    val trialBalance: StateFlow<List<AccountBalanceRow>> = repository.trialBalance
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allAccounts: StateFlow<List<AccountEntity>> = repository.allAccounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allDocuments: StateFlow<List<DocumentEntity>> = repository.allDocuments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allParties: StateFlow<List<PartyEntity>> = repository.allParties
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _entriesWithLines = MutableStateFlow<List<EntryWithLines>>(emptyList())
    val entriesWithLines: StateFlow<List<EntryWithLines>> = _entriesWithLines.asStateFlow()

    private val _invariantResult = MutableStateFlow<InvariantCheckResult?>(null)
    val invariantResult: StateFlow<InvariantCheckResult?> = _invariantResult.asStateFlow()

    private val _triggerTestResult = MutableStateFlow<String?>(null)
    val triggerTestResult: StateFlow<String?> = _triggerTestResult.asStateFlow()

    init {
        refreshAll()
    }

    fun refreshAll() {
        viewModelScope.launch {
            runInvariantCheck()
            loadEntriesWithLines()
        }
    }

    fun runInvariantCheck() {
        viewModelScope.launch {
            val result = repository.runInvariantCheck()
            _invariantResult.value = result
        }
    }

    private suspend fun loadEntriesWithLines() {
        val openHelper = db.openHelper.readableDatabase
        val cursor = openHelper.query("SELECT id, docId, entryNumber, entryDateEpochDay, type, memo, createdAt FROM journal_entries ORDER BY entryDateEpochDay DESC, entryNumber DESC")
        val entries = mutableListOf<JournalEntryEntity>()
        cursor.use { c ->
            while (c.moveToNext()) {
                entries.add(
                    JournalEntryEntity(
                        id = c.getString(0),
                        docId = c.getString(1),
                        entryNumber = c.getLong(2),
                        entryDateEpochDay = c.getLong(3),
                        type = c.getString(4),
                        memo = c.getString(5),
                        createdAt = c.getLong(6)
                    )
                )
            }
        }

        val full = entries.map { entry ->
            val lines = repository.getLinesForEntry(entry.id)
            EntryWithLines(entry, lines)
        }
        _entriesWithLines.value = full
    }

    /**
     * Attempts a raw SQL UPDATE on journal_lines to test that the SQLite trigger
     * prevents modification and raises an ABORT exception as required by IFRS.
     */
    fun testDirectUpdateTrigger() {
        viewModelScope.launch {
            try {
                db.openHelper.writableDatabase.execSQL("UPDATE journal_lines SET baseDebitMinor = baseDebitMinor + 100")
                _triggerTestResult.value = "فشل: المشغّل لم يمنع التعديل!"
            } catch (e: Exception) {
                _triggerTestResult.value = "نجاح الحماية: تم رفض التعديل بواسطة مشغّل قاعدة البيانات (${e.message?.take(60)}...)"
            }
        }
    }

    /**
     * Seeds canonical ISP demo transactions to immediately showcase a working ledger.
     */
    fun seedDemoTransactions() {
        viewModelScope.launch {
            val today = (System.currentTimeMillis() / 86400000L)
            val yerRate = ExchangeRate.parity(CurrencyCode.YER)
            val usdRate = ExchangeRate(CurrencyCode.USD, CurrencyCode.YER, 530_000_000L)

            // 1. Create a grocery agent party
            val agentParty = PartyEntity(
                id = "PARTY_AL_AMAL",
                name = "بقالة الأمل (وكيل كروت)",
                phone = "770123456",
                isCustomer = true
            )
            repository.insertParty(agentParty)

            // 2. Post Sales Invoice: 100 cards @ 500 YER = 50,000 YER
            val inv = repository.ledgerWriter.postSalesInvoice(
                partyId = agentParty.id,
                fiscalYear = 2026,
                dateEpochDay = today,
                currency = CurrencyCode.YER,
                exchangeRate = yerRate,
                cardItems = listOf(
                    SalesItemSpec(
                        description = "باقة كروت 1 جيجا (100 كرت)",
                        quantity = 100,
                        unitPriceMinor = 50000L
                    )
                ),
                serviceItems = emptyList(),
                notes = "فاتورة مبيعات كروت بالآجل لبقالة الأمل"
            )

            // 3. Post partial receipt voucher: 30,000 YER paid into Main Treasury
            repository.ledgerWriter.postCustomerReceipt(
                partyId = agentParty.id,
                treasuryId = "TR_MAIN_YER",
                fiscalYear = 2026,
                dateEpochDay = today,
                amountOrigMinor = 3000000L,
                currency = CurrencyCode.YER,
                exchangeRate = yerRate,
                allocations = listOf(InvoiceAllocationSpec(inv.id, 3000000L)),
                notes = "دفعة نقدية تحت حساب الفاتورة"
            )

            // 4. Post Upstream Starlink ISP direct expense: $250 USD @ 530 YER/USD
            repository.ledgerWriter.postPaymentVoucher(
                recipientPartyId = AppDatabase.WALK_IN_CASH_PARTY_ID,
                treasuryId = "TR_USD_VAULT",
                fiscalYear = 2026,
                dateEpochDay = today,
                amountOrigMinor = 25000L, // $250.00
                currency = CurrencyCode.USD,
                exchangeRate = usdRate,
                paymentType = PaymentVoucherType.DIRECT_ISP_SERVICE,
                notes = "سداد اشتراك Starlink الشهري لمحطة التوزيع"
            )

            // 5. Post Fixed Network Equipment purchase: 1 MikroTik CCR router $600 USD
            val vendorParty = PartyEntity(
                id = "PARTY_TECH_SUPPLY",
                name = "شركة تكنو للشبكات",
                phone = "771998877",
                isVendor = true
            )
            repository.insertParty(vendorParty)

            repository.ledgerWriter.postPurchaseInvoice(
                vendorPartyId = vendorParty.id,
                fiscalYear = 2026,
                dateEpochDay = today,
                currency = CurrencyCode.USD,
                exchangeRate = usdRate,
                items = listOf(
                    PurchaseItemSpec(
                        description = "راوتر MikroTik CCR2004",
                        accountCode = AccountConstants.FIXED_ASSETS_NETWORK,
                        quantity = 1,
                        unitPriceMinor = 60000L,
                        isAsset = true,
                        usefulLifeMonths = 24
                    )
                ),
                notes = "شراء راوتر التوزيع الرئيسي"
            )

            refreshAll()
        }
    }
}
