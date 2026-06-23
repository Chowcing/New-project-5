export function blurFocusedDescendant(
  container: HTMLElement | null | undefined,
  activeElement: Element | null | undefined
) {
  if (!container || !activeElement || !container.contains(activeElement)) {
    return false
  }

  const focusedElement = activeElement as HTMLElement
  if (typeof focusedElement.blur !== 'function') {
    return false
  }

  focusedElement.blur()
  return true
}
