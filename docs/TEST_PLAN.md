# SamMikrotik - Test Plan & Verification Strategy

## 1. Objectives & Scope
The test suite ensures 100% compliance with double-entry mathematical invariants, absence of floating point inaccuracies, immutable ledger protection, and robust error recovery across all document lifecycle events.

## 2. Test Suites

### Suite A: Pure JVM Unit Tests (`core:model` & `core:ledger`)
- `MoneyTest`: Addition, subtraction, multiplication, rounding down/up, zero equality, negative amounts.
- `ExchangeRateTest`: Micro-unit conversions, rounding distributions across multi-line drafts (e.g. 100.00 across 3 lines @ 530.50 rate).
- `PostingRulesTest`:
  - Sales invoice draft generation (Debit 1201, Credit 4101/4201).
  - Credit note draft generation (Debit 4102, Credit 1201).
  - Purchase invoice draft generation (Debit 1501 or 5xxx, Credit 2101).
  - Payment voucher direct service expense (Debit 5101, Credit Treasury).
  - Realized foreign exchange gain/loss calculation on settlement.
  - Depreciation run draft generation.
  - Year-end closing draft generation.

### Suite B: Robolectric & In-Memory Room Integration Tests (`LedgerWriterTest`)
1. **Invoice Partial Payments & Reversal:** Post sales invoice -> 2 partial receipts -> void 2nd receipt -> verify derived balance and allocation state.
2. **Document Numbering Isolation:** Concurrent sales invoice and purchase invoice verify isolated sequences per type.
3. **Fixed Asset Purchase with Instant Payment:** Purchase network router with cash -> verify debit 1501, zero vendor balance, and asset register creation.
4. **Foreign Currency Multi-Rate Settlement:** Invoice in USD settled at higher exchange rate -> verify realized FX gain booked in 4901 and trial balance perfectly in equilibrium.
5. **Depreciation Idempotency:** Running depreciation for the same period and asset twice -> rejected / idempotent single run.
6. **Cancellation Cascade:** Voiding a paid invoice reverts party receivables and resets allocations.
7. **Closed Period Protection:** Posting an entry into a closed fiscal period is rejected with validation error.
8. **Trigger Immutability:** Direct raw SQL `UPDATE` or `DELETE` on `journal_lines` or `journal_entries` triggers SQLite `ABORT`.
9. **Property-Based Invariants Test:** Run 100+ random sequence permutations (create, pay, allocate, void) and assert `LedgerInvariants.verifyAll()` passes after every operation.
10. **Cent-Level Multi-Line Balancing:** Multi-line foreign currency invoice balances down to the single minor unit after integer conversion.
11. **Schema & Migration Test:** Verify database creation and table integrity under Room v1 schema.

### Suite C: Multi-Currency Partner Capital & Equity Ratios (`PartnerCapitalPartCTest`)
25. **Historical Cost Principle (C.1):** Multi-currency capital contribution recorded at historical rate (DR Treasury, CR 3101 in base YER).
26. **IAS 21 Non-Revaluation of Equity (C.1):** Non-monetary equity is never revalued; historical capital remains frozen under rate spikes.
27. **Dynamic Partner Share (C.2):** DERIVED_FROM_CAPITAL dynamically calculates partner share bps from account 3101.
28. **Fixed Agreed Validation (C.2):** FIXED_AGREED enforces sum of partner shares == 100.00% (10,000 bps).
29. **Hare-Niemeyer Profit Distribution (C.2):** Largest Remainder Method distributes uneven profits with zero rounding residue down to 1 Rial (DR 3301, CR 3201).
30. **Capital Contribution Voiding & Reversal (C.1/C.3):** Voiding multi-currency contribution posts compensatory reversal entry and satisfies all invariants.

## 3. Execution Command
```bash
gradle :app:testDebugUnitTest
```
