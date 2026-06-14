<script setup lang="ts">
import { ref, watch } from 'vue'

const props = defineProps<{
  modelValue: boolean
  title: string
  target: string
  confirmText?: string
  loading?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  confirm: [reason: string]
}>()

const reason = ref('')
const error = ref('')

watch(() => props.modelValue, (visible) => {
  if (visible) {
    reason.value = ''
    error.value = ''
  }
})

function close() {
  if (!props.loading) {
    emit('update:modelValue', false)
  }
}

function submit() {
  const trimmed = reason.value.trim()
  if (!trimmed) {
    error.value = '请填写操作原因'
    return
  }
  error.value = ''
  emit('confirm', trimmed)
}
</script>

<template>
  <van-dialog
    :show="modelValue"
    :title="title"
    show-cancel-button
    :confirm-button-text="confirmText || '确认'"
    :before-close="() => false"
    :confirm-button-loading="loading"
    @cancel="close"
    @confirm="submit"
  >
    <div class="reason-dialog-body">
      <div class="reason-target">{{ target }}</div>
      <van-field
        v-model="reason"
        rows="3"
        autosize
        type="textarea"
        maxlength="120"
        show-word-limit
        placeholder="填写本次管理操作原因"
        :error-message="error"
      />
    </div>
  </van-dialog>
</template>

<style scoped>
.reason-dialog-body {
  padding: var(--space-12) var(--space-16) var(--space-4);
}

.reason-target {
  margin-bottom: var(--space-10);
  padding: var(--space-10);
  border: 1px solid var(--border-warm);
  border-radius: var(--radius-inner);
  color: var(--text-secondary);
  font-size: var(--font-size-meta);
  line-height: var(--line-height-meta);
  background: var(--card-bg-warm);
  word-break: break-word;
}
</style>
