package com.example.data.wifi

import android.net.wifi.ScanResult
import android.os.Build

object WifiFrequencyHelper {

    /**
     * Converts frequency in MHz to standard WiFi Channel number.
     * Supports 2.4 GHz (1..14) and 5.0 GHz (36..165).
     */
    fun frequencyToChannel(frequencyMhz: Int): Int {
        return when {
            // 2.4 GHz band
            frequencyMhz == 2484 -> 14
            frequencyMhz in 2412..2472 -> (frequencyMhz - 2407) / 5
            
            // 5 GHz band (UNII-1, UNII-2, UNII-2 Extended, UNII-3)
            frequencyMhz in 5170..5825 -> (frequencyMhz - 5000) / 5
            frequencyMhz in 5826..5865 -> (frequencyMhz - 5000) / 5
            
            // 6 GHz band (WiFi 6E)
            frequencyMhz in 5925..7125 -> (frequencyMhz - 5940) / 5
            
            else -> 0
        }
    }

    /**
     * Determines whether frequency is 2.4GHz or 5.0GHz band
     */
    fun frequencyToBand(frequencyMhz: Int): WifiBand {
        return if (frequencyMhz in 2400..2500) {
            WifiBand.BAND_2_4_GHZ
        } else {
            WifiBand.BAND_5_GHZ
        }
    }

    /**
     * Returns center frequency for standard 2.4 GHz or 5 GHz channel
     */
    fun channelToFrequency(channel: Int, band: WifiBand): Int {
        return when (band) {
            WifiBand.BAND_2_4_GHZ -> {
                if (channel == 14) 2484
                else if (channel in 1..13) 2407 + (channel * 5)
                else 2412
            }
            WifiBand.BAND_5_GHZ -> {
                if (channel in 36..165) 5000 + (channel * 5)
                else 5180
            }
        }
    }

    /**
     * Extracts channel width in MHz from ScanResult
     */
    fun getChannelWidthMhz(scanResult: ScanResult): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return when (scanResult.channelWidth) {
                ScanResult.CHANNEL_WIDTH_20MHZ -> 20
                ScanResult.CHANNEL_WIDTH_40MHZ -> 40
                ScanResult.CHANNEL_WIDTH_80MHZ -> 80
                ScanResult.CHANNEL_WIDTH_160MHZ -> 160
                ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ -> 80
                else -> 20
            }
        }
        return 20
    }

    /**
     * Parses the capability string from ScanResult to standard WifiSecurityType
     */
    fun parseSecurityType(capabilities: String): WifiSecurityType {
        val caps = capabilities.uppercase()
        return when {
            caps.contains("SAE") || caps.contains("WPA3") -> WifiSecurityType.WPA3
            caps.contains("EAP") || caps.contains("ENTERPRISE") -> WifiSecurityType.ENTERPRISE
            caps.contains("WPA2") -> WifiSecurityType.WPA2
            caps.contains("WPA") -> WifiSecurityType.WPA
            caps.contains("WEP") -> WifiSecurityType.WEP
            caps.contains("OPEN") || caps.isEmpty() || (!caps.contains("WPA") && !caps.contains("WEP")) -> WifiSecurityType.OPEN
            else -> WifiSecurityType.UNKNOWN
        }
    }

    /**
     * Categorizes RSSI strength into user-friendly descriptive labels
     */
    fun getRssiQualityLabel(rssiDbm: Int): String {
        return when {
            rssiDbm >= -50 -> "ممتازة جداً (قريب من البرج)"
            rssiDbm >= -60 -> "ممتازة (تغطية قوية)"
            rssiDbm >= -70 -> "جيدة (مثالية للبث والربط)"
            rssiDbm >= -80 -> "مقبولة (تغطية متوسطة)"
            rssiDbm >= -90 -> "ضعيفة (عرضة لانقطاع الخدمة)"
            else -> "حرجة للغاية (خارج التغطية)"
        }
    }

    /**
     * Returns standard non-overlapping channels for 2.4 GHz
     */
    val STANDARD_2_4_GHZ_CHANNELS = listOf(1, 6, 11)

    /**
     * Returns standard popular channels for 5.0 GHz
     */
    val STANDARD_5_GHZ_CHANNELS = listOf(36, 40, 44, 48, 149, 153, 157, 161)
}
