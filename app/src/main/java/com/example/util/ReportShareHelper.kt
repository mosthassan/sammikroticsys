package com.example.util

import com.example.domain.report.*

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object ReportShareHelper {

    fun shareReportPdf(
        context: Context,
        table: ReportTable,
        footer: ReportFooter? = null,
        title: String = "مشاركة التقرير"
    ) {
        val fileName = "sammikrotik_report_${System.currentTimeMillis()}.pdf"
        val file = ReportPdfExporter.exportToFile(context, table, footer, fileName)
        shareFile(context, file, "application/pdf", title)
    }

    fun shareReportCsv(
        context: Context,
        table: ReportTable,
        title: String = "مشاركة التقرير كملف CSV"
    ) {
        val bytes = ReportCsvExporter.exportToBytes(table)
        val fileName = "sammikrotik_report_${System.currentTimeMillis()}.csv"
        val file = File(context.cacheDir, fileName)
        FileOutputStream(file).use { it.write(bytes) }
        shareFile(context, file, "text/csv", title)
    }

    private fun shareFile(
        context: Context,
        file: File,
        mimeType: String,
        chooserTitle: String
    ) {
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, file)

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
