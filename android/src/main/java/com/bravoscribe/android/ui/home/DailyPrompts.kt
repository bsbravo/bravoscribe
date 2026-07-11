package com.bravoscribe.android.ui.home

import java.time.LocalDate

val DAILY_PROMPTS = listOf(
    "What made you smile today?",
    "What's one thing you're grateful for?",
    "Describe a challenge you faced and how you handled it.",
    "What's something you learned recently?",
    "What do you wish you had more time for?",
    "Describe your ideal day.",
    "What's something you're looking forward to?",
    "What would you tell your past self?",
    "What's something you accomplished today, no matter how small?",
    "What's been on your mind lately?",
    "Who inspired you today and why?",
    "What's one habit you'd like to build?",
    "Describe a moment of peace you experienced recently.",
    "What's something you want to improve about yourself?",
    "What made today different from yesterday?",
)

fun todayPrompt(today: LocalDate = LocalDate.now()): String =
    DAILY_PROMPTS[today.dayOfYear % DAILY_PROMPTS.size]
