<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showConfirmDialog, showFailToast, showImagePreview, showToast } from 'vant'
import type { UploaderFileListItem } from 'vant'
import { categoryApi, onlinePlatformApi, paymentMethodApi, transactionApi } from '@/api/services'
import AmapPlaceField from '@/components/AmapPlaceField.vue'
import BottomSheet from '@/components/BottomSheet.vue'
import FormActionBar from '@/components/FormActionBar.vue'
import ModernDateField from '@/components/ModernDateField.vue'
import PageSkeleton from '@/components/PageSkeleton.vue'
import type { Category, OnlinePlatform, PaymentMethod, TransactionPayload, TransactionRecord } from '@/types'
import { money, nowLocalInput, toBackendDateTime, toDateTimeLocal } from '@/utils/date'
import {
  displayTransactionDateTime,
  transactionChannelText,
  transactionImageCountText,
  transactionLedgerItems,
  transactionPlaceLabel,
  transactionPlaceValue,
  transactionSummaryChips,
  transactionTypeText
} from '@/utils/transactionDetailPresentation'
import { showError } from '@/utils/errors'
import { haptic } from '@/utils/haptics'
import { moneyError } from '@/utils/money'
import { resetRecordsQueryPreference } from '@/utils/preferences'
import { transactionTitle } from '@/utils/display'
import { transactionEditOnlinePlatformFields } from '@/utils/transactionEditPayload'
import { isAllowedTransactionImageFile, MAX_TRANSACTION_IMAGES, MAX_TRANSACTION_IMAGE_SIZE, TRANSACTION_IMAGE_ACCEPT } from '@/utils/transactionImages'
import { useVisualFeedback } from '@/utils/visualFeedback'

const route = useRoute()
const router = useRouter()
const record = ref<TransactionRecord | null>(null)
const categories = ref<Category[]>([])
const paymentMethods = ref<PaymentMethod[]>([])
const onlinePlatforms = ref<OnlinePlatform[]>([])
const editMode = ref(false)
const loading = ref(true)
const optionsLoading = ref(false)
const optionsLoaded = ref(false)
const saving = ref(false)
const copying = ref(false)
const deleting = ref(false)
const imageUploading = ref(false)
const imageLoading = ref(false)
const imageLoadFailed = ref(false)
const imageDeletingId = ref<number | null>(null)
const imageFiles = ref<UploaderFileListItem[]>([])
const imagePreviewUrls = ref<Record<number, string>>({})
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
let imageLoadRequestId = 0
let imageLoadAbortController: AbortController | null = null

const form = reactive({
  type: 'EXPENSE' as 'EXPENSE' | 'INCOME',
  itemName: '',
  amount: '',
  occurredAt: '',
  channel: 'OFFLINE' as 'ONLINE' | 'OFFLINE',
  onlineApp: '',
  onlinePlatformId: undefined as number | undefined,
  offlinePlace: '',
  paymentMethodId: undefined as number | undefined,
  categoryId: undefined as number | undefined,
  note: ''
})

const filteredCategories = computed(() => categories.value.filter((item) => item.type === form.type))
const selectedCategory = computed(() => categories.value.find((item) => item.id === form.categoryId))
const selectedPaymentMethod = computed(() => paymentMethods.value.find((item) => item.id === form.paymentMethodId))
const selectedOnlinePlatform = computed(() => {
  const selected = onlinePlatforms.value.find((item) => item.id === form.onlinePlatformId)
  if (selected) return selected
  if (form.onlinePlatformId && form.onlineApp.trim()) {
    return { id: form.onlinePlatformId, name: form.onlineApp.trim(), icon: 'apps-o' }
  }
  return undefined
})
const quickCategoryCandidates = computed(() => filteredCategories.value.slice(0, 10))
const quickPaymentCandidates = computed(() => paymentMethods.value.slice(0, 10))
const quickPlatformCandidates = computed(() => onlinePlatforms.value.slice(0, 10))
const visibleQuickCategoryCandidates = computed(() => withSelectedOption(quickCategoryCandidates.value, selectedCategory.value, 10))
const visibleQuickPaymentCandidates = computed(() => withSelectedOption(quickPaymentCandidates.value, selectedPaymentMethod.value, 10))
const visibleQuickPlatformCandidates = computed(() => withSelectedOption(quickPlatformCandidates.value, selectedOnlinePlatform.value, 10))
const filteredCategorySearchOptions = computed(() => filterByName(filteredCategories.value, categorySearch.value))
const filteredPaymentSearchOptions = computed(() => filterByName(paymentMethods.value, paymentSearch.value))
const filteredPlatformSearchOptions = computed(() => filterByName(onlinePlatforms.value, platformSearch.value))
const platformSearchCreateName = computed(() => searchCreateName(platformSearch.value, filteredPlatformSearchOptions.value))
const suggestedPlatformName = computed(() => suggestedCreateName(platformSearch.value, newPlatformName.value, filteredPlatformSearchOptions.value))
const detailTypeText = computed(() => record.value ? transactionTypeText(record.value) : '')
const detailChannelText = computed(() => record.value ? transactionChannelText(record.value) : '')
const detailPlaceLabel = computed(() => record.value ? transactionPlaceLabel(record.value) : '')
const detailPlaceValue = computed(() => record.value ? transactionPlaceValue(record.value) : '')
const detailSummaryChips = computed(() => record.value ? transactionSummaryChips(record.value) : [])
const detailLedgerItems = computed(() => record.value ? transactionLedgerItems(record.value) : [])
const detailImageCountText = computed(() => record.value ? transactionImageCountText(record.value) : '无凭证')
const editSubmitText = computed(() => (optionsLoading.value ? '正在加载选项' : '保存修改'))
const recordImages = computed(() => record.value?.images || [])
const remainingImageSlots = computed(() => Math.max(MAX_TRANSACTION_IMAGES - recordImages.value.length, 0))
const totalImageCount = computed(() => recordImages.value.length + imageFiles.value.length)
const amountInputWidth = computed(() => `${Math.min(Math.max((form.amount || '0.00').length, 4), 12) + 0.5}ch`)

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

