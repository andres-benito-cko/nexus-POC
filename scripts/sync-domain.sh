#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
NEXUS_REPO="${1:-$(dirname "$PROJECT_DIR")/Nexus}"
DOMAIN_DIR="$PROJECT_DIR/ai-generator/src/main/resources/domain"

if [ ! -d "$NEXUS_REPO/schema" ]; then
  echo "ERROR: Nexus repo not found at $NEXUS_REPO"
  echo "Usage: $0 [path-to-nexus-repo]"
  exit 1
fi

echo "Syncing domain knowledge from $NEXUS_REPO..."

rm -rf "$DOMAIN_DIR"
mkdir -p "$DOMAIN_DIR/examples"

cp "$NEXUS_REPO/schema/nexus.schema.json" "$DOMAIN_DIR/nexus.schema.json"
cp "$NEXUS_REPO/schema/le_nexus_mapping.md" "$DOMAIN_DIR/le_nexus_mapping.md"
cp "$NEXUS_REPO/research/le/per_pillar_structures.md" "$DOMAIN_DIR/per_pillar_structures.md"
cp "$NEXUS_REPO/schema/examples/"*.json "$DOMAIN_DIR/examples/"

if [ -f "$PROJECT_DIR/schema/nexus.schema.json" ]; then
  cp "$PROJECT_DIR/schema/nexus.schema.json" "$DOMAIN_DIR/nexus.schema.json"
  cp "$PROJECT_DIR/schema/examples/"*.json "$DOMAIN_DIR/examples/"
fi

python3 -c "
import json, sys

with open('$DOMAIN_DIR/nexus.schema.json') as f:
    schema = json.load(f)

txn_def = schema['\$defs']['Transaction']
matrix = txn_def.get('x-trade-type-matrix', {}).get('rules', [])
leg_rules = txn_def.get('x-leg-composition-rules', {}).get('rules', [])

product_types = txn_def['properties']['product_type']['enum']
transaction_types = txn_def['properties']['transaction_type']['enum']
transaction_statuses = txn_def['properties']['transaction_status']['enum']
leg_types = schema['\$defs']['Leg']['properties']['leg_type']['enum']
fee_types = schema['\$defs']['Fee']['properties']['fee_type']['enum']
party_types = schema['\$defs']['Party']['properties']['party_type']['enum']

result = {
    'valid_combinations': matrix,
    'leg_composition_rules': leg_rules,
    'enums': {
        'product_type': product_types,
        'transaction_type': transaction_types,
        'transaction_status': transaction_statuses,
        'leg_type': leg_types,
        'fee_type': fee_types,
        'party_type': party_types,
        'fee_status': ['PREDICTED', 'ACTUAL'],
        'leg_status': ['PREDICTED', 'ACTUAL'],
        'block_status': ['NOT_LIVE', 'LIVE', 'DEAD'],
        'fee_amount_type': ['FIXED', 'VARIABLE'],
        'client_settlement_type': ['Gross', 'Net']
    }
}

with open('$DOMAIN_DIR/valid_combinations.json', 'w') as f:
    json.dump(result, f, indent=2)

print(f'Generated valid_combinations.json with {len(matrix)} type combinations')
"

echo "Domain sync complete. Files in $DOMAIN_DIR:"
ls -la "$DOMAIN_DIR"
ls -la "$DOMAIN_DIR/examples/"
