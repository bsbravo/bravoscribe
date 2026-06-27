package com.bravoscribe.journalservice.dto;

import java.io.Serializable;
import java.time.LocalDate;

public record StatsResponse(
        long totalEntries,
        long totalWords,
        int currentStreak,
        int longestStreak,
        LocalDate firstEntryDate
) implements Serializable {}
