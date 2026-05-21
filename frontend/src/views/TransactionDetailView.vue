<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { categoryApi, paymentMethodApi, transactionApi } from '@/api/services'
import AmapPlaceField from '@/components/AmapPlaceField.vue'
import ModernDateField from '@/components/ModernDateField.vue'
import TransactionOptionFields from '@/components/TransactionOptionFields.vue'
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
const loading = ref(true)
const optionsLoading = ref(false)
const saving = ref(false)
const copying = ref(false)
const deleting = ref(false)

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
const detailTypeText = computed(() => record.value?.type === 'INCOME' ? '收入' : '支出')
const detailChannelText = computed(() => record.value?.channel === 'ONLINE' ? '线上' : '线下')
const detailPlaceLabel = computed(() => record.value?.channel === 'ONLINE' ? 'APP' : '地点')
const detailPlaceValue = computed(() => {
  if (!record.value) {
    return ''
  }
  return record.value.channel === 'ONLINE' ? record.value.onlineApp || '未填写' : record.value.offlinePlace || '未填写'
})
const editSubmitText = computed(() => (optionsLoading.value ? '正在加载选项' : '保存修改'))

function sortBySortOrder<T extends { id: number; sortOrder?: number }>(items: T[]) {
  return [...items].sort((left, right) => (left.sortOrder || 0) - (right.sortOrder || 0) || right.id - left.id)
}

function addCategoryOption(category: Category) {
  categories.value = sortBySortOrder([...categories.value.filter((item) => item.id !== category.id), category])
}

function addPaymentMethodOption(paymentMethod: PaymentMethod) {
  paymentMethods.value = sortBySortOrder([...paymentMethods.value.filter((item) => item.id !== paymentMethod.id), paymentMethod])
}

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
  loading.value = true
  try {
    const id = recordId()
    const nextRecord = await transactionApi.get(id)
    record.value = nextRecord
    if (!editMode.value) {
      fillForm(nextRecord)
    }
  } catch (error) {
    record.value = null
    showError(error, '记录详情加载失败')
  } finally {
    loading.value = false
  }
}

async function loadOptions() {
  if (categories.value.length > 0 && paymentMethods.value.length > 0) {
    return true
  }
  optionsLoading.value = true
  try {
    const [nextCategories, nextMethods] = await Promise.all([
      categoryApi.list(),
      paymentMethodApi.list()
    ])
    categories.value = nextCategories
    paymentMethods.value = nextMethods
    return true
  } catch (error) {
    showError(error, '选项加载失败')
    return false
  } finally {
    optionsLoading.value = false
  }
}

async function startEdit() {
  if (record.value) {
    const loaded = await loadOptions()
    if (!loaded) {
      return
    }
    fillForm(record.value)
  }
  editMode.value = true
}

function cancelEdit() {
  if (record.value) {
    fillForm(record.value)
  }
  editMode.value = false
}

function handleBack() {
  if (editMode.value) {
    cancelEdit()
    return
  }
  router.back()
}

function syncCategoryForType() {
  form.categoryId = filteredCategories.value[0]?.id
}

async function submit() {
  if (saving.value) return
  if (optionsLoading.value) {
    showToast('分类和支付方式加载中')
    return
  }
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
  if (copying.value) {
    return
  }
  copying.value = true
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
    await router.replace({
      path: `/records/${created.id}`,
      query: { ...route.query }
    })
    await load()
  } catch (error) {
    showError(error, '复制失败')
  } finally {
    copying.value = false
  }
}

async function createRecurringRule() {
  if (!record.value) {
    return
  }
  await router.push({
    path: '/recurring-rules/new',
    query: { sourceTransactionId: String(record.value.id) }
  })
}

async function removeRecord() {
  if (deleting.value) {
    return
  }
  try {
    await showConfirmDialog({ title: '删除记录', message: '确认删除这条记录？' })
  } catch {
    return
  }
  deleting.value = true
  try {
    await transactionApi.remove(recordId())
    showToast('已删除')
    await router.replace({
      path: '/records',
      query: { ...route.query }
    })
  } catch (error) {
    showError(error, '删除失败')
  } finally {
    deleting.value = false
  }
}

function displayDateTime(value: string) {
  return value.replace('T', ' ')
}

watch(() => form.type, ensureCategory)
onMounted(load)
</script>

