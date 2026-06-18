export function currentMonth() {
  const now = new Date()
  return `${now.getFullYear()}-${pad2(now.getMonth() + 1)}`
}

export function todayDate() {
  const now = new Date()
  return `${now.getFullYear()}-${pad2(now.getMonth() + 1)}-${pad2(now.getDate())}`
}

export function currentWeekStart() {
  return startOfWeekDate(todayDate())
}

export function startOfWeekDate(value: string) {
  const date = parseLocalDate(value)
  const day = date.getDay()
  const mondayOffset = day === 0 ? -6 : 1 - day
  date.setDate(date.getDate() + mondayOffset)
  return formatLocalDate(date)
}

export function endOfWeekDate(value: string) {
  const date = parseLocalDate(startOfWeekDate(value))
  date.setDate(date.getDate() + 6)
  return formatLocalDate(date)
}

export function previousWeekStart(value: string) {
  const date = parseLocalDate(startOfWeekDate(value))
  date.setDate(date.getDate() - 7)
  return formatLocalDate(date)
}

export function isDateString(value: unknown): value is string {
  return typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value)
}

export function nowLocalInput() {
  const now = new Date()
  const offset = now.getTimezoneOffset()
  const local = new Date(now.getTime() - offset * 60_000)
  return local.toISOString().slice(0, 16)
}

export function toBackendDateTime(value: string) {
  return value.length === 16 ? `${value}:00` : value
}

export function toDateTimeLocal(value: string | undefined | null) {
  return value ? value.slice(0, 16) : nowLocalInput()
}

export function money(value: number | string | undefined | null) {
  return Number(value ?? 0).toFixed(2)
}

function parseLocalDate(value: string) {
  const [year, month, day] = value.split('-').map(Number)
  return new Date(year, month - 1, day)
}

function formatLocalDate(value: Date) {
  return `${value.getFullYear()}-${pad2(value.getMonth() + 1)}-${pad2(value.getDate())}`
}

function pad2(value: number) {
  return String(value).padStart(2, '0')
}
