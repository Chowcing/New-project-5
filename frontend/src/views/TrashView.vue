<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import BottomSheet from '@/components/BottomSheet.vue'
import { transactionApi, userApi } from '@/api/services'
import type {
  PageResponse,
  RecycleBinSettings,
  TrashedTransactionRecord
} from '@/types'
import { showError } from '@/utils/errors'
import { haptic } from '@/utils/haptics'
import { navigateBackOrHome } from '@/utils/navigationBack'

const RETENTION_OPTIONS = [
  { label: '7天', value: 7 },
  { label: '15天', value: 15 },
  { label: '1个月（30天）', value: 30 },
  { label: '3个月（90天）', value: 90 },
  { label: '半年（180天）', value: 180 },
  { label: '1年（365天）', value: 365 }
] as const

const PAGE_SIZE = 20
const router = useRouter()
const page = ref(1)
const pageData = ref<PageResponse<TrashedTransactionRecord> | null>(null)
const settings = ref<RecycleBinSettings>({ retentionDays: 30 })
const recordActionId = ref<number | null>(null)
const clearing = ref(false)
const loading = ref(false)
const settingsSaving = ref(false)
const settingsVisible = ref(false)
const retentionDraft = ref('30')

const records = computed(() => pageData.value?.records ?? [])
const total = computed(() => pageData.value?.total ?? 0)
const totalPages = computed(() => pageData.value?.totalPages ?? 0)
const retentionDaysDraft = computed(() => {
  if (!/^\d+$/.test(retentionDraft.value)) {
    return null
  }
  const value = Number(retentionDraft.value)
  return Number.isInteger(value) && value >= 1 && value <= 365
    ? value
    : null
})
const currentRetentionLabel = computed(
  () => RETENTION_OPTIONS.find(
    (option) => option.value === settings.value.retentionDays
  )?.label ?? `${settings.value.retentionDays}天`
)

async function loadTrash(targetPage = page.value) {
  loading.value = true
  try {
    const result = await transactionApi.trash({
      page: targetPage,
      size: PAGE_SIZE
    })
    const lastValidPage = Math.max(1, result.totalPages)
    if (
      targetPage > lastValidPage
      && result.records.length === 0
    ) {
      await loadTrash(lastValidPage)
      return
    }
    page.value = result.page
    pageData.value = result
  } catch (error) {
    showError(error, '回收站加载失败')
  } finally {
    loading.value = false
  }
}

async function loadSettings() {
  try {
    settings.value = await userApi.recycleBinSettings()
    retentionDraft.value = String(settings.value.retentionDays)
  } catch (error) {
    showError(error, '保留时间加载失败')
  }
}

async function refreshAfterRecordRemoval() {
  const targetPage = records.value.length === 1 && page.value > 1
    ? page.value - 1
    : page.value
  await loadTrash(targetPage)
}

async function restoreRecord(id: number) {
  if (recordActionId.value !== null) {
    return
  }
  recordActionId.value = id
  try {
    await transactionApi.restore(id)
    haptic('confirm')
    showToast('已恢复到流水')
    await refreshAfterRecordRemoval()
  } catch (error) {
    showError(error, '恢复失败')
  } finally {
    recordActionId.value = null
  }
}

async function permanentlyRemoveRecord(id: number) {
  if (recordActionId.value !== null) {
    return
  }
  try {
    await showConfirmDialog({
      title: '永久删除',
      message: '删除后不可恢复，确认永久删除这条记录？'
    })
  } catch {
    return
  }

  recordActionId.value = id
  try {
    await transactionApi.permanentlyRemove(id)
    haptic('warning')
    showToast('已永久删除')
    await refreshAfterRecordRemoval()
  } catch (error) {
    showError(error, '永久删除失败')
  } finally {
    recordActionId.value = null
  }
}

async function clearTrash() {
  if (clearing.value || total.value === 0) {
    return
  }
  try {
    await showConfirmDialog({
      title: '清空回收站',
      message: '所有回收站记录都将被永久删除且不可恢复，确认清空？'
    })
  } catch {
    return
  }

  clearing.value = true
  try {
    await transactionApi.clearTrash()
    haptic('warning')
    showToast('已清空回收站')
    await loadTrash(1)
  } catch (error) {
    showError(error, '清空回收站失败')
  } finally {
    clearing.value = false
  }
}

function openRetentionSettings() {
  retentionDraft.value = String(settings.value.retentionDays)
  settingsVisible.value = true
}

function selectRetentionDays(value: number) {
  retentionDraft.value = String(value)
}

async function saveRetentionDays() {
  const retentionDays = retentionDaysDraft.value
  if (retentionDays === null || settingsSaving.value) {
    return
  }

  if (retentionDays < settings.value.retentionDays) {
    try {
      await showConfirmDialog({
        title: '缩短保留时间',
        message: '缩短后，现有到期记录将在下次自动清理时删除。'
      })
    } catch {
      return
    }
  }

  settingsSaving.value = true
  try {
    settings.value = await userApi.updateRecycleBinSettings(retentionDays)
    retentionDraft.value = String(settings.value.retentionDays)
    settingsVisible.value = false
    haptic('confirm')
    showToast('保留时间已更新')
  } catch (error) {
    showError(error, '保留时间更新失败')
  } finally {
    settingsSaving.value = false
  }
}

