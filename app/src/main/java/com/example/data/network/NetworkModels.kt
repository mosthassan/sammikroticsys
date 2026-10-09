package com.example.data.network

import android.content.Context
import com.example.core.model.RateZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class NetworkConfig(
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
    val rateZone: RateZone = RateZone.SANAA,
    val defaultUsdRateMicros: Long = 535_000_000L,
    val defaultSarRateMicros: Long = 140_500_000L,
    val updatedAt: Long = System.currentTimeMillis()
) {
    val defaultUsdRate: Double get() = defaultUsdRateMicros.toDouble() / 1_000_000.0
    val defaultSarRate: Double get() = defaultSarRateMicros.toDouble() / 1_000_000.0
}

typealias NetworkProfile = NetworkConfig

enum class DeviceType(val labelArabic: String) {
    ACCESS_POINT("سيكتور بث AP"),
    STATION("محطة استقبال Station"),
    SWITCH("سويتش شبكة Switch"),
    ROUTER("راوتر ميكروتك Router"),
    ANTENNA("هوائي توجيه Antenna")
}

enum class DeviceStatus(val labelArabic: String) {
    ONLINE("متصل أونلاين"),
    WARNING("تحذير / إشارة ضعيفة"),
    OFFLINE("غير متصل")
}

data class NetworkDevice(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val ipAddress: String,
    val deviceType: DeviceType = DeviceType.ACCESS_POINT,
    val macAddress: String = "",
    val towerLocation: String = "البرج الرئيسي",
    val frequency: String = "5500 MHz",
    val channelWidth: String = "20/40 MHz",
    val status: DeviceStatus = DeviceStatus.ONLINE,
    val notes: String = "",
    val model: String = "MikroTik RouterBOARD",
    val managementPort: Int = 8728,
    val subnet: String = "10.10.1.0/24",
    val credentials: String = "admin"
) {
    val location: String get() = towerLocation

    fun clone(customSuffix: String = " - نسخة"): NetworkDevice {
        return this.copy(
            id = UUID.randomUUID().toString(),
            name = "$name$customSuffix",
            ipAddress = "",
            macAddress = ""
        )
    }
}

data class SubnetRange(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val cidr: String,
    val gateway: String,
    val dhcpRangeStart: String,
    val dhcpRangeEnd: String,
    val purpose: String
)

typealias DeviceEntity = NetworkDevice

sealed interface DeviceSaveResult {
    data class Success(val device: NetworkDevice) : DeviceSaveResult
    data class IpConflict(val conflictingDevice: NetworkDevice) : DeviceSaveResult
}

class NetworkRepository(private val context: Context) : com.example.data.local.dao.DeviceDao {

