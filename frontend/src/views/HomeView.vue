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
      <section class="section">
        <van-button block round type="primary" icon="plus" to="/quick-add">快速记一笔</van-button>
      </section>

      <section class="section metric-grid">
        <div class="metric">
          <div class="metric-label">支出</div>
          <div class="metric-value expense">¥{{ money(stats?.totalExpense) }}</div>
        </div>
        <div class="metric">
          <div class="metric-label">收入</div>
          <div class="metric-value income">¥{{ money(stats?.totalIncome) }}</div>
        </div>
        <div class="metric">
          <div class="metric-label">结余</div>
          <div class="metric-value">¥{{ money(stats?.balance) }}</div>
        </div>
      </section>

      <section class="section panel">
        <van-cell title="最近记录" value="全部" is-link to="/records" />
        <div v-if="recent.length === 0" class="empty-text">暂无记录</div>
        <van-cell
          v-for="item in recent"
          :key="item.id"
          :title="item.itemName || item.categoryName"
          :label="`${contextText(item)} · ${item.categoryName} · ${item.note || '无备注'}`"
          :value="`${item.type === 'EXPENSE' ? '-' : '+'}¥${money(item.amount)}`"
          :value-class="item.type === 'EXPENSE' ? 'expense' : 'income'"
        />
      </section>

      <section class="section panel">
        <van-cell title="周期记账" value="管理" is-link to="/recurring-rules" />
        <div v-if="dueRuns.length === 0" class="empty-text">暂无待处理周期记录</div>
        <van-cell
          v-for="item in dueRuns.slice(0, 3)"
          :key="item.id"
          :title="item.ruleName"
          :label="`${item.itemName} · ${dueStatusText(item, todayDate())} · ${runStatusLabel(item.status)}`"
          :value="`${item.type === 'EXPENSE' ? '-' : '+'}¥${money(item.amount)}`"
          :value-class="item.type === 'EXPENSE' ? 'expense' : 'income'"
        />
        <van-cell v-if="dueRuns.length > 3" title="查看更多" is-link to="/recurring-rules" />
      </section>

      <section class="section panel">
        <div class="muted">你好，{{ auth.user?.nickname || '用户' }}</div>
        <ModernDateField v-model="month" mode="month" label="月份" title="选择月份" @change="load" />
      </section>
    </div>
  </main>
</template>
