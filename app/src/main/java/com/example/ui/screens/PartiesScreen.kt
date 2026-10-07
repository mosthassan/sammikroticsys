package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.components.PartyJsonBackupDialog
import com.example.ui.components.PartnerDetailDialog
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberDarkCardElevated
import com.example.ui.theme.CyberDarkSurface
import com.example.ui.theme.MikroTikCyan
import com.example.ui.theme.MikroTikPrimary
import com.example.ui.theme.StatusOnline
import com.example.ui.theme.TextMutedDark
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.ledger.AccountConstants
import com.example.core.model.CurrencyCode
import com.example.core.model.Money
import com.example.core.model.UuidUtils
import com.example.data.local.AppDatabase
import com.example.data.local.entity.PartyEntity
import com.example.domain.usecase.StatementOfAccountReport
import com.example.ui.components.AmountSemanticType
import com.example.ui.components.AmountText
import com.example.ui.components.SectionHeader
import com.example.ui.theme.SemanticExpenseRed
import com.example.ui.theme.SemanticIncomeGreen
import com.example.ui.theme.SemanticWarningAmber
import com.example.ui.viewmodel.AppViewModel

@Composable
fun PartiesScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val parties by viewModel.allParties.collectAsState()
    val currentStatement by viewModel.currentPartyStatement.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: All, 1: Groceries/Agents, 2: Vendors, 3: Partners
    var searchQuery by remember { mutableStateOf("") }
    var showAddPartySheet by remember { mutableStateOf(false) }
    var showJsonBackupDialog by remember { mutableStateOf(false) }
    var selectedPartyForStatement by remember { mutableStateOf<PartyEntity?>(null) }
    var selectedPartnerForDetail by remember { mutableStateOf<PartyEntity?>(null) }

    val filteredParties = remember(parties, selectedTab, searchQuery) {
        parties.filter { p ->
            val matchesTab = when (selectedTab) {
                1 -> p.isCustomer
                2 -> p.isVendor
                3 -> p.isPartner
                else -> true
            }
            val matchesQuery = searchQuery.isBlank() || p.name.contains(searchQuery, ignoreCase = true) || p.phone.contains(searchQuery)
            matchesTab && matchesQuery
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddPartySheet = true },
                containerColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("fab_add_party")
            ) {
                Icon(Icons.Default.Add, contentDescription = "إضافة طرف جديد")
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
            // Action Bar with JSON Export/Import
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("دليل الأطراف والوكلاء", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                OutlinedButton(
                    onClick = { showJsonBackupDialog = true },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.SwapVert, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("تصدير / استيراد JSON", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("بحث عن بقالة أو وكيل أو مورد") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Modern Tech Segmented Role Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CyberDarkCardElevated)
                    .border(BorderStroke(1.dp, CyberBorder), RoundedCornerShape(14.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                PartyModernTabPill(
                    title = "الكل",
                    count = parties.size,
                    icon = Icons.Default.Groups,
                    tintColor = MikroTikCyan,
                    isSelected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    modifier = Modifier.weight(1f)
                )
                PartyModernTabPill(
                    title = "بقالات",
                    count = parties.count { it.isCustomer },
                    icon = Icons.Default.Store,
                    tintColor = Color(0xFF10B981),
                    isSelected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    modifier = Modifier.weight(1.1f)
                )
                PartyModernTabPill(
                    title = "موردون",
                    count = parties.count { it.isVendor },
                    icon = Icons.Default.LocalShipping,
                    tintColor = Color(0xFF3B82F6),
                    isSelected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    modifier = Modifier.weight(1f)
                )
                PartyModernTabPill(
                    title = "شركاء",
                    count = parties.count { it.isPartner },
                    icon = Icons.Default.Handshake,
                    tintColor = Color(0xFFF59E0B),
                    isSelected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Parties List
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredParties) { party ->
                    var partyBalance by remember { mutableStateOf<Long?>(null) }

                    LaunchedEffect(party.id) {
                        val controlCode = when {
                            party.isPartner -> AccountConstants.PARTNER_CURRENT
                            party.isVendor -> AccountConstants.ACCOUNTS_PAYABLE
                            else -> AccountConstants.ACCOUNTS_RECEIVABLE
                        }
                        partyBalance = viewModel.repository.getPartyBalance(party.id, controlCode)
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                            .clickable {
                                if (party.isPartner) {
                                    selectedPartnerForDetail = party
                                } else {
                                    selectedPartyForStatement = party
                                    val controlCode = if (party.isVendor) AccountConstants.ACCOUNTS_PAYABLE else AccountConstants.ACCOUNTS_RECEIVABLE
                                    viewModel.loadStatementOfAccount(party.id, controlCode, null, null)
                                }
                            }
                            .testTag("party_item_${party.id}")
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
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (party.isCustomer) Icons.Default.Store else Icons.Default.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(text = party.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        if (party.isCustomer) RoleChip("وكيل/بقالة", SemanticIncomeGreen)
                                        if (party.isVendor) RoleChip("مورد", Color(0xFF3B82F6))
                                        if (party.isPartner) RoleChip("شريك", SemanticWarningAmber)
                                        if (!party.isActive) RoleChip("مؤرشف", Color.Gray)
                                    }
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                partyBalance?.let { bal ->
                                    AmountText(
                                        money = Money(bal, CurrencyCode.FUNCTIONAL),
                                        semanticType = AmountSemanticType.AUTO,
                                        fontSize = 14
                                    )
                                }
                                Text(
                                    if (party.isPartner) "إدارة رأس المال والسندات ➔" else "انقر لكشف الحساب",
                                    fontSize = 10.sp,
                                    color = if (party.isPartner) MikroTikCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (party.isPartner) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Statement of Account Dialog
    selectedPartyForStatement?.let { party ->
        StatementOfAccountDialog(
            party = party,
            report = currentStatement,
            onDismiss = { selectedPartyForStatement = null }
        )
    }

    // Add Party Dialog (Full Screen Responsive with Keyboard Avoidance)
    if (showAddPartySheet) {
        AddPartyDialog(
            initialRole = selectedTab,
            onDismiss = { showAddPartySheet = false },
            onSubmit = { name, phone, isCust, isVend, isPart, limitMinor, equityBps ->
                val newParty = PartyEntity(
                    id = UuidUtils.newTimeOrderedId(),
                    name = name,
                    phone = phone,
                    isCustomer = isCust,
                    isVendor = isVend,
                    isPartner = isPart,
                    creditLimitMinor = limitMinor,
                    equityPercentageBasisPoints = equityBps
                )
                viewModel.insertParty(newParty) {
                    showAddPartySheet = false
                }
            }
        )
    }

    // JSON Backup Dialog
    if (showJsonBackupDialog) {
        PartyJsonBackupDialog(
            parties = parties,
            onImport = { imported ->
                viewModel.importPartiesList(imported)
            },
            onDismissRequest = { showJsonBackupDialog = false }
        )
    }

    // Partner Capital & Vouchers Dialog
    selectedPartnerForDetail?.let { partner ->
        PartnerDetailDialog(
            partner = partner,
            viewModel = viewModel,
            onDismissRequest = { selectedPartnerForDetail = null }
        )
    }
}

@Composable
fun RoleChip(title: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = title,
            color = color,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun StatementOfAccountDialog(
    party: PartyEntity,
    report: StatementOfAccountReport?,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("كشف حساب: ${party.name}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                IconButton(onClick = { /* Share statement text */ }) {
                    Icon(Icons.Default.Share, contentDescription = "مشاركة")
                }
            }
        },
        text = {
            if (report == null) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("جاري استخراج أسطر الأستاذ...")
                }
            } else {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("الرصيد الافتتاحي: ${Money(report.openingBalanceMinor, CurrencyCode.FUNCTIONAL).format()}", fontSize = 12.sp)
                        Text("الرصيد الجاري: ${Money(report.closingBalanceMinor, CurrencyCode.FUNCTIONAL).format()}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(modifier = Modifier.height(280.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (report.items.isEmpty()) {
                            item {
                                Text("لا توجد حركات مسجلة لهذا الطرف في دفتر الأستاذ", fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                        items(report.items) { itm ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${itm.docType} #${itm.docNumber}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Text(itm.memo, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        if (itm.baseDebitMinor > 0L) {
                                            Text("مدين: ${Money(itm.baseDebitMinor, CurrencyCode.FUNCTIONAL).format()}", color = SemanticIncomeGreen, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                        }
                                        if (itm.baseCreditMinor > 0L) {
                                            Text("دائن: ${Money(itm.baseCreditMinor, CurrencyCode.FUNCTIONAL).format()}", color = SemanticExpenseRed, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                        }
                                        Text("الرصيد: ${Money(itm.runningBalanceMinor, CurrencyCode.FUNCTIONAL).format()}", fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("إغلاق") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPartyDialog(
    initialRole: Int = 0,
    onDismiss: () -> Unit,
    onSubmit: (name: String, phone: String, isCustomer: Boolean, isVendor: Boolean, isPartner: Boolean, creditLimitMinor: Long, equityBasisPoints: Int) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var isCustomer by remember { mutableStateOf(initialRole == 0 || initialRole == 1) }
    var isVendor by remember { mutableStateOf(initialRole == 2) }
    var isPartner by remember { mutableStateOf(initialRole == 3) }
    var creditLimitText by remember { mutableStateOf("") }
    var equityShareText by remember { mutableStateOf("") }

    val emeraldColor = Color(0xFF10B981)
    val blueColor = Color(0xFF3B82F6)
    val amberColor = Color(0xFFF59E0B)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 720.dp)
                .clip(RoundedCornerShape(22.dp))
                .border(BorderStroke(1.dp, CyberBorder), RoundedCornerShape(22.dp)),
            color = CyberDarkSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
            ) {
                // Header Bar (Pinned)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CyberDarkCardElevated)
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MikroTikPrimary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, tint = MikroTikCyan, modifier = Modifier.size(22.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("إضافة طرف جديد للنظام", color = TextPrimaryDark, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("تسجيل بقالة، مورد شبكة، أو شريك رأسمالي", color = TextSecondaryDark, fontSize = 11.sp)
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = TextSecondaryDark)
                    }
                }

                HorizontalDivider(color = CyberBorder)

                // Scrollable Form Fields
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Full Name Input
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("الاسم الكامل للطرف أو المحل (مطلوب)") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = MikroTikCyan) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MikroTikCyan,
                            unfocusedBorderColor = CyberBorder,
                            focusedTextColor = TextPrimaryDark,
                            unfocusedTextColor = TextPrimaryDark,
                            focusedContainerColor = CyberDarkCardElevated,
                            unfocusedContainerColor = CyberDarkCardElevated
                        ),
                        singleLine = true
                    )

                    // Phone / WhatsApp Input
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("رقم الهاتف أو الواتساب") },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = MikroTikCyan) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MikroTikCyan,
                            unfocusedBorderColor = CyberBorder,
                            focusedTextColor = TextPrimaryDark,
                            unfocusedTextColor = TextPrimaryDark,
                            focusedContainerColor = CyberDarkCardElevated,
                            unfocusedContainerColor = CyberDarkCardElevated
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        "تحديد أدوار وتصنيف الطرف (يمكن اختيار أكثر من دور):",
                        color = TextPrimaryDark,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )

                    // Role 1: Customer / Grocery Store
                    ModernRoleSelectionCard(
                        title = "وكيل / بقالة توزيع كروت",
                        subtitle = "حساب مدين ومبيعات كروت بالآجل وسندات قبض",
                        icon = Icons.Default.Store,
                        tintColor = emeraldColor,
                        isSelected = isCustomer,
                        onToggle = { isCustomer = !isCustomer }
                    )

                    // Role 2: Network Equipment Vendor
                    ModernRoleSelectionCard(
                        title = "مورد أجهزة وخدمات شبكة",
                        subtitle = "حساب دائن وفواتير مشتريات (أبراج، راوترات، خطوط إنترنت)",
                        icon = Icons.Default.LocalShipping,
                        tintColor = blueColor,
                        isSelected = isVendor,
                        onToggle = { isVendor = !isVendor }
                    )

                    // Role 3: Capital Partner
                    ModernRoleSelectionCard(
                        title = "شريك ومساهم في رأس المال",
                        subtitle = "سندات مساهمة نقدية وعينية، حصص الأرباح، وسجل الأستاذ 3101",
                        icon = Icons.Default.Handshake,
                        tintColor = amberColor,
                        isSelected = isPartner,
                        onToggle = { isPartner = !isPartner }
                    )

                    // Contextual Settings for Customer (Credit Limit)
                    if (isCustomer) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = emeraldColor.copy(alpha = 0.08f)),
                            border = BorderStroke(1.dp, emeraldColor.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = emeraldColor, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("الحد الائتماني المسموح به للبقالة (آجل)", color = emeraldColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = creditLimitText,
                                    onValueChange = { creditLimitText = it },
                                    label = { Text("المبلغ بالريال اليمني (YER)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = emeraldColor,
                                        unfocusedBorderColor = CyberBorder,
                                        focusedTextColor = TextPrimaryDark,
                                        unfocusedTextColor = TextPrimaryDark,
                                        focusedContainerColor = CyberDarkCardElevated,
                                        unfocusedContainerColor = CyberDarkCardElevated
                                    )
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("25000", "50000", "100000", "200000").forEach { quickVal ->
                                        Surface(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { creditLimitText = quickVal }
                                                .border(1.dp, emeraldColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                                            color = emeraldColor.copy(alpha = 0.12f)
                                        ) {
                                            Text(
                                                "${quickVal.toInt() / 1000} ألف",
                                                color = TextPrimaryDark,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Contextual Settings for Partner (Equity Share %)
                    if (isPartner) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = amberColor.copy(alpha = 0.08f)),
                            border = BorderStroke(1.dp, amberColor.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.PieChart, contentDescription = null, tint = amberColor, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("نسبة الشريك في توزيع الأرباح %", color = amberColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = equityShareText,
                                    onValueChange = { equityShareText = it },
                                    label = { Text("النسبة المئوية % (مثال: 25)") },
                                    leadingIcon = { Icon(Icons.Default.Percent, contentDescription = null, tint = amberColor) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = amberColor,
                                        unfocusedBorderColor = CyberBorder,
                                        focusedTextColor = TextPrimaryDark,
                                        unfocusedTextColor = TextPrimaryDark,
                                        focusedContainerColor = CyberDarkCardElevated,
                                        unfocusedContainerColor = CyberDarkCardElevated
                                    )
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("10", "20", "25", "33.3", "50").forEach { quickPct ->
                                        Surface(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { equityShareText = quickPct }
                                                .border(1.dp, amberColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                                            color = amberColor.copy(alpha = 0.12f)
                                        ) {
                                            Text(
                                                "$quickPct%",
                                                color = TextPrimaryDark,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = CyberBorder)

                // Pinned Action Button at Bottom
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CyberDarkCardElevated)
                        .padding(16.dp)
                ) {
                    val isValid = name.isNotBlank() && (isCustomer || isVendor || isPartner)

                    Button(
                        onClick = {
                            if (isValid) {
                                val limit = (creditLimitText.toLongOrNull() ?: 0L) * 100L
                                val equityBps = ((equityShareText.toDoubleOrNull() ?: 0.0) * 100.0).toInt()
                                onSubmit(name.trim(), phone.trim(), isCustomer, isVendor, isPartner, limit, equityBps)
                            }
                        },
                        enabled = isValid,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MikroTikPrimary,
                            disabledContainerColor = CyberDarkSurface
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("حفظ وتثبيت الطرف في النظام", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun ModernRoleSelectionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tintColor: Color,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onToggle)
            .border(
                BorderStroke(
                    width = if (isSelected) 1.5.dp else 1.dp,
                    color = if (isSelected) tintColor else CyberBorder
                ),
                shape = RoundedCornerShape(12.dp)
            ),
        color = if (isSelected) tintColor.copy(alpha = 0.12f) else CyberDarkCardElevated
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) tintColor.copy(alpha = 0.25f) else CyberDarkSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (isSelected) tintColor else TextSecondaryDark,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        title,
                        color = if (isSelected) tintColor else TextPrimaryDark,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        subtitle,
                        color = TextSecondaryDark,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) tintColor else Color.Transparent)
                    .border(
                        1.5.dp,
                        if (isSelected) tintColor else CyberBorder,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PartyModernTabPill(
    title: String,
    count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tintColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .border(
                1.dp,
                if (isSelected) tintColor else Color.Transparent,
                RoundedCornerShape(10.dp)
            ),
        color = if (isSelected) tintColor.copy(alpha = 0.18f) else Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (isSelected) tintColor else TextSecondaryDark,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                title,
                color = if (isSelected) tintColor else TextSecondaryDark,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (isSelected) tintColor.copy(alpha = 0.3f) else CyberDarkSurface)
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
                Text(
                    count.toString(),
                    color = if (isSelected) tintColor else TextMutedDark,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
