-- One entry per user per calendar day (soft-deleted entries excluded from uniqueness)
-- Note: PostgreSQL partial unique index enforces this without blocking historical data
CREATE UNIQUE INDEX uq_user_entry_date_active
    ON journal.journal_entries (user_id, entry_date)
    WHERE deleted = false;

-- Hard cap on content length at DB level
ALTER TABLE journal.journal_entries
    ADD CONSTRAINT chk_content_length CHECK (char_length(content) <= 10000);
