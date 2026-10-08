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

            // 3. Top Metrics Summary Bar for Tabs 0 & 2
            if (activeTab != 1) {
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
            }

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
                    // Enterprise Sales Invoices Screen Content
                    SalesInvoicesContent(
                        documents = documents,
                        allocations = allocations,
                        parties = parties,
                        packages = packages,
                        treasuries = treasuries,
                        stockMovements = stockMovements,
                        partyBalances = partyBalances,
                        onVoidInvoice = { docId, reason, onDone ->
                            viewModel.voidDocument(docId, reason, onDone)
                        },
                        onPostInvoice = { partyId, items, cashPaid, treasuryId, notes, onDone ->
                            viewModel.postSalesInvoiceWithSettlement(
                                partyId = partyId,
                                cardItems = items,
                                cashPaidMinor = cashPaid,
                                treasuryId = treasuryId,
                                notes = notes,
                                onSuccess = onDone
                            )
                        },
                        fetchDocumentItems = { docId, callback ->
                            viewModel.getDocumentItems(docId, callback)
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
                                                    append("شكراً لتعاملكم معنا - شبكة SamMikrotik")
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
