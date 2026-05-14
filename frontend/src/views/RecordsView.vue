<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter, type LocationQueryValue } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { categoryApi, paymentMethodApi, transactionApi } from '@/api/services'
import ModernDateField from '@/components/ModernDateField.vue'
import ModernSelectField from '@/components/ModernSelectField.vue'
import type { Category, PaymentMethod, TransactionDayCard, TransactionDayOption, TransactionRecord } from '@/types'
import { currentMonth, money, nowLocalInput, todayDate, toBackendDateTime } from '@/utils/date'
import { showError } from '@/utils/errors'
import { loadDayRecordPageSize, loadRecordsViewMode, saveRecordsViewMode, type RecordsViewMode } from '@/utils/preferences'

const categories = ref<Category[]>([])
const paymentMethods = ref<PaymentMethod[]>([])
const dayCards = ref<TransactionDayCard[]>([])
const dayOptions = ref<TransactionDayOption[]>([])
const route = useRoute()
const router = useRouter()
const dayPageSize = 30
const dayRecordPageSize = loadDayRecordPageSize()
const totalRecords = ref(0)
const totalDays = ref(0)
const activeDayIndex = ref(0)
const recordsViewMode = ref<RecordsViewMode>(loadRecordsViewMode())
const swipeStartX = ref(0)
const swipeStartY = ref(0)
const dayDragOffset = ref(0)
const dayDragging = ref(false)
const daySwipeIgnored = ref(false)
const dayTransitionName = ref('day-slide-older')
const loadingMoreDayRecords = ref<string | null>(null)
const filterPopupVisible = ref(false)
const recordsLoading = ref(true)
const recordActionId = ref<number | null>(null)
const recordActionType = ref<'copy' | 'delete' | ''>('')
let lastDayOptionsFilterKey = ''

type RecordsQuery = {
  type: '' | 'EXPENSE' | 'INCOME'
  startDate: string
  endDate: string
  channel: '' | 'ONLINE' | 'OFFLINE'
  categoryId: number | ''
  paymentMethodId: number | ''
  keyword: string
  dayPage: number
}

function defaultQuery(): RecordsQuery {
  return {
    type: '',
    startDate: `${currentMonth()}-01`,
    endDate: todayDate(),
    channel: '',
    categoryId: '',
    paymentMethodId: '',
    keyword: '',
    dayPage: 1
  }
}

const query = reactive(defaultQuery())

const filteredCategories = computed(() => {
  if (!query.type) {
    return categories.value
  }
  return categories.value.filter((item) => item.type === query.type)
})
const typeOptions = [
  { label: '全部', value: '' },
  { label: '支出', value: 'EXPENSE' },
  { label: '收入', value: 'INCOME' }
]
const channelOptions = [
  { label: '全部渠道', value: '' },
  { label: '线上', value: 'ONLINE' },
  { label: '线下', value: 'OFFLINE' }
]
const categoryOptions = computed(() => [
  { label: '全部分类', value: '' },
  ...filteredCategories.value.map((item) => ({
    label: item.name,
    value: item.id,
    icon: item.icon || 'records-o',
    color: item.color
  }))
])
const paymentMethodOptions = computed(() => [
  { label: '全部支付方式', value: '' },
  ...paymentMethods.value.map((item) => ({
    label: item.name,
    value: item.id,
    icon: item.icon || 'balance-o'
  }))
])
const totalDayPages = computed(() => Math.max(Math.ceil(totalDays.value / dayPageSize), 1))
const dayWindowStart = computed(() => (totalDays.value === 0 ? 0 : (query.dayPage - 1) * dayPageSize + 1))
const dayWindowEnd = computed(() => Math.min(query.dayPage * dayPageSize, totalDays.value))
const hasNewerDayWindow = computed(() => query.dayPage > 1)
const hasOlderDayWindow = computed(() => query.dayPage < totalDayPages.value)
const activeDayPosition = computed(() => (query.dayPage - 1) * dayPageSize + activeDayIndex.value + 1)
const activeDay = computed(() => dayCards.value[activeDayIndex.value])
const activeDayDate = computed(() => activeDay.value?.date)
const activeDayLoadedRecordCount = computed(() => activeDay.value?.records.records.length ?? 0)
const activeDayHasMoreRecords = computed(() => {
  if (!activeDay.value) {
    return false
  }
  return activeDayLoadedRecordCount.value < activeDay.value.records.total
})
const defaultDateRange = computed(() => ({
  startDate: `${currentMonth()}-01`,
  endDate: todayDate()
}))
const dateRangeText = computed(() => `${query.startDate || '不限'} 至 ${query.endDate || '不限'}`)
const activeFilterTags = computed(() => {
  const tags: string[] = []
  if (query.type) {
    tags.push(query.type === 'EXPENSE' ? '支出' : '收入')
  }
  if (query.categoryId !== '') {
    tags.push(categoryName(query.categoryId))
  }
  if (query.channel) {
    tags.push(query.channel === 'ONLINE' ? '线上' : '线下')
  }
  if (query.paymentMethodId !== '') {
    tags.push(paymentMethodName(query.paymentMethodId))
  }
  if (query.keyword.trim()) {
    tags.push(`搜索：${query.keyword.trim()}`)
  }
  if (query.startDate !== defaultDateRange.value.startDate || query.endDate !== defaultDateRange.value.endDate) {
    tags.push(dateRangeText.value)
  }
  return tags
})
const activeFilterCount = computed(() => activeFilterTags.value.length)
const dayJumpOptions = computed(() => dayOptions.value.map((item, index) => ({
  label: `${dayTitle(item.date)} · ${item.date}`,
  value: item.date,
  icon: 'calendar-o',
  description: `第 ${index + 1} 天 · ${item.transactionCount} 笔 · 支出 ¥${money(item.totalExpense)} · 收入 ¥${money(item.totalIncome)}`
})))
const isStackMode = computed(() => recordsViewMode.value === 'stack')
const dayDragProgress = computed(() => Math.min(Math.abs(dayDragOffset.value) / 88, 1))
const dayCardDragStyle = computed(() => {
  if (!dayDragging.value && dayDragOffset.value === 0) {
    return {}
  }
  const progress = dayDragProgress.value
  return {
    transform: `translate3d(${dayDragOffset.value}px, 0, 0) scale(${(1 - progress * 0.018).toFixed(4)})`,
    opacity: (1 - progress * 0.03).toFixed(3),
    boxShadow: `0 ${14 + progress * 8}px ${30 + progress * 18}px rgba(31, 41, 51, ${0.08 + progress * 0.06})`,
    transition: dayDragging.value
      ? 'none'
      : 'transform 220ms cubic-bezier(0.22, 1, 0.36, 1), opacity 180ms ease, box-shadow 220ms ease'
  }
})

