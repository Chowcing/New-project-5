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
const SAMPLE_HEADER = ['类型', '事项', '金额', '发生时间', '渠道', '线上APP', '线下地点', '支付方式', '分类', '备注']
const SAMPLE_ROWS = [
  ['支出', '早餐豆浆油条', '9.00', 1, '08:10:00', '线下', '', '小区早餐店', '微信', '餐饮', '早餐'],
  ['支出', '地铁通勤', '7.00', 1, '08:42:00', '线下', '', '地铁站', '微信', '交通', '上班'],
  ['支出', '午餐盖饭', '26.00', 1, '12:25:00', '线下', '', '公司食堂', '支付宝', '餐饮', ''],
  ['支出', '手机话费', '50.00', 1, '20:20:00', '线上', '支付宝', '', '支付宝', '通讯网络', '月度套餐'],
  ['支出', '超市日用品', '86.40', 2, '19:10:00', '线下', '', '永辉超市', '银行卡', '家居', '纸巾洗衣液'],
  ['支出', '网购书籍', '79.00', 3, '10:18:00', '线上', '京东', '', '银行卡', '教育', '专业书'],
  ['支出', '公交', '2.00', 3, '18:05:00', '线下', '', '公交车', '微信', '交通', '回家'],
  ['支出', '房租', '2800.00', 4, '09:30:00', '线上', '支付宝', '', '银行卡', '居住', '本月房租'],
  ['支出', '水电燃气', '186.32', 4, '20:15:00', '线上', '支付宝', '', '支付宝', '水电燃气', '账单缴费'],
  ['支出', '感冒药', '36.50', 5, '16:45:00', '线下', '', '社区药房', '微信', '医疗', ''],
  ['支出', '电影票', '45.00', 5, '19:00:00', '线上', '美团', '', '支付宝', '娱乐', '周末电影'],
  ['支出', '朋友聚餐', '168.00', 6, '20:35:00', '线下', '', '火锅店', '微信', '餐饮', '朋友聚餐'],
  ['收入', '工资到账', '12800.00', 7, '09:00:00', '线上', '微信', '', '银行卡', '工资', '本月工资'],
  ['收入', '项目奖金', '1500.00', 7, '09:10:00', '线上', '微信', '', '银行卡', '奖金', '季度奖金'],
  ['支出', '打车', '34.60', 7, '22:20:00', '线上', '滴滴', '', '微信', '交通', '晚归'],
  ['支出', '咖啡', '18.00', 8, '15:30:00', '线下', '', '咖啡店', '支付宝', '餐饮', ''],
  ['支出', '云闪付买菜', '64.20', 9, '18:40:00', '线下', '', '菜市场', '云闪付', '买菜', '晚餐食材'],
  ['支出', '', '28.00', 9, '19:10:00', '线上', '美团', '', '微信', '外卖', '极简模式空事项示例'],
  ['收入', '交通报销', '236.80', 10, '10:00:00', '线上', '支付宝', '', '银行卡', '报销', '上周出差'],
  ['支出', '生日礼物', '199.00', 11, '14:25:00', '线上', '淘宝', '', '银行卡', '人情', '朋友生日'],
  ['支出', '短途车票', '128.00', 12, '09:50:00', '线上', '铁路12306', '', '支付宝', '旅行', '高铁票'],
  ['支出', '酒店押金', '300.00', 12, '15:05:00', '线上', '携程旅行', '', '银行卡', '旅行', '周末住宿'],
  ['收入', '兼职收入', '600.00', 13, '18:30:00', '线上', '微信', '', '微信', '兼职', '周末兼职'],
  ['收入', '基金赎回', '520.00', 14, '14:00:00', '线上', '支付宝', '', '银行卡', '投资理财', '部分赎回'],
  ['支出', '运动鞋', '399.00', 15, '21:10:00', '线上', '天猫', '', '银行卡', '购物', '换季'],
  ['收入', '退款到账', '79.00', 16, '11:25:00', '线上', '支付宝', '', '支付宝', '退款', '退书'],
  ['支出', '备用现金支出', '12.00', 16, '17:55:00', '线下', '', '便利店', '现金', '其他', '零钱支付']
] as const

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

function padNumber(value: number) {
  return String(value).padStart(2, '0')
}

function currentSampleDate(day: number, time: string, baseDate = new Date()) {
  const year = baseDate.getFullYear()
  const monthIndex = baseDate.getMonth()
  const lastDay = new Date(year, monthIndex + 1, 0).getDate()
  return `${year}-${padNumber(monthIndex + 1)}-${padNumber(Math.min(day, lastDay))} ${time}`
}

function downloadSampleCsv() {
  const now = new Date()
  const rows = SAMPLE_ROWS.map((row) => [
    row[0],
    row[1],
    row[2],
    currentSampleDate(row[3], row[4], now),
    row[5],
    row[6],
    row[7],
    row[8],
    row[9],
    row[10]
  ])
  const csv = [SAMPLE_HEADER, ...rows].map((row) => row.map(csvCell).join(',')).join('\n')
  const blob = new Blob([`\uFEFF${csv}`], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `transactions-import-sample-${now.getFullYear()}-${padNumber(now.getMonth() + 1)}.csv`
  link.click()
  URL.revokeObjectURL(url)
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
            @click="downloadSampleCsv"
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
  color: var(--expense);
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
  color: var(--expense);
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
