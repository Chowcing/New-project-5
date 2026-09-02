<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import BottomSheet from '@/components/BottomSheet.vue'
import { haptic, hapticSelection } from '@/utils/haptics'
import { useVisualFeedback } from '@/utils/visualFeedback'
import {
  buildCalendarMonth,
  buildMonthGrid,
  buildYearGrid,
  clampDateParts,
  dateValue,
  formatDateParts,
  localDateTimeParts,
  monthValue,
  parseDateParts,
  todayValue,
  two,
  type CalendarDayCell,
  type CalendarMode,
  type DateParts
} from '@/utils/calendarPicker'

type DateMode = CalendarMode
type TimeColumnType = 'hour' | 'minute'

const props = withDefaults(defineProps<{
  modelValue?: string
  label: string
  mode: DateMode
  title?: string
  placeholder?: string
  required?: boolean
  disabled?: boolean
  minDate?: Date
  maxDate?: Date
  availableDates?: string[]
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
const tempParts = ref<DateParts>(parseDateParts(''))
const viewYear = ref(tempParts.value.year)
const viewMonth = ref(tempParts.value.month)
const tempTime = ref(['00', '00'])
const timeColumns: TimeColumnType[] = ['hour', 'minute']
const timeInputVisible = ref(false)
const timeInputType = ref<TimeColumnType>('hour')
const timeInputDraft = ref('')
const timeInputError = ref('')
const timeInputElement = ref<HTMLInputElement>()
const timePickerContainer = ref<HTMLElement>()
const { visualFeedback, triggerVisualFeedback } = useVisualFeedback()

const resolvedMinDate = computed(() => props.minDate || new Date(2000, 0, 1))
const resolvedMaxDate = computed(() => props.maxDate || new Date(new Date().getFullYear() + 10, 11, 31))
const sheetTitle = computed(() => props.title || props.label)
const selectedDate = computed(() => formatDateParts(tempParts.value, 'date'))
const selectedMonth = computed(() => monthValue(tempParts.value.year, tempParts.value.month))
const selectedYear = computed(() => String(tempParts.value.year))
const timeInputTitle = computed(() => timeInputType.value === 'hour' ? '输入小时' : '输入分钟')
const timeInputLabel = computed(() => timeInputType.value === 'hour' ? '小时' : '分钟')
const todayDate = ref(todayValue())
const calendarMonth = computed(() => buildCalendarMonth(viewYear.value, viewMonth.value, {
  selectedDate: selectedDate.value,
  minDate: resolvedMinDate.value,
  maxDate: resolvedMaxDate.value,
  availableDates: props.availableDates
}))
const monthOptions = computed(() => buildMonthGrid(viewYear.value, selectedMonth.value, resolvedMinDate.value, resolvedMaxDate.value))
const yearOptions = computed(() => buildYearGrid(viewYear.value, selectedYear.value, resolvedMinDate.value, resolvedMaxDate.value))
const canGoPrevious = computed(() => {
  if (props.mode === 'year') {
    const currentStartYear = Math.floor(viewYear.value / 10) * 10
    return currentStartYear + 1 >= resolvedMinDate.value.getFullYear()
  }
  if (props.mode === 'month') {
    return viewYear.value > resolvedMinDate.value.getFullYear()
  }
  const previousMonth = new Date(viewYear.value, viewMonth.value - 2, 1)
  return previousMonth >= new Date(resolvedMinDate.value.getFullYear(), resolvedMinDate.value.getMonth(), 1)
})
const canGoNext = computed(() => {
  if (props.mode === 'year') {
    const nextStartYear = Math.floor(viewYear.value / 10) * 10 + 10
    return nextStartYear <= resolvedMaxDate.value.getFullYear()
  }
  if (props.mode === 'month') {
    return viewYear.value < resolvedMaxDate.value.getFullYear()
  }
  const nextMonth = new Date(viewYear.value, viewMonth.value, 1)
  return nextMonth <= new Date(resolvedMaxDate.value.getFullYear(), resolvedMaxDate.value.getMonth(), 1)
})
const calendarTitle = computed(() => {
  if (props.mode === 'year') {
    const startYear = Math.floor(viewYear.value / 10) * 10
    return `${startYear} - ${startYear + 11}`
  }
  if (props.mode === 'month') {
    return `${viewYear.value}年`
  }
  return calendarMonth.value.title
})
function canChooseDate(value: string) {
  if (props.availableDates?.length && !props.availableDates.includes(value)) {
    return false
  }
  const clamped = formatDateParts(
    clampDateParts(parseDateParts(value), resolvedMinDate.value, resolvedMaxDate.value),
    'date'
  )
  return clamped === value
}

const canChooseToday = computed(() => canChooseDate(todayDate.value))

const displayValue = computed(() => {
  if (!props.modelValue) {
    return ''
  }
  const parts = parseDateParts(props.modelValue)
  if (props.mode === 'year') {
    return `${parts.year}年`
  }
  if (props.mode === 'month') {
    return `${parts.year}年${two(parts.month)}月`
  }
  if (props.mode === 'date') {
    return `${parts.year}年${two(parts.month)}月${two(parts.day)}日`
  }
  return `${parts.year}年${two(parts.month)}月${two(parts.day)}日 ${two(parts.hour ?? 0)}:${two(parts.minute ?? 0)}`
})

function syncTempFromValue() {
  const parts = clampDateParts(parseDateParts(props.modelValue), resolvedMinDate.value, resolvedMaxDate.value)
  tempParts.value = parts
  viewYear.value = parts.year
  viewMonth.value = parts.month
  tempTime.value = [two(parts.hour ?? 0), two(parts.minute ?? 0)]
}

function open() {
  if (props.disabled) {
    return
  }
  haptic('tap')
  todayDate.value = todayValue()
  syncTempFromValue()
  resetTimeInputEditor()
  visible.value = true
}

function cancel() {
  haptic('tap')
  resetTimeInputEditor()
  visible.value = false
}

function goPrevious() {
  if (!canGoPrevious.value) return
  hapticSelection()
  if (props.mode === 'year') {
    viewYear.value -= 10
  } else if (props.mode === 'month') {
    viewYear.value -= 1
  } else if (viewMonth.value === 1) {
    viewYear.value -= 1
    viewMonth.value = 12
  } else {
    viewMonth.value -= 1
  }
}

function goNext() {
  if (!canGoNext.value) return
  hapticSelection()
  if (props.mode === 'year') {
    viewYear.value += 10
  } else if (props.mode === 'month') {
    viewYear.value += 1
  } else if (viewMonth.value === 12) {
    viewYear.value += 1
    viewMonth.value = 1
  } else {
    viewMonth.value += 1
  }
}

function chooseDay(day: CalendarDayCell) {
  if (day.disabled) return
  hapticSelection()
  const [year, month, date] = day.date.split('-').map(Number)
  tempParts.value = {
    ...tempParts.value,
    year,
    month,
    day: date
  }
  viewYear.value = year
  viewMonth.value = month
}

function chooseMonth(value: string, disabled: boolean) {
  if (disabled) return
  hapticSelection()
  const [year, month] = value.split('-').map(Number)
  tempParts.value = clampDateParts({
    ...tempParts.value,
    year,
    month
  }, resolvedMinDate.value, resolvedMaxDate.value)
  viewYear.value = tempParts.value.year
  viewMonth.value = tempParts.value.month
}

function chooseYear(value: string, disabled: boolean) {
  if (disabled) return
  hapticSelection()
  tempParts.value = clampDateParts({
    ...tempParts.value,
    year: Number(value)
  }, resolvedMinDate.value, resolvedMaxDate.value)
  viewYear.value = tempParts.value.year
  viewMonth.value = tempParts.value.month
}

function chooseToday() {
  const nowParts = localDateTimeParts()
  const nowDate = dateValue(nowParts)
  if (!canChooseDate(nowDate)) return

  const todayParts = clampDateParts(nowParts, resolvedMinDate.value, resolvedMaxDate.value)
  hapticSelection()
  tempParts.value = {
    ...tempParts.value,
    ...todayParts
  }
  if (props.mode === 'datetime') {
    tempTime.value = [two(nowParts.hour ?? 0), two(nowParts.minute ?? 0)]
  }
  viewYear.value = todayParts.year
  viewMonth.value = todayParts.month
}

function onTimeUpdate(values: unknown[]) {
  hapticSelection()
  tempTime.value = [
    two(String(values[0] || '00')),
    two(String(values[1] || '00'))
  ]
}

async function openTimeInput(type: TimeColumnType) {
  timeInputType.value = type
  timeInputDraft.value = tempTime.value[type === 'hour' ? 0 : 1]
  timeInputError.value = ''
  timeInputVisible.value = true
  await nextTick()
  timeInputElement.value?.focus()
  timeInputElement.value?.select()
}

function onTimePickerClick(event: MouseEvent) {
  const target = event.target
  const picker = event.currentTarget
  if (!(target instanceof Element) || !(picker instanceof HTMLElement)) return

  const selectedItem = target.closest('.van-picker-column__item--selected')
  const column = target.closest('.van-picker-column')
  if (!selectedItem || !column) return

  const columnIndex = Array.from(picker.querySelectorAll('.van-picker-column')).indexOf(column)
  if (columnIndex === 0 || columnIndex === 1) {
    void openTimeInput(timeColumns[columnIndex])
  }
}

watch(timePickerContainer, (container, _, onCleanup) => {
  if (!container) return
  container.addEventListener('click', onTimePickerClick, { capture: true })
  onCleanup(() => container.removeEventListener('click', onTimePickerClick, { capture: true }))
})

function resetTimeInputEditor() {
  timeInputDraft.value = ''
  timeInputError.value = ''
}

function cancelTimeInput() {
  haptic('tap')
  timeInputVisible.value = false
}

function confirmTimeInput() {
  const max = timeInputType.value === 'hour' ? 23 : 59
  if (!/^\d{1,2}$/.test(timeInputDraft.value)) {
    timeInputError.value = `请输入 0–${max} 的${timeInputLabel.value}`
    return
  }

  const value = Number(timeInputDraft.value)
  if (value < 0 || value > max) {
    timeInputError.value = `请输入 0–${max} 的${timeInputLabel.value}`
    return
  }

  haptic('confirm')
  const nextTime = [...tempTime.value]
  nextTime[timeInputType.value === 'hour' ? 0 : 1] = two(value)
  tempTime.value = nextTime
  timeInputVisible.value = false
}

function confirm() {
  haptic('confirm')
  triggerVisualFeedback('confirm')
  const [hour, minute] = tempTime.value.map(Number)
  const parts = clampDateParts({
    ...tempParts.value,
    hour,
    minute
  }, resolvedMinDate.value, resolvedMaxDate.value)
  const nextValue = formatDateParts(parts, props.mode)

  emit('update:modelValue', nextValue)
  emit('change', nextValue)
  window.setTimeout(() => {
    visible.value = false
  }, 120)
}
</script>

<template>
  <slot name="trigger" :display-value="displayValue" :open="open" :disabled="disabled">
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
      @click-input.stop="open"
      @click-right-icon.stop="open"
    />
  </slot>

  <BottomSheet
    v-model:show="visible"
    :title="sheetTitle"
    header-variant="toolbar"
    :height-variant="mode === 'datetime' ? 'viewport' : 'default'"
    :sheet-class="[
      visualFeedback ? `ui-feedback-${visualFeedback}` : '',
      { 'modern-date-sheet--datetime': mode === 'datetime' }
    ]"
    :body-class="[
      'modern-date-body',
      { 'modern-date-body--datetime': mode === 'datetime' }
    ]"
  >
    <template #leading>
      <button type="button" class="modern-date-text-button" @click="cancel">
        <van-icon name="cross" />
        <span>取消</span>
      </button>
    </template>
    <template #actions>
      <button
        type="button"
        :class="['modern-date-text-button', 'primary', { 'ui-feedback-confirm': visualFeedback === 'confirm' }]"
        @click="confirm"
      >
        <van-icon name="success" />
        <span>确定</span>
      </button>
    </template>

    <div :class="['modern-calendar', { 'modern-calendar--datetime': mode === 'datetime' }]">
      <div class="modern-calendar-toolbar">
        <button type="button" class="modern-calendar-nav" :disabled="!canGoPrevious" aria-label="上一个时间段" title="上一个时间段" @click="goPrevious">
          <van-icon name="arrow-left" />
        </button>
        <div class="modern-calendar-title">{{ calendarTitle }}</div>
        <button type="button" class="modern-calendar-nav" :disabled="!canGoNext" aria-label="下一个时间段" title="下一个时间段" @click="goNext">
          <van-icon name="arrow" />
        </button>
      </div>

      <template v-if="mode === 'year'">
        <div class="modern-calendar-option-grid">
          <button
            v-for="yearOption in yearOptions"
            :key="yearOption.value"
            type="button"
            :class="['modern-calendar-option', { selected: yearOption.selected }]"
            :disabled="yearOption.disabled"
            @click="chooseYear(yearOption.value, yearOption.disabled)"
          >
            {{ yearOption.label }}
          </button>
        </div>
      </template>

      <template v-else-if="mode === 'month'">
        <div class="modern-calendar-option-grid months">
          <button
            v-for="monthOption in monthOptions"
            :key="monthOption.value"
            type="button"
            :class="['modern-calendar-option', { selected: monthOption.selected }]"
            :disabled="monthOption.disabled"
            @click="chooseMonth(monthOption.value, monthOption.disabled)"
          >
            {{ monthOption.label }}
          </button>
        </div>
      </template>

      <template v-else>
        <div class="modern-calendar-weekdays">
          <span v-for="weekday in calendarMonth.weekdays" :key="weekday">{{ weekday }}</span>
        </div>
        <div class="modern-calendar-days">
          <template v-for="(week, weekIndex) in calendarMonth.weeks" :key="weekIndex">
            <button
              v-for="day in week"
              :key="day.key"
              type="button"
              :class="[
                'modern-calendar-day',
                {
                  outside: !day.inCurrentMonth,
                  selected: day.selected,
                  today: day.today
                }
              ]"
              :disabled="day.disabled"
              @click="chooseDay(day)"
            >
              <span>{{ day.day }}</span>
            </button>
          </template>
        </div>
        <button type="button" class="modern-calendar-today" :disabled="!canChooseToday" @click="chooseToday">
          <van-icon name="aim" />
          <span>今天</span>
        </button>
      </template>
    </div>

    <div v-if="mode === 'datetime'" ref="timePickerContainer" class="modern-time-picker-container">
      <van-time-picker
        :model-value="tempTime"
        :columns-type="timeColumns"
        :show-toolbar="false"
        :visible-option-num="3"
        :option-height="44"
        class="modern-time-picker"
        @update:model-value="onTimeUpdate"
      />
    </div>
  </BottomSheet>

  <BottomSheet
    v-model:show="timeInputVisible"
    :title="timeInputTitle"
    header-variant="toolbar"
    sheet-class="modern-time-input-sheet"
    @closed="resetTimeInputEditor"
  >
    <template #leading>
      <button type="button" class="modern-date-text-button" @click="cancelTimeInput">
        <van-icon name="cross" />
        <span>取消</span>
      </button>
    </template>
    <template #actions>
      <button type="button" class="modern-date-text-button primary" @click="confirmTimeInput">
        <van-icon name="success" />
        <span>确定</span>
      </button>
    </template>

    <div class="modern-time-input-body">
      <label class="modern-time-input-field">
        <span>{{ timeInputLabel }}</span>
        <input
          ref="timeInputElement"
          v-model="timeInputDraft"
          class="modern-time-input"
          type="text"
          inputmode="numeric"
          pattern="[0-9]*"
          :aria-label="`输入${timeInputLabel}`"
          :aria-invalid="Boolean(timeInputError)"
          @input="timeInputError = ''"
          @keydown.enter.prevent="confirmTimeInput"
        >
      </label>
      <p v-if="timeInputError" class="modern-time-input-error" role="alert">
        {{ timeInputError }}
      </p>
    </div>
  </BottomSheet>
