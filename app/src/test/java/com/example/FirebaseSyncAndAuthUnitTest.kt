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
}
