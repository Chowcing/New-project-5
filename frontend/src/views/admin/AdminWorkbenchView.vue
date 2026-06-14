<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { adminApi } from '@/api/services'
import PageSkeleton from '@/components/PageSkeleton.vue'
import type { AdminWorkbench } from '@/types'
import { actionText, displayDateTime, formatMoney, transactionTitle, typeText } from './adminUtils'
import { showError } from '@/utils/errors'

const loading = ref(false)
const workbench = ref<AdminWorkbench | null>(null)

const metrics = computed(() => {
  const overview = workbench.value?.overview
  return [
    { label: '用户数', value: overview?.totalUsers ?? 0, icon: 'friends-o' },
    { label: '禁用用户', value: overview?.disabledUsers ?? 0, icon: 'warning-o' },
    { label: '30 天活跃', value: overview?.activeUsers30d ?? 0, icon: 'fire-o' },
    { label: '交易数', value: overview?.totalTransactions ?? 0, icon: 'orders-o' },
    { label: '总支出', value: formatMoney(overview?.totalExpense), icon: 'down', tone: 'expense' },
    { label: '总收入', value: formatMoney(overview?.totalIncome), icon: 'upgrade', tone: 'income' }
  ]
})

const latestDailyMetrics = computed(() => workbench.value?.dailyMetrics?.slice(-7) || [])

onMounted(loadWorkbench)

async function loadWorkbench() {
  loading.value = true
  try {
    workbench.value = await adminApi.workbench()
  } catch (error) {
    showError(error, '后台工作台加载失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="admin-workbench">
    <PageSkeleton v-if="loading && !workbench" variant="list" :cards="4" :rows="2" />
    <template v-else>
      <section class="admin-hero">
        <div>
          <span>管理工作台</span>
          <h1>待关注事项与系统概览</h1>
        </div>
        <van-button size="small" plain type="primary" icon="replay" :loading="loading" @click="loadWorkbench">刷新</van-button>
      </section>

      <section class="metric-grid">
        <article v-for="item in metrics" :key="item.label" class="metric-card">
          <van-icon :name="item.icon" />
          <span>{{ item.label }}</span>
          <strong :class="item.tone">{{ item.value }}</strong>
        </article>
      </section>

      <section class="attention-grid">
        <RouterLink
          v-for="item in workbench?.attentionItems"
          :key="item.key"
          class="attention-card"
          :class="item.severity"
          :to="item.route"
        >
          <div>
            <span>{{ item.title }}</span>
            <strong>{{ item.value }}</strong>
          </div>
          <p>{{ item.description }}</p>
        </RouterLink>
      </section>

      <section class="workbench-grid">
        <article class="admin-panel">
          <div class="panel-title">
            <h2>近 7 日趋势</h2>
          </div>
          <div class="daily-list">
            <div v-for="metric in latestDailyMetrics" :key="metric.date" class="daily-row">
              <span>{{ metric.date }}</span>
              <strong>{{ metric.transactionCount }} 笔</strong>
              <small>活跃 {{ metric.activeUsers }}</small>
              <small class="expense">{{ formatMoney(metric.totalExpense) }}</small>
              <small class="income">{{ formatMoney(metric.totalIncome) }}</small>
            </div>
            <van-empty v-if="!latestDailyMetrics.length" description="暂无趋势数据" />
          </div>
        </article>

        <article class="admin-panel">
          <div class="panel-title">
            <h2>近期大额交易</h2>
            <RouterLink to="/admin/transactions">查看</RouterLink>
          </div>
          <div class="compact-list">
            <RouterLink
              v-for="record in workbench?.recentRiskTransactions"
              :key="record.id"
              class="compact-row"
              :to="`/admin/transactions?id=${record.id}`"
            >
              <div>
                <strong>{{ transactionTitle(record) }}</strong>
                <span>{{ record.nickname || record.username }} · {{ typeText(record.type) }}</span>
              </div>
              <b :class="record.type === 'EXPENSE' ? 'expense' : 'income'">{{ formatMoney(record.amount) }}</b>
            </RouterLink>
            <van-empty v-if="workbench && !workbench.recentRiskTransactions.length" description="暂无大额交易" />
          </div>
        </article>

        <article class="admin-panel">
          <div class="panel-title">
            <h2>近期管理审计</h2>
            <RouterLink to="/admin/audit">查看</RouterLink>
          </div>
          <div class="compact-list">
            <RouterLink
              v-for="log in workbench?.recentAuditLogs"
              :key="log.id"
              class="compact-row"
              :to="`/admin/audit?targetType=${log.targetType}&targetId=${log.targetId}`"
            >
              <div>
                <strong>{{ actionText(log.action) }}</strong>
                <span>{{ displayDateTime(log.createdAt) }} · {{ log.adminUsername || log.adminUserId }}</span>
              </div>
              <small>#{{ log.targetId }}</small>
            </RouterLink>
            <van-empty v-if="workbench && !workbench.recentAuditLogs.length" description="暂无审计记录" />
          </div>
        </article>
      </section>
    </template>
  </div>
</template>

<style scoped>
.admin-workbench {
  display: grid;
  gap: var(--space-12);
  min-width: 0;
}

.admin-hero,
.metric-card,
.attention-card,
.admin-panel {
  border: 1px solid var(--border-warm);
  border-radius: var(--radius-card);
  background: var(--card-bg);
}

.admin-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-12);
  padding: var(--space-16);
}

.admin-hero > div {
  min-width: 0;
}

.admin-hero span,
.metric-card span,
.attention-card p,
.compact-row span,
.daily-row small {
  color: var(--text-secondary);
}

.admin-hero h1 {
  margin: var(--space-4) 0 0;
  font-size: var(--font-size-page-title);
  line-height: var(--line-height-page-title);
}

.metric-grid,
.attention-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-10);
}

