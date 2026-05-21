import { http } from './http'
import type {
  Budget,
  Category,
  AdminAuditLog,
  AdminOverview,
  AdminTransaction,
  AdminUser,
  AdminUserDetail,
  ImportJob,
  MonthlyStatistics,
  PageResponse,
  PaymentMethod,
  RecurringRule,
  RecurringRulePayload,
  RecurringRuleRun,
  TransactionDayCardsResponse,
  TransactionDayOption,
  TokenResponse,
  TransactionPayload,
  TransactionRecommendationContext,
  TransactionRecord,
  TransactionTemplate,
  UserProfile,
  YearlyStatistics
} from '@/types'

export const authApi = {
  register: (payload: { username: string; password: string; nickname: string }) =>
    http.post<unknown, TokenResponse>('/auth/register', payload),
  login: (payload: { username: string; password: string }) =>
    http.post<unknown, TokenResponse>('/auth/login', payload),
  refresh: (refreshToken: string) =>
    http.post<unknown, TokenResponse>('/auth/refresh', { refreshToken }),
  logout: (refreshToken: string) =>
    http.post<unknown, void>('/auth/logout', { refreshToken }),
  me: () => http.get<unknown, UserProfile>('/auth/me')
}

export const recurringRuleApi = {
  list: () => http.get<unknown, RecurringRule[]>('/recurring-rules'),
  get: (id: number) => http.get<unknown, RecurringRule>(`/recurring-rules/${id}`),
  create: (payload: RecurringRulePayload) => http.post<unknown, RecurringRule>('/recurring-rules', payload),
  update: (id: number, payload: RecurringRulePayload) => http.put<unknown, RecurringRule>(`/recurring-rules/${id}`, payload),
  remove: (id: number) => http.delete<unknown, void>(`/recurring-rules/${id}`),
  runs: (id: number) => http.get<unknown, RecurringRuleRun[]>(`/recurring-rules/${id}/runs`)
}

export const recurringRunApi = {
  due: (date?: string) => http.get<unknown, RecurringRuleRun[]>('/recurring-runs/due', { params: { date } }),
  generate: (id: number) => http.post<unknown, RecurringRuleRun>(`/recurring-runs/${id}/generate`),
  skip: (id: number) => http.post<unknown, RecurringRuleRun>(`/recurring-runs/${id}/skip`)
}

export const categoryApi = {
  list: (type?: string) => http.get<unknown, Category[]>('/categories', { params: { type } }),
  create: (payload: Omit<Category, 'id'>) => http.post<unknown, Category>('/categories', payload),
  update: (id: number, payload: Omit<Category, 'id'>) => http.put<unknown, Category>(`/categories/${id}`, payload),
  references: (id: number, size = 5) =>
    http.get<unknown, PageResponse<TransactionRecord>>(`/categories/${id}/references`, { params: { size } }),
  remove: (id: number) => http.delete<unknown, void>(`/categories/${id}`)
}

export const paymentMethodApi = {
  list: () => http.get<unknown, PaymentMethod[]>('/payment-methods'),
  create: (payload: Omit<PaymentMethod, 'id'>) => http.post<unknown, PaymentMethod>('/payment-methods', payload),
  update: (id: number, payload: Omit<PaymentMethod, 'id'>) => http.put<unknown, PaymentMethod>(`/payment-methods/${id}`, payload),
  references: (id: number, size = 5) =>
    http.get<unknown, PageResponse<TransactionRecord>>(`/payment-methods/${id}/references`, { params: { size } }),
  remove: (id: number) => http.delete<unknown, void>(`/payment-methods/${id}`)
}

export const budgetApi = {
  list: (month?: string) => http.get<unknown, Budget[]>('/budgets', { params: { month } }),
  create: (payload: Omit<Budget, 'id'>) => http.post<unknown, Budget>('/budgets', payload),
  update: (id: number, payload: Omit<Budget, 'id'>) => http.put<unknown, Budget>(`/budgets/${id}`, payload),
  remove: (id: number) => http.delete<unknown, void>(`/budgets/${id}`)
}

