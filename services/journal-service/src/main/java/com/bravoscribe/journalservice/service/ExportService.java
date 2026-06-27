package com.bravoscribe.journalservice.service;

import com.bravoscribe.journalservice.entity.JournalEntry;
import com.bravoscribe.journalservice.entity.Mood;
import com.bravoscribe.journalservice.exception.JournalError;
import com.bravoscribe.journalservice.exception.JournalServiceException;
import com.bravoscribe.journalservice.repository.JournalEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class ExportService {

    private static final Logger log = LoggerFactory.getLogger(ExportService.class);
    private static final int MAX_EXPORT_DAYS = 366;
    private static final DateTimeFormatter ENTRY_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd · EEEE");

    private final JournalEntryRepository entryRepo;

    public ExportService(JournalEntryRepository entryRepo) {
        this.entryRepo = entryRepo;
    }

    @Transactional(readOnly = true)
    public byte[] buildExportZip(UUID userId, LocalDate from, LocalDate to) {
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days > MAX_EXPORT_DAYS) {
            log.warn("Export range exceeded — userId: {} days: {}", userId, days);
            throw new JournalServiceException(new JournalError.ExportRangeExceeded((int) days));
        }

        List<JournalEntry> entries = entryRepo.findForExport(userId, from, to);
        if (entries.isEmpty()) {
            throw new JournalServiceException(new JournalError.ExportNoEntries());
        }

        String filename = "bravoscribe-export-" + from + "-to-" + to;
        String mdContent = buildMarkdown(entries, from, to);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(baos)) {
            zip.putNextEntry(new ZipEntry(filename + ".md"));
            zip.write(mdContent.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        } catch (IOException e) {
            log.error("Export generation failed — userId: {} — {}", userId, e.getMessage(), e);
            throw new RuntimeException("Export generation failed", e);
        }

        log.info("Export generated — userId: {} from: {} to: {} entries: {}", userId, from, to, entries.size());
        return baos.toByteArray();
    }

    private String buildMarkdown(List<JournalEntry> entries, LocalDate from, LocalDate to) {
        var sb = new StringBuilder();
        sb.append("# Bravoscribe Export\n");
        sb.append("**Period:** ").append(from).append(" to ").append(to).append("\n");
        sb.append("**Total entries:** ").append(entries.size()).append("\n");
        sb.append("**Exported:** ").append(Instant.now().truncatedTo(ChronoUnit.SECONDS)).append("\n");
        sb.append("\n");
        sb.append("> This is a personal journal export. Each entry is written by the\n");
        sb.append("> same person and represents their thoughts, feelings and experiences\n");
        sb.append("> on that day.\n");
        sb.append("\n---\n");

        for (JournalEntry entry : entries) {
            sb.append("\n## ").append(entry.getEntryDate().format(ENTRY_DATE_FORMAT)).append("\n\n");

            if (entry.getMood() != null) {
                sb.append("**Mood:** ").append(moodLabel(entry.getMood())).append("\n");
            }
            if (!entry.getTags().isEmpty()) {
                String tagNames = entry.getTags().stream()
                        .map(t -> t.getName())
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("");
                sb.append("**Tags:** ").append(tagNames).append("\n");
            }
            sb.append("**Words:** ").append(countWords(entry.getContent())).append("\n");

            if (entry.getTitle() != null && !entry.getTitle().isBlank()) {
                sb.append("\n### ").append(entry.getTitle()).append("\n");
            }
            sb.append("\n").append(entry.getContent()).append("\n");
            sb.append("\n---\n");
        }
        return sb.toString();
    }

    private String moodLabel(Mood mood) {
        return switch (mood) {
            case GREAT -> "Great 😄";
            case GOOD -> "Good 🙂";
            case NEUTRAL -> "Neutral 😐";
            case BAD -> "Bad 😔";
            case TERRIBLE -> "Terrible 😞";
        };
    }

    private int countWords(String content) {
        if (content == null || content.isBlank()) return 0;
        return content.trim().split("\\s+").length;
    }
}
