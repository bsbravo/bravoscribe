ALTER TABLE users.users ADD COLUMN IF NOT EXISTS reminder_time          VARCHAR(5);
ALTER TABLE users.users ADD COLUMN IF NOT EXISTS weekly_summary_enabled BOOLEAN NOT NULL DEFAULT FALSE;
