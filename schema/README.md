# Nexus Schema (Vendored Copy)

> **Source of truth:** The canonical Nexus schema lives in the
> [Nexus research repo](../Nexus/) under `schema/`.
> This is a point-in-time copy used by the POC services at build/runtime.

## Contents

| File | Purpose |
|------|---------|
| `nexus.schema.json` | JSON Schema (draft-07) defining the Nexus transaction contract |
| `le_nexus_mapping.md` | Field-level mapping from Linking Engine to Nexus |
| `examples/*.json` | 9 worked examples covering all trade families |

## Updating

When the canonical schema changes in the research repo, copy the updated
files here and run the transformer tests to verify compatibility:

```bash
# From Nexus-POC root
cp ../Nexus/schema/nexus.schema.json schema/
cp ../Nexus/schema/le_nexus_mapping.md schema/
cp ../Nexus/schema/examples/*.json schema/examples/
cd nexus-transformer && ./gradlew test
```
