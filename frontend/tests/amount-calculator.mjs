import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'

const source = await readFile(new URL('../src/utils/amountCalculator.ts', import.meta.url), 'utf8')
const { outputText } = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.ES2022, target: ts.ScriptTarget.ES2022 }
})
const { calculateAmount, appendCalculatorKey } = await import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`)

// Catch precedence errors, floating point rounding, and invalid amounts being applied.
for (const [expression, expected] of [
  ['12+3×4', '24.00'], ['20÷4-1', '4.00'], ['0.1+0.2', '0.30'],
  ['1.005', '1.01'], ['10÷3', '3.33'], ['1÷6×3', '0.50'],
  ['2-3+4', '3.00'], ['9999999999.99', '9999999999.99']
]) assert.equal(calculateAmount(expression).value, expected, expression)
for (const expression of ['', '1+', '1÷0', '1-2', '0', '10000000000', '1..2', 'alert(1)']) {
  assert.ok(calculateAmount(expression).error, expression)
  assert.equal(calculateAmount(expression).value, undefined, expression)
}
assert.equal(appendCalculatorKey('12+', '×'), '12×')
assert.equal(appendCalculatorKey('', '.'), '0.')
assert.equal(appendCalculatorKey('12+', '.'), '12+0.')
assert.equal(appendCalculatorKey('1.2', '.'), '1.2')
assert.equal(appendCalculatorKey('', '÷'), '')
assert.equal(appendCalculatorKey('0', '5'), '5')
console.log('计算器运算与输入测试通过')
