<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import type { UploaderFileListItem } from 'vant'
import { categoryApi, onlinePlatformApi, paymentMethodApi, transactionApi } from '@/api/services'
import AmapPlaceField from '@/components/AmapPlaceField.vue'
import ModernDateField from '@/components/ModernDateField.vue'
import TransactionOptionFields from '@/components/TransactionOptionFields.vue'
import type { Category, OnlinePlatform, PaymentMethod, QuickEntryRecommendations, TransactionTemplate } from '@/types'
import { nowLocalInput, toBackendDateTime } from '@/utils/date'
import { showError } from '@/utils/errors'
import { haptic } from '@/utils/haptics'
import { moneyError } from '@/utils/money'
import { transactionTitle } from '@/utils/display'
import { resetRecordsQueryPreference } from '@/utils/preferences'
import { useVisualFeedback } from '@/utils/visualFeedback'

type TransactionType = 'EXPENSE' | 'INCOME'
const MAX_TRANSACTION_IMAGES = 3
const MAX_TRANSACTION_IMAGE_SIZE = 3 * 1024 * 1024
const ALLOWED_TRANSACTION_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp']

const router = useRouter()
const route = useRoute()
const categories = ref<Category[]>([])
const paymentMethods = ref<PaymentMethod[]>([])
const onlinePlatforms = ref<OnlinePlatform[]>([])
const quickRecommendations = ref<QuickEntryRecommendations | null>(null)
const entryMode = ref<'minimal' | 'advanced'>('minimal')
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
const quickRecommendationsLoading = ref(false)
const activeTemplateKey = ref('')
const contextRecommendationText = ref('')
const suppressDirty = ref(false)
const amountFieldRef = ref<{ focus: () => void } | null>(null)
const imageFiles = ref<UploaderFileListItem[]>([])
const categoryChipGridRef = ref<HTMLElement | null>(null)
const paymentChipGridRef = ref<HTMLElement | null>(null)
const platformChipGridRef = ref<HTMLElement | null>(null)
const categoryPopup = ref(false)
const paymentPopup = ref(false)
const platformPopup = ref(false)
const categorySearch = ref('')
const paymentSearch = ref('')
const platformSearch = ref('')
const newCategoryName = ref('')
const newPaymentMethodName = ref('')
const newPlatformName = ref('')
const creatingCategory = ref(false)
const creatingPaymentMethod = ref(false)
const creatingPlatform = ref(false)
const { visualFeedback, triggerVisualFeedback } = useVisualFeedback()
let contextTimer: ReturnType<typeof setTimeout> | undefined
let contextRequestId = 0
let recommendationsRequestId = 0
const form = reactive({
  type: initialTransactionType(),
  itemName: '',
  amount: '',
  occurredAt: nowLocalInput(),
  channel: 'ONLINE' as 'ONLINE' | 'OFFLINE',
  onlineApp: '',
  onlinePlatformId: undefined as number | undefined,
  offlinePlace: '',
  paymentMethodId: undefined as number | undefined,
  categoryId: undefined as number | undefined,
  note: ''
})
const dirtyFields = reactive({
  amount: false,
  channel: false,
  onlineApp: false,
  onlinePlatformId: false,
  offlinePlace: false,
  paymentMethodId: false,
  categoryId: false
})

const filteredCategories = computed(() => categories.value.filter((item) => item.type === form.type))
const currentTemplates = computed(() => templatesByType[form.type].filter((item) => item.type === form.type))
const recommendationTitle = computed(() => `当前时段${form.type === 'EXPENSE' ? '支出' : '收入'}推荐`)
const submitText = computed(() => (optionsLoading.value ? '正在加载选项' : '保存记录'))
const quickCategoryCandidates = computed(() => rankedCategories().slice(0, 10))
const selectedCategory = computed(() => categories.value.find((item) => item.id === form.categoryId))
const visibleQuickCategoryCandidates = computed(() => withSelectedOption(quickCategoryCandidates.value, selectedCategory.value, 10))
const quickPaymentCandidates = computed(() => rankedPaymentMethods().slice(0, 10))
const quickPlatformCandidates = computed(() => rankedOnlinePlatforms().slice(0, 10))
const quickCombinations = computed(() => (quickRecommendations.value?.combinations || []).filter((item) => item.type === form.type).slice(0, 6))
const selectedPaymentMethod = computed(() => paymentMethods.value.find((item) => item.id === form.paymentMethodId))
const visibleQuickPaymentCandidates = computed(() => withSelectedOption(quickPaymentCandidates.value, selectedPaymentMethod.value, 10))
const selectedOnlinePlatform = computed(() => onlinePlatforms.value.find((item) => item.id === form.onlinePlatformId))
const visibleQuickPlatformCandidates = computed(() => withSelectedOption(quickPlatformCandidates.value, selectedOnlinePlatform.value, 10))
const filteredCategorySearchOptions = computed(() => filterByName(filteredCategories.value, categorySearch.value))
const filteredPaymentSearchOptions = computed(() => filterByName(paymentMethods.value, paymentSearch.value))
const filteredPlatformSearchOptions = computed(() => filterByName(onlinePlatforms.value, platformSearch.value))
const minimalTitle = computed(() => form.type === 'EXPENSE' ? '极简支出' : '极简收入')

