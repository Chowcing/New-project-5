<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
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
import { loadStatisticsPreference, saveStatisticsPreference } from '@/utils/preferences'
import { getCurrentThemeTokens, type ThemeTokens } from '@/utils/themes'

type PeriodMode = 'MONTHLY' | 'YEARLY'
type BreakdownPanel = 'CATEGORY' | 'CHANNEL' | 'PAYMENT'
type TrendSummary = DailySummary | MonthlyTrendSummary
type PieChartData = {
  name: string
  value: number
  categoryId?: number
  channel?: ChannelSummary['channel']
  paymentMethodId?: number
  type: 'EXPENSE' | 'INCOME'
  isOther?: boolean
}

const router = useRouter()
const savedStatisticsPreference = loadStatisticsPreference()
const currentYear = new Date().getFullYear()
const mode = ref<PeriodMode>(savedStatisticsPreference?.mode || 'MONTHLY')
const breakdownPanel = ref<BreakdownPanel>(savedStatisticsPreference?.breakdownPanel || 'CATEGORY')
const breakdownTransitionName = ref('panel-slide-left')
const month = ref(savedStatisticsPreference?.month || currentMonth())
const themeTokens = ref(getCurrentThemeTokens())
const minYear = 2000
const statsMinDate = new Date(minYear, 0, 1)
const statsMaxDate = new Date(currentYear, 11, 31)
const year = ref(savedStatisticsPreference?.year || String(currentYear))
const monthlyStats = ref<MonthlyStatistics | null>(null)
const yearlyStats = ref<YearlyStatistics | null>(null)

const currentStats = computed(() => {
  return mode.value === 'YEARLY' ? yearlyStats.value : monthlyStats.value
})

const currentInsight = computed(() => currentStats.value?.insight)

const breakdownPanelOrder: BreakdownPanel[] = ['CATEGORY', 'CHANNEL', 'PAYMENT']

const trendRows = computed<TrendSummary[]>(() => {
  return mode.value === 'YEARLY' ? yearlyStats.value?.monthlyTrend || [] : monthlyStats.value?.dailyTrend || []
})

