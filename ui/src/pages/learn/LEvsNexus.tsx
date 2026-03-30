import React from 'react'

interface MappingEntry {
  le: string
  nexus: string
  notes: string
}

interface PillarGroup {
  name: string
  entries: MappingEntry[]
}

const principles = [
  {
    title: 'LE is cumulative, Nexus reads latest',
    body: 'LE emits a new full record each time a pillar arrives. Nexus always reads the latest transaction_version for a given action_id.',
  },
  {
    title: 'One action_id = one Nexus block',
    body: 'Nexus is not a ledger and does not track balances. It describes financial events.',
  },
  {
    title: 'Partial state is normal',
    body: 'Pillars arrive in order (GW → COS → FIAPI → SD → Cash), but not all at once. Legs are added incrementally as data arrives.',
  },
  {
    title: 'PREDICTED vs ACTUAL',
    body: 'COS-sourced legs are PREDICTED; SD-sourced legs are ACTUAL.',
  },
  {
    title: 'LE internals not surfaced',
    body: 'Fields like transaction_version, transaction_state_id, metadata, and key are LE operational concepts. Nexus does not expose them.',
  },
  {
    title: 'status is computed',
    body: 'NOT_LIVE / LIVE / DEAD is determined by Nexus from data completeness. It does not come from LE.',
  },
]

const NOT_SURFACED = '(not surfaced)'

