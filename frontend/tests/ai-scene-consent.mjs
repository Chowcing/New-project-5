import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'

const values = new Map()
const writtenKeys = []

globalThis.localStorage = {
  getItem(key) {
    return values.get(key) ?? null
  },
  setItem(key, value) {
    writtenKeys.push(key)
    values.set(key, value)
  },
  removeItem(key) {
    values.delete(key)
  },
  clear() {
    values.clear()
  },
  key(index) {
    return [...values.keys()][index] ?? null
  },
  get length() {
    return values.size
  }
}

const source = await readFile(
  new URL('../src/utils/aiSceneConsent.ts', import.meta.url),
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
const { loadAiSceneConsent, saveAiSceneConsent } = await import(moduleUrl)

assert.equal(loadAiSceneConsent(1001), 'UNSET')
assert.equal(saveAiSceneConsent(1001, 'ENABLED'), 'ENABLED')
assert.equal(writtenKeys.at(-1), 'expense.aiSceneConsent.1001')
assert.equal(loadAiSceneConsent(1001), 'ENABLED')
assert.equal(loadAiSceneConsent(2002), 'UNSET')
assert.equal(saveAiSceneConsent(1001, 'DISABLED'), 'DISABLED')
