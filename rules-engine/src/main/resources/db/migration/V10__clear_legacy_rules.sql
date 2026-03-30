-- Remove V3 seed rules which reference account codes not in the accounts table.
-- New rules are created via the Chart of Accounts UI.
DELETE FROM rules;
