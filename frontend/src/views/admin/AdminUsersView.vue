<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { adminApi, type AdminAuditLogQuery, type AdminUserQuery } from '@/api/services'
import BottomSheet from '@/components/BottomSheet.vue'
import PageSkeleton from '@/components/PageSkeleton.vue'
import type { AdminAuditLog, AdminTransaction, AdminUser, AdminUserDetail, PageResponse } from '@/types'
import { showError } from '@/utils/errors'
import AdminReasonDialog from './AdminReasonDialog.vue'
import {
  actionText,
  cleanParams,
  displayDateTime,
  formatMoney,
  statusText,
  transactionTitle,
  typeText
} from './adminUtils'

type UserAction = 'status' | 'revoke' | 'email'

const route = useRoute()
const usersLoading = ref(false)
const detailLoading = ref(false)
const actionLoading = ref(false)
const usersPage = ref<PageResponse<AdminUser> | null>(null)
const selectedUser = ref<AdminUserDetail | null>(null)
const recentTransactions = ref<AdminTransaction[]>([])
const recentAuditLogs = ref<AdminAuditLog[]>([])
const detailVisible = ref(false)
const reasonVisible = ref(false)
const pendingAction = ref<UserAction | null>(null)
const pendingUser = ref<AdminUser | null>(null)
const isMobile = ref(false)
const statusPickerVisible = ref(false)

const statusOptions = [
  { label: '全部状态', value: '', description: '查看所有用户' },
  { label: '正常', value: 'ACTIVE', description: '当前可登录和使用的账号' },
  { label: '已禁用', value: 'DISABLED', description: '已被管理员禁用的账号' }
]

const userQuery = reactive<AdminUserQuery>({
  keyword: '',
  status: '',
  page: 1,
  size: 20
})

const drawerPosition = computed(() => isMobile.value ? 'bottom' : 'right')
const reasonTitle = computed(() => {
  if (pendingAction.value === 'status') {
    return pendingUser.value?.status === 'ACTIVE' ? '禁用用户' : '启用用户'
  }
  if (pendingAction.value === 'revoke') return '吊销登录凭证'
  if (pendingAction.value === 'email') return '重置邮箱'
  return '管理操作'
})
const reasonTarget = computed(() => pendingUser.value ? `${pendingUser.value.nickname || pendingUser.value.username} @${pendingUser.value.username}` : '-')

onMounted(() => {
  syncMobile()
  window.addEventListener('resize', syncMobile)
  if (route.query.status === 'DISABLED') {
    userQuery.status = 'DISABLED'
  }
  void loadUsers(true)
})

onBeforeUnmount(() => window.removeEventListener('resize', syncMobile))

function syncMobile() {
  isMobile.value = window.matchMedia('(max-width: 760px)').matches
}

async function loadUsers(resetPage = false) {
  if (resetPage) userQuery.page = 1
  usersLoading.value = true
  try {
    usersPage.value = await adminApi.users(cleanParams(userQuery))
  } catch (error) {
    showError(error, '用户列表加载失败')
  } finally {
    usersLoading.value = false
  }
}

async function openUser(user: AdminUser) {
  detailVisible.value = true
  detailLoading.value = true
  try {
    const [detail, transactions, audits] = await Promise.all([
      adminApi.user(user.id),
      adminApi.transactions({ userId: user.id, page: 1, size: 5 }),
      adminApi.auditLogs(cleanParams<AdminAuditLogQuery>({ targetType: 'USER', targetId: user.id, page: 1, size: 5 }))
    ])
    selectedUser.value = detail
    recentTransactions.value = transactions.records
    recentAuditLogs.value = audits.records
  } catch (error) {
    showError(error, '用户详情加载失败')
  } finally {
    detailLoading.value = false
  }
}

function selectStatus(status: string) {
  userQuery.status = status
  statusPickerVisible.value = false
}

function startAction(user: AdminUser, action: UserAction) {
  pendingUser.value = user
  pendingAction.value = action
  reasonVisible.value = true
}

async function confirmAction(reason: string) {
  const user = pendingUser.value
  const action = pendingAction.value
  if (!user || !action) return
  const nextStatus = user.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  const actionTextValue = action === 'status'
    ? nextStatus === 'DISABLED' ? '禁用' : '启用'
    : action === 'revoke' ? '吊销凭证' : '重置邮箱'
  try {
    await showConfirmDialog({ title: reasonTitle.value, message: `确认${actionTextValue} ${user.username}？` })
  } catch {
    return
  }
  actionLoading.value = true
  try {
    if (action === 'status') {
      await adminApi.updateUserStatus(user.id, { status: nextStatus, reason })
    } else if (action === 'revoke') {
      await adminApi.revokeUserTokens(user.id, reason)
    } else {
      await adminApi.resetUserEmail(user.id, reason)
    }
    showToast('操作已记录')
    reasonVisible.value = false
    await loadUsers()
    if (selectedUser.value?.user.id === user.id) {
      const latest = usersPage.value?.records.find((item) => item.id === user.id) || user
      await openUser(latest)
    }
  } catch (error) {
    showError(error, '操作失败')
  } finally {
    actionLoading.value = false
  }
}
</script>

