-- Partial index for active (non-deleted) entries per user — used by streak and date queries
CREATE INDEX idx_entries_user_not_deleted
    ON journal.journal_entries (user_id, entry_date)
    WHERE deleted = false;
