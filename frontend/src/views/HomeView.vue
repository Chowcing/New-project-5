<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { recurringRunApi, statisticsApi, transactionApi } from '@/api/services'
import ModernDateField from '@/components/ModernDateField.vue'
import { useAuthStore } from '@/stores/auth'
import type { MonthlyStatistics, RecurringRuleRun, TransactionRecord } from '@/types'
import { currentMonth, money, todayDate } from '@/utils/date'
import { showError } from '@/utils/errors'
import { dueStatusText, runStatusLabel } from '@/utils/recurring'

const auth = useAuthStore()
const month = ref(currentMonth())
const stats = ref<MonthlyStatistics | null>(null)
const recent = ref<TransactionRecord[]>([])
const dueRuns = ref<RecurringRuleRun[]>([])

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
  <main class="page">
    <van-nav-bar title="日常生活消费记录" />
    <div class="page-content">
      <section class="section panel home-hero-panel">
        <div class="home-hero-main">
          <div>
            <div class="home-greeting">你好，{{ auth.user?.nickname || '用户' }}</div>
            <div class="home-hero-copy">查看本月收支概览</div>
          </div>
          <div class="home-hero-icon">
            <van-icon name="calendar-o" />
          </div>
        </div>
        <div class="home-month-picker">
          <ModernDateField v-model="month" mode="month" label="月份" title="选择月份" @change="load">
            <template #trigger="{ displayValue, open }">
              <button class="home-month-trigger" type="button" @click="open">
                <span class="home-month-trigger-copy">
                  <span class="home-month-label">当前月份</span>
                  <strong>{{ displayValue }}</strong>
                </span>
                <span class="home-month-trigger-action">
                  <van-icon name="exchange" />
                  <span>切换</span>
                </span>
              </button>
            </template>
          </ModernDateField>
        </div>
      </section>

      <section class="section metric-grid">
        <div class="metric">
          <div class="metric-label"><van-icon name="cart-o" />支出</div>
          <div class="metric-value expense">¥{{ money(stats?.totalExpense) }}</div>
        </div>
        <div class="metric">
          <div class="metric-label"><van-icon name="cash-back-record" />收入</div>
          <div class="metric-value income">¥{{ money(stats?.totalIncome) }}</div>
        </div>
        <div class="metric">
          <div class="metric-label"><van-icon name="balance-o" />结余</div>
          <div class="metric-value">¥{{ money(stats?.balance) }}</div>
        </div>
      </section>

      <section class="section panel home-list-panel">
        <div class="section-heading home-panel-heading">
          <span class="home-panel-title"><van-icon name="orders-o" />最近记录</span>
          <van-button size="small" plain type="primary" icon="arrow" to="/records">全部</van-button>
        </div>
        <div v-if="recent.length === 0" class="empty-text">暂无记录</div>
        <RouterLink
          v-for="item in recent"
          :key="item.id"
          class="home-list-row"
          :to="`/records/${item.id}`"
        >
          <span :class="['home-row-icon', item.type === 'EXPENSE' ? 'expense-mark' : 'income-mark']">
            <van-icon :name="item.type === 'EXPENSE' ? 'cart-o' : 'cash-back-record'" />
          </span>
          <span class="home-row-main">
            <span class="home-row-title">{{ item.itemName || item.categoryName }}</span>
            <span class="home-row-meta">{{ contextText(item) }} · {{ item.categoryName }} · {{ item.note || '无备注' }}</span>
          </span>
          <span :class="['home-row-amount', item.type === 'EXPENSE' ? 'expense' : 'income']">
            {{ item.type === 'EXPENSE' ? '-' : '+' }}¥{{ money(item.amount) }}
          </span>
        </RouterLink>
      </section>

      <section class="section panel home-list-panel">
        <div class="section-heading home-panel-heading">
          <span class="home-panel-title"><van-icon name="replay" />周期记账</span>
          <van-button size="small" plain type="primary" icon="setting-o" to="/recurring-rules">管理</van-button>
        </div>
        <div v-if="dueRuns.length === 0" class="empty-text">暂无待处理周期记录</div>
        <RouterLink
          v-for="item in dueRuns.slice(0, 3)"
          :key="item.id"
          class="home-list-row"
          to="/recurring-rules"
        >
          <span class="home-row-icon recurring-mark">
            <van-icon name="clock-o" />
          </span>
          <span class="home-row-main">
            <span class="home-row-title">{{ item.ruleName }}</span>
            <span class="home-row-meta">{{ item.itemName }} · {{ dueStatusText(item, todayDate()) }} · {{ runStatusLabel(item.status) }}</span>
          </span>
          <span :class="['home-row-amount', item.type === 'EXPENSE' ? 'expense' : 'income']">
            {{ item.type === 'EXPENSE' ? '-' : '+' }}¥{{ money(item.amount) }}
          </span>
        </RouterLink>
        <RouterLink v-if="dueRuns.length > 3" class="home-more-row" to="/recurring-rules">
          <van-icon name="arrow" />
          <span>查看更多</span>
        </RouterLink>
      </section>
    </div>
  </main>
