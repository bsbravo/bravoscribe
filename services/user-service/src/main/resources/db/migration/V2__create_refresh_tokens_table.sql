CREATE TABLE IF NOT EXISTS users.refresh_tokens (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    token       VARCHAR(64) NOT NULL UNIQUE,
    user_id     UUID        NOT NULL REFERENCES users.users (id) ON DELETE CASCADE,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked     BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_user_id ON users.refresh_tokens (user_id);
