export interface TransactionEditOnlinePlatformForm {
  channel: 'ONLINE' | 'OFFLINE'
  onlineApp: string
  onlinePlatformId?: number
}

export function transactionEditOnlinePlatformFields(form: TransactionEditOnlinePlatformForm) {
  if (form.channel !== 'ONLINE') {
    return {
      onlineApp: undefined,
      onlinePlatformId: undefined
    }
  }

  return {
    onlineApp: form.onlineApp.trim() || undefined,
    onlinePlatformId: form.onlinePlatformId
  }
}
