package com.example.util

import com.example.core.model.CurrencyCode
import com.example.data.ledger.PurchaseItemSpec
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
import com.example.data.network.DeviceEntity
import com.example.data.network.DeviceStatus
import com.example.data.network.DeviceType
import com.example.data.network.NetworkDevice
import com.example.data.network.SubnetRange
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.roundToLong

data class NetworkProfileBackupDto(
    val networkName: String = "شبكة توزيع الإنترنت",
    val ownerName: String = "مدير الشبكة",
    val location: String = "المركز الرئيسي",
    val welcomeMessage: String = "أهلاً بكم في شبكتنا - إنترنت فائق السرعة",
    val supportPhone: String = "770000000",
    val supportWhatsapp: String = "967770000000",
    val mainRouterModel: String = "MikroTik CCR2004-16G-2S+",
    val routerOsVersion: String = "v7.16",
    val hotspotDomain: String = "login.net",
    val hotspotServerName: String = "hotspot1",
    val adminPort: Int = 8728,
    val primaryDns: String = "8.8.8.8",
    val secondaryDns: String = "1.1.1.1",
    val approvedSubnet: String = "10.10.0.0/16",
    val rateZone: String = "SANAA",
    val defaultUsdRateMicros: Long = 535_000_000L,
    val defaultSarRateMicros: Long = 140_500_000L,
    val updatedAt: Long = System.currentTimeMillis()
)

data class AppBackupData(
    val schemaVersion: Int = 1,
    val timestamp: Long = System.currentTimeMillis(),
    val organization: OrganizationEntity? = null,
    val parties: List<PartyEntity> = emptyList(),
    val packages: List<CardPackageEntity> = emptyList(),
    val treasuries: List<TreasuryAccountEntity> = emptyList(),
    val documents: List<DocumentEntity> = emptyList(),
    val documentItems: List<DocumentItemEntity> = emptyList(),
    val journalEntries: List<JournalEntryEntity> = emptyList(),
    val journalLines: List<JournalLineEntity> = emptyList(),
    val allocations: List<AllocationEntity> = emptyList(),
    val assets: List<AssetEntity> = emptyList(),
    val depreciationRuns: List<DepreciationRunEntity> = emptyList(),
    val numberSequences: List<NumberSequenceEntity> = emptyList(),
    val fiscalPeriods: List<FiscalPeriodEntity> = emptyList(),
    val currencyRates: List<CurrencyRateEntity> = emptyList(),
    val stockMovements: List<StockMovementEntity> = emptyList(),
    val networkDevices: List<DeviceEntity> = emptyList(),
    val networkSubnets: List<SubnetRange> = emptyList(),
    val networkProfile: NetworkProfileBackupDto? = null,
    val sha256: String = ""
)

typealias CompleteBackupPayload = AppBackupData

object DataJsonHelper {

    // ==========================================
    // 1. Parties (Customers / Grocery Agents) JSON
    // ==========================================

    fun exportPartiesToJson(parties: List<PartyEntity>, networkName: String = "شبكة سام ميكروتك"): String {
        val root = JSONObject()
        root.put("format", "SAM_CUSTOMER_BACKUP")
        root.put("version", 1)
        root.put("networkName", networkName)
        root.put("customerCount", parties.size)

        val array = JSONArray()
        for (p in parties) {
            val obj = JSONObject().apply {
                put("name", p.name)
                put("phone", p.phone)
                put("isCustomer", p.isCustomer)
                put("isVendor", p.isVendor)
                put("isPartner", p.isPartner)
                put("creditLimitMinor", p.creditLimitMinor)
                put("equityPercentageBasisPoints", p.equityPercentageBasisPoints)
            }
            array.put(obj)
        }
        root.put("customers", array)
        return root.toString(2)
    }

