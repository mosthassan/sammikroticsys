package com.example.domain.usecase

import android.content.Context
import androidx.room.withTransaction
import com.example.data.ledger.LedgerInvariants
import com.example.data.local.AppDatabase
import com.example.data.local.dao.DeviceDao
import com.example.data.network.DeviceStatus
import com.example.data.network.DeviceType
import com.example.data.network.NetworkDevice
import com.example.data.network.NetworkRepository
import com.example.data.network.toBackupDto
import com.example.data.network.toNetworkConfig
import com.example.util.AppBackupData
import com.example.util.DataJsonHelper
import com.example.util.NetworkProfileBackupDto
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

class BackupRestoreUseCase(
    private val db: AppDatabase,
    private val deviceDao: DeviceDao? = null,
    private val networkRepository: NetworkRepository? = null,
    private val context: Context? = null
) {

    private val invariants = LedgerInvariants(db)

    private val effectiveDeviceDao: DeviceDao?
        get() = deviceDao ?: networkRepository ?: context?.let { NetworkRepository(it) }

    private val effectiveNetworkRepository: NetworkRepository?
        get() = networkRepository ?: (deviceDao as? NetworkRepository) ?: context?.let { NetworkRepository(it) }

    suspend fun exportDatabaseToJson(): String {
        val root = JSONObject()
        root.put("schemaVersion", 1)
        root.put("timestamp", System.currentTimeMillis())

        // 1. Parties
        val partiesArr = JSONArray()
        db.partyDao().getAllPartiesSync().forEach { p ->
            val obj = JSONObject()
            obj.put("id", p.id)
            obj.put("name", p.name)
            obj.put("phone", p.phone)
            obj.put("isCustomer", p.isCustomer)
            obj.put("isVendor", p.isVendor)
            obj.put("isPartner", p.isPartner)
            obj.put("equityPercentageBasisPoints", p.equityPercentageBasisPoints)
            obj.put("creditLimitMinor", p.creditLimitMinor)
            obj.put("isActive", p.isActive)
            obj.put("createdAt", p.createdAt)
            partiesArr.put(obj)
        }
        root.put("parties", partiesArr)

        // 2. Card Packages
        val pkgsArr = JSONArray()
        db.cardPackageDao().getAllPackagesSync().forEach { pkg ->
            val obj = JSONObject()
            obj.put("id", pkg.id)
            obj.put("name", pkg.name)
            obj.put("durationOrQuota", pkg.durationOrQuota)
            obj.put("wholesalePriceMinor", pkg.wholesalePriceMinor)
            obj.put("retailPriceMinor", pkg.retailPriceMinor)
            obj.put("isActive", pkg.isActive)
            pkgsArr.put(obj)
        }
        root.put("packages", pkgsArr)

        // 3. Treasuries
        val treasuriesArr = JSONArray()
        db.treasuryDao().getAllTreasuriesSync().forEach { tr ->
            val obj = JSONObject()
            obj.put("id", tr.id)
            obj.put("name", tr.name)
            obj.put("glAccountCode", tr.glAccountCode)
            obj.put("currency", tr.currency)
            obj.put("isActive", tr.isActive)
            obj.put("allowNegative", tr.allowNegative)
            treasuriesArr.put(obj)
        }
        root.put("treasuries", treasuriesArr)

        // 4. Documents
        val docsArr = JSONArray()
        db.documentDao().getAllDocumentsSync().forEach { d ->
            val obj = JSONObject()
            obj.put("id", d.id)
            obj.put("type", d.type)
            obj.put("fiscalYear", d.fiscalYear)
            obj.put("docNumber", d.docNumber)
            obj.put("partyId", d.partyId)
            obj.put("dateEpochDay", d.dateEpochDay)
            obj.put("currency", d.currency)
            obj.put("exchangeRateMicros", d.exchangeRateMicros)
            obj.put("rateZone", d.rateZone)
            obj.put("rateSource", d.rateSource)
            obj.put("totalMinor", d.totalMinor)
            obj.put("totalBaseMinor", d.totalBaseMinor)
            obj.put("status", d.status)
            obj.put("notes", d.notes)
            obj.put("reversalOfDocId", d.reversalOfDocId)
            obj.put("createdAt", d.createdAt)
            docsArr.put(obj)
        }
        root.put("documents", docsArr)

        // 5. Document Items
        val itemsArr = JSONArray()
        db.documentDao().getAllDocumentItemsSync().forEach { itm ->
            val obj = JSONObject()
            obj.put("id", itm.id)
            obj.put("docId", itm.docId)
            obj.put("itemIndex", itm.itemIndex)
            obj.put("packageId", itm.packageId)
            obj.put("description", itm.description)
            obj.put("accountCode", itm.accountCode)
            obj.put("quantity", itm.quantity)
            obj.put("unitPriceMinor", itm.unitPriceMinor)
            obj.put("totalMinor", itm.totalMinor)
            obj.put("isAsset", itm.isAsset)
            itemsArr.put(obj)
        }
        root.put("document_items", itemsArr)

        // 6. Journal Entries
        val entriesArr = JSONArray()
        db.journalDao().getAllEntriesSync().forEach { je ->
            val obj = JSONObject()
            obj.put("id", je.id)
            obj.put("docId", je.docId)
            obj.put("entryNumber", je.entryNumber)
            obj.put("entryDateEpochDay", je.entryDateEpochDay)
            obj.put("type", je.type)
            obj.put("memo", je.memo)
            obj.put("createdAt", je.createdAt)
            entriesArr.put(obj)
        }
        root.put("journal_entries", entriesArr)

        // 7. Journal Lines
        val linesArr = JSONArray()
        db.journalDao().getAllLinesSync().forEach { jl ->
            val obj = JSONObject()
            obj.put("id", jl.id)
            obj.put("entryId", jl.entryId)
            obj.put("lineNo", jl.lineNo)
            obj.put("accountCode", jl.accountCode)
            obj.put("partyId", jl.partyId)
            obj.put("treasuryId", jl.treasuryId)
            obj.put("origMinor", jl.origMinor)
            obj.put("currency", jl.currency)
            obj.put("exchangeRateMicros", jl.exchangeRateMicros)
            obj.put("baseDebitMinor", jl.baseDebitMinor)
            obj.put("baseCreditMinor", jl.baseCreditMinor)
            obj.put("memo", jl.memo)
            linesArr.put(obj)
        }
        root.put("journal_lines", linesArr)

        // 8. Allocations
        val allocArr = JSONArray()
        db.allocationDao().getAllActiveAllocationsSync().forEach { al ->
            val obj = JSONObject()
            obj.put("id", al.id)
            obj.put("paymentDocId", al.paymentDocId)
            obj.put("invoiceDocId", al.invoiceDocId)
            obj.put("allocatedOrigMinor", al.allocatedOrigMinor)
            obj.put("allocatedBaseMinor", al.allocatedBaseMinor)
            obj.put("exchangeGainLossMinor", al.exchangeGainLossMinor)
            obj.put("isVoided", al.isVoided)
            obj.put("createdAt", al.createdAt)
            allocArr.put(obj)
        }
        root.put("allocations", allocArr)

        // 9. Assets
        val assetsArr = JSONArray()
        db.assetDao().getAllAssetsSync().forEach { ast ->
            val obj = JSONObject()
            obj.put("id", ast.id)
            obj.put("docId", ast.docId)
            obj.put("name", ast.name)
            obj.put("purchaseDateEpochDay", ast.purchaseDateEpochDay)
            obj.put("purchaseCostMinor", ast.purchaseCostMinor)
            obj.put("salvageValueMinor", ast.salvageValueMinor)
            obj.put("usefulLifeMonths", ast.usefulLifeMonths)
            obj.put("accumulatedDepreciationMinor", ast.accumulatedDepreciationMinor)
            obj.put("isDisposed", ast.isDisposed)
            assetsArr.put(obj)
        }
        root.put("assets", assetsArr)

        // 10. Depreciation Runs
        val depRunsArr = JSONArray()
        db.assetDao().getAllDepreciationRunsSync().forEach { dr ->
            val obj = JSONObject()
            obj.put("id", dr.id)
            obj.put("periodYear", dr.periodYear)
            obj.put("periodMonth", dr.periodMonth)
            obj.put("assetId", dr.assetId)
            obj.put("journalEntryId", dr.journalEntryId)
            obj.put("depreciationAmountMinor", dr.depreciationAmountMinor)
            depRunsArr.put(obj)
        }
        root.put("depreciation_runs", depRunsArr)

        // 11. Number Sequences
        val seqArr = JSONArray()
        db.numberSequenceDao().getAllSequencesSync().forEach { sq ->
            val obj = JSONObject()
            obj.put("docType", sq.docType)
            obj.put("fiscalYear", sq.fiscalYear)
            obj.put("nextValue", sq.nextValue)
            seqArr.put(obj)
        }
        root.put("number_sequences", seqArr)

        // 12. Fiscal Periods
        val periodsArr = JSONArray()
        db.fiscalPeriodDao().getAllPeriodsSync().forEach { fp ->
            val obj = JSONObject()
            obj.put("id", fp.id)
            obj.put("year", fp.year)
            obj.put("month", fp.month)
            obj.put("isClosed", fp.isClosed)
            obj.put("closedAt", fp.closedAt)
            periodsArr.put(obj)
        }
        root.put("fiscal_periods", periodsArr)

        // 13. Currency Rates
        val ratesArr = JSONArray()
        db.currencyRateDao().getAllRatesSync().forEach { cr ->
            val obj = JSONObject()
            obj.put("id", cr.id)
            obj.put("currency", cr.currency)
            obj.put("zone", cr.zone)
            obj.put("rateMicros", cr.rateMicros)
            obj.put("effectiveDateEpochDay", cr.effectiveDateEpochDay)
            obj.put("createdAt", cr.createdAt)
            obj.put("createdBy", cr.createdBy)
            obj.put("reason", cr.reason)
            ratesArr.put(obj)
        }
        root.put("currency_rates", ratesArr)

        // 14. Stock Movements
        val stockArr = JSONArray()
        db.cardPackageDao().getAllStockMovementsSync().forEach { sm ->
            val obj = JSONObject()
            obj.put("id", sm.id)
            obj.put("packageId", sm.packageId)
            obj.put("docId", sm.docId)
            obj.put("type", sm.type)
            obj.put("quantity", sm.quantity)
            obj.put("movementDateEpochDay", sm.movementDateEpochDay)
            obj.put("createdAt", sm.createdAt)
            stockArr.put(obj)
        }
        root.put("stock_movements", stockArr)

        // 15. Network Devices
        val devArr = JSONArray()
        val devices = effectiveDeviceDao?.getAllDevices() ?: emptyList()
        devices.forEach { d ->
            val obj = JSONObject()
            obj.put("id", d.id)
            obj.put("name", d.name)
            obj.put("ipAddress", d.ipAddress)
            obj.put("deviceType", d.deviceType.name)
            obj.put("macAddress", d.macAddress)
            obj.put("towerLocation", d.towerLocation)
            obj.put("frequency", d.frequency)
            obj.put("channelWidth", d.channelWidth)
            obj.put("status", d.status.name)
            obj.put("notes", d.notes)
            obj.put("model", d.model)
            obj.put("managementPort", d.managementPort)
            obj.put("subnet", d.subnet)
            obj.put("credentials", d.credentials)
            devArr.put(obj)
        }
        root.put("network_devices", devArr)
        root.put("networkDevices", devArr)

        // 16. Network Profile & FX Configuration
        val config = effectiveNetworkRepository?.config?.value
        val profile = config?.toBackupDto() ?: extractProfileFromPrefs()
        if (profile != null) {
            val profObj = JSONObject()
            profObj.put("networkName", profile.networkName)
            profObj.put("ownerName", profile.ownerName)
            profObj.put("location", profile.location)
            profObj.put("welcomeMessage", profile.welcomeMessage)
            profObj.put("supportPhone", profile.supportPhone)
            profObj.put("supportWhatsapp", profile.supportWhatsapp)
            profObj.put("mainRouterModel", profile.mainRouterModel)
            profObj.put("routerOsVersion", profile.routerOsVersion)
            profObj.put("hotspotDomain", profile.hotspotDomain)
            profObj.put("hotspotServerName", profile.hotspotServerName)
            profObj.put("adminPort", profile.adminPort)
            profObj.put("primaryDns", profile.primaryDns)
            profObj.put("secondaryDns", profile.secondaryDns)
            profObj.put("approvedSubnet", profile.approvedSubnet)
            profObj.put("rateZone", profile.rateZone)
            profObj.put("defaultUsdRateMicros", profile.defaultUsdRateMicros)
            profObj.put("defaultSarRateMicros", profile.defaultSarRateMicros)
            profObj.put("updatedAt", profile.updatedAt)
            root.put("network_profile", profObj)
            root.put("networkProfile", profObj)
        }

        val rawJson = root.toString(2)
        val hash = sha256(rawJson)
        root.put("sha256", hash)
        return root.toString(2)
    }

    suspend fun restoreDatabaseFromJson(jsonString: String): Result<String> = runCatching {
        val root = JSONObject(jsonString)
        val schemaVer = root.optInt("schemaVersion", -1)
        require(schemaVer == 1) { "Unsupported backup schema version: $schemaVer" }

        // Verify SHA-256 integrity if present
        if (root.has("sha256")) {
            val declaredHash = root.getString("sha256")
            root.remove("sha256")
            val payload = root.toString(2)
            val computedHash = sha256(payload)
            require(computedHash.equals(declaredHash, ignoreCase = true)) {
                "فشل التحقق من البصمة الرقمية للنسخة الاحتياطية (SHA-256 Mismatch)"
            }
        }

        db.withTransaction {
            val sdb = db.openHelper.writableDatabase

            // Temporarily disable SQLite update/delete triggers during clean bulk restore
            sdb.execSQL("DROP TRIGGER IF EXISTS prevent_journal_entries_update")
            sdb.execSQL("DROP TRIGGER IF EXISTS prevent_journal_entries_delete")
            sdb.execSQL("DROP TRIGGER IF EXISTS prevent_journal_lines_update")
            sdb.execSQL("DROP TRIGGER IF EXISTS prevent_journal_lines_delete")
            sdb.execSQL("DROP TRIGGER IF EXISTS prevent_currency_rates_update")
            sdb.execSQL("DROP TRIGGER IF EXISTS prevent_currency_rates_delete")

            sdb.execSQL("DELETE FROM journal_lines")
            sdb.execSQL("DELETE FROM journal_entries")
            sdb.execSQL("DELETE FROM document_items")
            sdb.execSQL("DELETE FROM allocations")
            sdb.execSQL("DELETE FROM documents")
            sdb.execSQL("DELETE FROM depreciation_runs")
            sdb.execSQL("DELETE FROM assets")
            sdb.execSQL("DELETE FROM stock_movements")
            sdb.execSQL("DELETE FROM card_packages")
            sdb.execSQL("DELETE FROM parties WHERE id != 'WALK_IN_CASH'")
            sdb.execSQL("DELETE FROM currency_rates")

            // 1. Restore Parties (using parameterized statement)
            val partiesArr = root.optJSONArray("parties") ?: JSONArray()
            val partyStmt = sdb.compileStatement("""
                INSERT OR REPLACE INTO parties 
                (id, name, phone, isCustomer, isVendor, isPartner, equityPercentageBasisPoints, creditLimitMinor, isActive, createdAt)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent())
            for (i in 0 until partiesArr.length()) {
                val p = partiesArr.getJSONObject(i)
                partyStmt.clearBindings()
                partyStmt.bindString(1, p.getString("id"))
                partyStmt.bindString(2, p.getString("name"))
                partyStmt.bindString(3, p.optString("phone", ""))
                partyStmt.bindLong(4, if (p.getBoolean("isCustomer")) 1L else 0L)
                partyStmt.bindLong(5, if (p.getBoolean("isVendor")) 1L else 0L)
                partyStmt.bindLong(6, if (p.getBoolean("isPartner")) 1L else 0L)
                partyStmt.bindLong(7, p.optLong("equityPercentageBasisPoints", 0L))
                partyStmt.bindLong(8, p.optLong("creditLimitMinor", 0L))
                partyStmt.bindLong(9, if (p.optBoolean("isActive", true)) 1L else 0L)
                partyStmt.bindLong(10, p.optLong("createdAt", System.currentTimeMillis()))
                partyStmt.executeInsert()
            }
            partyStmt.close()

            // 2. Restore Packages
            val pkgsArr = root.optJSONArray("packages") ?: JSONArray()
            val pkgStmt = sdb.compileStatement("""
                INSERT OR REPLACE INTO card_packages 
                (id, name, durationOrQuota, wholesalePriceMinor, retailPriceMinor, isActive)
                VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent())
            for (i in 0 until pkgsArr.length()) {
                val pkg = pkgsArr.getJSONObject(i)
                pkgStmt.clearBindings()
                pkgStmt.bindString(1, pkg.getString("id"))
                pkgStmt.bindString(2, pkg.getString("name"))
                pkgStmt.bindString(3, pkg.getString("durationOrQuota"))
                pkgStmt.bindLong(4, pkg.getLong("wholesalePriceMinor"))
                pkgStmt.bindLong(5, pkg.getLong("retailPriceMinor"))
                pkgStmt.bindLong(6, if (pkg.optBoolean("isActive", true)) 1L else 0L)
                pkgStmt.executeInsert()
            }
            pkgStmt.close()

            // 3. Restore Treasuries if present
            val treasuriesArr = root.optJSONArray("treasuries") ?: JSONArray()
            val trStmt = sdb.compileStatement("""
                INSERT OR REPLACE INTO treasury_accounts 
                (id, name, glAccountCode, currency, isActive, allowNegative)
                VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent())
            for (i in 0 until treasuriesArr.length()) {
                val tr = treasuriesArr.getJSONObject(i)
                trStmt.clearBindings()
                trStmt.bindString(1, tr.getString("id"))
                trStmt.bindString(2, tr.getString("name"))
                trStmt.bindString(3, tr.getString("glAccountCode"))
                trStmt.bindString(4, tr.getString("currency"))
                trStmt.bindLong(5, if (tr.optBoolean("isActive", true)) 1L else 0L)
                trStmt.bindLong(6, if (tr.optBoolean("allowNegative", false)) 1L else 0L)
                trStmt.executeInsert()
            }
            trStmt.close()

            // 4. Restore Documents
            val docsArr = root.optJSONArray("documents") ?: JSONArray()
            val docStmt = sdb.compileStatement("""
                INSERT INTO documents 
                (id, type, fiscalYear, docNumber, partyId, dateEpochDay, currency, exchangeRateMicros, rateZone, rateSource, totalMinor, totalBaseMinor, status, notes, reversalOfDocId, createdAt)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent())
            for (i in 0 until docsArr.length()) {
                val d = docsArr.getJSONObject(i)
                docStmt.clearBindings()
                docStmt.bindString(1, d.getString("id"))
                docStmt.bindString(2, d.getString("type"))
                docStmt.bindLong(3, d.getLong("fiscalYear"))
                docStmt.bindLong(4, d.getLong("docNumber"))
                docStmt.bindString(5, d.getString("partyId"))
                docStmt.bindLong(6, d.getLong("dateEpochDay"))
                docStmt.bindString(7, d.getString("currency"))
                docStmt.bindLong(8, d.getLong("exchangeRateMicros"))
                docStmt.bindString(9, d.optString("rateZone", "SANAA"))
                docStmt.bindString(10, d.optString("rateSource", "SYSTEM_DAILY"))
                docStmt.bindLong(11, d.getLong("totalMinor"))
                docStmt.bindLong(12, d.getLong("totalBaseMinor"))
                docStmt.bindString(13, d.getString("status"))
                docStmt.bindString(14, d.optString("notes", ""))
                if (d.isNull("reversalOfDocId")) docStmt.bindNull(15) else docStmt.bindString(15, d.getString("reversalOfDocId"))
                docStmt.bindLong(16, d.optLong("createdAt", System.currentTimeMillis()))
                docStmt.executeInsert()
            }
            docStmt.close()

            // 5. Restore Document Items
            val itemsArr = root.optJSONArray("document_items") ?: JSONArray()
            val itemStmt = sdb.compileStatement("""
                INSERT INTO document_items 
                (id, docId, itemIndex, packageId, description, accountCode, quantity, unitPriceMinor, totalMinor, isAsset)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent())
            for (i in 0 until itemsArr.length()) {
                val itm = itemsArr.getJSONObject(i)
                itemStmt.clearBindings()
                itemStmt.bindString(1, itm.getString("id"))
                itemStmt.bindString(2, itm.getString("docId"))
                itemStmt.bindLong(3, itm.getLong("itemIndex"))
                if (itm.isNull("packageId")) itemStmt.bindNull(4) else itemStmt.bindString(4, itm.getString("packageId"))
                itemStmt.bindString(5, itm.getString("description"))
                itemStmt.bindString(6, itm.getString("accountCode"))
                itemStmt.bindLong(7, itm.getLong("quantity"))
                itemStmt.bindLong(8, itm.getLong("unitPriceMinor"))
                itemStmt.bindLong(9, itm.getLong("totalMinor"))
                itemStmt.bindLong(10, if (itm.optBoolean("isAsset", false)) 1L else 0L)
                itemStmt.executeInsert()
            }
            itemStmt.close()

            // 6. Restore Journal Entries
            val entriesArr = root.optJSONArray("journal_entries") ?: JSONArray()
            val entryStmt = sdb.compileStatement("""
                INSERT INTO journal_entries 
                (id, docId, entryNumber, entryDateEpochDay, type, memo, createdAt)
                VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent())
            for (i in 0 until entriesArr.length()) {
                val je = entriesArr.getJSONObject(i)
                entryStmt.clearBindings()
                entryStmt.bindString(1, je.getString("id"))
                entryStmt.bindString(2, je.getString("docId"))
                entryStmt.bindLong(3, je.getLong("entryNumber"))
                entryStmt.bindLong(4, je.getLong("entryDateEpochDay"))
                entryStmt.bindString(5, je.getString("type"))
                entryStmt.bindString(6, je.getString("memo"))
                entryStmt.bindLong(7, je.optLong("createdAt", System.currentTimeMillis()))
                entryStmt.executeInsert()
            }
            entryStmt.close()

            // 7. Restore Journal Lines
            val linesArr = root.optJSONArray("journal_lines") ?: JSONArray()
            val lineStmt = sdb.compileStatement("""
                INSERT INTO journal_lines 
                (id, entryId, lineNo, accountCode, partyId, treasuryId, origMinor, currency, exchangeRateMicros, baseDebitMinor, baseCreditMinor, memo)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent())
            for (i in 0 until linesArr.length()) {
                val jl = linesArr.getJSONObject(i)
                lineStmt.clearBindings()
                lineStmt.bindString(1, jl.getString("id"))
                lineStmt.bindString(2, jl.getString("entryId"))
                lineStmt.bindLong(3, jl.getLong("lineNo"))
                lineStmt.bindString(4, jl.getString("accountCode"))
                if (jl.isNull("partyId")) lineStmt.bindNull(5) else lineStmt.bindString(5, jl.getString("partyId"))
                if (jl.isNull("treasuryId")) lineStmt.bindNull(6) else lineStmt.bindString(6, jl.getString("treasuryId"))
                lineStmt.bindLong(7, jl.getLong("origMinor"))
                lineStmt.bindString(8, jl.getString("currency"))
                lineStmt.bindLong(9, jl.getLong("exchangeRateMicros"))
                lineStmt.bindLong(10, jl.getLong("baseDebitMinor"))
                lineStmt.bindLong(11, jl.getLong("baseCreditMinor"))
                lineStmt.bindString(12, jl.getString("memo"))
                lineStmt.executeInsert()
            }
            lineStmt.close()

            // 8. Restore Allocations
            val allocArr = root.optJSONArray("allocations") ?: JSONArray()
            val allocStmt = sdb.compileStatement("""
                INSERT INTO allocations 
                (id, paymentDocId, invoiceDocId, allocatedOrigMinor, allocatedBaseMinor, exchangeGainLossMinor, isVoided, createdAt)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent())
            for (i in 0 until allocArr.length()) {
                val al = allocArr.getJSONObject(i)
                allocStmt.clearBindings()
                allocStmt.bindString(1, al.getString("id"))
                allocStmt.bindString(2, al.getString("paymentDocId"))
                allocStmt.bindString(3, al.getString("invoiceDocId"))
                allocStmt.bindLong(4, al.getLong("allocatedOrigMinor"))
                allocStmt.bindLong(5, al.getLong("allocatedBaseMinor"))
                allocStmt.bindLong(6, al.optLong("exchangeGainLossMinor", 0L))
                allocStmt.bindLong(7, if (al.optBoolean("isVoided", false)) 1L else 0L)
                allocStmt.bindLong(8, al.optLong("createdAt", System.currentTimeMillis()))
                allocStmt.executeInsert()
            }
            allocStmt.close()

            // 9. Restore Assets
            val assetsArr = root.optJSONArray("assets") ?: JSONArray()
            val assetStmt = sdb.compileStatement("""
                INSERT OR REPLACE INTO assets 
                (id, docId, name, purchaseDateEpochDay, purchaseCostMinor, salvageValueMinor, usefulLifeMonths, accumulatedDepreciationMinor, isDisposed)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent())
            for (i in 0 until assetsArr.length()) {
                val ast = assetsArr.getJSONObject(i)
                assetStmt.clearBindings()
                assetStmt.bindString(1, ast.getString("id"))
                assetStmt.bindString(2, ast.getString("docId"))
                assetStmt.bindString(3, ast.getString("name"))
                assetStmt.bindLong(4, ast.getLong("purchaseDateEpochDay"))
                assetStmt.bindLong(5, ast.getLong("purchaseCostMinor"))
                assetStmt.bindLong(6, ast.optLong("salvageValueMinor", 0L))
                assetStmt.bindLong(7, ast.getLong("usefulLifeMonths"))
                assetStmt.bindLong(8, ast.optLong("accumulatedDepreciationMinor", 0L))
                assetStmt.bindLong(9, if (ast.optBoolean("isDisposed", false)) 1L else 0L)
                assetStmt.executeInsert()
            }
            assetStmt.close()

            // 10. Restore Depreciation Runs
            val depRunsArr = root.optJSONArray("depreciation_runs") ?: JSONArray()
            val depStmt = sdb.compileStatement("""
                INSERT OR REPLACE INTO depreciation_runs 
                (id, periodYear, periodMonth, assetId, journalEntryId, depreciationAmountMinor)
                VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent())
            for (i in 0 until depRunsArr.length()) {
                val dr = depRunsArr.getJSONObject(i)
                depStmt.clearBindings()
                depStmt.bindString(1, dr.getString("id"))
                depStmt.bindLong(2, dr.getLong("periodYear"))
                depStmt.bindLong(3, dr.getLong("periodMonth"))
                depStmt.bindString(4, dr.getString("assetId"))
                depStmt.bindString(5, dr.getString("journalEntryId"))
                depStmt.bindLong(6, dr.getLong("depreciationAmountMinor"))
                depStmt.executeInsert()
            }
            depStmt.close()

            // 11. Restore Number Sequences (CRITICAL: prevents sequence collision on new documents)
            val seqArr = root.optJSONArray("number_sequences") ?: JSONArray()
            val seqStmt = sdb.compileStatement("""
                INSERT OR REPLACE INTO number_sequences (docType, fiscalYear, nextValue)
                VALUES (?, ?, ?)
            """.trimIndent())
            for (i in 0 until seqArr.length()) {
                val sq = seqArr.getJSONObject(i)
                seqStmt.clearBindings()
                seqStmt.bindString(1, sq.getString("docType"))
                seqStmt.bindLong(2, sq.getLong("fiscalYear"))
                seqStmt.bindLong(3, sq.getLong("nextValue"))
                seqStmt.executeInsert()
            }
            seqStmt.close()

            // 12. Restore Fiscal Periods
            val periodsArr = root.optJSONArray("fiscal_periods") ?: JSONArray()
            val periodStmt = sdb.compileStatement("""
                INSERT OR REPLACE INTO fiscal_periods (id, year, month, isClosed, closedAt)
                VALUES (?, ?, ?, ?, ?)
            """.trimIndent())
            for (i in 0 until periodsArr.length()) {
                val fp = periodsArr.getJSONObject(i)
                periodStmt.clearBindings()
                periodStmt.bindString(1, fp.getString("id"))
                periodStmt.bindLong(2, fp.getLong("year"))
                periodStmt.bindLong(3, fp.getLong("month"))
                periodStmt.bindLong(4, if (fp.optBoolean("isClosed", false)) 1L else 0L)
                if (fp.isNull("closedAt")) periodStmt.bindNull(5) else periodStmt.bindLong(5, fp.getLong("closedAt"))
                periodStmt.executeInsert()
            }
            periodStmt.close()

            // 13. Restore Currency Rates
            val ratesArr = root.optJSONArray("currency_rates") ?: JSONArray()
            val rateStmt = sdb.compileStatement("""
                INSERT OR REPLACE INTO currency_rates 
                (id, currency, zone, rateMicros, effectiveDateEpochDay, createdAt, createdBy, reason)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent())
            for (i in 0 until ratesArr.length()) {
                val cr = ratesArr.getJSONObject(i)
                rateStmt.clearBindings()
                rateStmt.bindString(1, cr.getString("id"))
                rateStmt.bindString(2, cr.optString("currency", cr.optString("fromCurrency", "USD")))
                rateStmt.bindString(3, cr.optString("zone", "SANAA"))
                rateStmt.bindLong(4, cr.getLong("rateMicros"))
                rateStmt.bindLong(5, cr.getLong("effectiveDateEpochDay"))
                rateStmt.bindLong(6, cr.optLong("createdAt", cr.optLong("updatedAt", System.currentTimeMillis())))
                rateStmt.bindString(7, cr.optString("createdBy", "SYSTEM"))
                rateStmt.bindString(8, cr.optString("reason", ""))
                rateStmt.executeInsert()
            }
            rateStmt.close()

            // 14. Restore Stock Movements
            val stockArr = root.optJSONArray("stock_movements") ?: JSONArray()
            val stockStmt = sdb.compileStatement("""
                INSERT OR REPLACE INTO stock_movements 
                (id, packageId, docId, type, quantity, movementDateEpochDay, createdAt)
                VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent())
            for (i in 0 until stockArr.length()) {
                val sm = stockArr.getJSONObject(i)
                stockStmt.clearBindings()
                stockStmt.bindString(1, sm.getString("id"))
                stockStmt.bindString(2, sm.getString("packageId"))
                if (sm.isNull("docId")) stockStmt.bindNull(3) else stockStmt.bindString(3, sm.getString("docId"))
                stockStmt.bindString(4, sm.getString("type"))
                stockStmt.bindLong(5, sm.getLong("quantity"))
                stockStmt.bindLong(6, sm.getLong("movementDateEpochDay"))
                stockStmt.bindLong(7, sm.optLong("createdAt", System.currentTimeMillis()))
                stockStmt.executeInsert()
            }
            stockStmt.close()

            // 15. Restore Network Devices
            val devJsonArr = root.optJSONArray("network_devices") ?: root.optJSONArray("networkDevices")
            if (devJsonArr != null && devJsonArr.length() > 0) {
                val restoredDevices = mutableListOf<NetworkDevice>()
                for (i in 0 until devJsonArr.length()) {
                    val dObj = devJsonArr.getJSONObject(i)
                    val typeStr = dObj.optString("deviceType", DeviceType.ACCESS_POINT.name)
                    val statusStr = dObj.optString("status", DeviceStatus.ONLINE.name)
                    restoredDevices.add(
                        NetworkDevice(
                            id = dObj.optString("id", UUID.randomUUID().toString()),
                            name = dObj.getString("name"),
                            ipAddress = dObj.getString("ipAddress"),
                            deviceType = runCatching { DeviceType.valueOf(typeStr) }.getOrDefault(DeviceType.ACCESS_POINT),
                            macAddress = dObj.optString("macAddress", ""),
                            towerLocation = dObj.optString("towerLocation", "البرج الرئيسي"),
                            frequency = dObj.optString("frequency", "5500 MHz"),
                            channelWidth = dObj.optString("channelWidth", "20/40 MHz"),
                            status = runCatching { DeviceStatus.valueOf(statusStr) }.getOrDefault(DeviceStatus.ONLINE),
                            notes = dObj.optString("notes", ""),
                            model = dObj.optString("model", "MikroTik RouterBOARD"),
                            managementPort = dObj.optInt("managementPort", 8728),
                            subnet = dObj.optString("subnet", "10.10.1.0/24"),
                            credentials = dObj.optString("credentials", "admin")
                        )
                    )
                }
                effectiveDeviceDao?.deleteAllDevices()
                effectiveDeviceDao?.insertAll(restoredDevices)
            }

            // 16. Restore Network Profile & FX Configuration
            val profObj = root.optJSONObject("network_profile") ?: root.optJSONObject("networkProfile")
            if (profObj != null) {
                val profileDto = NetworkProfileBackupDto(
                    networkName = profObj.optString("networkName", "شبكة توزيع الإنترنت"),
                    ownerName = profObj.optString("ownerName", "مدير الشبكة"),
                    location = profObj.optString("location", "المركز الرئيسي"),
                    welcomeMessage = profObj.optString("welcomeMessage", "أهلاً بكم في شبكتنا - إنترنت فائق السرعة"),
                    supportPhone = profObj.optString("supportPhone", "770000000"),
                    supportWhatsapp = profObj.optString("supportWhatsapp", "967770000000"),
                    mainRouterModel = profObj.optString("mainRouterModel", "MikroTik CCR2004-16G-2S+"),
                    routerOsVersion = profObj.optString("routerOsVersion", "v7.16"),
                    hotspotDomain = profObj.optString("hotspotDomain", "login.net"),
                    hotspotServerName = profObj.optString("hotspotServerName", "hotspot1"),
                    adminPort = profObj.optInt("adminPort", 8728),
                    primaryDns = profObj.optString("primaryDns", "8.8.8.8"),
                    secondaryDns = profObj.optString("secondaryDns", "1.1.1.1"),
                    approvedSubnet = profObj.optString("approvedSubnet", "10.10.0.0/16"),
                    rateZone = profObj.optString("rateZone", "SANAA"),
                    defaultUsdRateMicros = profObj.optLong("defaultUsdRateMicros", 535_000_000L),
                    defaultSarRateMicros = profObj.optLong("defaultSarRateMicros", 140_500_000L),
                    updatedAt = profObj.optLong("updatedAt", System.currentTimeMillis())
                )
                saveProfileToPrefs(profileDto)
                effectiveNetworkRepository?.restoreNetworkProfile(profileDto)
            }

            // Re-install SQLite triggers
            AppDatabase.installTriggers(sdb)

            // Verify all mathematical and ledger invariants before finalizing
            invariants.verifyAll(failFast = true)
        }

        "تمت استعادة البيانات بنجاح ومطابقة تسلسل المستندات والثوابت المحاسبية 100%"
    }

    suspend fun createBackupPayload(): AppBackupData {
        val rawJson = exportDatabaseToJson()
        return DataJsonHelper.parseBackupDataFromJson(rawJson)
    }

    suspend fun exportData(): String = exportDatabaseToJson()

    suspend fun restoreBackup(payload: AppBackupData): Result<String> {
        val json = DataJsonHelper.exportBackupDataToJson(payload)
        return restoreDatabaseFromJson(json)
    }

    suspend fun importData(payload: AppBackupData): Result<String> = restoreBackup(payload)

    suspend fun restoreBackup(jsonString: String): Result<String> = restoreDatabaseFromJson(jsonString)

    suspend fun importData(jsonString: String): Result<String> = restoreDatabaseFromJson(jsonString)

    private fun extractProfileFromPrefs(): NetworkProfileBackupDto? {
        val prefs = context?.getSharedPreferences("sammikrotik_network_prefs", Context.MODE_PRIVATE) ?: return null
        if (!prefs.contains("network_name") && !prefs.contains("networkName")) return null
        return NetworkProfileBackupDto(
            networkName = prefs.getString("network_name", null) ?: prefs.getString("networkName", "شبكة توزيع الإنترنت") ?: "شبكة توزيع الإنترنت",
            ownerName = prefs.getString("owner_name", null) ?: prefs.getString("ownerName", "مدير الشبكة") ?: "مدير الشبكة",
            location = prefs.getString("location", "المركز الرئيسي") ?: "المركز الرئيسي",
            welcomeMessage = prefs.getString("welcome_message", null) ?: prefs.getString("welcomeMessage", "أهلاً بكم في شبكتنا - إنترنت فائق السرعة") ?: "أهلاً بكم في شبكتنا - إنترنت فائق السرعة",
            supportPhone = prefs.getString("support_phone", null) ?: prefs.getString("supportPhone", "770000000") ?: "770000000",
            supportWhatsapp = prefs.getString("support_whatsapp", null) ?: prefs.getString("supportWhatsapp", "967770000000") ?: "967770000000",
            mainRouterModel = prefs.getString("main_router_model", null) ?: prefs.getString("mainRouterModel", "MikroTik CCR2004-16G-2S+") ?: "MikroTik CCR2004-16G-2S+",
            routerOsVersion = prefs.getString("router_os_version", null) ?: prefs.getString("routerOsVersion", "v7.16") ?: "v7.16",
            hotspotDomain = prefs.getString("hotspot_domain", null) ?: prefs.getString("hotspotDomain", "login.net") ?: "login.net",
            hotspotServerName = prefs.getString("hotspot_server_name", null) ?: prefs.getString("hotspotServerName", "hotspot1") ?: "hotspot1",
            adminPort = prefs.getInt("admin_port", prefs.getInt("adminPort", 8728)),
            primaryDns = prefs.getString("primary_dns", null) ?: prefs.getString("primaryDns", "8.8.8.8") ?: "8.8.8.8",
            secondaryDns = prefs.getString("secondary_dns", null) ?: prefs.getString("secondaryDns", "1.1.1.1") ?: "1.1.1.1",
            approvedSubnet = prefs.getString("approved_subnet", null) ?: prefs.getString("approvedSubnet", "10.10.0.0/16") ?: "10.10.0.0/16",
            rateZone = prefs.getString("rate_zone", null) ?: prefs.getString("rateZone", "SANAA") ?: "SANAA",
            defaultUsdRateMicros = prefs.getLong("default_usd_rate_micros", prefs.getLong("defaultUsdRateMicros", 535_000_000L)),
            defaultSarRateMicros = prefs.getLong("default_sar_rate_micros", prefs.getLong("defaultSarRateMicros", 140_500_000L)),
            updatedAt = prefs.getLong("updated_at", prefs.getLong("updatedAt", System.currentTimeMillis()))
        )
    }

    private fun saveProfileToPrefs(dto: NetworkProfileBackupDto) {
        context?.getSharedPreferences("sammikrotik_network_prefs", Context.MODE_PRIVATE)?.edit()
            ?.putString("network_name", dto.networkName)
            ?.putString("networkName", dto.networkName)
            ?.putString("owner_name", dto.ownerName)
            ?.putString("ownerName", dto.ownerName)
            ?.putString("location", dto.location)
            ?.putString("welcome_message", dto.welcomeMessage)
            ?.putString("welcomeMessage", dto.welcomeMessage)
            ?.putString("support_phone", dto.supportPhone)
            ?.putString("supportPhone", dto.supportPhone)
            ?.putString("support_whatsapp", dto.supportWhatsapp)
            ?.putString("supportWhatsapp", dto.supportWhatsapp)
            ?.putString("main_router_model", dto.mainRouterModel)
            ?.putString("mainRouterModel", dto.mainRouterModel)
            ?.putString("router_os_version", dto.routerOsVersion)
            ?.putString("routerOsVersion", dto.routerOsVersion)
            ?.putString("hotspot_domain", dto.hotspotDomain)
            ?.putString("hotspotDomain", dto.hotspotDomain)
            ?.putString("hotspot_server_name", dto.hotspotServerName)
            ?.putString("hotspotServerName", dto.hotspotServerName)
            ?.putInt("admin_port", dto.adminPort)
            ?.putInt("adminPort", dto.adminPort)
            ?.putString("primary_dns", dto.primaryDns)
            ?.putString("primaryDns", dto.primaryDns)
            ?.putString("secondary_dns", dto.secondaryDns)
            ?.putString("secondaryDns", dto.secondaryDns)
            ?.putString("approved_subnet", dto.approvedSubnet)
            ?.putString("approvedSubnet", dto.approvedSubnet)
            ?.putString("rate_zone", dto.rateZone)
            ?.putString("rateZone", dto.rateZone)
            ?.putLong("default_usd_rate_micros", dto.defaultUsdRateMicros)
            ?.putLong("defaultUsdRateMicros", dto.defaultUsdRateMicros)
            ?.putLong("default_sar_rate_micros", dto.defaultSarRateMicros)
            ?.putLong("defaultSarRateMicros", dto.defaultSarRateMicros)
            ?.putLong("updated_at", dto.updatedAt)
            ?.putLong("updatedAt", dto.updatedAt)
            ?.apply()
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
