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

export interface ImportRowError {
  rowNumber: number
  message: string
}

export interface ImportResult {
  totalRows: number
  importedRows: number
  failedRows: number
  errors: ImportRowError[]
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

export interface TransactionTemplate {
  type: 'EXPENSE' | 'INCOME'
  itemName: string
  amount: number
  channel: 'ONLINE' | 'OFFLINE'
  onlineApp?: string
  offlinePlace?: string
  paymentMethodId: number
  paymentMethodName: string
  categoryId: number
  categoryName: string
  note?: string
  reason: string
  score: number
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
  transactionCount: number
}

export interface DailySummary {
  date: string
  totalExpense: number
  totalIncome: number
  balance: number
  transactionCount: number
}

export interface ChannelSummary {
  channel: 'ONLINE' | 'OFFLINE'
  amount: number
  transactionCount: number
}

export interface PaymentMethodSummary {
  paymentMethodId: number
  paymentMethodName: string
  amount: number
  transactionCount: number
}

export interface MonthlyStatistics {
  month: string
  totalExpense: number
  totalIncome: number
  balance: number
  transactionCount: number
  expenseCount: number
  incomeCount: number
  dailyTrend: DailySummary[]
  expenseByCategory: CategorySummary[]
  incomeByCategory: CategorySummary[]
  expenseByChannel: ChannelSummary[]
  expenseByPaymentMethod: PaymentMethodSummary[]
}
