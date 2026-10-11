package com.example.data.wifi

import kotlin.math.pow

object ChannelRecommendationEngine {

    /**
     * Evaluates channel congestion and calculates the best recommended channel for the given band.
     */
    fun evaluateBand(accessPoints: List<WifiAccessPoint>, band: WifiBand): ChannelRecommendation {
        val filteredAps = accessPoints.filter { it.band == band }

        return when (band) {
            WifiBand.BAND_2_4_GHZ -> evaluate24Ghz(filteredAps)
            WifiBand.BAND_5_GHZ -> evaluate5Ghz(filteredAps)
        }
    }

    private fun evaluate24Ghz(aps: List<WifiAccessPoint>): ChannelRecommendation {
        val evaluatedChannels = (1..13).toList()
        val scores = mutableListOf<ChannelScore>()

        for (channel in evaluatedChannels) {
            var coChannelCount = 0
            var adjacentChannelCount = 0
            var totalEnergy = 0.0

            for (ap in aps) {
                val channelDiff = kotlin.math.abs(ap.channel - channel)
                // Linear RF power from RSSI dBm: P = 10^(rssi / 10) in milliwatts
                // We use a normalized power: max RSSI (-30 dBm) -> ~1.0, min (-95 dBm) -> ~0.001
                val linearPower = 10.0.pow((ap.rssiDbm + 100.0) / 20.0)

                if (channelDiff == 0) {
                    coChannelCount++
                    totalEnergy += linearPower * 1.5 // Co-channel contention weight
                } else if (channelDiff < 5) {
                    adjacentChannelCount++
                    // Sideband interference attenuation factor
                    val attenuation = (5 - channelDiff) / 5.0
                    totalEnergy += linearPower * attenuation
                }
            }

            // Standard 1, 6, 11 get a slight bonus because using standard non-overlapping
            // channels avoids non-standard split-interference in 2.4 GHz
            val standardBonus = if (channel in WifiFrequencyHelper.STANDARD_2_4_GHZ_CHANNELS) 0.85 else 1.15
            val rawCongestion = (totalEnergy * standardBonus)
            val congestionPercent = (rawCongestion * 5).toInt().coerceIn(0, 100)

            val rating = when {
                congestionPercent == 0 && coChannelCount == 0 -> ChannelRating.EXCELLENT
                congestionPercent < 25 -> ChannelRating.GOOD
                congestionPercent < 55 -> ChannelRating.MODERATE
                else -> ChannelRating.CONGESTED
            }

            scores.add(
                ChannelScore(
                    channel = channel,
                    frequencyMhz = WifiFrequencyHelper.channelToFrequency(channel, WifiBand.BAND_2_4_GHZ),
                    band = WifiBand.BAND_2_4_GHZ,
                    coChannelCount = coChannelCount,
                    adjacentChannelCount = adjacentChannelCount,
                    totalInterferenceEnergy = totalEnergy,
                    congestionPercent = congestionPercent,
                    rating = rating
                )
            )
        }

        // Among standard channels (1, 6, 11), pick the best one first; if all are very congested, check all 1..13
        val standardScores = scores.filter { it.channel in WifiFrequencyHelper.STANDARD_2_4_GHZ_CHANNELS }
        val bestStandard = standardScores.minByOrNull { it.totalInterferenceEnergy }
        val overallBest = scores.minByOrNull { it.totalInterferenceEnergy } ?: scores.first()

        val chosen = if (bestStandard != null && bestStandard.congestionPercent <= 50) {
            bestStandard
        } else {
            overallBest
        }

        val rationale = buildString {
            append("القناة ${chosen.channel} (${chosen.frequencyMhz} MHz): ")
            when (chosen.rating) {
                ChannelRating.EXCELLENT -> {
                    append("نظيفة وخالية تماماً من الشبكات المتداخلة. الخيار الأمثل لبث نقطة الوصول (AP).")
                }
                ChannelRating.GOOD -> {
                    append("تداخل منخفض (${chosen.coChannelCount} شبكات على نفس القناة). كفاءة إرسال عالية.")
                }
                ChannelRating.MODERATE -> {
                    append("تداخل متوسط في نطاق 2.4GHz. يوصى بها كأقل القنوات ازدحاماً في المنطقة.")
                }
                ChannelRating.CONGESTED -> {
                    append("نطاق 2.4GHz مزدحم للغاية في الموقع. يُنصح بالتحويل إلى تردد 5.0GHz إن أمكن.")
                }
            }
        }

        return ChannelRecommendation(
            recommendedChannel = chosen.channel,
            recommendedFrequencyMhz = chosen.frequencyMhz,
            band = WifiBand.BAND_2_4_GHZ,
            rating = chosen.rating,
            congestionScore = chosen.congestionPercent,
            rationale = rationale,
            allChannelScores = scores
        )
    }