let dayDragFrame = 0
let pendingDayDragOffset = 0
let pendingDayDragging = false

function cancelDayDragFrame() {
  if (dayDragFrame === 0) {
    return
  }
  cancelAnimationFrame(dayDragFrame)
  dayDragFrame = 0
}

function commitDayDragFrame() {
  dayDragFrame = 0
  dayDragOffset.value = pendingDayDragOffset
  dayDragging.value = pendingDayDragging
}

function scheduleDayDrag(offset: number, dragging: boolean) {
  pendingDayDragOffset = offset
  pendingDayDragging = dragging
  if (dayDragFrame !== 0) {
    return
  }
  dayDragFrame = requestAnimationFrame(commitDayDragFrame)
}

function firstQueryValue(value: LocationQueryValue | LocationQueryValue[]) {
  return Array.isArray(value) ? value[0] : value
}

function isTransactionType(value: LocationQueryValue): value is 'EXPENSE' | 'INCOME' {
  return value === 'EXPENSE' || value === 'INCOME'
}

function isTransactionChannel(value: LocationQueryValue): value is 'ONLINE' | 'OFFLINE' {
  return value === 'ONLINE' || value === 'OFFLINE'
}

function isDateString(value: LocationQueryValue): value is string {
  return typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value)
}

function positiveInteger(value: LocationQueryValue) {
  if (typeof value !== 'string' || !/^\d+$/.test(value)) {
    return undefined
  }
  const numberValue = Number(value)
  return numberValue > 0 ? numberValue : undefined
}

function stackDayId(date: string) {
  return `records-stack-day-${date}`
}

async function scrollToStackDay(date: string, behavior: ScrollBehavior = 'auto') {
  await nextTick()
  document.getElementById(stackDayId(date))?.scrollIntoView({
    behavior,
    block: 'start'
  })
}

async function syncModeViewport(mode: RecordsViewMode) {
  await nextTick()
  if (mode === 'stack') {
    if (activeDayDate.value) {
      await scrollToStackDay(activeDayDate.value)
    }
    return
  }
  document.querySelector('.records-section')?.scrollIntoView({
    behavior: 'auto',
    block: 'start'
  })
}

function routeQueryFromFilters(dayPage = query.dayPage) {
  const nextQuery: Record<string, string> = {}
  if (query.type) nextQuery.type = query.type
  if (query.startDate) nextQuery.startDate = query.startDate
  if (query.endDate) nextQuery.endDate = query.endDate
  if (query.channel) nextQuery.channel = query.channel
  if (query.categoryId !== '') nextQuery.categoryId = String(query.categoryId)
  if (query.paymentMethodId !== '') nextQuery.paymentMethodId = String(query.paymentMethodId)
  if (query.keyword.trim()) nextQuery.keyword = query.keyword.trim()
  if (dayPage > 1) nextQuery.dayPage = String(dayPage)
  return nextQuery
}

function clearInvalidCategory() {
  if (categories.value.length === 0) {
    return
  }
  if (query.categoryId !== '' && !filteredCategories.value.some((item) => item.id === query.categoryId)) {
    query.categoryId = ''
  }
}

function applyRouteQuery() {
  const type = firstQueryValue(route.query.type)
  const startDate = firstQueryValue(route.query.startDate)
  const endDate = firstQueryValue(route.query.endDate)
  const channel = firstQueryValue(route.query.channel)
  const categoryId = positiveInteger(firstQueryValue(route.query.categoryId))
  const paymentMethodId = positiveInteger(firstQueryValue(route.query.paymentMethodId))
  const keyword = firstQueryValue(route.query.keyword)
  const dayPage = positiveInteger(firstQueryValue(route.query.dayPage)) ?? positiveInteger(firstQueryValue(route.query.page))

  query.type = isTransactionType(type) ? type : ''
  query.startDate = isDateString(startDate) ? startDate : `${currentMonth()}-01`
  query.endDate = isDateString(endDate) ? endDate : todayDate()
  query.channel = isTransactionChannel(channel) ? channel : ''
  query.categoryId = categoryId ?? ''
  query.paymentMethodId = paymentMethodId ?? ''
  query.keyword = typeof keyword === 'string' ? keyword : ''
  query.dayPage = dayPage ?? 1
}

