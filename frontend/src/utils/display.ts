type EntryTitleSource = {
  itemName?: string | null
  categoryName?: string | null
  onlineApp?: string | null
  offlinePlace?: string | null
  ruleName?: string | null
  name?: string | null
}

function clean(value?: string | null) {
  const text = value?.trim()
  return text || undefined
}

export function transactionTitle(item: EntryTitleSource) {
  return clean(item.itemName)
    || clean(item.categoryName)
    || clean(item.onlineApp)
    || clean(item.offlinePlace)
    || '未命名记录'
}

export function recurringEntryTitle(item: EntryTitleSource) {
  return clean(item.itemName)
    || clean(item.categoryName)
    || clean(item.onlineApp)
    || clean(item.offlinePlace)
    || clean(item.ruleName)
    || clean(item.name)
    || '未填写事项'
}
