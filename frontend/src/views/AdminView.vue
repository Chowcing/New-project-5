<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { adminApi, type AdminTransactionQuery, type AdminUserQuery, type BusinessAuditLogQuery } from '@/api/services'
import ModernDateField from '@/components/ModernDateField.vue'
import PageSkeleton from '@/components/PageSkeleton.vue'
import type { AdminAuditLog, AdminOverview, AdminTransaction, AdminUser, BusinessAuditLog, PageResponse } from '@/types'
import { transactionTitle } from '@/utils/display'
import { showError } from '@/utils/errors'
import { navigateBackOrHome } from '@/utils/navigationBack'

const router = useRouter()
const activeTab = ref('overview')
const overviewLoading = ref(false)
const usersLoading = ref(false)
const transactionsLoading = ref(false)
const auditLogsLoading = ref(false)
const businessAuditLogsLoading = ref(false)
const auditMode = ref<'admin' | 'business'>('admin')
const overview = ref<AdminOverview | null>(null)
const usersPage = ref<PageResponse<AdminUser> | null>(null)
const transactionsPage = ref<PageResponse<AdminTransaction> | null>(null)
const auditLogsPage = ref<PageResponse<AdminAuditLog> | null>(null)
const businessAuditLogsPage = ref<PageResponse<BusinessAuditLog> | null>(null)

const userQuery = reactive<AdminUserQuery>({
  keyword: '',
  status: '',
  page: 1,
  size: 20
})

const transactionQuery = reactive<AdminTransactionQuery>({
  userId: undefined,
  type: '',
  channel: '',
  keyword: '',
  startDate: '',
  endDate: '',
  page: 1,
  size: 20
})

const auditQuery = reactive({
  page: 1,
  size: 20
})

const businessAuditQuery = reactive<BusinessAuditLogQuery>({
  userId: undefined,
  action: '',
  targetType: '',
  source: '',
  page: 1,
  size: 20
})

const latestDailyMetrics = computed(() => overview.value?.dailyMetrics.slice(-7) || [])

onMounted(async () => {
  await Promise.all([loadOverview(), loadUsers(), loadTransactions(), loadAuditLogs(), loadBusinessAuditLogs()])
})

async function loadOverview() {
  overviewLoading.value = true
  try {
    overview.value = await adminApi.overview()
  } catch (error) {
    showError(error, '后台概览加载失败')
  } finally {
    overviewLoading.value = false
  }
}

async function loadUsers(resetPage = false) {
  if (resetPage) {
    userQuery.page = 1
  }
  usersLoading.value = true
  try {
    usersPage.value = await adminApi.users(cleanParams(userQuery))
  } catch (error) {
    showError(error, '用户列表加载失败')
  } finally {
    usersLoading.value = false
  }
}

async function loadTransactions(resetPage = false) {
  if (resetPage) {
    transactionQuery.page = 1
  }
  transactionsLoading.value = true
  try {
    transactionsPage.value = await adminApi.transactions(cleanParams(transactionQuery))
  } catch (error) {
    showError(error, '交易列表加载失败')
  } finally {
    transactionsLoading.value = false
  }
}

async function loadAuditLogs(resetPage = false) {
  if (resetPage) {
    auditQuery.page = 1
  }
  auditLogsLoading.value = true
  try {
    auditLogsPage.value = await adminApi.auditLogs(auditQuery)
  } catch (error) {
    showError(error, '审计日志加载失败')
  } finally {
    auditLogsLoading.value = false
  }
}

async function loadBusinessAuditLogs(resetPage = false) {
  if (resetPage) {
    businessAuditQuery.page = 1
  }
  businessAuditLogsLoading.value = true
  try {
    businessAuditLogsPage.value = await adminApi.businessAuditLogs(cleanParams(businessAuditQuery))
  } catch (error) {
    showError(error, '业务审计日志加载失败')
  } finally {
    businessAuditLogsLoading.value = false
  }
}

async function toggleUserStatus(user: AdminUser) {
  const nextStatus = user.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  const actionText = nextStatus === 'DISABLED' ? '禁用' : '启用'
  const reason = window.prompt(`请输入${actionText}原因`)
  if (!reason?.trim()) {
    return
  }
  try {
    await showConfirmDialog({ title: `${actionText}用户`, message: `确认${actionText} ${user.username}？` })
  } catch {
    return
  }
  try {
    await adminApi.updateUserStatus(user.id, { status: nextStatus, reason: reason.trim() })
    showToast(`用户已${actionText}`)
    await Promise.all([loadUsers(), loadOverview(), loadAuditLogs()])
  } catch (error) {
    showError(error, `${actionText}用户失败`)
  }
}

