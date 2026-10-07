package com.example.ui.components

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Router
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.core.model.CurrencyCode
import com.example.core.model.ExchangeRate
import com.example.core.model.Money
import com.example.data.local.AppDatabase
import com.example.data.local.entity.DocumentEntity
import com.example.data.local.entity.PartyEntity
import com.example.data.local.entity.TreasuryAccountEntity
import com.example.domain.usecase.ExchangeRateResolver
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberDarkCardElevated
import com.example.ui.theme.CyberDarkSurface
import com.example.ui.theme.MikroTikCyan
import com.example.ui.theme.MikroTikPrimary
import com.example.ui.theme.SemanticExpenseRed
import com.example.ui.theme.SemanticIncomeGreen
import com.example.ui.theme.StatusOnline
import com.example.ui.theme.TextMutedDark
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark
import com.example.ui.viewmodel.AppViewModel
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartnerDetailDialog(
    partner: PartyEntity,
    viewModel: AppViewModel,
    onDismissRequest: () -> Unit
) {
    val dynamicCapitalMinor by viewModel.getPartnerCapitalBalanceFlow(partner.id).collectAsState(initial = 0L)
    val partnerDocs by viewModel.getDocumentsByPartyFlow(partner.id).collectAsState(initial = emptyList())
    val treasuries by viewModel.allTreasuries.collectAsState(initial = emptyList())

    var showNewContributionDialog by remember { mutableStateOf(false) }
    var docToVoid by remember { mutableStateOf<DocumentEntity?>(null) }
    var voidReason by remember { mutableStateOf("خطأ في البيانات المحاسبية") }

    val contributionVouchers = remember(partnerDocs) {
        partnerDocs.filter { it.type == "RECEIPT_VOUCHER" }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 720.dp)
                .clip(RoundedCornerShape(20.dp))
                .border(BorderStroke(1.dp, CyberBorder), RoundedCornerShape(20.dp)),
            color = CyberDarkSurface
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Header with Partner Info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MikroTikPrimary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Handshake, contentDescription = null, tint = MikroTikCyan, modifier = Modifier.size(24.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(partner.name, color = TextPrimaryDark, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(if (partner.phone.isNotBlank()) partner.phone else "بدون رقم هاتف", color = TextSecondaryDark, fontSize = 12.sp)
                                val sharePct = partner.equityPercentageBasisPoints / 100.0
                                if (sharePct > 0) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("• نسبة الأرباح: $sharePct%", color = MikroTikCyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }

                    IconButton(onClick = onDismissRequest) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = TextSecondaryDark)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Dynamic Capital Balance Hero Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CyberDarkCardElevated),
                    border = BorderStroke(1.dp, CyberBorder),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("رأس المال المساهم به حالياً (3101)", color = TextSecondaryDark, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                Money(dynamicCapitalMinor, CurrencyCode.YER).format(),
                                color = StatusOnline,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text("محسوب ديناميكياً من دفتر الأستاذ العام", color = TextMutedDark, fontSize = 10.sp)
                        }

                        Button(
                            onClick = { showNewContributionDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MikroTikPrimary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("سند مساهمة جديد", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "سجل سندات مساهمات رأس المال (${contributionVouchers.size})",
                    color = TextPrimaryDark,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    "جميع الحركات مسجلة كقيود يومية مزدوجة ومحمية من التعديل العشوائي",
                    color = TextMutedDark,
                    fontSize = 11.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Vouchers Audit List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (contributionVouchers.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("لا توجد سندات مساهمة مسجلة لهذا الشريك بعد", color = TextMutedDark, fontSize = 13.sp)
                            }
                        }
                    } else {
                        items(contributionVouchers) { doc ->
                            val isVoided = doc.status == "VOIDED"
                            val isInKind = doc.notes.contains("عينية") || doc.notes.contains("أصل")

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isVoided) CyberDarkCardElevated.copy(alpha = 0.5f) else CyberDarkCardElevated
                                ),
                                border = BorderStroke(1.dp, if (isVoided) SemanticExpenseRed.copy(alpha = 0.4f) else CyberBorder),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isInKind) MikroTikCyan.copy(alpha = 0.15f) else StatusOnline.copy(alpha = 0.15f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    if (isInKind) Icons.Default.Router else Icons.Default.Payments,
                                                    contentDescription = null,
                                                    tint = if (isInKind) MikroTikCyan else StatusOnline,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    "سند مساهمة #${doc.docNumber}",
                                                    color = TextPrimaryDark,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                                Text(
                                                    LocalDate.ofEpochDay(doc.dateEpochDay).toString() + " • " + if (isInKind) "مساهمة عينية (أصل ثابت)" else "مساهمة نقدية",
                                                    color = TextSecondaryDark,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }

                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                Money(doc.totalBaseMinor, CurrencyCode.YER).format(),
                                                color = if (isVoided) TextMutedDark else StatusOnline,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                            if (doc.currency != "YER") {
                                                Text(
                                                    "${doc.totalMinor / 100.0} ${doc.currency}",
                                                    color = TextSecondaryDark,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                    }

                                    if (doc.notes.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text("البيان: ${doc.notes}", color = TextMutedDark, fontSize = 11.sp)
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    HorizontalDivider(color = CyberBorder.copy(alpha = 0.5f))
                                    Spacer(modifier = Modifier.height(6.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            if (isVoided) "ملغي بقيد عكسي تعويضي" else "مرحل في الأستاذ العام (3101)",
                                            color = if (isVoided) SemanticExpenseRed else StatusOnline,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )

                                        if (!isVoided) {
                                            TextButton(
                                                onClick = { docToVoid = doc },
                                                colors = ButtonDefaults.textButtonColors(contentColor = SemanticExpenseRed)
                                            ) {
                                                Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("إلغاء السند (قيد عكسي)", fontSize = 11.sp)
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
    }

    // New Contribution Dialog
    if (showNewContributionDialog) {
        NewCapitalContributionDialog(
            partner = partner,
            treasuries = treasuries,
            resolver = viewModel.exchangeRateResolver,
            onSubmitCash = { treasuryId, amountOrigMinor, curr, rate, notes ->
                viewModel.postCapitalReceipt(
                    partnerPartyId = partner.id,
                    treasuryId = treasuryId,
                    amountOrigMinor = amountOrigMinor,
                    currency = curr,
                    exchangeRate = rate,
                    notes = notes,
                    onSuccess = { showNewContributionDialog = false }
                )
            },
            onSubmitInKind = { assetName, amountOrigMinor, curr, rate, usefulLife, notes ->
                viewModel.postInKindCapitalContribution(
                    partnerPartyId = partner.id,
                    assetName = assetName,
                    amountOrigMinor = amountOrigMinor,
                    currency = curr,
                    exchangeRate = rate,
                    usefulLifeMonths = usefulLife,
                    notes = notes,
                    onSuccess = { showNewContributionDialog = false }
                )
            },
            onDismissRequest = { showNewContributionDialog = false }
        )
    }

    // Void / Reversal Confirmation Dialog
    docToVoid?.let { doc ->
        AlertDialog(
            onDismissRequest = { docToVoid = null },
            title = { Text("إلغاء سند مساهمة رأس المال #${doc.docNumber}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "تطبيقاً للمعايير المحاسبية الصارمة، لن يتم حذف السند فيزيائياً بل سيتم توليد قيد يومية عكسي تعويضي فوري يعيد رأس مال الشريك وحسابات الأستاذ إلى حالتها السابقة بدقة 100%.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = voidReason,
                        onValueChange = { voidReason = it },
                        label = { Text("سبب الإلغاء") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.voidDocument(doc.id, voidReason) {
                            docToVoid = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SemanticExpenseRed)
                ) {
                    Text("تأكيد الإلغاء والقيد العكسي")
                }
            },
            dismissButton = {
                TextButton(onClick = { docToVoid = null }) {
                    Text("تراجع")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewCapitalContributionDialog(
    partner: PartyEntity,
    treasuries: List<TreasuryAccountEntity>,
    resolver: ExchangeRateResolver? = null,
    onSubmitCash: (treasuryId: String, amountOrigMinor: Long, curr: CurrencyCode, rate: ExchangeRate, notes: String) -> Unit,
    onSubmitInKind: (assetName: String, amountOrigMinor: Long, curr: CurrencyCode, rate: ExchangeRate, usefulLife: Int, notes: String) -> Unit,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val effectiveResolver = resolver ?: remember { ExchangeRateResolver(AppDatabase.getInstance(context)) }
    val today = remember { java.time.LocalDate.now().toEpochDay() }
    var contributionType by remember { mutableIntStateOf(0) } // 0: Cash, 1: In-Kind Asset

    // Cash fields
    var selectedTreasuryId by remember { mutableStateOf(treasuries.firstOrNull()?.id ?: "TR_MAIN_YER") }
    var treasuryExpanded by remember { mutableStateOf(false) }

    // Common fields
    var selectedCurrency by remember { mutableStateOf(CurrencyCode.YER) }
    var currencyExpanded by remember { mutableStateOf(false) }
    var amountText by remember { mutableStateOf("") }
    var exchangeRateText by remember { mutableStateOf("") }
    var resolvedRate by remember { mutableStateOf<ExchangeRate?>(null) }
    var notesText by remember { mutableStateOf("") }

    LaunchedEffect(selectedCurrency) {
        if (selectedCurrency == CurrencyCode.FUNCTIONAL) {
            exchangeRateText = "1"
            resolvedRate = ExchangeRate.parity(CurrencyCode.FUNCTIONAL)
        } else {
            try {
                val r = effectiveResolver.resolve(selectedCurrency, today)
                resolvedRate = r
                exchangeRateText = ExchangeRate.formatRateMicros(r.rateMicros)
            } catch (e: Exception) {
                resolvedRate = null
                exchangeRateText = ""
            }
        }
    }

    // In-Kind fields
    var assetNameText by remember { mutableStateOf("") }
    var usefulLifeText by remember { mutableStateOf("36") }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(20.dp))
                .border(BorderStroke(1.dp, CyberBorder), RoundedCornerShape(20.dp)),
            color = CyberDarkSurface
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "سند مساهمة رأس مال جديد",
                    color = TextPrimaryDark,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
                Text(
                    "الشريك: ${partner.name}",
                    color = MikroTikCyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Contribution Type Selector
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PartnerTabPill(
                        label = "مساهمة نقدية (سيولة للخزينة)",
                        icon = Icons.Default.Payments,
                        isSelected = contributionType == 0,
                        onClick = { contributionType = 0 },
                        modifier = Modifier.weight(1f)
                    )
                    PartnerTabPill(
                        label = "مساهمة عينية (أصل ثابت)",
                        icon = Icons.Default.Router,
                        isSelected = contributionType == 1,
                        onClick = { contributionType = 1 },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (contributionType == 0) {
                    // Treasury Selector
                    ExposedDropdownMenuBox(
                        expanded = treasuryExpanded,
                        onExpandedChange = { treasuryExpanded = it },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = treasuries.find { it.id == selectedTreasuryId }?.name ?: selectedTreasuryId,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("إيداع إلى الخزينة / الصندوق (1101)") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = treasuryExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = treasuryExpanded,
                            onDismissRequest = { treasuryExpanded = false }
                        ) {
                            treasuries.forEach { t ->
                                DropdownMenuItem(
                                    text = { Text("${t.name} (${t.currency})") },
                                    onClick = {
                                        selectedTreasuryId = t.id
                                        selectedCurrency = runCatching { CurrencyCode.valueOf(t.currency) }.getOrDefault(CurrencyCode.YER)
                                        treasuryExpanded = false
                                    }
                                )
                            }
                        }
                    }
                } else {
                    // Asset Info
                    OutlinedTextField(
                        value = assetNameText,
                        onValueChange = { assetNameText = it },
                        label = { Text("اسم الأصل الثابت (مثال: برج اتصالات، راوتر CCR2004)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = usefulLifeText,
                        onValueChange = { usefulLifeText = it },
                        label = { Text("العمر الافتراضي للإهلاك (بالأشهر)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Currency & Amount
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExposedDropdownMenuBox(
                        expanded = currencyExpanded,
                        onExpandedChange = { currencyExpanded = it },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = selectedCurrency.name,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("العملة") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = currencyExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = currencyExpanded,
                            onDismissRequest = { currencyExpanded = false }
                        ) {
                            CurrencyCode.values().forEach { c ->
                                DropdownMenuItem(
                                    text = { Text(c.name) },
                                    onClick = {
                                        selectedCurrency = c
                                        currencyExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text(if (contributionType == 0) "المبلغ النقدي" else "القيمة التقديرية للأصل") },
                        modifier = Modifier.weight(2f)
                    )
                }

                if (selectedCurrency != CurrencyCode.YER) {
                    Spacer(modifier = Modifier.height(8.dp))
                    ExchangeRateCard(
                        currency = selectedCurrency,
                        rate = resolvedRate
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = exchangeRateText,
                        onValueChange = {
                            exchangeRateText = it
                            val parsed = ExchangeRate.parseRateFromUserInput(it)
                            if (parsed != null && parsed > 0L) {
                                resolvedRate = ExchangeRate(selectedCurrency, CurrencyCode.FUNCTIONAL, parsed)
                            }
                        },
                        label = { Text("سعر الصرف المعتمد مقابل الريال اليمني (YER)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text("ملاحظات وبيان السند") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                val parsedMoney = Money.parseFromUserInput(amountText, selectedCurrency)
                val isAmountValid = (parsedMoney?.minor ?: 0L) > 0L
                val parsedRateMicros = if (selectedCurrency == CurrencyCode.FUNCTIONAL) {
                    ExchangeRate.SCALE_MICROS
                } else {
                    ExchangeRate.parseRateFromUserInput(exchangeRateText) ?: (resolvedRate?.rateMicros ?: 0L)
                }
                val isRateValid = parsedRateMicros > 0L
                val isInKindValid = contributionType == 0 || assetNameText.isNotBlank()

                Button(
                    onClick = {
                        val amtMinor = parsedMoney!!.minor
                        val rate = if (selectedCurrency == CurrencyCode.FUNCTIONAL) {
                            ExchangeRate.parity(CurrencyCode.FUNCTIONAL)
                        } else {
                            ExchangeRate(selectedCurrency, CurrencyCode.FUNCTIONAL, parsedRateMicros)
                        }

                        if (contributionType == 0) {
                            onSubmitCash(
                                selectedTreasuryId,
                                amtMinor,
                                selectedCurrency,
                                rate,
                                if (notesText.isBlank()) "مساهمة نقدية برأس المال" else notesText
                            )
                        } else {
                            val months = usefulLifeText.toIntOrNull() ?: 36
                            onSubmitInKind(
                                assetNameText.trim(),
                                amtMinor,
                                selectedCurrency,
                                rate,
                                months,
                                if (notesText.isBlank()) "مساهمة عينية: ${assetNameText.trim()}" else notesText
                            )
                        }
                    },
                    enabled = isAmountValid && isRateValid && isInKindValid,
                    colors = ButtonDefaults.buttonColors(containerColor = MikroTikPrimary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (contributionType == 0) "ترحيل سند المساهمة النقدية (DR 1101 / CR 3101)" else "ترحيل سند المساهمة العينية (DR 1501 / CR 3101)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun PartnerTabPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
                if (isSelected) MikroTikCyan else CyberBorder,
                RoundedCornerShape(10.dp)
            ),
        color = if (isSelected) MikroTikPrimary.copy(alpha = 0.2f) else CyberDarkCardElevated
    ) {
        Row(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (isSelected) MikroTikCyan else TextSecondaryDark,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                label,
                color = if (isSelected) TextPrimaryDark else TextSecondaryDark,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 12.sp
            )
        }
    }
}
