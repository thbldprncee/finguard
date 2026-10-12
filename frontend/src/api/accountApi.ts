import apiClient from './axiosClient'
import type { Account } from '../types'

export const accountApi = {
  list: () => apiClient.get<Account[]>('/accounts').then((r) => r.data),
  open: (currency: string) => apiClient.post<Account>('/accounts', { currency }).then((r) => r.data),
}