function initialTransactionType(): TransactionType {
  return route.query.type === 'INCOME' ? 'INCOME' : 'EXPENSE'
}

function sortBySortOrder<T extends { id: number; sortOrder?: number }>(items: T[]) {
  return [...items].sort((left, right) => (Number(Boolean((right as { pinned?: boolean }).pinned)) - Number(Boolean((left as { pinned?: boolean }).pinned))) || (left.sortOrder || 0) - (right.sortOrder || 0) || right.id - left.id)
}

function normalizeName(value: string) {
  return value.trim().toLowerCase()
}

function filterByName<T extends { name: string }>(items: T[], keyword: string) {
  const query = normalizeName(keyword)
  if (!query) return items
  return items.filter((item) => normalizeName(item.name).includes(query))
}

function rankedCategories() {
  const recommended = (quickRecommendations.value?.categories || []).filter((item) => item.type === form.type)
  return mergeRankedOptions(recommended, filteredCategories.value)
}

function rankedPaymentMethods() {
  return mergeRankedOptions(quickRecommendations.value?.paymentMethods || [], paymentMethods.value)
}

function rankedOnlinePlatforms() {
  return mergeRankedOptions(quickRecommendations.value?.onlinePlatforms || [], onlinePlatforms.value)
}

function mergeRankedOptions<T extends { id: number }>(primary: T[], fallback: T[]) {
  const ids = new Set<number>()
  const merged: T[] = []
  for (const item of [...primary, ...fallback]) {
    if (!ids.has(item.id)) {
      ids.add(item.id)
      merged.push(item)
    }
  }
  return merged
}

function withSelectedOption<T extends { id: number }>(items: T[], selected: T | undefined, limit: number) {
  if (!selected || items.some((item) => item.id === selected.id)) {
    return items
  }
  return [selected, ...items.filter((item) => item.id !== selected.id)].slice(0, limit)
}

function addCategoryOption(category: Category) {
  categories.value = sortBySortOrder([...categories.value.filter((item) => item.id !== category.id), category])
}

function addPaymentMethodOption(paymentMethod: PaymentMethod) {
  paymentMethods.value = sortBySortOrder([...paymentMethods.value.filter((item) => item.id !== paymentMethod.id), paymentMethod])
}

function addOnlinePlatformOption(platform: OnlinePlatform) {
  onlinePlatforms.value = sortBySortOrder([...onlinePlatforms.value.filter((item) => item.id !== platform.id), platform])
}

function nextSortOrder(items: Array<{ sortOrder?: number }>) {
  const maxOrder = items.reduce((max, item) => Math.max(max, item.sortOrder || 0), 0)
  return maxOrder + 10
}

function categoryDefaults() {
  if (form.type === 'INCOME') {
    return { icon: 'cash-back-record' }
  }
  return { icon: 'records-o' }
}

async function loadOptions() {
  optionsLoading.value = true
  try {
    const [categoryRows, paymentMethodRows, platformRows] = await Promise.all([
      categoryApi.list(),
      paymentMethodApi.list(),
      onlinePlatformApi.list()
    ])
    categories.value = categoryRows
    paymentMethods.value = paymentMethodRows
    onlinePlatforms.value = platformRows
    await loadQuickRecommendations(form.type)
    suppressDirty.value = true
    applyQuickDefaults()
  } catch (error) {
    showError(error, '选项加载失败')
  } finally {
    suppressDirty.value = false
    optionsLoading.value = false
  }
}

async function loadQuickRecommendations(type: TransactionType = form.type) {
  quickRecommendationsLoading.value = true
  try {
    quickRecommendations.value = await transactionApi.quickEntryRecommendations(10, type)
  } catch (error) {
    console.warn('极简推荐加载失败', error)
    quickRecommendations.value = null
  } finally {
    quickRecommendationsLoading.value = false
  }
}

function applyQuickDefaults() {
  if (!filteredCategories.value.some((item) => item.id === form.categoryId)) {
    form.categoryId = quickCategoryCandidates.value[0]?.id || filteredCategories.value[0]?.id
  }
  if (!paymentMethods.value.some((item) => item.id === form.paymentMethodId)) {
    form.paymentMethodId = quickPaymentCandidates.value[0]?.id || paymentMethods.value[0]?.id
  }
  if (form.channel === 'ONLINE' && !onlinePlatforms.value.some((item) => item.id === form.onlinePlatformId)) {
    form.onlinePlatformId = quickPlatformCandidates.value[0]?.id || onlinePlatforms.value[0]?.id
    form.onlineApp = selectedOnlinePlatform.value?.name || form.onlineApp
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
  form.itemName = template.itemName || ''
  form.amount = String(template.amount)
  form.occurredAt = nowLocalInput()
  form.channel = template.channel
  form.onlineApp = template.onlineApp || ''
  form.onlinePlatformId = template.onlinePlatformId
  form.offlinePlace = template.offlinePlace || ''
  form.paymentMethodId = template.paymentMethodId
  form.categoryId = template.categoryId
  form.note = template.note || ''
  suppressDirty.value = false
  markTemplateFieldsDirty()
  scrollSelectedQuickOptions()
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
  void loadQuickRecommendations(form.type).then(() => {
    suppressDirty.value = true
    applyQuickDefaults()
    suppressDirty.value = false
  })
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
  dirtyFields.onlinePlatformId = true
  dirtyFields.offlinePlace = true
  dirtyFields.paymentMethodId = true
  dirtyFields.categoryId = true
}

function selectedImageFiles() {
  return imageFiles.value
    .map((item) => item.file)
    .filter((file): file is File => Boolean(file))
}

function validateImageFile(file: File) {
  if (!ALLOWED_TRANSACTION_IMAGE_TYPES.includes(file.type)) {
    showToast('仅支持 JPG、PNG、WebP 图片')
    return false
  }
  if (file.size > MAX_TRANSACTION_IMAGE_SIZE) {
    showToast('单张图片不能超过 3MB')
    return false
  }
  return true
}

function beforeReadImage(file: File | File[]) {
  const files = Array.isArray(file) ? file : [file]
  if (imageFiles.value.length + files.length > MAX_TRANSACTION_IMAGES) {
    showToast('单笔记录最多上传 3 张图片')
    return false
  }
  return files.every(validateImageFile)
}

function handleImageOversize() {
  showToast('单张图片不能超过 3MB')
}

function scrollQuickChipIntoView(grid: HTMLElement | null, id: number) {
  nextTick(() => {
    const chip = grid?.querySelector<HTMLElement>(`[data-option-id="${id}"]`)
    chip?.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'center' })
  })
}

