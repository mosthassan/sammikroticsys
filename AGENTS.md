# SamMikrotik - Agent Memory & Operating Guidelines

## Identity & Mission
SamMikrotik is a production-grade, offline-first, IFRS-compliant double-entry accounting and financial management Android application designed specifically for Internet Service Providers (ISPs) and wireless network distributors.

Brand Name: `AppBrand.NAME = "SamMikrotik"`

## Build & Test Commands
- Compile Applet: `compile_applet` tool
- Run Unit & Robolectric Tests: `run_command(CommandLine="gradle :app:testDebugUnitTest", Cwd="/")`
- Linting: `lint_applet` tool (only when specifically needed)

## Environment Limitations & Decisions
- Environment: Cloud-based Android Linux build environment.
- No direct ADB/emulator connection for the agent. Emulation is streamed to the user via browser.
- Tests must be fast, local JVM Robolectric and JUnit tests.
- Database: Room with KSP. Schema export enabled in `app/schemas`.
- Architecture: Unidirectional Data Flow (UDF), MVVM, Single Activity with Jetpack Compose (M3), Kotlin Coroutines and StateFlow.

## Absolute Prohibitions (Never Violate)
1. **Never use `OnConflictStrategy.REPLACE`** on any financial or ledger tables.
2. **Never use `fallbackToDestructiveMigration`** anywhere in database configuration.
3. **Never use `Double` or `Float`** for monetary amounts or exchange calculations. Always use `Long` minor units (`Money`) and integer micro-rates.
4. **Never generate document or transaction numbers** using `System.currentTimeMillis()` or random generators. Always use atomic sequential numbering per `(docType, fiscalYear)`.
5. **Never hard-delete** any financial document or journal entry. Edits and cancellations are strictly handled via reversal entries + new entries.
6. **Never match accounting types or accounts by string text/contains**. Always use typed enums and fixed chart-of-accounts codes.
7. **Never store derived balances** as source of truth. The ledger entries (`journal_lines`) are the single source of truth.
8. **Never bypass `LedgerWriter`**. All journal writes must go through this single pipeline.
9. **Never swallow exceptions** inside financial transactions (`try/catch` must not hide posting failures).
10. **Never use arbitrary percentage estimates** for Cost of Goods Sold. Service-based costing (account 5101) is fed solely by direct upstream ISP payment vouchers (e.g., Starlink, bulk fiber). Card inventory is tracked by quantities only.

## Key Learnings & Status
- Phase 1: Accounting core, SQLite triggers, Room DB, pure posting rules, invariants validator, design system, verification/inspection screens, and comprehensive test suite established.
