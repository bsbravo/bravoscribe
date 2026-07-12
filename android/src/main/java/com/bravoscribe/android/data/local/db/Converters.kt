package com.bravoscribe.android.data.local.db

import androidx.room.TypeConverter
import com.bravoscribe.android.domain.model.Mood

class Converters {

    @TypeConverter
    fun fromMood(mood: Mood?): String? = mood?.name

    @TypeConverter
    fun toMood(value: String?): Mood? = value?.let { Mood.valueOf(it) }
}
