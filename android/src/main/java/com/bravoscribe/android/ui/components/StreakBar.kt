package com.bravoscribe.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.bravoscribe.android.ui.theme.BravoscribeExtras
import java.time.LocalDate

data class StreakDay(val date: LocalDate, val hasEntry: Boolean, val isToday: Boolean)

/** Builds the trailing 7-day window (6 days ago .. today) from a set of dates with entries. */
fun buildStreakDays(entryDates: Set<LocalDate>, today: LocalDate = LocalDate.now()): List<StreakDay> =
    (6 downTo 0).map { offset ->
        val date = today.minusDays(offset.toLong())
        StreakDay(date = date, hasEntry = entryDates.contains(date), isToday = date == today)
    }

@Composable
fun StreakBar(
    days: List<StreakDay>,
    currentStreak: Int,
    modifier: Modifier = Modifier,
) {
    val streakColor = BravoscribeExtras.colors.streak
    val todayHasEntry = days.lastOrNull()?.hasEntry == true

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
    ) {
        Text(
            text = streakLabel(currentStreak, todayHasEntry),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            days.forEach { day ->
                StreakSquare(day = day, streakColor = streakColor, emptyColor = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun StreakSquare(day: StreakDay, streakColor: Color, emptyColor: Color) {
    val shape = RoundedCornerShape(6.dp)
    val base = Modifier.size(28.dp).clip(shape)
    when {
        day.isToday && day.hasEntry -> Box(base.background(streakColor))
        day.isToday -> Box(base.dashedBorder(streakColor))
        day.hasEntry -> Box(base.background(streakColor.copy(alpha = 0.6f)))
        else -> Box(base.background(emptyColor))
    }
}

private fun streakLabel(currentStreak: Int, todayHasEntry: Boolean): String = when {
    currentStreak <= 0 -> "Start your streak today!"
    todayHasEntry -> "🎉 $currentStreak-day streak!"
    else -> "$currentStreak-day streak — write today to keep it!"
}

private fun Modifier.dashedBorder(color: Color, strokeWidthDp: Float = 2f, cornerRadiusDp: Float = 6f): Modifier =
    drawBehind {
        val stroke = Stroke(
            width = strokeWidthDp.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f),
        )
        drawRoundRect(
            color = color,
            size = Size(size.width - stroke.width, size.height - stroke.width),
            topLeft = Offset(stroke.width / 2, stroke.width / 2),
            cornerRadius = CornerRadius(cornerRadiusDp.dp.toPx()),
            style = stroke,
        )
    }
