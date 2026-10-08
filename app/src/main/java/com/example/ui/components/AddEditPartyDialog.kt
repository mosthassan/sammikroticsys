package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Send
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
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.data.local.entity.PartyEntity
import com.example.ui.screens.ModernRoleSelectionCard
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberDarkCardElevated
import com.example.ui.theme.CyberDarkSurface
import com.example.ui.theme.MikroTikCyan
import com.example.ui.theme.MikroTikPrimary
import com.example.ui.theme.StatusOnline
import com.example.ui.theme.TextMutedDark
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark
import com.example.ui.viewmodel.PartyViewModel
import com.example.util.WhatsAppDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditPartyDialog(
    partyToEdit: PartyEntity? = null,
    initialRole: Int = 0, // 0: All, 1: Customer, 2: Vendor, 3: Partner
    onDismiss: () -> Unit,
    onSubmit: (
        name: String,
        phone: String,
        isCustomer: Boolean,
        isVendor: Boolean,
        isPartner: Boolean,
        creditLimitMinor: Long,
        equityBasisPoints: Int
    ) -> Unit
) {
    val context = LocalContext.current
    val isEditing = partyToEdit != null

    var name by remember { mutableStateOf(partyToEdit?.name.orEmpty()) }
    var phone by remember { mutableStateOf(partyToEdit?.phone.orEmpty()) }
    var isCustomer by remember {
        mutableStateOf(partyToEdit?.isCustomer ?: (initialRole == 0 || initialRole == 1))
    }
    var isVendor by remember {
        mutableStateOf(partyToEdit?.isVendor ?: (initialRole == 2))
    }
    var isPartner by remember {
        mutableStateOf(partyToEdit?.isPartner ?: (initialRole == 3))
    }
    var creditLimitText by remember {
        mutableStateOf(partyToEdit?.let { (it.creditLimitMinor / 100L).toString() } ?: "")
    }
    var equityShareText by remember {
        mutableStateOf(
            partyToEdit?.let {
                val bps = it.equityPercentageBasisPoints
                if (bps > 0) String.format(java.util.Locale.US, "%.2f", bps / 100.0) else ""
            } ?: ""
        )
    }

    var bannerMessage by remember { mutableStateOf<String?>(null) }
    var bannerIsSuccess by remember { mutableStateOf(true) }

    val emeraldColor = Color(0xFF10B981)
    val blueColor = Color(0xFF3B82F6)
    val amberColor = Color(0xFFF59E0B)

    // Contact Picker Launcher
    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { contactUri: Uri? ->
        if (contactUri != null) {
            val contactInfo = PartyViewModel.extractContact(context, contactUri)
            if (contactInfo != null) {
                // Populate name if current is blank or update requested
                if (contactInfo.name.isNotBlank()) {
                    name = contactInfo.name
                }
                if (contactInfo.phone.isNotBlank()) {
                    phone = contactInfo.phone
                    bannerMessage = "تم استيراد جهة الاتصال بنجاح"
                    bannerIsSuccess = true
                } else {
                    bannerMessage = "تم اختيار جهة الاتصال ولكن لا يوجد رقم هاتف مسجل لها"
                    bannerIsSuccess = false
                }
            } else {
                bannerMessage = "تعذر قراءة بيانات جهة الاتصال. يرجى التأكد من الصلاحيات"
                bannerIsSuccess = false
            }
        }
    }

    // Permission Launcher for READ_CONTACTS
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            contactPickerLauncher.launch(null)
        } else {
            bannerMessage = "تم رفض إذن الوصول إلى جهات الاتصال"
            bannerIsSuccess = false
        }
    }

    // Auto-dismiss banner message after 3.5 seconds
    LaunchedEffect(bannerMessage) {
        if (bannerMessage != null) {
            delay(3500)
            bannerMessage = null
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 740.dp)
                .clip(RoundedCornerShape(22.dp))
                .border(BorderStroke(1.dp, CyberBorder), RoundedCornerShape(22.dp)),
            color = CyberDarkSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
            ) {
                // 1. Header Bar (Pinned)
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
                            Icon(
                                imageVector = if (isEditing) Icons.Default.Edit else Icons.Default.PersonAdd,
                                contentDescription = null,
                                tint = MikroTikCyan,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (isEditing) "تعديل بيانات الطرف" else "إضافة طرف جديد للنظام",
                                color = TextPrimaryDark,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "دعم الربط بجهات الاتصال وتصدير فواتير الواتساب",
                                color = TextSecondaryDark,
                                fontSize = 11.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("btn_close_party_dialog")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = TextSecondaryDark)
                    }
                }

                HorizontalDivider(color = CyberBorder)

                // 2. Informative Banner (Snackbar replacement inside dialog)
                AnimatedVisibility(
                    visible = bannerMessage != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    bannerMessage?.let { msg ->
                        Surface(
                            color = if (bannerIsSuccess) StatusOnline.copy(alpha = 0.15f) else Color(0xFFEF4444).copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, if (bannerIsSuccess) StatusOnline else Color(0xFFEF4444)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (bannerIsSuccess) Icons.Default.Check else Icons.Default.Close,
                                    contentDescription = null,
                                    tint = if (bannerIsSuccess) StatusOnline else Color(0xFFEF4444),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = msg,
                                    color = TextPrimaryDark,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // 3. Scrollable Form Fields
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_party_name"),
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

                    // Phone / WhatsApp Input + Native Contact Picker Button
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = phone,
                                onValueChange = { phone = it },
                                label = { Text("رقم الهاتف / واتساب") },
                                placeholder = { Text("77xxxxxxx أو +967xxxxxxxxx", fontSize = 12.sp, color = TextMutedDark) },
                                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = MikroTikCyan) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("input_party_phone"),
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

                            // Native Contact Picker Action Button
                            FilledIconButton(
                                onClick = {
                                    val hasPermission = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.READ_CONTACTS
                                    ) == PackageManager.PERMISSION_GRANTED

                                    if (hasPermission) {
                                        contactPickerLauncher.launch(null)
                                    } else {
                                        permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MikroTikPrimary.copy(alpha = 0.25f),
                                    contentColor = MikroTikCyan
                                ),
                                modifier = Modifier
                                    .size(54.dp)
                                    .border(1.dp, MikroTikCyan.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                    .testTag("btn_pick_contact")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Contacts,
                                    contentDescription = "اختيار من جهات الاتصال",
                                    tint = MikroTikCyan,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        // Contact Picker helper caption & WhatsApp test link
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "💡 انقر زر جهات الاتصال لاختيار وتعبئة الاسم والرقم تلقائياً",
                                fontSize = 10.sp,
                                color = TextSecondaryDark
                            )

                            if (phone.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFF25D366).copy(alpha = 0.15f),
                                    modifier = Modifier
                                        .clickable {
                                            WhatsAppDispatcher.sendTextMessage(
                                                context,
                                                phone,
                                                "مرحباً $name، نرحب بكم في شبكة SamMikrotik."
                                            )
                                        }
                                        .testTag("btn_test_whatsapp")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Send,
                                            contentDescription = "اختبار واتساب",
                                            tint = Color(0xFF25D366),
                                            modifier = Modifier.size(10.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            "تجربة واتساب",
                                            fontSize = 9.sp,
                                            color = Color(0xFF25D366),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

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
                                    modifier = Modifier.fillMaxWidth().testTag("input_credit_limit"),
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
                                    modifier = Modifier.fillMaxWidth().testTag("input_equity_share"),
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

                // 4. Pinned Action Button at Bottom
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
                                val cleanEq = equityShareText.trim().replace("،", "").replace("٬", "").replace(",", "").replace("٫", ".")
                                val eqParts = cleanEq.split(".")
                                val eqWhole = eqParts.getOrNull(0)?.toIntOrNull() ?: 0
                                val eqFrac = eqParts.getOrNull(1)?.padEnd(2, '0')?.take(2)?.toIntOrNull() ?: 0
                                val equityBps = (eqWhole * 100 + eqFrac).coerceIn(0, 10000)
                                val sanitizedPhone = WhatsAppDispatcher.sanitizePhoneNumber(phone.trim())

                                onSubmit(
                                    name.trim(),
                                    sanitizedPhone,
                                    isCustomer,
                                    isVendor,
                                    isPartner,
                                    limit,
                                    equityBps
                                )
                            }
                        },
                        enabled = isValid,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MikroTikPrimary,
                            disabledContainerColor = CyberDarkSurface
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_save_party")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isEditing) "حفظ التعديلات في النظام" else "حفظ وتثبيت الطرف في النظام",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}
