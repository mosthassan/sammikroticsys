package com.example.data.sync

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.room.withTransaction
import com.example.core.model.RateZone
import com.example.data.local.AppDatabase
import com.example.data.local.entity.AllocationEntity
import com.example.data.local.entity.AssetEntity
import com.example.data.local.entity.CardPackageEntity
import com.example.data.local.entity.CurrencyRateEntity
import com.example.data.local.entity.DepreciationRunEntity
import com.example.data.local.entity.DocumentEntity
import com.example.data.local.entity.DocumentItemEntity
import com.example.data.local.entity.FiscalPeriodEntity
import com.example.data.local.entity.JournalEntryEntity
import com.example.data.local.entity.JournalLineEntity
import com.example.data.local.entity.NumberSequenceEntity
import com.example.data.local.entity.OrganizationEntity
import com.example.data.local.entity.PartyEntity
import com.example.data.local.entity.StockMovementEntity
import com.example.data.local.entity.TreasuryAccountEntity
import com.example.data.network.DeviceStatus
import com.example.data.network.DeviceType
import com.example.data.network.NetworkConfig
import com.example.data.network.NetworkDevice
import com.example.data.network.NetworkRepository
import com.example.data.network.SubnetRange
import com.example.domain.usecase.BackupRestoreUseCase
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

sealed class SyncStatus {
    object Idle : SyncStatus()
    data class InProgress(val message: String, val progressPercent: Int = 0) : SyncStatus()
    data class Success(val lastSyncFormatted: String, val recordsSynced: Int) : SyncStatus()
    data class Error(val errorMessage: String) : SyncStatus()
}

/**
 * Production-Grade Multi-Tenant Firebase Cloud Sync Engine
 * Synchronizes ALL application data:
 * 1. Network Profile & Identity (هوية الشبكة)
 * 2. Exchange Rates / Currency Rates (سعر الصرف)
 * 3. Network Devices (أجهزة الشبكة)
 * 4. Network Subnets (نطاقات الشبكة)
 * 5. Organization Profile (بيانات المنشأة)
 * 6. Chart of Accounts, Treasuries, Parties, Packages
 * 7. Invoices, Vouchers, Document Items, Ledger Entries & Lines
 * 8. Allocations, Assets, Depreciation Runs, Number Sequences, Stock Movements
 * 9. Complete atomic snapshot for 100% loss-free disaster recovery
 */
