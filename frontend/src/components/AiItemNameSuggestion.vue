<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { transactionApi } from '@/api/services'
import BottomSheet from '@/components/BottomSheet.vue'

const props = defineProps<{ itemName: string }>()
const emit = defineEmits<{ apply: [suggestion: string] }>()
const visible = defineModel<boolean>('show', { required: true })

const loading = ref(false)
const suggestion = ref('')
const error = ref('')
const itemName = computed(() => props.itemName.trim())
const canRequest = computed(() => itemName.value.length > 0 && itemName.value.length <= 64)
let activeRequest: AbortController | undefined

function reset() {
  activeRequest?.abort()
  activeRequest = undefined
  loading.value = false
  suggestion.value = ''
  error.value = ''
}

function close() {
  visible.value = false
  reset()
}

async function requestSuggestion() {
  if (!visible.value || !canRequest.value || activeRequest) return
  const source = itemName.value
  const request = new AbortController()
  activeRequest = request
  loading.value = true
  error.value = ''
  try {
    const result = await transactionApi.aiItemNameSuggestion(source, request.signal)
    if (request.signal.aborted || itemName.value !== source) return
    suggestion.value = result
  } catch {
    if (!request.signal.aborted) error.value = '暂时无法生成建议，请稍后重试'
  } finally {
    if (activeRequest === request) {
      activeRequest = undefined
      loading.value = false
    }
  }
}

function applySuggestion() {
  if (!suggestion.value || suggestion.value === itemName.value) return
  emit('apply', suggestion.value)
  close()
}

watch(() => props.itemName, close)
onBeforeUnmount(close)
</script>

<template>
  <van-button
    size="small"
    plain
    type="primary"
    icon="edit"
    native-type="button"
    :disabled="!canRequest"
    @click="visible = true"
  >
    AI 优化
  </van-button>

  <BottomSheet :show="visible" title="AI 优化事项" @update:show="close">
    <div class="ai-item-suggestion">
      <p class="ai-item-suggestion__original">{{ itemName }}</p>
      <template v-if="suggestion">
        <p v-if="suggestion === itemName" role="status">当前事项已经清楚，可以直接使用。</p>
        <template v-else>
          <p role="status">建议改为「{{ suggestion }}」</p>
          <van-button block round type="primary" icon="success" native-type="button" @click="applySuggestion">
            采用建议
          </van-button>
        </template>
      </template>
      <template v-else>
        <p>生成建议时，仅将上方的事项发给 AI 服务。</p>
        <p v-if="error" role="alert">{{ error }}</p>
        <van-button
          block
          round
          type="primary"
          icon="edit"
          native-type="button"
          :loading="loading"
          loading-text="正在生成建议"
          @click="requestSuggestion"
        >
          {{ error ? '重试' : '生成建议' }}
        </van-button>
      </template>
    </div>
  </BottomSheet>
</template>

<style scoped>
.ai-item-suggestion {
  display: grid;
  gap: var(--space-12);
  color: var(--text-secondary);
  overflow-wrap: anywhere;
}

.ai-item-suggestion p {
  margin: var(--space-0);
}

.ai-item-suggestion__original {
  padding: var(--space-12);
  border-radius: var(--radius-card);
  background: var(--surface-muted);
  color: var(--text-main);
}
</style>
