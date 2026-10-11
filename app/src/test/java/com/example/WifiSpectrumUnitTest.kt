package com.example

import com.example.data.wifi.ChannelRating
import com.example.data.wifi.ChannelRecommendationEngine
import com.example.data.wifi.WifiAccessPoint
import com.example.data.wifi.WifiBand
import com.example.data.wifi.WifiFrequencyHelper
import com.example.data.wifi.WifiSecurityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiSpectrumUnitTest {

    @Test
    fun testFrequencyToChannel24Ghz() {
        assertEquals(1, WifiFrequencyHelper.frequencyToChannel(2412))
        assertEquals(6, WifiFrequencyHelper.frequencyToChannel(2437))
        assertEquals(11, WifiFrequencyHelper.frequencyToChannel(2462))
        assertEquals(13, WifiFrequencyHelper.frequencyToChannel(2472))
        assertEquals(14, WifiFrequencyHelper.frequencyToChannel(2484))
    }

    @Test
    fun testFrequencyToChannel5Ghz() {
        assertEquals(36, WifiFrequencyHelper.frequencyToChannel(5180))
        assertEquals(40, WifiFrequencyHelper.frequencyToChannel(5200))
        assertEquals(48, WifiFrequencyHelper.frequencyToChannel(5240))
        assertEquals(149, WifiFrequencyHelper.frequencyToChannel(5745))
        assertEquals(165, WifiFrequencyHelper.frequencyToChannel(5825))
    }

    @Test
    fun testChannelToFrequencyMapping() {
        assertEquals(2412, WifiFrequencyHelper.channelToFrequency(1, WifiBand.BAND_2_4_GHZ))
        assertEquals(2437, WifiFrequencyHelper.channelToFrequency(6, WifiBand.BAND_2_4_GHZ))
        assertEquals(2462, WifiFrequencyHelper.channelToFrequency(11, WifiBand.BAND_2_4_GHZ))
        assertEquals(5180, WifiFrequencyHelper.channelToFrequency(36, WifiBand.BAND_5_GHZ))
    }

    @Test
    fun testParseSecurityType() {
        assertEquals(
            WifiSecurityType.WPA3,
            WifiFrequencyHelper.parseSecurityType("[WPA3-SAE-CCMP][ESS]")
        )
        assertEquals(
            WifiSecurityType.WPA2,
            WifiFrequencyHelper.parseSecurityType("[WPA2-PSK-CCMP][ESS]")
        )
        assertEquals(
            WifiSecurityType.OPEN,
            WifiFrequencyHelper.parseSecurityType("[ESS]")
        )
        assertEquals(
            WifiSecurityType.WEP,
            WifiFrequencyHelper.parseSecurityType("[WEP][ESS]")
        )
    }

    @Test
    fun testChannelRecommendationEngine24Ghz() {
        // Given APs heavily congesting channel 1 and channel 6
        val congestedAps = listOf(
            WifiAccessPoint(
                bssid = "AA:BB:CC:DD:EE:01",
                ssid = "Busy-AP-Ch1",
                frequencyMhz = 2412,
                channel = 1,
                channelWidthMhz = 20,
                rssiDbm = -40,
                securityType = WifiSecurityType.WPA2,
                band = WifiBand.BAND_2_4_GHZ,
                capabilities = "[WPA2-PSK-CCMP]"
            ),
            WifiAccessPoint(
                bssid = "AA:BB:CC:DD:EE:02",
                ssid = "Busy-AP-Ch6",
                frequencyMhz = 2437,
                channel = 6,
                channelWidthMhz = 20,
                rssiDbm = -45,
                securityType = WifiSecurityType.WPA2,
                band = WifiBand.BAND_2_4_GHZ,
                capabilities = "[WPA2-PSK-CCMP]"
            )
        )

        val recommendation = ChannelRecommendationEngine.evaluateBand(
            congestedAps,
            WifiBand.BAND_2_4_GHZ
        )

        assertNotNull(recommendation)
        // Channel 11 must be chosen as the cleanest non-overlapping channel
        assertEquals(11, recommendation.recommendedChannel)
        assertEquals(ChannelRating.EXCELLENT, recommendation.rating)
        assertTrue(recommendation.rationale.contains("11"))
    }

    @Test
    fun testChannelRecommendationEngine5Ghz() {
        val aps5G = listOf(
            WifiAccessPoint(
                bssid = "00:11:22:33:44:01",
                ssid = "Mikrotik-5G",
                frequencyMhz = 5180,
                channel = 36,
                channelWidthMhz = 40,
                rssiDbm = -50,
                securityType = WifiSecurityType.WPA3,
                band = WifiBand.BAND_5_GHZ,
                capabilities = "[WPA3-SAE-CCMP]"
            )
        )

        val recommendation = ChannelRecommendationEngine.evaluateBand(
            aps5G,
            WifiBand.BAND_5_GHZ
        )

        assertNotNull(recommendation)
        assertTrue(recommendation.recommendedChannel > 0)
        assertEquals(WifiBand.BAND_5_GHZ, recommendation.band)
    }

    @Test
    fun testSignalPercentageClamping() {
        val apStrong = WifiAccessPoint(
            bssid = "00:00:00:00:00:01",
            ssid = "StrongAP",
            frequencyMhz = 2412,
            channel = 1,
            channelWidthMhz = 20,
            rssiDbm = -30,
            securityType = WifiSecurityType.OPEN,
            band = WifiBand.BAND_2_4_GHZ,
            capabilities = ""
        )
        assertEquals(100, apStrong.signalPercent)

        val apWeak = WifiAccessPoint(
            bssid = "00:00:00:00:00:02",
            ssid = "WeakAP",
            frequencyMhz = 2412,
            channel = 1,
            channelWidthMhz = 20,
            rssiDbm = -105,
            securityType = WifiSecurityType.OPEN,
            band = WifiBand.BAND_2_4_GHZ,
            capabilities = ""
        )
        assertEquals(0, apWeak.signalPercent)
    }

    @Test
    fun testHiddenSsidFallback() {
        val hiddenAp = WifiAccessPoint(
            bssid = "AA:BB:CC:11:22:33",
            ssid = "",
            frequencyMhz = 2412,
            channel = 1,
            channelWidthMhz = 20,
            rssiDbm = -65,
            securityType = WifiSecurityType.WPA2,
            band = WifiBand.BAND_2_4_GHZ,
            capabilities = ""
        )
        assertTrue(hiddenAp.displaySsid.contains("مخفية"))
        assertTrue(hiddenAp.displaySsid.contains("AA:BB:CC:11:22:33"))
    }
}
