-- V13__reseed_accounting_rules.sql
--
-- Replaces V11 stub rules with the full accounting rule set derived from the
-- Nexus Accounting Paper (docs/papers/nexus-accounting-paper.md).
--
-- Key changes vs V11:
--   1. leg_status discrimination: rules bind to PREDICTED or ACTUAL, enabling
--      stage-based firing (Stage 1 = PREDICTED, Stage 2 = ACTUAL).
--   2. Interchange/scheme fees generate TWO entry pairs each: a revenue entry
--      (Dr client / Cr revenue — fee withheld from client) and a COS entry
--      (Dr passthrough_cos / Cr accrued_cos — cost recognised).
--   3. Stage 2 (Scheme Advised) rules: scheme debtor raised, COS confirmed.
--   4. Chargeback fee split by passthrough flag.
--   5. No FUNDING LEG rule on CAPTURE — client settlement is a separate PAYOUT.
--   6. FX markup revenue rule for cross-currency captures.
--
-- Rule naming convention: "{TxnType} {Description} (S{stage})"
-- where stage corresponds to the paper's lifecycle stages.

DELETE FROM rules;

INSERT INTO rules (name, description, product_type, transaction_type, transaction_status, leg_type, leg_status, firing_context, fee_type, passthrough, debit_account, credit_account, amount_source) VALUES

-- ============================================================
-- ACQUIRING CAPTURE — Stage 1 (Processing, leg_status = PREDICTED)
-- Paper §4.1.2
-- ============================================================

-- 1a: Gross scheme receivable anticipated
('Capture Scheme Receivable (S1)',
 'Recognise SDCA obligation and client liability at pending date',
 'ACQUIRING', 'CAPTURE', NULL, 'SCHEME_SETTLEMENT', 'PREDICTED',
 'LEG', NULL, NULL,
 'scheme_clearing_settlement', 'client', 'leg_amount'),

-- 1b: Processing fee revenue
('Capture Processing Fee (S1)',
 'CKO processing fee withheld from client — recognised as revenue',
 'ACQUIRING', 'CAPTURE', NULL, 'FUNDING', 'PREDICTED',
 'FEE', 'PROCESSING_FEE', NULL,
 'client', 'revenue', 'fee_amount'),

-- Acquirer fee revenue (same pattern as processing fee)
('Capture Acquirer Fee (S1)',
 'Acquirer fee withheld from client — recognised as revenue',
 'ACQUIRING', 'CAPTURE', NULL, 'FUNDING', 'PREDICTED',
 'FEE', 'ACQUIRER_FEE', NULL,
 'client', 'revenue', 'fee_amount'),

-- 1c: Interchange withheld from client (revenue side)
('Capture IC Withheld (S1)',
 'Predicted interchange withheld from client — gross revenue',
 'ACQUIRING', 'CAPTURE', NULL, 'SCHEME_SETTLEMENT', 'PREDICTED',
 'FEE', 'INTERCHANGE', NULL,
 'client', 'revenue', 'fee_amount'),

-- 1d: Scheme fee withheld from client (revenue side)
('Capture SF Withheld (S1)',
 'Predicted scheme fee withheld from client — gross revenue',
 'ACQUIRING', 'CAPTURE', NULL, 'SCHEME_SETTLEMENT', 'PREDICTED',
 'FEE', 'SCHEME_FEE', NULL,
 'client', 'revenue', 'fee_amount'),

-- 1e: Interchange COS recognised (cost side)
('Capture IC COS Predicted (S1)',
 'Predicted interchange cost recognised — deferred on balance sheet',
 'ACQUIRING', 'CAPTURE', NULL, 'SCHEME_SETTLEMENT', 'PREDICTED',
 'FEE', 'INTERCHANGE', NULL,
 'passthrough_cos', 'accrued_cos', 'fee_amount'),

