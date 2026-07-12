package com.bravoscribe.android.ui.export

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bravoscribe.android.domain.repository.JournalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

enum class ExportRange(val label: String) {
    TODAY("Today"),
    THIS_WEEK("This week"),
    THIS_MONTH("This month"),
    THIS_YEAR("This year"),
    ALL_TIME("All time"),
    CUSTOM("Custom"),
}

data class ExportUiState(
    val selectedRange: ExportRange = ExportRange.THIS_WEEK,
    val customFrom: LocalDate = LocalDate.now().minusDays(30),
    val customTo: LocalDate = LocalDate.now(),
    val isDownloading: Boolean = false,
    val errorMessage: String? = null,
    val downloadedFileName: String? = null,
    val downloadedUri: Uri? = null,
)

@HiltViewModel
class ExportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val journalRepository: JournalRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    fun onRangeSelected(range: ExportRange) {
        _uiState.update {
            it.copy(selectedRange = range, errorMessage = null, downloadedFileName = null, downloadedUri = null)
        }
    }

    fun onCustomRangeChange(from: LocalDate, to: LocalDate) {
        _uiState.update { it.copy(customFrom = from, customTo = to) }
    }

    fun download() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(isDownloading = true, errorMessage = null, downloadedFileName = null, downloadedUri = null)
            }
            val (from, to) = resolveRange()
            if (from == null) {
                _uiState.update { it.copy(isDownloading = false, errorMessage = "Couldn't determine an export range.") }
                return@launch
            }

            journalRepository.exportEntries(from.toString(), to.toString())
                .onSuccess { body ->
                    val fileName = "bravoscribe-export-${LocalDate.now()}.zip"
                    val uri = withContext(Dispatchers.IO) { ExportFileWriter.save(context, body, fileName) }
                    _uiState.update {
                        if (uri != null) {
                            it.copy(isDownloading = false, downloadedFileName = fileName, downloadedUri = uri)
                        } else {
                            it.copy(isDownloading = false, errorMessage = "Couldn't save the export.")
                        }
                    }
                }
                .onFailure { error ->
                    val message = when ((error as? HttpException)?.code()) {
                        404 -> "No entries to export for this range."
                        400 -> "Date range is too large (max 366 days)."
                        else -> "Export failed. Please try again."
                    }
                    _uiState.update { it.copy(isDownloading = false, errorMessage = message) }
                }
        }
    }

    private suspend fun resolveRange(): Pair<LocalDate?, LocalDate> {
        val today = LocalDate.now()
        return when (_uiState.value.selectedRange) {
            ExportRange.TODAY -> today to today
            ExportRange.THIS_WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) to today
            ExportRange.THIS_MONTH -> today.withDayOfMonth(1) to today
            ExportRange.THIS_YEAR -> today.withDayOfYear(1) to today
            ExportRange.ALL_TIME -> {
                val firstEntryDate = journalRepository.getStats().getOrNull()?.firstEntryDate
                firstEntryDate?.let(LocalDate::parse) to today
            }
            ExportRange.CUSTOM -> _uiState.value.customFrom to _uiState.value.customTo
        }
    }

    fun consumeError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