export interface TransactionQuery {
  type?: string
  startDate?: string
  endDate?: string
  channel?: string
  categoryId?: number
  paymentMethodId?: number
  keyword?: string
  page?: number
  size?: number
}

export interface TransactionDayCardsQuery extends TransactionQuery {
  dayPage?: number
  daySize?: number
  recordPage?: number
  recordSize?: number
}

export const transactionApi = {
  list: (params?: TransactionQuery) => http.get<unknown, PageResponse<TransactionRecord>>('/transactions', { params }),
  dailyCards: (params?: TransactionDayCardsQuery) =>
    http.get<unknown, TransactionDayCardsResponse>('/transactions/daily-cards', { params }),
  dailyOptions: (params?: TransactionQuery) =>
    http.get<unknown, TransactionDayOption[]>('/transactions/daily-options', { params }),
  get: (id: number) => http.get<unknown, TransactionRecord>(`/transactions/${id}`),
  recommendations: (limit = 5) =>
    http.get<unknown, TransactionTemplate[]>('/transactions/recommendations', { params: { limit } }),
  contextRecommendations: (params: TransactionRecommendationContext) =>
    http.get<unknown, TransactionTemplate[]>('/transactions/recommendations/context', { params }),
  create: (payload: TransactionPayload) => http.post<unknown, TransactionRecord>('/transactions', payload),
  update: (id: number, payload: TransactionPayload) => http.put<unknown, TransactionRecord>(`/transactions/${id}`, payload),
  remove: (id: number) => http.delete<unknown, void>(`/transactions/${id}`)
}

export const statisticsApi = {
  monthly: (month: string) => http.get<unknown, MonthlyStatistics>('/statistics/monthly', { params: { month } }),
  yearly: (year: string | number) => http.get<unknown, YearlyStatistics>('/statistics/yearly', { params: { year } })
}

export const exportApi = {
  transactionsCsv: (params?: TransactionQuery) =>
    http.get<unknown, Blob>('/exports/transactions.csv', { params, responseType: 'blob' })
}

export const importApi = {
  transactionsCsv: (file: File) => {
    const formData = new FormData()
    formData.append('file', file)
    return http.post<unknown, ImportJob>('/imports/transactions.csv', formData, { timeout: 30000 })
  },
  getJob: (id: number) => http.get<unknown, ImportJob>(`/imports/${id}`)
}

export interface AdminUserQuery {
  keyword?: string
  status?: string
  page?: number
  size?: number
}

export interface AdminTransactionQuery extends TransactionQuery {
  userId?: number
}

export const adminApi = {
  overview: () => http.get<unknown, AdminOverview>('/admin/overview'),
  users: (params?: AdminUserQuery) => http.get<unknown, PageResponse<AdminUser>>('/admin/users', { params }),
  user: (id: number) => http.get<unknown, AdminUserDetail>(`/admin/users/${id}`),
  updateUserStatus: (id: number, payload: { status: 'ACTIVE' | 'DISABLED'; reason?: string }) =>
    http.patch<unknown, AdminUser>(`/admin/users/${id}/status`, payload),
  revokeUserTokens: (id: number, reason: string) =>
    http.post<unknown, void>(`/admin/users/${id}/revoke-tokens`, { reason }),
  transactions: (params?: AdminTransactionQuery) =>
    http.get<unknown, PageResponse<AdminTransaction>>('/admin/transactions', { params }),
  deleteTransaction: (id: number, reason: string) =>
    http.delete<unknown, void>(`/admin/transactions/${id}`, { data: { reason } }),
  auditLogs: (params?: { page?: number; size?: number }) =>
    http.get<unknown, PageResponse<AdminAuditLog>>('/admin/audit-logs', { params })
}
