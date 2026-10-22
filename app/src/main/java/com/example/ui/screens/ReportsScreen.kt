package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberDarkCardElevated
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark
import com.example.ui.theme.TextMutedDark
import com.example.ui.theme.SemanticExpenseRed
import com.example.ui.theme.SemanticIncomeGreen
import com.example.ui.theme.SemanticWarningAmber
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Sync
import com.example.data.sync.BackupManifest
import com.example.data.sync.CompletenessReport
import com.example.data.sync.SyncState
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.platform.LocalContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.example.ui.components.FintechTabItem
import com.example.ui.components.ModernFintechScrollableTabs
import com.example.ui.theme.MikroTikCyan
import com.example.ui.theme.AssetPurple
import com.example.ui.theme.StatusOnline
import com.example.ui.theme.StatusWarning
import com.example.util.findActivity
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.CurrencyCode
import com.example.core.model.Money
import com.example.domain.usecase.AgingReport
import com.example.domain.usecase.BalanceSheetReport
import com.example.domain.usecase.IncomeStatementReport
import com.example.ui.components.AmountSemanticType
import com.example.ui.components.AmountText
import com.example.ui.components.SectionHeader
import com.example.ui.theme.BrandCyanPrimary
import com.example.ui.theme.SemanticExpenseRed
import com.example.ui.theme.SemanticIncomeGreen
import com.example.ui.theme.SemanticWarningAmber
import com.example.ui.viewmodel.AppViewModel

@Composable
fun ReportsScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val incomeReport by viewModel.incomeStatement.collectAsState()
    val balanceReport by viewModel.balanceSheet.collectAsState()
    val agingReport by viewModel.agingReport.collectAsState()

    val todayEpoch = remember { com.example.core.time.AppClock.todayEpochDay() }

    LaunchedEffect(selectedTab) {
        when (selectedTab) {
            1 -> viewModel.loadIncomeStatement(todayEpoch - 30, todayEpoch)
            2 -> viewModel.loadBalanceSheet(todayEpoch)
            3 -> viewModel.loadAgingReport(todayEpoch)
            4 -> {
                viewModel.loadIncomeStatement(todayEpoch - 30, todayEpoch)
                viewModel.loadBalanceSheet(todayEpoch)
            }
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        ModernFintechScrollableTabs(
            items = listOf(
                FintechTabItem(
                    title = "مركز التقارير المتكامل",
                    icon = Icons.Default.Analytics,
                    accentColor = MikroTikCyan
                ),
                FintechTabItem(
                    title = "قائمة الدخل (P&L)",
                    icon = Icons.Default.Analytics,
                    accentColor = StatusOnline
                ),
                FintechTabItem(
                    title = "الميزانية العمومية",
                    icon = Icons.Default.AccountBalance,
                    accentColor = MikroTikCyan
                ),
                FintechTabItem(
                    title = "أعمار الديون",
                    icon = Icons.Default.HourglassBottom,
                    accentColor = StatusWarning
                ),
                FintechTabItem(
                    title = "أرباح الشركاء",
                    icon = Icons.Default.Group,
                    accentColor = AssetPurple
                ),
                FintechTabItem(
                    title = "سحابة Firebase وحساب Google",
                    icon = Icons.Default.CloudSync,
                    accentColor = MikroTikCyan
                )
            ),
            selectedIndex = selectedTab,
            onTabSelected = { selectedTab = it }
        )

        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            when (selectedTab) {
                0 -> com.example.ui.reports.ReportsCenterScreen(viewModel = viewModel)
                1 -> IncomeStatementView(incomeReport, onPeriodSelected = { start, end ->
                    viewModel.loadIncomeStatement(start, end)
                })
                2 -> BalanceSheetView(balanceReport, onRefresh = {
                    viewModel.loadBalanceSheet(todayEpoch)
                })
                3 -> AgingReportView(agingReport, onRefresh = {
                    viewModel.loadAgingReport(todayEpoch)
                })
                4 -> PartnerDividendsView(incomeReport, balanceReport, viewModel)
                5 -> CloudSyncAndBackupView(viewModel)
            }
        }
    }
}

