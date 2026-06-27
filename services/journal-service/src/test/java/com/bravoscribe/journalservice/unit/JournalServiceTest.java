package com.bravoscribe.journalservice.service;

import com.bravoscribe.journalservice.dto.CreateEntryRequest;
import com.bravoscribe.journalservice.dto.JournalEntryResponse;
import com.bravoscribe.journalservice.dto.StatsResponse;
import com.bravoscribe.journalservice.dto.UpdateEntryRequest;
import com.bravoscribe.journalservice.entity.JournalEntry;
import com.bravoscribe.journalservice.entity.Mood;
import com.bravoscribe.journalservice.exception.JournalError;
import com.bravoscribe.journalservice.exception.JournalServiceException;
import com.bravoscribe.journalservice.kafka.JournalEventProducer;
import com.bravoscribe.journalservice.repository.JournalEntryRepository;
import com.bravoscribe.journalservice.repository.TagRepository;
import com.bravoscribe.journalservice.service.JournalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JournalServiceTest {

    @Mock JournalEntryRepository entryRepo;
    @Mock TagRepository tagRepo;
    @Mock JournalEventProducer eventProducer;

    JournalService service;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID ENTRY_ID = UUID.randomUUID();
    private static final LocalDate TODAY = LocalDate.now();

    @BeforeEach
    void setUp() {
        service = new JournalService(entryRepo, tagRepo, eventProducer);
    }

    // ── createEntry ──────────────────────────────────────────────────────────

    @Test
    void createEntry_success_persists_and_publishes() {
        when(entryRepo.existsByUserIdAndEntryDateAndDeletedFalse(USER_ID, TODAY)).thenReturn(false);
        when(entryRepo.save(any())).thenAnswer(inv -> {
            JournalEntry e = inv.getArgument(0);
            ReflectionTestUtils.setField(e, "id", ENTRY_ID);
            return e;
        });

        var req = new CreateEntryRequest(TODAY, "Title", "Some content", Mood.GOOD, null);
        JournalEntryResponse result = service.createEntry(USER_ID, req);

        assertThat(result.id()).isEqualTo(ENTRY_ID);
        assertThat(result.content()).isEqualTo("Some content");
        verify(entryRepo).save(any());
        verify(eventProducer).publishEntryCreated(ENTRY_ID, USER_ID, TODAY);
    }

    @Test
    void createEntry_duplicate_date_throws_409() {
        when(entryRepo.existsByUserIdAndEntryDateAndDeletedFalse(USER_ID, TODAY)).thenReturn(true);

        var req = new CreateEntryRequest(TODAY, null, "Content", null, null);
        assertThatThrownBy(() -> service.createEntry(USER_ID, req))
                .isInstanceOf(JournalServiceException.class)
                .satisfies(ex -> assertThat(((JournalServiceException) ex).getError())
                        .isInstanceOf(JournalError.DuplicateEntryDate.class));

        verify(entryRepo, never()).save(any());
    }

    // ── getEntryById ─────────────────────────────────────────────────────────

    @Test
    void getEntryById_found_returns_response() {
        var entry = makeEntry();
        when(entryRepo.findByIdAndUserIdAndDeletedFalse(ENTRY_ID, USER_ID)).thenReturn(Optional.of(entry));

        var result = service.getEntryById(USER_ID, ENTRY_ID);
        assertThat(result.id()).isEqualTo(ENTRY_ID);
    }

    @Test
    void getEntryById_not_found_throws_404() {
        when(entryRepo.findByIdAndUserIdAndDeletedFalse(ENTRY_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getEntryById(USER_ID, ENTRY_ID))
                .isInstanceOf(JournalServiceException.class)
                .satisfies(ex -> assertThat(((JournalServiceException) ex).getError())
                        .isInstanceOf(JournalError.EntryNotFound.class));
    }

    // ── updateEntry ──────────────────────────────────────────────────────────

    @Test
    void updateEntry_success_publishes_event() {
        var entry = makeEntry();
        when(entryRepo.findByIdAndUserIdAndDeletedFalse(ENTRY_ID, USER_ID)).thenReturn(Optional.of(entry));
        when(entryRepo.save(any())).thenReturn(entry);

        var req = new UpdateEntryRequest(null, "Updated content", Mood.GREAT, null);
        var result = service.updateEntry(USER_ID, ENTRY_ID, req);

        assertThat(result.content()).isEqualTo("Updated content");
        verify(eventProducer).publishEntryUpdated(ENTRY_ID, USER_ID);
    }

    @Test
    void updateEntry_not_owned_throws_404() {
        when(entryRepo.findByIdAndUserIdAndDeletedFalse(ENTRY_ID, USER_ID)).thenReturn(Optional.empty());

        var req = new UpdateEntryRequest(null, "Content", null, null);
        assertThatThrownBy(() -> service.updateEntry(USER_ID, ENTRY_ID, req))
                .isInstanceOf(JournalServiceException.class)
                .satisfies(ex -> assertThat(((JournalServiceException) ex).getError())
                        .isInstanceOf(JournalError.EntryNotFound.class));
    }

    // ── deleteEntry ──────────────────────────────────────────────────────────

    @Test
    void deleteEntry_marks_deleted_true() {
        var entry = makeEntry();
        when(entryRepo.findByIdAndUserIdAndDeletedFalse(ENTRY_ID, USER_ID)).thenReturn(Optional.of(entry));
        when(entryRepo.save(any())).thenReturn(entry);

        service.deleteEntry(USER_ID, ENTRY_ID);

        assertThat(entry.isDeleted()).isTrue();
        verify(entryRepo).save(entry);
    }

    // ── getStats ─────────────────────────────────────────────────────────────

    @Test
    void getStats_returns_correct_totals() {
        when(entryRepo.countByUserIdAndDeletedFalse(USER_ID)).thenReturn(3L);
        when(entryRepo.sumTotalWords(USER_ID)).thenReturn(150L);
        when(entryRepo.findAllEntryDatesByUserIdDesc(USER_ID)).thenReturn(List.of(
                TODAY, TODAY.minusDays(1), TODAY.minusDays(2)));
        when(entryRepo.findFirstEntryDate(USER_ID)).thenReturn(TODAY.minusDays(2));

        StatsResponse stats = service.getStats(USER_ID);

        assertThat(stats.totalEntries()).isEqualTo(3);
        assertThat(stats.totalWords()).isEqualTo(150);
        assertThat(stats.currentStreak()).isEqualTo(3);
        assertThat(stats.longestStreak()).isEqualTo(3);
        assertThat(stats.firstEntryDate()).isEqualTo(TODAY.minusDays(2));
    }

    @Test
    void getStats_no_entries_returns_zeros() {
        when(entryRepo.countByUserIdAndDeletedFalse(USER_ID)).thenReturn(0L);
        when(entryRepo.sumTotalWords(USER_ID)).thenReturn(0L);
        when(entryRepo.findAllEntryDatesByUserIdDesc(USER_ID)).thenReturn(List.of());
        when(entryRepo.findFirstEntryDate(USER_ID)).thenReturn(null);

        StatsResponse stats = service.getStats(USER_ID);

        assertThat(stats.totalEntries()).isZero();
        assertThat(stats.currentStreak()).isZero();
        assertThat(stats.longestStreak()).isZero();
        assertThat(stats.firstEntryDate()).isNull();
    }

    // ── streak calculation ────────────────────────────────────────────────────

    @Test
    void currentStreak_consecutive_days_including_today() {
        var dates = List.of(TODAY, TODAY.minusDays(1), TODAY.minusDays(2));
        assertThat(service.calculateCurrentStreak(dates)).isEqualTo(3);
    }

    @Test
    void currentStreak_last_entry_was_yesterday() {
        var dates = List.of(TODAY.minusDays(1), TODAY.minusDays(2));
        assertThat(service.calculateCurrentStreak(dates)).isEqualTo(2);
    }

    @Test
    void currentStreak_gap_two_days_ago_returns_zero() {
        var dates = List.of(TODAY.minusDays(2), TODAY.minusDays(3));
        assertThat(service.calculateCurrentStreak(dates)).isEqualTo(0);
    }

    @Test
    void longestStreak_with_gap_finds_longest_run() {
        var dates = List.of(
                TODAY,
                TODAY.minusDays(1),
                TODAY.minusDays(2),
                TODAY.minusDays(5),
                TODAY.minusDays(6));
        assertThat(service.calculateLongestStreak(dates)).isEqualTo(3);
    }

    @Test
    void softDeleteAllByUserId_delegates_to_repo() {
        service.softDeleteAllByUserId(USER_ID);
        verify(entryRepo).softDeleteAllByUserId(USER_ID);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private JournalEntry makeEntry() {
        var e = new JournalEntry();
        ReflectionTestUtils.setField(e, "id", ENTRY_ID);
        e.setUserId(USER_ID);
        e.setEntryDate(TODAY);
        e.setContent("Test content");
        e.setTags(new ArrayList<>());
        return e;
    }
}
