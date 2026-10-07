package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.inspector.ChartOfAccountsScreen
import com.example.ui.inspector.HealthCheckScreen
import com.example.ui.inspector.InspectorViewModel
import com.example.ui.inspector.JournalBrowserScreen
import com.example.ui.inspector.TrialBalanceScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.PackagesStockScreen
import com.example.ui.screens.PartiesScreen
import com.example.ui.screens.PurchasesAssetsScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.SalesScreen
import com.example.ui.screens.VouchersScreen
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.IconButton
import androidx.compose.material3.rememberModalBottomSheetState
import com.example.data.sync.SyncState
import com.example.ui.screens.CloudSyncSheet
import com.example.ui.screens.NetworkHubScreen
import com.example.ui.theme.BrandCyanPrimary
import com.example.ui.theme.SamMikrotikTheme
import com.example.ui.theme.SemanticExpenseRed
import com.example.ui.theme.SemanticIncomeGreen
import com.example.ui.viewmodel.AppViewModel

object AppBrand {
    const val NAME = "SamMikrotik"
    const val TAGLINE = "النظام المالي المحاسبي المتكامل لشبكات الإنترنت"
}

sealed class MainTab(val title: String, val icon: ImageVector, val tag: String) {
    object Dashboard : MainTab("الرئيسية", Icons.Default.Dashboard, "tab_dashboard")
    object Sales : MainTab("المبيعات", Icons.Default.Receipt, "tab_sales")
    object Vouchers : MainTab("السندات", Icons.Default.AccountBalanceWallet, "tab_vouchers")
    object Purchases : MainTab("المشتريات", Icons.Default.ShoppingCart, "tab_purchases")
    object PartiesAndStock : MainTab("العملاء", Icons.Default.Store, "tab_parties_stock")
    object NetworkHub : MainTab("الشبكة", Icons.Default.Router, "tab_network_hub")
    object Reports : MainTab("التقارير", Icons.Default.Assessment, "tab_reports")
    object Audit : MainTab("الرقابة", Icons.Default.Security, "tab_audit")

