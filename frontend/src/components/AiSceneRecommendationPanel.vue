<script setup lang="ts">
import { computed } from 'vue'
import type { AiSceneRecommendation, TransactionTemplate } from '@/types'

type PanelState =
  | 'IDLE'
  | 'LOADING'
  | 'APPLIED'
  | 'AGREEMENT'
  | 'CONFLICT'
  | 'UNCERTAIN'
  | 'UNAVAILABLE'

const props = withDefaults(defineProps<{
  state: PanelState
  ai?: AiSceneRecommendation
  history?: TransactionTemplate
  canUndo: boolean
}>(), {
  ai: undefined,
  history: undefined
})

const emit = defineEmits<{
  (event: 'choose-history'): void
  (event: 'choose-ai'): void
  (event: 'undo'): void
}>()

const aiSceneText = computed(() => {
  const category = props.ai?.categoryName || '未识别分类'
  return sceneText(
    category,
    props.ai?.channel,
    props.ai?.onlinePlatformName
  )
})

const historySceneText = computed(() => {
  const category = props.history?.categoryName || '未识别分类'
  return sceneText(
    category,
    props.history?.channel,
    props.history?.onlineApp
  )
})

const historyChoiceText = computed(() => (
  props.history?.channel === 'ONLINE' && props.history.onlineApp
    ? props.history.onlineApp
    : historySceneText.value
))

const aiChoiceText = computed(() => (
  props.ai?.channel === 'ONLINE' && props.ai.onlinePlatformName
    ? props.ai.onlinePlatformName
    : aiSceneText.value
))

function sceneText(
  category: string,
  channel: 'ONLINE' | 'OFFLINE' | null | undefined,
  onlinePlatformName?: string | null
) {
  if (channel === 'ONLINE') {
    return onlinePlatformName
      ? `${category} · 线上 · ${onlinePlatformName}`
      : `${category} · 线上`
  }
  if (channel === 'OFFLINE') return `${category} · 线下`
  return `${category} · 场景不确定`
}
</script>

<template>
  <section
    v-if="state !== 'IDLE'"
    class="ai-scene-recommendation"
    aria-live="polite"
  >
    <div class="ai-scene-recommendation__icon" aria-hidden="true">
      <van-loading v-if="state === 'LOADING'" size="18px" />
      <van-icon v-else-if="state === 'UNAVAILABLE'" name="warning-o" />
      <van-icon v-else-if="state === 'UNCERTAIN'" name="question-o" />
      <van-icon v-else name="bulb-o" />
    </div>

    <div class="ai-scene-recommendation__content">
      <template v-if="state === 'LOADING'">
        <strong>AI 正在判断分类场景…</strong>
        <span>历史推荐会独立完成，不必等待 AI。</span>
      </template>

      <template v-else-if="state === 'APPLIED'">
        <strong>AI 建议：{{ aiSceneText }}</strong>
        <span v-if="ai?.reason">{{ ai.reason }}</span>
      </template>

      <template v-else-if="state === 'AGREEMENT'">
        <strong>历史与 AI 均建议：{{ aiSceneText }}</strong>
        <span v-if="ai?.reason">{{ ai.reason }}</span>
      </template>

      <template v-else-if="state === 'CONFLICT'">
        <strong>历史和 AI 给出了不同建议</strong>
        <span>历史建议：{{ historySceneText }}</span>
        <span>AI 建议：{{ aiSceneText }}</span>
        <span v-if="ai?.reason">{{ ai.reason }}</span>
        <div class="ai-scene-recommendation__actions">
          <van-button
            size="small"
            plain
            type="primary"
            native-type="button"
            @click="emit('choose-history')"
          >
            采用历史建议（{{ historyChoiceText }}）
          </van-button>
          <van-button
            size="small"
            type="primary"
            native-type="button"
            @click="emit('choose-ai')"
          >
            采用 AI 建议（{{ aiChoiceText }}）
          </van-button>
        </div>
      </template>

      <template v-else-if="state === 'UNCERTAIN'">
        <strong>AI 暂无法确定，请手动选择</strong>
        <span v-if="ai?.reason">{{ ai.reason }}</span>
      </template>

      <template v-else>
        <strong v-if="history">AI 暂不可用，已保留历史推荐</strong>
        <strong v-else>AI 暂不可用，请手动选择</strong>
        <span v-if="history">你仍可使用历史推荐或手动填写。</span>
        <span v-else>请手动选择分类和场景。</span>
      </template>

      <button
        v-if="canUndo && (state === 'APPLIED' || state === 'AGREEMENT')"
        class="ai-scene-recommendation__undo"
        type="button"
        @click="emit('undo')"
      >
        撤销 AI 建议
      </button>
    </div>
  </section>
</template>

<style scoped>
.ai-scene-recommendation {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: var(--space-10);
  padding: var(--space-12);
  border: 1px solid var(--border-warm);
  border-radius: var(--radius-card);
  background: var(--primary-soft);
}

.ai-scene-recommendation__icon {
  display: grid;
  width: var(--space-34);
  height: var(--space-34);
  place-items: center;
  border-radius: var(--radius-pill);
  background: var(--card-bg);
  color: var(--primary);
  font-size: var(--icon-size-md);
}

.ai-scene-recommendation__content {
  display: grid;
  min-width: 0;
  gap: var(--space-5);
  color: var(--text-secondary);
  font-size: var(--font-size-caption);
  line-height: var(--line-height-caption);
}

.ai-scene-recommendation__content strong {
  color: var(--text-main);
  font-size: var(--font-size-body-strong);
  line-height: var(--line-height-body-strong);
  overflow-wrap: anywhere;
}

.ai-scene-recommendation__content span {
  overflow-wrap: anywhere;
}

.ai-scene-recommendation__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-8);
  padding-top: var(--space-4);
}

.ai-scene-recommendation__undo {
  justify-self: start;
  border: 0;
  background: transparent;
  color: var(--primary);
  font: inherit;
  font-weight: 750;
}
</style>
