package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.dao.DeviceDao
import com.example.data.network.DeviceSaveResult
import com.example.data.network.DeviceStatus
import com.example.data.network.DeviceType
import com.example.data.network.NetworkDevice
import com.example.data.network.NetworkRepository
import com.example.ui.viewmodel.NetworkDeviceViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NetworkDeviceManagementTest {

    private lateinit var context: Context
    private lateinit var repository: NetworkRepository
    private lateinit var viewModel: NetworkDeviceViewModel

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        repository = NetworkRepository(context)
        viewModel = NetworkDeviceViewModel(repository)
    }

    /**
     * Requirement 1 & 3: Verify device cloning preserves all configuration metadata
     * (Model, Device Type, Management Port, Subnet, Credentials, Location/Tower, Frequency,
     * Channel Width, and Configuration Notes), automatically suffixes the device name with " - نسخة",
     * clears the IP address and MAC address, and generates a distinct unique ID.
     */
    @Test
    fun testDeviceCloningPreservesMetadataWithDistinctIdAndClearedIp() {
        val originalDevice = NetworkDevice(
            id = "router-dev-001",
            name = "راوتر التوزيع 2",
            ipAddress = "10.10.5.1",
            macAddress = "00:11:22:33:44:55",
            deviceType = DeviceType.ROUTER,
            model = "MikroTik CCR2004-16G-2S+",
            managementPort = 8291,
            subnet = "10.10.5.0/24",
            credentials = "admin:securepass",
            towerLocation = "برج السبعين الرئيسي",
            frequency = "5800 MHz",
            channelWidth = "40 MHz",
            status = DeviceStatus.ONLINE,
            notes = "راوتر التوجيه المركزي للبوابة 1"
        )

        // Clone via NetworkDeviceViewModel
        val clonedFromVm = viewModel.cloneDevice(originalDevice)

        // 1. Verify Distinct ID
        assertNotEquals("Cloned device ID must be distinct from original", originalDevice.id, clonedFromVm.id)
        assertTrue("Cloned device ID must not be blank", clonedFromVm.id.isNotBlank())

        // 2. Verify Name Suffix
        assertEquals("Name must have ' - نسخة' suffix", "راوتر التوزيع 2 - نسخة", clonedFromVm.name)

        // 3. Verify IP Address is cleared to enforce assigning a new unique IP
        assertEquals("IP address must be empty on cloned device", "", clonedFromVm.ipAddress)
        assertEquals("MAC address must be empty on cloned device", "", clonedFromVm.macAddress)

        // 4. Verify Technical Specifications are strictly preserved
        assertEquals("Model must be preserved", originalDevice.model, clonedFromVm.model)
        assertEquals("Device type must be preserved", originalDevice.deviceType, clonedFromVm.deviceType)
        assertEquals("Management port must be preserved", originalDevice.managementPort, clonedFromVm.managementPort)
        assertEquals("Subnet must be preserved", originalDevice.subnet, clonedFromVm.subnet)
        assertEquals("Credentials must be preserved", originalDevice.credentials, clonedFromVm.credentials)
        assertEquals("Location/Tower must be preserved", originalDevice.towerLocation, clonedFromVm.towerLocation)
        assertEquals("Location getter must match", originalDevice.location, clonedFromVm.location)
        assertEquals("Frequency must be preserved", originalDevice.frequency, clonedFromVm.frequency)
        assertEquals("Channel width must be preserved", originalDevice.channelWidth, clonedFromVm.channelWidth)
        assertEquals("Notes must be preserved", originalDevice.notes, clonedFromVm.notes)
        assertEquals("Status must be preserved", originalDevice.status, clonedFromVm.status)

        // Also test model helper clone method
        val clonedFromModel = originalDevice.clone()
        assertNotEquals(originalDevice.id, clonedFromModel.id)
        assertEquals("راوتر التوزيع 2 - نسخة", clonedFromModel.name)
        assertEquals("", clonedFromModel.ipAddress)
        assertEquals(originalDevice.model, clonedFromModel.model)
        assertEquals(originalDevice.managementPort, clonedFromModel.managementPort)
    }

    /**
     * Requirement 2 & 3: Verify IP conflict detection blocks duplicate IP submissions
     * and permits unique IPs across NetworkRepository and NetworkDeviceViewModel.
     */
    @Test
    fun testIpConflictDetectionBlocksDuplicatesAndPermitsUniqueIps() = runBlocking {
        // Ensure we have a known primary device in the repository
        val primaryDevice = NetworkDevice(
            id = "primary-ap-101",
            name = "سيكتور البرج الشمالي",
            ipAddress = "10.10.10.50",
            towerLocation = "برج شميلة",
            deviceType = DeviceType.ACCESS_POINT,
            model = "BaseBox 5"
        )
        val insertResult = repository.addOrUpdateDevice(primaryDevice)
        assertTrue("Initial device setup must succeed", insertResult is DeviceSaveResult.Success)

        // 1. Reactive Check via ViewModel for Duplicate IP
        val conflictDetected = viewModel.checkIpConflict("10.10.10.50", currentDeviceId = "another-device-id")
        assertNotNull("Conflict check must detect existing IP", conflictDetected)
        assertEquals("Conflicting device name must match", primaryDevice.name, conflictDetected?.name)
        assertEquals("Conflicting device location must match", primaryDevice.towerLocation, conflictDetected?.location)

        // 2. Self-check (same device ID) must NOT trigger conflict
        val selfConflict = viewModel.checkIpConflict("10.10.10.50", currentDeviceId = primaryDevice.id)
        assertNull("Same device editing its own IP must not trigger conflict", selfConflict)

        // 3. Unique IP check must NOT trigger conflict
        val uniqueCheck = viewModel.checkIpConflict("10.10.10.99", currentDeviceId = "any-device")
        assertNull("Unique IP must not trigger conflict", uniqueCheck)

        // 4. Attempt to add a second device with DUPLICATE IP -> MUST BE BLOCKED
        val duplicateDevice = NetworkDevice(
            id = "duplicate-ap-102",
            name = "سيكتور تجريبي مكرر",
            ipAddress = "10.10.10.50",
            towerLocation = "برج حدة",
            deviceType = DeviceType.ACCESS_POINT
        )
        val blockedResult = repository.addOrUpdateDevice(duplicateDevice)
        assertTrue("Duplicate IP submission must result in IpConflict", blockedResult is DeviceSaveResult.IpConflict)
        val conflict = blockedResult as DeviceSaveResult.IpConflict
        assertEquals("Conflict must report existing device name", primaryDevice.name, conflict.conflictingDevice.name)
        assertEquals("Conflict must report existing tower location", primaryDevice.towerLocation, conflict.conflictingDevice.towerLocation)

        // 5. Add a second device with UNIQUE IP -> MUST SUCCEED
        val validUniqueDevice = NetworkDevice(
            id = "unique-ap-103",
            name = "سيكتور فريد جديد",
            ipAddress = "10.10.10.75",
            towerLocation = "برج نقم",
            deviceType = DeviceType.ACCESS_POINT
        )
        val allowedResult = repository.addOrUpdateDevice(validUniqueDevice)
        assertTrue("Unique IP submission must succeed", allowedResult is DeviceSaveResult.Success)

        // Verify it was added to the device list
        val allDevices = repository.getAllDevices()
        assertTrue(allDevices.any { it.id == validUniqueDevice.id && it.ipAddress == "10.10.10.75" })
    }

    /**
     * Requirement 2: Verify DeviceDao query findDeviceByIp contracts.
     */
    @Test
    fun testDeviceDaoInterfaceQueryContracts() = runBlocking {
        val deviceDao: DeviceDao = repository

        val testDevice = NetworkDevice(
            id = "dao-test-dev-1",
            name = "سويتش التوزيع الرئيسي",
            ipAddress = "10.10.20.1",
            towerLocation = "غرفة الخوادم",
            deviceType = DeviceType.SWITCH
        )
        repository.addOrUpdateDevice(testDevice)

        // Query by string ID
        val foundByString = deviceDao.findDeviceByIp("10.10.20.1", currentDeviceId = "different-id")
        assertNotNull("DeviceDao must find device by IP excluding other ID", foundByString)
        assertEquals("dao-test-dev-1", foundByString?.id)

        // Query by Long hash code ID (for Long parameter overload)
        val otherIdLong = "different-id".hashCode().toLong()
        val foundByLong = deviceDao.findDeviceByIp("10.10.20.1", currentDeviceId = otherIdLong)
        assertNotNull("DeviceDao must find device with Long ID parameter", foundByLong)
        assertEquals("dao-test-dev-1", foundByLong?.id)

        // Query for non-existent IP returns null
        val notFound = deviceDao.findDeviceByIp("192.168.100.254", currentDeviceId = 99999L)
        assertNull("DeviceDao must return null for non-existent IP", notFound)
    }

    /**
     * Test cloning an existing device, giving it a unique IP, and saving it successfully.
     */
    @Test
    fun testCloneAndSaveWithNewUniqueIp() = runBlocking {
        val baseDevice = NetworkDevice(
            id = "base-sector-1",
            name = "سيكتور التوزيع 1",
            ipAddress = "10.10.30.10",
            towerLocation = "برج عصر",
            model = "mANTBox 19s",
            managementPort = 8728,
            subnet = "10.10.30.0/24",
            credentials = "admin"
        )
        repository.addOrUpdateDevice(baseDevice)

        // Clone base device
        val cloned = viewModel.cloneDevice(baseDevice)
        assertEquals("سيكتور التوزيع 1 - نسخة", cloned.name)
        assertEquals("", cloned.ipAddress)

        // Assign a new valid IP and save
        val configuredClone = cloned.copy(ipAddress = "10.10.30.11")
        val saveResult = repository.addOrUpdateDevice(configuredClone)

        assertTrue("Saving cloned device with new unique IP must succeed", saveResult is DeviceSaveResult.Success)

        // Verify both original and cloned exist side by side
        val devices = repository.getAllDevices()
        val originalInDb = devices.firstOrNull { it.id == baseDevice.id }
        val cloneInDb = devices.firstOrNull { it.id == configuredClone.id }

        assertNotNull("Original device must still exist", originalInDb)
        assertNotNull("Cloned device must exist in DB", cloneInDb)
        assertEquals("10.10.30.10", originalInDb?.ipAddress)
        assertEquals("10.10.30.11", cloneInDb?.ipAddress)
        assertEquals(baseDevice.model, cloneInDb?.model)
        assertEquals(baseDevice.towerLocation, cloneInDb?.towerLocation)
    }
}