    private val configFile = File(context.filesDir, "network_config.json")
    private val devicesFile = File(context.filesDir, "network_devices.json")
    private val subnetsFile = File(context.filesDir, "network_subnets.json")
    private val networkPrefs = context.getSharedPreferences("sammikrotik_network_prefs", Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(loadConfig())
    val config: StateFlow<NetworkConfig> = _config.asStateFlow()

    private val _devices = MutableStateFlow(loadDevices())
    val devices: StateFlow<List<NetworkDevice>> = _devices.asStateFlow()

    private val _subnets = MutableStateFlow(loadSubnets())
    val subnets: StateFlow<List<SubnetRange>> = _subnets.asStateFlow()

    private fun loadConfig(): NetworkConfig {
        return try {
            if (configFile.exists()) {
                val json = JSONObject(configFile.readText())
                val zoneStr = json.optString("rateZone", RateZone.SANAA.name)
                val zone = runCatching { RateZone.valueOf(zoneStr) }.getOrDefault(RateZone.SANAA)
                val usdMicros = json.optLong("defaultUsdRateMicros", 535_000_000L)
                val sarMicros = json.optLong("defaultSarRateMicros", 140_500_000L)
                val updatedAt = json.optLong("updatedAt", System.currentTimeMillis())
                NetworkConfig(
                    networkName = json.optString("networkName", "شبكة توزيع الإنترنت"),
                    ownerName = json.optString("ownerName", "مدير الشبكة"),
                    location = json.optString("location", "المركز الرئيسي"),
                    welcomeMessage = json.optString("welcomeMessage", "أهلاً بكم في شبكتنا - إنترنت فائق السرعة"),
                    supportPhone = json.optString("supportPhone", "770000000"),
                    supportWhatsapp = json.optString("supportWhatsapp", "967770000000"),
                    mainRouterModel = json.optString("mainRouterModel", "MikroTik CCR2004-16G-2S+"),
                    routerOsVersion = json.optString("routerOsVersion", "v7.16"),
                    hotspotDomain = json.optString("hotspotDomain", "login.net"),
                    hotspotServerName = json.optString("hotspotServerName", "hotspot1"),
                    adminPort = json.optInt("adminPort", 8728),
                    primaryDns = json.optString("primaryDns", "8.8.8.8"),
                    secondaryDns = json.optString("secondaryDns", "1.1.1.1"),
                    approvedSubnet = json.optString("approvedSubnet", "10.10.0.0/16"),
                    rateZone = zone,
                    defaultUsdRateMicros = usdMicros,
                    defaultSarRateMicros = sarMicros,
                    updatedAt = updatedAt
                )
            } else if (networkPrefs.contains("network_name") || networkPrefs.contains("networkName")) {
                val name = networkPrefs.getString("network_name", null) ?: networkPrefs.getString("networkName", "شبكة توزيع الإنترنت") ?: "شبكة توزيع الإنترنت"
                val zoneStr = networkPrefs.getString("rate_zone", null) ?: networkPrefs.getString("rateZone", RateZone.SANAA.name) ?: RateZone.SANAA.name
                val zone = runCatching { RateZone.valueOf(zoneStr) }.getOrDefault(RateZone.SANAA)
                NetworkConfig(
                    networkName = name,
                    ownerName = networkPrefs.getString("owner_name", null) ?: networkPrefs.getString("ownerName", "مدير الشبكة") ?: "مدير الشبكة",
                    location = networkPrefs.getString("location", "المركز الرئيسي") ?: "المركز الرئيسي",
                    welcomeMessage = networkPrefs.getString("welcome_message", null) ?: networkPrefs.getString("welcomeMessage", "أهلاً بكم في شبكتنا - إنترنت فائق السرعة") ?: "أهلاً بكم في شبكتنا - إنترنت فائق السرعة",
                    supportPhone = networkPrefs.getString("support_phone", null) ?: networkPrefs.getString("supportPhone", "770000000") ?: "770000000",
                    supportWhatsapp = networkPrefs.getString("support_whatsapp", null) ?: networkPrefs.getString("supportWhatsapp", "967770000000") ?: "967770000000",
                    mainRouterModel = networkPrefs.getString("main_router_model", null) ?: networkPrefs.getString("mainRouterModel", "MikroTik CCR2004-16G-2S+") ?: "MikroTik CCR2004-16G-2S+",
                    routerOsVersion = networkPrefs.getString("router_os_version", null) ?: networkPrefs.getString("routerOsVersion", "v7.16") ?: "v7.16",
                    hotspotDomain = networkPrefs.getString("hotspot_domain", null) ?: networkPrefs.getString("hotspotDomain", "login.net") ?: "login.net",
                    hotspotServerName = networkPrefs.getString("hotspot_server_name", null) ?: networkPrefs.getString("hotspotServerName", "hotspot1") ?: "hotspot1",
                    adminPort = networkPrefs.getInt("admin_port", networkPrefs.getInt("adminPort", 8728)),
                    primaryDns = networkPrefs.getString("primary_dns", null) ?: networkPrefs.getString("primaryDns", "8.8.8.8") ?: "8.8.8.8",
                    secondaryDns = networkPrefs.getString("secondary_dns", null) ?: networkPrefs.getString("secondaryDns", "1.1.1.1") ?: "1.1.1.1",
                    approvedSubnet = networkPrefs.getString("approved_subnet", null) ?: networkPrefs.getString("approvedSubnet", "10.10.0.0/16") ?: "10.10.0.0/16",
                    rateZone = zone,
                    defaultUsdRateMicros = networkPrefs.getLong("default_usd_rate_micros", networkPrefs.getLong("defaultUsdRateMicros", 535_000_000L)),
                    defaultSarRateMicros = networkPrefs.getLong("default_sar_rate_micros", networkPrefs.getLong("defaultSarRateMicros", 140_500_000L)),
                    updatedAt = networkPrefs.getLong("updated_at", networkPrefs.getLong("updatedAt", System.currentTimeMillis()))
                )
            } else {
                NetworkConfig()
            }
        } catch (_: Exception) {
            NetworkConfig()
        }
    }

    suspend fun saveConfig(newConfig: NetworkConfig) = withContext(Dispatchers.IO) {
        val json = JSONObject().apply {
            put("networkName", newConfig.networkName)
            put("ownerName", newConfig.ownerName)
            put("location", newConfig.location)
            put("welcomeMessage", newConfig.welcomeMessage)
            put("supportPhone", newConfig.supportPhone)
            put("supportWhatsapp", newConfig.supportWhatsapp)
            put("mainRouterModel", newConfig.mainRouterModel)
            put("routerOsVersion", newConfig.routerOsVersion)
            put("hotspotDomain", newConfig.hotspotDomain)
            put("hotspotServerName", newConfig.hotspotServerName)
            put("adminPort", newConfig.adminPort)
            put("primaryDns", newConfig.primaryDns)
            put("secondaryDns", newConfig.secondaryDns)
            put("approvedSubnet", newConfig.approvedSubnet)
            put("rateZone", newConfig.rateZone.name)
            put("defaultUsdRateMicros", newConfig.defaultUsdRateMicros)
            put("defaultSarRateMicros", newConfig.defaultSarRateMicros)
            put("updatedAt", newConfig.updatedAt)
        }
        configFile.writeText(json.toString(2))

        networkPrefs.edit()
            .putString("network_name", newConfig.networkName)
            .putString("networkName", newConfig.networkName)
            .putString("owner_name", newConfig.ownerName)
            .putString("ownerName", newConfig.ownerName)
            .putString("location", newConfig.location)
            .putString("welcome_message", newConfig.welcomeMessage)
            .putString("welcomeMessage", newConfig.welcomeMessage)
            .putString("support_phone", newConfig.supportPhone)
            .putString("supportPhone", newConfig.supportPhone)
            .putString("support_whatsapp", newConfig.supportWhatsapp)
            .putString("supportWhatsapp", newConfig.supportWhatsapp)
            .putString("main_router_model", newConfig.mainRouterModel)
            .putString("mainRouterModel", newConfig.mainRouterModel)
            .putString("router_os_version", newConfig.routerOsVersion)
            .putString("routerOsVersion", newConfig.routerOsVersion)
            .putString("hotspot_domain", newConfig.hotspotDomain)
            .putString("hotspotDomain", newConfig.hotspotDomain)
            .putString("hotspot_server_name", newConfig.hotspotServerName)
            .putString("hotspotServerName", newConfig.hotspotServerName)
            .putInt("admin_port", newConfig.adminPort)
            .putInt("adminPort", newConfig.adminPort)
            .putString("primary_dns", newConfig.primaryDns)
            .putString("primaryDns", newConfig.primaryDns)
            .putString("secondary_dns", newConfig.secondaryDns)
            .putString("secondaryDns", newConfig.secondaryDns)
            .putString("approved_subnet", newConfig.approvedSubnet)
            .putString("approvedSubnet", newConfig.approvedSubnet)
            .putString("rate_zone", newConfig.rateZone.name)
            .putString("rateZone", newConfig.rateZone.name)
            .putLong("default_usd_rate_micros", newConfig.defaultUsdRateMicros)
            .putLong("defaultUsdRateMicros", newConfig.defaultUsdRateMicros)
            .putLong("default_sar_rate_micros", newConfig.defaultSarRateMicros)
            .putLong("defaultSarRateMicros", newConfig.defaultSarRateMicros)
            .putLong("updated_at", newConfig.updatedAt)
            .putLong("updatedAt", newConfig.updatedAt)
            .apply()

        _config.value = newConfig
    }

    suspend fun restoreNetworkProfile(profile: com.example.util.NetworkProfileBackupDto) = withContext(Dispatchers.IO) {
        val newConfig = profile.toNetworkConfig()
        saveConfig(newConfig)
    }

    private fun loadDevices(): List<NetworkDevice> {
        return try {
            if (devicesFile.exists()) {
                val array = JSONArray(devicesFile.readText())
                val list = mutableListOf<NetworkDevice>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        NetworkDevice(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            name = obj.getString("name"),
                            ipAddress = obj.getString("ipAddress"),
                            deviceType = runCatching { DeviceType.valueOf(obj.getString("deviceType")) }.getOrDefault(DeviceType.ACCESS_POINT),
                            macAddress = obj.optString("macAddress", ""),
                            towerLocation = obj.optString("towerLocation", "البرج الرئيسي"),
                            frequency = obj.optString("frequency", "5500 MHz"),
                            channelWidth = obj.optString("channelWidth", "20/40 MHz"),
                            status = runCatching { DeviceStatus.valueOf(obj.getString("status")) }.getOrDefault(DeviceStatus.ONLINE),
                            notes = obj.optString("notes", ""),
                            model = obj.optString("model", "MikroTik RouterBOARD"),
                            managementPort = obj.optInt("managementPort", 8728),
                            subnet = obj.optString("subnet", "10.10.1.0/24"),
                            credentials = obj.optString("credentials", "admin")
                        )
                    )
                }
                list
            } else {
                seedInitialDevices()
            }
        } catch (_: Exception) {
            seedInitialDevices()
        }
    }

    private fun seedInitialDevices(): List<NetworkDevice> {
        val initial = listOf(
            NetworkDevice(
                name = "راوتر السيرفر الرئيسي CCR2004",
                ipAddress = "10.10.0.1",
                deviceType = DeviceType.ROUTER,
                macAddress = "D4:01:C3:A1:B2:C3",
                towerLocation = "غرفة السيرفرات المركزية",
                frequency = "Core Fiber",
                status = DeviceStatus.ONLINE,
                notes = "سيرفر التوجيه وتوزيع الهوتسبوت الأساسي"
            ),
            NetworkDevice(
                name = "سيكتور شمالي BaseBox 5 (برج السبعين)",
                ipAddress = "10.10.1.11",
                deviceType = DeviceType.ACCESS_POINT,
                macAddress = "6C:3B:6B:44:55:66",
                towerLocation = "برج السبعين - الدور 12",
                frequency = "5500 MHz (Ch 100)",
                status = DeviceStatus.ONLINE,
                notes = "يغطي مربع بقالات الحي الشمالي"
            ),
            NetworkDevice(
                name = "سيكتور جنوبي mANTBox 19s",
                ipAddress = "10.10.1.12",
                deviceType = DeviceType.ACCESS_POINT,
                macAddress = "6C:3B:6B:77:88:99",
                towerLocation = "برج السبعين - الدور 12",
                frequency = "5700 MHz (Ch 140)",
                status = DeviceStatus.ONLINE,
                notes = "يغطي السوق ومجمع الوكلاء"
            ),
            NetworkDevice(
                name = "ستيشن ربط فرعي بقالة الأمل (SXTsq 5)",
                ipAddress = "10.10.2.50",
                deviceType = DeviceType.STATION,
                macAddress = "48:8F:5A:11:22:33",
                towerLocation = "مبنى بقالة الأمل",
                frequency = "5500 MHz",
                status = DeviceStatus.ONLINE,
                notes = "نقطة توزيع وكيل معتمد"
            )
        )
        saveDevicesInternal(initial)
        return initial
    }

    override suspend fun findDeviceByIp(ip: String, currentDeviceId: Long): NetworkDevice? {
        val cleanIp = ip.trim()
        if (cleanIp.isBlank()) return null
        return _devices.value.firstOrNull {
            it.id.hashCode().toLong() != currentDeviceId && it.ipAddress.trim().equals(cleanIp, ignoreCase = true)
        }
    }

    override suspend fun findDeviceByIp(ip: String, currentDeviceId: String): NetworkDevice? {
        return findDeviceByIp(ip, excludeDeviceId = currentDeviceId)
    }

    override suspend fun getAllDevices(): List<NetworkDevice> {
        return _devices.value
    }

    override suspend fun insertDevice(device: NetworkDevice) {
        addOrUpdateDevice(device)
    }

    override suspend fun updateDevice(device: NetworkDevice) {
        addOrUpdateDevice(device)
    }

    fun findDeviceByIp(ip: String, excludeDeviceId: String? = null): NetworkDevice? {
        val cleanIp = ip.trim()
        if (cleanIp.isBlank()) return null
        return _devices.value.firstOrNull {
            it.id != excludeDeviceId && it.ipAddress.trim().equals(cleanIp, ignoreCase = true)
        }
    }

    suspend fun addOrUpdateDevice(device: NetworkDevice): DeviceSaveResult = withContext(Dispatchers.IO) {
        val conflict = findDeviceByIp(device.ipAddress, device.id)
        if (conflict != null) {
            return@withContext DeviceSaveResult.IpConflict(conflict)
        }

        val current = _devices.value.toMutableList()
        val index = current.indexOfFirst { it.id == device.id }
        if (index >= 0) {
            current[index] = device
        } else {
            current.add(0, device)
        }
        saveDevicesInternal(current)
        _devices.value = current
        DeviceSaveResult.Success(device)
    }

    override suspend fun deleteDevice(deviceId: String) = withContext(Dispatchers.IO) {
        val current = _devices.value.filter { it.id != deviceId }
        saveDevicesInternal(current)
        _devices.value = current
    }

    override suspend fun insertAll(devices: List<NetworkDevice>) = withContext(Dispatchers.IO) {
        if (_devices.value.isEmpty()) {
            saveDevicesInternal(devices)
            _devices.value = devices
        } else {
            val currentMap = _devices.value.associateBy { it.id }.toMutableMap()
            devices.forEach { currentMap[it.id] = it }
            val list = currentMap.values.toList()
            saveDevicesInternal(list)
            _devices.value = list
        }
    }

    override suspend fun deleteAllDevices() = withContext(Dispatchers.IO) {
        saveDevicesInternal(emptyList())
        _devices.value = emptyList()
    }

    suspend fun overwriteAllDevices(devices: List<NetworkDevice>) = withContext(Dispatchers.IO) {
        saveDevicesInternal(devices)
        _devices.value = devices
    }

    suspend fun importDevicesFromJson(jsonString: String, replaceExisting: Boolean = false): Int = withContext(Dispatchers.IO) {
        val result = com.example.util.DataJsonHelper.parseDevicesFromJson(jsonString)
        val imported = result.devices

        if (!result.networkName.isNullOrBlank() && _config.value.networkName == "شبكة سام ميكروتك الذكية") {
            saveConfig(_config.value.copy(networkName = result.networkName))
        }

        val updated = if (replaceExisting) {
            imported
        } else {
            val existingIps = _devices.value.map { it.ipAddress.trim() }.toSet()
            val filtered = imported.filter { it.ipAddress.trim() !in existingIps }
            filtered + _devices.value
        }
        saveDevicesInternal(updated)
        _devices.value = updated
        imported.size
    }

    fun exportDevicesToJson(): String {
        return com.example.util.DataJsonHelper.exportDevicesToJson(_devices.value, _config.value.networkName)
    }

    private fun saveDevicesInternal(devicesList: List<NetworkDevice>) {
        val array = JSONArray()
        for (d in devicesList) {
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
            array.put(obj)
        }
        devicesFile.writeText(array.toString(2))
    }

    private fun loadSubnets(): List<SubnetRange> {
        return try {
            if (subnetsFile.exists()) {
                val array = JSONArray(subnetsFile.readText())
                val list = mutableListOf<SubnetRange>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        SubnetRange(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            name = obj.getString("name"),
                            cidr = obj.getString("cidr"),
                            gateway = obj.getString("gateway"),
                            dhcpRangeStart = obj.getString("dhcpRangeStart"),
                            dhcpRangeEnd = obj.getString("dhcpRangeEnd"),
                            purpose = obj.getString("purpose")
                        )
                    )
                }
                list
            } else {
                seedInitialSubnets()
            }
        } catch (_: Exception) {
            seedInitialSubnets()
        }
    }

    private fun seedInitialSubnets(): List<SubnetRange> {
        val initial = listOf(
            SubnetRange(
                name = "رينج إدارة أجهزة البث والأبراج",
                cidr = "10.10.0.0/16",
                gateway = "10.10.0.1",
                dhcpRangeStart = "10.10.1.1",
                dhcpRangeEnd = "10.10.254.254",
                purpose = "Management / APs / Stations"
            ),
            SubnetRange(
                name = "رينج خادم الهوتسبوت والمشتركين",
                cidr = "10.20.0.0/16",
                gateway = "10.20.0.1",
                dhcpRangeStart = "10.20.2.1",
                dhcpRangeEnd = "10.20.254.254",
                purpose = "Hotspot Users / Captive Portal"
            ),
            SubnetRange(
                name = "رينج خطوط الروابط اللاسلكية PTP",
                cidr = "172.16.10.0/24",
                gateway = "172.16.10.1",
                dhcpRangeStart = "172.16.10.2",
                dhcpRangeEnd = "172.16.10.30",
                purpose = "PTP Backhauls / Fiber Gateways"
            )
        )
        saveSubnetsInternal(initial)
        return initial
    }

    suspend fun addOrUpdateSubnet(subnet: SubnetRange) = withContext(Dispatchers.IO) {
        val current = _subnets.value.toMutableList()
        val index = current.indexOfFirst { it.id == subnet.id }
        if (index >= 0) {
            current[index] = subnet
        } else {
            current.add(subnet)
        }
        saveSubnetsInternal(current)
        _subnets.value = current
    }

    suspend fun deleteSubnet(subnetId: String) = withContext(Dispatchers.IO) {
        val current = _subnets.value.filter { it.id != subnetId }
        saveSubnetsInternal(current)
        _subnets.value = current
    }

    private fun saveSubnetsInternal(subnetsList: List<SubnetRange>) {
        val array = JSONArray()
        for (s in subnetsList) {
            val obj = JSONObject().apply {
                put("id", s.id)
                put("name", s.name)
                put("cidr", s.cidr)
                put("gateway", s.gateway)
                put("dhcpRangeStart", s.dhcpRangeStart)
                put("dhcpRangeEnd", s.dhcpRangeEnd)
                put("purpose", s.purpose)
            }
            array.put(obj)
        }
        subnetsFile.writeText(array.toString(2))
    }
}

