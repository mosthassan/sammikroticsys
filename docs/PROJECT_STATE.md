# SamMikrotik Project State

## 1. System Overview
- **App Name:** SamMikrotik
- **Core Technology:** Kotlin 2.2, Jetpack Compose (M3), Room (SQLite) with KSP, Coroutines & Flow.
- **Current DB Schema Version:** 4 (Schemas 1.json to 4.json preserved, migration to 5 planned for partner share history).
- **Core Accounting:** Pure double-entry general ledger (`journal_entries` and `journal_lines`), non-destructive reversals, sequential atomic numbering, multi-currency with fixed IAS 21 snapshot rates, no Double/Float.

## 2. Active Milestone: Reports Center (مركز التقارير)
- **Goal:** Comprehensive financial, operational, and auditing reports hub:
  - Part 0: Reports Engine (`domain/report`), Drill-down, PDF/CSV Export, Reports Center UI shell.
  - Part 1: Monthly Profit Breakdown (Comparative Income Statement), Partner Statement, Equity Summary, Direct Cash Flows, Daily Vault/Cash Register.
  - Part 2: Activity Reports (Sales by package/agent, Collections & DSO, Aging, Batch Statements, Expense analytics, Card inventory movements, Asset register, Currencies & FX positions).
  - Part 3: Internal Control & Audit (Revisions/Cancellations, Backdated entries, Negative vault detection, Manual FX detection, Period lock status, Invariants verification, Performance benchmarking on 50,000 entries).

## 3. Strict Invariants
- Zero Double/Float in financial calculations.
- Read-only execution in all reporting domain & UI layers (no inserts, updates, deletes, or LedgerWriter calls).
- Reconciliation invariants guaranteed across all reports.
