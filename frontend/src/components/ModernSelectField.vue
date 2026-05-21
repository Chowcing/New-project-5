<script setup lang="ts">
import { computed, ref } from 'vue'

type SelectValue = string | number | undefined

interface SelectOption {
  label: string
  value: SelectValue
  icon?: string
  color?: string
  description?: string
  disabled?: boolean
}

const props = withDefaults(defineProps<{
  modelValue?: SelectValue
  label: string
  options: SelectOption[]
  placeholder?: string
  title?: string
  required?: boolean
  disabled?: boolean
  inputAlign?: 'left' | 'center' | 'right'
}>(), {
  placeholder: '请选择',
  inputAlign: 'right'
})

const emit = defineEmits<{
  'update:modelValue': [value: SelectValue]
  change: [value: SelectValue]
}>()

const visible = ref(false)

const selectedOption = computed(() => props.options.find((item) => item.value === props.modelValue))
const displayValue = computed(() => selectedOption.value?.label || '')
const sheetTitle = computed(() => props.title || props.label)

function open() {
  if (!props.disabled) {
    visible.value = true
  }
}

function selectOption(option: SelectOption) {
  if (option.disabled) {
    return
  }
  emit('update:modelValue', option.value)
  emit('change', option.value)
  visible.value = false
}

defineExpose({
  open
})
</script>

<template>
  <van-field
    :label="label"
    :model-value="displayValue"
    :placeholder="placeholder"
    :required="required"
    :disabled="disabled"
    :input-align="inputAlign"
    readonly
    is-link
    class="modern-select-field"
    @click="open"
  >
    <template v-if="$slots.button" #button>
      <slot name="button" />
    </template>
  </van-field>

  <van-popup v-model:show="visible" position="bottom" round class="modern-select-popup">
    <div class="modern-select-sheet">
      <header class="modern-select-header">
        <button type="button" class="modern-select-text-button" @click="visible = false">
          <van-icon name="cross" />
          <span>取消</span>
        </button>
        <strong>{{ sheetTitle }}</strong>
        <span class="modern-select-header-spacer" />
      </header>

      <div class="modern-select-list">
        <button
          v-for="item in options"
          :key="String(item.value)"
          type="button"
          :class="['modern-select-option', { active: item.value === modelValue, disabled: item.disabled }]"
          :disabled="item.disabled"
          @click="selectOption(item)"
        >
          <span v-if="item.icon || item.color" class="modern-select-icon" :style="{ color: item.color || undefined }">
            <van-icon v-if="item.icon" :name="item.icon" />
            <span v-else class="modern-select-color-dot" :style="{ backgroundColor: item.color }" />
          </span>
          <span class="modern-select-option-copy">
            <span class="modern-select-option-label">{{ item.label }}</span>
            <span v-if="item.description" class="modern-select-option-description">{{ item.description }}</span>
          </span>
          <van-icon v-if="item.value === modelValue" class="modern-select-check" name="success" />
        </button>

        <div v-if="options.length === 0" class="modern-select-empty">暂无可选项</div>
      </div>
    </div>
  </van-popup>
</template>

<style scoped>
.modern-select-field {
  --van-field-input-text-color: var(--text-main);
}

.modern-select-popup {
  overflow: hidden;
}

.modern-select-sheet {
  max-height: min(72vh, 560px);
  padding: var(--space-6) var(--space-0) max(var(--space-14), env(safe-area-inset-bottom));
  background: var(--page-bg-soft);
}

.modern-select-header {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr) 72px;
  align-items: center;
  min-height: 48px;
  padding: var(--space-0) var(--space-12);
  background: var(--card-bg);
  border-bottom: 1px solid var(--border-warm);
}

.modern-select-header strong {
  overflow: hidden;
  font-size: var(--font-size-control);
  font-weight: 650;
  text-align: center;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.modern-select-text-button {
  display: inline-flex;
  align-items: center;
  gap: var(--space-3);
  border: 0;
  background: transparent;
  color: var(--text-secondary);
  font: inherit;
  text-align: left;
}

.modern-select-header-spacer {
  width: 72px;
}

.modern-select-list {
  display: grid;
  gap: var(--space-8);
  max-height: calc(min(72vh, 560px) - 54px);
  padding: var(--space-12);
  overflow-y: auto;
}

.modern-select-option {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) 22px;
  gap: var(--space-10);
  align-items: center;
  width: 100%;
  min-height: 48px;
  padding: var(--space-11) var(--space-12);
  border: 1px solid var(--border-warm);
  border-radius: var(--radius-card);
  background: var(--card-bg);
  color: var(--text-main);
  font: inherit;
  text-align: left;
}

.modern-select-option.active {
  border-color: var(--primary);
  background: var(--primary-soft);
}

.modern-select-option.disabled {
  color: var(--text-muted);
  background: #f7eadb;
}

.modern-select-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: var(--radius-card);
  background: var(--card-bg-warm);
  font-size: var(--font-size-title-lg);
}

.modern-select-color-dot {
  width: 12px;
  height: 12px;
  border-radius: var(--radius-pill);
}

.modern-select-option-copy {
  min-width: 0;
}

.modern-select-option-label,
.modern-select-option-description {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.modern-select-option-label {
  font-size: var(--font-size-body-strong);
  font-weight: 500;
}

.modern-select-option-description {
  margin-top: var(--space-2);
  color: var(--text-secondary);
  font-size: var(--font-size-caption);
}

.modern-select-check {
  color: var(--primary);
  font-size: var(--font-size-title-lg);
}

.modern-select-empty {
  padding: var(--space-28) var(--space-0);
  color: var(--text-muted);
  text-align: center;
}
</style>
