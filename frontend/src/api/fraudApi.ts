import apiClient from './axiosClient'
import type { FraudAnalysis, FraudRule, PageResponse } from '../types'

export const fraudApi = {
  getAlerts: (page = 0, size = 20) =>
    apiClient.get<PageResponse<FraudAnalysis>>('/fraud/alerts', {
      params: { page, size, sort: 'analyzedAt,desc' },
    }).then((r) => r.data),

  getAnalysis: (transactionId: string) =>
    apiClient.get<FraudAnalysis>(`/fraud/analysis/${transactionId}`).then((r) => r.data),

  getRules: () =>
    apiClient.get<FraudRule[]>('/fraud/rules').then((r) => r.data),

  updateRule: (id: string, data: { enabled?: boolean; weight?: number }) =>
    apiClient.patch<FraudRule>(`/fraud/rules/${id}`, data).then((r) => r.data),
}
