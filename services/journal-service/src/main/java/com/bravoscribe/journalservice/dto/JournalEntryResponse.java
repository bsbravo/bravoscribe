package com.bravoscribe.journalservice.dto;

import com.bravoscribe.journalservice.entity.JournalEntry;
import com.bravoscribe.journalservice.entity.Mood;
import com.bravoscribe.journalservice.entity.Tag;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record JournalEntryResponse(
        UUID id,
        LocalDate entryDate,
        String title,
        String content,
        Mood mood,
        List<TagResponse> tags,
        Instant createdAt,
        Instant updatedAt
) {
    public static JournalEntryResponse from(JournalEntry e) {
        return new JournalEntryResponse(
                e.getId(),
                e.getEntryDate(),
                e.getTitle(),
                e.getContent(),
                e.getMood(),
                e.getTags().stream().map(TagResponse::from).toList(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}
