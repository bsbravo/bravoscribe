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
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val LOCAL_ID_PREFIX = "local-"

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

    override suspend fun getEntry(id: String): Result<JournalEntry> = try {
        val response = journalApi.getEntry(id)
        journalEntryDao.upsert(response.toEntity(isSynced = true))
        Result.success(response.toDomain())
    } catch (e: IOException) {
        journalEntryDao.getById(id)?.let { Result.success(it.toDomain()) } ?: Result.failure(e)
    }

    override suspend fun getEntryByDate(date: String): Result<JournalEntry?> = try {
        val response = journalApi.getEntryByDate(date)
        journalEntryDao.upsert(response.toEntity(isSynced = true))
        Result.success(response.toDomain())
    } catch (e: HttpException) {
        if (e.code() == 404) Result.success(journalEntryDao.getByDate(date)?.toDomain()) else Result.failure(e)
    } catch (e: IOException) {
        Result.success(journalEntryDao.getByDate(date)?.toDomain())
    }

    override suspend fun getEntryDates(from: String, to: String): Result<List<String>> = try {
        Result.success(journalApi.getEntryDates(from, to))
    } catch (e: IOException) {
        val fromDate = LocalDate.parse(from)
        val toDate = LocalDate.parse(to)
        val cachedDates = journalEntryDao.getAllEntryDates().filter {
            val date = LocalDate.parse(it)
            !date.isBefore(fromDate) && !date.isAfter(toDate)
        }
        Result.success(cachedDates)
    }

    override suspend fun createEntry(
        entryDate: String,
        title: String?,
        content: String,
        mood: Mood?,
        tagIds: List<String>,
    ): Result<JournalEntry> = try {
        val response = journalApi.createEntry(CreateEntryRequest(entryDate, title, content, mood, tagIds))
        journalEntryDao.upsert(response.toEntity(isSynced = true))
        Result.success(response.toDomain())
    } catch (e: IOException) {
        val now = Instant.now().toString()
        val entity = JournalEntryEntity(
            id = "$LOCAL_ID_PREFIX${UUID.randomUUID()}",
            entryDate = entryDate,
            title = title,
            content = content,
            mood = mood,
            tagsJson = encodeTags(resolveTags(tagIds)),
            createdAt = now,
            updatedAt = now,
            isSynced = false,
        )
        journalEntryDao.upsert(entity)
        Result.success(entity.toDomain())
    }

    override suspend fun updateEntry(
        id: String,
        title: String?,
        content: String,
        mood: Mood?,
        tagIds: List<String>,
    ): Result<JournalEntry> = try {
        val response = journalApi.updateEntry(id, UpdateEntryRequest(title, content, mood, tagIds))
        journalEntryDao.upsert(response.toEntity(isSynced = true))
        Result.success(response.toDomain())
    } catch (e: IOException) {
        val existing = journalEntryDao.getById(id) ?: return Result.failure(e)
        val updated = existing.copy(
            title = title,
            content = content,
            mood = mood,
            tagsJson = encodeTags(resolveTags(tagIds)),
            updatedAt = Instant.now().toString(),
            isSynced = false,
        )
        journalEntryDao.upsert(updated)
        Result.success(updated.toDomain())
    }

    override suspend fun deleteEntry(id: String): Result<Unit> = try {
        journalApi.deleteEntry(id)
        journalEntryDao.deleteById(id)
        Result.success(Unit)
    } catch (e: IOException) {
        if (id.startsWith(LOCAL_ID_PREFIX)) {
            journalEntryDao.deleteById(id)
        } else {
            journalEntryDao.markDeleted(id)
        }
        Result.success(Unit)
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
        val pending = journalEntryDao.getUnsynced()
        for (entity in pending) {
            if (entity.isDeleted) {
                val deletedRemotely = entity.id.startsWith(LOCAL_ID_PREFIX) ||
                    runCatching { journalApi.deleteEntry(entity.id) }.isSuccess
                if (deletedRemotely) journalEntryDao.deleteById(entity.id)
                continue
            }

            val tagIds = json.decodeFromString(tagListSerializer, entity.tagsJson).map { it.id }
            val result = runCatching {
                if (entity.id.startsWith(LOCAL_ID_PREFIX)) {
                    journalApi.createEntry(CreateEntryRequest(entity.entryDate, entity.title, entity.content, entity.mood, tagIds))
                } else {
                    journalApi.updateEntry(entity.id, UpdateEntryRequest(entity.title, entity.content, entity.mood, tagIds))
                }
            }
            result.onSuccess { response ->
                if (entity.id.startsWith(LOCAL_ID_PREFIX)) journalEntryDao.deleteById(entity.id)
                journalEntryDao.upsert(response.toEntity(isSynced = true))
            }
            // On failure the row stays isSynced = false and is retried on the next sync pass.
        }
    }

    private suspend fun resolveTags(tagIds: List<String>): List<Tag> =
        tagDao.getByIds(tagIds).map { Tag(it.id, it.name) }

    private fun encodeTags(tags: List<Tag>): String =
        json.encodeToString(tagListSerializer, tags.map { TagResponse(it.id, it.name) })

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
