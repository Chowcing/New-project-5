import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'

const source = await readFile(new URL('../src/utils/savedTransactionTemplates.ts', import.meta.url), 'utf8')
const { outputText } = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ESNext, module: ts.ModuleKind.ESNext } })
const { createSavedTemplate, readSavedTemplates, writeSavedTemplates, resolveSavedTemplate } =
  await import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`)

const values = new Map()
globalThis.localStorage = {
  getItem: (key) => values.get(key) ?? null,
  setItem: (key, value) => values.set(key, value)
}

const record = {
  type: 'EXPENSE', channel: 'ONLINE', amount: 28.5, itemName: '午饭',
  categoryId: 1, paymentMethodId: 2, onlinePlatformId: 3, onlineApp: '旧平台名',
  occurredAt: '2025-01-01T12:00', images: [{ id: 9 }]
}
const template = createSavedTemplate(record, '  午饭  ')
assert.equal(template.name, '午饭')
assert.equal(template.amount, undefined)
assert.equal('images' in template, false)
assert.equal('occurredAt' in template, false)
assert.equal(createSavedTemplate(record, '午饭', record.amount).amount, 28.5)
writeSavedTemplates(1, [template])
assert.deepEqual(readSavedTemplates(1), JSON.parse(JSON.stringify([template])))
assert.deepEqual(readSavedTemplates(2), [])
assert.deepEqual(readSavedTemplates(undefined), [])

const references = [{ id: 1, type: 'EXPENSE' }]
const payments = [{ id: 2 }]
assert.deepEqual(resolveSavedTemplate(template, references, payments, []).missing, ['线上平台'])
const resolved = resolveSavedTemplate(template, references, payments, [{ id: 3, name: '新平台名' }])
assert.equal(resolved.fields.onlineApp, '新平台名')
assert.equal(resolved.fields.amount, undefined)
assert.deepEqual(resolved.missing, [])
assert.deepEqual(resolveSavedTemplate(template, [{ id: 1, type: 'INCOME' }], [], []).missing,
  ['分类', '支付方式', '线上平台'])
