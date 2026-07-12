package com.bravoscribe.android.data.remote.dto

import com.bravoscribe.android.domain.model.Mood
import kotlinx.serialization.Serializable

@Serializable
data class TagResponse(val id: String, val name: String)

@Serializable
data class CreateTagRequest(val name: String)

@Serializable
data class JournalEntryResponse(
    val id: String,
    val entryDate: String,
    val title: String? = null,
    val content: String,
    val mood: Mood? = null,
    val tags: List<TagResponse> = emptyList(),
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class CreateEntryRequest(
    val entryDate: String,
    val title: String? = null,
    val content: String,
    val mood: Mood? = null,
    val tagIds: List<String> = emptyList(),
)

@Serializable
data class UpdateEntryRequest(
    val title: String? = null,
    val content: String,
    val mood: Mood? = null,
    val tagIds: List<String> = emptyList(),
)

@Serializable
data class StatsResponse(
    val totalEntries: Long,
    val totalWords: Long,
    val currentStreak: Int,
    val longestStreak: Int,
    val firstEntryDate: String? = null,
)

@Serializable
data class PageResponse<T>(
    val content: List<T>,
    val totalElements: Long,
    val totalPages: Int,
    val number: Int,
    val size: Int,
    val first: Boolean,
    val last: Boolean,
    val empty: Boolean,
)
