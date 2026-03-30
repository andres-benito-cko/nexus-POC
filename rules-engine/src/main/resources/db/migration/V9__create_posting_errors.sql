CREATE TABLE posting_errors (
    id             UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    nexus_id       VARCHAR(255)  NOT NULL,
    transaction_id VARCHAR(255)  NOT NULL,
    currency       VARCHAR(10)   NOT NULL,
    debit_total    NUMERIC(19,6) NOT NULL,
    credit_total   NUMERIC(19,6) NOT NULL,
    rule_ids       TEXT,
    created_at     TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_posting_errors_nexus_id ON posting_errors(nexus_id);