@Composable
private fun IncomeStatementView(
    report: IncomeStatementReport?,
    onPeriodSelected: (Long?, Long?) -> Unit
) {
    var periodMode by remember { mutableIntStateOf(1) } // 0: Today, 1: Month, 2: All time
    val todayEpoch = remember { com.example.core.time.AppClock.todayEpochDay() }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = periodMode == 0,
                    onClick = {
                        periodMode = 0
                        onPeriodSelected(todayEpoch, todayEpoch)
                    },
                    label = { Text("اليوم") }
                )
                FilterChip(
                    selected = periodMode == 1,
                    onClick = {
                        periodMode = 1
                        onPeriodSelected(todayEpoch - 30, todayEpoch)
                    },
                    label = { Text("آخر 30 يوماً") }
                )
                FilterChip(
                    selected = periodMode == 2,
                    onClick = {
                        periodMode = 2
                        onPeriodSelected(null, todayEpoch)
                    },
                    label = { Text("كل الفترات") }
                )
            }
        }

        if (report == null) {
            item {
                Text("جاري استخراج قائمة الدخل وتكلفة الخدمة...", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            // Net Profit Hero Banner
            item {
                val isProfit = report.netProfitMinor >= 0
                val netColor = if (isProfit) SemanticIncomeGreen else SemanticExpenseRed
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, netColor, RoundedCornerShape(14.dp))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isProfit) "صافي أرباح النشاط" else "صافي الخسائر للفترة",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Icon(
                                imageVector = if (isProfit) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                                contentDescription = null,
                                tint = netColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        AmountText(
                            money = Money(report.netProfitMinor, CurrencyCode.FUNCTIONAL),
                            semanticType = if (isProfit) AmountSemanticType.INCOME else AmountSemanticType.EXPENSE,
                            fontSize = 26,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        val marginPercent = if (report.netRevenueMinor > 0) {
                            (report.netProfitMinor * 100) / report.netRevenueMinor
                        } else 0
                        Text(
                            text = "هامش صافي الربح: $marginPercent% من صافي الإيرادات",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Section 1: Revenues
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionHeader(title = "1. الإيرادات التشغيلية (Revenues)")
                        ReportLineRow("مبيعات كروت المايكروتك (4101)", report.cardRevenueMinor, isPositive = true)
                        ReportLineRow("إيرادات الاشتراكات المباشرة والألياف (4201)", report.serviceRevenueMinor, isPositive = true)
                        if (report.salesReturnsMinor > 0) {
                            ReportLineRow("مردودات ومسموحات المبيعات (4102)", -report.salesReturnsMinor, isPositive = false)
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        ReportSubtotalRow("صافي الإيرادات (Net Revenue)", report.netRevenueMinor, isHighlight = true)
                    }
                }
            }

            // Section 2: Direct ISP Service Cost (Starlink / Wholesale Fiber)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionHeader(title = "2. تكلفة الخدمة المباشرة (Direct ISP Cost)")
                        Text(
                            text = "تكلفة خطوط الإنترنت الرئيسية وحزم البيانات المباشرة (Starlink، فايبر الجملة)",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        ReportLineRow("اشتراكات الإنترنت المباشرة (5101)", report.directIspCostMinor, isPositive = false)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        ReportSubtotalRow("مجمل الربح (Gross Profit)", report.grossProfitMinor, isHighlight = true)
                    }
                }
            }

            // Section 3: Operating Expenses & Depreciation
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionHeader(title = "3. المصاريف التشغيلية والإهلاك")
                        ReportLineRow("مصاريف ديزل ومولدات وكهرباء (5201)", report.operatingExpensesMinor, isPositive = false)
                        ReportLineRow("صيانة وقطع غيار وأسلاك (5202)", report.maintenanceExpensesMinor, isPositive = false)
                        ReportLineRow("رواتب فنيين وعمال الشبكة (5204)", report.salariesExpensesMinor, isPositive = false)
                        ReportLineRow("مصاريف نثرية ومتنوعة (5299)", report.miscExpensesMinor, isPositive = false)
                        ReportLineRow("إهلاك أجهزة ومعدات الشبكة (5203)", report.depreciationExpenseMinor, isPositive = false)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        val totalOpex = report.totalOperatingExpensesMinor + report.depreciationExpenseMinor
                        ReportSubtotalRow("إجمالي مصاريف التشغيل والإهلاك", totalOpex, isPositive = false)
                    }
                }
            }

            // Section 4: FX Gains / Losses (IAS 21 Realized & Unrealized FX)
            if (report.realizedFxGainMinor > 0 || report.realizedFxLossMinor > 0 || report.unrealizedFxGainMinor > 0 || report.unrealizedFxLossMinor > 0) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SectionHeader(title = "4. فروق أسعار صرف العملات (IAS 21 FX Effects)")
                            if (report.realizedFxGainMinor > 0) {
                                ReportLineRow("أرباح فروق صرف العملة المحققة (4901)", report.realizedFxGainMinor, isPositive = true)
                            }
                            if (report.realizedFxLossMinor > 0) {
                                ReportLineRow("خسائر فروق صرف العملة المحققة (5901)", report.realizedFxLossMinor, isPositive = false)
                            }
                            if (report.unrealizedFxGainMinor > 0) {
                                ReportLineRow("أرباح تقييم العملة غير المحققة (4902)", report.unrealizedFxGainMinor, isPositive = true)
                            }
                            if (report.unrealizedFxLossMinor > 0) {
                                ReportLineRow("خسائر تقييم العملة غير المحققة (5902)", report.unrealizedFxLossMinor, isPositive = false)
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            ReportSubtotalRow("صافي أثر فروق العملات (Net FX)", report.totalFxNetMinor, isHighlight = true, isPositive = report.totalFxNetMinor >= 0)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BalanceSheetView(
    report: BalanceSheetReport?,
    onRefresh: () -> Unit
) {
    if (report == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("جاري توليد الميزانية العمومية IFRS...")
        }
        return
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Balance Status Banner
        item {
            val statusColor = if (report.isBalanced) SemanticIncomeGreen else SemanticExpenseRed
            Card(
                colors = CardDefaults.cardColors(containerColor = statusColor.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, statusColor, RoundedCornerShape(12.dp))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (report.isBalanced) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (report.isBalanced) "الميزانية متطابقة رياضياً (IFRS متوافقة)" else "تنبيه: عدم اتزان بين الأصول والخصوم",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = statusColor
                        )
                        Text(
                            text = "إجمالي الأصول = إجمالي الخصوم وحقوق الملكية (${Money(report.totalAssetsMinor, CurrencyCode.FUNCTIONAL).format()})",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "تحديث")
                    }
                }
            }
        }

        // 1. Assets (الأصول)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader(title = "الأصول (Assets)")

                    Text("الأصول المتداولة (Current Assets):", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = BrandCyanPrimary)
                    ReportLineRow("النقدية بالصناديق والبنوك والمحافظ (1101 / 1102)", report.currentAssetsMinor, isPositive = true)
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("الأصول غير المتداولة (Fixed Assets):", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = BrandCyanPrimary)
                    ReportLineRow("تكلفة أجهزة وأبراج الشبكة (1501)", report.fixedAssetsCostMinor, isPositive = true)
                    ReportLineRow("مجمع إهلاك الأصول (1599)", -report.accumulatedDepreciationMinor, isPositive = false)
                    ReportSubtotalRow("صافي الأصول الثابتة الدفترية", report.netFixedAssetsMinor, isHighlight = false)

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    ReportSubtotalRow("إجمالي الأصول (Total Assets)", report.totalAssetsMinor, isHighlight = true)
                }
            }
        }

        // 2. Liabilities (الخصوم)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader(title = "الخصوم والالتزامات (Liabilities)")
                    ReportLineRow("أرصدة الموردين ومزودي الخدمة (2101)", report.currentLiabilitiesMinor, isPositive = false)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    ReportSubtotalRow("إجمالي الخصوم (Total Liabilities)", report.totalLiabilitiesMinor, isHighlight = false)
                }
            }
        }

        // 3. Equity (حقوق الملكية)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader(title = "حقوق الملكية (Equity)")
                    ReportLineRow("رأس المال التأسيسي (3101)", report.capitalMinor, isPositive = true)
                    ReportLineRow("جاري الشركاء والمساهمين (3201)", report.partnerCurrentMinor, isPositive = true)
                    ReportLineRow("الأرباح المحتجزة / المرحّلة (3301)", report.retainedEarningsMinor, isPositive = true)
                    ReportLineRow("أرباح / خسائر غير مقفلة (Unclosed Profit/Loss)", report.unclosedProfitMinor, isPositive = report.unclosedProfitMinor >= 0)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    ReportSubtotalRow("إجمالي حقوق الملكية (Total Equity)", report.totalEquityMinor, isHighlight = false)
                }
            }
        }

        // Total Liabilities & Equity Check
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    ReportSubtotalRow("إجمالي الخصوم وحقوق الملكية", report.totalLiabilitiesAndEquityMinor, isHighlight = true)
                }
            }
        }
    }
}