function money(value: number) {
  return Number(value || 0).toFixed(2)
}

function displayDateTime(value: string) {
  const match = value.match(
    /^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})/
  )
  if (!match) {
    return value
  }
  return `${match[1]}年${match[2]}月${match[3]}日 ${match[4]}:${match[5]}`
}

onMounted(() => {
  void Promise.all([loadTrash(), loadSettings()])
})
</script>

<template>
  <main class="page trash-page">
    <van-nav-bar
      title="回收站"
      left-arrow
      @click-left="navigateBackOrHome(router)"
    >
      <template #right>
        <button
          type="button"
          class="trash-nav-action"
          aria-label="设置保留时间"
          @click="openRetentionSettings"
        >
          <van-icon name="setting-o" />
          <span>设置</span>
        </button>
      </template>
    </van-nav-bar>

    <div class="page-content trash-content">
      <section class="section panel trash-summary">
        <div class="trash-summary-copy">
          <span class="trash-summary-icon" aria-hidden="true">
            <van-icon name="delete-o" />
          </span>
          <div>
            <strong>{{ total }} 条记录</strong>
            <p>保留 {{ currentRetentionLabel }}</p>
          </div>
        </div>
        <van-button
          plain
          type="danger"
          size="small"
          icon="delete-o"
          :disabled="total === 0"
          :loading="clearing"
          @click="clearTrash"
        >
          清空回收站
        </van-button>
      </section>

      <van-loading v-if="loading && !pageData" class="trash-loading">
        正在加载回收站
      </van-loading>

      <van-empty
        v-else-if="records.length === 0"
        image="default"
        description="回收站是空的"
      />

      <section v-else class="section trash-list" aria-label="回收站记录">
        <article
          v-for="item in records"
          :key="item.id"
          class="panel trash-record"
        >
          <header class="trash-record-heading">
            <div class="trash-record-title">
              <span :class="['trash-type-tag', item.type === 'EXPENSE' ? 'expense' : 'income']">
                {{ item.type === 'EXPENSE' ? '支出' : '收入' }}
              </span>
              <h2>{{ item.itemName || item.categoryName }}</h2>
            </div>
            <strong :class="['trash-amount', item.type === 'EXPENSE' ? 'expense' : 'income']">
              {{ item.type === 'EXPENSE' ? '-' : '+' }}¥{{ money(item.amount) }}
            </strong>
          </header>

          <div class="trash-record-meta">
            <span>
              <van-icon :name="item.categoryIcon || 'apps-o'" />
              {{ item.categoryName }} · {{ item.paymentMethodName }}
            </span>
            <span>
              <van-icon name="clock-o" />
              原记录 {{ displayDateTime(item.occurredAt) }}
            </span>
            <span>
              <van-icon name="delete-o" />
              移入时间 {{ displayDateTime(item.trashedAt) }}
            </span>
          </div>

          <div class="trash-record-actions">
            <van-button
              plain
              block
              type="primary"
              icon="revoke"
              :loading="recordActionId === item.id"
              @click="restoreRecord(item.id)"
            >
              恢复
            </van-button>
            <van-button
              plain
              block
              type="danger"
              icon="delete-o"
              :loading="recordActionId === item.id"
              @click="permanentlyRemoveRecord(item.id)"
            >
              永久删除
            </van-button>
          </div>
        </article>
      </section>

      <nav
        v-if="totalPages > 1"
        class="panel trash-pagination"
        aria-label="回收站分页"
      >
        <van-button
          plain
          type="primary"
          icon="arrow-left"
          :disabled="page <= 1 || loading"
          @click="loadTrash(page - 1)"
        >
          上一页
        </van-button>
        <span>第 {{ page }} / {{ totalPages }} 页</span>
        <van-button
          plain
          type="primary"
          icon-position="right"
          icon="arrow"
          :disabled="page >= totalPages || loading"
          @click="loadTrash(page + 1)"
        >
          下一页
        </van-button>
      </nav>
    </div>

    <BottomSheet
      v-model:show="settingsVisible"
      title="设置保留时间"
      subtitle="到期记录会在自动清理任务运行时删除"
    >
      <div class="retention-sheet">
        <div class="retention-options" role="group" aria-label="保留时间快捷选项">
          <button
            v-for="option in RETENTION_OPTIONS"
            :key="option.value"
            type="button"
            :class="['retention-option', { active: retentionDraft === String(option.value) }]"
            :aria-label="option.label"
            @click="selectRetentionDays(option.value)"
          >
            <van-icon
              :name="retentionDraft === String(option.value) ? 'success' : 'clock-o'"
            />
            <span>{{ option.label }}</span>
          </button>
        </div>

        <label class="retention-custom">
          <span>自定义保留天数</span>
          <input
            v-model="retentionDraft"
            type="number"
            inputmode="numeric"
            min="1"
            max="365"
            step="1"
            aria-label="自定义保留天数"
            placeholder="请输入 1–365 的整数"
          />
          <small>仅支持 1–365 的整数</small>
        </label>

        <van-button
          block
          round
          type="primary"
          icon="success"
          aria-label="保存保留时间"
          :disabled="retentionDaysDraft === null"
          :loading="settingsSaving"
          @click="saveRetentionDays"
        >
          保存保留时间
        </van-button>
      </div>
    </BottomSheet>
  </main>
