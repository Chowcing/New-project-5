export function currentMonth() {
  const now = new Date()
  return `${now.getFullYear()}-${pad2(now.getMonth() + 1)}`
}

export function todayDate() {
  const now = new Date()
  return `${now.getFullYear()}-${pad2(now.getMonth() + 1)}-${pad2(now.getDate())}`
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

function pad2(value: number) {
  return String(value).padStart(2, '0')
}
