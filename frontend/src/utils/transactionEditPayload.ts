import type { OnlinePlatform } from '@/types'

export interface TransactionEditOnlinePlatformForm {
  channel: 'ONLINE' | 'OFFLINE'
  onlineApp: string
  onlinePlatformId?: number
}

export function transactionEditOnlinePlatformFields(form: TransactionEditOnlinePlatformForm, onlinePlatforms: OnlinePlatform[]) {
  if (form.channel !== 'ONLINE') {
    return {
      onlineApp: undefined,
      onlinePlatformId: undefined
    }
  }

  const selectedPlatform = onlinePlatforms.find((item) => item.id === form.onlinePlatformId)

  return {
    onlineApp: selectedPlatform?.name || form.onlineApp.trim() || undefined,
    onlinePlatformId: form.onlinePlatformId
  }
}
