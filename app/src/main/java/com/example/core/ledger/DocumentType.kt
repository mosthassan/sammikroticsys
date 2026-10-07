package com.example.core.ledger

enum class DocumentType(val codePrefix: String, val arabicName: String) {
    SALES_INVOICE("INV", "فاتورة مبيعات"),
    RECEIPT_VOUCHER("RCV", "سند قبض"),
    CREDIT_NOTE("CRN", "إشعار دائن"),
    PURCHASE_INVOICE("PINV", "فاتورة مشتريات"),
    PAYMENT_VOUCHER("PAY", "سند صرف"),
    TREASURY_TRANSFER("TRF", "تحويل صناديق"),
    DEPRECIATION_RUN("DEP", "قيد إهلاك"),
    OPENING_BALANCE("OPN", "رصيد افتتاحي"),
    DIVIDEND_DISTRIBUTION("DIV", "توزيع أرباح"),
    CLOSING_ENTRY("CLS", "قيد إقفال")
}

enum class DocumentStatus(val arabicName: String) {
    DRAFT("مسودة"),
    POSTED("مرحَّل"),
    VOIDED("ملغي")
}

enum class JournalEntryType(val arabicName: String) {
    NORMAL("اعتيادي"),
    REVERSAL("عكسي"),
    CLOSING("إقفال سنوي")
}
