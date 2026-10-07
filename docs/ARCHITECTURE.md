# SamMikrotik - Technical Architecture

## 1. System Overview & Layering
SamMikrotik follows Clean Architecture and MVVM with unidirectional data flow (UDF).

```mermaid
graph TD
    UI[Jetpack Compose UI (RTL, M3)] --> VM[ViewModels (StateFlow / UiEvent)]
    VM --> REP[Repositories (Data Access Abstraction)]
    REP --> LW[LedgerWriter (Single Posting Authority)]
    REP --> DAO[Room DAOs (Read Queries & Flow)]
    LW --> PR[PostingRules (Pure JVM Business Rules)]
    LW --> INV[LedgerInvariants (Consistency Validator)]
    LW --> DB[(SQLite Database via Room)]
    DB --> TRIG[Immutable Triggers (BEFORE UPDATE/DELETE)]
    PR --> MONEY[core:model Money & ExchangeRate]
```

## 2. Module & Package Breakdown
```
com.example
├── core
│   ├── model
│   │   ├── Money.kt                  (Long minor units, safe operations)
│   │   ├── CurrencyCode.kt           (YER, USD, SAR enums & symbols)
│   │   └── ExchangeRate.kt           (Integer micro-rate calculations)
│   └── ledger
│       ├── AccountConstants.kt       (Standard IFRS COA codes)
│       ├── DocumentType.kt           (Sales, Purchases, Payments, etc.)
│       ├── JournalDraft.kt           (Draft models for pure rules)
│       └── PostingRules.kt           (Deterministic, pure posting functions)
├── data
│   ├── local
│   │   ├── AppDatabase.kt            (Room DB, triggers, versioning)
│   │   ├── dao                       (AccountDao, PartyDao, JournalDao, DocumentDao...)
│   │   └── entity                    (Parties, Documents, JournalEntries, etc.)
│   ├── ledger
│   │   ├── LedgerWriter.kt           (Single write pathway & transaction runner)
│   │   └── LedgerInvariants.kt       (Mathematical & structural integrity checker)
│   └── repository
│       └── AccountingRepository.kt   (Read queries, aggregates, reports)
├── ui
│   ├── theme                         (Theme.kt, Color.kt, Type.kt, Dimensions)
│   ├── components                    (AmountText, MoneyField, StatCard, InvariantBanner...)
│   ├── navigation                    (AppNavigation, Routes)
│   ├── onboarding                    (Initial Org, Currency & Treasury Setup)
│   └── inspector                     (COA Inspector, Journal Inspector, Trial Balance, Health Check)
```

## 3. Concurrency & Idempotency
- All accounting transactions are committed inside `withTransaction { ... }`.
- Mutation calls accept an optional `idempotencyKey` preventing double-tap submissions on mobile touchscreens.
- Queries return `kotlinx.coroutines.flow.Flow` for dynamic reactive Compose updates.
