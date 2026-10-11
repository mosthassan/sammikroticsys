package com.example.ui.wifi

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.wifi.ChannelRecommendation
import com.example.data.wifi.WifiAccessPoint
import com.example.data.wifi.WifiBand
import com.example.ui.theme.BrandCyanPrimary
import com.example.ui.theme.CyberDarkCardElevated
import com.example.ui.theme.SemanticIncomeGreen

// Distinctive cyber palette for WiFi AP curves
private val SPECTRUM_COLORS = listOf(
    Color(0xFF00E5FF), // Cyan
    Color(0xFFFF9100), // Amber
    Color(0xFF00E676), // Green
    Color(0xFFD500F9), // Purple
    Color(0xFFFF3D00), // Deep Orange
    Color(0xFF40C4FF), // Light Blue
    Color(0xFFFFD600), // Yellow
    Color(0xFFFF4081)  // Pink
)

@Composable
fun WifiSpectrumGraph(
    accessPoints: List<WifiAccessPoint>,
    band: WifiBand,
    recommendation: ChannelRecommendation?,
    selectedAp: WifiAccessPoint?,
    modifier: Modifier = Modifier
) {
    val is24G = band == WifiBand.BAND_2_4_GHZ

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(CyberDarkCardElevated)
            .border(1.dp, BrandCyanPrimary.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .padding(8.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            val paddingLeft = 45.dp.toPx()
            val paddingBottom = 34.dp.toPx()
            val paddingTop = 20.dp.toPx()
            val paddingRight = 16.dp.toPx()

            val plotWidth = canvasWidth - paddingLeft - paddingRight
            val plotHeight = canvasHeight - paddingTop - paddingBottom

            val minDbm = -100f
            val maxDbm = -20f
            val dbmRange = maxDbm - minDbm

            fun dbmToY(dbm: Float): Float {
                val clamped = dbm.coerceIn(minDbm, maxDbm)
                val ratio = (clamped - minDbm) / dbmRange
                return paddingTop + plotHeight * (1f - ratio)
            }

            // 1. Draw horizontal dBm grid lines
            val gridDbms = listOf(-90, -70, -50, -30)
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.argb(170, 160, 180, 205)
                textSize = 9.dp.toPx()
                textAlign = android.graphics.Paint.Align.RIGHT
                isAntiAlias = true
            }

            for (dbm in gridDbms) {
                val y = dbmToY(dbm.toFloat())
                drawLine(
                    color = Color.White.copy(alpha = 0.08f),
                    start = Offset(paddingLeft, y),
                    end = Offset(canvasWidth - paddingRight, y),
                    strokeWidth = 1.dp.toPx()
                )
                drawContext.canvas.nativeCanvas.drawText(
                    "$dbm dBm",
                    paddingLeft - 6.dp.toPx(),
                    y + 3.dp.toPx(),
                    paint
                )
            }

            // 2. Setup X-axis channels
            if (is24G) {
                // 2.4 GHz channels 1 to 14
                val totalChannels = 14
                fun channelToX(channel: Float): Float {
                    val ratio = (channel - 0.5f) / 14f
                    return paddingLeft + plotWidth * ratio
                }

                // Highlight recommended channel zone
                if (recommendation != null) {
                    val recCh = recommendation.recommendedChannel.toFloat()
                    val recCenterX = channelToX(recCh)
                    val recWidth = plotWidth / 14f * 3.5f
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                SemanticIncomeGreen.copy(alpha = 0.18f),
                                SemanticIncomeGreen.copy(alpha = 0.02f)
                            )
                        ),
                        topLeft = Offset(recCenterX - recWidth / 2f, paddingTop),
                        size = androidx.compose.ui.geometry.Size(recWidth, plotHeight)
                    )
                    drawLine(
                        color = SemanticIncomeGreen.copy(alpha = 0.6f),
                        start = Offset(recCenterX, paddingTop),
                        end = Offset(recCenterX, paddingTop + plotHeight),
                        strokeWidth = 1.5.dp.toPx()
                    )
                }

                // X-axis line
                val baseY = dbmToY(-100f)
                drawLine(
                    color = Color.White.copy(alpha = 0.2f),
                    start = Offset(paddingLeft, baseY),
                    end = Offset(canvasWidth - paddingRight, baseY),
                    strokeWidth = 1.5.dp.toPx()
                )

                // Draw channel marks and labels
                val channelPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.argb(230, 220, 230, 250)
                    textSize = 10.dp.toPx()
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }

                for (ch in 1..14) {
                    val cx = channelToX(ch.toFloat())
                    val isStandard = ch in listOf(1, 6, 11)
                    val isRec = recommendation?.recommendedChannel == ch

                    channelPaint.color = when {
                        isRec -> android.graphics.Color.parseColor("#00E676")
                        isStandard -> android.graphics.Color.parseColor("#00E5FF")
                        else -> android.graphics.Color.argb(160, 160, 175, 195)
                    }

                    drawContext.canvas.nativeCanvas.drawText(
                        "$ch",
                        cx,
                        baseY + 16.dp.toPx(),
                        channelPaint
                    )
                }

                // 3. Draw AP bell curves for 2.4 GHz
                accessPoints.forEachIndexed { index, ap ->
                    val color = SPECTRUM_COLORS[index % SPECTRUM_COLORS.size]
                    val isSelected = selectedAp?.bssid == ap.bssid

                    val centerX = channelToX(ap.channel.toFloat())
                    // Standard 2.4 GHz channel width is 20-22 MHz which spans ~4.4 channel steps
                    val channelSpan = (ap.channelWidthMhz / 5f).coerceAtLeast(3.8f)
                    val halfWidthPx = (plotWidth / 14f) * (channelSpan / 2f)

                    val leftX = centerX - halfWidthPx
                    val rightX = centerX + halfWidthPx
                    val peakY = dbmToY(ap.rssiDbm.toFloat())
                    val bottomY = baseY

                    val path = Path().apply {
                        moveTo(leftX, bottomY)
                        // Cubic curve up to peak
                        cubicTo(
                            leftX + halfWidthPx * 0.4f, bottomY,
                            centerX - halfWidthPx * 0.3f, peakY,
                            centerX, peakY
                        )
                        // Cubic curve down to right base
                        cubicTo(
                            centerX + halfWidthPx * 0.3f, peakY,
                            rightX - halfWidthPx * 0.4f, bottomY,
                            rightX, bottomY
                        )
                        close()
                    }

                    // Fill area
                    drawPath(
                        path = path,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                color.copy(alpha = if (isSelected) 0.55f else 0.28f),
                                color.copy(alpha = 0.02f)
                            ),
                            startY = peakY,
                            endY = bottomY
                        )
                    )

                    // Curve stroke
                    drawPath(
                        path = path,
                        color = if (isSelected) Color.White else color,
                        style = Stroke(width = if (isSelected) 3.dp.toPx() else 2.dp.toPx())
                    )

                    // Draw SSID label at peak
                    val labelPaint = android.graphics.Paint().apply {
                        this.color = if (isSelected) android.graphics.Color.WHITE else color.hashCode()
                        textSize = 9.dp.toPx()
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                    }

                    val label = if (ap.ssid.length > 12) ap.ssid.take(10) + ".." else ap.displaySsid
                    drawContext.canvas.nativeCanvas.drawText(
                        "$label (${ap.rssiDbm})",
                        centerX,
                        peakY - 4.dp.toPx(),
                        labelPaint
                    )
                }

            } else {
                // 5.0 GHz spectrum channels (36 to 165)
                val channels5G = listOf(36, 40, 44, 48, 52, 56, 60, 64, 100, 108, 116, 124, 132, 140, 149, 153, 157, 161, 165)
                val minCh = 36f
                val maxCh = 165f

                fun channel5GToX(ch: Float): Float {
                    val ratio = ((ch - minCh) / (maxCh - minCh)).coerceIn(0f, 1f)
                    return paddingLeft + plotWidth * ratio
                }

                val baseY = dbmToY(-100f)
                drawLine(
                    color = Color.White.copy(alpha = 0.2f),
                    start = Offset(paddingLeft, baseY),
                    end = Offset(canvasWidth - paddingRight, baseY),
                    strokeWidth = 1.5.dp.toPx()
                )

                // Highlight recommended channel in 5G
                if (recommendation != null) {
                    val recCh = recommendation.recommendedChannel.toFloat()
                    val recCenterX = channel5GToX(recCh)
                    val recWidth = plotWidth * 0.08f
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                SemanticIncomeGreen.copy(alpha = 0.20f),
                                SemanticIncomeGreen.copy(alpha = 0.02f)
                            )
                        ),
                        topLeft = Offset(recCenterX - recWidth / 2f, paddingTop),
                        size = androidx.compose.ui.geometry.Size(recWidth, plotHeight)
                    )
                }

                val channelPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.argb(180, 180, 195, 220)
                    textSize = 8.5f.dp.toPx()
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                }

                // Show selected 5GHz channel marks
                val keyChannels = listOf(36, 48, 64, 100, 128, 149, 161, 165)
                for (ch in keyChannels) {
                    val cx = channel5GToX(ch.toFloat())
                    val isRec = recommendation?.recommendedChannel == ch
                    channelPaint.color = if (isRec) android.graphics.Color.parseColor("#00E676") else android.graphics.Color.argb(180, 180, 195, 220)
                    drawContext.canvas.nativeCanvas.drawText(
                        "$ch",
                        cx,
                        baseY + 16.dp.toPx(),
                        channelPaint
                    )
                }

                // Draw AP curves for 5 GHz
                accessPoints.forEachIndexed { index, ap ->
                    val color = SPECTRUM_COLORS[index % SPECTRUM_COLORS.size]
                    val isSelected = selectedAp?.bssid == ap.bssid

                    val centerX = channel5GToX(ap.channel.toFloat())
                    val widthRatio = (ap.channelWidthMhz.toFloat() / (maxCh - minCh) * 1.5f).coerceIn(0.04f, 0.15f)
                    val halfWidthPx = plotWidth * widthRatio / 2f

                    val leftX = centerX - halfWidthPx
                    val rightX = centerX + halfWidthPx
                    val peakY = dbmToY(ap.rssiDbm.toFloat())
                    val bottomY = baseY

                    val path = Path().apply {
                        moveTo(leftX, bottomY)
                        cubicTo(
                            leftX + halfWidthPx * 0.4f, bottomY,
                            centerX - halfWidthPx * 0.3f, peakY,
                            centerX, peakY
                        )
                        cubicTo(
                            centerX + halfWidthPx * 0.3f, peakY,
                            rightX - halfWidthPx * 0.4f, bottomY,
                            rightX, bottomY
                        )
                        close()
                    }

                    drawPath(
                        path = path,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                color.copy(alpha = if (isSelected) 0.55f else 0.28f),
                                color.copy(alpha = 0.02f)
                            ),
                            startY = peakY,
                            endY = bottomY
                        )
                    )

                    drawPath(
                        path = path,
                        color = if (isSelected) Color.White else color,
                        style = Stroke(width = if (isSelected) 3.dp.toPx() else 2.dp.toPx())
                    )

                    val labelPaint = android.graphics.Paint().apply {
                        this.color = if (isSelected) android.graphics.Color.WHITE else color.hashCode()
                        textSize = 9.dp.toPx()
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                    }

                    val label = if (ap.ssid.length > 10) ap.ssid.take(9) + ".." else ap.displaySsid
                    drawContext.canvas.nativeCanvas.drawText(
                        "$label (${ap.rssiDbm})",
                        centerX,
                        peakY - 4.dp.toPx(),
                        labelPaint
                    )
                }
            }
        }
    }
}