<template>
  <div class="admin-users-page">
    <section class="admin-panel">
      <div class="page-head">
        <div>
          <span>用户治理</span>
          <h1>用户状态与凭证管理</h1>
        </div>
        <van-button size="small" plain type="primary" icon="replay" :loading="usersLoading" @click="loadUsers()">刷新</van-button>
      </div>

      <div class="admin-filters">
        <van-field v-model="userQuery.keyword" clearable placeholder="用户名或昵称" />
        <van-field :model-value="userQuery.status ? statusText(userQuery.status) : ''" readonly is-link placeholder="状态" @click="statusPickerVisible = true" />
        <van-button type="primary" icon="search" @click="loadUsers(true)">搜索</van-button>
      </div>

      <div class="admin-list">
        <PageSkeleton v-if="usersLoading && !usersPage" variant="list" :cards="3" :rows="2" />
        <template v-else>
          <article v-for="user in usersPage?.records" :key="user.id" class="admin-card" @click="openUser(user)">
            <div class="card-main">
              <h3>{{ user.nickname }} <small>@{{ user.username }}</small></h3>
              <p>ID {{ user.id }} · {{ statusText(user.status) }} · {{ user.emailVerifiedAt ? '邮箱已验证' : '待绑定邮箱' }} · {{ user.transactionCount }} 笔</p>
            </div>
            <div class="admin-actions" @click.stop>
              <van-tag v-if="user.admin" type="primary">管理员</van-tag>
              <van-button size="small" plain type="primary" icon="delete-o" @click="startAction(user, 'revoke')">吊销</van-button>
              <van-button size="small" plain type="warning" icon="envelop-o" @click="startAction(user, 'email')">邮箱</van-button>
              <van-button size="small" plain :type="user.status === 'ACTIVE' ? 'danger' : 'success'" @click="startAction(user, 'status')">
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

      <van-pagination
        v-if="usersPage && usersPage.totalPages > 1"
        v-model="userQuery.page"
        :total-items="usersPage.total"
        :items-per-page="userQuery.size"
        @change="loadUsers()"
      />
    </section>

    <van-popup v-model:show="detailVisible" :position="drawerPosition" teleport="body" class="admin-detail-popup">
      <div class="detail-head">
        <div>
          <span>用户详情</span>
          <h2>{{ selectedUser?.user.nickname || selectedUser?.user.username || '-' }}</h2>
        </div>
        <van-button size="small" icon="cross" aria-label="关闭" @click="detailVisible = false" />
      </div>

      <PageSkeleton v-if="detailLoading" variant="list" :cards="3" :rows="2" />
      <div v-else-if="selectedUser" class="detail-body">
        <section class="detail-section">
          <div class="detail-grid">
            <div><span>账号</span><strong>@{{ selectedUser.user.username }}</strong></div>
            <div><span>状态</span><strong>{{ statusText(selectedUser.user.status) }}</strong></div>
            <div><span>邮箱</span><strong>{{ selectedUser.user.email || '未绑定' }}</strong></div>
            <div><span>交易</span><strong>{{ selectedUser.statistics.transactionCount }} 笔</strong></div>
            <div><span>总支出</span><strong class="expense">{{ formatMoney(selectedUser.statistics.totalExpense) }}</strong></div>
            <div><span>总收入</span><strong class="income">{{ formatMoney(selectedUser.statistics.totalIncome) }}</strong></div>
          </div>
        </section>

        <section class="detail-section">
          <h3>最近交易</h3>
          <RouterLink
            v-for="record in recentTransactions"
            :key="record.id"
            class="mini-row"
            :to="`/admin/transactions?id=${record.id}`"
          >
            <div>
              <strong>{{ transactionTitle(record) }}</strong>
              <span>{{ displayDateTime(record.occurredAt) }} · {{ typeText(record.type) }}</span>
            </div>
            <b :class="record.type === 'EXPENSE' ? 'expense' : 'income'">{{ formatMoney(record.amount) }}</b>
          </RouterLink>
          <van-empty v-if="!recentTransactions.length" description="暂无交易" />
        </section>

        <section class="detail-section">
          <h3>最近审计</h3>
          <div v-for="log in recentAuditLogs" :key="log.id" class="mini-row">
            <div>
              <strong>{{ actionText(log.action) }}</strong>
              <span>{{ displayDateTime(log.createdAt) }} · {{ log.adminUsername || log.adminUserId }}</span>
            </div>
            <small>#{{ log.targetId }}</small>
          </div>
          <van-empty v-if="!recentAuditLogs.length" description="暂无审计" />
        </section>
      </div>
    </van-popup>

    <BottomSheet
      v-model:show="statusPickerVisible"
      title="选择用户状态"
      header-variant="toolbar"
      sheet-class="admin-filter-sheet"
      body-class="admin-filter-body"
    >
      <template #leading="{ close }">
        <button type="button" class="admin-filter-cancel" @click="close">取消</button>
      </template>
      <template #actions><span /></template>
      <div class="filter-option-list">
        <button
          v-for="option in statusOptions"
          :key="option.value || 'all'"
          type="button"
          class="filter-option"
          :class="{ active: option.value === userQuery.status }"
          @click="selectStatus(option.value)"
        >
          <span>
            <strong>{{ option.label }}</strong>
            <small>{{ option.description }}</small>
          </span>
          <van-icon v-if="option.value === userQuery.status" name="success" />
        </button>
      </div>
    </BottomSheet>

    <AdminReasonDialog
      v-model="reasonVisible"
      :title="reasonTitle"
      :target="reasonTarget"
      :loading="actionLoading"
      confirm-text="提交操作"
      @confirm="confirmAction"
    />
  </div>
