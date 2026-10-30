import apiClient from './axiosClient'
import type { FraudAnalysis, FraudRule, PageResponse } from '../types'

export const fraudApi = {
  getAlerts: (page = 0, size = 20) =>
    apiClient.get<PageResponse<FraudAnalysis>>('/fraud/alerts', {
      params: { page, size, sort: 'analyzedAt,desc' },
    }).then((r) => r.data),

  getAnalysis: (transactionId: string) =>
    apiClient.get<FraudAnalysis>(`/fraud/analysis/${transactionId}`).then((r) => r.data),

  /** Analyst verdict on a transaction; the ML model is trained on these labels. */
  label: (transactionId: string, fraud: boolean) =>
    apiClient.put(`/fraud/analysis/${transactionId}/label`, { fraud }).then((r) => r.data),

  getRules: () =>
    apiClient.get<FraudRule[]>('/fraud/rules').then((r) => r.data),

  /**
   * `version` is the rule version the admin was looking at. If someone changed the rule since,
   * the API answers 412 Precondition Failed: reload the rules and let the admin decide again.
   */
  updateRule: (id: string, version: number, data: { enabled?: boolean; weight?: number }) =>
    apiClient.patch<FraudRule>(`/fraud/rules/${id}`, data, {
      headers: { 'If-Match': `"${version}"` },
    }).then((r) => r.data),
}
