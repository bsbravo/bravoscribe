package com.bravoscribe.userservice.dto;

import jakarta.validation.constraints.Pattern;

public record UpdatePreferencesRequest(
    @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "must be HH:mm format")
    String reminderTime,
    Boolean weeklySummaryEnabled
) {}
