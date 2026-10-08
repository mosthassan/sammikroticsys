package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.core.model.CurrencyCode
import com.example.core.model.Money
import com.example.data.local.entity.PartyEntity
import com.example.domain.usecase.StatementOfAccountReport
import com.example.ui.components.AddEditPartyDialog
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberDarkCanvas
import com.example.ui.theme.CyberDarkCardElevated
import com.example.ui.theme.CyberDarkSurface
import com.example.ui.theme.MikroTikCyan
import com.example.ui.theme.MikroTikPrimary
import com.example.ui.theme.SemanticExpenseRed
import com.example.ui.theme.SemanticIncomeGreen
import com.example.ui.theme.TextMutedDark
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark
import com.example.util.PdfDocumentGenerator
import com.example.util.WhatsAppDispatcher
import java.io.File
import androidx.compose.material.icons.filled.PictureAsPdf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartyLedgerScreen(
    party: PartyEntity,
    report: StatementOfAccountReport?,
    onBack: () -> Unit,
    onEditParty: ((PartyEntity) -> Unit)? = null
) {
    val context = LocalContext.current
    var showEditDialog by remember { mutableStateOf(false) }
    var previewPdfFile by remember { mutableStateOf<File?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "ملف وحساب الطرف",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = TextPrimaryDark
                        )
                        Text(
                            text = party.name,
                            fontSize = 12.sp,
                            color = MikroTikCyan
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع",
                            tint = TextPrimaryDark
                        )
                    }
                },
                actions = {
                    if (onEditParty != null) {
                        IconButton(onClick = { showEditDialog = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "تعديل الطرف", tint = MikroTikCyan)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CyberDarkCardElevated)
            )
        },
        containerColor = CyberDarkCanvas
    ) { innerPadding ->
        PartyLedgerBody(
            party = party,
            report = report,
            context = context,
            onOpenEdit = { showEditDialog = true },
            onOpenPdf = { previewPdfFile = it },
            modifier = Modifier.padding(innerPadding)
        )
    }

    previewPdfFile?.let { pdfFile ->
        PdfPreviewDialog(
            pdfFile = pdfFile,
            title = "كشف حساب ${party.name}",
            onDismiss = { previewPdfFile = null }
        )
    }

    if (showEditDialog && onEditParty != null) {
        AddEditPartyDialog(
            partyToEdit = party,
            onDismiss = { showEditDialog = false },
            onSubmit = { name, phone, isCust, isVend, isPart, limit, eqBps ->
                val updated = party.copy(
                    name = name,
                    phone = phone,
                    isCustomer = isCust,
                    isVendor = isVend,
                    isPartner = isPart,
                    creditLimitMinor = limit,
                    equityPercentageBasisPoints = eqBps
                )
                onEditParty(updated)
                showEditDialog = false
            }
        )
    }
}

@Composable
fun PartyLedgerDialog(
    party: PartyEntity,
    report: StatementOfAccountReport?,
    onDismiss: () -> Unit,
    onEditParty: ((PartyEntity) -> Unit)? = null
) {
    val context = LocalContext.current
    var showEditDialog by remember { mutableStateOf(false) }
    var previewPdfFile by remember { mutableStateOf<File?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxSize(0.92f)
                .clip(RoundedCornerShape(20.dp))
                .border(BorderStroke(1.dp, CyberBorder), RoundedCornerShape(20.dp)),
            color = CyberDarkCanvas
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CyberDarkCardElevated)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MikroTikPrimary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (party.isCustomer) Icons.Default.Store else Icons.Default.Person,
                                contentDescription = null,
                                tint = MikroTikCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = party.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = TextPrimaryDark
                            )
                            Text(
                                text = "كشف حساب الأستاذ المالي المعتمد",
                                fontSize = 11.sp,
                                color = TextSecondaryDark
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (onEditParty != null) {
                            IconButton(onClick = { showEditDialog = true }) {
                                Icon(Icons.Default.Edit, contentDescription = "تعديل", tint = MikroTikCyan)
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = TextSecondaryDark)
                        }
                    }
                }

                HorizontalDivider(color = CyberBorder)

                PartyLedgerBody(
                    party = party,
                    report = report,
                    context = context,
                    onOpenEdit = { showEditDialog = true },
                    onOpenPdf = { previewPdfFile = it },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    previewPdfFile?.let { pdfFile ->
        PdfPreviewDialog(
            pdfFile = pdfFile,
            title = "كشف حساب ${party.name}",
            onDismiss = { previewPdfFile = null }
        )
    }

    if (showEditDialog && onEditParty != null) {
        AddEditPartyDialog(
            partyToEdit = party,
            onDismiss = { showEditDialog = false },
            onSubmit = { name, phone, isCust, isVend, isPart, limit, eqBps ->
                val updated = party.copy(
                    name = name,
                    phone = phone,
                    isCustomer = isCust,
                    isVendor = isVend,
                    isPartner = isPart,
                    creditLimitMinor = limit,
                    equityPercentageBasisPoints = eqBps
                )
                onEditParty(updated)
                showEditDialog = false
            }
        )
    }
}

