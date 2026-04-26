import apiClient from './axiosClient'
import type { CreateTransactionRequest, PageResponse, Transaction } from '../types'

export const transactionApi = {
  create: (data: CreateTransactionRequest, idempotencyKey?: string) =>
    apiClient.post<Transaction>('/transactions', data, {
      headers: idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : {},
    }).then((r) => r.data),

  list: (page = 0, size = 20) =>
    apiClient.get<PageResponse<Transaction>>('/transactions', {
      params: { page, size, sort: 'createdAt,desc' },
    }).then((r) => r.data),

  getById: (id: string) =>
    apiClient.get<Transaction>(`/transactions/${id}`).then((r) => r.data),
}
