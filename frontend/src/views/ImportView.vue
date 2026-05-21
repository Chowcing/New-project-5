<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { showToast } from 'vant'
import { importApi } from '@/api/services'
import type { ImportErrorType, ImportJob, ImportJobStatus, ImportRowError } from '@/types'
import { showError } from '@/utils/errors'

const LAST_IMPORT_JOB_KEY = 'expense.import.lastJobId'
const POLL_INTERVAL_MS = 1500
const ERROR_PREVIEW_LIMIT = 20
const ALL_ERROR_TYPES = 'ALL'

type ErrorFilter = ImportErrorType | typeof ALL_ERROR_TYPES

const errorTypeLabels: Record<ImportErrorType, string> = {
  PAYMENT_METHOD: '支付方式',
  CATEGORY: '分类',
  AMOUNT: '金额',
  TIME: '时间',
  REQUIRED: '必填项',
  TYPE: '类型',
  CHANNEL: '渠道',
  ROW_FORMAT: '行格式',
  DUPLICATE: '重复记录',
  OTHER: '其他'
}

const inputRef = ref<HTMLInputElement | null>(null)
const selectedFile = ref<File | null>(null)
const job = ref<ImportJob | null>(null)
const loading = ref(false)
const restoring = ref(false)
const activeErrorType = ref<ErrorFilter>(ALL_ERROR_TYPES)
const expandedErrors = ref(false)
let pollTimer: ReturnType<typeof window.setTimeout> | null = null

const result = computed(() => job.value?.result || null)
const isProcessing = computed(() => job.value?.status === 'PENDING' || job.value?.status === 'RUNNING')
const handledRows = computed(() => (job.value?.importedRows || 0) + (job.value?.failedRows || 0))
const errors = computed(() => result.value?.errors || [])
const errorSummary = computed(() => {
  const counts = new Map<ImportErrorType, number>()
  for (const item of errors.value) {
    const type = normalizedErrorType(item)
    counts.set(type, (counts.get(type) || 0) + 1)
  }
  return [...counts.entries()]
    .map(([type, count]) => ({ type, label: errorTypeLabels[type], count }))
    .sort((a, b) => b.count - a.count)
})
const filteredErrors = computed(() => {
  if (activeErrorType.value === ALL_ERROR_TYPES) {
    return errors.value
  }
  return errors.value.filter((item) => normalizedErrorType(item) === activeErrorType.value)
})
const visibleErrors = computed(() => (
  expandedErrors.value ? filteredErrors.value : filteredErrors.value.slice(0, ERROR_PREVIEW_LIMIT)
))
const hiddenErrorCount = computed(() => Math.max(filteredErrors.value.length - visibleErrors.value.length, 0))
const hasPaymentMethodErrors = computed(() => errors.value.some((item) => normalizedErrorType(item) === 'PAYMENT_METHOD'))
const hasCategoryErrors = computed(() => errors.value.some((item) => normalizedErrorType(item) === 'CATEGORY'))
const progressPercentage = computed(() => {
  if (!job.value) return 0
  if (job.value.status === 'SUCCESS') return 100
  if (job.value.totalRows <= 0) return 0
  return Math.min(100, Math.round((handledRows.value / job.value.totalRows) * 100))
})

function normalizedErrorType(item: ImportRowError): ImportErrorType {
  return item.errorType && item.errorType in errorTypeLabels ? item.errorType : 'OTHER'
}

function errorTypeLabel(item: ImportRowError) {
  return errorTypeLabels[normalizedErrorType(item)]
}

function statusText(status?: ImportJobStatus) {
  const map: Record<ImportJobStatus, string> = {
    PENDING: '排队中',
    RUNNING: '导入中',
    SUCCESS: '已完成',
    FAILED: '导入失败'
  }
  return status ? map[status] : '-'
}

function statusType(status?: ImportJobStatus) {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'RUNNING') return 'primary'
  return 'warning'
}

function progressText() {
  if (!job.value) return ''
  if (job.value.totalRows <= 0) return statusText(job.value.status)
  return `${handledRows.value}/${job.value.totalRows}`
}

