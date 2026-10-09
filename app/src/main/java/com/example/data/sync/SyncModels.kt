package com.example.data.sync

sealed interface SyncState {
    object Idle : SyncState
    data class InProgress(val message: String, val progress: Float = 0f) : SyncState
    data class Success(val message: String, val lastSyncEpochMs: Long, val totalRecords: Int) : SyncState
    data class Error(val message: String) : SyncState
}

data class SyncMetadata(
    val userEmail: String = "",
    val lastSyncedAt: Long = 0L,
    val totalDocuments: Int = 0,
    val totalJournalLines: Int = 0,
    val totalParties: Int = 0,
    val totalPackages: Int = 0,
    val totalTreasuries: Int = 0,
    val totalAllocations: Int = 0,
    val totalDevices: Int = 0,
    val hasNetworkProfile: Boolean = false,
    val isBalanced: Boolean = true,
    val checksum: String = ""
)

data class SyncLogItem(
    val id: String,
    val timestamp: Long,
    val action: String, // "رفع إلى السحاب" or "سحب واستعادة"
    val userEmail: String,
    val recordsCount: Int,
    val isSuccess: Boolean,
    val summary: String
)
