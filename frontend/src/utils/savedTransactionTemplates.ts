import type { Category, OnlinePlatform, PaymentMethod, TransactionRecord } from '@/types'

type TemplateFields = Pick<TransactionRecord,
  'type' | 'itemName' | 'channel' | 'onlineApp' | 'onlinePlatformId' | 'offlinePlace' | 'note'>
  & Partial<Pick<TransactionRecord, 'amount' | 'paymentMethodId' | 'categoryId'>>

export type SavedTransactionTemplate = TemplateFields & { id: string; name: string }

function storageKey(userId: number) {
  return `expense.savedTransactionTemplates.${userId}`
}

export function readSavedTemplates(userId?: number): SavedTransactionTemplate[] {
  if (userId === undefined) return []
  try {
    const stored: unknown = JSON.parse(localStorage.getItem(storageKey(userId)) || '[]')
    return Array.isArray(stored)
      ? stored.filter((item) => typeof item?.id === 'string' && typeof item.name === 'string')
      : []
  } catch {
    return []
  }
}

export function writeSavedTemplates(userId: number, templates: SavedTransactionTemplate[]) {
  localStorage.setItem(storageKey(userId), JSON.stringify(templates))
}

export function createSavedTemplate(record: TransactionRecord, name: string, amount?: number): SavedTransactionTemplate {
  const id = Array.from(crypto.getRandomValues(new Uint32Array(4)), (value) => value.toString(16)).join('-')
  return {
    id,
    name: name.trim(),
    type: record.type,
    itemName: record.itemName,
    amount,
    channel: record.channel,
    onlineApp: record.channel === 'ONLINE' ? record.onlineApp : undefined,
    onlinePlatformId: record.channel === 'ONLINE' ? record.onlinePlatformId : undefined,
    offlinePlace: record.channel === 'OFFLINE' ? record.offlinePlace : undefined,
    paymentMethodId: record.paymentMethodId,
    categoryId: record.categoryId,
    note: record.note
  }
}

export function resolveSavedTemplate(
  template: SavedTransactionTemplate,
  categories: Pick<Category, 'id' | 'type'>[],
  payments: Pick<PaymentMethod, 'id'>[],
  platforms: Pick<OnlinePlatform, 'id' | 'name'>[]
) {
  const missing: string[] = []
  const category = categories.find((item) => item.id === template.categoryId && item.type === template.type)
  const payment = payments.find((item) => item.id === template.paymentMethodId)
  const platform = platforms.find((item) => item.id === template.onlinePlatformId)
  if (template.categoryId && !category) missing.push('分类')
  if (template.paymentMethodId && !payment) missing.push('支付方式')
  if (template.onlinePlatformId && !platform) missing.push('线上平台')
  const fields: TemplateFields = {
    ...template,
    categoryId: category?.id,
    paymentMethodId: payment?.id,
    onlinePlatformId: platform?.id,
    onlineApp: template.onlinePlatformId ? platform?.name : template.onlineApp
  }
  return { fields, missing }
}
