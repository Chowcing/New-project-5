<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { EChartsOption } from 'echarts'
import { statisticsApi } from '@/api/services'
import BaseChart from '@/components/BaseChart.vue'
import ModernDateField from '@/components/ModernDateField.vue'
import type {
  BudgetUsageSummary,
  CategorySummary,
  ChannelSummary,
  DailySummary,
  MonthlyStatistics,
  MonthlyTrendSummary,
  PaymentMethodSummary,
  YearlyStatistics
} from '@/types'
import { currentMonth, money } from '@/utils/date'
import { showError } from '@/utils/errors'

type PeriodMode = 'MONTHLY' | 'YEARLY'
type TrendSummary = DailySummary | MonthlyTrendSummary
type CategoryChartData = {
  name: string
  value: number
  categoryId?: number
  type: 'EXPENSE' | 'INCOME'
  isOther?: boolean
}

const router = useRouter()
const mode = ref<PeriodMode>('MONTHLY')
const month = ref(currentMonth())
const currentYear = new Date().getFullYear()
const minYear = 2000
const statsMinDate = new Date(minYear, 0, 1)
const statsMaxDate = new Date(currentYear, 11, 31)
const year = ref(String(currentYear))
const monthlyStats = ref<MonthlyStatistics | null>(null)
const yearlyStats = ref<YearlyStatistics | null>(null)
const activeTab = ref(0)

const currentStats = computed(() => {
  return mode.value === 'YEARLY' ? yearlyStats.value : monthlyStats.value
})

const currentInsight = computed(() => currentStats.value?.insight)

const trendRows = computed<TrendSummary[]>(() => {
  return mode.value === 'YEARLY' ? yearlyStats.value?.monthlyTrend || [] : monthlyStats.value?.dailyTrend || []
})

const activeTrend = computed(() => {
  return trendRows.value.filter((item) => {
    return Number(item.totalExpense) > 0 || Number(item.totalIncome) > 0
  })
})

const maxTrendExpense = computed(() => {
  return Math.max(0, ...activeTrend.value.map((item) => Number(item.totalExpense)))
})

const budgetButtonMonth = computed(() => {
  if (mode.value === 'MONTHLY') return month.value
  const monthNumber = String(new Date().getMonth() + 1).padStart(2, '0')
  return `${year.value}-${monthNumber}`
})

const monthlyBudget = computed(() => {
  return mode.value === 'MONTHLY' ? monthlyStats.value?.monthlyBudget || null : null
})

const categoryBudgetUsages = computed(() => {
  return mode.value === 'MONTHLY' ? monthlyStats.value?.categoryBudgetUsages || [] : []
})

const expenseByCategory = computed(() => currentStats.value?.expenseByCategory || [])

const incomeByCategory = computed(() => currentStats.value?.incomeByCategory || [])

const expenseByChannel = computed(() => currentStats.value?.expenseByChannel || [])

const expenseByPaymentMethod = computed(() => currentStats.value?.expenseByPaymentMethod || [])

const categoryChartRows = computed<CategoryChartData[]>(() => {
  const rows = expenseByCategory.value
  const topRows: CategoryChartData[] = rows.slice(0, 5).map((item) => ({
    name: item.categoryName,
    value: Number(item.amount || 0),
    categoryId: item.categoryId,
    type: 'EXPENSE' as const
  }))
  const restAmount = rows.slice(5).reduce((sum, item) => sum + Number(item.amount || 0), 0)
  if (restAmount > 0) {
    topRows.push({
      name: '其他',
      value: restAmount,
      categoryId: undefined,
      type: 'EXPENSE' as const,
      isOther: true
    })
  }
  return topRows
})

