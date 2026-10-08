package com.example.data.local.dao

import androidx.room.Query
import com.example.data.network.NetworkDevice

/**
 * Data Access Object definition for Network Devices and Real-Time IP Conflict Resolution.
 */
interface DeviceDao {

    @Query("SELECT * FROM network_devices WHERE ip_address = :ip AND id != :currentDeviceId LIMIT 1")
    suspend fun findDeviceByIp(ip: String, currentDeviceId: Long): NetworkDevice?

    suspend fun findDeviceByIp(ip: String, currentDeviceId: String): NetworkDevice?

    suspend fun getAllDevices(): List<NetworkDevice>

    suspend fun insertDevice(device: NetworkDevice)

    suspend fun updateDevice(device: NetworkDevice)

    suspend fun deleteDevice(deviceId: String)
}