-- 1e: Scheme fee COS recognised (cost side)
('Capture SF COS Predicted (S1)',
 'Predicted scheme fee cost recognised — deferred on balance sheet',
 'ACQUIRING', 'CAPTURE', NULL, 'SCHEME_SETTLEMENT', 'PREDICTED',
 'FEE', 'SCHEME_FEE', NULL,
 'passthrough_cos', 'accrued_cos', 'fee_amount'),

-- ============================================================
-- ACQUIRING CAPTURE — Stage 2 (Scheme Advised, leg_status = ACTUAL)
-- Paper §4.1.5
-- ============================================================

-- 2a: Scheme debtor raised, SDCA cleared
('Capture Scheme Debtor Raised (S2)',
 'SD file arrived — raise scheme debtor and clear SDCA',
 'ACQUIRING', 'CAPTURE', NULL, 'SCHEME_SETTLEMENT', 'ACTUAL',
 'LEG', NULL, NULL,
 'scheme_debtor', 'scheme_clearing_settlement', 'leg_amount'),

-- 2b: Interchange COS confirmed (reverses accrual)
('Capture IC COS Confirmed (S2)',
 'Confirmed interchange clears the predicted accrual',
 'ACQUIRING', 'CAPTURE', NULL, 'SCHEME_SETTLEMENT', 'ACTUAL',
 'FEE', 'INTERCHANGE', NULL,
 'accrued_cos', 'passthrough_cos', 'fee_amount'),

-- 2b: Scheme fee COS confirmed (reverses accrual)
('Capture SF COS Confirmed (S2)',
 'Confirmed scheme fee clears the predicted accrual',
 'ACQUIRING', 'CAPTURE', NULL, 'SCHEME_SETTLEMENT', 'ACTUAL',
 'FEE', 'SCHEME_FEE', NULL,
 'accrued_cos', 'passthrough_cos', 'fee_amount'),

-- ============================================================
-- ACQUIRING REFUND — Stage 1 (Processing, leg_status = PREDICTED)
-- Paper §4.1.3 — mirrors capture entries with reversed Dr/Cr
-- ============================================================

-- R1a: Scheme payable anticipated (reversal of capture 1a)
('Refund Scheme Payable (S1)',
 'Refund reverses scheme clearing — CKO now owes scheme',
 'ACQUIRING', 'REFUND', NULL, 'SCHEME_SETTLEMENT', 'PREDICTED',
 'LEG', NULL, NULL,
 'client', 'scheme_clearing_settlement', 'leg_amount'),

-- R1b: Processing fee reversal
('Refund Processing Fee (S1)',
 'Refund processing fee reversed against revenue',
 'ACQUIRING', 'REFUND', NULL, 'FUNDING', 'PREDICTED',
 'FEE', 'PROCESSING_FEE', NULL,
 'revenue', 'client', 'fee_amount'),

-- R1c: Interchange revenue reversal
('Refund IC Reversal (S1)',
 'Interchange revenue reversed — refund reduces gross revenue',
 'ACQUIRING', 'REFUND', NULL, 'SCHEME_SETTLEMENT', 'PREDICTED',
 'FEE', 'INTERCHANGE', NULL,
 'revenue', 'client', 'fee_amount'),

-- R1d: Scheme fee revenue reversal
('Refund SF Reversal (S1)',
 'Scheme fee revenue reversed — refund reduces gross revenue',
 'ACQUIRING', 'REFUND', NULL, 'SCHEME_SETTLEMENT', 'PREDICTED',
 'FEE', 'SCHEME_FEE', NULL,
 'revenue', 'client', 'fee_amount'),

-- R1e: Interchange COS reversal
('Refund IC COS Reversal (S1)',
 'Predicted interchange COS reversed',
 'ACQUIRING', 'REFUND', NULL, 'SCHEME_SETTLEMENT', 'PREDICTED',
 'FEE', 'INTERCHANGE', NULL,
 'accrued_cos', 'passthrough_cos', 'fee_amount'),