.metric-card {
  min-height: 98px;
  padding: var(--space-12);
}

.metric-card .van-icon {
  color: var(--primary);
}

.metric-card span,
.metric-card strong {
  display: block;
}

.metric-card strong {
  margin-top: var(--space-8);
  font-size: var(--font-size-panel-title);
}

.attention-card {
  display: grid;
  gap: var(--space-8);
  padding: var(--space-12);
  color: var(--text-main);
  text-decoration: none;
}

.attention-card div {
  display: flex;
  justify-content: space-between;
  gap: var(--space-10);
  min-width: 0;
}

.attention-card p {
  margin: 0;
  font-size: var(--font-size-meta);
  overflow-wrap: anywhere;
}

.attention-card.danger {
  border-color: rgba(251, 113, 133, 0.42);
}

.attention-card.warning {
  border-color: rgba(251, 191, 36, 0.42);
}

.workbench-grid {
  display: grid;
  gap: var(--space-12);
  min-width: 0;
}

.admin-panel {
  min-width: 0;
  padding: var(--space-12);
}

.panel-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-10);
}

.panel-title h2 {
  margin: 0;
  font-size: var(--font-size-panel-title);
}

.panel-title a {
  color: var(--primary);
  text-decoration: none;
}

.daily-list,
.compact-list {
  display: grid;
  gap: var(--space-8);
  min-width: 0;
}

.daily-list {
  max-width: 100%;
  overflow-x: auto;
  overscroll-behavior-x: contain;
}

.daily-row,
.compact-row {
  display: grid;
  grid-template-columns: 1.2fr 0.8fr 0.8fr 1fr 1fr;
  gap: var(--space-8);
  align-items: center;
  min-width: 620px;
  padding: var(--space-10);
  border: 1px solid var(--border-warm);
  border-radius: var(--radius-inner);
}

.compact-row {
  grid-template-columns: minmax(0, 1fr) auto;
  min-width: 0;
  color: var(--text-main);
  text-decoration: none;
}

.compact-row div,
.compact-row strong,
.compact-row span {
  min-width: 0;
}

.compact-row span {
  display: block;
  margin-top: var(--space-3);
  overflow-wrap: anywhere;
}

.expense {
  color: var(--expense);
}

.income {
  color: var(--income);
}

@media (min-width: 760px) {
  .metric-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .attention-grid {
    grid-template-columns: repeat(5, minmax(0, 1fr));
  }

  .workbench-grid {
    grid-template-columns: 1.2fr 1fr;
  }

  .workbench-grid .admin-panel:first-child {
    grid-column: 1 / -1;
  }
}

@media (max-width: 480px) {
  .admin-hero {
    align-items: flex-start;
    flex-direction: column;
  }

  .admin-hero h1 {
    font-size: var(--font-size-panel-title);
    line-height: var(--line-height-panel-title);
    overflow-wrap: anywhere;
  }

  .metric-grid,
  .attention-grid {
    gap: var(--space-8);
  }

  .metric-card {
    min-height: 86px;
  }

  .daily-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    min-width: 0;
  }

  .daily-row span {
    grid-column: 1 / -1;
  }

  .daily-row small,
  .daily-row strong {
    min-width: 0;
    overflow-wrap: anywhere;
  }
}
</style>