function searchCreateName<T extends { name: string }>(keyword: string, matchedItems: T[]) {
  const searchedName = keyword.trim()
  return searchedName && matchedItems.length === 0 ? searchedName : ''
}

function suggestedCreateName<T extends { name: string }>(keyword: string, input: string, matchedItems: T[]) {
  const typedName = input.trim()
  if (typedName) return typedName
  return searchCreateName(keyword, matchedItems)
}

function withSelectedOption<T extends { id: number }>(items: T[], selected: T | undefined, limit: number) {
  if (!selected || items.some((item) => item.id === selected.id)) {
    return items
  }
  return [selected, ...items.filter((item) => item.id !== selected.id)].slice(0, limit)
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

function addCategoryOption(category: Category) {
  categories.value = sortBySortOrder([...categories.value.filter((item) => item.id !== category.id), category])
}

function addPaymentMethodOption(paymentMethod: PaymentMethod) {
  paymentMethods.value = sortBySortOrder([...paymentMethods.value.filter((item) => item.id !== paymentMethod.id), paymentMethod])
}

function addOnlinePlatformOption(platform: OnlinePlatform) {
  onlinePlatforms.value = sortBySortOrder([...onlinePlatforms.value.filter((item) => item.id !== platform.id), platform])
}

function recordId() {
  return Number(route.params.id)
}

function fillForm(item: TransactionRecord) {
  form.type = item.type
  form.itemName = item.itemName || ''
  form.amount = String(item.amount)
  form.occurredAt = toDateTimeLocal(item.occurredAt)
  form.channel = item.channel
  form.onlineApp = item.onlineApp || ''
  form.onlinePlatformId = item.onlinePlatformId
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
    loading.value = false
    void loadRecordImageUrls(nextRecord)
  } catch (error) {
    record.value = null
    imageLoading.value = false
    imageLoadFailed.value = false
    revokeImagePreviewUrls()
    showError(error, '记录详情加载失败')
  } finally {
    loading.value = false
  }
}

function selectedImageFiles() {
  return imageFiles.value
    .map((item) => item.file)
    .filter((file): file is File => Boolean(file))
}

function transactionPayload(): TransactionPayload {
  const onlinePlatformFields = transactionEditOnlinePlatformFields(form)
  return {
    type: form.type,
    itemName: form.itemName.trim() || undefined,
    amount: Number(form.amount),
    occurredAt: toBackendDateTime(form.occurredAt),
    channel: form.channel,
    onlineApp: onlinePlatformFields.onlineApp,
    onlinePlatformId: onlinePlatformFields.onlinePlatformId,
    offlinePlace: form.channel === 'OFFLINE' ? form.offlinePlace.trim() || undefined : undefined,
    paymentMethodId: form.paymentMethodId as number,
    categoryId: form.categoryId as number,
    note: form.note.trim() || undefined
  }
}

function recordPayload(item: TransactionRecord): TransactionPayload {
  return {
    type: item.type,
    itemName: item.itemName?.trim() || undefined,
    amount: Number(item.amount),
    occurredAt: toBackendDateTime(toDateTimeLocal(item.occurredAt)),
    channel: item.channel,
    onlineApp: item.channel === 'ONLINE' ? item.onlineApp?.trim() || undefined : undefined,
    onlinePlatformId: item.channel === 'ONLINE' ? item.onlinePlatformId : undefined,
    offlinePlace: item.channel === 'OFFLINE' ? item.offlinePlace?.trim() || undefined : undefined,
    paymentMethodId: item.paymentMethodId,
    categoryId: item.categoryId,
    note: item.note?.trim() || undefined
  }
}

function normalizedPayloadValue(value: unknown) {
  return value ?? null
}

function normalizedDateTimeValue(value: unknown) {
  return typeof value === 'string' ? value.replace(' ', 'T') : normalizedPayloadValue(value)
}

function transactionPayloadChanged(nextPayload: TransactionPayload, currentPayload: TransactionPayload) {
  const keys: Array<keyof TransactionPayload> = [
    'type',
    'itemName',
    'amount',
    'occurredAt',
    'channel',
    'onlineApp',
    'onlinePlatformId',
    'offlinePlace',
    'paymentMethodId',
    'categoryId',
    'note'
  ]
  return keys.some((key) => {
    const normalize = key === 'occurredAt' ? normalizedDateTimeValue : normalizedPayloadValue
    return normalize(nextPayload[key]) !== normalize(currentPayload[key])
  })
}

function hasFieldChanges(payload = transactionPayload()) {
  if (!record.value) return false
  return transactionPayloadChanged(payload, recordPayload(record.value))
}

