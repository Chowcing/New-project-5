<script setup lang="ts">
withDefaults(defineProps<{
  confirm?: boolean
  layout?: 'full' | 'split'
  spacerHeight?: string
}>(), {
  confirm: false,
  layout: 'full',
  spacerHeight: '82px'
})
</script>

<template>
  <div class="form-action-bar-spacer" :style="{ height: spacerHeight }" aria-hidden="true" />
  <div :class="['form-action-bar', `form-action-bar--${layout}`, { 'ui-feedback-confirm': confirm }]">
    <slot />
  </div>
</template>

<style scoped>
.form-action-bar-spacer {
  height: 82px;
}

.form-action-bar {
  position: fixed;
  right: 50%;
  bottom: env(safe-area-inset-bottom);
  left: auto;
  z-index: 40;
  gap: var(--space-8);
  width: min(100%, var(--app-max-width));
  padding: var(--space-10) var(--space-12) max(var(--space-10), env(safe-area-inset-bottom));
  transform: translateX(50%);
  border-top: 1px solid rgba(var(--theme-border-warm-rgb), 0.2);
  background: var(--glass-strong-bg);
  -webkit-backdrop-filter: blur(20px) saturate(1.2);
  backdrop-filter: blur(20px) saturate(1.2);
}

.form-action-bar--full {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
}

.form-action-bar--split {
  display: flex;
}

.form-action-bar--split :slotted(.van-button:first-child:not(:only-child)) {
  flex: 0 0 104px;
}

.form-action-bar--split :slotted(.van-button:last-child),
.form-action-bar--full :slotted(.van-button) {
  flex: 1 1 auto;
}

@media (max-width: 360px) {
  .form-action-bar {
    padding-right: var(--space-10);
    padding-left: var(--space-10);
  }
}
</style>
