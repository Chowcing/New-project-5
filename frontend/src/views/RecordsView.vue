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
import { haptic } from '@/utils/haptics'
import { useVisualFeedback } from '@/utils/visualFeedback'
import {
  loadDayRecordPageSize,
  loadRecordsQueryPreference,
  loadRecordsViewMode,
  saveRecordsQueryPreference,
  saveRecordsViewMode,
  type RecordsViewMode
} from '@/utils/preferences'

const categories = ref<Category[]>([])
const paymentMethods = ref<PaymentMethod[]>([])
const dayCards = ref<TransactionDayCard[]>([])
const dayOptions = ref<TransactionDayOption[]>([])
const route = useRoute()
const router = useRouter()
const recordsPageRef = ref<HTMLElement | null>(null)
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
const dayJumpPopupVisible = ref(false)
const recordsLoading = ref(true)
const recordActionId = ref<number | null>(null)
const recordActionType = ref<'copy' | 'delete' | ''>('')
const showBackTop = ref(false)
const { visualFeedback, triggerVisualFeedback } = useVisualFeedback()
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
    boxShadow: `0 ${14 + progress * 8}px ${30 + progress * 18}px rgba(var(--theme-shadow-warm-rgb), ${0.08 + progress * 0.06})`,
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

function openDayJumpPopup() {
  haptic('tap')
  triggerVisualFeedback('selection')
  dayJumpPopupVisible.value = true
}

function openFilterPopup() {
  haptic('tap')
  triggerVisualFeedback('selection')
  filterPopupVisible.value = true
}

