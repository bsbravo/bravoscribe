package com.bravoscribe.android.domain.model

data class JournalEntry(
    val id: String,
    val entryDate: String,
    val title: String?,
    val content: String,
    val mood: Mood?,
    val tags: List<Tag>,
    val createdAt: String,
    val updatedAt: String,
    val isSynced: Boolean = true,
)

data class Stats(
    val totalEntries: Long,
    val totalWords: Long,
    val currentStreak: Int,
    val longestStreak: Int,
    val firstEntryDate: String?,
)
