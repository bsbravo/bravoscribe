package com.bravoscribe.notificationservice.dto;

public record JournalEntryUpdatedEvent(String eventId, String entryId, String userId) {}