const trendChartOption = computed<EChartsOption>(() => ({
  color: ['#e25555', '#2f9b63'],
  tooltip: { trigger: 'axis' },
  legend: {
    top: 0,
    itemWidth: 10,
    itemHeight: 10,
    textStyle: { color: '#6b7280', fontSize: 12 }
  },
  grid: { top: 42, left: 8, right: 8, bottom: 10, containLabel: true },
  xAxis: {
    type: 'category',
    data: trendRows.value.map(trendLabel),
    axisTick: { show: false },
    axisLine: { lineStyle: { color: '#d8dde3' } },
    axisLabel: { color: '#6b7280', fontSize: 11 }
  },
  yAxis: {
    type: 'value',
    axisLabel: { color: '#6b7280', fontSize: 11 },
    splitLine: { lineStyle: { color: '#eef1f4' } }
  },
  series: [
    {
      name: '支出',
      type: 'bar',
      barMaxWidth: 18,
      data: trendRows.value.map((item) => ({ value: Number(item.totalExpense || 0), period: trendKey(item) }))
    },
    {
      name: '收入',
      type: 'line',
      smooth: true,
      symbolSize: 6,
      data: trendRows.value.map((item) => ({ value: Number(item.totalIncome || 0), period: trendKey(item) }))
    }
  ]
}))

const categoryChartOption = computed<EChartsOption>(() => ({
  color: ['#e25555', '#f59f3a', '#2f7d68', '#3b82f6', '#7c3aed', '#9ca3af'],
  tooltip: { trigger: 'item' },
  legend: {
    bottom: 0,
    type: 'scroll',
    itemWidth: 10,
    itemHeight: 10,
    textStyle: { color: '#6b7280', fontSize: 12 }
  },
  series: [
    {
      name: '支出分类',
      type: 'pie',
      radius: ['44%', '68%'],
      center: ['50%', '44%'],
      avoidLabelOverlap: true,
      label: { formatter: '{b}\n{d}%', color: '#374151', fontSize: 11 },
      data: categoryChartRows.value
    }
  ]
}))

async function load() {
  try {
    if (mode.value === 'YEARLY') {
      yearlyStats.value = await statisticsApi.yearly(year.value)
    } else {
      monthlyStats.value = await statisticsApi.monthly(month.value)
    }
  } catch (error) {
    showError(error, '统计数据加载失败')
  }
}

function percent(item: CategorySummary, total: number | undefined) {
  if (!total) return '0%'
  return `${Math.round((Number(item.amount) / Number(total)) * 100)}%`
}

function amountPercent(amount: number | string | undefined, total: number | string | undefined) {
  if (!Number(total)) return '0%'
  return `${Math.round((Number(amount || 0) / Number(total)) * 100)}%`
}

function progress(amount: number | string | undefined, total: number | string | undefined) {
  return Number(amountPercent(amount, total).replace('%', ''))
}

function boundedProgress(value: number | string | undefined) {
  return Math.min(100, Math.max(0, Number(value || 0)))
}

function barWidth(amount: number | string | undefined, max: number | string | undefined) {
  const numericAmount = Number(amount || 0)
  if (!numericAmount || !Number(max)) return '0%'
  return `${Math.max(4, Math.round((numericAmount / Number(max)) * 100))}%`
}

function dayLabel(date: string) {
  return date.slice(5)
}

function signedMoney(value: number | string | undefined | null) {
  const numericValue = Number(value || 0)
  const sign = numericValue > 0 ? '+' : numericValue < 0 ? '-' : ''
  return `${sign}¥${money(Math.abs(numericValue))}`
}

function signedPercent(value: number | string | undefined | null) {
  if (value === null || value === undefined) return '新增'
  const numericValue = Number(value)
  const sign = numericValue > 0 ? '+' : ''
  return `${sign}${numericValue.toFixed(2)}%`
}

function changeClass(value: number | string | undefined | null, increaseClass = 'income', decreaseClass = 'expense') {
  const numericValue = Number(value || 0)
  if (numericValue === 0) return 'muted-value'
  return numericValue > 0 ? increaseClass : decreaseClass
}

function channelLabel(channel: ChannelSummary['channel']) {
  return channel === 'ONLINE' ? '线上支出' : '线下支出'
}

function monthEndDate(value: string) {
  const [year, monthNumber] = value.split('-').map(Number)
  const lastDay = new Date(year, monthNumber, 0).getDate()
  return `${value}-${String(lastDay).padStart(2, '0')}`
}

function periodStartDate() {
  return mode.value === 'YEARLY' ? `${year.value}-01-01` : `${month.value}-01`
}

function periodEndDate() {
  return mode.value === 'YEARLY' ? `${year.value}-12-31` : monthEndDate(month.value)
}

