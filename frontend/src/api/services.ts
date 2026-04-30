import { http } from './http'
import type {
  Account,
  Budget,
  Category,
  MonthlyStatistics,
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
  remove: (id: number) => http.delete<unknown, void>(`/categories/${id}`)
}

export const accountApi = {
  list: () => http.get<unknown, Account[]>('/accounts'),
  create: (payload: Omit<Account, 'id'>) => http.post<unknown, Account>('/accounts', payload),
  update: (id: number, payload: Omit<Account, 'id'>) => http.put<unknown, Account>(`/accounts/${id}`, payload),
  remove: (id: number) => http.delete<unknown, void>(`/accounts/${id}`)
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
  accountId?: number
  keyword?: string
}

export const transactionApi = {
  list: (params?: TransactionQuery) => http.get<unknown, TransactionRecord[]>('/transactions', { params }),
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

