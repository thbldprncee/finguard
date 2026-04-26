import { Navigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import type { UserRole } from '../types'

interface Props {
  children: React.ReactNode
  roles?: UserRole[]
}

export default function ProtectedRoute({ children, roles }: Props) {
  const { isAuthenticated, hasRole } = useAuth()

  if (!isAuthenticated) return <Navigate to="/login" replace />
  if (roles && !hasRole(...roles)) return <Navigate to="/dashboard" replace />

  return <>{children}</>
}
