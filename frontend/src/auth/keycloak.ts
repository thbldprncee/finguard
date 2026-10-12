import Keycloak from 'keycloak-js'
import type { UserRole } from '../types'

/**
 * Keycloak is served by the same nginx under /auth, so login, token refresh and the API
 * share one origin: no CORS, and the CSP can stay at connect-src 'self'.
 * Tokens live only in memory (inside this object), never in localStorage, so an XSS bug
 * cannot read a long-lived token from storage.
 */
export const keycloak = new Keycloak({
  url: `${window.location.origin}/auth`,
  realm: 'finguard',
  clientId: 'finguard-frontend',
})

export function initAuth(): Promise<boolean> {
  keycloak.onTokenExpired = () => {
    keycloak.updateToken(30).catch(() => keycloak.login())
  }
  return keycloak.init({
    onLoad: 'login-required',
    pkceMethod: 'S256',
    checkLoginIframe: false,
  })
}

/** A token valid for at least 30 more seconds, refreshing it first if needed. */
export async function getAccessToken(): Promise<string> {
  try {
    await keycloak.updateToken(30)
  } catch {
    await keycloak.login()
  }
  return keycloak.token ?? ''
}

const ROLES_BY_PRIVILEGE: UserRole[] = ['ADMIN', 'ANALYST', 'USER']

export function currentRoles(): UserRole[] {
  const roles = keycloak.tokenParsed?.realm_access?.roles ?? []
  return ROLES_BY_PRIVILEGE.filter((r) => roles.includes(r))
}
