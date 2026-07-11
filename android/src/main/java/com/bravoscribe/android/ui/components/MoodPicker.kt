package com.bravoscribe.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bravoscribe.android.domain.model.Mood
import com.bravoscribe.android.domain.model.emoji
import com.bravoscribe.android.domain.model.label

@Composable
fun MoodPicker(
    selected: Mood?,
    onSelect: (Mood?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Mood.entries.forEach { mood ->
            val isSelected = mood == selected
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    )
                    .border(
                        width = if (isSelected) 2.dp else 0.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape,
                    )
                    .clickable { onSelect(if (isSelected) null else mood) }
                    .semantics { contentDescription = mood.label() },
                contentAlignment = Alignment.Center,
            ) {
                Text(text = mood.emoji(), style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}