function validateImageFile(file: File) {
  if (!isAllowedTransactionImageFile(file)) {
    showToast('仅支持 JPG、PNG、WebP、HEIC/HEIF 图片')
    return false
  }
  if (file.size > MAX_TRANSACTION_IMAGE_SIZE) {
    showToast('单张图片不能超过 5MB')
    return false
  }
  return true
}

function beforeReadImage(file: File | File[]) {
  const files = Array.isArray(file) ? file : [file]
  if (imageFiles.value.length + files.length > remainingImageSlots.value) {
    showToast('单笔记录最多上传 3 张图片')
    return false
  }
  if (!files.every(validateImageFile)) {
    return false
  }
  return true
}

function handleImageOversize() {
  showToast('单张图片不能超过 5MB')
}

function imageUploadFailureMessage(error: unknown) {
  const reason = error instanceof Error && error.message ? error.message : ''
  return reason ? `记录已更新，凭证保存失败：${reason}` : '记录已更新，凭证保存失败'
}

function revokeImagePreviewUrls() {
  Object.values(imagePreviewUrls.value).forEach((url) => URL.revokeObjectURL(url))
  imagePreviewUrls.value = {}
}

function abortImageLoad() {
  imageLoadAbortController?.abort()
  imageLoadAbortController = null
}

function cleanupImagePreviews() {
  imageLoadRequestId += 1
  abortImageLoad()
  imageLoading.value = false
  imageLoadFailed.value = false
  revokeImagePreviewUrls()
}

async function loadRecordImageUrls(item: TransactionRecord) {
  const requestId = ++imageLoadRequestId
  abortImageLoad()
  revokeImagePreviewUrls()
  imageLoadFailed.value = false
  if (!item.images?.length) {
    imageLoading.value = false
    return
  }
  const controller = new AbortController()
  imageLoadAbortController = controller
  imageLoading.value = true
  const entries = await Promise.all((item.images || []).map(async (image) => {
    try {
      const blob = await transactionApi.imageBlob(item.id, image.id, controller.signal)
      return [image.id, URL.createObjectURL(blob)] as const
    } catch (error) {
      if (controller.signal.aborted) {
        return null
      }
      console.warn('凭证图片加载失败', error)
      return null
    }
  }))
  if (imageLoadAbortController === controller) {
    imageLoadAbortController = null
  }
  if (requestId !== imageLoadRequestId || controller.signal.aborted) {
    entries.forEach((entry) => {
      if (entry) URL.revokeObjectURL(entry[1])
    })
    return
  }
  imagePreviewUrls.value = Object.fromEntries(entries.filter((entry): entry is readonly [number, string] => Boolean(entry)))
  imageLoadFailed.value = entries.some((entry) => !entry)
  imageLoading.value = false
}

function previewRecordImage(index: number) {
  const urls = recordImages.value
    .map((image) => imagePreviewUrls.value[image.id])
    .filter(Boolean)
  if (urls.length === 0) return
  showImagePreview({
    images: urls,
    startPosition: index,
    closeable: true
  })
}

async function deleteRecordImage(imageId: number) {
  if (imageDeletingId.value !== null) return
  try {
    await showConfirmDialog({ title: '删除图片', message: '确认删除这张凭证图片？' })
  } catch {
    return
  }
  imageDeletingId.value = imageId
  try {
    await transactionApi.deleteImage(recordId(), imageId)
    haptic('warning')
    triggerVisualFeedback('danger')
    showToast('图片已删除')
    await load()
  } catch (error) {
    showError(error, '图片删除失败')
  } finally {
    imageDeletingId.value = null
  }
}

async function loadOptions() {
  if (optionsLoaded.value) {
    return true
  }
  optionsLoading.value = true
  try {
    const [nextCategories, nextMethods, nextPlatforms] = await Promise.all([
      categoryApi.list(),
      paymentMethodApi.list(),
      onlinePlatformApi.list()
    ])
    categories.value = nextCategories
    paymentMethods.value = nextMethods
    onlinePlatforms.value = sortBySortOrder(nextPlatforms)
    optionsLoaded.value = true
    return true
  } catch (error) {
    showError(error, '选项加载失败')
    return false
  } finally {
    optionsLoading.value = false
  }
}

async function startEdit() {
  haptic('tap')
  triggerVisualFeedback('selection')
  if (record.value) {
    const loaded = await loadOptions()
    if (!loaded) {
      return
    }
    fillForm(record.value)
  }
  editMode.value = true
  void scrollSelectedQuickOptions()
}

function cancelEdit() {
  haptic('tap')
  triggerVisualFeedback('selection')
  if (record.value) {
    fillForm(record.value)
  }
  imageFiles.value = []
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
  haptic('selection')
  triggerVisualFeedback('selection')
  form.categoryId = filteredCategories.value[0]?.id
}

function scrollQuickChipIntoView(grid: HTMLElement | null, id: number) {
  const chip = grid?.querySelector<HTMLElement>(`[data-option-id="${id}"]`)
  chip?.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'center' })
}