function scrollSelectedQuickOptions() {
  if (form.categoryId) {
    scrollQuickChipIntoView(categoryChipGridRef.value, form.categoryId)
  }
  if (form.paymentMethodId) {
    scrollQuickChipIntoView(paymentChipGridRef.value, form.paymentMethodId)
  }
  if (form.channel === 'ONLINE' && form.onlinePlatformId) {
    scrollQuickChipIntoView(platformChipGridRef.value, form.onlinePlatformId)
  }
}

function selectCategory(id: number | undefined, source: 'quick' | 'popup' = 'quick') {
  if (!id) return
  haptic('selection')
  triggerVisualFeedback('selection')
  form.categoryId = id
  categoryPopup.value = false
  if (source === 'popup') {
    scrollQuickChipIntoView(categoryChipGridRef.value, id)
  }
}

function selectPaymentMethod(id: number | undefined, source: 'quick' | 'popup' = 'quick') {
  if (!id) return
  haptic('selection')
  triggerVisualFeedback('selection')
  form.paymentMethodId = id
  paymentPopup.value = false
  if (source === 'popup') {
    scrollQuickChipIntoView(paymentChipGridRef.value, id)
  }
}

function selectOnlinePlatform(platform: OnlinePlatform | undefined, source: 'quick' | 'popup' = 'quick') {
  if (!platform) return
  haptic('selection')
  triggerVisualFeedback('selection')
  form.onlinePlatformId = platform.id
  form.onlineApp = platform.name
  platformPopup.value = false
  if (source === 'popup') {
    scrollQuickChipIntoView(platformChipGridRef.value, platform.id)
  }
}

function openCategoryPopup() {
  categorySearch.value = ''
  newCategoryName.value = ''
  categoryPopup.value = true
}

function openPaymentPopup() {
  paymentSearch.value = ''
  newPaymentMethodName.value = ''
  paymentPopup.value = true
}

function openPlatformPopup() {
  platformSearch.value = ''
  newPlatformName.value = ''
  platformPopup.value = true
}

async function createCategoryFromMinimal() {
  if (creatingCategory.value) return
  const name = newCategoryName.value.trim()
  if (!name) {
    showToast('请填写分类名称')
    return
  }
  if (filteredCategories.value.some((item) => normalizeName(item.name) === normalizeName(name))) {
    showToast(`${form.type === 'EXPENSE' ? '支出' : '收入'}分类已存在`)
    return
  }
  creatingCategory.value = true
  try {
    const defaults = categoryDefaults()
    const created = await categoryApi.create({
      name,
      type: form.type,
      icon: defaults.icon,
      sortOrder: nextSortOrder(filteredCategories.value),
      pinned: false
    })
    addCategoryOption(created)
    form.categoryId = created.id
    categoryPopup.value = false
    showToast('分类已创建')
  } catch (error) {
    showError(error, '分类创建失败')
  } finally {
    creatingCategory.value = false
  }
}

async function createPaymentFromMinimal() {
  if (creatingPaymentMethod.value) return
  const name = newPaymentMethodName.value.trim()
  if (!name) {
    showToast('请填写支付方式名称')
    return
  }
  if (paymentMethods.value.some((item) => normalizeName(item.name) === normalizeName(name))) {
    showToast('支付方式已存在')
    return
  }
  creatingPaymentMethod.value = true
  try {
    const created = await paymentMethodApi.create({
      name,
      icon: 'balance-o',
      sortOrder: nextSortOrder(paymentMethods.value),
      pinned: false
    })
    addPaymentMethodOption(created)
    form.paymentMethodId = created.id
    paymentPopup.value = false
    showToast('支付方式已创建')
  } catch (error) {
    showError(error, '支付方式创建失败')
  } finally {
    creatingPaymentMethod.value = false
  }
}

