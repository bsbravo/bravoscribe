package com.bravoscribe.android.ui.theme

import androidx.compose.ui.graphics.Color

// Warm palette (default) — parchment, ink, leather. Mirrors the React/Angular tokens.
val Ink = Color(0xFF2C1A0E)
val Ink2 = Color(0xFF5C3D1E)
val Ink3 = Color(0xFF6B4226)
val Ink4 = Color(0xFFC4A882)
val Parchment = Color(0xFFFDFAF3)
val Parchment2 = Color(0xFFF5F0E8)
val Parchment3 = Color(0xFFEDE5D4)
val Accent = Color(0xFF8B4513)
val AccentLight = Color(0xFFF2E4D4)
val Gold = Color(0xFFC17D52)
val GoldLight = Color(0xFFFAEEDA)
val Teal = Color(0xFF2D6A4F)
val TealLight = Color(0xFFD8F3DC)
val Border = Color(0xFFDDD5C0)
val Rust = Color(0xFF993C1D)

// Chronicle palette (dark) — FFT-inspired midnight blue + gold.
val ChronicleDeep = Color(0xFF080B18)
val ChroniclePanel = Color(0xFF0D1128)
val ChronicleGold = Color(0xFFC8A84B)
val ChronicleCream = Color(0xFFE8DFC8)
val ChronicleDim = Color(0xFFB8AD96)
val ChronicleTeal = Color(0xFF2A8A7A)
val ChronicleRuby = Color(0xFFA83040)
val ChronicleBorder = Color(0xFF3A4A70)

// Mood indicator colors — used as a left border on entry cards (matches React/Angular).
val MoodGreat = Teal
val MoodGood = Gold
val MoodNeutral = Border
val MoodBad = Gold
val MoodTerrible = Rust

// Streak / editing-context accent — distinct from the theme's primary/secondary,
// used for the streak bar squares, "saved" pill, and the past-entry-editing banner
// (see android/SPEC.md's "purple" callouts in the Screen designs section).
val StreakPurple = Color(0xFF6B4FA0)
val StreakPurpleLight = Color(0xFFE8E0F5)
val StreakPurpleChronicle = Color(0xFFA593E0)
val StreakPurpleChronicleContainer = Color(0xFF241C3D)