async function openCategoryRecords(item: CategorySummary, type: 'EXPENSE' | 'INCOME') {
  await router.push({
    path: '/records',
    query: {
      type,
      startDate: periodStartDate(),
      endDate: periodEndDate(),
      categoryId: String(item.categoryId),
      dayPage: '1'
    }
  })
}

function trendKey(item: TrendSummary) {
  return 'date' in item ? item.date : item.month
}

function trendLabel(item: TrendSummary) {
  return 'date' in item ? dayLabel(item.date) : `${item.month.slice(5)}月`
}

async function openTrendRecords(item: TrendSummary) {
  const startDate = 'date' in item ? item.date : `${item.month}-01`
  const endDate = 'date' in item ? item.date : monthEndDate(item.month)
  await router.push({
    path: '/records',
    query: {
      startDate,
      endDate,
      dayPage: '1'
    }
  })
}

async function openChannelRecords(item: ChannelSummary) {
  await router.push({
    path: '/records',
    query: {
      type: 'EXPENSE',
      startDate: periodStartDate(),
      endDate: periodEndDate(),
      channel: item.channel,
      dayPage: '1'
    }
  })
}

async function openPaymentMethodRecords(item: PaymentMethodSummary) {
  await router.push({
    path: '/records',
    query: {
      type: 'EXPENSE',
      startDate: periodStartDate(),
      endDate: periodEndDate(),
      paymentMethodId: String(item.paymentMethodId),
      dayPage: '1'
    }
  })
}

async function openBudgetRecords(item: BudgetUsageSummary) {
  const query: Record<string, string> = {
    type: 'EXPENSE',
    startDate: periodStartDate(),
    endDate: periodEndDate(),
    dayPage: '1'
  }
  if (item.categoryId) {
    query.categoryId = String(item.categoryId)
  }
  await router.push({ path: '/records', query })
}

async function openBudgets() {
  await router.push({ path: '/budgets', query: { month: budgetButtonMonth.value } })
}

type ChartData = {
  period?: string
  name?: string
  value?: number
  categoryId?: number
  type?: 'EXPENSE' | 'INCOME'
  isOther?: boolean
}

function chartData(params: unknown) {
  if (!params || typeof params !== 'object') return null
  const data = (params as { data?: unknown }).data
  if (!data || typeof data !== 'object') return null
  return data as ChartData
}

async function openTrendFromChart(params: unknown) {
  const data = chartData(params)
  if (!data?.period) return
  const item = trendRows.value.find((row) => trendKey(row) === data.period)
  if (item) {
    await openTrendRecords(item)
  }
}

async function openCategoryFromChart(params: unknown) {
  const data = chartData(params)
  if (!data || data.isOther || !data.categoryId || !data.type) return
  await openCategoryRecords({
    categoryId: data.categoryId,
    categoryName: data.name || '分类',
    amount: data.value || 0,
    transactionCount: 0
  }, data.type)
}

onMounted(load)
</script>

