package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.core.ledger.AccountConstants
import com.example.data.local.dao.AccountDao
import com.example.data.local.dao.AllocationDao
import com.example.data.local.dao.AssetDao
import com.example.data.local.dao.AuditLogDao
import com.example.data.local.dao.CardPackageDao
import com.example.data.local.dao.CurrencyRateDao
import com.example.data.local.dao.DocumentDao
import com.example.data.local.dao.FiscalPeriodDao
import com.example.data.local.dao.IdempotencyDao
import com.example.data.local.dao.JournalDao
import com.example.data.local.dao.NumberSequenceDao
import com.example.data.local.dao.OrganizationDao
import com.example.data.local.dao.PartyDao
import com.example.data.local.dao.TreasuryDao
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        OrganizationEntity::class,
        AccountEntity::class,
        PartyEntity::class,
        TreasuryAccountEntity::class,
        FiscalPeriodEntity::class,
        CurrencyRateEntity::class,
        DocumentEntity::class,
        DocumentItemEntity::class,
        JournalEntryEntity::class,
        JournalLineEntity::class,
        AllocationEntity::class,
        AssetEntity::class,
        DepreciationRunEntity::class,
        CardPackageEntity::class,
        StockMovementEntity::class,
        NumberSequenceEntity::class,
        AuditLogEntity::class,
        IdempotencyKeyEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun organizationDao(): OrganizationDao
    abstract fun accountDao(): AccountDao
    abstract fun partyDao(): PartyDao
    abstract fun treasuryDao(): TreasuryDao
    abstract fun fiscalPeriodDao(): FiscalPeriodDao
    abstract fun currencyRateDao(): CurrencyRateDao
    abstract fun documentDao(): DocumentDao
    internal abstract fun journalDao(): JournalDao
    abstract fun allocationDao(): AllocationDao
    abstract fun assetDao(): AssetDao
    abstract fun cardPackageDao(): CardPackageDao
    abstract fun numberSequenceDao(): NumberSequenceDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun idempotencyDao(): IdempotencyDao

    companion object {
        const val DATABASE_NAME = "sammikrotik_accounting.db"
        const val WALK_IN_CASH_PARTY_ID = "WALK_IN_CASH"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE treasury_accounts ADD COLUMN allowNegative INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE organizations ADD COLUMN equityShareMode TEXT NOT NULL DEFAULT 'DERIVED_FROM_CAPITAL'")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .addCallback(DatabaseCallback())
                // Destructive migration is strictly forbidden
                .build()
        }

        fun createInMemory(context: Context): AppDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                AppDatabase::class.java
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .addCallback(DatabaseCallback())
                .allowMainThreadQueries()
                .build()
        }

        fun installTriggers(db: SupportSQLiteDatabase) {
            // Immutable journal entries trigger
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS prevent_journal_entries_update
                BEFORE UPDATE ON journal_entries
                BEGIN
                    SELECT RAISE(ABORT, 'Journal entries are immutable and cannot be updated');
                END;
            """)

            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS prevent_journal_entries_delete
                BEFORE DELETE ON journal_entries
                BEGIN
                    SELECT RAISE(ABORT, 'Journal entries are immutable and cannot be deleted');
                END;
            """)

            // Immutable journal lines trigger
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS prevent_journal_lines_update
                BEFORE UPDATE ON journal_lines
                BEGIN
                    SELECT RAISE(ABORT, 'Journal lines are immutable and cannot be updated');
                END;
            """)

            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS prevent_journal_lines_delete
                BEFORE DELETE ON journal_lines
                BEGIN
                    SELECT RAISE(ABORT, 'Journal lines are immutable and cannot be deleted');
                END;
            """)

            // Append-only currency rates trigger
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS prevent_currency_rates_update
                BEFORE UPDATE ON currency_rates
                BEGIN
                    SELECT RAISE(ABORT, 'currency_rates is append-only');
                END;
            """)

            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS prevent_currency_rates_delete
                BEFORE DELETE ON currency_rates
                BEGIN
                    SELECT RAISE(ABORT, 'currency_rates is append-only');
                END;
            """)
        }

        fun seedDefaultData(db: SupportSQLiteDatabase) {
            // Seed Organization
            db.execSQL("""
                INSERT OR IGNORE INTO organizations (id, name, taxNumber, functionalCurrency, fiscalYearStartMonth, isInitialized, primaryRateZone, equityShareMode, createdAt)
                VALUES ('DEFAULT_ORG', 'شبكة سام اللاسلكية', '', 'YER', 1, 1, 'SANAA', 'DERIVED_FROM_CAPITAL', strftime('%s','now') * 1000)
            """)

            // Seed Permanent Walk-in Cash Party
            db.execSQL("""
                INSERT OR IGNORE INTO parties (id, name, phone, isCustomer, isVendor, isPartner, equityPercentageBasisPoints, creditLimitMinor, isActive, createdAt)
                VALUES ('$WALK_IN_CASH_PARTY_ID', 'عميل نقدي (مبيعات فورية)', '', 1, 0, 0, 0, 0, 1, strftime('%s','now') * 1000)
            """)

            // Seed Standard IFRS Chart of Accounts
            val accounts = listOf(
                AccountEntity(AccountConstants.CASH_VAULT, "النقدية بالصندوق", "ASSET", isDebitNormal = true, isLocked = true),
                AccountEntity(AccountConstants.BANKS_WALLETS, "البنوك والمحافظ الإلكترونية", "ASSET", isDebitNormal = true, isLocked = true),
                AccountEntity(AccountConstants.ACCOUNTS_RECEIVABLE, "العملاء والوكلاء", "ASSET", isDebitNormal = true, isLocked = true),
                AccountEntity(AccountConstants.CARD_INVENTORY_RESERVE, "مخزون الكروت (محجوز)", "ASSET", isDebitNormal = true, isLocked = true, isActive = false),
                AccountEntity(AccountConstants.FIXED_ASSETS_NETWORK, "أجهزة ومعدات الشبكة", "ASSET", isDebitNormal = true, isLocked = true),
                AccountEntity(AccountConstants.ACCUMULATED_DEPRECIATION, "مجمع الإهلاك", "ASSET", isDebitNormal = false, isLocked = true),
                AccountEntity(AccountConstants.ACCOUNTS_PAYABLE, "الموردون", "LIABILITY", isDebitNormal = false, isLocked = true),
                AccountEntity(AccountConstants.CAPITAL, "رأس المال", "EQUITY", isDebitNormal = false, isLocked = true),
                AccountEntity(AccountConstants.PARTNER_CURRENT, "جاري الشركاء", "EQUITY", isDebitNormal = false, isLocked = true),
                AccountEntity(AccountConstants.RETAINED_EARNINGS, "الأرباح المرحّلة", "EQUITY", isDebitNormal = false, isLocked = true),
                AccountEntity(AccountConstants.CARD_SALES_REVENUE, "إيرادات مبيعات الكروت", "REVENUE", isDebitNormal = false, isLocked = true),
                AccountEntity(AccountConstants.SALES_RETURNS, "مردودات ومسموحات المبيعات", "REVENUE", isDebitNormal = true, isLocked = true),
                AccountEntity(AccountConstants.DIRECT_SERVICE_REVENUE, "إيرادات الاشتراكات المباشرة والخدمات", "REVENUE", isDebitNormal = false, isLocked = true),
                AccountEntity(AccountConstants.REALIZED_FX_GAIN, "أرباح فروق العملة", "REVENUE", isDebitNormal = false, isLocked = true),
                AccountEntity(AccountConstants.DIRECT_ISP_SERVICE_COST, "تكلفة الخدمة المباشرة (اشتراكات الإنترنت)", "EXPENSE", isDebitNormal = true, isLocked = true),
                AccountEntity(AccountConstants.OPERATING_EXPENSES, "مصاريف تشغيل وعمومية (ديزل/كهرباء)", "EXPENSE", isDebitNormal = true, isLocked = true),
                AccountEntity(AccountConstants.MAINTENANCE_SPARES, "صيانة وقطع غيار", "EXPENSE", isDebitNormal = true, isLocked = true),
                AccountEntity(AccountConstants.DEPRECIATION_EXPENSE, "إهلاك الأصول", "EXPENSE", isDebitNormal = true, isLocked = true),
                AccountEntity(AccountConstants.SALARIES_STAFF, "رواتب وأجور وفنيون", "EXPENSE", isDebitNormal = true, isLocked = true),
                AccountEntity(AccountConstants.MISC_EXPENSES, "مصاريف متنوعة", "EXPENSE", isDebitNormal = true, isLocked = true),
                AccountEntity(AccountConstants.REALIZED_FX_LOSS, "خسائر فروق العملة", "EXPENSE", isDebitNormal = true, isLocked = true)
            )

            accounts.forEach { acc ->
                db.execSQL("""
                    INSERT OR IGNORE INTO accounts (code, name, type, isDebitNormal, isLocked, isActive)
                    VALUES ('${acc.code}', '${acc.name}', '${acc.type}', ${if (acc.isDebitNormal) 1 else 0}, ${if (acc.isLocked) 1 else 0}, ${if (acc.isActive) 1 else 0})
                """)
            }

            // Seed Standard Treasuries
            db.execSQL("""
                INSERT OR IGNORE INTO treasury_accounts (id, name, glAccountCode, currency, isActive, allowNegative)
                VALUES ('TR_MAIN_YER', 'صندوق النقدية الرئيسي (YER)', '1101', 'YER', 1, 0)
            """)
            db.execSQL("""
                INSERT OR IGNORE INTO treasury_accounts (id, name, glAccountCode, currency, isActive, allowNegative)
                VALUES ('TR_USD_VAULT', 'خزينة الدولار (USD)', '1101', 'USD', 1, 0)
            """)
            db.execSQL("""
                INSERT OR IGNORE INTO treasury_accounts (id, name, glAccountCode, currency, isActive, allowNegative)
                VALUES ('TR_SAR_VAULT', 'خزينة الريال السعودي (SAR)', '1101', 'SAR', 1, 0)
            """)

            // Seed Exchange Rates (Sana'a & Aden zones)
            // Sana'a: 1 USD = 530 YER (530_000_000 micros), 1 SAR = 140 YER (140_000_000 micros)
            db.execSQL("""
                INSERT OR IGNORE INTO currency_rates (id, currency, zone, rateMicros, effectiveDateEpochDay, createdAt, createdBy, reason)
                VALUES ('RATE_USD_SANAA_INIT', 'USD', 'SANAA', 530000000, 0, strftime('%s','now') * 1000, 'SYSTEM', 'Initial Seed Rate');
            """)
            db.execSQL("""
                INSERT OR IGNORE INTO currency_rates (id, currency, zone, rateMicros, effectiveDateEpochDay, createdAt, createdBy, reason)
                VALUES ('RATE_SAR_SANAA_INIT', 'SAR', 'SANAA', 140000000, 0, strftime('%s','now') * 1000, 'SYSTEM', 'Initial Seed Rate');
            """)
            // Aden: 1 USD = 1600 YER (1_600_000_000 micros), 1 SAR = 420 YER (420_000_000 micros)
            db.execSQL("""
                INSERT OR IGNORE INTO currency_rates (id, currency, zone, rateMicros, effectiveDateEpochDay, createdAt, createdBy, reason)
                VALUES ('RATE_USD_ADEN_INIT', 'USD', 'ADEN', 1600000000, 0, strftime('%s','now') * 1000, 'SYSTEM', 'Initial Seed Rate');
            """)
            db.execSQL("""
                INSERT OR IGNORE INTO currency_rates (id, currency, zone, rateMicros, effectiveDateEpochDay, createdAt, createdBy, reason)
                VALUES ('RATE_SAR_ADEN_INIT', 'SAR', 'ADEN', 420000000, 0, strftime('%s','now') * 1000, 'SYSTEM', 'Initial Seed Rate');
            """)
        }
    }

    private class DatabaseCallback : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            installTriggers(db)
            seedDefaultData(db)
        }

        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            installTriggers(db)
        }
    }
}
