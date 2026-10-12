import { Navigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import type { UserRole } from '../types'

interface Props {
  children: React.ReactNode
  roles?: UserRole[]
}

/** UI convenience only: every API call is authorised again by the backend. */
export default function ProtectedRoute({ children, roles }: Props) {
  const { hasRole } = useAuth()
  if (roles && !hasRole(...roles)) return <Navigate to="/dashboard" replace />
  return <>{children}</>
}
