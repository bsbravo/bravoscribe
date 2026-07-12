package com.bravoscribe.android.ui.home

import com.bravoscribe.android.MainDispatcherExtension
import com.bravoscribe.android.data.local.ConnectivityObserver
import com.bravoscribe.android.domain.model.JournalEntry
import com.bravoscribe.android.domain.model.Mood
import com.bravoscribe.android.domain.model.Stats
import com.bravoscribe.android.domain.repository.JournalRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherExtension()

    private val journalRepository = mockk<JournalRepository>()
    private val connectivityObserver = mockk<ConnectivityObserver>()

    private val emptyStats = Stats(totalEntries = 0, totalWords = 0, currentStreak = 0, longestStreak = 0, firstEntryDate = null)

    private fun savedEntry(id: String = "entry-1", content: String) = JournalEntry(
        id = id,
        entryDate = "2026-07-11",
        title = null,
        content = content,
        mood = Mood.GOOD,
        tags = emptyList(),
        createdAt = "2026-07-11T09:00:00Z",
        updatedAt = "2026-07-11T09:00:00Z",
    )

    private fun createViewModel(existingEntry: JournalEntry? = null): HomeViewModel {
        every { connectivityObserver.isOnline } returns flowOf(true)
        every { journalRepository.observeTags() } returns flowOf(emptyList())
        coEvery { journalRepository.refreshTags() } returns Result.success(Unit)
        coEvery { journalRepository.getEntryByDate(any()) } returns Result.success(existingEntry)
        coEvery { journalRepository.getEntryDates(any(), any()) } returns Result.success(emptyList())
        coEvery { journalRepository.getStats() } returns Result.success(emptyStats)
        return HomeViewModel(journalRepository, connectivityObserver)
    }

    @Test
    fun `starts in the empty state when there is no entry for today`() {
        val viewModel = createViewModel(existingEntry = null)

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.isEditorOpen)
        assertNull(state.entryId)
    }

    @Test
    fun `loads an already-saved entry into the open editor`() {
        val viewModel = createViewModel(existingEntry = savedEntry(content = "Already written"))

        val state = viewModel.uiState.value
        assertTrue(state.isEditorOpen)
        assertEquals("entry-1", state.entryId)
        assertEquals("Already written", state.content)
        assertFalse(state.isDirty)
    }

    @Test
    fun `startWriting opens the editor`() {
        val viewModel = createViewModel()

        viewModel.startWriting()

        assertTrue(viewModel.uiState.value.isEditorOpen)
    }

    @Test
    fun `save is a no-op when content is blank`() {
        val viewModel = createViewModel()
        viewModel.startWriting()

        viewModel.save()

        coVerify(exactly = 0) { journalRepository.createEntry(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `first save creates a new entry and marks the state saved`() {
        val viewModel = createViewModel()
        viewModel.startWriting()
        coEvery {
            journalRepository.createEntry(any(), any(), content = "My first entry", any(), any())
        } returns Result.success(savedEntry(content = "My first entry"))

        viewModel.onContentChange("My first entry")
        viewModel.save()

        coVerify(exactly = 1) { journalRepository.createEntry(any(), any(), "My first entry", any(), any()) }
        val state = viewModel.uiState.value
        assertEquals("entry-1", state.entryId)
        assertFalse(state.isDirty)
        assertTrue(state.isSaved)
    }

    @Test
    fun `saving again after the entry exists updates it instead of creating a new one`() {
        val viewModel = createViewModel(existingEntry = savedEntry(content = "Original"))
        coEvery {
            journalRepository.updateEntry("entry-1", any(), "Original edited", any(), any())
        } returns Result.success(savedEntry(content = "Original edited"))

        viewModel.onContentChange("Original edited")
        viewModel.save()

        coVerify(exactly = 1) { journalRepository.updateEntry("entry-1", any(), "Original edited", any(), any()) }
        coVerify(exactly = 0) { journalRepository.createEntry(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `editing marks the entry dirty until the save completes`() {
        val viewModel = createViewModel(existingEntry = savedEntry(content = "Original"))
        coEvery {
            journalRepository.updateEntry(any(), any(), any(), any(), any())
        } returns Result.success(savedEntry(content = "Changed"))

        viewModel.onContentChange("Changed")
        assertTrue(viewModel.uiState.value.isDirty)
        assertFalse(viewModel.uiState.value.isSaved)

        viewModel.save()
        assertFalse(viewModel.uiState.value.isDirty)
        assertTrue(viewModel.uiState.value.isSaved)
    }

    @Test
    fun `autosave fires 30 seconds after the last keystroke`() {
        val viewModel = createViewModel()
        viewModel.startWriting()
        coEvery {
            journalRepository.createEntry(any(), any(), any(), any(), any())
        } returns Result.success(savedEntry(content = "Autosaved"))

        viewModel.onContentChange("Autosaved")
        coVerify(exactly = 0) { journalRepository.createEntry(any(), any(), any(), any(), any()) }

        mainDispatcherExtension.dispatcher.scheduler.advanceTimeBy(30_000)
        mainDispatcherExtension.dispatcher.scheduler.runCurrent()

        coVerify(exactly = 1) { journalRepository.createEntry(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `autosave does not fire before 30 seconds of inactivity`() {
        val viewModel = createViewModel()
        viewModel.startWriting()

        viewModel.onContentChange("Still typing")
        mainDispatcherExtension.dispatcher.scheduler.advanceTimeBy(29_000)
        mainDispatcherExtension.dispatcher.scheduler.runCurrent()

        coVerify(exactly = 0) { journalRepository.createEntry(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `further typing resets the autosave timer`() {
        val viewModel = createViewModel()
        viewModel.startWriting()

        viewModel.onContentChange("First")
        mainDispatcherExtension.dispatcher.scheduler.advanceTimeBy(20_000)
        viewModel.onContentChange("First and more")
        mainDispatcherExtension.dispatcher.scheduler.advanceTimeBy(20_000)
        mainDispatcherExtension.dispatcher.scheduler.runCurrent()

        // 40s of wall time passed but the timer restarted at the 20s mark,
        // so only 20s have elapsed since the last keystroke — no autosave yet.
        coVerify(exactly = 0) { journalRepository.createEntry(any(), any(), any(), any(), any()) }
    }
}
