import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'

const source = await readFile(new URL('../src/utils/recordsRouteQuery.ts', import.meta.url), 'utf8')
const { outputText } = ts.transpileModule(source, {
  compilerOptions: {
    module: ts.ModuleKind.ES2022,
    target: ts.ScriptTarget.ES2022
  }
})
const moduleUrl = `data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`
const { defaultRecordsRouteQuery, recordsRouteQueryFromPreference } = await import(moduleUrl)

const defaultPreference = {
  type: '',
  startDate: '2026-06-01',
  endDate: '2026-06-24',
  channel: '',
  categoryId: '',
  paymentMethodId: '',
  keyword: '',
  dayPage: 1
}

assert.deepEqual(defaultRecordsRouteQuery('2026-06-24'), {
  activeDate: '2026-06-24'
})

assert.deepEqual(recordsRouteQueryFromPreference({
  ...defaultPreference,
  type: 'EXPENSE',
  channel: 'ONLINE',
  categoryId: 11,
  paymentMethodId: 7,
  keyword: ' 午餐 ',
  dayPage: 3
}, '2026-06-23'), {
  type: 'EXPENSE',
  startDate: '2026-06-01',
  endDate: '2026-06-24',
  channel: 'ONLINE',
  categoryId: '11',
  paymentMethodId: '7',
  keyword: '午餐',
  dayPage: '3',
  activeDate: '2026-06-23'
})

assert.deepEqual(defaultRecordsRouteQuery('2026/06/24'), {})
