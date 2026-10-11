package com.example.core.time

import java.time.LocalDate

/**
 * Pure Kotlin Fiscal Calendar calculator with zero Android dependencies.
 * Respects organizations.fiscalYearStartMonth (default 1).
 *
 * Convention:
 * If fiscal year starts in month M (where M in 1..12):
 * - If M == 1 (standard calendar fiscal year):
 *     Fiscal Year of date D is D.year.
 *     periodOf(D) is (D.year, D.monthValue).
 *     yearStart(FY) is Jan 1 of FY.
 *     yearEnd(FY) is Dec 31 of FY.
 * - If M in 2..12:
 *     A fiscal year FY begins on LocalDate(FY, M, 1) and ends on LocalDate(FY + 1, M, 1).minusDays(1).
 *     If D.monthValue >= M: fiscalYear is D.year, month index is D.monthValue - M + 1.
 *     If D.monthValue < M: fiscalYear is D.year - 1, month index is D.monthValue + 12 - M + 1.
 *     yearStart(FY) is LocalDate(FY, M, 1).toEpochDay().
 *     yearEnd(FY) is LocalDate(FY + 1, M, 1).minusDays(1).toEpochDay().
 */
class FiscalCalendar(
    val fiscalYearStartMonth: Int = 1
) {
    init {
        require(fiscalYearStartMonth in 1..12) { "fiscalYearStartMonth must be between 1 and 12" }
    }

    fun fiscalYearOf(epochDay: Long): Int {
        val date = LocalDate.ofEpochDay(epochDay)
        return if (fiscalYearStartMonth == 1) {
            date.year
        } else {
            if (date.monthValue >= fiscalYearStartMonth) date.year else date.year - 1
        }
    }

    fun periodOf(epochDay: Long): Pair<Int, Int> {
        val date = LocalDate.ofEpochDay(epochDay)
        return if (fiscalYearStartMonth == 1) {
            Pair(date.year, date.monthValue)
        } else {
            val fy = if (date.monthValue >= fiscalYearStartMonth) date.year else date.year - 1
            val periodMonth = if (date.monthValue >= fiscalYearStartMonth) {
                date.monthValue - fiscalYearStartMonth + 1
            } else {
                date.monthValue + 12 - fiscalYearStartMonth + 1
            }
            Pair(fy, periodMonth)
        }
    }

    fun yearStart(fiscalYear: Int): Long {
        return if (fiscalYearStartMonth == 1) {
            LocalDate.of(fiscalYear, 1, 1).toEpochDay()
        } else {
            LocalDate.of(fiscalYear, fiscalYearStartMonth, 1).toEpochDay()
        }
    }

    fun yearEnd(fiscalYear: Int): Long {
        return if (fiscalYearStartMonth == 1) {
            LocalDate.of(fiscalYear, 12, 31).toEpochDay()
        } else {
            LocalDate.of(fiscalYear + 1, fiscalYearStartMonth, 1).minusDays(1).toEpochDay()
        }
    }

    companion object {
        val DEFAULT = FiscalCalendar(1)

        fun fiscalYearOf(epochDay: Long): Int = DEFAULT.fiscalYearOf(epochDay)
        fun periodOf(epochDay: Long): Pair<Int, Int> = DEFAULT.periodOf(epochDay)
        fun yearStart(fiscalYear: Int): Long = DEFAULT.yearStart(fiscalYear)
        fun yearEnd(fiscalYear: Int): Long = DEFAULT.yearEnd(fiscalYear)
    }
}
