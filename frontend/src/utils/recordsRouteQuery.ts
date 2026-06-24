import type { RecordsQueryPreference } from '@/utils/preferences'

function validDate(value: string) {
  return /^\d{4}-\d{2}-\d{2}$/.test(value)
}

export function recordsRouteQueryFromPreference(query: RecordsQueryPreference, activeDate = '') {
  const nextQuery: Record<string, string> = {}
  if (query.type) nextQuery.type = query.type
  if (query.startDate) nextQuery.startDate = query.startDate
  if (query.endDate) nextQuery.endDate = query.endDate
  if (query.channel) nextQuery.channel = query.channel
  if (query.categoryId !== '') nextQuery.categoryId = String(query.categoryId)
  if (query.paymentMethodId !== '') nextQuery.paymentMethodId = String(query.paymentMethodId)
  if (query.keyword.trim()) nextQuery.keyword = query.keyword.trim()
  if (query.dayPage > 1) nextQuery.dayPage = String(query.dayPage)
  if (activeDate && validDate(activeDate)) nextQuery.activeDate = activeDate
  return nextQuery
}

export function defaultRecordsRouteQuery(activeDate = '') {
  return activeDate && validDate(activeDate) ? { activeDate } : {}
}
