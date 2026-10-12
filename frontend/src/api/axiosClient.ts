import axios from 'axios'
import { getAccessToken, keycloak } from '../auth/keycloak'
import { getDeviceId } from '../auth/deviceId'

const apiClient = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' },
})

apiClient.interceptors.request.use(async (config) => {
  config.headers.Authorization = `Bearer ${await getAccessToken()}`
  config.headers['X-Correlation-Id'] = crypto.randomUUID()
  config.headers['X-Device-Id'] = getDeviceId()
  return config
})

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    // The Keycloak session is gone (expired, revoked, user disabled): sign in again.
    if (error.response?.status === 401) {
      keycloak.login()
    }
    return Promise.reject(error)
  }
)

/** Message from our error response body, if any. */
export function errorMessage(error: unknown, fallback: string): string {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as { message?: string; fieldErrors?: { field: string; message: string }[] } | undefined
    if (data?.fieldErrors?.length) return data.fieldErrors.map((e) => `${e.field}: ${e.message}`).join('; ')
    if (data?.message) return data.message
  }
  return fallback
}

export default apiClient