async function scrollSelectedQuickOptions() {
  await nextTick()
  if (form.categoryId) {
    scrollQuickChipIntoView(categoryChipGridRef.value, form.categoryId)
  }
  if (form.paymentMethodId) {
    scrollQuickChipIntoView(paymentChipGridRef.value, form.paymentMethodId)
  }
  if (form.onlinePlatformId) {
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

async function createCategoryFromEditor() {
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
    await scrollSelectedQuickOptions()
    showToast('分类已创建')
  } catch (error) {
    showError(error, '分类创建失败')
  } finally {
    creatingCategory.value = false
  }
}

async function createPaymentFromEditor() {
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
    await scrollSelectedQuickOptions()
    showToast('支付方式已创建')
  } catch (error) {
    showError(error, '支付方式创建失败')
  } finally {
    creatingPaymentMethod.value = false
  }
}

async function createPlatformFromEditor() {
  if (creatingPlatform.value) return
  const name = suggestedPlatformName.value
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
    platformPopup.value = false
    await scrollSelectedQuickOptions()
    showToast('线上平台已创建')
  } catch (error) {
    showError(error, '线上平台创建失败')
  } finally {
    creatingPlatform.value = false
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
  if (form.channel === 'ONLINE' && form.type === 'EXPENSE' && !form.onlinePlatformId && !form.onlineApp.trim()) {
    haptic('warning')
    triggerVisualFeedback('warning')
    showToast('请选择线上平台')
    return
  }
  const images = selectedImageFiles()
  const payload = transactionPayload()
  const fieldChanged = hasFieldChanges(payload)
  if (!fieldChanged && images.length === 0) {
    haptic('warning')
    triggerVisualFeedback('warning')
    showToast('没有修改内容')
    return
  }
  saving.value = true
  try {
    if (fieldChanged) {
      await transactionApi.update(recordId(), payload)
    }
    if (images.length > 0) {
      imageUploading.value = true
      try {
        await transactionApi.appendImages(recordId(), images)
        imageFiles.value = []
      } catch (error) {
        haptic('warning')
        triggerVisualFeedback('warning')
        showFailToast(imageUploadFailureMessage(error))
        await load()
        return
      } finally {
        imageUploading.value = false
      }
    }
    haptic('confirm')
    triggerVisualFeedback('confirm')
    showToast(fieldChanged && images.length > 0 ? '记录和图片已更新' : images.length > 0 ? '图片已更新' : '记录已更新')
    await new Promise((resolve) => window.setTimeout(resolve, 140))
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
      itemName: item.itemName || '',
      amount: Number(item.amount),
      occurredAt: toBackendDateTime(nowLocalInput()),
      channel: item.channel,
      onlineApp: item.channel === 'ONLINE' ? item.onlineApp : undefined,
      onlinePlatformId: item.channel === 'ONLINE' ? item.onlinePlatformId : undefined,
      offlinePlace: item.channel === 'OFFLINE' ? item.offlinePlace : undefined,
      paymentMethodId: item.paymentMethodId,
      categoryId: item.categoryId,
      note: item.note
    })
    haptic('confirm')
    triggerVisualFeedback('confirm')
    showToast('已复制为新记录')
    resetRecordsQueryPreference()
    await new Promise((resolve) => window.setTimeout(resolve, 120))
    await router.replace({
      path: `/records/${created.id}`,
      query: {}
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
  haptic('tap')
  triggerVisualFeedback('selection')
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
    haptic('warning')
    triggerVisualFeedback('danger')
    showToast('已删除')
    await new Promise((resolve) => window.setTimeout(resolve, 140))
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
  return displayTransactionDateTime(value)
}

watch(() => form.type, ensureCategory)
onMounted(load)
onBeforeUnmount(cleanupImagePreviews)
</script>

<template>
  <main class="page detail-page">
    <van-nav-bar :title="editMode ? '编辑记录' : '记录详情'" left-arrow @click-left="handleBack" />
    <div class="page-content">
      <PageSkeleton v-if="loading" class="section" variant="panel" :cards="3" :rows="3" />

      <template v-else-if="record && !editMode">
        <section
          :class="[
            'section',
            'panel',
            'detail-summary',
            record.type === 'EXPENSE' ? 'detail-summary-expense' : 'detail-summary-income',
            visualFeedback === 'confirm' ? 'ui-feedback-confirm' : '',
            visualFeedback === 'danger' ? 'ui-feedback-danger' : ''
          ]"
        >
          <div class="detail-summary-main">
            <div class="detail-summary-copy">
              <div class="detail-summary-kickers">
                <span>{{ detailTypeText }}</span>
                <span>{{ detailChannelText }}</span>
              </div>
              <h1 class="detail-summary-title">{{ transactionTitle(record) }}</h1>
              <p class="detail-summary-meta">
                <van-icon name="clock-o" />
                <span>{{ displayDateTime(record.occurredAt) }}</span>
              </p>
            </div>
            <div :class="['detail-summary-amount', record.type === 'EXPENSE' ? 'expense' : 'income']">
              {{ record.type === 'EXPENSE' ? '-' : '+' }}¥{{ money(record.amount) }}
            </div>
          </div>
          <div class="detail-summary-chips">
            <span v-for="(chip, index) in detailSummaryChips" :key="`${index}-${chip}`">{{ chip }}</span>
          </div>
        </section>

        <section class="section panel detail-ledger-panel">
          <div class="detail-panel-heading">
            <div>
              <span>账单脉络</span>
              <p>按记录信息顺序阅读</p>
            </div>
          </div>
          <div class="detail-ledger-list">
            <div v-for="item in detailLedgerItems" :key="item.key" class="detail-ledger-item">
              <div class="detail-ledger-icon">
                <van-icon :name="item.icon" />
              </div>
              <div class="detail-ledger-content">
                <div class="detail-ledger-label">{{ item.label }}</div>
                <div class="detail-ledger-value">{{ item.value }}</div>
                <div v-if="item.description" class="detail-ledger-description">{{ item.description }}</div>
              </div>
            </div>
          </div>
        </section>

        <section v-if="recordImages.length" class="section panel detail-images-panel">
          <div class="detail-panel-heading">
            <div>
              <span>凭证图片</span>
              <p>{{ detailImageCountText }}，点击预览</p>
            </div>
          </div>
          <div v-if="imageLoading || imageLoadFailed" class="detail-image-status">
            <van-loading v-if="imageLoading" size="16px">正在加载凭证图片</van-loading>
            <span v-else>部分凭证图片加载失败</span>
          </div>
          <div class="detail-image-grid">
            <button
              v-for="(image, index) in recordImages"
              :key="image.id"
              type="button"
              class="detail-image-thumb"
              :aria-label="`预览凭证图片 ${index + 1}：${image.originalFilename}`"
              @click="previewRecordImage(index)"
            >
              <img v-if="imagePreviewUrls[image.id]" :src="imagePreviewUrls[image.id]" :alt="image.originalFilename" />
              <van-icon v-else name="photo-o" />
            </button>
          </div>
        </section>

        <section :class="['section', 'panel', 'detail-actions', visualFeedback === 'selection' ? 'ui-feedback-selection' : '']">
          <div class="detail-panel-heading">
            <div>
              <span>操作</span>
              <p>编辑、复制或管理这笔记录</p>
            </div>
          </div>
          <div class="detail-main-actions">
            <van-button class="detail-action-button primary" block round type="primary" icon="edit" :loading="optionsLoading" @click="startEdit">
              编辑记录
            </van-button>
            <van-button class="detail-action-button" block round plain type="primary" icon="description-o" :loading="copying" @click="copyRecord">
              复制为今日
            </van-button>
            <van-button class="detail-action-button" block round plain type="primary" icon="replay" @click="createRecurringRule">
              设为周期
            </van-button>
            <van-button class="detail-action-button danger" block plain type="danger" icon="delete-o" :loading="deleting" @click="removeRecord">
              删除记录
            </van-button>
          </div>
        </section>
      </template>

      <van-form v-else-if="record" class="detail-edit-form" @submit="submit">
        <section :class="['section', 'panel', 'detail-edit-summary', visualFeedback === 'warning' ? 'ui-feedback-warning' : '']">
          <div class="detail-edit-heading">
            <div>
              <span class="quick-kicker">EDIT ENTRY</span>
              <strong>{{ form.type === 'EXPENSE' ? '编辑支出' : '编辑收入' }}</strong>
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

          <van-cell-group inset class="quick-cell-group quick-primary-group detail-edit-primary">
            <van-field
              v-model="form.amount"
              class="quick-amount-field"
              label="金额"
              type="text"
              inputmode="decimal"
              placeholder="0.00"
              required
              :style="{ '--quick-amount-input-width': amountInputWidth }"
            />
            <van-field v-model="form.itemName" label="事项" placeholder="如冰棍、工资、泳镜" />
          </van-cell-group>
        </section>

        <section class="section panel detail-edit-ledger">
          <div class="detail-panel-heading">
            <div>
              <span>账单脉络</span>
              <p>与详情页保持同一顺序</p>
            </div>
          </div>

          <div class="detail-edit-ledger-list">
            <ModernDateField v-model="form.occurredAt" mode="datetime" label="时间" title="选择发生时间" required />

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
                <div
                  v-if="form.channel === 'ONLINE'"
                  key="online-platforms"
                  class="minimal-block detail-platform-block"
                >
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

            <van-cell-group inset class="quick-cell-group detail-note-group">
              <van-field v-model="form.note" label="备注" placeholder="可选" />
            </van-cell-group>
          </div>
        </section>

        <section class="section panel detail-edit-images">
          <div class="detail-panel-heading">
            <div>
              <span>凭证图片</span>
              <p>{{ totalImageCount }} / {{ MAX_TRANSACTION_IMAGES }}</p>
            </div>
          </div>
          <div class="quick-image-upload detail-edit-image-upload">
            <div v-if="imageLoading || imageLoadFailed" class="detail-image-status detail-edit-image-status">
              <van-loading v-if="imageLoading" size="16px">正在加载已有凭证</van-loading>
              <span v-else>部分已有凭证加载失败</span>
            </div>
            <div v-if="recordImages.length" class="detail-image-grid detail-edit-image-grid">
              <div v-for="(image, index) in recordImages" :key="image.id" class="detail-image-manage">
                <button
                  type="button"
                  class="detail-image-thumb"
                  :aria-label="`预览凭证图片 ${index + 1}：${image.originalFilename}`"
                  @click="previewRecordImage(index)"
                >
                  <img v-if="imagePreviewUrls[image.id]" :src="imagePreviewUrls[image.id]" :alt="image.originalFilename" />
                  <van-icon v-else name="photo-o" />
                </button>
                <button
                  type="button"
                  class="detail-image-delete"
                  :disabled="imageDeletingId === image.id"
                  :aria-label="`删除${image.originalFilename}`"
                  @click="deleteRecordImage(image.id)"
                >
                  <van-loading v-if="imageDeletingId === image.id" size="14" />
                  <van-icon v-else name="cross" />
                </button>
              </div>
            </div>
            <van-uploader
              v-if="remainingImageSlots > 0"
              v-model="imageFiles"
              multiple
              result-type="file"
              :accept="TRANSACTION_IMAGE_ACCEPT"
              upload-icon="photograph"
              upload-text="上传"
              :max-count="remainingImageSlots"
              :max-size="MAX_TRANSACTION_IMAGE_SIZE"
              :before-read="beforeReadImage"
              @oversize="handleImageOversize"
            />
          </div>
        </section>

        <BottomSheet
          v-model:show="categoryPopup"
          title="选择分类"
          header-variant="toolbar"
          sheet-class="quick-choice-shell"
          body-class="quick-choice-body"
          :close-on-click-overlay="!creatingCategory"
          :close-disabled="creatingCategory"
        >
          <template #leading="{ close }">
            <button type="button" class="quick-choice-cancel" :disabled="creatingCategory" @click="close"><van-icon name="cross" /><span>取消</span></button>
          </template>
          <template #actions><span /></template>
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
              <van-field v-model="newCategoryName" label="新增" placeholder="分类名称" autocomplete="off" @keyup.enter="createCategoryFromEditor" />
              <van-button type="primary" icon="plus" :loading="creatingCategory" native-type="button" @click="createCategoryFromEditor">添加</van-button>
            </div>
        </BottomSheet>

        <BottomSheet
          v-model:show="paymentPopup"
          title="选择支付方式"
          header-variant="toolbar"
          sheet-class="quick-choice-shell"
          body-class="quick-choice-body"
          :close-on-click-overlay="!creatingPaymentMethod"
          :close-disabled="creatingPaymentMethod"
        >
          <template #leading="{ close }">
            <button type="button" class="quick-choice-cancel" :disabled="creatingPaymentMethod" @click="close"><van-icon name="cross" /><span>取消</span></button>
          </template>
          <template #actions><span /></template>
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
              <van-field v-model="newPaymentMethodName" label="新增" placeholder="支付方式名称" autocomplete="off" @keyup.enter="createPaymentFromEditor" />
              <van-button type="primary" icon="plus" :loading="creatingPaymentMethod" native-type="button" @click="createPaymentFromEditor">添加</van-button>
            </div>
        </BottomSheet>

        <BottomSheet
          v-model:show="platformPopup"
          title="选择线上平台"
          header-variant="toolbar"
          sheet-class="quick-choice-shell"
          body-class="quick-choice-body"
          :close-on-click-overlay="!creatingPlatform"
          :close-disabled="creatingPlatform"
        >
          <template #leading="{ close }">
            <button type="button" class="quick-choice-cancel" :disabled="creatingPlatform" @click="close"><van-icon name="cross" /><span>取消</span></button>
          </template>
          <template #actions><span /></template>
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
              <div v-if="platformSearchCreateName" class="quick-choice-empty">
                <van-icon name="search" />
                <span>没有找到“{{ platformSearchCreateName }}”</span>
                <button type="button" @click="createPlatformFromEditor">添加为平台</button>
              </div>
            </div>
            <div class="quick-create-row">
              <van-field v-model="newPlatformName" label="新增" placeholder="平台名称" autocomplete="off" @keyup.enter="createPlatformFromEditor" />
              <van-button type="primary" icon="plus" :loading="creatingPlatform" native-type="button" @click="createPlatformFromEditor">
                {{ suggestedPlatformName ? '添加建议' : '添加' }}
              </van-button>
            </div>
        </BottomSheet>

        <FormActionBar :confirm="visualFeedback === 'confirm'" spacer-height="128px">
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
        </FormActionBar>
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