<template>
  <main class="page">
    <van-nav-bar :title="editMode ? '编辑记录' : '记录详情'" left-arrow @click-left="handleBack" />
    <div class="page-content">
      <section v-if="loading" class="section panel detail-loading">
        <van-loading size="22px">正在加载记录</van-loading>
      </section>

      <template v-else-if="record && !editMode">
        <section :class="['section', 'detail-hero', record.type === 'EXPENSE' ? 'detail-hero-expense' : 'detail-hero-income']">
          <div class="detail-hero-top">
            <span class="detail-type-pill">{{ detailTypeText }}</span>
            <span class="detail-time">{{ displayDateTime(record.occurredAt) }}</span>
          </div>
          <div :class="['detail-amount', record.type === 'EXPENSE' ? 'expense' : 'income']">
            {{ record.type === 'EXPENSE' ? '-' : '+' }}¥{{ money(record.amount) }}
          </div>
          <div class="detail-title">{{ record.itemName || record.categoryName }}</div>
          <div class="detail-tags">
            <span>{{ record.categoryName }}</span>
            <span>{{ record.paymentMethodName }}</span>
          </div>
        </section>

        <section class="section detail-actions">
          <div class="detail-main-actions">
            <van-button block round type="primary" icon="edit" :loading="optionsLoading" @click="startEdit">
              编辑记录
            </van-button>
            <van-button block round plain type="primary" icon="description-o" :loading="copying" @click="copyRecord">
              复制为今日
            </van-button>
            <van-button block round plain type="primary" icon="replay" @click="createRecurringRule">
              设为周期
            </van-button>
          </div>
          <van-button class="detail-delete-button" block plain type="danger" icon="delete-o" :loading="deleting" @click="removeRecord">
            删除记录
          </van-button>
        </section>

        <section class="section panel detail-info-panel">
          <div class="detail-section-title">记录信息</div>
          <div class="detail-info-list">
            <div class="detail-info-row">
              <van-icon name="clock-o" />
              <div>
                <div class="detail-info-label">发生时间</div>
                <div class="detail-info-value">{{ displayDateTime(record.occurredAt) }}</div>
              </div>
            </div>
            <div class="detail-info-row">
              <van-icon name="apps-o" />
              <div>
                <div class="detail-info-label">分类</div>
                <div class="detail-info-value">{{ record.categoryName }}</div>
              </div>
            </div>
            <div class="detail-info-row">
              <van-icon name="balance-o" />
              <div>
                <div class="detail-info-label">支付方式</div>
                <div class="detail-info-value">{{ record.paymentMethodName }}</div>
              </div>
            </div>
            <div class="detail-info-row">
              <van-icon name="exchange" />
              <div>
                <div class="detail-info-label">渠道</div>
                <div class="detail-info-value">{{ detailChannelText }}</div>
              </div>
            </div>
            <div class="detail-info-row">
              <van-icon :name="record.channel === 'ONLINE' ? 'shopping-cart-o' : 'location-o'" />
              <div>
                <div class="detail-info-label">{{ detailPlaceLabel }}</div>
                <div class="detail-info-value">{{ detailPlaceValue }}</div>
              </div>
            </div>
            <div class="detail-info-row detail-note-row">
              <van-icon name="comment-o" />
              <div>
                <div class="detail-info-label">备注</div>
                <div class="detail-info-value detail-note">{{ record.note || '无备注' }}</div>
              </div>
            </div>
          </div>
        </section>
      </template>

      <van-form v-else-if="record" class="detail-edit-form" @submit="submit">
        <van-cell-group inset class="detail-edit-group">
          <van-field label="类型">
            <template #input>
              <van-radio-group v-model="form.type" direction="horizontal" @change="syncCategoryForType">
                <van-radio name="EXPENSE">支出</van-radio>
                <van-radio name="INCOME">收入</van-radio>
              </van-radio-group>
            </template>
          </van-field>
          <van-field
            v-model="form.amount"
            class="detail-edit-amount-field"
            label="金额"
            type="text"
            inputmode="decimal"
            placeholder="0.00"
            required
          />
          <van-field v-model="form.itemName" label="事项" placeholder="如冰棍、工资、泳镜" required />
          <TransactionOptionFields
            v-model:payment-method-id="form.paymentMethodId"
            v-model:category-id="form.categoryId"
            :payment-methods="paymentMethods"
            :categories="categories"
            :transaction-type="form.type"
            @payment-method-created="addPaymentMethodOption"
            @category-created="addCategoryOption"
          />
        </van-cell-group>

        <van-cell-group inset class="detail-edit-group">
          <div class="detail-edit-group-heading">补充信息</div>
          <ModernDateField v-model="form.occurredAt" mode="datetime" label="时间" title="选择发生时间" required />
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
            :placeholder="form.type === 'EXPENSE' ? '如淘宝、美团、京东' : '可选，如银行、公司系统'"
            :required="form.type === 'EXPENSE'"
          />
          <AmapPlaceField v-else v-model="form.offlinePlace" label="地点" required />
          <van-field v-model="form.note" label="备注" placeholder="可选" />
        </van-cell-group>

        <div class="detail-edit-spacer" />
        <div class="detail-edit-actions">
          <van-button
            block
            round
            type="primary"
            icon="success"
            native-type="submit"
            :loading="saving"
            :disabled="optionsLoading"
          >
            {{ editSubmitText }}
          </van-button>
          <van-button block round plain type="default" icon="cross" native-type="button" @click="cancelEdit">取消编辑</van-button>
        </div>
      </van-form>

      <section v-else class="section panel detail-empty">
        <div class="empty-text">记录不存在或已删除</div>
        <van-button round plain type="primary" icon="arrow-left" @click="$router.back()">返回上一页</van-button>
      </section>
    </div>
  </main>
