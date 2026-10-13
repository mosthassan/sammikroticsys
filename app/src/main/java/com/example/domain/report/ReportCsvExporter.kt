package com.example.domain.report

import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.time.LocalDate

object ReportCsvExporter {

    private val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

    fun exportToBytes(table: ReportTable): ByteArray {
        val baos = ByteArrayOutputStream()
        baos.write(UTF8_BOM)

        val writer = OutputStreamWriter(baos, StandardCharsets.UTF_8)

        // Title and Subtitle
        writer.write(escapeCsv(table.title))
        writer.write("\r\n")
        if (table.subtitle.isNotBlank()) {
            writer.write(escapeCsv(table.subtitle))
            writer.write("\r\n")
        }
        writer.write("\r\n")

        // Header Columns
        val headerRow = table.columns.joinToString(",") { escapeCsv(it.title) }
        writer.write(headerRow)
        writer.write("\r\n")

        // Rows
        for (row in table.rows) {
            val rowStr = row.cells.joinToString(",") { cell ->
                escapeCsv(formatCellForCsv(cell))
            }
            writer.write(rowStr)
            writer.write("\r\n")
        }

        writer.flush()
        return baos.toByteArray()
    }

    private fun formatCellForCsv(cell: ReportCell): String {
        return when (cell) {
            is ReportCell.MoneyCell -> formatMoneyDecimal(cell.amountMinor)
            is ReportCell.TextCell -> cell.text
            is ReportCell.PercentCell -> cell.formatPercentText()
            is ReportCell.CountCell -> cell.count.toString()
            is ReportCell.DateCell -> LocalDate.ofEpochDay(cell.epochDay).toString()
        }
    }

    /**
     * Pure integer decimal formatting.
     * e.g. 10050L -> "100.50", -25L -> "-0.25", 0L -> "0.00"
     */
    fun formatMoneyDecimal(minor: Long): String {
        val isNegative = minor < 0L
        val absMinor = if (minor < 0L) -minor else minor
        val majorPart = absMinor / 100L
        val fractionPart = absMinor % 100L
        val fracStr = if (fractionPart < 10L) "0$fractionPart" else "$fractionPart"
        return if (isNegative) "-$majorPart.$fracStr" else "$majorPart.$fracStr"
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }
}
