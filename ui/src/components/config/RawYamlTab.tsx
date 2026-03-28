import { lazy, Suspense } from 'react'

const MonacoEditor = lazy(() => import('@monaco-editor/react'))

interface RawYamlTabProps {
  value: string
  onChange: (value: string) => void
}

export default function RawYamlTab({ value, onChange }: RawYamlTabProps) {
  return (
    <div className="h-full">
      <Suspense
        fallback={
          <div className="flex items-center justify-center h-64 text-sm text-zinc-400">
            Loading editor...
          </div>
        }
      >
        <MonacoEditor
          height="100%"
          language="yaml"
          theme="vs-dark"
          value={value}
          onChange={(v) => onChange(v ?? '')}
          options={{
            minimap: { enabled: false },
            fontSize: 13,
            lineNumbers: 'on',
            scrollBeyondLastLine: false,
            wordWrap: 'on',
            tabSize: 2,
            renderWhitespace: 'selection',
            padding: { top: 12, bottom: 12 },
          }}
        />
      </Suspense>
    </div>
  )
}
