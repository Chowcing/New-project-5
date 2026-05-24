<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { categoryApi, paymentMethodApi, transactionApi } from '@/api/services'
import AmapPlaceField from '@/components/AmapPlaceField.vue'
import ModernDateField from '@/components/ModernDateField.vue'
import TransactionOptionFields from '@/components/TransactionOptionFields.vue'
import type { Category, PaymentMethod, TransactionTemplate } from '@/types'
import { nowLocalInput, toBackendDateTime } from '@/utils/date'
import { showError } from '@/utils/errors'
import { haptic } from '@/utils/haptics'
import { moneyError } from '@/utils/money'
import { resetRecordsQueryPreference } from '@/utils/preferences'
import { useVisualFeedback } from '@/utils/visualFeedback'

type TransactionType = 'EXPENSE' | 'INCOME'

const router = useRouter()
const route = useRoute()
const categories = ref<Category[]>([])
const paymentMethods = ref<PaymentMethod[]>([])
const templatesByType = reactive<Record<TransactionType, TransactionTemplate[]>>({
  EXPENSE: [],
  INCOME: []
})
const templatesLoaded = reactive<Record<TransactionType, boolean>>({
  EXPENSE: false,
  INCOME: false
})
const saving = ref(false)
const optionsLoading = ref(false)
const templatesLoading = ref(false)
const activeTemplateKey = ref('')
const contextRecommendationText = ref('')
const suppressDirty = ref(false)
const amountFieldRef = ref<{ focus: () => void } | null>(null)
const { visualFeedback, triggerVisualFeedback } = useVisualFeedback()
let contextTimer: ReturnType<typeof setTimeout> | undefined
let contextRequestId = 0
let recommendationsRequestId = 0
const form = reactive({
  type: initialTransactionType(),
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
const currentTemplates = computed(() => templatesByType[form.type].filter((item) => item.type === form.type))
const recommendationTitle = computed(() => `当前时段${form.type === 'EXPENSE' ? '支出' : '收入'}推荐`)
const submitText = computed(() => (optionsLoading.value ? '正在加载选项' : '保存记录'))

function initialTransactionType(): TransactionType {
  return route.query.type === 'INCOME' ? 'INCOME' : 'EXPENSE'
}

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

async function loadRecommendations(type: TransactionType = form.type, force = false) {
  if (!force && templatesLoaded[type]) return
  const requestId = ++recommendationsRequestId
  templatesLoading.value = true
  try {
    templatesByType[type] = await transactionApi.recommendations(5, type)
    templatesLoaded[type] = true
  } catch (error) {
    showError(error, '推荐模板加载失败')
  } finally {
    if (requestId === recommendationsRequestId) {
      templatesLoading.value = false
    }
  }
}

function applyTemplate(template: TransactionTemplate) {
  haptic('selection')
  triggerVisualFeedback('selection')
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
  haptic('selection')
  triggerVisualFeedback('selection')
  activeTemplateKey.value = ''
  contextRecommendationText.value = ''
  dirtyFields.categoryId = false
  suppressDirty.value = true
  form.categoryId = filteredCategories.value[0]?.id
  suppressDirty.value = false
  void loadRecommendations(form.type)
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
    haptic('warning')
    triggerVisualFeedback('warning')
    showToast('分类和支付方式加载中')
    return
  }
  if (!form.categoryId || !form.paymentMethodId) {
    haptic('warning')
    triggerVisualFeedback('warning')
    showToast('请先创建分类和支付方式')
    return
  }
  if (!form.itemName.trim()) {
    haptic('warning')
    triggerVisualFeedback('warning')
    showToast('请填写事项')
    return
  }
  const amountError = moneyError(form.amount)
  if (amountError) {
    haptic('warning')
    triggerVisualFeedback('warning')
    showToast(amountError)
    return
  }
  if (!form.occurredAt) {
    haptic('warning')
    triggerVisualFeedback('warning')
    showToast('请选择发生时间')
    return
  }
  if (form.channel === 'OFFLINE' && !form.offlinePlace.trim()) {
    haptic('warning')
    triggerVisualFeedback('warning')
    showToast('线下记录需要填写地点')
    return
  }
  if (form.channel === 'ONLINE' && form.type === 'EXPENSE' && !form.onlineApp.trim()) {
    haptic('warning')
    triggerVisualFeedback('warning')
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
    haptic('confirm')
    triggerVisualFeedback('confirm')
    showToast('记录已保存')
    resetRecordsQueryPreference()
    await new Promise((resolve) => window.setTimeout(resolve, 140))
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

async function focusAmountInput() {
  await nextTick()
  amountFieldRef.value?.focus()
}

onMounted(() => {
  void init()
  void focusAmountInput()
})
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
    <van-nav-bar title="记一笔" left-arrow @click-left="router.back()" />
    <div class="page-content quick-add-content">
      <van-form class="quick-add-form" @submit="submit">
        <section :class="['section', 'panel', 'quick-entry-panel', visualFeedback === 'warning' ? 'ui-feedback-warning' : '']">
          <div class="quick-entry-header">
            <div>
              <span class="quick-kicker">FAST ENTRY</span>
              <strong>{{ form.type === 'EXPENSE' ? '记录支出' : '记录收入' }}</strong>
            </div>
            <van-radio-group v-model="form.type" class="quick-type-switch" direction="horizontal" @change="syncCategoryForType">
              <van-radio name="EXPENSE">支出</van-radio>
              <van-radio name="INCOME">收入</van-radio>
            </van-radio-group>
          </div>

          <van-cell-group inset class="quick-cell-group quick-primary-group">
            <van-field
              ref="amountFieldRef"
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
        </section>

        <section v-if="templatesLoading || currentTemplates.length || contextRecommendationText" class="section panel quick-recommendations">
          <div class="quick-section-header">
            <span>{{ recommendationTitle }}</span>
            <van-loading v-if="templatesLoading" size="16px" />
          </div>
          <div v-if="contextRecommendationText" class="context-recommendation-hint">{{ contextRecommendationText }}</div>
          <div v-if="currentTemplates.length" class="recommendation-list">
            <button
              v-for="item in currentTemplates"
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

        <section class="section panel quick-extra-panel">
          <div class="quick-group-heading">补充信息</div>
          <van-cell-group inset class="quick-cell-group">
            <ModernDateField v-model="form.occurredAt" mode="datetime" label="时间" title="选择发生时间" required />
            <van-field label="渠道">
              <template #input>
                <van-radio-group v-model="form.channel" class="quick-channel-switch" direction="horizontal">
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
        </section>

        <div class="quick-submit-spacer" />
        <div :class="['quick-submit-bar', visualFeedback === 'confirm' ? 'ui-feedback-confirm' : '']">
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
  padding-bottom: var(--space-150);
}

.quick-add-content,
.quick-add-form {
  display: grid;
  gap: var(--space-12);
}

.quick-entry-panel {
  display: grid;
  gap: var(--space-12);
  padding: var(--space-14);
  background:
    radial-gradient(circle at 88% 4%, rgba(var(--theme-primary-glow-rgb), 0.22), transparent 36%),
    var(--card-bg);
}

.quick-entry-header {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: var(--space-12);
  align-items: center;
}

.quick-entry-header strong {
  display: block;
  margin-top: var(--space-3);
  font-size: var(--font-size-panel-title);
  line-height: var(--line-height-panel-title);
}

.quick-kicker {
  color: var(--primary);
  font-size: var(--font-size-caption);
  font-weight: 750;
  line-height: var(--line-height-caption);
}

.quick-type-switch,
.quick-channel-switch {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-4);
  min-width: 132px;
  padding: var(--space-4);
  border-radius: var(--radius-pill);
  background: rgba(var(--theme-border-warm-rgb), 0.1);
}

.quick-type-switch :deep(.van-radio),
.quick-channel-switch :deep(.van-radio) {
  display: flex;
  justify-content: center;
  min-height: 30px;
  margin: 0;
  padding: 0 var(--space-10);
  border-radius: var(--radius-pill);
  color: var(--text-secondary);
  font-size: var(--font-size-caption);
  font-weight: 700;
}

.quick-type-switch :deep(.van-radio__icon),
.quick-channel-switch :deep(.van-radio__icon) {
  display: none;
}

.quick-type-switch :deep(.van-radio__label),
.quick-channel-switch :deep(.van-radio__label) {
  margin: 0;
}

.quick-type-switch :deep(.van-radio[aria-checked='true']),
.quick-channel-switch :deep(.van-radio[aria-checked='true']) {
  background: var(--glass-strong-bg);
  color: var(--primary);
  box-shadow: 0 8px 18px rgba(var(--theme-shadow-warm-rgb), 0.18);
}

.quick-primary-group,
.quick-cell-group {
  margin: 0;
}

.quick-add-form :deep(.van-cell-group--inset) {
  border-radius: var(--radius-card);
}

.quick-add-form :deep(.van-field__control) {
  font-size: var(--font-size-section-title);
  line-height: var(--line-height-body);
}

.quick-amount-field :deep(.van-field__control) {
  font-size: var(--font-size-amount);
  font-weight: 780;
  line-height: var(--line-height-amount);
}

.quick-recommendations {
  padding: var(--space-12);
}

.quick-section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-10);
  margin-bottom: var(--space-10);
  color: var(--text-main);
  font-size: var(--font-size-body-strong);
  font-weight: 700;
  line-height: var(--line-height-body-strong);
}

