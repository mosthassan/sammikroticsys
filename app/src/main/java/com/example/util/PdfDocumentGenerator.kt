package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.core.model.CurrencyCode
import com.example.core.model.Money
import com.example.data.local.entity.DocumentEntity
import com.example.data.local.entity.DocumentItemEntity
import com.example.data.local.entity.PartyEntity
import com.example.domain.usecase.StatementOfAccountReport
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

/**
 * Pixel-Perfect Branded PDF Document Generator for NetFlow / SamMikrotik.
 * Generates:
 * 1. Agent Account Statement (كشف حساب وكيل / AGENT ACCOUNT STATEMENT)
 * 2. Sales Invoice (فاتورة مبيعات كروت / SALES INVOICE)
 * Includes in-app rendering, sharing, external viewing, and WhatsApp integration.
 */
object PdfDocumentGenerator {

    // Standard A4 dimensions in PostScript points (72 points/inch)
    const val PAGE_WIDTH = 595
    const val PAGE_HEIGHT = 842

    private const val MARGIN_LEFT = 36
    private const val MARGIN_RIGHT = 559
    private const val CONTENT_WIDTH = MARGIN_RIGHT - MARGIN_LEFT // 523 points

    // Brand Palette
    private const val COLOR_NAVY_DARK = 0xFF0B192C.toInt()
    private const val COLOR_NAVY_MEDIUM = 0xFF1E293B.toInt()
    private const val COLOR_CYAN_ACCENT = 0xFF00E5FF.toInt()
    private const val COLOR_BORDER = 0xFFCBD5E1.toInt()
    private const val COLOR_ROW_ALT = 0xFFF8FAFC.toInt()
    private const val COLOR_TEXT_PRIMARY = 0xFF0F172A.toInt()
    private const val COLOR_TEXT_MUTED = 0xFF64748B.toInt()
    private const val COLOR_SUCCESS = 0xFF10B981.toInt()
    private const val COLOR_DANGER = 0xFFEF4444.toInt()
    private const val COLOR_WARNING = 0xFFF59E0B.toInt()

