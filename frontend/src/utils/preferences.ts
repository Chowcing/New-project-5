export const DAY_RECORD_PAGE_SIZE_OPTIONS = [
  { label: '3 条', value: 3 },
  { label: '5 条', value: 5 },
  { label: '10 条', value: 10 },
  { label: '15 条', value: 15 },
  { label: '20 条', value: 20 }
]

const STORAGE_KEY = 'expense.preferences'
const DEFAULT_DAY_RECORD_PAGE_SIZE = 5

interface AppPreferences {
  dayRecordPageSize: number
}

function normalizeDayRecordPageSize(value: unknown) {
  return DAY_RECORD_PAGE_SIZE_OPTIONS.some((item) => item.value === value) ? Number(value) : DEFAULT_DAY_RECORD_PAGE_SIZE
}

export function loadPreferences(): AppPreferences {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    const parsed = raw ? JSON.parse(raw) : {}
    return {
      dayRecordPageSize: normalizeDayRecordPageSize(parsed.dayRecordPageSize)
    }
  } catch {
    return {
      dayRecordPageSize: DEFAULT_DAY_RECORD_PAGE_SIZE
    }
  }
}

export function savePreferences(preferences: AppPreferences) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify({
    dayRecordPageSize: normalizeDayRecordPageSize(preferences.dayRecordPageSize)
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
