export type CalendarMode = 'year' | 'month' | 'date' | 'datetime'

export interface DateParts {
  year: number
  month: number
  day: number
  hour?: number
  minute?: number
}

export interface CalendarDayCell {
  key: string
  date: string
  day: number
  inCurrentMonth: boolean
  selected: boolean
  today: boolean
  disabled: boolean
}

export interface CalendarMonth {
  title: string
  weekdays: string[]
  weeks: CalendarDayCell[][]
}

export interface CalendarMonthOption {
  label: string
  value: string
  selected: boolean
  disabled: boolean
}

const WEEKDAYS_MONDAY_FIRST = ['一', '二', '三', '四', '五', '六', '日']

export function two(value: string | number) {
  return String(value).padStart(2, '0')
}

export function dateValue(parts: Pick<DateParts, 'year' | 'month' | 'day'>) {
  return `${parts.year}-${two(parts.month)}-${two(parts.day)}`
}

export function monthValue(year: number, month: number) {
  return `${year}-${two(month)}`
}

export function todayValue() {
  const now = new Date()
  return dateValue({
    year: now.getFullYear(),
    month: now.getMonth() + 1,
    day: now.getDate()
  })
}

export function daysInMonth(year: number, month: number) {
  return new Date(year, month, 0).getDate()
}

function normalizeDateOnly(value: Date) {
  return new Date(value.getFullYear(), value.getMonth(), value.getDate())
}

function compareDateStrings(left: string, right: string) {
  return left.localeCompare(right)
}

function minDateValue(minDate?: Date) {
  return minDate ? dateValue({
    year: minDate.getFullYear(),
    month: minDate.getMonth() + 1,
    day: minDate.getDate()
  }) : ''
}

function maxDateValue(maxDate?: Date) {
  return maxDate ? dateValue({
    year: maxDate.getFullYear(),
    month: maxDate.getMonth() + 1,
    day: maxDate.getDate()
  }) : ''
}

function clampNumber(value: number, min: number, max: number) {
  return Math.min(Math.max(value, min), max)
}

export function clampDateParts(parts: DateParts, minDate?: Date, maxDate?: Date): DateParts {
  const day = clampNumber(parts.day, 1, daysInMonth(parts.year, parts.month))
  let date = new Date(parts.year, parts.month - 1, day)
  if (minDate && date < normalizeDateOnly(minDate)) {
    date = normalizeDateOnly(minDate)
  }
  if (maxDate && date > normalizeDateOnly(maxDate)) {
    date = normalizeDateOnly(maxDate)
  }
  const nextParts: DateParts = {
    year: date.getFullYear(),
    month: date.getMonth() + 1,
    day: date.getDate()
  }
  if (typeof parts.hour === 'number') nextParts.hour = parts.hour
  if (typeof parts.minute === 'number') nextParts.minute = parts.minute
  return nextParts
}

export function parseDateParts(value: string | undefined | null, fallback = new Date()): DateParts {
  const match = String(value || '').match(/^(\d{4})(?:-(\d{2})(?:-(\d{2})(?:[T ](\d{2}):(\d{2}))?)?)?/)
  const fallbackParts = {
    year: fallback.getFullYear(),
    month: fallback.getMonth() + 1,
    day: fallback.getDate(),
    hour: fallback.getHours(),
    minute: fallback.getMinutes()
  }
  if (!match) {
    return fallbackParts
  }
  const year = Number(match[1])
  const month = clampNumber(Number(match[2] || fallbackParts.month), 1, 12)
  const day = clampNumber(Number(match[3] || fallbackParts.day), 1, daysInMonth(year, month))
  return {
    year,
    month,
    day,
    hour: clampNumber(Number(match[4] || fallbackParts.hour), 0, 23),
    minute: clampNumber(Number(match[5] || fallbackParts.minute), 0, 59)
  }
}

export function formatDateParts(parts: DateParts, mode: CalendarMode) {
  if (mode === 'year') {
    return String(parts.year)
  }
  if (mode === 'month') {
    return monthValue(parts.year, parts.month)
  }
  const date = dateValue(parts)
  if (mode === 'datetime') {
    return `${date}T${two(parts.hour ?? 0)}:${two(parts.minute ?? 0)}`
  }
  return date
}

export function buildCalendarMonth(
  year: number,
  month: number,
  options: {
    selectedDate?: string
    minDate?: Date
    maxDate?: Date
    availableDates?: string[]
  } = {}
): CalendarMonth {
  const firstDay = new Date(year, month - 1, 1)
  const mondayFirstOffset = (firstDay.getDay() + 6) % 7
  const start = new Date(year, month - 1, 1 - mondayFirstOffset)
  const minValue = minDateValue(options.minDate)
  const maxValue = maxDateValue(options.maxDate)
  const availableDateSet = options.availableDates?.length ? new Set(options.availableDates) : undefined
  const today = todayValue()
  const cells: CalendarDayCell[] = []

  for (let index = 0; index < 42; index += 1) {
    const current = new Date(start.getFullYear(), start.getMonth(), start.getDate() + index)
    const currentValue = dateValue({
      year: current.getFullYear(),
      month: current.getMonth() + 1,
      day: current.getDate()
    })
    const inCurrentMonth = current.getFullYear() === year && current.getMonth() === month - 1
    const beforeMin = Boolean(minValue && compareDateStrings(currentValue, minValue) < 0)
    const afterMax = Boolean(maxValue && compareDateStrings(currentValue, maxValue) > 0)
    const unavailable = Boolean(availableDateSet && !availableDateSet.has(currentValue))
    cells.push({
      key: currentValue,
      date: currentValue,
      day: current.getDate(),
      inCurrentMonth,
      selected: currentValue === options.selectedDate,
      today: currentValue === today,
      disabled: beforeMin || afterMax || unavailable
    })
  }

  return {
    title: `${year}年${month}月`,
    weekdays: WEEKDAYS_MONDAY_FIRST,
    weeks: Array.from({ length: 6 }, (_, index) => cells.slice(index * 7, index * 7 + 7))
  }
}

export function buildMonthGrid(year: number, selectedMonth = '', minDate?: Date, maxDate?: Date): CalendarMonthOption[] {
  const minMonth = minDate ? monthValue(minDate.getFullYear(), minDate.getMonth() + 1) : ''
  const maxMonth = maxDate ? monthValue(maxDate.getFullYear(), maxDate.getMonth() + 1) : ''
  return Array.from({ length: 12 }, (_, index) => {
    const month = index + 1
    const value = monthValue(year, month)
    return {
      label: `${month}月`,
      value,
      selected: value === selectedMonth,
      disabled: Boolean((minMonth && value < minMonth) || (maxMonth && value > maxMonth))
    }
  })
}

export function buildYearGrid(centerYear: number, selectedYear = '', minDate?: Date, maxDate?: Date): CalendarMonthOption[] {
  const startYear = Math.floor(centerYear / 10) * 10
  const minYear = minDate?.getFullYear()
  const maxYear = maxDate?.getFullYear()
  return Array.from({ length: 12 }, (_, index) => {
    const year = startYear + index
    const value = String(year)
    return {
      label: `${year}年`,
      value,
      selected: value === selectedYear,
      disabled: Boolean((minYear && year < minYear) || (maxYear && year > maxYear))
    }
  })
}
