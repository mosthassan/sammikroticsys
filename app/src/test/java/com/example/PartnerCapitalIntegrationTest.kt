package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.ledger.AccountConstants
import com.example.core.model.CurrencyCode
import com.example.core.model.ExchangeRate
import com.example.core.model.RateZone
import com.example.data.ledger.LedgerInvariants
import com.example.data.ledger.LedgerWriter
import com.example.data.local.AppDatabase
import com.example.data.local.entity.PartyEntity
import com.example.data.network.NetworkConfig
import com.example.domain.usecase.PartnerEquityUseCase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PartnerCapitalIntegrationTest {

    private lateinit var db: AppDatabase
    private lateinit var writer: LedgerWriter
    private lateinit var invariants: LedgerInvariants

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.createInMemory(context)
        AppDatabase.installTriggers(db.openHelper.writableDatabase)
        AppDatabase.seedDefaultData(db.openHelper.writableDatabase)

        writer = LedgerWriter(db, enableInvariantValidation = true)
        invariants = LedgerInvariants(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testPartnerCreationProducesNoJournalEntries() {
        runBlocking {
            val initialLinesCount = db.journalDao().getAllLinesSync().size

            // 1. Create a partner profile
            val partner = PartyEntity(
                id = "PARTNER_001",
                name = "المهندس أسامة الشريك",
                phone = "771234567",
                isCustomer = false,
                isVendor = false,
                isPartner = true,
                equityPercentageBasisPoints = 3000 // 30.0%
            )
            db.partyDao().insertParty(partner)

            val retrieved = db.partyDao().getPartyById("PARTNER_001")
            assertNotNull(retrieved)
            assertEquals("المهندس أسامة الشريك", retrieved?.name)
            assertEquals(3000, retrieved?.equityPercentageBasisPoints)

            // Verify NO journal lines were generated for creating the partner
            val linesCountAfter = db.journalDao().getAllLinesSync().size
            assertEquals(initialLinesCount, linesCountAfter)

            // Dynamic capital must be 0
            val capitalBalance = db.journalDao().getPartnerCapitalBalanceSync("PARTNER_001")
            assertEquals(0L, capitalBalance)
        }
    }

    @Test
    fun testCashCapitalContributionPosting() {
        runBlocking {
            val partnerId = "PARTNER_CASH_01"
            db.partyDao().insertParty(
                PartyEntity(
                    id = partnerId,
                    name = "الشريك فهد",
                    isPartner = true,
                    equityPercentageBasisPoints = 5000
                )
            )

            val contributionAmountMinor = 5_000_000_00L // 5,000,000 YER
            val dateEpoch = LocalDate.of(2026, 10, 1).toEpochDay()

            // 2. Post cash capital contribution
            val doc = writer.postCapitalReceipt(
                targetAccountCode = AccountConstants.CAPITAL,
                partnerPartyId = partnerId,
                treasuryId = "TR_MAIN_YER",
                fiscalYear = 2026,
                dateEpochDay = dateEpoch,
                amountOrigMinor = contributionAmountMinor,
                currency = CurrencyCode.YER,
                exchangeRate = ExchangeRate.parity(CurrencyCode.YER),
                notes = "مساهمة نقدية في رأس مال الشبكة"
            )

            assertNotNull(doc)
            assertEquals("POSTED", doc.status)

            // Verify partner dynamic capital balance is exactly 5,000,000 YER
            val dynamicCapital = db.journalDao().getPartnerCapitalBalanceSync(partnerId)
            assertEquals(contributionAmountMinor, dynamicCapital)

            // Verify Journal Lines: DR 1101 (5M), CR 3101 (5M)
            val entries = db.journalDao().getEntriesForDocument(doc.id)
            assertEquals(1, entries.size)
            val lines = db.journalDao().getLinesForEntry(entries[0].id)
            assertEquals(2, lines.size)

            val debitLine = lines.first { it.baseDebitMinor > 0 }
            val creditLine = lines.first { it.baseCreditMinor > 0 }

            assertEquals(AccountConstants.CASH_VAULT, debitLine.accountCode)
            assertEquals(contributionAmountMinor, debitLine.baseDebitMinor)

            assertEquals(AccountConstants.CAPITAL, creditLine.accountCode)
            assertEquals(partnerId, creditLine.partyId)
            assertEquals(contributionAmountMinor, creditLine.baseCreditMinor)

            // Verify invariants pass
            invariants.verifyAll()
        }
    }

    @Test
    fun testInKindAssetCapitalContributionAndReversal() {
        runBlocking {
            val partnerId = "PARTNER_INKIND_01"
            db.partyDao().insertParty(
                PartyEntity(
                    id = partnerId,
                    name = "الشريك عمار (معدات وأصول)",
                    isPartner = true,
                    equityPercentageBasisPoints = 2000
                )
            )

            val assetValueMinor = 1_500_000_00L // 1,500,000 YER
            val dateEpoch = LocalDate.of(2026, 10, 2).toEpochDay()

            // 1. Post In-Kind Asset Contribution
            val doc = writer.postInKindCapitalContribution(
                partnerPartyId = partnerId,
                assetName = "برج اتصالات حديدي 24 متر مع سيكتورات",
                fiscalYear = 2026,
                dateEpochDay = dateEpoch,
                amountOrigMinor = assetValueMinor,
                currency = CurrencyCode.YER,
                exchangeRate = ExchangeRate.parity(CurrencyCode.YER),
                usefulLifeMonths = 60,
                notes = "مساهمة عينية بأصل ثابت للشبكة"
            )

            assertNotNull(doc)

            // Dynamic capital should be 1.5M
            val capitalBeforeVoid = db.journalDao().getPartnerCapitalBalanceSync(partnerId)
            assertEquals(assetValueMinor, capitalBeforeVoid)

            // Asset must be recorded in assets table
            val assets = db.assetDao().getAllAssetsSync()
            val createdAsset = assets.firstOrNull { it.docId == doc.id }
            assertNotNull(createdAsset)
            assertEquals("برج اتصالات حديدي 24 متر مع سيكتورات", createdAsset?.name)
            assertEquals(assetValueMinor, createdAsset?.purchaseCostMinor)
            assertEquals(60, createdAsset?.usefulLifeMonths)

            // Verify Journal Lines: DR 1501, CR 3101
            val entries = db.journalDao().getEntriesForDocument(doc.id)
            val normalEntry = entries.first { it.type == "NORMAL" }
            val lines = db.journalDao().getLinesForEntry(normalEntry.id)
            val debitLine = lines.first { it.baseDebitMinor > 0 }
            val creditLine = lines.first { it.baseCreditMinor > 0 }

            assertEquals(AccountConstants.FIXED_ASSETS_NETWORK, debitLine.accountCode)
            assertEquals(AccountConstants.CAPITAL, creditLine.accountCode)
            assertEquals(partnerId, creditLine.partyId)

            // 2. Test Voiding / Reversal of the Capital Contribution Voucher
            val voidSuccess = writer.voidDocument(doc.id, dateEpoch, "إلغاء السند بطلب الشركاء")
            assertTrue(voidSuccess)

            // Updated doc status
            val updatedDoc = db.documentDao().getDocumentById(doc.id)
            assertEquals("VOIDED", updatedDoc?.status)

            // Dynamic capital must drop back to 0!
            val capitalAfterVoid = db.journalDao().getPartnerCapitalBalanceSync(partnerId)
            assertEquals(0L, capitalAfterVoid)

            // Reversal entry must exist
            val allDocEntries = db.journalDao().getEntriesForDocument(doc.id)
            assertEquals(2, allDocEntries.size)
            val reversalEntry = allDocEntries.first { it.type == "REVERSAL" }
            assertNotNull(reversalEntry)

            // Verify invariants pass completely
            invariants.verifyAll()
        }
    }

    @Test
    fun testCapitalContributionInheritsRateZoneAndResolvesDynamically() {
        runBlocking {
            val partnerEquityUseCase = PartnerEquityUseCase(db, writer)
            val today = LocalDate.of(2026, 1, 15).toEpochDay()

            val adenConfig = NetworkConfig(
                networkName = "شبكة عدن العالمية",
                rateZone = RateZone.ADEN,
                defaultUsdRateMicros = 1_650_000_000L,
                defaultSarRateMicros = 435_000_000L
            )

            // Verify resolution for Aden
            val adenUsdRate = partnerEquityUseCase.resolveContributionRate(
                currency = CurrencyCode.USD,
                dateEpochDay = today,
                rateZone = RateZone.ADEN,
                networkConfig = adenConfig
            )
            assertEquals(1_650_000_000L, adenUsdRate)

            val adenSarRate = partnerEquityUseCase.resolveContributionRate(
                currency = CurrencyCode.SAR,
                dateEpochDay = today,
                rateZone = RateZone.ADEN,
                networkConfig = adenConfig
            )
            assertEquals(435_000_000L, adenSarRate)

            // Verify resolution for Sanaa fallback (seeded in DB: USD 530, SAR 140)
            val sanaaUsdRate = partnerEquityUseCase.resolveContributionRate(
                currency = CurrencyCode.USD,
                dateEpochDay = today,
                rateZone = RateZone.SANAA,
                networkConfig = adenConfig
            )
            assertEquals(530_000_000L, sanaaUsdRate)

            val sanaaSarRate = partnerEquityUseCase.resolveContributionRate(
                currency = CurrencyCode.SAR,
                dateEpochDay = today,
                rateZone = RateZone.SANAA,
                networkConfig = adenConfig
            )
            assertEquals(140_000_000L, sanaaSarRate)

            // Create partner
            val partnerId = "PARTNER_ADEN_01"
            db.partyDao().insertParty(
                PartyEntity(
                    id = partnerId,
                    name = "شريك عدن المستثمر",
                    phone = "733000111",
                    isPartner = true
                )
            )

            // Post capital receipt with ADEN rateZone
            val rate = ExchangeRate(CurrencyCode.SAR, CurrencyCode.FUNCTIONAL, adenSarRate)
            val doc = writer.postCapitalReceipt(
                targetAccountCode = AccountConstants.CAPITAL,
                partnerPartyId = partnerId,
                treasuryId = "TR_SAR_VAULT",
                fiscalYear = 2026,
                dateEpochDay = today,
                amountOrigMinor = 1_000_00L, // 1000 SAR
                currency = CurrencyCode.SAR,
                exchangeRate = rate,
                notes = "مساهمة رأس مال بنطاق عدن",
                rateZone = RateZone.ADEN
            )

            assertEquals("ADEN", doc.rateZone)
            assertEquals(435_000_000L, doc.exchangeRateMicros)
            val expectedBase = rate.convert(1_000_00L) // 1000 * 435 = 435,000 YER -> 43,500,000 minor
            assertEquals(expectedBase, doc.totalBaseMinor)

            val check = invariants.verifyAll()
            assertTrue(check.isValid)
        }
    }
}