    fun parsePartiesFromJson(jsonString: String): List<PartyEntity> {
        val trimmed = jsonString.trim()
        val array: JSONArray = if (trimmed.startsWith("{")) {
            val root = JSONObject(trimmed)
            when {
                root.has("customers") -> root.getJSONArray("customers")
                root.has("parties") -> root.getJSONArray("parties")
                root.has("retailers") -> root.getJSONArray("retailers")
                else -> JSONArray()
            }
        } else {
            JSONArray(trimmed)
        }

        val list = mutableListOf<PartyEntity>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val name = obj.optString("name", "").trim()
            if (name.isNotEmpty()) {
                val phone = obj.optString("phone", "").trim()
                val isCustomer = obj.optBoolean("isCustomer", true)
                val isVendor = obj.optBoolean("isVendor", false)
                val isPartner = obj.optBoolean("isPartner", false)

                val creditLimit = if (obj.has("creditLimitMinor")) {
                    obj.optLong("creditLimitMinor", 0L)
                } else if (obj.has("balanceOwed")) {
                    (obj.optDouble("balanceOwed", 0.0) * 100.0).roundToLong().coerceAtLeast(0L)
                } else {
                    0L
                }

                list.add(
                    PartyEntity(
                        id = UUID.randomUUID().toString(),
                        name = name,
                        phone = phone,
                        isCustomer = isCustomer,
                        isVendor = isVendor,
                        isPartner = isPartner,
                        creditLimitMinor = creditLimit,
                        equityPercentageBasisPoints = obj.optInt("equityPercentageBasisPoints", 0),
                        isActive = true,
                        createdAt = System.currentTimeMillis()
                    )
                )
            }
        }
        return list
    }

    // ==========================================
    // 2. Network Devices JSON (Full Backward Compatibility)
    // ==========================================

    data class ParsedDevicesResult(
        val networkName: String?,
        val devices: List<NetworkDevice>
    )

    fun exportDevicesToJson(devices: List<NetworkDevice>, networkName: String = "شبكة سام ميكروتك"): String {
        val root = JSONObject()
        root.put("format", "MIKROTIK_DEVICE_BACKUP")
        root.put("version", 1)
        root.put("networkName", networkName)
        root.put("deviceCount", devices.size)

        val array = JSONArray()
        for (d in devices) {
            val obj = JSONObject().apply {
                put("name", d.name)
                put("ipAddress", d.ipAddress)
                put("deviceType", d.deviceType.name)
                put("macAddress", d.macAddress)
                put("locationArea", d.towerLocation)
                put("frequencyOrSsid", d.frequency)
                put("channelWidth", d.channelWidth)
                put("status", d.status.name)
                put("notes", d.notes)
            }
            array.put(obj)
        }
        root.put("devices", array)
        return root.toString(2)
    }

    fun parseDevicesFromJson(jsonString: String): ParsedDevicesResult {
        val trimmed = jsonString.trim()
        var extractedNetworkName: String? = null
        val array: JSONArray = if (trimmed.startsWith("{")) {
            val root = JSONObject(trimmed)
            if (root.has("networkName")) {
                extractedNetworkName = root.optString("networkName").takeIf { it.isNotBlank() }
            }
            when {
                root.has("devices") -> root.getJSONArray("devices")
                root.has("deviceList") -> root.getJSONArray("deviceList")
                else -> JSONArray()
            }
        } else {
            JSONArray(trimmed)
        }

        val list = mutableListOf<NetworkDevice>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val name = obj.optString("name", "جهاز ${i + 1}").trim()
            val ipAddress = obj.optString("ipAddress", "").trim()
            if (ipAddress.isEmpty()) continue

            val rawType = obj.optString("deviceType", "").trim().lowercase()
            val mappedType = when {
                rawType.contains("مرسل") || rawType.contains("access") || rawType.contains("ap") || rawType.contains("sector") -> DeviceType.ACCESS_POINT
                rawType.contains("لاقط") || rawType.contains("مستقبل") || rawType.contains("station") -> DeviceType.STATION
                rawType.contains("سويتش") || rawType.contains("switch") -> DeviceType.SWITCH
                rawType.contains("راوتر") || rawType.contains("router") || rawType.contains("mikrotik") || rawType.contains("ccr") -> DeviceType.ROUTER
                rawType.contains("هوائي") || rawType.contains("باوربيم") || rawType.contains("antenna") || rawType.contains("dish") -> DeviceType.ANTENNA
                else -> DeviceType.ACCESS_POINT
            }

            val rawStatus = obj.optString("status", "ONLINE").trim().uppercase()
            val mappedStatus = when {
                rawStatus.contains("OFFLINE") || rawStatus.contains("مفصول") || rawStatus.contains("معطل") -> DeviceStatus.OFFLINE
                rawStatus.contains("WARN") || rawStatus.contains("ضعيف") -> DeviceStatus.WARNING
                else -> DeviceStatus.ONLINE
            }

            val location = when {
                obj.has("locationArea") && obj.getString("locationArea").isNotBlank() -> obj.getString("locationArea").trim()
                obj.has("towerLocation") && obj.getString("towerLocation").isNotBlank() -> obj.getString("towerLocation").trim()
                obj.has("location") && obj.getString("location").isNotBlank() -> obj.getString("location").trim()
                else -> "البرج الرئيسي"
            }

            val freq = when {
                obj.has("frequencyOrSsid") && obj.getString("frequencyOrSsid").isNotBlank() -> obj.getString("frequencyOrSsid").trim()
                obj.has("frequency") && obj.getString("frequency").isNotBlank() -> obj.getString("frequency").trim()
                obj.has("model") && obj.getString("model").isNotBlank() -> obj.getString("model").trim()
                else -> "5500 MHz"
            }

            val model = obj.optString("model", "").trim()
            val port = obj.optString("portOrInterface", "").trim()
            val rawNotes = obj.optString("notes", "").trim()
            val combinedNotes = buildString {
                if (rawNotes.isNotEmpty()) append(rawNotes)
                if (model.isNotEmpty() && !freq.contains(model)) {
                    if (isNotEmpty()) append(" • ")
                    append("الموديل: $model")
                }
                if (port.isNotEmpty()) {
                    if (isNotEmpty()) append(" • ")
                    append("المنفذ: $port")
                }
            }

            list.add(
                NetworkDevice(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    ipAddress = ipAddress,
                    deviceType = mappedType,
                    macAddress = obj.optString("macAddress", ""),
                    towerLocation = location,
                    frequency = freq,
                    channelWidth = obj.optString("channelWidth", "20/40 MHz"),
                    status = mappedStatus,
                    notes = combinedNotes
                )
            )
        }
        return ParsedDevicesResult(extractedNetworkName, list)
    }

    // ==========================================
    // 3. Purchase Invoices JSON (Full Backward Compatibility)
    // ==========================================

    data class ImportedPurchaseDraft(
        val vendorName: String,
        val currencyCode: CurrencyCode,
        val exchangeRateMicros: Long,
        val items: List<PurchaseItemSpec>,
        val notes: String = "",
        val isCash: Boolean = false
    )

    fun exportPurchasesToJson(
        purchases: List<ImportedPurchaseDraft>,
        networkName: String = "شبكة سام ميكروتك"
    ): String {
        val root = JSONObject()
        root.put("format", "SAM_PURCHASE_INVOICE_BACKUP")
        root.put("version", 1)
        root.put("networkName", networkName)
        root.put("invoiceCount", purchases.size)

        val array = JSONArray()
        for (p in purchases) {
            val obj = JSONObject().apply {
                put("supplierName", p.vendorName)
                put("currency", p.currencyCode.name)
                put("exchangeRateMicros", p.exchangeRateMicros)
                put("notes", p.notes)
                put("isCash", p.isCash)
                val itemsArr = JSONArray()
                for (item in p.items) {
                    val itemObj = JSONObject().apply {
                        put("description", item.description)
                        put("accountCode", item.accountCode)
                        put("quantity", item.quantity)
                        put("unitPriceMinor", item.unitPriceMinor)
                        put("isAsset", item.isAsset)
                        put("usefulLifeMonths", item.usefulLifeMonths ?: 36)
                    }
                    itemsArr.put(itemObj)
                }
                put("items", itemsArr)
            }
            array.put(obj)
        }
        root.put("invoices", array)
        return root.toString(2)
    }

    fun parsePurchasesFromJson(jsonString: String): List<ImportedPurchaseDraft> {
        val trimmed = jsonString.trim()
        val array: JSONArray = if (trimmed.startsWith("{")) {
            val root = JSONObject(trimmed)
            when {
                root.has("invoices") -> root.getJSONArray("invoices")
                root.has("purchases") -> root.getJSONArray("purchases")
                root.has("purchaseInvoices") -> root.getJSONArray("purchaseInvoices")
                root.has("bills") -> root.getJSONArray("bills")
                root.has("data") && root.get("data") is JSONArray -> root.getJSONArray("data")
                // Single invoice root object (e.g., from OCR invoice scan, Gemini/ChatGPT export)
                root.has("items") || root.has("company_info") || root.has("invoice_info") ||
                root.has("supplierName") || root.has("vendorName") || root.has("company") ||
                root.has("supplier") || root.has("seller") || root.has("summary") -> JSONArray().apply { put(root) }
                else -> JSONArray()
            }
        } else if (trimmed.startsWith("[")) {
            JSONArray(trimmed)
        } else {
            JSONArray()
        }

        val list = mutableListOf<ImportedPurchaseDraft>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val vendorName = when {
                obj.has("supplierName") && obj.getString("supplierName").isNotBlank() -> obj.getString("supplierName").trim()
                obj.has("vendorName") && obj.getString("vendorName").isNotBlank() -> obj.getString("vendorName").trim()
                obj.optJSONObject("company_info")?.optString("name")?.isNotBlank() == true -> obj.getJSONObject("company_info").getString("name").trim()
                obj.optJSONObject("supplier_info")?.optString("name")?.isNotBlank() == true -> obj.getJSONObject("supplier_info").getString("name").trim()
                obj.optJSONObject("vendor_info")?.optString("name")?.isNotBlank() == true -> obj.getJSONObject("vendor_info").getString("name").trim()
                obj.optJSONObject("seller")?.optString("name")?.isNotBlank() == true -> obj.getJSONObject("seller").getString("name").trim()
                obj.optJSONObject("store")?.optString("name")?.isNotBlank() == true -> obj.getJSONObject("store").getString("name").trim()
                obj.optJSONObject("company")?.optString("name")?.isNotBlank() == true -> obj.getJSONObject("company").getString("name").trim()
                obj.has("companyName") && obj.getString("companyName").isNotBlank() -> obj.getString("companyName").trim()
                obj.has("sellerName") && obj.getString("sellerName").isNotBlank() -> obj.getString("sellerName").trim()
                else -> "مورد عام"
            }

            val rawCurr = when {
                obj.has("currency") -> obj.optString("currency")
                obj.optJSONObject("invoice_info")?.has("currency") == true -> obj.getJSONObject("invoice_info").optString("currency")
                obj.optJSONObject("header")?.has("currency") == true -> obj.getJSONObject("header").optString("currency")
                else -> "YER"
            }.trim().uppercase()

            val currency = when {
                rawCurr.contains("USD") || rawCurr.contains("دولار") || rawCurr.contains("$") -> CurrencyCode.USD
                rawCurr.contains("SAR") || rawCurr.contains("سعودي") || rawCurr.contains("ر.س") || rawCurr.contains("SR") -> CurrencyCode.SAR
                else -> CurrencyCode.YER
            }

            val rateMicros = obj.optLong("exchangeRateMicros", 0L)

            val invNum = when {
                obj.has("invoiceNumber") -> obj.optString("invoiceNumber")
                obj.optJSONObject("invoice_info")?.has("invoice_number") == true -> obj.getJSONObject("invoice_info").optString("invoice_number")
                obj.optJSONObject("invoice_info")?.has("invoiceNumber") == true -> obj.getJSONObject("invoice_info").optString("invoiceNumber")
                else -> null
            }
            val invDate = obj.optJSONObject("invoice_info")?.optString("date")
            val rawNotes = obj.optString("notes")
            val statementNotes = if (obj.has("statement_notes")) {
                val arr = obj.optJSONArray("statement_notes")
                if (arr != null) {
                    (0 until arr.length()).map { arr.getString(it) }.joinToString("، ")
                } else ""
            } else ""

            val notes = buildString {
                if (!invNum.isNullOrBlank()) append("فاتورة رقم $invNum")
                if (!invDate.isNullOrBlank()) append(" بتأريخ $invDate")
                if (rawNotes.isNotBlank()) append(" | $rawNotes")
                if (statementNotes.isNotBlank()) append(" | ملاحظات السداد: $statementNotes")
                if (isEmpty()) append("استيراد فاتورة مورد من صورة/JSON")
            }

            val invoiceTitle = when {
                obj.optJSONObject("invoice_info")?.has("title") == true -> obj.getJSONObject("invoice_info").optString("title")
                obj.has("title") -> obj.optString("title")
                else -> ""
            }

            val isCashDetected = invoiceTitle.contains("نقد") || invoiceTitle.contains("كاش") ||
                invoiceTitle.contains("CASH", ignoreCase = true) ||
                rawNotes.contains("نقد") || rawNotes.contains("كاش") ||
                statementNotes.contains("نقد") || statementNotes.contains("كاش") ||
                obj.optBoolean("isCash", false) ||
                obj.optString("paymentType").contains("نقد") ||
                obj.optString("type").contains("نقد")

            val items = mutableListOf<PurchaseItemSpec>()
            if (obj.has("items")) {
                val itemsArr = obj.getJSONArray("items")
                for (j in 0 until itemsArr.length()) {
                    val itObj = itemsArr.getJSONObject(j)
                    val desc = when {
                        itObj.has("description") && itObj.getString("description").isNotBlank() -> itObj.getString("description").trim()
                        itObj.has("name") && itObj.getString("name").isNotBlank() -> itObj.getString("name").trim()
                        itObj.has("item_name") && itObj.getString("item_name").isNotBlank() -> itObj.getString("item_name").trim()
                        itObj.has("title") && itObj.getString("title").isNotBlank() -> itObj.getString("title").trim()
                        else -> "بند مشتريات #${j + 1}"
                    }

                    val quantity = when {
                        itObj.has("quantity") -> itObj.optInt("quantity", 1)
                        itObj.has("qty") -> itObj.optInt("qty", 1)
                        itObj.has("count") -> itObj.optInt("count", 1)
                        else -> 1
                    }.coerceAtLeast(1)

                    val unitPriceMinor = when {
                        itObj.has("unitPriceMinor") -> itObj.optLong("unitPriceMinor")
                        itObj.has("net") && itObj.optDouble("net", 0.0) > 0.0 -> {
                            val net = itObj.optDouble("net")
                            ((net / quantity) * 100.0).roundToLong()
                        }
                        itObj.has("total_after_discount") && itObj.optDouble("total_after_discount", 0.0) > 0.0 -> {
                            val net = itObj.optDouble("total_after_discount")
                            ((net / quantity) * 100.0).roundToLong()
                        }
                        itObj.has("unit_price") -> (itObj.optDouble("unit_price", 1.0) * 100.0).roundToLong()
                        itObj.has("unitPrice") -> (itObj.optDouble("unitPrice", 1.0) * 100.0).roundToLong()
                        itObj.has("price") -> (itObj.optDouble("price", 1.0) * 100.0).roundToLong()
                        itObj.has("total") && itObj.optDouble("total", 0.0) > 0.0 -> {
                            val total = itObj.optDouble("total")
                            ((total / quantity) * 100.0).roundToLong()
                        }
                        else -> 100L
                    }.coerceAtLeast(1L)

                    val isExplicitAsset = itObj.optBoolean("isAsset", false)
                    val descUpper = desc.uppercase()
                    val isProbableAsset = isExplicitAsset ||
                        descUpper.contains("راوتر") || descUpper.contains("سيرفر") || descUpper.contains("سيكتور") ||
                        descUpper.contains("أنتينا") || descUpper.contains("انتينة") || descUpper.contains("ROUTER") ||
                        descUpper.contains("SWITCH") || descUpper.contains("سويتش") || descUpper.contains("CCR") ||
                        descUpper.contains("AX1800") || descUpper.contains("BASEBOX") || descUpper.contains("MANTBOX") ||
                        descUpper.contains("SXT") || descUpper.contains("NANOSTATION") || descUpper.contains("POWERBEAM") ||
                        descUpper.contains("بطارية") || descUpper.contains("طاقة شمسية") || descUpper.contains("محول")

                    val accCode = when {
                        itObj.has("accountCode") -> itObj.optString("accountCode")
                        isProbableAsset -> "1501"
                        else -> "5101"
                    }

                    items.add(
                        PurchaseItemSpec(
                            description = desc,
                            accountCode = accCode,
                            quantity = quantity,
                            unitPriceMinor = unitPriceMinor,
                            isAsset = isProbableAsset,
                            usefulLifeMonths = if (isProbableAsset) 36 else null
                        )
                    )
                }
            } else {
                // Backward compatibility: If invoice was exported as a single flat total
                val amountDouble = if (obj.has("originalAmount") && obj.getDouble("originalAmount") > 0.0) {
                    obj.getDouble("originalAmount")
                } else if (obj.has("totalAmount")) {
                    obj.getDouble("totalAmount")
                } else if (obj.optJSONObject("summary")?.has("net_amount") == true) {
                    obj.getJSONObject("summary").getDouble("net_amount")
                } else if (obj.optJSONObject("summary")?.has("total_amount") == true) {
                    obj.getJSONObject("summary").getDouble("total_amount")
                } else {
                    100.0
                }
                val minorAmount = (amountDouble * 100.0).roundToLong().coerceAtLeast(100L)
                val targetType = obj.optString("targetType", "SERVICE").uppercase()
                val isFixedAsset = targetType.contains("ASSET") || targetType.contains("أصل")
                val accCode = if (isFixedAsset) "1501" else "5101"
                val summary = obj.optString("itemsSummary", "مشتريات/اشتراك شبكة")

                items.add(
                    PurchaseItemSpec(
                        description = summary,
                        accountCode = accCode,
                        quantity = 1,
                        unitPriceMinor = minorAmount,
                        isAsset = isFixedAsset,
                        usefulLifeMonths = if (isFixedAsset) 36 else null
                    )
                )
            }

            if (vendorName.isNotEmpty() && items.isNotEmpty()) {
                list.add(
                    ImportedPurchaseDraft(
                        vendorName = vendorName,
                        currencyCode = currency,
                        exchangeRateMicros = rateMicros,
                        items = items,
                        notes = notes,
                        isCash = isCashDetected
                    )
                )
            }
        }
        return list
    }

    // ==========================================
    // 4. Complete Backup Data JSON (Full Backward Compatibility)
    // ==========================================

    fun parseBackupDataFromJson(jsonString: String): AppBackupData {
        val root = JSONObject(jsonString)
        val schemaVer = root.optInt("schemaVersion", 1)
        val ts = root.optLong("timestamp", System.currentTimeMillis())
        val sha = root.optString("sha256", "")

        // 0. Organization
        val orgObj = root.optJSONObject("organization")
        val org = orgObj?.let {
            OrganizationEntity(
                id = it.optString("id", UUID.randomUUID().toString()),
                name = it.optString("name", "شبكة سام ميكروتك"),
                taxNumber = it.optString("taxNumber", ""),
                functionalCurrency = it.optString("functionalCurrency", "YER"),
                fiscalYearStartMonth = it.optInt("fiscalYearStartMonth", 1),
                isInitialized = it.optBoolean("isInitialized", true),
                primaryRateZone = it.optString("primaryRateZone", "SANAA"),
                equityShareMode = it.optString("equityShareMode", "DERIVED_FROM_CAPITAL"),
                createdAt = it.optLong("createdAt", System.currentTimeMillis())
            )
        }

        // 1. Parties
        val parties = mutableListOf<PartyEntity>()
        val partiesArr = root.optJSONArray("parties")
        if (partiesArr != null) {
            for (i in 0 until partiesArr.length()) {
                val p = partiesArr.getJSONObject(i)
                parties.add(
                    PartyEntity(
                        id = p.getString("id"),
                        name = p.getString("name"),
                        phone = p.optString("phone", ""),
                        isCustomer = p.optBoolean("isCustomer", false),
                        isVendor = p.optBoolean("isVendor", false),
                        isPartner = p.optBoolean("isPartner", false),
                        equityPercentageBasisPoints = p.optInt("equityPercentageBasisPoints", 0),
                        creditLimitMinor = p.optLong("creditLimitMinor", 0L),
                        isActive = p.optBoolean("isActive", true),
                        createdAt = p.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
        }

        // 2. Packages
        val pkgs = mutableListOf<CardPackageEntity>()
        val pkgsArr = root.optJSONArray("packages")
        if (pkgsArr != null) {
            for (i in 0 until pkgsArr.length()) {
                val pkg = pkgsArr.getJSONObject(i)
                pkgs.add(
                    CardPackageEntity(
                        id = pkg.getString("id"),
                        name = pkg.getString("name"),
                        durationOrQuota = pkg.optString("durationOrQuota", ""),
                        wholesalePriceMinor = pkg.optLong("wholesalePriceMinor", 0L),
                        retailPriceMinor = pkg.optLong("retailPriceMinor", 0L),
                        isActive = pkg.optBoolean("isActive", true)
                    )
                )
            }
        }

        // 3. Treasuries
        val treasuries = mutableListOf<TreasuryAccountEntity>()
        val treasuriesArr = root.optJSONArray("treasuries")
        if (treasuriesArr != null) {
            for (i in 0 until treasuriesArr.length()) {
                val tr = treasuriesArr.getJSONObject(i)
                treasuries.add(
                    TreasuryAccountEntity(
                        id = tr.getString("id"),
                        name = tr.getString("name"),
                        glAccountCode = tr.getString("glAccountCode"),
                        currency = tr.getString("currency"),
                        isActive = tr.optBoolean("isActive", true),
                        allowNegative = tr.optBoolean("allowNegative", false)
                    )
                )
            }
        }

        // 4. Documents
        val documents = mutableListOf<DocumentEntity>()
        val docsArr = root.optJSONArray("documents")
        if (docsArr != null) {
            for (i in 0 until docsArr.length()) {
                val d = docsArr.getJSONObject(i)
                documents.add(
                    DocumentEntity(
                        id = d.getString("id"),
                        type = d.getString("type"),
                        fiscalYear = d.getInt("fiscalYear"),
                        docNumber = d.getLong("docNumber"),
                        partyId = d.getString("partyId"),
                        dateEpochDay = d.getLong("dateEpochDay"),
                        currency = d.getString("currency"),
                        exchangeRateMicros = d.getLong("exchangeRateMicros"),
                        rateZone = d.optString("rateZone", "SANAA"),
                        rateSource = d.optString("rateSource", "SYSTEM_DAILY"),
                        totalMinor = d.getLong("totalMinor"),
                        totalBaseMinor = d.getLong("totalBaseMinor"),
                        status = d.getString("status"),
                        notes = d.optString("notes", ""),
                        reversalOfDocId = if (d.isNull("reversalOfDocId")) null else d.optString("reversalOfDocId"),
                        createdAt = d.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
        }

        // 5. Document Items
        val documentItems = mutableListOf<DocumentItemEntity>()
        val itemsArr = root.optJSONArray("document_items") ?: root.optJSONArray("documentItems")
        if (itemsArr != null) {
            for (i in 0 until itemsArr.length()) {
                val itm = itemsArr.getJSONObject(i)
                documentItems.add(
                    DocumentItemEntity(
                        id = itm.getString("id"),
                        docId = itm.getString("docId"),
                        itemIndex = itm.getInt("itemIndex"),
                        packageId = if (itm.isNull("packageId")) null else itm.optString("packageId"),
                        description = itm.getString("description"),
                        accountCode = itm.getString("accountCode"),
                        quantity = itm.getInt("quantity"),
                        unitPriceMinor = itm.getLong("unitPriceMinor"),
                        totalMinor = itm.getLong("totalMinor"),
                        isAsset = itm.optBoolean("isAsset", false)
                    )
                )
            }
        }

        // 6. Journal Entries
        val journalEntries = mutableListOf<JournalEntryEntity>()
        val entriesArr = root.optJSONArray("journal_entries") ?: root.optJSONArray("journalEntries")
        if (entriesArr != null) {
            for (i in 0 until entriesArr.length()) {
                val je = entriesArr.getJSONObject(i)
                journalEntries.add(
                    JournalEntryEntity(
                        id = je.getString("id"),
                        docId = je.getString("docId"),
                        entryNumber = je.getLong("entryNumber"),
                        entryDateEpochDay = je.getLong("entryDateEpochDay"),
                        type = je.getString("type"),
                        memo = je.optString("memo", ""),
                        createdAt = je.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
        }

        // 7. Journal Lines
        val journalLines = mutableListOf<JournalLineEntity>()
        val linesArr = root.optJSONArray("journal_lines") ?: root.optJSONArray("journalLines")
        if (linesArr != null) {
            for (i in 0 until linesArr.length()) {
                val jl = linesArr.getJSONObject(i)
                journalLines.add(
                    JournalLineEntity(
                        id = jl.getString("id"),
                        entryId = jl.getString("entryId"),
                        lineNo = jl.getInt("lineNo"),
                        accountCode = jl.getString("accountCode"),
                        partyId = if (jl.isNull("partyId")) null else jl.optString("partyId"),
                        treasuryId = if (jl.isNull("treasuryId")) null else jl.optString("treasuryId"),
                        origMinor = jl.getLong("origMinor"),
                        currency = jl.getString("currency"),
                        exchangeRateMicros = jl.getLong("exchangeRateMicros"),
                        baseDebitMinor = jl.getLong("baseDebitMinor"),
                        baseCreditMinor = jl.getLong("baseCreditMinor"),
                        memo = jl.optString("memo", "")
                    )
                )
            }
        }

        // 8. Allocations
        val allocations = mutableListOf<AllocationEntity>()
        val allocArr = root.optJSONArray("allocations")
        if (allocArr != null) {
            for (i in 0 until allocArr.length()) {
                val al = allocArr.getJSONObject(i)
                allocations.add(
                    AllocationEntity(
                        id = al.getString("id"),
                        paymentDocId = al.getString("paymentDocId"),
                        invoiceDocId = al.getString("invoiceDocId"),
                        allocatedOrigMinor = al.getLong("allocatedOrigMinor"),
                        allocatedBaseMinor = al.getLong("allocatedBaseMinor"),
                        exchangeGainLossMinor = al.optLong("exchangeGainLossMinor", 0L),
                        isVoided = al.optBoolean("isVoided", false),
                        createdAt = al.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
        }

        // 9. Assets
        val assets = mutableListOf<AssetEntity>()
        val assetsArr = root.optJSONArray("assets")
        if (assetsArr != null) {
            for (i in 0 until assetsArr.length()) {
                val ast = assetsArr.getJSONObject(i)
                assets.add(
                    AssetEntity(
                        id = ast.getString("id"),
                        docId = ast.getString("docId"),
                        name = ast.getString("name"),
                        purchaseDateEpochDay = ast.getLong("purchaseDateEpochDay"),
                        purchaseCostMinor = ast.getLong("purchaseCostMinor"),
                        salvageValueMinor = ast.optLong("salvageValueMinor", 0L),
                        usefulLifeMonths = ast.getInt("usefulLifeMonths"),
                        accumulatedDepreciationMinor = ast.optLong("accumulatedDepreciationMinor", 0L),
                        isDisposed = ast.optBoolean("isDisposed", false)
                    )
                )
            }
        }

        // 10. Depreciation Runs
        val depreciationRuns = mutableListOf<DepreciationRunEntity>()
        val depRunsArr = root.optJSONArray("depreciation_runs") ?: root.optJSONArray("depreciationRuns")
        if (depRunsArr != null) {
            for (i in 0 until depRunsArr.length()) {
                val dr = depRunsArr.getJSONObject(i)
                depreciationRuns.add(
                    DepreciationRunEntity(
                        id = dr.getString("id"),
                        periodYear = dr.getInt("periodYear"),
                        periodMonth = dr.getInt("periodMonth"),
                        assetId = dr.getString("assetId"),
                        journalEntryId = dr.getString("journalEntryId"),
                        depreciationAmountMinor = dr.getLong("depreciationAmountMinor")
                    )
                )
            }
        }

        // 11. Number Sequences
        val numberSequences = mutableListOf<NumberSequenceEntity>()
        val seqArr = root.optJSONArray("number_sequences") ?: root.optJSONArray("numberSequences")
        if (seqArr != null) {
            for (i in 0 until seqArr.length()) {
                val sq = seqArr.getJSONObject(i)
                numberSequences.add(
                    NumberSequenceEntity(
                        docType = sq.getString("docType"),
                        fiscalYear = sq.getInt("fiscalYear"),
                        nextValue = sq.getLong("nextValue")
                    )
                )
            }
        }

        // 12. Fiscal Periods
        val fiscalPeriods = mutableListOf<FiscalPeriodEntity>()
        val periodsArr = root.optJSONArray("fiscal_periods") ?: root.optJSONArray("fiscalPeriods")
        if (periodsArr != null) {
            for (i in 0 until periodsArr.length()) {
                val fp = periodsArr.getJSONObject(i)
                fiscalPeriods.add(
                    FiscalPeriodEntity(
                        id = fp.getString("id"),
                        year = fp.getInt("year"),
                        month = fp.getInt("month"),
                        isClosed = fp.optBoolean("isClosed", false),
                        closedAt = if (fp.isNull("closedAt")) null else fp.optLong("closedAt")
                    )
                )
            }
        }

        // 13. Currency Rates (سعر الصرف)
        val currencyRates = mutableListOf<CurrencyRateEntity>()
        val ratesArr = root.optJSONArray("currency_rates") ?: root.optJSONArray("currencyRates")
        if (ratesArr != null) {
            for (i in 0 until ratesArr.length()) {
                val cr = ratesArr.getJSONObject(i)
                currencyRates.add(
                    CurrencyRateEntity(
                        id = cr.getString("id"),
                        currency = cr.optString("currency", cr.optString("fromCurrency", "USD")),
                        zone = cr.optString("zone", "SANAA"),
                        rateMicros = cr.getLong("rateMicros"),
                        effectiveDateEpochDay = cr.getLong("effectiveDateEpochDay"),
                        createdAt = cr.optLong("createdAt", cr.optLong("updatedAt", System.currentTimeMillis())),
                        createdBy = cr.optString("createdBy", "SYSTEM"),
                        reason = cr.optString("reason", "")
                    )
                )
            }
        }

        // 14. Stock Movements
        val stockMovements = mutableListOf<StockMovementEntity>()
        val stockArr = root.optJSONArray("stock_movements") ?: root.optJSONArray("stockMovements")
        if (stockArr != null) {
            for (i in 0 until stockArr.length()) {
                val sm = stockArr.getJSONObject(i)
                stockMovements.add(
                    StockMovementEntity(
                        id = sm.getString("id"),
                        packageId = sm.getString("packageId"),
                        docId = if (sm.isNull("docId")) null else sm.optString("docId"),
                        type = sm.getString("type"),
                        quantity = sm.getInt("quantity"),
                        movementDateEpochDay = sm.getLong("movementDateEpochDay"),
                        createdAt = sm.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
        }

        // 15. Parse network devices with full backward compatibility
        val devicesArr = root.optJSONArray("network_devices") ?: root.optJSONArray("networkDevices")
        val devices = mutableListOf<DeviceEntity>()
        if (devicesArr != null) {
            for (i in 0 until devicesArr.length()) {
                val obj = devicesArr.getJSONObject(i)
                val typeStr = obj.optString("deviceType", DeviceType.ACCESS_POINT.name)
                val statusStr = obj.optString("status", DeviceStatus.ONLINE.name)
                devices.add(
                    NetworkDevice(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        name = obj.getString("name"),
                        ipAddress = obj.getString("ipAddress"),
                        deviceType = runCatching { DeviceType.valueOf(typeStr) }.getOrDefault(DeviceType.ACCESS_POINT),
                        macAddress = obj.optString("macAddress", ""),
                        towerLocation = obj.optString("towerLocation", "البرج الرئيسي"),
                        frequency = obj.optString("frequency", "5500 MHz"),
                        channelWidth = obj.optString("channelWidth", "20/40 MHz"),
                        status = runCatching { DeviceStatus.valueOf(statusStr) }.getOrDefault(DeviceStatus.ONLINE),
                        notes = obj.optString("notes", ""),
                        model = obj.optString("model", "MikroTik RouterBOARD"),
                        managementPort = obj.optInt("managementPort", 8728),
                        subnet = obj.optString("subnet", "10.10.1.0/24"),
                        credentials = obj.optString("credentials", "admin")
                    )
                )
            }
        }

        // 16. Subnets
        val subnets = mutableListOf<SubnetRange>()
        val subnetsArr = root.optJSONArray("network_subnets") ?: root.optJSONArray("networkSubnets")
        if (subnetsArr != null) {
            for (i in 0 until subnetsArr.length()) {
                val sObj = subnetsArr.getJSONObject(i)
                subnets.add(
                    SubnetRange(
                        id = sObj.optString("id", UUID.randomUUID().toString()),
                        name = sObj.getString("name"),
                        cidr = sObj.getString("cidr"),
                        gateway = sObj.getString("gateway"),
                        dhcpRangeStart = sObj.optString("dhcpRangeStart", ""),
                        dhcpRangeEnd = sObj.optString("dhcpRangeEnd", ""),
                        purpose = sObj.optString("purpose", "")
                    )
                )
            }
        }

        // 17. Parse network profile with full backward compatibility
        val profObj = root.optJSONObject("network_profile") ?: root.optJSONObject("networkProfile")
        val profile = profObj?.let {
            NetworkProfileBackupDto(
                networkName = it.optString("networkName", "شبكة توزيع الإنترنت"),
                ownerName = it.optString("ownerName", "مدير الشبكة"),
                location = it.optString("location", "المركز الرئيسي"),
                welcomeMessage = it.optString("welcomeMessage", "أهلاً بكم في شبكتنا - إنترنت فائق السرعة"),
                supportPhone = it.optString("supportPhone", "770000000"),
                supportWhatsapp = it.optString("supportWhatsapp", "967770000000"),
                mainRouterModel = it.optString("mainRouterModel", "MikroTik CCR2004-16G-2S+"),
                routerOsVersion = it.optString("routerOsVersion", "v7.16"),
                hotspotDomain = it.optString("hotspotDomain", "login.net"),
                hotspotServerName = it.optString("hotspotServerName", "hotspot1"),
                adminPort = it.optInt("adminPort", 8728),
                primaryDns = it.optString("primaryDns", "8.8.8.8"),
                secondaryDns = it.optString("secondaryDns", "1.1.1.1"),
                approvedSubnet = it.optString("approvedSubnet", "10.10.0.0/16"),
                rateZone = it.optString("rateZone", "SANAA"),
                defaultUsdRateMicros = it.optLong("defaultUsdRateMicros", 535_000_000L),
                defaultSarRateMicros = it.optLong("defaultSarRateMicros", 140_500_000L),
                updatedAt = it.optLong("updatedAt", System.currentTimeMillis())
            )
        }

        return AppBackupData(
            schemaVersion = schemaVer,
            timestamp = ts,
            organization = org,
            parties = parties,
            packages = pkgs,
            treasuries = treasuries,
            documents = documents,
            documentItems = documentItems,
            journalEntries = journalEntries,
            journalLines = journalLines,
            allocations = allocations,
            assets = assets,
            depreciationRuns = depreciationRuns,
            numberSequences = numberSequences,
            fiscalPeriods = fiscalPeriods,
            currencyRates = currencyRates,
            stockMovements = stockMovements,
            networkDevices = devices,
            networkSubnets = subnets,
            networkProfile = profile,
            sha256 = sha
        )
    }

    fun exportBackupDataToJson(data: AppBackupData): String {
        val root = JSONObject()
        root.put("schemaVersion", data.schemaVersion)
        root.put("timestamp", data.timestamp)

        // 0. Organization
        data.organization?.let { org ->
            val orgObj = JSONObject()
            orgObj.put("id", org.id)
            orgObj.put("name", org.name)
            orgObj.put("taxNumber", org.taxNumber)
            orgObj.put("functionalCurrency", org.functionalCurrency)
            orgObj.put("fiscalYearStartMonth", org.fiscalYearStartMonth)
            orgObj.put("isInitialized", org.isInitialized)
            orgObj.put("primaryRateZone", org.primaryRateZone)
            orgObj.put("equityShareMode", org.equityShareMode)
            orgObj.put("createdAt", org.createdAt)
            root.put("organization", orgObj)
        }

        // 1. Parties
        val partiesArr = JSONArray()
        data.parties.forEach { p ->
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

        // 2. Packages
        val pkgsArr = JSONArray()
        data.packages.forEach { pkg ->
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
        data.treasuries.forEach { tr ->
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
        data.documents.forEach { d ->
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
        data.documentItems.forEach { itm ->
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
        data.journalEntries.forEach { je ->
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
        data.journalLines.forEach { jl ->
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
        data.allocations.forEach { al ->
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
        data.assets.forEach { ast ->
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
        data.depreciationRuns.forEach { dr ->
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
        data.numberSequences.forEach { sq ->
            val obj = JSONObject()
            obj.put("docType", sq.docType)
            obj.put("fiscalYear", sq.fiscalYear)
            obj.put("nextValue", sq.nextValue)
            seqArr.put(obj)
        }
        root.put("number_sequences", seqArr)

        // 12. Fiscal Periods
        val periodsArr = JSONArray()
        data.fiscalPeriods.forEach { fp ->
            val obj = JSONObject()
            obj.put("id", fp.id)
            obj.put("year", fp.year)
            obj.put("month", fp.month)
            obj.put("isClosed", fp.isClosed)
            obj.put("closedAt", fp.closedAt)
            periodsArr.put(obj)
        }
        root.put("fiscal_periods", periodsArr)

        // 13. Currency Rates (سعر الصرف)
        val ratesArr = JSONArray()
        data.currencyRates.forEach { cr ->
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
        data.stockMovements.forEach { sm ->
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

        // 15. Devices
        val devArr = JSONArray()
        data.networkDevices.forEach { d ->
            val obj = JSONObject().apply {
                put("id", d.id)
                put("name", d.name)
                put("ipAddress", d.ipAddress)
                put("deviceType", d.deviceType.name)
                put("macAddress", d.macAddress)
                put("towerLocation", d.towerLocation)
                put("frequency", d.frequency)
                put("channelWidth", d.channelWidth)
                put("status", d.status.name)
                put("notes", d.notes)
                put("model", d.model)
                put("managementPort", d.managementPort)
                put("subnet", d.subnet)
                put("credentials", d.credentials)
            }
            devArr.put(obj)
        }
        root.put("network_devices", devArr)
        root.put("networkDevices", devArr)

        // 16. Profile
        data.networkProfile?.let { p ->
            val obj = JSONObject().apply {
                put("networkName", p.networkName)
                put("ownerName", p.ownerName)
                put("location", p.location)
                put("welcomeMessage", p.welcomeMessage)
                put("supportPhone", p.supportPhone)
                put("supportWhatsapp", p.supportWhatsapp)
                put("mainRouterModel", p.mainRouterModel)
                put("routerOsVersion", p.routerOsVersion)
                put("hotspotDomain", p.hotspotDomain)
                put("hotspotServerName", p.hotspotServerName)
                put("adminPort", p.adminPort)
                put("primaryDns", p.primaryDns)
                put("secondaryDns", p.secondaryDns)
                put("approvedSubnet", p.approvedSubnet)
                put("rateZone", p.rateZone)
                put("defaultUsdRateMicros", p.defaultUsdRateMicros)
                put("defaultSarRateMicros", p.defaultSarRateMicros)
                put("updatedAt", p.updatedAt)
            }
            root.put("network_profile", obj)
            root.put("networkProfile", obj)
        }

        // 17. Subnets
        val subnetsArr = JSONArray()
        data.networkSubnets.forEach { s ->
            val obj = JSONObject().apply {
                put("id", s.id)
                put("name", s.name)
                put("cidr", s.cidr)
                put("gateway", s.gateway)
                put("dhcpRangeStart", s.dhcpRangeStart)
                put("dhcpRangeEnd", s.dhcpRangeEnd)
                put("purpose", s.purpose)
            }
            subnetsArr.put(obj)
        }
        root.put("network_subnets", subnetsArr)
        root.put("networkSubnets", subnetsArr)

        if (data.sha256.isNotBlank()) {
            root.put("sha256", data.sha256)
        }
        return root.toString(2)
    }
}
