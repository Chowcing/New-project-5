import { onBeforeUnmount, ref } from 'vue'

export type VisualFeedbackType = 'selection' | 'confirm' | 'warning' | 'danger'

export function useVisualFeedback(defaultDuration = 460) {
  const visualFeedback = ref<VisualFeedbackType | ''>('')
  let timer: number | undefined
  let frame: number | undefined

  function clearVisualFeedback() {
    if (timer !== undefined) {
      window.clearTimeout(timer)
      timer = undefined
    }
    if (frame !== undefined) {
      window.cancelAnimationFrame(frame)
      frame = undefined
    }
    visualFeedback.value = ''
  }

  function triggerVisualFeedback(type: VisualFeedbackType, duration = defaultDuration) {
    if (typeof window === 'undefined') {
      return
    }
    clearVisualFeedback()
    frame = window.requestAnimationFrame(() => {
      visualFeedback.value = type
      timer = window.setTimeout(() => {
        visualFeedback.value = ''
        timer = undefined
      }, duration)
    })
  }

  onBeforeUnmount(clearVisualFeedback)

  return {
    visualFeedback,
    triggerVisualFeedback
  }
}
