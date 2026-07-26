import type { TransactionTemplate } from '@/types'

function normalizeTemplateText(value?: string) {
  return (value || '').trim().toLowerCase()
}

export function recommendationTemplateKey(template: TransactionTemplate) {
  return JSON.stringify([
    template.type,
    normalizeTemplateText(template.itemName),
    template.categoryId,
    template.paymentMethodId,
    template.channel,
    template.onlinePlatformId || '',
    normalizeTemplateText(template.onlineApp),
    normalizeTemplateText(template.offlinePlace)
  ])
}
