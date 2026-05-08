<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { categoryApi, transactionApi } from '@/api/services'
import type { Category, TransactionRecord } from '@/types'
import { currentMonth, money, nowLocalInput, todayDate, toBackendDateTime } from '@/utils/date'
import { showError } from '@/utils/errors'

const categories = ref<Category[]>([])
const records = ref<TransactionRecord[]>([])
const router = useRouter()
const pageSize = 10
const total = ref(0)
const query = reactive({
  type: '' as '' | 'EXPENSE' | 'INCOME',
  startDate: `${currentMonth()}-01`,
  endDate: todayDate(),
  categoryId: '' as number | '',
  keyword: '',
  page: 1
})

const filteredCategories = computed(() => {
  if (!query.type) {
    return categories.value
  }
  return categories.value.filter((item) => item.type === query.type)
})

async function load(page = 1) {
  query.page = page
  try {
    const result = await transactionApi.list({
      type: query.type || undefined,
      startDate: query.startDate || undefined,
      endDate: query.endDate || undefined,
      categoryId: query.categoryId === '' ? undefined : query.categoryId,
      keyword: query.keyword || undefined,
      page: query.page,
      size: pageSize
    })
    records.value = result.records
    total.value = result.total
  } catch (error) {
    showError(error, '记录加载失败')
  }
}

async function init() {
  try {
    categories.value = await categoryApi.list()
  } catch (error) {
    showError(error, '分类加载失败')
  }
  await load(1)
}

async function removeRecord(id: number) {
  try {
    await showConfirmDialog({ title: '删除记录', message: '确认删除这条记录？' })
  } catch {
    return
  }
  try {
    await transactionApi.remove(id)
    showToast('已删除')
    const nextPage = records.value.length === 1 && query.page > 1 ? query.page - 1 : query.page
    await load(nextPage)
  } catch (error) {
    showError(error, '删除失败')
  }
}

async function copyRecord(item: TransactionRecord) {
  try {
    const created = await transactionApi.create({
      type: item.type,
      itemName: item.itemName,
      amount: Number(item.amount),
      occurredAt: toBackendDateTime(nowLocalInput()),
      channel: item.channel,
      onlineApp: item.channel === 'ONLINE' ? item.onlineApp : undefined,
      offlinePlace: item.channel === 'OFFLINE' ? item.offlinePlace : undefined,
      paymentMethodId: item.paymentMethodId,
      categoryId: item.categoryId,
      note: item.note
    })
    showToast('已复制为新记录')
    await routerPushRecord(created.id)
  } catch (error) {
    showError(error, '复制失败')
  }
}

async function routerPushRecord(id: number) {
  await router.push(`/records/${id}`)
}

function contextText(item: TransactionRecord) {
  const channel = item.channel === 'ONLINE' ? '线上' : '线下'
  const placeOrApp = item.channel === 'ONLINE' ? item.onlineApp : item.offlinePlace
  return [channel, placeOrApp, item.paymentMethodName].filter(Boolean).join(' · ')
}

watch(() => query.type, () => {
  if (query.categoryId !== '' && !filteredCategories.value.some((item) => item.id === query.categoryId)) {
    query.categoryId = ''
  }
})

onMounted(init)
</script>

<template>
  <main class="page">
    <van-nav-bar title="收支明细" />
    <div class="page-content">
      <section class="section panel">
        <van-field label="类型">
          <template #input>
            <select v-model="query.type" class="native-select" @change="load(1)">
              <option value="">全部</option>
              <option value="EXPENSE">支出</option>
              <option value="INCOME">收入</option>
            </select>
          </template>
        </van-field>
        <van-field label="分类">
          <template #input>
            <select v-model.number="query.categoryId" class="native-select" @change="load(1)">
              <option value="">全部分类</option>
              <option v-for="item in filteredCategories" :key="item.id" :value="item.id">{{ item.name }}</option>
            </select>
          </template>
        </van-field>
        <van-field v-model="query.startDate" type="date" label="开始" @change="load(1)" />
        <van-field v-model="query.endDate" type="date" label="结束" @change="load(1)" />
        <van-field v-model="query.keyword" label="搜索" placeholder="事项、备注、地点、APP、支付方式" @keyup.enter="load(1)">
          <template #button>
            <van-button size="small" type="primary" @click="load(1)">筛选</van-button>
          </template>
        </van-field>
      </section>

      <section class="section panel">
        <div v-if="total > 0" class="list-meta">共 {{ total }} 条记录</div>
        <div v-if="records.length === 0" class="empty-text">没有符合条件的记录</div>
        <van-swipe-cell v-for="item in records" :key="item.id">
          <van-cell
            is-link
            :title="item.itemName || item.categoryName"
            :label="`${item.occurredAt.replace('T', ' ')} · ${contextText(item)} · ${item.categoryName} · ${item.note || '无备注'}`"
            :value="`${item.type === 'EXPENSE' ? '-' : '+'}¥${money(item.amount)}`"
            :value-class="item.type === 'EXPENSE' ? 'expense' : 'income'"
            @click="$router.push(`/records/${item.id}`)"
          />
          <template #right>
            <van-button square type="primary" text="复制" @click.stop="copyRecord(item)" />
            <van-button square type="danger" text="删除" @click.stop="removeRecord(item.id)" />
          </template>
        </van-swipe-cell>
        <van-pagination
          v-if="total > pageSize"
          v-model="query.page"
          class="record-pagination"
          mode="simple"
          :total-items="total"
          :items-per-page="pageSize"
          @change="load"
        />
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

.list-meta {
  padding: 0 0 8px;
  color: #6b7280;
  font-size: 13px;
}

.record-pagination {
  margin-top: 12px;
}
</style>
