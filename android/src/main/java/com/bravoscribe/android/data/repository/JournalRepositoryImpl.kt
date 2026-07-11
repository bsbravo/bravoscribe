package com.bravoscribe.android.data.repository

import com.bravoscribe.android.data.local.db.dao.JournalEntryDao
import com.bravoscribe.android.data.local.db.dao.TagDao
import com.bravoscribe.android.data.local.db.entity.JournalEntryEntity
import com.bravoscribe.android.data.local.db.entity.TagEntity
import com.bravoscribe.android.data.remote.api.JournalApi
import com.bravoscribe.android.data.remote.dto.CreateEntryRequest
import com.bravoscribe.android.data.remote.dto.CreateTagRequest
import com.bravoscribe.android.data.remote.dto.JournalEntryResponse
import com.bravoscribe.android.data.remote.dto.TagResponse
import com.bravoscribe.android.data.remote.dto.UpdateEntryRequest
import com.bravoscribe.android.domain.model.JournalEntry
import com.bravoscribe.android.domain.model.Mood
import com.bravoscribe.android.domain.model.Stats
import com.bravoscribe.android.domain.model.Tag
import com.bravoscribe.android.domain.repository.JournalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JournalRepositoryImpl @Inject constructor(
    private val journalApi: JournalApi,
    private val journalEntryDao: JournalEntryDao,
    private val tagDao: TagDao,
    private val json: Json,
) : JournalRepository {

    private val tagListSerializer = ListSerializer(TagResponse.serializer())

    override fun observeEntries(query: String?): Flow<List<JournalEntry>> {
        val flow = if (query.isNullOrBlank()) journalEntryDao.observeAll() else journalEntryDao.observeSearch(query)
        return flow.map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun refreshEntries(from: String?, to: String?): Result<Unit> = runCatching {
        val page = journalApi.listEntries(from = from, to = to)
        journalEntryDao.upsertAll(page.content.map { it.toEntity(isSynced = true) })
    }

    override suspend fun getEntry(id: String): Result<JournalEntry> = runCatching {
        val response = journalApi.getEntry(id)
        journalEntryDao.upsert(response.toEntity(isSynced = true))
        response.toDomain()
    }

    override suspend fun getEntryByDate(date: String): Result<JournalEntry?> = runCatching {
        val response = try {
            journalApi.getEntryByDate(date)
        } catch (e: HttpException) {
            if (e.code() == 404) return@runCatching journalEntryDao.getByDate(date)?.toDomain()
            throw e
        }
        journalEntryDao.upsert(response.toEntity(isSynced = true))
        response.toDomain()
    }

    override suspend fun getEntryDates(from: String, to: String): Result<List<String>> = runCatching {
        journalApi.getEntryDates(from, to)
    }

    override suspend fun createEntry(
        entryDate: String,
        title: String?,
        content: String,
        mood: Mood?,
        tagIds: List<String>,
    ): Result<JournalEntry> = runCatching {
        val response = journalApi.createEntry(CreateEntryRequest(entryDate, title, content, mood, tagIds))
        journalEntryDao.upsert(response.toEntity(isSynced = true))
        response.toDomain()
    }

    override suspend fun updateEntry(
        id: String,
        title: String?,
        content: String,
        mood: Mood?,
        tagIds: List<String>,
    ): Result<JournalEntry> = runCatching {
        val response = journalApi.updateEntry(id, UpdateEntryRequest(title, content, mood, tagIds))
        journalEntryDao.upsert(response.toEntity(isSynced = true))
        response.toDomain()
    }

    override suspend fun deleteEntry(id: String): Result<Unit> = runCatching {
        journalApi.deleteEntry(id)
        journalEntryDao.deleteById(id)
    }

    override suspend fun exportEntries(from: String, to: String): Result<ResponseBody> = runCatching {
        journalApi.exportEntries(from, to)
    }

    override suspend fun getStats(): Result<Stats> = runCatching {
        val response = journalApi.getStats()
        Stats(
            totalEntries = response.totalEntries,
            totalWords = response.totalWords,
            currentStreak = response.currentStreak,
            longestStreak = response.longestStreak,
            firstEntryDate = response.firstEntryDate,
        )
    }

    override fun observeTags(): Flow<List<Tag>> =
        tagDao.observeAll().map { entities -> entities.map { Tag(it.id, it.name) } }

    override suspend fun refreshTags(): Result<Unit> = runCatching {
        val tags = journalApi.listTags()
        tagDao.upsertAll(tags.map { TagEntity(it.id, it.name) })
    }

    override suspend fun createTag(name: String): Result<Tag> = runCatching {
        val response = journalApi.createTag(CreateTagRequest(name))
        tagDao.upsertAll(listOf(TagEntity(response.id, response.name)))
        Tag(response.id, response.name)
    }

    override suspend fun deleteTag(id: String): Result<Unit> = runCatching {
        journalApi.deleteTag(id)
        tagDao.deleteById(id)
    }

    override suspend fun syncPendingWrites() {
        // Offline write queueing is implemented as part of the offline-caching pass
        // (see IMPLEMENTATION_PLAN.md phase 6 task: "Offline behavior + Room caching").
        // Until then, createEntry/updateEntry/deleteEntry are network-first and fail
        // fast when offline rather than silently queueing.
    }

    private fun JournalEntryResponse.toEntity(isSynced: Boolean) = JournalEntryEntity(
        id = id,
        entryDate = entryDate,
        title = title,
        content = content,
        mood = mood,
        tagsJson = json.encodeToString(tagListSerializer, tags),
        createdAt = createdAt,
        updatedAt = updatedAt,
        isSynced = isSynced,
    )

    private fun JournalEntryResponse.toDomain() = JournalEntry(
        id = id,
        entryDate = entryDate,
        title = title,
        content = content,
        mood = mood,
        tags = tags.map { Tag(it.id, it.name) },
        createdAt = createdAt,
        updatedAt = updatedAt,
        isSynced = true,
    )

    private fun JournalEntryEntity.toDomain() = JournalEntry(
        id = id,
        entryDate = entryDate,
        title = title,
        content = content,
        mood = mood,
        tags = json.decodeFromString(tagListSerializer, tagsJson).map { Tag(it.id, it.name) },
        createdAt = createdAt,
        updatedAt = updatedAt,
        isSynced = isSynced,
    )
}