</template>

<style scoped>
.modern-date-text-button {
  display: inline-flex;
  align-items: center;
  gap: var(--space-3);
  border: 0;
  background: transparent;
  color: var(--text-secondary);
  font: inherit;
  text-align: left;
  transition: transform var(--motion-fast) ease, color var(--motion-fast) ease, filter var(--motion-fast) ease;
}

.modern-date-text-button:active {
  transform: scale(0.96);
  filter: brightness(1.1);
}

.modern-date-text-button.primary {
  color: var(--primary);
  font-weight: 600;
  text-align: right;
}

.modern-calendar {
  display: grid;
  gap: var(--space-10);
  padding: var(--space-12);
}

.modern-calendar-toolbar {
  display: grid;
  grid-template-columns: 40px minmax(0, 1fr) 40px;
  align-items: center;
  gap: var(--space-8);
}

.modern-calendar-title {
  color: var(--text-main);
  font-size: var(--font-size-body-strong);
  font-weight: 600;
  text-align: center;
}

.modern-calendar-nav {
  display: inline-grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border: 1px solid var(--border-warm);
  border-radius: var(--radius-pill);
  background: var(--card-bg);
  color: var(--text-main);
  font: inherit;
}

.modern-calendar-nav:disabled {
  color: var(--text-muted);
  opacity: 0.45;
}