.detail-empty {
  display: grid;
  justify-items: center;
  gap: var(--space-12);
}

.quick-kicker {
  color: var(--primary);
  font-size: var(--font-size-caption);
  font-weight: 750;
  line-height: var(--line-height-caption);
}

.quick-type-switch,
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
.quick-channel-switch::before {
  position: absolute;
  top: var(--space-4);
  bottom: var(--space-4);
  left: var(--space-4);
  z-index: 0;
  width: calc((100% - var(--space-12)) / 2);
  border-radius: var(--radius-pill);
  background: var(--glass-strong-bg);
  box-shadow: var(--shadow-sm);
  content: '';
  transition: transform 220ms cubic-bezier(0.22, 1, 0.36, 1);
}

.quick-type-switch.is-right::before,
.quick-channel-switch.is-right::before {
  transform: translateX(calc(100% + var(--space-4)));
}

.quick-type-switch :deep(.van-radio),
.quick-channel-switch :deep(.van-radio) {
  z-index: 1;
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
  color: var(--primary);
}

.quick-cell-group {
  margin: 0;
}

.detail-edit-form :deep(.van-cell-group--inset) {
  border-radius: var(--radius-card);
}

.detail-edit-form :deep(.van-field__control) {
  font-size: var(--font-size-section-title);
  line-height: var(--line-height-body);
}

