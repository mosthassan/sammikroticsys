package com.example.data.network

import android.content.Context
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
    val approvedSubnet: String = "10.10.0.0/16"
)

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
    val notes: String = ""
)

data class SubnetRange(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val cidr: String,
    val gateway: String,
    val dhcpRangeStart: String,
    val dhcpRangeEnd: String,
    val purpose: String
)

sealed interface DeviceSaveResult {
    data class Success(val device: NetworkDevice) : DeviceSaveResult
    data class IpConflict(val conflictingDevice: NetworkDevice) : DeviceSaveResult
}

class NetworkRepository(private val context: Context) {

    private val configFile = File(context.filesDir, "network_config.json")
    private val devicesFile = File(context.filesDir, "network_devices.json")
    private val subnetsFile = File(context.filesDir, "network_subnets.json")

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
                    approvedSubnet = json.optString("approvedSubnet", "10.10.0.0/16")
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
        }
        configFile.writeText(json.toString(2))
        _config.value = newConfig
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
                            notes = obj.optString("notes", "")
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

    suspend fun deleteDevice(deviceId: String) = withContext(Dispatchers.IO) {
        val current = _devices.value.filter { it.id != deviceId }
        saveDevicesInternal(current)
        _devices.value = current
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

    fun exportAllNetworkDataToJson(): JSONObject {
        val root = JSONObject()

        // 1. Network Identity & Config
        val conf = _config.value
        val confObj = JSONObject().apply {
            put("networkName", conf.networkName)
            put("ownerName", conf.ownerName)
            put("location", conf.location)
            put("welcomeMessage", conf.welcomeMessage)
            put("supportPhone", conf.supportPhone)
            put("supportWhatsapp", conf.supportWhatsapp)
            put("mainRouterModel", conf.mainRouterModel)
            put("routerOsVersion", conf.routerOsVersion)
            put("hotspotDomain", conf.hotspotDomain)
            put("hotspotServerName", conf.hotspotServerName)
            put("adminPort", conf.adminPort)
            put("primaryDns", conf.primaryDns)
            put("secondaryDns", conf.secondaryDns)
            put("approvedSubnet", conf.approvedSubnet)
        }
        root.put("config", confObj)

        // 2. Network Devices (AP, Router, Station, Switch, Antenna)
        val devArr = JSONArray()
        for (d in _devices.value) {
            val devObj = JSONObject().apply {
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
            }
            devArr.put(devObj)
        }
        root.put("devices", devArr)

        // 3. Subnets & IP ranges
        val subArr = JSONArray()
        for (s in _subnets.value) {
            val subObj = JSONObject().apply {
                put("id", s.id)
                put("name", s.name)
                put("cidr", s.cidr)
                put("gateway", s.gateway)
                put("dhcpRangeStart", s.dhcpRangeStart)
                put("dhcpRangeEnd", s.dhcpRangeEnd)
                put("purpose", s.purpose)
            }
            subArr.put(subObj)
        }
        root.put("subnets", subArr)

        return root
    }

    suspend fun restoreAllNetworkDataFromJson(root: JSONObject) = withContext(Dispatchers.IO) {
        if (root.has("config")) {
            val c = root.getJSONObject("config")
            val restoredConfig = NetworkConfig(
                networkName = c.optString("networkName", "شبكة توزيع الإنترنت"),
                ownerName = c.optString("ownerName", "مدير الشبكة"),
                location = c.optString("location", "المركز الرئيسي"),
                welcomeMessage = c.optString("welcomeMessage", "أهلاً بكم في شبكتنا - إنترنت فائق السرعة"),
                supportPhone = c.optString("supportPhone", "770000000"),
                supportWhatsapp = c.optString("supportWhatsapp", "967770000000"),
                mainRouterModel = c.optString("mainRouterModel", "MikroTik CCR2004-16G-2S+"),
                routerOsVersion = c.optString("routerOsVersion", "v7.16"),
                hotspotDomain = c.optString("hotspotDomain", "login.net"),
                hotspotServerName = c.optString("hotspotServerName", "hotspot1"),
                adminPort = c.optInt("adminPort", 8728),
                primaryDns = c.optString("primaryDns", "8.8.8.8"),
                secondaryDns = c.optString("secondaryDns", "1.1.1.1"),
                approvedSubnet = c.optString("approvedSubnet", "10.10.0.0/16")
            )
            saveConfig(restoredConfig)
        }

        if (root.has("devices")) {
            val devArr = root.getJSONArray("devices")
            val restoredDevices = mutableListOf<NetworkDevice>()
            for (i in 0 until devArr.length()) {
                val d = devArr.getJSONObject(i)
                restoredDevices.add(
                    NetworkDevice(
                        id = d.optString("id", UUID.randomUUID().toString()),
                        name = d.getString("name"),
                        ipAddress = d.getString("ipAddress"),
                        deviceType = runCatching { DeviceType.valueOf(d.getString("deviceType")) }.getOrDefault(DeviceType.ACCESS_POINT),
                        macAddress = d.optString("macAddress", ""),
                        towerLocation = d.optString("towerLocation", "البرج الرئيسي"),
                        frequency = d.optString("frequency", "5500 MHz"),
                        channelWidth = d.optString("channelWidth", "20/40 MHz"),
                        status = runCatching { DeviceStatus.valueOf(d.getString("status")) }.getOrDefault(DeviceStatus.ONLINE),
                        notes = d.optString("notes", "")
                    )
                )
            }
            saveDevicesInternal(restoredDevices)
            _devices.value = restoredDevices
        }

        if (root.has("subnets")) {
            val subArr = root.getJSONArray("subnets")
            val restoredSubnets = mutableListOf<SubnetRange>()
            for (i in 0 until subArr.length()) {
                val s = subArr.getJSONObject(i)
                restoredSubnets.add(
                    SubnetRange(
                        id = s.optString("id", UUID.randomUUID().toString()),
                        name = s.getString("name"),
                        cidr = s.getString("cidr"),
                        gateway = s.getString("gateway"),
                        dhcpRangeStart = s.getString("dhcpRangeStart"),
                        dhcpRangeEnd = s.getString("dhcpRangeEnd"),
                        purpose = s.getString("purpose")
                    )
                )
            }
            saveSubnetsInternal(restoredSubnets)
            _subnets.value = restoredSubnets
        }
    }
}