async function createPlatformFromMinimal() {
  if (creatingPlatform.value) return
  const name = newPlatformName.value.trim()
  if (!name) {
    showToast('请填写平台名称')
    return
  }
  if (onlinePlatforms.value.some((item) => normalizeName(item.name) === normalizeName(name))) {
    showToast('线上平台已存在')
    return
  }
  creatingPlatform.value = true
  try {
    const created = await onlinePlatformApi.create({
      name,
      icon: 'apps-o',
      sortOrder: nextSortOrder(onlinePlatforms.value),
      pinned: false
    })
    addOnlinePlatformOption(created)
    selectOnlinePlatform(created)
    showToast('线上平台已创建')
  } catch (error) {
    showError(error, '线上平台创建失败')
  } finally {
    creatingPlatform.value = false
  }
}

function transactionPayload() {
  const itemName = form.itemName.trim()
  return {
    type: form.type,
    itemName: itemName || undefined,
    amount: Number(form.amount),
    occurredAt: toBackendDateTime(form.occurredAt),
    channel: form.channel,
    onlineApp: form.channel === 'ONLINE' ? selectedOnlinePlatform.value?.name || form.onlineApp.trim() : undefined,
    onlinePlatformId: form.channel === 'ONLINE' ? form.onlinePlatformId : undefined,
    offlinePlace: form.channel === 'OFFLINE' ? form.offlinePlace.trim() : undefined,
    paymentMethodId: form.paymentMethodId as number,
    categoryId: form.categoryId as number,
    note: form.note.trim() || undefined
  }
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
  if (!dirtyFields.onlinePlatformId && form.channel === 'ONLINE' && template.onlinePlatformId && onlinePlatforms.value.some((item) => item.id === template.onlinePlatformId)) {
    form.onlinePlatformId = template.onlinePlatformId
    changed = true
  }
  if (!dirtyFields.offlinePlace && form.channel === 'OFFLINE') {
    form.offlinePlace = template.offlinePlace || ''
    changed = true
  }
  suppressDirty.value = false
  contextRecommendationText.value = changed ? `已按历史习惯预填：${template.reason}` : ''
  if (changed) {
    scrollSelectedQuickOptions()
  }
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
  if (form.channel === 'ONLINE' && !form.onlinePlatformId && !form.onlineApp.trim()) {
    haptic('warning')
    triggerVisualFeedback('warning')
    showToast('请选择线上平台')
    return
  }
  saving.value = true
  try {
    const images = selectedImageFiles()
    if (images.length > 0) {
      await transactionApi.createWithImages(transactionPayload(), images)
    } else {
      await transactionApi.create(transactionPayload())
    }
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
watch(() => form.onlinePlatformId, () => markDirty('onlinePlatformId'), { flush: 'sync' })
watch(() => form.offlinePlace, () => markDirty('offlinePlace'), { flush: 'sync' })
watch(() => form.paymentMethodId, () => markDirty('paymentMethodId'), { flush: 'sync' })
watch(() => form.categoryId, () => markDirty('categoryId'), { flush: 'sync' })
watch(() => [form.itemName, form.type, form.channel, form.occurredAt], scheduleContextRecommendation)
watch(() => form.channel, () => {
  if (form.channel === 'ONLINE') {
    suppressDirty.value = true
    applyQuickDefaults()
    suppressDirty.value = false
  }
})
watch(selectedOnlinePlatform, (platform) => {
  if (platform && form.channel === 'ONLINE') {
    form.onlineApp = platform.name
  }
})
</script>

<template>
  <main class="page quick-add-page">
    <van-nav-bar title="记一笔" left-arrow @click-left="router.back()" />
    <div class="page-content quick-add-content">
      <van-form class="quick-add-form" @submit="submit">
        <section :class="['section', 'panel', 'quick-entry-panel', visualFeedback === 'warning' ? 'ui-feedback-warning' : '']">
          <div class="quick-entry-header">
            <div>
              <span class="quick-kicker">{{ entryMode === 'minimal' ? 'SIMPLE ENTRY' : 'ADVANCED ENTRY' }}</span>
              <strong>{{ entryMode === 'minimal' ? minimalTitle : (form.type === 'EXPENSE' ? '记录支出' : '记录收入') }}</strong>
            </div>
            <van-radio-group
              v-model="form.type"
              :class="['quick-type-switch', { 'is-right': form.type === 'INCOME' }]"
              direction="horizontal"
              @change="syncCategoryForType"
            >
              <van-radio name="EXPENSE">支出</van-radio>
              <van-radio name="INCOME">收入</van-radio>
            </van-radio-group>
          </div>

          <van-radio-group
            v-model="entryMode"
            :class="['quick-mode-switch', { 'is-right': entryMode === 'advanced' }]"
            direction="horizontal"
          >
            <van-radio name="minimal">极简</van-radio>
            <van-radio name="advanced">进阶</van-radio>
          </van-radio-group>

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
            <van-field v-if="entryMode === 'advanced'" v-model="form.itemName" label="事项" placeholder="如冰棍、工资、泳镜" />
            <TransactionOptionFields
              v-if="entryMode === 'advanced'"
              v-model:payment-method-id="form.paymentMethodId"
              v-model:category-id="form.categoryId"
              :payment-methods="paymentMethods"
              :categories="categories"
              :transaction-type="form.type"
              @payment-method-created="addPaymentMethodOption"
              @category-created="addCategoryOption"
            />
          </van-cell-group>

          <div v-if="entryMode === 'minimal'" class="minimal-entry">
            <ModernDateField v-model="form.occurredAt" mode="datetime" label="支付时间" title="选择支付时间" required>
              <template #trigger="{ displayValue, open, disabled }">
                <button class="minimal-date-field" type="button" :disabled="disabled" @click="open">
                  <span class="minimal-date-label"><span class="required-mark">*</span>支付时间</span>
                  <strong>{{ displayValue || '请选择支付时间' }}</strong>
                  <van-icon name="arrow" />
                </button>
              </template>
            </ModernDateField>

            <div class="minimal-block">
              <div class="minimal-block-header">
                <span>分类</span>
                <button type="button" @click="openCategoryPopup">更多</button>
              </div>
              <div ref="categoryChipGridRef" class="quick-chip-grid">
                <button
                  v-for="item in visibleQuickCategoryCandidates"
                  :key="item.id"
                  type="button"
                  :data-option-id="item.id"
                  :class="['quick-chip', { active: form.categoryId === item.id }]"
                  @click="selectCategory(item.id)"
                >
                  <van-icon :name="item.icon || 'records-o'" />
                  <span>{{ item.name }}</span>
                </button>
              </div>
            </div>

            <div class="minimal-block">
              <div class="minimal-block-header">
                <span>支付方式</span>
                <button type="button" @click="openPaymentPopup">更多</button>
              </div>
              <div ref="paymentChipGridRef" class="quick-chip-grid compact">
                <button
                  v-for="item in visibleQuickPaymentCandidates"
                  :key="item.id"
                  type="button"
                  :data-option-id="item.id"
                  :class="['quick-chip', { active: form.paymentMethodId === item.id }]"
                  @click="selectPaymentMethod(item.id)"
                >
                  <van-icon :name="item.icon || 'balance-o'" />
                  <span>{{ item.name }}</span>
                </button>
              </div>
            </div>

            <div class="minimal-row">
              <div class="minimal-row-title">
                <span>渠道</span>
              </div>
              <van-radio-group
                v-model="form.channel"
                :class="['quick-channel-switch', { 'is-right': form.channel === 'OFFLINE' }]"
                direction="horizontal"
              >
                <van-radio name="ONLINE">线上</van-radio>
                <van-radio name="OFFLINE">线下</van-radio>
              </van-radio-group>
            </div>

            <div class="channel-content-switch">
              <Transition :name="form.channel === 'OFFLINE' ? 'channel-slide-left' : 'channel-slide-right'">
                <div v-if="form.channel === 'ONLINE'" key="online-platforms" class="minimal-block">
                  <div class="minimal-block-header">
                    <span>线上平台</span>
                    <button type="button" @click="openPlatformPopup">更多</button>
                  </div>
                  <div ref="platformChipGridRef" class="quick-chip-grid">
                    <button
                      v-for="item in visibleQuickPlatformCandidates"
                      :key="item.id"
                      type="button"
                      :data-option-id="item.id"
                      :class="['quick-chip', { active: form.onlinePlatformId === item.id }]"
                      @click="selectOnlinePlatform(item)"
                    >
                      <van-icon :name="item.icon || 'apps-o'" />
                      <span>{{ item.name }}</span>
                    </button>
                  </div>
                </div>
                <AmapPlaceField v-else key="offline-place" v-model="form.offlinePlace" class="minimal-place-block" label="线下地点" required />
              </Transition>
            </div>

            <section v-if="quickCombinations.length" class="minimal-combos">
              <div class="minimal-block-header">
                <span>最近组合</span>
                <van-loading v-if="quickRecommendationsLoading" size="16px" />
              </div>
              <div class="recommendation-list">
                <button
                  v-for="item in quickCombinations"
                  :key="templateKey(item)"
                  type="button"
                  class="recommendation-card minimal-combo-card"
                  @click="applyTemplate(item)"
                >
                  <span class="recommendation-title">{{ transactionTitle(item) }}</span>
                  <span class="recommendation-meta">{{ item.categoryName }} · {{ item.paymentMethodName }}</span>
                  <span class="recommendation-reason">{{ item.reason }}</span>
                </button>
              </div>
            </section>
          </div>
        </section>

        <section v-if="entryMode === 'advanced' && (templatesLoading || currentTemplates.length || contextRecommendationText)" class="section panel quick-recommendations">
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
              <span class="recommendation-title">{{ transactionTitle(item) }}</span>
              <span class="recommendation-meta">{{ item.categoryName }} · {{ item.paymentMethodName }}</span>
              <span class="recommendation-reason">{{ item.reason }}</span>
            </button>
          </div>
          <div v-else-if="templatesLoading" class="muted recommendation-loading">正在生成推荐...</div>
        </section>

        <section v-if="entryMode === 'advanced'" class="section panel quick-extra-panel">
          <div class="quick-group-heading">补充信息</div>
          <van-cell-group inset class="quick-cell-group">
            <ModernDateField v-model="form.occurredAt" mode="datetime" label="时间" title="选择发生时间" required />
            <van-field label="渠道">
              <template #input>
                <van-radio-group
                  v-model="form.channel"
                  :class="['quick-channel-switch', { 'is-right': form.channel === 'OFFLINE' }]"
                  direction="horizontal"
                >
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
          <div class="quick-image-upload">
            <div class="quick-image-upload-header">
              <span>凭证图片</span>
              <span>{{ imageFiles.length }} / {{ MAX_TRANSACTION_IMAGES }}</span>
            </div>
            <van-uploader
              v-model="imageFiles"
              multiple
              result-type="file"
              accept="image/jpeg,image/png,image/webp"
              upload-icon="photograph"
              upload-text="上传"
              :max-count="MAX_TRANSACTION_IMAGES"
              :max-size="MAX_TRANSACTION_IMAGE_SIZE"
              :before-read="beforeReadImage"
              @oversize="handleImageOversize"
            />
          </div>
        </section>

        <van-popup v-model:show="categoryPopup" position="bottom" round teleport="body">
          <div class="quick-choice-sheet">
            <div class="quick-choice-header">
              <button type="button" @click="categoryPopup = false"><van-icon name="cross" /><span>取消</span></button>
              <strong>选择分类</strong>
              <span />
            </div>
            <van-search v-model="categorySearch" placeholder="搜索分类" />
            <div class="quick-choice-list">
              <button
                v-for="item in filteredCategorySearchOptions"
                :key="item.id"
                type="button"
                :class="['quick-choice-option', { active: form.categoryId === item.id }]"
                @click="selectCategory(item.id, 'popup')"
              >
                <van-icon :name="item.icon || 'records-o'" />
                <span>{{ item.name }}</span>
                <van-icon v-if="form.categoryId === item.id" name="success" />
              </button>
            </div>
            <div class="quick-create-row">
              <van-field v-model="newCategoryName" label="新增" placeholder="分类名称" autocomplete="off" @keyup.enter="createCategoryFromMinimal" />
              <van-button type="primary" icon="plus" :loading="creatingCategory" @click="createCategoryFromMinimal">添加</van-button>
            </div>
          </div>
        </van-popup>

        <van-popup v-model:show="paymentPopup" position="bottom" round teleport="body">
          <div class="quick-choice-sheet">
            <div class="quick-choice-header">
              <button type="button" @click="paymentPopup = false"><van-icon name="cross" /><span>取消</span></button>
              <strong>选择支付方式</strong>
              <span />
            </div>
            <van-search v-model="paymentSearch" placeholder="搜索支付方式" />
            <div class="quick-choice-list">
              <button
                v-for="item in filteredPaymentSearchOptions"
                :key="item.id"
                type="button"
                :class="['quick-choice-option', { active: form.paymentMethodId === item.id }]"
                @click="selectPaymentMethod(item.id, 'popup')"
              >
                <van-icon :name="item.icon || 'balance-o'" />
                <span>{{ item.name }}</span>
                <van-icon v-if="form.paymentMethodId === item.id" name="success" />
              </button>
            </div>
            <div class="quick-create-row">
              <van-field v-model="newPaymentMethodName" label="新增" placeholder="支付方式名称" autocomplete="off" @keyup.enter="createPaymentFromMinimal" />
              <van-button type="primary" icon="plus" :loading="creatingPaymentMethod" @click="createPaymentFromMinimal">添加</van-button>
            </div>
          </div>
        </van-popup>

        <van-popup v-model:show="platformPopup" position="bottom" round teleport="body">
          <div class="quick-choice-sheet">
            <div class="quick-choice-header">
              <button type="button" @click="platformPopup = false"><van-icon name="cross" /><span>取消</span></button>
              <strong>选择线上平台</strong>
              <span />
            </div>
            <van-search v-model="platformSearch" placeholder="搜索平台" />
            <div class="quick-choice-list">
              <button
                v-for="item in filteredPlatformSearchOptions"
                :key="item.id"
                type="button"
                :class="['quick-choice-option', { active: form.onlinePlatformId === item.id }]"
                @click="selectOnlinePlatform(item, 'popup')"
              >
                <van-icon :name="item.icon || 'apps-o'" />
                <span>{{ item.name }}</span>
                <van-icon v-if="form.onlinePlatformId === item.id" name="success" />
              </button>
            </div>
            <div class="quick-create-row">
              <van-field v-model="newPlatformName" label="新增" placeholder="平台名称" autocomplete="off" @keyup.enter="createPlatformFromMinimal" />
              <van-button type="primary" icon="plus" :loading="creatingPlatform" @click="createPlatformFromMinimal">添加</van-button>
            </div>
          </div>
        </van-popup>

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
.quick-mode-switch,
.quick-channel-switch {
  position: relative;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-4);
  min-width: 132px;
  padding: var(--space-4);
  border-radius: var(--radius-pill);
  background: rgba(var(--theme-border-warm-rgb), 0.1);
  overflow: hidden;
  isolation: isolate;
}

.quick-type-switch::before,
.quick-mode-switch::before,
.quick-channel-switch::before {
  position: absolute;
  top: var(--space-4);
  bottom: var(--space-4);
  left: var(--space-4);
  z-index: 0;
  width: calc((100% - var(--space-12)) / 2);
  border-radius: var(--radius-pill);
  background: var(--glass-strong-bg);
  box-shadow: 0 8px 18px rgba(var(--theme-shadow-warm-rgb), 0.18);
  content: '';
  transition:
    transform 220ms cubic-bezier(0.22, 1, 0.36, 1),
    background-color 180ms ease,
    box-shadow 180ms ease;
}

.quick-type-switch.is-right::before,
.quick-mode-switch.is-right::before,
.quick-channel-switch.is-right::before {
  transform: translateX(calc(100% + var(--space-4)));
}

.quick-mode-switch {
  width: 100%;
}

.quick-type-switch :deep(.van-radio),
.quick-mode-switch :deep(.van-radio),
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
  transition: color 180ms ease;
  z-index: 1;
}