.quick-amount-field :deep(.van-field__control) {
  flex: 0 0 var(--quick-amount-input-width);
  width: var(--quick-amount-input-width);
  font-size: calc(var(--font-size-amount) + 6px);
  font-weight: 780;
  line-height: var(--line-height-amount);
  text-align: left;
}

.quick-amount-field :deep(.van-field__label) {
  display: none;
}

.quick-amount-field :deep(.van-field__body) {
  width: fit-content;
  max-width: 100%;
  margin: 0 auto;
  gap: var(--space-4);
  align-self: center;
}

.quick-amount-field :deep(.van-field__body)::before {
  content: '¥';
  display: inline-flex;
  align-items: center;
  height: var(--line-height-amount);
  color: var(--text-secondary);
  font-size: calc(var(--font-size-amount) + 6px);
  font-weight: 780;
  line-height: var(--line-height-amount);
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

.minimal-row,
.minimal-block,
.minimal-place-block {
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

.minimal-place-block {
  overflow: visible;
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
  box-shadow: var(--inset-primary);
}

.channel-content-switch {
  position: relative;
  overflow: hidden;
  transform: translateZ(0);
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

.quick-image-upload {
  display: grid;
  gap: var(--space-10);
  padding: var(--space-12) var(--space-16) var(--space-16);
  border-top: 1px solid rgba(var(--theme-border-warm-rgb), 0.16);
}

.quick-image-upload :deep(.van-uploader__upload),
.quick-image-upload :deep(.van-uploader__preview-image) {
  border-radius: var(--radius-card);
  background: rgba(var(--theme-border-warm-rgb), 0.08);
}

.quick-image-upload :deep(.van-uploader__upload) {
  border: 1px dashed rgba(var(--theme-border-warm-rgb), 0.38);
}

.detail-edit-image-upload {
  padding-inline: 0;
}

.detail-edit-image-grid {
  padding: 0;
}

:deep(.bottom-sheet.quick-choice-shell) {
  height: min(78vh, 620px);
  max-height: min(78vh, 620px);
  background: var(--page-bg-soft);
}

:deep(.bottom-sheet__body.quick-choice-body) {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  overflow: hidden;
  padding: var(--space-0) var(--space-0) max(var(--space-12), env(safe-area-inset-bottom));
}

.quick-choice-cancel {
  display: inline-flex;
  align-items: center;
  gap: var(--space-3);
  border: 0;
  background: transparent;
  color: var(--text-secondary);
  font: inherit;
}

.quick-choice-cancel:disabled {
  color: var(--text-muted);
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

.quick-choice-empty {
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr) auto;
  gap: var(--space-8);
  align-items: center;
  border: 1px dashed rgba(var(--theme-primary-glow-rgb), 0.42);
  border-radius: var(--radius-card);
  padding: var(--space-10) var(--space-12);
  background: var(--primary-soft);
  color: var(--text-secondary);
}

.quick-choice-empty > .van-icon {
  color: var(--primary);
}

.quick-choice-empty span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.quick-choice-empty button {
  border: 0;
  background: transparent;
  color: var(--primary);
  font-weight: 700;
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
  box-shadow: var(--inset-primary-subtle);
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
  box-shadow: var(--inset-primary-strong);
}

.detail-page .page-content {
  gap: var(--space-12);
}

.detail-summary {
  display: grid;
  gap: var(--space-12);
  overflow: hidden;
  border-radius: var(--radius-floating);
  padding: var(--space-16);
  box-shadow: var(--shadow-md);
}

.detail-summary-expense {
  background:
    radial-gradient(circle at 88% 8%, rgba(var(--expense-rgb), 0.2), transparent 34%),
    var(--card-bg);
}

.detail-summary-income {
  background:
    radial-gradient(circle at 88% 8%, rgba(var(--income-rgb), 0.2), transparent 34%),
    var(--card-bg);
}

.detail-summary-main {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: var(--space-12);
  align-items: start;
}

.detail-summary-copy {
  min-width: 0;
}

.detail-summary-kickers,
.detail-summary-chips {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-8);
}

.detail-summary-kickers span,
.detail-summary-chips span {
  max-width: 100%;
  min-height: var(--space-24);
  border: 1px solid rgba(var(--theme-border-warm-rgb), 0.16);
  border-radius: var(--radius-pill);
  padding: var(--space-3) var(--space-8);
  background: var(--primary-soft);
  color: var(--text-main);
  font-size: var(--font-size-caption);
  font-weight: 700;
  line-height: var(--line-height-caption);
  overflow-wrap: anywhere;
}

.detail-summary-kickers span {
  color: var(--primary);
}

.detail-summary-title {
  margin: var(--space-10) 0 var(--space-0);
  overflow-wrap: anywhere;
  color: var(--text-main);
  font-size: var(--font-size-panel-title);
  font-weight: 780;
  line-height: var(--line-height-panel-title);
}

.detail-summary-meta {
  display: inline-flex;
  align-items: center;
  gap: var(--space-4);
  margin: var(--space-5) 0 var(--space-0);
  color: var(--text-secondary);
  font-size: var(--font-size-caption);
  font-weight: 650;
  line-height: var(--line-height-caption);
}

.detail-summary-amount {
  max-width: 100%;
  font-size: var(--font-size-amount-large);
  font-weight: 850;
  line-height: var(--line-height-amount-large);
  text-align: right;
  white-space: nowrap;
}

.detail-panel-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-10);
  margin-bottom: var(--space-12);
}

