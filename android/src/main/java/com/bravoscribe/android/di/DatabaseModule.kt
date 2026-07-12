package com.bravoscribe.android.di

import android.content.Context
import androidx.room.Room
import com.bravoscribe.android.data.local.datastore.ThemeDataStore
import com.bravoscribe.android.data.local.db.AppDatabase
import com.bravoscribe.android.data.local.db.dao.JournalEntryDao
import com.bravoscribe.android.data.local.db.dao.TagDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "bravoscribe.db").build()

    @Provides
    fun provideJournalEntryDao(db: AppDatabase): JournalEntryDao = db.journalEntryDao()

    @Provides
    fun provideTagDao(db: AppDatabase): TagDao = db.tagDao()

    @Provides
    @Singleton
    fun provideThemeDataStore(@ApplicationContext context: Context): ThemeDataStore =
        ThemeDataStore(context)
}
