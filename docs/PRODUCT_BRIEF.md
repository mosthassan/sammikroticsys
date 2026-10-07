# SamMikrotik - Product Brief

## 1. Executive Summary
**SamMikrotik** is a specialized, mobile-first Android ERP and accounting system built for wireless internet service providers (ISPs), local network operators, and community Wi-Fi distributors. It bridges the gap between field distribution (selling voucher cards through corner groceries and agents) and double-entry financial accounting compliance (IFRS).

## 2. Target Personas
1. **Network Owner / Managing Partner:** Needs real-time visibility into net profit, partner equity, cash flow across multi-currency treasuries (YER, USD, SAR), and asset depreciation.
2. **Accountant / Bookkeeper:** Requires double-entry journals, trial balance, ledger reconciliation, audit logs, and closed fiscal periods.
3. **Field Agent / Cashier:** Distributes batch cards to grocery stores on credit or cash, issues instant payment receipts, and tracks agent credit balances.
4. **Silent Partners / Investors:** View capital contributions, withdrawals, and distributed dividend dividends.

## 3. Business Model & Revenue Mechanics
- **Wholesale Bandwidth Purchase:** ISP pays recurring monthly upstream subscriptions (e.g., Starlink, wholesale fiber optic) in USD or local currency.
- **Card / Voucher Sales:** Network generates or distributes Wi-Fi vouchers with packages (1 Hour, 1 Day, 1 Week, 1 Month, or specific gigabyte limits) sold to agents at wholesale price, who sell at retail price.
- **Direct Services & Subscriptions:** Direct Ethernet/wireless link monthly fees for businesses, homes, and public venues.
- **Costing Model:** Service-based costing. Costs are recorded upon actual disbursement for upstream bandwidth, diesel/solar generator maintenance, equipment repairs, and technician payroll.

## 4. Multi-Currency Operations
- **Functional Currency:** Yemeni Rial (YER).
- **Secondary Currencies:** US Dollar (USD), Saudi Riyal (SAR).
- Each transaction locks the exchange rate at creation date. Realized foreign exchange gains (4901) or losses (5901) are recognized during settlement allocations in accordance with IAS 21.

## 5. Phased Roadmap
- **Phase 1 (Current):** Architectural Core, Immutable Double-Entry Ledger, Room Schema, Design System & Developer Verification Screens.
- **Phase 2:** Business Operations UI (Invoicing, Vouchers, Card Inventory, Expenses, Partner Management, Dynamic Reports).
- **Phase 3:** Mikrotik RouterOS API / Billing sync, Cloud Backup, Multi-user roles, and Print/Export (PDF/Thermal Bluetooth).
