-- V11__seed_rules.sql
-- Seed rules for the accounting engine, aligned with the Chart of Accounts (V6).
-- Covers: Acquiring Capture, Refund, Chargeback, Cash Settlement, Transfer, Payout, Topup.
--
-- Design notes:
--   - transaction_status and leg_status are NULL (wildcard) for most rules — the same
--     accounting treatment applies across PREDICTED/ACTUAL/SETTLED states.
--   - passthrough is NULL on all FEE rules — the current Nexus event schema does not
--     populate passthrough on fees. Split into passthrough/non-passthrough variants when
--     the data model carries that field.
--   - All account codes reference the 16 accounts seeded in V6.

INSERT INTO rules (name, description, product_type, transaction_type, transaction_status, leg_type, leg_status, firing_context, fee_type, passthrough, debit_account, credit_account, amount_source) VALUES

-- ============================================================
-- ACQUIRING CAPTURE — LEG rules
-- ============================================================
('Capture Scheme Settlement',
 'On capture, recognise the scheme clearing obligation and client liability',
 'ACQUIRING', 'CAPTURE', NULL, 'SCHEME_SETTLEMENT', NULL,
 'LEG', NULL, NULL,
 'scheme_clearing_settlement', 'client', 'leg_amount'),

('Capture Funding',
 'On capture, recognise client funding offset against clearing control',
 'ACQUIRING', 'CAPTURE', NULL, 'FUNDING', NULL,
 'LEG', NULL, NULL,
 'client', 'scheme_clearing_settlement', 'leg_amount'),

-- ============================================================
-- ACQUIRING CAPTURE — FEE rules
-- ============================================================
('Capture Interchange Fee',
 'Recognise interchange as passthrough cost of sales',
 'ACQUIRING', 'CAPTURE', NULL, 'SCHEME_SETTLEMENT', NULL,
 'FEE', 'INTERCHANGE', NULL,
 'passthrough_cos', 'accrued_cos', 'fee_amount'),

('Capture Scheme Fee',
 'Recognise scheme fee as passthrough cost of sales',
 'ACQUIRING', 'CAPTURE', NULL, 'SCHEME_SETTLEMENT', NULL,
 'FEE', 'SCHEME_FEE', NULL,
 'passthrough_cos', 'accrued_cos', 'fee_amount'),

('Capture Processing Fee',
 'Recognise processing fee as revenue',
 'ACQUIRING', 'CAPTURE', NULL, 'FUNDING', NULL,
 'FEE', 'PROCESSING_FEE', NULL,
 'client', 'revenue', 'fee_amount'),

('Capture Acquirer Fee',
 'Recognise acquirer fee as revenue',
 'ACQUIRING', 'CAPTURE', NULL, 'FUNDING', NULL,
 'FEE', 'ACQUIRER_FEE', NULL,
 'client', 'revenue', 'fee_amount'),

-- ============================================================
-- ACQUIRING REFUND — LEG rules
-- ============================================================
('Refund Scheme Settlement',
 'On refund, reverse the scheme clearing obligation',
 'ACQUIRING', 'REFUND', NULL, 'SCHEME_SETTLEMENT', NULL,
 'LEG', NULL, NULL,
 'client', 'scheme_clearing_settlement', 'leg_amount'),

('Refund Funding',
 'On refund, reverse the client funding',
 'ACQUIRING', 'REFUND', NULL, 'FUNDING', NULL,
 'LEG', NULL, NULL,
 'scheme_clearing_settlement', 'client', 'leg_amount'),

-- ============================================================
-- ACQUIRING REFUND — FEE rules
-- ============================================================
('Refund Processing Fee',
 'Recognise refund processing fee as revenue',
 'ACQUIRING', 'REFUND', NULL, 'FUNDING', NULL,
 'FEE', 'PROCESSING_FEE', NULL,
 'client', 'revenue', 'fee_amount'),

-- ============================================================
-- ACQUIRING CHARGEBACK — LEG rules
-- ============================================================
('Chargeback Scheme Settlement',
 'On chargeback, reverse the scheme clearing obligation',
 'ACQUIRING', 'CHARGEBACK', NULL, 'SCHEME_SETTLEMENT', NULL,
 'LEG', NULL, NULL,
 'client', 'scheme_clearing_settlement', 'leg_amount'),

('Chargeback Funding',
 'On chargeback, reverse the client funding',
 'ACQUIRING', 'CHARGEBACK', NULL, 'FUNDING', NULL,
 'LEG', NULL, NULL,
 'scheme_clearing_settlement', 'client', 'leg_amount'),

-- ============================================================
-- ACQUIRING CHARGEBACK — FEE rules
-- ============================================================
('Chargeback Fee',
 'Recognise chargeback fee as non-passthrough cost of sales',
 'ACQUIRING', 'CHARGEBACK', NULL, 'SCHEME_SETTLEMENT', NULL,
 'FEE', 'CHARGEBACK_FEE', NULL,
 'non_passthrough_cos', 'accrued_cos', 'fee_amount'),

-- ============================================================
-- CASH SETTLEMENT — LEG rules
-- ============================================================
('Cash Settlement',
 'On cash receipt from scheme, recognise cash and clear scheme debtor',
 'CASH', 'SETTLEMENT', 'SETTLED', 'FUNDING', 'ACTUAL',
 'LEG', NULL, NULL,
 'cash', 'scheme_debtor', 'leg_amount'),

-- ============================================================
-- TRANSFER — LEG rules
-- ============================================================
('Entity Transfer',
 'Intercompany transfer between CKO entities',
 'TRANSFER', 'DEBIT', NULL, 'ENTITY_TRANSFER', NULL,
 'LEG', NULL, NULL,
 'intercompany_debtor_malpb', 'intercompany_creditor_malpb', 'leg_amount'),

-- ============================================================
-- TRANSFER — FEE rules
-- ============================================================
('Transfer Fee',
 'Recognise transfer fee as non-passthrough cost of sales',
 'TRANSFER', 'DEBIT', NULL, 'ENTITY_TRANSFER', NULL,
 'FEE', 'TRANSFER_FEE', NULL,
 'non_passthrough_cos', 'accrued_cos', 'fee_amount'),

-- ============================================================
-- PAYOUT — LEG rules
-- ============================================================
('Payout Funding',
 'On payout, recognise client liability and expected settlement',
 'PAYOUT', 'CREDIT', NULL, 'FUNDING', NULL,
 'LEG', NULL, NULL,
 'client', 'expected_client_settlement', 'leg_amount'),

-- ============================================================
-- PAYOUT — FEE rules
-- ============================================================
('Payout Processing Fee',
 'Recognise payout processing fee as revenue',
 'PAYOUT', 'CREDIT', NULL, 'FUNDING', NULL,
 'FEE', 'PROCESSING_FEE', NULL,
 'client', 'revenue', 'fee_amount'),

-- ============================================================
-- TOPUP — LEG rules
-- ============================================================
('Topup Funding',
 'On topup, recognise client deposit against expected settlement',
 'TOPUP', 'CREDIT', NULL, 'FUNDING', NULL,
 'LEG', NULL, NULL,
 'expected_client_settlement', 'client', 'leg_amount');