@Composable
private fun PartyLedgerBody(
    party: PartyEntity,
    report: StatementOfAccountReport?,
    context: Context,
    onOpenEdit: () -> Unit,
    onOpenPdf: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    val curr = CurrencyCode.FUNCTIONAL

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Party Profile & Direct WhatsApp Dispatch Card
        Card(
            colors = CardDefaults.cardColors(containerColor = CyberDarkSurface),
            border = BorderStroke(1.dp, CyberBorder),
            shape = RoundedCornerShape(14.dp),
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
                    Column {
                        Text(party.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimaryDark)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (party.isCustomer) RoleChip("وكيل/بقالة", SemanticIncomeGreen)
                            if (party.isVendor) RoleChip("مورد", Color(0xFF3B82F6))
                            if (party.isPartner) RoleChip("شريك", Color(0xFFF59E0B))
                        }
                    }

                    if (party.creditLimitMinor > 0L) {
                        Surface(
                            color = MikroTikCyan.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, MikroTikCyan.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = "حد ائتماني: ${Money(party.creditLimitMinor, curr).format()}",
                                fontSize = 10.sp,
                                color = MikroTikCyan,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = CyberBorder.copy(alpha = 0.5f))

                // Phone Row with Prominent Direct WhatsApp Icon
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = null,
                            tint = MikroTikCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (party.phone.isNotBlank()) party.phone else "لا يوجد رقم مسجل",
                            fontSize = 13.sp,
                            color = if (party.phone.isNotBlank()) TextPrimaryDark else TextMutedDark,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium
                        )

                        // DIRECT WHATSAPP ICON NEXT TO PHONE NUMBER
                        if (party.phone.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF25D366).copy(alpha = 0.18f),
                                border = BorderStroke(1.dp, Color(0xFF25D366).copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .clickable {
                                        WhatsAppDispatcher.sendTextMessage(
                                            context,
                                            party.phone,
                                            "مرحباً ${party.name}، بخصوص حسابك في شبكة SamMikrotik."
                                        )
                                    }
                                    .testTag("whatsapp_party_btn")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Send,
                                        contentDescription = "مراسلة واتساب مباشرة",
                                        tint = Color(0xFF25D366),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "واتساب مباشر",
                                        fontSize = 11.sp,
                                        color = Color(0xFF25D366),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    if (party.phone.isNotBlank()) {
                        IconButton(
                            onClick = {
                                val callIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${party.phone}"))
                                context.startActivity(callIntent)
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Phone, contentDescription = "اتصال", tint = TextSecondaryDark, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // 2. Financial Summary Highlights & WhatsApp Statement Action Button
        report?.let { rep ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CyberDarkCardElevated),
                    border = BorderStroke(1.dp, CyberBorder),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("الرصيد الافتتاحي", fontSize = 10.sp, color = TextSecondaryDark)
                        Text(
                            Money(rep.openingBalanceMinor, curr).format(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryDark,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = CyberDarkCardElevated),
                    border = BorderStroke(1.5.dp, if (rep.closingBalanceMinor > 0L) SemanticIncomeGreen else MikroTikCyan),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1.2f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("الرصيد الجاري المستحق", fontSize = 10.sp, color = TextSecondaryDark)
                        Text(
                            Money(rep.closingBalanceMinor, curr).format(),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = if (rep.closingBalanceMinor > 0L) SemanticIncomeGreen else TextPrimaryDark,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Action Buttons: PDF Statement & WhatsApp Statement Dispatch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Branded PDF Statement Button
                Button(
                    onClick = {
                        val pdfFile = PdfDocumentGenerator.generateAgentStatementPdf(
                            context = context,
                            report = rep,
                            party = party
                        )
                        onOpenPdf(pdfFile)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .testTag("btn_generate_pdf_statement")
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "كشف حساب PDF",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color.White
                    )
                }

                // Direct WhatsApp Statement Dispatch Button
                Button(
                    onClick = {
                        val openingStr = Money(rep.openingBalanceMinor, curr).format()
                        val closingStr = Money(rep.closingBalanceMinor, curr).format()
                        WhatsAppDispatcher.dispatchStatementSummary(
                            context = context,
                            party = party,
                            openingBalanceText = openingStr,
                            closingBalanceText = closingStr,
                            transactionCount = rep.items.size
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1.3f)
                        .height(42.dp)
                        .testTag("btn_dispatch_statement_whatsapp")
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "إرسال عبر واتساب",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color.Black
                    )
                }
            }
        }

        // 3. Ledger Transactions List
        Card(
            colors = CardDefaults.cardColors(containerColor = CyberDarkSurface),
            border = BorderStroke(1.dp, CyberBorder),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                Text(
                    text = "سجل القيود وحركات الأستاذ (IFRS Ledger)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimaryDark
                )
                Spacer(modifier = Modifier.height(6.dp))
                HorizontalDivider(color = CyberBorder)
                Spacer(modifier = Modifier.height(6.dp))

                if (report == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("جاري استخراج أسطر الأستاذ...", color = TextSecondaryDark, fontSize = 12.sp)
                    }
                } else if (report.items.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("لا توجد حركات مسجلة لهذا الطرف في دفتر الأستاذ", color = TextMutedDark, fontSize = 12.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(report.items) { itm ->
                            Surface(
                                color = CyberDarkCardElevated,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(0.5.dp, CyberBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${itm.docType} #${itm.docNumber}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimaryDark
                                        )
                                        Text(
                                            text = itm.memo,
                                            fontSize = 10.sp,
                                            color = TextSecondaryDark
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        if (itm.baseDebitMinor > 0L) {
                                            Text(
                                                text = "مدين: +${Money(itm.baseDebitMinor, curr).format()}",
                                                color = SemanticIncomeGreen,
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                        if (itm.baseCreditMinor > 0L) {
                                            Text(
                                                text = "دائن: -${Money(itm.baseCreditMinor, curr).format()}",
                                                color = SemanticExpenseRed,
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                        Text(
                                            text = "الرصيد: ${Money(itm.runningBalanceMinor, curr).format()}",
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = MikroTikCyan,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
