package com.bravoscribe.android.ui.entries.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bravoscribe.android.domain.model.JournalEntry
import com.bravoscribe.android.domain.repository.JournalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EntryDetailUiState(
    val isLoading: Boolean = true,
    val entry: JournalEntry? = null,
    val isDeleted: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class EntryDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val journalRepository: JournalRepository,
) : ViewModel() {

    private val entryId: String = checkNotNull(savedStateHandle["entryId"])

    private val _uiState = MutableStateFlow(EntryDetailUiState())
    val uiState: StateFlow<EntryDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            journalRepository.getEntry(entryId)
                .onSuccess { entry -> _uiState.update { it.copy(isLoading = false, entry = entry) } }
                .onFailure {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Couldn't load this entry.")
                    }
                }
        }
    }

    fun deleteEntry() {
        viewModelScope.launch {
            journalRepository.deleteEntry(entryId)
                .onSuccess { _uiState.update { it.copy(isDeleted = true) } }
                .onFailure {
                    _uiState.update { it.copy(errorMessage = "Couldn't delete this entry.") }
                }
        }
    }
}
