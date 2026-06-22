import { strict as assert } from 'node:assert'
import { hasAppHistoryBack } from '../src/utils/navigationBack.ts'

assert.equal(hasAppHistoryBack(null), false)
assert.equal(hasAppHistoryBack({}), false)
assert.equal(hasAppHistoryBack({ back: null }), false)
assert.equal(hasAppHistoryBack({ back: '' }), false)
assert.equal(hasAppHistoryBack({ back: '/records' }), true)
assert.equal(hasAppHistoryBack({ back: '/quick-add?type=EXPENSE' }), true)
