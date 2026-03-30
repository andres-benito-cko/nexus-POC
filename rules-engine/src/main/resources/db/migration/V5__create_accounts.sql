CREATE TABLE accounts (
    code           VARCHAR(100) PRIMARY KEY,
    name           VARCHAR(255) NOT NULL,
    account_type   VARCHAR(20)  NOT NULL,
    normal_balance VARCHAR(10)  NOT NULL,
    description    TEXT,
    enabled        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW()
);
