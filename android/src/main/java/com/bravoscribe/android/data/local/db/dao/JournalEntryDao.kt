package com.bravoscribe.android.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bravoscribe.android.data.local.db.entity.JournalEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface JournalEntryDao {

    @Query("SELECT * FROM journal_entries WHERE isDeleted = 0 ORDER BY entryDate DESC")
    fun observeAll(): Flow<List<JournalEntryEntity>>

    @Query(
        "SELECT * FROM journal_entries WHERE isDeleted = 0 " +
            "AND (title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%') " +
            "ORDER BY entryDate DESC",
    )
    fun observeSearch(query: String): Flow<List<JournalEntryEntity>>

    @Query("SELECT * FROM journal_entries WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): JournalEntryEntity?

    @Query("SELECT * FROM journal_entries WHERE entryDate = :date AND isDeleted = 0 LIMIT 1")
    suspend fun getByDate(date: String): JournalEntryEntity?

    @Query("SELECT entryDate FROM journal_entries WHERE isDeleted = 0")
    suspend fun getAllEntryDates(): List<String>

    @Query("SELECT * FROM journal_entries WHERE isSynced = 0")
    suspend fun getUnsynced(): List<JournalEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: JournalEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<JournalEntryEntity>)

    @Query("UPDATE journal_entries SET isDeleted = 1, isSynced = 0 WHERE id = :id")
    suspend fun markDeleted(id: String)

    @Query("DELETE FROM journal_entries WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM journal_entries")
    suspend fun clear()
}
