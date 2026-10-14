# SamMikrotik Project State

## 1. System Overview
- **App Name:** SamMikrotik
- **Core Technology:** Kotlin 2.2, Jetpack Compose (M3), Room (SQLite) with KSP, Coroutines & Flow.
- **Current DB Schema Version:** 4 (Schemas 1.json to 4.json preserved, migration to 5 planned for partner share history).
- **Core Accounting:** Pure double-entry general ledger (`journal_entries` and `journal_lines`), non-destructive reversals, sequential atomic numbering, multi-currency with fixed IAS 21 snapshot rates, no Double/Float.

## 2. Active Milestone: Reports Center (مركز التقارير) - مكتمل بنجاح
- **Status:** All 16 IFRS-compliant reports implemented, verified, and tested across all stages:
  - Part 0 [Completed]: Reports Engine (`domain/report`), Drill-down, PDF/CSV Export, Reports Center UI shell.
  - Part 1 [Completed]: Monthly Profit Breakdown (Comparative Income Statement), Partner Statement, Equity Summary, Direct Cash Flows, Daily Vault/Cash Register.
  - Part 2 [Completed]: Activity Reports (`ActivityReportsService`): Sales by package & agent, Collections & DSO, Expense analytics & service gross margins, Card inventory movements, Fixed assets register, Currency positions & FX differences.
  - Part 3 [Completed]: Internal Control & Audit (`AuditReportsService`): Reversals & cancellations, Backdated entries detection, Negative vault detection, Period lock status, Double-entry mathematical invariants verification.

## 3. Strict Invariants
- Zero Double/Float in financial calculations.
- Read-only execution in all reporting domain & UI layers (no inserts, updates, deletes, or LedgerWriter calls).
- Reconciliation invariants guaranteed across all reports.