function pickFile() {
  if (isProcessing.value) {
    showToast('导入处理中，请稍后')
    return
  }
  inputRef.value?.click()
}

function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  selectedFile.value = input.files?.[0] || null
  job.value = null
  activeErrorType.value = ALL_ERROR_TYPES
  expandedErrors.value = false
}

function saveLastJobId(id: number) {
  localStorage.setItem(LAST_IMPORT_JOB_KEY, String(id))
}

function lastJobId() {
  const raw = localStorage.getItem(LAST_IMPORT_JOB_KEY)
  const id = raw ? Number(raw) : 0
  return Number.isFinite(id) && id > 0 ? id : null
}

function clearPollTimer() {
  if (pollTimer) {
    window.clearTimeout(pollTimer)
    pollTimer = null
  }
}

function schedulePoll(id: number) {
  clearPollTimer()
  pollTimer = window.setTimeout(() => {
    void refreshJob(id, true)
  }, POLL_INTERVAL_MS)
}

function handlePolledJob(nextJob: ImportJob, keepPolling: boolean) {
  job.value = nextJob
  if (activeErrorType.value !== ALL_ERROR_TYPES && !nextJob.result?.errors.some((item) => normalizedErrorType(item) === activeErrorType.value)) {
    activeErrorType.value = ALL_ERROR_TYPES
  }
  saveLastJobId(nextJob.id)
  if (nextJob.status === 'SUCCESS') {
    clearPollTimer()
    showToast(`已导入 ${nextJob.result?.importedRows ?? nextJob.importedRows} 条`)
    return
  }
  if (nextJob.status === 'FAILED') {
    clearPollTimer()
    showToast(nextJob.errorMessage || '导入失败')
    return
  }
  if (keepPolling) {
    schedulePoll(nextJob.id)
  }
}

function selectErrorType(type: ErrorFilter) {
  activeErrorType.value = type
  expandedErrors.value = false
}

function errorFieldText(item: ImportRowError) {
  return [
    item.itemName ? `事项：${item.itemName}` : '',
    item.amount ? `金额：${item.amount}` : '',
    item.occurredAt ? `时间：${item.occurredAt}` : '',
    item.paymentMethodName ? `支付方式：${item.paymentMethodName}` : '',
    item.categoryName ? `分类：${item.categoryName}` : ''
  ].filter(Boolean).join(' · ')
}

function csvCell(value: unknown) {
  const text = value == null ? '' : String(value)
  return `"${text.replace(/"/g, '""')}"`
}