</template>

<style scoped>
.admin-users-page,
.admin-list,
.detail-body {
  display: grid;
  gap: var(--space-12);
  min-width: 0;
}

.admin-panel,
.admin-card,
.detail-section {
  border: 1px solid var(--border-warm);
  border-radius: var(--radius-card);
  background: var(--card-bg);
}

.admin-panel {
  min-width: 0;
  padding: var(--space-12);
}

.page-head,
.admin-card,
.detail-head,
.mini-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-12);
}

.page-head {
  margin-bottom: var(--space-12);
}

.page-head > div,
.detail-head > div {
  min-width: 0;
}

.page-head span,
.admin-card p,
.admin-card small,
.detail-head span,
.detail-grid span,
.mini-row span {
  color: var(--text-secondary);
}

.page-head h1,
.admin-card h3,
.detail-head h2,
.detail-section h3 {
  margin: 0;
}

.page-head h1 {
  margin-top: var(--space-4);
  font-size: var(--font-size-page-title);
  line-height: var(--line-height-page-title);
}

.admin-filters {
  display: grid;
  grid-template-columns: minmax(180px, 1fr) minmax(120px, 160px) 44px;
  gap: var(--space-8);
  margin-bottom: var(--space-12);
}

.admin-card {
  min-width: 0;
  padding: var(--space-12);
  cursor: pointer;
}

.card-main {
  min-width: 0;
}

.admin-card p {
  margin: var(--space-6) 0 0;
  font-size: var(--font-size-meta);
  overflow-wrap: anywhere;
}

.admin-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  max-width: 100%;
  gap: var(--space-8);
}

.admin-detail-popup {
  width: min(520px, 100vw);
  max-width: 100vw;
  height: 100%;
  padding: var(--space-14);
  overflow-y: auto;
  overflow-x: hidden;
}

:deep(.bottom-sheet.admin-filter-sheet) {
  max-height: min(70vh, 520px);
  background: var(--page-bg-soft);
}

:deep(.bottom-sheet__body.admin-filter-body) {
  padding: var(--space-0) var(--space-12) max(var(--space-14), env(safe-area-inset-bottom));
}

.admin-filter-cancel {
  border: 0;
  background: transparent;
  color: var(--text-secondary);
  font: inherit;
  text-align: left;
}

.filter-option-list {
  display: grid;
  gap: var(--space-8);
  padding: var(--space-12);
}

.filter-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-12);
  min-height: 64px;
  padding: var(--space-10) var(--space-12);
  border: 1px solid var(--border-warm);
  border-radius: var(--radius-inner);
  background: var(--card-bg);
  color: var(--text-main);
  font: inherit;
  text-align: left;
}

.filter-option span,
.filter-option strong,
.filter-option small {
  display: block;
  min-width: 0;
}

.filter-option small {
  margin-top: var(--space-3);
  color: var(--text-secondary);
  font-size: var(--font-size-meta);
}

.filter-option.active {
  border-color: rgba(var(--theme-primary-glow-rgb), 0.46);
  background: var(--primary-soft);
  color: var(--primary);
}

.detail-head {
  margin-bottom: var(--space-12);
}

.detail-section {
  min-width: 0;
  padding: var(--space-12);
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-10);
}

.detail-grid div {
  min-width: 0;
  padding: var(--space-10);
  border: 1px solid var(--border-warm);
  border-radius: var(--radius-inner);
}

.detail-grid span,
.detail-grid strong {
  display: block;
  overflow-wrap: anywhere;
}

.mini-row {
  padding: var(--space-10);
  border-bottom: 1px solid var(--border-warm);
  color: var(--text-main);
  text-decoration: none;
}

.mini-row:last-child {
  border-bottom: 0;
}

.mini-row div {
  min-width: 0;
}

.expense {
  color: var(--expense);
}

.income {
  color: var(--income);
}

@media (max-width: 760px) {
  .admin-filters {
    grid-template-columns: 1fr;
  }

  .admin-card {
    align-items: flex-start;
    flex-direction: column;
  }

  .admin-actions {
    justify-content: flex-start;
  }

  .page-head,
  .detail-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .page-head h1,
  .detail-head h2 {
    font-size: var(--font-size-panel-title);
    line-height: var(--line-height-panel-title);
    overflow-wrap: anywhere;
  }

  .admin-detail-popup {
    width: 100%;
    max-width: 100vw;
    height: 86vh;
    border-radius: var(--radius-sheet) var(--radius-sheet) 0 0;
  }

  .detail-grid {
    grid-template-columns: 1fr;
  }
}
</style>