.detail-panel-heading span {
  color: var(--text-main);
  font-size: var(--font-size-body-strong);
  font-weight: 760;
  line-height: var(--line-height-body-strong);
}

.detail-panel-heading p {
  margin: var(--space-2) 0 var(--space-0);
  color: var(--text-secondary);
  font-size: var(--font-size-caption);
  line-height: var(--line-height-caption);
}

.detail-ledger-panel,
.detail-images-panel,
.detail-actions,
.detail-edit-summary,
.detail-edit-ledger,
.detail-edit-images {
  padding: var(--space-14);
}

.detail-ledger-list {
  display: grid;
}

.detail-ledger-item {
  position: relative;
  display: grid;
  grid-template-columns: var(--space-34) minmax(0, 1fr);
  gap: var(--space-10);
  min-width: 0;
  padding-bottom: var(--space-14);
}

.detail-ledger-item:not(:last-child)::before {
  position: absolute;
  top: var(--space-38);
  bottom: var(--space-4);
  left: var(--space-16);
  width: 1px;
  background: rgba(var(--theme-border-warm-rgb), 0.18);
  content: '';
}

.detail-ledger-item:last-child {
  padding-bottom: var(--space-0);
}

.detail-ledger-icon {
  display: grid;
  width: var(--space-34);
  height: var(--space-34);
  place-items: center;
  border: 1px solid rgba(var(--theme-border-warm-rgb), 0.16);
  border-radius: var(--radius-card);
  background: var(--primary-soft);
  color: var(--primary);
}