async function revokeTokens(user: AdminUser) {
  const reason = window.prompt('请输入吊销原因')
  if (!reason?.trim()) {
    return
  }
  try {
    await showConfirmDialog({ title: '吊销登录凭证', message: `确认让 ${user.username} 重新登录？` })
  } catch {
    return
  }
  try {
    await adminApi.revokeUserTokens(user.id, reason.trim())
    showToast('登录凭证已吊销')
    await loadAuditLogs()
  } catch (error) {
    showError(error, '吊销失败')
  }
}

async function resetEmail(user: AdminUser) {
  const reason = window.prompt('请输入重置邮箱原因')
  if (!reason?.trim()) {
    return
  }
  try {
    await showConfirmDialog({ title: '重置邮箱', message: `确认让 ${user.username} 下次登录重新绑定邮箱？` })
  } catch {
    return
  }
  try {
    await adminApi.resetUserEmail(user.id, reason.trim())
    showToast('邮箱状态已重置')
    await Promise.all([loadUsers(), loadAuditLogs()])
  } catch (error) {
    showError(error, '重置邮箱失败')
  }
}

async function deleteTransaction(record: AdminTransaction) {
  const reason = window.prompt('请输入删除原因')
  if (!reason?.trim()) {
    return
  }
  try {
    await showConfirmDialog({ title: '删除交易', message: `确认删除「${transactionTitle(record)}」？` })
  } catch {
    return
  }
  try {
    await adminApi.deleteTransaction(record.id, reason.trim())
    showToast('记录已删除')
    await Promise.all([loadTransactions(), loadOverview(), loadAuditLogs()])
  } catch (error) {
    showError(error, '删除失败')
  }
}

function cleanParams<T extends object>(params: T) {
  return Object.fromEntries(
    Object.entries(params).filter(([, value]) => value !== '' && value !== undefined && value !== null)
  ) as T
}

function formatMoney(value: number | undefined) {
  return `¥${Number(value || 0).toFixed(2)}`
}

function statusText(status: string) {
  return status === 'DISABLED' ? '已禁用' : '正常'
}

function typeText(type: string) {
  return type === 'INCOME' ? '收入' : '支出'
}

function channelText(channel: string) {
  return channel === 'OFFLINE' ? '线下' : '线上'
}

function actionText(action: string) {
  const map: Record<string, string> = {
    USER_STATUS_ACTIVE: '启用用户',
    USER_STATUS_DISABLED: '禁用用户',
    REVOKE_TOKENS: '吊销凭证',
    RESET_USER_EMAIL: '重置邮箱',
    TRANSACTION_DELETE: '删除交易',
    TRANSACTION_CREATE: '新增交易',
    TRANSACTION_UPDATE: '更新交易',
    TRANSACTION_IMAGE_DELETE: '删除凭证',
    CATEGORY_CREATE: '新增分类',
    CATEGORY_UPDATE: '更新分类',
    CATEGORY_DELETE: '删除分类',
    PAYMENT_METHOD_CREATE: '新增支付方式',
    PAYMENT_METHOD_UPDATE: '更新支付方式',
    PAYMENT_METHOD_DELETE: '删除支付方式',
    ONLINE_PLATFORM_CREATE: '新增线上平台',
    ONLINE_PLATFORM_UPDATE: '更新线上平台',
    ONLINE_PLATFORM_DELETE: '删除线上平台',
    BUDGET_CREATE: '新增预算',
    BUDGET_UPDATE: '更新预算',
    BUDGET_DELETE: '删除预算',
    RECURRING_RULE_CREATE: '新增周期规则',
    RECURRING_RULE_UPDATE: '更新周期规则',
    RECURRING_RULE_DELETE: '删除周期规则',
    RECURRING_RUN_GENERATE: '生成周期流水',
    IMPORT_JOB_CREATE: '创建导入任务',
    IMPORT_JOB_SUCCESS: '导入完成',
    OCR_IMAGE_RECOGNIZE: '图片识别'
  }
  return map[action] || action
}

