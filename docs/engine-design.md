# Nexus POC — Engine Design

## Design Decision: Option C — Config-Driven with Named Resolvers

The Nexus Transformer uses a two-layer DSL:

- **Simple expressions** (user-owned) — SpEL expressions referencing `$field` paths, `$when` conditionals, and `$concat` helpers. Cover approximately 70% of fields. Business users can modify these without engineering involvement.
- **Named resolvers** (engineer-implemented) — Opaque functions registered in the resolver registry and invoked via `$resolve(resolverName, args...)`. Used for logic that cannot be expressed safely in config: currency normalisation, pillar aggregation, ID generation.

**Principle:** users configure *what*, engineers implement *how*.

This avoids two failure modes:
- Pure-code engines require engineer involvement for every business logic change.
- Fully Turing-complete config DSLs (e.g., raw Groovy) create security and maintainability risks.

## Engine Config (YAML)

```yaml
# nexus-engine-config.yaml
# Loaded by nexus-api and distributed to nexus-transformer at startup

version: "1.0"

classifier:
  tradeFamily:
    - when: "$pillars.contains('SD')"
      value: "SCHEME_SETTLEMENT"
    - when: "$pillars.contains('COS') && $pillars.contains('FIAPI')"
      value: "CARD_TRANSACTION"
    - when: "$pillars.contains('CASH')"
      value: "CASH_MOVEMENT"
    - default: "UNKNOWN"

  tradeType:
    - when: "$tradeFamily == 'SCHEME_SETTLEMENT'"
      value: "$resolve(schemeSettlementType, $sd.settlementType)"
    - when: "$tradeFamily == 'CARD_TRANSACTION' && $gw.transactionType == 'CAPTURE'"
      value: "CAPTURE"
    - when: "$tradeFamily == 'CARD_TRANSACTION' && $gw.transactionType == 'REFUND'"
      value: "REFUND"
    - when: "$tradeFamily == 'CARD_TRANSACTION' && $gw.transactionType == 'VOID'"
      value: "VOID"
    - when: "$tradeFamily == 'CASH_MOVEMENT'"
      value: "$resolve(cashMovementType, $cash.direction, $cash.subType)"
    - default: "UNKNOWN"

stateMachine:
  transactionStatus:
    NOT_LIVE:
      - when: "$pillarCount >= $resolve(minimumPillarsForLive, $tradeFamily)"
        transition: LIVE
    LIVE:
      - when: "$pillars.contains('SD') || $tradeFamily == 'CASH_MOVEMENT'"
        transition: DEAD

  tradeStatus:
    PENDING:
      - when: "$leg.valueType == 'ACTUAL'"
        transition: SETTLED
      - when: "$leg.valueType == 'PREDICTED' && $leg.pillar == 'FIAPI'"
        transition: PREDICTED

transaction:
  header:
    transactionId: "$resolve(generateTransactionId, $actionId, $version)"
    actionId: "$actionId"
    parentTransactionId: "$resolve(lookupParentTransaction, $actionId)"
    transactionTimestamp: "$resolve(normaliseTimestamp, $gw.createdOn)"
    processingDate: "$resolve(deriveProcessingDate, $gw.createdOn, $gw.timeZone)"
    transactionStatus: "$transactionStatus"
    version: "$version"
    schemaVersion: "1.0"

  clientEntity:
    entityId: "$gw.merchantId"
    entityType: "CLIENT_ENTITY"
    subEntityId: "$gw.subMerchantId"

  ckoEntity:
    entityId: "$resolve(deriveCkoEntityId, $gw.processingChannelId)"
    entityType: "CKO_ENTITY"

  trades:
    - id: "$resolve(generateTradeId, $transactionId, $tradeFamily)"
      tradeFamily: "$tradeFamily"
      tradeType: "$tradeType"
      tradeStatus: "$tradeStatus"
      originatingAmount: "$gw.amount"
      originatingCurrency: "$resolve(normaliseCurrency, $gw.currency)"
      settledAmount: "$resolve(resolveSettledAmount, $sd, $fiapi)"
      settledCurrency: "$resolve(normaliseSettledCurrency, $sd, $fiapi)"
      valueDate: "$resolve(resolveValueDate, $sd, $fiapi)"
      scheme: "$resolve(normaliseScheme, $gw.scheme)"
      authCode: "$gw.authCode"

      legs:
        - id: "$resolve(generateLegId, $tradeId, 'SCHEME_SETTLEMENT')"
          pillar: "SCHEME_SETTLEMENT"
          valueType: "$resolve(deriveValueType, $sd)"
          amount: "$resolve(resolveSchemeSettlementAmount, $sd, $fiapi)"
          currency: "$resolve(normaliseSettledCurrency, $sd, $fiapi)"
          direction: "$resolve(deriveDirection, $tradeType)"
          counterpartyType: "SCHEME"
          counterpartyId: "$resolve(normaliseScheme, $gw.scheme)"
          enabled:
            when: "$tradeFamily == 'CARD_TRANSACTION'"

        - id: "$resolve(generateLegId, $tradeId, 'FUNDING')"
          pillar: "FUNDING"
          valueType: "$resolve(deriveValueType, $fiapi)"
          amount: "$fiapi.netAmount"
          currency: "$resolve(normaliseCurrency, $fiapi.currency)"
          direction: "$resolve(deriveDirection, $tradeType)"
          counterpartyType: "CLIENT_ENTITY"
          counterpartyId: "$gw.merchantId"
          enabled:
            when: "$pillars.contains('FIAPI')"

        - id: "$resolve(generateLegId, $tradeId, 'CASH')"
          pillar: "CASH"
          valueType: "ACTUAL"
          amount: "$cash.amount"
          currency: "$resolve(normaliseCurrency, $cash.currency)"
          direction: "$cash.direction"
          counterpartyType: "$resolve(cashCounterpartyType, $cash.subType)"
          counterpartyId: "$resolve(cashCounterpartyId, $cash)"
          enabled:
            when: "$tradeFamily == 'CASH_MOVEMENT'"

      fees:
        - id: "$resolve(generateFeeId, $tradeId, 'SCHEME_FEE')"
          feeType: "SCHEME_FEE"
          valueType: "$resolve(deriveValueType, $sd)"
          amount: "$resolve(resolveSchemeFee, $sd, $cos)"
          currency: "$resolve(normaliseSettledCurrency, $sd, $fiapi)"
          enabled:
            when: "$pillars.contains('COS') || $pillars.contains('SD')"

        - id: "$resolve(generateFeeId, $tradeId, 'PROCESSING_FEE')"
          feeType: "PROCESSING_FEE"
          valueType: "ACTUAL"
          amount: "$cos.processingFee"
          currency: "$resolve(normaliseCurrency, $cos.currency)"
          enabled:
            when: "$pillars.contains('COS')"
```

