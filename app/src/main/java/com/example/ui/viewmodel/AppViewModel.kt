package com.example.ui.viewmodel

import android.app.Activity
import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.tasks.await
import com.example.core.ledger.AccountConstants
import com.example.core.model.CurrencyCode
import com.example.core.model.ExchangeRate
import com.example.core.model.Money
import com.example.data.auth.AuthManager
import com.example.data.auth.GoogleAuthManager
import com.example.data.auth.UserProfile
import com.example.data.auth.UserSession
import com.example.data.sync.FirebaseSyncManager
import com.example.data.sync.FirestoreSyncManager
import com.example.data.sync.SyncLogItem
import com.example.data.sync.SyncMetadata
import com.example.data.sync.SyncState
import com.example.data.sync.SyncStatus
import com.example.data.ledger.InvariantCheckResult
import com.example.data.ledger.InvoiceAllocationSpec
import com.example.data.ledger.PaymentVoucherType
import com.example.data.ledger.PurchaseItemSpec
import com.example.data.ledger.SalesItemSpec
import com.example.data.local.AppDatabase
import com.example.data.local.dao.AccountBalanceRow
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.AllocationEntity
import com.example.data.local.entity.AssetEntity
import com.example.data.local.entity.CardPackageEntity
import com.example.data.local.entity.CurrencyRateEntity
import com.example.data.local.entity.DocumentEntity
import com.example.data.local.entity.DocumentItemEntity
import com.example.data.local.entity.FiscalPeriodEntity
import com.example.data.local.entity.PartyEntity
import com.example.data.local.entity.StockMovementEntity
import com.example.data.local.entity.TreasuryAccountEntity
import com.example.data.repository.AccountingRepository
import com.example.data.network.NetworkRepository
import com.example.util.DataJsonHelper
import com.example.core.model.UuidUtils
import com.example.core.model.RateZone
import com.example.core.model.SignificantRateChangeException
import com.example.data.local.entity.OrganizationEntity
import com.example.domain.usecase.ExchangeRateResolver
import com.example.domain.usecase.AgingReport
import com.example.domain.usecase.BackupRestoreUseCase
import com.example.domain.usecase.BalanceSheetReport
import com.example.domain.usecase.BatchImportUseCase
import com.example.domain.usecase.FinancialStatementsUseCase
import com.example.domain.usecase.ImportBatchReport
import com.example.domain.usecase.IncomeStatementReport
import com.example.domain.usecase.StatementOfAccountReport
import com.example.domain.usecase.StatementOfAccountUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardSummary(
    val totalSalesPeriodMinor: Long = 0L,
    val totalReceiptsPeriodMinor: Long = 0L,
    val totalExpensesPeriodMinor: Long = 0L,
    val netCashFlowMinor: Long = 0L,
    val totalReceivablesMinor: Long = 0L,
    val totalPayablesMinor: Long = 0L,
    val treasuryBalancesByCurrency: Map<String, Long> = emptyMap(),
    val openInvoiceCount: Int = 0,
    val isBalanced: Boolean = true
)

enum class PeriodFilter(val title: String) {
    TODAY("اليوم"),
    THIS_WEEK("هذا الأسبوع"),
    THIS_MONTH("هذا الشهر"),
    ALL_TIME("الكل")
}

class AppViewModel(application: Application) : AndroidViewModel(application) {
    val db = AppDatabase.getInstance(application)
    val repository = AccountingRepository(db)
    val writer = repository.ledgerWriter
    val invariants = repository.invariants
    val networkRepository = NetworkRepository(application)

    val statementsUseCase = FinancialStatementsUseCase(db)
    val statementOfAccountUseCase = StatementOfAccountUseCase(db)
    val backupRestoreUseCase = BackupRestoreUseCase(
        db = db,
        deviceDao = networkRepository,
        networkRepository = networkRepository,
        context = application
    )
    val batchImportUseCase = BatchImportUseCase(db, writer)
    val exchangeRateResolver = ExchangeRateResolver(db)
    val partnerEquityUseCase = com.example.domain.usecase.PartnerEquityUseCase(db, writer)
    val periodicRevaluationUseCase = com.example.domain.usecase.PeriodicRevaluationUseCase(db, writer, exchangeRateResolver)

    // Organization & Exchange Rates
    val organization: StateFlow<OrganizationEntity?> = db.organizationDao().getOrganizationFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allRates: StateFlow<List<CurrencyRateEntity>> = db.currencyRateDao().getAllRatesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Auth & Firebase Sync
    val authManager = AuthManager(application)
    val syncManager = FirestoreSyncManager(
        context = application,
        db = db,
        backupRestoreUseCase = backupRestoreUseCase,
        networkRepository = networkRepository
    )
    val googleAuthManager = GoogleAuthManager(application)
    val firebaseSyncManager = FirebaseSyncManager(
        context = application,
        db = db,
        backupRestoreUseCase = backupRestoreUseCase,
        networkRepository = networkRepository
    )

    val currentUser: StateFlow<UserSession?> = authManager.currentUser
    val authError: StateFlow<String?> = authManager.authError
    val isAuthLoading: StateFlow<Boolean> = authManager.isLoading

    val currentUserProfile: StateFlow<UserProfile?> = googleAuthManager.userProfile
    val syncStatus: StateFlow<SyncStatus> = firebaseSyncManager.syncStatus
    val lastSyncTimestamp: StateFlow<String?> = firebaseSyncManager.lastSyncTimestamp
    val autoSyncEnabled: StateFlow<Boolean> = firebaseSyncManager.autoSyncEnabled

    val syncState: StateFlow<SyncState> = syncManager.syncState
    val syncMetadata: StateFlow<SyncMetadata?> = syncManager.syncMetadata
    val syncHistory: StateFlow<List<SyncLogItem>> = syncManager.syncHistory

