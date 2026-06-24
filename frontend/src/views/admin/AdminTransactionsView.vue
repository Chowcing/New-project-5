<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { adminApi, type AdminTransactionQuery } from '@/api/services'
import BottomSheet from '@/components/BottomSheet.vue'
import ModernDateField from '@/components/ModernDateField.vue'
import PageSkeleton from '@/components/PageSkeleton.vue'
import type { AdminTransaction, AdminTransactionDetail, PageResponse } from '@/types'
import { showError } from '@/utils/errors'
import AdminReasonDialog from './AdminReasonDialog.vue'
import {
  actionText,
  channelText,
  cleanParams,
  displayDateTime,
  formatMoney,
  statusText,
  transactionTitle,
  typeText
} from './adminUtils'

const route = useRoute()
const transactionsLoading = ref(false)
const detailLoading = ref(false)
const actionLoading = ref(false)
const imageLoading = ref(false)
const transactionsPage = ref<PageResponse<AdminTransaction> | null>(null)
const detail = ref<AdminTransactionDetail | null>(null)
const detailVisible = ref(false)
const reasonVisible = ref(false)
const pendingRecord = ref<AdminTransaction | null>(null)
const imageUrls = ref<Record<number, string>>({})
const isMobile = ref(false)
const typePickerVisible = ref(false)
const channelPickerVisible = ref(false)

const typeOptions = [
  { label: '全部类型', value: '', description: '查看支出和收入' },
  { label: '支出', value: 'EXPENSE', description: '只查看支出交易' },
  { label: '收入', value: 'INCOME', description: '只查看收入交易' }
]

const channelOptions = [
  { label: '全部渠道', value: '', description: '查看线上和线下交易' },
  { label: '线上', value: 'ONLINE', description: '只查看线上交易' },
  { label: '线下', value: 'OFFLINE', description: '只查看线下交易' }
]

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

const drawerPosition = computed(() => isMobile.value ? 'bottom' : 'right')
const reasonTarget = computed(() => pendingRecord.value ? `${transactionTitle(pendingRecord.value)} · ${formatMoney(pendingRecord.value.amount)}` : '-')

