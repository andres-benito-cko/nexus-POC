CREATE TABLE ledger_entries (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  rule_id UUID REFERENCES rules(id),
  rule_name VARCHAR(255),
  nexus_id VARCHAR(255) NOT NULL,
  transaction_id VARCHAR(255),
  leg_id VARCHAR(255),
  debit_account VARCHAR(255) NOT NULL,
  credit_account VARCHAR(255) NOT NULL,
  amount DECIMAL(20,6) NOT NULL,
  currency VARCHAR(3) NOT NULL,
  product_type VARCHAR(50),
  transaction_type VARCHAR(50),
  transaction_status VARCHAR(50),
  created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ledger_nexus_id ON ledger_entries(nexus_id);