function exportErrorCsv() {
  if (!errors.value.length) {
    showToast('暂无错误记录')
    return
  }
  const header = ['行号', '错误类型', '错误原因', '类型', '事项', '金额', '发生时间', '渠道', '线上APP', '线下地点', '支付方式', '分类', '备注']
  const rows = errors.value.map((item) => [
    item.rowNumber,
    errorTypeLabels[normalizedErrorType(item)],
    item.message,
    item.type,
    item.itemName,
    item.amount,
    item.occurredAt,
    item.channel,
    item.onlineApp,
    item.offlinePlace,
    item.paymentMethodName,
    item.categoryName,
    item.note
  ])
  const csv = [header, ...rows].map((row) => row.map(csvCell).join(',')).join('\n')
  const blob = new Blob([`\uFEFF${csv}`], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `导入错误记录-${job.value?.id || 'latest'}.csv`
  link.click()
  URL.revokeObjectURL(url)
}

async function refreshJob(id: number, keepPolling = false) {
  try {
    handlePolledJob(await importApi.getJob(id), keepPolling)
  } catch (error) {
    clearPollTimer()
    showError(error, '导入任务状态加载失败')
  }
}

async function submit() {
  if (loading.value || isProcessing.value) {
    showToast('导入处理中，请勿重复提交')
    return
  }
  if (!selectedFile.value) {
    showToast('请选择 CSV 文件')
    return
  }
  loading.value = true
  try {
    const createdJob = await importApi.transactionsCsv(selectedFile.value)
    job.value = createdJob
    saveLastJobId(createdJob.id)
    showToast('导入任务已创建')
    if (createdJob.status === 'PENDING' || createdJob.status === 'RUNNING') {
      schedulePoll(createdJob.id)
    } else {
      handlePolledJob(createdJob, false)
    }
  } catch (error) {
    showError(error, '导入任务创建失败')
  } finally {
    loading.value = false
  }
}

async function restoreLastJob() {
  const id = lastJobId()
  if (!id) return
  restoring.value = true
  try {
    const restoredJob = await importApi.getJob(id)
    job.value = restoredJob
    if (restoredJob.status === 'PENDING' || restoredJob.status === 'RUNNING') {
      schedulePoll(restoredJob.id)
    }
  } catch {
    localStorage.removeItem(LAST_IMPORT_JOB_KEY)
  } finally {
    restoring.value = false
  }
}

onMounted(() => {
  void restoreLastJob()
})

onBeforeUnmount(() => {
  clearPollTimer()
})
</script>

<template>
  <main class="page">
    <van-nav-bar title="数据导入" left-arrow @click-left="$router.back()" />
    <div class="page-content">
      <section class="section panel">
        <div class="section-heading">选择并导入 CSV</div>
        <input ref="inputRef" class="file-input" type="file" accept=".csv,text/csv" @change="onFileChange" />
        <van-cell title="CSV 文件" icon="description-o" :value="selectedFile?.name || '未选择'" />
        <div class="import-actions">
          <van-button block round plain type="primary" icon="orders-o" :disabled="isProcessing" @click="pickFile">选择文件</van-button>
          <van-button block round type="primary" icon="upgrade" :loading="loading || isProcessing" :disabled="loading || isProcessing || restoring" @click="submit">
            {{ isProcessing ? '导入处理中' : '导入 CSV' }}
          </van-button>
          <van-button
            block
            round
            plain
            type="default"
            icon="down"
            url="/samples/transactions-import-sample.csv"
          >
            示例数据
          </van-button>
        </div>
      </section>

      <section v-if="job" class="section panel">
        <div class="section-heading">导入进度</div>
        <van-cell title="导入任务" icon="underway-o">
          <template #value>
            <van-tag :type="statusType(job.status)">{{ statusText(job.status) }}</van-tag>
          </template>
        </van-cell>
        <van-cell title="任务编号" icon="label-o" :value="String(job.id)" />
        <van-cell v-if="job.originalFilename" title="文件名" icon="description-o" :value="job.originalFilename" />
        <van-cell v-if="job.errorMessage" title="失败原因" icon="warning-o" :label="job.errorMessage" />
        <div v-if="job.status === 'PENDING' || job.status === 'RUNNING' || job.totalRows > 0" class="import-progress">
          <div class="progress-meta">
            <span>{{ statusText(job.status) }}</span>
            <span>{{ progressText() }}</span>
          </div>
          <van-progress :percentage="progressPercentage" />
        </div>
      </section>

      <section v-if="result" class="section panel">
        <div class="section-heading">导入结果</div>
        <van-cell title="总行数" icon="orders-o" :value="String(result.totalRows)" />
        <van-cell title="成功" icon="passed" :value="String(result.importedRows)" />
        <van-cell title="失败" icon="warning-o" :value="String(result.failedRows)" />
      </section>

      <section v-if="errors.length" class="section panel error-panel">
        <van-cell title="错误记录" icon="warning-o">
          <template #value>
            <van-button size="small" plain type="primary" icon="down" @click="exportErrorCsv">导出</van-button>
          </template>
        </van-cell>

        <div class="error-summary">
          <button
            v-for="item in errorSummary"
            :key="item.type"
            type="button"
            :class="['error-summary-item', { active: activeErrorType === item.type }]"
            @click="selectErrorType(item.type)"
          >
            <span>{{ item.label }}</span>
            <strong>{{ item.count }}</strong>
          </button>
        </div>

        <div class="error-filter">
          <button
            type="button"
            :class="['filter-chip', { active: activeErrorType === 'ALL' }]"
            @click="selectErrorType('ALL')"
          >
            全部 {{ errors.length }}
          </button>
          <button
            v-for="item in errorSummary"
            :key="`filter-${item.type}`"
            type="button"
            :class="['filter-chip', { active: activeErrorType === item.type }]"
            @click="selectErrorType(item.type)"
          >
            {{ item.label }}
          </button>
        </div>

        <div v-if="hasCategoryErrors || hasPaymentMethodErrors" class="error-actions">
          <van-button v-if="hasCategoryErrors" size="small" round plain type="primary" icon="apps-o" to="/categories">分类管理</van-button>
          <van-button v-if="hasPaymentMethodErrors" size="small" round plain type="primary" icon="balance-o" to="/payment-methods">支付方式管理</van-button>
        </div>

        <van-cell
          v-for="item in visibleErrors"
          :key="`${item.rowNumber}-${item.message}`"
          class="error-row"
          :title="`第 ${item.rowNumber} 行 · ${errorTypeLabel(item)}`"
        >
          <template #label>
            <div class="error-message">{{ item.message }}</div>
            <div v-if="errorFieldText(item)" class="error-fields">{{ errorFieldText(item) }}</div>
          </template>
        </van-cell>

        <div v-if="hiddenErrorCount > 0 || expandedErrors" class="error-more">
          <van-button block round plain type="primary" :icon="expandedErrors ? 'arrow-up' : 'arrow-down'" @click="expandedErrors = !expandedErrors">
            {{ expandedErrors ? '收起错误记录' : `查看更多 ${hiddenErrorCount} 条` }}
          </van-button>
        </div>
      </section>
    </div>
  </main>
</template>

<style scoped>
.file-input {
  display: none;
}

.import-actions {
  display: grid;
  gap: var(--space-10);
  margin-top: var(--space-12);
}

.import-progress {
  padding: var(--space-10) var(--space-16) var(--space-4);
}

.progress-meta {
  display: flex;
  justify-content: space-between;
  margin-bottom: var(--space-8);
  color: var(--text-secondary);
  font-size: var(--font-size-caption);
}

.error-panel {
  padding: var(--space-0) var(--space-0) var(--space-12);
  overflow: hidden;
}

.error-summary {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-8);
  padding: var(--space-10) var(--space-12);
}

