import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { AuthProvider } from './context/AuthContext'
import ProtectedRoute from './components/ProtectedRoute'
import DashboardPage from './pages/DashboardPage'
import TransactionsPage from './pages/TransactionsPage'
import FraudAlertsPage from './pages/FraudAlertsPage'
import AuditPage from './pages/AuditPage'

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/transactions" element={<TransactionsPage />} />
          <Route path="/alerts" element={
            <ProtectedRoute roles={['ANALYST', 'ADMIN']}><FraudAlertsPage /></ProtectedRoute>
          } />
          <Route path="/audit" element={
            <ProtectedRoute roles={['ANALYST', 'ADMIN']}><AuditPage /></ProtectedRoute>
          } />
          <Route path="*" element={<Navigate to="/dashboard" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  )
}