function sourceText(source: string) {
  const map: Record<string, string> = {
    USER: '用户',
    IMPORT: '导入',
    OCR: 'OCR',
    RECURRING: '周期'
  }
  return map[source] || source
}
</script>

<template>
  <main class="admin-page">
    <van-nav-bar title="后台管理" left-arrow @click-left="navigateBackOrHome(router)" />

    <van-tabs v-model:active="activeTab" sticky>
      <van-tab title="概览" name="overview">
        <div class="admin-content">
          <section class="admin-metrics">
            <div class="admin-metric">
              <span>用户数</span>
              <strong>{{ overview?.totalUsers || 0 }}</strong>
            </div>
            <div class="admin-metric">
              <span>禁用用户</span>
              <strong>{{ overview?.disabledUsers || 0 }}</strong>
            </div>
            <div class="admin-metric">
              <span>30 天活跃</span>
              <strong>{{ overview?.activeUsers30d || 0 }}</strong>
            </div>
            <div class="admin-metric">
              <span>交易数</span>
              <strong>{{ overview?.totalTransactions || 0 }}</strong>
            </div>
            <div class="admin-metric">
              <span>总支出</span>
              <strong class="expense">{{ formatMoney(overview?.totalExpense) }}</strong>
            </div>
            <div class="admin-metric">
              <span>总收入</span>
              <strong class="income">{{ formatMoney(overview?.totalIncome) }}</strong>
            </div>
          </section>

          <section class="admin-panel">
            <h2>近 7 日</h2>
            <div class="admin-table">
              <div class="admin-row admin-head">
                <span>日期</span>
                <span>交易</span>
                <span>活跃</span>
                <span>支出</span>
                <span>收入</span>
              </div>
              <div v-for="metric in latestDailyMetrics" :key="metric.date" class="admin-row">
                <span>{{ metric.date }}</span>
                <span>{{ metric.transactionCount }}</span>
                <span>{{ metric.activeUsers }}</span>
                <span class="expense">{{ formatMoney(metric.totalExpense) }}</span>
                <span class="income">{{ formatMoney(metric.totalIncome) }}</span>
              </div>
              <PageSkeleton v-if="overviewLoading" variant="list" :cards="2" :rows="2" />
              <van-empty v-else-if="!latestDailyMetrics.length" description="暂无数据" />
            </div>
          </section>
        </div>
      </van-tab>

      <van-tab title="用户" name="users">
        <div class="admin-content">
          <section class="admin-panel">
            <div class="admin-filters">
              <van-field v-model="userQuery.keyword" clearable placeholder="用户名或昵称" />
              <van-field v-model="userQuery.status" readonly is-link placeholder="状态" @click="userQuery.status = userQuery.status === 'ACTIVE' ? 'DISABLED' : userQuery.status === 'DISABLED' ? '' : 'ACTIVE'" />
              <van-button type="primary" icon="search" @click="loadUsers(true)">搜索</van-button>
            </div>
            <div class="admin-list">
              <PageSkeleton v-if="usersLoading && !usersPage" variant="list" :cards="3" :rows="2" />
              <template v-else>
                <article v-for="user in usersPage?.records" :key="user.id" class="admin-card">
                  <div>
                    <h3>{{ user.nickname }} <small>@{{ user.username }}</small></h3>
                    <p>ID {{ user.id }} · {{ statusText(user.status) }} · {{ user.emailVerifiedAt ? '邮箱已验证' : '待绑定邮箱' }} · {{ user.transactionCount }} 笔</p>
                  </div>
                  <div class="admin-actions">
                    <van-tag v-if="user.admin" type="primary">管理员</van-tag>
                    <van-button size="small" plain type="primary" icon="delete-o" @click="revokeTokens(user)">吊销</van-button>
                    <van-button size="small" plain type="warning" icon="envelop-o" @click="resetEmail(user)">邮箱</van-button>
                    <van-button size="small" plain :type="user.status === 'ACTIVE' ? 'danger' : 'success'" @click="toggleUserStatus(user)">
                      <template #icon>
                        <van-icon :name="user.status === 'ACTIVE' ? 'close' : 'passed'" />
                      </template>
                      {{ user.status === 'ACTIVE' ? '禁用' : '启用' }}
                    </van-button>
                  </div>
                </article>
              </template>
              <van-empty v-if="usersPage && !usersPage.records.length" description="暂无用户" />
            </div>
            <van-pagination v-if="usersPage && usersPage.totalPages > 1" v-model="userQuery.page" :total-items="usersPage.total" :items-per-page="userQuery.size" @change="loadUsers()" />
          </section>
        </div>
      </van-tab>

      <van-tab title="交易" name="transactions">
        <div class="admin-content">
          <section class="admin-panel">
            <div class="admin-filters transaction-filters">
              <van-field v-model.number="transactionQuery.userId" clearable type="number" placeholder="用户 ID" />
              <van-field v-model="transactionQuery.keyword" clearable placeholder="关键词" />
              <van-field v-model="transactionQuery.type" readonly is-link placeholder="类型" @click="transactionQuery.type = transactionQuery.type === 'EXPENSE' ? 'INCOME' : transactionQuery.type === 'INCOME' ? '' : 'EXPENSE'" />
              <van-field v-model="transactionQuery.channel" readonly is-link placeholder="渠道" @click="transactionQuery.channel = transactionQuery.channel === 'ONLINE' ? 'OFFLINE' : transactionQuery.channel === 'OFFLINE' ? '' : 'ONLINE'" />
              <ModernDateField v-model="transactionQuery.startDate" mode="date" label="" title="选择开始日期" placeholder="开始日期" />
              <ModernDateField v-model="transactionQuery.endDate" mode="date" label="" title="选择结束日期" placeholder="结束日期" />
              <van-button type="primary" icon="search" @click="loadTransactions(true)">搜索</van-button>
            </div>
            <div class="admin-list">
              <PageSkeleton v-if="transactionsLoading && !transactionsPage" variant="list" :cards="3" :rows="2" />
              <template v-else>
                <article v-for="record in transactionsPage?.records" :key="record.id" class="admin-card transaction-card">
                  <div>
                    <h3>{{ transactionTitle(record) }} <small>{{ record.nickname || record.username }}</small></h3>
                    <p>{{ record.occurredAt }} · {{ typeText(record.type) }} · {{ channelText(record.channel) }} · {{ record.categoryName }} · {{ record.paymentMethodName }}</p>
                  </div>
                  <div class="admin-actions">
                    <strong :class="record.type === 'EXPENSE' ? 'expense' : 'income'">{{ formatMoney(record.amount) }}</strong>
                    <van-button size="small" plain type="danger" icon="delete-o" @click="deleteTransaction(record)">删除</van-button>
                  </div>
                </article>
              </template>
              <van-empty v-if="transactionsPage && !transactionsPage.records.length" description="暂无交易" />
            </div>
            <van-pagination v-if="transactionsPage && transactionsPage.totalPages > 1" v-model="transactionQuery.page" :total-items="transactionsPage.total" :items-per-page="transactionQuery.size" @change="loadTransactions()" />
          </section>
        </div>
      </van-tab>

      <van-tab title="审计" name="audit">
        <div class="admin-content">
          <section class="admin-panel">
            <van-tabs v-model:active="auditMode" type="card" class="audit-mode-tabs">
              <van-tab title="管理操作" name="admin" />
              <van-tab title="业务操作" name="business" />
            </van-tabs>

            <template v-if="auditMode === 'admin'">
              <div class="admin-list">
                <PageSkeleton v-if="auditLogsLoading && !auditLogsPage" variant="list" :cards="3" :rows="2" />
                <template v-else>
                  <article v-for="log in auditLogsPage?.records" :key="log.id" class="admin-card">
                    <div>
                      <h3>{{ actionText(log.action) }} <small>#{{ log.targetId }}</small></h3>
                      <p>{{ log.createdAt }} · {{ log.adminUsername || log.adminUserId }} · {{ log.targetType }}</p>
                      <p v-if="log.reason">{{ log.reason }}</p>
                    </div>
                  </article>
                </template>
                <van-empty v-if="auditLogsPage && !auditLogsPage.records.length" description="暂无审计日志" />
              </div>
              <van-pagination v-if="auditLogsPage && auditLogsPage.totalPages > 1" v-model="auditQuery.page" :total-items="auditLogsPage.total" :items-per-page="auditQuery.size" @change="loadAuditLogs()" />
            </template>

            <template v-else>
              <div class="admin-filters business-audit-filters">
                <van-field v-model.number="businessAuditQuery.userId" clearable type="number" placeholder="用户 ID" />
                <van-field v-model="businessAuditQuery.action" clearable placeholder="动作编码" />
                <van-field v-model="businessAuditQuery.targetType" clearable placeholder="目标类型" />
                <van-field v-model="businessAuditQuery.source" clearable placeholder="来源" />
                <van-button type="primary" icon="search" @click="loadBusinessAuditLogs(true)">搜索</van-button>
              </div>
              <div class="admin-list">
                <PageSkeleton v-if="businessAuditLogsLoading && !businessAuditLogsPage" variant="list" :cards="3" :rows="2" />
                <template v-else>
                  <article v-for="log in businessAuditLogsPage?.records" :key="log.id" class="admin-card audit-card">
                    <div>
                      <h3>{{ actionText(log.action) }} <small>{{ log.targetType }}{{ log.targetId ? ` #${log.targetId}` : '' }}</small></h3>
                      <p>{{ log.createdAt }} · 用户 #{{ log.userId }} · {{ sourceText(log.source) }} · {{ log.status }}</p>
                      <p v-if="log.requestId">requestId {{ log.requestId }}</p>
                    </div>
                  </article>
                </template>
                <van-empty v-if="businessAuditLogsPage && !businessAuditLogsPage.records.length" description="暂无业务审计日志" />
              </div>
              <van-pagination v-if="businessAuditLogsPage && businessAuditLogsPage.totalPages > 1" v-model="businessAuditQuery.page" :total-items="businessAuditLogsPage.total" :items-per-page="businessAuditQuery.size" @change="loadBusinessAuditLogs()" />
            </template>
          </section>
        </div>
      </van-tab>
    </van-tabs>
  </main>
