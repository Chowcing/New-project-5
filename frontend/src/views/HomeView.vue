<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { categoryApi, recurringRunApi, statisticsApi, transactionApi } from '@/api/services'
import ModernDateField from '@/components/ModernDateField.vue'
import { useAuthStore } from '@/stores/auth'
import type { BudgetUsageSummary, Category, MonthlyStatistics, RecurringRuleRun, TransactionRecord } from '@/types'
import { currentMonth, money, todayDate } from '@/utils/date'
import { recurringEntryTitle, transactionTitle } from '@/utils/display'
import { showError } from '@/utils/errors'
import { loadWorkspaceMonth, saveWorkspaceMonth } from '@/utils/preferences'
import { dueStatusText, runStatusLabel } from '@/utils/recurring'

const auth = useAuthStore()
const month = ref(loadWorkspaceMonth() || currentMonth())
const stats = ref<MonthlyStatistics | null>(null)
const categories = ref<Category[]>([])
const recent = ref<TransactionRecord[]>([])
const dueRuns = ref<RecurringRuleRun[]>([])

const balanceClass = computed(() => Number(stats.value?.balance || 0) >= 0 ? 'income' : 'expense')
const transactionCountText = computed(() => `${stats.value?.transactionCount || 0} 笔流水`)
const budgetRisk = computed<BudgetUsageSummary | null>(() => {
  const candidates = [
    stats.value?.monthlyBudget,
    ...(stats.value?.categoryBudgetUsages || [])
  ].filter(Boolean) as BudgetUsageSummary[]
  return candidates.sort((left, right) => Number(right.usagePercent || 0) - Number(left.usagePercent || 0))[0] || null
})
const budgetRiskName = computed(() => budgetRisk.value?.categoryName || '暂无预算')
const budgetRiskPercent = computed(() => budgetRisk.value ? `${money(budgetRisk.value.usagePercent)}%` : '去设置')

async function load() {
  saveWorkspaceMonth(month.value)
  try {
    if (!auth.user) {
      await auth.fetchMe()
    }
    const [nextStats, page, nextDueRuns, categoryRows] = await Promise.all([
      statisticsApi.monthly(month.value),
      transactionApi.list({ startDate: `${month.value}-01`, page: 1, size: 5 }),
      recurringRunApi.due(todayDate()),
      categoryApi.list()
    ])
    stats.value = nextStats
    recent.value = page.records
    dueRuns.value = nextDueRuns
    categories.value = categoryRows
  } catch (error) {
    showError(error, '首页数据加载失败')
  }
}

function offsetDate(value: string, offset: number) {
  const date = new Date(`${value}T00:00:00`)
  date.setDate(date.getDate() + offset)
  const year = date.getFullYear()
  const monthValue = `${date.getMonth() + 1}`.padStart(2, '0')
  const day = `${date.getDate()}`.padStart(2, '0')
  return `${year}-${monthValue}-${day}`
}

function recordTime(value: string) {
  return value.slice(11, 16)
}

function recordDisplayTime(value: string) {
  const date = value.slice(0, 10)
  const today = todayDate()
  let dateText = `${Number(date.slice(5, 7))}月${Number(date.slice(8, 10))}日`
  if (date === today) {
    dateText = '今天'
  } else if (date === offsetDate(today, -1)) {
    dateText = '昨天'
  }
  return `${dateText} ${recordTime(value)}`
}

function recordCategoryIcon(item: TransactionRecord) {
  return item.categoryIcon || categories.value.find((category) => category.id === item.categoryId)?.icon || 'records-o'
}

onMounted(load)
</script>

