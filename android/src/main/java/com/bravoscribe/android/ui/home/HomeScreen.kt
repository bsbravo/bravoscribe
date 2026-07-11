package com.bravoscribe.android.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import com.bravoscribe.android.ui.components.StreakBar
import com.bravoscribe.android.ui.components.StreakDay
import com.bravoscribe.android.ui.components.TagChipsRow
import java.time.format.DateTimeFormatter

private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d yyyy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeSnackbarMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bravoscribe") },
                actions = {
                    if (uiState.isEditorOpen) {
                        SaveStatusPill(isSaved = uiState.isSaved, modifier = Modifier.padding(end = 16.dp))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                uiState.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                !uiState.isEditorOpen -> {
                    EmptyHomeState(
                        streakDays = uiState.streakDays,
                        currentStreak = uiState.currentStreak,
                        onStartWriting = viewModel::startWriting,
                    )
                }
                else -> {
                    EditorHomeState(uiState = uiState, viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
private fun EmptyHomeState(
    streakDays: List<StreakDay>,
    currentStreak: Int,
    onStartWriting: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        Icon(
            Icons.Default.Book,
            contentDescription = null,
            modifier = Modifier.height(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text("No entry yet today", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "What's on your mind? Take a moment to write your thoughts for today.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(onClick = onStartWriting, modifier = Modifier.fillMaxWidth()) {
            Text("Start writing", style = MaterialTheme.typography.labelLarge)
        }

        Spacer(modifier = Modifier.height(24.dp))
        StreakBar(days = streakDays, currentStreak = currentStreak)

        Spacer(modifier = Modifier.height(24.dp))
        Card(
            onClick = onStartWriting,
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Today's prompt",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = todayPrompt(), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun EditorHomeState(uiState: HomeUiState, viewModel: HomeViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text(
            text = "${uiState.today.format(DATE_FORMATTER)} · today",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))

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
            label = { Text("What's on your mind today?") },
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
        StreakBar(days = uiState.streakDays, currentStreak = uiState.currentStreak)

        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = viewModel::save,
            enabled = uiState.content.isNotBlank() && !uiState.isSaving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (uiState.isSaving) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
            } else {
                Text(
                    text = if (uiState.entryId == null) "Save" else "Save changes",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}