</template>

<style scoped>
.admin-page {
  min-height: 100vh;
  background: var(--page-bg);
}

.admin-content {
  width: min(1180px, 100%);
  margin: var(--space-0) auto;
  padding: var(--space-12);
}

.admin-metrics {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: var(--space-10);
  margin-bottom: var(--space-12);
}

.admin-metric,
.admin-panel,
.admin-card {
  border: 1px solid var(--border-warm);
  border-radius: var(--radius-card);
  background: var(--card-bg);
}

.admin-metric {
  min-height: 84px;
  padding: var(--space-12);
}

.admin-metric span,
.admin-card p,
.admin-card small {
  color: var(--text-secondary);
}

.admin-metric strong {
  display: block;
  margin-top: var(--space-10);
  font-size: var(--icon-size-lg);
}

.admin-panel {
  padding: var(--space-12);
}

.admin-panel h2 {
  margin: var(--space-0) var(--space-0) var(--space-10);
  font-size: var(--font-size-section-title);
}

.admin-table {
  overflow-x: auto;
}

.admin-row {
  display: grid;
  grid-template-columns: 1.2fr repeat(4, minmax(86px, 1fr));
  min-width: 620px;
  padding: var(--space-10) var(--space-0);
  border-bottom: 1px solid var(--border-warm);
  font-size: var(--font-size-body);
}