onMounted(async () => {
  syncMobile()
  window.addEventListener('resize', syncMobile)
  await loadTransactions(true)
  const id = Number(route.query.id)
  if (Number.isFinite(id) && id > 0) {
    await openTransaction(id)
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', syncMobile)
  revokeImageUrls()
})

watch(detailVisible, (visible) => {
  if (!visible) {
    revokeImageUrls()
  }
})

function syncMobile() {
  isMobile.value = window.matchMedia('(max-width: 760px)').matches
}

async function loadTransactions(resetPage = false) {
  if (resetPage) transactionQuery.page = 1
  transactionsLoading.value = true
  try {
    transactionsPage.value = await adminApi.transactions(cleanParams(transactionQuery))
  } catch (error) {
    showError(error, '交易列表加载失败')
  } finally {
    transactionsLoading.value = false
  }
}

async function openTransaction(input: AdminTransaction | number) {
  const id = typeof input === 'number' ? input : input.id
  detailVisible.value = true
  detailLoading.value = true
  revokeImageUrls()
  try {
    detail.value = await adminApi.transaction(id)
    await loadImages()
  } catch (error) {
    showError(error, '交易详情加载失败')
  } finally {
    detailLoading.value = false
  }
}

async function loadImages() {
  if (!detail.value?.images.length) return
  imageLoading.value = true
  try {
    const entries = await Promise.all(detail.value.images.map(async (image) => {
      const blob = await adminApi.adminTransactionImageBlob(detail.value!.transaction.id, image.id)
      return [image.id, URL.createObjectURL(blob)] as const
    }))
    imageUrls.value = Object.fromEntries(entries)
  } catch (error) {
    showError(error, '凭证图片加载失败')
  } finally {
    imageLoading.value = false
  }
}

function revokeImageUrls() {
  Object.values(imageUrls.value).forEach((url) => URL.revokeObjectURL(url))
  imageUrls.value = {}
}

function selectType(type: string) {
  transactionQuery.type = type
  typePickerVisible.value = false
}

function selectChannel(channel: string) {
  transactionQuery.channel = channel
  channelPickerVisible.value = false
}

function startDelete(record: AdminTransaction) {
  pendingRecord.value = record
  reasonVisible.value = true
}

async function confirmDelete(reason: string) {
  const record = pendingRecord.value
  if (!record) return
  try {
    await showConfirmDialog({ title: '删除交易', message: `确认删除「${transactionTitle(record)}」？` })
  } catch {
    return
  }
  actionLoading.value = true
  try {
    await adminApi.deleteTransaction(record.id, reason)
    showToast('记录已删除')
    reasonVisible.value = false
    detailVisible.value = false
    await loadTransactions()
  } catch (error) {
    showError(error, '删除失败')
  } finally {
    actionLoading.value = false
  }
}
</script>

<template>
  <div class="admin-transactions-page">
    <section class="admin-panel">
      <div class="page-head">
        <div>
          <span>交易治理</span>
          <h1>跨用户交易查询与处理</h1>
        </div>
        <van-button size="small" plain type="primary" icon="replay" :loading="transactionsLoading" @click="loadTransactions()">刷新</van-button>
      </div>

      <div class="admin-filters">
        <van-field v-model.number="transactionQuery.userId" clearable type="number" placeholder="用户 ID" />
        <van-field v-model="transactionQuery.keyword" clearable placeholder="关键词" />
        <van-field :model-value="transactionQuery.type ? typeText(transactionQuery.type) : ''" readonly is-link placeholder="类型" @click="typePickerVisible = true" />
        <van-field :model-value="transactionQuery.channel ? channelText(transactionQuery.channel) : ''" readonly is-link placeholder="渠道" @click="channelPickerVisible = true" />
        <ModernDateField v-model="transactionQuery.startDate" mode="date" label="" title="选择开始日期" placeholder="开始日期" />
        <ModernDateField v-model="transactionQuery.endDate" mode="date" label="" title="选择结束日期" placeholder="结束日期" />
        <van-button type="primary" icon="search" @click="loadTransactions(true)">搜索</van-button>
      </div>

      <div class="admin-list">
        <PageSkeleton v-if="transactionsLoading && !transactionsPage" variant="list" :cards="3" :rows="2" />
        <template v-else>
          <article v-for="record in transactionsPage?.records" :key="record.id" class="admin-card" @click="openTransaction(record)">
            <div class="card-main">
              <h3>{{ transactionTitle(record) }} <small>{{ record.nickname || record.username }}</small></h3>
              <p>{{ displayDateTime(record.occurredAt) }} · {{ typeText(record.type) }} · {{ channelText(record.channel) }} · {{ record.categoryName }} · {{ record.paymentMethodName }}</p>
            </div>
            <div class="admin-actions" @click.stop>
              <strong :class="record.type === 'EXPENSE' ? 'expense' : 'income'">{{ formatMoney(record.amount) }}</strong>
              <van-button size="small" plain type="danger" icon="delete-o" @click="startDelete(record)">删除</van-button>
            </div>
          </article>
        </template>
        <van-empty v-if="transactionsPage && !transactionsPage.records.length" description="暂无交易" />
      </div>

      <van-pagination
        v-if="transactionsPage && transactionsPage.totalPages > 1"
        v-model="transactionQuery.page"
        :total-items="transactionsPage.total"
        :items-per-page="transactionQuery.size"
        @change="loadTransactions()"
      />
    </section>

    <van-popup v-model:show="detailVisible" :position="drawerPosition" teleport="body" class="admin-detail-popup">
      <div class="detail-head">
        <div>
          <span>交易详情</span>
          <h2>{{ detail ? transactionTitle(detail.transaction) : '-' }}</h2>
        </div>
        <van-button size="small" icon="cross" aria-label="关闭" @click="detailVisible = false" />
      </div>

      <PageSkeleton v-if="detailLoading" variant="list" :cards="3" :rows="2" />
      <div v-else-if="detail" class="detail-body">
        <section class="hero-card" :class="detail.transaction.type === 'EXPENSE' ? 'expense-border' : 'income-border'">
          <span>{{ typeText(detail.transaction.type) }}</span>
          <strong :class="detail.transaction.type === 'EXPENSE' ? 'expense' : 'income'">{{ formatMoney(detail.transaction.amount) }}</strong>
          <p>{{ displayDateTime(detail.transaction.occurredAt) }}</p>
        </section>

        <section class="detail-section">
          <h3>记录信息</h3>
          <div class="detail-grid">
            <div><span>用户</span><strong>{{ detail.user?.nickname || detail.transaction.nickname || detail.transaction.username }}</strong></div>
            <div><span>账号状态</span><strong>{{ statusText(detail.user?.status) }}</strong></div>
            <div><span>分类</span><strong>{{ detail.transaction.categoryName }}</strong></div>
            <div><span>支付方式</span><strong>{{ detail.transaction.paymentMethodName }}</strong></div>
            <div><span>渠道</span><strong>{{ channelText(detail.transaction.channel) }}</strong></div>
            <div><span>{{ detail.transaction.channel === 'OFFLINE' ? '地点' : 'APP' }}</span><strong>{{ detail.transaction.offlinePlace || detail.transaction.onlineApp || '-' }}</strong></div>
          </div>
          <div class="note-block">
            <span>备注</span>
            <p>{{ detail.transaction.note || '无备注' }}</p>
          </div>
        </section>

        <section class="detail-section">
          <h3>凭证图片</h3>
          <div v-if="imageLoading" class="image-status">图片加载中</div>
          <div v-else-if="detail.images.length" class="image-grid">
            <a v-for="image in detail.images" :key="image.id" :href="imageUrls[image.id]" target="_blank" class="image-thumb">
              <img v-if="imageUrls[image.id]" :src="imageUrls[image.id]" :alt="image.originalFilename" />
              <van-icon v-else name="photo-o" />
            </a>
          </div>
          <van-empty v-else description="暂无凭证图片" />
        </section>

        <section class="detail-section">
          <h3>相关审计</h3>
          <div v-for="log in detail.relatedAuditLogs" :key="log.id" class="mini-row">
            <div>
              <strong>{{ actionText(log.action) }}</strong>
              <span>{{ displayDateTime(log.createdAt) }} · {{ log.adminUsername || log.adminUserId }}</span>
            </div>
            <small>#{{ log.targetId }}</small>
          </div>
          <van-empty v-if="!detail.relatedAuditLogs.length" description="暂无审计" />
        </section>

        <section class="detail-section danger-actions">
          <van-button block plain type="danger" icon="delete-o" @click="startDelete(detail.transaction)">删除交易</van-button>
        </section>
      </div>
    </van-popup>

    <AdminReasonDialog
      v-model="reasonVisible"
      title="删除交易"
      :target="reasonTarget"
      :loading="actionLoading"
      confirm-text="提交删除"
      @confirm="confirmDelete"
    />

    <BottomSheet
      v-model:show="typePickerVisible"
      title="选择交易类型"
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
          v-for="option in typeOptions"
          :key="option.value || 'all'"
          type="button"
          class="filter-option"
          :class="{ active: option.value === transactionQuery.type }"
          @click="selectType(option.value)"
        >
          <span>
            <strong>{{ option.label }}</strong>
            <small>{{ option.description }}</small>
          </span>
          <van-icon v-if="option.value === transactionQuery.type" name="success" />
        </button>
      </div>
    </BottomSheet>

    <BottomSheet
      v-model:show="channelPickerVisible"
      title="选择交易渠道"
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
          v-for="option in channelOptions"
          :key="option.value || 'all'"
          type="button"
          class="filter-option"
          :class="{ active: option.value === transactionQuery.channel }"
          @click="selectChannel(option.value)"
        >
          <span>
            <strong>{{ option.label }}</strong>
            <small>{{ option.description }}</small>
          </span>
          <van-icon v-if="option.value === transactionQuery.channel" name="success" />
        </button>
      </div>
    </BottomSheet>
  </div>
</template>

<style scoped>
.admin-transactions-page,
.admin-list,
.detail-body {
  display: grid;
  gap: var(--space-12);
  min-width: 0;
}

.admin-panel,
.admin-card,
.detail-section,
.hero-card {
  border: 1px solid var(--border-warm);
  border-radius: var(--radius-card);
  background: var(--card-bg);
}

.admin-panel,
.detail-section,
.hero-card {
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
.mini-row span,
.note-block span,
.hero-card span,
.hero-card p {
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
  grid-template-columns: 120px minmax(160px, 1fr) 120px 120px 150px 150px 44px;
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
  align-items: center;
  max-width: 100%;
  gap: var(--space-8);
}

.admin-detail-popup {
  width: min(560px, 100vw);
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

.hero-card strong {
  display: block;
  margin: var(--space-6) 0;
  font-size: var(--font-size-amount);
}

.expense-border {
  border-color: rgba(var(--expense-rgb), 0.36);
}

.income-border {
  border-color: rgba(var(--income-rgb), 0.36);
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-10);
  margin-top: var(--space-10);
}

.detail-grid div,
.note-block {
  min-width: 0;
  padding: var(--space-10);
  border: 1px solid var(--border-warm);
  border-radius: var(--radius-inner);
}

.detail-grid span,
.detail-grid strong,
.note-block span {
  display: block;
  overflow-wrap: anywhere;
}

.note-block {
  margin-top: var(--space-10);
}

.note-block p {
  margin: var(--space-6) 0 0;
  overflow-wrap: anywhere;
}

.image-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-8);
  margin-top: var(--space-10);
}

.image-thumb {
  display: grid;
  place-items: center;
  aspect-ratio: 1;
  border: 1px solid var(--border-warm);
  border-radius: var(--radius-inner);
  overflow: hidden;
  color: var(--primary);
}

.image-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.image-status {
  color: var(--text-secondary);
}

.mini-row {
  padding: var(--space-10);
  border-bottom: 1px solid var(--border-warm);
}

.mini-row:last-child {
  border-bottom: 0;
}

.mini-row div {
  min-width: 0;
}

.danger-actions {
  border-color: rgba(var(--expense-rgb), 0.34);
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
