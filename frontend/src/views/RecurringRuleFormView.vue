<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { categoryApi, paymentMethodApi, recurringRuleApi, transactionApi } from '@/api/services'
import AmapPlaceField from '@/components/AmapPlaceField.vue'
import ModernDateField from '@/components/ModernDateField.vue'
import ModernSelectField from '@/components/ModernSelectField.vue'
import PageSkeleton from '@/components/PageSkeleton.vue'
import TransactionOptionFields from '@/components/TransactionOptionFields.vue'
import type { Category, PaymentMethod, RecurringRule, RecurringRulePayload, TransactionRecord } from '@/types'
import {
  RECURRING_DAY_OPTIONS,
  RECURRING_INTERVAL_OPTIONS,
  RECURRING_REMINDER_OPTIONS,
  RECURRING_SCHEDULE_TYPE_OPTIONS,
  RECURRING_STATUS_OPTIONS,
  RECURRING_WEEKDAY_OPTIONS,
  ruleStatusLabel,
  scheduleSummary
} from '@/utils/recurring'
import { moneyError } from '@/utils/money'
import { showError } from '@/utils/errors'
import { todayDate } from '@/utils/date'
import { transactionTitle } from '@/utils/display'

const route = useRoute()
const router = useRouter()
const categories = ref<Category[]>([])
const paymentMethods = ref<PaymentMethod[]>([])
const loading = ref(true)
const saving = ref(false)
const ruleId = computed(() => Number(route.params.id || 0))
const sourceTransactionId = computed(() => Number(route.query.sourceTransactionId || 0))
const isEdit = computed(() => Boolean(ruleId.value))
const title = computed(() => (isEdit.value ? '编辑周期规则' : '新建周期规则'))
const submitText = computed(() => (isEdit.value ? '保存修改' : '创建规则'))
const intervalOptions = computed(() =>
  RECURRING_INTERVAL_OPTIONS.map((item) => ({
    label: form.scheduleType === 'MONTHLY' ? `每 ${item.value} 月` : `每 ${item.value} 周`,
    value: item.value
  }))
)
const filteredCategories = computed(() => {
  if (!form.type) {
    return categories.value
  }
  return categories.value.filter((item) => item.type === form.type)
})
const summaryText = computed(() => scheduleSummary(form))

const form = reactive({
  name: '',
  type: 'EXPENSE' as 'EXPENSE' | 'INCOME',
  itemName: '',
  amount: '',
  channel: 'OFFLINE' as 'ONLINE' | 'OFFLINE',
  onlineApp: '',
  offlinePlace: '',
  paymentMethodId: undefined as number | undefined,
  categoryId: undefined as number | undefined,
  note: '',
  scheduleType: 'MONTHLY' as 'MONTHLY' | 'WEEKLY',
  intervalValue: 1,
  dayOfMonth: new Date().getDate(),
  weekday: undefined as (typeof RECURRING_WEEKDAY_OPTIONS)[number]['value'] | undefined,
  startDate: todayDate(),
  endDate: '',
  reminderDaysBefore: 0,
  status: 'ACTIVE' as 'ACTIVE' | 'PAUSED'
})

function sortBySortOrder<T extends { id: number; sortOrder?: number }>(items: T[]) {
  return [...items].sort((left, right) => (left.sortOrder || 0) - (right.sortOrder || 0) || right.id - left.id)
}

function addCategoryOption(category: Category) {
  categories.value = sortBySortOrder([...categories.value.filter((item) => item.id !== category.id), category])
}

function addPaymentMethodOption(paymentMethod: PaymentMethod) {
  paymentMethods.value = sortBySortOrder([...paymentMethods.value.filter((item) => item.id !== paymentMethod.id), paymentMethod])
}

function normalizeCategory() {
  if (filteredCategories.value.some((item) => item.id === form.categoryId)) {
    return
  }
  form.categoryId = filteredCategories.value[0]?.id
}

function fillFromRule(rule: RecurringRule) {
  form.name = rule.name
  form.type = rule.type
  form.itemName = rule.itemName || ''
  form.amount = String(rule.amount)
  form.channel = rule.channel
  form.onlineApp = rule.onlineApp || ''
  form.offlinePlace = rule.offlinePlace || ''
  form.paymentMethodId = rule.paymentMethodId
  form.categoryId = rule.categoryId
  form.note = rule.note || ''
  form.scheduleType = rule.scheduleType
  form.intervalValue = rule.intervalValue
  form.dayOfMonth = rule.dayOfMonth || new Date().getDate()
  form.weekday = rule.weekday || undefined
  form.startDate = rule.startDate
  form.endDate = rule.endDate || ''
  form.reminderDaysBefore = rule.reminderDaysBefore
  form.status = rule.status
}

