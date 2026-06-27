package com.bravoscribe.journalservice.dto;

import com.bravoscribe.journalservice.entity.Tag;

import java.util.UUID;

public record TagResponse(UUID id, String name) {
    public static TagResponse from(Tag tag) {
        return new TagResponse(tag.getId(), tag.getName());
    }
}