.modern-calendar-weekdays,
.modern-calendar-days {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: var(--space-5);
}

.modern-calendar-weekdays {
  color: var(--text-muted);
  font-size: var(--font-size-caption);
  text-align: center;
}

.modern-calendar-day {
  display: grid;
  place-items: center;
  width: 100%;
  min-width: 0;
  min-height: 44px;
  border: 1px solid transparent;
  border-radius: var(--radius-card);
  background: rgba(var(--theme-border-warm-rgb), 0.06);
  color: var(--text-main);
  font: inherit;
}

.modern-calendar-day.outside {
  color: var(--text-muted);
  opacity: 0.5;
}

.modern-calendar-day.today {
  border-color: var(--border-warm);
}

.modern-calendar-day.selected {
  border-color: var(--primary);
  background: var(--primary-soft);
  box-shadow: var(--inset-primary);
  color: var(--text-main);
  font-weight: 700;
}

.modern-calendar-day:disabled,
.modern-calendar-option:disabled {
  color: var(--text-muted);
  cursor: default;
  opacity: 0.38;
}

.modern-calendar-option-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-8);
}

.modern-calendar-option-grid.months {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.modern-calendar-option {
  min-height: 48px;
  border: 1px solid var(--border-warm);
  border-radius: var(--radius-card);
  background: var(--card-bg);
  color: var(--text-main);
  font: inherit;
}

.modern-calendar-option.selected {
  border-color: var(--primary);
  background: var(--primary-soft);
  box-shadow: var(--inset-primary);
  font-weight: 700;
}

.modern-calendar-today {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-4);
  min-height: 40px;
  border: 1px solid var(--border-warm);
  border-radius: var(--radius-pill);
  background: var(--card-bg);
  color: var(--primary);
  font: inherit;
  font-weight: 600;
}

