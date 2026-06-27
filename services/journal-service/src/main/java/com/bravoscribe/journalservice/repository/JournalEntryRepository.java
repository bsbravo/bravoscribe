package com.bravoscribe.journalservice.repository;

import com.bravoscribe.journalservice.entity.JournalEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {

    boolean existsByUserIdAndEntryDateAndDeletedFalse(UUID userId, LocalDate entryDate);

    Optional<JournalEntry> findByIdAndUserIdAndDeletedFalse(UUID id, UUID userId);

    Optional<JournalEntry> findByUserIdAndEntryDateAndDeletedFalse(UUID userId, LocalDate entryDate);

    @Query(value = """
            SELECT e FROM JournalEntry e
            WHERE e.userId = :userId
            AND e.deleted = false
            AND (:from IS NULL OR e.entryDate >= :from)
            AND (:to IS NULL OR e.entryDate <= :to)
            AND (:q IS NULL OR (LOWER(e.content) LIKE LOWER(CONCAT('%', CAST(:q AS String), '%')) OR LOWER(e.title) LIKE LOWER(CONCAT('%', CAST(:q AS String), '%'))))
            """,
           countQuery = """
            SELECT COUNT(e) FROM JournalEntry e
            WHERE e.userId = :userId
            AND e.deleted = false
            AND (:from IS NULL OR e.entryDate >= :from)
            AND (:to IS NULL OR e.entryDate <= :to)
            AND (:q IS NULL OR (LOWER(e.content) LIKE LOWER(CONCAT('%', CAST(:q AS String), '%')) OR LOWER(e.title) LIKE LOWER(CONCAT('%', CAST(:q AS String), '%'))))
            """)
    Page<JournalEntry> searchEntries(
            @Param("userId") UUID userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("q") String q,
            Pageable pageable);

    @Query("SELECT e.entryDate FROM JournalEntry e WHERE e.userId = :userId AND e.deleted = false AND e.entryDate >= :from AND e.entryDate <= :to ORDER BY e.entryDate ASC")
    List<LocalDate> findEntryDatesByRange(
            @Param("userId") UUID userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("SELECT e.entryDate FROM JournalEntry e WHERE e.userId = :userId AND e.deleted = false ORDER BY e.entryDate DESC")
    List<LocalDate> findAllEntryDatesByUserIdDesc(@Param("userId") UUID userId);

    @Query("SELECT MIN(e.entryDate) FROM JournalEntry e WHERE e.userId = :userId AND e.deleted = false")
    LocalDate findFirstEntryDate(@Param("userId") UUID userId);

    long countByUserIdAndDeletedFalse(UUID userId);

    @Query(value = """
            SELECT COALESCE(SUM(
                CASE WHEN trim(content) = '' THEN 0
                     ELSE cardinality(regexp_split_to_array(trim(content), '\\s+'))
                END
            ), 0)
            FROM journal.journal_entries
            WHERE user_id = :userId AND deleted = false
            """, nativeQuery = true)
    Long sumTotalWords(@Param("userId") UUID userId);

    @Query("SELECT e FROM JournalEntry e WHERE e.userId = :userId AND e.deleted = false AND e.entryDate >= :from AND e.entryDate <= :to ORDER BY e.entryDate ASC")
    List<JournalEntry> findForExport(
            @Param("userId") UUID userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Modifying
    @Query("UPDATE JournalEntry e SET e.deleted = true WHERE e.userId = :userId AND e.deleted = false")
    void softDeleteAllByUserId(@Param("userId") UUID userId);
}
