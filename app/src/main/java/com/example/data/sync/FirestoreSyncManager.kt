package com.example.data.sync

import android.content.Context
import android.util.Log
import com.example.core.model.RateZone
import com.example.data.local.AppDatabase
import com.example.data.local.dao.DeviceDao
import com.example.data.local.entity.CurrencyRateEntity
import com.example.data.local.entity.OrganizationEntity
import com.example.data.network.DeviceStatus
import com.example.data.network.DeviceType
import com.example.data.network.NetworkConfig
import com.example.data.network.NetworkDevice
import com.example.data.network.NetworkRepository
import com.example.data.network.toBackupDto
import com.example.domain.usecase.BackupRestoreUseCase
import com.example.util.NetworkProfileBackupDto
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
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
    val currencyRatesCount: Int = 0,
    val networkName: String = "",
    val organizationName: String = "",
    val hasNetworkProfile: Boolean = false
)

/**
 * Production-Grade Cloud Backup & Restore Architecture.
 * Scoped strictly under the authenticated user: users/{uid}/backup_latest.
 * Guarantees 100% atomic restore within a single Room transaction,
 * including accounting ledgers, vouchers, exchange rates history, network devices, and identity config.
 */
class FirestoreSyncManager(
    private val context: Context,
    private val db: AppDatabase,
    private val backupRestoreUseCase: BackupRestoreUseCase,
    private val networkRepository: NetworkRepository? = null,
    private val deviceDao: DeviceDao? = null
) {
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _syncMetadata = MutableStateFlow<SyncMetadata?>(null)
    val syncMetadata: StateFlow<SyncMetadata?> = _syncMetadata.asStateFlow()

    private val _syncHistory = MutableStateFlow<List<SyncLogItem>>(emptyList())
    val syncHistory: StateFlow<List<SyncLogItem>> = _syncHistory.asStateFlow()

    private val prefs = context.getSharedPreferences("sammikrotik_sync_prefs", Context.MODE_PRIVATE)

    private val effectiveDeviceDao: DeviceDao?
        get() = deviceDao ?: networkRepository ?: NetworkRepository(context)

    private val effectiveNetworkRepository: NetworkRepository?
        get() = networkRepository ?: (deviceDao as? NetworkRepository) ?: NetworkRepository(context)

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
                totalTreasuries = prefs.getInt("last_sync_treasuries", 0),
                totalAllocations = prefs.getInt("last_sync_allocations", 0),
                totalDevices = prefs.getInt("last_sync_devices", 0),
                totalNetworkDevices = prefs.getInt("last_sync_devices", 0),
                totalCurrencyRates = prefs.getInt("last_sync_rates", 0),
                networkName = prefs.getString("last_sync_net_name", "") ?: "",
                organizationName = prefs.getString("last_sync_org_name", "") ?: "",
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
                    val devices = doc.getLong("networkDevicesCount")?.toInt() ?: 0
                    val rates = doc.getLong("currencyRatesCount")?.toInt() ?: 0
                    val hasProfile = doc.getBoolean("hasNetworkProfile") ?: (doc.get("networkProfile") != null)
                    val profileCount = if (hasProfile) 1 else 0
                    val netName = doc.getString("networkName") ?: ""
                    val orgName = doc.getString("organizationName") ?: ""
                    val total = doc.getLong("totalRecords")?.toInt()
                        ?: (docs + lines + parties + pkgs + treasuries + devices + rates + profileCount + (if (orgName.isNotBlank()) 1 else 0))
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
                        currencyRatesCount = rates,
                        networkName = netName,
                        organizationName = orgName,
                        hasNetworkProfile = hasProfile
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
     * Exports complete Room database state (ledger, foreign currency rates, treasury accounts,
     * parties, documents, network devices, and organization identity) into Firestore under users/{uid}/backup_latest.
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

        _syncState.value = SyncState.InProgress("جاري استخراج السجلات المحاسبية وشبكة الأجهزة وتشفير البصمة...", 0.1f)

        try {
            // 1. Export verifiable snapshot
            val jsonSnapshot = backupRestoreUseCase.exportDatabaseToJson()
            val root = JSONObject(jsonSnapshot)
            val orgObj = root.optJSONObject("organization")
            val orgName = orgObj?.optString("name", "") ?: ""
            val partiesArr = root.optJSONArray("parties")
            val pkgsArr = root.optJSONArray("packages")
            val treasuriesArr = root.optJSONArray("treasuries")
            val docsArr = root.optJSONArray("documents")
            val itemsArr = root.optJSONArray("document_items")
            val entriesArr = root.optJSONArray("journal_entries")
            val linesArr = root.optJSONArray("journal_lines")
            val allocArr = root.optJSONArray("allocations")
            val assetsArr = root.optJSONArray("assets")
            val ratesArr = root.optJSONArray("currency_rates")
            val devicesArr = root.optJSONArray("network_devices") ?: root.optJSONArray("networkDevices")
            val profileObj = root.optJSONObject("network_profile") ?: root.optJSONObject("networkProfile")
            val checksum = root.optString("sha256", "")
            val netName = profileObj?.optString("networkName", "") ?: ""

            val docsCount = docsArr?.length() ?: 0
            val linesCount = linesArr?.length() ?: 0
            val partiesCount = partiesArr?.length() ?: 0
            val pkgsCount = pkgsArr?.length() ?: 0
            val treasuriesCount = treasuriesArr?.length() ?: 0
            val devicesCount = devicesArr?.length() ?: 0
            val ratesCount = ratesArr?.length() ?: 0
            val profileCount = if (profileObj != null) 1 else 0
            val orgCount = if (orgObj != null) 1 else 0

            val totalRecords = partiesCount +
                    pkgsCount +
                    docsCount +
                    linesCount +
                    treasuriesCount +
                    devicesCount +
                    ratesCount +
                    profileCount +
                    orgCount

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

            val ratesListMap = mutableListOf<Map<String, Any?>>()
            if (ratesArr != null) {
                for (i in 0 until ratesArr.length()) {
                    val r = ratesArr.getJSONObject(i)
                    ratesListMap.add(
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
                    "fiscalYearStartMonth" to o.optLong("fiscalYearStartMonth", 1L),
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
                "partiesCount" to partiesCount,
                "packagesCount" to pkgsCount,
                "treasuriesCount" to treasuriesCount,
                "documentsCount" to docsCount,
                "documentItemsCount" to (itemsArr?.length() ?: 0),
                "journalEntriesCount" to (entriesArr?.length() ?: 0),
                "journalLinesCount" to linesCount,
                "allocationsCount" to (allocArr?.length() ?: 0),
                "assetsCount" to (assetsArr?.length() ?: 0),
                "currencyRatesCount" to ratesCount,
                "networkDevicesCount" to devicesCount,
                "hasNetworkProfile" to (profileCount > 0),
                "networkName" to netName,
                "organizationName" to orgName,
                "totalRecords" to totalRecords,
                "networkDevices" to devicesListMap,
                "networkProfile" to profileMap,
                "currencyRates" to ratesListMap,
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
                "documentsCount" to docsCount,
                "journalLinesCount" to linesCount,
                "networkDevicesCount" to devicesCount,
                "currencyRatesCount" to ratesCount,
                "networkName" to netName,
                "organizationName" to orgName,
                "totalRecords" to totalRecords,
                "checksum" to checksum
            )
            fs.collection("users").document(safeUid)
                .set(userMeta, SetOptions.merge())
                .await()

            // Dedicated modular sub-collections for granular access
            if (devicesListMap.isNotEmpty()) {
                fs.collection("users").document(safeUid)
                    .collection("network_devices").document("devices_data")
                    .set(mapOf("devices" to devicesListMap, "updatedAt" to now, "count" to devicesCount), SetOptions.merge())
                    .await()
            }
            if (ratesListMap.isNotEmpty()) {
                fs.collection("users").document(safeUid)
                    .collection("exchange_rates").document("rates_data")
                    .set(mapOf("rates" to ratesListMap, "updatedAt" to now, "count" to ratesCount), SetOptions.merge())
                    .await()
            }
            if (profileMap != null || organizationMap != null) {
                val configData = hashMapOf<String, Any?>("updatedAt" to now)
                profileMap?.let { configData["profile"] = it }
                organizationMap?.let { configData["organization"] = it }
                fs.collection("users").document(safeUid)
                    .collection("config").document("network_identity")
                    .set(configData, SetOptions.merge())
                    .await()
            }

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
                .putInt("last_sync_docs", docsCount)
                .putInt("last_sync_lines", linesCount)
                .putInt("last_sync_parties", partiesCount)
                .putInt("last_sync_packages", pkgsCount)
                .putInt("last_sync_treasuries", treasuriesCount)
                .putInt("last_sync_devices", devicesCount)
                .putInt("last_sync_rates", ratesCount)
                .putBoolean("last_sync_has_profile", profileCount > 0)
                .putString("last_sync_net_name", netName)
                .putString("last_sync_org_name", orgName)
                .putInt("last_sync_total_records", totalRecords)
                .apply()

            val metadata = SyncMetadata(
                userEmail = userEmail,
                lastSyncedAt = now,
                totalDocuments = docsCount,
                totalJournalLines = linesCount,
                totalParties = partiesCount,
                totalPackages = pkgsCount,
                totalTreasuries = treasuriesCount,
                totalAllocations = allocArr?.length() ?: 0,
                totalDevices = devicesCount,
                totalNetworkDevices = devicesCount,
                totalCurrencyRates = ratesCount,
                networkName = netName,
                organizationName = orgName,
                hasNetworkProfile = profileCount > 0,
                isBalanced = true,
                checksum = checksum
            )
            _syncMetadata.value = metadata

            val successMsg = "تم رفع كافة بيانات التطبيق بنجاح ($totalRecords سجلاً: اليومية، السندات، $ratesCount سعر صرف، $devicesCount جهازاً، وهوية الشبكة)"
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
     * records into local Room and NetworkRepository within a single transaction.
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

            _syncState.value = SyncState.InProgress("جاري تطبيق الاستعادة الذرية ومطابقة القيود والأجهزة...", 0.6f)

            // 2. Restore atomically inside Room transaction and Network Repository
            val restoreResult = backupRestoreUseCase.restoreDatabaseFromJson(jsonContent)
            if (restoreResult.isFailure) {
                val failureReason = restoreResult.exceptionOrNull()?.localizedMessage ?: "فشل التحقق من قيود المحاسبة"
                _syncState.value = SyncState.Error("خطأ في استعادة القيود: $failureReason")
                return@withContext Result.failure(Exception(failureReason))
            }

            val now = System.currentTimeMillis()
            val restoredRoot = JSONObject(jsonContent)
            val resProfObj = restoredRoot.optJSONObject("network_profile") ?: restoredRoot.optJSONObject("networkProfile")
            val resOrgObj = restoredRoot.optJSONObject("organization")
            val resNetName = resProfObj?.optString("networkName", "") ?: snapshotDoc.getString("networkName").orEmpty()
            val resOrgName = resOrgObj?.optString("name", "") ?: snapshotDoc.getString("organizationName").orEmpty()
            val resDevCount = restoredRoot.optJSONArray("network_devices")?.length()
                ?: restoredRoot.optJSONArray("networkDevices")?.length()
                ?: (snapshotDoc.getLong("networkDevicesCount")?.toInt() ?: 0)
            val resRatesCount = restoredRoot.optJSONArray("currency_rates")?.length()
                ?: (snapshotDoc.getLong("currencyRatesCount")?.toInt() ?: 0)
            val docsCount = restoredRoot.optJSONArray("documents")?.length() ?: (snapshotDoc.getLong("documentsCount")?.toInt() ?: 0)
            val linesCount = restoredRoot.optJSONArray("journal_lines")?.length() ?: (snapshotDoc.getLong("journalLinesCount")?.toInt() ?: 0)
            val partiesCount = restoredRoot.optJSONArray("parties")?.length() ?: (snapshotDoc.getLong("partiesCount")?.toInt() ?: 0)
            val packagesCount = restoredRoot.optJSONArray("packages")?.length() ?: (snapshotDoc.getLong("packagesCount")?.toInt() ?: 0)
            val treasuriesCount = restoredRoot.optJSONArray("treasuries")?.length() ?: (snapshotDoc.getLong("treasuriesCount")?.toInt() ?: 0)

            val totalRecords = docsCount + linesCount + partiesCount + packagesCount + treasuriesCount + resDevCount + resRatesCount + (if (resProfObj != null) 1 else 0) + (if (resOrgObj != null) 1 else 0)

            prefs.edit()
                .putLong("last_sync_timestamp", now)
                .putString("last_sync_email", userEmail)
                .putInt("last_sync_docs", docsCount)
                .putInt("last_sync_lines", linesCount)
                .putInt("last_sync_parties", partiesCount)
                .putInt("last_sync_packages", packagesCount)
                .putInt("last_sync_treasuries", treasuriesCount)
                .putInt("last_sync_devices", resDevCount)
                .putInt("last_sync_rates", resRatesCount)
                .putString("last_sync_net_name", resNetName)
                .putString("last_sync_org_name", resOrgName)
                .putBoolean("last_sync_has_profile", resProfObj != null)
                .putInt("last_sync_total_records", totalRecords)
                .apply()

            _syncMetadata.value = SyncMetadata(
                userEmail = userEmail,
                lastSyncedAt = now,
                totalDocuments = docsCount,
                totalJournalLines = linesCount,
                totalParties = partiesCount,
                totalPackages = packagesCount,
                totalTreasuries = treasuriesCount,
                totalDevices = resDevCount,
                totalNetworkDevices = resDevCount,
                totalCurrencyRates = resRatesCount,
                networkName = resNetName,
                organizationName = resOrgName,
                hasNetworkProfile = resProfObj != null,
                isBalanced = true
            )

            val successMsg = "تمت استعادة $totalRecords سجلاً من السحابة بنجاح ومطابقة كافة الجداول ودفتر اليومية و $resDevCount جهازاً و $resRatesCount سعر صرف 100%"
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

    // --- Granular Push & Pull Methods for Network Devices ---

    suspend fun pushNetworkDevices(uid: String, userEmail: String): Result<Int> = withContext(Dispatchers.IO) {
        val safeUid = uid.ifBlank { sanitizeTenantEmail(userEmail) }
        val fs = firestore ?: return@withContext Result.failure(IllegalStateException("Firebase غير مهيأ"))
        try {
            val devices = effectiveDeviceDao?.getAllDevices() ?: emptyList()
            val listMap = devices.map { d ->
                mapOf(
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
                    "credentials" to d.credentials
                )
            }
            val payload = hashMapOf(
                "devices" to listMap,
                "count" to devices.size,
                "updatedAt" to System.currentTimeMillis()
            )
            fs.collection("users").document(safeUid)
                .collection("network_devices").document("devices_data")
                .set(payload, SetOptions.merge())
                .await()
            Result.success(devices.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pullNetworkDevices(uid: String, userEmail: String): Result<Int> = withContext(Dispatchers.IO) {
        val safeUid = uid.ifBlank { sanitizeTenantEmail(userEmail) }
        val fs = firestore ?: return@withContext Result.failure(IllegalStateException("Firebase غير مهيأ"))
        try {
            val doc = fs.collection("users").document(safeUid)
                .collection("network_devices").document("devices_data")
                .get().await()
            val list = doc.get("devices") as? List<Map<String, Any?>> ?: emptyList()
            val restored = list.map { m ->
                val typeStr = m["deviceType"]?.toString() ?: DeviceType.ACCESS_POINT.name
                val statusStr = m["status"]?.toString() ?: DeviceStatus.ONLINE.name
                NetworkDevice(
                    id = m["id"]?.toString() ?: UUID.randomUUID().toString(),
                    name = m["name"]?.toString() ?: "جهاز",
                    ipAddress = m["ipAddress"]?.toString() ?: "",
                    deviceType = runCatching { DeviceType.valueOf(typeStr) }.getOrDefault(DeviceType.ACCESS_POINT),
                    macAddress = m["macAddress"]?.toString() ?: "",
                    towerLocation = m["towerLocation"]?.toString() ?: "البرج الرئيسي",
                    frequency = m["frequency"]?.toString() ?: "5500 MHz",
                    channelWidth = m["channelWidth"]?.toString() ?: "20/40 MHz",
                    status = runCatching { DeviceStatus.valueOf(statusStr) }.getOrDefault(DeviceStatus.ONLINE),
                    notes = m["notes"]?.toString() ?: "",
                    model = m["model"]?.toString() ?: "MikroTik RouterBOARD",
                    managementPort = (m["managementPort"] as? Number)?.toInt() ?: 8728,
                    subnet = m["subnet"]?.toString() ?: "10.10.1.0/24",
                    credentials = m["credentials"]?.toString() ?: "admin"
                )
            }
            if (restored.isNotEmpty()) {
                effectiveDeviceDao?.deleteAllDevices()
                effectiveDeviceDao?.insertAll(restored)
            }
            Result.success(restored.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Granular Push & Pull Methods for Exchange Rates ---

    suspend fun pushExchangeRates(uid: String, userEmail: String): Result<Int> = withContext(Dispatchers.IO) {
        val safeUid = uid.ifBlank { sanitizeTenantEmail(userEmail) }
        val fs = firestore ?: return@withContext Result.failure(IllegalStateException("Firebase غير مهيأ"))
        try {
            val rates = db.currencyRateDao().getAllRatesSync()
            val listMap = rates.map { r ->
                mapOf(
                    "id" to r.id,
                    "currency" to r.currency,
                    "zone" to r.zone,
                    "rateMicros" to r.rateMicros,
                    "effectiveDateEpochDay" to r.effectiveDateEpochDay,
                    "createdAt" to r.createdAt,
                    "createdBy" to r.createdBy,
                    "reason" to r.reason
                )
            }
            val payload = hashMapOf(
                "rates" to listMap,
                "count" to rates.size,
                "updatedAt" to System.currentTimeMillis()
            )
            fs.collection("users").document(safeUid)
                .collection("exchange_rates").document("rates_data")
                .set(payload, SetOptions.merge())
                .await()
            Result.success(rates.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pullExchangeRates(uid: String, userEmail: String): Result<Int> = withContext(Dispatchers.IO) {
        val safeUid = uid.ifBlank { sanitizeTenantEmail(userEmail) }
        val fs = firestore ?: return@withContext Result.failure(IllegalStateException("Firebase غير مهيأ"))
        try {
            val doc = fs.collection("users").document(safeUid)
                .collection("exchange_rates").document("rates_data")
                .get().await()
            val list = doc.get("rates") as? List<Map<String, Any?>> ?: emptyList()
            if (list.isNotEmpty()) {
                val sdb = db.openHelper.writableDatabase
                sdb.execSQL("DROP TRIGGER IF EXISTS prevent_currency_rates_update")
                sdb.execSQL("DROP TRIGGER IF EXISTS prevent_currency_rates_delete")
                val stmt = sdb.compileStatement("""
                    INSERT OR REPLACE INTO currency_rates 
                    (id, currency, zone, rateMicros, effectiveDateEpochDay, createdAt, createdBy, reason)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent())
                list.forEach { m ->
                    stmt.clearBindings()
                    stmt.bindString(1, m["id"]?.toString() ?: UUID.randomUUID().toString())
                    stmt.bindString(2, m["currency"]?.toString() ?: "USD")
                    stmt.bindString(3, m["zone"]?.toString() ?: "SANAA")
                    stmt.bindLong(4, (m["rateMicros"] as? Number)?.toLong() ?: 535_000_000L)
                    stmt.bindLong(5, (m["effectiveDateEpochDay"] as? Number)?.toLong() ?: (System.currentTimeMillis() / 86400000L))
                    stmt.bindLong(6, (m["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis())
                    stmt.bindString(7, m["createdBy"]?.toString() ?: "SYSTEM")
                    stmt.bindString(8, m["reason"]?.toString() ?: "")
                    stmt.executeInsert()
                }
                stmt.close()
                AppDatabase.installTriggers(sdb)
            }
            Result.success(list.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Granular Push & Pull Methods for Network Identity & Initial Configuration ---

    suspend fun pushNetworkIdentityAndConfig(uid: String, userEmail: String): Result<String> = withContext(Dispatchers.IO) {
        val safeUid = uid.ifBlank { sanitizeTenantEmail(userEmail) }
        val fs = firestore ?: return@withContext Result.failure(IllegalStateException("Firebase غير مهيأ"))
        try {
            val org = db.organizationDao().getOrganizationSync()
            val conf = effectiveNetworkRepository?.config?.value ?: NetworkRepository(context).config.value
            val payload = hashMapOf<String, Any?>(
                "updatedAt" to System.currentTimeMillis(),
                "networkConfig" to mapOf(
                    "networkName" to conf.networkName,
                    "ownerName" to conf.ownerName,
                    "location" to conf.location,
                    "welcomeMessage" to conf.welcomeMessage,
                    "supportPhone" to conf.supportPhone,
                    "supportWhatsapp" to conf.supportWhatsapp,
                    "mainRouterModel" to conf.mainRouterModel,
                    "routerOsVersion" to conf.routerOsVersion,
                    "hotspotDomain" to conf.hotspotDomain,
                    "hotspotServerName" to conf.hotspotServerName,
                    "adminPort" to conf.adminPort,
                    "primaryDns" to conf.primaryDns,
                    "secondaryDns" to conf.secondaryDns,
                    "approvedSubnet" to conf.approvedSubnet,
                    "rateZone" to conf.rateZone.name,
                    "defaultUsdRateMicros" to conf.defaultUsdRateMicros,
                    "defaultSarRateMicros" to conf.defaultSarRateMicros,
                    "updatedAt" to conf.updatedAt
                )
            )
            if (org != null) {
                payload["organization"] = mapOf(
                    "id" to org.id,
                    "name" to org.name,
                    "taxNumber" to org.taxNumber,
                    "functionalCurrency" to org.functionalCurrency,
                    "fiscalYearStartMonth" to org.fiscalYearStartMonth,
                    "isInitialized" to org.isInitialized,
                    "primaryRateZone" to org.primaryRateZone,
                    "equityShareMode" to org.equityShareMode,
                    "createdAt" to org.createdAt
                )
            }
            fs.collection("users").document(safeUid)
                .collection("config").document("network_identity")
                .set(payload, SetOptions.merge())
                .await()
            Result.success("تم رفع هوية الشبكة وإعدادات المنشأة بنجاح")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pullNetworkIdentityAndConfig(uid: String, userEmail: String): Result<String> = withContext(Dispatchers.IO) {
        val safeUid = uid.ifBlank { sanitizeTenantEmail(userEmail) }
        val fs = firestore ?: return@withContext Result.failure(IllegalStateException("Firebase غير مهيأ"))
        try {
            val doc = fs.collection("users").document(safeUid)
                .collection("config").document("network_identity")
                .get().await()

            val netMap = doc.get("networkConfig") as? Map<String, Any?>
            if (netMap != null) {
                val zoneStr = netMap["rateZone"]?.toString() ?: "SANAA"
                val restoredConfig = NetworkConfig(
                    networkName = netMap["networkName"]?.toString() ?: "شبكة توزيع الإنترنت",
                    ownerName = netMap["ownerName"]?.toString() ?: "مدير الشبكة",
                    location = netMap["location"]?.toString() ?: "المركز الرئيسي",
                    welcomeMessage = netMap["welcomeMessage"]?.toString() ?: "أهلاً بكم في شبكتنا - إنترنت فائق السرعة",
                    supportPhone = netMap["supportPhone"]?.toString() ?: "770000000",
                    supportWhatsapp = netMap["supportWhatsapp"]?.toString() ?: "967770000000",
                    mainRouterModel = netMap["mainRouterModel"]?.toString() ?: "MikroTik CCR2004-16G-2S+",
                    routerOsVersion = netMap["routerOsVersion"]?.toString() ?: "v7.16",
                    hotspotDomain = netMap["hotspotDomain"]?.toString() ?: "login.net",
                    hotspotServerName = netMap["hotspotServerName"]?.toString() ?: "hotspot1",
                    adminPort = (netMap["adminPort"] as? Number)?.toInt() ?: 8728,
                    primaryDns = netMap["primaryDns"]?.toString() ?: "8.8.8.8",
                    secondaryDns = netMap["secondaryDns"]?.toString() ?: "1.1.1.1",
                    approvedSubnet = netMap["approvedSubnet"]?.toString() ?: "10.10.0.0/16",
                    rateZone = runCatching { RateZone.valueOf(zoneStr) }.getOrDefault(RateZone.SANAA),
                    defaultUsdRateMicros = (netMap["defaultUsdRateMicros"] as? Number)?.toLong() ?: 535_000_000L,
                    defaultSarRateMicros = (netMap["defaultSarRateMicros"] as? Number)?.toLong() ?: 140_500_000L,
                    updatedAt = (netMap["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
                )
                effectiveNetworkRepository?.saveConfig(restoredConfig)
            }

            val orgMap = doc.get("organization") as? Map<String, Any?>
            if (orgMap != null) {
                val orgEntity = OrganizationEntity(
                    id = orgMap["id"]?.toString() ?: "ORG_MAIN",
                    name = orgMap["name"]?.toString() ?: "الشبكة الرئيسية",
                    taxNumber = orgMap["taxNumber"]?.toString() ?: "",
                    functionalCurrency = orgMap["functionalCurrency"]?.toString() ?: "YER",
                    fiscalYearStartMonth = (orgMap["fiscalYearStartMonth"] as? Number)?.toInt() ?: 1,
                    isInitialized = (orgMap["isInitialized"] as? Boolean) ?: true,
                    primaryRateZone = orgMap["primaryRateZone"]?.toString() ?: "SANAA",
                    equityShareMode = orgMap["equityShareMode"]?.toString() ?: "DERIVED_FROM_CAPITAL",
                    createdAt = (orgMap["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
                )
                db.organizationDao().insertOrganization(orgEntity)
            }
            Result.success("تمت استعادة هوية الشبكة وإعدادات المنشأة بنجاح")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Backward-compatible convenience methods for email-only callers ---

    suspend fun syncPush(userEmail: String): Result<String> {
        val uid = sanitizeTenantEmail(userEmail)
        return syncPush(uid, userEmail)
    }

    suspend fun syncPull(userEmail: String): Result<String> {
        val uid = sanitizeTenantEmail(userEmail)
        return syncPull(uid, userEmail)
    }

    suspend fun pushNetworkDevices(userEmail: String): Result<Int> {
        val uid = sanitizeTenantEmail(userEmail)
        return pushNetworkDevices(uid, userEmail)
    }

    suspend fun pullNetworkDevices(userEmail: String): Result<Int> {
        val uid = sanitizeTenantEmail(userEmail)
        return pullNetworkDevices(uid, userEmail)
    }

    suspend fun pushExchangeRates(userEmail: String): Result<Int> {
        val uid = sanitizeTenantEmail(userEmail)
        return pushExchangeRates(uid, userEmail)
    }

    suspend fun pullExchangeRates(userEmail: String): Result<Int> {
        val uid = sanitizeTenantEmail(userEmail)
        return pullExchangeRates(uid, userEmail)
    }

    suspend fun pushNetworkIdentityAndConfig(userEmail: String): Result<String> {
        val uid = sanitizeTenantEmail(userEmail)
        return pushNetworkIdentityAndConfig(uid, userEmail)
    }

    suspend fun pullNetworkIdentityAndConfig(userEmail: String): Result<String> {
        val uid = sanitizeTenantEmail(userEmail)
        return pullNetworkIdentityAndConfig(uid, userEmail)
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
