package com.example.core.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Unified application clock interface.
 * Default timezone is Asia/Aden (UTC+3).
 * Provides injectable/overridable clock for unit testing and configurable organization timezones.
 */
interface AppClock {
    fun todayEpochDay(): Long
    fun nowInstant(): Instant
    fun zoneId(): ZoneId
    fun todayLocalDate(): LocalDate = LocalDate.ofEpochDay(todayEpochDay())

    companion object {
        val ADEN_ZONE: ZoneId = ZoneId.of("Asia/Aden")

        @Volatile
        private var currentClock: AppClock = SystemAppClock(ADEN_ZONE)

        fun get(): AppClock = currentClock
        fun setClock(clock: AppClock) { currentClock = clock }
        fun resetToDefault() { currentClock = SystemAppClock(ADEN_ZONE) }

        fun todayEpochDay(): Long = get().todayEpochDay()
        fun nowInstant(): Instant = get().nowInstant()
        fun zoneId(): ZoneId = get().zoneId()
        fun todayLocalDate(): LocalDate = get().todayLocalDate()
    }
}

class SystemAppClock(
    private val zone: ZoneId = AppClock.ADEN_ZONE
) : AppClock {
    override fun zoneId(): ZoneId = zone
    override fun todayEpochDay(): Long = LocalDate.now(zone).toEpochDay()
    override fun nowInstant(): Instant = Instant.now()
}

class FixedAppClock(
    private val fixedInstant: Instant,
    private val zone: ZoneId = AppClock.ADEN_ZONE
) : AppClock {
    constructor(epochDay: Long, zone: ZoneId = AppClock.ADEN_ZONE) : this(
        LocalDate.ofEpochDay(epochDay).atStartOfDay(zone).toInstant(),
        zone
    )

    override fun zoneId(): ZoneId = zone
    override fun todayEpochDay(): Long = fixedInstant.atZone(zone).toLocalDate().toEpochDay()
    override fun nowInstant(): Instant = fixedInstant
}