function filterParams() {
  return {
    type: query.type || undefined,
    channel: query.channel || undefined,
    categoryId: query.categoryId === '' ? undefined : query.categoryId,
    paymentMethodId: query.paymentMethodId === '' ? undefined : query.paymentMethodId,
    keyword: query.keyword || undefined
  }
}

function dayOptionsFilterKey() {
  return JSON.stringify({
    ...filterParams(),
    startDate: query.startDate || undefined,
    endDate: query.endDate || undefined
  })
}

async function loadDayOptions(force = false) {
  const nextKey = dayOptionsFilterKey()
  if (!force && nextKey === lastDayOptionsFilterKey) {
    return
  }
  try {
    dayOptions.value = await transactionApi.dailyOptions({
      ...filterParams(),
      startDate: query.startDate || undefined,
      endDate: query.endDate || undefined
    })
    lastDayOptionsFilterKey = nextKey
  } catch (error) {
    showError(error, '日期列表加载失败')
  }
}

async function load(dayPage = 1, nextActiveDayIndex?: number) {
  query.dayPage = dayPage
  recordsLoading.value = true
  try {
    const result = await transactionApi.dailyCards({
      ...filterParams(),
      startDate: query.startDate || undefined,
      endDate: query.endDate || undefined,
      dayPage: query.dayPage,
      daySize: dayPageSize,
      recordPage: 1,
      recordSize: dayRecordPageSize
    })
    if (result.days.length === 0 && dayPage > 1 && result.totalDays > 0) {
      await applyFilters(dayPage - 1)
      return
    }
    dayCards.value = result.days
    totalRecords.value = result.totalRecords
    totalDays.value = result.totalDays
    activeDayIndex.value = Math.min(nextActiveDayIndex ?? 0, Math.max(result.days.length - 1, 0))
  } catch (error) {
    showError(error, '记录加载失败')
  } finally {
    recordsLoading.value = false
  }
}

function setType(value: string | number | undefined) {
  query.type = value === 'EXPENSE' || value === 'INCOME' ? value : ''
  clearInvalidCategory()
  void applyFilters(1)
}

function setChannel(value: string | number | undefined) {
  query.channel = value === 'ONLINE' || value === 'OFFLINE' ? value : ''
  void applyFilters(1)
}

function setCategory(value: string | number | undefined) {
  query.categoryId = typeof value === 'number' ? value : ''
  void applyFilters(1)
}

function setPaymentMethod(value: string | number | undefined) {
  query.paymentMethodId = typeof value === 'number' ? value : ''
  void applyFilters(1)
}

function categoryName(id: number) {
  return categories.value.find((item) => item.id === id)?.name || '未知分类'
}

function paymentMethodName(id: number) {
  return paymentMethods.value.find((item) => item.id === id)?.name || '未知支付方式'
}

function recordActionLoading(id: number, type: 'copy' | 'delete') {
  return recordActionId.value === id && recordActionType.value === type
}

async function jumpToDate(value: string | number | undefined) {
  if (typeof value !== 'string') {
    return
  }
  const targetOptionIndex = dayOptions.value.findIndex((item) => item.date === value)
  if (targetOptionIndex < 0) {
    showToast('所选日期不在当前筛选结果中')
    return
  }
  const targetDayNumber = targetOptionIndex + 1
  if (targetDayNumber === activeDayPosition.value) {
    return
  }
  const targetPage = Math.ceil(targetDayNumber / dayPageSize)
  const targetIndex = targetOptionIndex % dayPageSize
  dayTransitionName.value = targetDayNumber > activeDayPosition.value ? 'day-slide-older' : 'day-slide-newer'
  if (targetPage === query.dayPage) {
    activeDayIndex.value = Math.min(targetIndex, Math.max(dayCards.value.length - 1, 0))
    if (isStackMode.value) {
      void scrollToStackDay(value, 'smooth')
    }
    return
  }
  await load(targetPage, targetIndex)
  if (isStackMode.value) {
    void scrollToStackDay(value, 'smooth')
  }
}

async function applyFilters(dayPage = 1) {
  query.dayPage = dayPage
  await router.replace({
    path: '/records',
    query: routeQueryFromFilters(dayPage)
  })
}

async function applyFilterPopup() {
  filterPopupVisible.value = false
  await applyFilters(1)
}

async function resetFilters() {
  Object.assign(query, defaultQuery())
  if (Object.keys(route.query).length > 0) {
    await router.replace({ path: '/records', query: {} })
    return
  }
  lastDayOptionsFilterKey = ''
  await Promise.all([load(1), loadDayOptions(true)])
}

async function resetFiltersFromPopup() {
  filterPopupVisible.value = false
  await resetFilters()
}

async function init() {
  applyRouteQuery()
  try {
    const [categoryRows, paymentMethodRows] = await Promise.all([
      categoryApi.list(),
      paymentMethodApi.list()
    ])
    categories.value = categoryRows
    paymentMethods.value = paymentMethodRows
  } catch (error) {
    showError(error, '筛选项加载失败')
  }
  await Promise.all([load(query.dayPage), loadDayOptions(true)])
}

async function removeRecord(id: number) {
  if (recordActionId.value !== null) {
    return
  }
  try {
    await showConfirmDialog({ title: '删除记录', message: '确认删除这条记录？' })
  } catch {
    return
  }
  recordActionId.value = id
  recordActionType.value = 'delete'
  try {
    await transactionApi.remove(id)
    showToast('已删除')
    await load(query.dayPage, activeDayIndex.value)
    await loadDayOptions(true)
  } catch (error) {
    showError(error, '删除失败')
  } finally {
    recordActionId.value = null
    recordActionType.value = ''
  }
}

