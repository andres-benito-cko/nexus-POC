export interface LearnExample {
  id: string
  title: string
  subtitle: string
  lePayload: unknown
}

export const LEARN_EXAMPLES: LearnExample[] = [
  {
    id: 'simple-capture',
    title: 'Simple Capture',
    subtitle: 'GW-only → NOT_LIVE, FUNDING leg predicted',
    lePayload: {
      id: 'le-001',
      actionId: 'act_cap_001',
      actionRootId: 'pay_001',
      transactionVersion: 1,
      gatewayEvents: [
        {
          eventType: 'payment_captured',
          processedOn: '2026-03-20T14:30:00Z',
          amount: { value: 100.0, currencyCode: 'EUR' },
          acquirerName: 'CKO_UK_LTD',
          acquirerCountry: 'GB',
        },
      ],
      balancesChangedEvents: [],
      cosEvents: [],
      schemeSettlementEvents: [],
      cashEvents: [],
    },
  },
  {
    id: 'settled-capture',
    title: 'Settled Capture',
    subtitle: 'GW + SD → LIVE/SETTLED, SCHEME_SETTLEMENT + FUNDING legs',
    lePayload: {
      id: 'le-002',
      actionId: 'act_cap_002',
      actionRootId: 'pay_002',
      transactionVersion: 2,
      gatewayEvents: [
        {
          eventType: 'payment_captured',
          processedOn: '2026-03-18T10:15:00Z',
          amount: { value: 200.0, currencyCode: 'EUR' },
          acquirerName: 'CKO_UK_LTD',
          acquirerCountry: 'GB',
        },
      ],
      balancesChangedEvents: [],
      cosEvents: [],
      schemeSettlementEvents: [
        {
          payload: {
            settlementAmount: {
              money: { value: 200.0, currencyCode: 'EUR' },
              sign: 'CREDIT',
            },
            fees: [
              { type: 'INTERCHANGE_FEE', roundedAmount: 0.55, currencyCode: 'EUR', sign: 'DEBIT' },
              { type: 'SCHEME_FEE', roundedAmount: 0.12, currencyCode: 'EUR', sign: 'DEBIT' },
            ],
          },
          metadata: {
            scheme: 'VISA',
            transactionType: 'Capture',
            clientId: 'cli_acme_corp',
            settlementCompanyCkoLegalEntityCode: 'CKO_UK_LTD',
            acquirerCompanyCkoLegalEntityCode: 'CKO_UK_LTD',
            settlementServiceName: 'ISS',
            settlementCountryCode: 'GB',
            centralProcessingDate: '2026-03-19',
            expectedValueDate: '2026-03-21',
          },
        },
      ],
      cashEvents: [],
    },
  },
  {
    id: 'cross-currency',
    title: 'Cross-Currency Capture',
    subtitle: 'USD captured, EUR settled — currencies differ between GW and SD',
    lePayload: {
      id: 'le-003',
      actionId: 'act_cap_003',
      actionRootId: 'pay_003',
      transactionVersion: 2,
      gatewayEvents: [
        {
          eventType: 'payment_captured',
          processedOn: '2026-03-19T09:00:00Z',
          amount: { value: 100.0, currencyCode: 'USD' },
          acquirerName: 'CKO_UK_LTD',
          acquirerCountry: 'GB',
        },
      ],
      balancesChangedEvents: [],
      cosEvents: [],
      schemeSettlementEvents: [
        {
          payload: {
            settlementAmount: {
              money: { value: 92.0, currencyCode: 'EUR' },
              sign: 'CREDIT',
            },
            fees: [
              { type: 'INTERCHANGE_FEE', roundedAmount: 0.46, currencyCode: 'EUR', sign: 'DEBIT' },
            ],
          },
          metadata: {
            scheme: 'MASTERCARD',
            transactionType: 'Capture',
            clientId: 'cli_acme_corp',
            settlementCompanyCkoLegalEntityCode: 'CKO_UK_LTD',
            acquirerCompanyCkoLegalEntityCode: 'CKO_UK_LTD',
            expectedValueDate: '2026-03-21',
          },
        },
      ],
      cashEvents: [],
    },
  },
  {
    id: 'cos-stage',
    title: 'Capture with COS Fees',
    subtitle: 'GW + COS → NOT_LIVE, predicted fees visible before settlement',
    lePayload: {
      id: 'le-004',
      actionId: 'act_cap_004',
      actionRootId: 'pay_004',
      transactionVersion: 2,
      gatewayEvents: [
        {
          eventType: 'payment_captured',
          processedOn: '2026-03-20T11:00:00Z',
          amount: { value: 500.0, currencyCode: 'GBP' },
          acquirerName: 'CKO_UK_LTD',
          acquirerCountry: 'GB',
        },
      ],
      balancesChangedEvents: [],
      cosEvents: [
        {
          payload: {
            fee: { value: 1.25, currencyCode: 'GBP' },
            isPredicted: true,
            feeType: 'FEE_TYPE_INTERCHANGE',
            direction: 'DEBIT',
          },
          metadata: {
            acquirerName: 'CKO_UK_LTD',
            acquirerCountry: 'GB',
            acquirerCompanyCkoLegalEntityCode: 'CKO_UK_LTD',
            reconciliationReference: 'RECON-004',
          },
        },
      ],
      schemeSettlementEvents: [],
      cashEvents: [],
    },
  },
  {
    id: 'refund',
    title: 'Refund',
    subtitle: 'payment_refunded → ACQUIRING/REFUND, reversed fund flow',
    lePayload: {
      id: 'le-005',
      actionId: 'act_ref_001',
      actionRootId: 'pay_001',
      transactionVersion: 1,
      gatewayEvents: [
        {
          eventType: 'payment_refunded',
          processedOn: '2026-03-21T10:00:00Z',
          amount: { value: 50.0, currencyCode: 'EUR' },
          acquirerName: 'CKO_UK_LTD',
          acquirerCountry: 'GB',
        },
      ],
      balancesChangedEvents: [],
      cosEvents: [],
      schemeSettlementEvents: [],
      cashEvents: [],
    },
  },
  {
    id: 'chargeback',
    title: 'Chargeback',
    subtitle: 'SD Chargeback → ACQUIRING/CHARGEBACK/LIVE/SETTLED',
    lePayload: {
      id: 'le-006',
      actionId: 'act_cb_001',
      actionRootId: 'pay_002',
      transactionVersion: 1,
      gatewayEvents: [],
      balancesChangedEvents: [],
      cosEvents: [],
      schemeSettlementEvents: [
        {
          payload: {
            settlementAmount: {
              money: { value: 200.0, currencyCode: 'EUR' },
              sign: 'DEBIT',
            },
            fees: [],
          },
          metadata: {
            scheme: 'VISA',
            transactionType: 'Chargeback',
            clientId: 'cli_acme_corp',
            settlementCompanyCkoLegalEntityCode: 'CKO_UK_LTD',
            acquirerCompanyCkoLegalEntityCode: 'CKO_UK_LTD',
          },
        },
      ],
      cashEvents: [],
    },
  },
  {
    id: 'payout',
    title: 'Payout',
    subtitle: 'FIAPI CARD_PAYOUT → PAYOUT/CREDIT/LIVE/INITIATED',
    lePayload: {
      id: 'le-007',
      actionId: 'act_pay_001',
      actionRootId: 'act_pay_001',
      transactionVersion: 1,
      gatewayEvents: [],
      balancesChangedEvents: [
        {
          metadata: {
            clientId: 'cli_acme_corp',
            settlementCompanyCkoLegalEntityCode: 'CKO_UK_LTD',
            acquirerCompanyCkoLegalEntityCode: 'CKO_UK_LTD',
            actionType: 'CARD_PAYOUT',
            valueDate: '2026-03-22',
          },
          actions: [
            {
              changes: {
                fundingAmount: { value: 250.0, currencyCode: 'GBP' },
              },
            },
          ],
        },
      ],
      cosEvents: [],
      schemeSettlementEvents: [],
      cashEvents: [],
    },
  },
  {
    id: 'topup',
    title: 'Top-up',
    subtitle: 'FIAPI TOP_UP → TOPUP/CREDIT/LIVE/INITIATED, funds flow client→CKO',
    lePayload: {
      id: 'le-008',
      actionId: 'act_top_001',
      actionRootId: 'act_top_001',
      transactionVersion: 1,
      gatewayEvents: [],
      balancesChangedEvents: [
        {
          metadata: {
            clientId: 'cli_acme_corp',
            settlementCompanyCkoLegalEntityCode: 'CKO_UK_LTD',
            acquirerCompanyCkoLegalEntityCode: 'CKO_UK_LTD',
            actionType: 'TOP_UP',
            valueDate: '2026-03-22',
          },
          actions: [
            {
              changes: {
                fundingAmount: { value: 1000.0, currencyCode: 'EUR' },
              },
            },
          ],
        },
      ],
      cosEvents: [],
      schemeSettlementEvents: [],
      cashEvents: [],
    },
  },
  {
    id: 'cash-settlement',
    title: 'Cash Settlement',
    subtitle: 'CASH event only → CASH/SETTLEMENT/LIVE/SETTLED',
    lePayload: {
      id: 'le-009',
      actionId: 'act_cash_001',
      actionRootId: 'act_cash_001',
      transactionVersion: 1,
      gatewayEvents: [],
      balancesChangedEvents: [],
      cosEvents: [],
      schemeSettlementEvents: [],
      cashEvents: [
        {
          standardPayload: {
            amount: { value: 5000.0, currencyCode: 'EUR' },
            direction: 'credit',
          },
          standardMetadata: {
            scheme: 'SWIFT',
            actionType: 'SETTLEMENT',
            entityId: 'cli_acme_corp',
            legalEntity: 'CKO_UK_LTD',
            valueDate: '2026-03-22',
            payoutId: 'payout-batch-001',
          },
        },
      ],
    },
  },
]
