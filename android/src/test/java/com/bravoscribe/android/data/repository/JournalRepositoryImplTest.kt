package com.bravoscribe.android.data.repository

import com.bravoscribe.android.data.local.db.dao.JournalEntryDao
import com.bravoscribe.android.data.local.db.dao.TagDao
import com.bravoscribe.android.data.local.db.entity.JournalEntryEntity
import com.bravoscribe.android.data.local.db.entity.TagEntity
import com.bravoscribe.android.data.remote.api.JournalApi
import com.bravoscribe.android.data.remote.dto.JournalEntryResponse
import com.bravoscribe.android.data.remote.dto.TagResponse
import com.bravoscribe.android.domain.model.Mood
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class JournalRepositoryImplTest {

    private val journalApi = mockk<JournalApi>()
    private val journalEntryDao = mockk<JournalEntryDao>()
    private val tagDao = mockk<TagDao>()
    private lateinit var repository: JournalRepositoryImpl

    @BeforeEach
    fun setUp() {
        repository = JournalRepositoryImpl(journalApi, journalEntryDao, tagDao, Json { ignoreUnknownKeys = true })
    }

    private fun httpException(code: Int): HttpException =
        HttpException(Response.error<Any>(code, "".toResponseBody(null)))

    private fun cachedEntity(id: String = "entry-1", synced: Boolean = true, deleted: Boolean = false) = JournalEntryEntity(
        id = id,
        entryDate = "2026-07-11",
        title = null,
        content = "Cached content",
        mood = Mood.GOOD,
        tagsJson = "[]",
        createdAt = "2026-07-11T09:00:00Z",
        updatedAt = "2026-07-11T09:00:00Z",
        isSynced = synced,
        isDeleted = deleted,
    )

    private fun remoteEntry(id: String = "entry-1", content: String = "Remote content") = JournalEntryResponse(
        id = id,
        entryDate = "2026-07-11",
        title = null,
        content = content,
        mood = Mood.GOOD,
        tags = emptyList(),
        createdAt = "2026-07-11T09:00:00Z",
        updatedAt = "2026-07-11T09:00:00Z",
    )

    // ── getEntryByDate ────────────────────────────────────────────

    @Test
    fun `getEntryByDate falls back to the Room cache on a 404`() = runTest {
        coEvery { journalApi.getEntryByDate("2026-07-11") } throws httpException(404)
        coEvery { journalEntryDao.getByDate("2026-07-11") } returns cachedEntity()

        val result = repository.getEntryByDate("2026-07-11")

        assertTrue(result.isSuccess)
        assertEquals("Cached content", result.getOrNull()?.content)
    }

    @Test
    fun `getEntryByDate falls back to the Room cache when offline`() = runTest {
        coEvery { journalApi.getEntryByDate("2026-07-11") } throws IOException("no network")
        coEvery { journalEntryDao.getByDate("2026-07-11") } returns cachedEntity()

        val result = repository.getEntryByDate("2026-07-11")

        assertTrue(result.isSuccess)
        assertEquals("Cached content", result.getOrNull()?.content)
    }

    @Test
    fun `getEntryByDate propagates non-404 HTTP errors instead of masking them`() = runTest {
        coEvery { journalApi.getEntryByDate("2026-07-11") } throws httpException(500)

        val result = repository.getEntryByDate("2026-07-11")

        assertTrue(result.isFailure)
    }

    // ── createEntry ───────────────────────────────────────────────

    @Test
    fun `createEntry queues a local entry when offline`() = runTest {
        coEvery { journalApi.createEntry(any()) } throws IOException("no network")
        coEvery { tagDao.getByIds(any()) } returns emptyList()
        val savedSlot = slot<JournalEntryEntity>()
        coEvery { journalEntryDao.upsert(capture(savedSlot)) } returns Unit

        val result = repository.createEntry("2026-07-11", null, "Offline entry", Mood.GOOD, emptyList())

        assertTrue(result.isSuccess)
        val entry = result.getOrNull()!!
        assertTrue(entry.id.startsWith("local-"))
        assertFalse(entry.isSynced)
        assertEquals("Offline entry", entry.content)
        assertFalse(savedSlot.captured.isSynced)
    }

    // ── updateEntry ───────────────────────────────────────────────

    @Test
    fun `updateEntry patches the cached row when offline`() = runTest {
        coEvery { journalApi.updateEntry(any(), any()) } throws IOException("no network")
        coEvery { journalEntryDao.getById("entry-1") } returns cachedEntity(id = "entry-1")
        coEvery { tagDao.getByIds(any()) } returns emptyList()
        coEvery { journalEntryDao.upsert(any()) } returns Unit

        val result = repository.updateEntry("entry-1", null, "Edited offline", Mood.BAD, emptyList())

        assertTrue(result.isSuccess)
        val entry = result.getOrNull()!!
        assertEquals("Edited offline", entry.content)
        assertFalse(entry.isSynced)
    }

    @Test
    fun `updateEntry fails when offline and nothing is cached to patch`() = runTest {
        coEvery { journalApi.updateEntry(any(), any()) } throws IOException("no network")
        coEvery { journalEntryDao.getById("missing") } returns null

        val result = repository.updateEntry("missing", null, "Edited offline", null, emptyList())

        assertTrue(result.isFailure)
    }

    // ── deleteEntry ───────────────────────────────────────────────

    @Test
    fun `deleteEntry soft-deletes a synced entry when offline`() = runTest {
        coEvery { journalApi.deleteEntry("entry-1") } throws IOException("no network")
        coEvery { journalEntryDao.markDeleted("entry-1") } returns Unit

        val result = repository.deleteEntry("entry-1")

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { journalEntryDao.markDeleted("entry-1") }
        coVerify(exactly = 0) { journalEntryDao.deleteById(any()) }
    }

    @Test
    fun `deleteEntry hard-deletes a never-synced local entry when offline`() = runTest {
        coEvery { journalApi.deleteEntry("local-abc") } throws IOException("no network")
        coEvery { journalEntryDao.deleteById("local-abc") } returns Unit

        val result = repository.deleteEntry("local-abc")

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { journalEntryDao.deleteById("local-abc") }
        coVerify(exactly = 0) { journalEntryDao.markDeleted(any()) }
    }

    // ── syncPendingWrites ─────────────────────────────────────────

    @Test
    fun `syncPendingWrites pushes a queued local create and replaces the local row`() = runTest {
        val localEntity = cachedEntity(id = "local-abc", synced = false)
        coEvery { journalEntryDao.getUnsynced() } returns listOf(localEntity)
        coEvery { journalApi.createEntry(any()) } returns remoteEntry(id = "entry-9")
        coEvery { journalEntryDao.deleteById("local-abc") } returns Unit
        val upsertedSlot = slot<JournalEntryEntity>()
        coEvery { journalEntryDao.upsert(capture(upsertedSlot)) } returns Unit

        repository.syncPendingWrites()

        coVerify(exactly = 1) { journalEntryDao.deleteById("local-abc") }
        assertEquals("entry-9", upsertedSlot.captured.id)
        assertTrue(upsertedSlot.captured.isSynced)
    }

    @Test
    fun `syncPendingWrites pushes a queued edit to an already-synced entry`() = runTest {
        val editedEntity = cachedEntity(id = "entry-1", synced = false)
        coEvery { journalEntryDao.getUnsynced() } returns listOf(editedEntity)
        coEvery { journalApi.updateEntry("entry-1", any()) } returns remoteEntry(id = "entry-1")
        coEvery { journalEntryDao.upsert(any()) } returns Unit

        repository.syncPendingWrites()

        coVerify(exactly = 1) { journalApi.updateEntry("entry-1", any()) }
        coVerify(exactly = 0) { journalApi.createEntry(any()) }
    }

    @Test
    fun `syncPendingWrites pushes a queued delete and removes the row on success`() = runTest {
        val deletedEntity = cachedEntity(id = "entry-1", synced = false, deleted = true)
        coEvery { journalEntryDao.getUnsynced() } returns listOf(deletedEntity)
        coEvery { journalApi.deleteEntry("entry-1") } returns Unit
        coEvery { journalEntryDao.deleteById("entry-1") } returns Unit

        repository.syncPendingWrites()

        coVerify(exactly = 1) { journalApi.deleteEntry("entry-1") }
        coVerify(exactly = 1) { journalEntryDao.deleteById("entry-1") }
    }

    @Test
    fun `syncPendingWrites leaves the row queued when the retry itself fails`() = runTest {
        val editedEntity = cachedEntity(id = "entry-1", synced = false)
        coEvery { journalEntryDao.getUnsynced() } returns listOf(editedEntity)
        coEvery { journalApi.updateEntry("entry-1", any()) } throws IOException("still offline")

        repository.syncPendingWrites()

        coVerify(exactly = 0) { journalEntryDao.upsert(any()) }
        coVerify(exactly = 0) { journalEntryDao.deleteById(any()) }
    }
}
