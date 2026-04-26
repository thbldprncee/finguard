import React, { createContext, useContext, useState, useCallback } from 'react'
import type { AuthResponse, UserRole } from '../types'

interface AuthUser {
  userId: string
  email: string
  role: UserRole
  token: string
}

interface AuthContextValue {
  user: AuthUser | null
  login: (response: AuthResponse) => void
  logout: () => void
  isAuthenticated: boolean
  hasRole: (...roles: UserRole[]) => boolean
}

const AuthContext = createContext<AuthContextValue | null>(null)

function loadUser(): AuthUser | null {
  try {
    const raw = localStorage.getItem('finguard_user')
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(loadUser)

  const login = useCallback((response: AuthResponse) => {
    const authUser: AuthUser = {
      userId: response.userId,
      email: response.email,
      role: response.role as UserRole,
      token: response.token,
    }
    localStorage.setItem('finguard_token', response.token)
    localStorage.setItem('finguard_user', JSON.stringify(authUser))
    setUser(authUser)
  }, [])

  const logout = useCallback(() => {
    localStorage.removeItem('finguard_token')
    localStorage.removeItem('finguard_user')
    setUser(null)
  }, [])

  const hasRole = useCallback((...roles: UserRole[]) => {
    return user ? roles.includes(user.role) : false
  }, [user])

  return (
    <AuthContext.Provider value={{ user, login, logout, isAuthenticated: !!user, hasRole }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider')
  return ctx
}
