package com.example.ui.wifi

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.wifi.SignalHistoryPoint
import com.example.data.wifi.WifiAccessPoint
import com.example.data.wifi.WifiFrequencyHelper
import com.example.ui.theme.BrandCyanPrimary
import com.example.ui.theme.CyberDarkCardElevated
import com.example.ui.theme.CyberDarkSurface
import com.example.ui.theme.SemanticExpenseRed
import com.example.ui.theme.SemanticIncomeGreen
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun WifiLiveSignalMeter(
    selectedAp: WifiAccessPoint?,
    signalHistory: List<SignalHistoryPoint>,
    minRssi: Int,
    maxRssi: Int,
    avgRssi: Int,
    modifier: Modifier = Modifier
) {
    if (selectedAp == null) {
        Card(
            modifier = modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CyberDarkCardElevated),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, BrandCyanPrimary.copy(alpha = 0.2f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Sensors,
                        contentDescription = null,
                        tint = BrandCyanPrimary.copy(alpha = 0.5f),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "اختر نقطة وصول (AP) من القائمة لتوجيه وضبط الإشارة بدقة حية",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        return
    }

    val currentRssi = selectedAp.rssiDbm
    // Map -100 dBm..-30 dBm to 0f..1f
    val targetRatio = ((currentRssi + 100f) / 70f).coerceIn(0f, 1f)
    val animatedRatio by animateFloatAsState(
        targetValue = targetRatio,
        animationSpec = tween(durationMillis = 400),
        label = "RssiMeterRatio"
    )

    val signalColor = when {
        currentRssi >= -55 -> SemanticIncomeGreen
        currentRssi >= -70 -> BrandCyanPrimary
        currentRssi >= -82 -> Color(0xFFFFB300)
        else -> SemanticExpenseRed
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CyberDarkCardElevated),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, signalColor.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            // Header: Selected AP details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(signalColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CellTower,
                            contentDescription = null,
                            tint = signalColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = selectedAp.displaySsid,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "BSSID: ${selectedAp.bssid}  •  القناة: ${selectedAp.channel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = signalColor.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, signalColor.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "${selectedAp.signalPercent}% جودة",
                        color = signalColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Gauge + Live Metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Circular Arc Gauge
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 12.dp.toPx()
                        val arcSize = size.minDimension - strokeWidth
                        val topLeft = Offset(
                            (size.width - arcSize) / 2,
                            (size.height - arcSize) / 2
                        )
                        val startAngle = 140f
                        val sweepAngle = 260f

                        // Background track
                        drawArc(
                            color = Color.White.copy(alpha = 0.08f),
                            startAngle = startAngle,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            topLeft = topLeft,
                            size = Size(arcSize, arcSize),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )

                        // Active gradient arc
                        val activeSweep = sweepAngle * animatedRatio
                        if (activeSweep > 0f) {
                            drawArc(
                                brush = Brush.sweepGradient(
                                    colors = listOf(
                                        SemanticExpenseRed,
                                        Color(0xFFFFB300),
                                        BrandCyanPrimary,
                                        SemanticIncomeGreen
                                    )
                                ),
                                startAngle = startAngle,
                                sweepAngle = activeSweep,
                                useCenter = false,
                                topLeft = topLeft,
                                size = Size(arcSize, arcSize),
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$currentRssi",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            color = signalColor
                        )
                        Text(
                            text = "dBm",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Stats and Alignment Status
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = WifiFrequencyHelper.getRssiQualityLabel(currentRssi),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = signalColor
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Alignment Telemetry Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetricPill(label = "الأعلى (Max)", value = "$maxRssi dBm", color = SemanticIncomeGreen)
                        MetricPill(label = "المتوسط (Avg)", value = "$avgRssi dBm", color = BrandCyanPrimary)
                        MetricPill(label = "الأدنى (Min)", value = "$minRssi dBm", color = SemanticExpenseRed)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "مؤشر توجيه الصحن/الهوائي اللاسلكي: حرك الهوائي حتى يصل المؤشر إلى أعلى قيمة سالبة (أقرب للصفر).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }

            // Real-time Stability Timeline (Sparkline)
            if (signalHistory.size > 2) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "سجل استقرار الإشارة اللحظي (آخر التحديثات):",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(CyberDarkSurface)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val minVal = -95f
                        val maxVal = -30f
                        val range = maxVal - minVal

                        val points = signalHistory.mapIndexed { i, p ->
                            val x = (i.toFloat() / (signalHistory.size - 1).coerceAtLeast(1)) * w
                            val y = h - ((p.rssiDbm.toFloat() - minVal) / range).coerceIn(0f, 1f) * h
                            Offset(x, y)
                        }

                        val path = Path().apply {
                            points.forEachIndexed { i, pt ->
                                if (i == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
                            }
                        }

                        drawPath(
                            path = path,
                            color = signalColor,
                            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                        )

                        // Draw latest head point
                        val last = points.last()
                        drawCircle(color = Color.White, radius = 3.dp.toPx(), center = last)
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricPill(label: String, value: String, color: Color) {
    Column {
        Text(
            text = label,
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}
