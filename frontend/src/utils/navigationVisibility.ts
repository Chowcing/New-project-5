interface NavigationVisibilityOptions {
  previousY: number
  currentY: number
  visible: boolean
  threshold?: number
  topOffset?: number
}

const DEFAULT_THRESHOLD = 8
const DEFAULT_TOP_OFFSET = 4

export function resolveNavigationVisibility({
  previousY,
  currentY,
  visible,
  threshold = DEFAULT_THRESHOLD,
  topOffset = DEFAULT_TOP_OFFSET
}: NavigationVisibilityOptions) {
  const nextY = Math.max(0, currentY)
  const lastY = Math.max(0, previousY)
  const delta = nextY - lastY

  if (nextY <= topOffset) {
    return true
  }

  if (Math.abs(delta) < threshold) {
    return visible
  }

  return delta < 0
}