const activeTrend = computed(() => {
  return trendRows.value.filter((item) => {
    return Number(item.totalExpense) > 0 || Number(item.totalIncome) > 0
  })
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

const expenseByChannel = computed(() => currentStats.value?.expenseByChannel || [])

const expenseByPaymentMethod = computed(() => currentStats.value?.expenseByPaymentMethod || [])

const categoryChartRows = computed<PieChartData[]>(() => {
  const rows = expenseByCategory.value
  const topRows: PieChartData[] = rows.slice(0, 5).map((item) => ({
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

const channelChartRows = computed<PieChartData[]>(() => {
  return expenseByChannel.value.map((item) => ({
    name: channelLabel(item.channel),
    value: Number(item.amount || 0),
    channel: item.channel,
    type: 'EXPENSE' as const
  }))
})

const paymentMethodChartRows = computed<PieChartData[]>(() => {
  const topRows: PieChartData[] = expenseByPaymentMethod.value.slice(0, 6).map((item) => ({
    name: item.paymentMethodName,
    value: Number(item.amount || 0),
    paymentMethodId: item.paymentMethodId,
    type: 'EXPENSE' as const
  }))
  const restAmount = expenseByPaymentMethod.value.slice(6).reduce((sum, item) => sum + Number(item.amount || 0), 0)
  if (restAmount > 0) {
    topRows.push({
      name: '其他',
      value: restAmount,
      type: 'EXPENSE' as const,
      isOther: true
    })
  }
  return topRows
})

const budgetNormalColor = computed(() => themeTokens.value.income)
const budgetDangerColor = computed(() => themeTokens.value.expense)

function persistStatisticsPreference() {
  saveStatisticsPreference({
    mode: mode.value,
    month: month.value,
    year: year.value,
    breakdownPanel: breakdownPanel.value
  })
}

const trendChartOption = computed<EChartsOption>(() => {
  const tokens = themeTokens.value
  return {
    backgroundColor: 'transparent',
    color: [tokens.expense, tokens.income],
    tooltip: { trigger: 'axis', backgroundColor: tokens.glassStrongBg, borderColor: tokens.borderWarm, textStyle: { color: tokens.textMain } },
    legend: {
      top: 0,
      itemWidth: 10,
      itemHeight: 10,
      textStyle: { color: tokens.textSecondary, fontSize: 12 }
    },
    grid: { top: 42, left: 8, right: 8, bottom: 10, containLabel: true },
    xAxis: {
      type: 'category',
      data: trendRows.value.map(trendLabel),
      axisTick: { show: false },
      axisLine: { lineStyle: { color: tokens.chartAxis } },
      axisLabel: { color: tokens.textSecondary, fontSize: 11 }
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: tokens.textSecondary, fontSize: 11 },
      splitLine: { lineStyle: { color: tokens.chartAxis, type: 'dashed' } }
    },
    series: [
      {
        name: '支出',
        type: 'bar',
        barMaxWidth: 18,
        itemStyle: { borderRadius: [8, 8, 2, 2] },
        data: trendRows.value.map((item) => ({ value: Number(item.totalExpense || 0), period: trendKey(item) }))
      },
      {
        name: '收入',
        type: 'line',
        smooth: true,
        symbolSize: 6,
        lineStyle: { width: 3 },
        data: trendRows.value.map((item) => ({ value: Number(item.totalIncome || 0), period: trendKey(item) }))
      }
    ]
  }
})

function pieChartOption(name: string, data: PieChartData[]): EChartsOption {
  const tokens = themeTokens.value
  return {
    backgroundColor: 'transparent',
    color: tokens.chartPalette,
    tooltip: { trigger: 'item', backgroundColor: tokens.glassStrongBg, borderColor: tokens.borderWarm, textStyle: { color: tokens.textMain } },
    legend: {
      bottom: 0,
      type: 'scroll',
      itemWidth: 10,
      itemHeight: 10,
      textStyle: { color: tokens.textSecondary, fontSize: 12 }
    },
    series: [
      {
        name,
        type: 'pie',
        radius: ['44%', '68%'],
        center: ['50%', '44%'],
        avoidLabelOverlap: true,
        label: { formatter: '{b}\n{d}%', color: tokens.textMain, fontSize: 11 },
        emphasis: {
          scaleSize: 8,
          itemStyle: { shadowBlur: 18, shadowColor: tokens.primary }
        },
        data
      }
    ]
  }
}

const categoryChartOption = computed<EChartsOption>(() => pieChartOption('支出分类', categoryChartRows.value))

const channelChartOption = computed<EChartsOption>(() => pieChartOption('支出渠道', channelChartRows.value))

const paymentMethodChartOption = computed<EChartsOption>(() => pieChartOption('支付方式', paymentMethodChartRows.value))

watch(breakdownPanel, (next, previous) => {
  breakdownTransitionName.value = breakdownPanelOrder.indexOf(next) > breakdownPanelOrder.indexOf(previous)
    ? 'panel-slide-left'
    : 'panel-slide-right'
  persistStatisticsPreference()
})

watch(mode, () => {
  persistStatisticsPreference()
})

watch(month, () => {
  persistStatisticsPreference()
})

watch(year, () => {
  persistStatisticsPreference()
})

function previousMonth(value: string) {
  const [yearNumber, monthNumber] = value.split('-').map(Number)
  const date = new Date(yearNumber, monthNumber - 2, 1)
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`
}

async function chooseMonth(value: string) {
  if (month.value === value) return
  month.value = value
  persistStatisticsPreference()
  await load()
}

async function chooseYear(value: string) {
  if (year.value === value) return
  year.value = value
  persistStatisticsPreference()
  await load()
}

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

function syncThemeTokens(event?: Event) {
  const nextTokens = event instanceof CustomEvent ? event.detail as ThemeTokens : getCurrentThemeTokens()
  themeTokens.value = nextTokens
}

function boundedProgress(value: number | string | undefined) {
  return Math.min(100, Math.max(0, Number(value || 0)))
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
  channel?: ChannelSummary['channel']
  paymentMethodId?: number
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

async function openChannelFromChart(params: unknown) {
  const data = chartData(params)
  if (!data?.channel) return
  await openChannelRecords({
    channel: data.channel,
    amount: data.value || 0,
    transactionCount: 0
  })
}

async function openPaymentMethodFromChart(params: unknown) {
  const data = chartData(params)
  if (!data || data.isOther || !data.paymentMethodId) return
  await openPaymentMethodRecords({
    paymentMethodId: data.paymentMethodId,
    paymentMethodName: data.name || '支付方式',
    amount: data.value || 0,
    transactionCount: 0
  })
}

onMounted(() => {
  load()
  window.addEventListener('theme-change', syncThemeTokens)
})

onBeforeUnmount(() => {
  window.removeEventListener('theme-change', syncThemeTokens)
})
</script>

<template>
  <main class="page analysis-page">
    <div class="page-content analysis-content">
      <section class="section panel analysis-control-panel">
        <van-radio-group v-model="mode" class="period-switch" direction="horizontal" @change="load">
          <van-radio name="MONTHLY">月度</van-radio>
          <van-radio name="YEARLY">年度</van-radio>
        </van-radio-group>
        <template v-if="mode === 'MONTHLY'">
          <ModernDateField
            v-model="month"
            mode="month"
            label="月份"
            title="选择月份"
            @change="load"
          />
          <div class="period-shortcuts">
            <van-button
              size="small"
              plain
              type="primary"
              icon="arrow-left"
              :disabled="month === previousMonth(currentMonth())"
              @click="chooseMonth(previousMonth(currentMonth()))"
            >
              上个月
            </van-button>
            <van-button
              size="small"
              plain
              type="primary"
              icon="calendar-o"
              :disabled="month === currentMonth()"
              @click="chooseMonth(currentMonth())"
            >
              本月
            </van-button>
          </div>
        </template>
        <template v-else>
          <ModernDateField
            v-model="year"
            mode="year"
            label="年份"
            title="选择年份"
            :min-date="statsMinDate"
            :max-date="statsMaxDate"
            @change="load"
          />
          <div class="period-shortcuts">
            <van-button
              size="small"
              plain
              type="primary"
              icon="arrow-left"
              :disabled="year === String(currentYear - 1)"
              @click="chooseYear(String(currentYear - 1))"
            >
              去年
            </van-button>
            <van-button
              size="small"
              plain
              type="primary"
              icon="calendar-o"
              :disabled="year === String(currentYear)"
              @click="chooseYear(String(currentYear))"
            >
              今年
            </van-button>
          </div>
        </template>
      </section>

      <section class="section metric-grid analysis-primary-metrics">
        <div class="metric">
          <div class="metric-label"><van-icon name="cart-o" />支出</div>
          <div class="metric-value expense">¥{{ money(currentStats?.totalExpense) }}</div>
        </div>
        <div class="metric">
          <div class="metric-label"><van-icon name="cash-back-record" />收入</div>
          <div class="metric-value income">¥{{ money(currentStats?.totalIncome) }}</div>
        </div>
        <div class="metric">
          <div class="metric-label"><van-icon name="balance-o" />结余</div>
          <div class="metric-value">¥{{ money(currentStats?.balance) }}</div>
        </div>
      </section>

      <section class="section metric-grid analysis-secondary-metrics">
        <div class="metric metric-compact">
          <div class="metric-label"><van-icon name="orders-o" />总笔数</div>
          <div class="metric-value">{{ currentStats?.transactionCount || 0 }}</div>
        </div>
        <div class="metric metric-compact">
          <div class="metric-label"><van-icon name="cart-o" />支出笔数</div>
          <div class="metric-value expense">{{ currentStats?.expenseCount || 0 }}</div>
        </div>
        <div class="metric metric-compact">
          <div class="metric-label"><van-icon name="cash-back-record" />收入笔数</div>
          <div class="metric-value income">{{ currentStats?.incomeCount || 0 }}</div>
        </div>
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
              :color="monthlyBudget.overBudget ? budgetDangerColor : budgetNormalColor"
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
                :color="item.overBudget ? budgetDangerColor : budgetNormalColor"
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
          <span class="muted">点击趋势查看明细</span>
        </div>
        <div class="analysis-chart">
          <div v-if="!activeTrend.length" class="empty-text">暂无趋势数据</div>
          <BaseChart v-else :option="trendChartOption" :height="260" @chart-click="openTrendFromChart" />
        </div>
      </section>

      <section class="section panel breakdown-panel">
        <div class="section-title">
          <span>支出占比</span>
          <span class="muted">点击图表查看明细</span>
        </div>
        <van-radio-group v-model="breakdownPanel" class="analysis-switch" direction="horizontal">
          <van-radio name="CATEGORY">分类</van-radio>
          <van-radio name="CHANNEL">渠道</van-radio>
          <van-radio name="PAYMENT">支付方式</van-radio>
        </van-radio-group>

        <Transition :name="breakdownTransitionName" mode="out-in">
          <div v-if="breakdownPanel === 'CATEGORY'" key="category" class="analysis-chart">
            <div v-if="!categoryChartRows.length" class="empty-text">暂无支出分类</div>
            <BaseChart v-else :option="categoryChartOption" :height="270" @chart-click="openCategoryFromChart" />
          </div>
          <div v-else-if="breakdownPanel === 'CHANNEL'" key="channel" class="analysis-chart">
            <div v-if="!channelChartRows.length" class="empty-text">暂无渠道支出</div>
            <BaseChart v-else :option="channelChartOption" :height="250" @chart-click="openChannelFromChart" />
          </div>
          <div v-else key="payment" class="analysis-chart">
            <div v-if="!paymentMethodChartRows.length" class="empty-text">暂无支付方式支出</div>
            <BaseChart v-else :option="paymentMethodChartOption" :height="270" @chart-click="openPaymentMethodFromChart" />
          </div>
        </Transition>
      </section>
    </div>

  </main>
</template>

<style scoped>
.analysis-content {
  gap: var(--space-10);
}

.analysis-control-panel {
  overflow: hidden;
  background:
    radial-gradient(circle at 92% 0%, rgba(var(--theme-primary-glow-rgb), 0.2), transparent 36%),
    var(--card-bg);
}

.analysis-primary-metrics .metric {
  min-height: 86px;
}

.analysis-secondary-metrics .metric {
  min-height: 62px;
  background: rgba(var(--theme-border-warm-rgb), 0.08);
}

.metric-compact {
  min-height: 62px;
}

.period-switch {
  padding: var(--space-0) var(--space-0) var(--space-12);
}

.period-switch :deep(.van-radio) {
  min-height: 32px;
}

.period-shortcuts {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-8);
  padding: var(--space-10) var(--space-0) var(--space-0);
}

.section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-10);
  margin-bottom: var(--space-12);
  font-size: var(--font-size-body-strong);
  font-weight: 700;
}

.insight-panel,
.breakdown-panel,
.chart-panel,
.budget-panel {
  overflow: hidden;
  background:
    linear-gradient(135deg, rgba(var(--theme-primary-glow-rgb), 0.08), transparent 42%),
    var(--card-bg);
}

.analysis-switch {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-4);
  margin-bottom: var(--space-12);
  padding: var(--space-4);
  border-radius: var(--radius-card);
  border: 1px solid rgba(var(--theme-border-warm-rgb), 0.16);
  background: rgba(var(--theme-border-warm-rgb), 0.08);
}

.analysis-switch :deep(.van-radio) {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 32px;
  margin-right: var(--space-0);
  padding: var(--space-0) var(--space-10);
  border-radius: var(--radius-inner);
  color: var(--text-secondary);
  font-size: var(--font-size-meta);
  font-weight: 600;
  line-height: var(--line-height-meta);
}

.analysis-switch :deep(.van-radio__icon) {
  display: none;
}

.analysis-switch :deep(.van-radio__label) {
  margin: var(--space-0);
}

.analysis-switch :deep(.van-radio[aria-checked='true']) {
  background: var(--glass-strong-bg);
  box-shadow: 0 8px 18px rgba(var(--theme-shadow-warm-rgb), 0.18);
  color: var(--primary);
}

.analysis-switch :deep(.van-radio[aria-checked='true'] .van-radio__label) {
  color: var(--primary);
}

.analysis-chart {
  min-height: 260px;
  border: 1px solid rgba(var(--theme-border-warm-rgb), 0.12);
  border-radius: var(--radius-card);
  background: rgba(var(--theme-border-warm-rgb), 0.06);
  padding: var(--space-8);
}

.panel-slide-left-enter-active,
.panel-slide-left-leave-active,
.panel-slide-right-enter-active,
.panel-slide-right-leave-active {
  transition: opacity 220ms ease, transform 220ms cubic-bezier(0.22, 1, 0.36, 1);
}

.panel-slide-left-enter-from {
  opacity: 0;
  transform: translateX(24px);
}

.panel-slide-left-leave-to {
  opacity: 0;
  transform: translateX(-24px);
}

.panel-slide-right-enter-from {
  opacity: 0;
  transform: translateX(-24px);
}

.panel-slide-right-leave-to {
  opacity: 0;
  transform: translateX(24px);
}

.insight-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-10);
}

.insight-item {
  min-width: 0;
  border: 1px solid rgba(var(--theme-border-warm-rgb), 0.14);
  border-radius: var(--radius-card);
  padding: var(--space-10);
  background: rgba(var(--theme-border-warm-rgb), 0.08);
}

.insight-value {
  margin-top: var(--space-6);
  font-size: var(--font-size-panel-title);
  font-weight: 700;
}

.insight-sub {
  margin-top: var(--space-4);
  color: var(--text-secondary);
  font-size: var(--font-size-caption);
}

.muted-value {
  color: var(--text-secondary);
}

.peak-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-12);
  margin-top: var(--space-12);
  border-top: 1px solid var(--border-warm);
  padding-top: var(--space-12);
  font-size: var(--font-size-meta);
}

.peak-line strong {
  min-width: 0;
  text-align: right;
}

.budget-card {
  margin-bottom: var(--space-8);
  border: 1px solid rgba(var(--theme-border-warm-rgb), 0.18);
  border-radius: var(--radius-card);
  padding: var(--space-12);
  background: var(--income-soft);
  cursor: pointer;
}

.budget-card.danger {
  border-color: rgba(214, 91, 74, 0.32);
  background: var(--expense-soft);
}

.budget-head,
.budget-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-10);
}

.budget-head {
  margin-bottom: var(--space-10);
  font-size: var(--font-size-body);
}

.budget-meta {
  margin-top: var(--space-8);
  color: var(--text-secondary);
  font-size: var(--font-size-caption);
}

.budget-year-note {
  border-radius: var(--radius-card);
  padding: var(--space-12);
  background: rgba(var(--theme-border-warm-rgb), 0.08);
  font-size: var(--font-size-body);
  line-height: var(--line-height-body-strong);
}

.summary-label {
  margin-bottom: var(--space-6);
  color: var(--text-muted);
  font-size: var(--font-size-caption);
}

</style>
