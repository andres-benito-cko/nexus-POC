# LE → Nexus Field Mapping

## Purpose

The Linking Engine emits cumulative `linkedtransaction` records keyed by `action_id`. Each time a new pillar event arrives for an `action_id`, a new version of the record is emitted containing **all events seen so far** (not a delta). Nexus reads the latest version (`MAX(transaction_version)`) and maps the denormalised LE fields into its own canonical hierarchy: **Header → Trade → Leg → Fee**. Because pillars arrive at different times, Nexus must handle partial state gracefully — legs and fees are added incrementally as pillar data becomes available.

## Key Mapping Principles

- One LE `action_id` → one Nexus transaction
- `action_root_id` → `parent_transaction_id` (payment lifecycle grouper)
- Pillar arrival order: GW → COS → FIAPI → SD → Cash (expected, not guaranteed)
- COS-sourced data → `PREDICTED`; SD-sourced data → `ACTUAL`
- FIAPI first Pending event provides `value_date` for FUNDING legs (arrears logic)
- LE fields from pillars that haven't arrived yet are absent — Nexus handles partial state gracefully
- Nexus always uses the latest `transaction_version` for a given `action_id`
- LE pillar arrays are empty (`[]`) when data hasn't arrived yet, not null

## Mapping Tables

### LE Top-Level Fields → Nexus Header

| LE Field | Nexus Field | Notes |
|---|---|---|
| `action_id` | `transaction_id` AND `action_id` | Used both as transaction_id and preserved for traceability |
| `action_root_id` | `parent_transaction_id` AND `action_root_id` | Preserved for traceability |
| (not in LE) | `status` | Computed by Nexus from data completeness (NOT_LIVE/LIVE/DEAD) |
| `transaction_version` | (not surfaced) | Used internally to select the latest version; not part of Nexus contract |
| `transaction_state_id` | (not surfaced) | Stable ID across versions — internal LE concept |
| `linker_processing_timestamp` | (not surfaced) | LE processing timestamp — operational only |
| `id` | (not surfaced) | Unique BQ record ID — internal LE concept |
| `key` | (not surfaced) | Kafka partition key — operational only |
| `metadata` | (not surfaced) | Kafka metadata (ingestion_timestamp, partition, offset) — operational only |

### Common Envelope Fields (all pillars) → Nexus

Every pillar event contains a standard envelope (`balances_changed_event_envelope`, `cos_event_envelope`, etc.) with identical fields:

| LE Field | Nexus Field | Notes |
|---|---|---|
| `origin_unique_event_id` | (traceability only — not surfaced in schema) | Unique source event ID |
| `root_corrected_origin_unique_event_id` | Triggers correction handling | When set: new Nexus transaction with correction semantics |
| `action_id` | (redundant — same as top-level) | Cross-check with top-level action_id |
| `action_root_id` | (redundant — same as top-level) | Cross-check with top-level action_root_id |
| `event_class` | (not surfaced) | TL event structure class — internal |
| `version` | (not surfaced) | Event structure version — internal |
| `corrector_version` | Triggers correction handling | 0=normal, 1=old corrector, 2=new corrector (break-the-glass) |
| `processed_count` | Idempotency guard | >0 means replay — Nexus must deduplicate |
| `tl_consumer_event_id` | (not surfaced) | Internal TL investigation ID |
| `tl_consumer_event_timestamp` | (not surfaced) | TL consumer processing timestamp |
| `origin` | (not surfaced) | Source system (e.g. `fiapi`, `reserves-releaser`) |
| `origin_event_timestamp` | `processed_at` | Fallback when GW `processed_on` is absent |
| `correlation_id` | (not surfaced) | TL Double Entry correlation — internal |
| `ignore_not_found` | (not surfaced) | Correction handling flag — internal |
| `root_origin_unique_event_timestamp` | (not surfaced) | Original event timestamp for corrections — internal |
| `bigquery_primary_key` | (not surfaced) | BQ record key — internal |

### Gateway (`gateway_events[]`) → Nexus

