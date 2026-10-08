package com.example.core.model

/**
 * Partner Equity Share calculation mode (IFRS compliant).
 * DERIVED_FROM_CAPITAL: Partner share % is calculated dynamically from the ledger:
 *   (Partner Total Historical Capital in YER / All Partners Total Historical Capital).
 * FIXED_AGREED: Partner share % is fixed according to agreement (sum must equal 10,000 basis points = 100.00%).
 */
enum class EquityShareMode(val title: String) {
    DERIVED_FROM_CAPITAL("محسوب ديناميكياً من رأس المال"),
    FIXED_AGREED("نسب متفق عليها مسبقاً (ثابتة)");

    companion object {
        val DEFAULT = DERIVED_FROM_CAPITAL

        fun fromString(value: String?): EquityShareMode {
            if (value == null) return DEFAULT
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: DEFAULT
        }
    }
}
