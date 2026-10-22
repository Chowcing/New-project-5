<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import BottomSheet from '@/components/BottomSheet.vue'
import { appendCalculatorKey, calculateAmount } from '@/utils/amountCalculator'

const props = defineProps<{ show: boolean; amount: string }>()
const emit = defineEmits<{ (event: 'update:show', value: boolean): void; (event: 'apply', value: string): void }>()
const expression = ref('')
const result = computed(() => calculateAmount(expression.value))
const keys = ['C', '⌫', '÷', '×', '7', '8', '9', '-', '4', '5', '6', '+', '1', '2', '3', '=', '0', '.']
watch(() => props.show, (show) => {
  if (show) expression.value = /^\d+(\.\d*)?$/.test(props.amount) ? props.amount : ''
})
function press(key: string) {
  if (key === 'C') expression.value = ''
  else if (key === '⌫') expression.value = expression.value.slice(0, -1)
  else if (key === '=') {
    if (result.value.value) expression.value = result.value.value
  } else expression.value = appendCalculatorKey(expression.value, key)
}
function apply() {
  if (!result.value.value) return
  emit('apply', result.value.value)
  emit('update:show', false)
}
</script>

<template>
  <BottomSheet :show="show" title="计算器" @update:show="emit('update:show', $event)">
    <div class="amount-calculator">
      <div class="amount-calculator__expression" aria-label="计算式">{{ expression || '0' }}</div>
      <div class="amount-calculator__result" aria-live="polite">{{ result.value ? `¥${result.value}` : result.error }}</div>
      <div class="amount-calculator__keys">
        <button v-for="key in keys" :key="key" type="button"
          :aria-label="key === 'C' ? '清空' : key === '⌫' ? '退格' : key === '=' ? '计算结果' : key"
          :class="['amount-calculator__key', { 'amount-calculator__key--operator': !/^[\d.]$/.test(key), 'amount-calculator__key--zero': key === '0' }]"
          @click="press(key)">{{ key === 'C' ? '清空' : key }}</button>
      </div>
      <van-button block type="primary" icon="success" aria-label="使用结果" :disabled="!result.value" @click="apply">使用结果</van-button>
    </div>
  </BottomSheet>
</template>

<style scoped>
.amount-calculator { display: grid; gap: var(--space-12); }
.amount-calculator__expression { overflow-wrap: anywhere; text-align: right; color: var(--text-main); font-size: var(--font-size-section-title); }
.amount-calculator__result { text-align: right; color: var(--text-secondary); font-size: var(--font-size-section-title); }
.amount-calculator__keys { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: var(--space-8); }
.amount-calculator__key { min-height: var(--space-48); border-radius: var(--radius-card); background: var(--primary-soft); color: var(--text-main); font-size: var(--font-size-section-title); touch-action: manipulation; }
.amount-calculator__key--operator { color: var(--primary); font-weight: 700; }
.amount-calculator__key--zero { grid-column: span 3; }
</style>
