package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.core.model.CurrencyCode
import com.example.core.model.ExchangeRate
import com.example.core.model.Money
import com.example.data.ledger.PurchaseItemSpec
import com.example.data.local.entity.PartyEntity
import com.example.data.local.entity.TreasuryAccountEntity
import com.example.data.network.NetworkDevice
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberDarkCardElevated
import com.example.ui.theme.CyberDarkSurface
import com.example.ui.theme.MikroTikCyan
import com.example.ui.theme.MikroTikPrimary
import com.example.ui.theme.StatusOffline
import com.example.ui.theme.StatusOnline
import com.example.ui.theme.TextMutedDark
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark
import kotlin.math.roundToLong
import com.example.util.DataJsonHelper

@Composable
fun PartyJsonBackupDialog(
    parties: List<PartyEntity>,
    onImport: (List<PartyEntity>) -> Unit,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    var activeTab by remember { mutableStateOf(0) } // 0: Export, 1: Import
    val exportedJson = remember(parties) { DataJsonHelper.exportPartiesToJson(parties) }
    var inputJson by remember { mutableStateOf("") }
    var parsedPreview by remember { mutableStateOf<List<PartyEntity>?>(null) }
    var parseError by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 680.dp)
                .clip(RoundedCornerShape(20.dp))
                .border(BorderStroke(1.dp, CyberBorder), RoundedCornerShape(20.dp)),
            color = CyberDarkSurface
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MikroTikPrimary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.People, contentDescription = null, tint = MikroTikCyan, modifier = Modifier.size(22.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("تصدير واستيراد العملاء والبقالات", color = TextPrimaryDark, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("نقل ومشاركة بيانات الوكلاء كملف JSON", color = TextSecondaryDark, fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Tabs
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TabPill(
                        label = "تصدير (${parties.size} طرف)",
                        icon = Icons.Default.FileUpload,
                        isSelected = activeTab == 0,
                        onClick = { activeTab = 0 },
                        modifier = Modifier.weight(1f)
                    )
                    TabPill(
                        label = "استيراد JSON",
                        icon = Icons.Default.FileDownload,
                        isSelected = activeTab == 1,
                        onClick = { activeTab = 1 },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (activeTab == 0) {
                    // Export Tab
                    Text("بيانات العملاء والبقالات الحالية المنسقة:", color = TextSecondaryDark, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        colors = CardDefaults.cardColors(containerColor = CyberDarkCardElevated),
                        border = BorderStroke(1.dp, CyberBorder)
                    ) {
                        LazyColumn(modifier = Modifier.padding(12.dp)) {
                            item {
                                Text(exportedJson, color = TextPrimaryDark, fontSize = 11.sp, lineHeight = 16.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("Parties JSON", exportedJson))
                                Toast.makeText(context, "تم نسخ بيانات العملاء إلى الحافظة بنجاح!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MikroTikPrimary)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("نسخ JSON")
                        }
                        OutlinedButton(
                            onClick = {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "نسخة احتياطية لعملاء شبكة سام ميكروتك")
                                    putExtra(Intent.EXTRA_TEXT, exportedJson)
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "مشاركة بيانات العملاء"))
                            },
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, CyberBorder)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = MikroTikCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("مشاركة", color = MikroTikCyan)
                        }
                    }
                } else {
                    // Import Tab
                    OutlinedTextField(
                        value = inputJson,
                        onValueChange = {
                            inputJson = it
                            parseError = null
                            parsedPreview = null
                            if (it.isNotBlank()) {
                                try {
                                    val list = DataJsonHelper.parsePartiesFromJson(it)
                                    parsedPreview = list
                                } catch (e: Exception) {
                                    parseError = "صيغة JSON غير صحيحة: ${e.localizedMessage}"
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        placeholder = { Text("الصق كود JSON للعملاء هنا...", color = TextMutedDark, fontSize = 12.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MikroTikCyan,
                            unfocusedBorderColor = CyberBorder,
                            focusedTextColor = TextPrimaryDark,
                            unfocusedTextColor = TextPrimaryDark,
                            focusedContainerColor = CyberDarkCardElevated,
                            unfocusedContainerColor = CyberDarkCardElevated
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    parseError?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                    }

                    parsedPreview?.let { preview ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "تم التعرف على (${preview.size}) عميل/بقالة جاهزة للإضافة",
                            color = StatusOnline,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            parsedPreview?.let {
                                if (it.isNotEmpty()) {
                                    onImport(it)
                                    Toast.makeText(context, "تم استيراد ${it.size} عميل بنجاح!", Toast.LENGTH_SHORT).show()
                                    onDismissRequest()
                                }
                            }
                        },
                        enabled = !parsedPreview.isNullOrEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = StatusOnline)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("اعتماد وإضافة (${parsedPreview?.size ?: 0}) عميل للقاعدة")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, CyberBorder)
                ) {
                    Text("إغلاق", color = TextSecondaryDark)
                }
            }
        }
    }
}

@Composable
fun DeviceJsonBackupDialog(
    devices: List<NetworkDevice>,
    onImport: (String) -> Unit,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    var activeTab by remember { mutableStateOf(0) }
    var inputJson by remember { mutableStateOf("") }
    var parsedCount by remember { mutableStateOf<Int?>(null) }
    var detectedNetworkName by remember { mutableStateOf<String?>(null) }
    var parseError by remember { mutableStateOf<String?>(null) }

    val exportedJson = remember(devices) {
        DataJsonHelper.exportDevicesToJson(devices)
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 680.dp)
                .clip(RoundedCornerShape(20.dp))
                .border(BorderStroke(1.dp, CyberBorder), RoundedCornerShape(20.dp)),
            color = CyberDarkSurface
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MikroTikPrimary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Router, contentDescription = null, tint = MikroTikCyan, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("تصدير واستيراد أجهزة الشبكة", color = TextPrimaryDark, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("حفظ ونقل بيانات السيكتورات والروابط اللاسلكية", color = TextSecondaryDark, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TabPill(
                        label = "تصدير (${devices.size} جهاز)",
                        icon = Icons.Default.FileUpload,
                        isSelected = activeTab == 0,
                        onClick = { activeTab = 0 },
                        modifier = Modifier.weight(1f)
                    )
                    TabPill(
                        label = "استيراد JSON",
                        icon = Icons.Default.FileDownload,
                        isSelected = activeTab == 1,
                        onClick = { activeTab = 1 },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (activeTab == 0) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        colors = CardDefaults.cardColors(containerColor = CyberDarkCardElevated),
                        border = BorderStroke(1.dp, CyberBorder)
                    ) {
                        LazyColumn(modifier = Modifier.padding(12.dp)) {
                            item {
                                Text(exportedJson, color = TextPrimaryDark, fontSize = 11.sp, lineHeight = 16.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("Devices JSON", exportedJson))
                                Toast.makeText(context, "تم نسخ أجهزة الشبكة بنجاح!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MikroTikPrimary)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("نسخ JSON")
                        }
                        OutlinedButton(
                            onClick = {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "خريطة وأجهزة شبكة سام ميكروتك")
                                    putExtra(Intent.EXTRA_TEXT, exportedJson)
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "مشاركة أجهزة الشبكة"))
                            },
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, CyberBorder)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = MikroTikCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("مشاركة", color = MikroTikCyan)
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = inputJson,
                        onValueChange = {
                            inputJson = it
                            parseError = null
                            parsedCount = null
                            detectedNetworkName = null
                            if (it.isNotBlank()) {
                                try {
                                    val res = DataJsonHelper.parseDevicesFromJson(it)
                                    parsedCount = res.devices.size
                                    detectedNetworkName = res.networkName
                                } catch (e: Exception) {
                                    parseError = "صيغة JSON غير صحيحة: ${e.localizedMessage}"
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        placeholder = { Text("الصق كود JSON لأجهزة الشبكة هنا...", color = TextMutedDark, fontSize = 12.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MikroTikCyan,
                            unfocusedBorderColor = CyberBorder,
                            focusedTextColor = TextPrimaryDark,
                            unfocusedTextColor = TextPrimaryDark,
                            focusedContainerColor = CyberDarkCardElevated,
                            unfocusedContainerColor = CyberDarkCardElevated
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    parseError?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                    }

                    parsedCount?.let { count ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "تم التعرف على ($count) جهاز بث ومحطة${if (detectedNetworkName != null) " تابعة لـ [$detectedNetworkName]" else ""}",
                            color = StatusOnline,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            if (!inputJson.isBlank() && parsedCount != null) {
                                onImport(inputJson)
                                Toast.makeText(context, "تم استيراد الأجهزة بنجاح!", Toast.LENGTH_SHORT).show()
                                onDismissRequest()
                            }
                        },
                        enabled = parsedCount != null && parsedCount!! > 0,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = StatusOnline)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("اعتماد وإضافة الأجهزة")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, CyberBorder)
                ) {
                    Text("إغلاق", color = TextSecondaryDark)
                }
            }
        }
    }
}

@Composable
fun PurchaseJsonBackupDialog(
    onImportDrafts: (List<DataJsonHelper.ImportedPurchaseDraft>) -> Unit,
    onReviewDraft: ((DataJsonHelper.ImportedPurchaseDraft) -> Unit)? = null,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    var inputJson by remember { mutableStateOf("") }
    var parsedDrafts by remember { mutableStateOf<List<DataJsonHelper.ImportedPurchaseDraft>?>(null) }
    var parseError by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 680.dp)
                .clip(RoundedCornerShape(20.dp))
                .border(BorderStroke(1.dp, CyberBorder), RoundedCornerShape(20.dp)),
            color = CyberDarkSurface
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MikroTikPrimary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = MikroTikCyan, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("استيراد فواتير المشتريات كمسودات", color = TextPrimaryDark, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("استيراد فواتير الموردين ومشتريات الأصول لمراجعتها", color = TextSecondaryDark, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    OutlinedTextField(
                        value = inputJson,
                        onValueChange = {
                            inputJson = it
                            parseError = null
                            parsedDrafts = null
                            if (it.isNotBlank()) {
                                try {
                                    val drafts = DataJsonHelper.parsePurchasesFromJson(it)
                                    parsedDrafts = drafts
                                    if (drafts.isEmpty()) {
                                        parseError = "لم يتم العثور على بنود مشتريات صالحة في نص الـ JSON"
                                    }
                                } catch (e: Exception) {
                                    parseError = "صيغة JSON غير صحيحة: ${e.localizedMessage}"
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        textStyle = LocalTextStyle.current.copy(
                            textDirection = TextDirection.Ltr,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        ),
                        placeholder = {
                            Text(
                                "الصق كود JSON لفواتير المشتريات أو الفاتورة المحولة من صورة هنا...\n{\n  \"company_info\": {\"name\": \"السلطان تك\"},\n  \"invoice_info\": {\"currency\": \"دولار $\"},\n  \"items\": [\n    {\"name\": \"راوتر أو قسام شبكة\", \"quantity\": 4, \"unit_price\": 6.5}\n  ]\n}",
                                color = TextMutedDark,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MikroTikCyan,
                            unfocusedBorderColor = CyberBorder,
                            focusedTextColor = TextPrimaryDark,
                            unfocusedTextColor = TextPrimaryDark,
                            focusedContainerColor = CyberDarkCardElevated,
                            unfocusedContainerColor = CyberDarkCardElevated
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                parseError?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                }

                parsedDrafts?.let { drafts ->
                    if (drafts.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = StatusOnline.copy(alpha = 0.15f)),
                            border = BorderStroke(1.dp, StatusOnline.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    "✅ تم التعرف على (${drafts.size}) فواتير مشتريات جاهزة للمراجعة والترحيل",
                                    color = StatusOnline,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                drafts.forEachIndexed { index, draft ->
                                    val totalItems = draft.items.size
                                    val totalMinor = draft.items.sumOf { it.quantity * it.unitPriceMinor }
                                    val totalFormatted = String.format(java.util.Locale.US, "%.2f", totalMinor / 100.0)
                                    val paymentLabel = if (draft.isCash) "🟢 نقداً (كاش)" else "🔵 شراء آجل"

                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        colors = CardDefaults.cardColors(containerColor = CyberDarkCardElevated),
                                        border = BorderStroke(1.dp, CyberBorder),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    "المورد: ${draft.vendorName}",
                                                    color = TextPrimaryDark,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    paymentLabel,
                                                    color = if (draft.isCash) StatusOnline else MikroTikCyan,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    "البنود: $totalItems • الإجمالي: $totalFormatted ${draft.currencyCode}",
                                                    color = MikroTikCyan,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                if (onReviewDraft != null) {
                                                    TextButton(
                                                        onClick = {
                                                            onReviewDraft(draft)
                                                            onDismissRequest()
                                                        },
                                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp), tint = MikroTikCyan)
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("تعديل ومراجعة ✏️", fontSize = 11.sp, color = MikroTikCyan, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                            if (draft.notes.isNotBlank()) {
                                                Text(
                                                    "البيان: ${draft.notes}",
                                                    color = TextSecondaryDark,
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (onReviewDraft != null) {
                    Button(
                        onClick = {
                            parsedDrafts?.let {
                                if (it.isNotEmpty()) {
                                    onReviewDraft(it.first())
                                    onDismissRequest()
                                }
                            }
                        },
                        enabled = !parsedDrafts.isNullOrEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MikroTikPrimary)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("مراجعة وتعديل الفاتورة قبل الحفظ ✏️", fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                Button(
                    onClick = {
                        parsedDrafts?.let {
                            if (it.isNotEmpty()) {
                                onImportDrafts(it)
                                Toast.makeText(context, "تم ترحيل ${it.size} فواتير مسودة بنجاح!", Toast.LENGTH_SHORT).show()
                                onDismissRequest()
                            }
                        }
                    },
                    enabled = !parsedDrafts.isNullOrEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = if (onReviewDraft != null) StatusOnline.copy(alpha = 0.85f) else StatusOnline)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (onReviewDraft != null) "ترحيل فوري مباشر دون تعديل ⚡" else "تحويل إلى مسودات مشتريات للمراجعة")
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, CyberBorder)
                ) {
                    Text("إلغاء", color = TextSecondaryDark)
                }
            }
        }
    }
}

class EditPurchaseItemUiState(
    description: String,
    quantityText: String,
    priceText: String,
    isAsset: Boolean,
    accountCode: String = if (isAsset) "1501" else "5101",
    usefulLifeMonthsText: String = "36"
) {
    var description by mutableStateOf(description)
    var quantityText by mutableStateOf(quantityText)
    var priceText by mutableStateOf(priceText)
    var isAsset by mutableStateOf(isAsset)
    var accountCode by mutableStateOf(accountCode)
    var usefulLifeMonthsText by mutableStateOf(usefulLifeMonthsText)

    val quantity: Int get() = quantityText.toIntOrNull()?.coerceAtLeast(1) ?: 1
    val unitPriceMinor: Long
        get() {
            val m = Money.parseFromUserInput(priceText, CurrencyCode.FUNCTIONAL)?.minor
            return (m ?: 0L).coerceAtLeast(1L)
        }
    val lineTotalMinor: Long get() = quantity * unitPriceMinor

    fun toSpec(): PurchaseItemSpec = PurchaseItemSpec(
        description = description.trim().ifBlank { "بند مشتريات" },
        accountCode = if (isAsset) "1501" else "5101",
        quantity = quantity,
        unitPriceMinor = unitPriceMinor,
        isAsset = isAsset,
        usefulLifeMonths = if (isAsset) (usefulLifeMonthsText.toIntOrNull() ?: 36) else null
    )
}

@Composable
fun EditImportedPurchaseDialog(
    draft: DataJsonHelper.ImportedPurchaseDraft,
    parties: List<PartyEntity>,
    treasuries: List<TreasuryAccountEntity>,
    onSaveAndPost: (
        vendorName: String,
        isCash: Boolean,
        treasuryId: String?,
        currency: CurrencyCode,
        exchangeRate: ExchangeRate,
        items: List<PurchaseItemSpec>,
        notes: String
    ) -> Unit,
    onDismissRequest: () -> Unit
) {
    var vendorName by remember { mutableStateOf(draft.vendorName) }
    var isCash by remember { mutableStateOf(draft.isCash) }
    var selectedCurrency by remember { mutableStateOf(draft.currencyCode) }
    var exchangeRateText by remember {
        mutableStateOf(
            if (draft.exchangeRateMicros > 0L) ExchangeRate.formatRateMicros(draft.exchangeRateMicros)
            else if (draft.currencyCode == CurrencyCode.FUNCTIONAL) "1"
            else ""
        )
    }
    var selectedTreasuryId by remember {
        mutableStateOf(
            treasuries.firstOrNull { it.currency == selectedCurrency.name }?.id
                ?: treasuries.firstOrNull()?.id
        )
    }
    var notes by remember { mutableStateOf(draft.notes) }
    val uiItems = remember {
        mutableStateListOf<EditPurchaseItemUiState>().apply {
            addAll(
                draft.items.map { item ->
                    val pStr = Money(item.unitPriceMinor, draft.currencyCode).format(includeSymbol = false)
                    EditPurchaseItemUiState(
                        description = item.description,
                        quantityText = item.quantity.toString(),
                        priceText = pStr,
                        isAsset = item.isAsset,
                        accountCode = item.accountCode,
                        usefulLifeMonthsText = (item.usefulLifeMonths ?: 36).toString()
                    )
                }
            )
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .heightIn(max = 750.dp)
                .clip(RoundedCornerShape(20.dp))
                .border(BorderStroke(1.dp, CyberBorder), RoundedCornerShape(20.dp)),
            color = CyberDarkSurface
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MikroTikPrimary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = MikroTikCyan, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("مراجعة وتعديل فاتورة المشتريات", color = TextPrimaryDark, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("تعديل المورد، العملة، نوع السداد (نقداً/آجل)، والبنود قبل الحفظ النهائي", color = TextSecondaryDark, fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. Vendor Section
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = CyberDarkCardElevated),
                            border = BorderStroke(1.dp, CyberBorder),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("اسم المورد / الشركة:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextPrimaryDark)
                                OutlinedTextField(
                                    value = vendorName,
                                    onValueChange = { vendorName = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MikroTikCyan,
                                        unfocusedBorderColor = CyberBorder,
                                        focusedTextColor = TextPrimaryDark,
                                        unfocusedTextColor = TextPrimaryDark,
                                        focusedContainerColor = CyberDarkSurface,
                                        unfocusedContainerColor = CyberDarkSurface
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                val vendorList = parties.filter { it.isVendor }
                                if (vendorList.isNotEmpty()) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        vendorList.take(3).forEach { v ->
                                            FilterChip(
                                                selected = vendorName.trim().equals(v.name.trim(), ignoreCase = true),
                                                onClick = { vendorName = v.name },
                                                label = { Text(v.name, fontSize = 10.sp) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 2. Payment Method & Treasury
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = CyberDarkCardElevated),
                            border = BorderStroke(1.dp, CyberBorder),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("طريقة السداد المحاسبية:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextPrimaryDark)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = !isCash,
                                        onClick = { isCash = false },
                                        label = { Text("آجل (ذمم دائنة للمورد)", fontSize = 11.sp, fontWeight = if (!isCash) FontWeight.Bold else FontWeight.Normal) },
                                        leadingIcon = { Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    )
                                    FilterChip(
                                        selected = isCash,
                                        onClick = { isCash = true },
                                        label = { Text("نقداً (كاش فوري)", fontSize = 11.sp, fontWeight = if (isCash) FontWeight.Bold else FontWeight.Normal) },
                                        leadingIcon = { Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    )
                                }

                                if (isCash) {
                                    Text("الخزينة المسدد منها:", fontSize = 11.sp, color = MikroTikCyan, fontWeight = FontWeight.Medium)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        treasuries.forEach { tr ->
                                            FilterChip(
                                                selected = selectedTreasuryId == tr.id,
                                                onClick = { selectedTreasuryId = tr.id },
                                                label = { Text("${tr.name} (${tr.currency})", fontSize = 10.sp) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 3. Currency and Exchange Rate
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = CyberDarkCardElevated),
                            border = BorderStroke(1.dp, CyberBorder),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("العملة وسعر الصرف:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextPrimaryDark)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        CurrencyCode.entries.forEach { curr ->
                                            FilterChip(
                                                selected = selectedCurrency == curr,
                                                onClick = {
                                                    selectedCurrency = curr
                                                    if (curr == CurrencyCode.FUNCTIONAL) exchangeRateText = "1"
                                                    treasuries.firstOrNull { it.currency == curr.name }?.let { selectedTreasuryId = it.id }
                                                },
                                                label = { Text(curr.name, fontSize = 11.sp, fontWeight = if (selectedCurrency == curr) FontWeight.Bold else FontWeight.Normal) }
                                            )
                                        }
                                    }
                                    if (selectedCurrency != CurrencyCode.FUNCTIONAL) {
                                        OutlinedTextField(
                                            value = exchangeRateText,
                                            onValueChange = { exchangeRateText = it },
                                            label = { Text("سعر الصرف لليمني", fontSize = 10.sp) },
                                            modifier = Modifier.width(130.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = MikroTikCyan,
                                                unfocusedBorderColor = CyberBorder,
                                                focusedTextColor = TextPrimaryDark,
                                                unfocusedTextColor = TextPrimaryDark,
                                                focusedContainerColor = CyberDarkSurface,
                                                unfocusedContainerColor = CyberDarkSurface
                                            ),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 4. Items List Header
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("بنود ومواد الفاتورة (${uiItems.size}):", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimaryDark)
                            Button(
                                onClick = {
                                    uiItems.add(EditPurchaseItemUiState("بند جديد", "1", "10", false))
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MikroTikPrimary.copy(alpha = 0.25f)),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = MikroTikCyan, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("إضافة بند", color = MikroTikCyan, fontSize = 11.sp)
                            }
                        }
                    }

                    // Items List Cards
                    itemsIndexed(uiItems) { index, itemState ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = CyberDarkCardElevated),
                            border = BorderStroke(1.dp, CyberBorder),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(
                                        value = itemState.description,
                                        onValueChange = { itemState.description = it },
                                        label = { Text("اسم البند / المادة #${index + 1}", fontSize = 10.sp) },
                                        modifier = Modifier.weight(1f),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MikroTikCyan,
                                            unfocusedBorderColor = CyberBorder,
                                            focusedTextColor = TextPrimaryDark,
                                            unfocusedTextColor = TextPrimaryDark,
                                            focusedContainerColor = CyberDarkSurface,
                                            unfocusedContainerColor = CyberDarkSurface
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    IconButton(
                                        onClick = {
                                            if (uiItems.size > 1) {
                                                uiItems.removeAt(index)
                                            }
                                        },
                                        enabled = uiItems.size > 1
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = null, tint = if (uiItems.size > 1) StatusOffline else TextMutedDark, modifier = Modifier.size(18.dp))
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(
                                        value = itemState.quantityText,
                                        onValueChange = { itemState.quantityText = it },
                                        label = { Text("الكمية", fontSize = 10.sp) },
                                        modifier = Modifier.weight(1f),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MikroTikCyan,
                                            unfocusedBorderColor = CyberBorder,
                                            focusedTextColor = TextPrimaryDark,
                                            unfocusedTextColor = TextPrimaryDark,
                                            focusedContainerColor = CyberDarkSurface,
                                            unfocusedContainerColor = CyberDarkSurface
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    )

                                    OutlinedTextField(
                                        value = itemState.priceText,
                                        onValueChange = { itemState.priceText = it },
                                        label = { Text("السعر (${selectedCurrency.name})", fontSize = 10.sp) },
                                        modifier = Modifier.weight(1.5f),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MikroTikCyan,
                                            unfocusedBorderColor = CyberBorder,
                                            focusedTextColor = TextPrimaryDark,
                                            unfocusedTextColor = TextPrimaryDark,
                                            focusedContainerColor = CyberDarkSurface,
                                            unfocusedContainerColor = CyberDarkSurface
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    )

                                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1.2f)) {
                                        Text("الإجمالي:", fontSize = 9.sp, color = TextSecondaryDark)
                                        val lineTotal = itemState.lineTotalMinor / 100.0
                                        Text(String.format(java.util.Locale.US, "%.2f", lineTotal), fontWeight = FontWeight.Bold, color = MikroTikCyan, fontSize = 12.sp)
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = itemState.isAsset,
                                        onCheckedChange = { isChecked ->
                                            itemState.isAsset = isChecked
                                            itemState.accountCode = if (isChecked) "1501" else "5101"
                                        }
                                    )
                                    Text("أصل شبكة ثابت (سجل الأصول 1501)", fontSize = 11.sp, color = TextPrimaryDark)
                                    if (itemState.isAsset) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("• 36 شهر إهلاك", fontSize = 10.sp, color = MikroTikCyan)
                                    }
                                }
                            }
                        }
                    }

                    // 5. Notes
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = CyberDarkCardElevated),
                            border = BorderStroke(1.dp, CyberBorder),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("البيان والملاحظات:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextPrimaryDark)
                                OutlinedTextField(
                                    value = notes,
                                    onValueChange = { notes = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MikroTikCyan,
                                        unfocusedBorderColor = CyberBorder,
                                        focusedTextColor = TextPrimaryDark,
                                        unfocusedTextColor = TextPrimaryDark,
                                        focusedContainerColor = CyberDarkSurface,
                                        unfocusedContainerColor = CyberDarkSurface
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }
                    }

                    // 6. Summary Card
                    item {
                        val totalMinor = uiItems.sumOf { it.lineTotalMinor }
                        val rateMicros = if (selectedCurrency == CurrencyCode.FUNCTIONAL) {
                            ExchangeRate.SCALE_MICROS
                        } else {
                            ExchangeRate.parseRateFromUserInput(exchangeRateText) ?: 0L
                        }
                        val rate = if (selectedCurrency == CurrencyCode.FUNCTIONAL) {
                            ExchangeRate.parity(CurrencyCode.FUNCTIONAL)
                        } else if (rateMicros > 0L) {
                            ExchangeRate(selectedCurrency, CurrencyCode.FUNCTIONAL, rateMicros)
                        } else null
                        val totalBase = rate?.convert(totalMinor) ?: 0L

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = if (isCash) StatusOnline.copy(alpha = 0.12f) else MikroTikPrimary.copy(alpha = 0.15f)),
                            border = BorderStroke(1.dp, if (isCash) StatusOnline.copy(alpha = 0.5f) else MikroTikCyan.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("ملخص الفاتورة قبل الترحيل:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextPrimaryDark)
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("إجمالي البنود (${uiItems.size} بند):", color = TextSecondaryDark, fontSize = 12.sp)
                                    val formattedTotal = Money(totalMinor, selectedCurrency).format(includeSymbol = false)
                                    Text("$formattedTotal ${selectedCurrency.name}", color = TextPrimaryDark, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                if (selectedCurrency != CurrencyCode.FUNCTIONAL) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("المعادل بالريال اليمني:", color = TextSecondaryDark, fontSize = 11.sp)
                                        Text("${totalBase / 100L} YER", color = MikroTikCyan, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                    }
                                }
                                Text(
                                    if (isCash) "🟢 سداد نقدي فوري: سيتم إنشاء سند صرف وسداد الفاتورة مباشرة من الخزينة."
                                    else "🔵 شراء آجل: سيتم قيد المبلغ ذمة للمورد '${vendorName.ifBlank { "مورد عام" }}' في الأستاذ العام.",
                                    fontSize = 11.sp,
                                    color = if (isCash) StatusOnline else MikroTikCyan,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Actions
                val validRateMicros = if (selectedCurrency == CurrencyCode.FUNCTIONAL) {
                    ExchangeRate.SCALE_MICROS
                } else {
                    ExchangeRate.parseRateFromUserInput(exchangeRateText) ?: 0L
                }
                val isRateValid = validRateMicros > 0L

                Button(
                    onClick = {
                        val rate = if (selectedCurrency == CurrencyCode.FUNCTIONAL) {
                            ExchangeRate.parity(CurrencyCode.FUNCTIONAL)
                        } else {
                            ExchangeRate(selectedCurrency, CurrencyCode.FUNCTIONAL, validRateMicros)
                        }
                        onSaveAndPost(
                            vendorName.trim().ifBlank { "مورد عام" },
                            isCash,
                            if (isCash) selectedTreasuryId else null,
                            selectedCurrency,
                            rate,
                            uiItems.map { it.toSpec() },
                            notes
                        )
                    },
                    enabled = uiItems.isNotEmpty() && (!isCash || !selectedTreasuryId.isNullOrBlank()) && isRateValid,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = if (isCash) StatusOnline else MikroTikPrimary)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (isCash) "حفظ وترحيل الفاتورة وسدادها نقداً"
                        else "حفظ وترحيل الفاتورة الآجلة للأستاذ",
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, CyberBorder)
                ) {
                    Text("إلغاء", color = TextSecondaryDark)
                }
            }
        }
    }
}

@Composable
private fun TabPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) MikroTikPrimary else CyberDarkCardElevated)
            .border(BorderStroke(1.dp, if (isSelected) MikroTikCyan else CyberBorder), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (isSelected) Color.White else TextSecondaryDark,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                label,
                color = if (isSelected) Color.White else TextSecondaryDark,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}
