package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.util.WhatsAppDispatcher
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.CurrencyCode
import com.example.core.model.ExchangeRate
import com.example.core.model.Money
import com.example.data.ledger.SalesItemSpec
import com.example.data.local.AppDatabase
import com.example.data.local.entity.CardPackageEntity
import com.example.data.local.entity.DocumentEntity
import com.example.data.local.entity.DocumentItemEntity
import com.example.data.local.entity.PartyEntity
import com.example.data.local.entity.TreasuryAccountEntity
import com.example.ui.components.AmountSemanticType
import com.example.ui.components.AmountText
import com.example.ui.components.StatusChip
import com.example.ui.theme.*
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark
import com.example.ui.viewmodel.AppViewModel
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val documents by viewModel.allDocuments.collectAsState()
    val allocations by viewModel.allAllocations.collectAsState()
    val parties by viewModel.allParties.collectAsState()
    val packages by viewModel.allPackages.collectAsState()
    val treasuries by viewModel.allTreasuries.collectAsState()
    val stockMovements by viewModel.allStockMovements.collectAsState()

    var activeTab by rememberSaveable { mutableIntStateOf(1) } // 0: Points of Sale, 1: Sales Invoices, 2: Inventory Count
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var statusFilter by rememberSaveable { mutableStateOf("ALL") } // ALL, UNPAID, PAID, PARTIAL, VOIDED

    var showNewInvoiceSheet by remember { mutableStateOf(false) }
    var selectedInvoiceForDetail by remember { mutableStateOf<DocumentEntity?>(null) }
    var invoiceToVoid by remember { mutableStateOf<DocumentEntity?>(null) }
    var voidReason by remember { mutableStateOf("فاتورة مكررة / خطأ في الإدخال") }
    var invoiceItemsForDetail by remember { mutableStateOf<List<DocumentItemEntity>>(emptyList()) }

    var partyBalances by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }

    // Fetch live party receivables on composition & data updates
    LaunchedEffect(documents, allocations) {
        viewModel.getCustomerReceivableBalances { balances ->
            partyBalances = balances
        }
    }

    val partyMap = remember(parties) { parties.associateBy { it.id } }
    val allocationsByInvoice = remember(allocations) { allocations.groupBy { it.invoiceDocId } }

    // Calculate stock balances per package
    val stockBalanceByPackageId = remember(stockMovements) {
        stockMovements.groupBy { it.packageId }.mapValues { (_, movements) ->
            movements.sumOf { m ->
                when (m.type) {
                    "RECEIVE", "RETURN" -> m.quantity
                    "SELL" -> -m.quantity
                    "ADJUST" -> m.quantity
                    else -> 0
                }
            }.coerceAtLeast(0)
        }
    }

    val totalCardsInStock = remember(stockBalanceByPackageId) {
        stockBalanceByPackageId.values.sum()
    }

    // Card counts per sales invoice docId
    val cardCountByDocId = remember(stockMovements) {
        stockMovements.filter { it.type == "SELL" && it.docId != null }
            .groupBy { it.docId!! }
            .mapValues { (_, mvts) -> mvts.sumOf { it.quantity } }
    }

    // All sales invoices
    val allSalesInvoices = remember(documents) {
        documents.filter { it.type == "SALES_INVOICE" }
    }

    // Summary Metrics
    val activeInvoices = remember(allSalesInvoices) {
        allSalesInvoices.filter { it.status != "VOIDED" }
    }
    val totalSalesAmountMinor = remember(activeInvoices) {
        activeInvoices.sumOf { it.totalMinor }
    }
    val totalCollectedMinor = remember(activeInvoices, allocationsByInvoice) {
        activeInvoices.sumOf { inv ->
            val paid = allocationsByInvoice[inv.id]?.sumOf { it.allocatedOrigMinor } ?: 0L
            minOf(paid, inv.totalMinor)
        }
    }
    val totalReceivablesMinor = remember(totalSalesAmountMinor, totalCollectedMinor) {
        (totalSalesAmountMinor - totalCollectedMinor).coerceAtLeast(0L)
    }
    val totalSoldCards = remember(activeInvoices, cardCountByDocId) {
        activeInvoices.sumOf { cardCountByDocId[it.id] ?: 0 }
    }

    // Filtered invoices
    val filteredInvoices = remember(allSalesInvoices, searchQuery, statusFilter, allocationsByInvoice, partyMap) {
        allSalesInvoices.filter { doc ->
            val partyName = partyMap[doc.partyId]?.name ?: ""
            val matchesQuery = searchQuery.isBlank() ||
                    doc.docNumber.toString().contains(searchQuery) ||
                    partyName.contains(searchQuery, ignoreCase = true) ||
                    doc.notes.contains(searchQuery, ignoreCase = true)

            val paidMinor = allocationsByInvoice[doc.id]?.sumOf { it.allocatedOrigMinor } ?: 0L
            val isPaid = paidMinor >= doc.totalMinor && doc.status != "VOIDED"
            val isPartial = paidMinor > 0L && paidMinor < doc.totalMinor && doc.status != "VOIDED"
            val isCredit = paidMinor == 0L && doc.status != "VOIDED"

            val matchesFilter = when (statusFilter) {
                "UNPAID" -> isCredit
                "PAID" -> isPaid
                "PARTIAL" -> isPartial
                "VOIDED" -> doc.status == "VOIDED"
                else -> true
            }

            matchesQuery && matchesFilter
        }
    }

    // Filter counts
    val unpaidCount = remember(allSalesInvoices, allocationsByInvoice) {
        allSalesInvoices.count { it.status != "VOIDED" && (allocationsByInvoice[it.id]?.sumOf { a -> a.allocatedOrigMinor } ?: 0L) == 0L }
    }
    val paidCount = remember(allSalesInvoices, allocationsByInvoice) {
        allSalesInvoices.count { it.status != "VOIDED" && (allocationsByInvoice[it.id]?.sumOf { a -> a.allocatedOrigMinor } ?: 0L) >= it.totalMinor }
    }
    val partialCount = remember(allSalesInvoices, allocationsByInvoice) {
        allSalesInvoices.count {
            val paid = allocationsByInvoice[it.id]?.sumOf { a -> a.allocatedOrigMinor } ?: 0L
            it.status != "VOIDED" && paid > 0L && paid < it.totalMinor
        }
    }

    Scaffold(
        containerColor = CyberDarkCanvas,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            // 1. Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "المبيعات وتوزيع الكروت",
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        color = TextPrimaryDark
                    )
                    Text(
                        text = "فواتير ومتابعة نقاط البيع وتسليم الدفعات",
                        fontSize = 11.sp,
                        color = MikroTikCyanGlow
                    )
                }

                // Fast Action Shortcut "تسليم >"
                Surface(
                    color = CyberDarkCardElevated,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MikroTikCyan.copy(alpha = 0.5f)),
                    modifier = Modifier.clickable { showNewInvoiceSheet = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = null,
                            tint = MikroTikCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "تسليم",
                            color = MikroTikCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 2. Segmented Navigation Switcher [نقاط البيع] [فواتير المبيعات] [المخزن بالعدد]
            val customerPartiesCount = parties.count { it.isCustomer }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CyberDarkSurface)
                    .border(1.dp, CyberBorder, RoundedCornerShape(12.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Tab 0: Points of Sale
                val tab0Selected = activeTab == 0
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (tab0Selected) MikroTikPrimary else Color.Transparent)
                        .clickable { activeTab = 0 }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Storefront,
                            contentDescription = null,
                            tint = if (tab0Selected) TextPrimaryDark else TextSecondaryDark,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "نقاط البيع ($customerPartiesCount)",
                            fontSize = 11.sp,
                            fontWeight = if (tab0Selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (tab0Selected) TextPrimaryDark else TextSecondaryDark
                        )
                    }
                }

                // Tab 1: Sales Invoices
                val tab1Selected = activeTab == 1
                Box(
                    modifier = Modifier
                        .weight(1.2f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (tab1Selected) MikroTikPrimary else Color.Transparent)
                        .clickable { activeTab = 1 }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.AutoMirrored.Filled.ReceiptLong,
                            contentDescription = null,
                            tint = if (tab1Selected) TextPrimaryDark else TextSecondaryDark,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "فواتير المبيعات (${allSalesInvoices.size})",
                            fontSize = 11.sp,
                            fontWeight = if (tab1Selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (tab1Selected) TextPrimaryDark else TextSecondaryDark
                        )
                    }
                }

                // Tab 2: Inventory Count
                val tab2Selected = activeTab == 2
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (tab2Selected) MikroTikPrimary else Color.Transparent)
                        .clickable { activeTab = 2 }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Inventory2,
                            contentDescription = null,
                            tint = if (tab2Selected) TextPrimaryDark else TextSecondaryDark,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "المخزن بالعدد",
                            fontSize = 11.sp,
                            fontWeight = if (tab2Selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (tab2Selected) TextPrimaryDark else TextSecondaryDark
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 3. Top Metrics Summary Bar (Matching Screen 3)
            Card(
                colors = CardDefaults.cardColors(containerColor = CyberDarkSurface),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, CyberBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Total Sales
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CreditCard, contentDescription = null, tint = MikroTikCyan, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("المبيعات:", fontSize = 10.sp, color = TextSecondaryDark)
                        }
                        Text(
                            text = "${totalSalesAmountMinor / 100L} ر.ي",
                            color = MikroTikCyan,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Collected
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Payments, contentDescription = null, tint = StatusOnline, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("المحصل:", fontSize = 10.sp, color = TextSecondaryDark)
                        }
                        Text(
                            text = "${totalCollectedMinor / 100L} ر.ي",
                            color = StatusOnline,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Receivables (آجل)
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = PaymentRed, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("الآجل:", fontSize = 10.sp, color = TextSecondaryDark)
                        }
                        Text(
                            text = "${totalReceivablesMinor / 100L} ر.ي",
                            color = PaymentRed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Total Cards Count
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(0.9f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Inventory2, contentDescription = null, tint = InvestmentGold, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("الكروت:", fontSize = 10.sp, color = TextSecondaryDark)
                        }
                        Text(
                            text = "$totalSoldCards كرت",
                            color = InvestmentGold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 4. Main Body Content Based on Active Tab
            when (activeTab) {
                0 -> {
                    // Points of Sale (Customer Parties)
                    PointsOfSaleList(
                        parties = parties.filter { it.isCustomer },
                        balances = partyBalances,
                        onSelectParty = { p ->
                            showNewInvoiceSheet = true
                        }
                    )
                }
                1 -> {
                    // Sales Invoices Tab
                    SalesInvoicesTab(
                        salesInvoices = filteredInvoices,
                        allSalesInvoicesCount = allSalesInvoices.size,
                        unpaidCount = unpaidCount,
                        paidCount = paidCount,
                        partialCount = partialCount,
                        partyMap = partyMap,
                        allocationsByInvoice = allocationsByInvoice,
                        cardCountByDocId = cardCountByDocId,
                        searchQuery = searchQuery,
                        statusFilter = statusFilter,
                        onSearchChange = { searchQuery = it },
                        onFilterChange = { statusFilter = it },
                        onNewInvoiceClick = { showNewInvoiceSheet = true },
                        onInvoiceClick = { inv ->
                            selectedInvoiceForDetail = inv
                            viewModel.getDocumentItems(inv.id) { items ->
                                invoiceItemsForDetail = items
                            }
                        }
                    )
                }
                2 -> {
                    // Inventory Count Summary Tab
                    InventorySummaryTab(
                        packages = packages,
                        stockBalances = stockBalanceByPackageId,
                        totalCards = totalCardsInStock
                    )
                }
            }
        }
    }

    // 5. New Modern Sales Invoice BottomSheet (Matching Screens 1 & 2)
    if (showNewInvoiceSheet) {
        NewSalesInvoiceBottomSheet(
            parties = parties.filter { it.isCustomer },
            packages = packages,
            treasuries = treasuries,
            stockBalances = stockBalanceByPackageId,
            partyBalances = partyBalances,
            onDismiss = { showNewInvoiceSheet = false },
            onSubmit = { partyId, items, cashPaidMinor, treasuryId, notes ->
                viewModel.postSalesInvoiceWithSettlement(
                    partyId = partyId,
                    cardItems = items,
                    cashPaidMinor = cashPaidMinor,
                    treasuryId = treasuryId,
                    notes = notes,
                    onSuccess = { showNewInvoiceSheet = false }
                )
            }
        )
    }

    // 6. Invoice Detail Dialog
    selectedInvoiceForDetail?.let { inv ->
        val party = partyMap[inv.partyId]
        val allocs = allocationsByInvoice[inv.id] ?: emptyList()
        val paidMinor = allocs.sumOf { it.allocatedOrigMinor }
        val remainingMinor = (inv.totalMinor - paidMinor).coerceAtLeast(0L)
        val isVoided = inv.status == "VOIDED"
        val curr = CurrencyCode.fromString(inv.currency)

        AlertDialog(
            onDismissRequest = { selectedInvoiceForDetail = null },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("تفاصيل فاتورة مبيعات #${inv.docNumber}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    StatusChip(status = inv.status)
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CyberDarkCardElevated),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, CyberBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("العميل / نقطة البيع:", fontSize = 12.sp, color = TextSecondaryDark)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(party?.name ?: "عميل نقدي", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    party?.phone?.takeIf { it.isNotBlank() }?.let { phoneNum ->
                                        val context = LocalContext.current
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFF25D366).copy(alpha = 0.15f),
                                            modifier = Modifier.clickable {
                                                val summary = buildString {
                                                    append("فاتورة مبيعات كروت #${inv.docNumber}\n")
                                                    append("العميل: ${party.name}\n")
                                                    append("الإجمالي: ${Money(inv.totalMinor, curr).format()}\n")
                                                    append("المدفوع: ${Money(paidMinor, curr).format()}\n")
                                                    if (remainingMinor > 0L) {
                                                        append("المتبقي: ${Money(remainingMinor, curr).format()}\n")
                                                    }
                                                    append("شكراً لتعاملكم معنا - شبكة سبيكروتك")
                                                }
                                                WhatsAppDispatcher.sendTextMessage(context, phoneNum, summary)
                                            }
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Send,
                                                    contentDescription = "إرسال الفاتورة عبر واتساب",
                                                    tint = Color(0xFF25D366),
                                                    modifier = Modifier.size(10.dp)
                                                )
                                                Spacer(modifier = Modifier.width(2.dp))
                                                Text("واتساب", fontSize = 9.sp, color = Color(0xFF25D366), fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("إجمالي الفاتورة:", fontSize = 12.sp, color = TextSecondaryDark)
                                Text(Money(inv.totalMinor, curr).format(), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("المبلغ المحصل / المدفوع:", fontSize = 12.sp, color = TextSecondaryDark)
                                Text(Money(paidMinor, curr).format(), color = StatusOnline, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            if (remainingMinor > 0L && !isVoided) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("المبلغ المتبقي (آجل):", fontSize = 12.sp, color = TextSecondaryDark)
                                    Text(Money(remainingMinor, curr).format(), color = PaymentRed, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                            if (inv.notes.isNotBlank()) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("البيان والملاحظات:", fontSize = 12.sp, color = TextSecondaryDark)
                                    Text(inv.notes, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    if (invoiceItemsForDetail.isNotEmpty()) {
                        Text("بنود وأصناف الفاتورة:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            invoiceItemsForDetail.forEach { itm ->
                                Surface(
                                    color = CyberDarkCardElevated,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, CyberBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(itm.description.ifBlank { "كروت إنترنت" }, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                            Text("الكمية: ${itm.quantity} × ${Money(itm.unitPriceMinor, curr).format()}", fontSize = 11.sp, color = TextSecondaryDark)
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
                                    "الفاتورة ملغية بقيد عكسي. تمت استعادة أرصدة العميل/الصندوق والمخزون تلقائياً.",
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
                            modifier = Modifier.testTag("btn_void_sales_invoice")
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

    // 7. Void Sales Confirmation Dialog
    invoiceToVoid?.let { inv ->
        AlertDialog(
            onDismissRequest = { invoiceToVoid = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = SemanticExpenseRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("إلغاء فاتورة المبيعات #${inv.docNumber}", color = SemanticExpenseRed, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                ) {
                    Text(
                        "هل أنت متأكد من إلغاء هذه الفاتورة؟ سيتم عكس القيد المحاسبي في الأستاذ العام وإعادة الكروت للمخزون واستعادة أرصدة العميل تلقائياً.",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    OutlinedTextField(
                        value = voidReason,
                        onValueChange = { voidReason = it },
                        label = { Text("سبب الإلغاء (إلزامي)") },
                        modifier = Modifier.fillMaxWidth().testTag("input_sales_void_reason")
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
                    modifier = Modifier.testTag("btn_confirm_sales_void")
                ) {
                    Text("تأكيد الإلغاء والعكس")
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

// ==========================================
// Sales Invoices Tab Screen (Matching Image 3)
// ==========================================
@Composable
private fun SalesInvoicesTab(
    salesInvoices: List<DocumentEntity>,
    allSalesInvoicesCount: Int,
    unpaidCount: Int,
    paidCount: Int,
    partialCount: Int,
    partyMap: Map<String, PartyEntity>,
    allocationsByInvoice: Map<String, List<com.example.data.local.entity.AllocationEntity>>,
    cardCountByDocId: Map<String, Int>,
    searchQuery: String,
    statusFilter: String,
    onSearchChange: (String) -> Unit,
    onFilterChange: (String) -> Unit,
    onNewInvoiceClick: () -> Unit,
    onInvoiceClick: (DocumentEntity) -> Unit
) {
    var showSearchBar by rememberSaveable { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search & Filter Row with Action Button
        AnimatedVisibility(visible = showSearchBar) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                label = { Text("ابحث برقم الفاتورة أو اسم العميل / البقالة...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MikroTikCyan) },
                trailingIcon = {
                    IconButton(onClick = {
                        onSearchChange("")
                        showSearchBar = false
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق")
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MikroTikCyan,
                    unfocusedBorderColor = CyberBorder
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            )
        }

        // Quick Filter Chips Row (Matching Screen 3)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            // Search Toggle Button
            item {
                Surface(
                    color = CyberDarkSurface,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, CyberBorder),
                    modifier = Modifier.clickable { showSearchBar = !showSearchBar }
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "بحث",
                        tint = if (showSearchBar || searchQuery.isNotBlank()) MikroTikCyan else TextSecondaryDark,
                        modifier = Modifier.padding(8.dp).size(18.dp)
                    )
                }
            }

            // Prominent Action Button: "+ فاتورة جديدة" (Bright Mint/Cyan)
            item {
                Button(
                    onClick = onNewInvoiceClick,
                    colors = ButtonDefaults.buttonColors(containerColor = StatusOnline),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("btn_new_sales_invoice")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("فاتورة جديدة", fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 12.sp)
                }
            }

            // Filters
            val filters = listOf(
                "ALL" to "الكل ($allSalesInvoicesCount)",
                "UNPAID" to "آجلة ($unpaidCount)",
                "PAID" to "نقدية ($paidCount)",
                "PARTIAL" to "جزئية ($partialCount)"
            )

            items(filters) { (code, title) ->
                val isSelected = statusFilter == code
                Surface(
                    color = if (isSelected) MikroTikPrimary else CyberDarkSurface,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, if (isSelected) MikroTikCyan else CyberBorder),
                    modifier = Modifier.clickable { onFilterChange(code) }
                ) {
                    Text(
                        text = title,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) TextPrimaryDark else TextSecondaryDark,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Invoices Cards List (Matching Image 3)
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (salesInvoices.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "لا توجد فواتير مبيعات مسجلة في هذا القسم",
                            color = TextMutedDark,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            items(salesInvoices, key = { it.id }) { inv ->
                val partyName = partyMap[inv.partyId]?.name ?: "عميل غير معروف"
                val paidMinor = allocationsByInvoice[inv.id]?.sumOf { it.allocatedOrigMinor } ?: 0L
                val remainingMinor = (inv.totalMinor - paidMinor).coerceAtLeast(0L)
                val cardsCount = cardCountByDocId[inv.id] ?: 0
                val isVoided = inv.status == "VOIDED"
                val isPaid = paidMinor >= inv.totalMinor && !isVoided
                val isPartial = paidMinor > 0L && paidMinor < inv.totalMinor && !isVoided

                val formattedDocNum = "#INV-${inv.fiscalYear}-${inv.docNumber.toString().padStart(4, '0')}"

                Card(
                    colors = CardDefaults.cardColors(containerColor = CyberDarkSurface),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(
                        1.dp,
                        when {
                            isVoided -> SemanticExpenseRed.copy(alpha = 0.4f)
                            isPaid -> StatusOnline.copy(alpha = 0.3f)
                            isPartial -> InvestmentGold.copy(alpha = 0.4f)
                            else -> PaymentRed.copy(alpha = 0.4f)
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onInvoiceClick(inv) }
                        .testTag("invoice_card_${inv.docNumber}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: Expand / Arrow Indicator
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = TextMutedDark,
                            modifier = Modifier.size(16.dp)
                        )

                        // Center Details
                        Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                            // Top Row: Client Name + Invoice Doc Badge + Amount
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f, fill = false)
                                ) {
                                    Text(
                                        text = partyName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = TextPrimaryDark,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    // Invoice Badge
                                    Surface(
                                        color = MikroTikNavyLight,
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, MikroTikCyan.copy(alpha = 0.4f))
                                    ) {
                                        Text(
                                            text = formattedDocNum,
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = MikroTikCyan,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = "${inv.totalMinor / 100L} ر.ي",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 14.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = TextPrimaryDark
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Bottom Row: Cards count + Date + Status Badge
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (cardsCount > 0) "$cardsCount كرت • ${inv.fiscalYear}/01/${inv.docNumber}" else "خدمات • ${inv.fiscalYear}/01/${inv.docNumber}",
                                    fontSize = 11.sp,
                                    color = TextSecondaryDark,
                                    fontFamily = FontFamily.Monospace
                                )

                                // Status Badge
                                when {
                                    isVoided -> {
                                        Surface(
                                            color = SemanticExpenseRed.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "✕ ملغية",
                                                color = SemanticExpenseRed,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    isPaid -> {
                                        Surface(
                                            color = StatusOnline.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Icon(Icons.Default.Check, contentDescription = null, tint = StatusOnline, modifier = Modifier.size(11.dp))
                                                Spacer(modifier = Modifier.width(2.dp))
                                                Text("خالص", color = StatusOnline, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                    isPartial -> {
                                        Surface(
                                            color = InvestmentGold.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "جزئي: ${remainingMinor / 100L} ر.ي",
                                                color = InvestmentGold,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    else -> {
                                        Surface(
                                            color = PaymentRed.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "آجل: ${remainingMinor / 100L} ر.ي",
                                                color = PaymentRed,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Status Icon Circle
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isVoided -> SemanticExpenseRed.copy(alpha = 0.2f)
                                        isPaid -> StatusOnline.copy(alpha = 0.2f)
                                        isPartial -> InvestmentGold.copy(alpha = 0.2f)
                                        else -> PaymentRed.copy(alpha = 0.2f)
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when {
                                    isVoided -> Icons.Default.Close
                                    isPaid -> Icons.Default.Check
                                    isPartial -> Icons.Default.Schedule
                                    else -> Icons.Default.Schedule
                                },
                                contentDescription = null,
                                tint = when {
                                    isVoided -> SemanticExpenseRed
                                    isPaid -> StatusOnline
                                    isPartial -> InvestmentGold
                                    else -> PaymentRed
                                },
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// Points of Sale (Customers) Tab Screen
// ==========================================
@Composable
private fun PointsOfSaleList(
    parties: List<PartyEntity>,
    balances: Map<String, Long>,
    onSelectParty: (PartyEntity) -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        if (parties.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    Text("لا توجد نقاط بيع مسجلة حالياً", color = TextSecondaryDark)
                }
            }
        }

        items(parties) { party ->
            val dueBalance = balances[party.id] ?: 0L
            Card(
                colors = CardDefaults.cardColors(containerColor = CyberDarkSurface),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, CyberBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectParty(party) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(CyberDarkCardElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Storefront, contentDescription = null, tint = MikroTikCyan, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(party.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimaryDark)
                            if (party.phone.isNotBlank()) {
                                Text(party.phone, fontSize = 11.sp, color = TextSecondaryDark)
                            }
                        }
                    }

                    Surface(
                        color = if (dueBalance > 0L) PaymentRed.copy(alpha = 0.15f) else StatusOnline.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (dueBalance > 0L) "آجل: ${dueBalance / 100L} ر.ي" else "خالص (0 ر.ي)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (dueBalance > 0L) PaymentRed else StatusOnline,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// Inventory Summary Tab Screen
// ==========================================
@Composable
private fun InventorySummaryTab(
    packages: List<CardPackageEntity>,
    stockBalances: Map<String, Int>,
    totalCards: Int
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CyberDarkCardElevated),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MikroTikCyan.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("إجمالي الكروت في المخزن", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimaryDark)
                        Text("جاهزة للتوزيع ونقاط البيع", fontSize = 11.sp, color = TextSecondaryDark)
                    }
                    Text(
                        text = "$totalCards كرت",
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MikroTikCyan
                    )
                }
            }
        }

        items(packages) { pkg ->
            val count = stockBalances[pkg.id] ?: 0
            Card(
                colors = CardDefaults.cardColors(containerColor = CyberDarkSurface),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, CyberBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(pkg.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimaryDark)
                        Text(
                            "جملة: ${pkg.wholesalePriceMinor / 100L} ر.ي • تجزئة: ${pkg.retailPriceMinor / 100L} ر.ي",
                            fontSize = 11.sp,
                            color = TextSecondaryDark
                        )
                    }
                    Surface(
                        color = if (count > 0) StatusOnline.copy(alpha = 0.15f) else PaymentRed.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "$count كرت",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (count > 0) StatusOnline else PaymentRed,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

// =================================================================
// 8. Modern Sales Invoice Creation BottomSheet (Matching Screens 1 & 2)
// =================================================================
private data class SalesItemDraftState(
    var selectedPackageId: String = "",
    var description: String = "",
    var quantityText: String = "10",
    var unitPriceText: String = "500"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewSalesInvoiceBottomSheet(
    parties: List<PartyEntity>,
    packages: List<CardPackageEntity>,
    treasuries: List<TreasuryAccountEntity>,
    stockBalances: Map<String, Int>,
    partyBalances: Map<String, Long>,
    onDismiss: () -> Unit,
    onSubmit: (partyId: String, items: List<SalesItemSpec>, cashPaidMinor: Long, treasuryId: String, notes: String) -> Unit
) {
    var partySearchQuery by rememberSaveable { mutableStateOf("") }
    var selectedPartyId by rememberSaveable { mutableStateOf(parties.firstOrNull()?.id ?: AppDatabase.WALK_IN_CASH_PARTY_ID) }
    var paymentMode by rememberSaveable { mutableStateOf("CREDIT") } // CASH, CREDIT, PARTIAL
    var cashAdvanceText by rememberSaveable { mutableStateOf("") }
    var selectedTreasuryId by rememberSaveable { mutableStateOf(treasuries.firstOrNull()?.id ?: "TR_MAIN_YER") }
    var invoiceNotes by rememberSaveable { mutableStateOf("") }

    // Multi-item Draft List
    val initialPkg = packages.firstOrNull()
    val itemDrafts = remember {
        mutableStateListOf(
            SalesItemDraftState(
                selectedPackageId = initialPkg?.id ?: "",
                description = initialPkg?.name ?: "كروت إنترنت",
                quantityText = "50",
                unitPriceText = initialPkg?.let { (it.wholesalePriceMinor / 100L).toString() } ?: "3700"
            )
        )
    }

    val selectedParty = parties.firstOrNull { it.id == selectedPartyId }

    // Search matches for parties
    val matchingParties = remember(parties, partySearchQuery) {
        if (partySearchQuery.isBlank()) parties.take(5)
        else parties.filter { it.name.contains(partySearchQuery, ignoreCase = true) || it.phone.contains(partySearchQuery) }
    }

    // Total invoice calculated dynamically
    val totalInvoiceMinor by remember {
        derivedStateOf {
            itemDrafts.sumOf { item ->
                val qty = item.quantityText.toIntOrNull() ?: 0
                val price = (item.unitPriceText.toLongOrNull() ?: 0L) * 100L
                qty * price
            }
        }
    }

    val scrollState = rememberScrollState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = CyberDarkCanvas,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
        ) {
            // Header (Matching Screen 1)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = TextSecondaryDark)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "فاتورة مبيعات كروت جديدة",
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp,
                        color = TextPrimaryDark
                    )
                    Text(
                        text = "خصم تلقائي من المخزن وحسابات محاسبية دقيقة",
                        fontSize = 11.sp,
                        color = MikroTikCyan
                    )
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(CyberDarkCardElevated),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PointOfSale, contentDescription = null, tint = MikroTikCyan, modifier = Modifier.size(20.dp))
                }
            }

            HorizontalDivider(color = CyberBorder)

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ==========================================
                // 1. Client Search & Auto-complete Section
                // ==========================================
                Card(
                    colors = CardDefaults.cardColors(containerColor = CyberDarkSurface),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, CyberBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Search Input
                        OutlinedTextField(
                            value = partySearchQuery,
                            onValueChange = { partySearchQuery = it },
                            placeholder = { Text("ابحث عن العميل أو البقالة بالاسم أو الهاتف...", fontSize = 12.sp) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MikroTikCyan) },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MikroTikCyan,
                                unfocusedBorderColor = CyberBorder,
                                focusedTextColor = TextPrimaryDark,
                                unfocusedTextColor = TextPrimaryDark,
                                focusedContainerColor = CyberDarkCardElevated,
                                unfocusedContainerColor = CyberDarkCardElevated
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Selected Client Active Card (Matching Image 1)
                        selectedParty?.let { party ->
                            val balance = partyBalances[party.id] ?: 0L
                            val context = LocalContext.current
                            Surface(
                                color = CyberDarkCardElevated,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.5.dp, MikroTikCyan),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(StatusOnline.copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = StatusOnline, modifier = Modifier.size(16.dp))
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(party.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimaryDark)
                                            if (party.phone.isNotBlank()) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(party.phone, fontSize = 10.sp, color = TextSecondaryDark)
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = Color(0xFF25D366).copy(alpha = 0.15f),
                                                        modifier = Modifier.clickable {
                                                            val msg = "مرحباً ${party.name}، بخصوص حسابك ومشتريات كروت الإنترنت في شبكة سبيكروتك."
                                                            WhatsAppDispatcher.sendTextMessage(context, party.phone, msg)
                                                        }
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                        ) {
                                                            Icon(
                                                                Icons.Default.Send,
                                                                contentDescription = "مراسلة واتساب",
                                                                tint = Color(0xFF25D366),
                                                                modifier = Modifier.size(10.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(2.dp))
                                                            Text("واتساب", fontSize = 9.sp, color = Color(0xFF25D366), fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Surface(
                                        color = if (balance > 0) PaymentRed.copy(alpha = 0.15f) else StatusOnline.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = if (balance > 0) "آجل: ${balance / 100L} ر.ي" else "خالص (0 ر.ي)",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (balance > 0) PaymentRed else StatusOnline,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Matching List of Radio Buttons (Matching Image 1)
                        matchingParties.take(4).forEach { party ->
                            if (party.id != selectedPartyId) {
                                val bal = partyBalances[party.id] ?: 0L
                                Surface(
                                    color = CyberDarkCanvas,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, CyberBorder),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedPartyId = party.id
                                            partySearchQuery = ""
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            RadioButton(
                                                selected = false,
                                                onClick = {
                                                    selectedPartyId = party.id
                                                    partySearchQuery = ""
                                                },
                                                colors = RadioButtonDefaults.colors(unselectedColor = TextMutedDark),
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(party.name, fontSize = 12.sp, color = TextPrimaryDark)
                                        }

                                        Surface(
                                            color = if (bal > 0) PaymentRed.copy(alpha = 0.15f) else StatusOnline.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = if (bal > 0) "آجل: ${bal / 100L} ر.ي" else "خالص",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (bal > 0) PaymentRed else StatusOnline,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (matchingParties.size > 4) {
                            Text(
                                text = "+ ${matchingParties.size - 4} عملاء آخرين مطابقة للبحث...",
                                fontSize = 11.sp,
                                color = MikroTikCyan,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                            )
                        }
                    }
                }

                // ==========================================
                // 2. Invoice Items Section (Matching Image 2)
                // ==========================================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "أصناف الفاتورة (${itemDrafts.size} أصناف):",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = TextPrimaryDark
                    )

                    Button(
                        onClick = {
                            val nextPkg = packages.getOrNull(itemDrafts.size % packages.size) ?: initialPkg
                            itemDrafts.add(
                                SalesItemDraftState(
                                    selectedPackageId = nextPkg?.id ?: "",
                                    description = nextPkg?.name ?: "كروت إنترنت",
                                    quantityText = "20",
                                    unitPriceText = nextPkg?.let { (it.wholesalePriceMinor / 100L).toString() } ?: "3700"
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = StatusOnline),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("إضافة صنف آخر", fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 11.sp)
                    }
                }

                // Render Each Item Box (Matching Image 2)
                itemDrafts.forEachIndexed { index, item ->
                    val selectedPkg = packages.firstOrNull { it.id == item.selectedPackageId }
                    val currentStock = stockBalances[item.selectedPackageId] ?: 0
                    val enteredQty = item.quantityText.toIntOrNull() ?: 0
                    val isStockOverflow = enteredQty > currentStock && currentStock > 0

                    Card(
                        colors = CardDefaults.cardColors(containerColor = CyberDarkSurface),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(
                            1.5.dp,
                            if (isStockOverflow) SemanticExpenseRed else MikroTikPrimary.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Item Header (Index + Name + Available Stock Badge)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = MikroTikPrimary,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = " ${index + 1} ",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = TextPrimaryDark,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = selectedPkg?.name ?: item.description,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = TextPrimaryDark
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = if (currentStock > 0) StatusOnline.copy(alpha = 0.15f) else PaymentRed.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "المخزن: $currentStock كرت",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = if (currentStock > 0) StatusOnline else PaymentRed,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                        )
                                    }

                                    if (itemDrafts.size > 1) {
                                        IconButton(
                                            onClick = { itemDrafts.removeAt(index) },
                                            modifier = Modifier.size(24.dp).padding(start = 4.dp)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "حذف الصنف", tint = PaymentRed, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }

                            // Dynamic Package Selector (Horizontal Chips - Matching Image 2)
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(packages) { pkg ->
                                    val isPkgSelected = pkg.id == item.selectedPackageId
                                    Surface(
                                        color = if (isPkgSelected) MikroTikPrimary else CyberDarkCardElevated,
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, if (isPkgSelected) MikroTikCyan else CyberBorder),
                                        modifier = Modifier.clickable {
                                            itemDrafts[index] = item.copy(
                                                selectedPackageId = pkg.id,
                                                description = pkg.name,
                                                unitPriceText = (pkg.wholesalePriceMinor / 100L).toString()
                                            )
                                        }
                                    ) {
                                        Text(
                                            text = pkg.name,
                                            fontSize = 11.sp,
                                            fontWeight = if (isPkgSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isPkgSelected) TextPrimaryDark else TextSecondaryDark,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            }

                            // 3 Input Boxes Row: [العدد مع Stepper] [سعر الكرت] [الإجمالي]
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Stepper & Quantity Input
                                Column(modifier = Modifier.weight(1.3f)) {
                                    Text("العدد (اكتب يدوياً)", fontSize = 10.sp, color = TextSecondaryDark, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                                    Surface(
                                        color = CyberDarkCardElevated,
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, CyberBorder)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    val cur = item.quantityText.toIntOrNull() ?: 1
                                                    itemDrafts[index] = item.copy(quantityText = (cur + 5).toString())
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = "+", tint = MikroTikCyan, modifier = Modifier.size(14.dp))
                                            }

                                            OutlinedTextField(
                                                value = item.quantityText,
                                                onValueChange = { v ->
                                                    itemDrafts[index] = item.copy(quantityText = v.filter { it.isDigit() })
                                                },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                singleLine = true,
                                                textStyle = androidx.compose.ui.text.TextStyle(
                                                    textAlign = TextAlign.Center,
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = 14.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = TextPrimaryDark
                                                ),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = Color.Transparent,
                                                    unfocusedBorderColor = Color.Transparent,
                                                    focusedContainerColor = Color.Transparent,
                                                    unfocusedContainerColor = Color.Transparent
                                                ),
                                                modifier = Modifier.weight(1f).height(40.dp)
                                            )

                                            IconButton(
                                                onClick = {
                                                    val cur = item.quantityText.toIntOrNull() ?: 1
                                                    if (cur > 1) {
                                                        itemDrafts[index] = item.copy(quantityText = (cur - 5).coerceAtLeast(1).toString())
                                                    }
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(Icons.Default.Remove, contentDescription = "-", tint = TextSecondaryDark, modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    }
                                }

                                // Unit Price Input
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("سعر الكرت (ر.ي)", fontSize = 10.sp, color = TextSecondaryDark, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                                    OutlinedTextField(
                                        value = item.unitPriceText,
                                        onValueChange = { v ->
                                            itemDrafts[index] = item.copy(unitPriceText = v.filter { it.isDigit() })
                                        },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        textStyle = androidx.compose.ui.text.TextStyle(
                                            textAlign = TextAlign.Center,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = TextPrimaryDark
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MikroTikCyan,
                                            unfocusedBorderColor = CyberBorder,
                                            focusedContainerColor = CyberDarkCardElevated,
                                            unfocusedContainerColor = CyberDarkCardElevated
                                        ),
                                        modifier = Modifier.height(44.dp).fillMaxWidth()
                                    )
                                }

                                // Total calculated Box
                                Column(modifier = Modifier.weight(1.1f)) {
                                    Text("الإجمالي", fontSize = 10.sp, color = TextSecondaryDark, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                                    val itemTotal = (item.quantityText.toLongOrNull() ?: 0L) * (item.unitPriceText.toLongOrNull() ?: 0L)
                                    Surface(
                                        color = CyberDarkCardElevated,
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, CyberBorder),
                                        modifier = Modifier.height(44.dp).fillMaxWidth()
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "$itemTotal ر.ي",
                                                fontWeight = FontWeight.Black,
                                                fontSize = 12.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = MikroTikCyan
                                            )
                                        }
                                    }
                                }
                            }

                            // Quick Quantity Presets: [10], [20], [50], [100], [كامل المخزن] (Matching Image 2)
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val presets = listOf(10 to "10 كرت", 20 to "20 كرت", 50 to "50 كرت", 100 to "100 كرت")
                                items(presets) { (presetVal, label) ->
                                    val isSelected = item.quantityText == presetVal.toString()
                                    Surface(
                                        color = if (isSelected) MikroTikPrimary else CyberDarkCardElevated,
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, if (isSelected) MikroTikCyan else CyberBorder),
                                        modifier = Modifier.clickable {
                                            itemDrafts[index] = item.copy(quantityText = presetVal.toString())
                                        }
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 10.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) TextPrimaryDark else TextSecondaryDark,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                // Full Stock Preset
                                item {
                                    Surface(
                                        color = CyberCardHighlight,
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, InvestmentGold.copy(alpha = 0.5f)),
                                        modifier = Modifier.clickable {
                                            itemDrafts[index] = item.copy(quantityText = currentStock.coerceAtLeast(1).toString())
                                        }
                                    ) {
                                        Text(
                                            text = "كامل المخزن",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = InvestmentGold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            // Stock Overflow Alert (Matching Image 2)
                            if (isStockOverflow) {
                                Surface(
                                    color = SemanticExpenseRed.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, SemanticExpenseRed.copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Warning, contentDescription = null, tint = SemanticExpenseRed, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "تنبيه: الكمية المطلوبة ($enteredQty) تتجاوز الرصيد المتوفر في المخزن ($currentStock)!",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = SemanticExpenseRed
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ==========================================
                // 3. Payment Mode & Settlement (Matching Image 2)
                // ==========================================
                Card(
                    colors = CardDefaults.cardColors(containerColor = CyberDarkSurface),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, CyberBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "نوع الفاتورة وطريقة السداد:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = TextSecondaryDark
                        )

                        // 3-State Payment Selector
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // 1. CASH
                            val isCash = paymentMode == "CASH"
                            Surface(
                                color = if (isCash) StatusOnline.copy(alpha = 0.15f) else CyberDarkCardElevated,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.5.dp, if (isCash) StatusOnline else CyberBorder),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { paymentMode = "CASH" }
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("نقد (كاش)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (isCash) StatusOnline else TextPrimaryDark)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("سداد فوري", fontSize = 10.sp, color = TextSecondaryDark)
                                }
                            }

                            // 2. CREDIT
                            val isCredit = paymentMode == "CREDIT"
                            Surface(
                                color = if (isCredit) PaymentRed.copy(alpha = 0.15f) else CyberDarkCardElevated,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.5.dp, if (isCredit) PaymentRed else CyberBorder),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { paymentMode = "CREDIT" }
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("آجل (دين)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (isCredit) PaymentRed else TextPrimaryDark)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("قيد على الحساب", fontSize = 10.sp, color = TextSecondaryDark)
                                }
                            }

                            // 3. PARTIAL
                            val isPartial = paymentMode == "PARTIAL"
                            Surface(
                                color = if (isPartial) InvestmentGold.copy(alpha = 0.15f) else CyberDarkCardElevated,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.5.dp, if (isPartial) InvestmentGold else CyberBorder),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { paymentMode = "PARTIAL" }
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("مقدم ومتبقي", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (isPartial) InvestmentGold else TextPrimaryDark)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("دفع جزئي", fontSize = 10.sp, color = TextSecondaryDark)
                                }
                            }
                        }

                        // Treasury Selection if Cash / Partial
                        if (paymentMode == "CASH" || paymentMode == "PARTIAL") {
                            var treasuryExpanded by remember { mutableStateOf(false) }
                            val activeTreasury = treasuries.firstOrNull { it.id == selectedTreasuryId } ?: treasuries.firstOrNull()

                            ExposedDropdownMenuBox(
                                expanded = treasuryExpanded,
                                onExpandedChange = { treasuryExpanded = it }
                            ) {
                                OutlinedTextField(
                                    value = activeTreasury?.name ?: "الخزينة الرئيسية (YER)",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("الصندوق / الخزينة المستلمة") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = treasuryExpanded) },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MikroTikCyan,
                                        unfocusedBorderColor = CyberBorder
                                    ),
                                    modifier = Modifier.fillMaxWidth().menuAnchor()
                                )
                                ExposedDropdownMenu(
                                    expanded = treasuryExpanded,
                                    onDismissRequest = { treasuryExpanded = false }
                                ) {
                                    treasuries.forEach { tr ->
                                        DropdownMenuItem(
                                            text = { Text("${tr.name} (${tr.currency})") },
                                            onClick = {
                                                selectedTreasuryId = tr.id
                                                treasuryExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Cash Paid Input if Partial
                        if (paymentMode == "PARTIAL") {
                            OutlinedTextField(
                                value = cashAdvanceText,
                                onValueChange = { cashAdvanceText = it.filter { ch -> ch.isDigit() } },
                                label = { Text("المبلغ المدفوع مقدماً نقداً (ر.ي)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = InvestmentGold,
                                    unfocusedBorderColor = CyberBorder
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // ==========================================
                // 4. Notes Section
                // ==========================================
                OutlinedTextField(
                    value = invoiceNotes,
                    onValueChange = { invoiceNotes = it },
                    label = { Text("ملاحظات الفاتورة") },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MikroTikCyan,
                        unfocusedBorderColor = CyberBorder,
                        focusedContainerColor = CyberDarkSurface,
                        unfocusedContainerColor = CyberDarkSurface
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            HorizontalDivider(color = CyberBorder)

            // ==========================================
            // 5. Footer Summary & Action Buttons
            // ==========================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val validSpecs = itemDrafts.mapNotNull { item ->
                            val qty = item.quantityText.toIntOrNull() ?: 0
                            val price = (item.unitPriceText.toLongOrNull() ?: 0L) * 100L
                            if (qty > 0 && price > 0L) {
                                SalesItemSpec(
                                    description = item.description.ifBlank { "كروت إنترنت" },
                                    quantity = qty,
                                    unitPriceMinor = price,
                                    packageId = item.selectedPackageId.ifBlank { null }
                                )
                            } else null
                        }

                        if (validSpecs.isNotEmpty()) {
                            val cashPaidMinor = when (paymentMode) {
                                "CASH" -> totalInvoiceMinor
                                "CREDIT" -> 0L
                                "PARTIAL" -> (cashAdvanceText.toLongOrNull() ?: 0L) * 100L
                                else -> 0L
                            }

                            onSubmit(
                                selectedPartyId,
                                validSpecs,
                                cashPaidMinor,
                                selectedTreasuryId,
                                invoiceNotes
                            )
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MikroTikPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("btn_submit_sales_invoice")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "إصدار الفاتورة وخصم المخزن (${totalInvoiceMinor / 100L} ر.ي)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(40.dp)
                ) {
                    Text("إلغاء", color = TextSecondaryDark, fontSize = 13.sp)
                }
            }
        }
    }
}
