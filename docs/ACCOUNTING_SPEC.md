# SamMikrotik - Accounting Specification (IFRS Compliant)

## 1. Accounting Core Principles
1. **Single Source of Truth:** `journal_lines` is the sole source of financial truth. All balances, account totals, party receivables/payables, and treasury balances are derived via aggregate SQL queries.
2. **Immutable Ledger:** Journal entries cannot be updated or deleted. Database-level triggers `BEFORE UPDATE` and `BEFORE DELETE` abort any direct alteration attempt.
3. **Reversal Pattern:** Adjustments and voids create an exact compensatory `REVERSAL` entry pointing to the original document, accompanied by a fresh entry if modified.
4. **Integer Minor Units:** All monetary values are 64-bit integers (`Long`) with scale 2 (100 minor units = 1.00 major unit).
5. **Functional Currency & Rate Fixing:** YER is the functional reporting currency. Foreign currencies fix their exchange rate in micro-units upon posting. Multi-line conversion differences are absorbed by the final journal line.
6. **IAS 21 Realized Foreign Exchange:** Upon settlement of a receivable or payable at an exchange rate differing from the invoice rate, gain (4901) or loss (5901) is booked atomically.

---

## 2. Standard Chart of Accounts (COA)
| Code | Account Name (Arabic) | Type | Normal Balance | Locked System |
|---|---|---|---|---|
| **1101** | النقدية بالصندوق (Cash in Vault) | Asset | Debit | Yes |
| **1102** | البنوك والمحافظ الإلكترونية (Banks & Wallets) | Asset | Debit | Yes |
| **1201** | العملاء والوكلاء (Accounts Receivable) | Asset | Debit | Yes |
| **1301** | مخزون الكروت (Card Inventory - Inactive Reserve) | Asset | Debit | Yes |
| **1501** | أجهزة ومعدات الشبكة (Network Fixed Assets) | Asset | Debit | Yes |
| **1599** | مجمع الإهلاك (Accumulated Depreciation) | Contra-Asset | Credit | Yes |
| **2101** | الموردون (Accounts Payable) | Liability | Credit | Yes |
| **3101** | رأس المال (Share Capital) | Equity | Credit | Yes |
| **3201** | جاري الشركاء (Partner Current Accounts) | Equity | Credit | Yes |
| **3301** | الأرباح المرحّلة (Retained Earnings) | Equity | Credit | Yes |
| **4101** | إيرادات مبيعات الكروت (Card Sales Revenue) | Revenue | Credit | Yes |
| **4102** | مردودات ومسموحات المبيعات (Sales Returns) | Contra-Revenue| Debit | Yes |
| **4201** | إيرادات الاشتراكات المباشرة والخدمات (Direct ISP Services Revenue) | Revenue | Credit | Yes |
| **4901** | أرباح فروق العملة (Realized FX Gain) | Revenue | Credit | Yes |
| **5101** | تكلفة الخدمة المباشرة (اشتراكات الإنترنت الرئيسية) | Expense | Debit | Yes |
| **5201** | مصاريف تشغيل وعمومية (ديزل/كهرباء) | Expense | Debit | Yes |
| **5202** | صيانة وقطع غيار (Maintenance & Spares) | Expense | Debit | Yes |
| **5203** | إهلاك الأصول (Depreciation Expense) | Expense | Debit | Yes |
| **5204** | رواتب وأجور وفنيون (Staff & Technicians) | Expense | Debit | Yes |
| **5299** | مصاريف متنوعة (Miscellaneous Expenses) | Expense | Debit | Yes |
| **5901** | خسائر فروق العملة (Realized FX Loss) | Expense | Debit | Yes |

---

## 3. Posting Rules & Numerical Examples (Table 3.5 Detailed)

### 3.1 Sales Invoice (فاتورة مبيعات)
- **Rule:** Debit `1201` (Party receivable) with total invoice. Credit `4101` for card package lines, Credit `4201` for direct service lines.
- **Numerical Example:** Agent "Al-Amal Grocery" buys 50 cards (10,000 YER) + 1 month Fiber link (15,000 YER).
  - Total: 25,000 YER.
  - `DR 1201 (Al-Amal Grocery): 25,000 YER`
  - `CR 4101 (Card Sales): 10,000 YER`
  - `CR 4201 (Service Sales): 15,000 YER`

### 3.2 Cash Sale / Upfront Payment (مبيعات نقدية)
- **Rule:** Atomic transaction creating Sales Invoice + Receipt Voucher + Allocation.
- **Numerical Example:** Walk-in customer buys 2,000 YER voucher card for cash.
  - Invoice:
    - `DR 1201 (Walk-in Customer): 2,000 YER`
    - `CR 4101 (Card Sales): 2,000 YER`
  - Receipt:
    - `DR 1101 (Cash Box YER): 2,000 YER`
    - `CR 1201 (Walk-in Customer): 2,000 YER`
  - Allocation: Links receipt to invoice, invoice status = PAID. Balance of Walk-in = 0.

