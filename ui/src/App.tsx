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
import LearnLayout from './pages/learn/LearnLayout'
import SchemaExplorer from './pages/learn/SchemaExplorer'
import ExamplesPage from './pages/learn/ExamplesPage'
import LEvsNexus from './pages/learn/LEvsNexus'

function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route path="/" element={<Dashboard />} />
        <Route path="/dlq" element={<DlqPage />} />
        <Route path="/config" element={<ConfigEditor />} />
        <Route path="/test-bench" element={<TestBench />} />
        <Route path="/live" element={<LiveScreen />} />
        <Route path="/rules" element={<Rules />} />
        <Route path="/accounts" element={<ChartOfAccounts />} />
        <Route path="/posting-errors" element={<PostingErrors />} />
        <Route element={<LearnLayout />}>
          <Route path="/learn" element={<Navigate to="/learn/schema" replace />} />
          <Route path="/learn/schema" element={<SchemaExplorer />} />
<Route path="/learn/examples" element={<ExamplesPage />} />
          <Route path="/learn/le-vs-nexus" element={<LEvsNexus />} />
        </Route>
      </Route>
    </Routes>
  )
}

export default App