.error-summary-item,
.filter-chip {
  border: 1px solid var(--border-warm);
  background: var(--card-bg);
  color: var(--text-main);
  font: inherit;
}

.error-summary-item {
  display: flex;
  min-height: 44px;
  align-items: center;
  justify-content: space-between;
  border-radius: var(--radius-card);
  padding: var(--space-8) var(--space-10);
  font-size: var(--font-size-meta);
}

.error-summary-item strong {
  color: #d14343;
  font-size: var(--font-size-section-title);
}

.error-summary-item.active,
.filter-chip.active {
  border-color: var(--primary);
  background: var(--primary-soft);
  color: var(--primary);
}

.error-filter {
  display: flex;
  gap: var(--space-8);
  overflow-x: auto;
  padding: var(--space-2) var(--space-12) var(--space-10);
}

.error-filter::-webkit-scrollbar {
  display: none;
}

.filter-chip {
  min-height: 30px;
  flex: 0 0 auto;
  border-radius: var(--radius-pill);
  padding: var(--space-0) var(--space-12);
  font-size: var(--font-size-caption);
}

.error-actions {
  display: flex;
  gap: var(--space-8);
  padding: var(--space-0) var(--space-12) var(--space-10);
}

.error-row {
  padding: var(--space-10) var(--space-12);
}

.error-message {
  color: #d14343;
  line-height: var(--line-height-text);
}

.error-fields {
  margin-top: var(--space-4);
  color: var(--text-secondary);
  line-height: var(--line-height-text);
}

.error-more {
  padding: var(--space-12) var(--space-12) var(--space-0);
}
</style>
