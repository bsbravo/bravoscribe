package com.bravoscribe.journalservice.unit;

import com.bravoscribe.journalservice.entity.JournalEntry;
import com.bravoscribe.journalservice.entity.Mood;
import com.bravoscribe.journalservice.exception.JournalError;
import com.bravoscribe.journalservice.exception.JournalServiceException;
import com.bravoscribe.journalservice.repository.JournalEntryRepository;
import com.bravoscribe.journalservice.service.ExportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExportServiceTest {

    @Mock JournalEntryRepository entryRepo;

    ExportService service;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final LocalDate FROM = LocalDate.of(2026, 6, 1);
    private static final LocalDate TO = LocalDate.of(2026, 6, 10);

    @BeforeEach
    void setUp() {
        service = new ExportService(entryRepo);
    }

    @Test
    void buildExportZip_returns_valid_zip_with_md_entry() throws Exception {
        when(entryRepo.findForExport(eq(USER_ID), eq(FROM), eq(TO)))
                .thenReturn(List.of(makeEntry("A good day", "Had a productive morning.", Mood.GOOD)));

        byte[] zip = service.buildExportZip(USER_ID, FROM, TO);

        assertThat(zip).isNotEmpty();
        try (var zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            var entry = zis.getNextEntry();
            assertThat(entry).isNotNull();
            assertThat(entry.getName()).endsWith(".md");
            String content = new String(zis.readAllBytes());
            assertThat(content).contains("# Bravoscribe Export");
            assertThat(content).contains("A good day");
            assertThat(content).contains("Had a productive morning.");
            assertThat(content).doesNotContain("@");  // no email
        }
    }

    @Test
    void buildExportZip_no_entries_throws_404() {
        when(entryRepo.findForExport(any(), any(), any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.buildExportZip(USER_ID, FROM, TO))
                .isInstanceOf(JournalServiceException.class)
                .satisfies(ex -> assertThat(((JournalServiceException) ex).getError())
                        .isInstanceOf(JournalError.ExportNoEntries.class));
    }

    @Test
    void buildExportZip_range_exceeds_366_days_throws_400() {
        LocalDate bigFrom = LocalDate.of(2025, 1, 1);
        LocalDate bigTo = LocalDate.of(2026, 6, 1); // > 366 days

        assertThatThrownBy(() -> service.buildExportZip(USER_ID, bigFrom, bigTo))
                .isInstanceOf(JournalServiceException.class)
                .satisfies(ex -> assertThat(((JournalServiceException) ex).getError())
                        .isInstanceOf(JournalError.ExportRangeExceeded.class));
    }

    @Test
    void buildExportZip_mood_and_title_optional_lines_omitted_when_null() throws Exception {
        when(entryRepo.findForExport(eq(USER_ID), eq(FROM), eq(TO)))
                .thenReturn(List.of(makeEntry(null, "Just a thought.", null)));

        byte[] zip = service.buildExportZip(USER_ID, FROM, TO);
        try (var zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            zis.getNextEntry();
            String content = new String(zis.readAllBytes());
            assertThat(content).doesNotContain("**Mood:**");
            assertThat(content).doesNotContain("###");
        }
    }

    private JournalEntry makeEntry(String title, String content, Mood mood) {
        var e = new JournalEntry();
        ReflectionTestUtils.setField(e, "id", UUID.randomUUID());
        e.setUserId(USER_ID);
        e.setEntryDate(FROM);
        e.setTitle(title);
        e.setContent(content);
        e.setMood(mood);
        e.setTags(new ArrayList<>());
        return e;
    }
}
