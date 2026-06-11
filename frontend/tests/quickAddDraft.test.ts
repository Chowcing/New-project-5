import { strict as assert } from 'node:assert'
import {
  clearQuickAddDraft,
  getQuickAddDraftPrompt,
  hasQuickAddDraftContent,
  loadQuickAddDraft,
  saveQuickAddDraft,
  type QuickAddDraft
} from '../src/utils/quickAddDraft.ts'

class MemoryStorage implements Storage {
  private values = new Map<string, string>()

  get length() {
    return this.values.size
  }

  clear() {
    this.values.clear()
  }

  getItem(key: string) {
    return this.values.get(key) ?? null
  }

  key(index: number) {
    return [...this.values.keys()][index] ?? null
  }

  removeItem(key: string) {
    this.values.delete(key)
  }

  setItem(key: string, value: string) {
    this.values.set(key, value)
  }
}

const storage = new MemoryStorage()
const draft: QuickAddDraft = {
  version: 1,
  savedAt: 1770631200000,
  entryMode: 'advanced',
  advancedStep: 2,
  form: {
    type: 'EXPENSE',
    itemName: '午餐',
    amount: '18.5',
    occurredAt: '2026-06-11T12:30',
    channel: 'ONLINE',
    onlineApp: '美团',
    onlinePlatformId: 3,
    offlinePlace: '',
    paymentMethodId: 2,
    categoryId: 1,
    note: '少辣'
  },
  dirtyFields: {
    amount: true,
    channel: false,
    onlineApp: true,
    onlinePlatformId: true,
    offlinePlace: false,
    paymentMethodId: true,
    categoryId: true
  },
  ocrResults: [
    {
      imageKey: 'receipt',
      imageName: 'receipt.png',
      text: '午餐 18.5',
      provider: 'local',
      recognizedAt: 1770631200000
    }
  ]
}

clearQuickAddDraft(storage)
assert.equal(loadQuickAddDraft(storage), null)
assert.equal(getQuickAddDraftPrompt(storage), null)
assert.equal(hasQuickAddDraftContent({ ...draft, form: { ...draft.form, itemName: '', amount: '', note: '' }, ocrResults: [] }), true)

saveQuickAddDraft({ ...draft, form: { ...draft.form, itemName: '', amount: '', note: '' }, dirtyFields: {
  amount: false,
  channel: false,
  onlineApp: false,
  onlinePlatformId: false,
  offlinePlace: false,
  paymentMethodId: false,
  categoryId: false
}, ocrResults: [], advancedStep: 1 }, storage)
assert.equal(getQuickAddDraftPrompt(storage), null)

saveQuickAddDraft(draft, storage)
assert.deepEqual(loadQuickAddDraft(storage), draft)
assert.deepEqual(getQuickAddDraftPrompt(storage), draft)
assert.deepEqual(getQuickAddDraftPrompt(storage, 'EXPENSE'), draft)
assert.equal(getQuickAddDraftPrompt(storage, 'INCOME'), null)

const incomeDraft: QuickAddDraft = {
  ...draft,
  form: {
    ...draft.form,
    type: 'INCOME',
    itemName: '工资'
  }
}
saveQuickAddDraft(incomeDraft, storage)
assert.deepEqual(getQuickAddDraftPrompt(storage, 'INCOME'), incomeDraft)
assert.equal(getQuickAddDraftPrompt(storage, 'EXPENSE'), null)

clearQuickAddDraft(storage)
assert.equal(loadQuickAddDraft(storage), null)
