<script setup lang="ts">
withDefaults(defineProps<{
  title?: boolean
  rows?: number
  cards?: number
  variant?: 'panel' | 'list' | 'form'
}>(), {
  title: true,
  rows: 3,
  cards: 1,
  variant: 'panel'
})
</script>

<template>
  <div :class="['page-skeleton', `page-skeleton-${variant}`]" aria-busy="true" aria-label="正在加载">
    <div v-for="index in cards" :key="index" class="page-skeleton-card">
      <van-skeleton :title="title" :row="rows" />
    </div>
  </div>
</template>

<style scoped>
.page-skeleton {
  display: grid;
  gap: var(--space-12);
}

.page-skeleton-card {
  min-width: 0;
  border: 1px solid rgba(var(--theme-border-warm-rgb), 0.18);
  border-radius: var(--radius-card);
  padding: var(--space-14);
  background: var(--card-bg);
}

.page-skeleton-panel .page-skeleton-card {
  min-height: 108px;
}

.page-skeleton-list .page-skeleton-card {
  min-height: 76px;
}

.page-skeleton-form .page-skeleton-card {
  min-height: 156px;
}

.page-skeleton :deep(.van-skeleton) {
  padding: 0;
}
</style>
