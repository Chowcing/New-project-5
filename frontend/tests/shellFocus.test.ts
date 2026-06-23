import { strict as assert } from 'node:assert'
import { blurFocusedDescendant } from '../src/utils/shellFocus.ts'

class TestElement {
  blurred = false
  private readonly includesActiveElement: boolean

  constructor(includesActiveElement: boolean) {
    this.includesActiveElement = includesActiveElement
  }

  contains(element: unknown) {
    return this.includesActiveElement && element === activeElement
  }

  blur() {
    this.blurred = true
  }
}

const activeElement = new TestElement(false)

assert.equal(
  blurFocusedDescendant(new TestElement(true) as unknown as HTMLElement, activeElement as unknown as Element),
  true
)
assert.equal(activeElement.blurred, true)

const unrelatedActiveElement = new TestElement(false)
assert.equal(
  blurFocusedDescendant(
    new TestElement(false) as unknown as HTMLElement,
    unrelatedActiveElement as unknown as Element
  ),
  false
)
assert.equal(unrelatedActiveElement.blurred, false)

assert.equal(blurFocusedDescendant(null, activeElement as unknown as Element), false)
