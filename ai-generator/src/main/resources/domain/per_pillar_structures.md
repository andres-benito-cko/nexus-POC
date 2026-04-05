# Per-Pillar Event Structures

> Part of Nexus Phase 1 — Linking Engine Deep Dive
> Status: Complete
> Last updated: 2026-03-24

## Summary

Five pillars feed the LE, each carrying a distinct role. Gateway provides transaction facts and is the trigger for all downstream activity. COS produces estimated costs, FIAPI impacts the client wallet. Scheme Settlement arrives hours later from clearing files. Cash events (as designed with Cash Matching) provide transaction-level bank confirmation. FX conversion is not a separate event — it is embedded in the first FIAPI Pending message via dual-currency amount fields.

## Detail

### Gateway

Gateway is the system that mediates between the client (merchant) and the card scheme. Its events are the source of truth for transaction facts: processing amount, BIN data, event type (`ChargeCaptured`, `ChargeRefunded`, etc.), `payment_id`, acquirer, response code, timestamps.

Gateway does **not** carry balance impacts. It is the upstream trigger that COS and FIAPI react to. With the Cash Matching feature, Gateway events also carry a `cash_batch_id` that allows the Cash Matching Domain to correlate the transaction to a bank statement line.

---

### Balances / Revenue (FIAPI)

FIAPI impacts the client's currency accounts (wallet). For a single capture, FIAPI may produce **multiple** balance events across the lifecycle of the transaction as funds move through balance types:

```
Pending → Available → Payable
```

Some of these movements can be **bulk** (not per-transaction granularity). The key event for Nexus is the **first FIAPI Pending message**: it carries:

1. **The future available date** — when funds will transition from Pending to Available. This is the `value_date` of the Nexus funding leg. It is established here and does not change.
2. **Dual-currency amounts** — for cross-currency transactions: `processing_amount` (transaction/billing currency) and `holding_amount` (the client's currency account currency). The FX conversion is implicit in the difference between these two fields.

FIAPI consumes COS output to produce balance impacts, which is why COS typically arrives before FIAPI in the LE stream. However, this ordering is not deterministic — the Linker has no ordering logic and simply appends events as they arrive.

---

### Cost of Sales (COS)

COS events carry scheme fees and interchange estimates. They are produced by consuming Gateway events and applying Oracle's fee prediction model. In production, **~99.9% of COS events have `is_predicted=true`** — actual confirmed fees arrive later when schemes issue invoices.

Expected to arrive before FIAPI (since FIAPI consumes COS), but ordering is not guaranteed. When a COS event arrives late, the Linker merges it into existing state and emits a new version — no special handling.

---

### Scheme Settlement (SD)

Scheme Settlement events represent funds cleared at the card scheme and due to CKO. They are produced from clearing files (e.g., TC33 from Visa) via the clearing process.

**Key characteristic:** SD events arrive significantly after the capture — typically **5 to 29 hours** later. A `linkedtransaction` will exist through many versions before SD appears. The SD event carries the `net_settlement_amount`, `settlement_amount`, interchange, and scheme fees in settlement currency.

---

### Cash (with Cash Matching — treat as existing)

**Current state (without Cash Matching):** Cash events in the LE are bank statement lines with classification metadata (e.g., "VISA daily settlement"). They are batch-level, not transactional — they cannot be linked to individual transactions.

**With Cash Matching (design target):**
- Every Gateway event carries a `cash_batch_id`
- The Cash Matching Domain correlates bank statement lines to transactions and emits `cash_matched` events
- The LE surfaces these as transaction-level `cash_events` entries, adding the cash payload at transaction granularity
- Nexus consumes this to flag a capture as **settled** (funds confirmed in CKO bank account)

---

### FX / Cross-Currency

There is no dedicated FX event or pillar. For cross-currency transactions (e.g., client billed in EUR, scheme settles in GBP):

- The first FIAPI Pending event carries both `processing_amount` (transaction/billing currency) and `holding_amount` (client's currency account currency)
- The FX rate is derivable from these two fields
- Nexus constructs an `FX_CONVERSION` leg from this data — it is not a separate LE event

## Open Questions

None.

## Nexus Implications

| Pillar | Nexus mapping |
|--------|--------------|
| Gateway | Header fields: `transaction_amount`, `transaction_currency`, `scheme`, `payment_method`, `acquirer`, `acquirer_country`. Source of `cash_batch_id` for Cash Matching linkage. |
| FIAPI (Balances) | Funding leg: `leg_type=FUNDING`, `value_date` = future available date from first Pending event. Also source of FX rate for `FX_CONVERSION` leg. |
| COS | Fees on the SCHEME_SETTLEMENT leg: `fee_type=INTERCHANGE`, `SCHEME_FEE`. `fee_status=PREDICTED` initially, → `CONFIRMED` when actual invoice arrives. |
| SD | SCHEME_SETTLEMENT leg: `leg_type=SCHEME_SETTLEMENT`, amounts in settlement currency, `value_date=expected_value_date`. |
| Cash (Matched) | Updates the Nexus block status to indicate funds are settled in CKO bank account. May add or update the FUNDING leg with confirmation. |
| FX | `FX_CONVERSION` leg derived from FIAPI Pending `processing_amount` vs `holding_amount`. No separate LE source event. |

**Arrears logic:** The future available date on the first FIAPI Pending event is the single most important field for the funding leg `value_date`. Nexus must extract and persist this at first FIAPI arrival — subsequent Available/Payable movements do not change it.

**Partial state is normal:** SD arrives 5–29 hours after capture, Cash Matching arrives later still. A Nexus block will legitimately exist without a SCHEME_SETTLEMENT leg or cash confirmation for an extended period. The `transaction_status` field must reflect this progression (e.g., `CAPTURED` → `SETTLED`).