| LE Field | Nexus Field | Notes |
|---|---|---|
| `payment_id` | `action_root_id` / `parent_transaction_id` | Cross-check with LE top-level |
| `event_type` | `trades[].metadata.source_event_type` | ChargeCaptured, ChargeAuthorised, etc. |
| `processed_on` | `processed_at` | Primary source for processed_at |
| `requested_on` | (not surfaced) | Gateway request timestamp — informational only |
| `amount.value` | `trades[].trade_amount` | Processing amount |
| `amount.currency_iso3_code` | `trades[].trade_currency` | Processing currency |
| `aquirer_name` (typo in LE) | `trades[].metadata.acquirer_name` | Note: typo in live LE schema (missing 'c') |
| `aquirer_country` (typo in LE) | `trades[].metadata.acquirer_country` | Note: typo in live LE schema (missing 'c') |
| `cash_batch_id` | `trades[].metadata.cash_batch_id` | Future field — Cash Matching key |
| `event_id` | (traceability only) | Gateway deduplication key |
| `action_id` | (redundant) | Same as top-level |
| `transaction_id` | (traceability only) | GW internal transaction identifier |
| `global_acquirer_id` | (traceability only) | Acquirer identifier |
| `response_code` | (traceability only) | Transaction outcome code (e.g. 10000=success) |

### Balances/Revenue — Metadata (`balances_changed_metadata`) → Nexus

| LE Field | Nexus Field | Notes |
|---|---|---|
| `client_id` | `entity.id` | Primary client identifier |
| `entity.id` | `entity.id` | NAS entity ID (may equal client_id) |
| `entity.subId` | `entity.sub_id` | Sub-entity for platform clients |
| `settlement_company_cko_legal_entity_code` | `cko_entity_id` | Commercial relationship entity |
| `acquirer_company_cko_legal_entity_code` | `trades[].legs[].from_party.party_id` or `to_party.party_id` | Acquirer entity on SCHEME_SETTLEMENT leg |
| `service_provider_cko_legal_entity_code` | (traceability only) | Optional service provider — not surfaced |
| `scheme_code` | `trades[].metadata.scheme_code` | Payment scheme code |
| `payment_method` | `trades[].metadata.payment_method` | Normalise to uppercase (LE has mixed case) |
| `source_event_type` | `trades[].metadata.source_event_type` | Gateway event that triggered this (e.g. ChargeCaptured) |
| `action_type` | Drives `trades[].trade_type` | e.g. BALANCES_CHANGED_ACTION_TYPE_CAPTURE → CAPTURE |
| `enhanced_action_type` | `trades[].metadata.enhanced_action_type` | e.g. PartialRefund |
| `metadata_type` | Helps determine `trade_family` | Product type indicator |
| `external_id` | `trades[].metadata.external_id` | External reference |
| `fin_action_processed_on` | `processed_at` (fallback) | Timestamp balances were modified |
| `currency_account_id` | `trades[].legs[].from_party.currency_account_id` or `to_party.currency_account_id` | NAS currency account |
| `merchant_category_code` | `trades[].metadata.merchant_category_code` | MCC |
| `acquirer_name` | `trades[].metadata.acquirer_name` | Acquirer name |
| `acquirer_country` | `trades[].metadata.acquirer_country` | ISO2 acquirer country |
| `client_settlement_type` | `trades[].metadata.client_settlement_type` | Gross / Net |
| `invoice_number` | `trades[].metadata.invoice_number` | Invoice reference |
| `value_date` | `trades[].legs[FUNDING].value_date` | **Arrears logic** — future available date from first Pending event. Primary source for FUNDING leg value_date. |
| `bank_value_date` | (informational — not primary value_date) | Bank statement date |
| `period_start` | `period_start` (header) | For MinimumBilling / TieredPricing |
| `period_end` | `period_end` (header) | For MinimumBilling / TieredPricing |
| `total_amount_due` | Informs Gross/Net calculation | Not surfaced directly |
| `cash_origin_unique_event_id` | (traceability only) | Links to originating Cash event |

### Balances/Revenue — Actions (`balances_changed_actions[].changes`) → Nexus

