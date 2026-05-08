<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter, type LocationQueryValue } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { categoryApi, paymentMethodApi, transactionApi } from '@/api/services'
import type { Category, PaymentMethod, TransactionRecord } from '@/types'
import { currentMonth, money, nowLocalInput, todayDate, toBackendDateTime } from '@/utils/date'
import { showError } from '@/utils/errors'

const categories = ref<Category[]>([])
const paymentMethods = ref<PaymentMethod[]>([])
const records = ref<TransactionRecord[]>([])
const route = useRoute()
const router = useRouter()
const pageSize = 10
const total = ref(0)
const query = reactive({
  type: '' as '' | 'EXPENSE' | 'INCOME',
  startDate: `${currentMonth()}-01`,
  endDate: todayDate(),
  channel: '' as '' | 'ONLINE' | 'OFFLINE',
  categoryId: '' as number | '',
  paymentMethodId: '' as number | '',
  keyword: '',
  page: 1
})

const filteredCategories = computed(() => {
  if (!query.type) {
    return categories.value
  }
  return categories.value.filter((item) => item.type === query.type)
})

function firstQueryValue(value: LocationQueryValue | LocationQueryValue[]) {
  return Array.isArray(value) ? value[0] : value
}

function isTransactionType(value: LocationQueryValue): value is 'EXPENSE' | 'INCOME' {
  return value === 'EXPENSE' || value === 'INCOME'
}

function isTransactionChannel(value: LocationQueryValue): value is 'ONLINE' | 'OFFLINE' {
  return value === 'ONLINE' || value === 'OFFLINE'
}

function isDateString(value: LocationQueryValue): value is string {
  return typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value)
}

function positiveInteger(value: LocationQueryValue) {
  if (typeof value !== 'string' || !/^\d+$/.test(value)) {
    return undefined
  }
  const numberValue = Number(value)
  return numberValue > 0 ? numberValue : undefined
}

function applyRouteQuery() {
  const type = firstQueryValue(route.query.type)
  const startDate = firstQueryValue(route.query.startDate)
  const endDate = firstQueryValue(route.query.endDate)
  const channel = firstQueryValue(route.query.channel)
  const categoryId = positiveInteger(firstQueryValue(route.query.categoryId))
  const paymentMethodId = positiveInteger(firstQueryValue(route.query.paymentMethodId))
  const keyword = firstQueryValue(route.query.keyword)
  const page = positiveInteger(firstQueryValue(route.query.page))

  query.type = isTransactionType(type) ? type : ''
  query.startDate = isDateString(startDate) ? startDate : `${currentMonth()}-01`
  query.endDate = isDateString(endDate) ? endDate : todayDate()
  query.channel = isTransactionChannel(channel) ? channel : ''
  query.categoryId = categoryId ?? ''
  query.paymentMethodId = paymentMethodId ?? ''
  query.keyword = typeof keyword === 'string' ? keyword : ''
  query.page = page ?? 1
}

async function load(page = 1) {
  query.page = page
  try {
    const result = await transactionApi.list({
      type: query.type || undefined,
      startDate: query.startDate || undefined,
      endDate: query.endDate || undefined,
      channel: query.channel || undefined,
      categoryId: query.categoryId === '' ? undefined : query.categoryId,
      paymentMethodId: query.paymentMethodId === '' ? undefined : query.paymentMethodId,
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
  applyRouteQuery()
  try {
    const [categoryRows, paymentMethodRows] = await Promise.all([
      categoryApi.list(),
      paymentMethodApi.list()
    ])
    categories.value = categoryRows
    paymentMethods.value = paymentMethodRows
  } catch (error) {
    showError(error, '筛选项加载失败')
  }
  await load(query.page)
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
  if (categories.value.length === 0) {
    return
  }
  if (query.categoryId !== '' && !filteredCategories.value.some((item) => item.id === query.categoryId)) {
    query.categoryId = ''
  }
})

watch(() => route.query, async () => {
  applyRouteQuery()
  await load(query.page)
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
        <van-field label="渠道">
          <template #input>
            <select v-model="query.channel" class="native-select" @change="load(1)">
              <option value="">全部渠道</option>
              <option value="ONLINE">线上</option>
              <option value="OFFLINE">线下</option>
            </select>
          </template>
        </van-field>
        <van-field label="支付方式">
          <template #input>
            <select v-model.number="query.paymentMethodId" class="native-select" @change="load(1)">
              <option value="">全部支付方式</option>
              <option v-for="item in paymentMethods" :key="item.id" :value="item.id">{{ item.name }}</option>
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
