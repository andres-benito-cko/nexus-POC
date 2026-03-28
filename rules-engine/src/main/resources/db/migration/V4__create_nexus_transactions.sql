CREATE TABLE nexus_blocks (
  nexus_id VARCHAR(255) PRIMARY KEY,
  action_id VARCHAR(255) NOT NULL,
  action_root_id VARCHAR(255),
  status VARCHAR(20) NOT NULL,
  entity_id VARCHAR(255),
  cko_entity_id VARCHAR(255),
  product_type VARCHAR(50),
  transaction_type VARCHAR(50),
  transaction_status VARCHAR(50),
  transaction_amount DECIMAL(20,6),
  transaction_currency VARCHAR(3),
  raw_json TEXT NOT NULL,
  received_at TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
