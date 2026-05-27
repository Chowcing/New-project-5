import type { PageResponse, TransactionRecord } from '@/types'
import { money } from '@/utils/date'
import { transactionTitle } from '@/utils/display'

export function referenceMessage(result: PageResponse<TransactionRecord>) {
  const lines = result.records.map((item) => {
    const sign = item.type === 'EXPENSE' ? '-' : '+'
    const occurredAt = item.occurredAt.replace('T', ' ').slice(0, 16)
    return `${occurredAt} ${transactionTitle(item)} ${sign}¥${money(item.amount)}`
  })
  const suffix = result.total > result.records.length ? `\n另有 ${result.total - result.records.length} 条记录未显示。` : ''
  return `已有 ${result.total} 条收支记录引用，不能删除。\n\n${lines.join('\n')}${suffix}`
}
