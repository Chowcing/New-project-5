import assert from 'node:assert/strict'

const { normalizeFloatingPosition } = await import(
  '../src/utils/floatingPosition.ts'
)

assert.equal(normalizeFloatingPosition(undefined), undefined)
assert.deepEqual(
  normalizeFloatingPosition({ xRatio: 0.25, yRatio: 0.75 }),
  { xRatio: 0.25, yRatio: 0.75 }
)
assert.deepEqual(
  normalizeFloatingPosition({ xRatio: -0.4, yRatio: 1.8 }),
  { xRatio: 0, yRatio: 1 }
)
assert.equal(
  normalizeFloatingPosition({ xRatio: 'bad', yRatio: 0.5 }),
  undefined
)
assert.equal(
  normalizeFloatingPosition({ xRatio: Number.NaN, yRatio: 0.5 }),
  undefined
)

console.log('流水日期按钮位置偏好检查通过')
