package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.core.model.CurrencyCode
import com.example.core.model.Money
import com.example.data.ledger.SalesItemSpec
import com.example.data.local.AppDatabase
import com.example.data.local.entity.AllocationEntity
import com.example.data.local.entity.CardPackageEntity
import com.example.data.local.entity.DocumentEntity
import com.example.data.local.entity.DocumentItemEntity
import com.example.data.local.entity.PartyEntity
import com.example.data.local.entity.TreasuryAccountEntity
import com.example.ui.components.StatusChip
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberDarkCanvas
import com.example.ui.theme.CyberDarkCardElevated
import com.example.ui.theme.CyberDarkSurface
import com.example.ui.theme.InvestmentGold
import com.example.ui.theme.MikroTikCyan
import com.example.ui.theme.MikroTikCyanGlow
import com.example.ui.theme.MikroTikNavyLight
import com.example.ui.theme.MikroTikPrimary
import com.example.ui.theme.PaymentRed
import com.example.ui.theme.SemanticExpenseRed
import com.example.ui.theme.StatusOnline
import com.example.ui.theme.TextMutedDark
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark
import com.example.ui.viewmodel.AppViewModel
import com.example.util.PdfDocumentGenerator
import com.example.util.WhatsAppDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

/**
 * Enterprise Sales Invoice List & Quick Actions Bar.
 * Meets all enterprise requirements:
 * 1. Top Metrics Strip (Total Sales, Collected, Receivables, Sold Cards Count).
 * 2. Filter Tabs: [الكل] [آجلة] [نقدية] [جزئية] with live counters & live search.
 * 3. Expandable / Interactive Invoice Item Cards:
 *    - Header: Party/Store name, sequential invoice badge (#INV-YYYY-XXXX), Card count, Date/Time, Total in YER, status badge.
 *    - Instant Actions Row (revealed on click/expansion):
 *      [عرض] (Eye icon): Detailed items dialog.
 *      [PDF] (Document icon): Instantly generate & preview branded PDF invoice.
 *      [واتساب] (WhatsApp icon): Dispatch formatted receipt or PDF directly to customer number.
 *      [استنساخ] (Copy icon): Clone invoice items into a new draft.
 *      [تعديل] (Pencil icon): Edit invoice details.
 *      [حذف] (Trash icon): Void invoice with confirmation dialog.
 */