function fillFromTransaction(record: TransactionRecord) {
  const occurredDate = record.occurredAt.slice(0, 10)
  form.name = `${transactionTitle(record)} 周期`
  form.type = record.type
  form.itemName = record.itemName || ''
  form.amount = String(record.amount)
  form.channel = record.channel
  form.onlineApp = record.onlineApp || ''
  form.offlinePlace = record.offlinePlace || ''
  form.paymentMethodId = record.paymentMethodId
  form.categoryId = record.categoryId
  form.note = record.note || ''
  form.scheduleType = 'MONTHLY'
  form.intervalValue = 1
  form.dayOfMonth = Number(occurredDate.slice(8, 10))
  form.weekday = undefined
  form.startDate = occurredDate
  form.endDate = ''
  form.reminderDaysBefore = 0
  form.status = 'ACTIVE'
}

async function loadOptions() {
  const [nextCategories, nextMethods] = await Promise.all([
    categoryApi.list(),
    paymentMethodApi.list()
  ])
  categories.value = sortBySortOrder(nextCategories)
  paymentMethods.value = sortBySortOrder(nextMethods)
}

async function loadInitialData() {
  if (isEdit.value) {
    const rule = await recurringRuleApi.get(ruleId.value)
    fillFromRule(rule)
    return
  }
  if (sourceTransactionId.value > 0) {
    const record = await transactionApi.get(sourceTransactionId.value)
    fillFromTransaction(record)
  }
}

function buildPayload(): RecurringRulePayload {
  return {
    name: form.name.trim(),
    type: form.type,
    itemName: form.itemName.trim() || undefined,
    amount: Number(form.amount),
    channel: form.channel,
    onlineApp: form.channel === 'ONLINE' ? form.onlineApp.trim() || undefined : undefined,
    offlinePlace: form.channel === 'OFFLINE' ? form.offlinePlace.trim() || undefined : undefined,
    paymentMethodId: form.paymentMethodId as number,
    categoryId: form.categoryId as number,
    note: form.note.trim() || undefined,
    scheduleType: form.scheduleType,
    intervalValue: form.intervalValue,
    dayOfMonth: form.scheduleType === 'MONTHLY' ? form.dayOfMonth : undefined,
    weekday: form.scheduleType === 'WEEKLY' ? form.weekday : undefined,
    startDate: form.startDate,
    endDate: form.endDate || undefined,
    reminderDaysBefore: form.reminderDaysBefore,
    status: form.status
  }
}

async function submit() {
  if (saving.value) {
    return
  }
  if (!form.name.trim()) {
    showToast('请填写规则名称')
    return
  }
  const amountError = moneyError(form.amount)
  if (amountError) {
    showToast(amountError)
    return
  }
  if (!form.paymentMethodId || !form.categoryId) {
    showToast('请先创建分类和支付方式')
    return
  }
  if (form.channel === 'OFFLINE' && !form.offlinePlace.trim()) {
    showToast('线下规则需要填写地点')
    return
  }
  if (form.channel === 'ONLINE' && form.type === 'EXPENSE' && !form.onlineApp.trim()) {
    showToast('线上支出规则需要填写消费 APP')
    return
  }
  if (form.scheduleType === 'MONTHLY' && !form.dayOfMonth) {
    showToast('请选择每月日期')
    return
  }
  if (form.scheduleType === 'WEEKLY' && !form.weekday) {
    showToast('请选择每周星期')
    return
  }

  saving.value = true
  try {
    const payload = buildPayload()
    if (isEdit.value) {
      await recurringRuleApi.update(ruleId.value, payload)
      showToast('周期规则已更新')
    } else {
      await recurringRuleApi.create(payload)
      showToast('周期规则已创建')
    }
    await router.replace('/recurring-rules')
  } catch (error) {
    showError(error, isEdit.value ? '保存失败' : '创建失败')
  } finally {
    saving.value = false
  }
}

function handleTypeChange() {
  normalizeCategory()
}

function cancel() {
  router.back()
}

async function loadPage() {
  loading.value = true
  try {
    await Promise.all([loadOptions(), loadInitialData()])
    normalizeCategory()
  } catch (error) {
    showError(error, '页面加载失败')
  } finally {
    loading.value = false
  }
}

watch(() => form.type, handleTypeChange)
watch(() => form.channel, () => {
  if (form.channel === 'ONLINE' && form.type === 'EXPENSE' && !form.onlineApp) {
    form.onlineApp = ''
  }
})

