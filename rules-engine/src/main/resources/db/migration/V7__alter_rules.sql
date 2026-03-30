ALTER TABLE rules
    ADD COLUMN firing_context VARCHAR(10) NOT NULL DEFAULT 'LEG',
    ADD COLUMN leg_status     VARCHAR(50),
    ADD COLUMN fee_type       VARCHAR(50),
    ADD COLUMN passthrough    BOOLEAN;
