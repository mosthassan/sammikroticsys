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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.example.core.model.RateZone
import com.example.data.ledger.InKindAssetItem
import com.example.data.local.AppDatabase
import com.example.data.local.entity.DocumentEntity
import com.example.data.local.entity.PartyEntity
import com.example.data.local.entity.TreasuryAccountEntity
import com.example.data.network.NetworkConfig
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
    val currentAccountMinor by viewModel.getPartnerCurrentBalanceFlow(partner.id).collectAsState(initial = 0L)
    val partnerDocs by viewModel.getDocumentsByPartyFlow(partner.id).collectAsState(initial = emptyList())
    val treasuries by viewModel.allTreasuries.collectAsState(initial = emptyList())
    val org by viewModel.organization.collectAsState(initial = null)
    val networkConfig by viewModel.networkRepository.config.collectAsState()

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
                                    val modeLabel = if (org?.equityShareMode == "DERIVED_FROM_CAPITAL") "رأس المال" else "اتفاقية"
                                    Text("• حصة الأرباح ($modeLabel): $sharePct%", color = MikroTikCyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }

                    IconButton(onClick = onDismissRequest) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = TextSecondaryDark)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Dynamic Capital and Current Account Hero Cards
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Capital Account 3101
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = CyberDarkCardElevated),
                        border = BorderStroke(1.dp, CyberBorder),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("رأس المال التاريخي (3101)", color = TextSecondaryDark, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                Money(dynamicCapitalMinor, CurrencyCode.YER).format(),
                                color = StatusOnline,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text("مجمد بالتكلفة التاريخية (IAS 21)", color = TextMutedDark, fontSize = 9.sp)
                        }
                    }

                    // Current Account 3201
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = CyberDarkCardElevated),
                        border = BorderStroke(1.dp, CyberBorder),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("جاري الشريك (3201)", color = TextSecondaryDark, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                Money(currentAccountMinor, CurrencyCode.YER).format(),
                                color = if (currentAccountMinor >= 0) MikroTikCyan else SemanticExpenseRed,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text("أرباح موزعة ومسحوبات", color = TextMutedDark, fontSize = 9.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "سجل سندات مساهمات رأس المال (${contributionVouchers.size})",
                            color = TextPrimaryDark,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            "التاريخ • العملة الأصلية • سعر الصرف التاريخي • القيمة بالريال",
                            color = TextMutedDark,
                            fontSize = 10.sp
                        )
                    }

                    Button(
                        onClick = { showNewContributionDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MikroTikPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("مساهمة جديدة", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Vouchers Audit List (Historical Ledger)
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
                            val docCurr = CurrencyCode.fromString(doc.currency)

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
                                                    LocalDate.ofEpochDay(doc.dateEpochDay).toString() + " • " + if (isInKind) "مساهمة عينية (أصل)" else "مساهمة نقدية",
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
                                                    Money(doc.totalMinor, docCurr).format(),
                                                    color = TextSecondaryDark,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                    }

                                    // Display Historical Rate if Foreign Currency
                                    if (doc.currency != "YER") {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            "سعر الصرف التاريخي: ${ExchangeRate.formatRateMicros(doc.exchangeRateMicros)} YER (نطاق: ${doc.rateZone})",
                                            color = MikroTikCyan,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }

                                    if (doc.notes.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
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
            networkConfig = networkConfig,
            onSubmitCashWithZone = { treasuryId, amountOrigMinor, curr, rate, notes, zone ->
                viewModel.postCapitalReceipt(
                    partnerPartyId = partner.id,
                    treasuryId = treasuryId,
                    amountOrigMinor = amountOrigMinor,
                    currency = curr,
                    exchangeRate = rate,
                    notes = notes,
                    rateZone = zone,
                    onSuccess = { showNewContributionDialog = false }
                )
            },
            onSubmitInKindWithZone = { assetName, amountOrigMinor, curr, rate, usefulLife, notes, zone ->
                viewModel.postInKindCapitalContribution(
                    partnerPartyId = partner.id,
                    assetName = assetName,
                    amountOrigMinor = amountOrigMinor,
                    currency = curr,
                    exchangeRate = rate,
                    usefulLifeMonths = usefulLife,
                    notes = notes,
                    rateZone = zone,
                    onSuccess = { showNewContributionDialog = false }
                )
            },
            onSubmitMultiAssetInKindWithZone = { assets, curr, rate, notes, zone ->
                viewModel.postMultiAssetInKindCapitalContribution(
                    partnerPartyId = partner.id,
                    assets = assets,
                    currency = curr,
                    exchangeRate = rate,
                    notes = notes,
                    rateZone = zone,
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

data class InKindAssetDraft(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "",
    val quantityText: String = "1",
    val unitValueText: String = "",
    val usefulLifeMonthsText: String = "36"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewCapitalContributionDialog(
    partner: PartyEntity,
    treasuries: List<TreasuryAccountEntity>,
    resolver: ExchangeRateResolver? = null,
    networkConfig: NetworkConfig? = null,
    onSubmitCash: (treasuryId: String, amountOrigMinor: Long, curr: CurrencyCode, rate: ExchangeRate, notes: String) -> Unit = { _, _, _, _, _ -> },
    onSubmitInKind: (assetName: String, amountOrigMinor: Long, curr: CurrencyCode, rate: ExchangeRate, usefulLife: Int, notes: String) -> Unit = { _, _, _, _, _, _ -> },
    onSubmitCashWithZone: ((treasuryId: String, amountOrigMinor: Long, curr: CurrencyCode, rate: ExchangeRate, notes: String, rateZone: RateZone) -> Unit)? = null,
    onSubmitInKindWithZone: ((assetName: String, amountOrigMinor: Long, curr: CurrencyCode, rate: ExchangeRate, usefulLife: Int, notes: String, rateZone: RateZone) -> Unit)? = null,
    onSubmitMultiAssetInKindWithZone: ((assets: List<InKindAssetItem>, curr: CurrencyCode, rate: ExchangeRate, notes: String, rateZone: RateZone) -> Unit)? = null,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val effectiveConfig = networkConfig ?: remember {
        com.example.data.network.NetworkRepository(context).config.value
    }
    val effectiveResolver = resolver ?: remember { ExchangeRateResolver(AppDatabase.getInstance(context)) }
    val today = remember { com.example.core.time.AppClock.todayEpochDay() }
    val inheritedZone = effectiveConfig.rateZone
    var selectedZone by rememberSaveable(inheritedZone) { mutableStateOf(inheritedZone) }
    var contributionType by remember { mutableIntStateOf(0) } // 0: Cash, 1: In-Kind Asset

    // Cash fields
    var selectedTreasuryId by remember { mutableStateOf(treasuries.firstOrNull()?.id ?: "TR_MAIN_YER") }
    var treasuryExpanded by remember { mutableStateOf(false) }

    // Common fields
    var selectedCurrency by rememberSaveable { mutableStateOf(CurrencyCode.YER) }
    var cashAmountText by rememberSaveable { mutableStateOf("") }
    var exchangeRateText by rememberSaveable(selectedCurrency, selectedZone) { mutableStateOf("1.0") }
    var isRateManuallyEdited by rememberSaveable(selectedCurrency, selectedZone) { mutableStateOf(false) }
    var resolvedRate by remember { mutableStateOf<ExchangeRate?>(ExchangeRate.parity(CurrencyCode.FUNCTIONAL)) }
    var notesText by rememberSaveable { mutableStateOf("") }

    // Dynamic Multi-Asset In-Kind entries
    var inKindAssets by remember {
        mutableStateOf(
            listOf(
                InKindAssetDraft(
                    name = "",
                    quantityText = "1",
                    unitValueText = "",
                    usefulLifeMonthsText = "36"
                )
            )
        )
    }

    LaunchedEffect(selectedCurrency, selectedZone, effectiveConfig) {
        when (selectedCurrency) {
            CurrencyCode.YER -> {
                exchangeRateText = "1.0"
                resolvedRate = ExchangeRate.parity(CurrencyCode.FUNCTIONAL)
                isRateManuallyEdited = false
            }
            CurrencyCode.USD -> {
                val defaultRateMicros = if (selectedZone == effectiveConfig.rateZone && effectiveConfig.defaultUsdRateMicros > 0L) {
                    effectiveConfig.defaultUsdRateMicros
                } else {
                    effectiveResolver.resolveOrNull(CurrencyCode.USD, today, selectedZone)?.rateMicros
                        ?: if (selectedZone == RateZone.ADEN) 1_600_000_000L else 535_000_000L
                }
                exchangeRateText = ExchangeRate.formatRateMicros(defaultRateMicros)
                resolvedRate = ExchangeRate(CurrencyCode.USD, CurrencyCode.FUNCTIONAL, defaultRateMicros)
                isRateManuallyEdited = false
            }
            CurrencyCode.SAR -> {
                val defaultRateMicros = if (selectedZone == effectiveConfig.rateZone && effectiveConfig.defaultSarRateMicros > 0L) {
                    effectiveConfig.defaultSarRateMicros
                } else {
                    effectiveResolver.resolveOrNull(CurrencyCode.SAR, today, selectedZone)?.rateMicros
                        ?: if (selectedZone == RateZone.ADEN) 420_000_000L else 140_500_000L
                }
                exchangeRateText = ExchangeRate.formatRateMicros(defaultRateMicros)
                resolvedRate = ExchangeRate(CurrencyCode.SAR, CurrencyCode.FUNCTIONAL, defaultRateMicros)
                isRateManuallyEdited = false
            }
        }
    }

    val scrollState = rememberScrollState()

    // Calculations & Validations
    val parsedCashMoney = Money.parseFromUserInput(cashAmountText, selectedCurrency)
    val isCashAmountValid = (parsedCashMoney?.minor ?: 0L) > 0L

    val validInKindItems = remember(inKindAssets, selectedCurrency) {
        inKindAssets.mapNotNull { draft ->
            val qty = draft.quantityText.toIntOrNull() ?: 0
            val unitParsed = Money.parseFromUserInput(draft.unitValueText, selectedCurrency)
            val unitMinor = unitParsed?.minor ?: 0L
            val usefulLife = draft.usefulLifeMonthsText.toIntOrNull() ?: 36
            if (draft.name.isNotBlank() && qty > 0 && unitMinor > 0L) {
                InKindAssetItem(
                    name = draft.name.trim(),
                    quantity = qty,
                    unitValueMinor = unitMinor,
                    usefulLifeMonths = usefulLife
                )
            } else null
        }
    }

    val totalInKindMinor = remember(inKindAssets, selectedCurrency) {
        inKindAssets.sumOf { draft ->
            val qty = draft.quantityText.toIntOrNull() ?: 0
            val unitMinor = Money.parseFromUserInput(draft.unitValueText, selectedCurrency)?.minor ?: 0L
            if (qty > 0 && unitMinor > 0L) qty * unitMinor else 0L
        }
    }

    val parsedRateMicros = if (selectedCurrency == CurrencyCode.FUNCTIONAL) {
        ExchangeRate.SCALE_MICROS
    } else {
        if (exchangeRateText.isBlank()) 0L else (ExchangeRate.parseRateFromUserInput(exchangeRateText) ?: (resolvedRate?.rateMicros ?: 0L))
    }
    val isRateValid = parsedRateMicros > 0L
    val canSubmit = if (contributionType == 0) {
        isCashAmountValid && isRateValid
    } else {
        validInKindItems.isNotEmpty() && isRateValid
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.94f)
                .clip(RoundedCornerShape(22.dp))
                .border(BorderStroke(1.dp, CyberBorder), RoundedCornerShape(22.dp)),
            color = CyberDarkSurface
        ) {
            Column(modifier = Modifier.fillMaxHeight()) {
                // Fixed Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
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
                    }

                    IconButton(onClick = onDismissRequest) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = TextSecondaryDark)
                    }
                }

                HorizontalDivider(color = CyberBorder)

                // Safe Scrollable Content (No swipe dismiss when scrolling)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState)
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
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
                            label = "مساهمة عينية (أصول متعددة)",
                            icon = Icons.Default.Router,
                            isSelected = contributionType == 1,
                            onClick = { contributionType = 1 },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Currency Selector
                    Text("عملة المساهمة:", color = TextSecondaryDark, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    CurrencySelector(
                        selectedCurrency = selectedCurrency,
                        onCurrencySelected = { c ->
                            selectedCurrency = c
                            val matchingTreasury = treasuries.firstOrNull { it.currency == c.name }
                            if (matchingTreasury != null) {
                                selectedTreasuryId = matchingTreasury.id
                            }
                        }
                    )

                    if (contributionType == 0) {
                        // Treasury Selector
                        val matchingTreasuries = treasuries.filter { it.currency == selectedCurrency.name }
                        val effectiveTreasuries = if (matchingTreasuries.isNotEmpty()) matchingTreasuries else treasuries

                        ExposedDropdownMenuBox(
                            expanded = treasuryExpanded,
                            onExpandedChange = { treasuryExpanded = it },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = treasuries.find { it.id == selectedTreasuryId }?.let { "${it.name} (${it.currency})" } ?: selectedTreasuryId,
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
                                effectiveTreasuries.forEach { t ->
                                    DropdownMenuItem(
                                        text = { Text("${t.name} (${t.currency})") },
                                        onClick = {
                                            selectedTreasuryId = t.id
                                            treasuryExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // Cash Amount Field
                        OutlinedTextField(
                            value = cashAmountText,
                            onValueChange = { cashAmountText = it },
                            label = { Text("المبلغ النقدي بالعملة (${selectedCurrency.name})") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        // Multi-Asset Dynamic List Builder
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CyberDarkCardElevated),
                            border = BorderStroke(1.dp, MikroTikCyan.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("إجمالي الأصول العينية:", color = TextSecondaryDark, fontSize = 11.sp)
                                    Text(
                                        Money(totalInKindMinor, selectedCurrency).format(),
                                        color = StatusOnline,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 18.sp
                                    )
                                }
                                Surface(
                                    color = MikroTikPrimary.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, MikroTikCyan)
                                ) {
                                    Text(
                                        "${validInKindItems.size} أصل جاهز للترحيل",
                                        color = MikroTikCyan,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Text("بنود الأصول الثابتة الموردة:", color = TextPrimaryDark, fontWeight = FontWeight.Bold, fontSize = 13.sp)

                        // Dynamic Asset Items
                        inKindAssets.forEachIndexed { index, draft ->
                            val qty = draft.quantityText.toIntOrNull() ?: 0
                            val unitMinor = Money.parseFromUserInput(draft.unitValueText, selectedCurrency)?.minor ?: 0L
                            val itemTotal = if (qty > 0 && unitMinor > 0L) qty * unitMinor else 0L

                            Card(
                                colors = CardDefaults.cardColors(containerColor = CyberDarkCardElevated),
                                border = BorderStroke(1.dp, CyberBorder),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "الأصل #${index + 1}",
                                            color = MikroTikCyan,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (itemTotal > 0L) {
                                                Text(
                                                    "المجموع: ${Money(itemTotal, selectedCurrency).format()}",
                                                    color = StatusOnline,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                            }
                                            if (inKindAssets.size > 1) {
                                                IconButton(
                                                    onClick = {
                                                        inKindAssets = inKindAssets.toMutableList().also { it.removeAt(index) }
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Delete,
                                                        contentDescription = "حذف البند",
                                                        tint = SemanticExpenseRed,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Asset Name
                                    OutlinedTextField(
                                        value = draft.name,
                                        onValueChange = { newName ->
                                            inKindAssets = inKindAssets.toMutableList().also {
                                                it[index] = it[index].copy(name = newName)
                                            }
                                        },
                                        label = { Text("اسم الأصل (مثال: راوتر Mikrotik CCR2004، أنتينا ميمو)") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    // Row: Quantity, Unit Value, Depreciation Months
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = draft.quantityText,
                                            onValueChange = { newQty ->
                                                inKindAssets = inKindAssets.toMutableList().also {
                                                    it[index] = it[index].copy(quantityText = newQty)
                                                }
                                            },
                                            label = { Text("الكمية") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )

                                        OutlinedTextField(
                                            value = draft.unitValueText,
                                            onValueChange = { newVal ->
                                                inKindAssets = inKindAssets.toMutableList().also {
                                                    it[index] = it[index].copy(unitValueText = newVal)
                                                }
                                            },
                                            label = { Text("سعر الوحدة (${selectedCurrency.name})") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            singleLine = true,
                                            modifier = Modifier.weight(1.5f)
                                        )

                                        OutlinedTextField(
                                            value = draft.usefulLifeMonthsText,
                                            onValueChange = { newMonths ->
                                                inKindAssets = inKindAssets.toMutableList().also {
                                                    it[index] = it[index].copy(usefulLifeMonthsText = newMonths)
                                                }
                                            },
                                            label = { Text("الإهلاك (شهر)") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true,
                                            modifier = Modifier.weight(1.1f)
                                        )
                                    }
                                }
                            }
                        }

                        // Add Another Asset Button
                        OutlinedButton(
                            onClick = {
                                inKindAssets = inKindAssets + InKindAssetDraft(
                                    name = "",
                                    quantityText = "1",
                                    unitValueText = "",
                                    usefulLifeMonthsText = "36"
                                )
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MikroTikCyan),
                            border = BorderStroke(1.dp, MikroTikCyan.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("إضافة أصل ثابت آخر إلى السند", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    // Exchange Rate for Foreign Currencies (USD / SAR)
                    if (selectedCurrency != CurrencyCode.YER) {
                        val zoneNameArabic = if (selectedZone == RateZone.SANAA) "نطاق صنعاء" else "نطاق عدن"
                        ExchangeRateCard(
                            currency = selectedCurrency,
                            rate = resolvedRate,
                            zone = selectedZone,
                            onZoneToggle = {
                                selectedZone = if (selectedZone == RateZone.SANAA) RateZone.ADEN else RateZone.SANAA
                            }
                        )

                        OutlinedTextField(
                            value = exchangeRateText,
                            onValueChange = {
                                exchangeRateText = it
                                isRateManuallyEdited = true
                                val parsed = ExchangeRate.parseRateFromUserInput(it)
                                resolvedRate = if (parsed != null && parsed > 0L) {
                                    ExchangeRate(selectedCurrency, CurrencyCode.FUNCTIONAL, parsed)
                                } else null
                            },
                            label = { Text("سعر الصرف المعتمد ($zoneNameArabic):") },
                            placeholder = { Text("أدخل سعر الصرف...") },
                            singleLine = true,
                            trailingIcon = {
                                if (isRateManuallyEdited) {
                                    IconButton(
                                        onClick = {
                                            val defaultMicros = when (selectedCurrency) {
                                                CurrencyCode.USD -> {
                                                    if (selectedZone == effectiveConfig.rateZone && effectiveConfig.defaultUsdRateMicros > 0L) {
                                                        effectiveConfig.defaultUsdRateMicros
                                                    } else {
                                                        if (selectedZone == RateZone.ADEN) 1_600_000_000L else 535_000_000L
                                                    }
                                                }
                                                CurrencyCode.SAR -> {
                                                    if (selectedZone == effectiveConfig.rateZone && effectiveConfig.defaultSarRateMicros > 0L) {
                                                        effectiveConfig.defaultSarRateMicros
                                                    } else {
                                                        if (selectedZone == RateZone.ADEN) 420_000_000L else 140_500_000L
                                                    }
                                                }
                                                else -> ExchangeRate.SCALE_MICROS
                                            }
                                            exchangeRateText = ExchangeRate.formatRateMicros(defaultMicros)
                                            resolvedRate = ExchangeRate(selectedCurrency, CurrencyCode.FUNCTIONAL, defaultMicros)
                                            isRateManuallyEdited = false
                                        }
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = "استعادة السعر الافتراضي", tint = MikroTikCyan)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Notes / Memo
                    OutlinedTextField(
                        value = notesText,
                        onValueChange = { notesText = it },
                        label = { Text("ملاحظات وبيان السند") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                HorizontalDivider(color = CyberBorder)

                // Sticky Bottom Action Bar (Protected against keyboard and insets)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            require(canSubmit) { "الرجاء إكمال البيانات بشكل صحيح" }

                            val rate = if (selectedCurrency == CurrencyCode.FUNCTIONAL) {
                                ExchangeRate.parity(CurrencyCode.FUNCTIONAL)
                            } else {
                                ExchangeRate(selectedCurrency, CurrencyCode.FUNCTIONAL, parsedRateMicros)
                            }

                            if (contributionType == 0) {
                                val amtMinor = parsedCashMoney!!.minor
                                val finalNotes = if (notesText.isBlank()) "مساهمة نقدية برأس المال" else notesText
                                if (onSubmitCashWithZone != null) {
                                    onSubmitCashWithZone(selectedTreasuryId, amtMinor, selectedCurrency, rate, finalNotes, selectedZone)
                                } else {
                                    onSubmitCash(selectedTreasuryId, amtMinor, selectedCurrency, rate, finalNotes)
                                }
                            } else {
                                val summary = validInKindItems.joinToString("، ") { "${it.name} (${it.quantity})" }
                                val finalNotes = if (notesText.isBlank()) "مساهمة عينية رأسمالية: $summary" else notesText

                                if (onSubmitMultiAssetInKindWithZone != null) {
                                    onSubmitMultiAssetInKindWithZone(validInKindItems, selectedCurrency, rate, finalNotes, selectedZone)
                                } else if (validInKindItems.size == 1 && onSubmitInKindWithZone != null) {
                                    val item = validInKindItems[0]
                                    onSubmitInKindWithZone(item.name, item.totalMinor, selectedCurrency, rate, item.usefulLifeMonths, finalNotes, selectedZone)
                                } else if (validInKindItems.size == 1) {
                                    val item = validInKindItems[0]
                                    onSubmitInKind(item.name, item.totalMinor, selectedCurrency, rate, item.usefulLifeMonths, finalNotes)
                                } else {
                                    val totalMinor = validInKindItems.sumOf { it.totalMinor }
                                    onSubmitInKind(summary, totalMinor, selectedCurrency, rate, 36, finalNotes)
                                }
                            }
                        },
                        enabled = canSubmit,
                        colors = ButtonDefaults.buttonColors(containerColor = MikroTikPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Text(
                            if (contributionType == 0) {
                                "ترحيل سند المساهمة النقدية (DR 1101 / CR 3101)"
                            } else {
                                "ترحيل سند المساهمة العينية (${validInKindItems.size} أصول - ${Money(totalInKindMinor, selectedCurrency).format()}) (DR 1501 / CR 3101)"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    TextButton(
                        onClick = onDismissRequest,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                    ) {
                        Text("إلغاء", color = TextSecondaryDark, fontSize = 13.sp)
                    }
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