| LE Field | Nexus Field | Notes |
|---|---|---|
| `pending.holding_amount` | `trades[].legs[FUNDING].leg_amount` | Amount in client account currency |
| `pending.holding_amount.currency_code` | `trades[].legs[FUNDING].leg_currency` | Client account currency |
| `pending.processing_amount` | `trades[].trade_amount` | Amount in processing currency (may differ for cross-currency) |
| `pending.processing_amount.currency_code` | `trades[].trade_currency` | Processing currency (cross-check with GW) |
| `pending.fx_marked_up_rate` | (informational) | FX rate — implicit in holding vs processing amounts |
| `pending.fx_markup_amount` | (informational) | FX markup amount — implicit in rate |
| `rolling_reserve.holding_amount` | `trades[].legs[FUNDING].reserve_amount` | Rolling reserve withheld per transaction |
| `rolling_reserve.holding_amount.currency_code` | `trades[].legs[FUNDING].reserve_currency` | Reserve currency |
| `fixed_reserve.*` | Separate TRANSFER trade + FUNDING leg (CLIENT_ENTITY → CLIENT_ENTITY) | Fixed reserve movements are their own trade |
| `collateral.*` | Separate TRANSFER trade + FUNDING leg (CLIENT_ENTITY → CLIENT_ENTITY) | Collateral movements are their own trade |
| `operational_funding.*` | (not surfaced) | Internal operational funding — not exposed |
| `available.*` | Not directly mapped — triggers trade_status progression | Available transition = funds released |
| `payable.*` | Not directly mapped | Payable balance update |

### Balances/Revenue — Action Metadata (`action_metadata`) → Nexus

| LE Field | Nexus Field | Notes |
|---|---|---|
| `amount_type` | Helps classify fee_type | `revenue` / `fee` / `tax` |
| `fee_type` | `trades[].legs[].fees[].fee_type` | Maps to Nexus fee type enum (Gateway → PROCESSING_FEE, Scheme → SCHEME_FEE, Interchange → INTERCHANGE, etc.) |
| `fee_amount_type` | `trades[].legs[].fees[].fee_amount_type` | `Fixed` → FIXED, `Variable` → VARIABLE |
| `tax_percentage` | Informs `tax_amount` calculation | Applied tax percentage |

### COS (`cos_events[]`) — Payload (`cos_payload`) → Nexus

| LE Field | Nexus Field | Notes |
|---|---|---|
| `fee.value` | `trades[].legs[SCHEME_SETTLEMENT].fees[].fee_amount` | COS fee amount |
| `fee.currency_code` | `trades[].legs[SCHEME_SETTLEMENT].fees[].fee_currency` | Fee currency |
| `vat.value` | `trades[].legs[SCHEME_SETTLEMENT].fees[].tax_amount` | VAT on fee |
| `vat.currency_code` | `trades[].legs[SCHEME_SETTLEMENT].fees[].tax_currency` | VAT currency |
| `is_predicted` | `fee_status`: true → `PREDICTED`, false → `ACTUAL` | Dominant value is `true` (Oracle prediction) |
| `fee_type` | `trades[].legs[SCHEME_SETTLEMENT].fees[].fee_type` | `FEE_TYPE_SCHEME` → SCHEME_FEE |
| `fee_sub_type` | `trades[].legs[SCHEME_SETTLEMENT].fees[].passthrough` | `Passthrough` → `passthrough: true` |
| `direction` | Sign of fee_amount | Increase/decrease COS |
| `accounting_bucket` | (informational) | Net revenue classification — not surfaced |
| `fee_category` | (informational) | Finance reporting data — not surfaced |

### COS (`cos_events[]`) — Metadata (`cos_metadata`) → Nexus

| LE Field | Nexus Field | Notes |
|---|---|---|
| `acquirer_name` | `trades[].metadata.acquirer_name` | Scheme that issued invoice |
| `acquirer_country` | `trades[].metadata.acquirer_country` | Region of the scheme |
| `acquirer_company_cko_legal_entity_code` | `trades[].legs[SCHEME_SETTLEMENT].from_party.party_id` | CKO acquiring entity |
| `service` | (not surfaced) | Product type — same as gross revenue |
| `payment_method` | `trades[].metadata.payment_method` | Normalise to uppercase |
| `invoice_number` | `trades[].metadata.invoice_number` | Invoice document number |
| `scheme_partner_identifier` | `trades[].metadata.scheme_partner_identifier` | BID/ICA level scheme identifier |
| `invoice_date` | `trades[].legs[SCHEME_SETTLEMENT].value_date` | Accounting date (PREDICTED — use until SD arrives) |
| `period_end` | `period_end` (header) | End of billing period |
| `is_net_settled` | `trades[].metadata.is_net_settled` | Gross or net settled scheme |
| `is_region_live` | (operational — not surfaced) | Region live flag |
| `entity.id` | `entity.id` | Cross-check with FIAPI entity |
| `entity.subId` | `entity.sub_id` | Cross-check |
| `reconciliation_reference` | `trades[].metadata.reconciliation_reference` | Matches Predicted/Async/Actual/Sweep |
| `fee_structure` | Informs `passthrough` flag | Passthrough fee structure |
| `processed_on_timestamp` | `trade_date` (fallback) | Original transaction date before Oracle prediction |
| `billing_frequency` | (not surfaced) | Billing cycle — operational |
| `accrual_type` | (not surfaced) | Accrual method — operational |
| `is_vat_registered` | (not surfaced) | VAT registration — operational |

