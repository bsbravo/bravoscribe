CREATE TABLE journal.tags (
    id      UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id UUID         NOT NULL,
    name    VARCHAR(50)  NOT NULL,
    CONSTRAINT uq_user_tag_name UNIQUE (user_id, name)
);

CREATE INDEX idx_tags_user_id ON journal.tags (user_id);
