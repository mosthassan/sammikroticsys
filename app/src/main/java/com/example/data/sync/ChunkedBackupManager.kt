package com.example.data.sync

import android.content.Context
import android.util.Log
import com.example.data.ledger.LedgerInvariants
import com.example.data.local.AppDatabase
import com.example.domain.usecase.BackupRestoreUseCase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

/**
 * Manifest representation of a chunked cloud backup.
 * Stored under: users/{uid}/backups/{backupId}
 */
data class BackupManifest(
    val backupId: String = "",
    val createdAt: Long = 0L,
    val deviceId: String = "",
    val appVersion: String = "1.0.0",
    val dbSchemaVersion: Int = 4,
    val chunkCount: Int = 0,
    val sha256: String = "",
    val documentsCount: Int = 0,
    val journalLinesCount: Int = 0,
    val partiesCount: Int = 0,
    val lastEntryNumber: Long = 0L,
    val isEncrypted: Boolean = false
)

/**
 * Enterprise Production-Grade Chunked Cloud Backup Manager.
 * Complies with Firestore 1MB limits by chunking payloads (<= 500KB) under users/{uid}/backups/{backupId}/chunks/{n}.
 * Keeps the 5 most recent backups, validates ledger invariants before upload and after reassembly,
 * supports passphrase encryption, and provides safe restore with local safety snapshots.
 */
