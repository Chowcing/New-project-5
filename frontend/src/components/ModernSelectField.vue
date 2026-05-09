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
        <button type="button" class="modern-select-text-button" @click="visible = false">取消</button>
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
  --van-field-input-text-color: #1f2933;
}

.modern-select-popup {
  overflow: hidden;
}

.modern-select-sheet {
  max-height: min(72vh, 560px);
  padding: 6px 0 max(14px, env(safe-area-inset-bottom));
  background: #f8fafc;
}

.modern-select-header {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr) 72px;
  align-items: center;
  min-height: 48px;
  padding: 0 12px;
  background: #fff;
  border-bottom: 1px solid #edf0f3;
}

.modern-select-header strong {
  overflow: hidden;
  font-size: 16px;
  font-weight: 650;
  text-align: center;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.modern-select-text-button {
  border: 0;
  background: transparent;
  color: #667085;
  font: inherit;
  text-align: left;
}

.modern-select-header-spacer {
  width: 72px;
}

.modern-select-list {
  display: grid;
  gap: 8px;
  max-height: calc(min(72vh, 560px) - 54px);
  padding: 12px;
  overflow-y: auto;
}

.modern-select-option {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) 22px;
  gap: 10px;
  align-items: center;
  width: 100%;
  min-height: 48px;
  padding: 11px 12px;
  border: 1px solid #e6eaf0;
  border-radius: 8px;
  background: #fff;
  color: #1f2933;
  font: inherit;
  text-align: left;
}

.modern-select-option.active {
  border-color: var(--primary);
  background: #eef8f4;
}

.modern-select-option.disabled {
  color: #98a2b3;
  background: #f2f4f7;
}

.modern-select-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 8px;
  background: #f1f5f9;
  font-size: 18px;
}

.modern-select-color-dot {
  width: 12px;
  height: 12px;
  border-radius: 999px;
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
  font-size: 15px;
  font-weight: 500;
}

.modern-select-option-description {
  margin-top: 2px;
  color: #667085;
  font-size: 12px;
}

.modern-select-check {
  color: var(--primary);
  font-size: 18px;
}

.modern-select-empty {
  padding: 28px 0;
  color: #8a949b;
  text-align: center;
}
</style>
