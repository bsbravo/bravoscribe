package com.bravoscribe.android.ui.util

private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

fun isValidEmail(email: String): Boolean = EMAIL_REGEX.matches(email.trim())

fun isValidName(name: String): Boolean = name.trim().length in 2..100

fun isValidPassword(password: String): Boolean = password.length in 8..128
