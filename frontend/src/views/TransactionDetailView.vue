<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { categoryApi, paymentMethodApi, transactionApi } from '@/api/services'
import type { Category, PaymentMethod, TransactionRecord } from '@/types'
import { money, nowLocalInput, toBackendDateTime, toDateTimeLocal } from '@/utils/date'
import { showError } from '@/utils/errors'
import { moneyError } from '@/utils/money'

const route = useRoute()
const router = useRouter()
const record = ref<TransactionRecord | null>(null)
const categories = ref<Category[]>([])
const paymentMethods = ref<PaymentMethod[]>([])
const editMode = ref(false)
const saving = ref(false)

const form = reactive({
  type: 'EXPENSE' as 'EXPENSE' | 'INCOME',
  itemName: '',
  amount: '',
  occurredAt: '',
  channel: 'OFFLINE' as 'ONLINE' | 'OFFLINE',
  onlineApp: '',
  offlinePlace: '',
  paymentMethodId: undefined as number | undefined,
  categoryId: undefined as number | undefined,
  note: ''
})

const filteredCategories = computed(() => categories.value.filter((item) => item.type === form.type))

function recordId() {
  return Number(route.params.id)
}

function fillForm(item: TransactionRecord) {
  form.type = item.type
  form.itemName = item.itemName
  form.amount = String(item.amount)
  form.occurredAt = toDateTimeLocal(item.occurredAt)
  form.channel = item.channel
  form.onlineApp = item.onlineApp || ''
  form.offlinePlace = item.offlinePlace || ''
  form.paymentMethodId = item.paymentMethodId
  form.categoryId = item.categoryId
  form.note = item.note || ''
}

function ensureCategory() {
  if (!filteredCategories.value.some((item) => item.id === form.categoryId)) {
    form.categoryId = filteredCategories.value[0]?.id
  }
}

async function load() {
  try {
    const id = recordId()
    const [nextRecord, nextCategories, nextMethods] = await Promise.all([
      transactionApi.get(id),
      categoryApi.list(),
      paymentMethodApi.list()
    ])
    record.value = nextRecord
    categories.value = nextCategories
    paymentMethods.value = nextMethods
    if (!editMode.value) {
      fillForm(nextRecord)
    }
  } catch (error) {
    showError(error, '记录详情加载失败')
  }
}

function startEdit() {
  if (record.value) {
    fillForm(record.value)
  }
  editMode.value = true
}

async function submit() {
  if (saving.value) return
  if (!form.categoryId || !form.paymentMethodId) {
    showToast('请先创建分类和支付方式')
    return
  }
  if (!form.itemName.trim()) {
    showToast('请填写事项')
    return
  }
  const amountError = moneyError(form.amount)
  if (amountError) {
    showToast(amountError)
    return
  }
  if (!form.occurredAt) {
    showToast('请选择发生时间')
    return
  }
  if (form.channel === 'OFFLINE' && !form.offlinePlace.trim()) {
    showToast('线下记录需要填写地点')
    return
  }
  if (form.channel === 'ONLINE' && form.type === 'EXPENSE' && !form.onlineApp.trim()) {
    showToast('线上支出需要填写消费 APP')
    return
  }
  saving.value = true
  try {
    await transactionApi.update(recordId(), {
      type: form.type,
      itemName: form.itemName.trim(),
      amount: Number(form.amount),
      occurredAt: toBackendDateTime(form.occurredAt),
      channel: form.channel,
      onlineApp: form.channel === 'ONLINE' ? form.onlineApp.trim() : undefined,
      offlinePlace: form.channel === 'OFFLINE' ? form.offlinePlace.trim() : undefined,
      paymentMethodId: form.paymentMethodId,
      categoryId: form.categoryId,
      note: form.note.trim() || undefined
    })
    showToast('记录已更新')
    editMode.value = false
    await load()
  } catch (error) {
    showError(error, '更新失败')
  } finally {
    saving.value = false
  }
}

async function copyRecord() {
  if (!record.value) {
    return
  }
  const item = record.value
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
    await router.replace(`/records/${created.id}`)
    await load()
  } catch (error) {
    showError(error, '复制失败')
  }
}