.recommendation-list {
  display: flex;
  gap: var(--space-10);
  overflow-x: auto;
  padding: var(--space-1) var(--space-1) var(--space-4);
  scrollbar-width: none;
}

.recommendation-list::-webkit-scrollbar {
  display: none;
}

.context-recommendation-hint {
  margin: calc(var(--space-2) * -1) var(--space-0) var(--space-10);
  color: var(--text-secondary);
  font-size: var(--font-size-caption);
  line-height: var(--line-height-caption);
}

.recommendation-card {
  display: grid;
  flex: 0 0 176px;
  gap: var(--space-6);
  min-height: 112px;
  padding: var(--space-10);
  border: 1px solid rgba(var(--theme-border-warm-rgb), 0.22);
  border-radius: var(--radius-card);
  background: rgba(var(--theme-border-warm-rgb), 0.08);
  color: inherit;
  font: inherit;
  text-align: left;
}

.recommendation-card-active {
  border-color: var(--primary);
  background: var(--primary-soft);
  box-shadow: inset 0 0 0 1px rgba(var(--theme-primary-glow-rgb), 0.28);
}

.recommendation-card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-8);
}

.recommendation-type {
  flex: 0 0 auto;
  min-height: 22px;
  padding: var(--space-2) var(--space-8);
  border-radius: var(--radius-pill);
  background: rgba(var(--theme-border-warm-rgb), 0.1);
  color: var(--text-secondary);
  font-size: var(--font-size-caption);
  line-height: var(--line-height-caption);
}

