package com.example.core.ledger

object AccountConstants {
    // Assets (1xxx)
    const val CASH_VAULT = "1101"
    const val BANKS_WALLETS = "1102"
    const val ACCOUNTS_RECEIVABLE = "1201"
    const val CARD_INVENTORY_RESERVE = "1301"
    const val FIXED_ASSETS_NETWORK = "1501"
    const val ACCUMULATED_DEPRECIATION = "1599" // Contra-Asset

    // Liabilities (2xxx)
    const val ACCOUNTS_PAYABLE = "2101"

    // Equity (3xxx)
    const val CAPITAL = "3101"
    const val PARTNER_CURRENT = "3201"
    const val RETAINED_EARNINGS = "3301"

    // Revenue (4xxx)
    const val CARD_SALES_REVENUE = "4101"
    const val SALES_RETURNS = "4102" // Contra-Revenue
    const val DIRECT_SERVICE_REVENUE = "4201"
    const val REALIZED_FX_GAIN = "4901"

    // Expenses (5xxx)
    const val DIRECT_ISP_SERVICE_COST = "5101" // Upstream ISP (Starlink, fiber, bulk bandwidth)
    const val OPERATING_EXPENSES = "5201" // Diesel, electricity, rent
    const val MAINTENANCE_SPARES = "5202" // Router repairs, cabling, spare parts
    const val DEPRECIATION_EXPENSE = "5203" // Monthly straight line depreciation
    const val SALARIES_STAFF = "5204" // Field technicians & staff
    const val MISC_EXPENSES = "5299"
    const val REALIZED_FX_LOSS = "5901"

    val SYSTEM_CONTROL_ACCOUNTS = setOf(
        ACCOUNTS_RECEIVABLE,
        ACCOUNTS_PAYABLE,
        PARTNER_CURRENT
    )

    val LOCKED_SYSTEM_ACCOUNTS = setOf(
        CASH_VAULT,
        BANKS_WALLETS,
        ACCOUNTS_RECEIVABLE,
        CARD_INVENTORY_RESERVE,
        FIXED_ASSETS_NETWORK,
        ACCUMULATED_DEPRECIATION,
        ACCOUNTS_PAYABLE,
        CAPITAL,
        PARTNER_CURRENT,
        RETAINED_EARNINGS,
        CARD_SALES_REVENUE,
        SALES_RETURNS,
        DIRECT_SERVICE_REVENUE,
        REALIZED_FX_GAIN,
        DIRECT_ISP_SERVICE_COST,
        OPERATING_EXPENSES,
        MAINTENANCE_SPARES,
        DEPRECIATION_EXPENSE,
        SALARIES_STAFF,
        MISC_EXPENSES,
        REALIZED_FX_LOSS
    )

    fun isDebitNormal(accountCode: String): Boolean {
        return when {
            accountCode.startsWith("1") && accountCode != ACCUMULATED_DEPRECIATION -> true
            accountCode == SALES_RETURNS -> true
            accountCode.startsWith("5") -> true
            else -> false
        }
    }
}