async function chooseDayJump(value: string | number | undefined) {
  haptic('selection')
  triggerVisualFeedback('selection')
  dayJumpPopupVisible.value = false
  await jumpToDate(value)
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
  if (Object.keys(route.query).length === 0) {
    const savedQuery = loadRecordsQueryPreference()
    if (savedQuery) {
      Object.assign(query, savedQuery)
      return
    }
  }
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

function persistRecordsQuery() {
  saveRecordsQueryPreference({
    type: query.type,
    startDate: query.startDate,
    endDate: query.endDate,
    channel: query.channel,
    categoryId: query.categoryId,
    paymentMethodId: query.paymentMethodId,
    keyword: query.keyword,
    dayPage: query.dayPage
  })
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
  persistRecordsQuery()
  await router.replace({
    path: '/records',
    query: routeQueryFromFilters(dayPage)
  })
}

async function applySearchFilters() {
  haptic('tap')
  triggerVisualFeedback('confirm')
  await applyFilters(1)
}

async function applyFilterPopup() {
  haptic('confirm')
  triggerVisualFeedback('confirm')
  filterPopupVisible.value = false
  await applyFilters(1)
}

async function resetFilters() {
  Object.assign(query, defaultQuery())
  persistRecordsQuery()
  if (Object.keys(route.query).length > 0) {
    await router.replace({ path: '/records', query: {} })
    return
  }
  lastDayOptionsFilterKey = ''
  await Promise.all([load(1), loadDayOptions(true)])
}

async function resetFiltersFromPopup() {
  haptic('tap')
  triggerVisualFeedback('selection')
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
    haptic('warning')
    triggerVisualFeedback('danger')
    showToast('已删除')
    await new Promise((resolve) => window.setTimeout(resolve, 140))
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
    haptic('confirm')
    triggerVisualFeedback('confirm')
    showToast('已复制为新记录')
    await new Promise((resolve) => window.setTimeout(resolve, 120))
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
  haptic('selection')
  triggerVisualFeedback('selection')
  await applyFilters(query.dayPage + 1)
}

async function showNewerDayWindow() {
  if (!hasNewerDayWindow.value) {
    return
  }
  haptic('selection')
  triggerVisualFeedback('selection')
  await applyFilters(query.dayPage - 1)
}

async function showOlderDay() {
  haptic('selection')
  triggerVisualFeedback('selection')
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
  haptic('selection')
  triggerVisualFeedback('selection')
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

function scrollRecordsTop() {
  haptic('tap')
  triggerVisualFeedback('selection')
  recordsPageRef.value?.scrollTo({ top: 0, behavior: 'smooth' })
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function updateBackTopVisibility() {
  const pageTop = recordsPageRef.value?.scrollTop || 0
  showBackTop.value = Math.max(window.scrollY, pageTop) > 280
}

watch(() => query.type, () => {
  clearInvalidCategory()
})

watch(recordsViewMode, (value) => {
  haptic('selection')
  triggerVisualFeedback('selection')
  saveRecordsViewMode(value)
  void syncModeViewport(value)
  void nextTick(updateBackTopVisibility)
})

watch(() => route.query, async () => {
  applyRouteQuery()
  persistRecordsQuery()
  await Promise.all([load(query.dayPage), loadDayOptions()])
})

onMounted(() => {
  void init()
  window.addEventListener('scroll', updateBackTopVisibility, { passive: true })
  recordsPageRef.value?.addEventListener('scroll', updateBackTopVisibility, { passive: true })
  void nextTick(updateBackTopVisibility)
})
onBeforeUnmount(() => {
  cancelDayDragFrame()
  window.removeEventListener('scroll', updateBackTopVisibility)
  recordsPageRef.value?.removeEventListener('scroll', updateBackTopVisibility)
})
</script>

<template>
  <main ref="recordsPageRef" class="page records-page">
    <van-nav-bar title="流水" />
    <div class="page-content">
      <section class="section panel records-filter-panel">
        <div class="records-search-bar">
          <label class="records-search-input">
            <van-icon name="search" />
            <input
              v-model="query.keyword"
              type="search"
              enterkeyhint="search"
              placeholder="搜索事项、备注、地点、APP、支付方式"
              @keyup.enter="applySearchFilters"
            />
            <button
              v-if="query.keyword"
              class="records-search-clear"
              type="button"
              aria-label="清空搜索"
              title="清空搜索"
              @click="query.keyword = ''; applyFilters(1)"
            >
              <van-icon name="cross" />
            </button>
          </label>
          <button
            :class="['records-search-submit', visualFeedback === 'confirm' ? 'ui-feedback-confirm' : '']"
            type="button"
            @click="applySearchFilters"
          >
            <van-icon name="search" />
            <span>搜索</span>
          </button>
          <button
            :class="['records-filter-more', visualFeedback === 'selection' ? 'ui-feedback-selection' : '']"
            type="button"
            @click="openFilterPopup"
          >
            <van-icon name="filter-o" />
            <span>更多</span>
          </button>
        </div>
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
      </section>

      <section class="section panel records-overview-panel">
        <div class="records-overview-head">
          <div>
            <div class="section-heading records-overview-title">流水总览</div>
            <div v-if="totalRecords > 0 && !recordsLoading" class="records-meta">
              <span>共 {{ totalRecords }} 条记录 · {{ totalDays }} 天</span>
              <span v-if="dayCards.length > 0">{{ activeDayPosition }} / {{ totalDays }} 天</span>
            </div>
            <div v-else class="records-meta">
              <span>{{ recordsLoading ? '正在加载记录' : '暂无符合条件的记录' }}</span>
            </div>
          </div>
          <div v-if="totalDays > dayPageSize && !recordsLoading" class="records-tip">
            当前显示 {{ dayCards.length }} 天
          </div>
        </div>
        <div class="view-mode-row">
          <div class="view-mode-label">查看模式</div>
          <van-radio-group v-model="recordsViewMode" class="view-mode-switch" direction="horizontal">
            <van-radio name="card">日卡片</van-radio>
            <van-radio name="stack">时间线</van-radio>
          </van-radio-group>
        </div>
      </section>

      <section
        :class="[
          'section',
          'records-section',
          visualFeedback === 'selection' ? 'ui-feedback-selection' : '',
          visualFeedback === 'danger' ? 'ui-feedback-danger' : ''
        ]"
      >
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
      </section>
    </div>

    <Transition name="records-fab-fade">
      <van-button
        v-if="!recordsLoading && isStackMode && showBackTop"
        class="records-back-top"
        round
        type="primary"
        icon="arrow-up"
        aria-label="返回顶部"
        title="返回顶部"
        @click="scrollRecordsTop"
      />
    </Transition>

    <van-button
      v-if="!recordsLoading && totalDays > 1"
      class="records-jump-fab"
      round
      type="primary"
      icon="calendar-o"
      aria-label="跳转日期"
      :disabled="dayJumpOptions.length === 0"
      :style="{ bottom: isStackMode ? 'calc(156px + env(safe-area-inset-bottom))' : 'calc(100px + env(safe-area-inset-bottom))' }"
      @click="openDayJumpPopup"
    />

    <van-popup v-model:show="dayJumpPopupVisible" position="bottom" round teleport="body">
      <div class="day-jump-popup">
        <div class="day-jump-popup-header">
          <div>
            <div class="day-jump-popup-title">跳转日期</div>
            <div class="day-jump-popup-subtitle">选择筛选结果中的某一天</div>
          </div>
          <van-button size="small" plain type="default" icon="cross" @click="dayJumpPopupVisible = false">关闭</van-button>
        </div>
        <div class="day-jump-popup-list">
          <button
            v-for="item in dayJumpOptions"
            :key="item.value"
            type="button"
            :class="['day-jump-popup-item', { active: item.value === activeDayDate }]"
            @click="chooseDayJump(item.value)"
          >
            <span class="day-jump-popup-item-copy">
              <span class="day-jump-popup-item-title">{{ item.label }}</span>
              <span v-if="item.description" class="day-jump-popup-item-desc">{{ item.description }}</span>
            </span>
            <van-icon v-if="item.value === activeDayDate" name="success" class="day-jump-popup-check" />
          </button>
          <div v-if="dayJumpOptions.length === 0" class="day-jump-popup-empty">暂无可跳转日期</div>
        </div>
      </div>
    </van-popup>

    <van-popup v-model:show="filterPopupVisible" position="bottom" round teleport="body">
      <div class="filter-popup">
        <div class="filter-popup-header">
          <div>
            <div class="filter-popup-title">筛选记录</div>
            <div class="filter-popup-subtitle">
              {{ activeFilterCount > 0 ? `已启用 ${activeFilterCount} 个条件` : '默认显示本月至今' }}
            </div>
          </div>
          <van-button size="small" plain type="default" icon="replay" @click="resetFiltersFromPopup">重置</van-button>
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
          <van-button block round type="primary" icon="success" @click="applyFilterPopup">完成</van-button>
        </div>
      </div>
    </van-popup>
  </main>
</template>

<style scoped>
.records-section {
  min-width: 0;
  border-radius: var(--radius-floating);
}

.records-filter-panel {
  padding: var(--space-0);
  overflow: hidden;
  background:
    radial-gradient(circle at 92% 0%, rgba(var(--theme-primary-glow-rgb), 0.18), transparent 34%),
    var(--card-bg);
}

.records-search-bar {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  gap: var(--space-8);
  align-items: center;
  padding: var(--space-12);
}

.records-search-input {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: var(--space-8);
  align-items: center;
  min-width: 0;
  min-height: 42px;
  padding: var(--space-0) var(--space-12);
  border: 1px solid rgba(var(--theme-border-warm-rgb), 0.22);
  border-radius: var(--radius-pill);
  background: rgba(var(--theme-border-warm-rgb), 0.08);
  color: var(--text-secondary);
}

.records-search-input:focus-within {
  border-color: var(--primary);
  background: var(--glass-strong-bg);
  box-shadow: 0 0 0 3px rgba(var(--theme-primary-glow-rgb), 0.1);
}

.records-search-input :deep(.van-icon) {
  flex: 0 0 auto;
  color: var(--text-muted);
  font-size: var(--icon-size-md);
}

.records-search-input input {
  width: 100%;
  min-width: 0;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--text-main);
  font: inherit;
  font-size: var(--font-size-section-title);
}

.records-search-input input::placeholder {
  color: var(--text-muted);
}

.records-search-clear,
.records-search-submit,
.records-filter-more {
  border: 0;
  font: inherit;
}

.records-search-clear {
  display: grid;
  width: 26px;
  height: 26px;
  place-items: center;
  border-radius: var(--radius-pill);
  background: var(--card-bg-warm);
  color: var(--text-muted);
}

.records-search-submit,
.records-filter-more {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-4);
  min-height: 42px;
  padding: var(--space-0) var(--space-12);
  border-radius: var(--radius-pill);
  font-size: var(--font-size-meta);
  font-weight: 600;
  white-space: nowrap;
  transition: transform var(--motion-fast) ease, filter var(--motion-fast) ease, box-shadow var(--motion-fast) ease, border-color var(--motion-fast) ease;
}

