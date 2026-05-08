import { http } from './http'
import type {
  Budget,
  Category,
  ImportResult,
  MonthlyStatistics,
  PageResponse,
  PaymentMethod,
  TokenResponse,
  TransactionPayload,
  TransactionRecord,
  UserProfile
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
  categoryId?: number
  paymentMethodId?: number
  keyword?: string
  page?: number
  size?: number
}

export const transactionApi = {
  list: (params?: TransactionQuery) => http.get<unknown, PageResponse<TransactionRecord>>('/transactions', { params }),
  get: (id: number) => http.get<unknown, TransactionRecord>(`/transactions/${id}`),
  create: (payload: TransactionPayload) => http.post<unknown, TransactionRecord>('/transactions', payload),
  update: (id: number, payload: TransactionPayload) => http.put<unknown, TransactionRecord>(`/transactions/${id}`, payload),
  remove: (id: number) => http.delete<unknown, void>(`/transactions/${id}`)
}

export const statisticsApi = {
  monthly: (month: string) => http.get<unknown, MonthlyStatistics>('/statistics/monthly', { params: { month } })
}

export const exportApi = {
  transactionsCsv: (params?: TransactionQuery) =>
    http.get<unknown, Blob>('/exports/transactions.csv', { params, responseType: 'blob' })
}

export const importApi = {
  transactionsCsv: (file: File) => {
    const formData = new FormData()
    formData.append('file', file)
    return http.post<unknown, ImportResult>('/imports/transactions.csv', formData)
  }
}