</template>

<style scoped>
.detail-loading {
  display: grid;
  place-items: center;
  min-height: 180px;
}

.detail-hero {
  overflow: hidden;
  border-radius: var(--radius-card);
  padding: var(--space-16);
  background: var(--card-bg);
}

.detail-hero-expense {
  border-top: 4px solid var(--expense);
}

.detail-hero-income {
  border-top: 4px solid var(--income);
}

.detail-hero-top {
  display: flex;
  justify-content: space-between;
  gap: var(--space-12);
  align-items: center;
}

.detail-type-pill {
  display: inline-flex;
  align-items: center;
  min-height: 26px;
  padding: var(--space-0) var(--space-10);
  border-radius: var(--radius-pill);
  background: var(--card-bg-warm);
  color: var(--text-main);
  font-size: var(--font-size-meta);
  font-weight: 600;
}

.detail-time {
  min-width: 0;
  color: var(--text-secondary);
  font-size: var(--font-size-caption);
  line-height: var(--line-height-caption);
  text-align: right;
}

.detail-amount {
  margin-top: var(--space-14);
  font-size: var(--font-size-amount-large);
  font-weight: 700;
  line-height: var(--line-height-amount-large);
}

.detail-title {
  margin-top: var(--space-6);
  overflow-wrap: anywhere;
  color: var(--text-main);
  font-size: var(--font-size-panel-title);
  font-weight: 600;
  line-height: var(--line-height-panel-title);
}

.detail-tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-8);
  margin-top: var(--space-12);
}

.detail-tags span {
  max-width: 100%;
  min-height: 26px;
  padding: var(--space-4) var(--space-10);
  border-radius: var(--radius-pill);
  background: var(--primary-soft);
  color: var(--text-secondary);
  font-size: var(--font-size-caption);
  line-height: var(--line-height-caption);
}

.detail-info-panel {
  padding: var(--space-0);
}

.detail-section-title {
  padding: var(--space-14) var(--space-14) var(--space-10);
  color: var(--text-main);
  font-size: var(--font-size-body-strong);
  font-weight: 700;
}

.detail-info-list {
  border-top: 1px solid var(--border-warm);
}

.detail-info-row {
  display: grid;
  grid-template-columns: 22px minmax(0, 1fr);
  gap: var(--space-10);
  align-items: start;
  padding: var(--space-12) var(--space-14);
  border-bottom: 1px solid var(--border-warm);
}

.detail-info-row:last-child {
  border-bottom: 0;
}

.detail-info-row :deep(.van-icon) {
  margin-top: var(--space-2);
  color: var(--text-secondary);
  font-size: var(--icon-size-md);
}

.detail-info-label {
  color: var(--text-muted);
  font-size: var(--font-size-caption);
  line-height: var(--line-height-caption);
}

.detail-info-value {
  margin-top: var(--space-2);
  overflow-wrap: anywhere;
  color: var(--text-main);
  font-size: var(--font-size-body-strong);
  line-height: var(--line-height-body-strong);
}

.detail-note {
  white-space: pre-wrap;
}

.detail-actions {
  display: grid;
  gap: var(--space-10);
}

.detail-main-actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-10);
}

.detail-delete-button {
  border-color: transparent;
  background: transparent;
}

.detail-edit-form {
  margin: var(--space-0) calc(var(--space-12) * -1);
}

.detail-edit-group {
  margin-bottom: var(--space-12);
}

.detail-edit-amount-field :deep(.van-field__control) {
  font-size: var(--font-size-amount);
  font-weight: 700;
  line-height: var(--line-height-amount);
}

.detail-edit-group-heading {
  padding: var(--space-13) var(--space-16) var(--space-5);
  color: var(--text-main);
  font-size: var(--font-size-body-strong);
  font-weight: 700;
  line-height: var(--line-height-body-strong);
}

.detail-edit-spacer {
  height: 126px;
}

.detail-edit-actions {
  display: grid;
  gap: var(--space-10);
  position: fixed;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 20;
  padding: var(--space-10) var(--space-12) max(var(--space-10), env(safe-area-inset-bottom));
  border-top: 1px solid var(--border-warm);
  background: rgba(255, 250, 244, 0.96);
  backdrop-filter: blur(8px);
}

.detail-empty {
  display: grid;
  justify-items: center;
  gap: var(--space-12);
}

@media (max-width: 360px) {
  .detail-hero-top {
    display: grid;
    justify-items: start;
  }

  .detail-time {
    text-align: left;
  }

  .detail-amount {
    font-size: var(--font-size-amount-large);
    line-height: var(--line-height-amount-large);
  }

  .detail-main-actions {
    grid-template-columns: 1fr;
  }

  .detail-edit-actions {
    padding: var(--space-8) var(--space-10) max(var(--space-8), env(safe-area-inset-bottom));
  }
}
</style>