    // Base Flows
    val allParties: StateFlow<List<PartyEntity>> = repository.allParties
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPartners: StateFlow<List<PartyEntity>> = db.partyDao().getPartnersFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPackages: StateFlow<List<CardPackageEntity>> = repository.allPackages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTreasuries: StateFlow<List<TreasuryAccountEntity>> = repository.allTreasuries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allDocuments: StateFlow<List<DocumentEntity>> = repository.allDocuments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allAllocations: StateFlow<List<AllocationEntity>> = db.allocationDao().getAllActiveAllocationsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allAssets: StateFlow<List<AssetEntity>> = repository.allAssets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPeriods: StateFlow<List<FiscalPeriodEntity>> = db.fiscalPeriodDao().getAllPeriodsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allStockMovements: StateFlow<List<StockMovementEntity>> = db.cardPackageDao().getAllStockMovementsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trialBalance: StateFlow<List<AccountBalanceRow>> = repository.trialBalance
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allAccounts: StateFlow<List<AccountEntity>> = repository.allAccounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Dashboard State
    val selectedPeriodFilter = MutableStateFlow(PeriodFilter.THIS_MONTH)

    private val _dashboardSummary = MutableStateFlow(DashboardSummary())
    val dashboardSummary: StateFlow<DashboardSummary> = _dashboardSummary.asStateFlow()

    // Generated Reports State
    private val _incomeStatement = MutableStateFlow<IncomeStatementReport?>(null)
    val incomeStatement: StateFlow<IncomeStatementReport?> = _incomeStatement.asStateFlow()

    private val _balanceSheet = MutableStateFlow<BalanceSheetReport?>(null)
    val balanceSheet: StateFlow<BalanceSheetReport?> = _balanceSheet.asStateFlow()

    private val _agingReport = MutableStateFlow<AgingReport?>(null)
    val agingReport: StateFlow<AgingReport?> = _agingReport.asStateFlow()

    private val _currentPartyStatement = MutableStateFlow<StatementOfAccountReport?>(null)
    val currentPartyStatement: StateFlow<StatementOfAccountReport?> = _currentPartyStatement.asStateFlow()

    private val _invariantResult = MutableStateFlow<InvariantCheckResult?>(null)
    val invariantResult: StateFlow<InvariantCheckResult?> = _invariantResult.asStateFlow()

    // UI Feedback Messages
    private val _userMessage = MutableSharedFlow<String>()
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()

    // Prompt for restoring remote backup on empty/fresh app
    val pendingCloudRestorePrompt = MutableStateFlow<com.example.data.sync.CloudBackupMeta?>(null)

    init {
        refreshDashboard()
        runInvariantCheck()
        checkForRemoteBackupIfEmpty()
    }

    fun checkForRemoteBackupIfEmpty() {
        viewModelScope.launch {
            val user = currentUser.value
            if (user != null && user.isSignedIn) {
                val docCount = db.documentDao().getAllDocumentsSync().size
                val linesCount = db.journalDao().getAllLinesSync().size
                if (docCount == 0 && linesCount == 0) {
                    val meta = syncManager.checkRemoteBackup(user.uid, user.email)
                    if (meta != null && meta.totalRecords > 0) {
                        pendingCloudRestorePrompt.value = meta
                    }
                }
            }
        }
    }

    fun dismissCloudRestorePrompt() {
        pendingCloudRestorePrompt.value = null
    }

    fun restorePendingCloudBackup() {
        val prompt = pendingCloudRestorePrompt.value ?: return
        pendingCloudRestorePrompt.value = null
        restoreFromCloud(prompt.uid, prompt.userEmail)
    }

    fun refreshDashboard() {
        viewModelScope.launch {
            val todayEpoch = System.currentTimeMillis() / 86400000L
            val filter = selectedPeriodFilter.value

            val startEpoch: Long? = when (filter) {
                PeriodFilter.TODAY -> todayEpoch
                PeriodFilter.THIS_WEEK -> todayEpoch - 7
                PeriodFilter.THIS_MONTH -> todayEpoch - 30
                PeriodFilter.ALL_TIME -> null
            }

            val income = statementsUseCase.generateIncomeStatement(startEpoch, todayEpoch)
            val receivables = repository.getNetBalanceForAccount(AccountConstants.ACCOUNTS_RECEIVABLE)
            val payables = -repository.getNetBalanceForAccount(AccountConstants.ACCOUNTS_PAYABLE)

            // Treasury balances
            val treasuries = db.treasuryDao().getAllTreasuriesSync()
            val trMap = mutableMapOf<String, Long>()
            treasuries.forEach { tr ->
                val bal = repository.getTreasuryBalance(tr.id)
                trMap[tr.currency] = (trMap[tr.currency] ?: 0L) + bal
            }

            // Invariant check
            val invRes = repository.runInvariantCheck()
            _invariantResult.value = invRes

            _dashboardSummary.value = DashboardSummary(
                totalSalesPeriodMinor = income.netRevenueMinor,
                totalReceiptsPeriodMinor = 0L, // Derived
                totalExpensesPeriodMinor = income.directIspCostMinor + income.totalOperatingExpensesMinor,
                netCashFlowMinor = income.netProfitMinor,
                totalReceivablesMinor = receivables,
                totalPayablesMinor = payables,
                treasuryBalancesByCurrency = trMap,
                openInvoiceCount = 0,
                isBalanced = invRes.isValid
            )
        }
    }

    fun setPeriodFilter(filter: PeriodFilter) {
        selectedPeriodFilter.value = filter
        refreshDashboard()
    }

    fun runInvariantCheck() {
        viewModelScope.launch {
            val result = repository.runInvariantCheck()
            _invariantResult.value = result
        }
    }

    // --- Action Methods ---

    private fun getLocalNow(): java.time.LocalDate {
        return java.time.LocalDate.now(java.time.ZoneId.systemDefault())
    }