.records-search-submit:active,
.records-filter-more:active,
.records-search-clear:active {
  transform: translateY(1px) scale(0.975);
  filter: brightness(1.08);
}

.records-search-submit {
  background: linear-gradient(135deg, var(--primary), var(--primary-deep));
  color: #fff;
  box-shadow: 0 12px 24px rgba(var(--theme-primary-glow-rgb), 0.22);
}

.records-filter-more {
  border: 1px solid rgba(var(--theme-border-warm-rgb), 0.22);
  background: rgba(var(--theme-border-warm-rgb), 0.08);
  color: var(--primary);
}

.records-overview-panel {
  display: grid;
  gap: var(--space-12);
  background:
    linear-gradient(135deg, rgba(var(--theme-primary-glow-rgb), 0.12), transparent 48%),
    var(--card-bg);
}

.records-overview-head {
  display: grid;
  gap: var(--space-8);
}

.records-overview-title {
  margin-bottom: var(--space-4);
}

.filter-summary {
  padding: var(--space-8) var(--space-12) var(--space-12);
  border-top: 1px solid rgba(var(--theme-border-warm-rgb), 0.18);
}

.filter-date-summary {
  display: flex;
  gap: var(--space-6);
  align-items: center;
  color: var(--text-secondary);
  font-size: var(--font-size-caption);
  line-height: var(--line-height-caption);
}