### Scheme Settlement (`scheme_settlement_events[]`) — Payload (`scheme_settlement_payload`) → Nexus

| LE Field | Nexus Field | Notes |
|---|---|---|
| `settlement_amount.money.value` | `trades[].legs[SCHEME_SETTLEMENT].leg_amount` | Gross settlement amount |
| `settlement_amount.money.currency_code` | `trades[].legs[SCHEME_SETTLEMENT].leg_currency` | Settlement currency |
| `settlement_amount.sign` | Sign of `leg_amount` | Positive/negative settlement |
| `net_settlement_amount` | (informational — used for reconciliation) | Net amount after fees |
| `processing_amount.money.value` | `trades[].trade_amount` | Cross-check with GW amount |
| `processing_amount.money.currency_code` | `trades[].trade_currency` | Cross-check with GW currency |
| `interchange_fee.money.value` | `trades[].legs[SCHEME_SETTLEMENT].fees[]` fee_type=INTERCHANGE, ACTUAL | Convenience field (same as fees[] array) |
| `interchange_fee_tax.money.value` | `trades[].legs[SCHEME_SETTLEMENT].fees[].tax_amount` | Tax on interchange |
| `interchange_fee_truncated` | (not surfaced) | Truncated variant — use `fees[].rounded_amount` instead |
| `scheme_fee.money.value` | `trades[].legs[SCHEME_SETTLEMENT].fees[]` fee_type=SCHEME_FEE, ACTUAL | Convenience field (same as fees[] array) |
| `scheme_fee_truncated` | (not surfaced) | Truncated variant — use `fees[].rounded_amount` instead |
| `fees[].rounded_amount` | `trades[].legs[SCHEME_SETTLEMENT].fees[].fee_amount` | Use `rounded_amount` for fee_amount |
| `fees[].type` | Maps to `fee_type` | INTERCHANGE_FEE → INTERCHANGE, SCHEME_FEE → SCHEME_FEE |
| `fees[].sign` | Sign of fee_amount | Positive = charge, negative = credit |
| `fees[].tax_amount` | `trades[].legs[SCHEME_SETTLEMENT].fees[].tax_amount` | Tax component of fee |
| `fees[].precise_amount` | (not surfaced) | Full precision amount — Nexus uses rounded_amount |
| `fees[].code` | (not surfaced) | Scheme-specific fee code |
| `fees[].type_description` | (not surfaced) | Human-readable fee type description |

### Scheme Settlement (`scheme_settlement_events[]`) — Metadata (`scheme_settlement_metadata`) → Nexus