.recommendation-amount {
  overflow: hidden;
  font-size: var(--font-size-body);
  font-weight: 750;
  line-height: var(--line-height-body);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recommendation-title,
.recommendation-meta {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recommendation-title {
  color: var(--text-main);
  font-size: var(--font-size-body-strong);
  font-weight: 700;
  line-height: var(--line-height-body-strong);
}

.recommendation-meta,
.recommendation-reason {
  color: var(--text-secondary);
  font-size: var(--font-size-caption);
  line-height: var(--line-height-caption);
}

.recommendation-reason {
  overflow-wrap: anywhere;
  white-space: normal;
}

.recommendation-loading {
  padding: var(--space-8) var(--space-0) var(--space-4);
}

.quick-extra-panel {
  padding: var(--space-0);
  overflow: hidden;
}

.quick-group-heading {
  padding: var(--space-13) var(--space-16) var(--space-5);
  color: var(--text-main);
  font-size: var(--font-size-body-strong);
  font-weight: 700;
  line-height: var(--line-height-body-strong);
}

.quick-submit-spacer {
  height: 82px;
}

.quick-submit-bar {
  position: fixed;
  right: 50%;
  bottom: env(safe-area-inset-bottom);
  left: auto;
  z-index: 40;
  width: min(100%, var(--app-max-width));
  padding: var(--space-10) var(--space-12) max(var(--space-10), env(safe-area-inset-bottom));
  transform: translateX(50%);
}

@media (max-width: 360px) {
  .quick-entry-header {
    grid-template-columns: 1fr;
  }

  .quick-type-switch {
    width: 100%;
  }

  .recommendation-card {
    flex-basis: 164px;
  }

  .quick-submit-bar {
    padding-right: var(--space-10);
    padding-left: var(--space-10);
  }
}
</style>