.filter-tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-6);
  margin-top: var(--space-8);
}

.filter-tag {
  max-width: 100%;
  min-height: 24px;
  padding: var(--space-3) var(--space-8);
  border-radius: var(--radius-pill);
  border: 1px solid rgba(var(--theme-border-warm-rgb), 0.18);
  background: var(--primary-soft);
  color: var(--text-main);
  font-size: var(--font-size-caption);
  line-height: var(--line-height-caption);
  overflow-wrap: anywhere;
}

.filter-empty {
  margin-top: var(--space-6);
  color: var(--text-muted);
  font-size: var(--font-size-caption);
  line-height: var(--line-height-caption);
}

.view-mode-row {
  display: grid;
  gap: var(--space-8);
}

.view-mode-label {
  color: var(--text-secondary);
  font-size: var(--font-size-caption);
  line-height: var(--line-height-caption);
}

.view-mode-switch {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-4);
  padding: var(--space-4);
  border-radius: var(--radius-card);
  border: 1px solid rgba(var(--theme-border-warm-rgb), 0.16);
  background: rgba(var(--theme-border-warm-rgb), 0.08);
}

.view-mode-switch :deep(.van-radio) {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 32px;
  margin-right: var(--space-0);
  padding: var(--space-0) var(--space-12);
  border-radius: var(--radius-inner);
  color: var(--text-secondary);
  font-size: var(--font-size-meta);
  font-weight: 600;
  line-height: var(--line-height-meta);
  transition: color 0.2s ease, background 0.2s ease, box-shadow 0.2s ease;
}

.view-mode-switch :deep(.van-radio__icon) {
  display: none;
}

.view-mode-switch :deep(.van-radio__label) {
  margin: var(--space-0);
}

.view-mode-switch :deep(.van-radio[aria-checked='true']) {
  background: var(--glass-strong-bg);
  border-radius: var(--radius-inner);
  box-shadow: 0 8px 18px rgba(var(--theme-shadow-warm-rgb), 0.18);
  color: var(--primary);
}

.view-mode-switch :deep(.van-radio[aria-checked='true'] .van-radio__label) {
  color: var(--primary);
}

.records-meta {
  display: flex;
  justify-content: space-between;
  gap: var(--space-12);
  color: var(--text-secondary);
  font-size: var(--font-size-meta);
}

.records-tip {
  color: var(--text-muted);
  font-size: var(--font-size-caption);
}

.records-loading {
  display: grid;
  place-items: center;
  min-height: 220px;
}

.day-jump {
  margin-top: var(--space-12);
  padding: var(--space-0);
}

.day-jump :deep(.van-cell) {
  border-radius: var(--radius-card);
}

.records-back-top {
  position: fixed;
  right: 12px;
  bottom: calc(100px + env(safe-area-inset-bottom));
  z-index: 121;
  width: 44px;
  height: 44px;
  padding: var(--space-0);
  box-shadow: 0 14px 30px rgba(var(--theme-primary-glow-rgb), 0.28);
}

