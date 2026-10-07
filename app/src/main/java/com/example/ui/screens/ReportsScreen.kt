package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.platform.LocalContext
import com.example.data.sync.SyncStatus
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

    val todayEpoch = remember { System.currentTimeMillis() / 86400000L }

    LaunchedEffect(selectedTab) {
        when (selectedTab) {
            0 -> viewModel.loadIncomeStatement(todayEpoch - 30, todayEpoch)
            1 -> viewModel.loadBalanceSheet(todayEpoch)
            2 -> viewModel.loadAgingReport(todayEpoch)
            3 -> {
                viewModel.loadIncomeStatement(todayEpoch - 30, todayEpoch)
                viewModel.loadBalanceSheet(todayEpoch)
            }
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        ModernFintechScrollableTabs(
            items = listOf(
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
                0 -> IncomeStatementView(incomeReport, onPeriodSelected = { start, end ->
                    viewModel.loadIncomeStatement(start, end)
                })
                1 -> BalanceSheetView(balanceReport, onRefresh = {
                    viewModel.loadBalanceSheet(todayEpoch)
                })
                2 -> AgingReportView(agingReport, onRefresh = {
                    viewModel.loadAgingReport(todayEpoch)
                })
                3 -> PartnerDividendsView(incomeReport, balanceReport)
                4 -> CloudSyncAndBackupView(viewModel)
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
    val todayEpoch = remember { System.currentTimeMillis() / 86400000L }

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

            // Section 4: FX Gains / Losses
            if (report.realizedFxGainMinor > 0 || report.realizedFxLossMinor > 0) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SectionHeader(title = "4. فروق العملة المحققة (IAS 21 Realized FX)")
                            if (report.realizedFxGainMinor > 0) {
                                ReportLineRow("أرباح فروق صرف العملة (4901)", report.realizedFxGainMinor, isPositive = true)
                            }
                            if (report.realizedFxLossMinor > 0) {
                                ReportLineRow("خسائر فروق صرف العملة (5901)", report.realizedFxLossMinor, isPositive = false)
                            }
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
                    ReportLineRow("أرباح الفترة الحالية", report.currentPeriodNetProfitMinor, isPositive = report.currentPeriodNetProfitMinor >= 0)
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
    balanceReport: BalanceSheetReport?
) {
    val netProfit = incomeReport?.netProfitMinor ?: 0L
    var partnerSharePercent by remember { mutableStateOf("50") }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("حاسبة توزيع أرباح الشركاء والشبكة", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "تتيح احتساب حصة كل شريك آلياً بناءً على صافي الأرباح المحققة بعد خصم التكاليف التشغيلية وعمليات السحب السابقة.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("صافي أرباح الفترة القابلة للتوزيع:", fontSize = 13.sp)
                        AmountText(
                            money = Money(netProfit, CurrencyCode.FUNCTIONAL),
                            semanticType = AmountSemanticType.INCOME,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader(title = "محاكاة التوزيع بالنسب المئوية")

                    OutlinedTextField(
                        value = partnerSharePercent,
                        onValueChange = { partnerSharePercent = it },
                        label = { Text("نسبة الشريك من رأس المال (%)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    val cleanP = partnerSharePercent.trim().replace("،", "").replace("٬", "").replace(",", "").replace("٫", ".")
                    val pParts = cleanP.split(".")
                    val pWhole = pParts.getOrNull(0)?.toLongOrNull() ?: 50L
                    val pFrac = pParts.getOrNull(1)?.padEnd(2, '0')?.take(2)?.toLongOrNull() ?: 0L
                    val pBps = (pWhole * 100L + pFrac).coerceIn(0L, 10000L)
                    val partnerShareMinor = (netProfit * pBps) / 10000L

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("الحصة المستحقة للشريك:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        AmountText(
                            money = Money(partnerShareMinor, CurrencyCode.FUNCTIONAL),
                            semanticType = AmountSemanticType.INCOME,
                            fontSize = 18,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "ملاحظة: لصرف الأرباح فعلياً، يتم تحرير سند صرف (Payment Voucher) للمسحوبات أو توزيع الأرباح ليتم إدراج قيد (DR 3201 جاري الشريك، CR 1101 الخزينة).",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
    val syncStatus by viewModel.syncStatus.collectAsState()
    val lastSyncTime by viewModel.lastSyncTimestamp.collectAsState()
    val autoSync by viewModel.autoSyncEnabled.collectAsState()

    var backupJson by remember { mutableStateOf("") }
    var restoreJsonText by remember { mutableStateOf("") }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // 1. Google Account & Multi-Tenant ID Card
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
                                    text = if (profile != null) "حساب Google ومعرّف المزامنة (Tenant ID)" else "تسجيل الدخول بحساب Google",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = TextPrimaryDark
                                )
                                Text(
                                    text = "البريد الإلكتروني هو المعرّف الوحيد لعزل وتشفير بيانات شبكتك",
                                    fontSize = 11.sp,
                                    color = TextSecondaryDark
                                )
                            }
                        }

                        if (profile != null) {
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

                    if (profile != null) {
                        // User Signed In Display
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("اسم الحساب:", color = TextSecondaryDark, fontSize = 12.sp)
                                Text(profile?.displayName ?: "", color = TextPrimaryDark, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("البريد الإلكتروني (المعرّف):", color = TextSecondaryDark, fontSize = 12.sp)
                                Text(
                                    profile?.email ?: "",
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
                                Text("مسار العزل السحابي (Firestore):", color = TextSecondaryDark, fontSize = 11.sp)
                                Text(
                                    "tenants/${viewModel.syncManager.sanitizeTenantEmail(profile?.email ?: "")}",
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
                                text = "اضغط للدخول بحساب Google بنقرة واحدة لربط قاعدة بياناتك بسحابة Firebase المخصصة لك تلقائياً:",
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
                                Text("الدخول بنقرة واحدة عبر حساب Google", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    viewModel.signInDirectWithEmail("mosthassan.ye@gmail.com", "Mostafa Hassan")
                                },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MikroTikCyan.copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("btn_reports_email_fallback")
                            ) {
                                Text("دخول فوري بالبريد: mosthassan.ye@gmail.com (بيئة الاختبار / تجاوز Google)", color = MikroTikCyan, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // 2. Firebase Cloud Sync Hub Card
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
                                Text("مركز المزامنة السحابية (Firebase Firestore)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimaryDark)
                                Text("مزامنة فورية ودقيقة للسندات، الفواتير، الأطراف، واليومية", fontSize = 11.sp, color = TextSecondaryDark)
                            }
                        }
                    }

                    // Sync Status Badge & Progress
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
                                Text("آخر مزامنة ناجحة:", color = TextSecondaryDark, fontSize = 12.sp)
                                Text(
                                    lastSyncTime ?: "لم تتم المزامنة بعد",
                                    color = if (lastSyncTime != null) StatusOnline else TextMutedDark,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }

                            when (val status = syncStatus) {
                                is SyncStatus.InProgress -> {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    LinearProgressIndicator(
                                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                        color = MikroTikCyan,
                                        trackColor = CyberBorder
                                    )
                                    Text(
                                        status.message,
                                        color = MikroTikCyan,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                is SyncStatus.Success -> {
                                    Text(
                                        "✓ اكتملت المزامنة بنجاح (${status.recordsSynced} سجل في سحابة Firebase)",
                                        color = StatusOnline,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                is SyncStatus.Error -> {
                                    Text(
                                        "⚠ ${status.errorMessage}",
                                        color = SemanticExpenseRed,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                is SyncStatus.Idle -> {
                                    Text(
                                        "الحالة: جاهز للمزامنة مع مسار المستأجر الخاص بك",
                                        color = TextSecondaryDark,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }

                    // Primary Full Cloud Sync Button
                    Button(
                        onClick = { viewModel.performCloudSync() },
                        colors = ButtonDefaults.buttonColors(containerColor = StatusOnline),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("مزامنة سحابية شاملة الآن (Full Cloud Sync)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    // Directional Push and Pull Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.performCloudSync() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MikroTikCyan.copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, tint = MikroTikCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("رفع للسحاب", color = MikroTikCyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }

                        OutlinedButton(
                            onClick = { viewModel.performCloudPull() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF3B82F6).copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, tint = Color(0xFF3B82F6), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("جلب من السحاب", color = Color(0xFF3B82F6), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    // Auto-sync Toggle Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("المزامنة التلقائية (Auto-Sync)", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = TextPrimaryDark)
                            Text("رفع السندات والفواتير والحركات إلى Firebase تلقائياً", fontSize = 10.sp, color = TextSecondaryDark)
                        }
                        Switch(
                            checked = autoSync,
                            onCheckedChange = { viewModel.setAutoSync(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = StatusOnline,
                                checkedTrackColor = StatusOnline.copy(alpha = 0.3f)
                            )
                        )
                    }
                }
            }
        }

        // 3. Local JSON Disaster Recovery Export Section
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

        // 4. Local JSON Disaster Recovery Import Section
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

    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = { Text("تأكيد استعادة البيانات") },
            text = { Text("هل أنت متأكد من استعادة هذه النسخة؟ سيتم دمج أو استبدال البيانات والتحقق من صحة دفتر الأستاذ.") },
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
