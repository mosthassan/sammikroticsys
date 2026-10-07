# SamMikrotik - Design System Specification

## 1. Brand & Foundations
- **Brand Identity:** High-tech, trustworthy, modern financial engineering for wireless ISPs.
- **Design Language:** Material Design 3 (M3) with full RTL support (Right-to-Left Arabic as primary, English as secondary).
- **Default Theme:** Dark Slate Navy (`#0B0F19` background, `#111827` surface, `#1E293B` container), modern glowing accents (Cyan `#06B6D4`, Emerald `#10B981`, Amber `#F59E0B`, Crimson `#EF4444`).
- **Light Theme:** Modern high-contrast clean slate for daylight fieldwork.

## 2. Color Tokens (Semantic & WCAG AA Compliant)
- **Primary:** `#06B6D4` (Cyan 500) - Primary actions, active highlights.
- **OnPrimary:** `#00363F`
- **Secondary:** `#3B82F6` (Blue 500) - Auxiliary controls.
- **Income / Asset:** `#10B981` (Emerald 500) - Revenues, cash inflows, positive balances.
- **Expense / Liability:** `#EF4444` (Red 500) - Costs, outflows, payables.
- **Warning / Pending:** `#F59E0B` (Amber 500) - Partially paid, draft status.
- **Neutral Dark / Slate:** `#94A3B8` (Slate 400) - Subtitles, metadata, inactive items.

## 3. Typography & Numerals
- **Type Hierarchy:** Display, Headline, Title, Body, Label scaled for mobile and tablet.
- **Tabular Numerals (`tnum`):** Monospace numerical glyphs for all monetary figures and financial tables to ensure vertical alignment of digits.
- **Currencies:** Rendered dynamically using `CurrencyCode.symbol` (`ر.ي`, `$`, `ر.س`). Never hardcode currency text.

## 4. Reusable UI Components
1. **`AmountText`**: Renders `Money` with semantic color (green for credits/positive, red for debits/negative/expenses), formatted with decimal separator, and correct RTL alignment.
2. **`MoneyField`**: Numeric input field tailored for currency entry with instant conversion estimation.
3. **`StatCard`**: Financial KPI metric display with icon, label, amount, and trend/status badge.
4. **`SectionHeader`**: Clean section title with optional action button.
5. **`StatusChip`**: Status visualizer (POSTED = Emerald, DRAFT = Amber, VOIDED = Gray/Red).
6. **`InvariantBanner`**: Health status indicator showing real-time ledger balance and invariant integrity.
7. **`EmptyState` & `ErrorState`**: Clear messaging with retry action and no dead-ends.
8. **`ConfirmDialog` / `ConfirmSheet`**: Guarding critical accounting operations.
