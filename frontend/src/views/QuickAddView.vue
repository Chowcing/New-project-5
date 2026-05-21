<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { categoryApi, paymentMethodApi, transactionApi } from '@/api/services'
import AmapPlaceField from '@/components/AmapPlaceField.vue'
import ModernDateField from '@/components/ModernDateField.vue'
import TransactionOptionFields from '@/components/TransactionOptionFields.vue'
import type { Category, PaymentMethod, TransactionTemplate } from '@/types'
import { nowLocalInput, toBackendDateTime } from '@/utils/date'
import { showError } from '@/utils/errors'
import { moneyError } from '@/utils/money'

const router = useRouter()
const categories = ref<Category[]>([])
const paymentMethods = ref<PaymentMethod[]>([])
const templates = ref<TransactionTemplate[]>([])
const saving = ref(false)
const optionsLoading = ref(false)
const templatesLoading = ref(false)
const activeTemplateKey = ref('')
const contextRecommendationText = ref('')
const suppressDirty = ref(false)
let contextTimer: ReturnType<typeof setTimeout> | undefined
let contextRequestId = 0
const form = reactive({
  type: 'EXPENSE' as 'EXPENSE' | 'INCOME',
  itemName: '',
  amount: '',
  occurredAt: nowLocalInput(),
  channel: 'OFFLINE' as 'ONLINE' | 'OFFLINE',
  onlineApp: '',
  offlinePlace: '',
  paymentMethodId: undefined as number | undefined,
  categoryId: undefined as number | undefined,
  note: ''
})
const dirtyFields = reactive({
  amount: false,
  channel: false,
  onlineApp: false,
  offlinePlace: false,
  paymentMethodId: false,
  categoryId: false
})

const filteredCategories = computed(() => categories.value.filter((item) => item.type === form.type))
const submitText = computed(() => (optionsLoading.value ? '正在加载选项' : '保存记录'))

function sortBySortOrder<T extends { id: number; sortOrder?: number }>(items: T[]) {
  return [...items].sort((left, right) => (left.sortOrder || 0) - (right.sortOrder || 0) || right.id - left.id)
}

function addCategoryOption(category: Category) {
  categories.value = sortBySortOrder([...categories.value.filter((item) => item.id !== category.id), category])
}

function addPaymentMethodOption(paymentMethod: PaymentMethod) {
  paymentMethods.value = sortBySortOrder([...paymentMethods.value.filter((item) => item.id !== paymentMethod.id), paymentMethod])
}

async function loadOptions() {
  optionsLoading.value = true
  try {
    categories.value = await categoryApi.list()
    paymentMethods.value = await paymentMethodApi.list()
    suppressDirty.value = true
    form.categoryId = form.categoryId || filteredCategories.value[0]?.id
    form.paymentMethodId = form.paymentMethodId || paymentMethods.value[0]?.id
  } catch (error) {
    showError(error, '选项加载失败')
  } finally {
    suppressDirty.value = false
    optionsLoading.value = false
  }
}

async function loadRecommendations() {
  templatesLoading.value = true
  try {
    templates.value = await transactionApi.recommendations(5)
  } catch (error) {
    showError(error, '推荐模板加载失败')
  } finally {
    templatesLoading.value = false
  }
}

function applyTemplate(template: TransactionTemplate) {
  activeTemplateKey.value = templateKey(template)
  contextRecommendationText.value = ''
  suppressDirty.value = true
  form.type = template.type
  form.itemName = template.itemName
  form.amount = String(template.amount)
  form.occurredAt = nowLocalInput()
  form.channel = template.channel
  form.onlineApp = template.onlineApp || ''
  form.offlinePlace = template.offlinePlace || ''
  form.paymentMethodId = template.paymentMethodId
  form.categoryId = template.categoryId
  form.note = template.note || ''
  suppressDirty.value = false
  markTemplateFieldsDirty()
  showToast('已套用推荐模板')
}

function templateKey(template: TransactionTemplate) {
  return `${template.type}-${template.itemName}-${template.categoryId}-${template.paymentMethodId}-${template.channel}`
}

function syncCategoryForType() {
  activeTemplateKey.value = ''
  contextRecommendationText.value = ''
  dirtyFields.categoryId = false
  suppressDirty.value = true
  form.categoryId = filteredCategories.value[0]?.id
  suppressDirty.value = false
}

