package com.bravoscribe.android.ui.util

/** When an entry has no title, its first content line stands in as the display title. */
fun displayTitle(title: String?, content: String): String =
    title?.takeIf { it.isNotBlank() } ?: content.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()

fun wordCount(content: String): Int = content.trim().split(Regex("\\s+")).count { it.isNotBlank() }
