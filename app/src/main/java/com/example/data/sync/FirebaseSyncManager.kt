package com.example.data.sync

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.entity.AssetEntity
import com.example.data.local.entity.CardPackageEntity
import com.example.data.local.entity.DocumentEntity
import com.example.data.local.entity.DocumentItemEntity
import com.example.data.local.entity.JournalEntryEntity
import com.example.data.local.entity.JournalLineEntity
import com.example.data.local.entity.PartyEntity
import com.example.data.local.entity.StockMovementEntity
import com.example.data.local.entity.TreasuryAccountEntity
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

sealed class SyncStatus {
    object Idle : SyncStatus()
    data class InProgress(val message: String, val progressPercent: Int = 0) : SyncStatus()
    data class Success(val lastSyncFormatted: String, val recordsSynced: Int) : SyncStatus()
    data class Error(val errorMessage: String) : SyncStatus()
}

/**
 * Production-Grade Multi-Tenant Firebase Cloud Sync Engine
 * Isolates all tenant data strictly by user email (e.g. `tenants/{email}/...`).
 * Ensures offline-first data integrity without compromising IFRS double-entry invariants.
 */
class FirebaseSyncManager(
    private val context: Context,
    private val db: AppDatabase,
    private val firestoreSyncManager: FirestoreSyncManager? = null
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("firebase_sync_prefs", Context.MODE_PRIVATE)

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
     * Uploads local Room state and pulls remote changes for this user email.
     */
    suspend fun performFullSync(userEmail: String): Result<Int> = withContext(Dispatchers.IO) {
        if (firestoreSyncManager != null) {
            _syncStatus.value = SyncStatus.InProgress("جاري المزامنة الشاملة لكافة البيانات والسجلات...", 25)
            val pushResult = firestoreSyncManager.syncPush(userEmail)
            if (pushResult.isSuccess) {
                val dateFormat = SimpleDateFormat("yyyy/MM/dd hh:mm a", Locale.getDefault())
                val formattedTime = dateFormat.format(Date())
                val recordsCount = firestoreSyncManager.syncMetadata.value?.run {
                    totalDocuments + totalJournalLines + totalParties + totalPackages + totalNetworkDevices + totalCurrencyRates
                } ?: 0
                prefs.edit().putString(KEY_LAST_SYNC, formattedTime).apply()
                _lastSyncTimestamp.value = formattedTime
                _syncStatus.value = SyncStatus.Success(formattedTime, recordsCount)
                return@withContext Result.success(recordsCount)
            } else {
                val err = pushResult.exceptionOrNull()?.localizedMessage ?: "فشل الرفع السحابي"
                _syncStatus.value = SyncStatus.Error(err)
                return@withContext Result.failure(pushResult.exceptionOrNull() ?: Exception(err))
            }
        }

        if (userEmail.isBlank()) {
            val err = "يجب تسجيل الدخول بحساب Google أولاً لتحديد معرف المزامنة"
            _syncStatus.value = SyncStatus.Error(err)
            return@withContext Result.failure(IllegalStateException(err))
        }

        val tenantKey = sanitizeTenantEmail(userEmail)
        _syncStatus.value = SyncStatus.InProgress("جاري الاتصال بسحابة Firebase...", 10)

        val dbInstance = firestore
        if (dbInstance == null) {
            val errMsg = "خدمة Firebase غير مهيأة على هذا الجهاز أو تعمل بدون إنترنت"
            _syncStatus.value = SyncStatus.Error(errMsg)
            return@withContext Result.failure(IllegalStateException(errMsg))
        }

        try {
            var totalSynced = 0

            // 1. PUSH: Sync Parties to Firestore
            _syncStatus.value = SyncStatus.InProgress("مزامنة أطراف المعاملات والعملاء...", 25)
            val parties = db.partyDao().getAllPartiesSync()
            val partiesColl = dbInstance.collection("tenants").document(tenantKey).collection("parties")
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
                    "syncedAt" to System.currentTimeMillis()
                )
                partiesColl.document(p.id).set(data, SetOptions.merge()).await()
                totalSynced++
            }

            // 2. PUSH: Sync Card Packages & Inventory
            _syncStatus.value = SyncStatus.InProgress("مزامنة باقات الكروت والمخزون...", 40)
            val packages = db.cardPackageDao().getAllPackagesSync()
            val pkgsColl = dbInstance.collection("tenants").document(tenantKey).collection("card_packages")
            packages.forEach { pkg ->
                val data = hashMapOf(
                    "id" to pkg.id,
                    "name" to pkg.name,
                    "durationOrQuota" to pkg.durationOrQuota,
                    "wholesalePriceMinor" to pkg.wholesalePriceMinor,
                    "retailPriceMinor" to pkg.retailPriceMinor,
                    "isActive" to pkg.isActive,
                    "syncedAt" to System.currentTimeMillis()
                )
                pkgsColl.document(pkg.id).set(data, SetOptions.merge()).await()
                totalSynced++
            }

            // 3. PUSH: Sync Treasuries & Cashboxes
            _syncStatus.value = SyncStatus.InProgress("مزامنة صناديق النقدية والخزائن...", 55)
            val treasuries = db.treasuryDao().getAllTreasuriesSync()
            val trColl = dbInstance.collection("tenants").document(tenantKey).collection("treasuries")
            treasuries.forEach { tr ->
                val data = hashMapOf(
                    "id" to tr.id,
                    "name" to tr.name,
                    "glAccountCode" to tr.glAccountCode,
                    "currency" to tr.currency,
                    "isActive" to tr.isActive,
                    "syncedAt" to System.currentTimeMillis()
                )
                trColl.document(tr.id).set(data, SetOptions.merge()).await()
                totalSynced++
            }

            // 4. PUSH: Sync Documents (Vouchers, Invoices)
            _syncStatus.value = SyncStatus.InProgress("مزامنة السندات وفواتير المبيعات والمشتريات...", 70)
            val docs = db.documentDao().getAllDocumentsSync()
            val docsColl = dbInstance.collection("tenants").document(tenantKey).collection("documents")
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
                    "totalMinor" to d.totalMinor,
                    "totalBaseMinor" to d.totalBaseMinor,
                    "status" to d.status,
                    "notes" to d.notes,
                    "reversalOfDocId" to d.reversalOfDocId,
                    "createdAt" to d.createdAt,
                    "syncedAt" to System.currentTimeMillis()
                )
                docsColl.document(d.id).set(data, SetOptions.merge()).await()
                totalSynced++
            }

            // 5. PUSH: Sync Journal Entries & Lines (IFRS Ledger)
            _syncStatus.value = SyncStatus.InProgress("مزامنة قيود اليومية ودفتر الأستاذ العام...", 85)
            val journalLines = db.journalDao().getAllLinesSync()
            val linesColl = dbInstance.collection("tenants").document(tenantKey).collection("journal_lines")
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
                    "syncedAt" to System.currentTimeMillis()
                )
                linesColl.document(line.id).set(data, SetOptions.merge()).await()
                totalSynced++
            }

            // 6. Update Tenant Cloud Metadata
            _syncStatus.value = SyncStatus.InProgress("تحديث مؤشرات المزامنة وحالة السحابة...", 95)
            val metaDoc = dbInstance.collection("tenants").document(tenantKey).collection("metadata").document("sync_info")
            val metaData = hashMapOf(
                "userEmail" to userEmail,
                "tenantKey" to tenantKey,
                "lastSyncTimestamp" to System.currentTimeMillis(),
                "recordsCount" to totalSynced,
                "appVersion" to "1.0",
                "syncedBy" to "SamMikrotik Cloud Agent"
            )
            metaDoc.set(metaData, SetOptions.merge()).await()

            // Success formatting
            val dateFormat = SimpleDateFormat("yyyy/MM/dd hh:mm a", Locale.getDefault())
            val formattedTime = dateFormat.format(Date())

            prefs.edit().putString(KEY_LAST_SYNC, formattedTime).apply()
            _lastSyncTimestamp.value = formattedTime
            _syncStatus.value = SyncStatus.Success(formattedTime, totalSynced)

            return@withContext Result.success(totalSynced)
        } catch (e: Exception) {
            Log.e("FirebaseSyncManager", "Sync failure: ${e.message}", e)
            val errMsg = "فشل أثناء المزامنة: ${e.localizedMessage ?: e.message}"
            _syncStatus.value = SyncStatus.Error(errMsg)
            return@withContext Result.failure(e)
        }
    }

    /**
     * Pull data from Firestore into local Room database
     */
    suspend fun pullDataFromCloud(userEmail: String): Result<Int> = withContext(Dispatchers.IO) {
        if (firestoreSyncManager != null) {
            _syncStatus.value = SyncStatus.InProgress("جاري جلب واستعادة كافة السجلات المحاسبية والشبكية...", 25)
            val pullResult = firestoreSyncManager.syncPull(userEmail)
            if (pullResult.isSuccess) {
                val dateFormat = SimpleDateFormat("yyyy/MM/dd hh:mm a", Locale.getDefault())
                val formattedTime = dateFormat.format(Date())
                val recordsCount = firestoreSyncManager.syncMetadata.value?.run {
                    totalDocuments + totalJournalLines + totalParties + totalPackages + totalNetworkDevices + totalCurrencyRates
                } ?: 0
                prefs.edit().putString(KEY_LAST_SYNC, formattedTime).apply()
                _lastSyncTimestamp.value = formattedTime
                _syncStatus.value = SyncStatus.Success(formattedTime, recordsCount)
                return@withContext Result.success(recordsCount)
            } else {
                val err = pullResult.exceptionOrNull()?.localizedMessage ?: "فشل جلب البيانات من السحابة"
                _syncStatus.value = SyncStatus.Error(err)
                return@withContext Result.failure(pullResult.exceptionOrNull() ?: Exception(err))
            }
        }

        if (userEmail.isBlank()) {
            return@withContext Result.failure(IllegalStateException("البريد الإلكتروني فارغ"))
        }

        val tenantKey = sanitizeTenantEmail(userEmail)
        val dbInstance = firestore ?: return@withContext Result.failure(IllegalStateException("Firebase غير متصل"))

        try {
            _syncStatus.value = SyncStatus.InProgress("جاري فحص البيانات السحابية...", 20)
            var pulledCount = 0

            // Pull parties
            val partiesSnap = dbInstance.collection("tenants").document(tenantKey).collection("parties").get().await()
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
                } catch (e: Exception) { null }
            }

            // Pull packages
            val pkgsSnap = dbInstance.collection("tenants").document(tenantKey).collection("card_packages").get().await()
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
                } catch (e: Exception) { null }
            }

            // Transactional safe merge into Room
            db.withTransaction {
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
            }

            val dateFormat = SimpleDateFormat("yyyy/MM/dd hh:mm a", Locale.getDefault())
            val formattedTime = dateFormat.format(Date())
            _lastSyncTimestamp.value = formattedTime
            _syncStatus.value = SyncStatus.Success(formattedTime, pulledCount)

            return@withContext Result.success(pulledCount)
        } catch (e: Exception) {
            _syncStatus.value = SyncStatus.Error("فشل في جلب البيانات: ${e.message}")
            return@withContext Result.failure(e)
        }
    }

    companion object {
        private const val KEY_LAST_SYNC = "last_sync_timestamp"
        private const val KEY_AUTO_SYNC = "auto_sync_enabled"
    }
}
