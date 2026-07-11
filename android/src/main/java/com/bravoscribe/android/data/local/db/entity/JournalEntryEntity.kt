package com.bravoscribe.android.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.bravoscribe.android.domain.model.Mood

/**
 * [id] is the server UUID once synced. For an entry created offline (not yet
 * synced), [id] is a locally-generated placeholder and [isSynced] is false —
 * see the offline write queue in OUTBOX below.
 */
@Entity(tableName = "journal_entries")
data class JournalEntryEntity(
    @PrimaryKey val id: String,
    val entryDate: String,
    val title: String?,
    val content: String,
    val mood: Mood?,
    val tagsJson: String, // List<TagResponse> serialized — see Converters
    val createdAt: String,
    val updatedAt: String,
    val isSynced: Boolean = true,
    val isDeleted: Boolean = false,
)
