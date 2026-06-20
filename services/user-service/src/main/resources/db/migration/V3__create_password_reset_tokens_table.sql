CREATE TABLE IF NOT EXISTS users.password_reset_tokens (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    token       VARCHAR(64) NOT NULL UNIQUE,
    user_id     UUID        NOT NULL REFERENCES users.users (id) ON DELETE CASCADE,
    expires_at  TIMESTAMPTZ NOT NULL,
    used        BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_password_reset_tokens_user_id ON users.password_reset_tokens (user_id);
