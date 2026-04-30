<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { statisticsApi } from '@/api/services'
import type { CategorySummary, MonthlyStatistics } from '@/types'
import { currentMonth, money } from '@/utils/date'

const month = ref(currentMonth())
const stats = ref<MonthlyStatistics | null>(null)

async function load() {
  stats.value = await statisticsApi.monthly(month.value)
}

function percent(item: CategorySummary, total: number | undefined) {
  if (!total) return '0%'
  return `${Math.round((Number(item.amount) / Number(total)) * 100)}%`
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

      <section class="section panel">
        <van-cell title="支出分类" />
        <div v-if="!stats?.expenseByCategory.length" class="empty-text">暂无支出</div>
        <van-cell v-for="item in stats?.expenseByCategory" :key="item.categoryId" :title="item.categoryName">
          <template #label>
            <van-progress :percentage="Number(percent(item, stats?.totalExpense).replace('%', ''))" stroke-width="6" />
          </template>
          <template #value>
            ¥{{ money(item.amount) }} · {{ percent(item, stats?.totalExpense) }}
          </template>
        </van-cell>
      </section>

      <section class="section panel">
        <van-cell title="收入分类" />
        <div v-if="!stats?.incomeByCategory.length" class="empty-text">暂无收入</div>
        <van-cell v-for="item in stats?.incomeByCategory" :key="item.categoryId" :title="item.categoryName">
          <template #value>
            ¥{{ money(item.amount) }} · {{ percent(item, stats?.totalIncome) }}
          </template>
        </van-cell>
      </section>
    </div>
  </main>
</template>