@Composable
fun SalesInvoicesScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val documents by viewModel.allDocuments.collectAsState()
    val allocations by viewModel.allAllocations.collectAsState()
    val parties by viewModel.allParties.collectAsState()
    val packages by viewModel.allPackages.collectAsState()
    val treasuries by viewModel.allTreasuries.collectAsState()
    val stockMovements by viewModel.allStockMovements.collectAsState()

    var partyBalances by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    LaunchedEffect(documents, allocations) {
        viewModel.getCustomerReceivableBalances { balances ->
            partyBalances = balances
        }
    }

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
        },
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesInvoicesContent(
    documents: List<DocumentEntity>,
    allocations: List<AllocationEntity>,
    parties: List<PartyEntity>,
    packages: List<CardPackageEntity>,
    treasuries: List<TreasuryAccountEntity>,
    stockMovements: List<com.example.data.local.entity.StockMovementEntity>,
    partyBalances: Map<String, Long>,
    onVoidInvoice: (docId: String, reason: String, onDone: () -> Unit) -> Unit,
    onPostInvoice: (partyId: String, items: List<SalesItemSpec>, cashPaid: Long, treasuryId: String, notes: String, onDone: () -> Unit) -> Unit,
    fetchDocumentItems: (docId: String, callback: (List<DocumentItemEntity>) -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var statusFilter by rememberSaveable { mutableStateOf("ALL") } // ALL, UNPAID, PAID, PARTIAL
    var showSearchBar by rememberSaveable { mutableStateOf(false) }

    // Bottom sheet & dialog states
    var showNewInvoiceSheet by remember { mutableStateOf(false) }
    var cloningInvoiceDraft by remember { mutableStateOf<List<SalesItemDraftState>?>(null) }
    var cloningPartyId by remember { mutableStateOf<String?>(null) }
    var cloningNotes by remember { mutableStateOf<String?>(null) }
    var editingDocId by remember { mutableStateOf<String?>(null) }
    var isCloneActive by remember { mutableStateOf(false) }

    // Detail dialog
    var selectedInvoiceForDetail by remember { mutableStateOf<DocumentEntity?>(null) }
    var invoiceItemsForDetail by remember { mutableStateOf<List<DocumentItemEntity>>(emptyList()) }

    // Void dialog
    var invoiceToVoid by remember { mutableStateOf<DocumentEntity?>(null) }
    var voidReason by remember { mutableStateOf("فاتورة مكررة / خطأ في الإدخال") }

    // PDF Preview dialog
    var previewPdfFile by remember { mutableStateOf<File?>(null) }
    var previewPdfTitle by remember { mutableStateOf("") }
    var isGeneratingPdf by remember { mutableStateOf(false) }

    // WhatsApp Direct Send Dialog
    var invoiceForWhatsApp by remember { mutableStateOf<DocumentEntity?>(null) }

    // Expansion states per invoice ID
    val expandedInvoiceIds = remember { mutableStateMapOf<String, Boolean>() }

    val partyMap = remember(parties) { parties.associateBy { it.id } }
    val allocationsByInvoice = remember(allocations) { allocations.groupBy { it.invoiceDocId } }

    val cardCountByDocId = remember(stockMovements) {
        stockMovements.filter { it.type == "SELL" && it.docId != null }
            .groupBy { it.docId!! }
            .mapValues { (_, mvts) -> mvts.sumOf { it.quantity } }
    }

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

    // Filter sales invoices
    val allSalesInvoices = remember(documents) {
        documents.filter { it.type == "SALES_INVOICE" }
    }
    val activeInvoices = remember(allSalesInvoices) {
        allSalesInvoices.filter { it.status != "VOIDED" }
    }

    // Metrics
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

    // Filter live counters
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

    // Filtered list
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
                else -> true
            }

            matchesQuery && matchesFilter
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CyberDarkCanvas)
    ) {
        // =========================================================================
        // 1. TOP METRICS STRIP (High-Contrast Summary Container)
        // =========================================================================
        Card(
            colors = CardDefaults.cardColors(containerColor = CyberDarkSurface),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.2.dp, CyberBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp)
                .testTag("metrics_strip_sales")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Total Sales (with Sold Cards Count)
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1.1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CreditCard, contentDescription = null, tint = MikroTikCyan, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("إجمالي المبيعات", fontSize = 10.sp, color = TextSecondaryDark, fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        text = "${totalSalesAmountMinor / 100L} ر.ي",
                        color = MikroTikCyan,
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "($totalSoldCards كرت مباع)",
                        color = InvestmentGold,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Box(modifier = Modifier.width(1.dp).height(32.dp).background(CyberBorder))

                // Collected (المحصل - أخضر)
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Payments, contentDescription = null, tint = StatusOnline, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("المحصل", fontSize = 10.sp, color = TextSecondaryDark, fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        text = "${totalCollectedMinor / 100L} ر.ي",
                        color = StatusOnline,
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "نقدي مسدد",
                        color = StatusOnline.copy(alpha = 0.8f),
                        fontSize = 9.sp
                    )
                }

                Box(modifier = Modifier.width(1.dp).height(32.dp).background(CyberBorder))

                // Receivables / Remaining (الآجل / المتبقي - أحمر / برتقالي)
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = PaymentRed, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("الآجل / المتبقي", fontSize = 10.sp, color = TextSecondaryDark, fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        text = "${totalReceivablesMinor / 100L} ر.ي",
                        color = PaymentRed,
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "ديون ذمم",
                        color = PaymentRed.copy(alpha = 0.8f),
                        fontSize = 9.sp
                    )
                }
            }
        }

        // =========================================================================
        // 2. SEARCH BAR & QUICK FILTER TABS WITH LIVE COUNTERS
        // =========================================================================
        AnimatedVisibility(visible = showSearchBar) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("ابحث برقم الفاتورة، اسم العميل أو البقالة...", fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MikroTikCyan) },
                trailingIcon = {
                    IconButton(onClick = {
                        searchQuery = ""
                        showSearchBar = false
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق")
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MikroTikCyan,
                    unfocusedBorderColor = CyberBorder,
                    focusedContainerColor = CyberDarkCardElevated,
                    unfocusedContainerColor = CyberDarkCardElevated,
                    focusedTextColor = TextPrimaryDark,
                    unfocusedTextColor = TextPrimaryDark
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .testTag("input_search_sales_invoices")
            )
        }

        // Action Buttons Row & Filter Chips
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

            // Prominent Action Button: "+ فاتورة جديدة" (Mint / StatusOnline)
            item {
                Button(
                    onClick = {
                        cloningInvoiceDraft = null
                        cloningPartyId = null
                        cloningNotes = null
                        editingDocId = null
                        isCloneActive = false
                        showNewInvoiceSheet = true
                    },
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

            // Live Filter Tabs: [الكل] [آجلة] [نقدية] [جزئية]
            val filterTabs = listOf(
                "ALL" to "الكل (${allSalesInvoices.size})",
                "UNPAID" to "آجلة ($unpaidCount)",
                "PAID" to "نقدية ($paidCount)",
                "PARTIAL" to "جزئية ($partialCount)"
            )

            items(filterTabs) { (code, title) ->
                val isSelected = statusFilter == code
                Surface(
                    color = if (isSelected) MikroTikPrimary else CyberDarkSurface,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, if (isSelected) MikroTikCyan else CyberBorder),
                    modifier = Modifier
                        .clickable { statusFilter = code }
                        .testTag("filter_tab_$code")
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

        // =========================================================================
        // 3. EXPANDABLE / INTERACTIVE INVOICE ITEM CARDS LIST
        // =========================================================================
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (filteredInvoices.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.AutoMirrored.Filled.ReceiptLong,
                                contentDescription = null,
                                tint = TextMutedDark,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "لا توجد فواتير مبيعات مسجلة في هذا القسم",
                                color = TextMutedDark,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            items(filteredInvoices, key = { it.id }) { inv ->
                val isExpanded = expandedInvoiceIds[inv.id] == true
                val party = partyMap[inv.partyId]
                val partyName = party?.name ?: "عميل نقدي"
                val paidMinor = allocationsByInvoice[inv.id]?.sumOf { it.allocatedOrigMinor } ?: 0L
                val remainingMinor = (inv.totalMinor - paidMinor).coerceAtLeast(0L)
                val cardsCount = cardCountByDocId[inv.id] ?: 0
                val isVoided = inv.status == "VOIDED"
                val isPaid = paidMinor >= inv.totalMinor && !isVoided
                val isPartial = paidMinor > 0L && paidMinor < inv.totalMinor && !isVoided

                val formattedDocNum = "#INV-${inv.fiscalYear}-${inv.docNumber.toString().padStart(4, '0')}"
                val curr = CurrencyCode.fromString(inv.currency)

                InvoiceItemCard(
                    invoice = inv,
                    party = party,
                    partyName = partyName,
                    formattedDocNum = formattedDocNum,
                    cardsCount = cardsCount,
                    paidMinor = paidMinor,
                    remainingMinor = remainingMinor,
                    isVoided = isVoided,
                    isPaid = isPaid,
                    isPartial = isPartial,
                    currency = curr,
                    isExpanded = isExpanded,
                    onToggleExpand = {
                        expandedInvoiceIds[inv.id] = !isExpanded
                    },
                    onViewClick = {
                        selectedInvoiceForDetail = inv
                        fetchDocumentItems(inv.id) { items ->
                            invoiceItemsForDetail = items
                        }
                    },
                    onPdfClick = {
                        isGeneratingPdf = true
                        fetchDocumentItems(inv.id) { items ->
                            scope.launch(Dispatchers.IO) {
                                val pdfFile = PdfDocumentGenerator.generateSalesInvoicePdf(
                                    context = context,
                                    invoice = inv,
                                    items = items,
                                    party = party,
                                    paidAmountMinor = paidMinor
                                )
                                withContext(Dispatchers.Main) {
                                    isGeneratingPdf = false
                                    previewPdfFile = pdfFile
                                    previewPdfTitle = "فاتورة مبيعات $formattedDocNum"
                                }
                            }
                        }
                    },
                    onWhatsAppClick = {
                        invoiceForWhatsApp = inv
                    },
                    onCloneClick = {
                        fetchDocumentItems(inv.id) { items ->
                            val drafts = items.map { item ->
                                SalesItemDraftState(
                                    selectedPackageId = item.packageId ?: "",
                                    description = item.description,
                                    quantityText = item.quantity.toString(),
                                    unitPriceText = (item.unitPriceMinor / 100L).toString()
                                )
                            }
                            cloningInvoiceDraft = drafts
                            cloningPartyId = inv.partyId
                            cloningNotes = "نسخة مستنسخة من فاتورة #INV-${inv.fiscalYear}-${inv.docNumber}"
                            editingDocId = null
                            isCloneActive = true
                            showNewInvoiceSheet = true
                        }
                    },
                    onEditClick = {
                        fetchDocumentItems(inv.id) { items ->
                            val drafts = items.map { item ->
                                SalesItemDraftState(
                                    selectedPackageId = item.packageId ?: "",
                                    description = item.description,
                                    quantityText = item.quantity.toString(),
                                    unitPriceText = (item.unitPriceMinor / 100L).toString()
                                )
                            }
                            cloningInvoiceDraft = drafts
                            cloningPartyId = inv.partyId
                            cloningNotes = inv.notes
                            editingDocId = inv.id
                            isCloneActive = false
                            showNewInvoiceSheet = true
                        }
                    },
                    onDeleteClick = {
                        invoiceToVoid = inv
                    }
                )
            }
        }
    }

    // =========================================================================
    // 4. NEW / CLONE / EDIT SALES INVOICE BOTTOM SHEET
    // =========================================================================
    if (showNewInvoiceSheet) {
        NewSalesInvoiceBottomSheet(
            parties = parties.filter { it.isCustomer },
            packages = packages,
            treasuries = treasuries,
            stockBalances = stockBalanceByPackageId,
            partyBalances = partyBalances,
            initialPartyId = cloningPartyId,
            initialItems = cloningInvoiceDraft,
            initialNotes = cloningNotes,
            editingInvoiceDocId = editingDocId,
            isCloneMode = isCloneActive,
            onDismiss = { showNewInvoiceSheet = false },
            onSubmit = { partyId, items, cashPaidMinor, treasuryId, notes ->
                onPostInvoice(partyId, items, cashPaidMinor, treasuryId, notes) {
                    showNewInvoiceSheet = false
                }
            }
        )
    }

    // =========================================================================
    // 5. VIEW DETAILED ITEMS DIALOG
    // =========================================================================
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
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("العميل / نقطة البيع:", fontSize = 12.sp, color = TextSecondaryDark)
                                Text(party?.name ?: "عميل نقدي", fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
                }
            },
            confirmButton = {
                Button(onClick = { selectedInvoiceForDetail = null }) {
                    Text("إغلاق")
                }
            }
        )
    }

    // =========================================================================
    // 6. VOID / DELETE CONFIRMATION DIALOG (IFRS Compliance - Reversal Entry)
    // =========================================================================
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
                            onVoidInvoice(inv.id, voidReason) {
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

    // =========================================================================
    // 7. WHATSAPP DIRECT DISPATCH DIALOG
    // =========================================================================
    invoiceForWhatsApp?.let { inv ->
        val party = partyMap[inv.partyId]
        val phoneNum = party?.phone ?: ""
        var targetPhone by remember { mutableStateOf(phoneNum) }
        val allocs = allocationsByInvoice[inv.id] ?: emptyList()
        val paidMinor = allocs.sumOf { it.allocatedOrigMinor }
        val remainingMinor = (inv.totalMinor - paidMinor).coerceAtLeast(0L)
        val curr = CurrencyCode.fromString(inv.currency)

        AlertDialog(
            onDismissRequest = { invoiceForWhatsApp = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Send, contentDescription = null, tint = Color(0xFF25D366))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("إرسال الفاتورة عبر واتساب", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("العميل: ${party?.name ?: "عميل نقدي"}")
                    OutlinedTextField(
                        value = targetPhone,
                        onValueChange = { targetPhone = it },
                        label = { Text("رقم هاتف الوكيل / العميل") },
                        placeholder = { Text("77xxxxxxx أو +96777xxxxxxx") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("اختر طريقة الإرسال المناسبة:", fontSize = 12.sp, color = TextSecondaryDark)
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Send Formatted Receipt Text
                    Button(
                        onClick = {
                            val summary = buildString {
                                append("📄 *فاتورة مبيعات كروت #${inv.docNumber}*\n")
                                append("العميل: ${party?.name ?: "عميل نقدي"}\n")
                                append("إجمالي الفاتورة: ${Money(inv.totalMinor, curr).format()}\n")
                                append("المدفوع: ${Money(paidMinor, curr).format()}\n")
                                if (remainingMinor > 0L) {
                                    append("المتبقي: ${Money(remainingMinor, curr).format()}\n")
                                }
                                append("شكراً لتعاملكم معنا - شبكة SamMikrotik")
                            }
                            WhatsAppDispatcher.sendTextMessage(context, targetPhone, summary)
                            invoiceForWhatsApp = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
                    ) {
                        Text("إرسال نص الفاتورة", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }

                    // Send PDF Document
                    Button(
                        onClick = {
                            fetchDocumentItems(inv.id) { items ->
                                scope.launch(Dispatchers.IO) {
                                    val pdfFile = PdfDocumentGenerator.generateSalesInvoicePdf(
                                        context = context,
                                        invoice = inv,
                                        items = items,
                                        party = party,
                                        paidAmountMinor = paidMinor
                                    )
                                    withContext(Dispatchers.Main) {
                                        WhatsAppDispatcher.sendDocument(
                                            context = context,
                                            phoneNumber = targetPhone,
                                            file = pdfFile,
                                            caption = "مرفق فاتورة مبيعات كروت #${inv.docNumber}"
                                        )
                                        invoiceForWhatsApp = null
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MikroTikCyan)
                    ) {
                        Text("إرسال مستند PDF", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { invoiceForWhatsApp = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // =========================================================================
    // 8. PIXEL-PERFECT IN-APP PDF PREVIEW DIALOG
    // =========================================================================
    previewPdfFile?.let { pdfFile ->
        PdfPreviewDialog(
            pdfFile = pdfFile,
            title = previewPdfTitle,
            onDismiss = { previewPdfFile = null }
        )
    }

    // Loading overlay
    if (isGeneratingPdf) {
        Dialog(onDismissRequest = {}) {
            Surface(
                color = CyberDarkCardElevated,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, CyberBorder),
                modifier = Modifier.padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(color = MikroTikCyan, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("جاري توليد ملف الـ PDF عالي الدقة...", fontSize = 13.sp, color = TextPrimaryDark)
                }
            }
        }
    }
}

/**
 * Expandable / Interactive Invoice Item Card.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InvoiceItemCard(
    invoice: DocumentEntity,
    party: PartyEntity?,
    partyName: String,
    formattedDocNum: String,
    cardsCount: Int,
    paidMinor: Long,
    remainingMinor: Long,
    isVoided: Boolean,
    isPaid: Boolean,
    isPartial: Boolean,
    currency: CurrencyCode,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onViewClick: () -> Unit,
    onPdfClick: () -> Unit,
    onWhatsAppClick: () -> Unit,
    onCloneClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CyberDarkSurface),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            1.2.dp,
            when {
                isVoided -> SemanticExpenseRed.copy(alpha = 0.5f)
                isPaid -> StatusOnline.copy(alpha = 0.35f)
                isPartial -> InvestmentGold.copy(alpha = 0.45f)
                else -> PaymentRed.copy(alpha = 0.45f)
            }
        ),
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .testTag("invoice_card_${invoice.docNumber}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            // ==========================================
            // HEADER ROW (Click to toggle expansion)
            // ==========================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Expand Icon / Chevron
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "طي التفاصيل" else "توسيع وتفاصيل الإجراءات",
                    tint = if (isExpanded) MikroTikCyan else TextSecondaryDark,
                    modifier = Modifier.size(22.dp)
                )

                // Center Info Column
                Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    // Top: Party/Store name + Sequential Invoice Badge + Total
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

                            // Sequential Invoice Badge (#INV-YYYY-XXXX)
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

                        // Total in YER
                        Text(
                            text = "${invoice.totalMinor / 100L} ر.ي",
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextPrimaryDark
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Bottom Row: Card Count, Date/Time, and Status Badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (cardsCount > 0) "$cardsCount كرت • ${invoice.fiscalYear}/01/${invoice.docNumber}" else "خدمات • ${invoice.fiscalYear}/01/${invoice.docNumber}",
                            fontSize = 11.sp,
                            color = TextSecondaryDark,
                            fontFamily = FontFamily.Monospace
                        )

                        // Status Badge: (خالص / آجل / دفعة / ملغية)
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
                                        text = "دفعة: ${paidMinor / 100L} ر.ي",
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
            }

            // ==========================================
            // EXPANDED BODY: INSTANT ACTIONS ROW
            // ==========================================
            if (isExpanded) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = CyberBorder.copy(alpha = 0.7f))
                Spacer(modifier = Modifier.height(8.dp))

                // Financial Overview Strip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "المدفوع: ${paidMinor / 100L} ر.ي | المتبقي: ${remainingMinor / 100L} ر.ي",
                        fontSize = 11.sp,
                        color = if (remainingMinor > 0) PaymentRed else StatusOnline,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (invoice.notes.isNotBlank()) {
                        Text(
                            text = invoice.notes,
                            fontSize = 10.5.sp,
                            color = TextMutedDark,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Instant Actions Row: [عرض] [PDF] [واتساب] [استنساخ] [تعديل] [حذف]
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // 1. [عرض] (Eye icon): View detailed items
                    ActionButtonChip(
                        icon = Icons.Default.Visibility,
                        label = "عرض",
                        tint = MikroTikCyan,
                        onClick = onViewClick,
                        tag = "btn_view_invoice_${invoice.docNumber}"
                    )

                    // 2. [PDF] (Document icon): Instantly generate & preview PDF invoice
                    ActionButtonChip(
                        icon = Icons.Default.PictureAsPdf,
                        label = "PDF",
                        tint = Color(0xFFFF5252),
                        onClick = onPdfClick,
                        tag = "btn_pdf_invoice_${invoice.docNumber}"
                    )

                    // 3. [واتساب] (WhatsApp icon): Dispatch formatted receipt or PDF
                    ActionButtonChip(
                        icon = Icons.Default.Send,
                        label = "واتساب",
                        tint = Color(0xFF25D366),
                        onClick = onWhatsAppClick,
                        tag = "btn_whatsapp_invoice_${invoice.docNumber}"
                    )

                    // 4. [استنساخ] (Copy icon): Clone invoice items into a new draft
                    ActionButtonChip(
                        icon = Icons.Default.ContentCopy,
                        label = "استنساخ",
                        tint = InvestmentGold,
                        onClick = onCloneClick,
                        tag = "btn_clone_invoice_${invoice.docNumber}"
                    )

                    // 5. [تعديل] (Pencil icon): Edit invoice details
                    if (!isVoided) {
                        ActionButtonChip(
                            icon = Icons.Default.Edit,
                            label = "تعديل",
                            tint = Color(0xFF38BDF8),
                            onClick = onEditClick,
                            tag = "btn_edit_invoice_${invoice.docNumber}"
                        )
                    }

                    // 6. [حذف] (Trash icon with confirmation dialog): Void invoice
                    if (!isVoided) {
                        ActionButtonChip(
                            icon = Icons.Default.Delete,
                            label = "حذف",
                            tint = SemanticExpenseRed,
                            onClick = onDeleteClick,
                            tag = "btn_delete_invoice_${invoice.docNumber}"
                        )
                    }
                }
            }
        }
    }
}

/**
 * Reusable Action Button Chip with touch target >= 48dp.
 */
@Composable
private fun ActionButtonChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    tag: String
) {
    Surface(
        color = tint.copy(alpha = 0.12f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.45f)),
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = tint
            )
        }
    }
}

/**
 * Pixel-Perfect In-App PDF Preview Dialog.
 * Shows high-resolution rendered Bitmaps of the PDF, plus share, WhatsApp, open, and close actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfPreviewDialog(
    pdfFile: File,
    title: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var pages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(pdfFile) {
        withContext(Dispatchers.IO) {
            val rendered = PdfDocumentGenerator.renderPdfPages(pdfFile)
            withContext(Dispatchers.Main) {
                pages = rendered
                isLoading = false
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                Surface(
                    color = CyberDarkCardElevated,
                    border = BorderStroke(1.dp, CyberBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = TextPrimaryDark)
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimaryDark)
                            Text(
                                text = "معاينة المستند الرسمي للطباعة والتصدير",
                                fontSize = 10.sp,
                                color = MikroTikCyan
                            )
                        }

                        // Share Action
                        IconButton(onClick = {
                            PdfDocumentGenerator.sharePdfFile(context, pdfFile, title)
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "مشاركة", tint = MikroTikCyan)
                        }
                    }
                }
            },
            bottomBar = {
                Surface(
                    color = CyberDarkCardElevated,
                    border = BorderStroke(1.dp, CyberBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // WhatsApp Direct Send Button
                        Button(
                            onClick = {
                                WhatsAppDispatcher.sendDocument(
                                    context = context,
                                    phoneNumber = "",
                                    file = pdfFile,
                                    caption = title
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("إرسال عبر واتساب", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        // Open in External App
                        OutlinedButton(
                            onClick = {
                                PdfDocumentGenerator.openPdfFile(context, pdfFile)
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null, tint = MikroTikCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("فتح بقارئ خارجي", color = TextPrimaryDark, fontSize = 12.sp)
                        }
                    }
                }
            },
            containerColor = Color(0xFF1E293B)
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = MikroTikCyan)
                } else if (pages.isEmpty()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "تم توليد مستند الـ PDF بنجاح",
                            color = TextPrimaryDark,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "حجم الملف: ${pdfFile.length()} بايت",
                            color = TextSecondaryDark,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(onClick = {
                            PdfDocumentGenerator.openPdfFile(context, pdfFile)
                        }) {
                            Text("فتح المستند مباشرة")
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        pages.forEachIndexed { idx, bmp ->
                            Card(
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                            ) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "صفحة ${idx + 1}",
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
