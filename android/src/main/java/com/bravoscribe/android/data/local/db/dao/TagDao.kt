package com.bravoscribe.android.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bravoscribe.android.data.local.db.entity.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {

    @Query("SELECT * FROM tags ORDER BY name ASC")
    fun observeAll(): Flow<List<TagEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tags: List<TagEntity>)

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM tags")
    suspend fun clear()
}
