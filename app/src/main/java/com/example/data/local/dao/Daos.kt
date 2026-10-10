package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.AllocationEntity
import com.example.data.local.entity.AssetEntity
import com.example.data.local.entity.AuditLogEntity
import com.example.data.local.entity.CardPackageEntity
import com.example.data.local.entity.CurrencyRateEntity
import com.example.data.local.entity.DepreciationRunEntity
import com.example.data.local.entity.DocumentEntity
import com.example.data.local.entity.DocumentItemEntity
import com.example.data.local.entity.FiscalPeriodEntity
import com.example.data.local.entity.IdempotencyKeyEntity
import com.example.data.local.entity.JournalEntryEntity
import com.example.data.local.entity.JournalLineEntity
import com.example.data.local.entity.NumberSequenceEntity
import com.example.data.local.entity.OrganizationEntity
import com.example.data.local.entity.PartyEntity
import com.example.data.local.entity.StockMovementEntity
import com.example.data.local.entity.TreasuryAccountEntity
import kotlinx.coroutines.flow.Flow

data class AccountBalanceRow(
    val accountCode: String,
    val accountName: String,
    val totalDebitMinor: Long,
    val totalCreditMinor: Long,
    val isDebitNormal: Boolean,
    val origDebitMinor: Long = 0L,
    val origCreditMinor: Long = 0L,
    val currency: String = "YER"
) {
    val netBalanceMinor: Long get() = if (isDebitNormal) totalDebitMinor - totalCreditMinor else totalCreditMinor - totalDebitMinor
    val netOrigBalanceMinor: Long get() = if (isDebitNormal) origDebitMinor - origCreditMinor else origCreditMinor - origDebitMinor
}

data class PartyBalanceRow(
    val partyId: String,
    val partyName: String,
    val accountCode: String,
    val totalDebitMinor: Long,
    val totalCreditMinor: Long
) {
    val netBalanceMinor: Long get() = totalDebitMinor - totalCreditMinor
}

data class StatementLineRow(
    val entryId: String,
    val docId: String,
    val entryDateEpochDay: Long,
    val docType: String,
    val docNumber: Long,
    val memo: String,
    val baseDebitMinor: Long,
    val baseCreditMinor: Long,
    val origMinor: Long,
    val currency: String
)

data class IncomeStatementRow(
    val accountCode: String,
    val accountName: String,
    val totalDebitMinor: Long,
    val totalCreditMinor: Long,
    val isDebitNormal: Boolean,
    val origDebitMinor: Long = 0L,
    val origCreditMinor: Long = 0L,
    val currency: String = "YER"
) {
    // For revenue accounts (4xxx, credit normal): credit - debit
    // For expense accounts (5xxx, debit normal): debit - credit
    val netAmountMinor: Long
        get() = if (isDebitNormal) totalDebitMinor - totalCreditMinor else totalCreditMinor - totalDebitMinor
    val netOrigAmountMinor: Long
        get() = if (isDebitNormal) origDebitMinor - origCreditMinor else origCreditMinor - origDebitMinor
}

data class BalanceSheetRow(
    val accountCode: String,
    val accountName: String,
    val accountType: String,
    val totalDebitMinor: Long,
    val totalCreditMinor: Long,
    val isDebitNormal: Boolean,
    val origDebitMinor: Long = 0L,
    val origCreditMinor: Long = 0L,
    val currency: String = "YER"
) {
    val netBalanceMinor: Long
        get() = if (isDebitNormal) totalDebitMinor - totalCreditMinor else totalCreditMinor - totalDebitMinor
    val netOrigBalanceMinor: Long
        get() = if (isDebitNormal) origDebitMinor - origCreditMinor else origCreditMinor - origDebitMinor
}

data class PartyForeignBalanceItem(
    val partyId: String,
    val currency: String
)

/**
 * Journal entries and lines write access is restricted exclusively to LedgerWriter.
 */