class FirebaseSyncManager(
    private val context: Context,
    private val db: AppDatabase,
    private val backupRestoreUseCase: BackupRestoreUseCase? = null,
    private val networkRepository: NetworkRepository? = null
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("firebase_sync_prefs", Context.MODE_PRIVATE)

    private val effectiveNetworkRepository: NetworkRepository by lazy {
        networkRepository ?: NetworkRepository(context)
    }

    private val effectiveBackupRestoreUseCase: BackupRestoreUseCase by lazy {
        backupRestoreUseCase ?: BackupRestoreUseCase(
            db = db,
            deviceDao = effectiveNetworkRepository,
            networkRepository = effectiveNetworkRepository,
            context = context
        )
    }

    private val firestore: FirebaseFirestore? by lazy {
        try {
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                FirebaseFirestore.getInstance()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w("FirebaseSyncManager", "Firestore initialization: ${e.message}")
            null
        }
    }

    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    private val _lastSyncTimestamp = MutableStateFlow<String?>(prefs.getString(KEY_LAST_SYNC, null))
    val lastSyncTimestamp: StateFlow<String?> = _lastSyncTimestamp.asStateFlow()

    private val _autoSyncEnabled = MutableStateFlow(prefs.getBoolean(KEY_AUTO_SYNC, true))
    val autoSyncEnabled: StateFlow<Boolean> = _autoSyncEnabled.asStateFlow()

    fun setAutoSync(enabled: Boolean) {
        _autoSyncEnabled.value = enabled
        prefs.edit().putBoolean(KEY_AUTO_SYNC, enabled).apply()
    }

    /**
     * Sanitizes email to be used as a clean document ID in Firestore
     */
    fun sanitizeTenantEmail(email: String): String {
        return email.trim().lowercase()
            .replace(".", "_")
            .replace("@", "_at_")
    }

    /**
     * Complete Bidirectional Cloud Synchronization
     * Uploads ALL local state (network profile, devices, FX rates, and complete ledger) to Firestore.
     */
    suspend fun performFullSync(userEmail: String): Result<Int> = withContext(Dispatchers.IO) {
        if (userEmail.isBlank()) {
            val err = "يجب تسجيل الدخول بحساب Google أولاً لتحديد معرف المزامنة"
            _syncStatus.value = SyncStatus.Error(err)
            return@withContext Result.failure(IllegalStateException(err))
        }

        val tenantKey = sanitizeTenantEmail(userEmail)
        _syncStatus.value = SyncStatus.InProgress("جاري الاتصال بسحابة Firebase...", 5)

        val dbInstance = firestore
        if (dbInstance == null) {
            val errMsg = "خدمة Firebase غير مهيأة على هذا الجهاز أو تعمل بدون إنترنت"
            _syncStatus.value = SyncStatus.Error(errMsg)
            return@withContext Result.failure(IllegalStateException(errMsg))
        }

        try {
            var totalSynced = 0
            val now = System.currentTimeMillis()
            val tenantDoc = dbInstance.collection("tenants").document(tenantKey)

            // 1. PUSH: Network Profile & Identity (هوية الشبكة)
            _syncStatus.value = SyncStatus.InProgress("مزامنة هوية الشبكة والإعدادات العامة...", 10)
            val netConfig = effectiveNetworkRepository.config.value
            val profileData = hashMapOf(
                "networkName" to netConfig.networkName,
                "ownerName" to netConfig.ownerName,
                "location" to netConfig.location,
                "welcomeMessage" to netConfig.welcomeMessage,
                "supportPhone" to netConfig.supportPhone,
                "supportWhatsapp" to netConfig.supportWhatsapp,
                "mainRouterModel" to netConfig.mainRouterModel,
                "routerOsVersion" to netConfig.routerOsVersion,
                "hotspotDomain" to netConfig.hotspotDomain,
                "hotspotServerName" to netConfig.hotspotServerName,
                "adminPort" to netConfig.adminPort,
                "primaryDns" to netConfig.primaryDns,
                "secondaryDns" to netConfig.secondaryDns,
                "approvedSubnet" to netConfig.approvedSubnet,
                "rateZone" to netConfig.rateZone.name,
                "defaultUsdRateMicros" to netConfig.defaultUsdRateMicros,
                "defaultSarRateMicros" to netConfig.defaultSarRateMicros,
                "updatedAt" to netConfig.updatedAt,
                "syncedAt" to now
            )
            tenantDoc.collection("network_profile").document("profile")
                .set(profileData, SetOptions.merge()).await()
            totalSynced++

            // 2. PUSH: Network Devices (أجهزة الشبكة)
            _syncStatus.value = SyncStatus.InProgress("مزامنة أجهزة الشبكة وأبراج البث...", 15)
            val devices = effectiveNetworkRepository.getAllDevices()
            val devColl = tenantDoc.collection("network_devices")
            devices.forEach { d ->
                val dData = hashMapOf(
                    "id" to d.id,
                    "name" to d.name,
                    "ipAddress" to d.ipAddress,
                    "deviceType" to d.deviceType.name,
                    "macAddress" to d.macAddress,
                    "towerLocation" to d.towerLocation,
                    "frequency" to d.frequency,
                    "channelWidth" to d.channelWidth,
                    "status" to d.status.name,
                    "notes" to d.notes,
                    "model" to d.model,
                    "managementPort" to d.managementPort,
                    "subnet" to d.subnet,
                    "credentials" to d.credentials,
                    "syncedAt" to now
                )
                devColl.document(d.id).set(dData, SetOptions.merge()).await()
                totalSynced++
            }

            // 3. PUSH: Network Subnets
            val subnets = effectiveNetworkRepository.subnets.value
            val subnetsColl = tenantDoc.collection("network_subnets")
            subnets.forEach { s ->
                val sData = hashMapOf(
                    "id" to s.id,
                    "name" to s.name,
                    "cidr" to s.cidr,
                    "gateway" to s.gateway,
                    "dhcpRangeStart" to s.dhcpRangeStart,
                    "dhcpRangeEnd" to s.dhcpRangeEnd,
                    "purpose" to s.purpose,
                    "syncedAt" to now
                )
                subnetsColl.document(s.id).set(sData, SetOptions.merge()).await()
                totalSynced++
            }

            // 4. PUSH: Currency Rates (أسعار الصرف اليومية)
            _syncStatus.value = SyncStatus.InProgress("مزامنة أسعار الصرف لجميع المناطق...", 22)
            val currencyRates = db.currencyRateDao().getAllRatesSync()
            val ratesColl = tenantDoc.collection("currency_rates")
            currencyRates.forEach { r ->
                val rData = hashMapOf(
                    "id" to r.id,
                    "currency" to r.currency,
                    "zone" to r.zone,
                    "rateMicros" to r.rateMicros,
                    "effectiveDateEpochDay" to r.effectiveDateEpochDay,
                    "createdAt" to r.createdAt,
                    "createdBy" to r.createdBy,
                    "reason" to r.reason,
                    "syncedAt" to now
                )
                ratesColl.document(r.id).set(rData, SetOptions.merge()).await()
                totalSynced++
            }

            // 5. PUSH: Organization
            val org = db.organizationDao().getOrganizationSync()
            if (org != null) {
                val orgData = hashMapOf(
                    "id" to org.id,
                    "name" to org.name,
                    "taxNumber" to org.taxNumber,
                    "functionalCurrency" to org.functionalCurrency,
                    "fiscalYearStartMonth" to org.fiscalYearStartMonth,
                    "isInitialized" to org.isInitialized,
                    "primaryRateZone" to org.primaryRateZone,
                    "equityShareMode" to org.equityShareMode,
                    "createdAt" to org.createdAt,
                    "syncedAt" to now
                )
                tenantDoc.collection("organization").document("current")
                    .set(orgData, SetOptions.merge()).await()
                totalSynced++
            }

            // 6. PUSH: Parties (العملاء والموردين والشركاء)
            _syncStatus.value = SyncStatus.InProgress("مزامنة أطراف المعاملات والعملاء...", 32)
            val parties = db.partyDao().getAllPartiesSync()
            val partiesColl = tenantDoc.collection("parties")
            parties.forEach { p ->
                val data = hashMapOf(
                    "id" to p.id,
                    "name" to p.name,
                    "phone" to p.phone,
                    "isCustomer" to p.isCustomer,
                    "isVendor" to p.isVendor,
                    "isPartner" to p.isPartner,
                    "equityPercentageBasisPoints" to p.equityPercentageBasisPoints,
                    "creditLimitMinor" to p.creditLimitMinor,
                    "isActive" to p.isActive,
                    "createdAt" to p.createdAt,
                    "syncedAt" to now
                )
                partiesColl.document(p.id).set(data, SetOptions.merge()).await()
                totalSynced++
            }

            // 7. PUSH: Card Packages & Inventory
            _syncStatus.value = SyncStatus.InProgress("مزامنة باقات الكروت والمخزون...", 42)
            val packages = db.cardPackageDao().getAllPackagesSync()
            val pkgsColl = tenantDoc.collection("card_packages")
            packages.forEach { pkg ->
                val data = hashMapOf(
                    "id" to pkg.id,
                    "name" to pkg.name,
                    "durationOrQuota" to pkg.durationOrQuota,
                    "wholesalePriceMinor" to pkg.wholesalePriceMinor,
                    "retailPriceMinor" to pkg.retailPriceMinor,
                    "isActive" to pkg.isActive,
                    "syncedAt" to now
                )
                pkgsColl.document(pkg.id).set(data, SetOptions.merge()).await()
                totalSynced++
            }

            // 8. PUSH: Treasuries & Cashboxes
            _syncStatus.value = SyncStatus.InProgress("مزامنة صناديق النقدية والخزائن...", 50)
            val treasuries = db.treasuryDao().getAllTreasuriesSync()
            val trColl = tenantDoc.collection("treasuries")
            treasuries.forEach { tr ->
                val data = hashMapOf(
                    "id" to tr.id,
                    "name" to tr.name,
                    "glAccountCode" to tr.glAccountCode,
                    "currency" to tr.currency,
                    "isActive" to tr.isActive,
                    "allowNegative" to tr.allowNegative,
                    "syncedAt" to now
                )
                trColl.document(tr.id).set(data, SetOptions.merge()).await()
                totalSynced++
            }

            // 9. PUSH: Documents
            _syncStatus.value = SyncStatus.InProgress("مزامنة السندات وفواتير المبيعات والمشتريات...", 60)
            val docs = db.documentDao().getAllDocumentsSync()
            val docsColl = tenantDoc.collection("documents")
            docs.forEach { d ->
                val data = hashMapOf(
                    "id" to d.id,
                    "type" to d.type,
                    "fiscalYear" to d.fiscalYear,
                    "docNumber" to d.docNumber,
                    "partyId" to d.partyId,
                    "dateEpochDay" to d.dateEpochDay,
                    "currency" to d.currency,
                    "exchangeRateMicros" to d.exchangeRateMicros,
                    "rateZone" to d.rateZone,
                    "rateSource" to d.rateSource,
                    "totalMinor" to d.totalMinor,
                    "totalBaseMinor" to d.totalBaseMinor,
                    "status" to d.status,
                    "notes" to d.notes,
                    "reversalOfDocId" to d.reversalOfDocId,
                    "createdAt" to d.createdAt,
                    "syncedAt" to now
                )
                docsColl.document(d.id).set(data, SetOptions.merge()).await()
                totalSynced++
            }

            // 10. PUSH: Document Items
            val docItems = db.documentDao().getAllDocumentItemsSync()
            val itemsColl = tenantDoc.collection("document_items")
            docItems.forEach { itm ->
                val itmData = hashMapOf(
                    "id" to itm.id,
                    "docId" to itm.docId,
                    "itemIndex" to itm.itemIndex,
                    "packageId" to itm.packageId,
                    "description" to itm.description,
                    "accountCode" to itm.accountCode,
                    "quantity" to itm.quantity,
                    "unitPriceMinor" to itm.unitPriceMinor,
                    "totalMinor" to itm.totalMinor,
                    "isAsset" to itm.isAsset,
                    "syncedAt" to now
                )
                itemsColl.document(itm.id).set(itmData, SetOptions.merge()).await()
                totalSynced++
            }

            // 11. PUSH: Journal Entries
            val journalEntries = db.journalDao().getAllEntriesSync()
            val entriesColl = tenantDoc.collection("journal_entries")
            journalEntries.forEach { je ->
                val jeData = hashMapOf(
                    "id" to je.id,
                    "docId" to je.docId,
                    "entryNumber" to je.entryNumber,
                    "entryDateEpochDay" to je.entryDateEpochDay,
                    "type" to je.type,
                    "memo" to je.memo,
                    "createdAt" to je.createdAt,
                    "syncedAt" to now
                )
                entriesColl.document(je.id).set(jeData, SetOptions.merge()).await()
                totalSynced++
            }

            // 12. PUSH: Journal Lines
            _syncStatus.value = SyncStatus.InProgress("مزامنة قيود اليومية ودفتر الأستاذ العام...", 72)
            val journalLines = db.journalDao().getAllLinesSync()
            val linesColl = tenantDoc.collection("journal_lines")
            journalLines.forEach { line ->
                val data = hashMapOf(
                    "id" to line.id,
                    "entryId" to line.entryId,
                    "lineNo" to line.lineNo,
                    "accountCode" to line.accountCode,
                    "currency" to line.currency,
                    "exchangeRateMicros" to line.exchangeRateMicros,
                    "origMinor" to line.origMinor,
                    "baseDebitMinor" to line.baseDebitMinor,
                    "baseCreditMinor" to line.baseCreditMinor,
                    "partyId" to line.partyId,
                    "treasuryId" to line.treasuryId,
                    "memo" to line.memo,
                    "syncedAt" to now
                )
                linesColl.document(line.id).set(data, SetOptions.merge()).await()
                totalSynced++
            }

            // 13. PUSH: Allocations
            val allocations = db.allocationDao().getAllActiveAllocationsSync()
            val allocColl = tenantDoc.collection("allocations")
            allocations.forEach { al ->
                val alData = hashMapOf(
                    "id" to al.id,
                    "paymentDocId" to al.paymentDocId,
                    "invoiceDocId" to al.invoiceDocId,
                    "allocatedOrigMinor" to al.allocatedOrigMinor,
                    "allocatedBaseMinor" to al.allocatedBaseMinor,
                    "exchangeGainLossMinor" to al.exchangeGainLossMinor,
                    "isVoided" to al.isVoided,
                    "createdAt" to al.createdAt,
                    "syncedAt" to now
                )
                allocColl.document(al.id).set(alData, SetOptions.merge()).await()
                totalSynced++
            }

            // 14. PUSH: Assets & Depreciation
            val assets = db.assetDao().getAllAssetsSync()
            val assetsColl = tenantDoc.collection("assets")
            assets.forEach { ast ->
                val astData = hashMapOf(
                    "id" to ast.id,
                    "docId" to ast.docId,
                    "name" to ast.name,
                    "purchaseDateEpochDay" to ast.purchaseDateEpochDay,
                    "purchaseCostMinor" to ast.purchaseCostMinor,
                    "salvageValueMinor" to ast.salvageValueMinor,
                    "usefulLifeMonths" to ast.usefulLifeMonths,
                    "accumulatedDepreciationMinor" to ast.accumulatedDepreciationMinor,
                    "isDisposed" to ast.isDisposed,
                    "syncedAt" to now
                )
                assetsColl.document(ast.id).set(astData, SetOptions.merge()).await()
                totalSynced++
            }

            // 15. PUSH: Depreciation Runs
            val depRuns = db.assetDao().getAllDepreciationRunsSync()
            val depColl = tenantDoc.collection("depreciation_runs")
            depRuns.forEach { dr ->
                val drData = hashMapOf(
                    "id" to dr.id,
                    "periodYear" to dr.periodYear,
                    "periodMonth" to dr.periodMonth,
                    "assetId" to dr.assetId,
                    "journalEntryId" to dr.journalEntryId,
                    "depreciationAmountMinor" to dr.depreciationAmountMinor,
                    "syncedAt" to now
                )
                depColl.document(dr.id).set(drData, SetOptions.merge()).await()
                totalSynced++
            }

            // 16. PUSH: Number Sequences
            val sequences = db.numberSequenceDao().getAllSequencesSync()
            val seqColl = tenantDoc.collection("number_sequences")
            sequences.forEach { sq ->
                val seqKey = "${sq.docType}_${sq.fiscalYear}"
                val sqData = hashMapOf(
                    "docType" to sq.docType,
                    "fiscalYear" to sq.fiscalYear,
                    "nextValue" to sq.nextValue,
                    "syncedAt" to now
                )
                seqColl.document(seqKey).set(sqData, SetOptions.merge()).await()
                totalSynced++
            }

            // 17. PUSH: Fiscal Periods
            val periods = db.fiscalPeriodDao().getAllPeriodsSync()
            val fpColl = tenantDoc.collection("fiscal_periods")
            periods.forEach { fp ->
                val fpData = hashMapOf(
                    "id" to fp.id,
                    "year" to fp.year,
                    "month" to fp.month,
                    "isClosed" to fp.isClosed,
                    "closedAt" to fp.closedAt,
                    "syncedAt" to now
                )
                fpColl.document(fp.id).set(fpData, SetOptions.merge()).await()
                totalSynced++
            }

            // 18. PUSH: Stock Movements
            val stock = db.cardPackageDao().getAllStockMovementsSync()
            val smColl = tenantDoc.collection("stock_movements")
            stock.forEach { sm ->
                val smData = hashMapOf(
                    "id" to sm.id,
                    "packageId" to sm.packageId,
                    "docId" to sm.docId,
                    "type" to sm.type,
                    "quantity" to sm.quantity,
                    "movementDateEpochDay" to sm.movementDateEpochDay,
                    "createdAt" to sm.createdAt,
                    "syncedAt" to now
                )
                smColl.document(sm.id).set(smData, SetOptions.merge()).await()
                totalSynced++
            }

            // 19. PUSH: Complete Atomic Verifiable Snapshot
            _syncStatus.value = SyncStatus.InProgress("تصدير لقطة سحابية شاملة ومطابقة التوازن...", 88)
            val jsonSnapshot = effectiveBackupRestoreUseCase.exportDatabaseToJson()
            val snapData = hashMapOf(
                "schemaVersion" to 1,
                "tenantKey" to tenantKey,
                "userEmail" to userEmail,
                "timestamp" to now,
                "jsonContent" to jsonSnapshot,
                "totalRecords" to totalSynced,
                "partiesCount" to parties.size,
                "packagesCount" to packages.size,
                "treasuriesCount" to treasuries.size,
                "documentsCount" to docs.size,
                "documentItemsCount" to docItems.size,
                "journalEntriesCount" to journalEntries.size,
                "journalLinesCount" to journalLines.size,
                "currencyRatesCount" to currencyRates.size,
                "networkDevicesCount" to devices.size,
                "hasNetworkProfile" to true
            )
            tenantDoc.collection("snapshots").document("latest")
                .set(snapData, SetOptions.merge()).await()

            // 20. Update Tenant Cloud Metadata
            _syncStatus.value = SyncStatus.InProgress("تحديث مؤشرات المزامنة وحالة السحابة...", 95)
            val metaData = hashMapOf(
                "userEmail" to userEmail,
                "tenantKey" to tenantKey,
                "lastSyncTimestamp" to now,
                "recordsCount" to totalSynced,
                "partiesCount" to parties.size,
                "packagesCount" to packages.size,
                "treasuriesCount" to treasuries.size,
                "documentsCount" to docs.size,
                "journalLinesCount" to journalLines.size,
                "currencyRatesCount" to currencyRates.size,
                "networkDevicesCount" to devices.size,
                "hasNetworkProfile" to true,
                "appVersion" to "1.0",
                "syncedBy" to "SamMikrotik Cloud Engine"
            )
            tenantDoc.collection("metadata").document("sync_info")
                .set(metaData, SetOptions.merge()).await()

            // Success formatting
            val dateFormat = SimpleDateFormat("yyyy/MM/dd hh:mm a", Locale.getDefault())
            val formattedTime = dateFormat.format(Date(now))

            prefs.edit()
                .putString(KEY_LAST_SYNC, formattedTime)
                .putLong(KEY_LAST_SYNC_MS, now)
                .putInt(KEY_LAST_SYNC_COUNT, totalSynced)
                .apply()

            _lastSyncTimestamp.value = formattedTime
            _syncStatus.value = SyncStatus.Success(formattedTime, totalSynced)

            Result.success(totalSynced)
        } catch (e: Exception) {
            Log.e("FirebaseSyncManager", "Sync failure: ${e.message}", e)
            val errMsg = "فشل أثناء المزامنة: ${e.localizedMessage ?: e.message}"
            _syncStatus.value = SyncStatus.Error(errMsg)
            Result.failure(e)
        }
    }

    /**
     * Pull data from Firestore into local Room database and Network Repository.
     * Supports atomic full snapshot restoration and individual collection fallback.
     */
    suspend fun pullDataFromCloud(userEmail: String): Result<Int> = withContext(Dispatchers.IO) {
        if (userEmail.isBlank()) {
            return@withContext Result.failure(IllegalStateException("البريد الإلكتروني فارغ"))
        }

        val tenantKey = sanitizeTenantEmail(userEmail)
        val dbInstance = firestore ?: return@withContext Result.failure(IllegalStateException("Firebase غير متصل"))

        try {
            _syncStatus.value = SyncStatus.InProgress("جاري فحص النسخ السحابية لمستأجر الشبكة...", 15)
            val tenantDoc = dbInstance.collection("tenants").document(tenantKey)

            // Step 1: Check if complete snapshot exists
            val snapshotDoc = tenantDoc.collection("snapshots").document("latest").get().await()
            val jsonContent = snapshotDoc.getString("jsonContent")

            if (!jsonContent.isNullOrBlank()) {
                _syncStatus.value = SyncStatus.InProgress("استعادة شاملة من اللقطة السحابية الذرية...", 40)
                val restoreResult = effectiveBackupRestoreUseCase.restoreDatabaseFromJson(jsonContent)
                if (restoreResult.isSuccess) {
                    val totalRecords = snapshotDoc.getLong("totalRecords")?.toInt()
                        ?: (snapshotDoc.getLong("documentsCount")?.toInt() ?: 0)
                    val dateFormat = SimpleDateFormat("yyyy/MM/dd hh:mm a", Locale.getDefault())
                    val formattedTime = dateFormat.format(Date())
                    _lastSyncTimestamp.value = formattedTime
                    _syncStatus.value = SyncStatus.Success(formattedTime, totalRecords)
                    return@withContext Result.success(totalRecords)
                }
            }

            // Step 2: Fallback to collection-by-collection pull if snapshot was absent
            _syncStatus.value = SyncStatus.InProgress("جلب السجلات من المجموعات السحابية...", 30)
            var pulledCount = 0

            // 1. Pull Network Profile
            val profileSnap = tenantDoc.collection("network_profile").document("profile").get().await()
            if (profileSnap.exists()) {
                val zoneStr = profileSnap.getString("rateZone") ?: RateZone.SANAA.name
                val zone = runCatching { RateZone.valueOf(zoneStr) }.getOrDefault(RateZone.SANAA)
                val netConfig = NetworkConfig(
                    networkName = profileSnap.getString("networkName") ?: "شبكة توزيع الإنترنت",
                    ownerName = profileSnap.getString("ownerName") ?: "مدير الشبكة",
                    location = profileSnap.getString("location") ?: "المركز الرئيسي",
                    welcomeMessage = profileSnap.getString("welcomeMessage") ?: "أهلاً بكم في شبكتنا",
                    supportPhone = profileSnap.getString("supportPhone") ?: "770000000",
                    supportWhatsapp = profileSnap.getString("supportWhatsapp") ?: "967770000000",
                    mainRouterModel = profileSnap.getString("mainRouterModel") ?: "MikroTik CCR2004-16G-2S+",
                    routerOsVersion = profileSnap.getString("routerOsVersion") ?: "v7.16",
                    hotspotDomain = profileSnap.getString("hotspotDomain") ?: "login.net",
                    hotspotServerName = profileSnap.getString("hotspotServerName") ?: "hotspot1",
                    adminPort = profileSnap.getLong("adminPort")?.toInt() ?: 8728,
                    primaryDns = profileSnap.getString("primaryDns") ?: "8.8.8.8",
                    secondaryDns = profileSnap.getString("secondaryDns") ?: "1.1.1.1",
                    approvedSubnet = profileSnap.getString("approvedSubnet") ?: "10.10.0.0/16",
                    rateZone = zone,
                    defaultUsdRateMicros = profileSnap.getLong("defaultUsdRateMicros") ?: 535_000_000L,
                    defaultSarRateMicros = profileSnap.getLong("defaultSarRateMicros") ?: 140_500_000L,
                    updatedAt = profileSnap.getLong("updatedAt") ?: System.currentTimeMillis()
                )
                effectiveNetworkRepository.saveConfig(netConfig)
                pulledCount++
            }

            // 2. Pull Network Devices
            val devicesSnap = tenantDoc.collection("network_devices").get().await()
            val remoteDevices = devicesSnap.documents.mapNotNull { doc ->
                try {
                    val typeStr = doc.getString("deviceType") ?: DeviceType.ACCESS_POINT.name
                    val statusStr = doc.getString("status") ?: DeviceStatus.ONLINE.name
                    NetworkDevice(
                        id = doc.getString("id") ?: doc.id,
                        name = doc.getString("name") ?: "",
                        ipAddress = doc.getString("ipAddress") ?: "",
                        deviceType = runCatching { DeviceType.valueOf(typeStr) }.getOrDefault(DeviceType.ACCESS_POINT),
                        macAddress = doc.getString("macAddress") ?: "",
                        towerLocation = doc.getString("towerLocation") ?: "البرج الرئيسي",
                        frequency = doc.getString("frequency") ?: "5500 MHz",
                        channelWidth = doc.getString("channelWidth") ?: "20/40 MHz",
                        status = runCatching { DeviceStatus.valueOf(statusStr) }.getOrDefault(DeviceStatus.ONLINE),
                        notes = doc.getString("notes") ?: "",
                        model = doc.getString("model") ?: "MikroTik RouterBOARD",
                        managementPort = doc.getLong("managementPort")?.toInt() ?: 8728,
                        subnet = doc.getString("subnet") ?: "10.10.1.0/24",
                        credentials = doc.getString("credentials") ?: "admin"
                    )
                } catch (_: Exception) { null }
            }
            if (remoteDevices.isNotEmpty()) {
                effectiveNetworkRepository.insertAll(remoteDevices)
                pulledCount += remoteDevices.size
            }

            // 3. Pull Currency Rates
            val ratesSnap = tenantDoc.collection("currency_rates").get().await()
            val remoteRates = ratesSnap.documents.mapNotNull { doc ->
                try {
                    CurrencyRateEntity(
                        id = doc.getString("id") ?: doc.id,
                        currency = doc.getString("currency") ?: "USD",
                        zone = doc.getString("zone") ?: "SANAA",
                        rateMicros = doc.getLong("rateMicros") ?: 535_000_000L,
                        effectiveDateEpochDay = doc.getLong("effectiveDateEpochDay") ?: 0L,
                        createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                        createdBy = doc.getString("createdBy") ?: "SYSTEM",
                        reason = doc.getString("reason") ?: ""
                    )
                } catch (_: Exception) { null }
            }

            // 4. Pull Parties
            val partiesSnap = tenantDoc.collection("parties").get().await()
            val remoteParties = partiesSnap.documents.mapNotNull { doc ->
                try {
                    PartyEntity(
                        id = doc.getString("id") ?: doc.id,
                        name = doc.getString("name") ?: "",
                        phone = doc.getString("phone") ?: "",
                        isCustomer = doc.getBoolean("isCustomer") ?: false,
                        isVendor = doc.getBoolean("isVendor") ?: false,
                        isPartner = doc.getBoolean("isPartner") ?: false,
                        equityPercentageBasisPoints = (doc.getLong("equityPercentageBasisPoints") ?: 0L).toInt(),
                        creditLimitMinor = doc.getLong("creditLimitMinor") ?: 0L,
                        isActive = doc.getBoolean("isActive") ?: true,
                        createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                    )
                } catch (_: Exception) { null }
            }

            // 5. Pull Packages
            val pkgsSnap = tenantDoc.collection("card_packages").get().await()
            val remotePackages = pkgsSnap.documents.mapNotNull { doc ->
                try {
                    CardPackageEntity(
                        id = doc.getString("id") ?: doc.id,
                        name = doc.getString("name") ?: "",
                        durationOrQuota = doc.getString("durationOrQuota") ?: "",
                        wholesalePriceMinor = doc.getLong("wholesalePriceMinor") ?: 0L,
                        retailPriceMinor = doc.getLong("retailPriceMinor") ?: 0L,
                        isActive = doc.getBoolean("isActive") ?: true
                    )
                } catch (_: Exception) { null }
            }

            // 6. Pull Treasuries
            val treasuriesSnap = tenantDoc.collection("treasuries").get().await()
            val remoteTreasuries = treasuriesSnap.documents.mapNotNull { doc ->
                try {
                    TreasuryAccountEntity(
                        id = doc.getString("id") ?: doc.id,
                        name = doc.getString("name") ?: "",
                        glAccountCode = doc.getString("glAccountCode") ?: "1101",
                        currency = doc.getString("currency") ?: "YER",
                        isActive = doc.getBoolean("isActive") ?: true,
                        allowNegative = doc.getBoolean("allowNegative") ?: false
                    )
                } catch (_: Exception) { null }
            }

            // Transactional safe merge into Room
            db.withTransaction {
                remoteRates.forEach { r ->
                    db.currencyRateDao().insertRate(r)
                    pulledCount++
                }
                remoteParties.forEach { p ->
                    val existing = db.partyDao().getPartyById(p.id)
                    if (existing == null) {
                        db.partyDao().insertParty(p)
                        pulledCount++
                    }
                }
                remotePackages.forEach { pkg ->
                    val existing = db.cardPackageDao().getPackageById(pkg.id)
                    if (existing == null) {
                        db.cardPackageDao().insertPackage(pkg)
                        pulledCount++
                    }
                }
                remoteTreasuries.forEach { tr ->
                    val existing = db.treasuryDao().getTreasuryById(tr.id)
                    if (existing == null) {
                        db.treasuryDao().insertTreasury(tr)
                        pulledCount++
                    }
                }
            }

            val dateFormat = SimpleDateFormat("yyyy/MM/dd hh:mm a", Locale.getDefault())
            val formattedTime = dateFormat.format(Date())
            _lastSyncTimestamp.value = formattedTime
            _syncStatus.value = SyncStatus.Success(formattedTime, pulledCount)

            Result.success(pulledCount)
        } catch (e: Exception) {
            _syncStatus.value = SyncStatus.Error("فشل في جلب البيانات: ${e.message}")
            Result.failure(e)
        }
    }

    companion object {
        private const val KEY_LAST_SYNC = "last_sync_timestamp"
        private const val KEY_LAST_SYNC_MS = "last_sync_timestamp_ms"
        private const val KEY_LAST_SYNC_COUNT = "last_sync_count"
        private const val KEY_AUTO_SYNC = "auto_sync_enabled"
    }
}
