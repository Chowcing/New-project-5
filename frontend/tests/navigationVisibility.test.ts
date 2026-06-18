import { strict as assert } from 'node:assert'
import { resolveNavigationVisibility } from '../src/utils/navigationVisibility.ts'

assert.equal(resolveNavigationVisibility({ previousY: 0, currentY: 32, visible: true }), false)
assert.equal(resolveNavigationVisibility({ previousY: 120, currentY: 72, visible: false }), true)
assert.equal(resolveNavigationVisibility({ previousY: 120, currentY: 124, visible: true }), true)
assert.equal(resolveNavigationVisibility({ previousY: 120, currentY: 116, visible: false }), false)
assert.equal(resolveNavigationVisibility({ previousY: 24, currentY: 0, visible: false }), true)
