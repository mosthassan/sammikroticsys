package com.example.shadows

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import java.io.OutputStream

@Implements(PdfDocument::class)
class ShadowPdfDocument {
    private var currentPage: PdfDocument.Page? = null
    private val pages = mutableListOf<PdfDocument.PageInfo>()

    @Implementation
    fun startPage(pageInfo: PdfDocument.PageInfo): PdfDocument.Page {
        val constructor = PdfDocument.Page::class.java.getDeclaredConstructor(
            Canvas::class.java,
            PdfDocument.PageInfo::class.java
        )
        constructor.isAccessible = true
        val width = pageInfo.pageWidth.coerceAtLeast(1)
        val height = pageInfo.pageHeight.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val page = constructor.newInstance(canvas, pageInfo)
        currentPage = page
        pages.add(pageInfo)
        return page
    }

    @Implementation
    fun finishPage(page: PdfDocument.Page) {
        val method = PdfDocument.Page::class.java.getDeclaredMethod("finish")
        method.isAccessible = true
        method.invoke(page)
        currentPage = null
    }

    @Implementation
    fun writeTo(out: OutputStream) {
        out.write("%PDF-1.4\n%SamMikrotik Generated PDF\n%%EOF\n".toByteArray(Charsets.UTF_8))
        out.flush()
    }

    @Implementation
    fun close() {
        currentPage = null
    }

    @Implementation
    fun getPages(): List<PdfDocument.PageInfo> {
        return pages
    }
}
