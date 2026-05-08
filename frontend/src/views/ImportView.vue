<script setup lang="ts">
import { ref } from 'vue'
import { showToast } from 'vant'
import { importApi } from '@/api/services'
import type { ImportResult } from '@/types'
import { showError } from '@/utils/errors'

const inputRef = ref<HTMLInputElement | null>(null)
const selectedFile = ref<File | null>(null)
const result = ref<ImportResult | null>(null)
const loading = ref(false)

function pickFile() {
  inputRef.value?.click()
}

function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  selectedFile.value = input.files?.[0] || null
  result.value = null
}

async function submit() {
  if (!selectedFile.value) {
    showToast('请选择 CSV 文件')
    return
  }
  loading.value = true
  try {
    result.value = await importApi.transactionsCsv(selectedFile.value)
    showToast(`已导入 ${result.value.importedRows} 条`)
  } catch (error) {
    showError(error, '导入失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="page">
    <van-nav-bar title="数据导入" left-arrow @click-left="$router.back()" />
    <div class="page-content">
      <section class="section panel">
        <input ref="inputRef" class="file-input" type="file" accept=".csv,text/csv" @change="onFileChange" />
        <van-cell title="CSV 文件" :value="selectedFile?.name || '未选择'" />
        <div class="import-actions">
          <van-button block round plain type="primary" icon="orders-o" @click="pickFile">选择文件</van-button>
          <van-button block round type="primary" icon="upgrade" :loading="loading" @click="submit">导入 CSV</van-button>
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
</style>