async function copyRecord(item: TransactionRecord) {
  if (recordActionId.value !== null) {
    return
  }
  recordActionId.value = item.id
  recordActionType.value = 'copy'
  try {
    const created = await transactionApi.create({
      type: item.type,
      itemName: item.itemName,
      amount: Number(item.amount),
      occurredAt: toBackendDateTime(nowLocalInput()),
      channel: item.channel,
      onlineApp: item.channel === 'ONLINE' ? item.onlineApp : undefined,
      offlinePlace: item.channel === 'OFFLINE' ? item.offlinePlace : undefined,
      paymentMethodId: item.paymentMethodId,
      categoryId: item.categoryId,
      note: item.note
    })
    showToast('已复制为新记录')
    await routerPushRecord(created.id)
  } catch (error) {
    showError(error, '复制失败')
  } finally {
    recordActionId.value = null
    recordActionType.value = ''
  }
}

async function routerPushRecord(id: number) {
  await router.push({
    path: `/records/${id}`,
    query: routeQueryFromFilters(query.dayPage)
  })
}

function contextText(item: TransactionRecord) {
  const channel = item.channel === 'ONLINE' ? '线上' : '线下'
  const placeOrApp = item.channel === 'ONLINE' ? item.onlineApp : item.offlinePlace
  return [channel, placeOrApp, item.paymentMethodName].filter(Boolean).join(' · ')
}

function parseDate(value: string) {
  const [year, month, day] = value.split('-').map(Number)
  return new Date(year, month - 1, day)
}