.records-fab-fade-enter-active,
.records-fab-fade-leave-active {
  transition: opacity 180ms ease, transform 180ms ease;
}

.records-fab-fade-enter-from,
.records-fab-fade-leave-to {
  opacity: 0;
  transform: translateY(8px) scale(0.94);
}

.records-jump-fab {
  position: fixed;
  right: 12px;
  z-index: 121;
  width: 44px;
  height: 44px;
  padding: var(--space-0);
  box-shadow: 0 14px 30px rgba(var(--theme-primary-glow-rgb), 0.28);
}

.day-jump-popup {
  max-height: min(72vh, 560px);
  padding: var(--space-14) var(--space-0) max(var(--space-14), env(safe-area-inset-bottom));
  overflow-y: auto;
  background: var(--card-bg);
}

.day-jump-popup-header {
  display: flex;
  justify-content: space-between;
  gap: var(--space-12);
  align-items: center;
  padding: var(--space-0) var(--space-14) var(--space-12);
  border-bottom: 1px solid var(--border-warm);
}

.day-jump-popup-title {
  color: var(--text-main);
  font-size: var(--font-size-panel-title);
  font-weight: 700;
  line-height: var(--line-height-panel-title);
}

.day-jump-popup-subtitle {
  margin-top: var(--space-2);
  color: var(--text-muted);
  font-size: var(--font-size-caption);
  line-height: var(--line-height-caption);
}

.day-jump-popup-list {
  display: grid;
  gap: var(--space-8);
  padding: var(--space-12);
}

.day-jump-popup-item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
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

.day-jump-popup-item.active {
  border-color: var(--primary);
  background: var(--primary-soft);
}

.day-jump-popup-item-copy {
  min-width: 0;
}

.day-jump-popup-item-title,
.day-jump-popup-item-desc {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.day-jump-popup-item-title {
  font-size: var(--font-size-body-strong);
  font-weight: 500;
}

.day-jump-popup-item-desc {
  margin-top: var(--space-2);
  color: var(--text-secondary);
  font-size: var(--font-size-caption);
}

.day-jump-popup-check {
  color: var(--primary);
  font-size: var(--icon-size-md);
}

.day-jump-popup-empty {
  padding: var(--space-28) var(--space-0);
  color: var(--text-muted);
  text-align: center;
}

.day-card-stage {
  position: relative;
  height: clamp(420px, 62dvh, 560px);
  margin-bottom: var(--space-22);
  border-radius: var(--radius-floating);
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
  gap: var(--space-12);
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
  margin: var(--space-0) var(--space-1);
  overflow: hidden;
  border-radius: var(--radius-floating);
  background: var(--card-bg);
  border: 1px solid rgba(var(--theme-border-warm-rgb), 0.22);
  box-shadow: 0 18px 42px rgba(var(--theme-shadow-warm-rgb), 0.24);
  backdrop-filter: blur(18px) saturate(1.16);
  backface-visibility: hidden;
  transform-origin: center center;
  will-change: transform, opacity;
}

.day-card-stack {
  position: relative;
  inset: auto;
  margin: var(--space-0);
  scroll-margin-top: var(--space-12);
}

.day-card-stack.active {
  border-color: rgba(var(--theme-primary-glow-rgb), 0.34);
  box-shadow: 0 18px 42px rgba(var(--theme-primary-glow-rgb), 0.14);
}

.day-card-header {
  display: flex;
  justify-content: space-between;
  gap: var(--space-12);
  padding: var(--space-16) var(--space-14) var(--space-12);
  border-bottom: 1px solid rgba(var(--theme-border-warm-rgb), 0.16);
  background: linear-gradient(135deg, rgba(var(--theme-primary-glow-rgb), 0.1), transparent);
}

.day-card-header-stack {
  display: grid;
  gap: var(--space-12);
}

.day-card-header-stack .day-summary {
  text-align: left;
}

.day-heading-row {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr) 34px;
  gap: var(--space-8);
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
  padding: var(--space-0);
}

.day-title {
  font-size: var(--font-size-section-title);
  font-weight: 700;
  line-height: var(--line-height-section-title);
}

.day-subtitle {
  margin-top: var(--space-3);
  color: var(--text-secondary);
  font-size: var(--font-size-caption);
  line-height: var(--line-height-caption);
}