.detail-ledger-icon :deep(.van-icon) {
  font-size: var(--icon-size-md);
}

.detail-ledger-content {
  min-width: 0;
}

.detail-ledger-label {
  color: var(--text-muted);
  font-size: var(--font-size-caption);
  line-height: var(--line-height-caption);
}

.detail-ledger-value {
  margin-top: var(--space-2);
  overflow-wrap: anywhere;
  color: var(--text-main);
  font-size: var(--font-size-body-strong);
  font-weight: 720;
  line-height: var(--line-height-body-strong);
  white-space: pre-wrap;
}

.detail-ledger-description {
  margin-top: var(--space-2);
  color: var(--text-secondary);
  font-size: var(--font-size-caption);
  line-height: var(--line-height-caption);
}

.detail-image-status {
  display: flex;
  align-items: center;
  min-height: var(--space-28);
  padding: var(--space-0) var(--space-0) var(--space-10);
  color: var(--text-secondary);
  font-size: var(--font-size-caption);
  line-height: var(--line-height-caption);
}

.detail-edit-image-status {
  padding: 0;
}

.detail-image-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-10);
  padding: 0;
}

.detail-image-thumb {
  display: grid;
  width: 100%;
  aspect-ratio: 1;
  place-items: center;
  overflow: hidden;
  border: 1px solid rgba(var(--theme-border-warm-rgb), 0.2);
  border-radius: var(--radius-card);
  background: rgba(var(--theme-border-warm-rgb), 0.08);
  color: var(--text-secondary);
}

.detail-image-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.detail-image-thumb :deep(.van-icon) {
  font-size: var(--icon-size-lg);
}

.detail-image-manage {
  position: relative;
  min-width: 0;
}

.detail-image-delete {
  position: absolute;
  top: var(--space-4);
  right: var(--space-4);
  display: grid;
  width: var(--space-28);
  height: var(--space-28);
  place-items: center;
  border: 0;
  border-radius: var(--radius-pill);
  background: var(--glass-strong-bg);
  color: var(--text-main);
  -webkit-backdrop-filter: blur(10px);
  backdrop-filter: blur(10px);
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

.detail-action-button {
  min-height: 46px;
  border-radius: var(--radius-card);
}

.detail-action-button.danger {
  border-color: rgba(var(--expense-rgb), 0.22);
  background: rgba(var(--expense-rgb), 0.08);
}

.detail-edit-form {
  display: grid;
  gap: var(--space-12);
  margin: 0;
}

.detail-edit-summary {
  display: grid;
  gap: var(--space-12);
  background:
    radial-gradient(circle at 88% 4%, rgba(var(--theme-primary-glow-rgb), 0.2), transparent 36%),
    var(--card-bg);
}

.detail-edit-heading {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: var(--space-12);
  align-items: center;
}

.detail-edit-heading strong {
  display: block;
  margin-top: var(--space-3);
  color: var(--text-main);
  font-size: var(--font-size-panel-title);
  line-height: var(--line-height-panel-title);
}

.detail-edit-primary,
.detail-note-group {
  margin: 0;
}

.detail-edit-ledger-list {
  display: grid;
  gap: var(--space-12);
}

.detail-edit-images .quick-image-upload {
  padding: 0;
  border-top: 0;
}

@media (prefers-reduced-motion: reduce) {
  .channel-slide-left-enter-active,
  .channel-slide-left-leave-active,
  .channel-slide-right-enter-active,
  .channel-slide-right-leave-active {
    transition: none;
  }
}

@media (max-width: 360px) {
  .detail-summary-main,
  .detail-edit-heading,
  .detail-main-actions,
  .minimal-row {
    grid-template-columns: 1fr;
  }

  .detail-summary-amount {
    text-align: left;
    white-space: normal;
  }

  .quick-type-switch,
  .quick-channel-switch {
    width: 100%;
  }
}
</style>
