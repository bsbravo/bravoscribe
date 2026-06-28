package com.bravoscribe.notificationservice.dto;

public record PasswordResetRequestedEvent(
    String eventId,
    String userId,
    String email,
    String resetToken,
    String expiresAt
) {}
