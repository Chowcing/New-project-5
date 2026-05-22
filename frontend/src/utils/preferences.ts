import { DEFAULT_THEME_PREFERENCE, normalizeThemePreference, type ThemeAccent, type ThemeAppearance } from '@/utils/themes'

export const DAY_RECORD_PAGE_SIZE_OPTIONS = [
  { label: '3 条', value: 3 },
  { label: '5 条', value: 5 },
  { label: '10 条', value: 10 },
  { label: '15 条', value: 15 },
  { label: '20 条', value: 20 }
]

const STORAGE_KEY = 'expense.preferences'
const DEFAULT_DAY_RECORD_PAGE_SIZE = 5
const DEFAULT_RECORDS_VIEW_MODE = 'card'

export type RecordsViewMode = 'card' | 'stack'
export type StatisticsPeriodMode = 'MONTHLY' | 'YEARLY'
export type StatisticsBreakdownPanel = 'CATEGORY' | 'CHANNEL' | 'PAYMENT'

export interface RecordsQueryPreference {
  type: '' | 'EXPENSE' | 'INCOME'
  startDate: string
  endDate: string
  channel: '' | 'ONLINE' | 'OFFLINE'
  categoryId: number | ''
  paymentMethodId: number | ''
  keyword: string
  dayPage: number
}

export interface StatisticsPreference {
  mode: StatisticsPeriodMode
  month: string
  year: string
  breakdownPanel: StatisticsBreakdownPanel
}

interface AppPreferences {
  dayRecordPageSize: number
  recordsViewMode: RecordsViewMode
  workspaceMonth?: string
  recordsQuery?: RecordsQueryPreference
  statistics?: StatisticsPreference
  appearance: ThemeAppearance
  accent: ThemeAccent
}

function normalizeDayRecordPageSize(value: unknown) {
  return DAY_RECORD_PAGE_SIZE_OPTIONS.some((item) => item.value === value) ? Number(value) : DEFAULT_DAY_RECORD_PAGE_SIZE
}

function normalizeRecordsViewMode(value: unknown): RecordsViewMode {
  return value === 'stack' ? 'stack' : DEFAULT_RECORDS_VIEW_MODE
}

function normalizeDate(value: unknown) {
  return typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value) ? value : ''
}

function normalizeMonth(value: unknown) {
  return typeof value === 'string' && /^\d{4}-\d{2}$/.test(value) ? value : ''
}

function normalizeYear(value: unknown) {
  return typeof value === 'string' && /^\d{4}$/.test(value) ? value : ''
}

function normalizePositiveInteger(value: unknown) {
  return typeof value === 'number' && Number.isInteger(value) && value > 0 ? value : 1
}

function normalizeOptionalId(value: unknown) {
  return typeof value === 'number' && Number.isInteger(value) && value > 0 ? value : ''
}

function normalizeRecordsQuery(value: unknown): RecordsQueryPreference | undefined {
  const source = typeof value === 'object' && value ? value as Partial<RecordsQueryPreference> : undefined
  if (!source) return undefined
  return {
    type: source.type === 'EXPENSE' || source.type === 'INCOME' ? source.type : '',
    startDate: normalizeDate(source.startDate),
    endDate: normalizeDate(source.endDate),
    channel: source.channel === 'ONLINE' || source.channel === 'OFFLINE' ? source.channel : '',
    categoryId: normalizeOptionalId(source.categoryId),
    paymentMethodId: normalizeOptionalId(source.paymentMethodId),
    keyword: typeof source.keyword === 'string' ? source.keyword : '',
    dayPage: normalizePositiveInteger(source.dayPage)
  }
}

function normalizeStatisticsPreference(value: unknown): StatisticsPreference | undefined {
  const source = typeof value === 'object' && value ? value as Partial<StatisticsPreference> : undefined
  if (!source) return undefined
  return {
    mode: source.mode === 'YEARLY' ? 'YEARLY' : 'MONTHLY',
    month: normalizeMonth(source.month),
    year: normalizeYear(source.year),
    breakdownPanel: source.breakdownPanel === 'CHANNEL' || source.breakdownPanel === 'PAYMENT' ? source.breakdownPanel : 'CATEGORY'
  }
}

export function loadPreferences(): AppPreferences {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    const parsed = raw ? JSON.parse(raw) : {}
    const themePreference = normalizeThemePreference(parsed)
    return {
      dayRecordPageSize: normalizeDayRecordPageSize(parsed.dayRecordPageSize),
      recordsViewMode: normalizeRecordsViewMode(parsed.recordsViewMode),
      workspaceMonth: normalizeMonth(parsed.workspaceMonth) || undefined,
      recordsQuery: normalizeRecordsQuery(parsed.recordsQuery),
      statistics: normalizeStatisticsPreference(parsed.statistics),
      ...themePreference
    }
  } catch {
    return {
      dayRecordPageSize: DEFAULT_DAY_RECORD_PAGE_SIZE,
      recordsViewMode: DEFAULT_RECORDS_VIEW_MODE,
      ...DEFAULT_THEME_PREFERENCE
    }
  }
}

export function savePreferences(preferences: AppPreferences) {
  const themePreference = normalizeThemePreference(preferences)
  const current = loadPreferences()
  localStorage.setItem(STORAGE_KEY, JSON.stringify({
    dayRecordPageSize: normalizeDayRecordPageSize(preferences.dayRecordPageSize),
    recordsViewMode: normalizeRecordsViewMode(preferences.recordsViewMode),
    workspaceMonth: normalizeMonth(preferences.workspaceMonth) || current.workspaceMonth,
    recordsQuery: normalizeRecordsQuery(preferences.recordsQuery) || current.recordsQuery,
    statistics: normalizeStatisticsPreference(preferences.statistics) || current.statistics,
    ...themePreference
  }))
}

export function loadDayRecordPageSize() {
  return loadPreferences().dayRecordPageSize
}

export function saveDayRecordPageSize(value: number) {
  const nextValue = normalizeDayRecordPageSize(value)
  savePreferences({
    ...loadPreferences(),
    dayRecordPageSize: nextValue
  })
  return nextValue
}

export function loadRecordsViewMode() {
  return loadPreferences().recordsViewMode
}

export function saveRecordsViewMode(value: RecordsViewMode) {
  const nextValue = normalizeRecordsViewMode(value)
  savePreferences({
    ...loadPreferences(),
    recordsViewMode: nextValue
  })
  return nextValue
}

export function loadWorkspaceMonth() {
  return loadPreferences().workspaceMonth || ''
}

export function saveWorkspaceMonth(value: string) {
  const nextValue = normalizeMonth(value)
  if (!nextValue) return loadWorkspaceMonth()
  savePreferences({
    ...loadPreferences(),
    workspaceMonth: nextValue
  })
  return nextValue
}

export function loadRecordsQueryPreference() {
  return loadPreferences().recordsQuery
}

export function saveRecordsQueryPreference(value: RecordsQueryPreference) {
  const nextValue = normalizeRecordsQuery(value)
  if (!nextValue) return loadRecordsQueryPreference()
  savePreferences({
    ...loadPreferences(),
    recordsQuery: nextValue
  })
  return nextValue
}

export function loadStatisticsPreference() {
  return loadPreferences().statistics
}

export function saveStatisticsPreference(value: StatisticsPreference) {
  const nextValue = normalizeStatisticsPreference(value)
  if (!nextValue) return loadStatisticsPreference()
  savePreferences({
    ...loadPreferences(),
    statistics: nextValue
  })
  return nextValue
}