fun com.example.util.NetworkProfileBackupDto.toNetworkConfig(): NetworkConfig {
    val zone = runCatching { RateZone.valueOf(rateZone) }.getOrDefault(RateZone.SANAA)
    return NetworkConfig(
        networkName = networkName,
        ownerName = ownerName,
        location = location,
        welcomeMessage = welcomeMessage,
        supportPhone = supportPhone,
        supportWhatsapp = supportWhatsapp,
        mainRouterModel = mainRouterModel,
        routerOsVersion = routerOsVersion,
        hotspotDomain = hotspotDomain,
        hotspotServerName = hotspotServerName,
        adminPort = adminPort,
        primaryDns = primaryDns,
        secondaryDns = secondaryDns,
        approvedSubnet = approvedSubnet,
        rateZone = zone,
        defaultUsdRateMicros = defaultUsdRateMicros,
        defaultSarRateMicros = defaultSarRateMicros,
        updatedAt = updatedAt
    )
}

fun NetworkConfig.toBackupDto(): com.example.util.NetworkProfileBackupDto {
    return com.example.util.NetworkProfileBackupDto(
        networkName = networkName,
        ownerName = ownerName,
        location = location,
        welcomeMessage = welcomeMessage,
        supportPhone = supportPhone,
        supportWhatsapp = supportWhatsapp,
        mainRouterModel = mainRouterModel,
        routerOsVersion = routerOsVersion,
        hotspotDomain = hotspotDomain,
        hotspotServerName = hotspotServerName,
        adminPort = adminPort,
        primaryDns = primaryDns,
        secondaryDns = secondaryDns,
        approvedSubnet = approvedSubnet,
        rateZone = rateZone.name,
        defaultUsdRateMicros = defaultUsdRateMicros,
        defaultSarRateMicros = defaultSarRateMicros,
        updatedAt = updatedAt
    )
}
