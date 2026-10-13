package com.example.domain.report

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object ReportHashCalculator {

    fun computeSha256(table: ReportTable): String {
        val sb = StringBuilder()
        sb.append("TITLE=").append(table.title).append("\n")
        sb.append("SUBTITLE=").append(table.subtitle).append("\n")
        sb.append("COLS=")
        table.columns.forEach { col ->
            sb.append(col.id).append(":").append(col.title).append(":").append(col.alignment.name).append("|")
        }
        sb.append("\nROWS=\n")
        table.rows.forEach { row ->
            sb.append(row.type.name).append("::")
            row.cells.forEach { cell ->
                when (cell) {
                    is ReportCell.MoneyCell -> sb.append("M:").append(cell.amountMinor).append(":").append(cell.currency.name)
                    is ReportCell.TextCell -> sb.append("T:").append(cell.text).append(":").append(cell.isBold)
                    is ReportCell.PercentCell -> sb.append("P:").append(cell.basisPoints).append(":").append(cell.isNegative)
                    is ReportCell.CountCell -> sb.append("C:").append(cell.count)
                    is ReportCell.DateCell -> sb.append("D:").append(cell.epochDay)
                }
                sb.append(";")
            }
            sb.append("\n")
        }

        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(sb.toString().toByteArray(StandardCharsets.UTF_8))
        val hexString = StringBuilder(hashBytes.size * 2)
        for (b in hashBytes) {
            val hex = Integer.toHexString(0xff and b.toInt())
            if (hex.length == 1) hexString.append('0')
            hexString.append(hex)
        }
        return hexString.toString()
    }
}