const pillarGroups: PillarGroup[] = [
  {
    name: 'LE Top-Level Fields → Nexus Header',
    entries: [
      { le: 'action_id', nexus: 'nexus_id AND action_id', notes: 'Used both as nexus_id and preserved for traceability' },
      { le: 'action_root_id', nexus: 'parent_nexus_id AND action_root_id', notes: 'Preserved for traceability' },
      { le: '(not in LE)', nexus: 'status', notes: 'Computed by Nexus from data completeness (NOT_LIVE/LIVE/DEAD)' },
      { le: 'transaction_version', nexus: NOT_SURFACED, notes: 'Used internally to select the latest version; not part of Nexus contract' },
      { le: 'transaction_state_id', nexus: NOT_SURFACED, notes: 'Stable ID across versions — internal LE concept' },
      { le: 'linker_processing_timestamp', nexus: NOT_SURFACED, notes: 'LE processing timestamp — operational only' },
      { le: 'id', nexus: NOT_SURFACED, notes: 'Unique BQ record ID — internal LE concept' },
      { le: 'key', nexus: NOT_SURFACED, notes: 'Kafka partition key — operational only' },
      { le: 'metadata', nexus: NOT_SURFACED, notes: 'Kafka metadata (ingestion_timestamp, partition, offset) — operational only' },
    ],
  },
  {
    name: 'Common Envelope Fields (all pillars) → Nexus',
    entries: [
      { le: 'origin_unique_event_id', nexus: '(traceability only)', notes: 'Unique source event ID' },
      { le: 'root_corrected_origin_unique_event_id', nexus: 'Triggers correction handling', notes: 'When set: new Nexus transaction with correction semantics' },
      { le: 'action_id', nexus: '(redundant)', notes: 'Cross-check with top-level action_id' },
      { le: 'action_root_id', nexus: '(redundant)', notes: 'Cross-check with top-level action_root_id' },
      { le: 'event_class', nexus: NOT_SURFACED, notes: 'TL event structure class — internal' },
      { le: 'version', nexus: NOT_SURFACED, notes: 'Event structure version — internal' },
      { le: 'corrector_version', nexus: 'Triggers correction handling', notes: '0=normal, 1=old corrector, 2=new corrector (break-the-glass)' },
      { le: 'processed_count', nexus: 'Idempotency guard', notes: '>0 means replay — Nexus must deduplicate' },
      { le: 'tl_consumer_event_id', nexus: NOT_SURFACED, notes: 'Internal TL investigation ID' },
      { le: 'tl_consumer_event_timestamp', nexus: NOT_SURFACED, notes: 'TL consumer processing timestamp' },
      { le: 'origin', nexus: NOT_SURFACED, notes: 'Source system (e.g. fiapi, reserves-releaser)' },
      { le: 'origin_event_timestamp', nexus: 'processed_at', notes: 'Fallback when GW processed_on is absent' },
      { le: 'correlation_id', nexus: NOT_SURFACED, notes: 'TL Double Entry correlation — internal' },
      { le: 'ignore_not_found', nexus: NOT_SURFACED, notes: 'Correction handling flag — internal' },
      { le: 'root_origin_unique_event_timestamp', nexus: NOT_SURFACED, notes: 'Original event timestamp for corrections — internal' },
      { le: 'bigquery_primary_key', nexus: NOT_SURFACED, notes: 'BQ record key — internal' },
    ],
  },
  {
    name: 'Gateway (gateway_events[]) → Nexus',
    entries: [
      { le: 'payment_id', nexus: 'action_root_id / parent_nexus_id', notes: 'Cross-check with LE top-level' },
      { le: 'event_type', nexus: 'transactions[].metadata.source_event_type', notes: 'ChargeCaptured, ChargeAuthorised, etc.' },
      { le: 'processed_on', nexus: 'processed_at', notes: 'Primary source for processed_at' },
      { le: 'requested_on', nexus: NOT_SURFACED, notes: 'Gateway request timestamp — informational only' },
      { le: 'amount.value', nexus: 'transactions[].transaction_amount', notes: 'Processing amount' },
      { le: 'amount.currency_iso3_code', nexus: 'transactions[].transaction_currency', notes: 'Processing currency' },
      { le: 'aquirer_name (typo in LE)', nexus: 'transactions[].metadata.acquirer_name', notes: 'Note: typo in live LE schema (missing \'c\')' },
      { le: 'aquirer_country (typo in LE)', nexus: 'transactions[].metadata.acquirer_country', notes: 'Note: typo in live LE schema (missing \'c\')' },
      { le: 'cash_batch_id', nexus: 'transactions[].metadata.cash_batch_id', notes: 'Future field — Cash Matching key' },
      { le: 'event_id', nexus: '(traceability only)', notes: 'Gateway deduplication key' },
      { le: 'action_id', nexus: '(redundant)', notes: 'Same as top-level' },
      { le: 'transaction_id', nexus: '(traceability only)', notes: 'GW internal transaction identifier' },
      { le: 'global_acquirer_id', nexus: '(traceability only)', notes: 'Acquirer identifier' },
      { le: 'response_code', nexus: '(traceability only)', notes: 'Transaction outcome code (e.g. 10000=success)' },
    ],
  },
  {
    name: 'Balances/Revenue — Metadata (balances_changed_metadata) → Nexus',
    entries: [
      { le: 'client_id', nexus: 'entity.id', notes: 'Primary client identifier' },
      { le: 'entity.id', nexus: 'entity.id', notes: 'NAS entity ID (may equal client_id)' },
      { le: 'entity.subId', nexus: 'entity.sub_id', notes: 'Sub-entity for platform clients' },
      { le: 'settlement_company_cko_legal_entity_code', nexus: 'cko_entity_id', notes: 'Commercial relationship entity' },
      { le: 'acquirer_company_cko_legal_entity_code', nexus: 'transactions[].legs[].from_party.party_id or to_party.party_id', notes: 'Acquirer entity on SCHEME_SETTLEMENT leg' },
      { le: 'service_provider_cko_legal_entity_code', nexus: '(traceability only)', notes: 'Optional service provider — not surfaced' },
      { le: 'scheme_code', nexus: 'transactions[].metadata.scheme_code', notes: 'Payment scheme code' },
      { le: 'payment_method', nexus: 'transactions[].metadata.payment_method', notes: 'Normalise to uppercase (LE has mixed case)' },
      { le: 'source_event_type', nexus: 'transactions[].metadata.source_event_type', notes: 'Gateway event that triggered this (e.g. ChargeCaptured)' },
      { le: 'action_type', nexus: 'Drives transactions[].transaction_type', notes: 'e.g. BALANCES_CHANGED_ACTION_TYPE_CAPTURE → CAPTURE' },
      { le: 'enhanced_action_type', nexus: 'transactions[].metadata.enhanced_action_type', notes: 'e.g. PartialRefund' },
      { le: 'metadata_type', nexus: 'Helps determine product_type', notes: 'Product type indicator' },
      { le: 'external_id', nexus: 'transactions[].metadata.external_id', notes: 'External reference' },
      { le: 'fin_action_processed_on', nexus: 'processed_at (fallback)', notes: 'Timestamp balances were modified' },
      { le: 'currency_account_id', nexus: 'transactions[].legs[].from_party.currency_account_id or to_party.currency_account_id', notes: 'NAS currency account' },
      { le: 'merchant_category_code', nexus: 'transactions[].metadata.merchant_category_code', notes: 'MCC' },
      { le: 'acquirer_name', nexus: 'transactions[].metadata.acquirer_name', notes: 'Acquirer name' },
      { le: 'acquirer_country', nexus: 'transactions[].metadata.acquirer_country', notes: 'ISO2 acquirer country' },
      { le: 'client_settlement_type', nexus: 'transactions[].metadata.client_settlement_type', notes: 'Gross / Net' },
      { le: 'invoice_number', nexus: 'transactions[].metadata.invoice_number', notes: 'Invoice reference' },
      { le: 'value_date', nexus: 'transactions[].legs[FUNDING].value_date', notes: 'Arrears logic — future available date from first Pending event. Primary source for FUNDING leg value_date.' },
      { le: 'bank_value_date', nexus: '(informational)', notes: 'Bank statement date — not primary value_date' },
      { le: 'period_start', nexus: 'period_start (header)', notes: 'For MinimumBilling / TieredPricing' },
      { le: 'period_end', nexus: 'period_end (header)', notes: 'For MinimumBilling / TieredPricing' },
      { le: 'total_amount_due', nexus: 'Informs Gross/Net calculation', notes: 'Not surfaced directly' },
      { le: 'cash_origin_unique_event_id', nexus: '(traceability only)', notes: 'Links to originating Cash event' },
    ],
  },
  {
    name: 'Balances/Revenue — Actions (balances_changed_actions[].changes) → Nexus',
    entries: [
      { le: 'pending.holding_amount', nexus: 'transactions[].legs[FUNDING].leg_amount', notes: 'Amount in client account currency' },
      { le: 'pending.holding_amount.currency_code', nexus: 'transactions[].legs[FUNDING].leg_currency', notes: 'Client account currency' },
      { le: 'pending.processing_amount', nexus: 'transactions[].transaction_amount', notes: 'Amount in processing currency (may differ for cross-currency)' },
      { le: 'pending.processing_amount.currency_code', nexus: 'transactions[].transaction_currency', notes: 'Processing currency (cross-check with GW)' },
      { le: 'pending.fx_marked_up_rate', nexus: '(informational)', notes: 'FX rate — implicit in holding vs processing amounts' },
      { le: 'pending.fx_markup_amount', nexus: '(informational)', notes: 'FX markup amount — implicit in rate' },
      { le: 'rolling_reserve.holding_amount', nexus: 'transactions[].legs[FUNDING].reserve_amount', notes: 'Rolling reserve withheld per transaction' },
      { le: 'rolling_reserve.holding_amount.currency_code', nexus: 'transactions[].legs[FUNDING].reserve_currency', notes: 'Reserve currency' },
      { le: 'fixed_reserve.*', nexus: 'Separate TRANSFER trade + FUNDING leg (CLIENT_ENTITY → CLIENT_ENTITY)', notes: 'Fixed reserve movements are their own trade' },
      { le: 'collateral.*', nexus: 'Separate TRANSFER trade + FUNDING leg (CLIENT_ENTITY → CLIENT_ENTITY)', notes: 'Collateral movements are their own trade' },
      { le: 'operational_funding.*', nexus: NOT_SURFACED, notes: 'Internal operational funding — not exposed' },
      { le: 'available.*', nexus: 'Not directly mapped', notes: 'Available transition = funds released; triggers transaction_status progression' },
      { le: 'payable.*', nexus: 'Not directly mapped', notes: 'Payable balance update' },
    ],
  },
  {
    name: 'Balances/Revenue — Action Metadata (action_metadata) → Nexus',
    entries: [
      { le: 'amount_type', nexus: 'Helps classify fee_type', notes: 'revenue / fee / tax' },
      { le: 'fee_type', nexus: 'transactions[].legs[].fees[].fee_type', notes: 'Maps to Nexus fee type enum (Gateway → PROCESSING_FEE, Scheme → SCHEME_FEE, Interchange → INTERCHANGE, etc.)' },
      { le: 'fee_amount_type', nexus: 'transactions[].legs[].fees[].fee_amount_type', notes: 'Fixed → FIXED, Variable → VARIABLE' },
      { le: 'tax_percentage', nexus: 'Informs tax_amount calculation', notes: 'Applied tax percentage' },
    ],
  },
  {
    name: 'COS (cos_events[]) — Payload (cos_payload) → Nexus',
    entries: [
      { le: 'fee.value', nexus: 'transactions[].legs[SCHEME_SETTLEMENT].fees[].fee_amount', notes: 'COS fee amount' },
      { le: 'fee.currency_code', nexus: 'transactions[].legs[SCHEME_SETTLEMENT].fees[].fee_currency', notes: 'Fee currency' },
      { le: 'vat.value', nexus: 'transactions[].legs[SCHEME_SETTLEMENT].fees[].tax_amount', notes: 'VAT on fee' },
      { le: 'vat.currency_code', nexus: 'transactions[].legs[SCHEME_SETTLEMENT].fees[].tax_currency', notes: 'VAT currency' },
      { le: 'is_predicted', nexus: 'fee_status: true → PREDICTED, false → ACTUAL', notes: 'Dominant value is true (Oracle prediction)' },
      { le: 'fee_type', nexus: 'transactions[].legs[SCHEME_SETTLEMENT].fees[].fee_type', notes: 'FEE_TYPE_SCHEME → SCHEME_FEE' },
      { le: 'fee_sub_type', nexus: 'transactions[].legs[SCHEME_SETTLEMENT].fees[].passthrough', notes: 'Passthrough → passthrough: true' },
      { le: 'direction', nexus: 'Sign of fee_amount', notes: 'Increase/decrease COS' },
      { le: 'accounting_bucket', nexus: '(informational)', notes: 'Net revenue classification — not surfaced' },
      { le: 'fee_category', nexus: '(informational)', notes: 'Finance reporting data — not surfaced' },
    ],
  },
  {
    name: 'COS (cos_events[]) — Metadata (cos_metadata) → Nexus',
    entries: [
      { le: 'acquirer_name', nexus: 'transactions[].metadata.acquirer_name', notes: 'Scheme that issued invoice' },
      { le: 'acquirer_country', nexus: 'transactions[].metadata.acquirer_country', notes: 'Region of the scheme' },
      { le: 'acquirer_company_cko_legal_entity_code', nexus: 'transactions[].legs[SCHEME_SETTLEMENT].from_party.party_id', notes: 'CKO acquiring entity' },
      { le: 'service', nexus: NOT_SURFACED, notes: 'Product type — same as gross revenue' },
      { le: 'payment_method', nexus: 'transactions[].metadata.payment_method', notes: 'Normalise to uppercase' },
      { le: 'invoice_number', nexus: 'transactions[].metadata.invoice_number', notes: 'Invoice document number' },
      { le: 'scheme_partner_identifier', nexus: 'transactions[].metadata.scheme_partner_identifier', notes: 'BID/ICA level scheme identifier' },
      { le: 'invoice_date', nexus: 'transactions[].legs[SCHEME_SETTLEMENT].value_date', notes: 'Accounting date (PREDICTED — use until SD arrives)' },
      { le: 'period_end', nexus: 'period_end (header)', notes: 'End of billing period' },
      { le: 'is_net_settled', nexus: 'transactions[].metadata.is_net_settled', notes: 'Gross or net settled scheme' },
      { le: 'is_region_live', nexus: NOT_SURFACED, notes: 'Region live flag — operational' },
      { le: 'entity.id', nexus: 'entity.id', notes: 'Cross-check with FIAPI entity' },
      { le: 'entity.subId', nexus: 'entity.sub_id', notes: 'Cross-check' },
      { le: 'reconciliation_reference', nexus: 'transactions[].metadata.reconciliation_reference', notes: 'Matches Predicted/Async/Actual/Sweep' },
      { le: 'fee_structure', nexus: 'Informs passthrough flag', notes: 'Passthrough fee structure' },
      { le: 'processed_on_timestamp', nexus: 'transaction_date (fallback)', notes: 'Original transaction date before Oracle prediction' },
      { le: 'billing_frequency', nexus: NOT_SURFACED, notes: 'Billing cycle — operational' },
      { le: 'accrual_type', nexus: NOT_SURFACED, notes: 'Accrual method — operational' },
      { le: 'is_vat_registered', nexus: NOT_SURFACED, notes: 'VAT registration — operational' },
    ],
  },
  {
    name: 'Scheme Settlement (scheme_settlement_events[]) — Payload → Nexus',
    entries: [
      { le: 'settlement_amount.money.value', nexus: 'transactions[].legs[SCHEME_SETTLEMENT].leg_amount', notes: 'Gross settlement amount' },
      { le: 'settlement_amount.money.currency_code', nexus: 'transactions[].legs[SCHEME_SETTLEMENT].leg_currency', notes: 'Settlement currency' },
      { le: 'settlement_amount.sign', nexus: 'Sign of leg_amount', notes: 'Positive/negative settlement' },
      { le: 'net_settlement_amount', nexus: '(informational)', notes: 'Net amount after fees — used for reconciliation' },
      { le: 'processing_amount.money.value', nexus: 'transactions[].transaction_amount', notes: 'Cross-check with GW amount' },
      { le: 'processing_amount.money.currency_code', nexus: 'transactions[].transaction_currency', notes: 'Cross-check with GW currency' },
      { le: 'interchange_fee.money.value', nexus: 'transactions[].legs[SCHEME_SETTLEMENT].fees[] fee_type=INTERCHANGE, ACTUAL', notes: 'Convenience field (same as fees[] array)' },
      { le: 'interchange_fee_tax.money.value', nexus: 'transactions[].legs[SCHEME_SETTLEMENT].fees[].tax_amount', notes: 'Tax on interchange' },
      { le: 'interchange_fee_truncated', nexus: NOT_SURFACED, notes: 'Truncated variant — use fees[].rounded_amount instead' },
      { le: 'scheme_fee.money.value', nexus: 'transactions[].legs[SCHEME_SETTLEMENT].fees[] fee_type=SCHEME_FEE, ACTUAL', notes: 'Convenience field (same as fees[] array)' },
      { le: 'scheme_fee_truncated', nexus: NOT_SURFACED, notes: 'Truncated variant — use fees[].rounded_amount instead' },
      { le: 'fees[].rounded_amount', nexus: 'transactions[].legs[SCHEME_SETTLEMENT].fees[].fee_amount', notes: 'Use rounded_amount for fee_amount' },
      { le: 'fees[].type', nexus: 'Maps to fee_type', notes: 'INTERCHANGE_FEE → INTERCHANGE, SCHEME_FEE → SCHEME_FEE' },
      { le: 'fees[].sign', nexus: 'Sign of fee_amount', notes: 'Positive = charge, negative = credit' },
      { le: 'fees[].tax_amount', nexus: 'transactions[].legs[SCHEME_SETTLEMENT].fees[].tax_amount', notes: 'Tax component of fee' },
      { le: 'fees[].precise_amount', nexus: NOT_SURFACED, notes: 'Full precision amount — Nexus uses rounded_amount' },
      { le: 'fees[].code', nexus: NOT_SURFACED, notes: 'Scheme-specific fee code' },
      { le: 'fees[].type_description', nexus: NOT_SURFACED, notes: 'Human-readable fee type description' },
    ],
  },
  {
    name: 'Scheme Settlement (scheme_settlement_events[]) — Metadata → Nexus',
    entries: [
      { le: 'scheme', nexus: 'transactions[].metadata.scheme_code', notes: 'Overrides COS value with ACTUAL' },
      { le: 'transaction_type', nexus: 'Helps determine transaction_type', notes: 'Capture / Refund / Chargeback / Aft Capture / PayToCard' },
      { le: 'expected_value_date', nexus: 'transactions[].legs[SCHEME_SETTLEMENT].value_date', notes: 'Primary source for SCHEME_SETTLEMENT value_date' },
      { le: 'central_processing_date', nexus: 'transactions[].metadata.central_processing_date', notes: 'Cleared-at-scheme date' },
      { le: 'settlement_service_name', nexus: 'transactions[].metadata.settlement_service_name', notes: 'ISS, UKNNSS, EANSS, etc.' },
      { le: 'acquirer_company_cko_legal_entity_code', nexus: 'transactions[].legs[SCHEME_SETTLEMENT].from_party.party_id', notes: 'Cross-check with FIAPI/COS' },
      { le: 'settlement_company_cko_legal_entity_code', nexus: 'cko_entity_id (cross-check)', notes: 'Should match FIAPI value' },
      { le: 'entity.id', nexus: 'entity.id (cross-check)', notes: 'Should match FIAPI value' },
      { le: 'entity.subId', nexus: 'entity.sub_id (cross-check)', notes: 'Should match FIAPI value' },
      { le: 'client_id', nexus: 'entity.id (cross-check)', notes: 'Should match FIAPI value' },
      { le: 'business_id', nexus: '(informational)', notes: 'MBC entity ID — not surfaced' },
      { le: 'acquirer_name', nexus: 'transactions[].metadata.acquirer_name', notes: 'Overrides COS/FIAPI value with ACTUAL' },
      { le: 'acquirer_country', nexus: 'transactions[].metadata.acquirer_country', notes: 'Overrides COS/FIAPI value with ACTUAL' },
      { le: 'settlement_country_code', nexus: 'transactions[].metadata.settlement_country_code', notes: 'Settlement location country' },
      { le: 'event_timestamp', nexus: 'processed_at (cross-check)', notes: 'When event occurred on CKO GW' },
      { le: 'is_transactional', nexus: NOT_SURFACED, notes: 'True when payment_id present — operational' },
      { le: 'transaction_event_type', nexus: NOT_SURFACED, notes: 'APM reconciliation flag — APM only' },
      { le: 'bank_join_reference', nexus: '(traceability only)', notes: 'APM: joins acquirer report with bank statement' },
      { le: 'partner_event_id', nexus: '(traceability only)', notes: 'APM: links unmatched/late/expired events' },
    ],
  },
  {
    name: 'Cash (cash_events[]) — Standard Payload → Nexus',
    entries: [
      { le: 'amount.value', nexus: 'transactions[].legs[FUNDING].leg_amount', notes: 'For standalone CASH transaction' },
      { le: 'amount.currency_code', nexus: 'transactions[].legs[FUNDING].leg_currency', notes: 'Cash leg currency' },
      { le: 'direction', nexus: 'Determines from_party/to_party direction', notes: 'debit: CLIENT_ENTITY → CKO_ENTITY; credit: CKO_ENTITY → CLIENT_ENTITY' },
      { le: 'payout_amount.value', nexus: '(informational)', notes: 'Original payout amount before bank fees' },
      { le: 'payout_amount.currency_code', nexus: '(informational)', notes: 'Payout currency' },
      { le: 'bank_fee_amount.value', nexus: 'transactions[].legs[FUNDING].fees[] fee_type=PROCESSING_FEE', notes: 'Bank fee amount' },
      { le: 'bank_fee_amount.currency_code', nexus: 'transactions[].legs[FUNDING].fees[].fee_currency', notes: 'Bank fee currency' },
    ],
  },
  {
    name: 'Cash (cash_events[]) — Transfers Payload → Nexus',
    entries: [
      { le: 'credit_amount.value', nexus: 'transactions[].legs[ENTITY_TRANSFER].leg_amount (credit side)', notes: 'Internal transfer credit leg' },
      { le: 'credit_amount.currency_code', nexus: 'transactions[].legs[ENTITY_TRANSFER].leg_currency', notes: 'Credit currency' },
      { le: 'debit_amount.value', nexus: 'transactions[].legs[ENTITY_TRANSFER].leg_amount (debit side)', notes: 'Internal transfer debit leg' },
      { le: 'debit_amount.currency_code', nexus: 'transactions[].legs[ENTITY_TRANSFER].leg_currency', notes: 'Debit currency' },
    ],
  },
  {
    name: 'Cash (cash_events[]) — Standard Metadata → Nexus',
    entries: [
      { le: 'scheme', nexus: 'transactions[].metadata.scheme_code', notes: 'Scheme that settled funds' },
      { le: 'source_event_type', nexus: NOT_SURFACED, notes: 'Cash processing event identifier' },
      { le: 'value_date', nexus: 'transactions[].legs[FUNDING].value_date', notes: 'Bank accounting date' },
      { le: 'legal_entity', nexus: 'transactions[].legs[FUNDING].to_party.party_id', notes: 'CKO entity whose bank account is impacted' },
      { le: 'action_type', nexus: 'transactions[].transaction_type', notes: 'Type of cash action' },
      { le: 'entity.id', nexus: 'entity.id', notes: 'Client entity' },
      { le: 'entity.subId', nexus: 'entity.sub_id', notes: 'Sub-entity' },
      { le: 'currency_account_id', nexus: 'transactions[].legs[FUNDING].to_party.currency_account_id', notes: 'Currency-specific account identifier' },
      { le: 'account_number', nexus: 'transactions[].legs[FUNDING].to_party.currency_account_id', notes: 'Bank account number' },
      { le: 'payout_id', nexus: 'transactions[].metadata.payout_id', notes: 'Payout transaction identifier' },
      { le: 'billing_descriptor', nexus: 'transactions[].metadata.billing_descriptor', notes: 'Billing statement descriptor' },
      { le: 'invoice_number', nexus: 'transactions[].metadata.invoice_number', notes: 'Invoice reference' },
      { le: 'cash_batch_id', nexus: 'transactions[].metadata.cash_batch_id', notes: 'Cross-references transaction-level updates (from Cash Matching)' },
      { le: 'bank_account_number', nexus: '(deprecated)', notes: 'Legacy field — use account_number' },
      { le: 'bank_name', nexus: '(informational)', notes: 'Bank handling the transaction' },
      { le: 'bank_account_type', nexus: '(informational)', notes: 'Type of bank account' },
      { le: 'booked_date', nexus: '(cross-check with value_date)', notes: 'Accounting date' },
      { le: 'bulk_return', nexus: '(informational)', notes: 'Whether returned funds are in bulk lines' },
      { le: 'information_for_account_owner', nexus: '(informational)', notes: 'Free text from bank' },
      { le: 'reference_for_account_owner', nexus: '(informational)', notes: 'Reference for recipient' },
      { le: 'reference_for_account_servicing_institution', nexus: '(informational)', notes: 'Bank servicing reference' },
      { le: 'bank_join_reference', nexus: '(traceability only)', notes: 'Joins acquirer report with bank statement' },
    ],
  },
  {
    name: 'Cash (cash_events[]) — Transfer Metadata → Nexus',
    entries: [
      { le: 'credit_value_date', nexus: 'transactions[].legs[ENTITY_TRANSFER].value_date (credit side)', notes: 'Bank date for credit leg' },
      { le: 'credit_entry_date', nexus: NOT_SURFACED, notes: 'TL reporting date' },
      { le: 'credit_legal_entity', nexus: 'transactions[].legs[ENTITY_TRANSFER].to_party.party_id', notes: 'Legal entity for credit leg' },
      { le: 'credit_account_number', nexus: 'transactions[].legs[ENTITY_TRANSFER].to_party.currency_account_id', notes: 'Account for credit leg' },
      { le: 'debit_value_date', nexus: 'transactions[].legs[ENTITY_TRANSFER].value_date (debit side)', notes: 'Bank date for debit leg' },
      { le: 'debit_entry_date', nexus: NOT_SURFACED, notes: 'TL reporting date' },
      { le: 'debit_legal_entity', nexus: 'transactions[].legs[ENTITY_TRANSFER].from_party.party_id', notes: 'Legal entity for debit leg' },
      { le: 'debit_account_number', nexus: 'transactions[].legs[ENTITY_TRANSFER].from_party.currency_account_id', notes: 'Account for debit leg' },
      { le: 'information_for_account_owner', nexus: '(informational)', notes: 'Free text from bank' },
      { le: 'reference_for_account_owner', nexus: '(informational)', notes: 'Reference for recipient' },
      { le: 'reference_for_account_servicing_institution', nexus: '(informational)', notes: 'Bank servicing reference' },
      { le: 'rail_id', nexus: '(traceability only)', notes: 'Used for reconciling direct debit APMs' },
    ],
  },
]