.day-summary {
  flex: 0 0 auto;
  text-align: right;
  font-size: var(--font-size-caption);
  line-height: var(--line-height-caption);
}

.day-summary-line {
  white-space: nowrap;
}

.day-records {
  flex: 1 1 auto;
  min-height: 0;
  border-radius: 0 0 var(--radius-floating) var(--radius-floating);
  padding: var(--space-4) var(--space-0);
  overflow-y: auto;
  overscroll-behavior: contain;
  -webkit-overflow-scrolling: touch;
}

.day-records-stack {
  overflow: visible;
}

.record-swipe-cell {
  background: transparent;
}

.record-row {
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr) auto;
  gap: var(--space-10);
  align-items: center;
  min-height: 66px;
  padding: var(--space-10) var(--space-12);
  cursor: pointer;
}

.record-swipe-cell:not(:last-child) .record-row {
  border-bottom: 1px solid rgba(var(--theme-border-warm-rgb), 0.72);
}

.record-type-mark {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: var(--radius-floating);
  font-size: var(--font-size-body-strong);
  font-weight: 700;
}

.expense-mark {
  background: var(--expense-soft);
  color: var(--expense);
}

.income-mark {
  background: var(--income-soft);
  color: var(--income);
}

.record-main {
  min-width: 0;
}

.record-title {
  overflow: hidden;
  color: var(--text-main);
  font-size: var(--font-size-body-strong);
  font-weight: 600;
  line-height: var(--line-height-body-strong);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.record-meta,
.record-note {
  overflow: hidden;
  color: var(--text-secondary);
  font-size: var(--font-size-caption);
  line-height: var(--line-height-caption);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.record-note {
  color: var(--text-muted);
}

.record-side {
  display: grid;
  justify-items: end;
  gap: var(--space-6);
}

.record-amount {
  white-space: nowrap;
  font-size: var(--font-size-body);
  font-weight: 700;
  line-height: var(--line-height-body);
}

.record-swipe-action {
  width: 64px;
  height: 100%;
}

.load-more-records {
  padding: var(--space-8) var(--space-12) var(--space-14);
  border-top: 1px solid rgba(var(--theme-border-warm-rgb), 0.72);
}

.all-loaded-text {
  padding: var(--space-6) var(--space-0) var(--space-2);
  color: var(--text-muted);
  font-size: var(--font-size-caption);
  line-height: var(--line-height-caption);
  text-align: center;
}

.day-window-nav {
  margin-top: var(--space-12);
  padding: var(--space-12);
  border: 1px solid rgba(var(--theme-border-warm-rgb), 0.18);
  border-radius: var(--radius-card);
  background: var(--card-bg);
  backdrop-filter: blur(16px);
}

.day-window-summary {
  margin-bottom: var(--space-10);
  color: var(--text-secondary);
  font-size: var(--font-size-caption);
  line-height: var(--line-height-caption);
}

.day-window-actions {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(132px, 1fr));
  gap: var(--space-8);
}

.day-window-actions :deep(.van-button__text) {
  white-space: nowrap;
}

.filter-popup {
  max-height: min(78vh, 620px);
  padding: var(--space-14) var(--space-0) max(var(--space-14), env(safe-area-inset-bottom));
  overflow-y: auto;
  background: var(--card-bg);
}

.filter-popup-header {
  display: flex;
  justify-content: space-between;
  gap: var(--space-12);
  align-items: center;
  padding: var(--space-0) var(--space-14) var(--space-12);
  border-bottom: 1px solid var(--border-warm);
}

.filter-popup-title {
  color: var(--text-main);
  font-size: var(--font-size-panel-title);
  font-weight: 700;
  line-height: var(--line-height-panel-title);
}

.filter-popup-subtitle {
  margin-top: var(--space-2);
  color: var(--text-muted);
  font-size: var(--font-size-caption);
  line-height: var(--line-height-caption);
}

.filter-popup-body {
  padding-top: var(--space-4);
}

.filter-popup-actions {
  padding: var(--space-14) var(--space-12) var(--space-0);
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

  .records-search-bar {
    grid-template-columns: minmax(0, 1fr) auto;
  }

  .records-filter-more {
    grid-column: 1 / -1;
  }
}
</style>
