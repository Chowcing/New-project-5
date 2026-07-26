<script setup lang="ts">
import BottomSheet from '@/components/BottomSheet.vue'

defineProps<{
  show: boolean
}>()

const emit = defineEmits<{
  (event: 'update:show', value: boolean): void
  (event: 'enable'): void
  (event: 'decline'): void
}>()

function enable() {
  emit('enable')
}

function decline() {
  emit('decline')
}
</script>

<template>
  <BottomSheet
    :show="show"
    title="开启 AI 分类"
    subtitle="首次使用前请确认数据范围"
    :close-on-click-overlay="false"
    @update:show="emit('update:show', $event)"
  >
    <div class="ai-consent-content">
      <p>AI 会根据事项名称辅助推荐分类和消费场景，历史推荐仍会独立运行。</p>

      <section class="ai-consent-scope" aria-label="发送给 AI 的数据">
        <div class="ai-consent-scope__heading">
          <van-icon name="passed" />
          <strong>会发送</strong>
        </div>
        <span>事项名称、收支类型</span>
      </section>

      <section class="ai-consent-scope" aria-label="不会发送给 AI 的数据">
        <div class="ai-consent-scope__heading">
          <van-icon name="shield-o" />
          <strong>不会发送</strong>
        </div>
        <span>金额、支付方式、线上平台、线下地点、备注、凭证图片和 OCR 识别文本</span>
      </section>

      <div class="ai-consent-actions">
        <van-button
          round
          block
          plain
          type="primary"
          native-type="button"
          @click="decline"
        >
          暂不开启
        </van-button>
        <van-button
          round
          block
          type="primary"
          icon="success"
          native-type="button"
          @click="enable"
        >
          开启 AI 分类
        </van-button>
      </div>
    </div>
  </BottomSheet>
</template>

<style scoped>
.ai-consent-content {
  display: grid;
  gap: var(--space-12);
}

.ai-consent-content > p {
  margin: 0;
  color: var(--text-secondary);
  font-size: var(--font-size-body);
  line-height: var(--line-height-body);
}

.ai-consent-scope {
  display: grid;
  gap: var(--space-6);
  padding: var(--space-12);
  border: 1px solid var(--border-warm);
  border-radius: var(--radius-card);
  background: var(--card-bg);
  color: var(--text-secondary);
  font-size: var(--font-size-caption);
  line-height: var(--line-height-caption);
}

.ai-consent-scope__heading {
  display: flex;
  align-items: center;
  gap: var(--space-6);
  color: var(--text-main);
}

.ai-consent-scope__heading :deep(.van-icon) {
  color: var(--primary);
  font-size: var(--icon-size-sm);
}

.ai-consent-actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-10);
  padding-top: var(--space-4);
}

@media (max-width: 340px) {
  .ai-consent-actions {
    grid-template-columns: 1fr;
  }
}
</style>
