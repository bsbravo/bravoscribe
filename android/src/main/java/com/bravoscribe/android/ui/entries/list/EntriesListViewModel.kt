package com.bravoscribe.android.ui.entries.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bravoscribe.android.data.local.ConnectivityObserver
import com.bravoscribe.android.domain.model.JournalEntry
import com.bravoscribe.android.domain.repository.JournalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val SEARCH_DEBOUNCE_MS = 500L
private const val UNDO_WINDOW_MS = 4_000L

data class EntriesListUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val searchExpanded: Boolean = false,
    val searchQuery: String = "",
    val entries: List<JournalEntry> = emptyList(),
    val isOffline: Boolean = false,
    val snackbarMessage: String? = null,
    val snackbarShowUndo: Boolean = false,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class EntriesListViewModel @Inject constructor(
    private val journalRepository: JournalRepository,
    connectivityObserver: ConnectivityObserver,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EntriesListUiState())
    val uiState: StateFlow<EntriesListUiState> = _uiState.asStateFlow()

    private val searchQueryFlow = MutableStateFlow("")
    private val pendingDeletes = mutableMapOf<String, Pair<JournalEntry, Job>>()

    init {
        viewModelScope.launch {
            journalRepository.refreshEntries()
            _uiState.update { it.copy(isLoading = false) }
        }

        searchQueryFlow
            .debounce(SEARCH_DEBOUNCE_MS)
            .distinctUntilChanged()
            .flatMapLatest { query -> journalRepository.observeEntries(query.ifBlank { null }) }
            .onEach { entries ->
                val hiddenIds = pendingDeletes.keys
                _uiState.update { it.copy(entries = entries.filterNot { e -> e.id in hiddenIds }) }
            }
            .launchIn(viewModelScope)

        val isOnline = connectivityObserver.isOnline.distinctUntilChanged()
        isOnline
            .onEach { online -> _uiState.update { it.copy(isOffline = !online) } }
            .launchIn(viewModelScope)
        isOnline
            .drop(1)
            .filter { online -> online }
            .onEach {
                journalRepository.syncPendingWrites()
                journalRepository.refreshEntries()
            }
            .launchIn(viewModelScope)
    }

    fun onSearchQueryChange(value: String) {
        _uiState.update { it.copy(searchQuery = value) }
        searchQueryFlow.value = value
    }

    fun toggleSearch() {
        val expanding = !_uiState.value.searchExpanded
        _uiState.update { it.copy(searchExpanded = expanding, searchQuery = "") }
        searchQueryFlow.value = ""
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            journalRepository.refreshEntries()
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    fun deleteEntry(entry: JournalEntry) {
        _uiState.update {
            it.copy(
                entries = it.entries.filterNot { e -> e.id == entry.id },
                snackbarMessage = "Entry deleted",
                snackbarShowUndo = true,
            )
        }
        val job = viewModelScope.launch {
            delay(UNDO_WINDOW_MS)
            journalRepository.deleteEntry(entry.id)
            pendingDeletes.remove(entry.id)
        }
        pendingDeletes[entry.id] = entry to job
    }

    fun undoDelete() {
        val lastId = pendingDeletes.keys.lastOrNull() ?: return
        val (entry, job) = pendingDeletes.remove(lastId) ?: return
        job.cancel()
        _uiState.update {
            it.copy(entries = (it.entries + entry).sortedByDescending { e -> e.entryDate })
        }
    }

    fun consumeSnackbarMessage() {
        _uiState.update { it.copy(snackbarMessage = null, snackbarShowUndo = false) }
    }
}
