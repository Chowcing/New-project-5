<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { recurringRunApi, statisticsApi, transactionApi } from '@/api/services'
import ModernDateField from '@/components/ModernDateField.vue'
import { useAuthStore } from '@/stores/auth'
import type { BudgetUsageSummary, MonthlyStatistics, RecurringRuleRun, TransactionRecord } from '@/types'
import { currentMonth, money, todayDate } from '@/utils/date'
import { showError } from '@/utils/errors'
import { dueStatusText, runStatusLabel } from '@/utils/recurring'

const auth = useAuthStore()
const month = ref(currentMonth())
const stats = ref<MonthlyStatistics | null>(null)
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
const budgetRiskText = computed(() => {
  const risk = budgetRisk.value
  if (!risk) return '暂无预算监控'
  return `${risk.categoryName} ${money(risk.usagePercent)}%`
})

async function load() {
  try {
    if (!auth.user) {
      await auth.fetchMe()
    }
    const [nextStats, page, nextDueRuns] = await Promise.all([
      statisticsApi.monthly(month.value),
      transactionApi.list({ startDate: `${month.value}-01`, page: 1, size: 5 }),
      recurringRunApi.due(todayDate())
    ])
    stats.value = nextStats
    recent.value = page.records
    dueRuns.value = nextDueRuns
  } catch (error) {
    showError(error, '首页数据加载失败')
  }
}

function contextText(item: TransactionRecord) {
  const channel = item.channel === 'ONLINE' ? '线上' : '线下'
  const placeOrApp = item.channel === 'ONLINE' ? item.onlineApp : item.offlinePlace
  return [channel, placeOrApp, item.paymentMethodName].filter(Boolean).join(' · ')
}

onMounted(load)
</script>

<template>
  <main class="page workspace-page">
    <van-nav-bar title="工作台" />
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
          <div class="metric-value" :class="{ expense: budgetRisk?.overBudget }">{{ budgetRiskText }}</div>
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
          <span :class="['workspace-row-icon', item.type === 'EXPENSE' ? 'expense-mark' : 'income-mark']">
            <van-icon :name="item.type === 'EXPENSE' ? 'cart-o' : 'cash-back-record'" />
          </span>
          <span class="workspace-row-main">
            <span class="workspace-row-title">{{ item.itemName || item.categoryName }}</span>
            <span class="workspace-row-meta">{{ contextText(item) }} · {{ item.categoryName }} · {{ item.note || '无备注' }}</span>
          </span>
          <span :class="['workspace-row-amount', item.type === 'EXPENSE' ? 'expense' : 'income']">
            {{ item.type === 'EXPENSE' ? '-' : '+' }}¥{{ money(item.amount) }}
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
            <span class="workspace-row-meta">{{ item.itemName }} · {{ dueStatusText(item, todayDate()) }} · {{ runStatusLabel(item.status) }}</span>
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
  color: inherit;
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
  background: var(--card-bg-warm);
  font-size: var(--icon-size-md);
}

.expense-mark {
  background: var(--expense-soft);
  color: var(--expense);
}

.income-mark,
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

.workspace-row-amount {
  max-width: 96px;
  font-size: var(--font-size-body);
  font-weight: 750;
  line-height: var(--line-height-body);
  text-align: right;
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

  .workspace-row-amount {
    max-width: 82px;
  }
}
</style>