    private fun evaluate5Ghz(aps: List<WifiAccessPoint>): ChannelRecommendation {
        val standard5GhzChannels = listOf(
            36, 40, 44, 48, 52, 56, 60, 64, 100, 104, 108, 112, 116, 120, 124, 128, 132, 136, 140, 149, 153, 157, 161, 165
        )

        val scores = mutableListOf<ChannelScore>()

        for (channel in standard5GhzChannels) {
            val matchingAps = aps.filter { it.channel == channel }
            var totalEnergy = 0.0

            for (ap in matchingAps) {
                val linearPower = 10.0.pow((ap.rssiDbm + 100.0) / 20.0)
                totalEnergy += linearPower
            }

            val congestionPercent = (totalEnergy * 10).toInt().coerceIn(0, 100)
            val rating = when {
                matchingAps.isEmpty() -> ChannelRating.EXCELLENT
                congestionPercent < 20 -> ChannelRating.GOOD
                congestionPercent < 50 -> ChannelRating.MODERATE
                else -> ChannelRating.CONGESTED
            }

            scores.add(
                ChannelScore(
                    channel = channel,
                    frequencyMhz = WifiFrequencyHelper.channelToFrequency(channel, WifiBand.BAND_5_GHZ),
                    band = WifiBand.BAND_5_GHZ,
                    coChannelCount = matchingAps.size,
                    adjacentChannelCount = 0, // 5GHz non-overlapping when 20MHz
                    totalInterferenceEnergy = totalEnergy,
                    congestionPercent = congestionPercent,
                    rating = rating
                )
            )
        }

        // Prefer lower UNII-1 channels (36, 40, 44, 48) or UNII-3 (149, 153, 157, 161) which don't require DFS
        val nonDfsChannels = scores.filter { it.channel in listOf(36, 40, 44, 48, 149, 153, 157, 161) }
        val bestNonDfs = nonDfsChannels.minByOrNull { it.totalInterferenceEnergy }
        val overallBest = scores.minByOrNull { it.totalInterferenceEnergy } ?: scores.first()

        val chosen = if (bestNonDfs != null && bestNonDfs.congestionPercent == 0) {
            bestNonDfs
        } else {
            overallBest
        }

        val rationale = buildString {
            append("القناة ${chosen.channel} (${chosen.frequencyMhz} MHz): ")
            when (chosen.rating) {
                ChannelRating.EXCELLENT -> {
                    append("نطاق 5GHz فائق السرعة ونظيف تماماً بدون أي تداخل راديوي. ممتاز للربط اللاسلكي Backhaul.")
                }
                ChannelRating.GOOD -> {
                    append("تداخل راديوي ضعيف جداً (${chosen.coChannelCount} شبكات). سرعات نقل عالية وثابتة.")
                }
                else -> {
                    append("أفضل قناة متاحة في نطاق 5GHz في هذه البيئة.")
                }
            }
        }

        return ChannelRecommendation(
            recommendedChannel = chosen.channel,
            recommendedFrequencyMhz = chosen.frequencyMhz,
            band = WifiBand.BAND_5_GHZ,
            rating = chosen.rating,
            congestionScore = chosen.congestionPercent,
            rationale = rationale,
            allChannelScores = scores
        )
    }
}
