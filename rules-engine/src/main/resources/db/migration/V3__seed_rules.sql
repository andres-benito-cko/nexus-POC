INSERT INTO rules (name, description, trade_family, trade_type, trade_status, leg_type, debit_account, credit_account, amount_source) VALUES
('Capture Settlement - Revenue Recognition', 'Recognise gross revenue on capture settlement', 'ACQUIRING', 'CAPTURE', 'SETTLED', 'SCHEME_SETTLEMENT', 'RECEIVABLE_FROM_SCHEME', 'GROSS_REVENUE', 'leg_amount'),
('Capture Settlement - Client Payable', 'Record client payable on capture settlement', 'ACQUIRING', 'CAPTURE', 'SETTLED', 'FUNDING', 'GROSS_REVENUE', 'PAYABLE_TO_CLIENT', 'leg_amount'),
('Interchange Fee - Cost Recognition', 'Recognise interchange as cost of sales', 'ACQUIRING', 'CAPTURE', NULL, 'SCHEME_SETTLEMENT', 'COS_INTERCHANGE', 'PAYABLE_TO_SCHEME', 'fee_amount'),
('Scheme Fee - Cost Recognition', 'Recognise scheme fee as cost of sales', 'ACQUIRING', 'CAPTURE', NULL, 'SCHEME_SETTLEMENT', 'COS_SCHEME_FEE', 'PAYABLE_TO_SCHEME', 'fee_amount'),
('Refund - Reverse Revenue', 'Reverse revenue on refund', 'ACQUIRING', 'REFUND', 'SETTLED', 'SCHEME_SETTLEMENT', 'GROSS_REVENUE', 'RECEIVABLE_FROM_SCHEME', 'leg_amount');