-- R1e: Scheme fee COS reversal
('Refund SF COS Reversal (S1)',
 'Predicted scheme fee COS reversed',
 'ACQUIRING', 'REFUND', NULL, 'SCHEME_SETTLEMENT', 'PREDICTED',
 'FEE', 'SCHEME_FEE', NULL,
 'accrued_cos', 'passthrough_cos', 'fee_amount'),

-- ============================================================
-- ACQUIRING REFUND — Stage 2 (Scheme Advised, leg_status = ACTUAL)
-- ============================================================

('Refund Scheme Debtor Reversal (S2)',
 'SD file confirms refund — reverse scheme debtor',
 'ACQUIRING', 'REFUND', NULL, 'SCHEME_SETTLEMENT', 'ACTUAL',
 'LEG', NULL, NULL,
 'scheme_clearing_settlement', 'scheme_debtor', 'leg_amount'),

-- ============================================================
-- ACQUIRING CHARGEBACK — Stage 1 (Processing, leg_status = PREDICTED)
-- Paper §4.1.4 — same principal structure as refund + chargeback fee
-- ============================================================

-- CB1a: Scheme payable (same as refund principal)
('CB Scheme Payable (S1)',
 'Chargeback reverses scheme clearing — dispute amount returned to scheme',
 'ACQUIRING', 'CHARGEBACK', NULL, 'SCHEME_SETTLEMENT', 'PREDICTED',
 'LEG', NULL, NULL,
 'client', 'scheme_clearing_settlement', 'leg_amount'),

-- CB fee revenue reversals
('CB IC Reversal (S1)',
 'Interchange revenue reversed on chargeback',
 'ACQUIRING', 'CHARGEBACK', NULL, 'SCHEME_SETTLEMENT', 'PREDICTED',
 'FEE', 'INTERCHANGE', NULL,
 'revenue', 'client', 'fee_amount'),

('CB SF Reversal (S1)',
 'Scheme fee revenue reversed on chargeback',
 'ACQUIRING', 'CHARGEBACK', NULL, 'SCHEME_SETTLEMENT', 'PREDICTED',
 'FEE', 'SCHEME_FEE', NULL,
 'revenue', 'client', 'fee_amount'),

-- CB COS reversals
('CB IC COS Reversal (S1)',
 'Interchange COS reversed on chargeback',
 'ACQUIRING', 'CHARGEBACK', NULL, 'SCHEME_SETTLEMENT', 'PREDICTED',
 'FEE', 'INTERCHANGE', NULL,
 'accrued_cos', 'passthrough_cos', 'fee_amount'),

('CB SF COS Reversal (S1)',
 'Scheme fee COS reversed on chargeback',
 'ACQUIRING', 'CHARGEBACK', NULL, 'SCHEME_SETTLEMENT', 'PREDICTED',
 'FEE', 'SCHEME_FEE', NULL,
 'accrued_cos', 'passthrough_cos', 'fee_amount'),

-- CB1f: Chargeback fee — passthrough (charged to client)
('CB Fee Passthrough',
 'Chargeback fee passed through to client as revenue',
 'ACQUIRING', 'CHARGEBACK', NULL, 'SCHEME_SETTLEMENT', NULL,
 'FEE', 'CHARGEBACK_FEE', true,
 'client', 'revenue', 'fee_amount'),

-- CB1g: Chargeback fee — absorbed by CKO
('CB Fee Absorbed',
 'Chargeback fee absorbed by CKO — non-passthrough cost',
 'ACQUIRING', 'CHARGEBACK', NULL, 'SCHEME_SETTLEMENT', NULL,
 'FEE', 'CHARGEBACK_FEE', false,
 'non_passthrough_cos', 'scheme_fee_debtor', 'fee_amount'),

-- ============================================================
-- ACQUIRING CHARGEBACK — Stage 2 (Scheme Advised, leg_status = ACTUAL)
-- ============================================================

('CB Scheme Debtor Reversal (S2)',
 'SD file confirms chargeback — reverse scheme debtor',
 'ACQUIRING', 'CHARGEBACK', NULL, 'SCHEME_SETTLEMENT', 'ACTUAL',
 'LEG', NULL, NULL,
 'scheme_clearing_settlement', 'scheme_debtor', 'leg_amount'),