### 3.3 Receipt Voucher: Customer Payment (سند قبض: دفعة عميل)
- **Rule:** Debit Treasury (`1101` or `1102`), Credit `1201` (Party).
- **Numerical Example:** Al-Amal Grocery pays 15,000 YER against their invoice.
  - `DR 1101 (Main Treasury): 15,000 YER`
  - `CR 1201 (Al-Amal Grocery): 15,000 YER`
  - Allocation applied to oldest open invoice.

### 3.4 Receipt Voucher: Capital Injection (سند قبض: رأس مال أو شريك)
- **Rule:** Debit Treasury, Credit `3101` (or `3201` for partner current account).
- **Numerical Example:** Partner "Saeed" contributes 100,000 YER additional equity.
  - `DR 1101 (Main Treasury): 100,000 YER`
  - `CR 3201 (Partner Saeed): 100,000 YER`

### 3.5 Credit Note / Sales Return (إشعار دائن: مرتجع مبيعات)
- **Rule:** Debit `4102` (Sales Returns), Credit `1201` (Customer).
- **Numerical Example:** Agent returns 5 defective cards worth 1,000 YER.
  - `DR 4102 (Sales Returns): 1,000 YER`
  - `CR 1201 (Al-Amal Grocery): 1,000 YER`

### 3.6 Purchase Invoice (فاتورة مشتريات)
- **Rule:** Debit Asset `1501` for fixed assets or Expense `5xxx` for supplies. Credit `2101` (Vendor) always in full.
- **Numerical Example:** Purchasing 3 MikroTik Cloud Core Routers worth $600 USD @ rate 530 YER/USD (Base: 318,000 YER).
  - `DR 1501 (Network Equipment): 318,000 YER (Orig: $600.00 USD)`
  - `CR 2101 (Vendor TechNet): 318,000 YER (Orig: $600.00 USD)`

### 3.7 Payment Voucher: Vendor Settlement (سند صرف: سداد مورد)
- **Rule:** Debit `2101` (Vendor), Credit Treasury (`1101`/`1102`).
- **Numerical Example:** Paying Vendor TechNet $600 USD from USD Cash box.
  - `DR 2101 (Vendor TechNet): 318,000 YER (Orig: $600.00 USD)`
  - `CR 1101 (USD Treasury): 318,000 YER (Orig: $600.00 USD)`

### 3.8 Payment Voucher: Direct Service Expense (سند صرف: اشتراك رئيسي)
- **Rule:** Debit `5101` (Direct Internet Service Cost), Credit Treasury.
- **Numerical Example:** Starlink Monthly Subscription $250.00 USD @ 530 YER/USD.
  - Base: 132,500 YER.
  - `DR 5101 (Direct ISP Internet Cost): 132,500 YER`
  - `CR 1101 (USD Treasury): 132,500 YER (Orig: $250.00 USD)`

### 3.9 Payment Voucher: Partner Drawings (سند صرف: سحب شريك)
- **Rule:** Debit `3201` (Partner), Credit Treasury.
- **Numerical Example:** Partner draws 20,000 YER cash.
  - `DR 3201 (Partner Current): 20,000 YER`
  - `CR 1101 (Cash Box): 20,000 YER`

### 3.10 Treasury Transfer (تحويل بين صناديق)
- **Rule:** Debit Destination Treasury (`1101`/`1102`), Credit Source Treasury. Any FX difference hits `4901`/`5901`.
- **Numerical Example:** Exchanging $100.00 USD from USD box to buy 53,500 YER into YER box (Historical book rate was 530.00 YER, actual conversion achieved 535.00 YER).
  - `DR 1101 (YER Box): 53,500 YER`
  - `CR 1101 (USD Box): 53,000 YER ($100 @ 530)`
  - `CR 4901 (Realized FX Gain): 500 YER`

### 3.11 Depreciation Run (إهلاك الأصول)
- **Rule:** Straight-line monthly depreciation: `(Cost - Salvage) / UsefulLifeMonths`. Debit `5203`, Credit `1599`. Unique per `(assetId, period)`.
- **Numerical Example:** Router cost 120,000 YER, useful life 24 months, salvage 0.
  - Monthly: 5,000 YER.
  - `DR 5203 (Depreciation Expense): 5,000 YER`
  - `CR 1599 (Accumulated Depreciation): 5,000 YER`

### 3.12 Foreign Currency Settlement Gain/Loss (IAS 21)
- **Numerical Example:** Invoice issued for $100 USD @ 530 YER (DR 1201: 53,000 YER). Customer pays $100 when market rate is 535 YER (Deposited to USD box at 53,500 YER).
  - `DR 1101 (USD Box): 53,500 YER`
  - `CR 1201 (Customer): 53,000 YER`
  - `CR 4901 (Realized FX Gain): 500 YER`

### 3.13 Year-End Closing (إقفال سنوي)
- **Rule:** Journal type `CLOSING`. Sum of all 4xxx credit balances debited; sum of all 5xxx debit balances credited; net transfer to `3301` (Retained Earnings).
