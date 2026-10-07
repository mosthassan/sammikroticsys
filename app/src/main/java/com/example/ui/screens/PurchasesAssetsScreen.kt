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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.example.data.local.entity.DocumentEntity
import com.example.data.local.entity.DocumentItemEntity
import com.example.ui.components.AmountSemanticType
import com.example.ui.components.AmountText
import com.example.ui.components.CurrencySelector
import com.example.ui.components.ExchangeRateCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusChip
import com.example.domain.usecase.ExchangeRateResolver
import androidx.compose.runtime.LaunchedEffect
import com.example.ui.theme.SemanticExpenseRed
import com.example.ui.theme.SemanticIncomeGreen
import com.example.ui.viewmodel.AppViewModel

@Composable
fun PurchasesAssetsScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val documents by viewModel.allDocuments.collectAsState()
    val assets by viewModel.allAssets.collectAsState()
    val parties by viewModel.allParties.collectAsState()
    val treasuries by viewModel.allTreasuries.collectAsState()

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
        NewPurchaseBottomSheet(
            parties = parties,
            resolver = viewModel.exchangeRateResolver,
            onDismiss = { showNewPurchaseSheet = false },
            onSubmit = { vendorId, currency, rate, items, notes ->
                viewModel.postPurchaseInvoice(
                    vendorPartyId = vendorId,
                    currency = currency,
                    exchangeRate = rate,
                    items = items,
                    notes = notes,
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
    resolver: ExchangeRateResolver? = null,
    onDismiss: () -> Unit,
    onSubmit: (vendorId: String, currency: CurrencyCode, rate: ExchangeRate, items: List<PurchaseItemSpec>, notes: String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val effectiveResolver = resolver ?: remember { ExchangeRateResolver(AppDatabase.getInstance(context)) }
    var selectedVendorId by rememberSaveable { mutableStateOf(parties.firstOrNull { it.isVendor }?.id ?: AppDatabase.WALK_IN_CASH_PARTY_ID) }
    var selectedCurrency by rememberSaveable { mutableStateOf(CurrencyCode.USD) }
    var selectedZone by rememberSaveable { mutableStateOf(RateZone.SANAA) }
    var resolvedRate by remember { mutableStateOf<ExchangeRate?>(null) }
    var manualRateOverride by remember { mutableStateOf<ExchangeRate?>(null) }
    var showManualRateDialog by remember { mutableStateOf(false) }
    var manualRateInputText by remember { mutableStateOf("") }
    var manualOverrideReason by remember { mutableStateOf("") }
    val today = remember { java.time.LocalDate.now().toEpochDay() }

    LaunchedEffect(selectedCurrency, selectedZone) {
        manualRateOverride = null
        if (selectedCurrency == CurrencyCode.FUNCTIONAL) {
            resolvedRate = ExchangeRate.parity(CurrencyCode.FUNCTIONAL)
        } else {
            try {
                resolvedRate = effectiveResolver.resolve(selectedCurrency, today, selectedZone)
            } catch (e: Exception) {
                resolvedRate = effectiveResolver.resolveOrNull(selectedCurrency, today, selectedZone)
            }
        }
    }

    val effectiveRate = manualRateOverride ?: resolvedRate

    var itemDesc by rememberSaveable { mutableStateOf("") }
    var itemQtyText by rememberSaveable { mutableStateOf("1") }
    var itemPriceText by rememberSaveable { mutableStateOf("100") }
    var isFixedAsset by rememberSaveable { mutableStateOf(true) }
    var usefulMonthsText by rememberSaveable { mutableStateOf("24") }
    var notes by rememberSaveable { mutableStateOf("") }

    val itemsList = remember { androidx.compose.runtime.mutableStateListOf<PurchaseItemSpec>() }
    val scrollState = rememberScrollState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
        ) {
            Text(
                text = "فاتورة مشتريات وتجهيز شبكة",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            HorizontalDivider()

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("المورد:")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    items(parties.filter { it.isVendor }) { v ->
                        FilterChip(
                            selected = selectedVendorId == v.id,
                            onClick = { selectedVendorId = v.id },
                            label = { Text(v.name, maxLines = 1, softWrap = false) }
                        )
                    }
                }

                Text("العملة المعتمدة للفاتورة:")
                CurrencySelector(
                    selectedCurrency = selectedCurrency,
                    onCurrencySelected = { selectedCurrency = it }
                )

                if (selectedCurrency != CurrencyCode.FUNCTIONAL) {
                    ExchangeRateCard(
                        currency = selectedCurrency,
                        rate = effectiveRate,
                        zone = selectedZone,
                        onZoneToggle = {
                            selectedZone = if (selectedZone == RateZone.SANAA) RateZone.ADEN else RateZone.SANAA
                            manualRateOverride = null
                        },
                        onEditRateClick = {
                            manualRateInputText = effectiveRate?.let { ExchangeRate.formatRateMicros(it.rateMicros) } ?: ""
                            manualOverrideReason = "سعر صراف معتمد / تقلبات سوق"
                            showManualRateDialog = true
                        }
                    )
                }

                SectionHeader(title = "إضافة بند مشتريات")
                OutlinedTextField(
                    value = itemDesc,
                    onValueChange = { itemDesc = it },
                    label = { Text("اسم الجهاز أو المادة (مثال: راوتر MikroTik CCR)") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = itemQtyText,
                        onValueChange = { itemQtyText = it },
                        label = { Text("الكمية") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = itemPriceText,
                        onValueChange = { itemPriceText = it },
                        label = { Text("السعر بالـ ${selectedCurrency.name}") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1.5f)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isFixedAsset, onCheckedChange = { isFixedAsset = it })
                    Text("أصل شبكة ثابت (يُدرج تلقائياً في سجل الأصول 1501)")
                }

                if (isFixedAsset) {
                    OutlinedTextField(
                        value = usefulMonthsText,
                        onValueChange = { usefulMonthsText = it },
                        label = { Text("العمر الافتراضي (بالأشهر)") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Button(
                    onClick = {
                        val q = itemQtyText.toIntOrNull() ?: 1
                        val p = Money.parseFromUserInput(itemPriceText, selectedCurrency)?.minor ?: 0L
                        val m = usefulMonthsText.toIntOrNull() ?: 24
                        val code = if (isFixedAsset) AccountConstants.FIXED_ASSETS_NETWORK else AccountConstants.OPERATING_EXPENSES
                        itemsList.add(PurchaseItemSpec(itemDesc.ifBlank { "معدات شبكة" }, code, q, p, isFixedAsset, m))
                        itemDesc = ""
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("إضافة البند للفاتورة")
                }

                itemsList.forEachIndexed { idx, itm ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("• ${itm.description}: ${itm.quantity} × ${Money(itm.unitPriceMinor, selectedCurrency).format()}")
                            IconButton(onClick = { itemsList.removeAt(idx) }) {
                                Icon(Icons.Default.Delete, contentDescription = "حذف")
                            }
                        }
                    }
                }

                val totalForeignMinor = itemsList.sumOf { it.quantity * it.unitPriceMinor }
                val totalYerMinor = if (selectedCurrency == CurrencyCode.FUNCTIONAL) {
                    totalForeignMinor
                } else {
                    effectiveRate?.convert(totalForeignMinor) ?: 0L
                }

                if (itemsList.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth().testTag("purchase_totals_summary_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("إجمالي الفاتورة (${selectedCurrency.name}):", fontWeight = FontWeight.SemiBold)
                                Text(
                                    Money(totalForeignMinor, selectedCurrency).format(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (selectedCurrency != CurrencyCode.FUNCTIONAL) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "المعادل بالريال اليمني (${if (selectedZone == RateZone.SANAA) "صنعاء" else "عدن"}):",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        Money(totalYerMinor, CurrencyCode.FUNCTIONAL).format(),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = SemanticIncomeGreen
                                    )
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("ملاحظات") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            HorizontalDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        if (itemsList.isNotEmpty()) {
                            val rate = if (selectedCurrency == CurrencyCode.FUNCTIONAL) {
                                ExchangeRate.parity(CurrencyCode.FUNCTIONAL)
                            } else {
                                effectiveRate ?: error("Exchange rate not resolved for $selectedCurrency")
                            }
                            onSubmit(selectedVendorId, selectedCurrency, rate, itemsList.toList(), notes)
                        }
                    },
                    enabled = itemsList.isNotEmpty() && (selectedCurrency == CurrencyCode.FUNCTIONAL || effectiveRate != null),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text("ترحيل فاتورة المشتريات", fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onDismiss, modifier = Modifier.height(48.dp)) {
                    Text("إلغاء")
                }
            }
        }
    }

    if (showManualRateDialog) {
        AlertDialog(
            onDismissRequest = { showManualRateDialog = false },
            title = { Text("تعديل سعر الصرف يدوياً", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "أدخل سعر الصرف المعتمد لـ 1 ${selectedCurrency.name} بالريال اليمني (YER) لتسعيرة ${if (selectedZone == RateZone.SANAA) "صنعاء" else "عدن"}:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = manualRateInputText,
                        onValueChange = { manualRateInputText = it },
                        label = { Text("سعر الصرف (مثال: 535.50 أو 1900)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_manual_exchange_rate")
                    )
                    OutlinedTextField(
                        value = manualOverrideReason,
                        onValueChange = { manualOverrideReason = it },
                        label = { Text("سبب التعديل اليدوي") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_manual_rate_reason")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = ExchangeRate.parseRateFromUserInput(manualRateInputText)
                        if (parsed != null && parsed > 0L) {
                            manualRateOverride = ExchangeRate(selectedCurrency, CurrencyCode.FUNCTIONAL, parsed)
                            showManualRateDialog = false
                        }
                    },
                    modifier = Modifier.testTag("btn_confirm_manual_rate")
                ) {
                    Text("تطبيق السعر")
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualRateDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}
