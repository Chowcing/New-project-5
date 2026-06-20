import type { TransactionRecord } from '@/types'

export interface TransactionLedgerItem {
  key: 'occurredAt' | 'category' | 'payment' | 'place' | 'note'
  icon: string
  label: string
  value: string
  description?: string
}

function compactText(value: string | undefined | null) {
  return value?.trim() || ''
}

export function displayTransactionDateTime(value: string | undefined | null) {
  if (!value) return '-'
  const normalized = value.replace('T', ' ').slice(0, 16)
  const match = /^(\d{4})-(\d{2})-(\d{2}) (\d{2}:\d{2})$/.exec(normalized)
  if (!match) return normalized
  return `${match[1]}年${match[2]}月${match[3]}日 ${match[4]}`
}

export function transactionTypeText(record: Pick<TransactionRecord, 'type'>) {
  return record.type === 'INCOME' ? '收入' : '支出'
}

export function transactionChannelText(record: Pick<TransactionRecord, 'channel'>) {
  return record.channel === 'ONLINE' ? '线上' : '线下'
}

export function transactionPlaceLabel(record: Pick<TransactionRecord, 'channel'>) {
  return record.channel === 'ONLINE' ? '线上平台' : '线下地点'
}

export function transactionPlaceValue(record: Pick<TransactionRecord, 'channel' | 'onlineApp' | 'offlinePlace'>) {
  return record.channel === 'ONLINE'
    ? compactText(record.onlineApp) || '未填写'
    : compactText(record.offlinePlace) || '未填写'
}

export function transactionImageCountText(record: Pick<TransactionRecord, 'images'>) {
  const count = record.images?.length ?? 0
  return count > 0 ? `${count} 张凭证` : '无凭证'
}

export function transactionSummaryChips(record: TransactionRecord) {
  const chips = [
    transactionTypeText(record),
    transactionChannelText(record),
    record.categoryName,
    record.paymentMethodName,
    transactionPlaceValue(record)
  ]
  const imageCount = record.images?.length ?? 0
  if (imageCount > 0) {
    chips.push(transactionImageCountText(record))
  }
  return chips.filter((item) => compactText(item))
}

export function transactionLedgerItems(record: TransactionRecord): TransactionLedgerItem[] {
  const channel = transactionChannelText(record)
  const placeLabel = transactionPlaceLabel(record)
  return [
    {
      key: 'occurredAt',
      icon: 'clock-o',
      label: '发生时间',
      value: displayTransactionDateTime(record.occurredAt)
    },
    {
      key: 'category',
      icon: 'apps-o',
      label: '分类',
      value: compactText(record.categoryName) || '未分类',
      description: `${transactionTypeText(record)}分类`
    },
    {
      key: 'payment',
      icon: 'balance-o',
      label: '支付方式',
      value: compactText(record.paymentMethodName) || '未填写'
    },
    {
      key: 'place',
      icon: record.channel === 'ONLINE' ? 'shopping-cart-o' : 'location-o',
      label: placeLabel,
      value: transactionPlaceValue(record),
      description: channel
    },
    {
      key: 'note',
      icon: 'comment-o',
      label: '备注',
      value: compactText(record.note) || '无备注'
    }
  ]
}
