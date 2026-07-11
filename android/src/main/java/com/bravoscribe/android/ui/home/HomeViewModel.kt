package com.bravoscribe.android.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bravoscribe.android.data.local.ConnectivityObserver
import com.bravoscribe.android.domain.model.JournalEntry
import com.bravoscribe.android.domain.model.Mood
import com.bravoscribe.android.domain.model.Tag
import com.bravoscribe.android.domain.repository.JournalRepository
import com.bravoscribe.android.ui.components.StreakDay
import com.bravoscribe.android.ui.components.buildStreakDays
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

private const val AUTOSAVE_DELAY_MS = 30_000L

data class HomeUiState(
    val isLoading: Boolean = true,
    val today: LocalDate = LocalDate.now(),
    val entryId: String? = null,
    val isEditorOpen: Boolean = false,
    val title: String = "",
    val content: String = "",
    val mood: Mood? = null,
    val selectedTags: List<Tag> = emptyList(),
    val availableTags: List<Tag> = emptyList(),
    val isDirty: Boolean = false,
    val isSaving: Boolean = false,
    val streakDays: List<StreakDay> = emptyList(),
    val currentStreak: Int = 0,
    val isOffline: Boolean = false,
    val snackbarMessage: String? = null,
) {
    val isSaved: Boolean get() = entryId != null && !isDirty
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val journalRepository: JournalRepository,
    connectivityObserver: ConnectivityObserver,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var autosaveJob: Job? = null

    init {
        journalRepository.observeTags()
            .onEach { tags -> _uiState.update { it.copy(availableTags = tags) } }
            .launchIn(viewModelScope)

        viewModelScope.launch { journalRepository.refreshTags() }

        val isOnline = connectivityObserver.isOnline.distinctUntilChanged()
        isOnline
            .onEach { online -> _uiState.update { it.copy(isOffline = !online) } }
            .launchIn(viewModelScope)
        isOnline
            .drop(1)
            .filter { online -> online }
            .onEach {
                journalRepository.syncPendingWrites()
                loadToday()
            }
            .launchIn(viewModelScope)

        loadToday()
    }

    private fun loadToday() {
        val today = _uiState.value.today
        viewModelScope.launch {
            journalRepository.getEntryByDate(today.toString())
                .onSuccess { entry -> applyLoadedEntry(entry) }
            loadStreak(today)
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private fun applyLoadedEntry(entry: JournalEntry?) {
        if (entry == null) return
        _uiState.update {
            it.copy(
                entryId = entry.id,
                isEditorOpen = true,
                title = entry.title.orEmpty(),
                content = entry.content,
                mood = entry.mood,
                selectedTags = entry.tags,
                isDirty = false,
            )
        }
    }

    private suspend fun loadStreak(today: LocalDate) {
        val from = today.minusDays(6)
        val dates = journalRepository.getEntryDates(from.toString(), today.toString())
            .getOrNull()
            .orEmpty()
            .map(LocalDate::parse)
            .toSet()
        val currentStreak = journalRepository.getStats().getOrNull()?.currentStreak ?: 0
        _uiState.update {
            it.copy(streakDays = buildStreakDays(dates, today), currentStreak = currentStreak)
        }
    }

    fun startWriting() {
        _uiState.update { it.copy(isEditorOpen = true) }
    }

    fun onTitleChange(value: String) {
        if (value.length > 255) return
        _uiState.update { it.copy(title = value, isDirty = true) }
        scheduleAutosave()
    }

    fun onContentChange(value: String) {
        if (value.length > 10_000) return
        _uiState.update { it.copy(content = value, isDirty = true) }
        scheduleAutosave()
    }

    fun onMoodChange(value: Mood?) {
        _uiState.update { it.copy(mood = value, isDirty = true) }
        scheduleAutosave()
    }

    fun onAddTag(tag: Tag) {
        _uiState.update { it.copy(selectedTags = it.selectedTags + tag, isDirty = true) }
        scheduleAutosave()
    }

    fun onRemoveTag(tag: Tag) {
        _uiState.update { it.copy(selectedTags = it.selectedTags - tag, isDirty = true) }
        scheduleAutosave()
    }

    fun onCreateTag(name: String) {
        viewModelScope.launch {
            journalRepository.createTag(name).onSuccess { tag -> onAddTag(tag) }
        }
    }

    private fun scheduleAutosave() {
        autosaveJob?.cancel()
        if (_uiState.value.content.isBlank()) return
        autosaveJob = viewModelScope.launch {
            delay(AUTOSAVE_DELAY_MS)
            save()
        }
    }

    fun save() {
        val state = _uiState.value
        if (state.content.isBlank() || state.isSaving) return
        autosaveJob?.cancel()

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val tagIds = state.selectedTags.map { it.id }
            val result = if (state.entryId == null) {
                journalRepository.createEntry(
                    entryDate = state.today.toString(),
                    title = state.title.ifBlank { null },
                    content = state.content,
                    mood = state.mood,
                    tagIds = tagIds,
                )
            } else {
                journalRepository.updateEntry(
                    id = state.entryId,
                    title = state.title.ifBlank { null },
                    content = state.content,
                    mood = state.mood,
                    tagIds = tagIds,
                )
            }

            result
                .onSuccess { entry ->
                    _uiState.update {
                        it.copy(entryId = entry.id, isDirty = false, isSaving = false)
                    }
                    loadStreak(state.today)
                }
                .onFailure {
                    _uiState.update {
                        it.copy(isSaving = false, snackbarMessage = "Couldn't save your entry. Please try again.")
                    }
                }
        }
    }

    fun consumeSnackbarMessage() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}
