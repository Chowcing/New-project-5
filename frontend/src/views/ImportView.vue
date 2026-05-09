<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { showToast } from 'vant'
import { importApi } from '@/api/services'
import type { ImportJob, ImportJobStatus } from '@/types'
import { showError } from '@/utils/errors'

const LAST_IMPORT_JOB_KEY = 'expense.import.lastJobId'
const POLL_INTERVAL_MS = 1500

const inputRef = ref<HTMLInputElement | null>(null)
const selectedFile = ref<File | null>(null)
const job = ref<ImportJob | null>(null)
const loading = ref(false)
const restoring = ref(false)
let pollTimer: ReturnType<typeof window.setTimeout> | null = null

const result = computed(() => job.value?.result || null)
const isProcessing = computed(() => job.value?.status === 'PENDING' || job.value?.status === 'RUNNING')
const handledRows = computed(() => (job.value?.importedRows || 0) + (job.value?.failedRows || 0))
const progressPercentage = computed(() => {
  if (!job.value) return 0
  if (job.value.status === 'SUCCESS') return 100
  if (job.value.totalRows <= 0) return 0
  return Math.min(100, Math.round((handledRows.value / job.value.totalRows) * 100))
})

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
        <input ref="inputRef" class="file-input" type="file" accept=".csv,text/csv" @change="onFileChange" />
        <van-cell title="CSV 文件" :value="selectedFile?.name || '未选择'" />
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
        <van-cell title="导入任务">
          <template #value>
            <van-tag :type="statusType(job.status)">{{ statusText(job.status) }}</van-tag>
          </template>
        </van-cell>
        <van-cell title="任务编号" :value="String(job.id)" />
        <van-cell v-if="job.originalFilename" title="文件名" :value="job.originalFilename" />
        <van-cell v-if="job.errorMessage" title="失败原因" :label="job.errorMessage" />
        <div v-if="job.status === 'PENDING' || job.status === 'RUNNING' || job.totalRows > 0" class="import-progress">
          <div class="progress-meta">
            <span>{{ statusText(job.status) }}</span>
            <span>{{ progressText() }}</span>
          </div>
          <van-progress :percentage="progressPercentage" />
        </div>
      </section>

      <section v-if="result" class="section panel">
        <van-cell title="总行数" :value="String(result.totalRows)" />
        <van-cell title="成功" :value="String(result.importedRows)" />
        <van-cell title="失败" :value="String(result.failedRows)" />
      </section>

      <section v-if="result?.errors.length" class="section panel">
        <van-cell title="错误记录" />
        <van-cell
          v-for="item in result.errors"
          :key="`${item.rowNumber}-${item.message}`"
          :title="`第 ${item.rowNumber} 行`"
          :label="item.message"
        />
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
  gap: 10px;
  margin-top: 12px;
}

.import-progress {
  padding: 10px 16px 4px;
}

.progress-meta {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
  color: #6b7280;
  font-size: 12px;
}
</style>
