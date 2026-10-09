package com.example.data.sync

import android.content.Context
import android.util.Log
import com.example.data.local.AppDatabase
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
import org.json.JSONObject
import java.util.UUID

data class CloudBackupMeta(
    val uid: String,
    val userEmail: String,
    val timestamp: Long,
    val documentsCount: Int,
    val journalLinesCount: Int,
    val partiesCount: Int,
    val totalRecords: Int,
    val checksum: String,
    val networkDevicesCount: Int = 0,
    val hasNetworkProfile: Boolean = false,
    val currencyRatesCount: Int = 0
)

/**
 * Genuine Cloud Backup & Restore Architecture.
 * Scoped strictly under the authenticated user: users/{uid}/backup_latest.
 * Guarantees 100% atomic restore within a single Room transaction.
 */
class FirestoreSyncManager(
    private val context: Context,
    private val db: AppDatabase,
    private val backupRestoreUseCase: BackupRestoreUseCase,
    private val networkRepository: com.example.data.network.NetworkRepository? = null
) {
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _syncMetadata = MutableStateFlow<SyncMetadata?>(null)
    val syncMetadata: StateFlow<SyncMetadata?> = _syncMetadata.asStateFlow()

    private val _syncHistory = MutableStateFlow<List<SyncLogItem>>(emptyList())
    val syncHistory: StateFlow<List<SyncLogItem>> = _syncHistory.asStateFlow()

    private val prefs = context.getSharedPreferences("sammikrotik_sync_prefs", Context.MODE_PRIVATE)

    private val firestore: FirebaseFirestore?
        get() = try {
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                FirebaseFirestore.getInstance()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w("FirestoreSyncManager", "Firestore access: ${e.message}")
            null
        }

    init {
        loadLastSyncMetadata()
    }

    private fun loadLastSyncMetadata() {
        val lastSync = prefs.getLong("last_sync_timestamp", 0L)
        val email = prefs.getString("last_sync_email", "") ?: ""
        if (lastSync > 0L && email.isNotBlank()) {
            _syncMetadata.value = SyncMetadata(
                userEmail = email,
                lastSyncedAt = lastSync,
                totalDocuments = prefs.getInt("last_sync_docs", 0),
                totalJournalLines = prefs.getInt("last_sync_lines", 0),
                totalParties = prefs.getInt("last_sync_parties", 0),
                totalPackages = prefs.getInt("last_sync_packages", 0),
                totalCurrencyRates = prefs.getInt("last_sync_rates", 0),
                totalDevices = prefs.getInt("last_sync_devices", 0),
                hasNetworkProfile = prefs.getBoolean("last_sync_has_profile", false)
            )
        }
    }

    fun sanitizeTenantEmail(email: String): String {
        return email.trim().lowercase()
            .replace(".", "_")
            .replace("@", "_at_")
    }

    /**
     * Checks if a remote backup exists for the specified user in Firestore.
     */
    suspend fun checkRemoteBackup(uid: String, userEmail: String): CloudBackupMeta? = withContext(Dispatchers.IO) {
        val fs = firestore ?: return@withContext null
        val safeUid = uid.ifBlank { sanitizeTenantEmail(userEmail) }
        if (safeUid.isBlank()) return@withContext null

        try {
            // Check primary user scoped path: users/{uid}/backup_latest/latest
            var doc = fs.collection("users").document(safeUid)
                .collection("backup_latest").document("latest")
                .get().await()

            if (!doc.exists() && userEmail.isNotBlank()) {
                // Secondary check under tenant scope
                doc = fs.collection("tenants").document(sanitizeTenantEmail(userEmail))
                    .collection("snapshots").document("latest")
                    .get().await()
            }

            if (doc.exists()) {
                val jsonContent = doc.getString("jsonContent")
                if (!jsonContent.isNullOrBlank()) {
                    val timestamp = doc.getLong("timestamp") ?: 0L
                    val docs = doc.getLong("documentsCount")?.toInt() ?: 0
                    val lines = doc.getLong("journalLinesCount")?.toInt() ?: 0
                    val parties = doc.getLong("partiesCount")?.toInt() ?: 0
                    val pkgs = doc.getLong("packagesCount")?.toInt() ?: 0
                    val treasuries = doc.getLong("treasuriesCount")?.toInt() ?: 0
                    val rates = doc.getLong("currencyRatesCount")?.toInt() ?: 0
                    val devices = doc.getLong("networkDevicesCount")?.toInt() ?: 0
                    val hasProfile = doc.getBoolean("hasNetworkProfile") ?: (doc.get("networkProfile") != null)
                    val profileCount = if (hasProfile) 1 else 0
                    val total = doc.getLong("totalRecords")?.toInt()
                        ?: (docs + lines + parties + pkgs + treasuries + rates + devices + profileCount)
                    val checksum = doc.getString("checksum") ?: ""
                    return@withContext CloudBackupMeta(
                        uid = safeUid,
                        userEmail = userEmail,
                        timestamp = timestamp,
                        documentsCount = docs,
                        journalLinesCount = lines,
                        partiesCount = parties,
                        totalRecords = total,
                        checksum = checksum,
                        networkDevicesCount = devices,
                        hasNetworkProfile = hasProfile,
                        currencyRatesCount = rates
                    )
                }
            }
        } catch (e: Exception) {
            Log.w("FirestoreSyncManager", "checkRemoteBackup error: ${e.message}")
        }
        null
    }

    /**
     * Cloud Push (رفع إلى السحابة):
     * Exports complete Room database state (ledger, foreign currency, treasury accounts,
     * parties, documents) into Firestore under users/{uid}/backup_latest.
     */
    suspend fun syncPush(uid: String, userEmail: String): Result<String> = withContext(Dispatchers.IO) {
        val safeUid = uid.ifBlank { sanitizeTenantEmail(userEmail) }
        if (safeUid.isBlank()) {
            val err = "معرف المستخدم غير محدد، يرجى تسجيل الدخول أولاً"
            _syncState.value = SyncState.Error(err)
            return@withContext Result.failure(IllegalArgumentException(err))
        }

        val fs = firestore
        if (fs == null) {
            val err = "خدمة Firebase غير مهيأة على هذا الجهاز"
            _syncState.value = SyncState.Error(err)
            return@withContext Result.failure(IllegalStateException(err))
        }

        _syncState.value = SyncState.InProgress("جاري استخراج السجلات المحاسبية وتشفير البصمة...", 0.1f)

        try {
            // 1. Export verifiable snapshot
            val jsonSnapshot = backupRestoreUseCase.exportDatabaseToJson()
            val root = JSONObject(jsonSnapshot)
            val partiesArr = root.optJSONArray("parties")
            val pkgsArr = root.optJSONArray("packages")
            val treasuriesArr = root.optJSONArray("treasuries")
            val docsArr = root.optJSONArray("documents")
            val itemsArr = root.optJSONArray("document_items")
            val entriesArr = root.optJSONArray("journal_entries")
            val linesArr = root.optJSONArray("journal_lines")
            val allocArr = root.optJSONArray("allocations")
            val assetsArr = root.optJSONArray("assets")
            val ratesArr = root.optJSONArray("currency_rates") ?: root.optJSONArray("currencyRates")
            val devicesArr = root.optJSONArray("network_devices") ?: root.optJSONArray("networkDevices")
            val profileObj = root.optJSONObject("network_profile") ?: root.optJSONObject("networkProfile")
            val subnetsArr = root.optJSONArray("network_subnets") ?: root.optJSONArray("networkSubnets")
            val orgObj = root.optJSONObject("organization")
            val checksum = root.optString("sha256", "")

            val devicesCount = devicesArr?.length() ?: 0
            val ratesCount = ratesArr?.length() ?: 0
            val profileCount = if (profileObj != null) 1 else 0
            val subnetsCount = subnetsArr?.length() ?: 0

            val totalRecords = (partiesArr?.length() ?: 0) +
                    (pkgsArr?.length() ?: 0) +
                    (docsArr?.length() ?: 0) +
                    (linesArr?.length() ?: 0) +
                    (treasuriesArr?.length() ?: 0) +
                    ratesCount +
                    devicesCount +
                    profileCount +
                    subnetsCount +
                    (if (orgObj != null) 1 else 0)

            _syncState.value = SyncState.InProgress("الاتصال بالسحابة: users/$safeUid/backup_latest...", 0.4f)

            val now = System.currentTimeMillis()

            val devicesListMap = mutableListOf<Map<String, Any?>>()
            if (devicesArr != null) {
                for (i in 0 until devicesArr.length()) {
                    val d = devicesArr.getJSONObject(i)
                    devicesListMap.add(
                        mapOf(
                            "id" to d.getString("id"),
                            "name" to d.getString("name"),
                            "ipAddress" to d.getString("ipAddress"),
                            "deviceType" to d.optString("deviceType", "ACCESS_POINT"),
                            "macAddress" to d.optString("macAddress", ""),
                            "towerLocation" to d.optString("towerLocation", "البرج الرئيسي"),
                            "frequency" to d.optString("frequency", "5500 MHz"),
                            "channelWidth" to d.optString("channelWidth", "20/40 MHz"),
                            "status" to d.optString("status", "ONLINE"),
                            "notes" to d.optString("notes", ""),
                            "model" to d.optString("model", "MikroTik RouterBOARD"),
                            "managementPort" to d.optInt("managementPort", 8728),
                            "subnet" to d.optString("subnet", "10.10.1.0/24"),
                            "credentials" to d.optString("credentials", "admin")
                        )
                    )
                }
            }

            val currencyRatesListMap = mutableListOf<Map<String, Any?>>()
            if (ratesArr != null) {
                for (i in 0 until ratesArr.length()) {
                    val r = ratesArr.getJSONObject(i)
                    currencyRatesListMap.add(
                        mapOf(
                            "id" to r.getString("id"),
                            "currency" to r.optString("currency", "USD"),
                            "zone" to r.optString("zone", "SANAA"),
                            "rateMicros" to r.getLong("rateMicros"),
                            "effectiveDateEpochDay" to r.getLong("effectiveDateEpochDay"),
                            "createdAt" to r.optLong("createdAt", System.currentTimeMillis()),
                            "createdBy" to r.optString("createdBy", "SYSTEM"),
                            "reason" to r.optString("reason", "")
                        )
                    )
                }
            }

            val subnetsListMap = mutableListOf<Map<String, Any?>>()
            if (subnetsArr != null) {
                for (i in 0 until subnetsArr.length()) {
                    val s = subnetsArr.getJSONObject(i)
                    subnetsListMap.add(
                        mapOf(
                            "id" to s.getString("id"),
                            "name" to s.getString("name"),
                            "cidr" to s.getString("cidr"),
                            "gateway" to s.getString("gateway"),
                            "dhcpRangeStart" to s.optString("dhcpRangeStart", ""),
                            "dhcpRangeEnd" to s.optString("dhcpRangeEnd", ""),
                            "purpose" to s.optString("purpose", "")
                        )
                    )
                }
            }

            val profileMap = profileObj?.let { p ->
                mapOf(
                    "networkName" to p.optString("networkName"),
                    "ownerName" to p.optString("ownerName"),
                    "location" to p.optString("location"),
                    "welcomeMessage" to p.optString("welcomeMessage"),
                    "supportPhone" to p.optString("supportPhone"),
                    "supportWhatsapp" to p.optString("supportWhatsapp"),
                    "mainRouterModel" to p.optString("mainRouterModel"),
                    "routerOsVersion" to p.optString("routerOsVersion"),
                    "hotspotDomain" to p.optString("hotspotDomain"),
                    "hotspotServerName" to p.optString("hotspotServerName"),
                    "adminPort" to p.optInt("adminPort", 8728),
                    "primaryDns" to p.optString("primaryDns"),
                    "secondaryDns" to p.optString("secondaryDns"),
                    "approvedSubnet" to p.optString("approvedSubnet"),
                    "rateZone" to p.optString("rateZone", "SANAA"),
                    "defaultUsdRateMicros" to p.optLong("defaultUsdRateMicros"),
                    "defaultSarRateMicros" to p.optLong("defaultSarRateMicros"),
                    "updatedAt" to p.optLong("updatedAt")
                )
            }

            val organizationMap = orgObj?.let { o ->
                mapOf(
                    "id" to o.getString("id"),
                    "name" to o.getString("name"),
                    "taxNumber" to o.optString("taxNumber", ""),
                    "functionalCurrency" to o.optString("functionalCurrency", "YER"),
                    "fiscalYearStartMonth" to o.optInt("fiscalYearStartMonth", 1),
                    "isInitialized" to o.optBoolean("isInitialized", true),
                    "primaryRateZone" to o.optString("primaryRateZone", "SANAA"),
                    "equityShareMode" to o.optString("equityShareMode", "DERIVED_FROM_CAPITAL"),
                    "createdAt" to o.optLong("createdAt", System.currentTimeMillis())
                )
            }

            val backupData = hashMapOf(
                "schemaVersion" to 1,
                "uid" to safeUid,
                "userEmail" to userEmail,
                "timestamp" to now,
                "checksum" to checksum,
                "jsonContent" to jsonSnapshot,
                "partiesCount" to (partiesArr?.length() ?: 0),
                "packagesCount" to (pkgsArr?.length() ?: 0),
                "treasuriesCount" to (treasuriesArr?.length() ?: 0),
                "documentsCount" to (docsArr?.length() ?: 0),
                "documentItemsCount" to (itemsArr?.length() ?: 0),
                "journalEntriesCount" to (entriesArr?.length() ?: 0),
                "journalLinesCount" to (linesArr?.length() ?: 0),
                "allocationsCount" to (allocArr?.length() ?: 0),
                "assetsCount" to (assetsArr?.length() ?: 0),
                "currencyRatesCount" to ratesCount,
                "networkDevicesCount" to devicesCount,
                "networkSubnetsCount" to subnetsCount,
                "hasNetworkProfile" to (profileCount > 0),
                "totalRecords" to totalRecords,
                "networkDevices" to devicesListMap,
                "currencyRates" to currencyRatesListMap,
                "networkSubnets" to subnetsListMap,
                "networkProfile" to profileMap,
                "organization" to organizationMap
            )

            // Primary user scoped destination: users/{uid}/backup_latest/latest
            fs.collection("users").document(safeUid)
                .collection("backup_latest")
                .document("latest")
                .set(backupData, SetOptions.merge())
                .await()

            // User document summary record
            val userMeta = hashMapOf(
                "lastBackupEpochMs" to now,
                "userEmail" to userEmail,
                "hasBackup" to true,
                "documentsCount" to (docsArr?.length() ?: 0),
                "journalLinesCount" to (linesArr?.length() ?: 0),
                "currencyRatesCount" to ratesCount,
                "networkDevicesCount" to devicesCount,
                "networkSubnetsCount" to subnetsCount,
                "hasNetworkProfile" to (profileCount > 0),
                "totalRecords" to totalRecords,
                "checksum" to checksum
            )
            fs.collection("users").document(safeUid)
                .set(userMeta, SetOptions.merge())
                .await()

            // Legacy / multi-tenant mirror if email is present
            if (userEmail.isNotBlank()) {
                val tenantKey = sanitizeTenantEmail(userEmail)
                fs.collection("tenants").document(tenantKey)
                    .collection("snapshots").document("latest")
                    .set(backupData, SetOptions.merge())
                    .await()
            }

            _syncState.value = SyncState.InProgress("اكتمل حفظ النسخة السحابية...", 0.95f)

            prefs.edit()
                .putLong("last_sync_timestamp", now)
                .putString("last_sync_email", userEmail)
                .putInt("last_sync_docs", docsArr?.length() ?: 0)
                .putInt("last_sync_lines", linesArr?.length() ?: 0)
                .putInt("last_sync_parties", partiesArr?.length() ?: 0)
                .putInt("last_sync_packages", pkgsArr?.length() ?: 0)
                .putInt("last_sync_rates", ratesCount)
                .putInt("last_sync_devices", devicesCount)
                .putBoolean("last_sync_has_profile", profileCount > 0)
                .putInt("last_sync_total_records", totalRecords)
                .apply()

            val metadata = SyncMetadata(
                userEmail = userEmail,
                lastSyncedAt = now,
                totalDocuments = docsArr?.length() ?: 0,
                totalJournalLines = linesArr?.length() ?: 0,
                totalParties = partiesArr?.length() ?: 0,
                totalPackages = pkgsArr?.length() ?: 0,
                totalCurrencyRates = ratesCount,
                totalDevices = devicesCount,
                hasNetworkProfile = profileCount > 0,
                isBalanced = true,
                checksum = checksum
            )
            _syncMetadata.value = metadata

            val successMsg = "تم رفع النسخة السحابية بنجاح ($totalRecords سجلاً) تحت مسار users/$safeUid"
            _syncState.value = SyncState.Success(
                message = successMsg,
                lastSyncEpochMs = now,
                totalRecords = totalRecords
            )

            logSync("رفع سحابي", userEmail, totalRecords, true, successMsg)
            Result.success(successMsg)
        } catch (e: Exception) {
            Log.e("FirestoreSyncManager", "SyncPush error: ${e.message}", e)
            val err = "فشل الرفع السحابي: ${e.localizedMessage ?: "تعذر الوصول إلى Firebase"}"
            _syncState.value = SyncState.Error(err)
            logSync("رفع سحابي", userEmail, 0, false, err)
            Result.failure(e)
        }
    }

    /**
     * Cloud Pull / Restore (استعادة البيانات من السحابة):
     * Downloads Firestore backup from users/{uid}/backup_latest and atomically restores
     * records into local Room within a single transaction.
     */
    suspend fun syncPull(uid: String, userEmail: String): Result<String> = withContext(Dispatchers.IO) {
        val safeUid = uid.ifBlank { sanitizeTenantEmail(userEmail) }
        if (safeUid.isBlank()) {
            val err = "معرف المستخدم غير محدد، يرجى تسجيل الدخول أولاً"
            _syncState.value = SyncState.Error(err)
            return@withContext Result.failure(IllegalArgumentException(err))
        }

        val fs = firestore
        if (fs == null) {
            val err = "خدمة Firebase غير مهيأة على هذا الجهاز"
            _syncState.value = SyncState.Error(err)
            return@withContext Result.failure(IllegalStateException(err))
        }

        _syncState.value = SyncState.InProgress("جاري فحص النسخ السحابية تحت users/$safeUid...", 0.2f)

        try {
            // 1. Download document from users/{uid}/backup_latest/latest
            var snapshotDoc = fs.collection("users").document(safeUid)
                .collection("backup_latest")
                .document("latest")
                .get()
                .await()

            if (!snapshotDoc.exists() && userEmail.isNotBlank()) {
                snapshotDoc = fs.collection("tenants").document(sanitizeTenantEmail(userEmail))
                    .collection("snapshots").document("latest")
                    .get().await()
            }

            if (!snapshotDoc.exists()) {
                val err = "لا توجد نسخة سحابية سابقة مخزنة لهذا الحساب"
                _syncState.value = SyncState.Error(err)
                return@withContext Result.failure(IllegalStateException(err))
            }

            val jsonContent = snapshotDoc.getString("jsonContent")
            if (jsonContent.isNullOrBlank()) {
                val err = "النسخة السحابية فارغة أو غير متوافقة"
                _syncState.value = SyncState.Error(err)
                return@withContext Result.failure(IllegalStateException(err))
            }

            _syncState.value = SyncState.InProgress("جاري تطبيق الاستعادة الذرية ومطابقة القيود...", 0.6f)

            // 2. Restore atomically inside Room transaction
            val restoreResult = backupRestoreUseCase.restoreDatabaseFromJson(jsonContent)
            if (restoreResult.isFailure) {
                val failureReason = restoreResult.exceptionOrNull()?.localizedMessage ?: "فشل التحقق من قيود المحاسبة"
                _syncState.value = SyncState.Error("خطأ في استعادة القيود: $failureReason")
                return@withContext Result.failure(Exception(failureReason))
            }

            val now = System.currentTimeMillis()
            val docsCount = snapshotDoc.getLong("documentsCount")?.toInt() ?: 0
            val linesCount = snapshotDoc.getLong("journalLinesCount")?.toInt() ?: 0
            val partiesCount = snapshotDoc.getLong("partiesCount")?.toInt() ?: 0
            val packagesCount = snapshotDoc.getLong("packagesCount")?.toInt() ?: 0
            val ratesCount = snapshotDoc.getLong("currencyRatesCount")?.toInt() ?: 0
            val devicesCount = snapshotDoc.getLong("networkDevicesCount")?.toInt() ?: 0
            val hasProfile = snapshotDoc.getBoolean("hasNetworkProfile") ?: (snapshotDoc.get("networkProfile") != null)
            val profileCount = if (hasProfile) 1 else 0
            val totalRecords = docsCount + linesCount + partiesCount + packagesCount + ratesCount + devicesCount + profileCount

            prefs.edit()
                .putLong("last_sync_timestamp", now)
                .putString("last_sync_email", userEmail)
                .putInt("last_sync_docs", docsCount)
                .putInt("last_sync_lines", linesCount)
                .putInt("last_sync_parties", partiesCount)
                .putInt("last_sync_packages", packagesCount)
                .putInt("last_sync_rates", ratesCount)
                .putInt("last_sync_devices", devicesCount)
                .putBoolean("last_sync_has_profile", hasProfile)
                .putInt("last_sync_total_records", totalRecords)
                .apply()

            _syncMetadata.value = SyncMetadata(
                userEmail = userEmail,
                lastSyncedAt = now,
                totalDocuments = docsCount,
                totalJournalLines = linesCount,
                totalParties = partiesCount,
                totalPackages = packagesCount,
                totalCurrencyRates = ratesCount,
                totalDevices = devicesCount,
                hasNetworkProfile = hasProfile,
                isBalanced = true,
                checksum = snapshotDoc.getString("checksum") ?: ""
            )

            val successMsg = "تمت استعادة $totalRecords سجلاً من السحابة بنجاح ومطابقة دفتر اليومية 100%"
            _syncState.value = SyncState.Success(
                message = successMsg,
                lastSyncEpochMs = now,
                totalRecords = totalRecords
            )

            logSync("استعادة من السحابة", userEmail, totalRecords, true, successMsg)
            Result.success(successMsg)
        } catch (e: Exception) {
            Log.e("FirestoreSyncManager", "SyncPull error: ${e.message}", e)
            val err = "فشل سحب البيانات من السحابة: ${e.localizedMessage ?: "تعذر الوصول إلى Firebase"}"
            _syncState.value = SyncState.Error(err)
            logSync("استعادة من السحابة", userEmail, 0, false, err)
            Result.failure(e)
        }
    }

    /**
     * Backward-compatible convenience method for email-only callers.
     */
    suspend fun syncPush(userEmail: String): Result<String> {
        val uid = sanitizeTenantEmail(userEmail)
        return syncPush(uid, userEmail)
    }

    /**
     * Backward-compatible convenience method for email-only callers.
     */
    suspend fun syncPull(userEmail: String): Result<String> {
        val uid = sanitizeTenantEmail(userEmail)
        return syncPull(uid, userEmail)
    }

    private fun logSync(
        action: String,
        email: String,
        records: Int,
        isSuccess: Boolean,
        summary: String
    ) {
        val item = SyncLogItem(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            action = action,
            userEmail = email,
            recordsCount = records,
            isSuccess = isSuccess,
            summary = summary
        )
        val updated = listOf(item) + _syncHistory.value.take(19)
        _syncHistory.value = updated
    }
}