<template>
  <main class="page">
    <van-nav-bar :title="mode === 'YEARLY' ? '年度统计' : '月度统计'" />
    <div class="page-content">
      <section class="section panel">
        <van-radio-group v-model="mode" class="period-switch" direction="horizontal" @change="load">
          <van-radio name="MONTHLY">月度</van-radio>
          <van-radio name="YEARLY">年度</van-radio>
        </van-radio-group>
        <ModernDateField
          v-if="mode === 'MONTHLY'"
          v-model="month"
          mode="month"
          label="月份"
          title="选择月份"
          @change="load"
        />
        <ModernDateField
          v-else
          v-model="year"
          mode="year"
          label="年份"
          title="选择年份"
          :min-date="statsMinDate"
          :max-date="statsMaxDate"
          @change="load"
        />
      </section>

      <section class="section metric-grid">
        <div class="metric">
          <div class="metric-label">支出</div>
          <div class="metric-value expense">¥{{ money(currentStats?.totalExpense) }}</div>
        </div>
        <div class="metric">
          <div class="metric-label">收入</div>
          <div class="metric-value income">¥{{ money(currentStats?.totalIncome) }}</div>
        </div>
        <div class="metric">
          <div class="metric-label">结余</div>
          <div class="metric-value">¥{{ money(currentStats?.balance) }}</div>
        </div>
      </section>

      <section class="section metric-grid">
        <div class="metric metric-compact">
          <div class="metric-label">总笔数</div>
          <div class="metric-value">{{ currentStats?.transactionCount || 0 }}</div>
        </div>
        <div class="metric metric-compact">
          <div class="metric-label">支出笔数</div>
          <div class="metric-value expense">{{ currentStats?.expenseCount || 0 }}</div>
        </div>
        <div class="metric metric-compact">
          <div class="metric-label">收入笔数</div>
          <div class="metric-value income">{{ currentStats?.incomeCount || 0 }}</div>
        </div>
      </section>

      <section class="section panel insight-panel">
        <div class="section-title">
          <span>{{ mode === 'YEARLY' ? '同比洞察' : '环比洞察' }}</span>
          <span class="muted">对比 {{ currentInsight?.previousPeriod || '-' }}</span>
        </div>
        <div class="insight-grid">
          <div class="insight-item">
            <div class="metric-label">支出变化</div>
            <div class="insight-value" :class="changeClass(currentInsight?.expenseChangeAmount, 'expense', 'income')">
              {{ signedMoney(currentInsight?.expenseChangeAmount) }}
            </div>
            <div class="insight-sub">{{ signedPercent(currentInsight?.expenseChangePercent) }}</div>
          </div>
          <div class="insight-item">
            <div class="metric-label">收入变化</div>
            <div class="insight-value" :class="changeClass(currentInsight?.incomeChangeAmount)">
              {{ signedMoney(currentInsight?.incomeChangeAmount) }}
            </div>
            <div class="insight-sub">{{ signedPercent(currentInsight?.incomeChangePercent) }}</div>
          </div>
          <div class="insight-item">
            <div class="metric-label">结余变化</div>
            <div class="insight-value" :class="changeClass(currentInsight?.balanceChangeAmount)">
              {{ signedMoney(currentInsight?.balanceChangeAmount) }}
            </div>
            <div class="insight-sub">{{ signedPercent(currentInsight?.balanceChangePercent) }}</div>
          </div>
          <div class="insight-item">
            <div class="metric-label">日均支出</div>
            <div class="insight-value">¥{{ money(currentInsight?.averageDailyExpense) }}</div>
            <div class="insight-sub">笔均 ¥{{ money(currentInsight?.averageExpensePerTransaction) }}</div>
          </div>
        </div>
        <div class="peak-line">
          <span class="muted">{{ mode === 'YEARLY' ? '高消费月' : '高消费日' }}</span>
          <strong v-if="currentInsight?.peakExpense">
            {{ currentInsight.peakExpense.label }} · ¥{{ money(currentInsight.peakExpense.amount) }} · {{ currentInsight.peakExpense.transactionCount }} 笔
          </strong>
          <strong v-else>暂无支出</strong>
        </div>
      </section>

      <section class="section panel chart-panel">
        <div class="section-title">
          <span>{{ mode === 'YEARLY' ? '年度趋势' : '月度趋势' }}</span>
          <span class="muted">点击柱线查看明细</span>
        </div>
        <div v-if="!activeTrend.length" class="empty-text">暂无趋势数据</div>
        <BaseChart v-else :option="trendChartOption" :height="260" @chart-click="openTrendFromChart" />
      </section>

      <section class="section panel chart-panel">
        <div class="section-title">
          <span>支出分类占比</span>
          <span class="muted">点击分类查看明细</span>
        </div>
        <div v-if="!categoryChartRows.length" class="empty-text">暂无支出分类</div>
        <BaseChart v-else :option="categoryChartOption" :height="270" @chart-click="openCategoryFromChart" />
      </section>

      <section class="section panel budget-panel">
        <div class="section-title">
          <span>预算进度</span>
          <van-button size="small" plain type="primary" icon="chart-trending-o" @click="openBudgets">预算管理</van-button>
        </div>
        <template v-if="mode === 'MONTHLY'">
          <div v-if="!monthlyBudget && categoryBudgetUsages.length === 0" class="empty-text">本月暂无预算</div>
          <div v-if="monthlyBudget" class="budget-card" :class="{ danger: monthlyBudget.overBudget }" @click="openBudgetRecords(monthlyBudget)">
            <div class="budget-head">
              <span>{{ monthlyBudget.categoryName }}</span>
              <strong>¥{{ money(monthlyBudget.usedAmount) }} / ¥{{ money(monthlyBudget.budgetAmount) }}</strong>
            </div>
            <van-progress
              :percentage="boundedProgress(monthlyBudget.usagePercent)"
              :color="monthlyBudget.overBudget ? '#e25555' : '#2f7d68'"
              stroke-width="7"
            />
            <div class="budget-meta">
              <span>{{ monthlyBudget.overBudget ? '超出' : '剩余' }} ¥{{ money(Math.abs(Number(monthlyBudget.remainingAmount))) }}</span>
              <span>{{ money(monthlyBudget.usagePercent) }}%</span>
            </div>
          </div>
          <van-cell
            v-for="item in categoryBudgetUsages"
            :key="item.categoryId || item.categoryName"
            :title="item.categoryName"
            is-link
            @click="openBudgetRecords(item)"
          >
            <template #label>
              <div class="summary-label">{{ item.transactionCount }} 笔 · {{ item.overBudget ? '已超预算' : `剩余 ¥${money(item.remainingAmount)}` }}</div>
              <van-progress
                :percentage="boundedProgress(item.usagePercent)"
                :color="item.overBudget ? '#e25555' : '#2f7d68'"
                stroke-width="6"
              />
            </template>
            <template #value>
              ¥{{ money(item.usedAmount) }} / ¥{{ money(item.budgetAmount) }}
            </template>
          </van-cell>
        </template>
        <div v-else class="budget-year-note">
          <div>预算按月管理，不做年度合并展示。</div>
          <div class="muted">可进入 {{ budgetButtonMonth }} 预算查看或调整。</div>
        </div>
      </section>

      <section class="section panel">
        <van-tabs v-model:active="activeTab" shrink>
          <van-tab title="分类">
            <div class="tab-pane">
              <van-cell title="支出分类" />
              <div v-if="!expenseByCategory.length" class="empty-text">暂无支出</div>
              <van-cell
                v-for="item in expenseByCategory"
                :key="item.categoryId"
                :title="item.categoryName"
                is-link
                @click="openCategoryRecords(item, 'EXPENSE')"
              >
                <template #label>
                  <div class="summary-label">{{ item.transactionCount }} 笔</div>
                  <van-progress :percentage="Number(percent(item, currentStats?.totalExpense).replace('%', ''))" stroke-width="6" />
                </template>
                <template #value>
                  ¥{{ money(item.amount) }} · {{ percent(item, currentStats?.totalExpense) }}
                </template>
              </van-cell>

              <van-cell title="收入分类" />
              <div v-if="!incomeByCategory.length" class="empty-text">暂无收入</div>
              <van-cell
                v-for="item in incomeByCategory"
                :key="item.categoryId"
                :title="item.categoryName"
                is-link
                @click="openCategoryRecords(item, 'INCOME')"
              >
                <template #label>
                  <div class="summary-label">{{ item.transactionCount }} 笔</div>
                  <van-progress :percentage="Number(percent(item, currentStats?.totalIncome).replace('%', ''))" stroke-width="6" color="#2f9b63" />
                </template>
                <template #value>
                  ¥{{ money(item.amount) }} · {{ percent(item, currentStats?.totalIncome) }}
                </template>
              </van-cell>
            </div>
          </van-tab>

          <van-tab title="趋势">
            <div class="tab-pane">
              <div v-if="!activeTrend.length" class="empty-text">暂无趋势数据</div>
              <div
                v-for="item in activeTrend"
                :key="trendKey(item)"
                class="trend-row clickable-row"
                role="button"
                tabindex="0"
                @click="openTrendRecords(item)"
                @keyup.enter="openTrendRecords(item)"
              >
                <div class="trend-date">{{ trendLabel(item) }}</div>
                <div class="trend-main">
                  <div class="trend-bar-track">
                    <div class="trend-bar" :style="{ width: barWidth(item.totalExpense, maxTrendExpense) }" />
                  </div>
                  <div class="trend-values">
                    <span class="expense">支出 ¥{{ money(item.totalExpense) }}</span>
                    <span v-if="Number(item.totalIncome) > 0" class="income">收入 ¥{{ money(item.totalIncome) }}</span>
                    <span class="muted">{{ item.transactionCount }} 笔</span>
                  </div>
                </div>
              </div>
            </div>
          </van-tab>

          <van-tab title="渠道">
            <div class="tab-pane">
              <div v-if="!expenseByChannel.length" class="empty-text">暂无渠道支出</div>
              <van-cell
                v-for="item in expenseByChannel"
                :key="item.channel"
                :title="channelLabel(item.channel)"
                is-link
                @click="openChannelRecords(item)"
              >
                <template #label>
                  <div class="summary-label">{{ item.transactionCount }} 笔</div>
                  <van-progress :percentage="progress(item.amount, currentStats?.totalExpense)" stroke-width="6" />
                </template>
                <template #value>
                  ¥{{ money(item.amount) }} · {{ amountPercent(item.amount, currentStats?.totalExpense) }}
                </template>
              </van-cell>
            </div>
          </van-tab>

          <van-tab title="支付">
            <div class="tab-pane">
              <div v-if="!expenseByPaymentMethod.length" class="empty-text">暂无支付方式支出</div>
              <van-cell
                v-for="item in expenseByPaymentMethod"
                :key="item.paymentMethodId"
                :title="item.paymentMethodName"
                is-link
                @click="openPaymentMethodRecords(item)"
              >
                <template #label>
                  <div class="summary-label">{{ item.transactionCount }} 笔</div>
                  <van-progress :percentage="progress(item.amount, currentStats?.totalExpense)" stroke-width="6" />
                </template>
                <template #value>
                  ¥{{ money(item.amount) }} · {{ amountPercent(item.amount, currentStats?.totalExpense) }}
                </template>
              </van-cell>
            </div>
          </van-tab>
        </van-tabs>
      </section>
    </div>

  </main>
