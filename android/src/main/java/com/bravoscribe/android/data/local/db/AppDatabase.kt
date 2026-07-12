package com.bravoscribe.android.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.bravoscribe.android.data.local.db.dao.JournalEntryDao
import com.bravoscribe.android.data.local.db.dao.TagDao
import com.bravoscribe.android.data.local.db.entity.JournalEntryEntity
import com.bravoscribe.android.data.local.db.entity.TagEntity

@Database(
    entities = [JournalEntryEntity::class, TagEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun journalEntryDao(): JournalEntryDao
    abstract fun tagDao(): TagDao
}