function markDirty(field: keyof typeof dirtyFields) {
  if (suppressDirty.value) return
  dirtyFields[field] = true
  activeTemplateKey.value = ''
  contextRecommendationText.value = ''
}

function markTemplateFieldsDirty() {
  dirtyFields.amount = true
  dirtyFields.channel = true
  dirtyFields.onlineApp = true
  dirtyFields.offlinePlace = true
  dirtyFields.paymentMethodId = true
  dirtyFields.categoryId = true
}

function clearContextTimer() {
  if (contextTimer) {
    clearTimeout(contextTimer)
    contextTimer = undefined
  }
}

function scheduleContextRecommendation() {
  clearContextTimer()
  if (!form.itemName.trim()) {
    contextRecommendationText.value = ''
    return
  }
  contextTimer = setTimeout(loadContextRecommendation, 400)
}

async function loadContextRecommendation() {
  const itemName = form.itemName.trim()
  if (!itemName) return
  const requestId = ++contextRequestId
  try {
    const suggestions = await transactionApi.contextRecommendations({
      itemName,
      type: form.type,
      channel: dirtyFields.channel ? form.channel : undefined,
      occurredAt: form.occurredAt ? toBackendDateTime(form.occurredAt) : undefined,
      limit: 3
    })
    if (requestId !== contextRequestId) return
    const suggestion = suggestions[0]
    if (!suggestion) {
      contextRecommendationText.value = ''
      return
    }
    applyContextSuggestion(suggestion)
  } catch (error) {
    if (requestId === contextRequestId) {
      contextRecommendationText.value = ''
    }
    console.warn('智能预填失败', error)
  }
}

