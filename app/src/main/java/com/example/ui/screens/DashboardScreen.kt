package com.example.ui.screens

import android.app.Activity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Money
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.CurrencyCode
import com.example.core.model.Money
import com.example.data.sync.SyncState
import com.example.ui.components.AmountSemanticType
import com.example.ui.components.AmountText
import com.example.ui.components.InvariantBanner
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatCard
import com.example.ui.theme.BrandCyanPrimary
import com.example.ui.theme.SemanticExpenseRed
import com.example.ui.theme.SemanticIncomeGreen
import com.example.util.findActivity
import androidx.compose.runtime.remember
import com.example.ui.theme.SemanticWarningAmber
import com.example.ui.viewmodel.AppViewModel
import com.example.ui.viewmodel.PeriodFilter

@Composable
fun DashboardScreen(
    viewModel: AppViewModel,
    onNavigateToQuickSale: () -> Unit,
    onNavigateToSales: () -> Unit,
    onNavigateToReceipts: () -> Unit,
    onNavigateToPayments: () -> Unit,
    onNavigateToReports: () -> Unit,
    modifier: Modifier = Modifier
) {
    val summary by viewModel.dashboardSummary.collectAsState()
    val periodFilter by viewModel.selectedPeriodFilter.collectAsState()
    val invariantResult by viewModel.invariantResult.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Invariant Status Banner
        item {
            InvariantBanner(
                isValid = invariantResult?.isValid ?: true,
                violationsCount = invariantResult?.violations?.size ?: 0,
                debitTotal = invariantResult?.trialBalanceDebit ?: 0L,
                creditTotal = invariantResult?.trialBalanceCredit ?: 0L,
                onRunCheck = { viewModel.runInvariantCheck() }
            )
        }

        // Google Account & Cloud Sync Banner
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dashboard_cloud_sync_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    BrandCyanPrimary.copy(alpha = 0.3f)
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(BrandCyanPrimary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDone,
                                    contentDescription = null,
                                    tint = BrandCyanPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "مزامنة Firebase السحابية",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = currentUser?.email ?: "mosthassan.ye@gmail.com",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = BrandCyanPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        val isSyncing = syncState is SyncState.InProgress
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSyncing) BrandCyanPrimary.copy(alpha = 0.2f) else SemanticIncomeGreen.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = if (isSyncing) "جاري الرفع..." else "سحابة نشطة",
                                color = if (isSyncing) BrandCyanPrimary else SemanticIncomeGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.syncPushToFirebase() },
                            colors = ButtonDefaults.buttonColors(containerColor = BrandCyanPrimary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_dashboard_sync_now")
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("مزامنة الآن", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                viewModel.signInWithGoogle(activity ?: context)
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_dashboard_google_login")
                        ) {
                            Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("دخول Google بنقرة", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Quick Actions Bar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onNavigateToQuickSale,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("action_quick_sale")
                ) {
                    Icon(Icons.Default.FlashOn, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("بيع سريع", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                Button(
                    onClick = onNavigateToReceipts,
                    colors = ButtonDefaults.buttonColors(containerColor = SemanticIncomeGreen),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("action_receipt_voucher")
                ) {
                    Icon(Icons.Default.TrendingUp, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("سند قبض", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                Button(
                    onClick = onNavigateToPayments,
                    colors = ButtonDefaults.buttonColors(containerColor = SemanticExpenseRed),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("action_payment_voucher")
                ) {
                    Icon(Icons.Default.TrendingDown, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("سند صرف", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        // Period Filters
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PeriodFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = periodFilter == filter,
                        onClick = { viewModel.setPeriodFilter(filter) },
                        label = { Text(filter.title, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }
        }

        // Financial KPIs Cards
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    title = "مبيعات الفترة",
                    amount = Money(summary.totalSalesPeriodMinor, CurrencyCode.FUNCTIONAL),
                    icon = Icons.Default.Receipt,
                    semanticType = AmountSemanticType.INCOME,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "مصروفات وتكاليف",
                    amount = Money(summary.totalExpensesPeriodMinor, CurrencyCode.FUNCTIONAL),
                    icon = Icons.Default.Payments,
                    semanticType = AmountSemanticType.EXPENSE,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    title = "صافي ربح النشاط",
                    amount = Money(summary.netCashFlowMinor, CurrencyCode.FUNCTIONAL),
                    icon = Icons.Default.AccountBalance,
                    semanticType = AmountSemanticType.AUTO,
                    subtitle = "وفق معيار تكلفة الخدمة IFRS",
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigateToReports() }
                )
                StatCard(
                    title = "ديون الوكلاء (الذمم)",
                    amount = Money(summary.totalReceivablesMinor, CurrencyCode.FUNCTIONAL),
                    icon = Icons.Default.Money,
                    semanticType = AmountSemanticType.AUTO,
                    subtitle = "إجمالي مستحقات الشبكة",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Multi-Currency Treasury Balances
        item {
            SectionHeader(title = "أرصدة الخزائن والصناديق")
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val yer = summary.treasuryBalancesByCurrency["YER"] ?: 0L
                    val usd = summary.treasuryBalancesByCurrency["USD"] ?: 0L
                    val sar = summary.treasuryBalancesByCurrency["SAR"] ?: 0L

                    TreasuryBalanceRow("صندوق الريال اليمني (YER)", Money(yer, CurrencyCode.YER), SemanticIncomeGreen)
                    TreasuryBalanceRow("خزينة الدولار الأمريكي (USD)", Money(usd, CurrencyCode.USD), MaterialTheme.colorScheme.primary)
                    TreasuryBalanceRow("خزينة الريال السعودي (SAR)", Money(sar, CurrencyCode.SAR), SemanticWarningAmber)
                }
            }
        }

        // Financial Trend Flow (Lightweight Canvas Chart)
        item {
            SectionHeader(title = "مؤشر التدفق المالي")
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("النشاط التشغيلي المباشر", style = MaterialTheme.typography.labelMedium)
                        Text("مباشر من دفتر الأستاذ", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    FinancialTrendCanvas(
                        sales = summary.totalSalesPeriodMinor,
                        expenses = summary.totalExpensesPeriodMinor,
                        profit = summary.netCashFlowMinor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TreasuryBalanceRow(
    title: String,
    money: Money,
    accentColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(accentColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
        AmountText(money = money, semanticType = AmountSemanticType.NEUTRAL, fontSize = 14)
    }
}

@Composable
fun FinancialTrendCanvas(
    sales: Long,
    expenses: Long,
    profit: Long,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        val maxVal = maxOf(sales, expenses, kotlin.math.abs(profit), 1000L).toFloat()
        val salesY = height - (sales.toFloat() / maxVal * (height * 0.8f))
        val expY = height - (expenses.toFloat() / maxVal * (height * 0.8f))
        val profitY = height - ((profit.toFloat() + maxVal) / (maxVal * 2f) * (height * 0.8f))

        // Draw baseline
        drawLine(
            color = Color.Gray.copy(alpha = 0.3f),
            start = Offset(0f, height - 2f),
            end = Offset(width, height - 2f),
            strokeWidth = 2f
        )

        // Draw smooth bezier curve between points
        val path = Path().apply {
            moveTo(0f, height * 0.7f)
            cubicTo(
                width * 0.3f, salesY,
                width * 0.6f, expY,
                width, profitY
            )
        }

        drawPath(
            path = path,
            color = Color(0xFF06B6D4),
            style = Stroke(width = 4f, cap = StrokeCap.Round)
        )

        // Draw indicator points
        drawCircle(color = Color(0xFF10B981), radius = 6f, center = Offset(width * 0.3f, salesY))
        drawCircle(color = Color(0xFFEF4444), radius = 6f, center = Offset(width * 0.6f, expY))
        drawCircle(color = Color(0xFF3B82F6), radius = 6f, center = Offset(width, profitY))
    }
}