| LE Field | Nexus Field | Notes |
|---|---|---|
| `scheme` | `trades[].metadata.scheme_code` | Overrides COS value with ACTUAL |
| `transaction_type` | Helps determine `trade_type` | Capture / Refund / Chargeback / Aft Capture / PayToCard |
| `expected_value_date` | `trades[].legs[SCHEME_SETTLEMENT].value_date` | Primary source for SCHEME_SETTLEMENT value_date |
| `central_processing_date` | `trades[].metadata.central_processing_date` | Cleared-at-scheme date |
| `settlement_service_name` | `trades[].metadata.settlement_service_name` | ISS, UKNNSS, EANSS, etc. |
| `acquirer_company_cko_legal_entity_code` | `trades[].legs[SCHEME_SETTLEMENT].from_party.party_id` | Cross-check with FIAPI/COS |
| `settlement_company_cko_legal_entity_code` | `cko_entity_id` (cross-check) | Should match FIAPI value |
| `entity.id` | `entity.id` (cross-check) | Should match FIAPI value |
| `entity.subId` | `entity.sub_id` (cross-check) | Should match FIAPI value |
| `client_id` | `entity.id` (cross-check) | Should match FIAPI value |
| `business_id` | (informational) | MBC entity ID — not surfaced |
| `acquirer_name` | `trades[].metadata.acquirer_name` | Overrides COS/FIAPI value with ACTUAL |
| `acquirer_country` | `trades[].metadata.acquirer_country` | Overrides COS/FIAPI value with ACTUAL |
| `settlement_country_code` | `trades[].metadata.settlement_country_code` | Settlement location country |
| `event_timestamp` | `processed_at` (cross-check) | When event occurred on CKO GW |
| `is_transactional` | (operational — not surfaced) | True when `payment_id` present |
| `transaction_event_type` | (APM only — not surfaced) | APM reconciliation flag |
| `bank_join_reference` | (traceability only) | APM: joins acquirer report with bank statement |
| `partner_event_id` | (traceability only) | APM: links unmatched/late/expired events |

### Cash (`cash_events[]`) — Standard Payload (`cash_standard_payload`) → Nexus

| LE Field | Nexus Field | Notes |
|---|---|---|
| `amount.value` | `trades[].legs[FUNDING].leg_amount` | For standalone CASH transaction |
| `amount.currency_code` | `trades[].legs[FUNDING].leg_currency` | Cash leg currency |
| `direction` | Determines from_party/to_party direction | `debit`: CLIENT_ENTITY → CKO_ENTITY; `credit`: CKO_ENTITY → CLIENT_ENTITY |
| `payout_amount.value` | (informational) | Original payout amount before bank fees |
| `payout_amount.currency_code` | (informational) | Payout currency |
| `bank_fee_amount.value` | `trades[].legs[FUNDING].fees[]` fee_type=PROCESSING_FEE | Bank fee amount |
| `bank_fee_amount.currency_code` | `trades[].legs[FUNDING].fees[].fee_currency` | Bank fee currency |

### Cash (`cash_events[]`) — Transfers Payload (`cash_transfers_payload`) → Nexus

| LE Field | Nexus Field | Notes |
|---|---|---|
| `credit_amount.value` | `trades[].legs[ENTITY_TRANSFER].leg_amount` (credit side) | Internal transfer credit leg |
| `credit_amount.currency_code` | `trades[].legs[ENTITY_TRANSFER].leg_currency` | Credit currency |
| `debit_amount.value` | `trades[].legs[ENTITY_TRANSFER].leg_amount` (debit side) | Internal transfer debit leg |
| `debit_amount.currency_code` | `trades[].legs[ENTITY_TRANSFER].leg_currency` | Debit currency |

### Cash (`cash_events[]`) — Standard Metadata (`cash_standard_metadata`) → Nexus

| LE Field | Nexus Field | Notes |
|---|---|---|
| `scheme` | `trades[].metadata.scheme_code` | Scheme that settled funds |
| `source_event_type` | (not surfaced) | Cash processing event identifier |
| `value_date` | `trades[].legs[FUNDING].value_date` | Bank accounting date |
| `legal_entity` | `trades[].legs[FUNDING].to_party.party_id` | CKO entity whose bank account is impacted |
| `action_type` | `trades[].trade_type` | Type of cash action |
| `entity.id` | `entity.id` | Client entity |
| `entity.subId` | `entity.sub_id` | Sub-entity |
| `currency_account_id` | `trades[].legs[FUNDING].to_party.currency_account_id` | Currency-specific account identifier |
| `account_number` | `trades[].legs[FUNDING].to_party.currency_account_id` | Bank account number |
| `payout_id` | `trades[].metadata.payout_id` | Payout transaction identifier |
| `billing_descriptor` | `trades[].metadata.billing_descriptor` | Billing statement descriptor |
| `invoice_number` | `trades[].metadata.invoice_number` | Invoice reference |
| `cash_batch_id` | `trades[].metadata.cash_batch_id` | Cross-references transaction-level updates (from Cash Matching) |
| `bank_account_number` | (deprecated — use account_number) | Legacy field |
| `bank_name` | (informational) | Bank handling the transaction |
| `bank_account_type` | (informational) | Type of bank account |
| `booked_date` | (cross-check with value_date) | Accounting date |
| `bulk_return` | (informational) | Whether returned funds are in bulk lines |
| `information_for_account_owner` | (informational) | Free text from bank |
| `reference_for_account_owner` | (informational) | Reference for recipient |
| `reference_for_account_servicing_institution` | (informational) | Bank servicing reference |
| `bank_join_reference` | (traceability only) | Joins acquirer report with bank statement |

