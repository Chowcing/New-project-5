import type { QuickEntryMode } from '@/utils/preferences'

export type QuickAddDraftTransactionType = 'EXPENSE' | 'INCOME'
export type QuickAddDraftChannel = 'ONLINE' | 'OFFLINE'

export interface QuickAddDraftForm {
  type: QuickAddDraftTransactionType
  itemName: string
  amount: string
  occurredAt: string
  channel: QuickAddDraftChannel
  onlineApp: string
  onlinePlatformId?: number
  offlinePlace: string
  paymentMethodId?: number
  categoryId?: number
  note: string
}

export interface QuickAddDraftDirtyFields {
  amount: boolean
  channel: boolean
  onlineApp: boolean
  onlinePlatformId: boolean
  offlinePlace: boolean
  paymentMethodId: boolean
  categoryId: boolean
}

export interface QuickAddDraftOcrResult {
  imageKey: string
  imageName: string
  text: string
  provider: string
  recognizedAt: number
}

export interface QuickAddDraft {
  version: 1
  savedAt: number
  entryMode: QuickEntryMode
  advancedStep: 1 | 2 | 3
  form: QuickAddDraftForm
  dirtyFields: QuickAddDraftDirtyFields
  ocrResults: QuickAddDraftOcrResult[]
}

const STORAGE_KEY = 'expense.quickAddDraft'

function browserStorage() {
  return typeof localStorage === 'undefined' ? undefined : localStorage
}

function storageKey(userId?: number) {
  return userId ? `${STORAGE_KEY}.${userId}` : ''
}

function isPositiveId(value: unknown): value is number {
  return typeof value === 'number' && Number.isInteger(value) && value > 0
}

function optionalPositiveId(value: unknown) {
  return isPositiveId(value) ? value : undefined
}

function normalizeString(value: unknown) {
  return typeof value === 'string' ? value : ''
}

function normalizeDirtyFields(value: unknown): QuickAddDraftDirtyFields {
  const source = typeof value === 'object' && value ? value as Partial<QuickAddDraftDirtyFields> : {}
  return {
    amount: source.amount === true,
    channel: source.channel === true,
    onlineApp: source.onlineApp === true,
    onlinePlatformId: source.onlinePlatformId === true,
    offlinePlace: source.offlinePlace === true,
    paymentMethodId: source.paymentMethodId === true,
    categoryId: source.categoryId === true
  }
}

function normalizeOcrResults(value: unknown): QuickAddDraftOcrResult[] {
  if (!Array.isArray(value)) return []
  return value
    .map((item) => {
      const source = typeof item === 'object' && item ? item as Partial<QuickAddDraftOcrResult> : {}
      return {
        imageKey: normalizeString(source.imageKey),
        imageName: normalizeString(source.imageName),
        text: normalizeString(source.text),
        provider: normalizeString(source.provider),
        recognizedAt: typeof source.recognizedAt === 'number' ? source.recognizedAt : 0
      }
    })
    .filter((item) => item.imageKey && item.text)
}

function normalizeDraft(value: unknown): QuickAddDraft | null {
  const source = typeof value === 'object' && value ? value as Partial<QuickAddDraft> : undefined
  if (!source || source.version !== 1) return null
  const form = typeof source.form === 'object' && source.form ? source.form as Partial<QuickAddDraftForm> : undefined
  if (!form) return null

  return {
    version: 1,
    savedAt: typeof source.savedAt === 'number' ? source.savedAt : 0,
    entryMode: source.entryMode === 'advanced' ? 'advanced' : 'minimal',
    advancedStep: source.advancedStep === 2 || source.advancedStep === 3 ? source.advancedStep : 1,
    form: {
      type: form.type === 'INCOME' ? 'INCOME' : 'EXPENSE',
      itemName: normalizeString(form.itemName),
      amount: normalizeString(form.amount),
      occurredAt: normalizeString(form.occurredAt),
      channel: form.channel === 'OFFLINE' ? 'OFFLINE' : 'ONLINE',
      onlineApp: normalizeString(form.onlineApp),
      onlinePlatformId: optionalPositiveId(form.onlinePlatformId),
      offlinePlace: normalizeString(form.offlinePlace),
      paymentMethodId: optionalPositiveId(form.paymentMethodId),
      categoryId: optionalPositiveId(form.categoryId),
      note: normalizeString(form.note)
    },
    dirtyFields: normalizeDirtyFields(source.dirtyFields),
    ocrResults: normalizeOcrResults(source.ocrResults)
  }
}

export function loadQuickAddDraft(storage: Storage | undefined = browserStorage(), userId?: number) {
  const key = storageKey(userId)
  if (!storage || !key) return null
  try {
    const raw = storage.getItem(key)
    return raw ? normalizeDraft(JSON.parse(raw)) : null
  } catch {
    return null
  }
}

export function getQuickAddDraftPrompt(storage: Storage | undefined = browserStorage(), type?: QuickAddDraftTransactionType, userId?: number) {
  const draft = loadQuickAddDraft(storage, userId)
  if (type && draft?.form.type !== type) return null
  return draft && hasQuickAddDraftContent(draft) ? draft : null
}

export function saveQuickAddDraft(draft: QuickAddDraft, storage: Storage | undefined = browserStorage(), userId?: number) {
  const key = storageKey(userId)
  if (!storage || !key) return
  storage.setItem(key, JSON.stringify(draft))
}

export function clearQuickAddDraft(storage: Storage | undefined = browserStorage(), userId?: number) {
  const key = storageKey(userId)
  if (!storage || !key) return
  storage.removeItem(key)
}

export function hasQuickAddDraftContent(draft: QuickAddDraft) {
  const textFields = [
    draft.form.itemName,
    draft.form.amount,
    draft.form.note
  ]
  return (
    textFields.some((value) => value.trim().length > 0) ||
    Object.values(draft.dirtyFields).some(Boolean) ||
    draft.entryMode === 'advanced' && draft.advancedStep > 1 ||
    draft.ocrResults.length > 0
  )
}