<template>
  <main class="page workspace-page">
    <div class="page-content workspace-content">
      <section class="section panel workspace-hero">
        <div class="workspace-hero-top">
          <div class="workspace-title-group">
            <span class="workspace-kicker">FINANCE OS</span>
            <h1>你好，{{ auth.user?.nickname || '用户' }}</h1>
            <p>{{ month }} · {{ transactionCountText }}</p>
          </div>
          <ModernDateField v-model="month" mode="month" label="月份" title="选择月份" @change="load">
            <template #trigger="{ displayValue, open }">
              <button class="workspace-month-button" type="button" @click="open">
                <van-icon name="calendar-o" />
                <span>{{ displayValue }}</span>
              </button>
            </template>
          </ModernDateField>
        </div>

        <div class="workspace-balance">
          <span>本月净额</span>
          <strong :class="balanceClass">¥{{ money(stats?.balance) }}</strong>
        </div>

        <div class="workspace-command-grid">
          <RouterLink class="workspace-command primary" to="/quick-add">
            <van-icon name="plus" />
            <span>记一笔</span>
          </RouterLink>
          <RouterLink class="workspace-command" to="/records">
            <van-icon name="orders-o" />
            <span>查流水</span>
          </RouterLink>
          <RouterLink class="workspace-command" to="/statistics">
            <van-icon name="bar-chart-o" />
            <span>看分析</span>
          </RouterLink>
        </div>
      </section>

      <section class="section metric-grid workspace-metrics">
        <div class="metric">
          <div class="metric-label"><van-icon name="cart-o" />支出</div>
          <div class="metric-value expense">¥{{ money(stats?.totalExpense) }}</div>
        </div>
        <div class="metric">
          <div class="metric-label"><van-icon name="cash-back-record" />收入</div>
          <div class="metric-value income">¥{{ money(stats?.totalIncome) }}</div>
        </div>
        <RouterLink class="metric workspace-budget-tile" to="/budgets">
          <div class="metric-label"><van-icon name="chart-trending-o" />预算风险</div>
          <div class="metric-value workspace-budget-value" :class="{ expense: budgetRisk?.overBudget }">
            <span>{{ budgetRiskName }}</span>
            <strong>{{ budgetRiskPercent }}</strong>
          </div>
        </RouterLink>
      </section>

      <section class="section panel workspace-list-panel">
        <div class="section-heading workspace-panel-heading">
          <span class="workspace-panel-title"><van-icon name="orders-o" />最近流水</span>
          <van-button size="small" plain type="primary" icon="arrow" to="/records">全部</van-button>
        </div>
        <div v-if="recent.length === 0" class="empty-text">暂无记录</div>
        <RouterLink
          v-for="item in recent"
          :key="item.id"
          class="workspace-list-row"
          :to="`/records/${item.id}`"
        >
          <span class="workspace-row-icon">
            <van-icon :name="recordCategoryIcon(item)" />
          </span>
          <span class="workspace-row-main">
            <span class="workspace-row-title">{{ transactionTitle(item) }}</span>
            <span class="workspace-row-meta">{{ recordDisplayTime(item.occurredAt) }}</span>
          </span>
          <span class="workspace-row-side">
            <span :class="['workspace-row-amount', item.type === 'EXPENSE' ? 'expense' : 'income']">
              {{ item.type === 'EXPENSE' ? '-' : '+' }}¥{{ money(item.amount) }}
            </span>
            <span class="workspace-row-category">{{ item.categoryName }}</span>
          </span>
        </RouterLink>
      </section>

      <section class="section panel workspace-list-panel">
        <div class="section-heading workspace-panel-heading">
          <span class="workspace-panel-title"><van-icon name="replay" />周期事项</span>
          <van-button size="small" plain type="primary" icon="setting-o" to="/recurring-rules">管理</van-button>
        </div>
        <div v-if="dueRuns.length === 0" class="empty-text">暂无待处理周期记录</div>
        <RouterLink
          v-for="item in dueRuns.slice(0, 3)"
          :key="item.id"
          class="workspace-list-row"
          to="/recurring-rules"
        >
          <span class="workspace-row-icon recurring-mark">
            <van-icon name="clock-o" />
          </span>
          <span class="workspace-row-main">
            <span class="workspace-row-title">{{ item.ruleName }}</span>
            <span class="workspace-row-meta">{{ recurringEntryTitle(item) }} · {{ dueStatusText(item, todayDate()) }} · {{ runStatusLabel(item.status) }}</span>
          </span>
          <span :class="['workspace-row-amount', item.type === 'EXPENSE' ? 'expense' : 'income']">
            {{ item.type === 'EXPENSE' ? '-' : '+' }}¥{{ money(item.amount) }}
          </span>
        </RouterLink>
        <RouterLink v-if="dueRuns.length > 3" class="workspace-more-row" to="/recurring-rules">
          <van-icon name="arrow" />
          <span>查看更多</span>
        </RouterLink>
      </section>
    </div>
  </main>
</template>

<style scoped>
.workspace-content {
  gap: var(--space-10);
}

.workspace-hero {
  display: grid;
  gap: var(--space-16);
  padding: var(--space-16);
  overflow: hidden;
  background:
    radial-gradient(circle at 88% 6%, rgba(var(--theme-primary-glow-rgb), 0.28), transparent 38%),
    linear-gradient(135deg, rgba(var(--theme-primary-glow-rgb), 0.16), rgba(var(--theme-border-warm-rgb), 0.06)),
    var(--card-bg);
}

.workspace-hero-top {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: var(--space-12);
  align-items: start;
}

.workspace-title-group {
  min-width: 0;
}

.workspace-kicker {
  display: block;
  color: var(--primary);
  font-size: var(--font-size-caption);
  font-weight: 750;
  line-height: var(--line-height-caption);
}

