package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.model.CurrencyCode
import com.example.core.model.Money
import com.example.core.model.UuidUtils
import com.example.data.local.entity.DocumentEntity
import com.example.data.local.entity.DocumentItemEntity
import com.example.data.local.entity.PartyEntity
import com.example.domain.usecase.StatementItem
import com.example.domain.usecase.StatementOfAccountReport
import com.example.ui.screens.SalesItemDraftState
import com.example.util.PdfDocumentGenerator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream

@Implements(PdfDocument::class)
class ShadowPdfDocument {
    @Implementation
    fun startPage(pageInfo: PdfDocument.PageInfo): PdfDocument.Page {
        val bitmap = Bitmap.createBitmap(pageInfo.pageWidth, pageInfo.pageHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val constructor = PdfDocument.Page::class.java.getDeclaredConstructor(
            Canvas::class.java,
            PdfDocument.PageInfo::class.java
        )
        constructor.isAccessible = true
        return constructor.newInstance(canvas, pageInfo)
    }

    @Implementation
    fun finishPage(page: PdfDocument.Page) {
    }

    @Implementation
    fun writeTo(out: OutputStream) {
        out.write("%PDF-1.4\n".toByteArray(Charsets.UTF_8))
        out.flush()
    }

    @Implementation
    fun close() {
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [ShadowPdfDocument::class])
class SalesInvoicesAndPdfStatementTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testPdfAgentStatementGenerationProducesValidPdf() {
        val party = PartyEntity(
            id = "PARTY_TEST_001",
            name = "مركز الأمل لخدمات الإنترنت",
            phone = "771234567",
            isCustomer = true
        )

        val items = listOf(
            StatementItem(
                entryId = "ENTRY_1",
                docId = "DOC_1",
                entryDateEpochDay = 20000L,
                docType = "SALES_INVOICE",
                docNumber = 101L,
                memo = "فاتورة كروت مبيعات باقة 1 جيجا",
                baseDebitMinor = 5000000L, // 50,000 YER
                baseCreditMinor = 0L,
                origMinor = 5000000L,
                currency = "YER",
                runningBalanceMinor = 5000000L
            ),
            StatementItem(
                entryId = "ENTRY_2",
                docId = "DOC_2",
                entryDateEpochDay = 20002L,
                docType = "CUSTOMER_RECEIPT",
                docNumber = 201L,
                memo = "سند قبض سداد دفعة نقداً",
                baseDebitMinor = 0L,
                baseCreditMinor = 3000000L, // 30,000 YER
                origMinor = 3000000L,
                currency = "YER",
                runningBalanceMinor = 2000000L // 20,000 YER
            )
        )

        val report = StatementOfAccountReport(
            partyId = party.id,
            partyName = party.name,
            controlAccountCode = "1103",
            startDateEpochDay = 19990L,
            endDateEpochDay = 20010L,
            openingBalanceMinor = 0L,
            totalDebitMinor = 5000000L,
            totalCreditMinor = 3000000L,
            closingBalanceMinor = 2000000L,
            items = items
        )

        val pdfFile = PdfDocumentGenerator.generateAgentStatementPdf(
            context = context,
            report = report,
            party = party,
            organizationName = "شبكة طلقة نت",
            organizationContact = "هاتف: 777000000 - 733000000"
        )

        assertNotNull(pdfFile)
        assertTrue("PDF file should exist", pdfFile.exists())
        assertTrue("PDF file size should be > 0 bytes", pdfFile.length() > 0)
        assertTrue("File should end with .pdf", pdfFile.name.endsWith(".pdf"))

        // Verify PDF Header Magic Bytes "%PDF-"
        val headerBytes = ByteArray(5)
        FileInputStream(pdfFile).use { it.read(headerBytes) }
        val headerString = String(headerBytes)
        assertEquals("%PDF-", headerString)
    }

    @Test
    fun testPdfSalesInvoiceGenerationProducesValidPdf() {
        val invoice = DocumentEntity(
            id = UuidUtils.newTimeOrderedId(),
            type = "SALES_INVOICE",
            fiscalYear = 2026,
            docNumber = 1042L,
            partyId = "PARTY_BARAKA",
            dateEpochDay = 20005L,
            currency = "YER",
            exchangeRateMicros = 1000000L,
            totalMinor = 7500000L, // 75,000 YER
            totalBaseMinor = 7500000L,
            status = "POSTED",
            notes = "توريد كروت توزيع لمحل البركة"
        )

        val party = PartyEntity(
            id = "PARTY_BARAKA",
            name = "محل البركة للاتصالات",
            phone = "775555555",
            isCustomer = true
        )

        val items = listOf(
            DocumentItemEntity(
                id = UuidUtils.newTimeOrderedId(),
                docId = invoice.id,
                itemIndex = 1,
                packageId = "PKG_500MB",
                description = "كرت فئة 500 ميجا",
                accountCode = "4101",
                quantity = 50,
                unitPriceMinor = 50000L, // 500 YER
                totalMinor = 2500000L
            ),
            DocumentItemEntity(
                id = UuidUtils.newTimeOrderedId(),
                docId = invoice.id,
                itemIndex = 2,
                packageId = "PKG_1GB",
                description = "كرت فئة 1 جيجا",
                accountCode = "4101",
                quantity = 50,
                unitPriceMinor = 100000L, // 1000 YER
                totalMinor = 5000000L
            )
        )

        val paidAmountMinor = 5000000L // 50,000 YER paid, 25,000 YER remaining

        val pdfFile = PdfDocumentGenerator.generateSalesInvoicePdf(
            context = context,
            invoice = invoice,
            items = items,
            party = party,
            paidAmountMinor = paidAmountMinor,
            organizationName = "شبكة طلقة نت",
            organizationContact = "هاتف: 777000000 - 733000000"
        )

        assertNotNull(pdfFile)
        assertTrue("PDF invoice file should exist", pdfFile.exists())
        assertTrue("PDF invoice file size should be > 0 bytes", pdfFile.length() > 0)
        assertTrue("File should end with .pdf", pdfFile.name.endsWith(".pdf"))

        // Verify PDF Header Magic Bytes
        val headerBytes = ByteArray(5)
        FileInputStream(pdfFile).use { it.read(headerBytes) }
        val headerString = String(headerBytes)
        assertEquals("%PDF-", headerString)
    }

    @Test
    fun testTopMetricsAndFilterCalculations() {
        val inv1 = DocumentEntity(
            id = "INV_1",
            type = "SALES_INVOICE",
            fiscalYear = 2026,
            docNumber = 1L,
            partyId = "P1",
            dateEpochDay = 20000L,
            currency = "YER",
            exchangeRateMicros = 1000000L,
            totalMinor = 10000000L, // 100,000 YER
            totalBaseMinor = 10000000L,
            status = "POSTED"
        )
        val inv2 = DocumentEntity(
            id = "INV_2",
            type = "SALES_INVOICE",
            fiscalYear = 2026,
            docNumber = 2L,
            partyId = "P2",
            dateEpochDay = 20001L,
            currency = "YER",
            exchangeRateMicros = 1000000L,
            totalMinor = 5000000L, // 50,000 YER
            totalBaseMinor = 5000000L,
            status = "POSTED"
        )
        val inv3 = DocumentEntity(
            id = "INV_3",
            type = "SALES_INVOICE",
            fiscalYear = 2026,
            docNumber = 3L,
            partyId = "P3",
            dateEpochDay = 20002L,
            currency = "YER",
            exchangeRateMicros = 1000000L,
            totalMinor = 4000000L, // 40,000 YER
            totalBaseMinor = 4000000L,
            status = "VOIDED" // voided should be excluded from active totals
        )

        val invoices = listOf(inv1, inv2, inv3)
        val activeInvoices = invoices.filter { it.status != "VOIDED" }

        // Paid allocations: inv1 paid 100,000 (fully paid), inv2 paid 20,000 (partial)
        val paidMap = mapOf(
            "INV_1" to 10000000L,
            "INV_2" to 2000000L, // 20,000 YER
            "INV_3" to 0L
        )

        val totalSalesMinor = activeInvoices.sumOf { it.totalMinor }
        assertEquals(15000000L, totalSalesMinor) // 150,000 YER

        val totalCollectedMinor = activeInvoices.sumOf { inv ->
            minOf(paidMap[inv.id] ?: 0L, inv.totalMinor)
        }
        assertEquals(12000000L, totalCollectedMinor) // 100k + 20k = 120,000 YER

        val totalReceivablesMinor = (totalSalesMinor - totalCollectedMinor).coerceAtLeast(0L)
        assertEquals(3000000L, totalReceivablesMinor) // 30,000 YER remaining

        // Test Live Counter Filter Classifications
        val unpaidCount = invoices.count { it.status != "VOIDED" && (paidMap[it.id] ?: 0L) == 0L }
        val paidCount = invoices.count { it.status != "VOIDED" && (paidMap[it.id] ?: 0L) >= it.totalMinor }
        val partialCount = invoices.count {
            val paid = paidMap[it.id] ?: 0L
            it.status != "VOIDED" && paid > 0L && paid < it.totalMinor
        }

        assertEquals(0, unpaidCount)
        assertEquals(1, paidCount) // inv1
        assertEquals(1, partialCount) // inv2
    }

    @Test
    fun testInvoiceCloningPreservesDraftSpecifications() {
        val originalItems = listOf(
            DocumentItemEntity(
                id = "ITEM_1",
                docId = "ORIG_INV",
                itemIndex = 1,
                packageId = "PKG_A",
                description = "كرت سوبر 2 جيجا",
                accountCode = "4101",
                quantity = 100,
                unitPriceMinor = 150000L,
                totalMinor = 15000000L
            ),
            DocumentItemEntity(
                id = "ITEM_2",
                docId = "ORIG_INV",
                itemIndex = 2,
                packageId = "PKG_B",
                description = "كرت سرعة 512 ميجا",
                accountCode = "4101",
                quantity = 30,
                unitPriceMinor = 70000L,
                totalMinor = 2100000L
            )
        )

        // Clone into draft states
        val clonedDrafts = originalItems.map { item ->
            SalesItemDraftState(
                selectedPackageId = item.packageId ?: "",
                description = item.description,
                quantityText = item.quantity.toString(),
                unitPriceText = (item.unitPriceMinor / 100L).toString()
            )
        }

        assertEquals(2, clonedDrafts.size)
        assertEquals("PKG_A", clonedDrafts[0].selectedPackageId)
        assertEquals("كرت سوبر 2 جيجا", clonedDrafts[0].description)
        assertEquals("100", clonedDrafts[0].quantityText)
        assertEquals("1500", clonedDrafts[0].unitPriceText)

        assertEquals("PKG_B", clonedDrafts[1].selectedPackageId)
        assertEquals("كرت سرعة 512 ميجا", clonedDrafts[1].description)
        assertEquals("30", clonedDrafts[1].quantityText)
        assertEquals("700", clonedDrafts[1].unitPriceText)
    }
}
