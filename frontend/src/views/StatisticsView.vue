<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { statisticsApi } from '@/api/services'
import type { CategorySummary, ChannelSummary, MonthlyStatistics } from '@/types'
import { currentMonth, money } from '@/utils/date'
import { showError } from '@/utils/errors'

const month = ref(currentMonth())
const stats = ref<MonthlyStatistics | null>(null)
const activeTab = ref(0)

const activeDailyTrend = computed(() => {
  return (stats.value?.dailyTrend || []).filter((item) => {
    return Number(item.totalExpense) > 0 || Number(item.totalIncome) > 0
  })
})

const maxDailyExpense = computed(() => {
  return Math.max(0, ...activeDailyTrend.value.map((item) => Number(item.totalExpense)))
})

async function load() {
  try {
    stats.value = await statisticsApi.monthly(month.value)
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

function barWidth(amount: number | string | undefined, max: number | string | undefined) {
  const numericAmount = Number(amount || 0)
  if (!numericAmount || !Number(max)) return '0%'
  return `${Math.max(4, Math.round((numericAmount / Number(max)) * 100))}%`
}

function dayLabel(date: string) {
  return date.slice(5)
}

function channelLabel(channel: ChannelSummary['channel']) {
  return channel === 'ONLINE' ? '线上支出' : '线下支出'
}

onMounted(load)
</script>

<template>
  <main class="page">
    <van-nav-bar title="月度统计" />
    <div class="page-content">
      <section class="section panel">
        <van-field v-model="month" type="month" label="月份" input-align="right" @change="load" />
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

      <section class="section metric-grid">
        <div class="metric metric-compact">
          <div class="metric-label">总笔数</div>
          <div class="metric-value">{{ stats?.transactionCount || 0 }}</div>
        </div>
        <div class="metric metric-compact">
          <div class="metric-label">支出笔数</div>
          <div class="metric-value expense">{{ stats?.expenseCount || 0 }}</div>
        </div>
        <div class="metric metric-compact">
          <div class="metric-label">收入笔数</div>
          <div class="metric-value income">{{ stats?.incomeCount || 0 }}</div>
        </div>
      </section>

      <section class="section panel">
        <van-tabs v-model:active="activeTab" shrink>
          <van-tab title="分类">
            <div class="tab-pane">
              <van-cell title="支出分类" />
              <div v-if="!stats?.expenseByCategory.length" class="empty-text">暂无支出</div>
              <van-cell v-for="item in stats?.expenseByCategory" :key="item.categoryId" :title="item.categoryName">
                <template #label>
                  <div class="summary-label">{{ item.transactionCount }} 笔</div>
                  <van-progress :percentage="Number(percent(item, stats?.totalExpense).replace('%', ''))" stroke-width="6" />
                </template>
                <template #value>
                  ¥{{ money(item.amount) }} · {{ percent(item, stats?.totalExpense) }}
                </template>
              </van-cell>

              <van-cell title="收入分类" />
              <div v-if="!stats?.incomeByCategory.length" class="empty-text">暂无收入</div>
              <van-cell v-for="item in stats?.incomeByCategory" :key="item.categoryId" :title="item.categoryName">
                <template #label>
                  <div class="summary-label">{{ item.transactionCount }} 笔</div>
                  <van-progress :percentage="Number(percent(item, stats?.totalIncome).replace('%', ''))" stroke-width="6" color="#2f9b63" />
                </template>
                <template #value>
                  ¥{{ money(item.amount) }} · {{ percent(item, stats?.totalIncome) }}
                </template>
              </van-cell>
            </div>
          </van-tab>

          <van-tab title="趋势">
            <div class="tab-pane">
              <div v-if="!activeDailyTrend.length" class="empty-text">暂无趋势数据</div>
              <div v-for="item in activeDailyTrend" :key="item.date" class="trend-row">
                <div class="trend-date">{{ dayLabel(item.date) }}</div>
                <div class="trend-main">
                  <div class="trend-bar-track">
                    <div class="trend-bar" :style="{ width: barWidth(item.totalExpense, maxDailyExpense) }" />
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
              <div v-if="!stats?.expenseByChannel.length" class="empty-text">暂无渠道支出</div>
              <van-cell
                v-for="item in stats?.expenseByChannel"
                :key="item.channel"
                :title="channelLabel(item.channel)"
              >
                <template #label>
                  <div class="summary-label">{{ item.transactionCount }} 笔</div>
                  <van-progress :percentage="progress(item.amount, stats?.totalExpense)" stroke-width="6" />
                </template>
                <template #value>
                  ¥{{ money(item.amount) }} · {{ amountPercent(item.amount, stats?.totalExpense) }}
                </template>
              </van-cell>
            </div>
          </van-tab>

          <van-tab title="支付">
            <div class="tab-pane">
              <div v-if="!stats?.expenseByPaymentMethod.length" class="empty-text">暂无支付方式支出</div>
              <van-cell
                v-for="item in stats?.expenseByPaymentMethod"
                :key="item.paymentMethodId"
                :title="item.paymentMethodName"
              >
                <template #label>
                  <div class="summary-label">{{ item.transactionCount }} 笔</div>
                  <van-progress :percentage="progress(item.amount, stats?.totalExpense)" stroke-width="6" />
                </template>
                <template #value>
                  ¥{{ money(item.amount) }} · {{ amountPercent(item.amount, stats?.totalExpense) }}
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

.tab-pane {
  padding-top: 8px;
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
