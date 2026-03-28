# Nexus UI

React frontend for the Nexus POC. Provides real-time visibility into the transformation pipeline: dashboard, config editor, test bench, live event stream, and DLQ viewer.

## Pages

| Route | Page | Description |
|-------|------|-------------|
| `/` | Dashboard | Engine status, active config version, DLQ count, event throughput |
| `/config` | Config Editor | Structured + raw YAML editing per config section |
| `/test-bench` | Test Bench | Paste LE JSON → run through engine → see Nexus output side-by-side |
| `/live` | Live Stream | Real-time Kafka event stream via WebSocket, transaction trace inspector |
| `/dlq` | DLQ Viewer | Failed events, error details, replay button |

## Tech Stack

| Library | Purpose |
|---------|---------|
| React 18 + TypeScript | UI framework |
| Vite | Build tooling + dev server |
| Tailwind CSS | Styling |
| TanStack Query | Data fetching + cache |
| Monaco Editor | YAML/JSON editing |
| React Flow | Visual state machine editor |
| React Router | Client-side routing |

## Development

```bash
npm install
npm run dev
# Runs on http://localhost:5173
# Expects nexus-api on http://localhost:8083
```

## Build

```bash
npm run build
# Output in dist/
```

## Project Structure

```
src/
├── api/client.ts          # API client (base URL, fetch wrapper)
├── App.tsx                # Router + layout
├── components/
│   ├── config/            # Config editor tab components
│   ├── Layout.tsx         # Shell layout (nav, sidebar)
│   ├── StatusBadge.tsx    # Coloured status badges
│   ├── Toast.tsx          # Notification toasts
│   └── TransactionTrace.tsx # Transaction lifecycle visualiser
├── hooks/
│   ├── useWebSocket.ts    # WebSocket connection hook
│   └── useManualWebSocket.ts
├── pages/
│   ├── Dashboard.tsx
│   ├── ConfigEditor.tsx
│   ├── TestBench.tsx
│   ├── LiveScreen.tsx
│   └── DlqPage.tsx
└── main.tsx               # Entry point
```