@Dao
internal interface JournalDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEntry(entry: JournalEntryEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLines(lines: List<JournalLineEntity>)

    @Query("SELECT * FROM journal_entries ORDER BY entryDateEpochDay DESC, entryNumber DESC")
    fun getAllEntriesFlow(): Flow<List<JournalEntryEntity>>

    @Query("SELECT * FROM journal_entries ORDER BY entryDateEpochDay ASC, entryNumber ASC")
    suspend fun getAllEntriesSync(): List<JournalEntryEntity>

    @Query("SELECT * FROM journal_entries WHERE docId = :docId ORDER BY entryNumber ASC")
    suspend fun getEntriesForDocument(docId: String): List<JournalEntryEntity>

    @Query("SELECT * FROM journal_lines WHERE entryId = :entryId ORDER BY lineNo ASC")
    suspend fun getLinesForEntry(entryId: String): List<JournalLineEntity>

    @Query("SELECT * FROM journal_lines ORDER BY lineNo ASC")
    fun getAllLinesFlow(): Flow<List<JournalLineEntity>>

    @Query("SELECT * FROM journal_lines")
    suspend fun getAllLinesSync(): List<JournalLineEntity>

    @Query("""
        SELECT je.id AS entryId, d.id AS docId, je.entryDateEpochDay AS entryDateEpochDay,
               d.type AS docType, d.docNumber AS docNumber, jl.memo AS memo,
               jl.baseDebitMinor AS baseDebitMinor, jl.baseCreditMinor AS baseCreditMinor,
               jl.origMinor AS origMinor, jl.currency AS currency
        FROM journal_lines jl
        INNER JOIN journal_entries je ON jl.entryId = je.id
        INNER JOIN documents d ON je.docId = d.id
        WHERE jl.partyId = :partyId AND jl.accountCode = :controlAccountCode
          AND (:startDateEpochDay IS NULL OR je.entryDateEpochDay >= :startDateEpochDay)
          AND (:endDateEpochDay IS NULL OR je.entryDateEpochDay <= :endDateEpochDay)
        ORDER BY je.entryDateEpochDay ASC, je.entryNumber ASC
    """)
    suspend fun getStatementOfAccountLines(
        partyId: String,
        controlAccountCode: String,
        startDateEpochDay: Long?,
        endDateEpochDay: Long?
    ): List<StatementLineRow>

    @Query("""
        SELECT jl.accountCode AS accountCode, a.name AS accountName,
               COALESCE(SUM(jl.baseDebitMinor), 0) AS totalDebitMinor,
               COALESCE(SUM(jl.baseCreditMinor), 0) AS totalCreditMinor,
               a.isDebitNormal AS isDebitNormal,
               COALESCE(SUM(CASE WHEN jl.baseDebitMinor > 0 THEN jl.origMinor ELSE 0 END), 0) AS origDebitMinor,
               COALESCE(SUM(CASE WHEN jl.baseCreditMinor > 0 THEN jl.origMinor ELSE 0 END), 0) AS origCreditMinor,
               COALESCE(MAX(jl.currency), 'YER') AS currency
        FROM journal_lines jl
        INNER JOIN journal_entries je ON jl.entryId = je.id
        INNER JOIN accounts a ON jl.accountCode = a.code
        WHERE je.type != 'CLOSING'
          AND (a.code LIKE '4%' OR a.code LIKE '5%')
          AND (:startDateEpochDay IS NULL OR je.entryDateEpochDay >= :startDateEpochDay)
          AND (:endDateEpochDay IS NULL OR je.entryDateEpochDay <= :endDateEpochDay)
        GROUP BY jl.accountCode, a.name, a.isDebitNormal
        ORDER BY jl.accountCode ASC
    """)
    suspend fun getIncomeStatementLines(
        startDateEpochDay: Long?,
        endDateEpochDay: Long?
    ): List<IncomeStatementRow>

    @Query("""
        SELECT a.code AS accountCode, a.name AS accountName, a.type AS accountType,
               COALESCE(SUM(jl.baseDebitMinor), 0) AS totalDebitMinor,
               COALESCE(SUM(jl.baseCreditMinor), 0) AS totalCreditMinor,
               a.isDebitNormal AS isDebitNormal,
               COALESCE(SUM(CASE WHEN jl.baseDebitMinor > 0 THEN jl.origMinor ELSE 0 END), 0) AS origDebitMinor,
               COALESCE(SUM(CASE WHEN jl.baseCreditMinor > 0 THEN jl.origMinor ELSE 0 END), 0) AS origCreditMinor,
               COALESCE(MAX(jl.currency), 'YER') AS currency
        FROM accounts a
        LEFT JOIN (
            SELECT jl_inner.accountCode, jl_inner.baseDebitMinor, jl_inner.baseCreditMinor, jl_inner.origMinor, jl_inner.currency
            FROM journal_lines jl_inner
            INNER JOIN journal_entries je_inner ON jl_inner.entryId = je_inner.id
            WHERE (:asOfDateEpochDay IS NULL OR je_inner.entryDateEpochDay <= :asOfDateEpochDay)
        ) jl ON a.code = jl.accountCode
        WHERE (a.code LIKE '1%' OR a.code LIKE '2%' OR a.code LIKE '3%')
        GROUP BY a.code, a.name, a.type, a.isDebitNormal
        ORDER BY a.code ASC
    """)
    suspend fun getBalanceSheetLines(
        asOfDateEpochDay: Long?
    ): List<BalanceSheetRow>

    @Query("""
        SELECT a.code AS accountCode, a.name AS accountName,
               COALESCE(SUM(jl.baseDebitMinor), 0) AS totalDebitMinor,
               COALESCE(SUM(jl.baseCreditMinor), 0) AS totalCreditMinor,
               a.isDebitNormal AS isDebitNormal,
               COALESCE(SUM(CASE WHEN jl.baseDebitMinor > 0 THEN jl.origMinor ELSE 0 END), 0) AS origDebitMinor,
               COALESCE(SUM(CASE WHEN jl.baseCreditMinor > 0 THEN jl.origMinor ELSE 0 END), 0) AS origCreditMinor,
               COALESCE(MAX(jl.currency), 'YER') AS currency
        FROM accounts a
        LEFT JOIN journal_lines jl ON a.code = jl.accountCode
        GROUP BY a.code, a.name, a.isDebitNormal
        ORDER BY a.code ASC
    """)
    fun getTrialBalanceFlow(): Flow<List<AccountBalanceRow>>

    @Query("""
        SELECT a.code AS accountCode, a.name AS accountName,
               COALESCE(SUM(jl.baseDebitMinor), 0) AS totalDebitMinor,
               COALESCE(SUM(jl.baseCreditMinor), 0) AS totalCreditMinor,
               a.isDebitNormal AS isDebitNormal,
               COALESCE(SUM(CASE WHEN jl.baseDebitMinor > 0 THEN jl.origMinor ELSE 0 END), 0) AS origDebitMinor,
               COALESCE(SUM(CASE WHEN jl.baseCreditMinor > 0 THEN jl.origMinor ELSE 0 END), 0) AS origCreditMinor,
               COALESCE(MAX(jl.currency), 'YER') AS currency
        FROM accounts a
        LEFT JOIN journal_lines jl ON a.code = jl.accountCode
        GROUP BY a.code, a.name, a.isDebitNormal
        ORDER BY a.code ASC
    """)
    suspend fun getTrialBalanceSync(): List<AccountBalanceRow>

    @Query("""
        SELECT p.id AS partyId, p.name AS partyName, jl.accountCode AS accountCode,
               COALESCE(SUM(jl.baseDebitMinor), 0) AS totalDebitMinor,
               COALESCE(SUM(jl.baseCreditMinor), 0) AS totalCreditMinor
        FROM parties p
        INNER JOIN journal_lines jl ON p.id = jl.partyId
        WHERE jl.accountCode = :controlAccountCode
        GROUP BY p.id, p.name, jl.accountCode
    """)
    suspend fun getPartyBalancesForControlAccount(controlAccountCode: String): List<PartyBalanceRow>

    @Query("""
        SELECT COALESCE(SUM(baseDebitMinor) - SUM(baseCreditMinor), 0)
        FROM journal_lines
        WHERE accountCode = :accountCode
    """)
    suspend fun getNetDebitBalanceForAccount(accountCode: String): Long

    @Query("""
        SELECT COALESCE(SUM(baseCreditMinor) - SUM(baseDebitMinor), 0)
        FROM journal_lines
        WHERE partyId = :partyId AND accountCode = '3101'
    """)
    fun getPartnerCapitalBalanceFlow(partyId: String): Flow<Long>

    @Query("""
        SELECT COALESCE(SUM(baseCreditMinor) - SUM(baseDebitMinor), 0)
        FROM journal_lines
        WHERE partyId = :partyId AND accountCode = '3101'
    """)
    suspend fun getPartnerCapitalBalanceSync(partyId: String): Long

    @Query("""
        SELECT COALESCE(SUM(baseCreditMinor) - SUM(baseDebitMinor), 0)
        FROM journal_lines
        WHERE accountCode = '3101'
    """)
    suspend fun getTotalCapitalBalanceSync(): Long

    @Query("""
        SELECT COALESCE(SUM(baseCreditMinor) - SUM(baseDebitMinor), 0)
        FROM journal_lines
        WHERE accountCode = '3101'
    """)
    fun getTotalCapitalBalanceFlow(): Flow<Long>

    @Query("""
        SELECT COALESCE(SUM(baseCreditMinor) - SUM(baseDebitMinor), 0)
        FROM journal_lines
        WHERE partyId = :partyId AND accountCode = '3201'
    """)
    suspend fun getPartnerCurrentBalanceSync(partyId: String): Long

    @Query("""
        SELECT COALESCE(SUM(baseCreditMinor) - SUM(baseDebitMinor), 0)
        FROM journal_lines
        WHERE partyId = :partyId AND accountCode = '3201'
    """)
    fun getPartnerCurrentBalanceFlow(partyId: String): Flow<Long>

    @Query("""
        SELECT COALESCE(SUM(baseDebitMinor) - SUM(baseCreditMinor), 0)
        FROM journal_lines
        WHERE treasuryId = :treasuryId
    """)
    suspend fun getNetDebitBalanceForTreasury(treasuryId: String): Long

    @Query("""
        SELECT COALESCE(SUM(origMinor), 0)
        FROM journal_lines
        WHERE treasuryId = :treasuryId AND baseDebitMinor > 0
    """)
    suspend fun getTreasuryDebitOrigTotal(treasuryId: String): Long

    @Query("""
        SELECT COALESCE(SUM(origMinor), 0)
        FROM journal_lines
        WHERE treasuryId = :treasuryId AND baseCreditMinor > 0
    """)
    suspend fun getTreasuryCreditOrigTotal(treasuryId: String): Long

    @Query("""
        SELECT COALESCE(
            SUM(CASE WHEN baseDebitMinor > 0 THEN origMinor ELSE -origMinor END), 0
        )
        FROM journal_lines
        WHERE treasuryId = :treasuryId
    """)
    suspend fun getNetOrigBalanceForTreasury(treasuryId: String): Long

    @Query("""
        SELECT COALESCE(
            SUM(CASE WHEN baseDebitMinor > 0 THEN origMinor ELSE -origMinor END), 0
        )
        FROM journal_lines
        WHERE partyId = :partyId AND accountCode = '1201' AND currency = :currency
    """)
    suspend fun getPartyReceivableOrigBalance(partyId: String, currency: String): Long

    @Query("""
        SELECT COALESCE(SUM(baseDebitMinor) - SUM(baseCreditMinor), 0)
        FROM journal_lines
        WHERE partyId = :partyId AND accountCode = '1201' AND currency = :currency
    """)
    suspend fun getPartyReceivableBaseBalanceByCurrency(partyId: String, currency: String): Long

    @Query("""
        SELECT COALESCE(
            SUM(CASE WHEN baseCreditMinor > 0 THEN origMinor ELSE -origMinor END), 0
        )
        FROM journal_lines
        WHERE partyId = :partyId AND accountCode = '2101' AND currency = :currency
    """)
    suspend fun getPartyPayableOrigBalance(partyId: String, currency: String): Long

    @Query("""
        SELECT COALESCE(SUM(baseCreditMinor) - SUM(baseDebitMinor), 0)
        FROM journal_lines
        WHERE partyId = :partyId AND accountCode = '2101' AND currency = :currency
    """)
    suspend fun getPartyPayableBaseBalanceByCurrency(partyId: String, currency: String): Long

    @Query("""
        SELECT DISTINCT partyId, currency
        FROM journal_lines
        WHERE accountCode = :accountCode AND currency != 'YER' AND partyId IS NOT NULL
    """)
    suspend fun getPartiesWithForeignBalance(accountCode: String): List<PartyForeignBalanceItem>
}

