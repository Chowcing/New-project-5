<script setup lang="ts">
import { reactive } from 'vue'
import { showToast } from 'vant'
import { exportApi } from '@/api/services'
import ModernDateField from '@/components/ModernDateField.vue'
import ModernSelectField from '@/components/ModernSelectField.vue'
import { currentMonth, todayDate } from '@/utils/date'
import { showError } from '@/utils/errors'

const query = reactive({
  type: '',
  startDate: `${currentMonth()}-01`,
  endDate: todayDate(),
  keyword: ''
})
const typeOptions = [
  { label: '全部', value: '' },
  { label: '支出', value: 'EXPENSE' },
  { label: '收入', value: 'INCOME' }
]

function setType(value: string | number | undefined) {
  query.type = value === 'EXPENSE' || value === 'INCOME' ? value : ''
}

async function download() {
  try {
    const blob = await exportApi.transactionsCsv({
      type: query.type || undefined,
      startDate: query.startDate || undefined,
      endDate: query.endDate || undefined,
      keyword: query.keyword || undefined
    })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `transactions-${query.startDate}-${query.endDate}.csv`
    link.click()
    URL.revokeObjectURL(url)
    showToast('CSV 已生成')
  } catch (error) {
    showError(error, '导出失败')
  }
}
</script>

<template>
  <main class="page">
    <van-nav-bar title="数据导出" left-arrow @click-left="$router.back()" />
    <div class="page-content">
      <section class="section panel">
        <ModernSelectField
          :model-value="query.type"
          label="类型"
          title="选择导出类型"
          :options="typeOptions"
          @update:model-value="setType"
        />
        <ModernDateField v-model="query.startDate" mode="date" label="开始" title="选择开始日期" />
        <ModernDateField v-model="query.endDate" mode="date" label="结束" title="选择结束日期" />
        <van-field v-model="query.keyword" label="关键词" placeholder="可选" />
      </section>

      <section class="section">
        <van-button block round type="primary" icon="down" @click="download">导出 CSV</van-button>
      </section>
    </div>
  </main>
</template>