-- ============================================================
-- ACQUIRING — FX Cross-Currency (FX_CONVERSION leg)
-- Paper §4.1.10
-- ============================================================

('Capture FX Markup Revenue',
 'FX markup on cross-currency capture recognised as revenue',
 'ACQUIRING', 'CAPTURE', NULL, 'FX_CONVERSION', NULL,
 'FEE', 'FX_MARKUP', NULL,
 'client', 'revenue', 'fee_amount'),

-- ============================================================
-- CASH — Scheme Receivables
-- Paper §4.5
-- ============================================================

('Cash Scheme Receivable',
 'Bank cash receipt matched to scheme debtor — clears receivable',
 'CASH', 'SETTLEMENT', NULL, 'FUNDING', 'ACTUAL',
 'LEG', NULL, NULL,
 'cash', 'scheme_debtor', 'leg_amount'),

-- ============================================================
-- TRANSFER — Entity Cash Transfer
-- Paper §4.2.2
-- ============================================================

-- T1a: Cash departs acquirer entity
('Entity Transfer Debit',
 'Cash departs acquirer entity — intercompany receivable raised',
 'TRANSFER', 'DEBIT', NULL, 'ENTITY_TRANSFER', 'ACTUAL',
 'LEG', NULL, NULL,
 'intercompany_debtor_malpb', 'cash', 'leg_amount'),

-- T1b: Cash arrives settlement entity
('Entity Transfer Credit',
 'Cash arrives settlement entity — intercompany payable raised',
 'TRANSFER', 'CREDIT', NULL, 'ENTITY_TRANSFER', 'ACTUAL',
 'LEG', NULL, NULL,
 'cash', 'intercompany_creditor_malpb', 'leg_amount'),

-- Transfer fee
('Transfer Fee',
 'Transfer fee absorbed by CKO as non-passthrough cost',
 'TRANSFER', 'DEBIT', NULL, 'ENTITY_TRANSFER', NULL,
 'FEE', 'TRANSFER_FEE', NULL,
 'non_passthrough_cos', 'accrued_cos', 'fee_amount'),

-- ============================================================
-- PAYOUT — Client Fund Distribution
-- Paper §4.4
-- ============================================================

-- Initiation: funds earmarked for client
('Payout Initiation',
 'Payout initiated — client liability transferred to expected settlement',
 'PAYOUT', 'CREDIT', NULL, 'FUNDING', 'PREDICTED',
 'LEG', NULL, NULL,
 'client', 'expected_client_settlement', 'leg_amount'),

-- Confirmation: cash out to client
('Payout Confirmation',
 'Payout confirmed — cash disbursed to client',
 'PAYOUT', 'CREDIT', NULL, 'FUNDING', 'ACTUAL',
 'LEG', NULL, NULL,
 'expected_client_settlement', 'cash', 'leg_amount'),

-- Payout processing fee
('Payout Processing Fee',
 'Payout processing fee recognised as revenue',
 'PAYOUT', 'CREDIT', NULL, 'FUNDING', NULL,
 'FEE', 'PROCESSING_FEE', NULL,
 'client', 'revenue', 'fee_amount'),

-- ============================================================
-- TOPUP — Client Funding
-- Paper §4.3
-- ============================================================

-- Receipt: cash in from client
('Topup Receipt',
 'Client deposit received — cash increased, client liability recognised',
 'TOPUP', 'CREDIT', NULL, 'FUNDING', 'ACTUAL',
 'LEG', NULL, NULL,
 'cash', 'client', 'leg_amount'),

-- Transfer fee on topup
('Topup Transfer Fee',
 'Transfer fee on client topup recognised as revenue',
 'TOPUP', 'CREDIT', NULL, 'FUNDING', NULL,
 'FEE', 'TRANSFER_FEE', NULL,
 'client', 'revenue', 'fee_amount');
