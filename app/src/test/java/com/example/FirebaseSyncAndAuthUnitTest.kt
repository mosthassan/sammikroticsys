package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.auth.GoogleAuthManager
import com.example.data.auth.UserProfile
import com.example.data.local.AppDatabase
import com.example.data.sync.FirebaseSyncManager
import org.json.JSONObject
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
    fun testCompleteBackupAndRestoreCoversAllTabsAndEntities() = kotlinx.coroutines.runBlocking {
        val netRepo = com.example.data.network.NetworkRepository(context)
        val backupUseCase = com.example.domain.usecase.BackupRestoreUseCase(db, netRepo)

        // 1. Setup Network Config (هوية الشبكة)
        val customConfig = com.example.data.network.NetworkConfig(
            networkName = "شبكة سام العالمية للاختبار",
            ownerName = "المهندس مصطفى",
            mainRouterModel = "MikroTik CCR2116",
            routerOsVersion = "v7.17",
            supportPhone = "771234567"
        )
        netRepo.saveConfig(customConfig)

        // 2. Setup Network Devices (أجهزة ميكروتك)
        val testDevice = com.example.data.network.NetworkDevice(
            id = "DEV_TEST_101",
            name = "راوتر سيرفر رئيسي 2116",
            ipAddress = "10.10.99.1",
            deviceType = com.example.data.network.DeviceType.ROUTER,
            macAddress = "E4:8D:8C:00:11:22",
            towerLocation = "برج القمة",
            frequency = "Core Fiber",
            status = com.example.data.network.DeviceStatus.ONLINE,
            notes = "جهاز التوجيه الرئيسي"
        )
        netRepo.addOrUpdateDevice(testDevice)

        // 3. Setup Organization (هوية المنشأة)
        val existingOrg = db.organizationDao().getOrganizationSync()
        if (existingOrg != null) {
            db.organizationDao().updateOrganization(existingOrg.copy(name = "مؤسسة سام تيك للإنترنت"))
        } else {
            val org = com.example.data.local.entity.OrganizationEntity(
                id = "ORG_TEST_1",
                name = "مؤسسة سام تيك للإنترنت",
                functionalCurrency = "YER",
                primaryRateZone = "SANAA",
                isInitialized = true
            )
            db.organizationDao().insertOrganization(org)
        }

        // 4. Setup Currency Rate (أسعار الصرف)
        val rate = com.example.data.local.entity.CurrencyRateEntity(
            id = "RATE_USD_TEST_99",
            currency = "USD",
            zone = "SANAA",
            rateMicros = 535_000_000L,
            effectiveDateEpochDay = 20000L,
            createdBy = "ADMIN",
            reason = "سعر صرف اختباري"
        )
        db.currencyRateDao().insertRate(rate)

        // 5. Setup Party (عميل / وكيل)
        val party = com.example.data.local.entity.PartyEntity(
            id = "PARTY_TEST_99",
            name = "وكالة النور الرقمية",
            phone = "778899000",
            isCustomer = true
        )
        db.partyDao().insertParty(party)

        // 6. Export Complete Database
        val jsonString = backupUseCase.exportDatabaseToJson()
        val jsonRoot = JSONObject(jsonString)

        // Verify JSON includes all tabs
        assertTrue("يجب أن يحتوي التصدير على هوية المنشأة", jsonRoot.has("organization"))
        assertEquals("مؤسسة سام تيك للإنترنت", jsonRoot.getJSONObject("organization").getString("name"))

        assertTrue("يجب أن يحتوي التصدير على بيانات الشبكة", jsonRoot.has("network_hub"))
        val netHubJson = jsonRoot.getJSONObject("network_hub")
        assertEquals("شبكة سام العالمية للاختبار", netHubJson.getJSONObject("config").getString("networkName"))
        assertTrue("يجب أن يحتوي التصدير على الأجهزة", netHubJson.getJSONArray("devices").length() > 0)

        assertTrue("يجب أن يحتوي التصدير على أسعار الصرف", jsonRoot.has("currency_rates"))
        assertTrue("يجب أن يحتوي التصدير على العملاء والأطراف", jsonRoot.has("parties"))

        // 7. Restore into a fresh Database instance and fresh NetworkRepository
        val newDb = AppDatabase.createInMemory(context)
        val newNetRepo = com.example.data.network.NetworkRepository(context)
        val restoreUseCase = com.example.domain.usecase.BackupRestoreUseCase(newDb, newNetRepo)

        val restoreResult = restoreUseCase.restoreDatabaseFromJson(jsonString)
        assertTrue("يجب أن تنجح الاستعادة الكاملة", restoreResult.isSuccess)

        // 8. Assertions on restored state
        val restoredOrg = newDb.organizationDao().getOrganizationSync()
        assertNotNull("يجب استعادة هوية المنشأة", restoredOrg)
        assertEquals("مؤسسة سام تيك للإنترنت", restoredOrg?.name)

        val restoredRates = newDb.currencyRateDao().getAllRatesSync()
        assertTrue("يجب استعادة أسعار الصرف", restoredRates.any { it.id == "RATE_USD_TEST_99" })

        val restoredParties = newDb.partyDao().getAllPartiesSync()
        assertTrue("يجب استعادة العملاء", restoredParties.any { it.id == "PARTY_TEST_99" })

        assertEquals("يجب استعادة هوية الشبكة", "شبكة سام العالمية للاختبار", newNetRepo.config.value.networkName)
        assertEquals("يجب استعادة موديل راوتر ميكروتك", "MikroTik CCR2116", newNetRepo.config.value.mainRouterModel)
        assertTrue("يجب استعادة أجهزة ميكروتك", newNetRepo.devices.value.any { it.id == "DEV_TEST_101" })
    }
}