@Dao
interface DocumentDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDocument(doc: DocumentEntity)

    @Update
    suspend fun updateDocument(doc: DocumentEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertItems(items: List<DocumentItemEntity>)

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getDocumentById(id: String): DocumentEntity?

    @Query("SELECT * FROM documents ORDER BY dateEpochDay DESC, docNumber DESC")
    fun getAllDocumentsFlow(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents ORDER BY dateEpochDay ASC, docNumber ASC")
    suspend fun getAllDocumentsSync(): List<DocumentEntity>

    @Query("SELECT * FROM documents WHERE type = :type ORDER BY dateEpochDay DESC, docNumber DESC")
    fun getDocumentsByTypeFlow(type: String): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE partyId = :partyId ORDER BY dateEpochDay DESC, docNumber DESC")
    fun getDocumentsByPartyFlow(partyId: String): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE partyId = :partyId ORDER BY dateEpochDay DESC, docNumber DESC")
    suspend fun getDocumentsByPartySync(partyId: String): List<DocumentEntity>

    @Query("SELECT * FROM document_items WHERE docId = :docId ORDER BY itemIndex ASC")
    suspend fun getItemsForDocument(docId: String): List<DocumentItemEntity>

    @Query("SELECT * FROM document_items")
    suspend fun getAllDocumentItemsSync(): List<DocumentItemEntity>
}

@Dao
interface PartyDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertParty(party: PartyEntity)

    @Update
    suspend fun updateParty(party: PartyEntity)

    @Query("UPDATE parties SET isActive = :isActive WHERE id = :id")
    suspend fun setPartyActive(id: String, isActive: Boolean)

    @Query("SELECT * FROM parties WHERE id = :id")
    suspend fun getPartyById(id: String): PartyEntity?

    @Query("SELECT * FROM parties ORDER BY name ASC")
    fun getAllPartiesFlow(): Flow<List<PartyEntity>>

    @Query("SELECT * FROM parties ORDER BY name ASC")
    suspend fun getAllPartiesSync(): List<PartyEntity>

    @Query("SELECT * FROM parties WHERE isCustomer = 1 ORDER BY name ASC")
    fun getCustomersFlow(): Flow<List<PartyEntity>>

    @Query("SELECT * FROM parties WHERE isVendor = 1 ORDER BY name ASC")
    fun getVendorsFlow(): Flow<List<PartyEntity>>

    @Query("SELECT * FROM parties WHERE isPartner = 1 ORDER BY name ASC")
    fun getPartnersFlow(): Flow<List<PartyEntity>>

    @Query("SELECT * FROM parties WHERE isPartner = 1 ORDER BY name ASC")
    suspend fun getPartnersSync(): List<PartyEntity>
}

