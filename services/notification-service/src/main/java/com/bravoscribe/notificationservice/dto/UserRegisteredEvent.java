package com.bravoscribe.notificationservice.dto;

public record UserRegisteredEvent(String eventId, String userId, String email) {}
