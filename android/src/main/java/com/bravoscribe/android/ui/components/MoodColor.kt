package com.bravoscribe.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.bravoscribe.android.domain.model.Mood
import com.bravoscribe.android.ui.theme.MoodBad
import com.bravoscribe.android.ui.theme.MoodGood
import com.bravoscribe.android.ui.theme.MoodGreat
import com.bravoscribe.android.ui.theme.MoodNeutral
import com.bravoscribe.android.ui.theme.MoodTerrible

/** Left-border accent color per mood — matches the React/Angular mood color system. */
fun Mood.borderColor(): Color = when (this) {
    Mood.GREAT -> MoodGreat
    Mood.GOOD -> MoodGood
    Mood.NEUTRAL -> MoodNeutral
    Mood.BAD -> MoodBad
    Mood.TERRIBLE -> MoodTerrible
}

/**
 * A 4dp color stripe used as the left border on entry cards. The parent Row must use
 * `Modifier.height(IntrinsicSize.Min)` so this bar's `fillMaxHeight()` matches the card.
 */
@Composable
fun MoodColorBar(mood: Mood?, modifier: Modifier = Modifier) {
    val color = mood?.borderColor() ?: MaterialTheme.colorScheme.outlineVariant
    Box(modifier = modifier.fillMaxHeight().width(4.dp).background(color))
}
