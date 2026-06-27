package com.bravoscribe.journalservice.controller;

import com.bravoscribe.journalservice.dto.CreateEntryRequest;
import com.bravoscribe.journalservice.dto.JournalEntryResponse;
import com.bravoscribe.journalservice.dto.UpdateEntryRequest;
import com.bravoscribe.journalservice.service.ExportService;
import com.bravoscribe.journalservice.service.JournalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/journal/entries")
@Tag(name = "Journal Entries")
@SecurityRequirement(name = "bearerAuth")
@Validated
public class JournalController {

    private final JournalService journalService;
    private final ExportService exportService;

    public JournalController(JournalService journalService, ExportService exportService) {
        this.journalService = journalService;
        this.exportService = exportService;
    }

    @Operation(summary = "List entries (paginated)")
    @GetMapping
    public Page<JournalEntryResponse> listEntries(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "31") int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) @Size(max = 200) String q,
            @AuthenticationPrincipal Jwt jwt) {
        if (q != null && q.length() > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "q must not exceed 200 characters");
        }
        return journalService.listEntries(userId(jwt), page, size, from, to, q);
    }

    @Operation(summary = "Get entry dates (lightweight — for calendar dot markers)")
    @GetMapping("/dates")
    public List<String> getEntryDates(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal Jwt jwt) {
        return journalService.getEntryDates(userId(jwt), from, to);
    }

    @Operation(summary = "Get entry by specific date")
    @GetMapping("/date/{date}")
    public JournalEntryResponse getEntryByDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal Jwt jwt) {
        return journalService.getEntryByDate(userId(jwt), date);
    }

    @Operation(summary = "Export entries as zip")
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportEntries(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal Jwt jwt) {
        byte[] zip = exportService.buildExportZip(userId(jwt), from, to);
        String filename = "bravoscribe-export-" + from + "-to-" + to + ".zip";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .body(zip);
    }

    @Operation(summary = "Get entry by ID")
    @GetMapping("/{id}")
    public JournalEntryResponse getEntry(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        return journalService.getEntryById(userId(jwt), id);
    }

    @Operation(summary = "Create entry")
    @PostMapping
    public ResponseEntity<JournalEntryResponse> createEntry(
            @Valid @RequestBody CreateEntryRequest req,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(201).body(journalService.createEntry(userId(jwt), req));
    }

    @Operation(summary = "Update entry")
    @PutMapping("/{id}")
    public JournalEntryResponse updateEntry(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateEntryRequest req,
            @AuthenticationPrincipal Jwt jwt) {
        return journalService.updateEntry(userId(jwt), id, req);
    }

    @Operation(summary = "Soft-delete entry")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEntry(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        journalService.deleteEntry(userId(jwt), id);
        return ResponseEntity.noContent().build();
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