    private fun getDocsDirectory(context: Context): File {
        val dir = File(context.cacheDir, "docs")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Generates a Pixel-Perfect Branded Agent Account Statement PDF.
     */
    fun generateAgentStatementPdf(
        context: Context,
        report: StatementOfAccountReport,
        party: PartyEntity?,
        organizationName: String = "شبكة طلقة نت",
        organizationContact: String = "هاتف: 777000000 - 733000000"
    ): File {
        val docsDir = getDocsDirectory(context)
        val sanitizedName = (party?.name ?: report.partyName).replace("\\s+".toRegex(), "_")
        val outputFile = File(docsDir, "statement_${sanitizedName}_${System.currentTimeMillis()}.pdf")

        val document = PdfDocument()
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas

        // 1. Draw Header Bar
        drawHeaderBanner(
            canvas = canvas,
            orgName = organizationName,
            titleAr = "كشف حساب وكيل",
            titleEn = "AGENT ACCOUNT STATEMENT",
            contactInfo = organizationContact
        )

        var currentY = 96f

        // 2. Statement Details Box
        currentY = drawStatementDetailsBox(
            canvas = canvas,
            startY = currentY,
            partyName = party?.name ?: report.partyName,
            partyPhone = party?.phone ?: "",
            closingBalanceMinor = report.closingBalanceMinor,
            currency = CurrencyCode.YER,
            startDateEpoch = report.startDateEpochDay,
            endDateEpoch = report.endDateEpochDay
        )

        currentY += 12f

        // 3. Accounting Movement Table Headers
        currentY = drawStatementTableHeaders(canvas, currentY)

        val items = report.items
        val rowHeight = 26f
        val maxYBeforeFooter = 680f // leave room for summary card & sign-off

        if (items.isEmpty()) {
            // Draw Empty State Row
            paint.color = Color.WHITE
            canvas.drawRect(MARGIN_LEFT.toFloat(), currentY, MARGIN_RIGHT.toFloat(), currentY + 36f, paint)
            paint.color = COLOR_BORDER
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 0.5f
            canvas.drawRect(MARGIN_LEFT.toFloat(), currentY, MARGIN_RIGHT.toFloat(), currentY + 36f, paint)

            drawText(
                canvas = canvas,
                text = "لا توجد حركات مسجلة خلال الفترة المحددة",
                x = MARGIN_LEFT.toFloat(),
                y = currentY + 10f,
                width = CONTENT_WIDTH,
                textSize = 11f,
                textColor = COLOR_TEXT_MUTED,
                align = Layout.Alignment.ALIGN_CENTER,
                isBold = false
            )
            currentY += 44f
        } else {
            // Draw Table Rows
            items.forEachIndexed { index, item ->
                if (currentY + rowHeight > maxYBeforeFooter) {
                    // Draw page footer and open new page
                    drawPageFooter(canvas, pageNumber)
                    document.finishPage(page)

                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                    page = document.startPage(pageInfo)
                    canvas = page.canvas

                    drawHeaderBanner(
                        canvas = canvas,
                        orgName = organizationName,
                        titleAr = "كشف حساب وكيل (تابع)",
                        titleEn = "AGENT ACCOUNT STATEMENT (CONT.)",
                        contactInfo = organizationContact
                    )
                    currentY = 96f
                    currentY = drawStatementTableHeaders(canvas, currentY)
                }

                currentY = drawStatementRow(
                    canvas = canvas,
                    startY = currentY,
                    item = item,
                    isAlt = index % 2 != 0
                )
            }
        }

        currentY += 10f

        // 4. Summary Card (Totals & Net Balance)
        currentY = drawStatementSummaryCard(
            canvas = canvas,
            startY = currentY,
            totalDebit = report.totalDebitMinor,
            totalCredit = report.totalCreditMinor,
            netBalance = report.closingBalanceMinor,
            currency = CurrencyCode.YER
        )

        currentY += 16f

        // 5. Official Sign-off Footer
        drawOfficialSignOffFooter(
            canvas = canvas,
            startY = currentY,
            firstPartyLabel = "ختم واعتماد إدارة الشبكة",
            secondPartyLabel = "موافق وتوقيع الوكيل / البقالة"
        )

        // Draw Page Footer
        drawPageFooter(canvas, pageNumber)
        document.finishPage(page)

        FileOutputStream(outputFile).use { out ->
            document.writeTo(out)
        }
        document.close()

        return outputFile
    }

    /**
     * Generates a Pixel-Perfect Branded Sales Invoice PDF.
     */
    fun generateSalesInvoicePdf(
        context: Context,
        invoice: DocumentEntity,
        items: List<DocumentItemEntity>,
        party: PartyEntity?,
        paidAmountMinor: Long,
        organizationName: String = "شبكة طلقة نت",
        organizationContact: String = "هاتف: 777000000 - 733000000"
    ): File {
        val docsDir = getDocsDirectory(context)
        val formattedDocNum = "INV_${invoice.fiscalYear}_${invoice.docNumber}"
        val outputFile = File(docsDir, "${formattedDocNum}_${System.currentTimeMillis()}.pdf")

        val document = PdfDocument()
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        var pageNumber = 1
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        // 1. Header Bar
        drawHeaderBanner(
            canvas = canvas,
            orgName = organizationName,
            titleAr = "فاتورة مبيعات كروت",
            titleEn = "SALES INVOICE",
            contactInfo = organizationContact
        )

        var currentY = 96f

        // 2. Invoice Details Box
        currentY = drawInvoiceDetailsBox(
            canvas = canvas,
            startY = currentY,
            invoice = invoice,
            party = party,
            paidAmountMinor = paidAmountMinor
        )

        currentY += 12f

        // 3. Invoice Items Table Headers
        currentY = drawInvoiceTableHeaders(canvas, currentY)

        // 4. Draw Items Rows
        if (items.isEmpty()) {
            paint.color = Color.WHITE
            canvas.drawRect(MARGIN_LEFT.toFloat(), currentY, MARGIN_RIGHT.toFloat(), currentY + 36f, paint)
            paint.color = COLOR_BORDER
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 0.5f
            canvas.drawRect(MARGIN_LEFT.toFloat(), currentY, MARGIN_RIGHT.toFloat(), currentY + 36f, paint)

            drawText(
                canvas = canvas,
                text = "كروت إنترنت متنوعة",
                x = MARGIN_LEFT.toFloat(),
                y = currentY + 10f,
                width = CONTENT_WIDTH,
                textSize = 11f,
                textColor = COLOR_TEXT_PRIMARY,
                align = Layout.Alignment.ALIGN_CENTER,
                isBold = false
            )
            currentY += 36f
        } else {
            items.forEachIndexed { index, item ->
                currentY = drawInvoiceRow(
                    canvas = canvas,
                    startY = currentY,
                    index = index + 1,
                    item = item,
                    currency = CurrencyCode.fromString(invoice.currency),
                    isAlt = index % 2 != 0
                )
            }
        }

        currentY += 10f

        // 5. Invoice Totals Summary Card
        currentY = drawInvoiceSummaryCard(
            canvas = canvas,
            startY = currentY,
            totalMinor = invoice.totalMinor,
            paidMinor = paidAmountMinor,
            currency = CurrencyCode.fromString(invoice.currency)
        )

        currentY += 20f

        // 6. Official Sign-off Footer
        drawOfficialSignOffFooter(
            canvas = canvas,
            startY = currentY,
            firstPartyLabel = "ختم واعتماد إدارة الشبكة",
            secondPartyLabel = "استلمت الكروت كاملة / توقيع المستلم"
        )

        drawPageFooter(canvas, pageNumber)
        document.finishPage(page)

        FileOutputStream(outputFile).use { out ->
            document.writeTo(out)
        }
        document.close()

        return outputFile
    }

    // =========================================================================
    // HEADER BANNER (Dark Navy with Cyan Accent Stripe)
    // =========================================================================
    private fun drawHeaderBanner(
        canvas: Canvas,
        orgName: String,
        titleAr: String,
        titleEn: String,
        contactInfo: String
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Dark Navy Banner Background
        paint.color = COLOR_NAVY_DARK
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 82f, paint)

        // Cyan Accent Stripe
        paint.color = COLOR_CYAN_ACCENT
        canvas.drawRect(0f, 82f, PAGE_WIDTH.toFloat(), 85f, paint)

        // Left Side: English Doc Title & Date
        drawText(
            canvas = canvas,
            text = titleEn,
            x = MARGIN_LEFT.toFloat(),
            y = 16f,
            width = 240,
            textSize = 11f,
            textColor = COLOR_CYAN_ACCENT,
            align = Layout.Alignment.ALIGN_NORMAL,
            isBold = true
        )
        val dateStr = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date())
        drawText(
            canvas = canvas,
            text = "DATE: $dateStr",
            x = MARGIN_LEFT.toFloat(),
            y = 34f,
            width = 240,
            textSize = 9f,
            textColor = Color.WHITE,
            align = Layout.Alignment.ALIGN_NORMAL,
            isBold = false
        )

