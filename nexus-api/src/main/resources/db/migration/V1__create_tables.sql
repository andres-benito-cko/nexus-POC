CREATE TABLE engine_configs (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    version    VARCHAR(50),
    content    TEXT NOT NULL,
    is_active  BOOLEAN NOT NULL DEFAULT FALSE,
    valid_from TIMESTAMPTZ,
    valid_to   TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(255)
);

CREATE TABLE dlq_events (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    action_id   VARCHAR(255),
    payload     TEXT NOT NULL,
    errors      TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    replayed_at TIMESTAMPTZ
);