function formatDate(value: Date) {
  const year = value.getFullYear()
  const month = String(value.getMonth() + 1).padStart(2, '0')
  const day = String(value.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function offsetDate(value: string, offset: number) {
  const date = parseDate(value)
  date.setDate(date.getDate() + offset)
  return formatDate(date)
}

function dayTitle(value: string) {
  const today = todayDate()
  if (value === today) return '今天'
  if (value === offsetDate(today, -1)) return '昨天'
  if (value === offsetDate(today, -2)) return '前天'
  return `${Number(value.slice(5, 7))}月${Number(value.slice(8, 10))}日`
}

function daySubtitle(value: string) {
  const weekdayLabels = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
  return `${value} · ${weekdayLabels[parseDate(value).getDay()]}`
}

function recordTime(value: string) {
  return value.slice(11, 16)
}

async function loadDayRecords(date: string, page: number, append = false) {
  try {
    const result = await transactionApi.list({
      ...filterParams(),
      startDate: date,
      endDate: date,
      page,
      size: dayRecordPageSize
    })
    const index = dayCards.value.findIndex((item) => item.date === date)
    if (index < 0) {
      return
    }
    const nextDays = [...dayCards.value]
    nextDays[index] = {
      ...nextDays[index],
      records: append
        ? {
            ...result,
            records: [...nextDays[index].records.records, ...result.records]
          }
        : result
    }
    dayCards.value = nextDays
  } catch (error) {
    showError(error, '当天记录加载失败')
  }
}

async function loadMoreDayRecords(date: string) {
  const day = dayCards.value.find((item) => item.date === date)
  if (loadingMoreDayRecords.value !== null || !day || day.records.records.length >= day.records.total) {
    return
  }
  loadingMoreDayRecords.value = date
  try {
    await loadDayRecords(date, day.records.page + 1, true)
  } finally {
    if (loadingMoreDayRecords.value === date) {
      loadingMoreDayRecords.value = null
    }
  }
}

async function showOlderDayWindow() {
  if (!hasOlderDayWindow.value) {
    return
  }
  await applyFilters(query.dayPage + 1)
}

async function showNewerDayWindow() {
  if (!hasNewerDayWindow.value) {
    return
  }
  await applyFilters(query.dayPage - 1)
}

async function showOlderDay() {
  dayTransitionName.value = 'day-slide-older'
  if (activeDayIndex.value < dayCards.value.length - 1) {
    activeDayIndex.value += 1
    return
  }
  if (query.dayPage * dayPageSize < totalDays.value) {
    await load(query.dayPage + 1, 0)
    return
  }
  if (totalDays.value > 1) {
    if (query.dayPage === 1) {
      activeDayIndex.value = 0
      return
    }
    await load(1, 0)
  }
}

async function showNewerDay() {
  dayTransitionName.value = 'day-slide-newer'
  if (activeDayIndex.value > 0) {
    activeDayIndex.value -= 1
    return
  }
  if (query.dayPage > 1) {
    await load(query.dayPage - 1, dayPageSize - 1)
    return
  }
  if (totalDays.value > 1) {
    const lastPage = Math.ceil(totalDays.value / dayPageSize)
    if (lastPage === query.dayPage) {
      activeDayIndex.value = dayCards.value.length - 1
      return
    }
    await load(lastPage, dayPageSize - 1)
  }
}

function onDayTouchStart(event: TouchEvent) {
  const target = event.target
  daySwipeIgnored.value = target instanceof Element && Boolean(target.closest('.record-swipe-cell'))
  if (daySwipeIgnored.value) {
    return
  }
  const touch = event.touches[0]
  if (!touch) return
  cancelDayDragFrame()
  swipeStartX.value = touch.clientX
  swipeStartY.value = touch.clientY
  dayDragOffset.value = 0
  dayDragging.value = false
}

function onDayTouchMove(event: TouchEvent) {
  if (daySwipeIgnored.value) {
    return
  }
  const touch = event.touches[0]
  if (!touch) return
  const deltaX = touch.clientX - swipeStartX.value
  const deltaY = touch.clientY - swipeStartY.value
  if (Math.abs(deltaX) < 8 || Math.abs(deltaX) < Math.abs(deltaY) * 1.2) {
    return
  }
  scheduleDayDrag(Math.max(-88, Math.min(88, deltaX * 0.45)), true)
}

function onDayTouchEnd(event: TouchEvent) {
  if (daySwipeIgnored.value) {
    daySwipeIgnored.value = false
    cancelDayDragFrame()
    return
  }
  const touch = event.changedTouches[0]
  if (!touch) return
  const deltaX = touch.clientX - swipeStartX.value
  const deltaY = touch.clientY - swipeStartY.value
  cancelDayDragFrame()
  dayDragging.value = false
  dayDragOffset.value = 0
  if (Math.abs(deltaX) < 45 || Math.abs(deltaX) < Math.abs(deltaY) * 1.2) {
    return
  }
  if (deltaX > 0) {
    void showOlderDay()
    return
  }
  void showNewerDay()
}

watch(() => query.type, () => {
  clearInvalidCategory()
})

watch(recordsViewMode, (value) => {
  saveRecordsViewMode(value)
  void syncModeViewport(value)
})

watch(() => route.query, async () => {
  applyRouteQuery()
  await Promise.all([load(query.dayPage), loadDayOptions()])
})

onMounted(init)
onBeforeUnmount(cancelDayDragFrame)
</script>

<template>
  <main class="page">
    <van-nav-bar title="收支明细" />
    <div class="page-content">
      <section class="section panel records-filter-panel">
        <van-field
          v-model="query.keyword"
          left-icon="search"
          clearable
          placeholder="搜索事项、备注、地点、APP、支付方式"
          @clear="applyFilters(1)"
          @keyup.enter="applyFilters(1)"
        >
          <template #button>
            <div class="filter-actions">
              <van-button size="small" type="primary" @click="applyFilters(1)">筛选</van-button>
              <van-button size="small" plain type="primary" icon="filter-o" @click="filterPopupVisible = true">
                更多
              </van-button>
            </div>
          </template>
        </van-field>
        <div class="filter-summary">
          <div class="filter-date-summary">
            <van-icon name="calendar-o" />
            <span>{{ dateRangeText }}</span>
          </div>
          <div v-if="activeFilterTags.length" class="filter-tags">
            <span v-for="tag in activeFilterTags" :key="tag" class="filter-tag">{{ tag }}</span>
          </div>
          <div v-else class="filter-empty">默认显示本月至今记录</div>
        </div>
        <div class="view-mode-row">
          <div class="view-mode-label">查看模式</div>
          <van-radio-group v-model="recordsViewMode" class="view-mode-switch" direction="horizontal">
            <van-radio name="card">卡片</van-radio>
            <van-radio name="stack">列表</van-radio>
          </van-radio-group>
        </div>
      </section>

      <section class="section records-section">
        <div v-if="totalRecords > 0 && !recordsLoading" class="records-meta">
          <span>共 {{ totalRecords }} 条记录 · {{ totalDays }} 天</span>
          <span v-if="dayCards.length > 0">{{ activeDayPosition }} / {{ totalDays }} 天</span>
        </div>
        <div v-if="totalDays > dayPageSize && !recordsLoading" class="records-tip">
          当前显示 {{ dayCards.length }} 天，底部可直接选择筛选结果中的日期
        </div>
        <div v-if="recordsLoading" class="panel records-loading">
          <van-loading size="22px">正在加载记录</van-loading>
        </div>
        <div v-else-if="dayCards.length === 0" class="panel empty-text">没有符合条件的记录</div>
        <div
          v-else-if="!isStackMode"
          class="day-card-stage"
          @touchstart.passive="onDayTouchStart"
          @touchmove.passive="onDayTouchMove"
          @touchend="onDayTouchEnd"
        >
          <Transition :name="dayTransitionName">
            <article v-if="activeDay" :key="activeDay.date" class="day-card" :style="dayCardDragStyle">
              <header class="day-card-header">
                <div class="day-heading-row">
                  <van-button
                    class="day-nav-button"
                    plain
                    type="primary"
                    icon="arrow-left"
                    aria-label="查看更早一天"
                    :disabled="totalDays <= 1"
                    @click.stop="showOlderDay"
                  />
                  <div class="day-heading">
                    <div class="day-title">{{ dayTitle(activeDay.date) }}</div>
                    <div class="day-subtitle">{{ daySubtitle(activeDay.date) }} · {{ activeDay.transactionCount }} 笔</div>
                  </div>
                  <van-button
                    class="day-nav-button"
                    plain
                    type="primary"
                    icon="arrow"
                    aria-label="查看更新一天"
                    :disabled="totalDays <= 1"
                    @click.stop="showNewerDay"
                  />
                </div>
                <div class="day-summary">
                  <div class="day-summary-line expense">支出 ¥{{ money(activeDay.totalExpense) }}</div>
                  <div class="day-summary-line income">收入 ¥{{ money(activeDay.totalIncome) }}</div>
                  <div class="day-summary-line">结余 ¥{{ money(activeDay.balance) }}</div>
                </div>
              </header>

              <div class="day-records">
                <van-swipe-cell
                  v-for="item in activeDay.records.records"
                  :key="item.id"
                  class="record-swipe-cell"
                  :disabled="recordActionId !== null"
                >
                  <div
                    class="record-row"
                    role="button"
                    tabindex="0"
                    @click="routerPushRecord(item.id)"
                    @keyup.enter="routerPushRecord(item.id)"
                  >
                    <div :class="['record-type-mark', item.type === 'EXPENSE' ? 'expense-mark' : 'income-mark']">
                      {{ item.categoryName.slice(0, 1) }}
                    </div>
                    <div class="record-main">
                      <div class="record-title">{{ item.itemName || item.categoryName }}</div>
                      <div class="record-meta">{{ recordTime(item.occurredAt) }} · {{ contextText(item) }}</div>
                      <div class="record-note">{{ item.categoryName }} · {{ item.note || '无备注' }}</div>
                    </div>
                    <div class="record-side">
                      <div :class="['record-amount', item.type === 'EXPENSE' ? 'expense' : 'income']">
                        {{ item.type === 'EXPENSE' ? '-' : '+' }}¥{{ money(item.amount) }}
                      </div>
                    </div>
                  </div>
                  <template #right>
                    <van-button
                      class="record-swipe-action"
                      square
                      type="primary"
                      icon="description-o"
                      :loading="recordActionLoading(item.id, 'copy')"
                      @click="copyRecord(item)"
                    >
                      复制
                    </van-button>
                    <van-button
                      class="record-swipe-action"
                      square
                      type="danger"
                      icon="delete-o"
                      :loading="recordActionLoading(item.id, 'delete')"
                      @click="removeRecord(item.id)"
                    >
                      删除
                    </van-button>
                  </template>
                </van-swipe-cell>
              </div>

              <div v-if="activeDay.records.total > dayRecordPageSize" class="load-more-records">
                  <van-button
                    v-if="activeDayHasMoreRecords"
                    block
                    plain
                    type="primary"
                    icon="arrow-down"
                    :loading="loadingMoreDayRecords === activeDay.date"
                    @click="loadMoreDayRecords(activeDay.date)"
                  >
                    加载更多当天记录 {{ activeDayLoadedRecordCount }} / {{ activeDay.records.total }}
                  </van-button>
                <div v-else class="all-loaded-text">当天 {{ activeDay.records.total }} 条记录已全部显示</div>
              </div>
            </article>
          </Transition>
        </div>
        <div v-else class="day-stack-list">
          <article
            v-for="day in dayCards"
            :id="stackDayId(day.date)"
            :key="day.date"
            :class="['day-card', 'day-card-stack', { active: day.date === activeDayDate }]"
          >
            <header class="day-card-header day-card-header-stack">
              <div class="day-heading">
                <div class="day-title">{{ dayTitle(day.date) }}</div>
                <div class="day-subtitle">{{ daySubtitle(day.date) }} · {{ day.transactionCount }} 笔</div>
              </div>
              <div class="day-summary">
                <div class="day-summary-line expense">支出 ¥{{ money(day.totalExpense) }}</div>
                <div class="day-summary-line income">收入 ¥{{ money(day.totalIncome) }}</div>
                <div class="day-summary-line">结余 ¥{{ money(day.balance) }}</div>
              </div>
            </header>

            <div class="day-records day-records-stack">
              <van-swipe-cell
                v-for="item in day.records.records"
                :key="item.id"
                class="record-swipe-cell"
                :disabled="recordActionId !== null"
              >
                <div
                  class="record-row"
                  role="button"
                  tabindex="0"
                  @click="routerPushRecord(item.id)"
                  @keyup.enter="routerPushRecord(item.id)"
                >
                  <div :class="['record-type-mark', item.type === 'EXPENSE' ? 'expense-mark' : 'income-mark']">
                    {{ item.categoryName.slice(0, 1) }}
                  </div>
                  <div class="record-main">
                    <div class="record-title">{{ item.itemName || item.categoryName }}</div>
                    <div class="record-meta">{{ recordTime(item.occurredAt) }} · {{ contextText(item) }}</div>
                    <div class="record-note">{{ item.categoryName }} · {{ item.note || '无备注' }}</div>
                  </div>
                  <div class="record-side">
                    <div :class="['record-amount', item.type === 'EXPENSE' ? 'expense' : 'income']">
                      {{ item.type === 'EXPENSE' ? '-' : '+' }}¥{{ money(item.amount) }}
                    </div>
                  </div>
                </div>
                <template #right>
                  <van-button
                    class="record-swipe-action"
                    square
                    type="primary"
                    icon="description-o"
                    :loading="recordActionLoading(item.id, 'copy')"
                    @click="copyRecord(item)"
                  >
                    复制
                  </van-button>
                  <van-button
                    class="record-swipe-action"
                    square
                    type="danger"
                    icon="delete-o"
                    :loading="recordActionLoading(item.id, 'delete')"
                    @click="removeRecord(item.id)"
                  >
                    删除
                  </van-button>
                </template>
              </van-swipe-cell>
            </div>

            <div v-if="day.records.total > dayRecordPageSize" class="load-more-records">
              <van-button
                v-if="day.records.records.length < day.records.total"
                block
                plain
                type="primary"
                icon="arrow-down"
                :loading="loadingMoreDayRecords === day.date"
                :disabled="loadingMoreDayRecords !== null"
                @click="loadMoreDayRecords(day.date)"
              >
                加载更多当天记录 {{ day.records.records.length }} / {{ day.records.total }}
              </van-button>
              <div v-else class="all-loaded-text">当天 {{ day.records.total }} 条记录已全部显示</div>
            </div>
          </article>
        </div>
        <div v-if="!recordsLoading && totalDays > dayPageSize" class="day-window-nav">
          <div class="day-window-summary">
            当前显示第 {{ dayWindowStart }} - {{ dayWindowEnd }} 天，共 {{ totalDays }} 天
          </div>
          <div class="day-window-actions">
            <van-button
              v-if="hasNewerDayWindow"
              plain
              type="primary"
              icon="arrow-left"
              @click="showNewerDayWindow"
            >
              查看更新 {{ dayPageSize }} 天
            </van-button>
            <van-button
              v-if="hasOlderDayWindow"
              plain
              type="primary"
              icon="arrow"
              @click="showOlderDayWindow"
            >
              查看更早 {{ dayPageSize }} 天
            </van-button>
          </div>
        </div>
        <div v-if="!recordsLoading && totalDays > 1" class="day-jump panel">
          <ModernSelectField
            :model-value="activeDayDate"
            label="跳转日期"
            title="选择筛选结果中的日期"
            placeholder="选择日期"
            :options="dayJumpOptions"
            @update:model-value="jumpToDate"
          />
        </div>
      </section>
    </div>

    <van-popup v-model:show="filterPopupVisible" position="bottom" round>
      <div class="filter-popup">
        <div class="filter-popup-header">
          <div>
            <div class="filter-popup-title">筛选记录</div>
            <div class="filter-popup-subtitle">
              {{ activeFilterCount > 0 ? `已启用 ${activeFilterCount} 个条件` : '默认显示本月至今' }}
            </div>
          </div>
          <van-button size="small" plain type="default" @click="resetFiltersFromPopup">重置</van-button>
        </div>
        <div class="filter-popup-body">
          <ModernSelectField
            :model-value="query.type"
            label="类型"
            title="选择类型"
            :options="typeOptions"
            @update:model-value="setType"
          />
          <ModernSelectField
            :model-value="query.categoryId"
            label="分类"
            title="选择分类"
            :options="categoryOptions"
            @update:model-value="setCategory"
          />
          <ModernSelectField
            :model-value="query.channel"
            label="渠道"
            title="选择渠道"
            :options="channelOptions"
            @update:model-value="setChannel"
          />
          <ModernSelectField
            :model-value="query.paymentMethodId"
            label="支付方式"
            title="选择支付方式"
            :options="paymentMethodOptions"
            @update:model-value="setPaymentMethod"
          />
          <ModernDateField v-model="query.startDate" mode="date" label="开始" title="选择开始日期" @change="applyFilters(1)" />
          <ModernDateField v-model="query.endDate" mode="date" label="结束" title="选择结束日期" @change="applyFilters(1)" />
        </div>
        <div class="filter-popup-actions">
          <van-button block round type="primary" @click="applyFilterPopup">完成</van-button>
        </div>
      </div>
    </van-popup>
  </main>
</template>

<style scoped>
.records-section {
  min-width: 0;
}

.records-filter-panel {
  padding: 0;
  overflow: hidden;
}

.filter-summary {
  padding: 8px 12px 12px;
  border-top: 1px solid #eef1f4;
}

.filter-date-summary {
  display: flex;
  gap: 6px;
  align-items: center;
  color: #5f6c72;
  font-size: 12px;
  line-height: 18px;
}

.filter-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.filter-tag {
  max-width: 100%;
  min-height: 24px;
  padding: 3px 8px;
  border-radius: 999px;
  background: #f2f5f4;
  color: #3f4c51;
  font-size: 12px;
  line-height: 18px;
  overflow-wrap: anywhere;
}

.filter-empty {
  margin-top: 6px;
  color: #8a949b;
  font-size: 12px;
  line-height: 18px;
}

.view-mode-row {
  display: grid;
  gap: 8px;
  padding: 10px 12px 12px;
  border-top: 1px solid #eef1f4;
}

.view-mode-label {
  color: #6b7280;
  font-size: 12px;
  line-height: 18px;
}

.view-mode-switch {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px;
  padding: 4px;
  border-radius: 8px;
  background: #f2f5f4;
}

.view-mode-switch :deep(.van-radio) {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 32px;
  margin-right: 0;
  padding: 0 12px;
  border-radius: 6px;
  color: #6b7280;
  font-size: 13px;
  font-weight: 600;
  line-height: 20px;
  transition: color 0.2s ease, background 0.2s ease, box-shadow 0.2s ease;
}

.view-mode-switch :deep(.van-radio__icon) {
  display: none;
}

.view-mode-switch :deep(.van-radio__label) {
  margin: 0;
}

.view-mode-switch :deep(.van-radio[aria-checked='true']) {
  background: #fff;
  border-radius: 6px;
  box-shadow: 0 6px 16px rgba(31, 41, 51, 0.08);
  color: var(--primary);
}

.view-mode-switch :deep(.van-radio[aria-checked='true'] .van-radio__label) {
  color: var(--primary);
}

.records-meta {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 0 2px 8px;
  color: #6b7280;
  font-size: 13px;
}

.records-tip {
  padding: 0 2px 8px;
  color: #8a949b;
  font-size: 12px;
}

.records-loading {
  display: grid;
  place-items: center;
  min-height: 220px;
}

.day-jump {
  margin-top: 12px;
  padding: 0;
}

.day-jump :deep(.van-cell) {
  border-radius: 8px;
}

.day-card-stage {
  position: relative;
  height: clamp(420px, 62dvh, 560px);
  margin-bottom: 22px;
  overflow: hidden;
  touch-action: pan-y;
  user-select: none;
  perspective: 1200px;
}

.day-slide-older-enter-active,
.day-slide-older-leave-active,
.day-slide-newer-enter-active,
.day-slide-newer-leave-active {
  transition: transform 240ms cubic-bezier(0.22, 1, 0.36, 1), opacity 200ms ease;
  will-change: transform, opacity;
}

.day-stack-list {
  display: grid;
  gap: 12px;
}

.day-slide-older-enter-from {
  opacity: 0;
  transform: translate3d(-40px, 0, 0) scale(0.965);
}

.day-slide-older-leave-to {
  opacity: 0;
  transform: translate3d(40px, 0, 0) scale(0.965);
}

.day-slide-newer-enter-from {
  opacity: 0;
  transform: translate3d(40px, 0, 0) scale(0.965);
}

.day-slide-newer-leave-to {
  opacity: 0;
  transform: translate3d(-40px, 0, 0) scale(0.965);
}

.day-card {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  margin: 0 1px;
  overflow: hidden;
  border-radius: 8px;
  background: #fff;
  border: 1px solid rgba(238, 241, 244, 0.92);
  box-shadow: 0 12px 28px rgba(31, 41, 51, 0.08);
  backface-visibility: hidden;
  transform-origin: center center;
  will-change: transform, opacity;
}

.day-card-stack {
  position: relative;
  inset: auto;
  margin: 0;
  scroll-margin-top: 12px;
}

.day-card-stack.active {
  border-color: rgba(47, 125, 104, 0.26);
  box-shadow: 0 14px 30px rgba(31, 41, 51, 0.1);
}

.day-card-header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 14px 12px;
  border-bottom: 1px solid #eef1f4;
}

.day-card-header-stack {
  display: grid;
  gap: 12px;
}

.day-card-header-stack .day-summary {
  text-align: left;
}

.day-heading-row {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr) 34px;
  gap: 8px;
  align-items: center;
  min-width: 0;
}

