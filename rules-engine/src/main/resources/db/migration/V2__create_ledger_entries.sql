CREATE TABLE ledger_entries (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  rule_id UUID REFERENCES rules(id),
  rule_name VARCHAR(255),
  transaction_id VARCHAR(255) NOT NULL,
  trade_id VARCHAR(255),
  leg_id VARCHAR(255),
  debit_account VARCHAR(255) NOT NULL,
  credit_account VARCHAR(255) NOT NULL,
  amount DECIMAL(20,6) NOT NULL,
  currency VARCHAR(3) NOT NULL,
  trade_family VARCHAR(50),
  trade_type VARCHAR(50),
  trade_status VARCHAR(50),
  created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ledger_transaction_id ON ledger_entries(transaction_id);