.workspace-title-group h1 {
  margin: var(--space-3) 0 var(--space-2);
  overflow: hidden;
  color: var(--text-main);
  font-size: var(--font-size-panel-title);
  line-height: var(--line-height-panel-title);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workspace-title-group p {
  margin: 0;
  color: var(--text-secondary);
  font-size: var(--font-size-caption);
  line-height: var(--line-height-caption);
}

.workspace-month-button {
  display: inline-flex;
  align-items: center;
  gap: var(--space-6);
  min-height: 34px;
  border: 1px solid rgba(var(--theme-border-warm-rgb), 0.22);
  border-radius: var(--radius-pill);
  padding: var(--space-0) var(--space-10);
  background: rgba(var(--theme-border-warm-rgb), 0.08);
  color: var(--text-main);
  font-size: var(--font-size-caption);
  font-weight: 650;
}

.workspace-balance {
  display: grid;
  gap: var(--space-6);
}

.workspace-balance span {
  color: var(--text-secondary);
  font-size: var(--font-size-caption);
}

.workspace-balance strong {
  overflow: hidden;
  font-size: var(--font-size-amount-large);
  line-height: var(--line-height-amount-large);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workspace-command-grid {
  display: grid;
  grid-template-columns: 1.35fr 1fr 1fr;
  gap: var(--space-8);
}

.workspace-command {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-6);
  min-height: 42px;
  border: 1px solid rgba(var(--theme-border-warm-rgb), 0.2);
  border-radius: var(--radius-card);
  background: rgba(var(--theme-border-warm-rgb), 0.08);
  color: var(--text-main);
  font-size: var(--font-size-meta);
  font-weight: 700;
}

.workspace-command.primary {
  border-color: transparent;
  background: linear-gradient(135deg, var(--primary), var(--primary-deep));
  color: #fff;
  box-shadow: 0 16px 34px rgba(var(--theme-primary-glow-rgb), 0.28);
}

.workspace-budget-tile {
  display: grid;
  align-content: space-between;
  color: inherit;
}

.workspace-budget-value {
  display: grid;
  gap: var(--space-2);
  overflow: visible;
  font-size: var(--font-size-body-strong);
  line-height: var(--line-height-body-strong);
  white-space: normal;
}

.workspace-budget-value span,
.workspace-budget-value strong {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
}

.workspace-budget-value strong {
  font-size: var(--font-size-meta);
  line-height: var(--line-height-meta);
}

.workspace-list-panel {
  padding: 0;
  overflow: hidden;
}

.workspace-panel-heading {
  margin-bottom: 0;
  padding: var(--space-14) var(--space-14) var(--space-10);
  border-bottom: 1px solid rgba(var(--theme-border-warm-rgb), 0.18);
}

.workspace-panel-title {
  display: inline-flex;
  align-items: center;
  gap: var(--space-6);
  min-width: 0;
}

.workspace-panel-title :deep(.van-icon) {
  color: var(--primary);
  font-size: var(--icon-size-md);
}

.workspace-list-row {
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr) auto;
  gap: var(--space-10);
  align-items: center;
  min-height: var(--space-64);
  padding: var(--space-10) var(--space-12);
  color: inherit;
}

.workspace-list-row:not(:last-child) {
  border-bottom: 1px solid rgba(var(--theme-border-warm-rgb), 0.16);
}

.workspace-row-icon {
  display: grid;
  width: 38px;
  height: 38px;
  place-items: center;
  border-radius: var(--radius-card);
  background: var(--primary-soft);
  color: var(--primary);
  font-size: var(--icon-size-lg);
}

.recurring-mark {
  background: var(--income-soft);
  color: var(--income);
}

.workspace-row-main {
  display: grid;
  min-width: 0;
  gap: var(--space-4);
}

.workspace-row-title {
  overflow: hidden;
  color: var(--text-main);
  font-size: var(--font-size-body-strong);
  font-weight: 650;
  line-height: var(--line-height-body-strong);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workspace-row-meta {
  overflow: hidden;
  color: var(--text-secondary);
  font-size: var(--font-size-caption);
  line-height: var(--line-height-caption);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workspace-row-side {
  display: grid;
  min-width: 78px;
  justify-items: end;
  gap: var(--space-4);
}

.workspace-row-amount {
  max-width: 96px;
  font-size: var(--font-size-body);
  font-weight: 750;
  line-height: var(--line-height-body);
  text-align: right;
  white-space: nowrap;
}

.workspace-row-category {
  max-width: 92px;
  overflow: hidden;
  color: var(--text-secondary);
  font-size: var(--font-size-caption);
  line-height: var(--line-height-caption);
  text-align: right;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workspace-more-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-6);
  min-height: var(--space-42);
  border-top: 1px solid rgba(var(--theme-border-warm-rgb), 0.16);
  color: var(--primary);
  font-size: var(--font-size-meta);
  font-weight: 650;
}

@media (max-width: 360px) {
  .workspace-command-grid {
    grid-template-columns: 1fr;
  }

  .workspace-list-row {
    grid-template-columns: 34px minmax(0, 1fr) auto;
    gap: var(--space-8);
    padding: var(--space-10);
  }

  .workspace-row-icon {
    width: 34px;
    height: 34px;
  }

  .workspace-row-side {
    min-width: 68px;
  }

  .workspace-row-amount {
    max-width: 82px;
  }

  .workspace-row-category {
    max-width: 78px;
  }
}
</style>
