package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.auth.GoogleAuthManager
import com.example.data.auth.AuthManager
import com.example.data.local.AppDatabase
import com.example.data.sync.ChunkedBackupManager
import com.example.data.sync.BackupManifest
import com.example.data.sync.TableCompletenessItem
import com.example.data.sync.CompletenessReport
import com.example.domain.usecase.BackupRestoreUseCase
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
    private lateinit var localAuthManager: AuthManager
    private lateinit var db: AppDatabase
    private lateinit var chunkedBackupManager: ChunkedBackupManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = AppDatabase.createInMemory(context)
        authManager = GoogleAuthManager(context)
        localAuthManager = AuthManager(context)
        val backupRestoreUseCase = BackupRestoreUseCase(db)
        chunkedBackupManager = ChunkedBackupManager(context, db, backupRestoreUseCase)
    }

    @Test
    fun testGoogleAuthSessionPersistence() {
        val testEmail = "test_network_admin@example.com"
        val testName = "Admin User"

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
    fun testAuthManagerSessionHandling() {
        val session = localAuthManager.signInDirectly("test.user@company.com", "Test User")
        assertEquals("test.user@company.com", session.email)
        assertEquals("Test User", session.displayName)
        assertTrue(session.uid.isNotBlank())
        assertEquals(session, localAuthManager.currentUser.value)
    }

    @Test
    fun testChunkedBackupManifestModelAndCompleteness() {
        val manifest = BackupManifest(
            backupId = "backup_test_123",
            createdAt = System.currentTimeMillis(),
            deviceId = "TestDevice",
            documentsCount = 10,
            journalLinesCount = 20,
            partiesCount = 5,
            packagesCount = 3,
            treasuriesCount = 2,
            assetsCount = 1,
            allocationsCount = 0,
            currencyRatesCount = 2,
            chunkCount = 1,
            sha256 = "dummy_sha256_hash",
            isEncrypted = false
        )

        assertEquals("backup_test_123", manifest.backupId)
        assertEquals(10, manifest.documentsCount)
        assertEquals(20, manifest.journalLinesCount)

        val item = TableCompletenessItem(
            tableName = "documents",
            localCount = 10,
            backupCount = 10,
            isMatch = true
        )
        assertTrue(item.isMatch)

        val report = CompletenessReport(
            backupId = manifest.backupId,
            backupCreatedAt = manifest.createdAt,
            deviceId = manifest.deviceId,
            items = listOf(item),
            totalLocalRecords = 10,
            totalBackupRecords = 10
        )
        assertEquals(10, report.totalLocalRecords)
        assertEquals(10, report.totalBackupRecords)
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
