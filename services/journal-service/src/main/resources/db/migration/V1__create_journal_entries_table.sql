CREATE TABLE journal.journal_entries (
    id          UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id     UUID         NOT NULL,
    entry_date  DATE         NOT NULL,
    title       VARCHAR(255),
    content     TEXT         NOT NULL,
    mood        VARCHAR(10),
    deleted     BOOLEAN      NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_entries_user_date ON journal.journal_entries (user_id, entry_date DESC);
