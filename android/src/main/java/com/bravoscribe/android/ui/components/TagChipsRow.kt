package com.bravoscribe.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.bravoscribe.android.domain.model.Tag

private const val MAX_TAGS_PER_ENTRY = 10

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagChipsRow(
    selectedTags: List<Tag>,
    availableTags: List<Tag>,
    onAddTag: (Tag) -> Unit,
    onCreateTag: (String) -> Unit,
    onRemoveTag: (Tag) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        selectedTags.forEach { tag ->
            InputChip(
                selected = true,
                onClick = { onRemoveTag(tag) },
                label = { Text(tag.name) },
                trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Remove ${tag.name}") },
            )
        }
        if (selectedTags.size < MAX_TAGS_PER_ENTRY) {
            AssistChip(
                onClick = { showPicker = true },
                label = { Text("+ add tag") },
                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                colors = AssistChipDefaults.assistChipColors(),
            )
        }
    }

    if (showPicker) {
        TagPickerDialog(
            availableTags = availableTags.filterNot { available -> selectedTags.any { it.id == available.id } },
            onDismiss = { showPicker = false },
            onSelect = { tag ->
                onAddTag(tag)
                showPicker = false
            },
            onCreate = { name ->
                onCreateTag(name)
                showPicker = false
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagPickerDialog(
    availableTags: List<Tag>,
    onDismiss: () -> Unit,
    onSelect: (Tag) -> Unit,
    onCreate: (String) -> Unit,
) {
    var newTagName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add tag") },
        text = {
            Column {
                if (availableTags.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                    ) {
                        availableTags.forEach { tag ->
                            AssistChip(onClick = { onSelect(tag) }, label = { Text(tag.name) })
                        }
                    }
                }
                OutlinedTextField(
                    value = newTagName,
                    onValueChange = { if (it.length <= 50) newTagName = it },
                    label = { Text("New tag") },
                    singleLine = true,
                    supportingText = if (newTagName.length >= 40) {
                        { Text("${newTagName.length} / 50") }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(newTagName.trim()) },
                enabled = newTagName.isNotBlank(),
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
