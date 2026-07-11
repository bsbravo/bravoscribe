package com.bravoscribe.android.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class Mood {
    GREAT, GOOD, NEUTRAL, BAD, TERRIBLE
}

fun Mood.emoji(): String = when (this) {
    Mood.GREAT -> "😄"
    Mood.GOOD -> "🙂"
    Mood.NEUTRAL -> "😐"
    Mood.BAD -> "😔"
    Mood.TERRIBLE -> "😞"
}

fun Mood.label(): String = when (this) {
    Mood.GREAT -> "Great"
    Mood.GOOD -> "Good"
    Mood.NEUTRAL -> "Neutral"
    Mood.BAD -> "Bad"
    Mood.TERRIBLE -> "Terrible"
}
