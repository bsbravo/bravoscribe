package com.bravoscribe.notificationservice.dto;

public record JournalEntryCreatedEvent(String eventId, String entryId, String userId, String entryDate) {}