onMounted(loadPage)
</script>

<template>
  <main class="page recurring-form-page">
    <van-nav-bar :title="title" left-arrow @click-left="cancel" />
    <div class="page-content">
      <section class="section panel recurring-form-summary">
        <div class="recurring-form-summary-title">{{ summaryText }}</div>
        <div class="recurring-form-summary-meta">
          <span>{{ ruleStatusLabel(form.status) }}</span>
          <span>{{ form.startDate }}</span>
          <span v-if="form.endDate">至 {{ form.endDate }}</span>
        </div>
      </section>

      <PageSkeleton v-if="loading" class="section" variant="form" :cards="2" :rows="4" />

      <van-form v-else class="recurring-form" @submit="submit">
        <van-cell-group inset class="recurring-form-group">
          <van-field v-model="form.name" label="规则名称" placeholder="如房租、工资、会员费" required />
          <van-field label="类型">
            <template #input>
              <van-radio-group v-model="form.type" direction="horizontal">
                <van-radio name="EXPENSE">支出</van-radio>
                <van-radio name="INCOME">收入</van-radio>
              </van-radio-group>
            </template>
          </van-field>
          <van-field v-model="form.itemName" label="事项" placeholder="如房租、工资、订阅" />
          <van-field
            v-model="form.amount"
            label="金额"
            type="text"
            inputmode="decimal"
            placeholder="0.00"
            required
          />
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

        <van-cell-group inset class="recurring-form-group">
          <div class="recurring-form-group-title">周期设置</div>
          <ModernSelectField
            v-model="form.scheduleType"
            label="周期类型"
            title="选择周期类型"
            :options="RECURRING_SCHEDULE_TYPE_OPTIONS"
            required
          />
          <ModernSelectField
            v-model="form.intervalValue"
            label="间隔"
            title="选择间隔"
            :options="intervalOptions"
            required
          />
          <ModernSelectField
            v-if="form.scheduleType === 'MONTHLY'"
            v-model="form.dayOfMonth"
            label="每月日期"
            title="选择每月日期"
            :options="RECURRING_DAY_OPTIONS"
            required
          />
          <ModernSelectField
            v-else
            v-model="form.weekday"
            label="每周星期"
            title="选择星期"
            :options="RECURRING_WEEKDAY_OPTIONS"
            required
          />
          <ModernDateField v-model="form.startDate" mode="date" label="开始日期" title="选择开始日期" required />
          <ModernDateField v-model="form.endDate" mode="date" label="结束日期" title="选择结束日期" />
          <ModernSelectField
            v-model="form.reminderDaysBefore"
            label="提前提醒"
            title="选择提醒天数"
            :options="RECURRING_REMINDER_OPTIONS"
            required
          />
          <ModernSelectField
            v-model="form.status"
            label="状态"
            title="选择状态"
            :options="RECURRING_STATUS_OPTIONS"
            required
          />
        </van-cell-group>

        <van-cell-group inset class="recurring-form-group">
          <div class="recurring-form-group-title">补充信息</div>
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

        <div class="recurring-form-spacer" />
        <div class="recurring-form-actions">
          <van-button
            block
            round
            type="primary"
            icon="success"
            native-type="submit"
            :loading="saving"
          >
            {{ submitText }}
          </van-button>
          <van-button block round plain type="default" icon="cross" native-type="button" @click="cancel">取消</van-button>
        </div>
      </van-form>
    </div>
  </main>
</template>

<style scoped>
.recurring-form-page {
  padding-bottom: var(--space-28);
}

.recurring-form-summary {
  display: grid;
  gap: var(--space-8);
}

.recurring-form-summary-title {
  color: var(--text-main);
  font-size: var(--font-size-panel-title);
  font-weight: 700;
  line-height: var(--line-height-panel-title);
}

.recurring-form-summary-meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-8);
  color: var(--text-secondary);
  font-size: var(--font-size-caption);
  line-height: var(--line-height-caption);
}

.recurring-form-loading {
  display: grid;
  place-items: center;
  min-height: 180px;
}

.recurring-form {
  display: grid;
  gap: var(--space-14);
}

.recurring-form-group {
  overflow: hidden;
}

.recurring-form-group-title {
  padding: var(--space-14) var(--space-16) var(--space-6);
  color: var(--text-main);
  font-size: var(--font-size-body-strong);
  font-weight: 700;
}

.recurring-form-spacer {
  height: 10px;
}

.recurring-form-actions {
  display: grid;
  gap: var(--space-10);
}
</style>
