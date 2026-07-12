package com.bravoscribe.android.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bravoscribe.android.ui.components.MoodPicker
import com.bravoscribe.android.ui.components.SaveStatusPill
import com.bravoscribe.android.ui.components.TagChipsRow
import com.bravoscribe.android.ui.theme.BravoscribeExtras
import java.time.format.DateTimeFormatter

private val TITLE_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM d, yyyy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    onBack: () -> Unit,
    onNavigateToDate: (String) -> Unit,
    viewModel: EditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.snackbarMessage) {
        val message = uiState.snackbarMessage ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = message,
            actionLabel = if (uiState.snackbarShowUndo) "Undo" else null,
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.undoLastSave()
        viewModel.consumeSnackbarMessage()
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(uiState.date.format(TITLE_DATE_FORMATTER)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { onNavigateToDate(uiState.date.minusDays(1).toString()) }) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Previous day")
                        }
                        IconButton(
                            onClick = { onNavigateToDate(uiState.date.plusDays(1).toString()) },
                            enabled = !uiState.isToday,
                        ) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Next day")
                        }
                        SaveStatusPill(isSaved = uiState.isSaved, modifier = Modifier.padding(end = 12.dp))
                    },
                )
                if (!uiState.isToday) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BravoscribeExtras.colors.streakContainer)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Text(
                            text = "Editing a past entry · ${uiState.daysAgo} days ago",
                            style = MaterialTheme.typography.labelMedium,
                            color = BravoscribeExtras.colors.streak,
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                ) {
                    MoodPicker(selected = uiState.mood, onSelect = viewModel::onMoodChange)
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = uiState.title,
                        onValueChange = viewModel::onTitleChange,
                        label = { Text("Title (optional)") },
                        singleLine = true,
                        supportingText = if (uiState.title.length >= 200) {
                            { Text("${uiState.title.length} / 255") }
                        } else {
                            null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = uiState.content,
                        onValueChange = viewModel::onContentChange,
                        label = { Text("Write about this day...") },
                        minLines = 8,
                        supportingText = {
                            Text(
                                text = "${uiState.content.length} / 10,000",
                                color = if (uiState.content.length >= 9_500) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    TagChipsRow(
                        selectedTags = uiState.selectedTags,
                        availableTags = uiState.availableTags,
                        onAddTag = viewModel::onAddTag,
                        onCreateTag = viewModel::onCreateTag,
                        onRemoveTag = viewModel::onRemoveTag,
                    )

                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = viewModel::save,
                        enabled = uiState.content.isNotBlank() && !uiState.isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Save changes", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}
