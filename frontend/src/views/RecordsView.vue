<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { showConfirmDialog, showToast } from 'vant'
import { accountApi, categoryApi, transactionApi } from '@/api/services'
import type { Account, Category, TransactionRecord } from '@/types'
import { currentMonth, money, todayDate } from '@/utils/date'

const categories = ref<Category[]>([])
const accounts = ref<Account[]>([])
const records = ref<TransactionRecord[]>([])
const query = reactive({
  type: '',
  startDate: `${currentMonth()}-01`,
  endDate: todayDate(),
  categoryId: undefined as number | undefined,
  accountId: undefined as number | undefined,
  keyword: ''
})

async function load() {
  records.value = await transactionApi.list({
    type: query.type || undefined,
    startDate: query.startDate || undefined,
    endDate: query.endDate || undefined,
    categoryId: query.categoryId,
    accountId: query.accountId,
    keyword: query.keyword || undefined
  })
}

async function init() {
  categories.value = await categoryApi.list()
  accounts.value = await accountApi.list()
  await load()
}

async function removeRecord(id: number) {
  await showConfirmDialog({ title: '删除记录', message: '确认删除这条记录？' })
  await transactionApi.remove(id)
  showToast('已删除')
  await load()
}

onMounted(init)
</script>

<template>
  <main class="page">
    <van-nav-bar title="收支明细" />
    <div class="page-content">
      <section class="section panel">
        <van-field label="类型">
          <template #input>
            <select v-model="query.type" class="native-select" @change="load">
              <option value="">全部</option>
              <option value="EXPENSE">支出</option>
              <option value="INCOME">收入</option>
            </select>
          </template>
        </van-field>
        <van-field v-model="query.startDate" type="date" label="开始" @change="load" />
        <van-field v-model="query.endDate" type="date" label="结束" @change="load" />
        <van-field v-model="query.keyword" label="搜索" placeholder="备注、分类、账户" @keyup.enter="load">
          <template #button>
            <van-button size="small" type="primary" @click="load">筛选</van-button>
          </template>
        </van-field>
      </section>

      <section class="section panel">
        <div v-if="records.length === 0" class="empty-text">没有符合条件的记录</div>
        <van-swipe-cell v-for="item in records" :key="item.id">
          <van-cell
            :title="item.categoryName"
            :label="`${item.occurredAt.replace('T', ' ')} · ${item.accountName} · ${item.note || '无备注'}`"
            :value="`${item.type === 'EXPENSE' ? '-' : '+'}¥${money(item.amount)}`"
            :value-class="item.type === 'EXPENSE' ? 'expense' : 'income'"
          />
          <template #right>
            <van-button square type="danger" text="删除" @click="removeRecord(item.id)" />
          </template>
        </van-swipe-cell>
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

