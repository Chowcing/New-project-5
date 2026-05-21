<script setup lang="ts">
import { computed, ref } from 'vue'
import { showToast } from 'vant'
import { categoryApi, paymentMethodApi } from '@/api/services'
import ModernSelectField from '@/components/ModernSelectField.vue'
import type { Category, PaymentMethod } from '@/types'
import { showError } from '@/utils/errors'
import { maxTextLength, requiredText } from '@/utils/validation'

const props = defineProps<{
  categories: Category[]
  paymentMethods: PaymentMethod[]
  transactionType: 'EXPENSE' | 'INCOME'
  categoryId?: number
  paymentMethodId?: number
}>()

const emit = defineEmits<{
  'update:categoryId': [value: number | undefined]
  'update:paymentMethodId': [value: number | undefined]
  'category-created': [value: Category]
  'payment-method-created': [value: PaymentMethod]
}>()

const categoryPopup = ref(false)
const paymentPopup = ref(false)
const categoryName = ref('')
const paymentMethodName = ref('')
const creatingCategory = ref(false)
const creatingPaymentMethod = ref(false)

const filteredCategories = computed(() => props.categories.filter((item) => item.type === props.transactionType))
const paymentMethodOptions = computed(() => props.paymentMethods.map((item) => ({
  label: item.name,
  value: item.id,
  icon: item.icon || 'balance-o'
})))
const categoryOptions = computed(() => filteredCategories.value.map((item) => ({
  label: item.name,
  value: item.id,
  icon: item.icon || 'records-o',
  color: item.color
})))

function nextSortOrder(items: Array<{ sortOrder?: number }>) {
  const maxOrder = items.reduce((max, item) => Math.max(max, item.sortOrder || 0), 0)
  return maxOrder + 10
}

function normalizeName(value: string) {
  return value.trim().toLowerCase()
}

function categoryDefaults() {
  if (props.transactionType === 'INCOME') {
    return { icon: 'cash-back-record', color: '#6f8f4e' }
  }
  return { icon: 'records-o', color: '#c96f3a' }
}

function updatePaymentMethod(value: string | number | undefined) {
  emit('update:paymentMethodId', typeof value === 'number' ? value : undefined)
}

function updateCategory(value: string | number | undefined) {
  emit('update:categoryId', typeof value === 'number' ? value : undefined)
}

function openCategoryPopup() {
  categoryName.value = ''
  categoryPopup.value = true
}

function openPaymentPopup() {
  paymentMethodName.value = ''
  paymentPopup.value = true
}

async function createCategory() {
  if (creatingCategory.value) return
  const nameError = requiredText(categoryName.value, '分类名称') || maxTextLength(categoryName.value, '分类名称', 32)
  if (nameError) {
    showToast(nameError)
    return
  }
  if (filteredCategories.value.some((item) => normalizeName(item.name) === normalizeName(categoryName.value))) {
    showToast(`${props.transactionType === 'EXPENSE' ? '支出' : '收入'}分类已存在`)
    return
  }

  creatingCategory.value = true
  try {
    const defaults = categoryDefaults()
    const created = await categoryApi.create({
      name: categoryName.value.trim(),
      type: props.transactionType,
      icon: defaults.icon,
      color: defaults.color,
      sortOrder: nextSortOrder(filteredCategories.value)
    })
    emit('category-created', created)
    emit('update:categoryId', created.id)
    categoryPopup.value = false
    showToast('分类已创建')
  } catch (error) {
    showError(error, '分类创建失败')
  } finally {
    creatingCategory.value = false
  }
}

async function createPaymentMethod() {
  if (creatingPaymentMethod.value) return
  const nameError = requiredText(paymentMethodName.value, '支付方式名称') || maxTextLength(paymentMethodName.value, '支付方式名称', 64)
  if (nameError) {
    showToast(nameError)
    return
  }
  if (props.paymentMethods.some((item) => normalizeName(item.name) === normalizeName(paymentMethodName.value))) {
    showToast('支付方式已存在')
    return
  }

  creatingPaymentMethod.value = true
  try {
    const created = await paymentMethodApi.create({
      name: paymentMethodName.value.trim(),
      icon: 'balance-o',
      sortOrder: nextSortOrder(props.paymentMethods)
    })
    emit('payment-method-created', created)
    emit('update:paymentMethodId', created.id)
    paymentPopup.value = false
    showToast('支付方式已创建')
  } catch (error) {
    showError(error, '支付方式创建失败')
  } finally {
    creatingPaymentMethod.value = false
  }
}
</script>

<template>
  <ModernSelectField
    :model-value="categoryId"
    label="分类"
    title="选择分类"
    placeholder="请选择分类"
    :options="categoryOptions"
    required
    @update:model-value="updateCategory"
  >
    <template #button>
      <van-button
        class="quick-option-add"
        size="small"
        type="primary"
        plain
        icon="plus"
        native-type="button"
        aria-label="新增分类"
        title="新增分类"
        @click.stop="openCategoryPopup"
      />
    </template>
  </ModernSelectField>

  <ModernSelectField
    :model-value="paymentMethodId"
    label="支付方式"
    title="选择支付方式"
    placeholder="请选择支付方式"
    :options="paymentMethodOptions"
    required
    @update:model-value="updatePaymentMethod"
  >
    <template #button>
      <van-button
        class="quick-option-add"
        size="small"
        type="primary"
        plain
        icon="plus"
        native-type="button"
        aria-label="新增支付方式"
        title="新增支付方式"
        @click.stop="openPaymentPopup"
      />
    </template>
  </ModernSelectField>

  <van-popup v-model:show="paymentPopup" position="bottom" round>
    <div class="quick-option-popup">
      <van-cell title="新增支付方式" />
      <van-field
        v-model="paymentMethodName"
        label="名称"
        placeholder="如微信、支付宝、银行卡"
        required
        autofocus
        @keyup.enter="createPaymentMethod"
      />
      <div class="quick-option-actions">
        <van-button block round type="primary" native-type="button" :loading="creatingPaymentMethod" @click="createPaymentMethod">
          保存
        </van-button>
      </div>
    </div>
  </van-popup>

  <van-popup v-model:show="categoryPopup" position="bottom" round>
    <div class="quick-option-popup">
      <van-cell :title="transactionType === 'EXPENSE' ? '新增支出分类' : '新增收入分类'" />
      <van-field
        v-model="categoryName"
        label="名称"
        placeholder="如餐饮、工资、交通"
        required
        autofocus
        @keyup.enter="createCategory"
      />
      <div class="quick-option-actions">
        <van-button block round type="primary" native-type="button" :loading="creatingCategory" @click="createCategory">
          保存
        </van-button>
      </div>
    </div>
  </van-popup>
</template>

<style scoped>
.quick-option-add {
  width: 34px;
  height: 30px;
  padding: 0;
}

.quick-option-popup {
  padding: 8px 0 max(16px, env(safe-area-inset-bottom));
  background: var(--card-bg);
}

.quick-option-actions {
  padding: 14px 12px 0;
}
</style>
