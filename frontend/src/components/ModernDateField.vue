<script setup lang="ts">
import { computed, ref } from 'vue'

type DateMode = 'year' | 'month' | 'date' | 'datetime'
type DateColumnType = 'year' | 'month' | 'day'
type TimeColumnType = 'hour' | 'minute'

const props = withDefaults(defineProps<{
  modelValue: string
  label: string
  mode: DateMode
  title?: string
  placeholder?: string
  required?: boolean
  disabled?: boolean
  minDate?: Date
  maxDate?: Date
  inputAlign?: 'left' | 'center' | 'right'
}>(), {
  placeholder: '请选择',
  inputAlign: 'right'
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
  change: [value: string]
}>()

const visible = ref(false)
const tempDate = ref(['', '', ''])
const tempTime = ref(['00', '00'])

const resolvedMinDate = computed(() => props.minDate || new Date(2000, 0, 1))
const resolvedMaxDate = computed(() => props.maxDate || new Date(new Date().getFullYear() + 10, 11, 31))

const dateColumns = computed<DateColumnType[]>(() => {
  if (props.mode === 'year') {
    return ['year']
  }
  return props.mode === 'month' ? ['year', 'month'] : ['year', 'month', 'day']
})
const datePickerValue = computed(() => tempDate.value.slice(0, dateColumns.value.length))
const timeColumns: TimeColumnType[] = ['hour', 'minute']
const sheetTitle = computed(() => props.title || props.label)

const displayValue = computed(() => {
  if (!props.modelValue) {
    return ''
  }
  const parts = parseValue(props.modelValue)
  if (props.mode === 'year') {
    return `${parts.year}年`
  }
  if (props.mode === 'month') {
    return `${parts.year}年${parts.month}月`
  }
  if (props.mode === 'date') {
    return `${parts.year}年${parts.month}月${parts.day}日`
  }
  return `${parts.year}年${parts.month}月${parts.day}日 ${parts.hour}:${parts.minute}`
})

function two(value: string | number) {
  return String(value).padStart(2, '0')
}

function localNowParts() {
  const now = new Date()
  return {
    year: String(now.getFullYear()),
    month: two(now.getMonth() + 1),
    day: two(now.getDate()),
    hour: two(now.getHours()),
    minute: two(now.getMinutes())
  }
}

function daysInMonth(year: string, month: string) {
  return new Date(Number(year), Number(month), 0).getDate()
}

function parseValue(value: string) {
  const fallback = localNowParts()
  const match = value.match(/^(\d{4})(?:-(\d{2})(?:-(\d{2})(?:[T ](\d{2}):(\d{2}))?)?)?/)
  if (!match) {
    return fallback
  }

  const year = match[1]
  const month = two(match[2] || fallback.month)
  const maxDay = daysInMonth(year, month)
  const day = two(Math.min(Number(match[3] || fallback.day), maxDay))
  return {
    year,
    month,
    day,
    hour: two(match[4] || fallback.hour),
    minute: two(match[5] || fallback.minute)
  }
}

function open() {
  if (props.disabled) {
    return
  }
  const parts = parseValue(props.modelValue)
  tempDate.value = [parts.year, parts.month, parts.day]
  tempTime.value = [parts.hour, parts.minute]
  visible.value = true
}

function normalizeDate(values: unknown[]) {
  const current = parseValue(props.modelValue)
  const year = String(values[0] || current.year)
  const month = two(String(values[1] || current.month))
  const day = two(Math.min(Number(values[2] || current.day), daysInMonth(year, month)))
  return [year, month, day]
}

function onDateUpdate(values: unknown[]) {
  tempDate.value = normalizeDate(values)
}

function onTimeUpdate(values: unknown[]) {
  tempTime.value = [
    two(String(values[0] || '00')),
    two(String(values[1] || '00'))
  ]
}

function confirm() {
  const [year, month, day] = normalizeDate(tempDate.value)
  const [hour, minute] = tempTime.value
  let nextValue = year

  if (props.mode === 'month') {
    nextValue = `${year}-${month}`
  } else if (props.mode === 'date') {
    nextValue = `${year}-${month}-${day}`
  } else if (props.mode === 'datetime') {
    nextValue = `${year}-${month}-${day}T${hour}:${minute}`
  }

  emit('update:modelValue', nextValue)
  emit('change', nextValue)
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
    @click="open"
  />

  <van-popup v-model:show="visible" position="bottom" round class="modern-date-popup">
    <div class="modern-date-sheet">
      <header class="modern-date-header">
        <button type="button" class="modern-date-text-button" @click="visible = false">取消</button>
        <strong>{{ sheetTitle }}</strong>
        <button type="button" class="modern-date-text-button primary" @click="confirm">确定</button>
      </header>

      <van-date-picker
        :model-value="datePickerValue"
        :columns-type="dateColumns"
        :min-date="resolvedMinDate"
        :max-date="resolvedMaxDate"
        :show-toolbar="false"
        @update:model-value="onDateUpdate"
      />

      <van-time-picker
        v-if="mode === 'datetime'"
        :model-value="tempTime"
        :columns-type="timeColumns"
        :show-toolbar="false"
        class="modern-time-picker"
        @update:model-value="onTimeUpdate"
      />
    </div>
  </van-popup>
</template>

<style scoped>
.modern-date-popup {
  overflow: hidden;
}

.modern-date-sheet {
  padding-bottom: max(12px, env(safe-area-inset-bottom));
  background: var(--card-bg);
}

.modern-date-header {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr) 72px;
  align-items: center;
  min-height: 48px;
  padding: 0 12px;
  border-bottom: 1px solid var(--border-warm);
}

.modern-date-header strong {
  overflow: hidden;
  font-size: 16px;
  font-weight: 650;
  text-align: center;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.modern-date-text-button {
  border: 0;
  background: transparent;
  color: var(--text-secondary);
  font: inherit;
  text-align: left;
}

.modern-date-text-button.primary {
  color: var(--primary);
  font-weight: 600;
  text-align: right;
}

.modern-time-picker {
  border-top: 8px solid var(--page-bg);
}
</style>
