export interface ApiResponse<T> {
  success: boolean
  message: string
  data: T
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

export interface Account {
  id: number
  name: string
  type: string
  balance: number
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
  amount: number
  occurredAt: string
  categoryId: number
  categoryName: string
  accountId: number
  accountName: string
  note?: string
}

export interface TransactionPayload {
  type: 'EXPENSE' | 'INCOME'
  amount: number
  occurredAt: string
  categoryId: number
  accountId: number
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

