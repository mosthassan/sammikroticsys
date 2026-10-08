package com.example.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.local.entity.PartyEntity
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object WhatsAppDispatcher {

    /**
     * Sanitizes and intelligently normalizes local and international phone numbers.
     * Specifically formats 9-digit Yemeni mobile numbers (starting with 7) to +967 international standard.
     */
    fun sanitizePhoneNumber(raw: String): String {
        val trimmed = raw.trim()
        val hasPlus = trimmed.startsWith("+")
        // Strip everything except digits
        val digitsOnly = trimmed.filter { it.isDigit() }

        if (digitsOnly.isEmpty()) return ""

        return when {
            // Already has 967 country code with 12 digits (9677xxxxxxxx)
            digitsOnly.startsWith("967") && digitsOnly.length == 12 -> "+$digitsOnly"
            // Has 00967 prefix (009677xxxxxxxx)
            digitsOnly.startsWith("00967") && digitsOnly.length == 14 -> "+${digitsOnly.removePrefix("00")}"
            // 9-digit local Yemeni phone number (starts with 7xx xxx xxx)
            digitsOnly.length == 9 && digitsOnly.startsWith("7") -> "+967$digitsOnly"
            // 10-digit with leading 0 (07xx xxx xxx)
            digitsOnly.length == 10 && digitsOnly.startsWith("07") -> "+967${digitsOnly.substring(1)}"
            // Already starts with plus or international notation
            hasPlus -> "+$digitsOnly"
            // Any other length with 00 prefix
            digitsOnly.startsWith("00") -> "+${digitsOnly.removePrefix("00")}"
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
     * Dispatches a plain text message or invoice summary directly to the party's registered phone via WhatsApp.
     * Plain text receipt dispatch: Intent.ACTION_VIEW targeting https://wa.me/{cleanPhone}?text={urlEncodedSummary}.
     * Falls back gracefully to browser or general share chooser if WhatsApp is not installed.
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

        val urlString = if (cleanDigits.isNotBlank()) {
            "https://wa.me/$cleanDigits?text=$encodedText"
        } else {
            "https://api.whatsapp.com/send?text=$encodedText"
        }

        val uri = Uri.parse(urlString)

        // 1. Try launching WhatsApp directly via package com.whatsapp
        try {
            val waIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(waIntent)
            return
        } catch (_: ActivityNotFoundException) {
            // WhatsApp main app not found, try WhatsApp Business
            try {
                val w4bIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage("com.whatsapp.w4b")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(w4bIntent)
                return
            } catch (_: ActivityNotFoundException) {
                // Neither installed, fall through to browser / generic chooser
            }
        } catch (e: Exception) {
            // General exception fallback
        }

        // 2. Fallback to generic ACTION_VIEW (e.g. Chrome / Web WhatsApp)
        try {
            val browserIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(browserIntent)
        } catch (e: Exception) {
            // 3. Final fallback: standard text share chooser
            try {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, message)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(shareIntent, "مشاركة عبر").apply {
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

            // Try direct WhatsApp dispatch first
            try {
                val waIntent = Intent(Intent.ACTION_SEND).apply {
                    type = mimeType
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    if (!caption.isNullOrBlank()) {
                        putExtra(Intent.EXTRA_TEXT, caption)
                    }
                    if (cleanDigits.isNotBlank()) {
                        putExtra("jid", "$cleanDigits@s.whatsapp.net")
                    }
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    setPackage("com.whatsapp")
                }
                context.startActivity(waIntent)
                return
            } catch (_: ActivityNotFoundException) {
                // Try WhatsApp Business
                try {
                    val w4bIntent = Intent(Intent.ACTION_SEND).apply {
                        type = mimeType
                        putExtra(Intent.EXTRA_STREAM, contentUri)
                        if (!caption.isNullOrBlank()) {
                            putExtra(Intent.EXTRA_TEXT, caption)
                        }
                        if (cleanDigits.isNotBlank()) {
                            putExtra("jid", "$cleanDigits@s.whatsapp.net")
                        }
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        setPackage("com.whatsapp.w4b")
                    }
                    context.startActivity(w4bIntent)
                    return
                } catch (_: ActivityNotFoundException) {
                    // Fall through to chooser
                }
            }

            // Fallback to generic system share chooser
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
        } catch (e: Exception) {
            Toast.makeText(context, "تعذر مشاركة المستند: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Builds and dispatches an invoice receipt directly to a party via WhatsApp.
     */
    fun dispatchInvoiceReceipt(
        context: Context,
        party: PartyEntity?,
        docNumber: Long,
        totalText: String,
        paidText: String,
        remainingText: String,
        itemsSummary: String? = null,
        extraNotes: String? = null
    ) {
        val phone = party?.phone.orEmpty()
        val partyName = party?.name ?: "العميل الكريم"

        val receiptMessage = buildString {
            append("📄 *فاتورة مبيعات كروت إنترنت*\n")
            append("━━━━━━━━━━━━━━━━━━━━\n")
            append("رقم الفاتورة: #$docNumber\n")
            append("العميل: $partyName\n")
            if (!itemsSummary.isNullOrBlank()) {
                append("البنود: $itemsSummary\n")
            }
            append("الإجمالي: $totalText\n")
            append("المدفوع: $paidText\n")
            if (remainingText.isNotBlank() && remainingText != "0 ر.ي" && remainingText != "0.00 YER") {
                append("المتبقي (آجل): $remainingText\n")
            }
            if (!extraNotes.isNullOrBlank()) {
                append("ملاحظات: $extraNotes\n")
            }
            append("━━━━━━━━━━━━━━━━━━━━\n")
            append("✨ شكراً لتعاملكم مع شبكة SamMikrotik")
        }

        sendTextMessage(context, phone, receiptMessage)
    }

    /**
     * Builds and dispatches an account statement summary to a party via WhatsApp.
     */
    fun dispatchStatementSummary(
        context: Context,
        party: PartyEntity,
        openingBalanceText: String,
        closingBalanceText: String,
        transactionCount: Int,
        recentMovementText: String? = null
    ) {
        val message = buildString {
            append("📊 *كشف حساب مالي - SamMikrotik*\n")
            append("━━━━━━━━━━━━━━━━━━━━\n")
            append("الطرف: ${party.name}\n")
            append("الرصيد الافتتاحي: $openingBalanceText\n")
            append("عدد العمليات المقيدة: $transactionCount\n")
            if (!recentMovementText.isNullOrBlank()) {
                append(recentMovementText)
                append("\n")
            }
            append("━━━━━━━━━━━━━━━━━━━━\n")
            append("*الرصيد الجاري المستحق: $closingBalanceText*\n")
            append("تاريخ الإشعار: ${java.time.LocalDate.now()}\n")
            append("نسعد دائماً بخدمتكم.")
        }

        sendTextMessage(context, party.phone, message)
    }
}