.quick-type-switch :deep(.van-radio__icon),
.quick-mode-switch :deep(.van-radio__icon),
.quick-channel-switch :deep(.van-radio__icon) {
  display: none;
}

.quick-type-switch :deep(.van-radio__label),
.quick-mode-switch :deep(.van-radio__label),
.quick-channel-switch :deep(.van-radio__label) {
  margin: 0;
}

.quick-type-switch :deep(.van-radio[aria-checked='true']),
.quick-mode-switch :deep(.van-radio[aria-checked='true']),
.quick-channel-switch :deep(.van-radio[aria-checked='true']) {
  color: var(--primary);
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
  font-size: calc(var(--font-size-amount) + 6px);
  font-weight: 780;
  line-height: var(--line-height-amount);
  text-align: center;
}

.quick-amount-field :deep(.van-field__label) {
  display: none;
}

.quick-amount-field :deep(.van-field__body) {
  width: 100%;
}

.quick-primary-group:has(.quick-amount-field) {
  background: transparent;
  box-shadow: none;
}

.quick-primary-group:has(.quick-amount-field) :deep(.van-cell) {
  background: transparent;
}

.quick-primary-group:has(.quick-amount-field) :deep(.van-cell::after) {
  display: none;
}

.quick-amount-field :deep(.van-field__body),
.quick-amount-field :deep(.van-field__label) {
  align-self: center;
}

