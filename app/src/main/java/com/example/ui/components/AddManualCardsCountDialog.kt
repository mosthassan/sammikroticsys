package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.CurrencyCode
import com.example.core.model.Money
import com.example.data.local.entity.CardPackageEntity
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberDarkCardElevated
import com.example.ui.theme.CyberDarkSurface
import com.example.ui.theme.MikroTikCyan
import com.example.ui.theme.ReceiptGreen
import com.example.ui.theme.StatusOnline
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.TextMutedDark
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark

/**
 * 2026 Mobile Standard "Add Manual Cards Count" Sheet & Dialog
 * Features:
 * 1. Robust state retention across backgrounding/app-switching (rememberSaveable).
 * 2. Perfect keyboard handling: imePadding, navigationBarsPadding, verticalScroll.
 * 3. Sticky bottom action bar so primary action button stays visible above keyboard.
 * 4. Single-line non-wrapping chip design for package selection.
 * 5. Instant real-time wholesale/retail/margin recalculation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddManualCardsCountDialog(
    packages: List<CardPackageEntity>,
    onDismiss: () -> Unit,
    onSubmit: (packageId: String, quantity: Int, notes: String) -> Unit
) {
    // Persistent UI State surviving configuration changes and app switching (e.g. to WhatsApp)
    var selectedPackageId by rememberSaveable { mutableStateOf(packages.firstOrNull()?.id ?: "") }
    var qtyText by rememberSaveable { mutableStateOf("1000") }
    var notes by rememberSaveable { mutableStateOf("") }

    // If the package list loads dynamically, ensure a valid selection
    if (selectedPackageId.isBlank() && packages.isNotEmpty()) {
        selectedPackageId = packages.first().id
    }

    val selectedPkg = remember(packages, selectedPackageId) {
        packages.firstOrNull { it.id == selectedPackageId } ?: packages.firstOrNull()
    }

    val quantity = remember(qtyText) {
        qtyText.toIntOrNull()?.coerceAtLeast(0) ?: 0
    }

    // Calculations based on accounting package rates
    val wholesaleUnitMinor = selectedPkg?.wholesalePriceMinor ?: 0L
    val retailUnitMinor = selectedPkg?.retailPriceMinor ?: 0L
    val totalWholesaleMinor by remember(wholesaleUnitMinor, quantity) {
        derivedStateOf { wholesaleUnitMinor * quantity }
    }
    val totalRetailMinor by remember(retailUnitMinor, quantity) {
        derivedStateOf { retailUnitMinor * quantity }
    }
    val profitMinor by remember(totalRetailMinor, totalWholesaleMinor) {
        derivedStateOf { totalRetailMinor - totalWholesaleMinor }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollState = rememberScrollState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CyberDarkSurface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
        ) {
            // Header Bar (Fixed)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(StatusOnline.copy(alpha = 0.15f))
                            .border(BorderStroke(1.dp, StatusOnline.copy(alpha = 0.4f)), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "#",
                            color = StatusOnline,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "إضافة كروت يدوياً بالعدد",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryDark
                        )
                        Text(
                            text = "إضافة رصيد كروت للمخزن بدون اشتراط أرقام الكروت",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondaryDark,
                            fontSize = 11.sp
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "إغلاق",
                        tint = TextSecondaryDark
                    )
                }
            }

            HorizontalDivider(color = CyberBorder)

            // Scrollable Form Content (Scales and scrolls above keyboard)
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Package Selection Section
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "1. فئة الكرت / الباقة المعتمدة:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = TextPrimaryDark
                    )

                    // Horizontal scrolling single-line chips (Prevents vertical character breaking)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(packages) { pkg ->
                            val isSelected = pkg.id == selectedPackageId
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedPackageId = pkg.id },
                                label = {
                                    Text(
                                        text = pkg.name,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 12.sp
                                    )
                                },
                                leadingIcon = if (isSelected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = StatusOnline
                                        )
                                    }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = StatusOnline.copy(alpha = 0.15f),
                                    selectedLabelColor = StatusOnline,
                                    containerColor = CyberDarkCardElevated,
                                    labelColor = TextSecondaryDark
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = if (isSelected) StatusOnline else CyberBorder,
                                    selectedBorderColor = StatusOnline,
                                    borderWidth = if (isSelected) 1.5.dp else 1.dp
                                ),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }

                    // Package Name Readout
                    OutlinedTextField(
                        value = selectedPkg?.name ?: "لم يتم اختيار باقة",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("اسم الفئة المختارة") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MikroTikCyan,
                            unfocusedBorderColor = CyberBorder,
                            focusedTextColor = TextPrimaryDark,
                            unfocusedTextColor = TextPrimaryDark,
                            unfocusedContainerColor = CyberDarkCardElevated,
                            focusedContainerColor = CyberDarkCardElevated
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // 2. Quantity Section with Presets & Numeric Input
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "2. عدد الكروت (الكمية الإجمالية):",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = TextPrimaryDark
                    )

                    // Quick Preset Chips (50, 100, 200, 500, 1000, 2000)
                    val presets = listOf(50 to "50 كرت", 100 to "100 كرت", 200 to "200 كرت", 500 to "500 كرت", 1000 to "1 ألف كرت", 2000 to "2 ألف كرت")
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(presets) { (presetVal, label) ->
                            val isPresetActive = qtyText == presetVal.toString()
                            Surface(
                                onClick = { qtyText = presetVal.toString() },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isPresetActive) MikroTikCyan.copy(alpha = 0.2f) else CyberDarkCardElevated,
                                border = BorderStroke(1.dp, if (isPresetActive) MikroTikCyan else CyberBorder)
                            ) {
                                Text(
                                    text = label,
                                    color = if (isPresetActive) MikroTikCyan else TextSecondaryDark,
                                    fontWeight = if (isPresetActive) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    // Direct Manual Numeric Input
                    OutlinedTextField(
                        value = qtyText,
                        onValueChange = { input ->
                            if (input.all { it.isDigit() }) {
                                qtyText = input
                            }
                        },
                        label = { Text("أدخل العدد يدوياً") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Numbers,
                                contentDescription = null,
                                tint = StatusOnline,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            Text(
                                text = "كرت",
                                color = TextMutedDark,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_receive_cards_quantity"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = StatusOnline,
                            unfocusedBorderColor = CyberBorder,
                            focusedTextColor = TextPrimaryDark,
                            unfocusedTextColor = TextPrimaryDark,
                            focusedContainerColor = CyberDarkCardElevated,
                            unfocusedContainerColor = CyberDarkCardElevated
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // 3. Accounting Pricing Cards (Dual High-Contrast View)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "3. الأسعار المحاسبية (ريال يمني):",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = TextPrimaryDark
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Wholesale Price Box
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = CyberDarkCardElevated),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, CyberBorder)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("سعر الجملة للبقالة", fontSize = 11.sp, color = TextSecondaryDark)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = (wholesaleUnitMinor / 100L).toString(),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 18.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = TextPrimaryDark
                                )
                            }
                        }

                        // Retail Price Box
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = CyberDarkCardElevated),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, CyberBorder)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("سعر البيع للزبون", fontSize = 11.sp, color = TextSecondaryDark)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = (retailUnitMinor / 100L).toString(),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 18.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = TextPrimaryDark
                                )
                            }
                        }
                    }
                }

                // 4. Live Financial Calculation Summary
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CyberDarkCardElevated),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MikroTikCyan.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "ملخص الحسبة لهذه الكمية:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MikroTikCyan
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("إجمالي عدد الكروت المضافة:", color = TextSecondaryDark, fontSize = 12.sp)
                            Text("$quantity كرت", fontWeight = FontWeight.Bold, color = TextPrimaryDark, fontSize = 12.sp)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("إجمالي قيمة الجملة (المستحقة):", color = TextSecondaryDark, fontSize = 12.sp)
                            Text(
                                Money(totalWholesaleMinor, CurrencyCode.YER).format(),
                                fontWeight = FontWeight.Bold,
                                color = ReceiptGreen,
                                fontSize = 12.sp
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("إجمالي قيمة البيع النهائي:", color = TextSecondaryDark, fontSize = 12.sp)
                            Text(
                                Money(totalRetailMinor, CurrencyCode.YER).format(),
                                fontWeight = FontWeight.Bold,
                                color = MikroTikCyan,
                                fontSize = 12.sp
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("هامش ربح البقالات المتوقع:", color = TextSecondaryDark, fontSize = 12.sp)
                            Text(
                                "+ ${Money(profitMinor, CurrencyCode.YER).format()}",
                                fontWeight = FontWeight.Bold,
                                color = StatusWarning,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // Notes input field
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("ملاحظات / رقم الدفعة أو المطبعة (اختياري)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
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

                Spacer(modifier = Modifier.height(6.dp))
            }

            HorizontalDivider(color = CyberBorder)

            // Pinned Sticky Bottom Actions (ALWAYS visible above the keyboard!)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        if (selectedPackageId.isNotBlank() && quantity > 0) {
                            onSubmit(selectedPackageId, quantity, notes)
                        }
                    },
                    enabled = selectedPackageId.isNotBlank() && quantity > 0,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StatusOnline,
                        disabledContainerColor = StatusOnline.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("btn_submit_manual_cards")
                ) {
                    Text(
                        text = "+ إضافة $quantity كرت للمخزن",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.Black
                    )
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("إلغاء", color = TextSecondaryDark, fontSize = 13.sp)
                }
            }
        }
    }
}
