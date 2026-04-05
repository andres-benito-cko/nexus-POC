CREATE TABLE le_events (
    action_id VARCHAR(255) PRIMARY KEY,
    raw_json  TEXT         NOT NULL,
    received_at TIMESTAMP  NOT NULL DEFAULT NOW()
);
