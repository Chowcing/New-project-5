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

interface AppPreferences {
  dayRecordPageSize: number
  recordsViewMode: RecordsViewMode
}

function normalizeDayRecordPageSize(value: unknown) {
  return DAY_RECORD_PAGE_SIZE_OPTIONS.some((item) => item.value === value) ? Number(value) : DEFAULT_DAY_RECORD_PAGE_SIZE
}

function normalizeRecordsViewMode(value: unknown): RecordsViewMode {
  return value === 'stack' ? 'stack' : DEFAULT_RECORDS_VIEW_MODE
}

export function loadPreferences(): AppPreferences {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    const parsed = raw ? JSON.parse(raw) : {}
    return {
      dayRecordPageSize: normalizeDayRecordPageSize(parsed.dayRecordPageSize),
      recordsViewMode: normalizeRecordsViewMode(parsed.recordsViewMode)
    }
  } catch {
    return {
      dayRecordPageSize: DEFAULT_DAY_RECORD_PAGE_SIZE,
      recordsViewMode: DEFAULT_RECORDS_VIEW_MODE
    }
  }
}

export function savePreferences(preferences: AppPreferences) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify({
    dayRecordPageSize: normalizeDayRecordPageSize(preferences.dayRecordPageSize),
    recordsViewMode: normalizeRecordsViewMode(preferences.recordsViewMode)
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
