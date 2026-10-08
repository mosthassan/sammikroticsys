package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.ShoppingCart
import com.example.ui.components.PurchaseJsonBackupDialog
import com.example.ui.components.EditImportedPurchaseDialog
import com.example.util.DataJsonHelper
import com.example.ui.components.FintechTabItem
import com.example.ui.components.ModernFintechSegmentedTabs
import com.example.ui.theme.MikroTikCyan
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.ledger.AccountConstants
import com.example.core.model.CurrencyCode
import com.example.core.model.ExchangeRate
import com.example.core.model.Money
import com.example.core.model.RateZone
import com.example.data.ledger.PurchaseItemSpec
import com.example.data.local.AppDatabase
import com.example.data.local.entity.AssetEntity
import com.example.data.local.entity.CardPackageEntity
import com.example.data.local.entity.DocumentEntity
import com.example.data.local.entity.DocumentItemEntity
import com.example.ui.components.AmountSemanticType
import com.example.ui.components.AmountText
import com.example.ui.components.CurrencySelector
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusChip
import com.example.domain.usecase.ExchangeRateResolver
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.background
import com.example.ui.theme.AssetPurple
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberBorderGlow
import com.example.ui.theme.CyberDarkCanvas
import com.example.ui.theme.CyberDarkCardElevated
import com.example.ui.theme.CyberDarkSurface
import com.example.ui.theme.MikroTikNavyLight
import com.example.ui.theme.MikroTikPrimary
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StatusOnline
import com.example.ui.theme.TextMutedDark
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark
import com.example.ui.theme.SemanticExpenseRed
import com.example.ui.theme.SemanticIncomeGreen
import com.example.ui.viewmodel.AppViewModel
import com.example.data.network.NetworkConfig
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Edit

