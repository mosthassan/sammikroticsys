package com.example.core.model

/**
 * Geographic Exchange Rate Zones in Yemen (IAS 21 compliant multi-zone pricing).
 * SANAA: Northern Governorates (Central Bank Sana'a rate regime).
 * ADEN: Southern Governorates (Central Bank Aden rate regime).
 */
enum class RateZone(val arabicName: String) {
    SANAA("صنعاء والمحافظات الشمالية"),
    ADEN("عدن والمحافظات الجنوبية");

    companion object {
        val DEFAULT = SANAA

        fun fromString(value: String): RateZone {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: DEFAULT
        }
    }
}