.minimal-entry {
  display: grid;
  gap: var(--space-12);
}

.minimal-entry :deep(.van-cell) {
  border-radius: var(--radius-card);
  background: var(--card-bg);
}

.minimal-row,
.minimal-block,
.minimal-date-field,
.minimal-place-block,
.minimal-combos {
  display: grid;
  gap: var(--space-10);
  padding: var(--space-12);
  border: 1px solid rgba(var(--theme-border-warm-rgb), 0.18);
  border-radius: var(--radius-card);
  background: rgba(var(--theme-border-warm-rgb), 0.07);
}

.minimal-block {
  overflow: hidden;
}

.minimal-date-field {
  grid-template-columns: minmax(0, 1fr) auto auto;
  align-items: center;
  border: 1px solid rgba(var(--theme-border-warm-rgb), 0.18);
  color: inherit;
  font: inherit;
  text-align: left;
}

.minimal-date-field:disabled {
  opacity: 0.55;
}

.minimal-date-label {
  color: var(--text-secondary);
  font-size: var(--font-size-caption);
  line-height: var(--line-height-caption);
}

.minimal-date-label .required-mark {
  margin-right: var(--space-2);
  color: var(--expense);
}

.minimal-date-field strong {
  overflow: hidden;
  color: var(--text-main);
  font-size: var(--font-size-body-strong);
  font-weight: 700;
  line-height: var(--line-height-body-strong);
  text-align: right;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.minimal-date-field > .van-icon {
  color: var(--text-muted);
  font-size: var(--icon-size-sm);
}

.minimal-place-block {
  overflow: visible;
}

.channel-content-switch {
  position: relative;
  overflow: hidden;
  transform: translateZ(0);
}

.minimal-row {
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
}

.minimal-row-title,
.minimal-block-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-8);
}

