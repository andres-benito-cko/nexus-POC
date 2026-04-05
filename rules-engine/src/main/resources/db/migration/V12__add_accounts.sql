-- Additional accounts from the accounting paper not present in V6.
-- These complete the Chart of Accounts for MALPB consolidated settlement,
-- FX conversion, CASH event classes, and exception scenarios.

INSERT INTO accounts (code, name, account_type, normal_balance, description) VALUES
('emcr',                    'External MCR (SPE)',          'LIABILITY', 'CREDIT',
 'Consolidated settlement liability at CKO SPE for MALPB arrangement'),
('cash_control',            'Cash Control',                'CONTROL',   'DEBIT',
 'Internal control for client receivables and payables'),
('cash_clearing',           'Cash Clearing',               'CONTROL',   'DEBIT',
 'Clearing account for returned settlements'),
('cash_at_bank',            'Cash at Bank (Nostro)',        'ASSET',     'DEBIT',
 'Nostro bank account for returned settlement cash'),
('scheme_creditor',         'Scheme Creditor',             'LIABILITY', 'CREDIT',
 'Adjustment transactions — debit balance write-offs owed to scheme'),
('deferred_cos',            'Deferred Cost of Sales',      'LIABILITY', 'CREDIT',
 'Deferred COS for timing-adjustment scenarios'),
('other_income',            'Other Income',                'REVENUE',   'CREDIT',
 'Debit balance write-offs and non-trading adjustments'),
('scheme_clearing_holding', 'Scheme Clearing Holding (FX)','CONTROL',   'DEBIT',
 'FX conversion holding — absorbs scheme clearing in originating currency');