@Composable
fun PurchasesAssetsScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val documents by viewModel.allDocuments.collectAsState()
    val assets by viewModel.allAssets.collectAsState()
    val parties by viewModel.allParties.collectAsState()
    val treasuries by viewModel.allTreasuries.collectAsState()
    val packages by viewModel.allPackages.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Purchases, 1: Fixed Assets Register
    var showNewPurchaseSheet by remember { mutableStateOf(false) }
    var showPurchaseJsonDialog by remember { mutableStateOf(false) }
    var editingDraft by remember { mutableStateOf<DataJsonHelper.ImportedPurchaseDraft?>(null) }
    var selectedAssetForDeprecate by remember { mutableStateOf<AssetEntity?>(null) }
    var selectedAssetForDisposal by remember { mutableStateOf<AssetEntity?>(null) }
    var disposalProceedsText by remember { mutableStateOf("0") }
    var selectedInvoiceForDetail by remember { mutableStateOf<DocumentEntity?>(null) }
    var invoiceToVoid by remember { mutableStateOf<DocumentEntity?>(null) }
    var voidReason by remember { mutableStateOf("فاتورة مكررة / خطأ في الإدخال") }
    var invoiceItemsForDetail by remember { mutableStateOf<List<DocumentItemEntity>>(emptyList()) }
    var invoiceFilter by remember { mutableStateOf("ALL") }

    val partyMap = remember(parties) { parties.associateBy { it.id } }
    val purchaseInvoices = remember(documents) { documents.filter { it.type == "PURCHASE_INVOICE" } }

    Scaffold(
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = { showNewPurchaseSheet = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("fab_new_purchase")
                ) {
                    Row(modifier = Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("فاتورة مشتريات", fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Header with JSON Import button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("المشتريات ومعدات الشبكة", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                OutlinedButton(
                    onClick = { showPurchaseJsonDialog = true },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("استيراد فواتير JSON", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            ModernFintechSegmentedTabs(
                items = listOf(
                    FintechTabItem(
                        title = "فواتير المشتريات",
                        icon = Icons.Default.ShoppingCart,
                        count = purchaseInvoices.size,
                        accentColor = MikroTikCyan
                    ),
                    FintechTabItem(
                        title = "سجل الأصول ومعدات الشبكة",
                        icon = Icons.Default.Router,
                        count = assets.size,
                        accentColor = Color(0xFF8B5CF6)
                    )
                ),
                selectedIndex = selectedTab,
                onTabSelected = { selectedTab = it }
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (selectedTab == 0) {
                val filteredInvoices = remember(purchaseInvoices, invoiceFilter) {
                    when (invoiceFilter) {
                        "ACTIVE" -> purchaseInvoices.filter { it.status != "VOIDED" }
                        "VOIDED" -> purchaseInvoices.filter { it.status == "VOIDED" }
                        else -> purchaseInvoices
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = invoiceFilter == "ALL",
                        onClick = { invoiceFilter = "ALL" },
                        label = { Text("الكل (${purchaseInvoices.size})", fontSize = 12.sp) }
                    )
                    FilterChip(
                        selected = invoiceFilter == "ACTIVE",
                        onClick = { invoiceFilter = "ACTIVE" },
                        label = { Text("سارية (${purchaseInvoices.count { it.status != "VOIDED" }})", fontSize = 12.sp) }
                    )
                    FilterChip(
                        selected = invoiceFilter == "VOIDED",
                        onClick = { invoiceFilter = "VOIDED" },
                        label = { Text("ملغية (${purchaseInvoices.count { it.status == "VOIDED" }})", fontSize = 12.sp) }
                    )
                }

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                    if (filteredInvoices.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    if (invoiceFilter == "VOIDED") "لا توجد فواتير مشتريات ملغية"
                                    else if (invoiceFilter == "ACTIVE") "لا توجد فواتير مشتريات سارية"
                                    else "لا توجد فواتير مشتريات مسجلة"
                                )
                            }
                        }
                    }

                    items(filteredInvoices) { inv ->
                        val vendorName = partyMap[inv.partyId]?.name ?: "مورد عام"
                        val isVoided = inv.status == "VOIDED"
                        Card(
                            onClick = {
                                selectedInvoiceForDetail = inv
                                viewModel.getDocumentItems(inv.id) { items ->
                                    invoiceItemsForDetail = items
                                }
                            },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isVoided)
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    1.dp,
                                    if (isVoided) SemanticExpenseRed.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline,
                                    RoundedCornerShape(12.dp)
                                )
                                .testTag("purchase_card_${inv.docNumber}")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("فاتورة مشتريات #${inv.docNumber}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        StatusChip(status = inv.status)
                                    }
                                    Text("المورد: $vendorName", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                    if (inv.notes.isNotBlank()) {
                                        Text(inv.notes, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (isVoided) "ملغية بقيد عكسي (تمت استعادة الأرصدة وإلغاء الأجهزة) ✕" else "اضغط لعرض التفاصيل أو إلغاء الفاتورة 🔍",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isVoided) SemanticExpenseRed else MikroTikCyan
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    AmountText(
                                        money = Money(inv.totalMinor, CurrencyCode.fromString(inv.currency)),
                                        semanticType = if (isVoided) AmountSemanticType.NEUTRAL else AmountSemanticType.EXPENSE,
                                        fontSize = 14
                                    )
                                    if (inv.currency != CurrencyCode.FUNCTIONAL.name) {
                                        Text(
                                            "المعادل: ${Money(inv.totalBaseMinor, CurrencyCode.FUNCTIONAL).format()}",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                    if (assets.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                                Text("لا توجد أصول مسجلة في سجل الأصول الثابتة")
                            }
                        }
                    }

                    items(assets) { asset ->
                        val netBookValue = asset.purchaseCostMinor - asset.accumulatedDepreciationMinor
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Router, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(asset.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                    Text("صافي القيمة: ${Money(netBookValue, CurrencyCode.FUNCTIONAL).format()}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = SemanticIncomeGreen)
                                }

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("التكلفة: ${Money(asset.purchaseCostMinor, CurrencyCode.FUNCTIONAL).format()}", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                    Text("مجمع الإهلاك: ${Money(asset.accumulatedDepreciationMinor, CurrencyCode.FUNCTIONAL).format()}", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = SemanticExpenseRed)
                                    Text("العمر: ${asset.usefulLifeMonths} شهر", fontSize = 11.sp)
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { selectedAssetForDeprecate = asset },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("تشغيل الإهلاك الشهري", fontSize = 12.sp)
                                    }

                                    OutlinedButton(
                                        onClick = { selectedAssetForDisposal = asset },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SemanticExpenseRed),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("استبعاد/بيع الأصل", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // New Purchase Sheet
    if (showNewPurchaseSheet) {
        val networkConfig by viewModel.networkRepository.config.collectAsState()
        NewPurchaseBottomSheet(
            parties = parties,
            packages = packages,
            resolver = viewModel.exchangeRateResolver,
            networkConfig = networkConfig,
            onDismiss = { showNewPurchaseSheet = false },
            onSubmit = { vendorId, currency, rate, items, notes ->
                viewModel.postPurchaseInvoice(
                    vendorPartyId = vendorId,
                    currency = currency,
                    exchangeRate = rate,
                    items = items,
                    notes = notes,
                    rateZone = networkConfig.rateZone,
                    onSuccess = { showNewPurchaseSheet = false }
                )
            }
        )
    }

    // Depreciation Dialog
    selectedAssetForDeprecate?.let { ast ->
        AlertDialog(
            onDismissRequest = { selectedAssetForDeprecate = null },
            title = { Text("تشغيل إهلاك الأصل: ${ast.name}") },
            text = {
                val monthlyAmount = (ast.purchaseCostMinor - ast.salvageValueMinor) / ast.usefulLifeMonths.coerceAtLeast(1)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("القسط الشهري المحسوب: ${Money(monthlyAmount, CurrencyCode.FUNCTIONAL).format()}")
                    Text("سيتم ترحيل القيد فوراً (مدين 5203، دائن 1599). التكرار للشهر نفسه محمي بـ Idempotency.")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.runDepreciation(ast.id, 2026, 1)
                        selectedAssetForDeprecate = null
                    }
                ) { Text("اعتماد وترحيل الإهلاك") }
            },
            dismissButton = { TextButton(onClick = { selectedAssetForDeprecate = null }) { Text("إلغاء") } }
        )
    }

    // Disposal Dialog
    selectedAssetForDisposal?.let { ast ->
        AlertDialog(
            onDismissRequest = { selectedAssetForDisposal = null },
            title = { Text("استبعاد/بيع الأصل: ${ast.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("أدخل عائد البيع النقدي إن وجد (0 للتخريد):")
                    OutlinedTextField(
                        value = disposalProceedsText,
                        onValueChange = { disposalProceedsText = it },
                        label = { Text("عائد البيع (ر.ي)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val proceeds = (disposalProceedsText.toLongOrNull() ?: 0L) * 100L
                        viewModel.disposeAsset(
                            assetId = ast.id,
                            salvageProceedsMinor = proceeds,
                            treasuryId = if (proceeds > 0) "TR_MAIN_YER" else null,
                            notes = "استبعاد أصل وتخريد"
                        ) {
                            selectedAssetForDisposal = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SemanticExpenseRed)
                ) { Text("تأكيد الاستبعاد") }
            },
            dismissButton = { TextButton(onClick = { selectedAssetForDisposal = null }) { Text("تراجع") } }
        )
    }

    if (showPurchaseJsonDialog) {
        PurchaseJsonBackupDialog(
            onImportDrafts = { drafts ->
                viewModel.postImportedPurchases(drafts)
                showPurchaseJsonDialog = false
            },
            onReviewDraft = { draft ->
                editingDraft = draft
                showPurchaseJsonDialog = false
            },
            onDismissRequest = { showPurchaseJsonDialog = false }
        )
    }

    editingDraft?.let { draft ->
        EditImportedPurchaseDialog(
            draft = draft,
            parties = parties,
            treasuries = treasuries,
            onSaveAndPost = { vendorName, isCash, treasuryId, currency, exchangeRate, items, notes ->
                viewModel.postPurchaseInvoiceWithSettlement(
                    vendorName = vendorName,
                    isCash = isCash,
                    treasuryId = treasuryId,
                    currency = currency,
                    exchangeRate = exchangeRate,
                    items = items,
                    notes = notes,
                    onSuccess = {
                        editingDraft = null
                    }
                )
            },
            onDismissRequest = { editingDraft = null }
        )
    }

    // Invoice Detail Dialog
    selectedInvoiceForDetail?.let { inv ->
        val vendorName = partyMap[inv.partyId]?.name ?: "مورد عام"
        val curr = CurrencyCode.fromString(inv.currency)
        val isVoided = inv.status == "VOIDED"
        val detailScrollState = rememberScrollState()

        AlertDialog(
            onDismissRequest = { selectedInvoiceForDetail = null },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("تفاصيل فاتورة المشتريات #${inv.docNumber}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    StatusChip(status = inv.status)
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(detailScrollState)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("المورد:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(vendorName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("إجمالي الفاتورة:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(Money(inv.totalMinor, curr).format(), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            if (inv.currency != CurrencyCode.FUNCTIONAL.name) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("المعادل بالريال اليمني:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(Money(inv.totalBaseMinor, CurrencyCode.FUNCTIONAL).format(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                }
                            }
                            if (inv.notes.isNotBlank()) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("البيان والملاحظات:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(inv.notes, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    if (invoiceItemsForDetail.isNotEmpty()) {
                        Text("الأصناف والأجهزة المسجلة بالفاتورة:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            invoiceItemsForDetail.forEach { itm ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(itm.description.ifBlank { "بند مشتريات" }, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                            Text("الكمية: ${itm.quantity} × ${Money(itm.unitPriceMinor, curr).format()}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            if (itm.isAsset) {
                                                Text("أصل شبكة ثابت (حساب 1501)", fontSize = 10.sp, color = MikroTikCyan, fontWeight = FontWeight.Medium)
                                            }
                                        }
                                        Text(Money(itm.totalMinor, curr).format(), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    if (isVoided) {
                        Surface(
                            color = SemanticExpenseRed.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = SemanticExpenseRed, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "الفاتورة ملغية بقيد عكسي. تمت استعادة أرصدة المورد/الصندوق وتم استبعاد أجهزة الشبكة المرتبطة بها تلقائياً.",
                                    fontSize = 11.sp,
                                    color = SemanticExpenseRed
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isVoided) {
                        Button(
                            onClick = {
                                invoiceToVoid = inv
                                selectedInvoiceForDetail = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SemanticExpenseRed),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("btn_void_purchase_invoice")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("إلغاء الفاتورة (Void)", fontWeight = FontWeight.Bold)
                        }
                    }
                    Button(
                        onClick = { selectedInvoiceForDetail = null },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("إغلاق")
                    }
                }
            }
        )
    }

    // Void Purchase Confirmation Dialog
    invoiceToVoid?.let { inv ->
        AlertDialog(
            onDismissRequest = { invoiceToVoid = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = SemanticExpenseRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("إلغاء فاتورة المشتريات #${inv.docNumber}", color = SemanticExpenseRed, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                ) {
                    Text(
                        "هل أنت متأكد من إلغاء هذه الفاتورة؟ سيتم عكس القيد المحاسبي في الأستاذ العام وتعديل أرصدة الصندوق والموردين والأصول آلياً.",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("الأثر التلقائي للإلغاء (IFRS):", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MikroTikCyan)
                            Text("1. عكس القيد المحاسبي: يتم ترحيل قيد يومية عكسي تعويضي فوري يعيد أرصدة المورد أو الصندوق وحساب الأصول إلى وضعها الصحيح تلقائياً.", fontSize = 11.sp)
                            Text("2. إلغاء معدات الشبكة: يتم استبعاد وحذف أي أجهزة تم إنشاؤها بهذه الفاتورة تلقائياً من تبويب سجل الأصول لمنع تكرارها أو احتساب إهلاك لها.", fontSize = 11.sp)
                            Text("3. تصنيف الفاتورة كـ ملغية (VOIDED) لمنع أي تكرار مستقبلي.", fontSize = 11.sp)
                        }
                    }

                    OutlinedTextField(
                        value = voidReason,
                        onValueChange = { voidReason = it },
                        label = { Text("سبب الإلغاء (إلزامي)") },
                        modifier = Modifier.fillMaxWidth().testTag("input_void_reason")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (voidReason.isNotBlank()) {
                            viewModel.voidDocument(inv.id, voidReason) {
                                invoiceToVoid = null
                                voidReason = "فاتورة مكررة / خطأ في الإدخال"
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SemanticExpenseRed),
                    modifier = Modifier.testTag("btn_confirm_void_purchase")
                ) {
                    Text("تأكيد الإلغاء وعكس الأرصدة والأصول")
                }
            },
            dismissButton = {
                TextButton(onClick = { invoiceToVoid = null }) {
                    Text("تراجع")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewPurchaseBottomSheet(
    parties: List<com.example.data.local.entity.PartyEntity>,
    packages: List<CardPackageEntity> = emptyList(),
    resolver: ExchangeRateResolver? = null,
    networkConfig: NetworkConfig? = null,
    onDismiss: () -> Unit,
    onSubmit: (vendorId: String, currency: CurrencyCode, rate: ExchangeRate, items: List<PurchaseItemSpec>, notes: String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val effectiveConfig = networkConfig ?: remember {
        com.example.data.network.NetworkRepository(context).config.value
    }
    val effectiveResolver = resolver ?: remember { ExchangeRateResolver(AppDatabase.getInstance(context)) }
    val vendors = remember(parties) { parties.filter { it.isVendor } }
    var vendorSearchQuery by rememberSaveable { mutableStateOf("") }
    var selectedVendorId by rememberSaveable {
        mutableStateOf(vendors.firstOrNull()?.id ?: AppDatabase.WALK_IN_CASH_PARTY_ID)
    }
    var selectedCurrency by rememberSaveable { mutableStateOf(CurrencyCode.FUNCTIONAL) }
    var selectedZone by rememberSaveable { mutableStateOf(effectiveConfig.rateZone) }
    var rateInputText by rememberSaveable { mutableStateOf("1.0") }
    var isRateManuallyEdited by rememberSaveable { mutableStateOf(false) }
    val today = remember { java.time.LocalDate.now().toEpochDay() }

    LaunchedEffect(selectedCurrency, selectedZone, effectiveConfig) {
        when (selectedCurrency) {
            CurrencyCode.YER -> {
                rateInputText = "1.0"
                isRateManuallyEdited = false
            }
            CurrencyCode.USD -> {
                val defaultRateMicros = if (selectedZone == effectiveConfig.rateZone) {
                    effectiveConfig.defaultUsdRateMicros
                } else {
                    effectiveResolver.resolveOrNull(CurrencyCode.USD, today, selectedZone)?.rateMicros
                        ?: if (selectedZone == RateZone.ADEN) 1_600_000_000L else 535_000_000L
                }
                rateInputText = ExchangeRate.formatRateMicros(defaultRateMicros)
                isRateManuallyEdited = false
            }
            CurrencyCode.SAR -> {
                val defaultRateMicros = if (selectedZone == effectiveConfig.rateZone) {
                    effectiveConfig.defaultSarRateMicros
                } else {
                    effectiveResolver.resolveOrNull(CurrencyCode.SAR, today, selectedZone)?.rateMicros
                        ?: if (selectedZone == RateZone.ADEN) 420_000_000L else 140_500_000L
                }
                rateInputText = ExchangeRate.formatRateMicros(defaultRateMicros)
                isRateManuallyEdited = false
            }
        }
    }

    val parsedRateMicros: Long = if (selectedCurrency == CurrencyCode.FUNCTIONAL) {
        ExchangeRate.SCALE_MICROS
    } else {
        ExchangeRate.parseRateFromUserInput(rateInputText) ?: when (selectedCurrency) {
            CurrencyCode.USD -> effectiveConfig.defaultUsdRateMicros
            CurrencyCode.SAR -> effectiveConfig.defaultSarRateMicros
            else -> ExchangeRate.SCALE_MICROS
        }
    }

    val effectiveRate = remember(selectedCurrency, parsedRateMicros) {
        ExchangeRate(
            fromCurrency = selectedCurrency,
            toCurrency = CurrencyCode.FUNCTIONAL,
            rateMicros = parsedRateMicros
        )
    }

    // New item inputs & package dropdown state
    var itemDesc by rememberSaveable { mutableStateOf("") }
    var itemQtyText by rememberSaveable { mutableStateOf("1") }
    var itemPriceText by rememberSaveable { mutableStateOf("100") }
    var isFixedAsset by rememberSaveable { mutableStateOf(true) }
    var usefulMonthsText by rememberSaveable { mutableStateOf("24") }
    var notes by rememberSaveable { mutableStateOf("") }
    var packageDropdownExpanded by remember { mutableStateOf(false) }
    var selectedPackage by remember { mutableStateOf<CardPackageEntity?>(null) }
    var selectedPackageId by rememberSaveable { mutableStateOf("") }

    val itemsList = remember { androidx.compose.runtime.mutableStateListOf<PurchaseItemSpec>() }
    val scrollState = rememberScrollState()

    val filteredVendors = remember(vendors, vendorSearchQuery) {
        if (vendorSearchQuery.isBlank()) vendors
        else vendors.filter { it.name.contains(vendorSearchQuery.trim(), ignoreCase = true) }
    }

    val totalForeignMinor = itemsList.sumOf { it.quantity * it.unitPriceMinor }
    val totalYerMinor = if (selectedCurrency == CurrencyCode.FUNCTIONAL) {
        totalForeignMinor
    } else {
        effectiveRate.convert(totalForeignMinor)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = CyberDarkCanvas,
        dragHandle = {
            Surface(
                modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
                color = CyberBorderGlow,
                shape = RoundedCornerShape(2.dp)
            ) {
                Box(modifier = Modifier.size(width = 44.dp, height = 4.dp))
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MikroTikCyan.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = MikroTikCyan, modifier = Modifier.size(22.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "فاتورة مشتريات وتوريد شبكة",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryDark
                        )
                        Text(
                            text = "تسجيل فواتير الموردين واعتماد الأصول ومستلزمات الشبكة",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondaryDark,
                            fontSize = 11.sp
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Delete, contentDescription = "إغلاق", tint = TextMutedDark, modifier = Modifier.size(20.dp))
                }
            }

            HorizontalDivider(color = CyberBorder)

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // SECTION 1: HEADER & SUPPLIER SELECTION CARD
                Card(
                    colors = CardDefaults.cardColors(containerColor = CyberDarkSurface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, CyberBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = MikroTikPrimary.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("1", color = MikroTikCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("بيانات المورد والعملة والتسعيرة", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimaryDark)
                            }

                            // Interactive RateZone switch with live badge
                            if (selectedCurrency != CurrencyCode.FUNCTIONAL) {
                                Surface(
                                    color = if (selectedZone == RateZone.SANAA) MikroTikNavyLight else CyberDarkCardElevated,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, if (selectedZone == RateZone.SANAA) MikroTikCyan.copy(alpha = 0.5f) else StatusWarning.copy(alpha = 0.5f)),
                                    modifier = Modifier
                                        .clickable {
                                            selectedZone = if (selectedZone == RateZone.SANAA) RateZone.ADEN else RateZone.SANAA
                                            isRateManuallyEdited = false
                                        }
                                        .testTag("rate_zone_toggle_badge")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(if (selectedZone == RateZone.SANAA) MikroTikCyan else StatusWarning, RoundedCornerShape(4.dp))
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (selectedZone == RateZone.SANAA) "نطاق صنعاء" else "نطاق عدن",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = if (selectedZone == RateZone.SANAA) MikroTikCyan else StatusWarning
                                        )
                                    }
                                }
                            }
                        }

                        // Supplier search & picker
                        OutlinedTextField(
                            value = vendorSearchQuery,
                            onValueChange = { vendorSearchQuery = it },
                            placeholder = { Text("بحث عن مورد بالاسم...", fontSize = 12.sp, color = TextMutedDark) },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("input_vendor_search")
                        )

                        // Vendor chips
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(filteredVendors) { v ->
                                val isSelected = selectedVendorId == v.id
                                Surface(
                                    color = if (isSelected) MikroTikPrimary.copy(alpha = 0.25f) else CyberDarkCardElevated,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, if (isSelected) MikroTikCyan else CyberBorder),
                                    modifier = Modifier
                                        .clickable { selectedVendorId = v.id }
                                        .testTag("chip_vendor_${v.id}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = v.name,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) MikroTikCyan else TextPrimaryDark,
                                            fontSize = 12.sp,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }

                        // Currency Selection (Dropdown + Quick Selector)
                        Text("العملة المعتمدة للفاتورة:", fontSize = 12.sp, color = TextSecondaryDark, fontWeight = FontWeight.SemiBold)

                        var currencyDropdownExpanded by remember { mutableStateOf(false) }
                        val availableCurrencies = listOf(CurrencyCode.YER, CurrencyCode.SAR, CurrencyCode.USD)

                        ExposedDropdownMenuBox(
                            expanded = currencyDropdownExpanded,
                            onExpandedChange = { currencyDropdownExpanded = it },
                            modifier = Modifier.fillMaxWidth().testTag("dropdown_currency_selector")
                        ) {
                            OutlinedTextField(
                                value = "${selectedCurrency.name} (${selectedCurrency.arabicName} - ${selectedCurrency.symbol})",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("قائمة العملات المتاحة") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = currencyDropdownExpanded) },
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MikroTikCyan,
                                    unfocusedBorderColor = CyberBorder
                                ),
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = currencyDropdownExpanded,
                                onDismissRequest = { currencyDropdownExpanded = false }
                            ) {
                                availableCurrencies.forEach { curr ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    "${curr.name} - ${curr.arabicName} (${curr.symbol})",
                                                    fontWeight = if (selectedCurrency == curr) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (selectedCurrency == curr) MikroTikCyan else TextPrimaryDark
                                                )
                                                if (selectedCurrency == curr) {
                                                    Icon(Icons.Default.Check, contentDescription = null, tint = MikroTikCyan, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        },
                                        onClick = {
                                            selectedCurrency = curr
                                            currencyDropdownExpanded = false
                                        },
                                        modifier = Modifier.testTag("dropdown_currency_${curr.name}")
                                    )
                                }
                            }
                        }

                        CurrencySelector(
                            selectedCurrency = selectedCurrency,
                            onCurrencySelected = { selectedCurrency = it }
                        )

                        // Rate Details Card if foreign, or read-only parity notice if YER
                        if (selectedCurrency != CurrencyCode.FUNCTIONAL) {
                            Surface(
                                color = CyberDarkCardElevated,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MikroTikCyan.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.CurrencyExchange, contentDescription = null, tint = MikroTikCyan, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                "سعر الصرف (1 ${selectedCurrency.name} مقابل YER):",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimaryDark
                                            )
                                        }
                                        Surface(
                                            color = MikroTikCyan.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.clickable {
                                                selectedZone = if (selectedZone == RateZone.SANAA) RateZone.ADEN else RateZone.SANAA
                                            }
                                        ) {
                                            Text(
                                                if (selectedZone == RateZone.SANAA) "نطاق صنعاء (اضغط للتبديل)" else "نطاق عدن (اضغط للتبديل)",
                                                fontSize = 11.sp,
                                                color = MikroTikCyan,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    OutlinedTextField(
                                        value = rateInputText,
                                        onValueChange = {
                                            rateInputText = it
                                            isRateManuallyEdited = true
                                        },
                                        label = { Text("سعر الصرف بالريال اليمني (قابل للتعديل الاستثنائي)") },
                                        trailingIcon = {
                                            if (isRateManuallyEdited) {
                                                IconButton(
                                                    onClick = {
                                                        rateInputText = when (selectedCurrency) {
                                                            CurrencyCode.USD -> ExchangeRate.formatRateMicros(effectiveConfig.defaultUsdRateMicros)
                                                            CurrencyCode.SAR -> ExchangeRate.formatRateMicros(effectiveConfig.defaultSarRateMicros)
                                                            else -> "1.0"
                                                        }
                                                        isRateManuallyEdited = false
                                                    }
                                                ) {
                                                    Icon(Icons.Default.Refresh, contentDescription = "استعادة الافتراضي من هوية الشبكة", tint = MikroTikCyan, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MikroTikCyan,
                                            unfocusedBorderColor = CyberBorder
                                        ),
                                        modifier = Modifier.fillMaxWidth().testTag("input_purchase_fx_rate")
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (isRateManuallyEdited) "سعر صرف مخصص لهذه الفاتورة فقط" else "مستمد تلقائياً من هوية الشبكة (${if (selectedZone == RateZone.SANAA) "صنعاء" else "عدن"})",
                                            fontSize = 11.sp,
                                            color = if (isRateManuallyEdited) StatusWarning else TextSecondaryDark
                                        )
                                        Text(
                                            text = "1 ${selectedCurrency.name} = ${ExchangeRate.formatRateMicros(parsedRateMicros)} YER",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MikroTikCyan
                                        )
                                    }
                                }
                            }
                        } else {
                            Surface(
                                color = CyberDarkCardElevated,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, CyberBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Lock, contentDescription = null, tint = StatusOnline, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "عملة الأساس المحلية (YER) - سعر الصرف ثابت 1.0 (لا يتطلب تحويل)",
                                        fontSize = 12.sp,
                                        color = StatusOnline
                                    )
                                }
                            }
                        }
                    }
                }

                // SECTION 2: ITEMS ENTRY DYNAMIC MULTI-ITEM GRID
                Card(
                    colors = CardDefaults.cardColors(containerColor = CyberDarkSurface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, CyberBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = MikroTikCyan.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("2", color = MikroTikCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("إضافة بنود وأصناف الفاتورة", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimaryDark)
                            }
                            Text("${itemsList.size} بنود مضافة", fontSize = 11.sp, color = TextSecondaryDark)
                        }

                        // Quick presets chips for network hardware & consumables
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val suggestions = listOf("راوتر MikroTik CCR", "سويتش Gigabit 24-Port", "أنتينا Sector 5GHz", "كيبل إيثرنت Cat6 رول", "محول طاقة PoE 24V", "بطارية جيل 150Ah")
                            items(suggestions) { sugg ->
                                Surface(
                                    color = CyberDarkCardElevated,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, CyberBorder),
                                    modifier = Modifier.clickable {
                                        selectedPackage = null
                                        selectedPackageId = ""
                                        itemDesc = sugg
                                        isFixedAsset = sugg.contains("راوتر") || sugg.contains("سويتش") || sugg.contains("أنتينا") || sugg.contains("بطارية")
                                    }
                                ) {
                                    Text(
                                        text = sugg,
                                        fontSize = 11.sp,
                                        color = TextSecondaryDark,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        // Package & Item Selection Dropdown
                        ExposedDropdownMenuBox(
                            expanded = packageDropdownExpanded,
                            onExpandedChange = { packageDropdownExpanded = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("dropdown_package_selector")
                        ) {
                            OutlinedTextField(
                                value = selectedPackage?.name ?: itemDesc,
                                onValueChange = { newText ->
                                    itemDesc = newText
                                    if (selectedPackage?.name != newText) {
                                        selectedPackage = null
                                        selectedPackageId = ""
                                    }
                                },
                                label = { Text("الصنف / الباقة (اختر من القائمة أو اكتب يدوياً)") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = packageDropdownExpanded) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                                    .testTag("input_item_desc")
                            )

                            ExposedDropdownMenu(
                                expanded = packageDropdownExpanded,
                                onDismissRequest = { packageDropdownExpanded = false }
                            ) {
                                if (packages.isNotEmpty()) {
                                    Text(
                                        text = "فئات وباقات الكروت الذكية (مخزون)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MikroTikCyan,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                    )
                                    packages.forEach { pkg ->
                                        val isSelected = selectedPackageId == pkg.id || selectedPackage?.id == pkg.id
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column {
                                                        Text(
                                                            text = pkg.name,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                            color = if (isSelected) MikroTikCyan else TextPrimaryDark
                                                        )
                                                        Text(
                                                            text = "سعر التكلفة/الجملة: ${pkg.wholesalePriceMinor / 100L} ${selectedCurrency.name}",
                                                            fontSize = 11.sp,
                                                            color = TextSecondaryDark
                                                        )
                                                    }
                                                    if (isSelected) {
                                                        Icon(
                                                            Icons.Default.Check,
                                                            contentDescription = null,
                                                            tint = MikroTikCyan,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }
                                            },
                                            onClick = {
                                                selectedPackage = pkg
                                                selectedPackageId = pkg.id
                                                itemDesc = pkg.name
                                                if (pkg.wholesalePriceMinor > 0L) {
                                                    if (selectedCurrency == CurrencyCode.FUNCTIONAL) {
                                                        itemPriceText = (pkg.wholesalePriceMinor / 100L).toString()
                                                    } else {
                                                        val foreignMinor = (pkg.wholesalePriceMinor * 10_000L) / effectiveRate.rateMicros
                                                        itemPriceText = (foreignMinor / 100L).coerceAtLeast(1L).toString()
                                                    }
                                                }
                                                isFixedAsset = false
                                                packageDropdownExpanded = false
                                            },
                                            modifier = Modifier.testTag("dropdown_pkg_${pkg.id}")
                                        )
                                    }
                                    HorizontalDivider(color = CyberBorder, modifier = Modifier.padding(vertical = 4.dp))
                                }

                                Text(
                                    text = "أجهزة ومعدات شبكة مقترحة (أصول / مصروفات)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AssetPurple,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                )
                                val suggestedEquipment = listOf(
                                    Triple("راوتر MikroTik CCR", "250", true),
                                    Triple("سويتش Gigabit 24-Port", "120", true),
                                    Triple("أنتينا Sector 5GHz", "90", true),
                                    Triple("كيبل إيثرنت Cat6 رول", "60", false),
                                    Triple("محول طاقة PoE 24V", "15", false),
                                    Triple("بطارية جيل 150Ah", "180", true)
                                )
                                suggestedEquipment.forEach { (equipName, defaultPrice, isAsset) ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column {
                                                    Text(
                                                        text = equipName,
                                                        fontWeight = FontWeight.Medium,
                                                        color = TextPrimaryDark
                                                    )
                                                    Text(
                                                        text = if (isAsset) "أصل شبكة رأسمالي ($defaultPrice ${selectedCurrency.name})" else "مستهلكات شبكة ($defaultPrice ${selectedCurrency.name})",
                                                        fontSize = 11.sp,
                                                        color = TextSecondaryDark
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            selectedPackage = null
                                            selectedPackageId = ""
                                            itemDesc = equipName
                                            itemPriceText = defaultPrice
                                            isFixedAsset = isAsset
                                            packageDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = itemQtyText,
                                onValueChange = { itemQtyText = it },
                                label = { Text("الكمية") },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).testTag("input_item_qty")
                            )
                            OutlinedTextField(
                                value = itemPriceText,
                                onValueChange = { itemPriceText = it },
                                label = { Text("سعر الوحدة (${selectedCurrency.name})") },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1.5f).testTag("input_item_price")
                            )
                        }

                        // Row calculation preview
                        val enteredQty = itemQtyText.toIntOrNull() ?: 0
                        val enteredPrice = Money.parseFromUserInput(itemPriceText, selectedCurrency)?.minor ?: 0L
                        val rowCalculatedMinor = enteredQty * enteredPrice
                        val rowCalculatedYerMinor = if (selectedCurrency == CurrencyCode.FUNCTIONAL) rowCalculatedMinor else effectiveRate.convert(rowCalculatedMinor)

                        if (rowCalculatedMinor > 0L) {
                            Surface(
                                color = CyberDarkCardElevated,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("إجمالي البند المتوقع:", fontSize = 11.sp, color = TextSecondaryDark)
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            Money(rowCalculatedMinor, selectedCurrency).format(),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = MikroTikCyan
                                        )
                                        if (selectedCurrency != CurrencyCode.FUNCTIONAL) {
                                            Text(
                                                "≈ ${Money(rowCalculatedYerMinor, CurrencyCode.FUNCTIONAL).format()}",
                                                fontSize = 11.sp,
                                                color = StatusOnline
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Asset Classification Switch & Useful Months
                        Surface(
                            color = if (isFixedAsset) AssetPurple.copy(alpha = 0.12f) else CyberDarkCardElevated,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, if (isFixedAsset) AssetPurple.copy(alpha = 0.4f) else CyberBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = isFixedAsset,
                                            onCheckedChange = { isFixedAsset = it },
                                            modifier = Modifier.testTag("chk_fixed_asset")
                                        )
                                        Text(
                                            "تصنيف كـ أصل شبكة رأسمالي (حساب 1501)",
                                            fontSize = 12.sp,
                                            fontWeight = if (isFixedAsset) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isFixedAsset) AssetPurple else TextPrimaryDark
                                        )
                                    }
                                    if (isFixedAsset) {
                                        Surface(
                                            color = AssetPurple.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text("إهلاك تلقائي", fontSize = 10.sp, color = AssetPurple, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                if (isFixedAsset) {
                                    OutlinedTextField(
                                        value = usefulMonthsText,
                                        onValueChange = { usefulMonthsText = it },
                                        label = { Text("العمر الافتراضي للاستهلاك (بالأشهر)") },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth().testTag("input_useful_months")
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = {
                                val q = itemQtyText.toIntOrNull() ?: 1
                                val p = Money.parseFromUserInput(itemPriceText, selectedCurrency)?.minor ?: 0L
                                val m = usefulMonthsText.toIntOrNull() ?: 24
                                val code = if (isFixedAsset) AccountConstants.FIXED_ASSETS_NETWORK else AccountConstants.OPERATING_EXPENSES
                                val descToUse = selectedPackage?.name ?: itemDesc.ifBlank { "معدات شبكة" }
                                itemsList.add(PurchaseItemSpec(descToUse, code, q, p, isFixedAsset, m))
                                selectedPackage = null
                                selectedPackageId = ""
                                itemDesc = ""
                                itemQtyText = "1"
                            },
                            enabled = selectedPackage != null || itemDesc.isNotBlank() || itemsList.isEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = MikroTikPrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("btn_add_purchase_item_row")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("إدراج الصنف في جدول الفاتورة", fontWeight = FontWeight.Bold)
                        }

                        // Added Items Multi-Row List
                        if (itemsList.isNotEmpty()) {
                            Text("الأصناف المدرجة في الفاتورة:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextPrimaryDark)
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                itemsList.forEachIndexed { idx, itm ->
                                    val rowTotal = itm.quantity * itm.unitPriceMinor
                                    val rowTotalYer = if (selectedCurrency == CurrencyCode.FUNCTIONAL) rowTotal else effectiveRate.convert(rowTotal)
                                    Surface(
                                        color = CyberDarkCardElevated,
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, if (itm.isAsset) AssetPurple.copy(alpha = 0.3f) else CyberBorder),
                                        modifier = Modifier.fillMaxWidth().testTag("item_row_$idx")
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(itm.description, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimaryDark)
                                                    if (itm.isAsset) {
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Surface(color = AssetPurple.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                                                            Text("أصل ${itm.usefulLifeMonths} شهر", fontSize = 9.sp, color = AssetPurple, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                                        }
                                                    }
                                                }
                                                Text(
                                                    if (selectedCurrency == CurrencyCode.FUNCTIONAL) {
                                                        "${itm.quantity} قطعة × ${Money(itm.unitPriceMinor, selectedCurrency).format()} = ${Money(rowTotal, selectedCurrency).format()}"
                                                    } else {
                                                        "${itm.quantity} قطعة × ${Money(itm.unitPriceMinor, selectedCurrency).format()} = ${Money(rowTotal, selectedCurrency).format()} (المعادل: ${Money(rowTotalYer, CurrencyCode.FUNCTIONAL).format()})"
                                                    },
                                                    fontSize = 11.sp,
                                                    color = TextSecondaryDark
                                                )
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                IconButton(
                                                    onClick = {
                                                        val itmToEdit = itemsList.removeAt(idx)
                                                        itemDesc = itmToEdit.description
                                                        val matchedPkg = packages.find { it.name == itmToEdit.description }
                                                        selectedPackage = matchedPkg
                                                        selectedPackageId = matchedPkg?.id ?: ""
                                                        itemQtyText = itmToEdit.quantity.toString()
                                                        itemPriceText = (itmToEdit.unitPriceMinor / 100L).toString()
                                                        isFixedAsset = itmToEdit.isAsset
                                                        usefulMonthsText = itmToEdit.usefulLifeMonths.toString()
                                                    },
                                                    modifier = Modifier.testTag("btn_edit_item_$idx")
                                                ) {
                                                    Icon(Icons.Default.Edit, contentDescription = "تعديل", tint = MikroTikCyan, modifier = Modifier.size(18.dp))
                                                }
                                                IconButton(
                                                    onClick = { itemsList.removeAt(idx) },
                                                    modifier = Modifier.testTag("btn_delete_item_$idx")
                                                ) {
                                                    Icon(Icons.Default.Delete, contentDescription = "حذف", tint = SemanticExpenseRed, modifier = Modifier.size(18.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // SECTION 3: LIVE FINANCIAL SUMMARY BAR (Elevated Container)
                if (itemsList.isNotEmpty()) {
                    Surface(
                        color = CyberDarkSurface,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.5.dp, MikroTikCyan.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth().testTag("purchase_totals_summary_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("إجمالي الفاتورة (${selectedCurrency.name}):", fontWeight = FontWeight.SemiBold, color = TextSecondaryDark, fontSize = 13.sp)
                                Text(
                                    Money(totalForeignMinor, selectedCurrency).format(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = MikroTikCyan
                                )
                            }

                            if (selectedCurrency != CurrencyCode.FUNCTIONAL) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "سعر الصرف المطبق (${if (selectedZone == RateZone.SANAA) "صنعاء" else "عدن"}):",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextMutedDark,
                                        fontSize = 11.sp
                                    )
                                    Text(
                                        effectiveRate?.let { "1 ${selectedCurrency.name} = ${ExchangeRate.formatRateMicros(it.rateMicros)} YER" } ?: "غير محدد",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = TextSecondaryDark
                                    )
                                }

                                HorizontalDivider(color = CyberBorder)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            "الإجمالي بالريال اليمني (قيد الأستاذ العام):",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = TextPrimaryDark
                                        )
                                        Text(
                                            "الأساس المحاسبي لترحيل الالتزام والأصل",
                                            fontSize = 10.sp,
                                            color = TextMutedDark
                                        )
                                    }
                                    Text(
                                        Money(totalYerMinor, CurrencyCode.FUNCTIONAL).format(),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = SemanticIncomeGreen
                                    )
                                }
                            }
                        }
                    }
                }

                // Notes input
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("البيان والملاحظات (رقم الفاتورة اليدوي أو شروط الدفع)") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("input_purchase_notes")
                )
            }

            HorizontalDivider(color = CyberBorder)

            // ACTION BAR: Modern elevated primary action button with visual validation states
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val canSubmit = itemsList.isNotEmpty()
                Button(
                    onClick = {
                        if (canSubmit) {
                            val rate = if (selectedCurrency == CurrencyCode.FUNCTIONAL) {
                                ExchangeRate.parity(CurrencyCode.FUNCTIONAL)
                            } else {
                                effectiveRate
                            }
                            onSubmit(selectedVendorId, selectedCurrency, rate, itemsList.toList(), notes)
                        }
                    },
                    enabled = canSubmit,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (canSubmit) MikroTikCyan else CyberDarkCardElevated,
                        contentColor = if (canSubmit) CyberDarkCanvas else TextMutedDark
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("btn_submit_purchase_invoice")
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (itemsList.isEmpty()) "أضف بنوداً للاعتماد" else "ترحيل الفاتورة واعتماد السند",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.height(52.dp)
                ) {
                    Text("إلغاء", color = TextSecondaryDark)
                }
            }
        }
    }
}