.day-heading {
  min-width: 0;
  text-align: center;
}

.day-nav-button {
  width: 34px;
  height: 32px;
  padding: 0;
}

.day-title {
  font-size: 20px;
  font-weight: 700;
  line-height: 28px;
}

.day-subtitle {
  margin-top: 3px;
  color: #6b7280;
  font-size: 12px;
  line-height: 18px;
}

.day-summary {
  flex: 0 0 auto;
  text-align: right;
  font-size: 12px;
  line-height: 18px;
}

.day-summary-line {
  white-space: nowrap;
}

.day-records {
  flex: 1 1 auto;
  min-height: 0;
  padding: 4px 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  -webkit-overflow-scrolling: touch;
}

.day-records-stack {
  overflow: visible;
}

.record-swipe-cell {
  background: #fff;
}

.record-row {
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
  min-height: 66px;
  padding: 10px 12px;
  cursor: pointer;
}

.record-swipe-cell:not(:last-child) .record-row {
  border-bottom: 1px solid #f0f2f5;
}

.record-type-mark {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: 8px;
  font-size: 15px;
  font-weight: 700;
}

.expense-mark {
  background: #fff0f0;
  color: var(--expense);
}

.income-mark {
  background: #edf8f1;
  color: var(--income);
}

.record-main {
  min-width: 0;
}