</template>

<style scoped>
.metric-compact {
  min-height: 62px;
}

.period-switch {
  padding: 0 0 12px;
}

.tab-pane {
  padding-top: 8px;
}

.section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 12px;
  font-size: 15px;
  font-weight: 700;
}

.insight-panel,
.chart-panel,
.budget-panel {
  overflow: hidden;
}

.insight-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.insight-item {
  min-width: 0;
  border-radius: 8px;
  padding: 10px;
  background: #f7f8fa;
}

.insight-value {
  margin-top: 6px;
  font-size: 17px;
  font-weight: 700;
}

.insight-sub {
  margin-top: 4px;
  color: #6b7280;
  font-size: 12px;
}

.muted-value {
  color: #6b7280;
}

.peak-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 12px;
  border-top: 1px solid #eef1f4;
  padding-top: 12px;
  font-size: 13px;
}

.peak-line strong {
  min-width: 0;
  text-align: right;
}

.budget-card {
  margin-bottom: 8px;
  border: 1px solid #e4eee9;
  border-radius: 8px;
  padding: 12px;
  background: #f6fbf9;
  cursor: pointer;
}

.budget-card.danger {
  border-color: #f5c9c9;
  background: #fff7f7;
}

.budget-head,
.budget-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.budget-head {
  margin-bottom: 10px;
  font-size: 14px;
}

.budget-meta {
  margin-top: 8px;
  color: #6b7280;
  font-size: 12px;
}

.budget-year-note {
  border-radius: 8px;
  padding: 12px;
  background: #f7f8fa;
  font-size: 14px;
  line-height: 22px;
}

.summary-label {
  margin-bottom: 6px;
  color: #8a949b;
  font-size: 12px;
}

.trend-row {
  display: grid;
  grid-template-columns: 48px 1fr;
  gap: 10px;
  padding: 12px 0;
  border-bottom: 1px solid #f0f2f5;
}

.trend-row:last-child {
  border-bottom: 0;
}

.clickable-row {
  cursor: pointer;
}

.trend-date {
  color: #6b7280;
  font-size: 13px;
  line-height: 22px;
}

.trend-main {
  min-width: 0;
}

.trend-bar-track {
  height: 8px;
  overflow: hidden;
  border-radius: 999px;
  background: #eef1f4;
}

.trend-bar {
  height: 100%;
  border-radius: inherit;
  background: var(--expense);
}

.trend-values {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 6px;
  font-size: 12px;
  line-height: 18px;
}
</style>
