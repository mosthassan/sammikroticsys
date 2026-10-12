package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.example.core.time.AppClock
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.core.ledger.AccountConstants
import com.example.core.model.CurrencyCode
import com.example.core.model.ExchangeRate
import com.example.core.model.Money
import com.example.core.model.RateSource
import com.example.core.model.RateZone
import com.example.data.ledger.PurchaseItemSpec
import com.example.data.local.AppDatabase
import com.example.data.local.entity.CardPackageEntity
import com.example.data.local.entity.PartyEntity
import com.example.data.local.entity.TreasuryAccountEntity
import com.example.data.network.NetworkConfig
import com.example.data.network.NetworkRepository
import com.example.domain.usecase.ExchangeRateResolver
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
import com.example.ui.theme.StatusOnline
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.TextMutedDark
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark

enum class PurchasePaymentMode(val title: String, val subtitle: String) {
    ON_CREDIT("آجل (ذمم دائنة)", "يُقيّد كامل المبلغ كالتزام مستحق في حساب المورد (2101)"),
    FULL_CASH("نقدي كامل", "سداد فوري لكامل قيمة الفاتورة من الخزينة/الصندوق المختار"),
    PARTIAL_CASH("سداد جزئي", "دفعة نقدية فورية مع ترحيل المبلغ المتبقي كذمة آجلة")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewPurchaseInvoiceDialog(
    parties: List<PartyEntity>,
    packages: List<CardPackageEntity> = emptyList(),
    treasuries: List<TreasuryAccountEntity> = emptyList(),
    resolver: ExchangeRateResolver? = null,
    networkConfig: NetworkConfig? = null,
    vendorBalances: Map<String, Long> = emptyMap(),
    onDismiss: () -> Unit,
    onSubmit: (
        vendorId: String,
        currency: CurrencyCode,
        rate: ExchangeRate,
        items: List<PurchaseItemSpec>,
        notes: String,
        rateZone: RateZone,
        rateSource: RateSource,
        isCash: Boolean,
        cashPaidMinor: Long,
        treasuryId: String?
    ) -> Unit
) {
    val context = LocalContext.current
    val effectiveConfig = networkConfig ?: remember {
        NetworkRepository(context).config.value
    }
    val effectiveResolver = resolver ?: remember { ExchangeRateResolver(AppDatabase.getInstance(context)) }

    val vendors = remember(parties) {
        parties.filter { it.isVendor || it.id == AppDatabase.WALK_IN_CASH_PARTY_ID }
    }

    var vendorSearchQuery by rememberSaveable { mutableStateOf("") }
    var selectedVendorId by rememberSaveable {
        mutableStateOf(vendors.firstOrNull { it.id != AppDatabase.WALK_IN_CASH_PARTY_ID }?.id ?: AppDatabase.WALK_IN_CASH_PARTY_ID)
    }

    var selectedCurrency by rememberSaveable { mutableStateOf(CurrencyCode.FUNCTIONAL) }
    var selectedZone by rememberSaveable { mutableStateOf(effectiveConfig.rateZone) }
    var rateInputText by rememberSaveable { mutableStateOf("1.0") }
    var isManualRateOverride by rememberSaveable { mutableStateOf(false) }
    var manualRateReason by rememberSaveable { mutableStateOf("") }

    val today = remember { AppClock.todayEpochDay() }

    // Dynamic rate resolution when currency or rate zone toggles
    LaunchedEffect(selectedCurrency, selectedZone, effectiveConfig) {
        if (!isManualRateOverride) {
            when (selectedCurrency) {
                CurrencyCode.YER -> {
                    rateInputText = "1.0"
                }
                CurrencyCode.USD -> {
                    val defaultRateMicros = if (selectedZone == effectiveConfig.rateZone && effectiveConfig.defaultUsdRateMicros > 0L) {
                        effectiveConfig.defaultUsdRateMicros
                    } else {
                        effectiveResolver.resolveOrNull(CurrencyCode.USD, today, selectedZone)?.rateMicros
                            ?: if (selectedZone == RateZone.ADEN) 1_600_000_000L else 535_000_000L
                    }
                    rateInputText = ExchangeRate.formatRateMicros(defaultRateMicros)
                }
                CurrencyCode.SAR -> {
                    val defaultRateMicros = if (selectedZone == effectiveConfig.rateZone && effectiveConfig.defaultSarRateMicros > 0L) {
                        effectiveConfig.defaultSarRateMicros
                    } else {
                        effectiveResolver.resolveOrNull(CurrencyCode.SAR, today, selectedZone)?.rateMicros
                            ?: if (selectedZone == RateZone.ADEN) 420_000_000L else 140_500_000L
                    }
                    rateInputText = ExchangeRate.formatRateMicros(defaultRateMicros)
                }
            }
        }
    }

    val parsedRateMicros: Long = if (selectedCurrency == CurrencyCode.FUNCTIONAL) {
        ExchangeRate.SCALE_MICROS
    } else {
        ExchangeRate.parseRateFromUserInput(rateInputText) ?: when (selectedCurrency) {
            CurrencyCode.USD -> if (selectedZone == RateZone.ADEN) 1_600_000_000L else 535_000_000L
            CurrencyCode.SAR -> if (selectedZone == RateZone.ADEN) 420_000_000L else 140_500_000L
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

    // Items list state
    val itemsList = remember { mutableStateListOf<PurchaseItemSpec>() }

    // Item form inputs
    var itemDesc by rememberSaveable { mutableStateOf("") }
    var itemQtyText by rememberSaveable { mutableStateOf("1") }
    var itemPriceText by rememberSaveable { mutableStateOf("100") }
    var isFixedAsset by rememberSaveable { mutableStateOf(true) }
    var usefulMonthsText by rememberSaveable { mutableStateOf("24") }
    var packageDropdownExpanded by remember { mutableStateOf(false) }
    var selectedPackage by remember { mutableStateOf<CardPackageEntity?>(null) }
    var selectedPackageId by rememberSaveable { mutableStateOf("") }

    // Payment Settlement section state
    var paymentMode by rememberSaveable { mutableStateOf(PurchasePaymentMode.ON_CREDIT) }
    var selectedTreasuryId by rememberSaveable {
        mutableStateOf(treasuries.firstOrNull { it.currency == selectedCurrency.name }?.id ?: treasuries.firstOrNull()?.id ?: "TR_MAIN_YER")
    }
    var partialPaidAmountText by rememberSaveable { mutableStateOf("") }
    var treasuryDropdownExpanded by remember { mutableStateOf(false) }

    var notes by rememberSaveable { mutableStateOf("") }

    // Safety Prompt State
    var showCloseConfirmDialog by remember { mutableStateOf(false) }

    val pendingQty = (itemQtyText.toIntOrNull() ?: 1).coerceAtLeast(1)
    val pendingPriceMinor = Money.parseFromUserInput(itemPriceText, selectedCurrency)?.minor ?: 0L
    val pendingDesc = selectedPackage?.name ?: itemDesc.trim()
    val hasPendingValidDraft = pendingDesc.isNotBlank() && pendingPriceMinor > 0L && pendingQty > 0
    val pendingRowTotalMinor = if (hasPendingValidDraft) pendingQty * pendingPriceMinor else 0L

    val hasDraftData = itemsList.isNotEmpty() || itemDesc.isNotBlank() || notes.isNotBlank()

    val totalForeignMinor = itemsList.sumOf { it.quantity * it.unitPriceMinor }
    val totalYerMinor = if (selectedCurrency == CurrencyCode.FUNCTIONAL) {
        totalForeignMinor
    } else {
        effectiveRate.convert(totalForeignMinor)
    }

    val effectiveItemCount = itemsList.size + (if (hasPendingValidDraft) 1 else 0)
    val effectiveTotalForeignMinor = totalForeignMinor + pendingRowTotalMinor
    val effectiveTotalYerMinor = if (selectedCurrency == CurrencyCode.FUNCTIONAL) {
        effectiveTotalForeignMinor
    } else {
        effectiveRate.convert(effectiveTotalForeignMinor)
    }

    val partialPaidMinor: Long = remember(partialPaidAmountText, selectedCurrency, effectiveTotalForeignMinor, paymentMode) {
        when (paymentMode) {
            PurchasePaymentMode.ON_CREDIT -> 0L
            PurchasePaymentMode.FULL_CASH -> effectiveTotalForeignMinor
            PurchasePaymentMode.PARTIAL_CASH -> {
                val entered = Money.parseFromUserInput(partialPaidAmountText, selectedCurrency)?.minor ?: 0L
                entered.coerceIn(0L, effectiveTotalForeignMinor)
            }
        }
    }

    val remainingPayableMinor = (effectiveTotalForeignMinor - partialPaidMinor).coerceAtLeast(0L)

    val canSubmit = effectiveItemCount > 0 && (!isManualRateOverride || manualRateReason.isNotBlank())

    val performSubmit: () -> Unit = {
        val finalItems = itemsList.toMutableList()
        if (hasPendingValidDraft) {
            val m = usefulMonthsText.toIntOrNull() ?: 24
            val code = if (isFixedAsset) AccountConstants.FIXED_ASSETS_NETWORK else AccountConstants.OPERATING_EXPENSES
            finalItems.add(PurchaseItemSpec(pendingDesc, code, pendingQty, pendingPriceMinor, isFixedAsset, m))
        }

        if (finalItems.isNotEmpty() && (!isManualRateOverride || manualRateReason.isNotBlank())) {
            val rate = if (selectedCurrency == CurrencyCode.FUNCTIONAL) {
                ExchangeRate.parity(CurrencyCode.FUNCTIONAL)
            } else {
                effectiveRate
            }
            val source = if (isManualRateOverride) RateSource.MANUAL_OVERRIDE else RateSource.SYSTEM_DAILY
            val isCash = paymentMode != PurchasePaymentMode.ON_CREDIT
            val cashAmount = partialPaidMinor
            val trId = if (isCash) selectedTreasuryId else null

            val finalNotes = if (isManualRateOverride && manualRateReason.isNotBlank()) {
                if (notes.isBlank()) "سعر صرف استثنائي: $manualRateReason" else "$notes (سعر صرف استثنائي: $manualRateReason)"
            } else notes

            onSubmit(
                selectedVendorId,
                selectedCurrency,
                rate,
                finalItems,
                finalNotes,
                selectedZone,
                source,
                isCash,
                cashAmount,
                trId
            )
        }
    }

    val scrollState = rememberScrollState()

    val filteredVendors = remember(vendors, vendorSearchQuery) {
        if (vendorSearchQuery.isBlank()) vendors
        else vendors.filter { it.name.contains(vendorSearchQuery.trim(), ignoreCase = true) }
    }

    // Intercept back press to prevent accidental data loss
    BackHandler {
        if (hasDraftData) {
            showCloseConfirmDialog = true
        } else {
            onDismiss()
        }
    }

    // Safety Alert Dialog
    if (showCloseConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showCloseConfirmDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = StatusWarning, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("إغلاق الفاتورة وتجاهل المسودة؟", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Text(
                    "توجد أصناف وبيانات مدخلة في مسودة الفاتورة الحالية. في حال الخروج الآن سيتم فقدان التعديلات غير المحفوظة.",
                    fontSize = 13.sp,
                    color = TextSecondaryDark
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCloseConfirmDialog = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SemanticExpenseRed)
                ) {
                    Text("نعم، تجاهل وإغلاق")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showCloseConfirmDialog = false }) {
                    Text("متابعة التعديل")
                }
            },
            containerColor = CyberDarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    Dialog(
        onDismissRequest = {
            if (hasDraftData) showCloseConfirmDialog = true else onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        val dialogView = LocalView.current
        DisposableEffect(dialogView) {
            var parent = dialogView.parent
            while (parent != null) {
                if (parent is DialogWindowProvider) {
                    WindowCompat.setDecorFitsSystemWindows(parent.window, false)
                    break
                }
                parent = parent.parent
            }
            onDispose {}
        }

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(CyberDarkCanvas),
            color = CyberDarkCanvas
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .imePadding()
            ) {
                // 1. HEADER TOOLBAR
                Surface(
                    color = CyberDarkSurface,
                    border = BorderStroke(1.dp, CyberBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    if (hasDraftData) showCloseConfirmDialog = true else onDismiss()
                                },
                                modifier = Modifier
                                    .size(38.dp)
                                    .testTag("btn_close_purchase_dialog")
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "إغلاق الفاتورة",
                                    tint = TextSecondaryDark,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = MikroTikCyan.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, MikroTikCyan.copy(alpha = 0.4f)),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.ShoppingCart,
                                        contentDescription = null,
                                        tint = MikroTikCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "فاتورة مشتريات وتوريد",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimaryDark,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "نظام المشتريات ومعدات الشبكة والأصول",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondaryDark,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        // Right section: Item counter chip + Quick Save button
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                color = if (effectiveItemCount > 0) MikroTikCyan.copy(alpha = 0.18f) else CyberDarkCardElevated,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, if (effectiveItemCount > 0) MikroTikCyan.copy(alpha = 0.6f) else CyberBorder)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Inventory,
                                        contentDescription = null,
                                        tint = if (effectiveItemCount > 0) MikroTikCyan else TextMutedDark,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "$effectiveItemCount أصناف",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = if (effectiveItemCount > 0) MikroTikCyan else TextMutedDark
                                    )
                                }
                            }