</template>

<style scoped>
.trash-page {
  padding-bottom: max(var(--space-24), env(safe-area-inset-bottom));
}

.trash-content,
.trash-list,
.retention-sheet {
  display: grid;
  gap: var(--space-12);
}

.trash-nav-action {
  display: inline-flex;
  align-items: center;
  gap: var(--space-4);
  color: var(--primary);
  font-size: var(--font-size-body);
}

.trash-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-12);
}

.trash-summary-copy {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: var(--space-10);
}

.trash-summary-icon {
  display: grid;
  width: var(--space-38);
  height: var(--space-38);
  flex: 0 0 auto;
  place-items: center;
  border-radius: var(--radius-card);
  background: var(--expense-soft);
  color: var(--expense);
  font-size: var(--icon-size-md);
}

.trash-summary-copy strong,
.trash-summary-copy p {
  display: block;
  margin: var(--space-0);
}

.trash-summary-copy strong {
  color: var(--text-main);
  font-size: var(--font-size-body-strong);
  line-height: var(--line-height-body-strong);
}

.trash-summary-copy p {
  margin-top: var(--space-3);
  color: var(--text-secondary);
  font-size: var(--font-size-meta);
  line-height: var(--line-height-meta);
}

.trash-loading {
  padding: var(--space-48) var(--space-0);
  text-align: center;
}

.trash-record {
  display: grid;
  gap: var(--space-12);
}

.trash-record-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-12);
}

.trash-record-title {
  min-width: 0;
}

.trash-record-title h2 {
  margin: var(--space-6) var(--space-0) var(--space-0);
  overflow: hidden;
  color: var(--text-main);
  font-size: var(--font-size-panel-title);
  line-height: var(--line-height-panel-title);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.trash-type-tag {
  display: inline-flex;
  align-items: center;
  min-height: var(--space-24);
  border-radius: var(--radius-pill);
  padding: var(--space-3) var(--space-8);
  background: var(--primary-soft);
  font-size: var(--font-size-caption);
  font-weight: 700;
}

.trash-type-tag.expense {
  background: var(--expense-soft);
}

.trash-type-tag.income {
  background: var(--income-soft);
}

.trash-amount {
  flex: 0 0 auto;
  font-size: var(--font-size-amount);
  font-weight: 750;
  line-height: var(--line-height-amount);
}

.trash-record-meta {
  display: grid;
  gap: var(--space-6);
  color: var(--text-secondary);
  font-size: var(--font-size-meta);
  line-height: var(--line-height-meta);
}

.trash-record-meta span {
  display: flex;
  align-items: center;
  gap: var(--space-6);
}

.trash-record-meta :deep(.van-icon) {
  color: var(--primary);
}

.trash-record-actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-8);
}

.trash-pagination {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  align-items: center;
  gap: var(--space-8);
}

.trash-pagination span {
  color: var(--text-secondary);
  font-size: var(--font-size-meta);
  white-space: nowrap;
}

.retention-options {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-8);
}

.retention-option {
  display: flex;
  min-height: var(--space-48);
  align-items: center;
  justify-content: center;
  gap: var(--space-6);
  border: 1px solid var(--border-warm);
  border-radius: var(--radius-card);
  padding: var(--space-8);
  background: var(--glass-bg);
  color: var(--text-main);
  font-size: var(--font-size-body);
}

.retention-option.active {
  border-color: var(--primary);
  background: var(--primary-soft);
  color: var(--primary);
  box-shadow: var(--ring-primary-soft);
}

.retention-custom {
  display: grid;
  gap: var(--space-6);
  color: var(--text-main);
  font-size: var(--font-size-body);
}

.retention-custom input {
  width: 100%;
  min-height: var(--space-48);
  border: 1px solid var(--border-warm);
  border-radius: var(--radius-card);
  padding: var(--space-0) var(--space-12);
  background: var(--glass-bg);
  color: var(--text-main);
  font-size: var(--font-size-body);
  outline: none;
}

.retention-custom input:focus {
  border-color: var(--primary);
  box-shadow: var(--ring-primary-soft);
}

.retention-custom small {
  color: var(--text-muted);
  font-size: var(--font-size-meta);
  line-height: var(--line-height-meta);
}

@media (max-width: 360px) {
  .trash-summary {
    align-items: stretch;
    flex-direction: column;
  }

  .trash-record-heading {
    flex-direction: column;
  }

  .trash-pagination {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .trash-pagination span {
    grid-column: 1 / -1;
    grid-row: 1;
    text-align: center;
  }
}
</style>
