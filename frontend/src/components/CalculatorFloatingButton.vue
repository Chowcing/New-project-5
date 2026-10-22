<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'

const emit = defineEmits<{ (event: 'open'): void }>()
const frame = ref<HTMLElement | null>(null)
const button = ref<HTMLButtonElement | null>(null)
const position = ref({ x: 0, y: 0 })
const ready = ref(false)
const dragging = ref(false)
let suppressClick = false
let session: { id: number; x: number; y: number; origin: { x: number; y: number } } | null = null
let observer: ResizeObserver | undefined

function clamp(point: { x: number; y: number }) {
  if (!frame.value || !button.value) return point
  const bounds = frame.value.getBoundingClientRect()
  const viewport = window.visualViewport
  const bottom = Math.min(
    bounds.bottom,
    document.querySelector('.quick-add-page .form-action-bar')?.getBoundingClientRect().top ?? bounds.bottom,
    viewport ? viewport.offsetTop + viewport.height : window.innerHeight
  )
  return {
    x: Math.max(0, Math.min(bounds.width - button.value.offsetWidth, point.x)),
    y: Math.max(0, Math.min(bottom - bounds.top - button.value.offsetHeight, point.y))
  }
}

function syncPosition() {
  position.value = clamp(ready.value ? position.value : { x: window.innerWidth, y: window.innerHeight })
  ready.value = true
}

function pointerDown(event: PointerEvent) {
  if (!event.isPrimary || event.button !== 0 || !button.value) return
  suppressClick = false
  session = { id: event.pointerId, x: event.clientX, y: event.clientY, origin: { ...position.value } }
  button.value.setPointerCapture(event.pointerId)
}

function pointerMove(event: PointerEvent) {
  if (!session || session.id !== event.pointerId) return
  const dx = event.clientX - session.x
  const dy = event.clientY - session.y
  if (!dragging.value && Math.hypot(dx, dy) <= 6) return
  dragging.value = true
  event.preventDefault()
  position.value = clamp({ x: session.origin.x + dx, y: session.origin.y + dy })
}

function finishPointer(event: PointerEvent, cancelled = false) {
  if (!session || session.id !== event.pointerId) return
  suppressClick = dragging.value || cancelled
  if (cancelled) position.value = clamp(session.origin)
  session = null
  dragging.value = false
  if (button.value?.hasPointerCapture(event.pointerId)) button.value.releasePointerCapture(event.pointerId)
}

function open(event: MouseEvent) {
  if (suppressClick && event.detail !== 0) {
    suppressClick = false
    return
  }
  emit('open')
}

onMounted(() => {
  syncPosition()
  observer = new ResizeObserver(syncPosition)
  if (frame.value) observer.observe(frame.value)
  window.addEventListener('resize', syncPosition)
  window.visualViewport?.addEventListener('resize', syncPosition)
  window.visualViewport?.addEventListener('scroll', syncPosition)
})

onBeforeUnmount(() => {
  observer?.disconnect()
  window.removeEventListener('resize', syncPosition)
  window.visualViewport?.removeEventListener('resize', syncPosition)
  window.visualViewport?.removeEventListener('scroll', syncPosition)
})
</script>

<template>
  <Teleport to="body">
    <div ref="frame" class="calculator-floating-frame">
      <button ref="button" type="button" aria-label="计算器" title="计算器（可拖动）"
        :class="['calculator-floating-button', { 'is-dragging': dragging }]"
        :style="{ left: `${position.x}px`, top: `${position.y}px`, visibility: ready ? 'visible' : 'hidden' }"
        @pointerdown="pointerDown" @pointermove="pointerMove" @pointerup="finishPointer($event)"
        @pointercancel="finishPointer($event, true)" @lostpointercapture="finishPointer($event, true)" @click="open">
        <svg class="calculator-floating-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false">
          <rect x="5" y="2" width="14" height="20" rx="2" />
          <rect x="8" y="5" width="8" height="4" rx="0.5" />
          <path d="M8 12h1m3 0h1m3 0h0M8 15h1m3 0h1m3 0v4M8 18h1m3 0h1" />
        </svg>
      </button>
    </div>
  </Teleport>
</template>

<style scoped>
.calculator-floating-frame {
  position: fixed;
  z-index: 41;
  top: calc(max(var(--app-nav-bar-height), env(safe-area-inset-top)) + var(--space-12));
  bottom: calc(var(--space-100) + env(safe-area-inset-bottom));
  left: 50%;
  width: min(calc(100% - var(--space-24)), calc(var(--app-max-width) - var(--space-24)));
  transform: translateX(-50%);
  pointer-events: none;
}

.calculator-floating-button {
  position: absolute;
  display: grid;
  width: var(--space-48);
  height: var(--space-48);
  place-items: center;
  padding: var(--space-0);
  border: 1px solid var(--border-warm);
  border-radius: var(--radius-pill);
  background: var(--primary);
  box-shadow: var(--shadow-primary-md);
  color: var(--text-on-primary);
  font-size: var(--font-size-body);
  font-weight: 650;
  white-space: nowrap;
  cursor: grab;
  pointer-events: auto;
  touch-action: none;
  user-select: none;
}

.calculator-floating-button.is-dragging { cursor: grabbing; }

.calculator-floating-icon {
  width: var(--icon-size-lg);
  height: var(--icon-size-lg);
}
</style>