@Composable
private fun AgingReportView(
    report: AgingReport?,
    onRefresh: () -> Unit
) {
    if (report == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("جاري استخراج تقرير أعمار الديون...")
        }
        return
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("إجمالي المديونيات المعلقة:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AmountText(
                        money = Money(report.totalReceivablesMinor, CurrencyCode.FUNCTIONAL),
                        semanticType = AmountSemanticType.INCOME,
                        fontSize = 22,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "تحديث")
                }
            }
        }

        item {
            AgingBucketCard(
                title = report.current0to30.bucketName,
                amountMinor = report.current0to30.amountMinor,
                count = report.current0to30.invoiceCount,
                color = SemanticIncomeGreen,
                description = "فواتير نظامية ضمن المهلة الائتمانية"
            )
        }

        item {
            AgingBucketCard(
                title = report.aging31to60.bucketName,
                amountMinor = report.aging31to60.amountMinor,
                count = report.aging31to60.invoiceCount,
                color = BrandCyanPrimary,
                description = "فواتير تحتاج متابعة أولية مع الوكلاء"
            )
        }

        item {
            AgingBucketCard(
                title = report.aging61to90.bucketName,
                amountMinor = report.aging61to90.amountMinor,
                count = report.aging61to90.invoiceCount,
                color = SemanticWarningAmber,
                description = "فواتير متأخرة تتطلب إيقاف منح كروت جديدة"
            )
        }

        item {
            AgingBucketCard(
                title = report.agingOver90.bucketName,
                amountMinor = report.agingOver90.amountMinor,
                count = report.agingOver90.invoiceCount,
                color = SemanticExpenseRed,
                description = "فواتير حرجة جداً معرضة لمخصص الديون المشكوك فيها"
            )
        }
    }
}

