import schemaRaw from '../../../../schema/nexus.schema.json'

interface TradeRule {
  family: string
  type: string
  statuses: string[]
}

interface SchemaWithMatrix {
  $defs?: {
    Transaction?: {
      'x-trade-type-matrix'?: { rules: TradeRule[] }
    }
  }
}

const schema = schemaRaw as unknown as SchemaWithMatrix
const rules: TradeRule[] = schema.$defs?.Transaction?.['x-trade-type-matrix']?.rules ?? []

// Metadata fields and which families they apply to (from original schema x-relevant-families)
const METADATA_BY_FAMILY: Record<string, string[]> = {
  scheme_code:               ['ACQUIRING', 'ISSUING', 'APM', 'CASH'],
  payment_method:            ['ACQUIRING', 'ISSUING', 'APM'],
  acquirer_name:             ['ACQUIRING', 'ISSUING', 'APM'],
  acquirer_country:          ['ACQUIRING', 'ISSUING', 'APM'],
  cash_batch_id:             ['ACQUIRING', 'ISSUING', 'APM'],
  client_settlement_type:    ['ACQUIRING'],
  merchant_category_code:    ['ACQUIRING', 'ISSUING'],
  source_event_type:         ['ACQUIRING', 'ISSUING', 'APM'],
  invoice_number:            ['ACQUIRING', 'ISSUING', 'GLOBAL_PRICING', 'CASH'],
  settlement_service_name:   ['ACQUIRING'],
  settlement_country_code:   ['ACQUIRING'],
  central_processing_date:   ['ACQUIRING'],
  external_id:               ['ACQUIRING'],
  enhanced_action_type:      ['ACQUIRING', 'ISSUING'],
  reconciliation_reference:  ['ACQUIRING'],
  scheme_partner_identifier: ['ACQUIRING'],
  payout_id:                 ['PAYOUT', 'CASH'],
  billing_descriptor:        ['PAYOUT', 'CASH'],
  is_net_settled:            ['ACQUIRING'],
}

// Derive metadata fields per family
const metadataForFamily = (family: string): string[] =>
  Object.entries(METADATA_BY_FAMILY)
    .filter(([, families]) => families.includes(family))
    .map(([field]) => field)

const FAMILY_STYLES: Record<string, { header: string; badge: string; statusBadge: string }> = {
  ACQUIRING:      { header: 'bg-blue-50 border-blue-200',   badge: 'bg-blue-100 text-blue-700',    statusBadge: 'bg-blue-50 text-blue-600 border border-blue-200' },
  ISSUING:        { header: 'bg-purple-50 border-purple-200', badge: 'bg-purple-100 text-purple-700', statusBadge: 'bg-purple-50 text-purple-600 border border-purple-200' },
  PAYOUT:         { header: 'bg-emerald-50 border-emerald-200', badge: 'bg-emerald-100 text-emerald-700', statusBadge: 'bg-emerald-50 text-emerald-600 border border-emerald-200' },
  TOPUP:          { header: 'bg-amber-50 border-amber-200',  badge: 'bg-amber-100 text-amber-700',  statusBadge: 'bg-amber-50 text-amber-600 border border-amber-200' },
  FX:             { header: 'bg-pink-50 border-pink-200',    badge: 'bg-pink-100 text-pink-700',    statusBadge: 'bg-pink-50 text-pink-600 border border-pink-200' },
  APM:            { header: 'bg-indigo-50 border-indigo-200', badge: 'bg-indigo-100 text-indigo-700', statusBadge: 'bg-indigo-50 text-indigo-600 border border-indigo-200' },
  TRANSFER:       { header: 'bg-orange-50 border-orange-200', badge: 'bg-orange-100 text-orange-700', statusBadge: 'bg-orange-50 text-orange-600 border border-orange-200' },
  GLOBAL_PRICING: { header: 'bg-teal-50 border-teal-200',   badge: 'bg-teal-100 text-teal-700',    statusBadge: 'bg-teal-50 text-teal-600 border border-teal-200' },
  CASH:           { header: 'bg-lime-50 border-lime-200',    badge: 'bg-lime-100 text-lime-700',    statusBadge: 'bg-lime-50 text-lime-600 border border-lime-200' },
}

const DEFAULT_STYLE = { header: 'bg-zinc-50 border-zinc-200', badge: 'bg-zinc-100 text-zinc-700', statusBadge: 'bg-zinc-50 text-zinc-600 border border-zinc-200' }

const families = [...new Set(rules.map((r) => r.family))]

export function ProductMatrixGrid() {
  return (
    <div className="space-y-4">
      <div>
        <h3 className="text-base font-semibold text-zinc-900">Product Matrix</h3>
        <p className="text-sm text-zinc-500 mt-0.5">
          Valid transaction types, statuses, and available metadata fields per product family.
        </p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 xl:grid-cols-3 gap-4">
        {families.map((family) => {
          const style = FAMILY_STYLES[family] ?? DEFAULT_STYLE
          const familyRules = rules.filter((r) => r.family === family)
          const metaFields = metadataForFamily(family)

          return (
            <div key={family} className="border border-zinc-200 rounded-xl overflow-hidden">
              {/* Header */}
              <div className={`px-4 py-3 border-b ${style.header}`}>
                <span className={`inline-block px-2.5 py-0.5 rounded-full text-xs font-semibold ${style.badge}`}>
                  {family}
                </span>
              </div>

              {/* Transaction types table */}
              <div className="bg-white">
                <table className="w-full">
                  <thead>
                    <tr className="border-b border-zinc-100">
                      <th className="px-4 py-2 text-left text-[10px] font-semibold text-zinc-400 uppercase tracking-wider w-1/3">
                        Type
                      </th>
                      <th className="px-4 py-2 text-left text-[10px] font-semibold text-zinc-400 uppercase tracking-wider">
                        Valid Statuses
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {familyRules.map((rule) => (
                      <tr key={rule.type} className="border-b border-zinc-50">
                        <td className="px-4 py-2.5 font-mono text-xs text-zinc-800 align-top">
                          {rule.type}
                        </td>
                        <td className="px-4 py-2.5 align-top">
                          <div className="flex flex-wrap gap-1">
                            {rule.statuses.map((s) => (
                              <span
                                key={s}
                                className={`inline-block px-1.5 py-0.5 rounded text-[10px] font-medium ${style.statusBadge}`}
                              >
                                {s}
                              </span>
                            ))}
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              {/* Metadata fields */}
              {metaFields.length > 0 && (
                <div className="px-4 py-3 bg-zinc-50 border-t border-zinc-100">
                  <p className="text-[10px] font-semibold text-zinc-400 uppercase tracking-wider mb-2">
                    Metadata Fields
                  </p>
                  <div className="flex flex-wrap gap-1">
                    {metaFields.map((field) => (
                      <span
                        key={field}
                        className="inline-block px-2 py-0.5 rounded bg-white border border-zinc-200 font-mono text-[10px] text-zinc-600"
                      >
                        {field}
                      </span>
                    ))}
                  </div>
                </div>
              )}
            </div>
          )
        })}
      </div>
    </div>
  )
}

export default function ProductMatrix() {
  return (
    <div className="py-6">
      <ProductMatrixGrid />
    </div>
  )
}
