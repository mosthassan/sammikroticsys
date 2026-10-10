package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.CurrencyCode
import com.example.core.model.ExchangeRate
import com.example.core.model.Money
import com.example.core.model.RateZone
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material3.OutlinedButton
import com.example.ui.theme.SemanticExpenseRed
import com.example.ui.theme.SemanticIncomeGreen
import com.example.ui.theme.SemanticWarningAmber

enum class AmountSemanticType {
    INCOME,
    EXPENSE,
    NEUTRAL,
    AUTO
}

/**
 * Shared Multi-Currency Selector supporting YER, USD, and SAR.
 */
@Composable
fun CurrencySelector(
    selectedCurrency: CurrencyCode,
    onCurrencySelected: (CurrencyCode) -> Unit,
    modifier: Modifier = Modifier,
    enabledCurrencies: List<CurrencyCode> = listOf(CurrencyCode.YER, CurrencyCode.USD, CurrencyCode.SAR)
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(12.dp)),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            enabledCurrencies.forEach { curr ->
                val isSelected = selectedCurrency == curr
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onCurrencySelected(curr) }
                        .then(
                            if (isSelected) Modifier.border(
                                BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                                RoundedCornerShape(10.dp)
                            ) else Modifier
                        ),
                    color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "${curr.name} (${curr.symbol})",
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp
                        )
                        Text(
                            text = curr.arabicName,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Informative Card displaying dynamic exchange rate for active transaction currency.
 * Fully interactive with zone toggling (Sanaa <-> Aden) and manual override.
 */
@Composable
fun ExchangeRateCard(
    currency: CurrencyCode,
    rate: ExchangeRate?,
    zone: RateZone = RateZone.DEFAULT,
    modifier: Modifier = Modifier,
    onZoneToggle: (() -> Unit)? = null,
    onEditRateClick: (() -> Unit)? = null
) {
    if (currency == CurrencyCode.FUNCTIONAL) return

    val rateFormatted = if (rate != null) ExchangeRate.formatRateMicros(rate.rateMicros) else "غير محدد"
    val zoneShortName = if (zone == RateZone.SANAA) "نطاق صنعاء" else "نطاق عدن"

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (onZoneToggle != null) {
                    Modifier.clickable { onZoneToggle() }
                } else Modifier
            )
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)), RoundedCornerShape(12.dp)),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CurrencyExchange,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "سعر الصرف المعتمد ($zoneShortName):",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (onZoneToggle != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "(اضغط للتبديل)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 10.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "1 ${currency.name} = $rateFormatted YER",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (onZoneToggle != null) {
                    IconButton(
                        onClick = onZoneToggle,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("btn_switch_rate_zone")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = "تبديل تسعيرة صنعاء / عدن",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                if (onEditRateClick != null) {
                    OutlinedButton(
                        onClick = onEditRateClick,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("btn_edit_rate_manual")
                    ) {
                        Text("تعديل يدوي", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun AmountText(
    money: Money,
    modifier: Modifier = Modifier,
    semanticType: AmountSemanticType = AmountSemanticType.AUTO,
    fontSize: Int = 16,
    fontWeight: FontWeight = FontWeight.SemiBold,
    includeSymbol: Boolean = true,
    testTag: String = "amount_text"
) {
    val color = when (semanticType) {
        AmountSemanticType.INCOME -> SemanticIncomeGreen
        AmountSemanticType.EXPENSE -> SemanticExpenseRed
        AmountSemanticType.NEUTRAL -> MaterialTheme.colorScheme.onSurface
        AmountSemanticType.AUTO -> {
            if (money.minor > 0L) SemanticIncomeGreen
            else if (money.minor < 0L) SemanticExpenseRed
            else MaterialTheme.colorScheme.onSurfaceVariant
        }
    }

    Text(
        text = money.format(includeSymbol = includeSymbol),
        color = color,
        fontSize = fontSize.sp,
        fontWeight = fontWeight,
        fontFamily = FontFamily.Monospace, // Tabular numerals
        textAlign = TextAlign.Start,
        modifier = modifier.testTag(testTag)
    )
}

@Composable
fun MoneyField(
    value: String,
    onValueChange: (String) -> Unit,
    currency: CurrencyCode,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "0.00",
    testTag: String = "money_field"
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        trailingIcon = {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Text(
                    text = currency.symbol,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    fontSize = 12.sp
                )
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag)
    )
}

@Composable
fun StatCard(
    title: String,
    amount: Money,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    semanticType: AmountSemanticType = AmountSemanticType.AUTO,
    testTag: String = "stat_card"
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .testTag(testTag)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            AmountText(
                money = amount,
                semanticType = semanticType,
                fontSize = 20,
                fontWeight = FontWeight.Bold
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun InvariantBanner(
    isValid: Boolean,
    violationsCount: Int,
    debitTotal: Long,
    creditTotal: Long,
    onRunCheck: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "invariant_banner"
) {
    val containerColor = if (isValid) Color(0xFF064E3B) else Color(0xFF7F1D1D)
    val borderColor = if (isValid) SemanticIncomeGreen else SemanticExpenseRed
    val statusText = if (isValid) "دفتر الأستاذ متزن 100% (IFRS متوافق)" else "تنبيه: خلل في اتزان دفتر الأستاذ ($violationsCount مخالفات)"
    val icon = if (isValid) Icons.Default.CheckCircle else Icons.Default.Error

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable { onRunCheck() }
            .testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = "حالة الاتزان",
                tint = if (isValid) SemanticIncomeGreen else SemanticExpenseRed,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = statusText,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Text(
                    text = "إجمالي المدين: ${Money(debitTotal, CurrencyCode.FUNCTIONAL).format()} | الدائن: ${Money(creditTotal, CurrencyCode.FUNCTIONAL).format()}",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun StatusChip(
    status: String,
    modifier: Modifier = Modifier,
    testTag: String = "status_chip"
) {
    val (bg, fg) = when (status) {
        "POSTED", "NORMAL" -> SemanticIncomeGreen.copy(alpha = 0.2f) to SemanticIncomeGreen
        "DRAFT" -> SemanticWarningAmber.copy(alpha = 0.2f) to SemanticWarningAmber
        "VOIDED", "REVERSAL" -> SemanticExpenseRed.copy(alpha = 0.2f) to SemanticExpenseRed
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        color = bg,
        shape = RoundedCornerShape(6.dp),
        modifier = modifier.testTag(testTag)
    ) {
        Text(
            text = status,
            color = fg,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (actionText != null && onActionClick != null) {
            Text(
                text = actionText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { onActionClick() }
                    .padding(4.dp)
            )
        }
    }
}

/**
 * 2026 Executive FinTech Tab Item specification
 */
data class FintechTabItem(
    val title: String,
    val icon: ImageVector? = null,
    val count: Int? = null,
    val accentColor: Color? = null,
    val subtitle: String? = null
)

/**
 * Modern Segmented Tab Row - 2026 High-Tech UX
 * Eliminates primitive tab rows with sleek, memory-stimulating, high-contrast pills.
 */
@Composable
fun ModernFintechSegmentedTabs(
    items: List<FintechTabItem>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    containerColor: Color = Color(0xFF0F172A),
    borderColor: Color = Color(0xFF1E293B)
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(16.dp)),
        color = containerColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items.forEachIndexed { index, item ->
                val isSelected = selectedIndex == index
                val itemAccent = item.accentColor ?: activeColor

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onTabSelected(index) }
                        .then(
                            if (isSelected) Modifier.border(
                                BorderStroke(1.dp, itemAccent.copy(alpha = 0.6f)),
                                RoundedCornerShape(12.dp)
                            ) else Modifier
                        ),
                    color = if (isSelected) itemAccent.copy(alpha = 0.16f) else Color.Transparent
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp, horizontal = 6.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (item.icon != null) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                tint = if (isSelected) itemAccent else Color(0xFF94A3B8),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = item.title,
                            color = if (isSelected) Color(0xFFF8FAFC) else Color(0xFF94A3B8),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                        if (item.count != null) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = if (isSelected) itemAccent.copy(alpha = 0.25f) else Color(0xFF1E293B),
                                shape = CircleShape
                            ) {
                                Text(
                                    text = item.count.toString(),
                                    color = if (isSelected) itemAccent else Color(0xFF94A3B8),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Scrollable variant for screens with 4+ tabs
 */
@Composable
fun ModernFintechScrollableTabs(
    items: List<FintechTabItem>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    containerColor: Color = Color(0xFF0F172A),
    borderColor: Color = Color(0xFF1E293B)
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(16.dp)),
        color = containerColor
    ) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)
        ) {
            itemsIndexed(items) { index, item ->
                val isSelected = selectedIndex == index
                val itemAccent = item.accentColor ?: activeColor

                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onTabSelected(index) }
                        .then(
                            if (isSelected) Modifier.border(
                                BorderStroke(1.dp, itemAccent.copy(alpha = 0.6f)),
                                RoundedCornerShape(12.dp)
                            ) else Modifier
                        ),
                    color = if (isSelected) itemAccent.copy(alpha = 0.16f) else Color.Transparent
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 9.dp, horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (item.icon != null) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                tint = if (isSelected) itemAccent else Color(0xFF94A3B8),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = item.title,
                            color = if (isSelected) Color(0xFFF8FAFC) else Color(0xFF94A3B8),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp
                        )
                        if (item.count != null) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = if (isSelected) itemAccent.copy(alpha = 0.25f) else Color(0xFF1E293B),
                                shape = CircleShape
                            ) {
                                Text(
                                    text = item.count.toString(),
                                    color = if (isSelected) itemAccent else Color(0xFF94A3B8),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
