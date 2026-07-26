import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'

const source = await readFile(
  new URL('../src/utils/recommendationTemplateKey.ts', import.meta.url),
  'utf8'
)
const { outputText } = ts.transpileModule(source, {
  compilerOptions: {
    module: ts.ModuleKind.ES2022,
    target: ts.ScriptTarget.ES2022
  }
})
const moduleUrl =
  `data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`
const { recommendationTemplateKey } = await import(moduleUrl)

const base = {
  type: 'EXPENSE',
  itemName: '打车',
  amount: 30,
  channel: 'ONLINE',
  onlineApp: '滴滴',
  onlinePlatformId: 1,
  paymentMethodId: 10,
  paymentMethodName: '微信',
  categoryId: 20,
  categoryName: '交通',
  reason: '历史出现 3 次',
  score: 100
}

assert.equal(
  recommendationTemplateKey(base),
  recommendationTemplateKey({ ...base })
)
assert.notEqual(
  recommendationTemplateKey(base),
  recommendationTemplateKey({ ...base, onlinePlatformId: 2 })
)
assert.notEqual(
  recommendationTemplateKey(base),
  recommendationTemplateKey({ ...base, onlineApp: '高德' })
)
assert.notEqual(
  recommendationTemplateKey({ ...base, channel: 'OFFLINE', offlinePlace: '公司' }),
  recommendationTemplateKey({ ...base, channel: 'OFFLINE', offlinePlace: '家' })
)
assert.equal(
  recommendationTemplateKey({ ...base, itemName: ' 打车 ', onlineApp: '滴滴 ' }),
  recommendationTemplateKey(base)
)
