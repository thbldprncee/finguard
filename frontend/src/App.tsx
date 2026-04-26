import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { AuthProvider } from './context/AuthContext'
import ProtectedRoute from './components/ProtectedRoute'
import LoginPage from './pages/LoginPage'
import DashboardPage from './pages/DashboardPage'
import TransactionsPage from './pages/TransactionsPage'
import FraudAlertsPage from './pages/FraudAlertsPage'
import AuditPage from './pages/AuditPage'

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/" element={<Navigate to="/dashboard" replace />} />
          <Route path="/dashboard" element={
            <ProtectedRoute><DashboardPage /></ProtectedRoute>
          } />
          <Route path="/transactions" element={
            <ProtectedRoute><TransactionsPage /></ProtectedRoute>
          } />
          <Route path="/alerts" element={
            <ProtectedRoute roles={['ANALYST', 'ADMIN']}><FraudAlertsPage /></ProtectedRoute>
          } />
          <Route path="/audit" element={
            <ProtectedRoute roles={['ANALYST', 'ADMIN']}><AuditPage /></ProtectedRoute>
          } />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  )
}
