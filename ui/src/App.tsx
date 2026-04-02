import { Navigate, Routes, Route } from 'react-router-dom'
import Layout from './components/Layout'
import Dashboard from './pages/Dashboard'
import DlqPage from './pages/DlqPage'
import ConfigEditor from './pages/ConfigEditor'
import TestBench from './pages/TestBench'
import LiveScreen from './pages/LiveScreen'
import Rules from './pages/Rules'
import ChartOfAccounts from './pages/ChartOfAccounts'
import PostingErrors from './pages/PostingErrors'
import SchemaExplorer from './pages/learn/SchemaExplorer'
import ExamplesPage from './pages/learn/ExamplesPage'
import LEvsNexus from './pages/learn/LEvsNexus'

function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        {/* Root redirect */}
        <Route path="/" element={<Navigate to="/nexus" replace />} />

        {/* Knowledge */}
        <Route path="/knowledge" element={<Navigate to="/knowledge/schema" replace />} />
        <Route path="/knowledge/schema" element={<SchemaExplorer />} />
        <Route path="/knowledge/examples" element={<ExamplesPage />} />
        <Route path="/knowledge/le-vs-nexus" element={<LEvsNexus />} />

        {/* Nexus POC */}
        <Route path="/nexus" element={<Dashboard />} />
        <Route path="/nexus/config" element={<ConfigEditor />} />
        <Route path="/nexus/test-bench" element={<TestBench />} />
        <Route path="/nexus/live" element={<LiveScreen />} />
        <Route path="/nexus/dlq" element={<DlqPage />} />

        {/* Rules Engine POC */}
        <Route path="/rules-engine" element={<Navigate to="/rules-engine/accounts" replace />} />
        <Route path="/rules-engine/accounts" element={<ChartOfAccounts />} />
        <Route path="/rules-engine/rules" element={<Rules />} />
        <Route path="/rules-engine/errors" element={<PostingErrors />} />
      </Route>
    </Routes>
  )
}

export default App
