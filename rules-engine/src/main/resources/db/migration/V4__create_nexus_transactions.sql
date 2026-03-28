CREATE TABLE nexus_transactions (
  transaction_id VARCHAR(255) PRIMARY KEY,
  action_id VARCHAR(255) NOT NULL,
  action_root_id VARCHAR(255),
  status VARCHAR(20) NOT NULL,
  entity_id VARCHAR(255),
  cko_entity_id VARCHAR(255),
  trade_family VARCHAR(50),
  trade_type VARCHAR(50),
  trade_status VARCHAR(50),
  trade_amount DECIMAL(20,6),
  trade_currency VARCHAR(3),
  raw_json TEXT NOT NULL,
  received_at TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
