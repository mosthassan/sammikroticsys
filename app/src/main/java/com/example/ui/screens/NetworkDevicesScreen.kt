package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.network.DeviceSaveResult
import com.example.data.network.DeviceStatus
import com.example.data.network.DeviceType
import com.example.data.network.NetworkDevice
import com.example.data.network.NetworkRepository
import com.example.ui.components.AddEditDeviceDialog
import com.example.ui.components.DeviceItemCard
import com.example.ui.components.DeviceJsonBackupDialog
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberDarkCanvas
import com.example.ui.theme.CyberDarkCardElevated
import com.example.ui.theme.CyberDarkSurface
import com.example.ui.theme.MikroTikCyan
import com.example.ui.theme.MikroTikPrimary
import com.example.ui.theme.SemanticExpenseRed
import com.example.ui.theme.SemanticIncomeGreen
import com.example.ui.theme.StatusOffline
import com.example.ui.theme.StatusOnline
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.TextMutedDark
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark
import kotlinx.coroutines.launch

/**
 * Screen for managing Network Devices: Routers, Access Points, Stations, Switches, and Antennas.
 * Features quick cloning/duplication, real-time IP conflict validation guard, filtering, and backup.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkDevicesScreen(
    networkRepository: NetworkRepository,
    onNavigateBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val devices by networkRepository.devices.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedTypeFilter by remember { mutableStateOf<DeviceType?>(null) }

    var isAddingDevice by remember { mutableStateOf(false) }
    var deviceToEdit by remember { mutableStateOf<NetworkDevice?>(null) }
    var isCloningDevice by remember { mutableStateOf(false) }
    var deviceToDelete by remember { mutableStateOf<NetworkDevice?>(null) }
    var showBackupDialog by remember { mutableStateOf(false) }

    if (onNavigateBack != null) {
        BackHandler { onNavigateBack() }
    }

    // Filtered devices list based on search and type filter
    val filteredDevices = remember(devices, searchQuery, selectedTypeFilter) {
        devices.filter { dev ->
            val matchesSearch = searchQuery.isBlank() ||
                dev.name.contains(searchQuery, ignoreCase = true) ||
                dev.ipAddress.contains(searchQuery, ignoreCase = true) ||
                dev.towerLocation.contains(searchQuery, ignoreCase = true) ||
                dev.model.contains(searchQuery, ignoreCase = true)

            val matchesType = selectedTypeFilter == null || dev.deviceType == selectedTypeFilter
            matchesSearch && matchesType
        }
    }

    val onlineCount = remember(devices) { devices.count { it.status == DeviceStatus.ONLINE } }
    val warningCount = remember(devices) { devices.count { it.status == DeviceStatus.WARNING } }
    val offlineCount = remember(devices) { devices.count { it.status == DeviceStatus.OFFLINE } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CyberDarkCanvas)
            .padding(16.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onNavigateBack != null) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("btn_back_network_devices")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع",
                            tint = MikroTikCyan
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MikroTikPrimary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Router,
                        contentDescription = null,
                        tint = MikroTikCyan,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = "أجهزة الشبكة والتوزيع",
                        color = TextPrimaryDark,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "إدارة الراوترات، سيكتورات البث، ومحطات الاستقبال",
                        color = TextSecondaryDark,
                        fontSize = 11.sp
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = { showBackupDialog = true },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, CyberBorder),
                    modifier = Modifier.testTag("btn_network_devices_backup")
                ) {
                    Icon(
                        Icons.Default.SwapVert,
                        contentDescription = null,
                        tint = MikroTikCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("نسخ احتياطي", color = MikroTikCyan, fontSize = 11.sp)
                }

                Button(
                    onClick = {
                        deviceToEdit = null
                        isCloningDevice = false
                        isAddingDevice = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MikroTikPrimary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("btn_add_device")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("إضافة جهاز", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Device Status Summary Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatusSummaryChip(
                label = "إجمالي الأجهزة",
                count = devices.size,
                color = MikroTikCyan,
                modifier = Modifier.weight(1f)
            )
            StatusSummaryChip(
                label = "متصل أونلاين",
                count = onlineCount,
                color = StatusOnline,
                modifier = Modifier.weight(1f)
            )
            StatusSummaryChip(
                label = "إشارة ضعيفة",
                count = warningCount,
                color = StatusWarning,
                modifier = Modifier.weight(1f)
            )
            StatusSummaryChip(
                label = "غير متصل",
                count = offlineCount,
                color = StatusOffline,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Search Bar & Filter Row
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("بحث بالاسم، عنوان الـ IP، البرج، الموديل...", color = TextMutedDark, fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MikroTikCyan) },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "مسح", tint = TextSecondaryDark)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("input_search_devices"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MikroTikCyan,
                unfocusedBorderColor = CyberBorder,
                focusedTextColor = TextPrimaryDark,
                unfocusedTextColor = TextPrimaryDark,
                focusedContainerColor = CyberDarkCardElevated,
                unfocusedContainerColor = CyberDarkCardElevated
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Filter Chips for Device Types
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                FilterChip(
                    selected = selectedTypeFilter == null,
                    onClick = { selectedTypeFilter = null },
                    label = { Text("الكل (${devices.size})", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MikroTikCyan.copy(alpha = 0.2f),
                        selectedLabelColor = MikroTikCyan,
                        containerColor = CyberDarkCardElevated,
                        labelColor = TextSecondaryDark
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selectedTypeFilter == null,
                        borderColor = CyberBorder,
                        selectedBorderColor = MikroTikCyan
                    )
                )
            }
            items(DeviceType.values()) { type ->
                val count = devices.count { it.deviceType == type }
                val isSelected = selectedTypeFilter == type
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedTypeFilter = if (isSelected) null else type },
                    label = { Text("${type.labelArabic} ($count)", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MikroTikCyan.copy(alpha = 0.2f),
                        selectedLabelColor = MikroTikCyan,
                        containerColor = CyberDarkCardElevated,
                        labelColor = TextSecondaryDark
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = CyberBorder,
                        selectedBorderColor = MikroTikCyan
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Devices List
        if (filteredDevices.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(0.85f),
                    colors = CardDefaults.cardColors(containerColor = CyberDarkCardElevated),
                    border = BorderStroke(1.dp, CyberBorder),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Default.FilterList,
                            contentDescription = null,
                            tint = TextMutedDark,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = if (searchQuery.isNotBlank() || selectedTypeFilter != null) {
                                "لا توجد أجهزة مطابقة لمعايير البحث."
                            } else {
                                "لا توجد أجهزة مضافة بعد في الشبكة."
                            },
                            color = TextSecondaryDark,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("list_network_devices"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = filteredDevices,
                    key = { it.id }
                ) { device ->
                    DeviceItemCard(
                        device = device,
                        onEdit = {
                            deviceToEdit = device
                            isCloningDevice = false
                        },
                        onClone = {
                            deviceToEdit = device
                            isCloningDevice = true
                        },
                        onDelete = {
                            deviceToDelete = device
                        }
                    )
                }
            }
        }
    }

    // Add / Edit / Clone Dialog
    if (isAddingDevice || deviceToEdit != null) {
        AddEditDeviceDialog(
            device = deviceToEdit,
            isCloning = isCloningDevice,
            existingDevices = devices,
            onSave = { savedDevice ->
                scope.launch {
                    val result = networkRepository.addOrUpdateDevice(savedDevice)
                    when (result) {
                        is DeviceSaveResult.Success -> {
                            val msg = if (isCloningDevice) {
                                "تم تكرار وحفظ الجهاز '${savedDevice.name}' بنجاح"
                            } else if (deviceToEdit != null) {
                                "تم تعديل الجهاز '${savedDevice.name}' بنجاح"
                            } else {
                                "تم إضافة الجهاز '${savedDevice.name}' بنجاح"
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            isAddingDevice = false
                            deviceToEdit = null
                            isCloningDevice = false
                        }
                        is DeviceSaveResult.IpConflict -> {
                            Toast.makeText(
                                context,
                                "⚠️ تعذر الحفظ: الآي بي مستخدم مسبقاً للجهاز '${result.conflictingDevice.name}' بموقع '${result.conflictingDevice.towerLocation}'",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            },
            onDismiss = {
                isAddingDevice = false
                deviceToEdit = null
                isCloningDevice = false
            }
        )
    }

    // Delete Device Confirmation Dialog
    deviceToDelete?.let { device ->
        AlertDialog(
            onDismissRequest = { deviceToDelete = null },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = StatusOffline,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "تأكيد حذف جهاز الشبكة",
                    color = TextPrimaryDark,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "هل أنت متأكد من حذف الجهاز: '${device.name}'؟",
                        color = TextPrimaryDark,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "عنوان الـ IP: ${device.ipAddress} • الموقع: ${device.towerLocation}",
                        color = TextSecondaryDark,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "لن يتم مسح الجهاز من السيرفر الفعلي إنما فقط من سجلات الإدارة هنا.",
                        color = TextMutedDark,
                        fontSize = 10.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            networkRepository.deleteDevice(device.id)
                            Toast.makeText(context, "تم حذف الجهاز '${device.name}' بنجاح", Toast.LENGTH_SHORT).show()
                            deviceToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusOffline)
                ) {
                    Text("حذف الجهاز", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { deviceToDelete = null }) {
                    Text("إلغاء", color = TextSecondaryDark)
                }
            },
            containerColor = CyberDarkSurface
        )
    }

    // JSON Backup Dialog
    if (showBackupDialog) {
        DeviceJsonBackupDialog(
            devices = devices,
            onImport = { json ->
                scope.launch {
                    val count = networkRepository.importDevicesFromJson(json)
                    Toast.makeText(context, "تم استيراد $count جهاز بنجاح", Toast.LENGTH_SHORT).show()
                }
            },
            onDismissRequest = { showBackupDialog = false }
        )
    }
}

@Composable
private fun StatusSummaryChip(
    label: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = CyberDarkCardElevated),
        border = BorderStroke(1.dp, CyberBorder),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = count.toString(),
                color = color,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Text(
                text = label,
                color = TextSecondaryDark,
                fontSize = 10.sp,
                maxLines = 1
            )
        }
    }
}
