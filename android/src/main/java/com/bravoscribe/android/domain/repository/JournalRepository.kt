package com.bravoscribe.android.domain.repository

import com.bravoscribe.android.domain.model.JournalEntry
import com.bravoscribe.android.domain.model.Mood
import com.bravoscribe.android.domain.model.Stats
import com.bravoscribe.android.domain.model.Tag
import kotlinx.coroutines.flow.Flow
import okhttp3.ResponseBody

interface JournalRepository {
    /** Cached entries (Room), optionally filtered by a title/content search term. */
    fun observeEntries(query: String? = null): Flow<List<JournalEntry>>

    /** Refreshes the local cache from the network for the given date range. */
    suspend fun refreshEntries(from: String? = null, to: String? = null): Result<Unit>

    suspend fun getEntry(id: String): Result<JournalEntry>
    suspend fun getEntryByDate(date: String): Result<JournalEntry?>
    suspend fun getEntryDates(from: String, to: String): Result<List<String>>

    suspend fun createEntry(
        entryDate: String,
        title: String?,
        content: String,
        mood: Mood?,
        tagIds: List<String>,
    ): Result<JournalEntry>

    suspend fun updateEntry(
        id: String,
        title: String?,
        content: String,
        mood: Mood?,
        tagIds: List<String>,
    ): Result<JournalEntry>

    suspend fun deleteEntry(id: String): Result<Unit>

    suspend fun exportEntries(from: String, to: String): Result<ResponseBody>

    suspend fun getStats(): Result<Stats>

    fun observeTags(): Flow<List<Tag>>
    suspend fun refreshTags(): Result<Unit>
    suspend fun createTag(name: String): Result<Tag>
    suspend fun deleteTag(id: String): Result<Unit>

    /** Pushes any offline-queued creates/updates/deletes to the server. */
    suspend fun syncPendingWrites()
}
