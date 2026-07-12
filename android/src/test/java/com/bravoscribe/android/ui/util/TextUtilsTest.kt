package com.bravoscribe.android.ui.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TextUtilsTest {

    @Test
    fun `displayTitle returns the title when present`() {
        assertEquals("My title", displayTitle("My title", "Some content here"))
    }

    @Test
    fun `displayTitle falls back to the first non-blank content line when title is null`() {
        assertEquals("First line", displayTitle(null, "First line\nSecond line"))
    }

    @Test
    fun `displayTitle falls back to the first non-blank content line when title is blank`() {
        assertEquals("First real line", displayTitle("   ", "\n\nFirst real line\nSecond line"))
    }

    @Test
    fun `displayTitle returns empty string when content has no non-blank lines`() {
        assertEquals("", displayTitle(null, "   \n   "))
    }

    @Test
    fun `wordCount counts whitespace-separated words`() {
        assertEquals(5, wordCount("Today was a great day"))
    }

    @Test
    fun `wordCount collapses repeated whitespace and trims`() {
        assertEquals(2, wordCount("  hello    world  "))
    }

    @Test
    fun `wordCount is zero for blank content`() {
        assertEquals(0, wordCount(""))
        assertEquals(0, wordCount("   "))
    }
}
