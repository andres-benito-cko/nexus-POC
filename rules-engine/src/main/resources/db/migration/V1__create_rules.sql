CREATE TABLE rules (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name VARCHAR(255) NOT NULL,
  description TEXT,
  product_type VARCHAR(50),
  transaction_type VARCHAR(50),
  transaction_status VARCHAR(50),
  leg_type VARCHAR(50),
  party_type VARCHAR(50),
  debit_account VARCHAR(255) NOT NULL,
  credit_account VARCHAR(255) NOT NULL,
  amount_source VARCHAR(50) NOT NULL DEFAULT 'leg_amount',
  enabled BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
