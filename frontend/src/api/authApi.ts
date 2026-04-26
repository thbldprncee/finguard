import apiClient from './axiosClient'
import type { AuthResponse } from '../types'

export const authApi = {
  login: (email: string, password: string) =>
    apiClient.post<AuthResponse>('/auth/login', { email, password }).then((r) => r.data),

  register: (email: string, password: string, role?: string) =>
    apiClient.post<AuthResponse>('/auth/register', { email, password, role }).then((r) => r.data),
}
