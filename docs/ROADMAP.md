# SamMikrotik - Product & Engineering Roadmap

## Phase 1: Accounting Core & Architecture (Current)
- [x] Comprehensive Documentation (`AGENTS.md`, Architecture, Accounting Spec, Design System, Test Plan, Decisions).
- [x] Zero-Float Money Engine (`Money`, `CurrencyCode`, `ExchangeRate`, integer micro-rates, rounding distribution).
- [x] Room Database v1 Schema (Organizations, Parties, Treasuries, Chart of Accounts, Periods, Journal Entries & Lines, Documents & Items, Allocations, Assets, Depreciation Runs, Card Packages, Stock Movements, Number Sequences, Audit Log, Idempotency Keys).
- [x] Immutable SQLite triggers (`BEFORE UPDATE/DELETE` abort on `journal_entries` and `journal_lines`).
- [x] Pure Business Posting Rules (`PostingRules`).
- [x] Unified Transaction Pipeline (`LedgerWriter`).
- [x] Mathematical Invariant Validator (`LedgerInvariants`).
- [x] Seed Data (Locked IFRS COA, Walk-in Cash Party, Currencies, Standard Treasuries).
- [x] Clean Dark/Light Theme with Arabic RTL support and Tabular Numerals.
- [x] Reusable Financial UI Components (`AmountText`, `MoneyField`, `StatCard`, `InvariantBanner`, etc.).
- [x] App Structure: Onboarding Flow + Developer/Inspector Screens (COA, Journal Browser, Trial Balance, Health Check).
- [x] Unit & Robolectric Integration Test Suite.

## Phase 2: Business Operations & Workflows (Completed)
- [x] Sales & Distribution Module:
  - Batch Card Voucher generation & tracking (Package details, wholesale/retail prices).
  - Grocery Agent delivery sheets & receivables management.
  - Direct Internet Subscription contracts & billing.
  - Quick POS cash sale workflow with atomic receipt and allocation.
  - Thermal receipt printing format & voucher preview.
- [x] Expense & Upstream ISP Management:
  - Direct upstream bandwidth payment vouchers (Starlink, Wholesale Fiber).
  - Fuel, generator maintenance, site rentals, technician salary slips.
  - Fixed Assets Register (Routers, Antennas, Towers) with straight-line monthly depreciation and retirement disposal.
- [x] Treasury & Multi-Currency Transfers:
  - Cash drawers, bank accounts, electronic wallets (Floosak, Kuraimi, Jawali, etc.).
  - Inter-treasury currency conversions with realized FX tracking (IAS 21).
- [x] Partner Equity & Profit Distribution:
  - Capital contributions, monthly drawings, automated dividend calculator.
- [x] Financial Statements & Reports (`ReportsScreen`):
  - Real-time Income Statement (P&L with direct service costing).
  - IFRS Balance Sheet with mathematical asset-liability equality verification.
  - Aging of Receivables (أعمار الديون) with 0-30, 31-60, 61-90, 90+ buckets.
  - Partner dividend distribution simulator.
  - Offline-first JSON backup export and restore verification.
- [x] Modern 2026 Navigation & UI Integration (`MainActivity`):
  - Bottom navigation bar across 7 core functional domains (Dashboard, Sales, Vouchers, Purchases & Assets, Parties & Stock, Reports, Audit).
  - Live IFRS invariant health badge in TopAppBar with direct inspection shortcut.
  - Reactive Snackbar notifications for business actions.
  - Full Arabic RTL support and tabular monospace numerals for financial figures.

## Phase 3: Hardware Integrations & Cloud Sync
- [ ] MikroTik RouterOS API / Hotspot Integration:
  - Sync active users, pull generated voucher batches, disable expired users.
- [ ] Bluetooth ESC/POS Thermal Printing for voucher slips and customer receipts.
- [ ] Offline-First Cloud Sync (Firebase Firestore / Cloud SQL backend) with conflict resolution.
- [ ] Role-Based Access Control (Owner, Accountant, Cashier, Auditor).
- [ ] Encrypted Automated Backups (Local storage & Google Drive).