    fun postQuickSale(
        partyId: String,
        packageId: String?,
        description: String,
        quantity: Int,
        unitPriceMinor: Long,
        cashPaidMinor: Long,
        treasuryId: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                val now = getLocalNow()
                val fiscalYear = now.year
                val today = now.toEpochDay()
                writer.postQuickSale(
                    partyId = partyId,
                    packageId = packageId,
                    description = description,
                    quantity = quantity,
                    unitPriceMinor = unitPriceMinor,
                    cashPaidMinor = cashPaidMinor,
                    treasuryId = treasuryId,
                    fiscalYear = fiscalYear,
                    dateEpochDay = today
                )
                _userMessage.emit("تم تسجيل البيع السريع وترحيله بنجاح")
                refreshDashboard()
                onSuccess()
            } catch (e: Exception) {
                _userMessage.emit("خطأ في البيع السريع: ${e.message}")
            }
        }
    }

    fun postSalesInvoice(
        partyId: String,
        cardItems: List<SalesItemSpec>,
        serviceItems: List<SalesItemSpec>,
        currency: CurrencyCode,
        exchangeRate: ExchangeRate,
        notes: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                val now = getLocalNow()
                val fiscalYear = now.year
                val today = now.toEpochDay()
                writer.postSalesInvoice(
                    partyId = partyId,
                    fiscalYear = fiscalYear,
                    dateEpochDay = today,
                    currency = currency,
                    exchangeRate = exchangeRate,
                    cardItems = cardItems,
                    serviceItems = serviceItems,
                    notes = notes
                )
                _userMessage.emit("تم ترحيل فاتورة المبيعات بنجاح")
                refreshDashboard()
                onSuccess()
            } catch (e: Exception) {
                _userMessage.emit("فشل إصدار الفاتورة: ${e.message}")
            }
        }
    }

    fun postSalesInvoiceWithSettlement(
        partyId: String,
        cardItems: List<SalesItemSpec>,
        serviceItems: List<SalesItemSpec> = emptyList(),
        currency: CurrencyCode = CurrencyCode.YER,
        exchangeRate: ExchangeRate = ExchangeRate.parity(CurrencyCode.YER),
        cashPaidMinor: Long = 0L,
        treasuryId: String = "TR_MAIN_YER",
        notes: String = "",
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                val now = getLocalNow()
                val fiscalYear = now.year
                val today = now.toEpochDay()
                val doc = writer.postSalesInvoice(
                    partyId = partyId,
                    fiscalYear = fiscalYear,
                    dateEpochDay = today,
                    currency = currency,
                    exchangeRate = exchangeRate,
                    cardItems = cardItems,
                    serviceItems = serviceItems,
                    notes = notes
                )
                if (cashPaidMinor > 0L) {
                    val allocAmount = minOf(cashPaidMinor, doc.totalMinor)
                    writer.postCustomerReceipt(
                        partyId = partyId,
                        treasuryId = treasuryId,
                        fiscalYear = fiscalYear,
                        dateEpochDay = today,
                        amountOrigMinor = cashPaidMinor,
                        currency = currency,
                        exchangeRate = exchangeRate,
                        allocations = listOf(InvoiceAllocationSpec(doc.id, allocAmount)),
                        notes = "سند قبض سداد/دفعة مقدمة لفاتورة مبيعات #${doc.docNumber}"
                    )
                }
                _userMessage.emit("تم إصدار الفاتورة وخصم المخزن بنجاح")
                refreshDashboard()
                onSuccess()
            } catch (e: Exception) {
                _userMessage.emit("فشل إصدار الفاتورة: ${e.message}")
            }
        }
    }

    fun getCustomerReceivableBalances(onResult: (Map<String, Long>) -> Unit) {
        viewModelScope.launch {
            try {
                val rows = db.journalDao().getPartyBalancesForControlAccount(AccountConstants.ACCOUNTS_RECEIVABLE)
                val map = rows.associate { it.partyId to (it.totalDebitMinor - it.totalCreditMinor) }
                onResult(map)
            } catch (e: Exception) {
                onResult(emptyMap())
            }
        }
    }

    fun postCustomerReceipt(
        partyId: String,
        treasuryId: String,
        amountOrigMinor: Long,
        currency: CurrencyCode,
        exchangeRate: ExchangeRate,
        allocations: List<InvoiceAllocationSpec>,
        notes: String,
        rateZone: RateZone = RateZone.DEFAULT,
        rateSource: com.example.core.model.RateSource = com.example.core.model.RateSource.SYSTEM_DAILY,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                val now = getLocalNow()
                val fiscalYear = now.year
                val today = now.toEpochDay()
                writer.postCustomerReceipt(
                    partyId = partyId,
                    treasuryId = treasuryId,
                    fiscalYear = fiscalYear,
                    dateEpochDay = today,
                    amountOrigMinor = amountOrigMinor,
                    currency = currency,
                    exchangeRate = exchangeRate,
                    allocations = allocations,
                    notes = notes,
                    rateZone = rateZone,
                    rateSource = rateSource
                )
                _userMessage.emit("تم ترحيل سند القبض وتخصيصه بنجاح")
                refreshDashboard()
                onSuccess()
            } catch (e: Exception) {
                _userMessage.emit("فشل تسجيل سند القبض: ${e.message}")
            }
        }
    }

    fun postPaymentVoucher(
        recipientPartyId: String,
        treasuryId: String,
        amountOrigMinor: Long,
        currency: CurrencyCode,
        exchangeRate: ExchangeRate,
        paymentType: PaymentVoucherType,
        customExpenseCode: String? = null,
        invoiceAllocations: List<InvoiceAllocationSpec> = emptyList(),
        notes: String,
        rateZone: RateZone = RateZone.DEFAULT,
        rateSource: com.example.core.model.RateSource = com.example.core.model.RateSource.SYSTEM_DAILY,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                val now = getLocalNow()
                val fiscalYear = now.year
                val today = now.toEpochDay()
                writer.postPaymentVoucher(
                    recipientPartyId = recipientPartyId,
                    treasuryId = treasuryId,
                    fiscalYear = fiscalYear,
                    dateEpochDay = today,
                    amountOrigMinor = amountOrigMinor,
                    currency = currency,
                    exchangeRate = exchangeRate,
                    paymentType = paymentType,
                    customExpenseCode = customExpenseCode,
                    invoiceAllocations = invoiceAllocations,
                    notes = notes,
                    rateZone = rateZone,
                    rateSource = rateSource
                )
                _userMessage.emit("تم ترحيل سند الصرف بنجاح")
                refreshDashboard()
                onSuccess()
            } catch (e: Exception) {
                _userMessage.emit("فشل تسجيل سند الصرف: ${e.message}")
            }
        }
    }

    fun postCapitalReceipt(
        partnerPartyId: String,
        treasuryId: String,
        amountOrigMinor: Long,
        currency: CurrencyCode,
        exchangeRate: ExchangeRate,
        notes: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                val now = java.time.LocalDate.now()
                writer.postCapitalReceipt(
                    targetAccountCode = AccountConstants.CAPITAL,
                    partnerPartyId = partnerPartyId,
                    treasuryId = treasuryId,
                    fiscalYear = now.year,
                    dateEpochDay = now.toEpochDay(),
                    amountOrigMinor = amountOrigMinor,
                    currency = currency,
                    exchangeRate = exchangeRate,
                    notes = notes
                )
                _userMessage.emit("تم تسجيل سند مساهمة رأس المال بنجاح")
                refreshDashboard()
                onSuccess()
            } catch (e: Exception) {
                _userMessage.emit("فشل تسجيل سند المساهمة: ${e.message}")
            }
        }
    }

    fun postInKindCapitalContribution(
        partnerPartyId: String,
        assetName: String,
        amountOrigMinor: Long,
        currency: CurrencyCode,
        exchangeRate: ExchangeRate,
        usefulLifeMonths: Int,
        notes: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                val now = java.time.LocalDate.now()
                writer.postInKindCapitalContribution(
                    partnerPartyId = partnerPartyId,
                    assetName = assetName,
                    fiscalYear = now.year,
                    dateEpochDay = now.toEpochDay(),
                    amountOrigMinor = amountOrigMinor,
                    currency = currency,
                    exchangeRate = exchangeRate,
                    usefulLifeMonths = usefulLifeMonths,
                    notes = notes
                )
                _userMessage.emit("تم ترحيل المساهمة العينية وقيد الأصل الثابت بنجاح")
                refreshDashboard()
                onSuccess()
            } catch (e: Exception) {
                _userMessage.emit("فشل تسجيل المساهمة العينية: ${e.message}")
            }
        }
    }

    fun getPartnerCapitalBalanceFlow(partnerId: String): Flow<Long> {
        return db.journalDao().getPartnerCapitalBalanceFlow(partnerId)
    }

    fun getPartnerCurrentBalanceFlow(partnerId: String): Flow<Long> {
        return db.journalDao().getPartnerCurrentBalanceFlow(partnerId)
    }

    fun getTotalCapitalBalanceFlow(): Flow<Long> {
        return db.journalDao().getTotalCapitalBalanceFlow()
    }

    fun setEquityShareMode(mode: com.example.core.model.EquityShareMode) {
        viewModelScope.launch {
            try {
                partnerEquityUseCase.setEquityShareMode(mode)
                _userMessage.emit("تم تحديث نمط احتساب حصص الملكية إلى: ${mode.title}")
            } catch (e: Exception) {
                _userMessage.emit("فشل تحديث نمط احتساب الحصص: ${e.message}")
            }
        }
    }

    fun executeProfitDistribution(
        fiscalYear: Int,
        totalProfitMinor: Long,
        notes: String = "",
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                val today = getLocalNow().toEpochDay()
                val doc = partnerEquityUseCase.executeProfitDistribution(
                    fiscalYear = fiscalYear,
                    dateEpochDay = today,
                    totalProfitMinor = totalProfitMinor,
                    notes = notes
                )
                _userMessage.emit("تم توزيع الأرباح بنجاح بسند رقم #${doc.docNumber} بدون أي فواقد رياضية")
                refreshDashboard()
                onSuccess()
            } catch (e: Exception) {
                _userMessage.emit("تعذر توزيع الأرباح: ${e.message}")
            }
        }
    }

    fun getDocumentsByPartyFlow(partyId: String): Flow<List<DocumentEntity>> {
        return db.documentDao().getDocumentsByPartyFlow(partyId)
    }

    fun runPeriodicRevaluation(
        fiscalYear: Int = getLocalNow().year,
        dateEpochDay: Long = getLocalNow().toEpochDay(),
        rateZone: RateZone = RateZone.SANAA,
        notes: String? = null
    ) {
        viewModelScope.launch {
            try {
                val docs = periodicRevaluationUseCase.executePeriodicRevaluationRun(
                    fiscalYear = fiscalYear,
                    dateEpochDay = dateEpochDay,
                    rateZone = rateZone,
                    notes = notes
                )
                _userMessage.emit("تم تنفيذ إعادة التقييم الدوري بنجاح (${docs.size} قيد تم ترحيله)")
                refreshDashboard()
            } catch (e: Exception) {
                _userMessage.emit("فشل إعادة التقييم الدوري: ${e.message}")
            }
        }
    }

    fun postPurchaseInvoice(
        vendorPartyId: String,
        currency: CurrencyCode,
        exchangeRate: ExchangeRate,
        items: List<PurchaseItemSpec>,
        notes: String,
        rateZone: RateZone = RateZone.DEFAULT,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                val now = getLocalNow()
                val fiscalYear = now.year
                val today = now.toEpochDay()
                writer.postPurchaseInvoice(
                    vendorPartyId = vendorPartyId,
                    fiscalYear = fiscalYear,
                    dateEpochDay = today,
                    currency = currency,
                    exchangeRate = exchangeRate,
                    items = items,
                    notes = notes,
                    rateZone = rateZone
                )
                _userMessage.emit("تم ترحيل فاتورة المشتريات وتسجيل الأصول إن وُجدت")
                refreshDashboard()
                onSuccess()
            } catch (e: Exception) {
                _userMessage.emit("فشل تسجيل فاتورة المشتريات: ${e.message}")
            }
        }
    }

    fun addExchangeRate(
        currency: CurrencyCode,
        zone: RateZone,
        rateMicros: Long,
        effectiveDateEpochDay: Long,
        createdBy: String = "USER",
        reason: String = "",
        confirmSignificantChange: Boolean = false,
        onSuccess: () -> Unit = {},
        onError: (Throwable) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                exchangeRateResolver.addRate(
                    currency = currency,
                    zone = zone,
                    rateMicros = rateMicros,
                    effectiveDateEpochDay = effectiveDateEpochDay,
                    createdBy = createdBy,
                    reason = reason,
                    confirmSignificantChange = confirmSignificantChange
                )
                _userMessage.emit("تم إضافة وتحديث سعر الصرف بنجاح")
                onSuccess()
            } catch (e: Throwable) {
                onError(e)
                if (e !is SignificantRateChangeException) {
                    _userMessage.emit("خطأ في تحديث سعر الصرف: ${e.message}")
                }
            }
        }
    }

    fun postPurchaseInvoiceWithSettlement(
        vendorName: String,
        isCash: Boolean,
        treasuryId: String?,
        currency: CurrencyCode,
        exchangeRate: ExchangeRate,
        items: List<PurchaseItemSpec>,
        notes: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                val now = getLocalNow()
                val fiscalYear = now.year
                val today = now.toEpochDay()

                val allVendors = db.partyDao().getAllPartiesSync()
                var vendor = allVendors.find { it.name.trim().equals(vendorName.trim(), ignoreCase = true) }
                if (vendor == null) {
                    val newVendor = PartyEntity(
                        id = UuidUtils.newTimeOrderedId(),
                        name = vendorName.trim().ifBlank { "مورد عام" },
                        isVendor = true
                    )
                    db.partyDao().insertParty(newVendor)
                    vendor = newVendor
                }

                val doc = writer.postPurchaseInvoice(
                    vendorPartyId = vendor.id,
                    fiscalYear = fiscalYear,
                    dateEpochDay = today,
                    currency = currency,
                    exchangeRate = exchangeRate,
                    items = items,
                    notes = notes
                )

                if (isCash && !treasuryId.isNullOrBlank()) {
                    val totalOrig = items.sumOf { it.totalMinor }
                    writer.postPaymentVoucher(
                        recipientPartyId = vendor.id,
                        treasuryId = treasuryId,
                        fiscalYear = fiscalYear,
                        dateEpochDay = today,
                        amountOrigMinor = totalOrig,
                        currency = currency,
                        exchangeRate = exchangeRate,
                        paymentType = PaymentVoucherType.VENDOR_SETTLEMENT,
                        invoiceAllocations = listOf(InvoiceAllocationSpec(invoiceDocId = doc.id, allocatedOrigMinor = totalOrig)),
                        notes = "سداد نقدي فوري لفاتورة المشتريات #${doc.docNumber}"
                    )
                    _userMessage.emit("تم ترحيل فاتورة المشتريات #${doc.docNumber} وسدادها نقداً بنجاح")
                } else {
                    _userMessage.emit("تم ترحيل فاتورة المشتريات الآجلة #${doc.docNumber} في حساب المورد '${vendor.name}' بنجاح")
                }

                refreshDashboard()
                onSuccess()
            } catch (e: Exception) {
                _userMessage.emit("فشل تسجيل فاتورة المشتريات: ${e.message}")
            }
        }
    }

    fun postCreditNote(
        partyId: String,
        amountOrigMinor: Long,
        currency: CurrencyCode,
        exchangeRate: ExchangeRate,
        returnPackageId: String?,
        returnQty: Int,
        notes: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                val now = getLocalNow()
                val fiscalYear = now.year
                val today = now.toEpochDay()
                writer.postCreditNote(
                    partyId = partyId,
                    fiscalYear = fiscalYear,
                    dateEpochDay = today,
                    currency = currency,
                    exchangeRate = exchangeRate,
                    amountOrigMinor = amountOrigMinor,
                    returnPackageId = returnPackageId,
                    returnQty = returnQty,
                    notes = notes
                )
                _userMessage.emit("تم ترحيل الإشعار الدائن وإعادة الكميات إن وُجدت")
                refreshDashboard()
                onSuccess()
            } catch (e: Exception) {
                _userMessage.emit("فشل ترحيل الإشعار الدائن: ${e.message}")
            }
        }
    }

    fun postTreasuryTransfer(
        sourceTreasuryId: String,
        sourceAmountOrigMinor: Long,
        sourceCurrency: CurrencyCode,
        sourceRate: ExchangeRate,
        destTreasuryId: String,
        destAmountOrigMinor: Long,
        destCurrency: CurrencyCode,
        destRate: ExchangeRate,
        notes: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                val now = getLocalNow()
                val fiscalYear = now.year
                val today = now.toEpochDay()
                writer.postTreasuryTransfer(
                    sourceTreasuryId = sourceTreasuryId,
                    sourceAmountOrigMinor = sourceAmountOrigMinor,
                    sourceCurrency = sourceCurrency,
                    sourceRate = sourceRate,
                    destTreasuryId = destTreasuryId,
                    destAmountOrigMinor = destAmountOrigMinor,
                    destCurrency = destCurrency,
                    destRate = destRate,
                    fiscalYear = fiscalYear,
                    dateEpochDay = today,
                    notes = notes
                )
                _userMessage.emit("تم التحويل بين الخزائن وتسجيل فروق الصرف بنجاح")
                refreshDashboard()
                onSuccess()
            } catch (e: Exception) {
                _userMessage.emit("فشل التحويل: ${e.message}")
            }
        }
    }

    fun postCurrencyExchange(
        sourceTreasuryId: String,
        sourceAmountOrigMinor: Long,
        destTreasuryId: String,
        destAmountOrigMinor: Long,
        notes: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                val now = getLocalNow()
                val fiscalYear = now.year
                val today = now.toEpochDay()
                writer.postCurrencyExchange(
                    sourceTreasuryId = sourceTreasuryId,
                    sourceAmountOrigMinor = sourceAmountOrigMinor,
                    destTreasuryId = destTreasuryId,
                    destAmountOrigMinor = destAmountOrigMinor,
                    fiscalYear = fiscalYear,
                    dateEpochDay = today,
                    notes = notes
                )
                _userMessage.emit("تمت مصارفة العملات بنجاح")
                refreshDashboard()
                onSuccess()
            } catch (e: Exception) {
                _userMessage.emit("فشلت المصارفة: ${e.message}")
            }
        }
    }

    fun runDepreciation(assetId: String, year: Int, month: Int) {
        viewModelScope.launch {
            try {
                val today = getLocalNow().toEpochDay()
                val success = writer.runMonthlyDepreciation(assetId, year, month, today)
                if (success) {
                    _userMessage.emit("تم ترحيل قيد الإهلاك الشهري للأصل بنجاح")
                } else {
                    _userMessage.emit("تنبيه: تم إهلاك هذا الأصل مسبقاً لهذه الفترة أو تم استبعاده بالكامل")
                }
                refreshDashboard()
            } catch (e: Exception) {
                _userMessage.emit("فشل الإهلاك: ${e.message}")
            }
        }
    }

    fun disposeAsset(
        assetId: String,
        salvageProceedsMinor: Long,
        treasuryId: String?,
        notes: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                val today = getLocalNow().toEpochDay()
                writer.disposeAsset(
                    assetId = assetId,
                    disposalDateEpochDay = today,
                    salvageProceedsMinor = salvageProceedsMinor,
                    treasuryId = treasuryId,
                    notes = notes
                )
                _userMessage.emit("تم استبعاد الأصل وترحيل قيود التخريد والأرباح/الخسائر الرأسمالية بنجاح")
                refreshDashboard()
                onSuccess()
            } catch (e: Exception) {
                _userMessage.emit("فشل استبعاد الأصل: ${e.message}")
            }
        }
    }

    fun voidDocument(docId: String, reason: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                val today = getLocalNow().toEpochDay()
                val success = writer.voidDocument(docId, today, reason)
                if (success) {
                    _userMessage.emit("تم إلغاء المستند وإدراج قيد عكسي تعويضي بنجاح")
                    refreshDashboard()
                    onSuccess()
                } else {
                    _userMessage.emit("المستند ملغي مسبقاً")
                }
            } catch (e: Exception) {
                _userMessage.emit("فشل الإلغاء: ${e.message}")
            }
        }
    }

    fun getDocumentItems(docId: String, onResult: (List<DocumentItemEntity>) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val items = db.documentDao().getItemsForDocument(docId)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                onResult(items)
            }
        }
    }

    fun receiveCardStock(packageId: String, quantity: Int, notes: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                val today = getLocalNow().toEpochDay()
                writer.receiveCardStock(packageId, quantity, today, notes)
                _userMessage.emit("تم تسجيل استلام دفعة الكروت وزيادة الرصيد")
                onSuccess()
            } catch (e: Exception) {
                _userMessage.emit("فشل استلام الكروت: ${e.message}")
            }
        }
    }

    fun adjustCardStock(packageId: String, adjustmentQty: Int, reason: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                val today = getLocalNow().toEpochDay()
                writer.adjustCardStock(packageId, adjustmentQty, today, reason)
                _userMessage.emit("تم تسجيل تسوية رصيد الكروت")
                onSuccess()
            } catch (e: Exception) {
                _userMessage.emit("فشل تسوية الرصيد: ${e.message}")
            }
        }
    }

    fun reconcileTreasuryCash(treasuryId: String, actualCountMinor: Long, notes: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                val today = System.currentTimeMillis() / 86400000L
                val doc = writer.reconcileTreasuryCash(treasuryId, actualCountMinor, 2026, today, notes)
                if (doc != null) {
                    _userMessage.emit("تم تسجيل قيد تسوية فارق جرد الصندوق بنجاح")
                } else {
                    _userMessage.emit("الرصيد الفعلي مطابق تماماً للرصيد الدفتري، لا يلزم قيد")
                }
                refreshDashboard()
                onSuccess()
            } catch (e: Exception) {
                _userMessage.emit("فشل تسوية جرد الخزينة: ${e.message}")
            }
        }
    }

    fun executeYearEndClosing(fiscalYear: Int, memo: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                val closingDate = 20365L // End of year
                writer.executeYearEndClosing(fiscalYear, closingDate, memo)
                _userMessage.emit("تم الإقفال السنوي وترحيل الأرباح والخسائر لحساب الأرباح المرحّلة 3301")
                refreshDashboard()
                onSuccess()
            } catch (e: Exception) {
                _userMessage.emit("فشل الإقفال السنوي: ${e.message}")
            }
        }
    }

    fun setPeriodClosed(year: Int, month: Int, isClosed: Boolean) {
        viewModelScope.launch {
            try {
                db.fiscalPeriodDao().setPeriodClosed(year, month, isClosed, if (isClosed) System.currentTimeMillis() else null)
                _userMessage.emit("تم ${if (isClosed) "قفل" else "فتح"} الفترة المالية بنجاح")
            } catch (e: Exception) {
                _userMessage.emit("فشل تعديل حالة الفترة: ${e.message}")
            }
        }
    }

    fun loadStatementOfAccount(partyId: String, controlAccountCode: String, startEpoch: Long?, endEpoch: Long?) {
        viewModelScope.launch {
            try {
                val report = statementOfAccountUseCase.generateStatement(partyId, controlAccountCode, startEpoch, endEpoch)
                _currentPartyStatement.value = report
            } catch (e: Exception) {
                _userMessage.emit("فشل توليد كشف الحساب: ${e.message}")
            }
        }
    }

    fun loadIncomeStatement(startEpoch: Long?, endEpoch: Long?) {
        viewModelScope.launch {
            val rep = statementsUseCase.generateIncomeStatement(startEpoch, endEpoch)
            _incomeStatement.value = rep
        }
    }

    fun loadBalanceSheet(asOfDateEpoch: Long) {
        viewModelScope.launch {
            val rep = statementsUseCase.generateBalanceSheet(asOfDateEpoch)
            _balanceSheet.value = rep
        }
    }

    fun loadAgingReport(asOfDateEpoch: Long) {
        viewModelScope.launch {
            val rep = statementsUseCase.generateAgingReport(asOfDateEpoch)
            _agingReport.value = rep
        }
    }

    fun insertParty(party: PartyEntity, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                repository.insertParty(party)
                _userMessage.emit("تمت إضافة الطرف بنجاح")
                onSuccess()
            } catch (e: Exception) {
                _userMessage.emit("فشل إضافة الطرف: ${e.message}")
            }
        }
    }

    fun updateParty(party: PartyEntity, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                repository.updateParty(party)
                _userMessage.emit("تم تحديث بيانات الطرف بنجاح")
                onSuccess()
            } catch (e: Exception) {
                _userMessage.emit("فشل تحديث بيانات الطرف: ${e.message}")
            }
        }
    }

    fun setPartyActive(partyId: String, isActive: Boolean) {
        viewModelScope.launch {
            db.partyDao().setPartyActive(partyId, isActive)
            _userMessage.emit(if (isActive) "تم تنشيط الطرف" else "تمت أرشفة الطرف")
        }
    }

    fun insertPackage(pkg: CardPackageEntity, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                repository.insertPackage(pkg)
                _userMessage.emit("تم حفظ الباقة بنجاح")
                onSuccess()
            } catch (e: Exception) {
                _userMessage.emit("فشل حفظ الباقة: ${e.message}")
            }
        }
    }

    fun setPackageActive(packageId: String, isActive: Boolean) {
        viewModelScope.launch {
            db.cardPackageDao().setPackageActive(packageId, isActive)
            _userMessage.emit(if (isActive) "تم تنشيط الباقة" else "تمت أرشفة الباقة")
        }
    }

    fun insertTreasury(treasury: TreasuryAccountEntity, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                repository.insertTreasury(treasury)
                _userMessage.emit("تمت إضافة الخزينة بنجاح")
                onSuccess()
            } catch (e: Exception) {
                _userMessage.emit("فشل إضافة الخزينة: ${e.message}")
            }
        }
    }

    fun exportBackup(onExported: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val json = backupRestoreUseCase.exportDatabaseToJson()
                onExported(json)
                _userMessage.emit("تم تصدير النسخة الاحتياطية بنجاح")
            } catch (e: Exception) {
                _userMessage.emit("فشل تصدير النسخة: ${e.message}")
            }
        }
    }

    fun restoreBackup(json: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                val res = backupRestoreUseCase.restoreDatabaseFromJson(json)
                if (res.isSuccess) {
                    _userMessage.emit(res.getOrNull() ?: "تمت الاستعادة بنجاح")
                    refreshDashboard()
                    onComplete()
                } else {
                    _userMessage.emit("فشل استعادة النسخة: ${res.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                _userMessage.emit("خطأ أثناء الاستعادة: ${e.message}")
            }
        }
    }

    fun importInvoicesBatch(jsonString: String, onReport: (ImportBatchReport) -> Unit) {
        viewModelScope.launch {
            try {
                val report = batchImportUseCase.importInvoicesBatch(jsonString)
                onReport(report)
                _userMessage.emit("اكتمل الاستيراد: نجح ${report.successCount}، تخطي ${report.duplicateSkippedCount}، أخطاء ${report.errorCount}")
                refreshDashboard()
            } catch (e: Exception) {
                _userMessage.emit("فشل استيراد الدفعة: ${e.message}")
            }
        }
    }

    fun importPartiesList(parties: List<PartyEntity>) {
        viewModelScope.launch {
            try {
                val existing = db.partyDao().getAllPartiesSync().map { it.name.trim().lowercase() }.toSet()
                var added = 0
                for (p in parties) {
                    if (p.name.trim().lowercase() !in existing) {
                        repository.insertParty(p)
                        added++
                    }
                }
                _userMessage.emit("تم استيراد $added طرف بنجاح")
            } catch (e: Exception) {
                _userMessage.emit("فشل استيراد الأطراف: ${e.message}")
            }
        }
    }

    fun postImportedPurchases(drafts: List<DataJsonHelper.ImportedPurchaseDraft>) {
        viewModelScope.launch {
            try {
                var postedCount = 0
                val now = java.time.LocalDate.now()
                val todayEpoch = now.toEpochDay()
                val currentYear = now.year
                val allVendors = db.partyDao().getAllPartiesSync()

                for (draft in drafts) {
                    var vendor = allVendors.find { it.name.trim().equals(draft.vendorName.trim(), ignoreCase = true) }
                    if (vendor == null) {
                        val newVendor = PartyEntity(
                            id = UuidUtils.newTimeOrderedId(),
                            name = draft.vendorName.trim(),
                            isVendor = true
                        )
                        db.partyDao().insertParty(newVendor)
                        vendor = newVendor
                    }

                    val rate = ExchangeRate(draft.currencyCode, CurrencyCode.YER, draft.exchangeRateMicros)
                    writer.postPurchaseInvoice(
                        vendorPartyId = vendor.id,
                        fiscalYear = currentYear,
                        dateEpochDay = todayEpoch,
                        currency = draft.currencyCode,
                        exchangeRate = rate,
                        items = draft.items,
                        notes = draft.notes
                    )
                    postedCount++
                }
                _userMessage.emit("تم ترحيل $postedCount فاتورة مشتريات إلى الأستاذ بنجاح")
                refreshDashboard()
            } catch (e: Exception) {
                _userMessage.emit("فشل ترحيل فواتير المشتريات: ${e.message}")
            }
        }
    }

    // --- Google Auth & Firebase Sync Operations ---

    fun signInWithGoogle(activityOrContext: Context) {
        viewModelScope.launch {
            val res = authManager.signInWithGoogleCredentialManager(activityOrContext)
            if (res.isSuccess) {
                val user = res.getOrNull()
                _userMessage.emit("تم تسجيل الدخول بنجاح بحساب Google: ${user?.email}")
                if (user != null) {
                    googleAuthManager.signInDirectWithEmail(user.email, user.displayName)
                }
                checkForRemoteBackupIfEmpty()
            } else {
                val err = res.exceptionOrNull()?.localizedMessage ?: "فشل تسجيل الدخول"
                _userMessage.emit(err)
            }
        }
    }

    fun handleLegacyGoogleSignInResult(task: com.google.android.gms.tasks.Task<com.google.android.gms.auth.api.signin.GoogleSignInAccount>) {
        viewModelScope.launch {
            try {
                val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                val idToken = account?.idToken
                if (!idToken.isNullOrBlank()) {
                    val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
                    val authResult = com.google.firebase.auth.FirebaseAuth.getInstance().signInWithCredential(credential).await()
                    val fbUser = authResult.user
                    val email = account.email ?: fbUser?.email ?: ""
                    val displayName = account.displayName ?: fbUser?.displayName ?: email.substringBefore("@")
                    val photoUrl = account.photoUrl?.toString() ?: fbUser?.photoUrl?.toString()
                    val uid = fbUser?.uid ?: account.id ?: email.replace(".", "_").replace("@", "_at_")

                    val session = UserSession(
                        email = email,
                        displayName = displayName,
                        photoUrl = photoUrl,
                        uid = uid,
                        isSignedIn = true
                    )
                    authManager.saveSessionDirectly(session)
                    googleAuthManager.signInDirectWithEmail(email, displayName)
                    checkForRemoteBackupIfEmpty()
                    _userMessage.emit("تم تسجيل الدخول بنجاح بحساب Google: $email")
                } else {
                    _userMessage.emit("تعذر الحصول على رمز Google ID Token")
                }
            } catch (e: com.google.android.gms.common.api.ApiException) {
                val statusCode = e.statusCode
                android.util.Log.w("AppViewModel", "GoogleSignIn ApiException statusCode=$statusCode: ${e.message}")
                if (statusCode == 10 || statusCode == 12500) {
                    _userMessage.emit("تنبيه: فشل مطابقة شهادة SHA-1 في Firebase Console (Error $statusCode). يمكنك استخدام الدخول البديل بالبريد في بيئة الاختبار.")
                } else if (statusCode == 12501) {
                    _userMessage.emit("تم إلغاء اختيار الحساب")
                } else {
                    _userMessage.emit("فشل تسجيل الدخول عبر Google: ${e.message ?: "خطأ $statusCode"}")
                }
            } catch (e: Exception) {
                android.util.Log.e("AppViewModel", "GoogleSignIn error: ${e.message}", e)
                _userMessage.emit("خطأ في تسجيل الدخول: ${e.localizedMessage ?: e.message}")
            }
        }
    }

    fun signInDirectly(email: String, displayName: String = "") {
        val user = authManager.signInDirectly(email, displayName)
        googleAuthManager.signInDirectWithEmail(email, displayName)
        viewModelScope.launch {
            _userMessage.emit("تم تفعيل الحساب: ${user.email}")
            checkForRemoteBackupIfEmpty()
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authManager.signOut()
            googleAuthManager.signOut()
            pendingCloudRestorePrompt.value = null
            _userMessage.emit("تم تسجيل الخروج")
        }
    }

    fun restoreFromCloud(targetUid: String? = null, targetEmail: String? = null) {
        val user = currentUser.value
        val uid = targetUid ?: user?.uid ?: ""
        val email = targetEmail ?: user?.email ?: ""
        if (uid.isBlank() && email.isBlank()) {
            viewModelScope.launch {
                _userMessage.emit("يرجى تسجيل الدخول بحساب Google أولاً لتحديد النسخة السحابية")
            }
            return
        }
        viewModelScope.launch {
            val res = syncManager.syncPull(uid, email)
            if (res.isSuccess) {
                _userMessage.emit(res.getOrNull() ?: "تمت استعادة البيانات من السحابة بنجاح")
                refreshDashboard()
                runInvariantCheck()
            } else {
                _userMessage.emit(res.exceptionOrNull()?.localizedMessage ?: "فشل استعادة البيانات السحابية")
            }
        }
    }

    fun syncPushToFirebase() {
        val user = currentUser.value
        val uid = user?.uid ?: ""
        val email = user?.email ?: ""
        if (uid.isBlank() && email.isBlank()) {
            viewModelScope.launch {
                _userMessage.emit("يرجى تسجيل الدخول بحساب Google أولاً لرفع النسخة السحابية")
            }
            return
        }
        viewModelScope.launch {
            val res = syncManager.syncPush(uid, email)
            if (res.isSuccess) {
                _userMessage.emit(res.getOrNull() ?: "تمت المزامنة السحابية بنجاح")
            } else {
                _userMessage.emit(res.exceptionOrNull()?.localizedMessage ?: "فشل الرفع السحابي")
            }
        }
    }

    fun syncPullFromFirebase() {
        restoreFromCloud()
    }

    fun setAutoSync(enabled: Boolean) {
        firebaseSyncManager.setAutoSync(enabled)
    }

    fun signInWithGoogleOneTap(context: android.content.Context) {
        viewModelScope.launch {
            val res = googleAuthManager.signInWithGoogleOneTap(context)
            if (res.isSuccess) {
                val profile = res.getOrNull()
                if (profile != null) {
                    authManager.signInDirectly(profile.email, profile.displayName)
                }
                _userMessage.emit("تم تسجيل الدخول بنجاح بحساب Google: ${profile?.email}")
                checkForRemoteBackupIfEmpty()
            } else {
                val err = res.exceptionOrNull()?.localizedMessage ?: "فشل تسجيل الدخول عبر Google"
                _userMessage.emit(err)
            }
        }
    }

    fun signInDirectWithEmail(email: String, displayName: String = "") {
        googleAuthManager.signInDirectWithEmail(email, displayName)
        signInDirectly(email, displayName)
    }

    fun signOutGoogle() {
        viewModelScope.launch {
            googleAuthManager.signOut()
            authManager.signOut()
            _userMessage.emit("تم تسجيل الخروج بنجاح")
        }
    }

    fun performCloudSync() {
        val email = currentUserProfile.value?.email ?: currentUser.value?.email ?: "mosthassan.ye@gmail.com"
        viewModelScope.launch {
            val res = firebaseSyncManager.performFullSync(email)
            if (res.isSuccess) {
                val count = res.getOrNull() ?: 0
                _userMessage.emit("اكتملت المزامنة السحابية بنجاح ($count سجل)")
                refreshDashboard()
            } else {
                val err = res.exceptionOrNull()?.localizedMessage ?: "فشلت المزامنة السحابية"
                _userMessage.emit(err)
            }
        }
    }

    fun performCloudPull() {
        val email = currentUserProfile.value?.email ?: currentUser.value?.email ?: "mosthassan.ye@gmail.com"
        viewModelScope.launch {
            val res = firebaseSyncManager.pullDataFromCloud(email)
            if (res.isSuccess) {
                val count = res.getOrNull() ?: 0
                _userMessage.emit("تم جلب البيانات من السحاب بنجاح ($count سجل)")
                refreshDashboard()
                runInvariantCheck()
            } else {
                val err = res.exceptionOrNull()?.localizedMessage ?: "فشل جلب البيانات السحابية"
                _userMessage.emit(err)
            }
        }
    }
}