.record-title {
  overflow: hidden;
  color: #1f2933;
  font-size: 15px;
  font-weight: 600;
  line-height: 21px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.record-meta,
.record-note {
  overflow: hidden;
  color: #6b7280;
  font-size: 12px;
  line-height: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.record-note {
  color: #8a949b;
}

.record-side {
  display: grid;
  justify-items: end;
  gap: 6px;
}

.record-amount {
  white-space: nowrap;
  font-size: 14px;
  font-weight: 700;
  line-height: 20px;
}

.record-swipe-action {
  width: 64px;
  height: 100%;
}

.load-more-records {
  padding: 8px 12px 14px;
  border-top: 1px solid #f0f2f5;
}

.all-loaded-text {
  padding: 6px 0 2px;
  color: #8a949b;
  font-size: 12px;
  line-height: 18px;
  text-align: center;
}

.day-window-nav {
  margin-top: 12px;
  padding: 12px;
  border-radius: 8px;
  background: #fff;
}

.day-window-summary {
  margin-bottom: 10px;
  color: #6b7280;
  font-size: 12px;
  line-height: 18px;
}

.day-window-actions {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(132px, 1fr));
  gap: 8px;
}

.day-window-actions :deep(.van-button__text) {
  white-space: nowrap;
}

.filter-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.filter-popup {
  max-height: min(78vh, 620px);
  padding: 14px 0 max(14px, env(safe-area-inset-bottom));
  overflow-y: auto;
  background: #fff;
}

.filter-popup-header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  padding: 0 14px 12px;
  border-bottom: 1px solid #eef1f4;
}

.filter-popup-title {
  color: #1f2933;
  font-size: 17px;
  font-weight: 700;
  line-height: 24px;
}

.filter-popup-subtitle {
  margin-top: 2px;
  color: #8a949b;
  font-size: 12px;
  line-height: 18px;
}

.filter-popup-body {
  padding-top: 4px;
}

.filter-popup-actions {
  padding: 14px 12px 0;
}

@media (max-width: 360px) {
  .day-card-header {
    display: grid;
  }

  .day-summary {
    text-align: left;
  }

  .day-heading-row {
    grid-template-columns: 32px minmax(0, 1fr) 32px;
  }

  .day-nav-button {
    width: 32px;
  }

  .record-row {
    grid-template-columns: 34px minmax(0, 1fr);
  }

  .record-type-mark {
    width: 32px;
    height: 32px;
  }

  .record-side {
    grid-column: 2;
    grid-row: 2;
    justify-items: start;
  }
}
</style>