.modern-calendar-today:disabled {
  color: var(--text-muted);
  cursor: default;
  opacity: 0.45;
}

.modern-time-picker {
  border-top: 8px solid var(--page-bg);
}

:deep(.modern-date-body) {
  padding-right: var(--space-0);
  padding-left: var(--space-0);
}

:deep(.modern-date-body .van-picker-column__item) {
  transition: color var(--motion-fast) ease, transform var(--motion-fast) ease, opacity var(--motion-fast) ease;
}

:deep(.modern-time-picker .van-picker-column__item--selected) {
  cursor: text;
}

.modern-time-input-body {
  display: grid;
  gap: var(--space-8);
  padding: var(--space-16) var(--space-0) var(--space-8);
}

.modern-time-input-field {
  display: grid;
  gap: var(--space-8);
  color: var(--text-secondary);
  font-size: var(--font-size-caption);
}

.modern-time-input {
  width: 100%;
  min-height: 48px;
  padding: var(--space-10) var(--space-12);
  border: 1px solid var(--border-warm);
  border-radius: var(--radius-card);
  outline: none;
  background: var(--surface-muted);
  color: var(--text-main);
  font: inherit;
  font-size: var(--font-size-body);
  line-height: var(--line-height-body);
  transition: border-color var(--motion-fast) ease, box-shadow var(--motion-fast) ease;
}

.modern-time-input:focus {
  border-color: var(--primary);
  box-shadow: var(--ring-primary-soft);
}

.modern-time-input[aria-invalid="true"] {
  border-color: var(--expense);
}

.modern-time-input-error {
  margin: var(--space-0);
  color: var(--expense);
  font-size: var(--font-size-caption);
  line-height: var(--line-height-caption);
}

@media (max-height: 740px) {
  .modern-calendar--datetime {
    gap: var(--space-6);
    padding: var(--space-8) var(--space-12);
  }

  .modern-calendar--datetime .modern-calendar-weekdays,
  .modern-calendar--datetime .modern-calendar-days {
    gap: var(--space-4) var(--space-5);
  }

  .modern-calendar--datetime .modern-calendar-day {
    min-height: 40px;
  }
}
</style>