## What Users Can Do Without Engineers

| Capability | Example |
|---|---|
| Change classification rules | Add a new `tradeFamily` condition for a new pillar combination |
| Change state machine transitions | Adjust when a transaction moves from `LIVE` to `DEAD` |
| Add or remove a fee type from a trade | Enable/disable a fee entry via the `enabled.when` expression |
| Change field mappings | Map `originatingCurrency` to a different source field |
| Adjust leg enablement conditions | Enable the `CASH` leg only for specific `cash.subType` values |
| Change processing date derivation logic | Update the `$resolve` call or swap resolver arguments |

## What Still Needs Engineers

| Capability | Reason |
|---|---|
| Implement a new named resolver | Resolvers are Java classes registered in the resolver registry; config can only call them |
| Handle a structurally new pillar | New pillars may require new context fields, new resolver logic, and new test scenarios |
| Change ID generation strategy | ID schemes affect downstream idempotency and must be coordinated with consumers |

## Engine Pipeline

```
LeContext
  → Classifier          → tradeFamily, tradeType
  → StateMachineRunner  → transactionStatus, tradeStatus
  → TradeAssembler      → Trade[] (calls LegAssembler per leg rule)
  → TransactionAssembler→ NexusTransaction (standard fields + custom fields)
  → Validator           → valid: nexus.transactions / invalid: nexus.transactions.dlq
```

Each stage receives the output of the previous stage as additional context. The `LeContext` wraps all available pillar data and is immutable throughout the pipeline run.

## Package Structure

```
com.checkout.nexus.transformer
├── engine/
│   ├── config/          # YAML config model (POJOs)
│   ├── context/         # LeContext — pillar wrapper
│   ├── expression/      # ExpressionEvaluator ($field, $when, $resolve)
│   ├── resolver/        # FieldResolver interface + built-in resolvers
│   ├── pipeline/        # Classifier, StateMachineRunner, assemblers
│   └── NexusEngine.java # Entry point: LeContext → NexusTransaction
├── validation/          # NexusValidator + DlqHandler
├── service/             # TransformerService (wires Kafka → NexusEngine)
└── model/               # (unchanged)
```