@Dao
interface TreasuryDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTreasury(treasury: TreasuryAccountEntity)

    @Query("SELECT * FROM treasury_accounts WHERE id = :id")
    suspend fun getTreasuryById(id: String): TreasuryAccountEntity?

    @Query("SELECT * FROM treasury_accounts WHERE isActive = 1 ORDER BY name ASC")
    fun getAllTreasuriesFlow(): Flow<List<TreasuryAccountEntity>>

    @Query("SELECT * FROM treasury_accounts WHERE isActive = 1 ORDER BY name ASC")
    suspend fun getAllTreasuriesSync(): List<TreasuryAccountEntity>

    @Update
    suspend fun updateTreasury(treasury: TreasuryAccountEntity)

    @Query("UPDATE treasury_accounts SET allowNegative = :allowNegative WHERE id = :id")
    suspend fun setAllowNegative(id: String, allowNegative: Boolean)
}

@Dao
interface AccountDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAccounts(accounts: List<AccountEntity>)

    @Query("SELECT * FROM accounts WHERE code = :code")
    suspend fun getAccountByCode(code: String): AccountEntity?

    @Query("SELECT * FROM accounts ORDER BY code ASC")
    fun getAllAccountsFlow(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY code ASC")
    suspend fun getAllAccountsSync(): List<AccountEntity>
}