### Cash (`cash_events[]`) — Transfer Metadata (`cash_transfer_metadata`) → Nexus

| LE Field | Nexus Field | Notes |
|---|---|---|
| `credit_value_date` | `trades[].legs[ENTITY_TRANSFER].value_date` (credit side) | Bank date for credit leg |
| `credit_entry_date` | (not surfaced) | TL reporting date |
| `credit_legal_entity` | `trades[].legs[ENTITY_TRANSFER].to_party.party_id` | Legal entity for credit leg |
| `credit_account_number` | `trades[].legs[ENTITY_TRANSFER].to_party.currency_account_id` | Account for credit leg |
| `debit_value_date` | `trades[].legs[ENTITY_TRANSFER].value_date` (debit side) | Bank date for debit leg |
| `debit_entry_date` | (not surfaced) | TL reporting date |
| `debit_legal_entity` | `trades[].legs[ENTITY_TRANSFER].from_party.party_id` | Legal entity for debit leg |
| `debit_account_number` | `trades[].legs[ENTITY_TRANSFER].from_party.currency_account_id` | Account for debit leg |
| `information_for_account_owner` | (informational) | Free text from bank |
| `reference_for_account_owner` | (informational) | Reference for recipient |
| `reference_for_account_servicing_institution` | (informational) | Bank servicing reference |
| `rail_id` | (traceability only) | Used for reconciling direct debit APMs |

## Trade Family Determination

How Nexus derives `trade_family` from LE data:

| Indicator | trade_family | Notes |
|---|---|---|
| GW present + FIAPI `action_type` contains CAPTURE/AUTH/REFUND/CHARGEBACK + card `payment_method` | `ACQUIRING` | Standard card acquiring flow |
| FIAPI `action_type` = `BALANCES_CHANGED_ACTION_TYPE_CARD_PAYOUT` | `PAYOUT` | Card payout |
| Cash event standalone (no GW) | `CASH` | Cash movement without card transaction |
| FIAPI only, no GW, with `period_start`/`period_end` | `GLOBAL_PRICING` | MinimumBilling / TieredPricing |
| APM `payment_method` (non-card) | `APM` | Alternative payment method |
| Cross-entity: `acquirer_company_cko_legal_entity_code` ≠ `settlement_company_cko_legal_entity_code` | Additional `TRANSFER` trade | Separate trade for inter-entity movement |
| FIAPI `action_type` = `BALANCES_CHANGED_ACTION_TYPE_OPTIMIZATION` or `_RISK_ASSESSMENT` or `_AUTHENTICATION` | Depends on context | May map to ACQUIRING with appropriate trade_type |
| `fixed_reserve` or `collateral` changes present | Additional `TRANSFER` trade | Reserve/collateral movements get their own trade |

## Trade Type Determination

How Nexus derives `trade_type` from LE `action_type`:

| LE `action_type` | Nexus `trade_type` | Notes |
|---|---|---|
| `BALANCES_CHANGED_ACTION_TYPE_AUTHORIZATION` | `AUTH` | |
| `BALANCES_CHANGED_ACTION_TYPE_CAPTURE` | `CAPTURE` | |
| `BALANCES_CHANGED_ACTION_TYPE_REFUND` | `REFUND` | |
| `BALANCES_CHANGED_ACTION_TYPE_CARD_PAYOUT` | `CREDIT` | Under PAYOUT family |
| SD `transaction_type` = `Capture` | `CAPTURE` | Cross-check with FIAPI |
| SD `transaction_type` = `Aft Capture` | `CAPTURE` | Scheme settlement for capture |
| SD `transaction_type` = `Refund` | `REFUND` | Cross-check with FIAPI |
| SD `transaction_type` = `Chargeback` | `CHARGEBACK` | |
| SD `transaction_type` = `PayToCard` | `CREDIT` | Under PAYOUT family |