</template>

<style scoped>
.home-hero-panel {
  display: grid;
  gap: var(--space-12);
  padding: var(--space-14);
  background:
    linear-gradient(135deg, var(--card-bg) 0%, var(--page-bg-soft) 48%, var(--primary-soft) 100%);
}

.home-hero-main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-12);
}

.home-greeting {
  color: var(--text-main);
  font-size: var(--font-size-panel-title);
  font-weight: 700;
  line-height: var(--line-height-panel-title);
}

.home-hero-copy {
  margin-top: var(--space-4);
  color: var(--text-secondary);
  font-size: var(--font-size-meta);
  line-height: var(--line-height-meta);
}

.home-hero-icon {
  display: grid;
  width: var(--space-42);
  height: var(--space-42);
  flex: 0 0 auto;
  place-items: center;
  border-radius: var(--radius-card);
  background: var(--card-bg);
  color: var(--primary);
  font-size: var(--icon-size-lg);
  box-shadow: var(--shadow-sm);
}

.home-month-picker {
  min-width: 0;
}

.home-month-trigger {
  display: flex;
  width: 100%;
  min-height: var(--space-64);
  align-items: center;
  justify-content: space-between;
  gap: var(--space-12);
  border: 1px solid rgba(var(--theme-border-warm-rgb), 0.88);
  border-radius: var(--radius-card);
  padding: var(--space-10) var(--space-12);
  background: rgba(255, 253, 249, 0.76);
  color: inherit;
  font: inherit;
  text-align: left;
}

.home-month-trigger-copy {
  display: grid;
  min-width: 0;
  gap: var(--space-2);
}

.home-month-label {
  color: var(--text-secondary);
  font-size: var(--font-size-caption);
  line-height: var(--line-height-caption);
}

.home-month-trigger strong {
  overflow: hidden;
  color: var(--text-main);
  font-size: var(--font-size-panel-title);
  font-weight: 700;
  line-height: var(--line-height-panel-title);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.home-month-trigger-action {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: var(--space-4);
  border-radius: var(--radius-pill);
  padding: var(--space-4) var(--space-10);
  background: var(--primary-soft);
  color: var(--primary);
  font-size: var(--font-size-meta);
  font-weight: 600;
}

.home-list-panel {
  padding: var(--space-0);
  overflow: hidden;
}

.home-panel-heading {
  margin-bottom: var(--space-0);
  padding: var(--space-14) var(--space-14) var(--space-10);
  border-bottom: 1px solid var(--border-warm);
}

.home-panel-title {
  display: inline-flex;
  align-items: center;
  gap: var(--space-6);
  min-width: 0;
}

.home-panel-title :deep(.van-icon) {
  color: var(--primary);
  font-size: var(--icon-size-md);
}

.home-list-row {
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr) auto;
  gap: var(--space-10);
  align-items: center;
  min-height: var(--space-64);
  padding: var(--space-10) var(--space-12);
  color: inherit;
}

.home-list-row:not(:last-child) {
  border-bottom: 1px solid rgba(var(--theme-border-warm-rgb), 0.72);
}

.home-row-icon {
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

.home-row-main {
  display: grid;
  min-width: 0;
  gap: var(--space-4);
}

.home-row-title {
  overflow: hidden;
  color: var(--text-main);
  font-size: var(--font-size-body-strong);
  font-weight: 600;
  line-height: var(--line-height-body-strong);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.home-row-meta {
  overflow: hidden;
  color: var(--text-secondary);
  font-size: var(--font-size-caption);
  line-height: var(--line-height-caption);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.home-row-amount {
  max-width: 96px;
  font-size: var(--font-size-body);
  font-weight: 700;
  line-height: var(--line-height-body);
  text-align: right;
  white-space: nowrap;
}

.home-more-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-6);
  min-height: var(--space-42);
  border-top: 1px solid rgba(var(--theme-border-warm-rgb), 0.72);
  color: var(--primary);
  font-size: var(--font-size-meta);
  font-weight: 600;
}

@media (max-width: 360px) {
  .home-list-row {
    grid-template-columns: 34px minmax(0, 1fr) auto;
    gap: var(--space-8);
    padding: var(--space-10);
  }

  .home-row-icon {
    width: 34px;
    height: 34px;
  }

  .home-row-amount {
    max-width: 82px;
  }
}
</style>