async function removeRecord() {
  try {
    await showConfirmDialog({ title: '删除记录', message: '确认删除这条记录？' })
  } catch {
    return
  }
  try {
    await transactionApi.remove(recordId())
    showToast('已删除')
    await router.replace('/records')
  } catch (error) {
    showError(error, '删除失败')
  }
}

function contextText(item: TransactionRecord) {
  const channel = item.channel === 'ONLINE' ? '线上' : '线下'
  const placeOrApp = item.channel === 'ONLINE' ? item.onlineApp : item.offlinePlace
  return [channel, placeOrApp, item.paymentMethodName].filter(Boolean).join(' · ')
}

watch(() => form.type, ensureCategory)
onMounted(load)
</script>

<template>
  <main class="page">
    <van-nav-bar title="记录详情" left-arrow @click-left="$router.back()" />
    <div v-if="record" class="page-content">
      <template v-if="!editMode">
        <section class="section panel detail-summary">
          <div class="muted">{{ record.type === 'EXPENSE' ? '支出' : '收入' }}</div>
          <div :class="['detail-amount', record.type === 'EXPENSE' ? 'expense' : 'income']">
            {{ record.type === 'EXPENSE' ? '-' : '+' }}¥{{ money(record.amount) }}
          </div>
          <div class="detail-title">{{ record.itemName || record.categoryName }}</div>
        </section>

        <section class="section panel">
          <van-cell title="发生时间" :value="record.occurredAt.replace('T', ' ')" />
          <van-cell title="分类" :value="record.categoryName" />
          <van-cell title="渠道" :value="contextText(record)" />
          <van-cell title="备注" :value="record.note || '无备注'" />
        </section>

        <section class="section detail-actions">
          <van-button block round type="primary" icon="edit" @click="startEdit">编辑记录</van-button>
          <van-button block round plain type="primary" icon="description-o" @click="copyRecord">复制为今日记录</van-button>
          <van-button block round plain type="danger" icon="delete-o" @click="removeRecord">删除记录</van-button>
        </section>
      </template>

      <van-form v-else @submit="submit">
        <section class="section panel">
          <van-field label="类型">
            <template #input>
              <van-radio-group v-model="form.type" direction="horizontal">
                <van-radio name="EXPENSE">支出</van-radio>
                <van-radio name="INCOME">收入</van-radio>
              </van-radio-group>
            </template>
          </van-field>
          <van-field v-model="form.itemName" label="事项" required />
          <van-field v-model="form.amount" label="金额" type="text" inputmode="decimal" placeholder="0.00" required />
          <van-field v-model="form.occurredAt" label="时间" type="datetime-local" required />
          <van-field label="渠道">
            <template #input>
              <van-radio-group v-model="form.channel" direction="horizontal">
                <van-radio name="ONLINE">线上</van-radio>
                <van-radio name="OFFLINE">线下</van-radio>
              </van-radio-group>
            </template>
          </van-field>
          <van-field
            v-if="form.channel === 'ONLINE'"
            v-model="form.onlineApp"
            label="APP"
            :required="form.type === 'EXPENSE'"
          />
          <van-field v-else v-model="form.offlinePlace" label="地点" required />
          <van-field label="支付方式">
            <template #input>
              <select v-model.number="form.paymentMethodId" class="native-select">
                <option v-for="item in paymentMethods" :key="item.id" :value="item.id">{{ item.name }}</option>
              </select>
            </template>
          </van-field>
          <van-field label="分类">
            <template #input>
              <select v-model.number="form.categoryId" class="native-select">
                <option v-for="item in filteredCategories" :key="item.id" :value="item.id">{{ item.name }}</option>
              </select>
            </template>
          </van-field>
          <van-field v-model="form.note" label="备注" placeholder="可选" />
        </section>
        <section class="section detail-actions">
          <van-button block round type="primary" native-type="submit" :loading="saving">保存修改</van-button>
          <van-button block round plain type="default" native-type="button" @click="editMode = false">取消</van-button>
        </section>
      </van-form>
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

.detail-summary {
  text-align: center;
}

.detail-amount {
  margin-top: 8px;
  font-size: 30px;
  font-weight: 700;
}

.detail-title {
  margin-top: 8px;
  font-size: 16px;
  font-weight: 600;
}

.detail-actions {
  display: grid;
  gap: 10px;
}
</style>
