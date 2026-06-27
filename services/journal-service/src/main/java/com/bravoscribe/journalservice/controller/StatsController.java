package com.bravoscribe.journalservice.controller;

import com.bravoscribe.journalservice.dto.StatsResponse;
import com.bravoscribe.journalservice.service.JournalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/journal/stats")
@Tag(name = "Stats")
@SecurityRequirement(name = "bearerAuth")
public class StatsController {

    private final JournalService journalService;

    public StatsController(JournalService journalService) {
        this.journalService = journalService;
    }

    @Operation(summary = "Get journal stats for the current user")
    @GetMapping
    public StatsResponse getStats(@AuthenticationPrincipal Jwt jwt) {
        return journalService.getStats(UUID.fromString(jwt.getSubject()));
    }
}