.minimal-row-title {
  display: grid;
  justify-content: start;
}

.minimal-row-title span,
.minimal-block-header span {
  color: var(--text-secondary);
  font-size: var(--font-size-caption);
  line-height: var(--line-height-caption);
}

.minimal-row-title strong {
  color: var(--text-main);
  font-size: var(--font-size-body-strong);
  line-height: var(--line-height-body-strong);
}

.minimal-block-header button {
  border: 0;
  background: transparent;
  color: var(--primary);
  font-size: var(--font-size-caption);
  font-weight: 700;
}

.quick-chip-grid {
  display: flex;
  gap: var(--space-8);
  overflow-x: auto;
  overflow-y: hidden;
  width: 100%;
  padding: 0 0 var(--space-2);
  scroll-padding-inline: 0;
  scroll-snap-type: x proximity;
  -webkit-overflow-scrolling: touch;
  overscroll-behavior-x: contain;
  scrollbar-width: none;
}

.quick-chip-grid::-webkit-scrollbar {
  display: none;
}

.quick-chip {
  display: grid;
  gap: var(--space-5);
  flex: 0 0 86px;
  min-width: 86px;
  min-height: 72px;
  align-content: center;
  justify-items: center;
  border: 1px solid rgba(var(--theme-border-warm-rgb), 0.2);
  border-radius: var(--radius-card);
  padding: var(--space-7) var(--space-4);
  background: var(--card-bg);
  color: var(--text-main);
  font: inherit;
  scroll-snap-align: start;
}