@Composable
private fun AgingBucketCard(
    title: String,
    amountMinor: Long,
    count: Int,
    color: Color,
    description: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                            .background(color)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Surface(
                    color = color.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "$count فاتورة",
                        fontSize = 11.sp,
                        color = color,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            AmountText(
                money = Money(amountMinor, CurrencyCode.FUNCTIONAL),
                semanticType = AmountSemanticType.NEUTRAL,
                fontSize = 18,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PartnerDividendsView(
    incomeReport: IncomeStatementReport?,
    balanceReport: BalanceSheetReport?,
    viewModel: AppViewModel
) {
    val netProfit = incomeReport?.netProfitMinor ?: 0L
    val org by viewModel.organization.collectAsState(initial = null)
    val partners by viewModel.allPartners.collectAsState(initial = emptyList())
    val activePartners = remember(partners) { partners.filter { it.isPartner && it.isActive } }

    val currentMode = com.example.core.model.EquityShareMode.fromString(org?.equityShareMode)

    var equitySummary by remember { mutableStateOf<com.example.domain.usecase.PartnerEquitySummary?>(null) }
    LaunchedEffect(activePartners, currentMode) {
        equitySummary = viewModel.partnerEquityUseCase.getEquitySummary()
    }

    var profitToDistributeText by remember(netProfit) {
        mutableStateOf(if (netProfit > 0) (netProfit / 100L).toString() else "0")
    }
    var distributionNotes by remember { mutableStateOf("توزيع أرباح دوري وفق معايير المحاسبة IFRS") }
    var isPosting by remember { mutableStateOf(false) }

    val parsedProfitMinor = (profitToDistributeText.toLongOrNull() ?: 0L) * 100L

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Hero Information Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("نظام توزيع أرباح الشركاء (Hare-Niemeyer)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "تطبيق رياضي صارم لطريقة أكبر البواقي يضمن صفر بواقي تقريب وتوزيع الأرباح بالريال الكامل إلى جاري الشركاء (3201).",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("صافي أرباح الفترة المحققة:", fontSize = 13.sp)
                        AmountText(
                            money = Money(netProfit, CurrencyCode.FUNCTIONAL),
                            semanticType = AmountSemanticType.INCOME,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Equity Mode Switcher
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader(title = "نمط احتساب حصص الملكية (Equity Share Mode)")
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        com.example.core.model.EquityShareMode.values().forEach { mode ->
                            val isSelected = currentMode == mode
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { viewModel.setEquityShareMode(mode) }
                                    .border(
                                        BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                                        RoundedCornerShape(10.dp)
                                    ),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = mode.title,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 11.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Profit Distribution Configuration & Partners Simulation
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader(title = "المبلغ المراد توزيعه بالريال اليمني (YER)")

                    OutlinedTextField(
                        value = profitToDistributeText,
                        onValueChange = { profitToDistributeText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("المبلغ الإجمالي للتوزيع (DR 3301 / CR 3201)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = distributionNotes,
                        onValueChange = { distributionNotes = it },
                        label = { Text("بيان قيد توزيع الأرباح") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Text("محاكاة الحصص لجميع الشركاء النشطين (${activePartners.size}):", fontWeight = FontWeight.Bold, fontSize = 13.sp)

                    if (activePartners.isEmpty()) {
                        Text("لا يوجد شركاء نشطون مسجلون في النظام.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    } else {
                        // Compute shares using PartnerEquityUseCase Hare-Niemeyer logic
                        val weights = activePartners.map { p ->
                            val weight = if (currentMode == com.example.core.model.EquityShareMode.DERIVED_FROM_CAPITAL) {
                                // Dynamic capital weight
                                val cap = equitySummary?.partners?.find { it.partnerId == p.id }?.historicalCapitalMinor ?: 0L
                                if (cap > 0L) cap else 1L
                            } else {
                                p.equityPercentageBasisPoints.toLong().coerceAtLeast(1L)
                            }
                            com.example.domain.usecase.PartnerWeight(p.id, p.name, weight)
                        }

                        val computedShares = if (parsedProfitMinor > 0L && weights.sumOf { it.weight } > 0L) {
                            try {
                                viewModel.partnerEquityUseCase.calculateHareNiemeyerDistribution(parsedProfitMinor, weights)
                            } catch (e: Exception) {
                                emptyList()
                            }
                        } else emptyList()

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            activePartners.forEach { partner ->
                                val share = computedShares.find { it.partnerPartyId == partner.id }
                                val shareAmountMinor = share?.amountMinor ?: 0L

                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(partner.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            val pct = partner.equityPercentageBasisPoints / 100.0
                                            Text(
                                                if (currentMode == com.example.core.model.EquityShareMode.DERIVED_FROM_CAPITAL) "حصة ديناميكية من رأس المال" else "نسبة متفق عليها: $pct%",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        AmountText(
                                            money = Money(shareAmountMinor, CurrencyCode.FUNCTIONAL),
                                            semanticType = AmountSemanticType.INCOME,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = {
                                if (parsedProfitMinor > 0L) {
                                    isPosting = true
                                    val year = com.example.core.time.AppClock.todayLocalDate().year
                                    viewModel.executeProfitDistribution(
                                        fiscalYear = year,
                                        totalProfitMinor = parsedProfitMinor,
                                        notes = distributionNotes,
                                        onSuccess = { isPosting = false }
                                    )
                                }
                            },
                            enabled = parsedProfitMinor > 0L && !isPosting,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("ترحيل سند توزيع الأرباح إلى جاري الشركاء (DR 3301 / CR 3201)", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CloudSyncAndBackupView(viewModel: AppViewModel) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val profile by viewModel.currentUserProfile.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val syncMetadata by viewModel.syncMetadata.collectAsState()
    val backupsList by viewModel.cloudBackupsList.collectAsState()
    val conflictWarning by viewModel.conflictWarning.collectAsState()
    val completenessReport by viewModel.completenessReport.collectAsState()

    var backupJson by remember { mutableStateOf("") }
    var restoreJsonText by remember { mutableStateOf("") }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    var backupToRestore by remember { mutableStateOf<BackupManifest?>(null) }
    var backupToDelete by remember { mutableStateOf<BackupManifest?>(null) }
    var restorePassphrase by remember { mutableStateOf("") }

    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()) }

    LaunchedEffect(Unit) {
        viewModel.fetchCloudBackupsList()
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // 1. Google Account & Cloud Backup Path Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.dp, MikroTikCyan.copy(alpha = 0.5f)), RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(MikroTikCyan.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MikroTikCyan,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (currentUser != null) "حساب النسخ الاحتياطي السحابي" else "تسجيل الدخول السحابي",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = TextPrimaryDark
                                )
                                Text(
                                    text = "عزل مشفر للبيانات تحت معرف حسابك في السحابة",
                                    fontSize = 11.sp,
                                    color = TextSecondaryDark
                                )
                            }
                        }

                        if (currentUser != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = StatusOnline.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    "متصل ✓",
                                    color = StatusOnline,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = CyberBorder)

                    if (currentUser != null) {
                        // User Signed In Display
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("اسم الحساب:", color = TextSecondaryDark, fontSize = 12.sp)
                                Text(currentUser?.displayName ?: profile?.displayName ?: "", color = TextPrimaryDark, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("البريد الإلكتروني:", color = TextSecondaryDark, fontSize = 12.sp)
                                Text(
                                    currentUser?.email ?: profile?.email ?: "",
                                    color = MikroTikCyan,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("مسار النسخ السحابي:", color = TextSecondaryDark, fontSize = 11.sp)
                                Text(
                                    "users/${currentUser?.uid ?: ""}/backups",
                                    color = TextMutedDark,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedButton(
                                onClick = { viewModel.signOutGoogle() },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = SemanticExpenseRed),
                                border = BorderStroke(1.dp, SemanticExpenseRed.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("تسجيل الخروج / تبديل الحساب")
                            }
                        }
                    } else {
                        // User Not Signed In - Prominent One-Tap Google Options
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "اضغط للدخول بحساب Google لربط النسخ الاحتياطي السحابي الخاص بك:",
                                fontSize = 12.sp,
                                color = TextSecondaryDark
                            )

                            Button(
                                onClick = {
                                    val activity = context.findActivity()
                                    if (activity != null) {
                                        viewModel.signInWithGoogle(activity)
                                    } else {
                                        viewModel.signInWithGoogleOneTap(context)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MikroTikCyan),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("btn_reports_google_one_tap")
                            ) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("الدخول بحساب Google", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }

        // 2. Multi-Device Conflict Warning Banner
        conflictWarning?.let { warning ->
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SemanticWarningAmber.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SemanticWarningAmber, RoundedCornerShape(14.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = SemanticWarningAmber, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("حارس التعارض السحابي (Conflict Guard)", color = SemanticWarningAmber, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Text(
                            text = warning,
                            color = TextPrimaryDark,
                            fontSize = 12.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.uploadChunkedBackup(forceOverwrite = true) },
                                colors = ButtonDefaults.buttonColors(containerColor = SemanticWarningAmber),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("استبدال بالقوة (Force Upload)", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { viewModel.dismissConflictWarning() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("تراجع", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        // 3. Chunked Cloud Backup Hub Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.dp, CyberBorder), RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                                    .background(StatusOnline.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.CloudSync, contentDescription = null, tint = StatusOnline, modifier = Modifier.size(22.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("نسخ احتياطي سحابي مقطّع (Chunked Cloud Backup)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimaryDark)
                                Text("تجزئة مشفرة (≤ 500KB) مع ضغط Gzip وضمان سلامة IFRS", fontSize = 11.sp, color = TextSecondaryDark)
                            }
                        }

                        IconButton(onClick = { viewModel.fetchCloudBackupsList() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "تحديث", tint = MikroTikCyan)
                        }
                    }

                    // Sync State & Progress Display
                    Surface(
                        color = CyberDarkCardElevated,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("حالة النسخ:", color = TextSecondaryDark, fontSize = 12.sp)
                                val lastBackupTime = syncMetadata?.lastSyncedAt ?: 0L
                                Text(
                                    if (lastBackupTime > 0L) dateFormat.format(Date(lastBackupTime)) else "لا توجد نسخ سابقة",
                                    color = if (lastBackupTime > 0L) StatusOnline else TextMutedDark,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }

                            when (val state = syncState) {
                                is SyncState.InProgress -> {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "جارِ المعالجة المشفرة",
                                            color = MikroTikCyan,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text("${(state.progress * 100).toInt()}%", color = MikroTikCyan, fontSize = 11.sp)
                                    }
                                    LinearProgressIndicator(
                                        progress = { state.progress },
                                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                        color = MikroTikCyan,
                                        trackColor = CyberBorder
                                    )
                                    Text(
                                        state.message,
                                        color = TextSecondaryDark,
                                        fontSize = 11.sp
                                    )
                                }
                                is SyncState.Success -> {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusOnline, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            state.message,
                                            color = StatusOnline,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                is SyncState.Error -> {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Warning, contentDescription = null, tint = SemanticExpenseRed, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            state.message,
                                            color = SemanticExpenseRed,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                is SyncState.Idle -> {
                                    Text(
                                        "الحالة: جاهز لرفع نسخة سحابية جديدة أو الاستعادة",
                                        color = TextSecondaryDark,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }

                    // Upload Button
                    Button(
                        onClick = { viewModel.uploadChunkedBackup() },
                        enabled = syncState !is SyncState.InProgress,
                        colors = ButtonDefaults.buttonColors(containerColor = StatusOnline),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_reports_upload_chunked_backup")
                    ) {
                        if (syncState is SyncState.InProgress) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("جارِ الرفع المشفر...", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        } else {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("رفع نسخة احتياطية سحابية الآن (Cloud Backup)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        // 4. Last 5 Cloud Backups List
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.dp, CyberBorder), RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "قائمة آخر 5 نسخ سحابية",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = TextPrimaryDark
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MikroTikCyan.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "${backupsList.size} من 5",
                                color = MikroTikCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (backupsList.isEmpty()) {
                        Surface(
                            color = CyberDarkCardElevated,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("لا توجد نسخ احتياطية سحابية محفوظة بعد", color = TextSecondaryDark, fontSize = 12.sp)
                                Text("اضغط على 'رفع نسخة احتياطية سحابية الآن' لإنشاء أول نسخة.", color = TextMutedDark, fontSize = 11.sp)
                            }
                        }
                    } else {
                        backupsList.forEach { backup ->
                            Surface(
                                color = CyberDarkCardElevated,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, CyberBorder, RoundedCornerShape(12.dp))
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = dateFormat.format(Date(backup.createdAt)),
                                                color = TextPrimaryDark,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                text = "${backup.deviceId} • v${backup.appVersion}",
                                                color = TextMutedDark,
                                                fontSize = 10.sp
                                            )
                                        }

                                        if (backup.isEncrypted) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = SemanticWarningAmber.copy(alpha = 0.2f)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(Icons.Default.Lock, contentDescription = null, tint = SemanticWarningAmber, modifier = Modifier.size(12.dp))
                                                    Spacer(modifier = Modifier.width(3.dp))
                                                    Text("مشفرة", color = SemanticWarningAmber, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "السندات: ${backup.documentsCount} • القيود: ${backup.journalLinesCount} • الأطراف: ${backup.partiesCount}",
                                            color = TextSecondaryDark,
                                            fontSize = 11.sp
                                        )
                                        Text(
                                            "${backup.chunkCount} أجزاء",
                                            color = MikroTikCyan,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 11.sp
                                        )
                                    }

                                    HorizontalDivider(color = CyberBorder.copy(alpha = 0.5f))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = { viewModel.inspectBackupCompleteness(backup) },
                                            modifier = Modifier.weight(1.2f),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                                        ) {
                                            Icon(Icons.Default.Assessment, contentDescription = null, modifier = Modifier.size(14.dp), tint = MikroTikCyan)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("تقرير الاكتمال", fontSize = 11.sp, color = MikroTikCyan)
                                        }

                                        Button(
                                            onClick = {
                                                restorePassphrase = ""
                                                backupToRestore = backup
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = SemanticIncomeGreen),
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                                        ) {
                                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("استعادة", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }

                                        OutlinedButton(
                                            onClick = { backupToDelete = backup },
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = SemanticExpenseRed),
                                            border = BorderStroke(1.dp, SemanticExpenseRed.copy(alpha = 0.5f)),
                                            modifier = Modifier.weight(0.8f),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text("حذف", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Local JSON Disaster Recovery Export Section
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader(title = "تصدير نسخة احتياطية محلية (JSON Export)")
                    Text(
                        "يشمل التصدير: دليل الحسابات، الأطراف والوكلاء، الباقات، الخزائن، الفواتير، السندات، وسجل اليومية الكامل.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = {
                            viewModel.exportBackup { json ->
                                backupJson = json
                                clipboard.setText(AnnotatedString(json))
                                statusMessage = "تم إنشاء النسخة ونسخها إلى الحافظة بنجاح (${json.length} حرف)"
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تصدير الآن ونسخ إلى الحافظة")
                    }

                    if (backupJson.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = backupJson.take(250) + "...",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }
        }

        // 6. Local JSON Disaster Recovery Import Section
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader(title = "استعادة نسخة احتياطية محلية (JSON Restore)")
                    Text(
                        "تنبيه: ستخضع النسخة المسترجعة لفحص التوازن الرياضي IFRS تلقائياً قبل الحفظ.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = restoreJsonText,
                        onValueChange = { restoreJsonText = it },
                        label = { Text("الصق نص النسخة الاحتياطية هنا") },
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedButton(
                        onClick = { showRestoreDialog = true },
                        enabled = restoreJsonText.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("بدء استعادة النسخة")
                    }
                }
            }
        }

        statusMessage?.let { msg ->
            item {
                Surface(
                    color = SemanticIncomeGreen.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = msg,
                        color = SemanticIncomeGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }

    // Completeness Report Dialog
    completenessReport?.let { report ->
        AlertDialog(
            onDismissRequest = { viewModel.clearCompletenessReport() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Assessment, contentDescription = null, tint = MikroTikCyan, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("تقرير اكتمال النسخة السحابية", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "فحص مقارنة السجلات المحلية مقابل محتويات النسخة السحابية:",
                        fontSize = 12.sp,
                        color = TextSecondaryDark
                    )
                    HorizontalDivider(color = CyberBorder)

                    report.items.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(item.tableName, fontSize = 12.sp, color = TextPrimaryDark)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${item.localCount} محلي / ${item.backupCount} سحابي",
                                    fontSize = 11.sp,
                                    color = if (item.isMatch) StatusOnline else SemanticWarningAmber,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                if (item.isMatch) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusOnline, modifier = Modifier.size(14.dp))
                                } else {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = SemanticWarningAmber, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = CyberBorder)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("إجمالي السجلات:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(
                            "${report.totalLocalRecords} محلي • ${report.totalBackupRecords} سحابي",
                            fontWeight = FontWeight.Bold,
                            color = MikroTikCyan,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.clearCompletenessReport() },
                    colors = ButtonDefaults.buttonColors(containerColor = MikroTikCyan)
                ) {
                    Text("إغلاق", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Cloud Restore Confirmation Dialog
    backupToRestore?.let { backup ->
        AlertDialog(
            onDismissRequest = { backupToRestore = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, tint = SemanticIncomeGreen, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("تأكيد استعادة النسخة السحابية", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "تنبيه: سيتم استبدال البيانات المحلية الحالية بالكامل بالبيانات الواردة في هذه النسخة السحابية المؤرخة في ${dateFormat.format(Date(backup.createdAt))}. سيتم فحص التوازن المحاسبي الصارم قبل اعتماد الحفظ.",
                        fontSize = 12.sp
                    )

                    if (backup.isEncrypted) {
                        OutlinedTextField(
                            value = restorePassphrase,
                            onValueChange = { restorePassphrase = it },
                            label = { Text("كلمة مرور فك التشفير") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val id = backup.backupId
                        backupToRestore = null
                        viewModel.restoreChunkedBackup(id, restorePassphrase.ifBlank { null })
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SemanticIncomeGreen)
                ) {
                    Text("تأكيد الاستعادة الآن", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { backupToRestore = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Cloud Delete Confirmation Dialog
    backupToDelete?.let { backup ->
        AlertDialog(
            onDismissRequest = { backupToDelete = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = SemanticExpenseRed, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("تأكيد حذف النسخة السحابية", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    "هل أنت متأكد من حذف هذه النسخة الاحتياطية (${dateFormat.format(Date(backup.createdAt))}) من سحابة Firebase نهائياً؟ هذا الإجراء لا يمكن التراجع عنه.",
                    fontSize = 12.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val id = backup.backupId
                        backupToDelete = null
                        viewModel.deleteChunkedBackup(id)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SemanticExpenseRed)
                ) {
                    Text("تأكيد الحذف", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { backupToDelete = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Local JSON Restore Dialog
    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = { Text("تأكيد استعادة البيانات المحلية") },
            text = { Text("هل أنت متأكد من استعادة هذه النسخة المحلية؟ سيتم دمج أو استبدال البيانات والتحقق من صحة دفتر الأستاذ.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.restoreBackup(restoreJsonText) {
                            showRestoreDialog = false
                            statusMessage = "تمت استعادة البيانات وتحديث أرصدة الخزائن بنجاح"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SemanticWarningAmber)
                ) { Text("تأكيد الاستعادة") }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) { Text("إلغاء") }
            }
        )
    }
}

@Composable
private fun ReportLineRow(
    title: String,
    amountMinor: Long,
    isPositive: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
        AmountText(
            money = Money(if (isPositive) amountMinor else -amountMinor, CurrencyCode.FUNCTIONAL),
            semanticType = if (isPositive) AmountSemanticType.INCOME else AmountSemanticType.EXPENSE,
            fontSize = 13,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ReportSubtotalRow(
    title: String,
    amountMinor: Long,
    isHighlight: Boolean = false,
    isPositive: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontWeight = if (isHighlight) FontWeight.Bold else FontWeight.SemiBold,
            fontSize = if (isHighlight) 15.sp else 13.sp,
            color = if (isHighlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
        AmountText(
            money = Money(if (isPositive) amountMinor else -amountMinor, CurrencyCode.FUNCTIONAL),
            semanticType = if (isHighlight) AmountSemanticType.INCOME else AmountSemanticType.NEUTRAL,
            fontSize = if (isHighlight) 16 else 14,
            fontWeight = FontWeight.Bold
        )
    }
}
