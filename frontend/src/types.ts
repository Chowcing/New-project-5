export interface ApiResponse<T> {
  success: boolean
  message: string
  data: T
}

export interface PageResponse<T> {
  records: T[]
  total: number
  page: number
  size: number
  totalPages: number
}

export interface TokenResponse {
  accessToken: string
  refreshToken: string
  expiresInSeconds: number
}

export interface UserProfile {
  id: number
  username: string
  nickname: string
  createdAt: string
}

export interface Category {
  id: number
  name: string
  type: 'EXPENSE' | 'INCOME'
  icon?: string
  color?: string
  sortOrder?: number
}

export interface PaymentMethod {
  id: number
  name: string
  icon?: string
  sortOrder?: number
}

export interface Budget {
  id: number
  month: string
  categoryId?: number
  amount: number
}

export interface TransactionRecord {
  id: number
  type: 'EXPENSE' | 'INCOME'
  itemName: string
  amount: number
  occurredAt: string
  channel: 'ONLINE' | 'OFFLINE'
  onlineApp?: string
  offlinePlace?: string
  paymentMethodId: number
  paymentMethodName: string
  categoryId: number
  categoryName: string
  note?: string
}

export interface TransactionPayload {
  type: 'EXPENSE' | 'INCOME'
  itemName: string
  amount: number
  occurredAt: string
  channel: 'ONLINE' | 'OFFLINE'
  onlineApp?: string
  offlinePlace?: string
  paymentMethodId: number
  categoryId: number
  note?: string
}

export interface CategorySummary {
  categoryId: number
  categoryName: string
  amount: number
}

export interface MonthlyStatistics {
  month: string
  totalExpense: number
  totalIncome: number
  balance: number
  expenseByCategory: CategorySummary[]
  incomeByCategory: CategorySummary[]
}