.admin-head {
  color: var(--text-secondary);
  font-weight: 600;
}

.admin-filters {
  display: grid;
  grid-template-columns: minmax(180px, 1fr) minmax(120px, 160px) 44px;
  gap: var(--space-8);
  margin-bottom: var(--space-12);
}

.transaction-filters {
  grid-template-columns: 120px minmax(160px, 1fr) 120px 120px 150px 150px 44px;
}

.business-audit-filters {
  grid-template-columns: 120px minmax(160px, 1fr) 150px 120px 44px;
}

.audit-mode-tabs {
  margin-bottom: var(--space-12);
}

.admin-list {
  display: grid;
  gap: var(--space-8);
}

.admin-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-12);
  padding: var(--space-12);
}

.admin-card h3 {
  margin: var(--space-0) var(--space-0) var(--space-6);
  font-size: var(--font-size-body-strong);
}

.admin-card p {
  margin: var(--space-0);
  font-size: var(--font-size-meta);
}

.admin-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  align-items: center;
  gap: var(--space-8);
}

.transaction-card {
  align-items: flex-start;
}

.expense {
  color: var(--expense);
}

.income {
  color: var(--income);
}

@media (max-width: 760px) {
  .admin-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .admin-filters,
  .transaction-filters,
  .business-audit-filters {
    grid-template-columns: 1fr;
  }

  .admin-card {
    align-items: flex-start;
    flex-direction: column;
  }

  .admin-actions {
    justify-content: flex-start;
  }
}
</style>