@Dao
interface FiscalPeriodDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPeriod(period: FiscalPeriodEntity)

    @Query("SELECT * FROM fiscal_periods WHERE year = :year AND month = :month")
    suspend fun getPeriod(year: Int, month: Int): FiscalPeriodEntity?

    @Query("SELECT * FROM fiscal_periods ORDER BY year DESC, month DESC")
    fun getAllPeriodsFlow(): Flow<List<FiscalPeriodEntity>>

    @Query("SELECT * FROM fiscal_periods ORDER BY year DESC, month DESC")
    suspend fun getAllPeriodsSync(): List<FiscalPeriodEntity>

    @Query("UPDATE fiscal_periods SET isClosed = :isClosed, closedAt = :closedAt WHERE year = :year AND month = :month")
    suspend fun setPeriodClosed(year: Int, month: Int, isClosed: Boolean, closedAt: Long?)
}

@Dao
interface AllocationDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAllocation(allocation: AllocationEntity)

    @Query("SELECT * FROM allocations WHERE invoiceDocId = :invoiceDocId AND isVoided = 0")
    suspend fun getActiveAllocationsForInvoice(invoiceDocId: String): List<AllocationEntity>

    @Query("SELECT * FROM allocations WHERE paymentDocId = :paymentDocId AND isVoided = 0")
    suspend fun getActiveAllocationsForPayment(paymentDocId: String): List<AllocationEntity>

    @Query("SELECT * FROM allocations WHERE isVoided = 0")
    fun getAllActiveAllocationsFlow(): Flow<List<AllocationEntity>>

    @Query("SELECT * FROM allocations WHERE isVoided = 0")
    suspend fun getAllActiveAllocationsSync(): List<AllocationEntity>

    @Query("UPDATE allocations SET isVoided = 1 WHERE paymentDocId = :docId OR invoiceDocId = :docId")
    suspend fun voidAllocationsForDoc(docId: String)
}

