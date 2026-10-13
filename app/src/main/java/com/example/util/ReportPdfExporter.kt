package com.example.util

import com.example.domain.report.*

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.core.model.Money
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate

object ReportPdfExporter {

    private const val A4_PORTRAIT_WIDTH = 595
    private const val A4_PORTRAIT_HEIGHT = 842
    private const val MARGIN = 36

    fun exportToBytes(table: ReportTable, footer: ReportFooter? = null): ByteArray {
        val isLandscape = table.columns.size > 5
        val pageWidth = if (isLandscape) A4_PORTRAIT_HEIGHT else A4_PORTRAIT_WIDTH
        val pageHeight = if (isLandscape) A4_PORTRAIT_WIDTH else A4_PORTRAIT_HEIGHT

        val pdfDoc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = pdfDoc.startPage(pageInfo)
        val canvas = page.canvas

        drawReportPage(canvas, table, footer, pageWidth, pageHeight)

        pdfDoc.finishPage(page)

        val baos = ByteArrayOutputStream()
        pdfDoc.writeTo(baos)
        pdfDoc.close()
        return baos.toByteArray()
    }

    fun exportToFile(context: Context, table: ReportTable, footer: ReportFooter? = null, fileName: String = "report.pdf"): File {
        val bytes = exportToBytes(table, footer)
        val file = File(context.cacheDir, fileName)
        FileOutputStream(file).use { it.write(bytes) }
        return file
    }

    private fun drawReportPage(
        canvas: Canvas,
        table: ReportTable,
        footer: ReportFooter?,
        pageWidth: Int,
        pageHeight: Int
    ) {
        val contentWidth = pageWidth - (MARGIN * 2)

        val titlePaint = Paint().apply {
            color = Color.parseColor("#0F172A")
            textSize = 16f
            isFakeBoldText = true
            textAlign = Paint.Align.RIGHT
        }
        val subPaint = Paint().apply {
            color = Color.parseColor("#64748B")
            textSize = 10f
            textAlign = Paint.Align.RIGHT
        }
        val headerBgPaint = Paint().apply {
            color = Color.parseColor("#0F172A")
        }
        val headerTextPaint = Paint().apply {
            color = Color.WHITE
            textSize = 9f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        val rowBgAltPaint = Paint().apply {
            color = Color.parseColor("#F8FAFC")
        }
        val sectionBgPaint = Paint().apply {
            color = Color.parseColor("#E2E8F0")
        }
        val totalBgPaint = Paint().apply {
            color = Color.parseColor("#E0F2FE")
        }
        val cellTextPaint = Paint().apply {
            color = Color.parseColor("#1E293B")
            textSize = 8.5f
            textAlign = Paint.Align.RIGHT
        }
        val borderPaint = Paint().apply {
            color = Color.parseColor("#CBD5E1")
            strokeWidth = 0.5f
            style = Paint.Style.STROKE
        }
        val footerPaint = Paint().apply {
            color = Color.parseColor("#94A3B8")
            textSize = 7.5f
            textAlign = Paint.Align.RIGHT
        }

        var currentY = MARGIN + 20f

        // Draw Title & Subtitle
        canvas.drawText(table.title, (pageWidth - MARGIN).toFloat(), currentY, titlePaint)
        currentY += 15f
        if (table.subtitle.isNotBlank()) {
            canvas.drawText(table.subtitle, (pageWidth - MARGIN).toFloat(), currentY, subPaint)
            currentY += 15f
        }
        currentY += 10f

        // Calculate Column Widths
        val colCount = table.columns.size.coerceAtLeast(1)
        val colWidth = contentWidth.toFloat() / colCount
        val headerHeight = 22f

        // Draw Column Headers
        canvas.drawRect(
            MARGIN.toFloat(),
            currentY,
            (pageWidth - MARGIN).toFloat(),
            currentY + headerHeight,
            headerBgPaint
        )

        for (i in table.columns.indices) {
            val col = table.columns[i]
            // Right-to-Left order
            val colX = (pageWidth - MARGIN) - (i * colWidth) - (colWidth / 2f)
            canvas.drawText(col.title, colX, currentY + 14f, headerTextPaint)
        }
        currentY += headerHeight

        val rowHeight = 18f
        for ((rIdx, row) in table.rows.withIndex()) {
            if (currentY + rowHeight > pageHeight - MARGIN - 30) {
                break // fit on page
            }

            // Row background
            when (row.type) {
                RowType.SECTION -> canvas.drawRect(MARGIN.toFloat(), currentY, (pageWidth - MARGIN).toFloat(), currentY + rowHeight, sectionBgPaint)
                RowType.TOTAL -> canvas.drawRect(MARGIN.toFloat(), currentY, (pageWidth - MARGIN).toFloat(), currentY + rowHeight, totalBgPaint)
                RowType.SUBTOTAL -> canvas.drawRect(MARGIN.toFloat(), currentY, (pageWidth - MARGIN).toFloat(), currentY + rowHeight, rowBgAltPaint)
                RowType.DATA -> if (rIdx % 2 == 1) canvas.drawRect(MARGIN.toFloat(), currentY, (pageWidth - MARGIN).toFloat(), currentY + rowHeight, rowBgAltPaint)
            }

            // Border line
            canvas.drawLine(MARGIN.toFloat(), currentY + rowHeight, (pageWidth - MARGIN).toFloat(), currentY + rowHeight, borderPaint)

            // Draw Cells
            for (cIdx in row.cells.indices) {
                if (cIdx >= table.columns.size) break
                val cell = row.cells[cIdx]
                val col = table.columns[cIdx]
                val text = when (cell) {
                    is ReportCell.MoneyCell -> Money(cell.amountMinor, cell.currency).format()
                    is ReportCell.TextCell -> cell.text
                    is ReportCell.PercentCell -> cell.formatPercentText()
                    is ReportCell.CountCell -> cell.count.toString()
                    is ReportCell.DateCell -> LocalDate.ofEpochDay(cell.epochDay).toString()
                }

                val rightX = (pageWidth - MARGIN) - (cIdx * colWidth) - 4f
                val centerX = (pageWidth - MARGIN) - (cIdx * colWidth) - (colWidth / 2f)
                val leftX = (pageWidth - MARGIN) - ((cIdx + 1) * colWidth) + 4f

                cellTextPaint.isFakeBoldText = row.type == RowType.TOTAL || row.type == RowType.SECTION || (cell is ReportCell.TextCell && cell.isBold)
                when (col.alignment) {
                    ColumnAlignment.START -> {
                        cellTextPaint.textAlign = Paint.Align.RIGHT
                        canvas.drawText(text, rightX, currentY + 12f, cellTextPaint)
                    }
                    ColumnAlignment.CENTER -> {
                        cellTextPaint.textAlign = Paint.Align.CENTER
                        canvas.drawText(text, centerX, currentY + 12f, cellTextPaint)
                    }
                    ColumnAlignment.END -> {
                        cellTextPaint.textAlign = Paint.Align.LEFT
                        canvas.drawText(text, leftX, currentY + 12f, cellTextPaint)
                    }
                }
            }

            currentY += rowHeight
        }

        // Draw Footer
        footer?.let { f ->
            val footerY = (pageHeight - MARGIN).toFloat()
            val footerText = "بصمة التقرير SHA-256: ${f.contentHashSha256.take(24)}... | وقت الإصدار: ${f.issuedAtText} | نظام SamMikrotik المحاسبي"
            canvas.drawText(footerText, (pageWidth - MARGIN).toFloat(), footerY, footerPaint)
        }
    }
}
