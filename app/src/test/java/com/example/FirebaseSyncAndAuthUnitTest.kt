package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.auth.GoogleAuthManager
import com.example.data.auth.UserProfile
import com.example.data.local.AppDatabase
import com.example.data.sync.FirebaseSyncManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FirebaseSyncAndAuthUnitTest {

    private lateinit var context: Context
    private lateinit var authManager: GoogleAuthManager
    private lateinit var syncManager: FirebaseSyncManager
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = AppDatabase.createInMemory(context)
        authManager = GoogleAuthManager(context)
        syncManager = FirebaseSyncManager(context, db)
    }

    @Test
    fun testTenantEmailSanitization() {
        val email = "mosthassan.ye@gmail.com"
        val tenantKey = syncManager.sanitizeTenantEmail(email)
        assertEquals("mosthassan_ye_at_gmail_com", tenantKey)

        val emailUpper = "USER.TEST+NET@COMPANY.ORG"
        val sanitized = syncManager.sanitizeTenantEmail(emailUpper)
        assertEquals("user_test+net_at_company_org", sanitized)
    }

    @Test
    fun testGoogleAuthSessionPersistenceAndTenantBinding() {
        val testEmail = "mosthassan.ye@gmail.com"
        val testName = "Mostafa Hassan"

        // 1. Sign in directly with email
        authManager.signInDirectWithEmail(testEmail, testName)

        val profile = authManager.userProfile.value
        assertNotNull(profile)
        assertEquals(testEmail, profile?.email)
        assertEquals(testName, profile?.displayName)

        // 2. Verify new instance restores cached session
        val newAuthManager = GoogleAuthManager(context)
        val restoredProfile = newAuthManager.userProfile.value
        assertNotNull(restoredProfile)
        assertEquals(testEmail, restoredProfile?.email)
        assertEquals(testName, restoredProfile?.displayName)
    }

    @Test
    fun testAutoSyncConfigurationToggle() {
        syncManager.setAutoSync(true)
        assertTrue(syncManager.autoSyncEnabled.value)

        syncManager.setAutoSync(false)
        assertEquals(false, syncManager.autoSyncEnabled.value)

        syncManager.setAutoSync(true)
        assertEquals(true, syncManager.autoSyncEnabled.value)
    }

    @Test
    fun testWebClientIdExtractionDynamically() {
        val clientId = com.example.util.GoogleServicesConfigHelper.getWebClientId(context)
        assertNotNull(clientId)
        assertTrue("Web client ID should contain client id", clientId.contains(".apps.googleusercontent.com"))
        assertTrue("Web client ID should not contain quotes", !clientId.contains("\"") && !clientId.contains("'"))
        assertEquals(clientId.trim(), clientId)
        assertEquals("668455931031-burt9863pi64rshdlmgenejnj27ep0d0.apps.googleusercontent.com", clientId)
    }

    @Test
    fun testAuthManagerSessionHandling() {
        val auth = com.example.data.auth.AuthManager(context)
        val session = auth.signInDirectly("test.user@company.com", "Test User")
        assertEquals("test.user@company.com", session.email)
        assertEquals("Test User", session.displayName)
        assertTrue(session.uid.isNotBlank())
        assertEquals(session, auth.currentUser.value)
    }

    @Test
    fun testRateZoneToggleAndConversion() {
        val sanaaRate = com.example.core.model.ExchangeRate(
            com.example.core.model.CurrencyCode.USD,
            com.example.core.model.CurrencyCode.FUNCTIONAL,
            535_000_000L
        )
        val adenRate = com.example.core.model.ExchangeRate(
            com.example.core.model.CurrencyCode.USD,
            com.example.core.model.CurrencyCode.FUNCTIONAL,
            1_900_000_000L
        )

        val usdMinor = 100_00L // $100.00
        val sanaaYer = sanaaRate.convert(usdMinor)
        val adenYer = adenRate.convert(usdMinor)

        assertEquals(53_500_00L, sanaaYer) // 53,500.00 YER
        assertEquals(190_000_00L, adenYer) // 190,000.00 YER
        assertTrue(adenYer > sanaaYer)
    }

    @Test
    fun testExportAndRestoreIncludesNetworkDevicesRatesAndOrganization() = kotlinx.coroutines.runBlocking {
        val netRepo = com.example.data.network.NetworkRepository(context)
        val backupUseCase = com.example.domain.usecase.BackupRestoreUseCase(
            db = db,
            deviceDao = netRepo,
            networkRepository = netRepo,
            context = context
        )

        // 1. Insert or update test Organization
        val existingOrg = db.organizationDao().getOrganizationSync()
        if (existingOrg != null) {
            db.organizationDao().updateOrganization(existingOrg.copy(name = "شبكة سام ميكروتك السحابية"))
        } else {
            val testOrg = com.example.data.local.entity.OrganizationEntity(
                id = "ORG_TEST_SYNC",
                name = "شبكة سام ميكروتك السحابية",
                taxNumber = "998877",
                functionalCurrency = "YER",
                fiscalYearStartMonth = 1,
                isInitialized = true,
                primaryRateZone = "SANAA",
                equityShareMode = "DERIVED_FROM_CAPITAL",
                createdAt = System.currentTimeMillis()
            )
            db.organizationDao().insertOrganization(testOrg)
        }

        // 2. Insert test Currency Rates
        val testRateUsd = com.example.data.local.entity.CurrencyRateEntity(
            id = "RATE_USD_SANAA_TEST",
            currency = "USD",
            zone = "SANAA",
            rateMicros = 535_000_000L,
            effectiveDateEpochDay = 20450L,
            createdAt = System.currentTimeMillis(),
            createdBy = "ADMIN",
            reason = "Initial cloud sync test rate"
        )
        val testRateSar = com.example.data.local.entity.CurrencyRateEntity(
            id = "RATE_SAR_SANAA_TEST",
            currency = "SAR",
            zone = "SANAA",
            rateMicros = 140_500_000L,
            effectiveDateEpochDay = 20450L,
            createdAt = System.currentTimeMillis(),
            createdBy = "ADMIN",
            reason = "SAR rate"
        )
        db.currencyRateDao().insertRate(testRateUsd)
        db.currencyRateDao().insertRate(testRateSar)

        // 3. Add test Network Device
        val testDevice = com.example.data.network.NetworkDevice(
            id = "dev_cloud_sync_1",
            name = "سيكتور شمالي BaseBox 5",
            ipAddress = "10.10.50.1",
            deviceType = com.example.data.network.DeviceType.ACCESS_POINT,
            macAddress = "AA:BB:CC:DD:EE:01",
            towerLocation = "برج السبعين",
            frequency = "5500 MHz",
            channelWidth = "20/40 MHz",
            status = com.example.data.network.DeviceStatus.ONLINE,
            notes = "جهاز تجريبي للمزامنة السحابية",
            model = "MikroTik BaseBox 5",
            managementPort = 8728,
            subnet = "10.10.50.0/24",
            credentials = "admin:secret"
        )
        netRepo.addOrUpdateDevice(testDevice)

        // 4. Update Network Config
        val updatedConfig = netRepo.config.value.copy(
            networkName = "شبكة سام ميكروتك السحابية",
            location = "صنعاء - السبعين",
            defaultUsdRateMicros = 535_000_000L
        )
        netRepo.saveConfig(updatedConfig)

        // 5. Export JSON snapshot
        val jsonPayload = backupUseCase.exportDatabaseToJson()
        val root = org.json.JSONObject(jsonPayload)

        assertTrue("Root must contain organization", root.has("organization"))
        assertEquals("شبكة سام ميكروتك السحابية", root.getJSONObject("organization").getString("name"))

        assertTrue("Root must contain currency_rates", root.has("currency_rates"))
        val ratesArr = root.getJSONArray("currency_rates")
        assertTrue("Must export at least 2 currency rates", ratesArr.length() >= 2)

        assertTrue("Root must contain network_devices", root.has("network_devices"))
        val devArr = root.getJSONArray("network_devices")
        assertTrue("Must export network devices", devArr.length() >= 1)

        assertTrue("Root must contain network_profile", root.has("network_profile"))
        val profObj = root.getJSONObject("network_profile")
        assertEquals("شبكة سام ميكروتك السحابية", profObj.getString("networkName"))

        // 6. Test restore into fresh in-memory database
        val newDb = com.example.data.local.AppDatabase.createInMemory(context)
        val newNetRepo = com.example.data.network.NetworkRepository(context)
        val restoreUseCase = com.example.domain.usecase.BackupRestoreUseCase(
            db = newDb,
            deviceDao = newNetRepo,
            networkRepository = newNetRepo,
            context = context
        )

        val restoreResult = restoreUseCase.restoreDatabaseFromJson(jsonPayload)
        assertTrue("Restore must succeed", restoreResult.isSuccess)

        // Verify organization restored
        val restoredOrg = newDb.organizationDao().getOrganizationSync()
        assertNotNull("Restored organization must not be null", restoredOrg)
        assertEquals("شبكة سام ميكروتك السحابية", restoredOrg?.name)

        // Verify currency rates restored
        val restoredRates = newDb.currencyRateDao().getAllRatesSync()
        assertTrue("Restored currency rates must be >= 2", restoredRates.size >= 2)
        assertTrue(restoredRates.any { it.currency == "USD" && it.rateMicros == 535_000_000L })

        // Verify network devices restored
        val restoredDevices = newNetRepo.getAllDevices()
        assertTrue("Restored devices must be >= 1", restoredDevices.isNotEmpty())
        assertTrue(restoredDevices.any { it.id == "dev_cloud_sync_1" && it.ipAddress == "10.10.50.1" })

        // Verify network config restored
        assertEquals("شبكة سام ميكروتك السحابية", newNetRepo.config.value.networkName)
    }

    @Test
    fun testFirestoreSyncManagerMetadataModel() {
        val meta = com.example.data.sync.SyncMetadata(
            userEmail = "user@example.com",
            lastSyncedAt = 1000L,
            totalDocuments = 15,
            totalJournalLines = 30,
            totalParties = 5,
            totalPackages = 4,
            totalTreasuries = 3,
            totalAllocations = 2,
            totalDevices = 8,
            totalNetworkDevices = 8,
            totalCurrencyRates = 6,
            networkName = "شبكة النور",
            organizationName = "شركة النور للإنترنت",
            hasNetworkProfile = true,
            isBalanced = true,
            checksum = "abc123sha"
        )

        assertEquals("user@example.com", meta.userEmail)
        assertEquals(8, meta.totalDevices)
        assertEquals(8, meta.totalNetworkDevices)
        assertEquals(6, meta.totalCurrencyRates)
        assertEquals("شبكة النور", meta.networkName)
        assertEquals("شركة النور للإنترنت", meta.organizationName)
        assertTrue(meta.hasNetworkProfile)
    }

    @Test
    fun testAutomaticInitialSyncConditions() = kotlinx.coroutines.runBlocking {
        // Test condition: Empty local database triggers automatic restore on new device
        val docCount = db.documentDao().getAllDocumentsSync().size
        val linesCount = db.journalDao().getAllLinesSync().size
        val isNewDevice = docCount == 0 && linesCount == 0
        assertTrue("Fresh in-memory database must be identified as new device", isNewDevice)

        // Remote meta simulation
        val remoteMeta = com.example.data.sync.CloudBackupMeta(
            uid = "user_123",
            userEmail = "user@test.com",
            timestamp = System.currentTimeMillis(),
            documentsCount = 10,
            journalLinesCount = 20,
            partiesCount = 4,
            totalRecords = 34,
            checksum = "sha256_dummy",
            networkDevicesCount = 5,
            currencyRatesCount = 2,
            networkName = "شبكة اختبار",
            hasNetworkProfile = true
        )
        assertTrue(remoteMeta.totalRecords > 0)
        assertEquals(5, remoteMeta.networkDevicesCount)
        assertEquals(2, remoteMeta.currencyRatesCount)
    }
}

