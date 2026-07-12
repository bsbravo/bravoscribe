package com.bravoscribe.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.bravoscribe.android.ui.theme.BravoscribeExtras

@Composable
fun SaveStatusPill(isSaved: Boolean, modifier: Modifier = Modifier) {
    val backgroundColor = if (isSaved) BravoscribeExtras.colors.streakContainer else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isSaved) BravoscribeExtras.colors.streak else MaterialTheme.colorScheme.onSurfaceVariant

    Text(
        text = if (isSaved) "saved" else "not saved yet",
        style = MaterialTheme.typography.labelSmall,
        color = textColor,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(backgroundColor)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
