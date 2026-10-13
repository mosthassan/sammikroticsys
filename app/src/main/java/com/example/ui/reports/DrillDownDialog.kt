package com.example.ui.reports

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.core.model.CurrencyCode
import com.example.core.model.Money
import com.example.data.local.dao.DrillDownLineRow
import com.example.domain.report.DrillDownDocumentDetail
import com.example.domain.report.DrillDownService
import com.example.domain.report.DrillTarget
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberDarkCanvas
import com.example.ui.theme.CyberDarkCardElevated
import com.example.ui.theme.CyberDarkSurface
import com.example.ui.theme.MikroTikCyan
import com.example.ui.theme.SemanticExpenseRed
import com.example.ui.theme.SemanticIncomeGreen
import com.example.ui.theme.TextMutedDark
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark
import java.time.LocalDate

@Composable
fun DrillDownDialog(
    target: DrillTarget,
    expectedAmountMinor: Long,
    currency: CurrencyCode,
    drillDownService: DrillDownService,
    onDismiss: () -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    var lines by remember { mutableStateOf<List<DrillDownLineRow>>(emptyList()) }
    var selectedDocId by remember { mutableStateOf<String?>(null) }
    var docDetail by remember { mutableStateOf<DrillDownDocumentDetail?>(null) }

    LaunchedEffect(target) {
        isLoading = true
        lines = drillDownService.fetchDrillDownLines(target)
        isLoading = false
    }

    LaunchedEffect(selectedDocId) {
        if (selectedDocId != null) {
            docDetail = drillDownService.fetchDocumentDetails(selectedDocId!!)
        } else {
            docDetail = null
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = CyberDarkCanvas,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, CyberBorder),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = MikroTikCyan.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Layers,
                                    contentDescription = null,
                                    tint = MikroTikCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "تفاصيل الغوص في سطور الأستاذ",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimaryDark
                            )
                            val targetDesc = when (target) {
                                is DrillTarget.AccountLines -> "حساب: ${target.accountCode} (${lines.size} قيود)"
                                is DrillTarget.Document -> "مستند رقم: ${target.documentId.take(8)}"
                                is DrillTarget.EntryLines -> "قيد اليومية: ${target.entryId.take(8)}"
                            }
                            Text(
                                text = targetDesc,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondaryDark
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = TextSecondaryDark)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Reconciliation Verification Bar
                val totalDebit = lines.sumOf { it.baseDebitMinor }
                val totalCredit = lines.sumOf { it.baseCreditMinor }
                val netSum = totalDebit - totalCredit

                Surface(
                    color = CyberDarkCardElevated,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, CyberBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "المبلغ الإجمالي بالخلية:",
                                fontSize = 11.sp,
                                color = TextSecondaryDark
                            )
                            Text(
                                text = Money(expectedAmountMinor, currency).format(),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MikroTikCyan
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "مجموع سطور الأستاذ الفعلي:",
                                fontSize = 11.sp,
                                color = TextSecondaryDark
                            )
                            val isMatch = netSum == expectedAmountMinor || (totalDebit == expectedAmountMinor) || (totalCredit == expectedAmountMinor)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = Money(netSum, CurrencyCode.FUNCTIONAL).format(),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isMatch) SemanticIncomeGreen else SemanticExpenseRed
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isMatch) "✓ مطابق تماماً" else "⚠️ تفاوت",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isMatch) SemanticIncomeGreen else SemanticExpenseRed
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Content
                if (isLoading) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MikroTikCyan)
                    }
                } else if (lines.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "لا توجد قيود أستاذ مسجلة لهذا البند في الفترة المحددة",
                            color = TextSecondaryDark,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(lines) { line ->
                            Surface(
                                color = CyberDarkSurface,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(0.5.dp, CyberBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (line.docId != null) {
                                            selectedDocId = line.docId
                                        }
                                    }
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "قيد #${line.entryNumber}",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = MikroTikCyan
                                            )
                                            if (line.docNumber != null) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    color = CyberDarkCardElevated,
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(Icons.Default.Receipt, contentDescription = null, tint = TextMutedDark, modifier = Modifier.size(11.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(
                                                            text = "${line.docType ?: "مستند"} #${line.docNumber}",
                                                            fontSize = 10.sp,
                                                            color = TextSecondaryDark
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Text(
                                            text = LocalDate.ofEpochDay(line.dateEpochDay).toString(),
                                            fontSize = 11.sp,
                                            color = TextSecondaryDark
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    if (line.partyName.isNotBlank()) {
                                        Text(
                                            text = "الطرف: ${line.partyName}",
                                            fontSize = 11.sp,
                                            color = TextPrimaryDark,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }

                                    if (line.memo.isNotBlank()) {
                                        Text(
                                            text = line.memo,
                                            fontSize = 10.sp,
                                            color = TextSecondaryDark
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        if (line.currency != "YER") {
                                            Text(
                                                text = "المبلغ الأصلي: ${Money(line.origAmountMinor, CurrencyCode.fromString(line.currency)).format()}",
                                                fontSize = 10.sp,
                                                color = TextSecondaryDark
                                            )
                                        } else {
                                            Spacer(modifier = Modifier.width(1.dp))
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            if (line.baseDebitMinor > 0L) {
                                                Text(
                                                    text = "مدين: ${Money(line.baseDebitMinor, CurrencyCode.FUNCTIONAL).format()}",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = SemanticExpenseRed
                                                )
                                            }
                                            if (line.baseCreditMinor > 0L) {
                                                Text(
                                                    text = "دائن: ${Money(line.baseCreditMinor, CurrencyCode.FUNCTIONAL).format()}",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = SemanticIncomeGreen
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("إغلاق نافذة الغوص")
                }
            }
        }
    }

    // Document Details Sub-Dialog
    docDetail?.let { detail ->
        AlertDialog(
            onDismissRequest = { selectedDocId = null },
            title = {
                Text(
                    text = "تفاصيل المستند: ${detail.document?.type} #${detail.document?.docNumber}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = TextPrimaryDark
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("رقم المعرف: ${detail.document?.id}", fontSize = 11.sp, color = TextSecondaryDark)
                    Text("التاريخ: ${detail.document?.let { LocalDate.ofEpochDay(it.dateEpochDay) }}", fontSize = 12.sp, color = TextSecondaryDark)
                    Text("الإجمالي: ${detail.document?.let { Money(it.totalMinor, CurrencyCode.fromString(it.currency)).format() }}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MikroTikCyan)
                    if (detail.document?.notes?.isNotBlank() == true) {
                        Text("البيان: ${detail.document.notes}", fontSize = 11.sp, color = TextPrimaryDark)
                    }
                    HorizontalDivider(color = CyberBorder)
                    Text("بنود وأصناف المستند (${detail.items.size}):", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextPrimaryDark)
                    detail.items.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${item.quantity} × ${item.description}", fontSize = 11.sp, color = TextSecondaryDark)
                            Text(Money(item.totalMinor, CurrencyCode.FUNCTIONAL).format(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimaryDark)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedDocId = null }) {
                    Text("تم")
                }
            },
            containerColor = CyberDarkSurface,
            shape = RoundedCornerShape(14.dp)
        )
    }
}
