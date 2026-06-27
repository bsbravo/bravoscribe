CREATE TABLE journal.journal_entry_tags (
    entry_id UUID NOT NULL REFERENCES journal.journal_entries(id) ON DELETE CASCADE,
    tag_id   UUID NOT NULL REFERENCES journal.tags(id) ON DELETE CASCADE,
    PRIMARY KEY (entry_id, tag_id)
);
