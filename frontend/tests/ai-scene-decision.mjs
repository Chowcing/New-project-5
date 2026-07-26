import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'

const source = await readFile(
  new URL('../src/utils/aiSceneDecision.ts', import.meta.url),
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
const { resolveAiSceneDecision } = await import(moduleUrl)

const history = {
  categoryId: 20,
  channel: 'ONLINE',
  onlinePlatformId: 30
}
const ai = {
  status: 'SUGGESTED',
  categoryId: 20,
  categoryName: '交通',
  channel: 'ONLINE',
  onlinePlatformId: 30,
  onlinePlatformName: '滴滴',
  confidence: 0.91,
  reason: '与历史场景匹配'
}

assert.deepEqual(resolveAiSceneDecision(undefined, ai), {
  kind: 'AUTO_APPLY',
  ai
})
assert.deepEqual(resolveAiSceneDecision(history, ai), {
  kind: 'AGREEMENT',
  ai
})
assert.deepEqual(
  resolveAiSceneDecision(history, { ...ai, categoryId: 21 }),
  { kind: 'CONFLICT', ai: { ...ai, categoryId: 21 } }
)
assert.deepEqual(
  resolveAiSceneDecision(history, { ...ai, channel: 'OFFLINE' }),
  { kind: 'CONFLICT', ai: { ...ai, channel: 'OFFLINE' } }
)
assert.deepEqual(
  resolveAiSceneDecision(history, { ...ai, onlinePlatformId: 31 }),
  { kind: 'CONFLICT', ai: { ...ai, onlinePlatformId: 31 } }
)
assert.deepEqual(
  resolveAiSceneDecision(history, { ...ai, onlinePlatformId: null }),
  { kind: 'AGREEMENT', ai: { ...ai, onlinePlatformId: null } }
)
assert.deepEqual(
  resolveAiSceneDecision(history, { ...ai, status: 'UNCERTAIN' }),
  { kind: 'UNCERTAIN', ai: { ...ai, status: 'UNCERTAIN' } }
)
assert.deepEqual(resolveAiSceneDecision(history, undefined), {
  kind: 'HISTORY_ONLY'
})
