package com.bravoscribe.android.ui.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class StreakBarTest {

    private val today = LocalDate.of(2026, 7, 11)

    @Test
    fun `builds a 7-day window ending today`() {
        val days = buildStreakDays(entryDates = emptySet(), today = today)

        assertEquals(7, days.size)
        assertEquals(LocalDate.of(2026, 7, 5), days.first().date)
        assertEquals(today, days.last().date)
    }

    @Test
    fun `only the last day is marked isToday`() {
        val days = buildStreakDays(entryDates = emptySet(), today = today)

        assertTrue(days.last().isToday)
        assertTrue(days.dropLast(1).none { it.isToday })
    }

    @Test
    fun `hasEntry reflects the provided entry dates`() {
        val entryDates = setOf(today.minusDays(1), today.minusDays(3))

        val days = buildStreakDays(entryDates = entryDates, today = today)

        assertTrue(days[5].hasEntry) // yesterday
        assertTrue(days[3].hasEntry) // 3 days ago
        assertFalse(days[6].hasEntry) // today — not in the set
        assertFalse(days[0].hasEntry) // 6 days ago — not in the set
    }
}