export default function LEvsNexus() {
  return (
    <div className="max-w-4xl space-y-8 py-6">
      <section>
        <h2 className="text-lg font-semibold text-zinc-900 mb-4">Key Principles</h2>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {principles.map((p) => (
            <div
              key={p.title}
              className="bg-white border border-zinc-200 rounded-lg p-4"
            >
              <p className="font-bold text-zinc-900 text-sm mb-1">{p.title}</p>
              <p className="text-zinc-600 text-sm">{p.body}</p>
            </div>
          ))}
        </div>
      </section>

      <section>
        <h2 className="text-lg font-semibold text-zinc-900 mb-4">Field Mapping by Pillar</h2>
        <div className="border border-zinc-200 rounded-lg overflow-hidden">
          <table className="w-full border-collapse">
            <thead>
              <tr className="bg-zinc-100 border-b border-zinc-200">
                <th className="text-left px-3 py-2 text-xs font-semibold text-zinc-600 uppercase tracking-wider w-1/3">LE Field</th>
                <th className="text-left px-3 py-2 text-xs font-semibold text-zinc-600 uppercase tracking-wider w-1/3">Nexus Field</th>
                <th className="text-left px-3 py-2 text-xs font-semibold text-zinc-600 uppercase tracking-wider w-1/3">Notes</th>
              </tr>
            </thead>
            <tbody>
              {pillarGroups.map((group) => (
                <React.Fragment key={group.name}>
                  <tr className="bg-zinc-50">
                    <td
                      colSpan={3}
                      className="px-3 py-2 font-semibold text-zinc-600 text-xs uppercase tracking-wider border-t border-b border-zinc-200"
                    >
                      {group.name}
                    </td>
                  </tr>
                  {group.entries.map((entry) => (
                    <tr
                      key={`${group.name}::${entry.le}`}
                      className="border-t border-zinc-100 hover:bg-zinc-50"
                    >
                      <td className="px-3 py-2 font-mono text-zinc-800 text-xs align-top">{entry.le}</td>
                      <td className="px-3 py-2 align-top">
                        {entry.nexus === NOT_SURFACED ? (
                          <span className="text-zinc-400 italic text-xs">{entry.nexus}</span>
                        ) : (
                          <span className="font-mono text-blue-600 text-xs">{entry.nexus}</span>
                        )}
                      </td>
                      <td className="px-3 py-2 text-zinc-500 text-xs align-top">{entry.notes}</td>
                    </tr>
                  ))}
                </React.Fragment>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  )
}
