package com.example.data.wifi

/**
 * Frequency band for WiFi networks
 */
enum class WifiBand(val title: String, val frequencyRange: String) {
    BAND_2_4_GHZ("2.4 GHz", "2400 - 2500 MHz"),
    BAND_5_GHZ("5.0 GHz", "5150 - 5850 MHz")
}

/**
 * WiFi security encryption standards
 */
enum class WifiSecurityType(val displayName: String) {
    OPEN("مفتوحة (Open)"),
    WEP("WEP"),
    WPA("WPA"),
    WPA2("WPA2-PSK"),
    WPA3("WPA3-SAE"),
    ENTERPRISE("WPA-Enterprise"),
    UNKNOWN("غير معروف")
}

/**
 * Represents a scanned WiFi Access Point with signal and frequency telemetry
 */
data class WifiAccessPoint(
    val bssid: String,
    val ssid: String,
    val frequencyMhz: Int,
    val channel: Int,
    val channelWidthMhz: Int,
    val rssiDbm: Int,
    val securityType: WifiSecurityType,
    val band: WifiBand,
    val capabilities: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    val displaySsid: String
        get() = if (ssid.isBlank()) "(شبكة مخفية $bssid)" else ssid

    /**
     * Estimated signal quality percentage (0% to 100%)
     */
    val signalPercent: Int
        get() {
            // Typical WiFi RSSI is between -100 dBm (0%) and -40 dBm (100%)
            return ((rssiDbm + 100) * 100 / 60).coerceIn(0, 100)
        }
}

/**
 * Quality rating for a frequency channel
 */
enum class ChannelRating(val label: String) {
    EXCELLENT("ممتازة - خالية تماماً"),
    GOOD("جيدة - تداخل منخفض"),
    MODERATE("متوسطة - تداخل ملحوظ"),
    CONGESTED("مزدحمة - تداخل مرتفع")
}

/**
 * Evaluation score for an individual channel
 */
data class ChannelScore(
    val channel: Int,
    val frequencyMhz: Int,
    val band: WifiBand,
    val coChannelCount: Int,
    val adjacentChannelCount: Int,
    val totalInterferenceEnergy: Double,
    val congestionPercent: Int,
    val rating: ChannelRating
)

/**
 * Result from the smart channel recommendation engine
 */
data class ChannelRecommendation(
    val recommendedChannel: Int,
    val recommendedFrequencyMhz: Int,
    val band: WifiBand,
    val rating: ChannelRating,
    val congestionScore: Int, // 0 to 100% (lower is cleaner)
    val rationale: String,
    val allChannelScores: List<ChannelScore>
)

/**
 * Auto-refresh scan interval options
 */
enum class ScanRefreshInterval(val seconds: Int, val label: String) {
    MANUAL(0, "يدوي"),
    SECONDS_5(5, "5 ثوان"),
    SECONDS_10(10, "10 ثوان"),
    SECONDS_30(30, "30 ثانية")
}

/**
 * Live signal alignment data point for technician AP positioning
 */
data class SignalHistoryPoint(
    val timestamp: Long,
    val rssiDbm: Int
)
