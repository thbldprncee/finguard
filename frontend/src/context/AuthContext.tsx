import React, { createContext, useCallback, useContext, useMemo } from 'react'
import type { UserRole } from '../types'
import { currentRoles, keycloak } from '../auth/keycloak'

interface AuthUser {
  userId: string
  email: string
  role: UserRole
  roles: UserRole[]
}

interface AuthContextValue {
  user: AuthUser
  logout: () => void
  hasRole: (...roles: UserRole[]) => boolean
}

const AuthContext = createContext<AuthContextValue | null>(null)

/** Rendered only after keycloak.init() succeeded (see main.tsx), so a user always exists. */
export function AuthProvider({ children }: { children: React.ReactNode }) {
  const user = useMemo<AuthUser>(() => {
    const roles = currentRoles()
    return {
      userId: keycloak.subject ?? '',
      email: (keycloak.tokenParsed?.email as string | undefined) ?? '',
      role: roles[0] ?? 'USER',
      roles,
    }
  }, [])

  const logout = useCallback(() => {
    keycloak.logout({ redirectUri: window.location.origin })
  }, [])

  const hasRole = useCallback((...roles: UserRole[]) => roles.some((r) => user.roles.includes(r)), [user])

  return <AuthContext.Provider value={{ user, logout, hasRole }}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider')
  return ctx
}
