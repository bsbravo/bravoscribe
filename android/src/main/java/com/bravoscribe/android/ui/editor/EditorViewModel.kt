package com.bravoscribe.android.ui.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bravoscribe.android.domain.model.JournalEntry
import com.bravoscribe.android.domain.model.Mood
import com.bravoscribe.android.domain.model.Tag
import com.bravoscribe.android.domain.repository.JournalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

private const val AUTOSAVE_DELAY_MS = 30_000L

private data class EntrySnapshot(
    val title: String,
    val content: String,
    val mood: Mood?,
    val tags: List<Tag>,
)

data class EditorUiState(
    val isLoading: Boolean = true,
    val date: LocalDate = LocalDate.now(),
    val entryId: String? = null,
    val title: String = "",
    val content: String = "",
    val mood: Mood? = null,
    val selectedTags: List<Tag> = emptyList(),
    val availableTags: List<Tag> = emptyList(),
    val isDirty: Boolean = false,
    val isSaving: Boolean = false,
    val snackbarMessage: String? = null,
    val snackbarShowUndo: Boolean = false,
) {
    val isToday: Boolean get() = date == LocalDate.now()
    val isSaved: Boolean get() = entryId != null && !isDirty
    val daysAgo: Long get() = ChronoUnit.DAYS.between(date, LocalDate.now())
}

@HiltViewModel
class EditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val journalRepository: JournalRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState(date = LocalDate.parse(checkNotNull(savedStateHandle["date"]))))
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private var autosaveJob: Job? = null
    private var loadedSnapshot = EntrySnapshot("", "", null, emptyList())

    init {
        journalRepository.observeTags()
            .onEach { tags -> _uiState.update { it.copy(availableTags = tags) } }
            .launchIn(viewModelScope)
        viewModelScope.launch { journalRepository.refreshTags() }

        loadEntry()
    }

    private fun loadEntry() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            journalRepository.getEntryByDate(_uiState.value.date.toString())
                .onSuccess { entry -> applyLoadedEntry(entry) }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private fun applyLoadedEntry(entry: JournalEntry?) {
        loadedSnapshot = if (entry == null) {
            EntrySnapshot("", "", null, emptyList())
        } else {
            EntrySnapshot(entry.title.orEmpty(), entry.content, entry.mood, entry.tags)
        }
        _uiState.update {
            it.copy(
                entryId = entry?.id,
                title = loadedSnapshot.title,
                content = loadedSnapshot.content,
                mood = loadedSnapshot.mood,
                selectedTags = loadedSnapshot.tags,
                isDirty = false,
            )
        }
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
            persist(
                title = state.title,
                content = state.content,
                mood = state.mood,
                tags = state.selectedTags,
                successMessage = "Editing ${monthDay(state.date)} · changes saved",
                showUndo = true,
            )
        }
    }

    fun undoLastSave() {
        val snapshot = loadedSnapshot
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            persist(
                title = snapshot.title,
                content = snapshot.content,
                mood = snapshot.mood,
                tags = snapshot.tags,
                successMessage = "Change undone",
                showUndo = false,
            )
        }
    }

    private suspend fun persist(
        title: String,
        content: String,
        mood: Mood?,
        tags: List<Tag>,
        successMessage: String,
        showUndo: Boolean,
    ) {
        val entryId = _uiState.value.entryId
        val tagIds = tags.map { it.id }
        val result = if (entryId == null) {
            journalRepository.createEntry(_uiState.value.date.toString(), title.ifBlank { null }, content, mood, tagIds)
        } else {
            journalRepository.updateEntry(entryId, title.ifBlank { null }, content, mood, tagIds)
        }

        result
            .onSuccess { entry ->
                applyLoadedEntry(entry)
                _uiState.update {
                    it.copy(isSaving = false, snackbarMessage = successMessage, snackbarShowUndo = showUndo)
                }
            }
            .onFailure {
                _uiState.update {
                    it.copy(isSaving = false, snackbarMessage = "Couldn't save. Please try again.", snackbarShowUndo = false)
                }
            }
    }

    fun consumeSnackbarMessage() {
        _uiState.update { it.copy(snackbarMessage = null, snackbarShowUndo = false) }
    }

    private fun monthDay(date: LocalDate): String =
        date.format(java.time.format.DateTimeFormatter.ofPattern("MMM d"))
}
