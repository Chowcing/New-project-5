export interface FloatingPositionPreference {
  xRatio: number
  yRatio: number
}

function normalizeRatio(value: unknown) {
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    return undefined
  }
  return Math.max(0, Math.min(1, value))
}

export function normalizeFloatingPosition(value: unknown): FloatingPositionPreference | undefined {
  const source = typeof value === 'object' && value
    ? value as Partial<FloatingPositionPreference>
    : undefined
  if (!source) {
    return undefined
  }
  const xRatio = normalizeRatio(source.xRatio)
  const yRatio = normalizeRatio(source.yRatio)
  if (xRatio === undefined || yRatio === undefined) {
    return undefined
  }
  return { xRatio, yRatio }
}
