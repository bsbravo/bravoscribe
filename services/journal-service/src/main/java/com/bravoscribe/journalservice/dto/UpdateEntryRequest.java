package com.bravoscribe.journalservice.dto;

import com.bravoscribe.journalservice.entity.Mood;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record UpdateEntryRequest(
        @Size(max = 255) String title,
        @NotBlank @Size(max = 10000) String content,
        Mood mood,
        @Size(max = 10) List<UUID> tagIds
) {}
