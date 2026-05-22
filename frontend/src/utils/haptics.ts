type HapticStyle = 'selection' | 'tap' | 'confirm' | 'warning'

const patterns: Record<HapticStyle, number | number[]> = {
  selection: 8,
  tap: 12,
  confirm: [14, 24, 18],
  warning: [20, 36, 28]
}

let lastPulseAt = 0

function canVibrate() {
  return typeof window !== 'undefined' && 'vibrate' in navigator
}

export function haptic(style: HapticStyle = 'tap') {
  if (!canVibrate()) {
    return
  }
  navigator.vibrate(patterns[style])
}

export function hapticSelection(interval = 80) {
  const now = Date.now()
  if (now - lastPulseAt < interval) {
    return
  }
  lastPulseAt = now
  haptic('selection')
}