@Dao
interface AssetDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAsset(asset: AssetEntity)

    @Query("SELECT * FROM assets WHERE id = :id")
    suspend fun getAssetById(id: String): AssetEntity?

    @Query("SELECT * FROM assets WHERE isDisposed = 0 ORDER BY purchaseDateEpochDay DESC")
    fun getAllActiveAssetsFlow(): Flow<List<AssetEntity>>

    @Query("SELECT * FROM assets WHERE isDisposed = 0 ORDER BY purchaseDateEpochDay DESC")
    suspend fun getAllActiveAssetsSync(): List<AssetEntity>

    @Query("SELECT * FROM assets ORDER BY purchaseDateEpochDay DESC")
    suspend fun getAllAssetsSync(): List<AssetEntity>

    @Query("UPDATE assets SET isDisposed = :isDisposed WHERE id = :id")
    suspend fun setAssetDisposed(id: String, isDisposed: Boolean)

    @Query("DELETE FROM assets WHERE id = :id")
    suspend fun deleteAsset(id: String)

    @Query("DELETE FROM assets WHERE docId = :docId")
    suspend fun deleteAssetsByDocId(docId: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDepreciationRun(run: DepreciationRunEntity)

    @Query("SELECT COUNT(*) FROM depreciation_runs WHERE periodYear = :year AND periodMonth = :month AND assetId = :assetId")
    suspend fun countDepreciationRun(year: Int, month: Int, assetId: String): Int

    @Query("SELECT * FROM depreciation_runs")
    suspend fun getAllDepreciationRunsSync(): List<DepreciationRunEntity>

    @Query("UPDATE assets SET accumulatedDepreciationMinor = accumulatedDepreciationMinor + :amountMinor WHERE id = :assetId")
    suspend fun incrementAccumulatedDepreciation(assetId: String, amountMinor: Long)
}

