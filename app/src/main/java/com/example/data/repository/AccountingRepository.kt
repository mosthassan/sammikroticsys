package com.example.data.repository

import com.example.core.model.CurrencyCode
import com.example.core.model.ExchangeRate
import com.example.data.ledger.InvoiceAllocationSpec
import com.example.data.ledger.LedgerInvariants
import com.example.data.ledger.LedgerWriter
import com.example.data.ledger.PaymentVoucherType
import com.example.data.ledger.PurchaseItemSpec
import com.example.data.ledger.SalesItemSpec
import com.example.data.local.AppDatabase
import com.example.data.local.dao.AccountBalanceRow
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.AssetEntity
import com.example.data.local.entity.CardPackageEntity
import com.example.data.local.entity.DocumentEntity
import com.example.data.local.entity.DocumentItemEntity
import com.example.data.local.entity.JournalEntryEntity
import com.example.data.local.entity.JournalLineEntity
import com.example.data.local.entity.OrganizationEntity
import com.example.data.local.entity.PartyEntity
import com.example.data.local.entity.TreasuryAccountEntity
import kotlinx.coroutines.flow.Flow

class AccountingRepository(
    private val db: AppDatabase
) {
    val ledgerWriter = LedgerWriter(db, enableInvariantValidation = true)
    val invariants = LedgerInvariants(db)

    val trialBalance: Flow<List<AccountBalanceRow>> = db.journalDao().getTrialBalanceFlow()
    val allEntries: Flow<List<JournalEntryEntity>> = db.journalDao().getAllEntriesFlow()
    val allLines: Flow<List<JournalLineEntity>> = db.journalDao().getAllLinesFlow()
    val allDocuments: Flow<List<DocumentEntity>> = db.documentDao().getAllDocumentsFlow()
    val allAccounts: Flow<List<AccountEntity>> = db.accountDao().getAllAccountsFlow()
    val allParties: Flow<List<PartyEntity>> = db.partyDao().getAllPartiesFlow()
    val allTreasuries: Flow<List<TreasuryAccountEntity>> = db.treasuryDao().getAllTreasuriesFlow()
    val allAssets: Flow<List<AssetEntity>> = db.assetDao().getAllActiveAssetsFlow()
    val allPackages: Flow<List<CardPackageEntity>> = db.cardPackageDao().getAllPackagesFlow()
    val organization: Flow<OrganizationEntity?> = db.organizationDao().getOrganizationFlow()

    suspend fun getLinesForEntry(entryId: String): List<JournalLineEntity> {
        return db.journalDao().getLinesForEntry(entryId)
    }

    suspend fun getItemsForDocument(docId: String): List<DocumentItemEntity> {
        return db.documentDao().getItemsForDocument(docId)
    }

    suspend fun getNetBalanceForAccount(code: String): Long {
        return db.journalDao().getNetDebitBalanceForAccount(code)
    }

    suspend fun getPartyBalance(partyId: String, controlAccountCode: String): Long {
        val rows = db.journalDao().getPartyBalancesForControlAccount(controlAccountCode)
        return rows.firstOrNull { it.partyId == partyId }?.netBalanceMinor ?: 0L
    }

    suspend fun getTreasuryBalance(treasuryId: String): Long {
        return db.journalDao().getNetDebitBalanceForTreasury(treasuryId)
    }

    suspend fun runInvariantCheck() = invariants.verifyAll(failFast = false)

    suspend fun insertParty(party: PartyEntity) = db.partyDao().insertParty(party)

    suspend fun updateParty(party: PartyEntity) = db.partyDao().updateParty(party)

    suspend fun insertTreasury(treasury: TreasuryAccountEntity) = db.treasuryDao().insertTreasury(treasury)

    suspend fun insertPackage(pkg: CardPackageEntity) = db.cardPackageDao().insertPackage(pkg)

    suspend fun updateOrganization(org: OrganizationEntity) = db.organizationDao().updateOrganization(org)
}
