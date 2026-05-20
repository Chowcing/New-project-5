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
  errorType?: ImportErrorType
  message: string
  type?: string
  itemName?: string
  amount?: string
  occurredAt?: string
  channel?: string
  onlineApp?: string
  offlinePlace?: string
  paymentMethodName?: string
  categoryName?: string
  note?: string
}

export interface ImportResult {
  totalRows: number
  importedRows: number
  failedRows: number
  errors: ImportRowError[]
}

export type ImportErrorType =
  | 'PAYMENT_METHOD'
  | 'CATEGORY'
  | 'AMOUNT'
  | 'TIME'
  | 'REQUIRED'
  | 'TYPE'
  | 'CHANNEL'
  | 'ROW_FORMAT'
  | 'DUPLICATE'
  | 'OTHER'

export type ImportJobStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED'

export interface ImportJob {
  id: number
  originalFilename?: string
  status: ImportJobStatus
  totalRows: number
  importedRows: number
  failedRows: number
  result?: ImportResult
  errorMessage?: string
  createdAt?: string
  startedAt?: string
  finishedAt?: string
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

export interface TransactionDayCard {
  date: string
  totalExpense: number
  totalIncome: number
  balance: number
  transactionCount: number
  records: PageResponse<TransactionRecord>
}

export interface TransactionDayOption {
  date: string
  totalExpense: number
  totalIncome: number
  balance: number
  transactionCount: number
}

export interface TransactionDayCardsResponse {
  days: TransactionDayCard[]
  totalDays: number
  totalRecords: number
  dayPage: number
  daySize: number
  totalDayPages: number
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

export type RecurringRuleStatus = 'ACTIVE' | 'PAUSED'
export type RecurringRunStatus = 'PENDING' | 'GENERATED' | 'SKIPPED' | 'CANCELLED' | 'FAILED'

export interface RecurringRule {
  id: number
  name: string
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
  scheduleType: 'MONTHLY' | 'WEEKLY'
  intervalValue: number
  dayOfMonth?: number | null
  weekday?: 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY' | null
  startDate: string
  nextRunDate?: string | null
  endDate?: string | null
  reminderDaysBefore: number
  status: RecurringRuleStatus
}

export interface RecurringRuleRun {
  id: number
  ruleId: number
  ruleName: string
  dueDate: string
  reminderDaysBefore: number
  type: 'EXPENSE' | 'INCOME'
  itemName: string
  amount: number
  channel: 'ONLINE' | 'OFFLINE'
  onlineApp?: string
  offlinePlace?: string
  paymentMethodId: number
  categoryId: number
  note?: string
  status: RecurringRunStatus
  transactionId?: number | null
  errorMessage?: string | null
  processedAt?: string | null
}

export interface RecurringRulePayload {
  name: string
  type: 'EXPENSE' | 'INCOME'
  itemName: string
  amount: number
  channel: 'ONLINE' | 'OFFLINE'
  onlineApp?: string
  offlinePlace?: string
  paymentMethodId: number
  categoryId: number
  note?: string
  scheduleType: 'MONTHLY' | 'WEEKLY'
  intervalValue: number
  dayOfMonth?: number | null
  weekday?: 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY' | null
  startDate: string
  endDate?: string | null
  reminderDaysBefore: number
  status: RecurringRuleStatus
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

export interface MonthlyTrendSummary {
  month: string
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

export interface PeakExpenseSummary {
  period: string
  label: string
  amount: number
  transactionCount: number
}

export interface StatisticsInsight {
  currentPeriod: string
  previousPeriod: string
  previousTotalExpense: number
  previousTotalIncome: number
  previousBalance: number
  expenseChangeAmount: number
  expenseChangePercent: number | null
  incomeChangeAmount: number
  incomeChangePercent: number | null
  balanceChangeAmount: number
  balanceChangePercent: number | null
  averageDailyExpense: number
  averageExpensePerTransaction: number
  peakExpense?: PeakExpenseSummary | null
}

export interface BudgetUsageSummary {
  categoryId?: number | null
  categoryName: string
  budgetAmount: number
  usedAmount: number
  remainingAmount: number
  usagePercent: number
  overBudget: boolean
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
  insight: StatisticsInsight
  monthlyBudget?: BudgetUsageSummary | null
  categoryBudgetUsages: BudgetUsageSummary[]
  dailyTrend: DailySummary[]
  expenseByCategory: CategorySummary[]
  incomeByCategory: CategorySummary[]
  expenseByChannel: ChannelSummary[]
  expenseByPaymentMethod: PaymentMethodSummary[]
}

export interface YearlyStatistics {
  year: string
  totalExpense: number
  totalIncome: number
  balance: number
  transactionCount: number
  expenseCount: number
  incomeCount: number
  insight: StatisticsInsight
  monthlyTrend: MonthlyTrendSummary[]
  expenseByCategory: CategorySummary[]
  incomeByCategory: CategorySummary[]
  expenseByChannel: ChannelSummary[]
  expenseByPaymentMethod: PaymentMethodSummary[]
}