                            Button(
                                onClick = { performSubmit() },
                                enabled = canSubmit,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (canSubmit) MikroTikCyan else CyberDarkCardElevated,
                                    contentColor = if (canSubmit) CyberDarkCanvas else TextMutedDark
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                modifier = Modifier
                                    .height(36.dp)
                                    .testTag("btn_quick_save_purchase_invoice")
                            ) {
                                Icon(Icons.Default.Check, contentDescription = "حفظ", modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("حفظ", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }

                // SCROLLABLE FORM BODY
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // SECTION 1: SUPPLIER & VENDOR QUICK PICKER CARD
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
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text("1", color = MikroTikCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("المورد المعتمد للفاتورة", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimaryDark)
                                }

                                val currentVendor = vendors.find { it.id == selectedVendorId }
                                val currentBalance = vendorBalances[selectedVendorId] ?: 0L
                                if (currentVendor != null && currentBalance != 0L) {
                                    Surface(
                                        color = if (currentBalance > 0) SemanticExpenseRed.copy(alpha = 0.15f) else SemanticIncomeGreen.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, if (currentBalance > 0) SemanticExpenseRed.copy(alpha = 0.4f) else SemanticIncomeGreen.copy(alpha = 0.4f))
                                    ) {
                                        Text(
                                            text = if (currentBalance > 0) "له: ${Money(currentBalance, CurrencyCode.FUNCTIONAL).format()}" else "مقدم: ${Money(-currentBalance, CurrencyCode.FUNCTIONAL).format()}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (currentBalance > 0) SemanticExpenseRed else SemanticIncomeGreen,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            // Search field for suppliers
                            OutlinedTextField(
                                value = vendorSearchQuery,
                                onValueChange = { vendorSearchQuery = it },
                                placeholder = { Text("بحث عن مورد بالاسم...", fontSize = 12.sp, color = TextMutedDark) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMutedDark, modifier = Modifier.size(18.dp)) },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MikroTikCyan,
                                    unfocusedBorderColor = CyberBorder
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("input_vendor_search")
                            )

                            // Quick-switch horizontal chips
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(filteredVendors) { v ->
                                    val isSelected = selectedVendorId == v.id
                                    val balance = vendorBalances[v.id] ?: 0L
                                    Surface(
                                        color = if (isSelected) MikroTikPrimary.copy(alpha = 0.25f) else CyberDarkCardElevated,
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, if (isSelected) MikroTikCyan else CyberBorder),
                                        modifier = Modifier
                                            .clickable { selectedVendorId = v.id }
                                            .testTag("chip_vendor_${v.id}")
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (isSelected) {
                                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MikroTikCyan, modifier = Modifier.size(12.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                }
                                                Text(
                                                    text = v.name,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected) MikroTikCyan else TextPrimaryDark,
                                                    fontSize = 12.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            if (balance != 0L) {
                                                Text(
                                                    text = if (balance > 0) "له: ${Money(balance, CurrencyCode.FUNCTIONAL).format()}" else "مقدم: ${Money(-balance, CurrencyCode.FUNCTIONAL).format()}",
                                                    fontSize = 10.sp,
                                                    color = if (balance > 0) SemanticExpenseRed else SemanticIncomeGreen,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // SECTION 2: SMART CURRENCY & EXCHANGE RATE HUB
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
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text("2", color = MikroTikCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("عملة الفاتورة وسعر الصرف", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimaryDark)
                                }

                                // Interactive RateZone switch with live badge
                                if (selectedCurrency != CurrencyCode.FUNCTIONAL) {
                                    Surface(
                                        color = if (selectedZone == RateZone.SANAA) MikroTikNavyLight else CyberDarkCardElevated,
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, if (selectedZone == RateZone.SANAA) MikroTikCyan.copy(alpha = 0.6f) else StatusWarning.copy(alpha = 0.6f)),
                                        modifier = Modifier
                                            .clickable {
                                                selectedZone = if (selectedZone == RateZone.SANAA) RateZone.ADEN else RateZone.SANAA
                                                isManualRateOverride = false
                                            }
                                            .testTag("rate_zone_toggle_badge")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.Sync,
                                                contentDescription = "تبديل النطاق",
                                                tint = if (selectedZone == RateZone.SANAA) MikroTikCyan else StatusWarning,
                                                modifier = Modifier.size(13.dp)
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

                            // Currency Selector Tabs
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(CurrencyCode.YER, CurrencyCode.USD, CurrencyCode.SAR).forEach { curr ->
                                    val isCurrSelected = selectedCurrency == curr
                                    Surface(
                                        color = if (isCurrSelected) MikroTikPrimary.copy(alpha = 0.3f) else CyberDarkCardElevated,
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.5.dp, if (isCurrSelected) MikroTikCyan else CyberBorder),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                selectedCurrency = curr
                                                isManualRateOverride = false
                                            }
                                            .testTag("tab_currency_${curr.name}")
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(vertical = 10.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = curr.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = if (isCurrSelected) MikroTikCyan else TextPrimaryDark
                                            )
                                            Text(
                                                text = curr.arabicName,
                                                fontSize = 10.sp,
                                                color = if (isCurrSelected) MikroTikCyan.copy(alpha = 0.8f) else TextSecondaryDark
                                            )
                                        }
                                    }
                                }
                            }

                            // Foreign Currency Rate Card with Live Calculation
                            if (selectedCurrency != CurrencyCode.FUNCTIONAL) {
                                Surface(
                                    color = CyberDarkCardElevated,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, MikroTikCyan.copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
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

                                            // Clickable Zone switch button
                                            Surface(
                                                color = MikroTikCyan.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.clickable {
                                                    selectedZone = if (selectedZone == RateZone.SANAA) RateZone.ADEN else RateZone.SANAA
                                                    isManualRateOverride = false
                                                }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(Icons.Default.Sync, contentDescription = null, tint = MikroTikCyan, modifier = Modifier.size(12.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        if (selectedZone == RateZone.SANAA) "صنعاء (اضغط للتبديل)" else "عدن (اضغط للتبديل)",
                                                        fontSize = 11.sp,
                                                        color = MikroTikCyan,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }

                                        // Rate Input Field
                                        OutlinedTextField(
                                            value = rateInputText,
                                            onValueChange = {
                                                rateInputText = it
                                                isManualRateOverride = true
                                            },
                                            label = { Text("سعر الصرف المطبق بالريال اليمني") },
                                            trailingIcon = {
                                                if (isManualRateOverride) {
                                                    IconButton(
                                                        onClick = {
                                                            rateInputText = when (selectedCurrency) {
                                                                CurrencyCode.USD -> {
                                                                    val rate = if (selectedZone == effectiveConfig.rateZone && effectiveConfig.defaultUsdRateMicros > 0L) {
                                                                        effectiveConfig.defaultUsdRateMicros
                                                                    } else if (selectedZone == RateZone.ADEN) 1_600_000_000L else 535_000_000L
                                                                    ExchangeRate.formatRateMicros(rate)
                                                                }
                                                                CurrencyCode.SAR -> {
                                                                    val rate = if (selectedZone == effectiveConfig.rateZone && effectiveConfig.defaultSarRateMicros > 0L) {
                                                                        effectiveConfig.defaultSarRateMicros
                                                                    } else if (selectedZone == RateZone.ADEN) 420_000_000L else 140_500_000L
                                                                    ExchangeRate.formatRateMicros(rate)
                                                                }
                                                                else -> "1.0"
                                                            }
                                                            isManualRateOverride = false
                                                            manualRateReason = ""
                                                        }
                                                    ) {
                                                        Icon(Icons.Default.Refresh, contentDescription = "استعادة السعر الافتراضي للشبكة", tint = MikroTikCyan, modifier = Modifier.size(18.dp))
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

                                        // Manual override indicator & reason text
                                        AnimatedVisibility(visible = isManualRateOverride) {
                                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                OutlinedTextField(
                                                    value = manualRateReason,
                                                    onValueChange = { manualRateReason = it },
                                                    label = { Text("سبب تحديد سعر الصرف اليدوي/الاستثنائي (إلزامي)") },
                                                    placeholder = { Text("مثال: سعر صراف خاص بالفاتورة / اتفاقية مؤجلة", fontSize = 11.sp) },
                                                    shape = RoundedCornerShape(10.dp),
                                                    colors = OutlinedTextFieldDefaults.colors(
                                                        focusedBorderColor = StatusWarning,
                                                        unfocusedBorderColor = CyberBorder
                                                    ),
                                                    modifier = Modifier.fillMaxWidth().testTag("input_manual_rate_reason")
                                                )
                                                Text(
                                                    text = "⚠️ سيتم توثيق سعر الصرف اليدوي في قيود اليومية دون تعديل هوية الشبكة العامة",
                                                    fontSize = 11.sp,
                                                    color = StatusWarning
                                                )
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = if (isManualRateOverride) "سعر استثنائي يدوي" else "مستمد من ملف الشبكة (${if (selectedZone == RateZone.SANAA) "صنعاء" else "عدن"})",
                                                fontSize = 11.sp,
                                                color = if (isManualRateOverride) StatusWarning else TextSecondaryDark
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
                                    shape = RoundedCornerShape(10.dp),
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
                                            "الريال اليمني (YER) هو عملة الأساس المحاسبية - سعر الصرف ثابت 1.0",
                                            fontSize = 11.sp,
                                            color = StatusOnline
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // SECTION 3: ITEM ENTRY & PRESETS
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
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text("3", color = MikroTikCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("إدخال بنود وأصناف التوريد", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimaryDark)
                                }
                                Text("سريعة وسهلة", fontSize = 11.sp, color = TextSecondaryDark)
                            }

                            // Quick Add Chips
                            Text("أصناف الشبكة الشائعة (إضافة سريعة):", fontSize = 11.sp, color = TextSecondaryDark, fontWeight = FontWeight.SemiBold)
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val suggestions = listOf(
                                    Triple("راوتر MikroTik CCR", "250", true),
                                    Triple("سويتش Gigabit 24-Port", "120", true),
                                    Triple("أنتينا Sector 5GHz", "90", true),
                                    Triple("كيبل إيثرنت Cat6 رول", "60", false),
                                    Triple("محول طاقة PoE 24V", "15", false),
                                    Triple("بطارية جيل 150Ah", "180", true)
                                )
                                items(suggestions) { (name, price, asset) ->
                                    Surface(
                                        color = CyberDarkCardElevated,
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, CyberBorder),
                                        modifier = Modifier.clickable {
                                            selectedPackage = null
                                            selectedPackageId = ""
                                            itemDesc = name
                                            itemPriceText = price
                                            isFixedAsset = asset
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                if (asset) Icons.Default.Inventory else Icons.Default.ShoppingCart,
                                                contentDescription = null,
                                                tint = if (asset) AssetPurple else MikroTikCyan,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = name,
                                                fontSize = 11.sp,
                                                color = TextPrimaryDark
                                            )
                                        }
                                    }
                                }
                            }

                            // Package Dropdown or manual item description
                            ExposedDropdownMenuBox(
                                expanded = packageDropdownExpanded,
                                onExpandedChange = { packageDropdownExpanded = it },
                                modifier = Modifier.fillMaxWidth().testTag("dropdown_package_selector")
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
                                    label = { Text("بيان الصنف / باقة الكروت (اختر أو اكتب)") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = packageDropdownExpanded) },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MikroTikCyan,
                                        unfocusedBorderColor = CyberBorder
                                    ),
                                    modifier = Modifier.menuAnchor().fillMaxWidth().testTag("input_item_desc")
                                )

                                ExposedDropdownMenu(
                                    expanded = packageDropdownExpanded,
                                    onDismissRequest = { packageDropdownExpanded = false }
                                ) {
                                    if (packages.isNotEmpty()) {
                                        Text(
                                            text = "باقات وكروت الشبكة (مخزون كروت)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MikroTikCyan,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                        )
                                        packages.forEach { pkg ->
                                            DropdownMenuItem(
                                                text = {
                                                    Column {
                                                        Text(pkg.name, fontWeight = FontWeight.Bold, color = TextPrimaryDark)
                                                        Text("سعر الجملة: ${pkg.wholesalePriceMinor / 100L} ${selectedCurrency.name}", fontSize = 11.sp, color = TextSecondaryDark)
                                                    }
                                                },
                                                onClick = {
                                                    selectedPackage = pkg
                                                    selectedPackageId = pkg.id
                                                    itemDesc = pkg.name
                                                    if (pkg.wholesalePriceMinor > 0L) {
                                                        val price = if (selectedCurrency == CurrencyCode.FUNCTIONAL) {
                                                            (pkg.wholesalePriceMinor / 100L).toString()
                                                        } else {
                                                            val foreignMinor = (pkg.wholesalePriceMinor * 10_000L) / effectiveRate.rateMicros
                                                            (foreignMinor / 100L).coerceAtLeast(1L).toString()
                                                        }
                                                        itemPriceText = price
                                                    }
                                                    isFixedAsset = false
                                                    packageDropdownExpanded = false
                                                }
                                            )
                                        }
                                        HorizontalDivider(color = CyberBorder)
                                    }

                                    Text(
                                        text = "معدات شبكة مقترحة",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AssetPurple,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                    )
                                    listOf(
                                        "راوتر MikroTik CCR" to true,
                                        "سويتش Gigabit 24-Port" to true,
                                        "أنتينا Sector 5GHz" to true,
                                        "كيبل إيثرنت Cat6 رول" to false,
                                        "محول طاقة PoE 24V" to false,
                                        "بطارية جيل 150Ah" to true
                                    ).forEach { (equip, asset) ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(equip, color = TextPrimaryDark)
                                            },
                                            onClick = {
                                                selectedPackage = null
                                                selectedPackageId = ""
                                                itemDesc = equip
                                                isFixedAsset = asset
                                                packageDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            // Quantity with Increment/Decrement and Unit Price
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Ergonomic Quantity with +/- stepper
                                Surface(
                                    color = CyberDarkCardElevated,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, if (hasPendingValidDraft) MikroTikCyan.copy(alpha = 0.5f) else CyberBorder),
                                    modifier = Modifier.weight(1.1f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        IconButton(
                                            onClick = {
                                                val curr = itemQtyText.toIntOrNull() ?: 1
                                                if (curr > 1) itemQtyText = (curr - 1).toString()
                                            },
                                            modifier = Modifier.size(38.dp)
                                        ) {
                                            Icon(Icons.Default.Remove, contentDescription = "تقليل الكمية", tint = TextSecondaryDark, modifier = Modifier.size(16.dp))
                                        }

                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("الكمية", fontSize = 10.sp, color = TextSecondaryDark)
                                            OutlinedTextField(
                                                value = itemQtyText,
                                                onValueChange = { itemQtyText = it.filter { ch -> ch.isDigit() } },
                                                textStyle = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, color = MikroTikCyan),
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = Color.Transparent,
                                                    unfocusedBorderColor = Color.Transparent
                                                ),
                                                modifier = Modifier.width(60.dp).height(38.dp).testTag("input_item_qty")
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                val curr = itemQtyText.toIntOrNull() ?: 1
                                                itemQtyText = (curr + 1).toString()
                                            },
                                            modifier = Modifier.size(38.dp)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = "زيادة الكمية", tint = MikroTikCyan, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }

                                // Unit Price Field
                                OutlinedTextField(
                                    value = itemPriceText,
                                    onValueChange = { itemPriceText = it },
                                    label = { Text("سعر الوحدة (${selectedCurrency.name})") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MikroTikCyan,
                                        unfocusedBorderColor = CyberBorder
                                    ),
                                    modifier = Modifier.weight(1.3f).testTag("input_item_price")
                                )
                            }

                            // Dynamic subtotal preview when typing an item
                            if (hasPendingValidDraft) {
                                Surface(
                                    color = MikroTikCyan.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, MikroTikCyan.copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = MikroTikCyan, modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "إجمالي الصنف الحالي ($pendingQty قطعة):",
                                                fontSize = 11.sp,
                                                color = TextPrimaryDark,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                        Text(
                                            text = Money(pendingRowTotalMinor, selectedCurrency).format(),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MikroTikCyan
                                        )
                                    }
                                }
                            }

                            // Fixed Asset or Operating Expense Toggle Card
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
                                                "أصل شبكة رأسمالي (حساب 1501)",
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

                                    AnimatedVisibility(visible = isFixedAsset) {
                                        OutlinedTextField(
                                            value = usefulMonthsText,
                                            onValueChange = { usefulMonthsText = it.filter { ch -> ch.isDigit() } },
                                            label = { Text("العمر الافتراضي للاستهلاك (بالأشهر)") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            shape = RoundedCornerShape(10.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = AssetPurple,
                                                unfocusedBorderColor = CyberBorder
                                            ),
                                            modifier = Modifier.fillMaxWidth().testTag("input_useful_months")
                                        )
                                    }
                                }
                            }

                            // Add Item Button with dynamic badge
                            Button(
                                onClick = {
                                    val q = (itemQtyText.toIntOrNull() ?: 1).coerceAtLeast(1)
                                    val p = Money.parseFromUserInput(itemPriceText, selectedCurrency)?.minor ?: 0L
                                    val m = usefulMonthsText.toIntOrNull() ?: 24
                                    val code = if (isFixedAsset) AccountConstants.FIXED_ASSETS_NETWORK else AccountConstants.OPERATING_EXPENSES
                                    val descToUse = selectedPackage?.name ?: itemDesc.ifBlank { "معدات شبكة" }
                                    itemsList.add(PurchaseItemSpec(descToUse, code, q, p, isFixedAsset, m))

                                    // Reset inputs
                                    selectedPackage = null
                                    selectedPackageId = ""
                                    itemDesc = ""
                                    itemQtyText = "1"
                                    itemPriceText = "100"
                                },
                                enabled = selectedPackage != null || itemDesc.isNotBlank() || itemsList.isEmpty(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (hasPendingValidDraft) MikroTikCyan else MikroTikPrimary,
                                    contentColor = if (hasPendingValidDraft) CyberDarkCanvas else Color.White
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_add_purchase_item_row")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (hasPendingValidDraft) {
                                        "إدراج الصنف في الفاتورة (${Money(pendingRowTotalMinor, selectedCurrency).format()})"
                                    } else {
                                        "إدراج الصنف في الفاتورة"
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    // SECTION 4: ITEMS CART (MODERN CARDS LIST OR EMPTY STATE)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CyberDarkSurface),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, if (itemsList.isNotEmpty()) CyberBorderGlow.copy(alpha = 0.3f) else CyberBorder),
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
                                        color = MikroTikCyan.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Inventory, contentDescription = null, tint = MikroTikCyan, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "جدول الأصناف المدرجة (${itemsList.size} بنود - ${itemsList.sumOf { it.quantity }} قطعة)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = TextPrimaryDark
                                    )
                                }
                                Text(
                                    text = Money(totalForeignMinor, selectedCurrency).format(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MikroTikCyan
                                )
                            }

                            if (itemsList.isEmpty()) {
                                Surface(
                                    color = CyberDarkCardElevated.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, CyberBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.Inventory, contentDescription = null, tint = TextMutedDark, modifier = Modifier.size(26.dp))
                                        Text(
                                            text = if (hasPendingValidDraft) "يوجد صنف قيد الإدخال حالياً أعلاه" else "لم يتم إدراج أصناف في الجدول بعد",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = if (hasPendingValidDraft) MikroTikCyan else TextSecondaryDark
                                        )
                                        Text(
                                            text = if (hasPendingValidDraft) "اضغط 'إدراج الصنف في الفاتورة' أو اضغط 'حفظ' لاعتماده تلقائياً" else "أدخل بيانات الصنف واضغط على إدراج لإضافته لجدول الفاتورة",
                                            fontSize = 11.sp,
                                            color = TextMutedDark,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            } else {
                                itemsList.forEachIndexed { idx, itm ->
                                    val rowTotal = itm.quantity * itm.unitPriceMinor
                                    val rowTotalYer = if (selectedCurrency == CurrencyCode.FUNCTIONAL) rowTotal else effectiveRate.convert(rowTotal)

                                    Surface(
                                        color = CyberDarkCardElevated,
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, if (itm.isAsset) AssetPurple.copy(alpha = 0.35f) else CyberBorder),
                                        modifier = Modifier.fillMaxWidth().testTag("item_row_$idx")
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Surface(
                                                        color = CyberDarkCanvas,
                                                        shape = CircleShape,
                                                        modifier = Modifier.size(20.dp)
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Text("${idx + 1}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondaryDark)
                                                        }
                                                    }
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = itm.description,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp,
                                                        color = TextPrimaryDark
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    if (itm.isAsset) {
                                                        Surface(
                                                            color = AssetPurple.copy(alpha = 0.2f),
                                                            shape = RoundedCornerShape(4.dp)
                                                        ) {
                                                            Text(
                                                                "أصل رأسمالي (${itm.usefulLifeMonths} شهر)",
                                                                fontSize = 9.sp,
                                                                color = AssetPurple,
                                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    } else {
                                                        Surface(
                                                            color = MikroTikCyan.copy(alpha = 0.15f),
                                                            shape = RoundedCornerShape(4.dp)
                                                        ) {
                                                            Text(
                                                                "مصروف تشغيل",
                                                                fontSize = 9.sp,
                                                                color = MikroTikCyan,
                                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }
                                                }

                                                Text(
                                                    text = "${itm.quantity} وحدة × ${Money(itm.unitPriceMinor, selectedCurrency).format()} = ${Money(rowTotal, selectedCurrency).format()}",
                                                    fontSize = 11.sp,
                                                    color = TextSecondaryDark
                                                )

                                                if (selectedCurrency != CurrencyCode.FUNCTIONAL) {
                                                    Text(
                                                        text = "المعادل بالريال: ${Money(rowTotalYer, CurrencyCode.FUNCTIONAL).format()}",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = SemanticIncomeGreen
                                                    )
                                                }
                                            }

                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                                IconButton(
                                                    onClick = {
                                                        if (itm.quantity > 1) {
                                                            itemsList[idx] = itm.copy(quantity = itm.quantity - 1)
                                                        } else {
                                                            itemsList.removeAt(idx)
                                                        }
                                                    },
                                                    modifier = Modifier.size(30.dp)
                                                ) {
                                                    Icon(Icons.Default.Remove, contentDescription = "تقليل", tint = TextSecondaryDark, modifier = Modifier.size(14.dp))
                                                }
                                                Text(
                                                    text = "${itm.quantity}",
                                                    fontWeight = FontWeight.Bold,
                                                    color = MikroTikCyan,
                                                    fontSize = 12.sp,
                                                    modifier = Modifier.padding(horizontal = 2.dp)
                                                )
                                                IconButton(
                                                    onClick = {
                                                        itemsList[idx] = itm.copy(quantity = itm.quantity + 1)
                                                    },
                                                    modifier = Modifier.size(30.dp)
                                                ) {
                                                    Icon(Icons.Default.Add, contentDescription = "زيادة", tint = MikroTikCyan, modifier = Modifier.size(14.dp))
                                                }

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
                                                    modifier = Modifier.size(32.dp).testTag("btn_edit_item_$idx")
                                                ) {
                                                    Icon(Icons.Default.Edit, contentDescription = "تعديل الصنف", tint = MikroTikCyan, modifier = Modifier.size(15.dp))
                                                }
                                                IconButton(
                                                    onClick = { itemsList.removeAt(idx) },
                                                    modifier = Modifier.size(32.dp).testTag("btn_delete_item_$idx")
                                                ) {
                                                    Icon(Icons.Default.Delete, contentDescription = "حذف الصنف", tint = SemanticExpenseRed, modifier = Modifier.size(15.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // SECTION 5: PAYMENT & SETTLEMENT SEGMENTED BUTTONS
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = MikroTikPrimary.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("4", color = MikroTikCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("طريقة السداد والتسوية", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimaryDark)
                            }

                            // 3-Option Segmented Control
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                PurchasePaymentMode.values().forEach { mode ->
                                    val isSelected = paymentMode == mode
                                    Surface(
                                        color = if (isSelected) MikroTikPrimary.copy(alpha = 0.25f) else CyberDarkCardElevated,
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.5.dp, if (isSelected) MikroTikCyan else CyberBorder),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { paymentMode = mode }
                                            .testTag("mode_${mode.name}")
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = when (mode) {
                                                    PurchasePaymentMode.ON_CREDIT -> Icons.Default.Schedule
                                                    PurchasePaymentMode.FULL_CASH -> Icons.Default.Payments
                                                    PurchasePaymentMode.PARTIAL_CASH -> Icons.Default.PointOfSale
                                                },
                                                contentDescription = null,
                                                tint = if (isSelected) MikroTikCyan else TextSecondaryDark,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = mode.title,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) MikroTikCyan else TextPrimaryDark,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }

                            Text(
                                text = paymentMode.subtitle,
                                fontSize = 11.sp,
                                color = TextSecondaryDark
                            )

                            // Cash Treasury Selector (if Full or Partial cash)
                            AnimatedVisibility(visible = paymentMode != PurchasePaymentMode.ON_CREDIT) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ExposedDropdownMenuBox(
                                        expanded = treasuryDropdownExpanded,
                                        onExpandedChange = { treasuryDropdownExpanded = it },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        val selectedTreasury = treasuries.find { it.id == selectedTreasuryId }
                                        OutlinedTextField(
                                            value = selectedTreasury?.let { "${it.name} (${it.currency})" } ?: "الصندوق الرئيسي (TR_MAIN_YER)",
                                            onValueChange = {},
                                            readOnly = true,
                                            label = { Text("الخزينة / الصندوق المسدد منه") },
                                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = treasuryDropdownExpanded) },
                                            shape = RoundedCornerShape(10.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = MikroTikCyan,
                                                unfocusedBorderColor = CyberBorder
                                            ),
                                            modifier = Modifier.menuAnchor().fillMaxWidth()
                                        )

                                        ExposedDropdownMenu(
                                            expanded = treasuryDropdownExpanded,
                                            onDismissRequest = { treasuryDropdownExpanded = false }
                                        ) {
                                            treasuries.forEach { tr ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Text(tr.name, color = TextPrimaryDark)
                                                            Text(tr.currency, color = MikroTikCyan, fontWeight = FontWeight.Bold)
                                                        }
                                                    },
                                                    onClick = {
                                                        selectedTreasuryId = tr.id
                                                        treasuryDropdownExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    // If Partial Cash, input amount paid
                                    if (paymentMode == PurchasePaymentMode.PARTIAL_CASH) {
                                        OutlinedTextField(
                                            value = partialPaidAmountText,
                                            onValueChange = { partialPaidAmountText = it },
                                            label = { Text("المبلغ المدفوع نقداً (${selectedCurrency.name})") },
                                            placeholder = { Text("أدخل الدفعة النقدية المسددة فوراً") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            shape = RoundedCornerShape(10.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = MikroTikCyan,
                                                unfocusedBorderColor = CyberBorder
                                            ),
                                            modifier = Modifier.fillMaxWidth().testTag("input_partial_paid_amount")
                                        )

                                        Surface(
                                            color = CyberDarkCardElevated,
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("المتبقي الآجل في ذمة المورد:", fontSize = 11.sp, color = TextSecondaryDark)
                                                Text(
                                                    text = Money(remainingPayableMinor, selectedCurrency).format(),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = SemanticExpenseRed
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // SECTION 6: NOTES AND REMARKS
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("البيان والملاحظات (رقم الفاتورة الورقية أو شروط التوريد)") },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MikroTikCyan,
                            unfocusedBorderColor = CyberBorder
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("input_purchase_notes")
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // 2. STICKY BOTTOM ACTION BAR (Ultra Modern POS Style)
                Surface(
                    color = CyberDarkSurface,
                    border = BorderStroke(1.dp, CyberBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 10.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Financial summary preview row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "إجمالي الفاتورة ($effectiveItemCount أصناف):",
                                        fontSize = 11.sp,
                                        color = TextSecondaryDark
                                    )
                                    if (hasPendingValidDraft && itemsList.isEmpty()) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Surface(
                                            color = MikroTikCyan.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                "يشمل الصنف الحالي",
                                                fontSize = 9.sp,
                                                color = MikroTikCyan,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = Money(effectiveTotalForeignMinor, selectedCurrency).format(),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 20.sp,
                                    color = MikroTikCyan
                                )
                            }

                            if (selectedCurrency != CurrencyCode.FUNCTIONAL) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "المعادل بقيد اليومية (YER):",
                                        fontSize = 10.sp,
                                        color = TextSecondaryDark
                                    )
                                    Text(
                                        text = Money(effectiveTotalYerMinor, CurrencyCode.FUNCTIONAL).format(),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = SemanticIncomeGreen
                                    )
                                }
                            }
                        }

                        // Submit Button
                        Button(
                            onClick = { performSubmit() },
                            enabled = canSubmit,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (canSubmit) MikroTikCyan else CyberDarkCardElevated,
                                contentColor = if (canSubmit) CyberDarkCanvas else TextMutedDark
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("btn_submit_purchase_invoice")
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when {
                                    !canSubmit -> "أدخل صنفاً لتفعيل الاعتماد"
                                    hasPendingValidDraft && itemsList.isEmpty() -> "إدراج الصنف واعتماد الفاتورة (${Money(effectiveTotalForeignMinor, selectedCurrency).format()})"
                                    else -> "اعتماد وترحيل الفاتورة (${Money(effectiveTotalForeignMinor, selectedCurrency).format()})"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
