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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.CurrencyCode
import com.example.core.model.ExchangeRate
import com.example.core.model.Money
import com.example.core.model.RateSource
import com.example.core.model.RateZone
import com.example.data.ledger.PaymentVoucherType
import com.example.data.local.AppDatabase
import com.example.data.local.entity.DocumentEntity
import com.example.data.local.entity.PartyEntity
import com.example.data.local.entity.TreasuryAccountEntity
import com.example.data.network.NetworkConfig
import com.example.domain.usecase.ExchangeRateResolver
import com.example.ui.components.ExchangeRateCard
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.ui.components.AmountSemanticType
import com.example.ui.components.AmountText
import com.example.ui.components.StatusChip
import com.example.ui.theme.AssetPurple
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberBorderGlow
import com.example.ui.theme.CyberCardHighlight
import com.example.ui.theme.CyberDarkCardElevated
import com.example.ui.theme.CyberDarkCanvas
import com.example.ui.theme.CyberDarkSurface
import com.example.ui.theme.EquityBlue
import com.example.ui.theme.MikroTikCyan
import com.example.ui.theme.SemanticExpenseRed
import com.example.ui.theme.SemanticIncomeGreen
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.TextMutedDark
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark
import com.example.ui.viewmodel.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VouchersScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val documents by viewModel.allDocuments.collectAsState()
    val parties by viewModel.allParties.collectAsState()
    val treasuries by viewModel.allTreasuries.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Receipts (قبض), 1: Payments (صرف)
    var showVoucherCreationSheet by remember { mutableStateOf(false) }
    var voucherCreationInitialTab by remember { mutableIntStateOf(0) }

    var selectedVoucherForDetail by remember { mutableStateOf<DocumentEntity?>(null) }
    var voucherToVoid by remember { mutableStateOf<DocumentEntity?>(null) }
    var voidReason by remember { mutableStateOf("") }

    val partyMap = remember(parties) { parties.associateBy { it.id } }

    val filteredVouchers = remember(documents, selectedTab) {
        val targetType = if (selectedTab == 0) "RECEIPT_VOUCHER" else "PAYMENT_VOUCHER"
        documents.filter { it.type == targetType }
    }

    val receiptCount = remember(documents) {
        documents.count { it.type == "RECEIPT_VOUCHER" && it.status != "VOIDED" }
    }
    val paymentCount = remember(documents) {
        documents.count { it.type == "PAYMENT_VOUCHER" && it.status != "VOIDED" }
    }
    val totalReceiptsMinor = remember(documents) {
        documents.filter { it.type == "RECEIPT_VOUCHER" && it.status != "VOIDED" }.sumOf { it.totalBaseMinor }
    }
    val totalPaymentsMinor = remember(documents) {
        documents.filter { it.type == "PAYMENT_VOUCHER" && it.status != "VOIDED" }.sumOf { it.totalBaseMinor }
    }
    val netCashFlowMinor = totalReceiptsMinor - totalPaymentsMinor

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    voucherCreationInitialTab = selectedTab
                    showVoucherCreationSheet = true
                },
                containerColor = if (selectedTab == 0) SemanticIncomeGreen else SemanticExpenseRed,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("fab_new_voucher")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (selectedTab == 0) "سند قبض جديد" else "سند صرف جديد",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            // 2026 Executive FinTech Voucher Command Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Receipts Tab Card (Tab 0)
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { selectedTab = 0 }
                        .border(
                            BorderStroke(
                                width = if (selectedTab == 0) 2.dp else 1.dp,
                                color = if (selectedTab == 0) SemanticIncomeGreen else CyberBorder
                            ),
                            RoundedCornerShape(18.dp)
                        ),
                    color = if (selectedTab == 0) SemanticIncomeGreen.copy(alpha = 0.14f) else CyberDarkCardElevated
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (selectedTab == 0) SemanticIncomeGreen.copy(alpha = 0.25f) else CyberDarkSurface),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.ArrowDownward,
                                    contentDescription = null,
                                    tint = SemanticIncomeGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = SemanticIncomeGreen.copy(alpha = 0.22f)
                            ) {
                                Text(
                                    "$receiptCount سند",
                                    color = SemanticIncomeGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            "سندات القبض (وارد)",
                            color = if (selectedTab == 0) TextPrimaryDark else TextSecondaryDark,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            Money(totalReceiptsMinor, CurrencyCode.YER).format(),
                            color = SemanticIncomeGreen,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Payments Tab Card (Tab 1)
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { selectedTab = 1 }
                        .border(
                            BorderStroke(
                                width = if (selectedTab == 1) 2.dp else 1.dp,
                                color = if (selectedTab == 1) SemanticExpenseRed else CyberBorder
                            ),
                            RoundedCornerShape(18.dp)
                        ),
                    color = if (selectedTab == 1) SemanticExpenseRed.copy(alpha = 0.14f) else CyberDarkCardElevated
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (selectedTab == 1) SemanticExpenseRed.copy(alpha = 0.25f) else CyberDarkSurface),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.ArrowUpward,
                                    contentDescription = null,
                                    tint = SemanticExpenseRed,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = SemanticExpenseRed.copy(alpha = 0.22f)
                            ) {
                                Text(
                                    "$paymentCount سند",
                                    color = SemanticExpenseRed,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            "سندات الصرف (صادر)",
                            color = if (selectedTab == 1) TextPrimaryDark else TextSecondaryDark,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            Money(totalPaymentsMinor, CurrencyCode.YER).format(),
                            color = SemanticExpenseRed,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Micro-Telemetry Net Cash Flow Strip
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(BorderStroke(1.dp, CyberBorder), RoundedCornerShape(12.dp)),
                color = CyberDarkSurface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (netCashFlowMinor >= 0) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                            contentDescription = null,
                            tint = if (netCashFlowMinor >= 0) SemanticIncomeGreen else SemanticExpenseRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "صافي حركة النقدية بالسندات:",
                            color = TextSecondaryDark,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Text(
                        text = Money(netCashFlowMinor, CurrencyCode.YER).format(),
                        color = if (netCashFlowMinor >= 0) SemanticIncomeGreen else SemanticExpenseRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Vouchers List
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (filteredVouchers.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(CyberDarkCardElevated),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (selectedTab == 0) Icons.Default.Receipt else Icons.Default.Payments,
                                    contentDescription = null,
                                    tint = TextMutedDark,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                if (selectedTab == 0) "لا توجد سندات قبض مسجلة حتى الآن" else "لا توجد سندات صرف مسجلة حتى الآن",
                                color = TextSecondaryDark,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "اضغط على زر الإضافة بالأسفل لإنشاء سند جديد وترحيله في الأستاذ العام",
                                color = TextMutedDark,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                items(filteredVouchers) { voucher ->
                    val partyName = partyMap[voucher.partyId]?.name ?: "طرف عام"
                    val isReceipt = voucher.type == "RECEIPT_VOUCHER"

                    Card(
                        colors = CardDefaults.cardColors(containerColor = CyberDarkCardElevated),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(BorderStroke(1.dp, CyberBorder), RoundedCornerShape(14.dp))
                            .clickable { selectedVoucherForDetail = voucher }
                            .testTag("voucher_item_${voucher.docNumber}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isReceipt) SemanticIncomeGreen.copy(alpha = 0.18f) else SemanticExpenseRed.copy(alpha = 0.18f)
                                    ) {
                                        Text(
                                            text = if (isReceipt) "قبض" else "صرف",
                                            color = if (isReceipt) SemanticIncomeGreen else SemanticExpenseRed,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "سند #${voucher.docNumber}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = TextPrimaryDark
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    StatusChip(status = voucher.status)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "الطرف: $partyName",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextSecondaryDark
                                )
                                if (voucher.notes.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        voucher.notes,
                                        fontSize = 11.sp,
                                        color = TextMutedDark,
                                        maxLines = 1
                                    )
                                }
                            }

                            AmountText(
                                money = Money(voucher.totalMinor, CurrencyCode.fromString(voucher.currency)),
                                semanticType = if (isReceipt) AmountSemanticType.INCOME else AmountSemanticType.EXPENSE,
                                fontSize = 15,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    val networkConfig by viewModel.networkRepository.config.collectAsState()

    // Modern Full-Experience Modal Bottom Sheet for Voucher Creation
    if (showVoucherCreationSheet) {
        VoucherCreationBottomSheet(
            initialTab = voucherCreationInitialTab,
            parties = parties,
            treasuries = treasuries,
            resolver = viewModel.exchangeRateResolver,
            networkConfig = networkConfig,
            onDismiss = { showVoucherCreationSheet = false },
            onSubmitReceipt = { partyId, treasuryId, amountMinor, currency, rate, rateZone, notes ->
                viewModel.postCustomerReceipt(
                    partyId = partyId,
                    treasuryId = treasuryId,
                    amountOrigMinor = amountMinor,
                    currency = currency,
                    exchangeRate = rate,
                    allocations = emptyList(),
                    notes = notes,
                    rateZone = rateZone,
                    onSuccess = { showVoucherCreationSheet = false }
                )
            },
            onSubmitPayment = { recipientId, treasuryId, amountMinor, currency, rate, rateZone, paymentType, customExp, notes ->
                viewModel.postPaymentVoucher(
                    recipientPartyId = recipientId,
                    treasuryId = treasuryId,
                    amountOrigMinor = amountMinor,
                    currency = currency,
                    exchangeRate = rate,
                    paymentType = paymentType,
                    customExpenseCode = customExp,
                    invoiceAllocations = emptyList(),
                    notes = notes,
                    rateZone = rateZone,
                    onSuccess = { showVoucherCreationSheet = false }
                )
            }
        )
    }

    // Voucher Detail Dialog
    selectedVoucherForDetail?.let { voucher ->
        val partyName = partyMap[voucher.partyId]?.name ?: "طرف عام"
        val isReceipt = voucher.type == "RECEIPT_VOUCHER"

        AlertDialog(
            onDismissRequest = { selectedVoucherForDetail = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (isReceipt) SemanticIncomeGreen.copy(alpha = 0.2f) else SemanticExpenseRed.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (isReceipt) Icons.Default.Receipt else Icons.Default.Payments,
                            contentDescription = null,
                            tint = if (isReceipt) SemanticIncomeGreen else SemanticExpenseRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "${if (isReceipt) "سند قبض" else "سند صرف"} #${voucher.docNumber}",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryDark
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp)),
                        color = CyberDarkCardElevated
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("المبلغ:", color = TextSecondaryDark, fontSize = 12.sp)
                                Text(
                                    Money(voucher.totalMinor, CurrencyCode.fromString(voucher.currency)).format(),
                                    color = if (isReceipt) SemanticIncomeGreen else SemanticExpenseRed,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("الطرف:", color = TextSecondaryDark, fontSize = 12.sp)
                                Text(partyName, color = TextPrimaryDark, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("الحالة في الأستاذ:", color = TextSecondaryDark, fontSize = 12.sp)
                                StatusChip(status = voucher.status)
                            }
                        }
                    }

                    if (voucher.notes.isNotBlank()) {
                        Text("البيان: ${voucher.notes}", fontSize = 12.sp, color = TextSecondaryDark)
                    }

                    if (voucher.status != "VOIDED") {
                        HorizontalDivider(color = CyberBorder)
                        OutlinedButton(
                            onClick = {
                                voucherToVoid = voucher
                                selectedVoucherForDetail = null
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = SemanticExpenseRed),
                            border = BorderStroke(1.dp, SemanticExpenseRed.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("إلغاء السند بقيد عكسي (IFRS)")
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { selectedVoucherForDetail = null },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberDarkCardElevated),
                    shape = RoundedCornerShape(10.dp)
                ) { Text("إغلاق", color = TextPrimaryDark) }
            },
            containerColor = CyberDarkSurface
        )
    }

    // Void Dialog
    voucherToVoid?.let { doc ->
        AlertDialog(
            onDismissRequest = { voucherToVoid = null },
            title = { Text("إلغاء السند #${doc.docNumber}", color = SemanticExpenseRed, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "وفق معايير IFRS المحاسبية: لا يمكن حذف السند. سيتم إدراج قيد عكسي تعويضي في الأستاذ العام وتعديل أرصدة الخزينة والعميل آلياً.",
                        fontSize = 12.sp,
                        color = TextSecondaryDark
                    )
                    OutlinedTextField(
                        value = voidReason,
                        onValueChange = { voidReason = it },
                        label = { Text("سبب الإلغاء (إلزامي للتدقيق)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SemanticExpenseRed,
                            unfocusedBorderColor = CyberBorder,
                            focusedTextColor = TextPrimaryDark,
                            unfocusedTextColor = TextPrimaryDark,
                            focusedContainerColor = CyberDarkCardElevated,
                            unfocusedContainerColor = CyberDarkCardElevated
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (voidReason.isNotBlank()) {
                            viewModel.voidDocument(doc.id, voidReason) {
                                voucherToVoid = null
                                voidReason = ""
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SemanticExpenseRed),
                    shape = RoundedCornerShape(10.dp),
                    enabled = voidReason.isNotBlank()
                ) { Text("تأكيد الإلغاء والعكس", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { voucherToVoid = null }) {
                    Text("تراجع", color = TextSecondaryDark)
                }
            },
            containerColor = CyberDarkSurface
        )
    }
}

/**
 * 2026 Executive Voucher Creation Bottom Sheet
 * Solves keyboard displacement, disappearing fields, awkward button stretching, and primitive styling.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoucherCreationBottomSheet(
    initialTab: Int,
    parties: List<PartyEntity>,
    treasuries: List<TreasuryAccountEntity>,
    resolver: ExchangeRateResolver? = null,
    networkConfig: NetworkConfig? = null,
    onDismiss: () -> Unit,
    onSubmitReceipt: (partyId: String, treasuryId: String, amountMinor: Long, currency: CurrencyCode, rate: ExchangeRate, rateZone: RateZone, notes: String) -> Unit,
    onSubmitPayment: (recipientId: String, treasuryId: String, amountMinor: Long, currency: CurrencyCode, rate: ExchangeRate, rateZone: RateZone, paymentType: PaymentVoucherType, customExp: String?, notes: String) -> Unit
) {
    var voucherMode by remember { mutableIntStateOf(initialTab) } // 0: Receipt, 1: Payment
    val isReceipt = voucherMode == 0
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val context = androidx.compose.ui.platform.LocalContext.current
    val effectiveConfig = networkConfig ?: remember {
        com.example.data.network.NetworkRepository(context).config.value
    }
    val effectiveResolver = resolver ?: remember { ExchangeRateResolver(AppDatabase.getInstance(context)) }
    val today = remember { java.time.LocalDate.now().toEpochDay() }
    val inheritedZone = effectiveConfig.rateZone

    // Form States
    val customers = remember(parties) {
        val list = parties.filter { it.isCustomer || it.isPartner }
        if (list.isEmpty()) listOf(PartyEntity(AppDatabase.WALK_IN_CASH_PARTY_ID, "عميل عام / مشتري نقدي", "", isCustomer = true))
        else list
    }

    var paymentType by remember { mutableStateOf(PaymentVoucherType.DIRECT_ISP_SERVICE) }
    var selectedPartyId by remember { mutableStateOf(customers.first().id) }
    var selectedRecipientId by remember {
        mutableStateOf(parties.firstOrNull { it.isVendor }?.id ?: AppDatabase.WALK_IN_CASH_PARTY_ID)
    }
    var selectedTreasuryId by remember { mutableStateOf(treasuries.firstOrNull()?.id ?: "TR_MAIN_YER") }
    var amountText by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    var partyDropdownExpanded by remember { mutableStateOf(false) }
    var treasuryDropdownExpanded by remember { mutableStateOf(false) }

    val activeTreasury = treasuries.firstOrNull { it.id == selectedTreasuryId }
    val currency = CurrencyCode.fromString(activeTreasury?.currency ?: "YER")

    var rateInputText by rememberSaveable(currency, inheritedZone) { mutableStateOf("1.0") }
    var isRateManuallyEdited by rememberSaveable(currency, inheritedZone) { mutableStateOf(false) }

    LaunchedEffect(currency, inheritedZone, effectiveConfig) {
        when (currency) {
            CurrencyCode.YER -> {
                rateInputText = "1.0"
                isRateManuallyEdited = false
            }
            CurrencyCode.USD -> {
                val defaultRateMicros = effectiveConfig.defaultUsdRateMicros.takeIf { it > 0L }
                    ?: effectiveResolver.resolveOrNull(CurrencyCode.USD, today, inheritedZone)?.rateMicros
                    ?: if (inheritedZone == RateZone.ADEN) 1_600_000_000L else 535_000_000L
                rateInputText = ExchangeRate.formatRateMicros(defaultRateMicros)
                isRateManuallyEdited = false
            }
            CurrencyCode.SAR -> {
                val defaultRateMicros = effectiveConfig.defaultSarRateMicros.takeIf { it > 0L }
                    ?: effectiveResolver.resolveOrNull(CurrencyCode.SAR, today, inheritedZone)?.rateMicros
                    ?: if (inheritedZone == RateZone.ADEN) 420_000_000L else 140_500_000L
                rateInputText = ExchangeRate.formatRateMicros(defaultRateMicros)
                isRateManuallyEdited = false
            }
        }
    }

    val parsedRateMicros: Long? = remember(currency, rateInputText) {
        if (currency == CurrencyCode.FUNCTIONAL) {
            ExchangeRate.SCALE_MICROS
        } else {
            ExchangeRate.parseRateFromUserInput(rateInputText) ?: when (currency) {
                CurrencyCode.USD -> effectiveConfig.defaultUsdRateMicros
                CurrencyCode.SAR -> effectiveConfig.defaultSarRateMicros
                else -> null
            }
        }
    }

    val effectiveRate: ExchangeRate? = remember(currency, parsedRateMicros) {
        if (currency == CurrencyCode.FUNCTIONAL) {
            ExchangeRate.parity(CurrencyCode.FUNCTIONAL)
        } else if (parsedRateMicros != null && parsedRateMicros > 0L) {
            ExchangeRate(
                fromCurrency = currency,
                toCurrency = CurrencyCode.FUNCTIONAL,
                rateMicros = parsedRateMicros
            )
        } else {
            null
        }
    }

    val relevantRecipients = remember(parties, paymentType) {
        when (paymentType) {
            PaymentVoucherType.VENDOR_SETTLEMENT -> parties.filter { it.isVendor }
            PaymentVoucherType.PARTNER_DRAWINGS -> parties.filter { it.isPartner }
            else -> parties
        }.ifEmpty {
            listOf(PartyEntity(AppDatabase.WALK_IN_CASH_PARTY_ID, "مورد عام / جهة الخدمة", "", isVendor = true))
        }
    }

    val selectedCustomer = customers.find { it.id == selectedPartyId } ?: customers.first()
    val selectedRecipient = relevantRecipients.find { it.id == selectedRecipientId } ?: relevantRecipients.first()

    val themeColor = if (isReceipt) SemanticIncomeGreen else SemanticExpenseRed
    val amountMinor = (amountText.toLongOrNull() ?: 0L) * 100L

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CyberDarkSurface,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
        ) {
            // Elegant Drag Bar & Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CyberDarkCardElevated)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(themeColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isReceipt) Icons.Default.Receipt else Icons.Default.Payments,
                            contentDescription = null,
                            tint = themeColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (isReceipt) "إنشاء سند قبض نقدي" else "إنشاء سند صرف نقدي",
                            color = TextPrimaryDark,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = if (isReceipt) "تسجيل مقبوضات نقدية وإيداعها في الصندوق" else "صرف نفقات أو دفعات للموردين والشركاء",
                            color = TextSecondaryDark,
                            fontSize = 11.sp
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(CyberDarkSurface)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = TextSecondaryDark, modifier = Modifier.size(16.dp))
                }
            }

            HorizontalDivider(color = CyberBorder)

            // Segmented Switcher for Voucher Mode (Switch effortlessly between Receipt & Payment)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 12.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(BorderStroke(1.dp, CyberBorder), RoundedCornerShape(14.dp)),
                color = CyberDarkCanvas
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Receipt Mode Tab
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { voucherMode = 0 }
                            .then(
                                if (isReceipt) Modifier.border(
                                    BorderStroke(1.dp, SemanticIncomeGreen.copy(alpha = 0.6f)),
                                    RoundedCornerShape(10.dp)
                                ) else Modifier
                            ),
                        color = if (isReceipt) SemanticIncomeGreen.copy(alpha = 0.18f) else Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 9.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.ArrowDownward,
                                contentDescription = null,
                                tint = if (isReceipt) SemanticIncomeGreen else TextSecondaryDark,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "سند قبض (وارد)",
                                color = if (isReceipt) Color.White else TextSecondaryDark,
                                fontWeight = if (isReceipt) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        }
                    }

                    // Payment Mode Tab
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { voucherMode = 1 }
                            .then(
                                if (!isReceipt) Modifier.border(
                                    BorderStroke(1.dp, SemanticExpenseRed.copy(alpha = 0.6f)),
                                    RoundedCornerShape(10.dp)
                                ) else Modifier
                            ),
                        color = if (!isReceipt) SemanticExpenseRed.copy(alpha = 0.18f) else Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 9.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.ArrowUpward,
                                contentDescription = null,
                                tint = if (!isReceipt) SemanticExpenseRed else TextSecondaryDark,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "سند صرف (صادر)",
                                color = if (!isReceipt) Color.White else TextSecondaryDark,
                                fontWeight = if (!isReceipt) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // Scrollable Form Fields - Fluid, no clipping or weird gaps!
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // If Payment: 2x2 Memory-Activating FinTech Category Grid
                if (!isReceipt) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "غرض وتصنيف الصرف (IFRS):",
                            color = TextPrimaryDark,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 1. Internet Subscription 5101 (Cyan)
                            FintechCategoryTile(
                                title = "اشتراك إنترنت",
                                subtitle = "Starlink / ألياف",
                                code = "5101",
                                icon = Icons.Default.Router,
                                accentColor = MikroTikCyan,
                                isSelected = paymentType == PaymentVoucherType.DIRECT_ISP_SERVICE,
                                onClick = { paymentType = PaymentVoucherType.DIRECT_ISP_SERVICE },
                                modifier = Modifier.weight(1f)
                            )

                            // 2. Operating Expenses 5201 (Amber)
                            FintechCategoryTile(
                                title = "مصاريف تشغيل",
                                subtitle = "ديزل / كهرباء",
                                code = "5201",
                                icon = Icons.Default.Bolt,
                                accentColor = StatusWarning,
                                isSelected = paymentType == PaymentVoucherType.OPERATING_EXPENSE,
                                onClick = { paymentType = PaymentVoucherType.OPERATING_EXPENSE },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 3. Vendor Settlement 2101 (Blue)
                            FintechCategoryTile(
                                title = "سداد مورد",
                                subtitle = "كروت / أجهزة",
                                code = "2101",
                                icon = Icons.Default.LocalShipping,
                                accentColor = EquityBlue,
                                isSelected = paymentType == PaymentVoucherType.VENDOR_SETTLEMENT,
                                onClick = { paymentType = PaymentVoucherType.VENDOR_SETTLEMENT },
                                modifier = Modifier.weight(1f)
                            )

                            // 4. Partner Drawings 3201 (Purple)
                            FintechCategoryTile(
                                title = "مسحوبات شريك",
                                subtitle = "سحب جاري",
                                code = "3201",
                                icon = Icons.Default.Handshake,
                                accentColor = AssetPurple,
                                isSelected = paymentType == PaymentVoucherType.PARTNER_DRAWINGS,
                                onClick = { paymentType = PaymentVoucherType.PARTNER_DRAWINGS },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Party Selector (Customer or Recipient)
                ExposedDropdownMenuBox(
                    expanded = partyDropdownExpanded,
                    onExpandedChange = { partyDropdownExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = if (isReceipt) selectedCustomer.name else selectedRecipient.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(if (isReceipt) "العميل / الطرف الدافع" else "المستلم / الجهة المدفوع لها") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Store,
                                contentDescription = null,
                                tint = themeColor
                            )
                        },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = partyDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = themeColor,
                            unfocusedBorderColor = CyberBorder,
                            focusedTextColor = TextPrimaryDark,
                            unfocusedTextColor = TextPrimaryDark,
                            focusedContainerColor = CyberDarkCardElevated,
                            unfocusedContainerColor = CyberDarkCardElevated
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = partyDropdownExpanded,
                        onDismissRequest = { partyDropdownExpanded = false }
                    ) {
                        val activeList = if (isReceipt) customers else relevantRecipients
                        activeList.forEach { p ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(p.name, color = TextPrimaryDark, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            when {
                                                p.isPartner -> "شريك"
                                                p.isVendor -> "مورد"
                                                else -> "بقالة / عميل"
                                            },
                                            fontSize = 11.sp,
                                            color = themeColor
                                        )
                                    }
                                },
                                onClick = {
                                    if (isReceipt) selectedPartyId = p.id
                                    else selectedRecipientId = p.id
                                    partyDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // Treasury / Cashbox Selector
                ExposedDropdownMenuBox(
                    expanded = treasuryDropdownExpanded,
                    onExpandedChange = { treasuryDropdownExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = activeTreasury?.let { "${it.name} (${it.currency})" } ?: selectedTreasuryId,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(if (isReceipt) "الصندوق / الخزينة المستلمة (1101)" else "صندوق الدفع المستعمل (1101)") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.AccountBalance,
                                contentDescription = null,
                                tint = themeColor
                            )
                        },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = treasuryDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = themeColor,
                            unfocusedBorderColor = CyberBorder,
                            focusedTextColor = TextPrimaryDark,
                            unfocusedTextColor = TextPrimaryDark,
                            focusedContainerColor = CyberDarkCardElevated,
                            unfocusedContainerColor = CyberDarkCardElevated
                        )
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
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(tr.name, color = TextPrimaryDark, fontWeight = FontWeight.SemiBold)
                                        Text(tr.currency, color = themeColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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

                if (currency != CurrencyCode.FUNCTIONAL) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(BorderStroke(1.dp, themeColor.copy(alpha = 0.4f)), RoundedCornerShape(12.dp)),
                        color = CyberDarkCardElevated
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Bolt,
                                        contentDescription = null,
                                        tint = themeColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "سعر الصرف المعتمد من هوية الشبكة:",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = TextSecondaryDark
                                    )
                                }
                                Surface(
                                    color = if (inheritedZone == RateZone.SANAA) MikroTikCyan.copy(alpha = 0.18f) else StatusWarning.copy(alpha = 0.18f),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, if (inheritedZone == RateZone.SANAA) MikroTikCyan.copy(alpha = 0.4f) else StatusWarning.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = if (inheritedZone == RateZone.SANAA) "نطاق صنعاء (مرتبط تلقائياً)" else "نطاق عدن (مرتبط تلقائياً)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (inheritedZone == RateZone.SANAA) MikroTikCyan else StatusWarning,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = rateInputText,
                                onValueChange = {
                                    rateInputText = it
                                    isRateManuallyEdited = true
                                },
                                label = { Text("سعر الصرف المعتمد للسند (1 ${currency.name} مقابل YER)") },
                                placeholder = { Text("أدخل سعر الصرف...") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                trailingIcon = {
                                    if (isRateManuallyEdited) {
                                        TextButton(
                                            onClick = {
                                                rateInputText = when (currency) {
                                                    CurrencyCode.USD -> ExchangeRate.formatRateMicros(
                                                        effectiveConfig.defaultUsdRateMicros.takeIf { it > 0L }
                                                            ?: if (inheritedZone == RateZone.ADEN) 1_600_000_000L else 535_000_000L
                                                    )
                                                    CurrencyCode.SAR -> ExchangeRate.formatRateMicros(
                                                        effectiveConfig.defaultSarRateMicros.takeIf { it > 0L }
                                                            ?: if (inheritedZone == RateZone.ADEN) 420_000_000L else 140_500_000L
                                                    )
                                                    else -> "1.0"
                                                }
                                                isRateManuallyEdited = false
                                            }
                                        ) {
                                            Text("استعادة الافتراضي", fontSize = 11.sp, color = themeColor)
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_voucher_exchange_rate"),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = themeColor,
                                    unfocusedBorderColor = CyberBorder,
                                    focusedTextColor = TextPrimaryDark,
                                    unfocusedTextColor = TextPrimaryDark,
                                    focusedContainerColor = CyberDarkSurface,
                                    unfocusedContainerColor = CyberDarkSurface
                                )
                            )

                            if (effectiveRate != null && amountMinor > 0L) {
                                val equivalentYerMinor = effectiveRate.convert(amountMinor)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "المعادل بالريال اليمني (الأثر المحاسبي):",
                                        fontSize = 11.sp,
                                        color = TextSecondaryDark
                                    )
                                    Text(
                                        Money(equivalentYerMinor, CurrencyCode.YER).format(),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = themeColor,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }

                // Amount Input + Quick Chips (Smooth LazyRow, NO STRETCHING, NO GAPS)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it.filter { ch -> ch.isDigit() } },
                        label = { Text(if (isReceipt) "المبلغ المستلم (${currency.symbol})" else "المبلغ المدفوع (${currency.symbol})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = themeColor,
                            unfocusedBorderColor = CyberBorder,
                            focusedTextColor = TextPrimaryDark,
                            unfocusedTextColor = TextPrimaryDark,
                            focusedContainerColor = CyberDarkCardElevated,
                            unfocusedContainerColor = CyberDarkCardElevated
                        ),
                        singleLine = true,
                        trailingIcon = {
                            if (amountMinor > 0) {
                                Text(
                                    Money(amountMinor, currency).format(),
                                    color = themeColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(end = 12.dp)
                                )
                            }
                        }
                    )

                    // Smooth Horizontal LazyRow of Quick Amount Chips - eliminates stretched buttons!
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val quickAmounts = listOf("1000", "5000", "10000", "20000", "50000", "100000", "250000", "500000")
                        items(quickAmounts) { amt ->
                            val amtInt = amt.toInt()
                            val label = if (amtInt >= 1000) "${amtInt / 1000}k" else amt
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { amountText = amt }
                                    .border(BorderStroke(1.dp, themeColor.copy(alpha = 0.35f)), RoundedCornerShape(10.dp)),
                                color = themeColor.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = "+$label",
                                    color = TextPrimaryDark,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }
                }

                // Notes / Description Field
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = {
                        Text(
                            if (isReceipt) "البيان والملاحظات (مثال: سداد كروت شبكة، تحويل كريمي)"
                            else "البيان وتفاصيل الصرف (مثال: تجديد باقة 50 ميجا، ديزل مولد)"
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = themeColor,
                        unfocusedBorderColor = CyberBorder,
                        focusedTextColor = TextPrimaryDark,
                        unfocusedTextColor = TextPrimaryDark,
                        focusedContainerColor = CyberDarkCardElevated,
                        unfocusedContainerColor = CyberDarkCardElevated
                    ),
                    maxLines = 2
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Prominent Action Button
                Button(
                    onClick = {
                        if (amountMinor > 0L) {
                            val rate = if (currency == CurrencyCode.FUNCTIONAL) {
                                ExchangeRate.parity(CurrencyCode.FUNCTIONAL)
                            } else {
                                effectiveRate ?: error("Exchange rate not resolved for $currency")
                            }
                            if (isReceipt) {
                                onSubmitReceipt(
                                    selectedPartyId,
                                    selectedTreasuryId,
                                    amountMinor,
                                    currency,
                                    rate,
                                    inheritedZone,
                                    notes
                                )
                            } else {
                                onSubmitPayment(
                                    selectedRecipientId,
                                    selectedTreasuryId,
                                    amountMinor,
                                    currency,
                                    rate,
                                    inheritedZone,
                                    paymentType,
                                    null,
                                    notes
                                )
                            }
                        }
                    },
                    enabled = amountMinor > 0L && (currency == CurrencyCode.FUNCTIONAL || (effectiveRate != null && effectiveRate.rateMicros > 0L)),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = themeColor,
                        disabledContainerColor = CyberDarkCardElevated
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (isReceipt) "ترحيل سند القبض في الأستاذ العام" else "ترحيل سند الصرف في الأستاذ العام",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))
            }
        }
    }
}

/**
 * Modern High-Tech Category Tile for Payments (2x2 Grid)
 * Stimulates visual memory with distinct colors, GL codes, and icons.
 */
@Composable
fun FintechCategoryTile(
    title: String,
    subtitle: String,
    code: String,
    icon: ImageVector,
    accentColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .border(
                BorderStroke(
                    width = if (isSelected) 1.5.dp else 1.dp,
                    color = if (isSelected) accentColor else CyberBorder
                ),
                RoundedCornerShape(12.dp)
            ),
        color = if (isSelected) accentColor.copy(alpha = 0.16f) else CyberDarkCardElevated
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) accentColor.copy(alpha = 0.25f) else CyberDarkSurface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) accentColor else TextSecondaryDark,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        color = if (isSelected) accentColor else TextPrimaryDark,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                    Text(
                        text = code,
                        color = if (isSelected) accentColor else TextMutedDark,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Text(
                    text = subtitle,
                    color = TextSecondaryDark,
                    fontSize = 9.sp,
                    maxLines = 1
                )
            }
        }
    }
}
