import { todayDate } from '@/utils/date'
import type { RecurringRule, RecurringRulePayload, RecurringRuleRun, RecurringRuleStatus } from '@/types'

export const RECURRING_STATUS_OPTIONS = [
  { label: '启用', value: 'ACTIVE' as RecurringRuleStatus },
  { label: '暂停', value: 'PAUSED' as RecurringRuleStatus }
]

export const RECURRING_SCHEDULE_TYPE_OPTIONS = [
  { label: '每月', value: 'MONTHLY' as const },
  { label: '每周', value: 'WEEKLY' as const }
]

export const RECURRING_WEEKDAY_OPTIONS = [
  { label: '周一', value: 'MONDAY' as const },
  { label: '周二', value: 'TUESDAY' as const },
  { label: '周三', value: 'WEDNESDAY' as const },
  { label: '周四', value: 'THURSDAY' as const },
  { label: '周五', value: 'FRIDAY' as const },
  { label: '周六', value: 'SATURDAY' as const },
  { label: '周日', value: 'SUNDAY' as const }
]

export const RECURRING_REMINDER_OPTIONS = [
  { label: '当天', value: 0 },
  { label: '提前 1 天', value: 1 },
  { label: '提前 2 天', value: 2 },
  { label: '提前 3 天', value: 3 },
  { label: '提前 7 天', value: 7 },
  { label: '提前 14 天', value: 14 }
]

export const RECURRING_DAY_OPTIONS = Array.from({ length: 31 }, (_, index) => {
  const day = index + 1
  return { label: `${day} 号`, value: day }
})

export const RECURRING_INTERVAL_OPTIONS = Array.from({ length: 12 }, (_, index) => {
  const interval = index + 1
  return { label: `${interval}`, value: interval }
})

const WEEKDAY_TEXT: Record<NonNullable<RecurringRule['weekday']>, string> = {
  MONDAY: '周一',
  TUESDAY: '周二',
  WEDNESDAY: '周三',
  THURSDAY: '周四',
  FRIDAY: '周五',
  SATURDAY: '周六',
  SUNDAY: '周日'
}

const STATUS_TEXT: Record<RecurringRuleStatus, string> = {
  ACTIVE: '启用',
  PAUSED: '暂停'
}

const RUN_STATUS_TEXT: Record<RecurringRuleRun['status'], string> = {
  PENDING: '待处理',
  GENERATED: '已生成',
  SKIPPED: '已跳过',
  CANCELLED: '已取消',
  FAILED: '失败'
}

export function weekdayLabel(value?: RecurringRule['weekday'] | null) {
  if (!value) {
    return ''
  }
  return WEEKDAY_TEXT[value] || ''
}

export function ruleStatusLabel(value: RecurringRuleStatus) {
  return STATUS_TEXT[value] || value
}

export function runStatusLabel(value: RecurringRuleRun['status']) {
  return RUN_STATUS_TEXT[value] || value
}

export function scheduleSummary(rule: Pick<RecurringRule, 'scheduleType' | 'intervalValue' | 'dayOfMonth' | 'weekday'>) {
  const interval = Math.max(Number(rule.intervalValue || 1), 1)
  if (rule.scheduleType === 'WEEKLY') {
    return `每 ${interval} 周 · ${weekdayLabel(rule.weekday)}`
  }
  return `每 ${interval} 月 · ${rule.dayOfMonth ?? '-'} 号`
}

export function recurringRulePayload(rule: RecurringRule, status: RecurringRuleStatus = rule.status): RecurringRulePayload {
  return {
    name: rule.name,
    type: rule.type,
    itemName: rule.itemName,
    amount: Number(rule.amount),
    channel: rule.channel,
    onlineApp: rule.onlineApp || undefined,
    offlinePlace: rule.offlinePlace || undefined,
    paymentMethodId: rule.paymentMethodId,
    categoryId: rule.categoryId,
    note: rule.note || undefined,
    scheduleType: rule.scheduleType,
    intervalValue: rule.intervalValue,
    dayOfMonth: rule.dayOfMonth ?? undefined,
    weekday: rule.weekday ?? undefined,
    startDate: rule.startDate,
    endDate: rule.endDate ?? undefined,
    reminderDaysBefore: rule.reminderDaysBefore,
    status
  }
}

function utcDateValue(value: string) {
  const [year, month, day] = value.split('-').map(Number)
  return Date.UTC(year, month - 1, day)
}

export function daysUntil(value: string, reference = todayDate()) {
  return Math.round((utcDateValue(value) - utcDateValue(reference)) / 86_400_000)
}

export function dueStatusText(run: RecurringRuleRun, reference = todayDate()) {
  const diff = daysUntil(run.dueDate, reference)
  if (diff === 0) {
    return '今天到期'
  }
  if (diff > 0) {
    return `还有 ${diff} 天`
  }
  return `逾期 ${Math.abs(diff)} 天`
}

