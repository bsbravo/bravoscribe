package com.bravoscribe.userservice.dto;

public record PreferencesResponse(
    String reminderTime,
    boolean weeklySummaryEnabled
) {}