.quick-chip span {
  overflow: hidden;
  width: 100%;
  font-size: var(--font-size-caption);
  font-weight: 700;
  line-height: var(--line-height-caption);
  text-align: center;
  overflow-wrap: anywhere;
  word-break: break-word;
  white-space: normal;
}

.quick-chip :deep(.van-icon) {
  color: var(--primary);
  font-size: var(--icon-size-md);
}

.quick-chip.active {
  border-color: var(--primary);
  background: var(--primary-soft);
  box-shadow: inset 0 0 0 1px rgba(var(--theme-primary-glow-rgb), 0.2);
}

.channel-slide-left-enter-active,
.channel-slide-left-leave-active,
.channel-slide-right-enter-active,
.channel-slide-right-leave-active {
  backface-visibility: hidden;
  transition: transform 280ms cubic-bezier(0.2, 0.8, 0.2, 1);
  will-change: transform;
}

.channel-slide-left-leave-active,
.channel-slide-right-leave-active {
  position: absolute;
  inset: 0;
  width: 100%;
}

.channel-slide-left-enter-from {
  transform: translate3d(100%, 0, 0);
}

.channel-slide-left-leave-to {
  transform: translate3d(-100%, 0, 0);
}

.channel-slide-right-enter-from {
  transform: translate3d(-100%, 0, 0);
}

.channel-slide-right-leave-to {
  transform: translate3d(100%, 0, 0);
}

@media (prefers-reduced-motion: reduce) {
  .channel-slide-left-enter-active,
  .channel-slide-left-leave-active,
  .channel-slide-right-enter-active,
  .channel-slide-right-leave-active {
    transition: none;
  }
}

.minimal-combo-card {
  min-height: 92px;
}

.quick-choice-sheet {
  display: grid;
  max-height: min(78vh, 620px);
  grid-template-rows: auto auto minmax(0, 1fr) auto;
  padding-bottom: max(var(--space-12), env(safe-area-inset-bottom));
  background: var(--page-bg-soft);
}

.quick-choice-header {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr) 72px;
  align-items: center;
  min-height: 48px;
  padding: var(--space-0) var(--space-12);
  background: var(--card-bg);
  border-bottom: 1px solid var(--border-warm);
}

.quick-choice-header button {
  display: inline-flex;
  align-items: center;
  gap: var(--space-3);
  border: 0;
  background: transparent;
  color: var(--text-secondary);
  font: inherit;
}

.quick-choice-header strong {
  overflow: hidden;
  font-size: var(--font-size-section-title);
  text-align: center;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.quick-choice-list {
  display: grid;
  gap: var(--space-8);
  overflow-y: auto;
  padding: var(--space-12);
}

.quick-choice-option {
  display: grid;
  grid-template-columns: 26px minmax(0, 1fr) 22px;
  gap: var(--space-10);
  align-items: center;
  min-height: 46px;
  border: 1px solid var(--border-warm);
  border-radius: var(--radius-card);
  padding: var(--space-10) var(--space-12);
  background: var(--card-bg);
  color: var(--text-main);
  font: inherit;
  text-align: left;
}

.quick-choice-option span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.quick-choice-option :deep(.van-icon) {
  color: var(--primary);
}

.quick-choice-option.active {
  border-color: var(--primary);
  background: var(--primary-soft);
}

.quick-create-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: var(--space-8);
  align-items: center;
  padding: var(--space-10) var(--space-12) var(--space-0);
  border-top: 1px solid var(--border-warm);
  background: var(--card-bg);
}

.quick-create-row :deep(.van-cell) {
  min-height: 48px;
  border: 1px solid rgba(var(--theme-primary-glow-rgb), 0.38);
  border-radius: var(--radius-card);
  background: var(--card-bg);
  box-shadow: inset 0 0 0 1px rgba(var(--theme-primary-glow-rgb), 0.08);
}

.quick-create-row :deep(.van-cell::after) {
  display: none;
}

.quick-create-row :deep(.van-field__label) {
  color: var(--primary);
  font-weight: 700;
}

.quick-create-row :deep(.van-field__control) {
  color: var(--text-main);
  font-size: var(--font-size-body);
}

.quick-create-row :deep(.van-field:focus-within) {
  border-color: var(--primary);
  background: var(--primary-soft);
  box-shadow: inset 0 0 0 1px rgba(var(--theme-primary-glow-rgb), 0.26);
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

.quick-image-upload {
  display: grid;
  gap: var(--space-10);
  padding: var(--space-12) var(--space-16) var(--space-16);
  border-top: 1px solid rgba(var(--theme-border-warm-rgb), 0.16);
}

.quick-image-upload-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-10);
  color: var(--text-secondary);
  font-size: var(--font-size-meta);
  line-height: var(--line-height-meta);
}

.quick-image-upload-header span:first-child {
  color: var(--text-main);
  font-weight: 700;
}

.quick-image-upload :deep(.van-uploader__upload),
.quick-image-upload :deep(.van-uploader__preview-image) {
  border-radius: var(--radius-card);
  background: rgba(var(--theme-border-warm-rgb), 0.08);
}

.quick-image-upload :deep(.van-uploader__upload) {
  border: 1px dashed rgba(var(--theme-border-warm-rgb), 0.38);
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

  .minimal-row {
    grid-template-columns: 1fr;
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
