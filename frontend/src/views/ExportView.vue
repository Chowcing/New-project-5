<script setup lang="ts">
import { reactive } from 'vue'
import { showToast } from 'vant'
import { exportApi } from '@/api/services'
import { currentMonth, todayDate } from '@/utils/date'

const query = reactive({
  type: '',
  startDate: `${currentMonth()}-01`,
  endDate: todayDate(),
  keyword: ''
})

async function download() {
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
}
</script>

<template>
  <main class="page">
    <van-nav-bar title="数据导出" left-arrow @click-left="$router.back()" />
    <div class="page-content">
      <section class="section panel">
        <van-field label="类型">
          <template #input>
            <select v-model="query.type" class="native-select">
              <option value="">全部</option>
              <option value="EXPENSE">支出</option>
              <option value="INCOME">收入</option>
            </select>
          </template>
        </van-field>
        <van-field v-model="query.startDate" type="date" label="开始" />
        <van-field v-model="query.endDate" type="date" label="结束" />
        <van-field v-model="query.keyword" label="关键词" placeholder="可选" />
      </section>

      <section class="section">
        <van-button block round type="primary" icon="down" @click="download">导出 CSV</van-button>
      </section>
    </div>
  </main>
</template>

<style scoped>
.native-select {
  width: 100%;
  border: 0;
  background: transparent;
  color: #1f2933;
  font: inherit;
}
</style>

