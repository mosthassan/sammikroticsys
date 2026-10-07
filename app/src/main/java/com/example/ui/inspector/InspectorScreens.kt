package com.example.ui.inspector

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.CurrencyCode
import com.example.core.model.Money
import com.example.data.local.dao.AccountBalanceRow
import com.example.data.local.entity.AccountEntity
import com.example.ui.components.AmountSemanticType
import com.example.ui.components.AmountText
import com.example.ui.components.InvariantBanner
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatCard
import com.example.ui.components.StatusChip
import com.example.ui.theme.SemanticExpenseRed
import com.example.ui.theme.SemanticIncomeGreen
import com.example.ui.theme.SemanticWarningAmber

@Composable
fun HealthCheckScreen(
    viewModel: InspectorViewModel,
    modifier: Modifier = Modifier
) {
    val invariantResult by viewModel.invariantResult.collectAsState()
    val trialBalance by viewModel.trialBalance.collectAsState()
    val triggerResult by viewModel.triggerTestResult.collectAsState()
    val documents by viewModel.allDocuments.collectAsState()

    val totalDebit = trialBalance.sumOf { it.totalDebitMinor }
    val totalCredit = trialBalance.sumOf { it.totalCreditMinor }
    val isBalanced = invariantResult?.isValid ?: (totalDebit == totalCredit)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            InvariantBanner(
                isValid = isBalanced,
                violationsCount = invariantResult?.violations?.size ?: 0,
                debitTotal = totalDebit,
                creditTotal = totalCredit,
                onRunCheck = { viewModel.runInvariantCheck() }
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    title = "إجمالي حركات المدين",
                    amount = Money(totalDebit, CurrencyCode.FUNCTIONAL),
                    icon = Icons.Default.AccountBalance,
                    semanticType = AmountSemanticType.INCOME,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "إجمالي حركات الدائن",
                    amount = Money(totalCredit, CurrencyCode.FUNCTIONAL),
                    icon = Icons.Default.AccountBalance,
                    semanticType = AmountSemanticType.INCOME,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            SectionHeader(title = "إجراءات الاختبار والتحقق السريع")
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "1. زرع قيود تجريبية (مبيعات، Starlink، أصول MikroTik)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "يُنشئ مستندات حقيقية ويرحّلها عبر LedgerWriter للتحقق من الاتزان وتوليد أرقام متسلسلة وسطور القيود.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { viewModel.seedDemoTransactions() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("seed_demo_button")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("زرع بيانات تجريبية فورية")
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                    Text(
                        text = "2. فحص أمان المشغّلات (SQLite Triggers Test)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "محاولة تنفيذ أمر UPDATE SQL مباشر على سطور القيود. يجب أن ترفض قاعدة البيانات وتُسقط العملية بـ RAISE(ABORT).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = { viewModel.testDirectUpdateTrigger() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("test_triggers_button")
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("اختبار منع التعديل المباشر")
                    }

                    if (triggerResult != null) {
                        Text(
                            text = triggerResult!!,
                            color = if (triggerResult!!.startsWith("نجاح")) SemanticIncomeGreen else SemanticExpenseRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        item {
            SectionHeader(title = "المستندات المنشأة (${documents.size})")
        }

        items(documents) { doc ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${doc.type} #${doc.docNumber}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            StatusChip(status = doc.status)
                        }
                        if (doc.notes.isNotBlank()) {
                            Text(
                                text = doc.notes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    AmountText(
                        money = Money(doc.totalBaseMinor, CurrencyCode.FUNCTIONAL),
                        semanticType = AmountSemanticType.NEUTRAL,
                        fontSize = 14
                    )
                }
            }
        }
    }
}

@Composable
fun ChartOfAccountsScreen(
    viewModel: InspectorViewModel,
    modifier: Modifier = Modifier
) {
    val accounts by viewModel.allAccounts.collectAsState()
    val trialBalance by viewModel.trialBalance.collectAsState()

    val balanceMap = remember(trialBalance) {
        trialBalance.associateBy { it.accountCode }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = "دليل الحسابات النظامي IFRS (${accounts.size} حساب)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "الأرصدة مشتقة لحظياً من أسطر القيود ولا تُخزَّن.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        items(accounts) { acc ->
            val bal = balanceMap[acc.code]
            val netMinor = bal?.netBalanceMinor ?: 0L

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = acc.code,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = acc.name,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }
                        Text(
                            text = "${acc.type} | طبيعة الحساب: ${if (acc.isDebitNormal) "مدين" else "دائن"}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    AmountText(
                        money = Money(netMinor, CurrencyCode.FUNCTIONAL),
                        semanticType = if (acc.isDebitNormal) AmountSemanticType.INCOME else AmountSemanticType.EXPENSE,
                        fontSize = 13
                    )
                }
            }
        }
    }
}

