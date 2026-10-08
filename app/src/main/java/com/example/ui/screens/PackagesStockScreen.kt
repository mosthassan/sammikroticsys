package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.LinearScale
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.CurrencyCode
import com.example.core.model.Money
import com.example.core.model.UuidUtils
import com.example.data.local.entity.CardPackageEntity
import com.example.ui.components.AddManualCardsCountDialog
import com.example.ui.components.FintechTabItem
import com.example.ui.components.ModernFintechSegmentedTabs
import com.example.ui.theme.AssetPurple
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberBorderGlow
import com.example.ui.theme.CyberDarkCanvas
import com.example.ui.theme.CyberDarkCardElevated
import com.example.ui.theme.CyberDarkSurface
import com.example.ui.theme.MikroTikCyan
import com.example.ui.theme.MikroTikNavyLight
import com.example.ui.theme.MikroTikPrimary
import com.example.ui.theme.SemanticExpenseRed
import com.example.ui.theme.SemanticIncomeGreen
import com.example.ui.theme.SemanticWarningAmber
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.TextMutedDark
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark
import com.example.ui.viewmodel.AppViewModel

@Composable
fun PackagesStockScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val packages by viewModel.allPackages.collectAsState()
    val movements by viewModel.allStockMovements.collectAsState()
    val assets by viewModel.allAssets.collectAsState()

    // 0: كروت وباقات الشبكة, 1: أجهزة ومعدات الشبكة, 2: مستلزمات واستهلاكية, 3: سجل الحركات
    var selectedCategoryTab by rememberSaveable { mutableIntStateOf(0) }
    var showAddPackageSheet by remember { mutableStateOf(false) }
    var showReceiveStockSheet by remember { mutableStateOf(false) }
    var showAdjustStockSheet by remember { mutableStateOf(false) }
    var packageForAdjust by remember { mutableStateOf<CardPackageEntity?>(null) }
    var packageForPriceEdit by remember { mutableStateOf<CardPackageEntity?>(null) }
    var selectedPackageForHistory by remember { mutableStateOf<CardPackageEntity?>(null) }

    val packageMap = remember(packages) { packages.associateBy { it.id } }

    // Map of packageId to current stock balance
    val stockBalanceMap = remember { mutableStateMapOf<String, Int>() }
    LaunchedEffect(packages, movements) {
        packages.forEach { pkg ->
            stockBalanceMap[pkg.id] = viewModel.db.cardPackageDao().getStockBalance(pkg.id)
        }
    }

    // Executive KPI Dashboard Computations
    val totalInventoryValueYer = remember(packages, stockBalanceMap) {
        packages.sumOf { pkg ->
            val bal = stockBalanceMap[pkg.id] ?: 0
            bal * pkg.wholesalePriceMinor
        }
    }

    val totalActiveVoucherBatches = remember(packages, stockBalanceMap) {
        packages.count { (stockBalanceMap[it.id] ?: 0) > 0 }
    }

    val lowStockPackagesCount = remember(packages, stockBalanceMap) {
        packages.count {
            val bal = stockBalanceMap[it.id] ?: 0
            bal in 1..10
        }
    }

    val exhaustedStockPackagesCount = remember(packages, stockBalanceMap) {
        packages.count { (stockBalanceMap[it.id] ?: 0) <= 0 }
    }

    Scaffold(
        floatingActionButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FloatingActionButton(
                    onClick = { showReceiveStockSheet = true },
                    containerColor = SemanticIncomeGreen,
                    contentColor = CyberDarkCanvas,
                    modifier = Modifier.testTag("fab_receive_stock")
                ) {
                    Row(modifier = Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Inventory, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("استلام كروت", fontWeight = FontWeight.Bold)
                    }
                }

                FloatingActionButton(
                    onClick = { showAddPackageSheet = true },
                    containerColor = MikroTikCyan,
                    contentColor = CyberDarkCanvas,
                    modifier = Modifier.testTag("fab_add_package")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "باقة جديدة")
                }
            }
        },
        containerColor = CyberDarkCanvas,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // EXECUTIVE KPI DASHBOARD (Top Metric Cards)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Card 1: Total Inventory Value
                Surface(
                    color = CyberDarkSurface,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MikroTikCyan.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .weight(1.3f)
                        .testTag("kpi_inventory_value")
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Inventory, contentDescription = null, tint = MikroTikCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("قيمة المخزون (YER)", fontSize = 10.sp, color = TextSecondaryDark, maxLines = 1)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = Money(totalInventoryValueYer, CurrencyCode.YER).format(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MikroTikCyan,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "بسعر الجملة للبقالات",
                            fontSize = 9.sp,
                            color = TextMutedDark
                        )
                    }
                }

                // Card 2: Active Batches
                Surface(
                    color = CyberDarkSurface,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, CyberBorder),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("kpi_active_batches")
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Memory, contentDescription = null, tint = SemanticIncomeGreen, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("الباقات النشطة", fontSize = 10.sp, color = TextSecondaryDark, maxLines = 1)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "$totalActiveVoucherBatches / ${packages.size}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = TextPrimaryDark,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "باقة متوفرة للبيع",
                            fontSize = 9.sp,
                            color = TextMutedDark
                        )
                    }
                }

                // Card 3: Low Stock / Reorder Point Alert Badge
                val hasAlert = lowStockPackagesCount > 0 || exhaustedStockPackagesCount > 0
                val alertColor = if (exhaustedStockPackagesCount > 0) SemanticExpenseRed else if (lowStockPackagesCount > 0) SemanticWarningAmber else SemanticIncomeGreen
                Surface(
                    color = alertColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, alertColor.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .weight(1.1f)
                        .testTag("kpi_stock_alerts")
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (hasAlert) Icons.Default.NotificationsActive else Icons.Default.Inventory,
                                contentDescription = null,
                                tint = alertColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("تنبيهات النواقص", fontSize = 10.sp, color = alertColor, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (hasAlert) "${lowStockPackagesCount + exhaustedStockPackagesCount} صنف" else "المخزون كافٍ ✓",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = alertColor
                        )
                        Text(
                            text = if (exhaustedStockPackagesCount > 0) "$exhaustedStockPackagesCount نفذت بالكامل!" else if (lowStockPackagesCount > 0) "$lowStockPackagesCount أوشكت على النفاد" else "مستوى آمن",
                            fontSize = 9.sp,
                            color = alertColor,
                            maxLines = 1
                        )
                    }
                }
            }

            // CATEGORIZED TABS & SMART FILTERING
            ModernFintechSegmentedTabs(
                items = listOf(
                    FintechTabItem(
                        title = "كروت وباقات الشبكة",
                        icon = Icons.Default.Inventory,
                        count = packages.size,
                        accentColor = MikroTikCyan
                    ),
                    FintechTabItem(
                        title = "أجهزة ومعدات الشبكة",
                        icon = Icons.Default.Router,
                        count = assets.size,
                        accentColor = AssetPurple
                    ),
                    FintechTabItem(
                        title = "مستلزمات واستهلاكية",
                        icon = Icons.Default.Cable,
                        count = 0,
                        accentColor = StatusWarning
                    ),
                    FintechTabItem(
                        title = "حركات المخزون",
                        icon = Icons.Default.History,
                        count = movements.size,
                        accentColor = SemanticIncomeGreen
                    )
                ),
                selectedIndex = selectedCategoryTab,
                onTabSelected = { selectedCategoryTab = it }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // TAB 1: كروت وباقات الشبكة (Hotspot Voucher Batches)
            when (selectedCategoryTab) {
                0 -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (packages.isEmpty()) {
                            item {
                                Surface(
                                    color = CyberDarkSurface,
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, CyberBorder),
                                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(32.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.Inventory, contentDescription = null, tint = TextMutedDark, modifier = Modifier.size(48.dp))
                                        Text("لا توجد باقات كروت مضافة في المستودع", fontWeight = FontWeight.Bold, color = TextPrimaryDark)
                                        Text("أضف باقات جديدة وحدد سعر الجملة والتجزئة لبدء التوليد والتوزيع.", fontSize = 12.sp, color = TextSecondaryDark)
                                    }
                                }
                            }
                        }

                        items(packages) { pkg ->
                            val stockBalance = stockBalanceMap[pkg.id] ?: 0

                            // Sold count from movements
                            val soldCount = remember(movements, pkg.id) {
                                movements.filter { it.packageId == pkg.id && it.type == "SELL" }.sumOf { it.quantity }
                            }

                            // Stock status: optimal (>25), low (1..25), exhausted (<=0)
                            val (statusColor, statusText, progress) = when {
                                stockBalance > 25 -> Triple(SemanticIncomeGreen, "متوفر ومثالي", (stockBalance.coerceAtMost(100) / 100f))
                                stockBalance > 0 -> Triple(SemanticWarningAmber, "منخفض (إعادة طلب)", (stockBalance / 25f).coerceIn(0.1f, 1f))
                                else -> Triple(SemanticExpenseRed, "نافد تماماً (صفر)", 0.02f)
                            }

                            // RICH INVENTORY CARD
                            Card(
                                colors = CardDefaults.cardColors(containerColor = CyberDarkSurface),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, if (stockBalance == 0) SemanticExpenseRed.copy(alpha = 0.5f) else CyberBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("package_inventory_card_${pkg.id}")
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    // Row 1: Header + Badges
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                color = MikroTikNavyLight,
                                                shape = RoundedCornerShape(10.dp),
                                                border = BorderStroke(1.dp, MikroTikCyan.copy(alpha = 0.3f)),
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(Icons.Default.Memory, contentDescription = null, tint = MikroTikCyan, modifier = Modifier.size(20.dp))
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(pkg.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimaryDark)
                                                Text("سعة وصلاحية: ${pkg.durationOrQuota}", fontSize = 11.sp, color = TextSecondaryDark)
                                            }
                                        }

                                        // High-contrast stock level badge
                                        Surface(
                                            color = statusColor.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .background(statusColor, CircleShape)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "$stockBalance كرت",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = statusColor,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                        }
                                    }

                                    // Row 2: Stock Level Progress Indicator (Green / Amber / Red)
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(text = "مستوى التوفر: $statusText", fontSize = 10.sp, color = statusColor, fontWeight = FontWeight.SemiBold)
                                            Text(text = "إجمالي المبيعات السابقة: $soldCount كرت", fontSize = 10.sp, color = TextMutedDark)
                                        }
                                        LinearProgressIndicator(
                                            progress = { progress },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(3.dp)),
                                            color = statusColor,
                                            trackColor = CyberDarkCardElevated
                                        )
                                    }

                                    // Row 3: Pricing Profile Matrix
                                    Surface(
                                        color = CyberDarkCardElevated,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text("سعر الجملة للبقالة:", fontSize = 10.sp, color = TextSecondaryDark)
                                                Text(
                                                    Money(pkg.wholesalePriceMinor, CurrencyCode.YER).format(),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = MikroTikCyan
                                                )
                                            }
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text("سعر التجزئة المطبوع:", fontSize = 10.sp, color = TextSecondaryDark)
                                                Text(
                                                    Money(pkg.retailPriceMinor, CurrencyCode.YER).format(),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = TextPrimaryDark
                                                )
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                val unitMargin = pkg.retailPriceMinor - pkg.wholesalePriceMinor
                                                Text("هامش ربح الوكيل:", fontSize = 10.sp, color = TextSecondaryDark)
                                                Text(
                                                    "+${Money(unitMargin, CurrencyCode.YER).format()}",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = SemanticIncomeGreen
                                                )
                                            }
                                        }
                                    }

                                    // Row 4: Direct Quick-Action Buttons (تعديل السعر, استلام/توليد كروت, كشف حركة الصنف, تسوية جرد)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Button 1: تعديل السعر
                                        OutlinedButton(
                                            onClick = { packageForPriceEdit = pkg },
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(1.dp, CyberBorderGlow),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimaryDark),
                                            modifier = Modifier.weight(1f).height(38.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(13.dp), tint = MikroTikCyan)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("تعديل السعر", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }

                                        // Button 2: استلام / توريد كروت
                                        Button(
                                            onClick = { showReceiveStockSheet = true },
                                            colors = ButtonDefaults.buttonColors(containerColor = MikroTikPrimary),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.weight(1f).height(38.dp)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(13.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("استلام كروت", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }

                                        // Button 3: كشف حركة الصنف
                                        OutlinedButton(
                                            onClick = { selectedPackageForHistory = pkg },
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(1.dp, CyberBorder),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondaryDark),
                                            modifier = Modifier.weight(1.1f).height(38.dp)
                                        ) {
                                            Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(13.dp), tint = TextSecondaryDark)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("كشف الحركة", fontSize = 11.sp)
                                        }

                                        // Button 4: تسوية جرد
                                        IconButton(
                                            onClick = {
                                                packageForAdjust = pkg
                                                showAdjustStockSheet = true
                                            },
                                            modifier = Modifier.size(38.dp).border(1.dp, CyberBorder, RoundedCornerShape(10.dp))
                                        ) {
                                            Icon(Icons.Default.Tune, contentDescription = "تسوية جرد", tint = TextMutedDark, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // TAB 2: أجهزة ومعدات الشبكة (Routers, Antennas, and Network Hardware)
                1 -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (assets.isEmpty()) {
                            item {
                                Surface(
                                    color = CyberDarkSurface,
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, CyberBorder),
                                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(32.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.Router, contentDescription = null, tint = AssetPurple, modifier = Modifier.size(48.dp))
                                        Text("لا توجد أجهزة مسجلة في مستودع الشبكة", fontWeight = FontWeight.Bold, color = TextPrimaryDark)
                                        Text("سجل أجهزة الراوتر والأنتينات عبر تبويب 'المشتريات' مع تحديد خيار 'أصل شبكة رأسمالي (1501)'.", fontSize = 12.sp, color = TextSecondaryDark)
                                    }
                                }
                            }
                        }

                        items(assets) { asset ->
                            val netBookValue = asset.purchaseCostMinor - asset.accumulatedDepreciationMinor
                            Card(
                                colors = CardDefaults.cardColors(containerColor = CyberDarkSurface),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, CyberBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                color = AssetPurple.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(Icons.Default.Router, contentDescription = null, tint = AssetPurple, modifier = Modifier.size(20.dp))
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(asset.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimaryDark)
                                                Text("أصل شبكة تشغيلي (حساب 1501)", fontSize = 11.sp, color = AssetPurple)
                                            }
                                        }

                                        Surface(
                                            color = SemanticIncomeGreen.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "قيمة دفترية: ${Money(netBookValue, CurrencyCode.FUNCTIONAL).format()}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = SemanticIncomeGreen,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("تكلفة الشراء: ${Money(asset.purchaseCostMinor, CurrencyCode.FUNCTIONAL).format()}", fontSize = 11.sp, color = TextSecondaryDark, fontFamily = FontFamily.Monospace)
                                        Text("مجمع الإهلاك: ${Money(asset.accumulatedDepreciationMinor, CurrencyCode.FUNCTIONAL).format()}", fontSize = 11.sp, color = SemanticExpenseRed, fontFamily = FontFamily.Monospace)
                                        Text("العمر: ${asset.usefulLifeMonths} شهر", fontSize = 11.sp, color = TextSecondaryDark)
                                    }
                                }
                            }
                        }
                    }
                }

                // TAB 3: مستلزمات واستهلاكية (Cables, Connectors, Power Supplies)
                2 -> {
                    Surface(
                        color = CyberDarkSurface,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, CyberBorder),
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Cable, contentDescription = null, tint = StatusWarning, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("المستلزمات ومستهلكات التوصيل", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimaryDark)
                            }
                            Text(
                                "يتم تسجيل المستلزمات (كابلات الشبكة Cat6، رؤوس RJ45، أشرطة اللحام ومحولات الطاقة) مباشرة في فواتير المشتريات كمصاريف تشغيلية (حساب 5102) أو ضمن تجهيزات الأصول.",
                                fontSize = 12.sp,
                                color = TextSecondaryDark,
                                lineHeight = 18.sp
                            )
                            Surface(
                                color = CyberDarkCardElevated,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("أصناف استهلاكية شائعة في شبكات المايكروتك:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = TextPrimaryDark)
                                    Text("• كابلات شبكة رول خارجي 305m Cat6 Outdoor", fontSize = 11.sp, color = TextSecondaryDark)
                                    Text("• محولات طاقة PoE Injector 24V / 48V", fontSize = 11.sp, color = TextSecondaryDark)
                                    Text("• شواحن وبطاريات جيل احتياطية UPS للكبائن", fontSize = 11.sp, color = TextSecondaryDark)
                                    Text("• قواطع حماية وتأريض ضد الصواعق", fontSize = 11.sp, color = TextSecondaryDark)
                                }
                            }
                        }
                    }
                }

                // TAB 4: سجل حركات المخزون
                3 -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (movements.isEmpty()) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                                    Text("لا توجد حركات مخزون مسجلة.", color = TextMutedDark)
                                }
                            }
                        }

                        items(movements) { m ->
                            val pkgName = packageMap[m.packageId]?.name ?: "باقة غير معروفة"
                            val (color, title) = when (m.type) {
                                "RECEIVE" -> SemanticIncomeGreen to "استلام وتوريد كروت (+)"
                                "SELL" -> Color(0xFF3B82F6) to "بيع وتوزيع كروت (-)"
                                "RETURN" -> SemanticWarningAmber to "مرتجع من وكيل (+)"
                                "ADJUST" -> Color.Gray to "تسوية جردية"
                                else -> Color.Gray to m.type
                            }

                            Card(
                                colors = CardDefaults.cardColors(containerColor = CyberDarkSurface),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, CyberBorder)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(pkgName, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimaryDark)
                                        Text("نوع الحركة: $title | اليوم: ${m.movementDateEpochDay}", fontSize = 11.sp, color = TextSecondaryDark)
                                    }
                                    Text(
                                        text = "${if (m.type in listOf("RECEIVE", "RETURN")) "+" else "-"}${m.quantity} كرت",
                                        color = color,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Package Sheet
    if (showAddPackageSheet) {
        AddPackageBottomSheet(
            onDismiss = { showAddPackageSheet = false },
            onSubmit = { name, quota, wholesale, retail ->
                val pkg = CardPackageEntity(
                    id = UuidUtils.newTimeOrderedId(),
                    name = name,
                    durationOrQuota = quota,
                    wholesalePriceMinor = wholesale,
                    retailPriceMinor = retail
                )
                viewModel.insertPackage(pkg) { showAddPackageSheet = false }
            }
        )
    }

    // Receive Stock Sheet / Add Manual Cards Count
    if (showReceiveStockSheet) {
        AddManualCardsCountDialog(
            packages = packages,
            onDismiss = { showReceiveStockSheet = false },
            onSubmit = { pkgId, qty, notes ->
                viewModel.receiveCardStock(pkgId, qty, notes) { showReceiveStockSheet = false }
            }
        )
    }

    // Adjust Stock Sheet
    if (showAdjustStockSheet) {
        AdjustStockBottomSheet(
            packages = packages,
            initialPackageId = packageForAdjust?.id,
            onDismiss = {
                showAdjustStockSheet = false
                packageForAdjust = null
            },
            onSubmit = { pkgId, adjQty, reason ->
                viewModel.adjustCardStock(pkgId, adjQty, reason) {
                    showAdjustStockSheet = false
                    packageForAdjust = null
                }
            }
        )
    }

    // Price Edit Dialog (Direct quick-action)
    packageForPriceEdit?.let { pkg ->
        var wholesaleStr by remember { mutableStateOf((pkg.wholesalePriceMinor / 100L).toString()) }
        var retailStr by remember { mutableStateOf((pkg.retailPriceMinor / 100L).toString()) }

        AlertDialog(
            onDismissRequest = { packageForPriceEdit = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = MikroTikCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("تعديل تسعيرة باقة: ${pkg.name}", fontWeight = FontWeight.Bold, color = TextPrimaryDark)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("عدل أسعار الجملة للبقالات والتجزئة للمستخدمين (بالريال اليمني):", fontSize = 12.sp, color = TextSecondaryDark)
                    OutlinedTextField(
                        value = wholesaleStr,
                        onValueChange = { wholesaleStr = it },
                        label = { Text("سعر الجملة للبقالات (ر.ي)") },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("input_edit_wholesale_price")
                    )
                    OutlinedTextField(
                        value = retailStr,
                        onValueChange = { retailStr = it },
                        label = { Text("سعر التجزئة المقترح (ر.ي)") },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("input_edit_retail_price")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val w = (wholesaleStr.toLongOrNull() ?: 0L) * 100L
                        val r = (retailStr.toLongOrNull() ?: 0L) * 100L
                        if (w > 0L && r >= w) {
                            val updated = pkg.copy(wholesalePriceMinor = w, retailPriceMinor = r)
                            viewModel.insertPackage(updated) {
                                packageForPriceEdit = null
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MikroTikCyan, contentColor = CyberDarkCanvas),
                    modifier = Modifier.testTag("btn_confirm_edit_package_price")
                ) {
                    Text("حفظ الأسعار الجديدة", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { packageForPriceEdit = null }) {
                    Text("إلغاء", color = TextSecondaryDark)
                }
            }
        )
    }

    // Card History Statement Dialog (كشف حركة الصنف)
    selectedPackageForHistory?.let { pkg ->
        val pkgMovements = remember(movements, pkg.id) {
            movements.filter { it.packageId == pkg.id }
        }
        AlertDialog(
            onDismissRequest = { selectedPackageForHistory = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = MikroTikCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("كشف حركة باقة: ${pkg.name}", fontWeight = FontWeight.Bold, color = TextPrimaryDark)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(color = CyberDarkCardElevated, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("الرصيد الحالي بالمستودع:", fontSize = 12.sp, color = TextSecondaryDark)
                            Text("${stockBalanceMap[pkg.id] ?: 0} كرت", fontWeight = FontWeight.Bold, color = MikroTikCyan)
                        }
                    }

                    if (pkgMovements.isEmpty()) {
                        Text("لا توجد حركات سابقة لهذه الباقة.", fontSize = 12.sp, color = TextMutedDark, modifier = Modifier.padding(vertical = 12.dp))
                    } else {
                        pkgMovements.forEach { m ->
                            val (c, t) = when (m.type) {
                                "RECEIVE" -> SemanticIncomeGreen to "استلام دفعة (+)"
                                "SELL" -> Color(0xFF3B82F6) to "بيع وتوزيع (-)"
                                "RETURN" -> SemanticWarningAmber to "مرتجع (+)"
                                "ADJUST" -> Color.Gray to "تسوية جردية"
                                else -> Color.Gray to m.type
                            }
                            Surface(color = CyberDarkSurface, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(t, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = c)
                                        Text("التاريخ: ${m.movementDateEpochDay}", fontSize = 10.sp, color = TextMutedDark)
                                    }
                                    Text(
                                        "${if (m.type in listOf("RECEIVE", "RETURN")) "+" else "-"}${m.quantity}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = c,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedPackageForHistory = null }) {
                    Text("إغلاق", color = MikroTikCyan)
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPackageBottomSheet(
    onDismiss: () -> Unit,
    onSubmit: (name: String, quota: String, wholesaleMinor: Long, retailMinor: Long) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var quota by rememberSaveable { mutableStateOf("") }
    var wholesaleText by rememberSaveable { mutableStateOf("") }
    var retailText by rememberSaveable { mutableStateOf("") }

    val scrollState = rememberScrollState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = CyberDarkCanvas
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
        ) {
            Text(
                text = "إضافة باقة كروت إنترنت جديدة",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimaryDark,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            HorizontalDivider(color = CyberBorder)

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم الباقة (مثال: باقة اليوم الواحد)") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("input_package_name")
                )
                OutlinedTextField(
                    value = quota,
                    onValueChange = { quota = it },
                    label = { Text("السعة / المدة (مثال: 1GB / 24 ساعة)") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("input_package_quota")
                )
                OutlinedTextField(
                    value = wholesaleText,
                    onValueChange = { wholesaleText = it },
                    label = { Text("سعر الجملة للبقالات (ر.ي)") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("input_wholesale_price")
                )
                OutlinedTextField(
                    value = retailText,
                    onValueChange = { retailText = it },
                    label = { Text("سعر التجزئة المقترح للمستخدم (ر.ي)") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("input_retail_price")
                )
            }

            HorizontalDivider(color = CyberBorder)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            val w = (wholesaleText.toLongOrNull() ?: 0L) * 100L
                            val r = (retailText.toLongOrNull() ?: 0L) * 100L
                            onSubmit(name, quota, w, r)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MikroTikCyan, contentColor = CyberDarkCanvas),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(48.dp).testTag("btn_save_package")
                ) {
                    Text("حفظ الباقة", fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onDismiss, modifier = Modifier.height(48.dp)) {
                    Text("إلغاء", color = TextSecondaryDark)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiveStockBottomSheet(
    packages: List<CardPackageEntity>,
    onDismiss: () -> Unit,
    onSubmit: (packageId: String, quantity: Int, notes: String) -> Unit
) {
    AddManualCardsCountDialog(
        packages = packages,
        onDismiss = onDismiss,
        onSubmit = onSubmit
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdjustStockBottomSheet(
    packages: List<CardPackageEntity>,
    initialPackageId: String? = null,
    onDismiss: () -> Unit,
    onSubmit: (packageId: String, adjustmentQty: Int, reason: String) -> Unit
) {
    var selectedPackageId by rememberSaveable { mutableStateOf(initialPackageId ?: packages.firstOrNull()?.id ?: "") }
    var adjText by rememberSaveable { mutableStateOf("") }
    var reason by rememberSaveable { mutableStateOf("") }

    val scrollState = rememberScrollState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = CyberDarkCanvas
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
        ) {
            Text(
                text = "تسوية جرد كروت",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimaryDark,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            HorizontalDivider(color = CyberBorder)

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("الباقة المراد تسويتها:", style = MaterialTheme.typography.labelMedium, color = TextSecondaryDark)

                // Single-line non-wrapping package chips using LazyRow
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(packages) { pkg ->
                        FilterChip(
                            selected = selectedPackageId == pkg.id,
                            onClick = { selectedPackageId = pkg.id },
                            label = {
                                Text(
                                    text = pkg.name,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = adjText,
                    onValueChange = { adjText = it },
                    label = { Text("الكمية المضافة (+) أو المخصومة (-)") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("input_adjust_qty")
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("سبب التسوية (إلزامي)") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("input_adjust_reason")
                )
            }

            HorizontalDivider(color = CyberBorder)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val q = adjText.toIntOrNull() ?: 0
                        if (q != 0 && reason.isNotBlank()) {
                            onSubmit(selectedPackageId, q, reason)
                        }
                    },
                    enabled = reason.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = MikroTikCyan, contentColor = CyberDarkCanvas),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(48.dp).testTag("btn_confirm_adjust_stock")
                ) {
                    Text("اعتماد تسوية الرصيد", fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onDismiss, modifier = Modifier.height(48.dp)) {
                    Text("إلغاء", color = TextSecondaryDark)
                }
            }
        }
    }
}
