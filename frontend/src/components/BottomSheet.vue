<script setup lang="ts">
import { computed, useSlots } from 'vue'

type ClassValue = string | string[] | Record<string, boolean>

defineOptions({
  inheritAttrs: false
})

const props = withDefaults(defineProps<{
  show: boolean
  title?: string
  subtitle?: string
  headerVariant?: 'default' | 'toolbar'
  sheetClass?: ClassValue
  bodyClass?: ClassValue
  closeOnClickOverlay?: boolean
  closeDisabled?: boolean
}>(), {
  title: '',
  subtitle: '',
  headerVariant: 'default',
  sheetClass: '',
  bodyClass: '',
  closeOnClickOverlay: true,
  closeDisabled: false
})

const emit = defineEmits<{
  (event: 'update:show', value: boolean): void
  (event: 'closed'): void
}>()

const slots = useSlots()
const visible = computed({
  get: () => props.show,
  set: (value: boolean) => emit('update:show', value)
})
const hasHeader = computed(() => Boolean(props.title || props.subtitle || slots.title || slots.subtitle || slots.leading || slots.actions))

function close() {
  if (props.closeDisabled) return
  visible.value = false
}
</script>

<template>
  <van-popup
    v-bind="$attrs"
    v-model:show="visible"
    position="bottom"
    round
    teleport="body"
    class="bottom-sheet-popup"
    :close-on-click-overlay="closeOnClickOverlay"
    @closed="emit('closed')"
  >
    <section :class="['bottom-sheet', sheetClass]">
      <header v-if="hasHeader" :class="['bottom-sheet__header', `bottom-sheet__header--${headerVariant}`]">
        <div v-if="headerVariant === 'toolbar' || slots.leading" class="bottom-sheet__leading">
          <slot name="leading" :close="close" />
        </div>
        <div class="bottom-sheet__heading">
          <slot name="title">
            <div v-if="title" class="bottom-sheet__title">{{ title }}</div>
          </slot>
          <slot name="subtitle">
            <div v-if="subtitle" class="bottom-sheet__subtitle">{{ subtitle }}</div>
          </slot>
        </div>
        <div class="bottom-sheet__actions">
          <slot name="actions" :close="close">
            <button class="bottom-sheet__close" type="button" aria-label="关闭" title="关闭" :disabled="closeDisabled" @click="close">
              <van-icon name="cross" />
            </button>
          </slot>
        </div>
      </header>

      <div :class="['bottom-sheet__body', bodyClass]">
        <slot />
      </div>
    </section>
  </van-popup>
</template>

<style scoped>
.bottom-sheet {
  display: flex;
  max-height: min(78vh, 720px);
  flex-direction: column;
  overflow: hidden;
  background: var(--card-bg);
}

.bottom-sheet__header {
  display: flex;
  min-height: var(--space-38);
  align-items: center;
  justify-content: space-between;
  gap: var(--space-12);
  padding: var(--space-16) var(--space-14) var(--space-12);
}

.bottom-sheet__header--toolbar {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr) 72px;
  min-height: 48px;
  padding: var(--space-0) var(--space-12);
  border-bottom: 1px solid var(--border-warm);
}

.bottom-sheet__leading {
  min-width: 0;
}

.bottom-sheet__heading {
  min-width: 0;
}

.bottom-sheet__header--toolbar .bottom-sheet__heading {
  text-align: center;
}

.bottom-sheet__title {
  color: var(--text-main);
  font-size: var(--font-size-panel-title);
  font-weight: 700;
  line-height: var(--line-height-panel-title);
}

.bottom-sheet__header--toolbar .bottom-sheet__title {
  overflow: hidden;
  font-size: var(--font-size-section-title);
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.bottom-sheet__subtitle {
  max-width: 260px;
  margin-top: var(--space-3);
  overflow: hidden;
  color: var(--text-secondary);
  font-size: var(--font-size-caption);
  line-height: var(--line-height-caption);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.bottom-sheet__actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: var(--space-8);
}

.bottom-sheet__header--toolbar .bottom-sheet__actions {
  justify-content: flex-end;
}

.bottom-sheet__close {
  display: grid;
  width: var(--space-34);
  height: var(--space-34);
  place-items: center;
  border-radius: var(--radius-card);
  color: var(--text-secondary);
  font-size: var(--icon-size-md);
}

.bottom-sheet__close:disabled {
  color: var(--text-muted);
  cursor: not-allowed;
}

.bottom-sheet__body {
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: var(--space-0) var(--space-12) max(var(--space-18), env(safe-area-inset-bottom));
}
</style>
