import test from 'node:test'
import assert from 'node:assert/strict'
import { saveTransactionWithOptionalImages } from '../src/utils/transactionSaveFlow.ts'

test('creates the transaction before uploading receipt images', async () => {
  const calls: unknown[] = []
  const images = [{ name: 'receipt.jpg' }]
  const payload = { amount: 12.5 }

  const result = await saveTransactionWithOptionalImages({
    create: async (nextPayload) => {
      calls.push(['create', nextPayload])
      return { id: 88, amount: nextPayload.amount }
    },
    appendImages: async (id, nextImages) => {
      calls.push(['appendImages', id, nextImages])
    }
  }, payload, images)

  assert.deepEqual(calls, [
    ['create', payload],
    ['appendImages', 88, images]
  ])
  assert.deepEqual(result.record, { id: 88, amount: 12.5 })
  assert.equal(result.imageUploadError, undefined)
})

test('keeps the created transaction when receipt image upload fails', async () => {
  const imageUploadError = new Error('Network Error')

  const result = await saveTransactionWithOptionalImages({
    create: async () => ({ id: 89 }),
    appendImages: async () => {
      throw imageUploadError
    }
  }, { amount: 20 }, [{ name: 'receipt.jpg' }])

  assert.deepEqual(result.record, { id: 89 })
  assert.equal(result.imageUploadError, imageUploadError)
})

test('skips image upload when there are no receipt images', async () => {
  let appendCalls = 0

  const result = await saveTransactionWithOptionalImages({
    create: async () => ({ id: 90 }),
    appendImages: async () => {
      appendCalls += 1
    }
  }, { amount: 8 }, [])

  assert.deepEqual(result.record, { id: 90 })
  assert.equal(result.imageUploadError, undefined)
  assert.equal(appendCalls, 0)
})
