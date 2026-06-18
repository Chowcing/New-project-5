import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'

const source = readFileSync(new URL('../src/utils/date.ts', import.meta.url), 'utf8')
const compiled = ts.transpileModule(source, {
  compilerOptions: {
    module: ts.ModuleKind.CommonJS,
    target: ts.ScriptTarget.ES2020
  }
}).outputText

const module = { exports: {} }
vm.runInNewContext(compiled, { module, exports: module.exports, Date })

const {
  startOfWeekDate,
  endOfWeekDate,
  previousWeekStart,
  currentWeekStart
} = module.exports

assert.equal(startOfWeekDate('2026-06-18'), '2026-06-15')
assert.equal(startOfWeekDate('2026-06-21'), '2026-06-15')
assert.equal(endOfWeekDate('2026-06-15'), '2026-06-21')
assert.equal(previousWeekStart('2026-06-15'), '2026-06-08')
assert.match(currentWeekStart(), /^\d{4}-\d{2}-\d{2}$/)
