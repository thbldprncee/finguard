export interface AuthResponse {
  token: string
  tokenType: string
  userId: string
  email: string
  role: string
  expiresIn: number
}

export interface Transaction {
  id: string
  externalTransactionId: string
  userId: string
  sourceAccount: string
  targetAccount: string
  amount: number
  currency: string
  country: string
  merchantCategory: string | null
  paymentMethod: string
  ipAddress: string | null
  deviceId: string | null
  status: 'PENDING' | 'APPROVED' | 'REVIEW_REQUIRED' | 'BLOCKED'
  createdAt: string
  updatedAt: string
}

export interface FraudAnalysis {
  id: string
  transactionId: string
  riskScore: number
  decision: 'APPROVED' | 'REVIEW_REQUIRED' | 'BLOCKED'
  triggeredRules: string[]
  explanation: string
  analyzedAt: string
}

export interface FraudRule {
  id: string
  code: string
  name: string
  description: string
  enabled: boolean
  weight: number
  updatedAt: string
}

export interface AuditEvent {
  id: string
  eventType: string
  aggregateId: string
  payload: Record<string, unknown>
  correlationId: string
  createdAt: string
}

export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface CreateTransactionRequest {
  sourceAccount: string
  targetAccount: string
  amount: number
  currency: string
  country: string
  merchantCategory?: string
  paymentMethod: string
  ipAddress?: string
  deviceId?: string
}

export type UserRole = 'USER' | 'ANALYST' | 'ADMIN'
