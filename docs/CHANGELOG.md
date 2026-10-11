# Changelog - SamMikrotik

## [Prompt Fix 1 - Accounting Core Repair] - 2026-10-11

### 1. Unified Fiscal Calendar and Application Clock
- Added `FiscalCalendar` in `com.example.core.time` to handle arbitrary fiscal year starting months (`fiscalYearStartMonth`), leap years, epoch days, and period calculations without external dependencies.
- Added `AppClock` and `FixedAppClock` in `com.example.core.time` defaulting to the `Asia/Aden` (UTC+3) timezone, allowing reproducible time and date handling across the app and testing environments.
- Replaced all non-standard and hardcoded date calculations (e.g. `System.currentTimeMillis() / 86400000`, `20365L`, and unadjusted `LocalDate.now()`) with `AppClock.todayEpochDay()` and `FiscalCalendar.yearEnd(fiscalYear)`.

### 2. Year-End Closing Redesign & Balance Sheet Equilibrium
- Enhanced `LedgerWriter.executeYearEndClosing` and `PostingRules.createClosingDraftFromNetBalances`:
  - Properly clears nominal accounts (4xxx revenues, 5xxx expenses), including contra-accounts (debit balances in 4xxx and credit balances in 5xxx).
  - Automatically routes net income to Retained Earnings (`3301`).
  - Closes all 12 periods of the fiscal year in `fiscal_periods`.
  - Determines the closing date dynamically via `FiscalCalendar.yearEnd(fiscalYear)`.
- Replaced hardcoded closing date `20365L` in `AppViewModel.executeYearEndClosing` with `FiscalCalendar.yearEnd(fiscalYear)`.
- Verified Balance Sheet invariant: `Assets == Liabilities + Equity (Capital + Partner Current + Retained Earnings + Unclosed Profit)`.

### 3. Journal Entry Sequence & Unique Constraint Guard
- Updated `persistJournalDraft` in `LedgerWriter`:
  - Systematically allocates a unique, sequential `entryNumber` using `allocateNextDocNumber("JOURNAL_ENTRY", 0)` to prevent uniqueness collisions across different document types and reversal entries.
  - Supports `reversalOfEntryId` for explicit lineage linking on voiding and reversing entries.
- Updated `BackupRestoreUseCase` to include `revision` on documents and `reversalOfEntryId` on journal entries.

### 4. Document Amendment Workflow
- Added `amendSalesInvoice` to `LedgerWriter`:
  - Automatically reverses the original document with a compensating `REVERSAL` journal entry.
  - Marks the original document as `VOIDED`.
  - Reverses stock movements and enforces negative stock protection.
  - Creates a new `POSTED` document linking `reversalOfDocId` to the original document, incrementing `revision = originalDoc.revision + 1`.
  - Atomically executed within a single Room database transaction.

### 5. Invariant Validation Optimization
- Integrated lightweight $O(1)$ single-entry validation `invariants.verifyEntry(entryId)` immediately upon persisting each journal entry in production.
- Kept full ledger verification `invariants.verifyAll()` guarded by `enableInvariantValidation`, which defaults to `BuildConfig.DEBUG`.

### 6. Partner Equity Fixed Agreed Mode Fix
- Fixed `PartnerEquityUseCase.setEquityShareMode` to gracefully handle uninitialized organizations by creating/updating the record, preventing fallback failures in fixed agreed equity mode tests.
