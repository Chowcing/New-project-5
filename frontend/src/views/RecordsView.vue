<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter, type LocationQueryValue } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { categoryApi, paymentMethodApi, transactionApi } from '@/api/services'
import ModernDateField from '@/components/ModernDateField.vue'
import ModernSelectField from '@/components/ModernSelectField.vue'
import type { Category, PaymentMethod, TransactionDayCard, TransactionDayOption, TransactionRecord } from '@/types'
import { currentMonth, money, nowLocalInput, todayDate, toBackendDateTime } from '@/utils/date'
import { showError } from '@/utils/errors'

const categories = ref<Category[]>([])
const paymentMethods = ref<PaymentMethod[]>([])
const dayCards = ref<TransactionDayCard[]>([])
const dayOptions = ref<TransactionDayOption[]>([])
const route = useRoute()
const router = useRouter()
const dayPageSize = 30
const dayRecordPageSize = ref(5)
const totalRecords = ref(0)
const totalDays = ref(0)
const activeDayIndex = ref(0)
const swipeStartX = ref(0)
const swipeStartY = ref(0)
const dayDragOffset = ref(0)
const dayDragging = ref(false)
const dayTransitionName = ref('day-slide-older')
let lastDayOptionsFilterKey = ''

type RecordsQuery = {
  type: '' | 'EXPENSE' | 'INCOME'
  startDate: string
  endDate: string
  channel: '' | 'ONLINE' | 'OFFLINE'
  categoryId: number | ''
  paymentMethodId: number | ''
  keyword: string
  page: number
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
    page: 1
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
const dayRecordPageSizeOptions = [
  { label: '3 条/页', value: 3 },
  { label: '5 条/页', value: 5 },
  { label: '10 条/页', value: 10 },
  { label: '15 条/页', value: 15 },
  { label: '20 条/页', value: 20 }
]

const activeDayPosition = computed(() => (query.page - 1) * dayPageSize + activeDayIndex.value + 1)
const activeDay = computed(() => dayCards.value[activeDayIndex.value])
const activeDayDate = computed(() => activeDay.value?.date)
const dayJumpOptions = computed(() => dayOptions.value.map((item, index) => ({
  label: `${dayTitle(item.date)} · ${item.date}`,
  value: item.date,
  icon: 'calendar-o',
  description: `第 ${index + 1} 天 · ${item.transactionCount} 笔 · 支出 ¥${money(item.totalExpense)} · 收入 ¥${money(item.totalIncome)}`
})))
const dayCardDragStyle = computed(() => {
  if (!dayDragging.value && dayDragOffset.value === 0) {
    return {}
  }
  return {
    transform: `translateX(${dayDragOffset.value}px)`,
    transition: dayDragging.value ? 'none' : 'transform 180ms ease'
  }
})

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

function routeQueryFromFilters(page = query.page) {
  const nextQuery: Record<string, string> = {}
  if (query.type) nextQuery.type = query.type
  if (query.startDate) nextQuery.startDate = query.startDate
  if (query.endDate) nextQuery.endDate = query.endDate
  if (query.channel) nextQuery.channel = query.channel
  if (query.categoryId !== '') nextQuery.categoryId = String(query.categoryId)
  if (query.paymentMethodId !== '') nextQuery.paymentMethodId = String(query.paymentMethodId)
  if (query.keyword.trim()) nextQuery.keyword = query.keyword.trim()
  if (page > 1) nextQuery.page = String(page)
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
  const page = positiveInteger(firstQueryValue(route.query.page))

  query.type = isTransactionType(type) ? type : ''
  query.startDate = isDateString(startDate) ? startDate : `${currentMonth()}-01`
  query.endDate = isDateString(endDate) ? endDate : todayDate()
  query.channel = isTransactionChannel(channel) ? channel : ''
  query.categoryId = categoryId ?? ''
  query.paymentMethodId = paymentMethodId ?? ''
  query.keyword = typeof keyword === 'string' ? keyword : ''
  query.page = page ?? 1
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

async function load(page = 1, nextActiveDayIndex?: number) {
  query.page = page
  try {
    const result = await transactionApi.dailyCards({
      ...filterParams(),
      startDate: query.startDate || undefined,
      endDate: query.endDate || undefined,
      dayPage: query.page,
      daySize: dayPageSize,
      recordPage: 1,
      recordSize: dayRecordPageSize.value
    })
    if (result.days.length === 0 && page > 1 && result.totalDays > 0) {
      await applyFilters(page - 1)
      return
    }
    dayCards.value = result.days
    totalRecords.value = result.totalRecords
    totalDays.value = result.totalDays
    activeDayIndex.value = Math.min(nextActiveDayIndex ?? 0, Math.max(result.days.length - 1, 0))
  } catch (error) {
    showError(error, '记录加载失败')
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

function setDayRecordPageSize(value: string | number | undefined) {
  if (typeof value !== 'number') {
    return
  }
  dayRecordPageSize.value = value
  void load(query.page, activeDayIndex.value)
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
  if (targetPage === query.page) {
    activeDayIndex.value = Math.min(targetIndex, Math.max(dayCards.value.length - 1, 0))
    return
  }
  await load(targetPage, targetIndex)
}

async function applyFilters(page = 1) {
  query.page = page
  await router.replace({
    path: '/records',
    query: routeQueryFromFilters(page)
  })
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
  await Promise.all([load(query.page), loadDayOptions(true)])
}

async function removeRecord(id: number) {
  try {
    await showConfirmDialog({ title: '删除记录', message: '确认删除这条记录？' })
  } catch {
    return
  }
  try {
    await transactionApi.remove(id)
    showToast('已删除')
    await load(query.page, activeDayIndex.value)
    await loadDayOptions(true)
  } catch (error) {
    showError(error, '删除失败')
  }
}

async function copyRecord(item: TransactionRecord) {
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
  }
}

async function routerPushRecord(id: number) {
  await router.push({
    path: `/records/${id}`,
    query: routeQueryFromFilters(query.page)
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

async function loadDayRecords(date: string, page: number) {
  try {
    const result = await transactionApi.list({
      ...filterParams(),
      startDate: date,
      endDate: date,
      page,
      size: dayRecordPageSize.value
    })
    const index = dayCards.value.findIndex((item) => item.date === date)
    if (index < 0) {
      return
    }
    const nextDays = [...dayCards.value]
    nextDays[index] = {
      ...nextDays[index],
      records: result
    }
    dayCards.value = nextDays
  } catch (error) {
    showError(error, '当天记录加载失败')
  }
}

async function showOlderDay() {
  dayTransitionName.value = 'day-slide-older'
  if (activeDayIndex.value < dayCards.value.length - 1) {
    activeDayIndex.value += 1
    return
  }
  if (query.page * dayPageSize < totalDays.value) {
    await load(query.page + 1, 0)
    return
  }
  if (totalDays.value > 1) {
    if (query.page === 1) {
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
  if (query.page > 1) {
    await load(query.page - 1, dayPageSize - 1)
    return
  }
  if (totalDays.value > 1) {
    const lastPage = Math.ceil(totalDays.value / dayPageSize)
    if (lastPage === query.page) {
      activeDayIndex.value = dayCards.value.length - 1
      return
    }
    await load(lastPage, dayPageSize - 1)
  }
}

function onDayTouchStart(event: TouchEvent) {
  const touch = event.touches[0]
  if (!touch) return
  swipeStartX.value = touch.clientX
  swipeStartY.value = touch.clientY
  dayDragOffset.value = 0
  dayDragging.value = false
}

function onDayTouchMove(event: TouchEvent) {
  const touch = event.touches[0]
  if (!touch) return
  const deltaX = touch.clientX - swipeStartX.value
  const deltaY = touch.clientY - swipeStartY.value
  if (Math.abs(deltaX) < 8 || Math.abs(deltaX) < Math.abs(deltaY) * 1.2) {
    return
  }
  dayDragging.value = true
  dayDragOffset.value = Math.max(-64, Math.min(64, deltaX * 0.32))
}

function onDayTouchEnd(event: TouchEvent) {
  const touch = event.changedTouches[0]
  if (!touch) return
  const deltaX = touch.clientX - swipeStartX.value
  const deltaY = touch.clientY - swipeStartY.value
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

watch(() => route.query, async () => {
  applyRouteQuery()
  await Promise.all([load(query.page), loadDayOptions()])
})

onMounted(init)
</script>

<template>
  <main class="page">
    <van-nav-bar title="收支明细" />
    <div class="page-content">
      <section class="section panel">
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
        <van-field v-model="query.keyword" label="搜索" placeholder="事项、备注、地点、APP、支付方式" @keyup.enter="applyFilters(1)">
          <template #button>
            <div class="filter-actions">
              <van-button size="small" plain type="default" @click="resetFilters">重置</van-button>
              <van-button size="small" type="primary" @click="applyFilters(1)">筛选</van-button>
            </div>
          </template>
        </van-field>
        <ModernSelectField
          :model-value="dayRecordPageSize"
          label="每页记录"
          title="选择每页记录数"
          :options="dayRecordPageSizeOptions"
          @update:model-value="setDayRecordPageSize"
        />
      </section>

      <section class="section records-section">
        <div v-if="totalRecords > 0" class="records-meta">
          <span>共 {{ totalRecords }} 条记录 · {{ totalDays }} 天</span>
          <span v-if="dayCards.length > 0">{{ activeDayPosition }} / {{ totalDays }} 天</span>
        </div>
        <div v-if="totalDays > dayPageSize" class="records-tip">
          当前显示 {{ dayCards.length }} 天，底部可直接选择筛选结果中的日期
        </div>
        <div v-if="dayCards.length === 0" class="panel empty-text">没有符合条件的记录</div>
        <div
          v-else
          class="day-card-stage"
          @touchstart.passive="onDayTouchStart"
          @touchmove.passive="onDayTouchMove"
          @touchend="onDayTouchEnd"
        >
          <Transition :name="dayTransitionName" mode="out-in">
            <article v-if="activeDay" :key="activeDay.date" class="day-card" :style="dayCardDragStyle">
              <header class="day-card-header">
                <div class="day-heading">
                  <div class="day-title">{{ dayTitle(activeDay.date) }}</div>
                  <div class="day-subtitle">{{ daySubtitle(activeDay.date) }} · {{ activeDay.transactionCount }} 笔</div>
                </div>
                <div class="day-summary">
                  <div class="day-summary-line expense">支出 ¥{{ money(activeDay.totalExpense) }}</div>
                  <div class="day-summary-line income">收入 ¥{{ money(activeDay.totalIncome) }}</div>
                  <div class="day-summary-line">结余 ¥{{ money(activeDay.balance) }}</div>
                </div>
              </header>

              <div class="day-records">
                <div
                  v-for="item in activeDay.records.records"
                  :key="item.id"
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
                  <div class="record-side" @click.stop>
                    <div :class="['record-amount', item.type === 'EXPENSE' ? 'expense' : 'income']">
                      {{ item.type === 'EXPENSE' ? '-' : '+' }}¥{{ money(item.amount) }}
                    </div>
                    <div class="record-actions">
                      <van-button
                        class="record-action"
                        size="mini"
                        plain
                        type="primary"
                        icon="description-o"
                        @click="copyRecord(item)"
                      />
                      <van-button
                        class="record-action"
                        size="mini"
                        plain
                        type="danger"
                        icon="delete-o"
                        @click="removeRecord(item.id)"
                      />
                    </div>
                  </div>
                </div>
              </div>

              <div v-if="activeDay.records.total > dayRecordPageSize" class="pagination-block">
                <div class="pagination-label">当天记录分页 · 共 {{ activeDay.records.total }} 条</div>
                <van-pagination
                  class="day-record-pagination"
                  aria-label="当天记录分页"
                  mode="simple"
                  :model-value="activeDay.records.page"
                  :total-items="activeDay.records.total"
                  :items-per-page="dayRecordPageSize"
                  @change="loadDayRecords(activeDay.date, $event)"
                />
              </div>
            </article>
          </Transition>
        </div>
        <div v-if="totalDays > dayPageSize" class="pagination-block">
          <div class="pagination-label">日期分页 · 每页 {{ dayPageSize }} 天</div>
          <van-pagination
            v-model="query.page"
            class="day-pagination"
            aria-label="日期分页"
            mode="simple"
            :total-items="totalDays"
            :items-per-page="dayPageSize"
            @change="applyFilters"
          />
        </div>
        <div v-if="totalDays > 1" class="day-jump panel">
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
  </main>
</template>

<style scoped>
.records-section {
  min-width: 0;
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

.day-jump {
  margin-top: 12px;
  padding: 0;
}

.day-jump :deep(.van-cell) {
  border-radius: 8px;
}

.day-card-stage {
  min-height: 420px;
  padding-bottom: 22px;
  touch-action: pan-y;
  user-select: none;
}

.day-slide-older-enter-active,
.day-slide-older-leave-active,
.day-slide-newer-enter-active,
.day-slide-newer-leave-active {
  transition: transform 220ms ease, opacity 220ms ease;
}

.day-slide-older-enter-from {
  opacity: 0;
  transform: translateX(-28px);
}

.day-slide-older-leave-to {
  opacity: 0;
  transform: translateX(28px);
}

.day-slide-newer-enter-from {
  opacity: 0;
  transform: translateX(28px);
}

.day-slide-newer-leave-to {
  opacity: 0;
  transform: translateX(-28px);
}

.day-card {
  min-height: 398px;
  margin: 0 1px;
  overflow: hidden;
  border-radius: 8px;
  background: #fff;
}

.day-card-header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 14px 12px;
  border-bottom: 1px solid #eef1f4;
}

.day-heading {
  min-width: 0;
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
  padding: 4px 0;
}

.record-row {
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
  min-height: 66px;
  padding: 10px 12px;
  border-bottom: 1px solid #f0f2f5;
  cursor: pointer;
}

.record-row:last-child {
  border-bottom: 0;
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

.record-actions {
  display: flex;
  gap: 6px;
}

.record-action {
  width: 28px;
  height: 24px;
  padding: 0;
}

.day-record-pagination {
  padding: 4px 12px 12px;
}

.pagination-block {
  margin-top: 10px;
}

.pagination-label {
  padding: 0 12px 4px;
  color: #8a949b;
  font-size: 12px;
  line-height: 18px;
}

.day-pagination {
  margin-top: 4px;
}

.filter-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

@media (max-width: 360px) {
  .day-card-header {
    display: grid;
  }

  .day-summary {
    text-align: left;
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
