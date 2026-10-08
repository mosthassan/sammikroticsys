package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object WhatsAppDispatcher {

    /**
     * Sanitizes and intelligently normalizes local and international phone numbers.
     * Specifically formats 9-digit Yemeni mobile numbers (starting with 7) to +967 international standard.
     */
    fun sanitizePhoneNumber(raw: String): String {
        // Strip everything except digits and leading '+'
        val trimmed = raw.trim()
        val hasPlus = trimmed.startsWith("+")
        val digitsOnly = trimmed.filter { it.isDigit() }

        if (digitsOnly.isEmpty()) return ""

        return when {
            // Already has country code 967
            digitsOnly.startsWith("967") && digitsOnly.length == 12 -> "+$digitsOnly"
            // Has 00967
            digitsOnly.startsWith("00967") && digitsOnly.length == 14 -> "+${digitsOnly.removePrefix("00")}"
            // 9-digit local Yemeni phone number (starts with 7xx xxx xxx)
            digitsOnly.length == 9 && digitsOnly.startsWith("7") -> "+967$digitsOnly"
            // 10-digit with leading 0 (07xx xxx xxx)
            digitsOnly.length == 10 && digitsOnly.startsWith("07") -> "+967${digitsOnly.substring(1)}"
            // Other standard numbers: preserve international '+' or return clean digits
            hasPlus -> "+$digitsOnly"
            else -> digitsOnly
        }
    }

    /**
     * Extracts digits for wa.me URL scheme (wa.me expects digits without '+').
     */
    fun getCleanDigitsForWhatsApp(phone: String): String {
        val sanitized = sanitizePhoneNumber(phone)
        return sanitized.filter { it.isDigit() }
    }

    /**
     * Dispatches a plain text message or invoice summary via WhatsApp.
     * Falls back to general share chooser if WhatsApp is not installed.
     */
    fun sendTextMessage(
        context: Context,
        phoneNumber: String,
        message: String
    ) {
        val cleanDigits = getCleanDigitsForWhatsApp(phoneNumber)
        val encodedText = try {
            URLEncoder.encode(message, StandardCharsets.UTF_8.name())
        } catch (_: Exception) {
            message
        }

        try {
            val uri = if (cleanDigits.isNotBlank()) {
                Uri.parse("https://wa.me/$cleanDigits?text=$encodedText")
            } else {
                Uri.parse("https://api.whatsapp.com/send?text=$encodedText")
            }

            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // Check if WhatsApp can handle it, otherwise fallback
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                // Fallback to browser or generic VIEW intent
                val fallbackIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
            }
        } catch (e: Exception) {
            // Final fallback: standard share sheet
            try {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, message)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(shareIntent, "إرسال عبر").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (err: Exception) {
                Toast.makeText(context, "تعذر فتح تطبيق واتساب أو المشاركة", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Dispatches a document (such as PDF statement or invoice) via WhatsApp with FileProvider.
     * Combines FileProvider URI with package com.whatsapp or falls back to general share chooser.
     */
    fun sendDocument(
        context: Context,
        phoneNumber: String,
        file: File,
        caption: String? = null,
        mimeType: String = "application/pdf"
    ) {
        try {
            if (!file.exists()) {
                Toast.makeText(context, "الملف غير موجود للإرسال", Toast.LENGTH_SHORT).show()
                return
            }

            val authority = "${context.packageName}.fileprovider"
            val contentUri: Uri = FileProvider.getUriForFile(context, authority, file)

            val cleanDigits = getCleanDigitsForWhatsApp(phoneNumber)

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, contentUri)
                if (!caption.isNullOrBlank()) {
                    putExtra(Intent.EXTRA_TEXT, caption)
                }
                if (cleanDigits.isNotBlank()) {
                    // WhatsApp JID format for specific contact dispatch
                    putExtra("jid", "$cleanDigits@s.whatsapp.net")
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                setPackage("com.whatsapp")
            }

            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                // Fallback to generic chooser
                val fallbackIntent = Intent(Intent.ACTION_SEND).apply {
                    type = mimeType
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    if (!caption.isNullOrBlank()) {
                        putExtra(Intent.EXTRA_TEXT, caption)
                    }
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(fallbackIntent, "مشاركة المستند عبر").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
        } catch (e: Exception) {
            Toast.makeText(context, "تعذر مشاركة المستند: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}