## Trade Status Progression

How `trade_status` evolves as pillar data arrives:

| Stage | trade_status | Trigger |
|---|---|---|
| GW + FIAPI Pending only | `CAPTURED` / `AUTHORISED` / `INITIATED` | Depends on trade_type |
| COS arrives (predicted fees) | No status change | Fees added with `fee_status: PREDICTED` |
| SD arrives (actual fees) | `ADVISED` → `SETTLED` | `fee_status` flips to `ACTUAL`; `leg_status` flips to `ACTUAL` |
| Cash confirms bank movement | `SETTLED` | Final confirmation |

## Correction Handling

| LE Signal | Nexus Action |
|---|---|
| `root_corrected_origin_unique_event_id` populated | New Nexus transaction with correction semantics; `parent_transaction_id` links to original |
| `corrector_version` = 1 or 2 | Break-the-glass correction — full Nexus transaction correction |
| `corrector_version` = 0 | Normal processing (not a correction) |
| Refund/chargeback (separate `action_id`) | Independent Nexus transaction, same `action_root_id` |
| COS fee updated by SD actual | Update `fee_status` PREDICTED → ACTUAL on matching fee |
| `processed_count` > 0 | Replay — Nexus must deduplicate (idempotency guard) |
| `ignore_not_found` = true | Correction where original was filtered — handle gracefully |

## Fee Status Reconciliation (PREDICTED → ACTUAL)

When SD arrives for an `action_id` that already has COS-predicted fees:

1. Match SD `fees[].type` to existing COS fees by type (INTERCHANGE_FEE → INTERCHANGE, SCHEME_FEE → SCHEME_FEE)
2. Update `fee_amount` to SD `fees[].rounded_amount`
3. Update `fee_currency` from SD `fees[].money.currency_code` if different
4. Update `fee_status` PREDICTED → ACTUAL
5. Update `tax_amount` from SD `fees[].tax_amount`
6. Update `leg_status` on SCHEME_SETTLEMENT leg to ACTUAL
7. Update `trades[].trade_status` to SETTLED (or ADVISED, depending on SD event type)
8. Update `trades[].legs[SCHEME_SETTLEMENT].value_date` from SD `expected_value_date` (overrides COS `invoice_date`)

## Field Priority (when multiple pillars provide the same data)

Several fields are available from multiple pillars. Nexus uses the following priority order:

| Nexus Field | Priority Order | Notes |
|---|---|---|
| `processed_at` | GW `processed_on` > FIAPI `fin_action_processed_on` > Envelope `origin_event_timestamp` | GW is authoritative for processing time |
| `trades[].metadata.acquirer_name` | SD `acquirer_name` > FIAPI `acquirer_name` > GW `aquirer_name` > COS `acquirer_name` | SD is authoritative (ACTUAL) |
| `trades[].metadata.acquirer_country` | SD `acquirer_country` > FIAPI `acquirer_country` > GW `aquirer_country` > COS `acquirer_country` | SD is authoritative (ACTUAL) |
| `trades[].metadata.scheme_code` | SD `scheme` > FIAPI `scheme_code` > COS `payment_method` | SD is authoritative (ACTUAL) |
| `entity.id` | FIAPI `client_id` > SD `client_id` > COS `entity.id` > Cash `entity.id` | FIAPI is primary; others cross-check |
| `cko_entity_id` | FIAPI `settlement_company_cko_legal_entity_code` > SD `settlement_company_cko_legal_entity_code` | FIAPI arrives first; SD confirms |
| `trades[].trade_amount` | GW `amount.value` > FIAPI `pending.processing_amount` > SD `processing_amount` | GW is primary; others cross-check |
| `trades[].metadata.invoice_number` | FIAPI `invoice_number` > COS `invoice_number` > Cash `invoice_number` | Use earliest available |
| `trades[].legs[SCHEME_SETTLEMENT].value_date` | SD `expected_value_date` > COS `invoice_date` | SD overrides COS prediction |
| `trades[].legs[FUNDING].value_date` | FIAPI `value_date` > Cash `value_date` | FIAPI arrears logic is primary |
