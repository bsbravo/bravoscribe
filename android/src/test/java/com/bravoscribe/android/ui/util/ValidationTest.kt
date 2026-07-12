package com.bravoscribe.android.ui.util

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ValidationTest {

    @Test
    fun `valid email addresses pass`() {
        assertTrue(isValidEmail("user@example.com"))
        assertTrue(isValidEmail("first.last+tag@sub.example.co"))
        assertTrue(isValidEmail("  user@example.com  "))
    }

    @Test
    fun `invalid email addresses fail`() {
        assertFalse(isValidEmail("not-an-email"))
        assertFalse(isValidEmail("missing-domain@"))
        assertFalse(isValidEmail("@missing-local.com"))
        assertFalse(isValidEmail("no-at-sign.com"))
        assertFalse(isValidEmail(""))
    }

    @Test
    fun `name must be at least 2 characters after trimming`() {
        assertFalse(isValidName(""))
        assertFalse(isValidName("A"))
        assertFalse(isValidName(" A "))
        assertTrue(isValidName("Al"))
        assertTrue(isValidName("Android Tester"))
    }

    @Test
    fun `name must not exceed 100 characters`() {
        assertTrue(isValidName("A".repeat(100)))
        assertFalse(isValidName("A".repeat(101)))
    }

    @Test
    fun `password must be between 8 and 128 characters`() {
        assertFalse(isValidPassword("short"))
        assertTrue(isValidPassword("exactly8"))
        assertTrue(isValidPassword("A".repeat(128)))
        assertFalse(isValidPassword("A".repeat(129)))
    }
}