@Composable
fun JournalBrowserScreen(
    viewModel: InspectorViewModel,
    modifier: Modifier = Modifier
) {
    val entriesWithLines by viewModel.entriesWithLines.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = "دفتر اليومية العام (${entriesWithLines.size} قيد)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "القيود غير قابلة للتعديل أو الحذف (سجل تدقيق كامل).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { viewModel.refreshAll() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "تحديث")
                }
            }
        }

        items(entriesWithLines) { item ->
            var expanded by remember { mutableStateOf(true) }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expanded = !expanded }
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "قيد #${item.entry.entryNumber}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                StatusChip(status = item.entry.type)
                            }
                            Text(
                                text = item.entry.memo,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (expanded) {
                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.height(8.dp))

                        item.lines.forEach { line ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${line.accountCode} - ${line.memo}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    if (line.currency != "YER") {
                                        Text(
                                            text = "المبلغ الأصلي: ${Money(line.origMinor, CurrencyCode.fromString(line.currency)).format()}",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (line.baseDebitMinor > 0L) {
                                        Surface(
                                            color = SemanticIncomeGreen.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "مدين: ${Money(line.baseDebitMinor, CurrencyCode.FUNCTIONAL).format()}",
                                                color = SemanticIncomeGreen,
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    if (line.baseCreditMinor > 0L) {
                                        Surface(
                                            color = SemanticExpenseRed.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "دائن: ${Money(line.baseCreditMinor, CurrencyCode.FUNCTIONAL).format()}",
                                                color = SemanticExpenseRed,
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace,
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
        }
    }
}

@Composable
fun TrialBalanceScreen(
    viewModel: InspectorViewModel,
    modifier: Modifier = Modifier
) {
    val trialBalance by viewModel.trialBalance.collectAsState()
    val totalDebit = trialBalance.sumOf { it.totalDebitMinor }
    val totalCredit = trialBalance.sumOf { it.totalCreditMinor }
    val isBalanced = totalDebit == totalCredit

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = "ميزان المراجعة (Trial Balance)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "تطابق إجمالي المدين والدائن شرط أساسي للسلامة المحاسبية.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Grand Total Header Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isBalanced) Color(0xFF064E3B) else Color(0xFF7F1D1D)
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = if (isBalanced) "الميزان متطابق تماماً ✓" else "خلل في الميزان!",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "المدين: ${Money(totalDebit, CurrencyCode.FUNCTIONAL).format()} | الدائن: ${Money(totalCredit, CurrencyCode.FUNCTIONAL).format()}",
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        items(trialBalance.filter { it.totalDebitMinor > 0L || it.totalCreditMinor > 0L }) { row ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${row.accountCode} - ${row.accountName}",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "الرصيد الصافي: ${Money(row.netBalanceMinor, CurrencyCode.FUNCTIONAL).format()}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "مدين: ${Money(row.totalDebitMinor, CurrencyCode.FUNCTIONAL).format()}",
                            color = SemanticIncomeGreen,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "دائن: ${Money(row.totalCreditMinor, CurrencyCode.FUNCTIONAL).format()}",
                            color = SemanticExpenseRed,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}
