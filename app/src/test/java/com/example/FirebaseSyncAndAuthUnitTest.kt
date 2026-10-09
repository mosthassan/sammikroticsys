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
    fun testCompleteBackupIncludesNetworkProfileExchangeRatesAndDevices() = kotlinx.coroutines.runBlocking {
        val repo = com.example.data.network.NetworkRepository(context)

        // 1. Configure custom network identity (هوية الشبكة)
        val customConfig = com.example.data.network.NetworkConfig(
            networkName = "شبكة سام العالمية للألياف",
            ownerName = "المهندس مصطفى حسان",
            location = "صنعاء - برج نقم",
            rateZone = com.example.core.model.RateZone.SANAA,
            defaultUsdRateMicros = 540_000_000L,
            defaultSarRateMicros = 142_000_000L
        )
        repo.saveConfig(customConfig)

        // 2. Add custom exchange rates (سعر الصرف)
        val testRate = com.example.data.local.entity.CurrencyRateEntity(
            id = "RATE_TEST_CUSTOM",
            currency = "USD",
            zone = "ADEN",
            rateMicros = 1_920_000_000L,
            effectiveDateEpochDay = java.time.LocalDate.now().toEpochDay(),
            reason = "سعر إغلاق السوق في عدن"
        )
        db.currencyRateDao().insertRate(testRate)

        // 3. Add custom network devices (أجهزة الشبكة)
        val testDevice = com.example.data.network.NetworkDevice(
            id = "DEV_TEST_001",
            name = "سيكتور برج النصر mANTBox",
            ipAddress = "10.10.88.1",
            deviceType = com.example.data.network.DeviceType.ACCESS_POINT,
            macAddress = "AA:BB:CC:DD:EE:FF",
            towerLocation = "برج النصر المركزي"
        )
        repo.addOrUpdateDevice(testDevice)

        // 4. Create BackupRestoreUseCase with all components
        val useCase = com.example.domain.usecase.BackupRestoreUseCase(
            db = db,
            deviceDao = repo,
            networkRepository = repo,
            context = context
        )

        // 5. Export JSON
        val exportedJson = useCase.exportDatabaseToJson()
        assertNotNull(exportedJson)

        // Assert JSON contains Network Profile, Exchange Rates, and Network Devices
        assertTrue("Backup must include network identity name", exportedJson.contains("شبكة سام العالمية للألياف"))
        assertTrue("Backup must include network owner", exportedJson.contains("المهندس مصطفى حسان"))
        assertTrue("Backup must include custom USD micro-rate", exportedJson.contains("540000000"))
        assertTrue("Backup must include currency_rates", exportedJson.contains("currency_rates") || exportedJson.contains("currencyRates"))
        assertTrue("Backup must include custom exchange rate value", exportedJson.contains("1920000000"))
        assertTrue("Backup must include network_devices", exportedJson.contains("network_devices") || exportedJson.contains("networkDevices"))
        assertTrue("Backup must include custom device name", exportedJson.contains("سيكتور برج النصر mANTBox"))
        assertTrue("Backup must include custom device IP", exportedJson.contains("10.10.88.1"))

        // 6. Restore into fresh database & repo
        val freshDb = AppDatabase.createInMemory(context)
        val freshRepo = com.example.data.network.NetworkRepository(context)
        val restoreUseCase = com.example.domain.usecase.BackupRestoreUseCase(
            db = freshDb,
            deviceDao = freshRepo,
            networkRepository = freshRepo,
            context = context
        )

        val restoreResult = restoreUseCase.restoreDatabaseFromJson(exportedJson)
        assertTrue("Restore must succeed: ${restoreResult.exceptionOrNull()?.message}", restoreResult.isSuccess)

        // Verify Network Profile restored
        assertEquals("شبكة سام العالمية للألياف", freshRepo.config.value.networkName)
        assertEquals("المهندس مصطفى حسان", freshRepo.config.value.ownerName)
        assertEquals(540_000_000L, freshRepo.config.value.defaultUsdRateMicros)

        // Verify Network Devices restored
        val restoredDevices = freshRepo.devices.value
        val foundDevice = restoredDevices.firstOrNull { it.id == "DEV_TEST_001" || it.ipAddress == "10.10.88.1" }
        assertNotNull("Restored network device must be present", foundDevice)
        assertEquals("سيكتور برج النصر mANTBox", foundDevice!!.name)
        assertEquals("10.10.88.1", foundDevice.ipAddress)

        // Verify Currency Rates restored
        val restoredRates = freshDb.currencyRateDao().getAllRatesSync()
        val foundRate = restoredRates.firstOrNull { it.rateMicros == 1_920_000_000L }
        assertNotNull("Restored currency rate must be present in DB", foundRate)
        assertEquals("USD", foundRate!!.currency)
        assertEquals("ADEN", foundRate.zone)

        freshDb.close()
    }
}
