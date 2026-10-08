package com.example.ui.components

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.network.DeviceStatus
import com.example.data.network.DeviceType
import com.example.data.network.NetworkDevice
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
import com.example.ui.viewmodel.NetworkDeviceViewModel
import kotlinx.coroutines.delay
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditDeviceDialog(
    device: NetworkDevice? = null,
    isCloning: Boolean = false,
    existingDevices: List<NetworkDevice> = emptyList(),
    viewModel: NetworkDeviceViewModel? = null,
    onSave: (NetworkDevice) -> Unit,
    onDismiss: () -> Unit
) {
    val initialName = remember(device, isCloning) {
        when {
            device != null && isCloning -> "${device.name.trim()} - نسخة"
            device != null -> device.name
            else -> ""
        }
    }

    val initialIp = remember(device, isCloning) {
        if (isCloning) "" else device?.ipAddress.orEmpty()
    }

    var name by remember { mutableStateOf(initialName) }
    var ip by remember { mutableStateOf(initialIp) }
    var model by remember { mutableStateOf(device?.model ?: "MikroTik RouterBOARD") }
    var deviceType by remember { mutableStateOf(device?.deviceType ?: DeviceType.ACCESS_POINT) }
    var managementPortText by remember { mutableStateOf(device?.managementPort?.toString() ?: "8728") }
    var subnet by remember { mutableStateOf(device?.subnet ?: "10.10.1.0/24") }
    var credentials by remember { mutableStateOf(device?.credentials ?: "admin") }
    var towerLocation by remember { mutableStateOf(device?.towerLocation ?: "البرج الرئيسي") }
    var macAddress by remember { mutableStateOf(if (isCloning) "" else device?.macAddress.orEmpty()) }
    var frequency by remember { mutableStateOf(device?.frequency ?: "5500 MHz") }
    var notes by remember { mutableStateOf(device?.notes.orEmpty()) }
    var status by remember { mutableStateOf(device?.status ?: DeviceStatus.ONLINE) }

    // Real-Time IP Conflict and Validation State
    var conflictingDevice by remember { mutableStateOf<NetworkDevice?>(null) }
    var isCheckingIp by remember { mutableStateOf(false) }
    var isIpFormatValid by remember { mutableStateOf(false) }

    // When cloning, device ID is considered new (do not exclude original device ID from conflict checks)
    val deviceIdToExclude = if (isCloning) null else device?.id

    // Reactive 250ms debounced IP conflict validation
    LaunchedEffect(ip) {
        isCheckingIp = true
        delay(250)
        val cleanIp = ip.trim()
        val ipRegex = Regex("""^(\d{1,3}\.){3}\d{1,3}$""")
        val formatValid = cleanIp.matches(ipRegex) && cleanIp.split(".").all {
            it.toIntOrNull() in 0..255
        }
        isIpFormatValid = formatValid

        if (cleanIp.isBlank()) {
            conflictingDevice = null
        } else if (viewModel != null) {
            conflictingDevice = viewModel.checkIpConflict(cleanIp, deviceIdToExclude)
        } else {
            conflictingDevice = existingDevices.firstOrNull { d ->
                (deviceIdToExclude == null || d.id != deviceIdToExclude) &&
                    d.ipAddress.trim().equals(cleanIp, ignoreCase = true)
            }
        }
        isCheckingIp = false
    }

    val hasConflict = conflictingDevice != null
    val isFormValid = name.isNotBlank() && ip.isNotBlank() && isIpFormatValid && !hasConflict

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 760.dp)
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
                                .background(
                                    when {
                                        isCloning -> Color(0xFF10B981).copy(alpha = 0.2f)
                                        device != null -> MikroTikPrimary.copy(alpha = 0.2f)
                                        else -> MikroTikCyan.copy(alpha = 0.2f)
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when {
                                    isCloning -> Icons.Default.ContentCopy
                                    device != null -> Icons.Default.Edit
                                    else -> Icons.Default.Router
                                },
                                contentDescription = null,
                                tint = if (isCloning) Color(0xFF10B981) else MikroTikCyan,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = when {
                                    isCloning -> "تكرار وإضافة جهاز من نسخة"
                                    device != null -> "تعديل مواصفات الجهاز"
                                    else -> "إضافة جهاز شبكة جديد"
                                },
                                color = TextPrimaryDark,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = if (isCloning) "تم نسخ الإعدادات الفنية، يرجى تعيين آي بي فريد" else "فحص مباشر للتعارضات وتوثيق بيانات الأبراج",
                                color = TextSecondaryDark,
                                fontSize = 11.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("btn_close_device_dialog")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = TextSecondaryDark)
                    }
                }

                HorizontalDivider(color = CyberBorder)

                // Form Scrollable Body
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 1. Device Name
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("اسم الجهاز أو الستيشن (مطلوب)") },
                        leadingIcon = { Icon(Icons.Default.Router, contentDescription = null, tint = MikroTikCyan) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_device_name"),
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

                    // 2. IP Address with Real-Time Conflict Detection
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(
                            value = ip,
                            onValueChange = { ip = it },
                            label = { Text("عنوان الآي بي (IP Address)") },
                            placeholder = { Text("مثال: 10.10.1.20", color = TextMutedDark, fontSize = 12.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                            isError = hasConflict,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_device_ip"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = if (hasConflict) SemanticExpenseRed else if (isIpFormatValid) SemanticIncomeGreen else MikroTikCyan,
                                unfocusedBorderColor = if (hasConflict) SemanticExpenseRed else CyberBorder,
                                errorBorderColor = SemanticExpenseRed,
                                focusedTextColor = TextPrimaryDark,
                                unfocusedTextColor = TextPrimaryDark,
                                focusedContainerColor = CyberDarkCardElevated,
                                unfocusedContainerColor = CyberDarkCardElevated
                            ),
                            trailingIcon = {
                                when {
                                    hasConflict -> Icon(Icons.Default.Warning, contentDescription = "تعارض", tint = SemanticExpenseRed)
                                    isIpFormatValid && ip.isNotBlank() -> Icon(Icons.Default.CheckCircle, contentDescription = "صحيح", tint = SemanticIncomeGreen)
                                }
                            },
                            singleLine = true
                        )

                        // Real-Time IP Validation Indicators
                        AnimatedVisibility(visible = hasConflict, enter = fadeIn(), exit = fadeOut()) {
                            conflictingDevice?.let { conflict ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp)
                                        .testTag("banner_ip_conflict"),
                                    colors = CardDefaults.cardColors(containerColor = SemanticExpenseRed.copy(alpha = 0.15f)),
                                    border = BorderStroke(1.dp, SemanticExpenseRed),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = SemanticExpenseRed,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = "⚠️ تنبيه تعارض: عنوان الـ IP مستخدم مسبقاً بواسطة الجهاز (${conflict.name}) في موقع (${conflict.location})!",
                                            color = SemanticExpenseRed,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }

                        // Success Indicator for Valid and Unique IP
                        AnimatedVisibility(visible = !hasConflict && isIpFormatValid && ip.isNotBlank(), enter = fadeIn(), exit = fadeOut()) {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                    .testTag("indicator_ip_valid"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = SemanticIncomeGreen, modifier = Modifier.size(14.dp))
                                Text(
                                    text = "✅ عنوان الـ IP متاح وفريد على الشبكة",
                                    color = SemanticIncomeGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // 3. Model & Management Port
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = model,
                            onValueChange = { model = it },
                            label = { Text("موديل الجهاز (Model)") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1.3f)
                                .testTag("input_device_model"),
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

                        OutlinedTextField(
                            value = managementPortText,
                            onValueChange = { managementPortText = it.filter { ch -> ch.isDigit() } },
                            label = { Text("المنفذ (Port)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(0.9f)
                                .testTag("input_device_port"),
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
                    }

                    // 4. Subnet & Credentials
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = subnet,
                            onValueChange = { subnet = it },
                            label = { Text("السبنت (Subnet)") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_device_subnet"),
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

                        OutlinedTextField(
                            value = credentials,
                            onValueChange = { credentials = it },
                            label = { Text("المستخدم (Credentials)") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_device_credentials"),
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
                    }

                    // 5. Device Type Selector
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("نوع الجهاز ودوره:", color = TextSecondaryDark, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(DeviceType.values()) { t ->
                                val isSelected = t == deviceType
                                Surface(
                                    color = if (isSelected) MikroTikCyan.copy(alpha = 0.2f) else CyberDarkCardElevated,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, if (isSelected) MikroTikCyan else CyberBorder),
                                    modifier = Modifier.clickable { deviceType = t }
                                ) {
                                    Text(
                                        text = t.labelArabic,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MikroTikCyan else TextSecondaryDark,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 6. Location / Tower & Frequency
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = towerLocation,
                            onValueChange = { towerLocation = it },
                            label = { Text("موقع البرج / المحطة") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_device_location"),
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

                        OutlinedTextField(
                            value = frequency,
                            onValueChange = { frequency = it },
                            label = { Text("التردد والقناة") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_device_frequency"),
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
                    }

                    // 7. MAC Address & Configuration Notes
                    OutlinedTextField(
                        value = macAddress,
                        onValueChange = { macAddress = it },
                        label = { Text("عنوان الماك (MAC Address - اختياري)") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_device_mac"),
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

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("ملاحظات التثبيت والإعداد") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_device_notes"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MikroTikCyan,
                            unfocusedBorderColor = CyberBorder,
                            focusedTextColor = TextPrimaryDark,
                            unfocusedTextColor = TextPrimaryDark,
                            focusedContainerColor = CyberDarkCardElevated,
                            unfocusedContainerColor = CyberDarkCardElevated
                        )
                    )
                }

                HorizontalDivider(color = CyberBorder)

                // Action Submit Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CyberDarkCardElevated)
                        .padding(16.dp)
                ) {
                    Button(
                        onClick = {
                            if (isFormValid) {
                                val targetId = if (isCloning || device == null) UUID.randomUUID().toString() else device.id
                                val savedDevice = NetworkDevice(
                                    id = targetId,
                                    name = name.trim(),
                                    ipAddress = ip.trim(),
                                    deviceType = deviceType,
                                    model = model.trim(),
                                    managementPort = managementPortText.toIntOrNull() ?: 8728,
                                    subnet = subnet.trim(),
                                    credentials = credentials.trim(),
                                    macAddress = macAddress.trim(),
                                    towerLocation = towerLocation.trim(),
                                    frequency = frequency.trim(),
                                    status = status,
                                    notes = notes.trim()
                                )
                                onSave(savedDevice)
                            }
                        },
                        enabled = isFormValid,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isCloning) Color(0xFF10B981) else MikroTikPrimary,
                            disabledContainerColor = CyberDarkSurface
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_save_device")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when {
                                hasConflict -> "يوجد تعارض في الـ IP - لا يمكن الحفظ"
                                isCloning -> "تثبيت الجهاز المكرر"
                                device != null -> "حفظ التعديلات"
                                else -> "حفظ وتثبيت الجهاز"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}