function applyContextSuggestion(template: TransactionTemplate) {
  let changed = false
  suppressDirty.value = true
  if (!dirtyFields.amount) {
    form.amount = String(template.amount)
    changed = true
  }
  if (!dirtyFields.channel) {
    form.channel = template.channel
    changed = true
  }
  if (!dirtyFields.paymentMethodId && paymentMethods.value.some((item) => item.id === template.paymentMethodId)) {
    form.paymentMethodId = template.paymentMethodId
    changed = true
  }
  if (!dirtyFields.categoryId && filteredCategories.value.some((item) => item.id === template.categoryId)) {
    form.categoryId = template.categoryId
    changed = true
  }
  if (!dirtyFields.onlineApp && form.channel === 'ONLINE') {
    form.onlineApp = template.onlineApp || ''
    changed = true
  }
  if (!dirtyFields.offlinePlace && form.channel === 'OFFLINE') {
    form.offlinePlace = template.offlinePlace || ''
    changed = true
  }
  suppressDirty.value = false
  contextRecommendationText.value = changed ? `已按历史习惯预填：${template.reason}` : ''
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
    await transactionApi.create({
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
    showToast('记录已保存')
    await router.push('/records')
  } catch (error) {
    showError(error, '保存失败')
  } finally {
    saving.value = false
  }
}

async function init() {
  await Promise.all([loadOptions(), loadRecommendations()])
}

onMounted(init)
onBeforeUnmount(clearContextTimer)

watch(() => form.amount, () => markDirty('amount'), { flush: 'sync' })
watch(() => form.channel, () => markDirty('channel'), { flush: 'sync' })
watch(() => form.onlineApp, () => markDirty('onlineApp'), { flush: 'sync' })
watch(() => form.offlinePlace, () => markDirty('offlinePlace'), { flush: 'sync' })
watch(() => form.paymentMethodId, () => markDirty('paymentMethodId'), { flush: 'sync' })
watch(() => form.categoryId, () => markDirty('categoryId'), { flush: 'sync' })
watch(() => [form.itemName, form.type, form.channel, form.occurredAt], scheduleContextRecommendation)
</script>

<template>
  <main class="page quick-add-page">
    <van-nav-bar title="快速记一笔" />
    <div class="page-content quick-add-content">
      <section v-if="templatesLoading || templates.length || contextRecommendationText" class="section panel quick-recommendations">
        <div class="quick-section-header">
          <span>当前时段推荐</span>
          <van-loading v-if="templatesLoading" size="16px" />
        </div>
        <div v-if="contextRecommendationText" class="context-recommendation-hint">{{ contextRecommendationText }}</div>
        <div v-if="templates.length" class="recommendation-list">
          <button
            v-for="item in templates"
            :key="templateKey(item)"
            type="button"
            :class="['recommendation-card', activeTemplateKey === templateKey(item) ? 'recommendation-card-active' : '']"
            @click="applyTemplate(item)"
          >
            <span class="recommendation-card-top">
              <span class="recommendation-type">{{ item.type === 'EXPENSE' ? '支出' : '收入' }}</span>
              <span :class="['recommendation-amount', item.type === 'EXPENSE' ? 'expense' : 'income']">
                {{ item.type === 'EXPENSE' ? '-' : '+' }}¥{{ Number(item.amount).toFixed(2) }}
              </span>
            </span>
            <span class="recommendation-title">{{ item.itemName || item.categoryName }}</span>
            <span class="recommendation-meta">{{ item.categoryName }} · {{ item.paymentMethodName }}</span>
            <span class="recommendation-reason">{{ item.reason }}</span>
          </button>
        </div>
        <div v-else-if="templatesLoading" class="muted recommendation-loading">正在生成推荐...</div>
      </section>

      <van-form class="quick-add-form" @submit="submit">
        <van-cell-group inset class="quick-cell-group">
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
            class="quick-amount-field"
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

        <van-cell-group inset class="quick-cell-group quick-extra-group">
          <div class="quick-group-heading">补充信息</div>
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

        <div class="quick-submit-spacer" />
        <div class="quick-submit-bar">
          <van-button
            round
            block
            type="primary"
            icon="success"
            native-type="submit"
            :loading="saving"
            :disabled="optionsLoading"
          >
            {{ submitText }}
          </van-button>
        </div>
      </van-form>
    </div>
  </main>
</template>

<style scoped>
.quick-add-page {
  padding-bottom: 150px;
}

.quick-add-content {
  padding-bottom: 0;
}

.quick-recommendations {
  padding: 12px;
}

.quick-section-header {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: center;
  margin-bottom: 10px;
  color: var(--text-main);
  font-size: 15px;
  font-weight: 700;
  line-height: 22px;
}

.recommendation-list {
  display: flex;
  gap: 10px;
  overflow-x: auto;
  padding: 1px 1px 4px;
  scrollbar-width: none;
}

.context-recommendation-hint {
  margin: -2px 0 10px;
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 18px;
}

.recommendation-list::-webkit-scrollbar {
  display: none;
}

.recommendation-card {
  display: grid;
  flex: 0 0 190px;
  gap: 6px;
  min-height: 118px;
  padding: 10px;
  border: 1px solid var(--border-warm);
  border-radius: 8px;
  background: var(--card-bg);
  color: inherit;
  font: inherit;
  text-align: left;
}

.recommendation-card-active {
  border-color: var(--primary);
  background: var(--primary-soft);
  box-shadow: inset 0 0 0 1px var(--primary);
}

.recommendation-card-top {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  align-items: center;
}

.recommendation-type {
  flex: 0 0 auto;
  min-height: 22px;
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--card-bg-warm);
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 18px;
}

.recommendation-amount {
  overflow: hidden;
  font-size: 14px;
  font-weight: 700;
  line-height: 20px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recommendation-title {
  overflow: hidden;
  color: var(--text-main);
  font-size: 15px;
  font-weight: 700;
  line-height: 22px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recommendation-meta,
.recommendation-reason {
  overflow: hidden;
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recommendation-reason {
  color: var(--text-muted);
}

.recommendation-loading {
  padding: 8px 0 4px;
}

.quick-add-form {
  margin: 0 -12px;
}

.quick-cell-group {
  margin-bottom: 12px;
}

.quick-amount-field :deep(.van-field__control) {
  font-size: 24px;
  font-weight: 700;
  line-height: 32px;
}

.quick-group-heading {
  padding: 13px 16px 5px;
  color: var(--text-main);
  font-size: 15px;
  font-weight: 700;
  line-height: 22px;
}

.quick-submit-spacer {
  height: 78px;
}

.quick-submit-bar {
  position: fixed;
  right: 0;
  bottom: calc(50px + env(safe-area-inset-bottom));
  left: 0;
  z-index: 20;
  padding: 10px 12px;
  border-top: 1px solid var(--border-warm);
  background: rgba(255, 250, 244, 0.96);
  backdrop-filter: blur(8px);
}

@media (max-width: 360px) {
  .recommendation-card {
    flex-basis: 172px;
  }

  .quick-submit-bar {
    padding: 8px 10px;
  }
}

</style>