class ChunkedBackupManager(
    private val context: Context,
    private val db: AppDatabase,
    private val backupRestoreUseCase: BackupRestoreUseCase
) {
    companion object {
        private const val TAG = "ChunkedBackupManager"
        const val CURRENT_SCHEMA_VERSION = 4
        private const val MAX_RETAINED_BACKUPS = 5
        private const val PREFS_NAME = "sammikrotik_backup_prefs"
        private const val KEY_LAST_BACKUP_ID = "last_backup_id"
        private const val KEY_LAST_BACKUP_TIME = "last_backup_time"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val firestore: FirebaseFirestore? by lazy {
        try {
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                FirebaseFirestore.getInstance()
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "Firestore initialization unavailable: ${e.message}")
            null
        }
    }

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _syncMetadata = MutableStateFlow<SyncMetadata?>(null)
    val syncMetadata: StateFlow<SyncMetadata?> = _syncMetadata.asStateFlow()

    private val _syncHistory = MutableStateFlow<List<SyncLogItem>>(emptyList())
    val syncHistory: StateFlow<List<SyncLogItem>> = _syncHistory.asStateFlow()

    init {
        loadLastMetadata()
    }

    private fun loadLastMetadata() {
        val lastTime = prefs.getLong(KEY_LAST_BACKUP_TIME, 0L)
        if (lastTime > 0L) {
            _syncMetadata.value = SyncMetadata(
                lastSyncedAt = lastTime,
                checksum = prefs.getString(KEY_LAST_BACKUP_ID, "") ?: ""
            )
        }
    }

    private fun getAuthenticatedUid(explicitUid: String? = null): String {
        return try {
            FirebaseAuth.getInstance().currentUser?.uid?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        } ?: explicitUid?.trim()?.takeIf { !it.isNullOrBlank() }
        ?: ""
    }

    /**
     * Uploads database as a chunked, verified cloud backup.
     */
    suspend fun uploadBackup(
        explicitUid: String? = null,
        passphrase: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val uid = getAuthenticatedUid(explicitUid)
        if (uid.isBlank()) {
            _syncState.value = SyncState.Error("يجب تسجيل الدخول بحساب Google لرفع النسخة الاحتياطية")
            return@withContext Result.failure(IllegalStateException("User not authenticated"))
        }

        val fs = firestore ?: run {
            _syncState.value = SyncState.Error("خدمات Firebase غير مهيأة أو غير متوفرة")
            return@withContext Result.failure(IllegalStateException("Firestore unavailable"))
        }

        try {
            _syncState.value = SyncState.InProgress("التحقق من صحة وتوازن القيود المحاسبية...", 0.1f)
            // 1. Verify ledger invariants before uploading anything
            val validation = LedgerInvariants(db).verifyAll()
            if (!validation.isValid) {
                val err = "تم رفض الرفع: القيود المحاسبية غير متوازنة محلياً (${validation.violations.firstOrNull()?.details})"
                _syncState.value = SyncState.Error(err)
                return@withContext Result.failure(IllegalStateException(err))
            }

            _syncState.value = SyncState.InProgress("تصدير وضغط قاعدة البيانات...", 0.3f)
            val jsonSnapshot = backupRestoreUseCase.exportDatabaseToJson()
            val rawCompressed = ChunkedBackupHelper.compressGzip(jsonSnapshot)
            val sha256Hex = ChunkedBackupHelper.computeSha256(rawCompressed)

            val finalPayload = if (!passphrase.isNullOrBlank()) {
                ChunkedBackupHelper.encryptWithPassphrase(rawCompressed, passphrase)
            } else {
                rawCompressed
            }

            val chunks = ChunkedBackupHelper.splitIntoChunks(finalPayload)
            val backupId = "backup_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"

            // Extract entity counts for manifest
            val rootObj = JSONObject(jsonSnapshot)
            val docCount = rootObj.optJSONArray("documents")?.length() ?: 0
            val lineCount = rootObj.optJSONArray("journal_lines")?.length() ?: 0
            val partyCount = rootObj.optJSONArray("parties")?.length() ?: 0
            val entriesArr = rootObj.optJSONArray("journal_entries")
            var maxEntryNum = 0L
            if (entriesArr != null) {
                for (i in 0 until entriesArr.length()) {
                    val num = entriesArr.optJSONObject(i)?.optLong("entryNumber", 0L) ?: 0L
                    if (num > maxEntryNum) maxEntryNum = num
                }
            }

            _syncState.value = SyncState.InProgress("رفع أجزاء النسخة الاحتياطية (${chunks.size} أجزاء)...", 0.5f)
            val backupDocRef = fs.collection("users").document(uid)
                .collection("backups").document(backupId)

            // Upload chunks
            val chunksCollection = backupDocRef.collection("chunks")
            for ((index, chunkData) in chunks.withIndex()) {
                val chunkMap = mapOf(
                    "index" to index,
                    "data" to chunkData,
                    "sizeBytes" to chunkData.length
                )
                chunksCollection.document(index.toString()).set(chunkMap).await()
                val progress = 0.5f + (0.35f * (index + 1) / chunks.size)
                _syncState.value = SyncState.InProgress("رفع الجزء ${index + 1} من ${chunks.size}...", progress)
            }

            // Upload Manifest
            val manifestData = mapOf(
                "backupId" to backupId,
                "createdAt" to System.currentTimeMillis(),
                "deviceId" to android.os.Build.MODEL,
                "appVersion" to "1.0.0",
                "dbSchemaVersion" to CURRENT_SCHEMA_VERSION,
                "chunkCount" to chunks.size,
                "sha256" to sha256Hex,
                "documentsCount" to docCount,
                "journalLinesCount" to lineCount,
                "partiesCount" to partyCount,
                "lastEntryNumber" to maxEntryNum,
                "isEncrypted" to !passphrase.isNullOrBlank()
            )
            backupDocRef.set(manifestData, SetOptions.merge()).await()

            // Prune older backups: keep maximum 5
            pruneOldBackups(uid, fs)

            // Save local metadata
            prefs.edit()
                .putString(KEY_LAST_BACKUP_ID, backupId)
                .putLong(KEY_LAST_BACKUP_TIME, System.currentTimeMillis())
                .apply()

            loadLastMetadata()

            val msg = "تم رفع النسخة الاحتياطية بنجاح ($docCount وثيقة، $lineCount سطر قيد، ${chunks.size} أجزاء)"
            _syncState.value = SyncState.Success(msg, System.currentTimeMillis(), docCount + lineCount)
            Result.success(msg)
        } catch (e: Exception) {
            Log.e(TAG, "Upload failed: ${e.message}", e)
            _syncState.value = SyncState.Error("فشل رفع النسخة الاحتياطية: ${e.localizedMessage ?: e.message}")
            Result.failure(e)
        }
    }

    /**
     * Lists existing cloud backups for the user.
     */
    suspend fun listBackups(explicitUid: String? = null): List<BackupManifest> = withContext(Dispatchers.IO) {
        val uid = getAuthenticatedUid(explicitUid)
        if (uid.isBlank()) return@withContext emptyList()
        val fs = firestore ?: return@withContext emptyList()

        try {
            val snapshot = fs.collection("users").document(uid)
                .collection("backups")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(10)
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                val backupId = doc.getString("backupId") ?: doc.id
                val createdAt = doc.getLong("createdAt") ?: 0L
                val chunkCount = doc.getLong("chunkCount")?.toInt() ?: 0
                val sha256 = doc.getString("sha256") ?: ""
                val schemaVer = doc.getLong("dbSchemaVersion")?.toInt() ?: 1
                val isEncrypted = doc.getBoolean("isEncrypted") ?: false

                if (chunkCount > 0) {
                    BackupManifest(
                        backupId = backupId,
                        createdAt = createdAt,
                        deviceId = doc.getString("deviceId") ?: "",
                        appVersion = doc.getString("appVersion") ?: "1.0.0",
                        dbSchemaVersion = schemaVer,
                        chunkCount = chunkCount,
                        sha256 = sha256,
                        documentsCount = doc.getLong("documentsCount")?.toInt() ?: 0,
                        journalLinesCount = doc.getLong("journalLinesCount")?.toInt() ?: 0,
                        partiesCount = doc.getLong("partiesCount")?.toInt() ?: 0,
                        lastEntryNumber = doc.getLong("lastEntryNumber") ?: 0L,
                        isEncrypted = isEncrypted
                    )
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error listing backups: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Downloads and restores a cloud backup atomically with safety snapshot and integrity checks.
     */
    suspend fun restoreBackup(
        backupId: String,
        explicitUid: String? = null,
        passphrase: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val uid = getAuthenticatedUid(explicitUid)
        if (uid.isBlank()) {
            _syncState.value = SyncState.Error("يجب تسجيل الدخول لاستعادة النسخة")
            return@withContext Result.failure(IllegalStateException("User not authenticated"))
        }

        val fs = firestore ?: run {
            _syncState.value = SyncState.Error("خدمات Firebase غير متوفرة")
            return@withContext Result.failure(IllegalStateException("Firestore unavailable"))
        }

        // 1. Create a local safety backup snapshot BEFORE touching anything
        val localSafetySnapshot = try {
            backupRestoreUseCase.exportDatabaseToJson()
        } catch (e: Exception) {
            Log.w(TAG, "Failed creating safety snapshot: ${e.message}")
            null
        }

        try {
            _syncState.value = SyncState.InProgress("تحميل تفاصيل النسخة الاحتياطية...", 0.1f)
            val backupDoc = fs.collection("users").document(uid)
                .collection("backups").document(backupId)
                .get().await()

            if (!backupDoc.exists()) {
                val err = "النسخة الاحتياطية المطلوبة غير موجودة في السحابة"
                _syncState.value = SyncState.Error(err)
                return@withContext Result.failure(IllegalArgumentException(err))
            }

            val schemaVer = backupDoc.getLong("dbSchemaVersion")?.toInt() ?: 1
            if (schemaVer > CURRENT_SCHEMA_VERSION) {
                val err = "النسخة الاحتياطية أحدث من إصدار التطبيق الحالي (الإصدار: $schemaVer). يرجى تحديث التطبيق أولاً."
                _syncState.value = SyncState.Error(err)
                return@withContext Result.failure(IllegalStateException(err))
            }

            val chunkCount = backupDoc.getLong("chunkCount")?.toInt() ?: 0
            val expectedSha256 = backupDoc.getString("sha256") ?: ""
            val isEncrypted = backupDoc.getBoolean("isEncrypted") ?: false

            if (isEncrypted && passphrase.isNullOrBlank()) {
                val err = "هذه النسخة مشفرة بعبارة مرور. يرجى إدخال عبارة المرور للمتابعة."
                _syncState.value = SyncState.Error(err)
                return@withContext Result.failure(IllegalArgumentException(err))
            }

            _syncState.value = SyncState.InProgress("تحميل أجزاء النسخة ($chunkCount أجزاء)...", 0.2f)
            val chunksCollection = backupDoc.reference.collection("chunks")
            val chunkStrings = mutableListOf<String>()

            for (i in 0 until chunkCount) {
                val chunkSnap = chunksCollection.document(i.toString()).get().await()
                val chunkData = chunkSnap.getString("data")
                    ?: error("الجزء رقم $i مفقود أو تالف")
                chunkStrings.add(chunkData)
                val progress = 0.2f + (0.4f * (i + 1) / chunkCount)
                _syncState.value = SyncState.InProgress("تحميل الجزء ${i + 1} من $chunkCount...", progress)
            }

            _syncState.value = SyncState.InProgress("تجميع الأجزاء والتحقق من التشفير والبصمة...", 0.65f)
            val assembledBytes = ChunkedBackupHelper.assembleChunks(chunkStrings)

            val decryptedBytes = if (isEncrypted) {
                try {
                    ChunkedBackupHelper.decryptWithPassphrase(assembledBytes, passphrase!!)
                } catch (e: Exception) {
                    val err = "عبارة المرور غير صحيحة أو البيانات مشوهة"
                    _syncState.value = SyncState.Error(err)
                    return@withContext Result.failure(IllegalArgumentException(err))
                }
            } else {
                assembledBytes
            }

            // Verify SHA-256 integrity
            val actualSha256 = ChunkedBackupHelper.computeSha256(decryptedBytes)
            if (actualSha256 != expectedSha256) {
                val err = "فشل التحقق من سلامة البصمة (SHA-256 Mismatch): الملف تالف أو معدّل"
                _syncState.value = SyncState.Error(err)
                return@withContext Result.failure(IllegalStateException(err))
            }

            _syncState.value = SyncState.InProgress("فك ضغط واستعادة قاعدة البيانات...", 0.8f)
            val rawJson = ChunkedBackupHelper.decompressGzip(decryptedBytes)

            // Perform atomic restore
            val restoreResult = backupRestoreUseCase.restoreDatabaseFromJson(rawJson)
            if (restoreResult.isFailure) {
                // Rollback to safety snapshot if restore failed
                if (localSafetySnapshot != null) {
                    try {
                        backupRestoreUseCase.restoreDatabaseFromJson(localSafetySnapshot)
                    } catch (e: Exception) {
                        Log.e(TAG, "Safety snapshot rollback failed: ${e.message}")
                    }
                }
                val err = "فشلت استعادة البيانات: ${restoreResult.exceptionOrNull()?.message}"
                _syncState.value = SyncState.Error(err)
                return@withContext Result.failure(restoreResult.exceptionOrNull() ?: Exception(err))
            }

            // Post-restore ledger invariant verification
            val postValidation = LedgerInvariants(db).verifyAll()
            if (!postValidation.isValid) {
                if (localSafetySnapshot != null) {
                    backupRestoreUseCase.restoreDatabaseFromJson(localSafetySnapshot)
                }
                val err = "تم رفض الاستعادة: النسخة المستعادة غير متوازنة محاسبياً (${postValidation.violations.firstOrNull()?.details})"
                _syncState.value = SyncState.Error(err)
                return@withContext Result.failure(IllegalStateException(err))
            }

            val successMsg = "تمت استعادة النسخة الاحتياطية بنجاح ومطابقتها 100%"
            _syncState.value = SyncState.Success(successMsg, System.currentTimeMillis(), 0)
            Result.success(successMsg)
        } catch (e: Exception) {
            Log.e(TAG, "Restore error: ${e.message}", e)
            if (localSafetySnapshot != null) {
                try {
                    backupRestoreUseCase.restoreDatabaseFromJson(localSafetySnapshot)
                } catch (re: Exception) {
                    Log.e(TAG, "Failed rollback to safety snapshot: ${re.message}")
                }
            }
            _syncState.value = SyncState.Error("فشلت الاستعادة: ${e.localizedMessage ?: e.message}")
            Result.failure(e)
        }
    }

    /**
     * Prunes older backups beyond MAX_RETAINED_BACKUPS (5).
     */
    private suspend fun pruneOldBackups(uid: String, fs: FirebaseFirestore) {
        try {
            val query = fs.collection("users").document(uid)
                .collection("backups")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .await()

            if (query.documents.size > MAX_RETAINED_BACKUPS) {
                val excess = query.documents.drop(MAX_RETAINED_BACKUPS)
                for (doc in excess) {
                    // Delete chunks
                    val chunksSnap = doc.reference.collection("chunks").get().await()
                    for (cDoc in chunksSnap.documents) {
                        cDoc.reference.delete().await()
                    }
                    doc.reference.delete().await()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Pruning old backups error: ${e.message}")
        }
    }

    /**
     * Purges all cloud data for the authenticated user under users/{uid}/all_subcollections
     */
    suspend fun purgeAllCloudData(explicitUid: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = getAuthenticatedUid(explicitUid)
        if (uid.isBlank()) return@withContext Result.failure(IllegalStateException("User not logged in"))
        val fs = firestore ?: return@withContext Result.failure(IllegalStateException("Firestore unavailable"))

        try {
            val userDocRef = fs.collection("users").document(uid)
            val backupsSnap = userDocRef.collection("backups").get().await()
            for (bDoc in backupsSnap.documents) {
                val chunksSnap = bDoc.reference.collection("chunks").get().await()
                for (cDoc in chunksSnap.documents) {
                    cDoc.reference.delete().await()
                }
                bDoc.reference.delete().await()
            }
            // Delete legacy collection if exists
            val legacySnap = userDocRef.collection("backup_latest").get().await()
            for (lDoc in legacySnap.documents) {
                lDoc.reference.delete().await()
            }
            userDocRef.delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Purge all cloud data error: ${e.message}", e)
            Result.failure(e)
        }
    }
}
