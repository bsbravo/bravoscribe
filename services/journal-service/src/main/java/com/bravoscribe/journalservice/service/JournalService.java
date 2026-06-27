package com.bravoscribe.journalservice.service;

import com.bravoscribe.journalservice.dto.CreateEntryRequest;
import com.bravoscribe.journalservice.dto.JournalEntryResponse;
import com.bravoscribe.journalservice.dto.StatsResponse;
import com.bravoscribe.journalservice.dto.UpdateEntryRequest;
import com.bravoscribe.journalservice.entity.JournalEntry;
import com.bravoscribe.journalservice.exception.JournalError;
import com.bravoscribe.journalservice.exception.JournalServiceException;
import com.bravoscribe.journalservice.kafka.JournalEventProducer;
import com.bravoscribe.journalservice.repository.JournalEntryRepository;
import com.bravoscribe.journalservice.repository.TagRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class JournalService {

    private static final Logger log = LoggerFactory.getLogger(JournalService.class);

    private final JournalEntryRepository entryRepo;
    private final TagRepository tagRepo;
    private final JournalEventProducer eventProducer;

    public JournalService(JournalEntryRepository entryRepo,
                          TagRepository tagRepo,
                          JournalEventProducer eventProducer) {
        this.entryRepo = entryRepo;
        this.tagRepo = tagRepo;
        this.eventProducer = eventProducer;
    }

    @CacheEvict(value = "journal-stats", key = "#userId")
    public JournalEntryResponse createEntry(UUID userId, CreateEntryRequest req) {
        if (entryRepo.existsByUserIdAndEntryDateAndDeletedFalse(userId, req.entryDate())) {
            log.warn("Duplicate entry attempt — userId: {} entryDate: {}", userId, req.entryDate());
            throw new JournalServiceException(new JournalError.DuplicateEntryDate(req.entryDate().toString()));
        }

        var entry = new JournalEntry();
        entry.setUserId(userId);
        entry.setEntryDate(req.entryDate());
        entry.setTitle(req.title());
        entry.setContent(req.content());
        entry.setMood(req.mood());

        if (req.tagIds() != null && !req.tagIds().isEmpty()) {
            var tags = resolveTagsForUser(userId, req.tagIds());
            entry.setTags(tags);
        }

        var saved = entryRepo.save(entry);
        log.info("Entry created — userId: {} entryDate: {}", userId, req.entryDate());
        eventProducer.publishEntryCreated(saved.getId(), userId, req.entryDate());
        return JournalEntryResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public Page<JournalEntryResponse> listEntries(UUID userId, int page, int size,
                                                   LocalDate from, LocalDate to, String q) {
        int effectiveSize = Math.min(size, 100);
        var pageable = PageRequest.of(page, effectiveSize, Sort.by("entryDate").descending());
        return entryRepo.searchEntries(userId, from, to, q, pageable)
                .map(JournalEntryResponse::from);
    }

    @Transactional(readOnly = true)
    public JournalEntryResponse getEntryById(UUID userId, UUID entryId) {
        return entryRepo.findByIdAndUserIdAndDeletedFalse(entryId, userId)
                .map(JournalEntryResponse::from)
                .orElseThrow(() -> {
                    log.warn("Unauthorized access attempt — requestUserId: {} entryId: {}", userId, entryId);
                    return new JournalServiceException(new JournalError.EntryNotFound());
                });
    }

    @Transactional(readOnly = true)
    public JournalEntryResponse getEntryByDate(UUID userId, LocalDate date) {
        return entryRepo.findByUserIdAndEntryDateAndDeletedFalse(userId, date)
                .map(JournalEntryResponse::from)
                .orElseThrow(() -> new JournalServiceException(new JournalError.EntryNotFound()));
    }

    @Transactional(readOnly = true)
    public List<String> getEntryDates(UUID userId, LocalDate from, LocalDate to) {
        return entryRepo.findEntryDatesByRange(userId, from, to)
                .stream()
                .map(LocalDate::toString)
                .toList();
    }

    @CacheEvict(value = "journal-stats", key = "#userId")
    public JournalEntryResponse updateEntry(UUID userId, UUID entryId, UpdateEntryRequest req) {
        var entry = entryRepo.findByIdAndUserIdAndDeletedFalse(entryId, userId)
                .orElseThrow(() -> {
                    log.warn("Unauthorized access attempt — requestUserId: {} entryId: {}", userId, entryId);
                    return new JournalServiceException(new JournalError.EntryNotFound());
                });

        entry.setTitle(req.title());
        entry.setContent(req.content());
        entry.setMood(req.mood());

        if (req.tagIds() != null) {
            entry.setTags(resolveTagsForUser(userId, req.tagIds()));
        } else {
            entry.setTags(new ArrayList<>());
        }

        var saved = entryRepo.save(entry);
        log.info("Entry updated — userId: {} entryId: {}", userId, entryId);
        eventProducer.publishEntryUpdated(entryId, userId);
        return JournalEntryResponse.from(saved);
    }

    @CacheEvict(value = "journal-stats", key = "#userId")
    public void deleteEntry(UUID userId, UUID entryId) {
        var entry = entryRepo.findByIdAndUserIdAndDeletedFalse(entryId, userId)
                .orElseThrow(() -> new JournalServiceException(new JournalError.EntryNotFound()));
        entry.setDeleted(true);
        entryRepo.save(entry);
        log.info("Entry deleted — userId: {} entryId: {}", userId, entryId);
    }

    @Cacheable(value = "journal-stats", key = "#userId")
    @Transactional(readOnly = true)
    public StatsResponse getStats(UUID userId) {
        long totalEntries = entryRepo.countByUserIdAndDeletedFalse(userId);
        long totalWords = entryRepo.sumTotalWords(userId);
        List<LocalDate> dates = entryRepo.findAllEntryDatesByUserIdDesc(userId);
        LocalDate firstEntryDate = entryRepo.findFirstEntryDate(userId);

        int currentStreak = calculateCurrentStreak(dates);
        int longestStreak = calculateLongestStreak(dates);

        return new StatsResponse(totalEntries, totalWords, currentStreak, longestStreak, firstEntryDate);
    }

    @CacheEvict(value = "journal-stats", key = "#userId")
    public void softDeleteAllByUserId(UUID userId) {
        entryRepo.softDeleteAllByUserId(userId);
        log.info("All entries soft-deleted for deactivated userId: {}", userId);
    }

    private List<com.bravoscribe.journalservice.entity.Tag> resolveTagsForUser(UUID userId, List<UUID> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) return new ArrayList<>();
        return tagIds.stream()
                .map(tagId -> tagRepo.findByIdAndUserId(tagId, userId)
                        .orElseThrow(() -> new JournalServiceException(new JournalError.TagNotFound())))
                .toList();
    }

    int calculateCurrentStreak(List<LocalDate> datesDesc) {
        if (datesDesc.isEmpty()) return 0;
        LocalDate today = LocalDate.now();
        LocalDate mostRecent = datesDesc.get(0);

        // streak is live if last entry is today or yesterday
        if (mostRecent.isBefore(today.minusDays(1))) return 0;

        int streak = 1;
        for (int i = 1; i < datesDesc.size(); i++) {
            if (datesDesc.get(i).equals(datesDesc.get(i - 1).minusDays(1))) {
                streak++;
            } else {
                break;
            }
        }
        return streak;
    }

    int calculateLongestStreak(List<LocalDate> datesDesc) {
        if (datesDesc.isEmpty()) return 0;
        List<LocalDate> asc = new ArrayList<>(datesDesc);
        Collections.reverse(asc);

        int longest = 1;
        int current = 1;
        for (int i = 1; i < asc.size(); i++) {
            if (asc.get(i).equals(asc.get(i - 1).plusDays(1))) {
                current++;
                if (current > longest) longest = current;
            } else {
                current = 1;
            }
        }
        return longest;
    }
}
