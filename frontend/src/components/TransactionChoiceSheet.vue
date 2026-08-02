<script setup lang="ts">
import BottomSheet from '@/components/BottomSheet.vue'

export interface TransactionChoiceOption {
  id: number
  name: string
  icon?: string
}

withDefaults(defineProps<{
  show: boolean
  title: string
  search: string
  searchPlaceholder: string
  options: TransactionChoiceOption[]
  selectedId?: number
  fallbackIcon?: string
  closeDisabled?: boolean
}>(), {
  selectedId: undefined,
  fallbackIcon: 'records-o',
  closeDisabled: false
})

const emit = defineEmits<{
  (event: 'update:show', value: boolean): void
  (event: 'update:search', value: string): void
  (event: 'select', value: TransactionChoiceOption): void
}>()
</script>

<template>
  <BottomSheet
    class="quick-choice-popup"
    :show="show"
    :title="title"
    header-variant="toolbar"
    sheet-class="quick-choice-shell"
    body-class="quick-choice-body"
    :close-on-click-overlay="!closeDisabled"
    :close-disabled="closeDisabled"
    @update:show="emit('update:show', $event)"
  >
    <template #leading="{ close }">
      <button type="button" class="quick-choice-cancel" :disabled="closeDisabled" @click="close">
        <van-icon name="cross" />
        <span>取消</span>
      </button>
    </template>
    <template #actions><span /></template>

    <van-search
      :model-value="search"
      :placeholder="searchPlaceholder"
      @update:model-value="emit('update:search', $event)"
    />
    <div class="quick-choice-list" @touchmove.stop>
      <button
        v-for="item in options"
        :key="item.id"
        type="button"
        :class="['quick-choice-option', { active: selectedId === item.id }]"
        @click="emit('select', item)"
      >
        <van-icon :name="item.icon || fallbackIcon" />
        <span>{{ item.name }}</span>
        <van-icon v-if="selectedId === item.id" name="success" />
      </button>
      <div v-if="$slots.empty" class="quick-choice-empty">
        <slot name="empty" />
      </div>
    </div>
    <div v-if="$slots.footer" class="quick-create-row">
      <slot name="footer" />
    </div>
  </BottomSheet>
</template>

<style scoped>
:global(.van-popup.quick-choice-popup) {
  height: min(78vh, 620px);
  height: min(78lvh, 620px);
  max-height: min(78vh, 620px);
  max-height: min(78lvh, 620px);
}

:global(.bottom-sheet.quick-choice-shell) {
  height: 100%;
  max-height: 100%;
  background: var(--page-bg-soft);
}

:global(.bottom-sheet__body.quick-choice-body) {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  flex: 1 1 0;
  min-height: 0;
  overflow: hidden;
  padding: var(--space-0) var(--space-0) max(var(--space-12), env(safe-area-inset-bottom));
}

.quick-choice-cancel {
  display: inline-flex;
  align-items: center;
  gap: var(--space-3);
  border: 0;
  background: transparent;
  color: var(--text-secondary);
  font: inherit;
  transition: transform var(--motion-fast) ease, filter var(--motion-fast) ease;
}

.quick-choice-cancel:disabled {
  color: var(--text-muted);
}

.quick-choice-cancel:not(:disabled):active {
  transform: translateY(1px) scale(var(--motion-press-scale));
  filter: brightness(1.05);
}

.quick-choice-list {
  display: grid;
  gap: var(--space-8);
  align-content: start;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: var(--space-12);
  touch-action: pan-y;
  -webkit-overflow-scrolling: touch;
}

.quick-choice-option {
  display: grid;
  grid-template-columns: 26px minmax(0, 1fr) 22px;
  gap: var(--space-10);
  align-items: center;
  min-height: 46px;
  border: 1px solid var(--border-warm);
  border-radius: var(--radius-card);
  padding: var(--space-10) var(--space-12);
  background:
    linear-gradient(180deg, var(--surface-highlight), transparent 46%),
    var(--card-bg);
  color: var(--text-main);
  font: inherit;
  text-align: left;
  touch-action: pan-y;
  transition:
    transform var(--motion-fast) ease,
    border-color var(--motion-fast) ease,
    background var(--motion-fast) ease,
    box-shadow var(--motion-fast) ease,
    filter var(--motion-fast) ease;
}

.quick-choice-option span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.quick-choice-option :deep(.van-icon) {
  color: var(--primary);
}

.quick-choice-option.active {
  border-color: var(--primary);
  background: var(--primary-soft);
  box-shadow: var(--inset-primary);
}

.quick-choice-option:active {
  transform: translateY(2px) scale(var(--motion-press-scale));
  filter: brightness(1.05);
}

.quick-choice-empty {
  display: grid;
  justify-items: center;
  gap: var(--space-10);
  padding: var(--space-28) var(--space-16);
  color: var(--text-secondary);
  text-align: center;
}

.quick-choice-empty > :deep(.van-icon) {
  color: var(--text-muted);
  font-size: var(--icon-size-xl);
}

.quick-choice-empty :deep(span) {
  max-width: 100%;
  overflow-wrap: anywhere;
  font-size: var(--font-size-caption);
  line-height: var(--line-height-caption);
}

.quick-choice-empty :deep(button) {
  min-height: 36px;
  border: 1px solid rgba(var(--theme-primary-glow-rgb), 0.42);
  border-radius: var(--radius-pill);
  padding: var(--space-0) var(--space-16);
  background: var(--primary-soft);
  color: var(--primary);
  font: inherit;
  font-size: var(--font-size-caption);
  font-weight: 750;
}

.quick-create-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: var(--space-8);
  align-items: center;
  padding: var(--space-10) var(--space-12) var(--space-0);
  border-top: 1px solid var(--border-warm);
  background:
    linear-gradient(180deg, var(--surface-highlight), transparent),
    var(--card-bg);
}

.quick-create-row :deep(.van-cell) {
  min-height: 48px;
  border: 1px solid rgba(var(--theme-primary-glow-rgb), 0.38);
  border-radius: var(--radius-card);
  background: var(--card-bg);
  box-shadow: var(--inset-primary-subtle);
}

.quick-create-row :deep(.van-cell::after) {
  display: none;
}

.quick-create-row :deep(.van-field__label) {
  color: var(--primary);
  font-weight: 700;
}

.quick-create-row :deep(.van-field__control) {
  color: var(--text-main);
  font-size: var(--font-size-body);
}

.quick-create-row :deep(.van-field:focus-within) {
  border-color: var(--primary);
  background: var(--primary-soft);
  box-shadow: var(--inset-primary-strong);
}
</style>
