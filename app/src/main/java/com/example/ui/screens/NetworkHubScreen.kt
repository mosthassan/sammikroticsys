package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.network.DeviceSaveResult
import com.example.data.network.DeviceStatus
import com.example.data.network.DeviceType
import com.example.data.network.NetworkConfig
import com.example.data.network.NetworkDevice
import com.example.data.network.NetworkRepository
import com.example.data.network.SubnetRange
import com.example.ui.components.DeviceJsonBackupDialog
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberDarkCanvas
import com.example.ui.theme.CyberDarkCardElevated
import com.example.ui.theme.CyberDarkSurface
import com.example.ui.theme.MikroTikCyan
import com.example.ui.theme.MikroTikNavy
import com.example.ui.theme.MikroTikPrimary
import com.example.ui.theme.SemanticExpenseRed
import com.example.ui.theme.StatusOffline
import com.example.ui.theme.StatusOnline
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.TextMutedDark
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableLongStateOf
import com.example.core.model.CurrencyCode
import com.example.core.model.ExchangeRate
import com.example.core.model.RateZone
import com.example.core.model.SignificantRateChangeException
import com.example.data.local.entity.CurrencyRateEntity
import com.example.ui.viewmodel.AppViewModel
import com.example.util.NetworkSecurityHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@Composable
fun NetworkHubScreen(
    networkRepository: NetworkRepository,
    viewModel: AppViewModel? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val config by networkRepository.config.collectAsState()
    val devices by networkRepository.devices.collectAsState()
    val subnets by networkRepository.subnets.collectAsState()

    var activeTab by remember { mutableStateOf(0) } // 0: Identity/Config, 1: Security, 2: Devices, 3: Subnets, 4: Rates Hub
    var showDeviceBackupDialog by remember { mutableStateOf(false) }
    var deviceToEdit by remember { mutableStateOf<NetworkDevice?>(null) }
    var isAddingDevice by remember { mutableStateOf(false) }
    var subnetToEdit by remember { mutableStateOf<SubnetRange?>(null) }
    var isAddingSubnet by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CyberDarkCanvas)
            .padding(16.dp)
    ) {
        // Hero Top Identity Card
        HeroNetworkCard(config = config, devicesCount = devices.size)

        Spacer(modifier = Modifier.height(14.dp))

        // Subtabs Navigation
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HubTabItem(
                label = "التهيئة والهوية",
                icon = Icons.Default.Settings,
                isSelected = activeTab == 0,
                onClick = { activeTab = 0 },
                modifier = Modifier.width(105.dp)
            )
            HubTabItem(
                label = "حماية الشبكة",
                icon = Icons.Default.Security,
                isSelected = activeTab == 1,
                onClick = { activeTab = 1 },
                modifier = Modifier.width(105.dp)
            )
            HubTabItem(
                label = "أجهزة الشبكة",
                icon = Icons.Default.Router,
                isSelected = activeTab == 2,
                onClick = { activeTab = 2 },
                modifier = Modifier.width(105.dp)
            )
            HubTabItem(
                label = "رنجات الشبكة",
                icon = Icons.Default.Lan,
                isSelected = activeTab == 3,
                onClick = { activeTab = 3 },
                modifier = Modifier.width(105.dp)
            )
            HubTabItem(
                label = "أسعار الصرف",
                icon = Icons.Default.CurrencyExchange,
                isSelected = activeTab == 4,
                onClick = { activeTab = 4 },
                modifier = Modifier.width(105.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Content
        Box(modifier = Modifier.weight(1f)) {
            when (activeTab) {
                0 -> NetworkConfigTab(
                    config = config,
                    onSave = { updated ->
                        scope.launch {
                            networkRepository.saveConfig(updated)
                            Toast.makeText(context, "تم حفظ بيانات تهيئة الشبكة بنجاح!", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                1 -> NetworkSecurityTab(config = config)
                2 -> NetworkDevicesTab(
                    devices = devices,
                    onAddDevice = { isAddingDevice = true },
                    onEditDevice = { deviceToEdit = it },
                    onDeleteDevice = { deviceId ->
                        scope.launch { networkRepository.deleteDevice(deviceId) }
                    },
                    onOpenBackup = { showDeviceBackupDialog = true }
                )
                3 -> NetworkSubnetsTab(
                    subnets = subnets,
                    onAddSubnet = { isAddingSubnet = true },
                    onEditSubnet = { subnetToEdit = it },
                    onDeleteSubnet = { subnetId ->
                        scope.launch { networkRepository.deleteSubnet(subnetId) }
                    }
                )
                4 -> RatesHubTab(
                    viewModel = viewModel
                )
            }
        }
    }

    if (showDeviceBackupDialog) {
        DeviceJsonBackupDialog(
            devices = devices,
            onImport = { json ->
                scope.launch {
                    networkRepository.importDevicesFromJson(json)
                }
            },
            onDismissRequest = { showDeviceBackupDialog = false }
        )
    }

    if (isAddingDevice || deviceToEdit != null) {
        DeviceEditDialog(
            device = deviceToEdit,
            existingDevices = devices,
            onSave = { savedDevice ->
                scope.launch {
                    val result = networkRepository.addOrUpdateDevice(savedDevice)
                    when (result) {
                        is DeviceSaveResult.Success -> {
                            isAddingDevice = false
                            deviceToEdit = null
                            Toast.makeText(context, "تم حفظ الجهاز '${savedDevice.name}' بنجاح", Toast.LENGTH_SHORT).show()
                        }
                        is DeviceSaveResult.IpConflict -> {
                            Toast.makeText(
                                context,
                                "تعذر الحفظ: الآي بي مستخدم مسبقاً للجهاز '${result.conflictingDevice.name}' بموقع '${result.conflictingDevice.towerLocation}'",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            },
            onDismiss = {
                isAddingDevice = false
                deviceToEdit = null
            }
        )
    }

    if (isAddingSubnet || subnetToEdit != null) {
        SubnetEditDialog(
            subnet = subnetToEdit,
            onSave = { savedSubnet ->
                scope.launch {
                    networkRepository.addOrUpdateSubnet(savedSubnet)
                    isAddingSubnet = false
                    subnetToEdit = null
                }
            },
            onDismiss = {
                isAddingSubnet = false
                subnetToEdit = null
            }
        )
    }
}

@Composable
private fun HeroNetworkCard(config: NetworkConfig, devicesCount: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CyberDarkCardElevated),
        border = BorderStroke(1.dp, CyberBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(StatusOnline)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        config.networkName,
                        color = TextPrimaryDark,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "المسؤول: ${config.ownerName} • ${config.location}",
                    color = TextSecondaryDark,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "السيرفر: ${config.mainRouterModel} • نطاق: ${config.approvedSubnet}",
                    color = MikroTikCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MikroTikPrimary.copy(alpha = 0.2f))
                    .border(BorderStroke(1.dp, MikroTikPrimary.copy(alpha = 0.4f)), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$devicesCount", color = MikroTikCyan, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("أجهزة متصلة", color = TextSecondaryDark, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun HubTabItem(
    label: String,
    icon: ImageVector,
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
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (isSelected) Color.White else TextSecondaryDark,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                label,
                color = if (isSelected) Color.White else TextSecondaryDark,
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

// ==========================================
// Tab 1: Network Configuration
// ==========================================
@Composable
private fun NetworkConfigTab(
    config: NetworkConfig,
    onSave: (NetworkConfig) -> Unit
) {
    var networkName by remember(config) { mutableStateOf(config.networkName) }
    var ownerName by remember(config) { mutableStateOf(config.ownerName) }
    var location by remember(config) { mutableStateOf(config.location) }
    var welcomeMsg by remember(config) { mutableStateOf(config.welcomeMessage) }
    var supportPhone by remember(config) { mutableStateOf(config.supportPhone) }
    var supportWhatsapp by remember(config) { mutableStateOf(config.supportWhatsapp) }
    var routerModel by remember(config) { mutableStateOf(config.mainRouterModel) }
    var routerOsVersion by remember(config) { mutableStateOf(config.routerOsVersion) }
    var hotspotDomain by remember(config) { mutableStateOf(config.hotspotDomain) }
    var primaryDns by remember(config) { mutableStateOf(config.primaryDns) }
    var secondaryDns by remember(config) { mutableStateOf(config.secondaryDns) }
    var approvedSubnet by remember(config) { mutableStateOf(config.approvedSubnet) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CyberDarkCardElevated),
                border = BorderStroke(1.dp, CyberBorder),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("بيانات وهوية الشبكة الرسمية", color = TextPrimaryDark, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    HubTextField(label = "اسم الشبكة الرسمي", value = networkName, onValueChange = { networkName = it })
                    Spacer(modifier = Modifier.height(8.dp))
                    HubTextField(label = "اسم مالك الشبكة / المهندس", value = ownerName, onValueChange = { ownerName = it })
                    Spacer(modifier = Modifier.height(8.dp))
                    HubTextField(label = "الموقع ومقر السيرفرات", value = location, onValueChange = { location = it })
                    Spacer(modifier = Modifier.height(8.dp))
                    HubTextField(label = "رسالة الترحيب / الإعلان", value = welcomeMsg, onValueChange = { welcomeMsg = it })
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CyberDarkCardElevated),
                border = BorderStroke(1.dp, CyberBorder),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("قنوات الاتصال والدعم الفني", color = TextPrimaryDark, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    HubTextField(label = "رقم هاتف الدعم الفني", value = supportPhone, onValueChange = { supportPhone = it })
                    Spacer(modifier = Modifier.height(8.dp))
                    HubTextField(label = "رقم واتساب الشبكة", value = supportWhatsapp, onValueChange = { supportWhatsapp = it })
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CyberDarkCardElevated),
                border = BorderStroke(1.dp, CyberBorder),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("مواصفات السيرفر والهوتسبوت", color = TextPrimaryDark, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    HubTextField(label = "موديل راوتر ميكروتك الرئيسي", value = routerModel, onValueChange = { routerModel = it })
                    Spacer(modifier = Modifier.height(8.dp))
                    HubTextField(label = "إصدار RouterOS", value = routerOsVersion, onValueChange = { routerOsVersion = it })
                    Spacer(modifier = Modifier.height(8.dp))
                    HubTextField(label = "رابط صفحة تسجيل الدخول (DNS Name)", value = hotspotDomain, onValueChange = { hotspotDomain = it })
                    Spacer(modifier = Modifier.height(8.dp))
                    HubTextField(label = "رينج أجهزة الإدارة المعتمد (Subnet)", value = approvedSubnet, onValueChange = { approvedSubnet = it })
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HubTextField(label = "DNS أساسي", value = primaryDns, onValueChange = { primaryDns = it }, modifier = Modifier.weight(1f))
                        HubTextField(label = "DNS بديل", value = secondaryDns, onValueChange = { secondaryDns = it }, modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        item {
            Button(
                onClick = {
                    onSave(
                        config.copy(
                            networkName = networkName,
                            ownerName = ownerName,
                            location = location,
                            welcomeMessage = welcomeMsg,
                            supportPhone = supportPhone,
                            supportWhatsapp = supportWhatsapp,
                            mainRouterModel = routerModel,
                            routerOsVersion = routerOsVersion,
                            hotspotDomain = hotspotDomain,
                            approvedSubnet = approvedSubnet,
                            primaryDns = primaryDns,
                            secondaryDns = secondaryDns
                        )
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MikroTikPrimary)
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("حفظ بيانات تهيئة الشبكة", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ==========================================
// Tab 2: Network Security & Hardening
// ==========================================
@Composable
private fun NetworkSecurityTab(config: NetworkConfig) {
    val context = LocalContext.current
    var options by remember { mutableStateOf(NetworkSecurityHelper.SecurityOptions()) }
    val generatedScript = remember(config, options) {
        NetworkSecurityHelper.generateRouterOsHardeningScript(config, options)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CyberDarkCardElevated),
                border = BorderStroke(1.dp, CyberBorder),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = MikroTikCyan, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("خيارات تأمين جدار الحماية وسيرفر ميكروتك", color = TextPrimaryDark, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    SecurityToggle(
                        title = "إيقاف الخدمات غير المشفرة (Telnet, FTP, WWW)",
                        subtitle = "حصر الدخول للراوتر عبر منفذ Winbox الآمن فقط",
                        checked = options.disableInsecureServices,
                        onCheckedChange = { options = options.copy(disableInsecureServices = it) }
                    )
                    SecurityToggle(
                        title = "كشف ومنع فاحصي المنافذ (Port Scanners)",
                        subtitle = "حظر تلقائي لأي عنوان يقوم بمسح المنافذ لمدة 14 يوماً",
                        checked = options.blockPortScan,
                        onCheckedChange = { options = options.copy(blockPortScan = it) }
                    )
                    SecurityToggle(
                        title = "صد هجمات التخمين على Winbox (Brute Force)",
                        subtitle = "قائمة سوداء وحظر تلقائي بعد 3 محاولات فاشلة",
                        checked = options.blockBruteForceWinbox,
                        onCheckedChange = { options = options.copy(blockBruteForceWinbox = it) }
                    )
                    SecurityToggle(
                        title = "منع هجمات تسميم الـ DNS (Cache Poisoning)",
                        subtitle = "منع استعلامات DNS الخارجية وحظر استغلال السيرفر",
                        checked = options.preventDnsPoisoning,
                        onCheckedChange = { options = options.copy(preventDnsPoisoning = it) }
                    )
                    SecurityToggle(
                        title = "عزل المشتركين في الهوتسبوت (Client Isolation)",
                        subtitle = "تفعيل Horizon لمنع اختراق المشتركين لهواتف بعضهم",
                        checked = options.enableClientIsolation,
                        onCheckedChange = { options = options.copy(enableClientIsolation = it) }
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CyberDarkSurface),
                border = BorderStroke(1.dp, CyberBorder),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("سكربت MikroTik RouterOS v7 المُولد:", color = MikroTikCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            IconButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("RouterOS Script", generatedScript))
                                    Toast.makeText(context, "تم نسخ سكربت الحماية بنجاح!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = MikroTikCyan, modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = {
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "سكربت حماية راوتر ميكروتك")
                                        putExtra(Intent.EXTRA_TEXT, generatedScript)
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "مشاركة سكربت الحماية"))
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, tint = TextSecondaryDark, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CyberDarkCanvas),
                        border = BorderStroke(1.dp, CyberBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            generatedScript,
                            color = TextPrimaryDark,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SecurityToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = MikroTikCyan,
                checkmarkColor = CyberDarkCanvas
            )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimaryDark, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = TextSecondaryDark, fontSize = 11.sp)
        }
    }
}

// ==========================================
// Tab 3: Network Devices & Antennas
// ==========================================
@Composable
private fun NetworkDevicesTab(
    devices: List<NetworkDevice>,
    onAddDevice: () -> Unit,
    onEditDevice: (NetworkDevice) -> Unit,
    onDeleteDevice: (String) -> Unit,
    onOpenBackup: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onAddDevice,
                colors = ButtonDefaults.buttonColors(containerColor = MikroTikPrimary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("إضافة جهاز جديد", fontSize = 12.sp)
            }

            OutlinedButton(
                onClick = onOpenBackup,
                border = BorderStroke(1.dp, CyberBorder),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.SwapVert, contentDescription = null, tint = MikroTikCyan, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("تصدير / استيراد JSON", color = MikroTikCyan, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (devices.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("لا توجد أجهزة مسجلة في الشبكة حالياً.", color = TextMutedDark)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(devices) { device ->
                    DeviceCard(
                        device = device,
                        onEdit = { onEditDevice(device) },
                        onDelete = { onDeleteDevice(device.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceCard(
    device: NetworkDevice,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val statusColor = when (device.status) {
        DeviceStatus.ONLINE -> StatusOnline
        DeviceStatus.WARNING -> StatusWarning
        DeviceStatus.OFFLINE -> StatusOffline
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CyberDarkCardElevated),
        border = BorderStroke(1.dp, CyberBorder),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = 0.15f))
                    .border(BorderStroke(1.dp, statusColor.copy(alpha = 0.5f)), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    when (device.deviceType) {
                        DeviceType.ACCESS_POINT -> Icons.Default.WifiTethering
                        DeviceType.ROUTER -> Icons.Default.Router
                        DeviceType.STATION -> Icons.Default.Wifi
                        DeviceType.SWITCH -> Icons.Default.Lan
                        DeviceType.ANTENNA -> Icons.Default.Public
                    },
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(device.name, color = TextPrimaryDark, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(statusColor.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(device.status.labelArabic, color = statusColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "IP: ${device.ipAddress} • التردد: ${device.frequency} • ${device.towerLocation}",
                    color = TextSecondaryDark,
                    fontSize = 11.sp
                )
                if (device.notes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(device.notes, color = TextMutedDark, fontSize = 10.sp)
                }
            }

            Row {
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = MikroTikCyan, modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = StatusOffline, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

// ==========================================
// Tab 4: Subnets & IP Ranges
// ==========================================
@Composable
private fun NetworkSubnetsTab(
    subnets: List<SubnetRange>,
    onAddSubnet: () -> Unit,
    onEditSubnet: (SubnetRange) -> Unit,
    onDeleteSubnet: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("رنجات الشبكة وتقسيم العناوين (Subnets)", color = TextPrimaryDark, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Button(
                onClick = onAddSubnet,
                colors = ButtonDefaults.buttonColors(containerColor = MikroTikPrimary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("إضافة رينج", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(subnets) { subnet ->
                SubnetCard(
                    subnet = subnet,
                    onEdit = { onEditSubnet(subnet) },
                    onDelete = { onDeleteSubnet(subnet.id) }
                )
            }
        }
    }
}

@Composable
private fun SubnetCard(
    subnet: SubnetRange,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CyberDarkCardElevated),
        border = BorderStroke(1.dp, CyberBorder),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(subnet.name, color = TextPrimaryDark, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MikroTikPrimary.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(subnet.cidr, color = MikroTikCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text("بوابة العبور (Gateway): ${subnet.gateway}", color = TextSecondaryDark, fontSize = 11.sp)
            Text("نطاق الـ DHCP: من ${subnet.dhcpRangeStart} إلى ${subnet.dhcpRangeEnd}", color = TextMutedDark, fontSize = 11.sp)
            Text("الغرض: ${subnet.purpose}", color = StatusOnline, fontSize = 11.sp, fontWeight = FontWeight.Medium)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = MikroTikCyan, modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = StatusOffline, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun HubTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: @Composable (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 11.sp) },
        modifier = modifier.fillMaxWidth(),
        isError = isError,
        supportingText = supportingText,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = if (isError) SemanticExpenseRed else MikroTikCyan,
            unfocusedBorderColor = if (isError) SemanticExpenseRed else CyberBorder,
            errorBorderColor = SemanticExpenseRed,
            focusedTextColor = TextPrimaryDark,
            unfocusedTextColor = TextPrimaryDark,
            focusedLabelColor = if (isError) SemanticExpenseRed else MikroTikCyan,
            unfocusedLabelColor = if (isError) SemanticExpenseRed else TextSecondaryDark,
            errorLabelColor = SemanticExpenseRed,
            focusedContainerColor = CyberDarkCanvas,
            unfocusedContainerColor = CyberDarkCanvas
        ),
        shape = RoundedCornerShape(10.dp)
    )
}

// Dialog for Add/Edit Device
@Composable
private fun DeviceEditDialog(
    device: NetworkDevice?,
    existingDevices: List<NetworkDevice>,
    onSave: (NetworkDevice) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(device?.name ?: "") }
    var ip by remember { mutableStateOf(device?.ipAddress ?: "10.10.1.") }
    var mac by remember { mutableStateOf(device?.macAddress ?: "") }
    var tower by remember { mutableStateOf(device?.towerLocation ?: "البرج الرئيسي") }
    var freq by remember { mutableStateOf(device?.frequency ?: "5500 MHz") }
    var notes by remember { mutableStateOf(device?.notes ?: "") }
    var type by remember { mutableStateOf(device?.deviceType ?: DeviceType.ACCESS_POINT) }
    var status by remember { mutableStateOf(device?.status ?: DeviceStatus.ONLINE) }

    // Real-time IP conflict check against all other registered network devices
    val conflictingDevice = remember(ip, device, existingDevices) {
        val cleanIp = ip.trim()
        if (cleanIp.isBlank() || cleanIp == "10.10.1.") null
        else existingDevices.firstOrNull {
            it.id != device?.id && it.ipAddress.trim().equals(cleanIp, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (device == null) "إضافة جهاز شبكة جديد" else "تعديل جهاز الشبكة", color = TextPrimaryDark) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HubTextField(label = "اسم الجهاز (مثال: سيكتور شمالي)", value = name, onValueChange = { name = it })
                
                HubTextField(
                    label = "عنوان الآي بي (IP Address)",
                    value = ip,
                    onValueChange = { ip = it },
                    isError = conflictingDevice != null,
                    supportingText = if (conflictingDevice != null) {
                        {
                            Text(
                                "❌ هذا الآي بي محجوز مسبقاً لجهاز آخر",
                                color = SemanticExpenseRed,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else null
                )

                // Guidance message detailing the existing device and its location
                if (conflictingDevice != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = SemanticExpenseRed.copy(alpha = 0.15f)),
                        border = BorderStroke(1.dp, SemanticExpenseRed.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = SemanticExpenseRed,
                                modifier = Modifier.size(24.dp).padding(top = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    "تنبيه: تعارض في عنوان الآي بي!",
                                    color = SemanticExpenseRed,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Text(
                                    "الجهاز المسجل: ${conflictingDevice.name}",
                                    color = TextPrimaryDark,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp
                                )
                                Text(
                                    "الموقع / البرج: ${conflictingDevice.towerLocation}",
                                    color = MikroTikCyan,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 11.sp
                                )
                                Text(
                                    "نوع الجهاز: ${conflictingDevice.deviceType.labelArabic}",
                                    color = TextSecondaryDark,
                                    fontSize = 10.sp
                                )
                                Text(
                                    "⚠️ يجب أن يكون عنوان الآي بي فريداً لتجنب توقف وتضارب شبكة التوزيع.",
                                    color = TextMutedDark,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }

                HubTextField(label = "عنوان الماك (MAC Address)", value = mac, onValueChange = { mac = it })
                HubTextField(label = "موقع البرج / المحطة", value = tower, onValueChange = { tower = it })
                HubTextField(label = "التردد والقناة (مثال: 5500 MHz)", value = freq, onValueChange = { freq = it })
                HubTextField(label = "ملاحظات", value = notes, onValueChange = { notes = it })
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && ip.isNotBlank() && conflictingDevice == null) {
                        val d = (device ?: NetworkDevice(name = name, ipAddress = ip)).copy(
                            name = name,
                            ipAddress = ip.trim(),
                            macAddress = mac.trim(),
                            towerLocation = tower.trim(),
                            frequency = freq.trim(),
                            notes = notes.trim(),
                            deviceType = type,
                            status = status
                        )
                        onSave(d)
                    }
                },
                enabled = name.isNotBlank() && ip.isNotBlank() && conflictingDevice == null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MikroTikPrimary,
                    disabledContainerColor = CyberBorder
                )
            ) {
                Text("حفظ الجهاز")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("إلغاء", color = TextSecondaryDark)
            }
        },
        containerColor = CyberDarkSurface
    )
}

// Dialog for Add/Edit Subnet
@Composable
private fun SubnetEditDialog(
    subnet: SubnetRange?,
    onSave: (SubnetRange) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(subnet?.name ?: "") }
    var cidr by remember { mutableStateOf(subnet?.cidr ?: "10.10.0.0/16") }
    var gw by remember { mutableStateOf(subnet?.gateway ?: "10.10.0.1") }
    var start by remember { mutableStateOf(subnet?.dhcpRangeStart ?: "10.10.1.1") }
    var end by remember { mutableStateOf(subnet?.dhcpRangeEnd ?: "10.10.254.254") }
    var purpose by remember { mutableStateOf(subnet?.purpose ?: "Hotspot / APs") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (subnet == null) "إضافة رينج شبكة جديد" else "تعديل رينج الشبكة", color = TextPrimaryDark) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HubTextField(label = "اسم الرينج", value = name, onValueChange = { name = it })
                HubTextField(label = "عنوان الـ CIDR (مثال: 10.10.0.0/16)", value = cidr, onValueChange = { cidr = it })
                HubTextField(label = "بوابة العبور (Gateway)", value = gw, onValueChange = { gw = it })
                HubTextField(label = "بداية نطاق الـ DHCP", value = start, onValueChange = { start = it })
                HubTextField(label = "نهاية نطاق الـ DHCP", value = end, onValueChange = { end = it })
                HubTextField(label = "الغرض / الاستخدام", value = purpose, onValueChange = { purpose = it })
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && cidr.isNotBlank()) {
                        val s = (subnet ?: SubnetRange(name = name, cidr = cidr, gateway = gw, dhcpRangeStart = start, dhcpRangeEnd = end, purpose = purpose)).copy(
                            name = name,
                            cidr = cidr,
                            gateway = gw,
                            dhcpRangeStart = start,
                            dhcpRangeEnd = end,
                            purpose = purpose
                        )
                        onSave(s)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MikroTikPrimary)
            ) {
                Text("حفظ الرينج")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("إلغاء", color = TextSecondaryDark)
            }
        },
        containerColor = CyberDarkSurface
    )
}

@Composable
private fun RatesHubTab(
    viewModel: AppViewModel?,
    modifier: Modifier = Modifier
) {
    val org by (viewModel?.organization ?: remember { MutableStateFlow(null) }).collectAsState()
    val allRates by (viewModel?.allRates ?: remember { MutableStateFlow(emptyList()) }).collectAsState()
    val todayEpoch = remember { java.time.LocalDate.now().toEpochDay() }

    val primaryZone = org?.primaryRateZone ?: "SANAA"

    // Group rates for USD and SAR by zone
    val usdSanaa = remember(allRates) {
        allRates.filter { it.currency == "USD" && it.zone == "SANAA" }
            .maxByOrNull { it.effectiveDateEpochDay }
    }
    val usdAden = remember(allRates) {
        allRates.filter { it.currency == "USD" && it.zone == "ADEN" }
            .maxByOrNull { it.effectiveDateEpochDay }
    }
    val sarSanaa = remember(allRates) {
        allRates.filter { it.currency == "SAR" && it.zone == "SANAA" }
            .maxByOrNull { it.effectiveDateEpochDay }
    }
    val sarAden = remember(allRates) {
        allRates.filter { it.currency == "SAR" && it.zone == "ADEN" }
            .maxByOrNull { it.effectiveDateEpochDay }
    }

    // Alerts
    val isAnyRateOlderThan7Days = remember(usdSanaa, usdAden, sarSanaa, sarAden) {
        listOfNotNull(usdSanaa, usdAden, sarSanaa, sarAden).any {
            (todayEpoch - it.effectiveDateEpochDay) > 7
        }
    }

    val isUsdDivergenceHigh = remember(usdSanaa, usdAden) {
        if (usdSanaa != null && usdAden != null && usdSanaa.rateMicros > 0L && usdAden.rateMicros > 0L) {
            val diff = kotlin.math.abs(usdSanaa.rateMicros - usdAden.rateMicros)
            val minRate = minOf(usdSanaa.rateMicros, usdAden.rateMicros)
            diff * 2L > minRate // > 50%
        } else false
    }

    var showUpdateDialog by remember { mutableStateOf(false) }
    var selectedCurrencyForUpdate by remember { mutableStateOf(CurrencyCode.USD) }
    var selectedZoneForUpdate by remember { mutableStateOf(RateZone.SANAA) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Permanent Disclaimer Banner
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MikroTikNavy.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, MikroTikCyan.copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MikroTikCyan,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "تنبيه: تحديثات أسعار الصرف تسري على السندات والفواتير الجديدة فقط، وتبقى القيود والمعاملات التاريخية السابقة بسعرها التاريخي دون تغيير.",
                    color = TextPrimaryDark,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }

        // Warning banner if active rate is older than 7 days
        if (isAnyRateOlderThan7Days) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = StatusWarning.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, StatusWarning.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = StatusWarning,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "تحذير: توجد أسعار صرف نشطة لم يتم تحديثها منذ أكثر من 7 أيام. يُنصح بمراجعة وتحديث أسعار الصرف وفق السوق اليومي.",
                        color = StatusWarning,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // Warning banner if USD rates between Sana'a and Aden diverge by > 50%
        if (isUsdDivergenceHigh) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = SemanticExpenseRed.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, SemanticExpenseRed.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = SemanticExpenseRed,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "تحذير: يوجد فارق تجاوز 50% بين سعر صرف الدولار الأمريكي في صنعاء وعدن. يُرجى التحقق من صحة الأسعار المدخلة.",
                        color = SemanticExpenseRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // Header and New Rate Action Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "مركز أسعار الصرف والعملات",
                color = TextPrimaryDark,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Button(
                onClick = {
                    selectedCurrencyForUpdate = CurrencyCode.USD
                    selectedZoneForUpdate = RateZone.valueOf(primaryZone)
                    showUpdateDialog = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = MikroTikPrimary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("تحديث سعر الصرف", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        // USD Dual Rates Section
        CurrencyDualRatesSection(
            currency = CurrencyCode.USD,
            currencyLabel = "الدولار الأمريكي (USD)",
            sanaaRate = usdSanaa,
            adenRate = usdAden,
            primaryZone = primaryZone,
            onUpdateRate = { zone ->
                selectedCurrencyForUpdate = CurrencyCode.USD
                selectedZoneForUpdate = zone
                showUpdateDialog = true
            }
        )

        Spacer(modifier = Modifier.height(4.dp))

        // SAR Dual Rates Section
        CurrencyDualRatesSection(
            currency = CurrencyCode.SAR,
            currencyLabel = "الريال السعودي (SAR)",
            sanaaRate = sarSanaa,
            adenRate = sarAden,
            primaryZone = primaryZone,
            onUpdateRate = { zone ->
                selectedCurrencyForUpdate = CurrencyCode.SAR
                selectedZoneForUpdate = zone
                showUpdateDialog = true
            }
        )
    }

    if (showUpdateDialog && viewModel != null) {
        UpdateRateDialog(
            viewModel = viewModel,
            initialCurrency = selectedCurrencyForUpdate,
            initialZone = selectedZoneForUpdate,
            onDismiss = { showUpdateDialog = false }
        )
    }
}

@Composable
private fun CurrencyDualRatesSection(
    currency: CurrencyCode,
    currencyLabel: String,
    sanaaRate: CurrencyRateEntity?,
    adenRate: CurrencyRateEntity?,
    primaryZone: String,
    onUpdateRate: (RateZone) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CyberDarkCardElevated),
        border = BorderStroke(1.dp, CyberBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = currencyLabel,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MikroTikCyan
                )
                Text(
                    text = "العملة المقابلة: ريال يمني (YER)",
                    fontSize = 11.sp,
                    color = TextSecondaryDark
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RateZoneCard(
                    zone = RateZone.SANAA,
                    currency = currency,
                    rateEntity = sanaaRate,
                    isPrimary = primaryZone == "SANAA",
                    onUpdate = { onUpdateRate(RateZone.SANAA) },
                    modifier = Modifier.weight(1f)
                )

                RateZoneCard(
                    zone = RateZone.ADEN,
                    currency = currency,
                    rateEntity = adenRate,
                    isPrimary = primaryZone == "ADEN",
                    onUpdate = { onUpdateRate(RateZone.ADEN) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun RateZoneCard(
    zone: RateZone,
    currency: CurrencyCode,
    rateEntity: CurrencyRateEntity?,
    isPrimary: Boolean,
    onUpdate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = CyberDarkSurface,
        border = BorderStroke(
            1.dp,
            if (isPrimary) MikroTikCyan.copy(alpha = 0.6f) else CyberBorder
        )
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = zone.arabicName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = TextPrimaryDark
                )
                if (isPrimary) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MikroTikPrimary.copy(alpha = 0.25f),
                        border = BorderStroke(1.dp, MikroTikCyan.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "المنطقة الرئيسية",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MikroTikCyan,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            val rateText = if (rateEntity != null && rateEntity.rateMicros > 0L) {
                ExchangeRate.formatRateMicros(rateEntity.rateMicros)
            } else "غير محدد"

            Text(
                text = "1 ${currency.name} = $rateText YER",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = if (rateEntity != null) MikroTikCyan else StatusWarning
            )

            if (rateEntity != null) {
                val effectiveDateStr = try {
                    java.time.LocalDate.ofEpochDay(rateEntity.effectiveDateEpochDay).toString()
                } catch (e: Exception) {
                    rateEntity.effectiveDateEpochDay.toString()
                }
                val createdTimeStr = try {
                    val dt = java.time.Instant.ofEpochMilli(rateEntity.createdAt)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDateTime()
                    dt.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                } catch (e: Exception) {
                    ""
                }

                Text(
                    text = "تاريخ السريان: $effectiveDateStr",
                    fontSize = 10.sp,
                    color = TextSecondaryDark
                )
                Text(
                    text = "المسؤول: ${rateEntity.createdBy}",
                    fontSize = 10.sp,
                    color = TextMutedDark
                )
                if (createdTimeStr.isNotBlank()) {
                    Text(
                        text = "التسجيل: $createdTimeStr",
                        fontSize = 10.sp,
                        color = TextMutedDark
                    )
                }
                if (rateEntity.reason.isNotBlank()) {
                    Text(
                        text = "السبب: ${rateEntity.reason}",
                        fontSize = 10.sp,
                        color = TextMutedDark,
                        maxLines = 1
                    )
                }
            } else {
                Text(
                    text = "لا يوجد سعر مسجل لهذه المنطقة",
                    fontSize = 10.sp,
                    color = TextMutedDark
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            OutlinedButton(
                onClick = onUpdate,
                modifier = Modifier.fillMaxWidth().height(32.dp),
                shape = RoundedCornerShape(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text("تحديث السعر", fontSize = 11.sp, color = TextPrimaryDark)
            }
        }
    }
}

@Composable
private fun UpdateRateDialog(
    viewModel: AppViewModel,
    initialCurrency: CurrencyCode,
    initialZone: RateZone,
    onDismiss: () -> Unit
) {
    val todayEpoch = remember { java.time.LocalDate.now().toEpochDay() }
    var currency by remember { mutableStateOf(initialCurrency) }
    var zone by remember { mutableStateOf(initialZone) }
    var rateInputText by remember { mutableStateOf("") }
    var reasonText by remember { mutableStateOf("") }
    var effectiveDateEpochDay by remember { mutableLongStateOf(todayEpoch) }

    var significantChangeEx by remember { mutableStateOf<SignificantRateChangeException?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val parsedRateMicros = remember(rateInputText) {
        ExchangeRate.parseRateFromUserInput(rateInputText)
    }

    if (significantChangeEx != null) {
        val ex = significantChangeEx!!
        AlertDialog(
            onDismissRequest = { significantChangeEx = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = StatusWarning)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("تأكيد فارق السعر الكبير (> 10%)", color = StatusWarning, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val percentStr = String.format(java.util.Locale.US, "%.1f", ex.percentChange)
                    Text(
                        text = "السعر الجديد يختلف بنسبة $percentStr% عن السعر السابق المسجل.",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "السعر السابق: 1 ${currency.name} = ${ExchangeRate.formatRateMicros(ex.oldRateMicros)} YER\nالسعر المقترح: 1 ${currency.name} = ${ExchangeRate.formatRateMicros(ex.newRateMicros)} YER",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "هل ترغب في تأكيد واعتماد هذا السعر بشكل استثنائي؟",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val micros = parsedRateMicros ?: return@Button
                        viewModel.addExchangeRate(
                            currency = currency,
                            zone = zone,
                            rateMicros = micros,
                            effectiveDateEpochDay = effectiveDateEpochDay,
                            createdBy = "USER",
                            reason = reasonText.ifBlank { "تحديث سعر الصرف بتأكيد فارق" },
                            confirmSignificantChange = true,
                            onSuccess = {
                                significantChangeEx = null
                                onDismiss()
                            },
                            onError = { err ->
                                errorMessage = err.message
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusWarning)
                ) {
                    Text("نعم، تأكيد وحفظ السعر", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { significantChangeEx = null }) {
                    Text("تراجع وتعديل السعر")
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CurrencyExchange, contentDescription = null, tint = MikroTikCyan)
                Spacer(modifier = Modifier.width(8.dp))
                Text("تحديث سعر الصرف الرسمي", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("العملة الأجنبية:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(CurrencyCode.USD, CurrencyCode.SAR).forEach { c ->
                        FilterChip(
                            selected = currency == c,
                            onClick = { currency = c },
                            label = { Text(c.name) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MikroTikPrimary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                Text("النطاق الجغرافي / المنطقة:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(RateZone.SANAA, RateZone.ADEN).forEach { z ->
                        FilterChip(
                            selected = zone == z,
                            onClick = { zone = z },
                            label = { Text(z.arabicName) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MikroTikPrimary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                OutlinedTextField(
                    value = rateInputText,
                    onValueChange = {
                        rateInputText = it
                        errorMessage = null
                    },
                    label = { Text("سعر الصرف مقابل الريال اليمني (YER)") },
                    placeholder = { Text("مثال: 535 أو 535.50") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Live Preview
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = CyberDarkCardElevated,
                    border = BorderStroke(1.dp, if (parsedRateMicros != null) MikroTikCyan.copy(alpha = 0.5f) else CyberBorder)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("المعاينة الفورية:", fontSize = 11.sp, color = TextSecondaryDark)
                        if (parsedRateMicros != null) {
                            Text(
                                "1 ${currency.name} = ${ExchangeRate.formatRateMicros(parsedRateMicros)} YER",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MikroTikCyan
                            )
                        } else {
                            Text(
                                if (rateInputText.isBlank()) "أدخل السعر لمعاينة الناتج" else "تنسيق السعر غير صالح (أرقام فقط، بحد أقصى 6 خانات عشرية)",
                                fontSize = 11.sp,
                                color = if (rateInputText.isBlank()) TextMutedDark else SemanticExpenseRed
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = reasonText,
                    onValueChange = { reasonText = it },
                    label = { Text("سبب التحديث / المرجع (اختياري)") },
                    placeholder = { Text("مثال: إغلاق السوق اليومي") },
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = SemanticExpenseRed,
                        fontSize = 11.sp
                    )
                }

                Text(
                    text = "ملاحظة: السعر المسجل سيُطبق على المستندات الجديدة حصراً ولا يؤثر على العمليات المؤرشفة.",
                    fontSize = 10.sp,
                    color = TextMutedDark
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val micros = parsedRateMicros ?: return@Button
                    viewModel.addExchangeRate(
                        currency = currency,
                        zone = zone,
                        rateMicros = micros,
                        effectiveDateEpochDay = effectiveDateEpochDay,
                        createdBy = "USER",
                        reason = reasonText.ifBlank { "تحديث سعر الصرف اليومي" },
                        confirmSignificantChange = false,
                        onSuccess = {
                            onDismiss()
                        },
                        onError = { err ->
                            if (err is SignificantRateChangeException) {
                                significantChangeEx = err
                            } else {
                                errorMessage = err.message
                            }
                        }
                    )
                },
                enabled = parsedRateMicros != null && parsedRateMicros > 0L,
                colors = ButtonDefaults.buttonColors(containerColor = MikroTikPrimary)
            ) {
                Text("حفظ السعر واعتماده")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}