@Dao
interface CardPackageDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPackage(pkg: CardPackageEntity)

    @Query("SELECT * FROM card_packages WHERE id = :id")
    suspend fun getPackageById(id: String): CardPackageEntity?

    @Query("SELECT * FROM card_packages WHERE isActive = 1 ORDER BY name ASC")
    fun getAllPackagesFlow(): Flow<List<CardPackageEntity>>

    @Query("SELECT * FROM card_packages ORDER BY name ASC")
    suspend fun getAllPackagesSync(): List<CardPackageEntity>

    @Query("UPDATE card_packages SET isActive = :isActive WHERE id = :id")
    suspend fun setPackageActive(id: String, isActive: Boolean)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertStockMovement(movement: StockMovementEntity)

    @Query("SELECT * FROM stock_movements ORDER BY movementDateEpochDay DESC, createdAt DESC")
    fun getAllStockMovementsFlow(): Flow<List<StockMovementEntity>>

    @Query("SELECT * FROM stock_movements ORDER BY movementDateEpochDay DESC, createdAt DESC")
    suspend fun getAllStockMovementsSync(): List<StockMovementEntity>

    @Query("""
        SELECT COALESCE(SUM(
            CASE
                WHEN type IN ('RECEIVE', 'RETURN') THEN quantity
                WHEN type IN ('SELL') THEN -quantity
                WHEN type IN ('ADJUST') THEN quantity
                ELSE 0
            END
        ), 0)
        FROM stock_movements
        WHERE packageId = :packageId
    """)
    suspend fun getStockBalance(packageId: String): Int
}

@Dao
interface NumberSequenceDao {
    @Query("SELECT * FROM number_sequences WHERE docType = :docType AND fiscalYear = :fiscalYear")
    suspend fun getSequence(docType: String, fiscalYear: Int): NumberSequenceEntity?

    @Query("SELECT * FROM number_sequences")
    suspend fun getAllSequencesSync(): List<NumberSequenceEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSequence(sequence: NumberSequenceEntity)

    @Query("UPDATE number_sequences SET nextValue = :nextValue WHERE docType = :docType AND fiscalYear = :fiscalYear")
    suspend fun updateNextValue(docType: String, fiscalYear: Int, nextValue: Long)
}

@Dao
interface CurrencyRateDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRate(rate: CurrencyRateEntity)

    @Query("""
        SELECT * FROM currency_rates 
        WHERE currency = :currency AND zone = :zone AND effectiveDateEpochDay <= :dateEpochDay
        ORDER BY effectiveDateEpochDay DESC, createdAt DESC 
        LIMIT 1
    """)
    suspend fun getLatestRate(currency: String, zone: String, dateEpochDay: Long): CurrencyRateEntity?

    @Query("""
        SELECT * FROM currency_rates 
        WHERE currency = :currency AND zone = :zone
        ORDER BY effectiveDateEpochDay DESC, createdAt DESC 
        LIMIT 1
    """)
    suspend fun getLatestRateForZone(currency: String, zone: String): CurrencyRateEntity?

    @Query("""
        SELECT * FROM currency_rates 
        WHERE currency = :currency AND zone = :zone AND effectiveDateEpochDay = :dateEpochDay
        LIMIT 1
    """)
    suspend fun getExactRate(currency: String, zone: String, dateEpochDay: Long): CurrencyRateEntity?

    @Query("SELECT * FROM currency_rates ORDER BY effectiveDateEpochDay DESC, createdAt DESC")
    fun getAllRatesFlow(): Flow<List<CurrencyRateEntity>>

    @Query("SELECT * FROM currency_rates ORDER BY effectiveDateEpochDay DESC, createdAt DESC")
    suspend fun getAllRatesSync(): List<CurrencyRateEntity>

    @Query("SELECT * FROM currency_rates WHERE zone = :zone ORDER BY effectiveDateEpochDay DESC, createdAt DESC")
    fun getRatesByZoneFlow(zone: String): Flow<List<CurrencyRateEntity>>
}

@Dao
interface AuditLogDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLog(log: AuditLogEntity)
}

@Dao
interface IdempotencyDao {
    @Query("SELECT docId FROM idempotency_keys WHERE `key` = :key")
    suspend fun getDocIdForKey(key: String): String?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertKey(entry: IdempotencyKeyEntity)
}

@Dao
interface OrganizationDao {
    @Query("SELECT * FROM organizations LIMIT 1")
    suspend fun getOrganizationSync(): OrganizationEntity?

    @Query("SELECT * FROM organizations LIMIT 1")
    fun getOrganizationFlow(): Flow<OrganizationEntity?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOrganization(org: OrganizationEntity)

    @Update
    suspend fun updateOrganization(org: OrganizationEntity)

    @Query("UPDATE organizations SET equityShareMode = :mode WHERE id = :orgId")
    suspend fun updateEquityShareMode(orgId: String, mode: String)
}
