package com.bravoscribe.android.domain.model

data class User(
    val id: String,
    val name: String,
    val email: String,
    val role: String,
    val active: Boolean,
    val createdAt: String,
)

data class Preferences(
    val reminderTime: String,
    val weeklySummaryEnabled: Boolean,
)