        // Right Side: Arabic Network Name & Arabic Doc Title
        drawText(
            canvas = canvas,
            text = orgName,
            x = 280f,
            y = 14f,
            width = MARGIN_RIGHT - 280,
            textSize = 16f,
            textColor = Color.WHITE,
            align = Layout.Alignment.ALIGN_OPPOSITE,
            isBold = true
        )
        drawText(
            canvas = canvas,
            text = titleAr,
            x = 280f,
            y = 38f,
            width = MARGIN_RIGHT - 280,
            textSize = 13f,
            textColor = COLOR_CYAN_ACCENT,
            align = Layout.Alignment.ALIGN_OPPOSITE,
            isBold = true
        )
        drawText(
            canvas = canvas,
            text = contactInfo,
            x = 280f,
            y = 58f,
            width = MARGIN_RIGHT - 280,
            textSize = 8.5f,
            textColor = 0xFFE2E8F0.toInt(),
            align = Layout.Alignment.ALIGN_OPPOSITE,
            isBold = false
        )
    }

    // =========================================================================
    // STATEMENT DETAILS BOX
    // =========================================================================
    private fun drawStatementDetailsBox(
        canvas: Canvas,
        startY: Float,
        partyName: String,
        partyPhone: String,
        closingBalanceMinor: Long,
        currency: CurrencyCode,
        startDateEpoch: Long?,
        endDateEpoch: Long?
    ): Float {
        val boxHeight = 72f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Background & Border
        paint.color = 0xFFF1F5F9.toInt()
        paint.style = Paint.Style.FILL
        val rect = RectF(MARGIN_LEFT.toFloat(), startY, MARGIN_RIGHT.toFloat(), startY + boxHeight)
        canvas.drawRoundRect(rect, 8f, 8f, paint)

        paint.color = COLOR_BORDER
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(rect, 8f, 8f, paint)

        // Left Box: Highlighted "الرصيد المتبقي" with Period Badge
        val balanceBoxWidth = 190f
        val balanceBoxHeight = 56f
        val balanceBoxRect = RectF(
            MARGIN_LEFT + 8f,
            startY + 8f,
            MARGIN_LEFT + 8f + balanceBoxWidth,
            startY + 8f + balanceBoxHeight
        )
        paint.style = Paint.Style.FILL
        paint.color = if (closingBalanceMinor > 0) 0xFFFEF2F2.toInt() else 0xFFF0FDF4.toInt()
        canvas.drawRoundRect(balanceBoxRect, 6f, 6f, paint)

        paint.style = Paint.Style.STROKE
        paint.color = if (closingBalanceMinor > 0) COLOR_DANGER else COLOR_SUCCESS
        paint.strokeWidth = 1.2f
        canvas.drawRoundRect(balanceBoxRect, 6f, 6f, paint)

        // Balance label & value
        drawText(
            canvas = canvas,
            text = "الرصيد المتبقي (المستحق):",
            x = MARGIN_LEFT + 14f,
            y = startY + 14f,
            width = 175,
            textSize = 10f,
            textColor = if (closingBalanceMinor > 0) COLOR_DANGER else COLOR_SUCCESS,
            align = Layout.Alignment.ALIGN_OPPOSITE,
            isBold = true
        )
        val formattedBalance = Money(closingBalanceMinor, currency).format()
        drawText(
            canvas = canvas,
            text = formattedBalance,
            x = MARGIN_LEFT + 14f,
            y = startY + 34f,
            width = 175,
            textSize = 15f,
            textColor = if (closingBalanceMinor > 0) COLOR_DANGER else COLOR_SUCCESS,
            align = Layout.Alignment.ALIGN_OPPOSITE,
            isBold = true
        )

        // Right Side: Party Details & Period Badge
        val rightX = MARGIN_LEFT + balanceBoxWidth + 20f
        val rightWidth = MARGIN_RIGHT - rightX.toInt() - 10

        drawText(
            canvas = canvas,
            text = "الوكيل / العميل: $partyName",
            x = rightX,
            y = startY + 12f,
            width = rightWidth,
            textSize = 13f,
            textColor = COLOR_TEXT_PRIMARY,
            align = Layout.Alignment.ALIGN_OPPOSITE,
            isBold = true
        )

        val phoneText = if (partyPhone.isNotBlank()) "رقم الهاتف: $partyPhone" else "رقم الهاتف: غير مسجل"
        drawText(
            canvas = canvas,
            text = phoneText,
            x = rightX,
            y = startY + 32f,
            width = rightWidth,
            textSize = 10.5f,
            textColor = COLOR_TEXT_MUTED,
            align = Layout.Alignment.ALIGN_OPPOSITE,
            isBold = false
        )

        val periodText = if (startDateEpoch != null && endDateEpoch != null) {
            val sDate = LocalDate.ofEpochDay(startDateEpoch)
            val eDate = LocalDate.ofEpochDay(endDateEpoch)
            "الفترة: من $sDate إلى $eDate"
        } else {
            "الفترة: كافة الحركات المسجلة بالأستاذ العام"
        }
        drawText(
            canvas = canvas,
            text = periodText,
            x = rightX,
            y = startY + 49f,
            width = rightWidth,
            textSize = 9.5f,
            textColor = COLOR_NAVY_MEDIUM,
            align = Layout.Alignment.ALIGN_OPPOSITE,
            isBold = false
        )

        return startY + boxHeight
    }

    // =========================================================================
    // INVOICE DETAILS BOX
    // =========================================================================
    private fun drawInvoiceDetailsBox(
        canvas: Canvas,
        startY: Float,
        invoice: DocumentEntity,
        party: PartyEntity?,
        paidAmountMinor: Long
    ): Float {
        val boxHeight = 76f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.color = 0xFFF1F5F9.toInt()
        paint.style = Paint.Style.FILL
        val rect = RectF(MARGIN_LEFT.toFloat(), startY, MARGIN_RIGHT.toFloat(), startY + boxHeight)
        canvas.drawRoundRect(rect, 8f, 8f, paint)

        paint.color = COLOR_BORDER
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(rect, 8f, 8f, paint)

        val isPaid = paidAmountMinor >= invoice.totalMinor && invoice.status != "VOIDED"
        val isPartial = paidAmountMinor > 0L && paidAmountMinor < invoice.totalMinor && invoice.status != "VOIDED"
        val isVoided = invoice.status == "VOIDED"

        val statusText = when {
            isVoided -> "ملغية بقيد عكسي"
            isPaid -> "خالص (مدفوعة نقداً)"
            isPartial -> "دفعة جزئية"
            else -> "آجل غير مسدد"
        }
        val statusColor = when {
            isVoided -> COLOR_DANGER
            isPaid -> COLOR_SUCCESS
            isPartial -> COLOR_WARNING
            else -> COLOR_DANGER
        }

        // Left Side: Status Badge & Total
        val leftBoxWidth = 185f
        val leftBoxRect = RectF(MARGIN_LEFT + 8f, startY + 8f, MARGIN_LEFT + 8f + leftBoxWidth, startY + boxHeight - 8f)
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        canvas.drawRoundRect(leftBoxRect, 6f, 6f, paint)

        paint.style = Paint.Style.STROKE
        paint.color = statusColor
        paint.strokeWidth = 1.2f
        canvas.drawRoundRect(leftBoxRect, 6f, 6f, paint)

        drawText(
            canvas = canvas,
            text = "حالة الفاتورة: $statusText",
            x = MARGIN_LEFT + 14f,
            y = startY + 14f,
            width = 170,
            textSize = 9.5f,
            textColor = statusColor,
            align = Layout.Alignment.ALIGN_OPPOSITE,
            isBold = true
        )

        val totalFormatted = Money(invoice.totalMinor, CurrencyCode.fromString(invoice.currency)).format()
        drawText(
            canvas = canvas,
            text = totalFormatted,
            x = MARGIN_LEFT + 14f,
            y = startY + 34f,
            width = 170,
            textSize = 15f,
            textColor = COLOR_NAVY_DARK,
            align = Layout.Alignment.ALIGN_OPPOSITE,
            isBold = true
        )

        // Right Side: Client Name, Phone, Sequential Invoice Badge
        val rightX = MARGIN_LEFT + leftBoxWidth + 18f
        val rightWidth = MARGIN_RIGHT - rightX.toInt() - 10

        val formattedDocNum = "#INV-${invoice.fiscalYear}-${invoice.docNumber.toString().padStart(4, '0')}"
        drawText(
            canvas = canvas,
            text = "العميل: ${party?.name ?: "عميل نقدي"}",
            x = rightX,
            y = startY + 12f,
            width = rightWidth,
            textSize = 13f,
            textColor = COLOR_TEXT_PRIMARY,
            align = Layout.Alignment.ALIGN_OPPOSITE,
            isBold = true
        )

        drawText(
            canvas = canvas,
            text = "رقم الفاتورة: $formattedDocNum",
            x = rightX,
            y = startY + 32f,
            width = rightWidth,
            textSize = 10.5f,
            textColor = COLOR_CYAN_ACCENT.toInt().let { COLOR_NAVY_MEDIUM },
            align = Layout.Alignment.ALIGN_OPPOSITE,
            isBold = true
        )

        val dateStr = "التاريخ: ${LocalDate.ofEpochDay(invoice.dateEpochDay)}"
        drawText(
            canvas = canvas,
            text = if (party?.phone.isNullOrBlank()) dateStr else "$dateStr | هاتف: ${party?.phone}",
            x = rightX,
            y = startY + 50f,
            width = rightWidth,
            textSize = 9.5f,
            textColor = COLOR_TEXT_MUTED,
            align = Layout.Alignment.ALIGN_OPPOSITE,
            isBold = false
        )

        return startY + boxHeight
    }

    // =========================================================================
    // STATEMENT TABLE: HEADERS & ROWS
    // =========================================================================
    // Columns: التاريخ (65) | البيان ونوع الحركة (188) | مدين (85) | دائن (85) | الرصيد المتبقي (100) = 523
    private const val COL_DATE_W = 65f
    private const val COL_DESC_W = 188f
    private const val COL_DEBIT_W = 85f
    private const val COL_CREDIT_W = 85f
    private const val COL_BAL_W = 100f

    private fun drawStatementTableHeaders(canvas: Canvas, startY: Float): Float {
        val h = 26f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.color = COLOR_NAVY_MEDIUM
        paint.style = Paint.Style.FILL
        canvas.drawRect(MARGIN_LEFT.toFloat(), startY, MARGIN_RIGHT.toFloat(), startY + h, paint)

        // Draw Headers Right to Left
        var curX = MARGIN_RIGHT.toFloat()

        // 1. التاريخ
        drawHeaderCell(canvas, "التاريخ", curX - COL_DATE_W, startY, COL_DATE_W, h)
        curX -= COL_DATE_W

        // 2. البيان ونوع الحركة
        drawHeaderCell(canvas, "البيان ونوع الحركة", curX - COL_DESC_W, startY, COL_DESC_W, h)
        curX -= COL_DESC_W

        // 3. مدين (عليها)
        drawHeaderCell(canvas, "مدين (عليها)", curX - COL_DEBIT_W, startY, COL_DEBIT_W, h)
        curX -= COL_DEBIT_W

        // 4. دائن (مسدد)
        drawHeaderCell(canvas, "دائن (مسدد)", curX - COL_CREDIT_W, startY, COL_CREDIT_W, h)
        curX -= COL_CREDIT_W

        // 5. الرصيد المتبقي
        drawHeaderCell(canvas, "الرصيد المتبقي", curX - COL_BAL_W, startY, COL_BAL_W, h)

        return startY + h
    }

    private fun drawHeaderCell(canvas: Canvas, title: String, x: Float, y: Float, width: Float, height: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = 0xFF334155.toInt()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.5f
        canvas.drawRect(x, y, x + width, y + height, paint)

        drawText(
            canvas = canvas,
            text = title,
            x = x + 3f,
            y = y + 6f,
            width = (width - 6).toInt(),
            textSize = 9.5f,
            textColor = Color.WHITE,
            align = Layout.Alignment.ALIGN_CENTER,
            isBold = true
        )
    }

    private fun drawStatementRow(
        canvas: Canvas,
        startY: Float,
        item: com.example.domain.usecase.StatementItem,
        isAlt: Boolean
    ): Float {
        val h = 24f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.color = if (isAlt) COLOR_ROW_ALT else Color.WHITE
        paint.style = Paint.Style.FILL
        canvas.drawRect(MARGIN_LEFT.toFloat(), startY, MARGIN_RIGHT.toFloat(), startY + h, paint)

        paint.color = COLOR_BORDER
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.5f
        canvas.drawRect(MARGIN_LEFT.toFloat(), startY, MARGIN_RIGHT.toFloat(), startY + h, paint)

        var curX = MARGIN_RIGHT.toFloat()

        // 1. Date
        val dateStr = LocalDate.ofEpochDay(item.entryDateEpochDay).toString()
        drawCellText(canvas, dateStr, curX - COL_DATE_W, startY, COL_DATE_W, h, COLOR_TEXT_MUTED, isMonospace = true)
        curX -= COL_DATE_W

        // 2. Memo / Movement Type
        val docTypeAr = when (item.docType) {
            "SALES_INVOICE" -> "فاتورة مبيعات #${item.docNumber}"
            "CUSTOMER_RECEIPT" -> "سند قبض سداد #${item.docNumber}"
            "CREDIT_NOTE" -> "إشعار دائن مردودات #${item.docNumber}"
            else -> item.memo.ifBlank { "قيد يومية #${item.docNumber}" }
        }
        val fullDesc = if (item.memo.isNotBlank() && item.memo != docTypeAr) "$docTypeAr - ${item.memo}" else docTypeAr
        drawCellText(canvas, fullDesc, curX - COL_DESC_W, startY, COL_DESC_W, h, COLOR_TEXT_PRIMARY, align = Layout.Alignment.ALIGN_OPPOSITE)
        curX -= COL_DESC_W

        // 3. Debit (مدين)
        val debitText = if (item.baseDebitMinor > 0) "${item.baseDebitMinor / 100L}" else "-"
        drawCellText(canvas, debitText, curX - COL_DEBIT_W, startY, COL_DEBIT_W, h, if (item.baseDebitMinor > 0) COLOR_DANGER else COLOR_TEXT_MUTED, isMonospace = true)
        curX -= COL_DEBIT_W

        // 4. Credit (دائن)
        val creditText = if (item.baseCreditMinor > 0) "${item.baseCreditMinor / 100L}" else "-"
        drawCellText(canvas, creditText, curX - COL_CREDIT_W, startY, COL_CREDIT_W, h, if (item.baseCreditMinor > 0) COLOR_SUCCESS else COLOR_TEXT_MUTED, isMonospace = true)
        curX -= COL_CREDIT_W

        // 5. Running Balance (الرصيد)
        val balText = "${item.runningBalanceMinor / 100L} ر.ي"
        drawCellText(canvas, balText, curX - COL_BAL_W, startY, COL_BAL_W, h, COLOR_NAVY_DARK, isBold = true, isMonospace = true)

        return startY + h
    }

    // =========================================================================
    // INVOICE TABLE: HEADERS & ROWS
    // =========================================================================
    // Columns: م (35) | البيان وصنف الكرت (228) | الكمية (70) | سعر الوحدة (95) | الإجمالي (95) = 523
    private const val INV_COL_NUM_W = 35f
    private const val INV_COL_NAME_W = 228f
    private const val INV_COL_QTY_W = 70f
    private const val INV_COL_PRICE_W = 95f
    private const val INV_COL_TOTAL_W = 95f

    private fun drawInvoiceTableHeaders(canvas: Canvas, startY: Float): Float {
        val h = 26f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.color = COLOR_NAVY_MEDIUM
        paint.style = Paint.Style.FILL
        canvas.drawRect(MARGIN_LEFT.toFloat(), startY, MARGIN_RIGHT.toFloat(), startY + h, paint)

        var curX = MARGIN_RIGHT.toFloat()

        // 1. م
        drawHeaderCell(canvas, "م", curX - INV_COL_NUM_W, startY, INV_COL_NUM_W, h)
        curX -= INV_COL_NUM_W

        // 2. صنف الكرت والبيان
        drawHeaderCell(canvas, "صنف الكرت والبيان", curX - INV_COL_NAME_W, startY, INV_COL_NAME_W, h)
        curX -= INV_COL_NAME_W

        // 3. الكمية
        drawHeaderCell(canvas, "الكمية (كرت)", curX - INV_COL_QTY_W, startY, INV_COL_QTY_W, h)
        curX -= INV_COL_QTY_W

        // 4. سعر الوحدة
        drawHeaderCell(canvas, "سعر الكرت", curX - INV_COL_PRICE_W, startY, INV_COL_PRICE_W, h)
        curX -= INV_COL_PRICE_W

        // 5. الإجمالي
        drawHeaderCell(canvas, "الإجمالي (ر.ي)", curX - INV_COL_TOTAL_W, startY, INV_COL_TOTAL_W, h)

        return startY + h
    }

    private fun drawInvoiceRow(
        canvas: Canvas,
        startY: Float,
        index: Int,
        item: DocumentItemEntity,
        currency: CurrencyCode,
        isAlt: Boolean
    ): Float {
        val h = 26f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.color = if (isAlt) COLOR_ROW_ALT else Color.WHITE
        paint.style = Paint.Style.FILL
        canvas.drawRect(MARGIN_LEFT.toFloat(), startY, MARGIN_RIGHT.toFloat(), startY + h, paint)

        paint.color = COLOR_BORDER
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.5f
        canvas.drawRect(MARGIN_LEFT.toFloat(), startY, MARGIN_RIGHT.toFloat(), startY + h, paint)

        var curX = MARGIN_RIGHT.toFloat()

        // 1. Index
        drawCellText(canvas, index.toString(), curX - INV_COL_NUM_W, startY, INV_COL_NUM_W, h, COLOR_TEXT_MUTED)
        curX -= INV_COL_NUM_W

        // 2. Name
        val desc = item.description.ifBlank { "كروت إنترنت" }
        drawCellText(canvas, desc, curX - INV_COL_NAME_W, startY, INV_COL_NAME_W, h, COLOR_TEXT_PRIMARY, align = Layout.Alignment.ALIGN_OPPOSITE, isBold = true)
        curX -= INV_COL_NAME_W

        // 3. Qty
        drawCellText(canvas, "${item.quantity} كرت", curX - INV_COL_QTY_W, startY, INV_COL_QTY_W, h, COLOR_NAVY_DARK, isMonospace = true)
        curX -= INV_COL_QTY_W

        // 4. Unit Price
        val unitPriceStr = Money(item.unitPriceMinor, currency).format()
        drawCellText(canvas, unitPriceStr, curX - INV_COL_PRICE_W, startY, INV_COL_PRICE_W, h, COLOR_TEXT_MUTED, isMonospace = true)
        curX -= INV_COL_PRICE_W

        // 5. Total
        val totalStr = Money(item.totalMinor, currency).format()
        drawCellText(canvas, totalStr, curX - INV_COL_TOTAL_W, startY, INV_COL_TOTAL_W, h, COLOR_NAVY_DARK, isBold = true, isMonospace = true)

        return startY + h
    }

    private fun drawCellText(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        color: Int,
        align: Layout.Alignment = Layout.Alignment.ALIGN_CENTER,
        isBold: Boolean = false,
        isMonospace: Boolean = false
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = COLOR_BORDER
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.5f
        canvas.drawRect(x, y, x + width, y + height, paint)

        drawText(
            canvas = canvas,
            text = text,
            x = x + 3f,
            y = y + (height - 14f) / 2f,
            width = (width - 6).toInt(),
            textSize = 9.5f,
            textColor = color,
            align = align,
            isBold = isBold,
            isMonospace = isMonospace
        )
    }

    // =========================================================================
    // SUMMARY CARDS
    // =========================================================================
    private fun drawStatementSummaryCard(
        canvas: Canvas,
        startY: Float,
        totalDebit: Long,
        totalCredit: Long,
        netBalance: Long,
        currency: CurrencyCode
    ): Float {
        val h = 48f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.color = 0xFFF1F5F9.toInt()
        paint.style = Paint.Style.FILL
        val rect = RectF(MARGIN_LEFT.toFloat(), startY, MARGIN_RIGHT.toFloat(), startY + h)
        canvas.drawRoundRect(rect, 6f, 6f, paint)

        paint.color = COLOR_BORDER
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(rect, 6f, 6f, paint)

        val colWidth = CONTENT_WIDTH / 3f

        // Column 1: Total Sales (Debit)
        drawSummaryColumn(
            canvas = canvas,
            x = MARGIN_RIGHT - colWidth,
            y = startY,
            width = colWidth,
            title = "إجمالي المسحوبات (مدين):",
            value = Money(totalDebit, currency).format(),
            color = COLOR_TEXT_PRIMARY
        )

        // Column 2: Total Paid (Credit)
        drawSummaryColumn(
            canvas = canvas,
            x = MARGIN_RIGHT - colWidth * 2f,
            y = startY,
            width = colWidth,
            title = "إجمالي السداد (دائن):",
            value = Money(totalCredit, currency).format(),
            color = COLOR_SUCCESS
        )

        // Column 3: Final Net Balance
        drawSummaryColumn(
            canvas = canvas,
            x = MARGIN_LEFT.toFloat(),
            y = startY,
            width = colWidth,
            title = "صافي الرصيد النهائي:",
            value = Money(netBalance, currency).format(),
            color = if (netBalance > 0) COLOR_DANGER else COLOR_SUCCESS,
            isHighlight = true
        )

        return startY + h
    }

    private fun drawInvoiceSummaryCard(
        canvas: Canvas,
        startY: Float,
        totalMinor: Long,
        paidMinor: Long,
        currency: CurrencyCode
    ): Float {
        val h = 48f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.color = 0xFFF1F5F9.toInt()
        paint.style = Paint.Style.FILL
        val rect = RectF(MARGIN_LEFT.toFloat(), startY, MARGIN_RIGHT.toFloat(), startY + h)
        canvas.drawRoundRect(rect, 6f, 6f, paint)

        paint.color = COLOR_BORDER
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(rect, 6f, 6f, paint)

        val colWidth = CONTENT_WIDTH / 3f
        val remainingMinor = (totalMinor - paidMinor).coerceAtLeast(0L)

        drawSummaryColumn(
            canvas = canvas,
            x = MARGIN_RIGHT - colWidth,
            y = startY,
            width = colWidth,
            title = "إجمالي الفاتورة:",
            value = Money(totalMinor, currency).format(),
            color = COLOR_NAVY_DARK
        )

        drawSummaryColumn(
            canvas = canvas,
            x = MARGIN_RIGHT - colWidth * 2f,
            y = startY,
            width = colWidth,
            title = "المبلغ المسدد / المحصل:",
            value = Money(paidMinor, currency).format(),
            color = COLOR_SUCCESS
        )

        drawSummaryColumn(
            canvas = canvas,
            x = MARGIN_LEFT.toFloat(),
            y = startY,
            width = colWidth,
            title = "المتبقي (آجل):",
            value = Money(remainingMinor, currency).format(),
            color = if (remainingMinor > 0) COLOR_DANGER else COLOR_SUCCESS,
            isHighlight = true
        )

        return startY + h
    }

    private fun drawSummaryColumn(
        canvas: Canvas,
        x: Float,
        y: Float,
        width: Float,
        title: String,
        value: String,
        color: Int,
        isHighlight: Boolean = false
    ) {
        drawText(
            canvas = canvas,
            text = title,
            x = x + 4f,
            y = y + 8f,
            width = (width - 8).toInt(),
            textSize = 9.5f,
            textColor = COLOR_TEXT_MUTED,
            align = Layout.Alignment.ALIGN_CENTER,
            isBold = false
        )

        drawText(
            canvas = canvas,
            text = value,
            x = x + 4f,
            y = y + 24f,
            width = (width - 8).toInt(),
            textSize = if (isHighlight) 13f else 11.5f,
            textColor = color,
            align = Layout.Alignment.ALIGN_CENTER,
            isBold = true
        )
    }

    // =========================================================================
    // OFFICIAL SIGN-OFF FOOTER
    // =========================================================================
    private fun drawOfficialSignOffFooter(
        canvas: Canvas,
        startY: Float,
        firstPartyLabel: String,
        secondPartyLabel: String
    ) {
        val boxWidth = 220f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Balanced Signature 1: Right Side (إدارة الشبكة)
        val rightX = MARGIN_RIGHT - boxWidth
        drawText(
            canvas = canvas,
            text = firstPartyLabel,
            x = rightX,
            y = startY,
            width = boxWidth.toInt(),
            textSize = 10.5f,
            textColor = COLOR_NAVY_DARK,
            align = Layout.Alignment.ALIGN_CENTER,
            isBold = true
        )
        paint.color = COLOR_BORDER
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.pathEffect = DashPathEffect(floatArrayOf(4f, 4f), 0f)
        canvas.drawLine(rightX + 15f, startY + 45f, rightX + boxWidth - 15f, startY + 45f, paint)

        // Balanced Signature 2: Left Side (الوكيل / المستلم)
        val leftX = MARGIN_LEFT.toFloat()
        drawText(
            canvas = canvas,
            text = secondPartyLabel,
            x = leftX,
            y = startY,
            width = boxWidth.toInt(),
            textSize = 10.5f,
            textColor = COLOR_NAVY_DARK,
            align = Layout.Alignment.ALIGN_CENTER,
            isBold = true
        )
        canvas.drawLine(leftX + 15f, startY + 45f, leftX + boxWidth - 15f, startY + 45f, paint)
        paint.pathEffect = null
    }

    private fun drawPageFooter(canvas: Canvas, pageNumber: Int) {
        val y = PAGE_HEIGHT - 26f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = COLOR_BORDER
        paint.strokeWidth = 0.5f
        canvas.drawLine(MARGIN_LEFT.toFloat(), y, MARGIN_RIGHT.toFloat(), y, paint)

        drawText(
            canvas = canvas,
            text = "تم الإصدار آلياً عبر نظام SamMikrotik المحاسبي السحابي | صفحة $pageNumber",
            x = MARGIN_LEFT.toFloat(),
            y = y + 6f,
            width = CONTENT_WIDTH,
            textSize = 8f,
            textColor = COLOR_TEXT_MUTED,
            align = Layout.Alignment.ALIGN_CENTER,
            isBold = false
        )
    }

    // =========================================================================
    // STATICLAYOUT ARABIC TEXT RENDERING HELPER
    // =========================================================================
    private fun drawText(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        width: Int,
        textSize: Float,
        textColor: Int,
        align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
        isBold: Boolean = false,
        isMonospace: Boolean = false
    ) {
        if (text.isBlank() || width <= 0) return

        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            this.textSize = textSize
            typeface = when {
                isMonospace -> Typeface.create(Typeface.MONOSPACE, if (isBold) Typeface.BOLD else Typeface.NORMAL)
                isBold -> Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                else -> Typeface.DEFAULT
            }
        }

        val layout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, width)
            .setAlignment(align)
            .setTextDirection(TextDirectionHeuristics.ANYRTL_LTR)
            .setLineSpacing(0f, 1f)
            .setIncludePad(false)
            .build()

        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
    }

    // =========================================================================
    // PDF RENDERING & SYSTEM DISPATCH UTILITIES
    // =========================================================================
    /**
     * Renders all pages of a PDF file to high-resolution Bitmaps for in-app preview.
     */
    fun renderPdfPages(file: File): List<Bitmap> {
        val bitmaps = mutableListOf<Bitmap>()
        try {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                // Fill background white
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmaps.add(bitmap)
                page.close()
            }
            renderer.close()
            pfd.close()
        } catch (_: Exception) {
            // Fallback gracefully on Robolectric or environments without full native PDF renderer
        }
        return bitmaps
    }

    /**
     * Launches external PDF viewer for the generated document.
     */
    fun openPdfFile(context: Context, file: File) {
        try {
            val authority = "${context.packageName}.fileprovider"
            val uri: Uri = FileProvider.getUriForFile(context, authority, file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "عرض مستند PDF عبر"))
        } catch (e: Exception) {
            Toast.makeText(context, "تعذر فتح ملف PDF: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Dispatches the PDF file via Android Share Chooser.
     */
    fun sharePdfFile(context: Context, file: File, caption: String = "") {
        try {
            val authority = "${context.packageName}.fileprovider"
            val uri: Uri = FileProvider.getUriForFile(context, authority, file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                if (caption.isNotBlank()) {
                    putExtra(Intent.EXTRA_TEXT, caption)
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "مشاركة ملف PDF عبر"))
        } catch (e: Exception) {
            Toast.makeText(context, "تعذر مشاركة ملف PDF: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}
