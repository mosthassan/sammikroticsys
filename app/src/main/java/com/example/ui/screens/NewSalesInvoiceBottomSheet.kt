package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ledger.SalesItemSpec
import com.example.data.local.AppDatabase
import com.example.data.local.entity.CardPackageEntity
import com.example.data.local.entity.PartyEntity
import com.example.data.local.entity.TreasuryAccountEntity
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberDarkCanvas
import com.example.ui.theme.CyberDarkCardElevated
import com.example.ui.theme.CyberDarkSurface
import com.example.ui.theme.InvestmentGold
import com.example.ui.theme.MikroTikCyan
import com.example.ui.theme.MikroTikPrimary
import com.example.ui.theme.PaymentRed
import com.example.ui.theme.SemanticExpenseRed
import com.example.ui.theme.StatusOnline
import com.example.ui.theme.TextMutedDark
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark
import com.example.util.WhatsAppDispatcher

data class SalesItemDraftState(
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
    val context = LocalContext.current

    var partySearchQuery by rememberSaveable { mutableStateOf("") }
    var selectedPartyId by rememberSaveable {
        mutableStateOf(parties.firstOrNull()?.id ?: AppDatabase.WALK_IN_CASH_PARTY_ID)
    }
    var paymentMode by rememberSaveable { mutableStateOf("CREDIT") } // CASH, CREDIT, PARTIAL
    var cashAdvanceText by rememberSaveable { mutableStateOf("") }
    var selectedTreasuryId by rememberSaveable {
        mutableStateOf(treasuries.firstOrNull()?.id ?: "TR_MAIN_YER")
    }
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
            // Header Bar
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
                        text = "خصم تلقائي من المخزن وتوثيق محاسبي مزدوج",
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

                        // Selected Client Active Card with Prominent Direct WhatsApp Icon
                        selectedParty?.let { party ->
                            val balance = partyBalances[party.id] ?: 0L
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
                                                .size(26.dp)
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
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(party.phone, fontSize = 11.sp, color = TextSecondaryDark)

                                                    // DIRECT WHATSAPP ICON NEXT TO THE PHONE NUMBER
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = Color(0xFF25D366).copy(alpha = 0.18f),
                                                        border = BorderStroke(1.dp, Color(0xFF25D366).copy(alpha = 0.5f)),
                                                        modifier = Modifier
                                                            .clickable {
                                                                val msg = "مرحباً ${party.name}، بخصوص مشتريات كروت الإنترنت في شبكة SamMikrotik."
                                                                WhatsAppDispatcher.sendTextMessage(context, party.phone, msg)
                                                            }
                                                            .testTag("whatsapp_invoice_party_btn")
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Send,
                                                                contentDescription = "مراسلة واتساب الفاتورة",
                                                                tint = Color(0xFF25D366),
                                                                modifier = Modifier.size(11.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text(
                                                                text = "واتساب مباشر",
                                                                fontSize = 9.sp,
                                                                color = Color(0xFF25D366),
                                                                fontWeight = FontWeight.Bold
                                                            )
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

                        // Matching List of Radio Buttons with Phone & WhatsApp
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
                                            Column {
                                                Text(party.name, fontSize = 12.sp, color = TextPrimaryDark, fontWeight = FontWeight.Medium)
                                                if (party.phone.isNotBlank()) {
                                                    Text(party.phone, fontSize = 10.sp, color = TextSecondaryDark)
                                                }
                                            }
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            if (party.phone.isNotBlank()) {
                                                IconButton(
                                                    onClick = {
                                                        WhatsAppDispatcher.sendTextMessage(
                                                            context,
                                                            party.phone,
                                                            "مرحباً ${party.name}، بخصوص حسابك في شبكة SamMikrotik."
                                                        )
                                                    },
                                                    modifier = Modifier.size(26.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Send,
                                                        contentDescription = "واتساب",
                                                        tint = Color(0xFF25D366),
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                }
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
                // 2. Invoice Items Section
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

                // Render Each Item Box
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
                            // Item Header
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

                            // Dynamic Package Selector
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(packages) { pkg ->
                                    val isSelected = pkg.id == item.selectedPackageId
                                    Surface(
                                        color = if (isSelected) MikroTikCyan.copy(alpha = 0.2f) else CyberDarkCardElevated,
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, if (isSelected) MikroTikCyan else CyberBorder),
                                        modifier = Modifier.clickable {
                                            item.selectedPackageId = pkg.id
                                            item.description = pkg.name
                                            item.unitPriceText = (pkg.wholesalePriceMinor / 100L).toString()
                                        }
                                    ) {
                                        Text(
                                            text = pkg.name,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MikroTikCyan else TextSecondaryDark,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            // Inputs Row (Quantity & Price)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = item.quantityText,
                                    onValueChange = { item.quantityText = it.filter { ch -> ch.isDigit() } },
                                    label = { Text("الكمية") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MikroTikCyan,
                                        unfocusedBorderColor = CyberBorder
                                    ),
                                    modifier = Modifier.weight(1f)
                                )

                                OutlinedTextField(
                                    value = item.unitPriceText,
                                    onValueChange = { item.unitPriceText = it.filter { ch -> ch.isDigit() } },
                                    label = { Text("سعر الحبة (ر.ي)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MikroTikCyan,
                                        unfocusedBorderColor = CyberBorder
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // ==========================================
                // 3. Payment Mode & Treasury
                // ==========================================
                Card(
                    colors = CardDefaults.cardColors(containerColor = CyberDarkSurface),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, CyberBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "طريقة الدفع والتسوية:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = TextPrimaryDark
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Mode CREDIT (آجل)
                            Surface(
                                color = if (paymentMode == "CREDIT") PaymentRed.copy(alpha = 0.2f) else CyberDarkCardElevated,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, if (paymentMode == "CREDIT") PaymentRed else CyberBorder),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { paymentMode = "CREDIT" }
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Default.Schedule, contentDescription = null, tint = if (paymentMode == "CREDIT") PaymentRed else TextSecondaryDark, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("آجل (ديون)", fontSize = 10.sp, color = TextSecondaryDark)
                                }
                            }

                            // Mode CASH (نقداً)
                            Surface(
                                color = if (paymentMode == "CASH") StatusOnline.copy(alpha = 0.2f) else CyberDarkCardElevated,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, if (paymentMode == "CASH") StatusOnline else CyberBorder),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { paymentMode = "CASH" }
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Default.Payments, contentDescription = null, tint = if (paymentMode == "CASH") StatusOnline else TextSecondaryDark, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("نقداً كامل", fontSize = 10.sp, color = TextSecondaryDark)
                                }
                            }

                            // Mode PARTIAL (دفع جزئي)
                            Surface(
                                color = if (paymentMode == "PARTIAL") InvestmentGold.copy(alpha = 0.2f) else CyberDarkCardElevated,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, if (paymentMode == "PARTIAL") InvestmentGold else CyberBorder),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { paymentMode = "PARTIAL" }
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Default.CreditCard, contentDescription = null, tint = if (paymentMode == "PARTIAL") InvestmentGold else TextSecondaryDark, modifier = Modifier.size(18.dp))
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