    companion object {
        val all = listOf(Dashboard, Sales, Vouchers, Purchases, PartiesAndStock, NetworkHub, Reports, Audit)
    }
}

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SamMikrotikTheme {
                val appViewModel: AppViewModel = viewModel()
                val inspectorViewModel: InspectorViewModel = viewModel()
                val snackbarHostState = remember { SnackbarHostState() }

                var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
                val invariantResult by appViewModel.invariantResult.collectAsState()
                val syncState by appViewModel.syncState.collectAsState()
                val currentUser by appViewModel.currentUser.collectAsState()

                var showCloudSyncSheet by rememberSaveable { mutableStateOf(false) }
                val syncSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

                // Listen for business feedback messages
                LaunchedEffect(appViewModel) {
                    appViewModel.userMessage.collect { msg ->
                        snackbarHostState.showSnackbar(msg)
                    }
                }

                // Handle system back navigation (navigate back to Dashboard if on sub-screens)
                if (selectedTabIndex != 0) {
                    BackHandler {
                        selectedTabIndex = 0
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = AppBrand.NAME,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = MainTab.all[selectedTabIndex].title,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = BrandCyanPrimary
                                    )
                                }
                            },
                            actions = {
                                IconButton(
                                    onClick = { showCloudSyncSheet = true },
                                    modifier = Modifier.testTag("top_bar_cloud_sync_btn")
                                ) {
                                    Icon(
                                        imageVector = when (syncState) {
                                            is SyncState.InProgress -> Icons.Default.Sync
                                            is SyncState.Success -> Icons.Default.CloudDone
                                            is SyncState.Error -> Icons.Default.CloudOff
                                            else -> Icons.Default.Cloud
                                        },
                                        contentDescription = "المزامنة السحابية",
                                        tint = BrandCyanPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                val isBalanced = invariantResult?.isValid ?: true
                                val badgeColor = if (isBalanced) SemanticIncomeGreen else SemanticExpenseRed
                                Surface(
                                    color = badgeColor.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .padding(end = 12.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable { selectedTabIndex = 7 /* Jump to Audit tab */ }
                                        .border(1.dp, badgeColor, RoundedCornerShape(16.dp))
                                        .testTag("top_bar_invariant_badge")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isBalanced) Icons.Default.CheckCircle else Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = badgeColor,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (isBalanced) "IFRS متزن" else "خلل رياضي",
                                            color = badgeColor,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    },
                    bottomBar = {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            MainTab.all.forEachIndexed { index, tab ->
                                NavigationBarItem(
                                    selected = selectedTabIndex == index,
                                    onClick = { selectedTabIndex = index },
                                    icon = {
                                        Icon(
                                            imageVector = tab.icon,
                                            contentDescription = tab.title,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = tab.title,
                                            fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 10.sp,
                                            maxLines = 1
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                    ),
                                    modifier = Modifier.testTag(tab.tag)
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    val modifier = Modifier.padding(innerPadding)
                    when (selectedTabIndex) {
                        0 -> DashboardScreen(
                            viewModel = appViewModel,
                            onNavigateToQuickSale = { selectedTabIndex = 1 },
                            onNavigateToSales = { selectedTabIndex = 1 },
                            onNavigateToReceipts = { selectedTabIndex = 2 },
                            onNavigateToPayments = { selectedTabIndex = 2 },
                            onNavigateToReports = { selectedTabIndex = 6 },
                            modifier = modifier
                        )
                        1 -> SalesScreen(viewModel = appViewModel, modifier = modifier)
                        2 -> VouchersScreen(viewModel = appViewModel, modifier = modifier)
                        3 -> PurchasesAssetsScreen(viewModel = appViewModel, modifier = modifier)
                        4 -> PartiesAndStockCombinedScreen(viewModel = appViewModel, modifier = modifier)
                        5 -> NetworkHubScreen(networkRepository = appViewModel.networkRepository, modifier = modifier)
                        6 -> ReportsScreen(viewModel = appViewModel, modifier = modifier)
                        7 -> AuditScreen(inspectorViewModel = inspectorViewModel, modifier = modifier)
                    }
                }

                if (showCloudSyncSheet) {
                    CloudSyncSheet(
                        viewModel = appViewModel,
                        sheetState = syncSheetState,
                        onDismissRequest = { showCloudSyncSheet = false }
                    )
                }
            }
        }
    }
}

/**
 * Host screen combining Parties (Customers, Agents, Vendors) and Card Packages / Physical Stock.
 */
@Composable
private fun PartiesAndStockCombinedScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    var subTab by rememberSaveable { mutableIntStateOf(0) }

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = subTab,
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Tab(
                selected = subTab == 0,
                onClick = { subTab = 0 },
                text = { Text("الأطراف والوكلاء والديون", fontWeight = FontWeight.Bold) },
                icon = { Icon(Icons.Default.Store, contentDescription = null, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.testTag("subtab_parties")
            )
            Tab(
                selected = subTab == 1,
                onClick = { subTab = 1 },
                text = { Text("باقات الكروت والمخزون", fontWeight = FontWeight.Bold) },
                icon = { Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.testTag("subtab_packages_stock")
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            if (subTab == 0) {
                PartiesScreen(viewModel = viewModel)
            } else {
                PackagesStockScreen(viewModel = viewModel)
            }
        }
    }
}

/**
 * Audit & Inspector Host screen with direct access to Core Double-Entry accounting verification tools.
 */
@Composable
private fun AuditScreen(
    inspectorViewModel: InspectorViewModel,
    modifier: Modifier = Modifier
) {
    var auditSubTab by rememberSaveable { mutableIntStateOf(0) }

    Column(modifier = modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = auditSubTab,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            edgePadding = 8.dp
        ) {
            Tab(
                selected = auditSubTab == 0,
                onClick = { auditSubTab = 0 },
                text = { Text("السلامة والاتزان", fontWeight = FontWeight.Bold) },
                icon = { Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.testTag("audit_subtab_health")
            )
            Tab(
                selected = auditSubTab == 1,
                onClick = { auditSubTab = 1 },
                text = { Text("دليل الحسابات الموحد", fontWeight = FontWeight.Bold) },
                icon = { Icon(Icons.Default.AccountTree, contentDescription = null, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.testTag("audit_subtab_accounts")
            )
            Tab(
                selected = auditSubTab == 2,
                onClick = { auditSubTab = 2 },
                text = { Text("دفتر اليومية العام", fontWeight = FontWeight.Bold) },
                icon = { Icon(Icons.Default.Book, contentDescription = null, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.testTag("audit_subtab_journal")
            )
            Tab(
                selected = auditSubTab == 3,
                onClick = { auditSubTab = 3 },
                text = { Text("ميزان المراجعة", fontWeight = FontWeight.Bold) },
                icon = { Icon(Icons.Default.AccountBalance, contentDescription = null, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.testTag("audit_subtab_trial_balance")
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when (auditSubTab) {
                0 -> HealthCheckScreen(inspectorViewModel)
                1 -> ChartOfAccountsScreen(inspectorViewModel)
                2 -> JournalBrowserScreen(inspectorViewModel)
                3 -> TrialBalanceScreen(inspectorViewModel)
            }
        }
    }
}
