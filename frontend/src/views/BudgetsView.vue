<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { budgetApi, categoryApi } from '@/api/services'
import ModernDateField from '@/components/ModernDateField.vue'
import ModernSelectField from '@/components/ModernSelectField.vue'
import type { Budget, Category } from '@/types'
import { currentMonth, money } from '@/utils/date'
import { showError } from '@/utils/errors'
import { moneyError } from '@/utils/money'
import { requiredText } from '@/utils/validation'

const route = useRoute()
const budgets = ref<Budget[]>([])
const categories = ref<Category[]>([])
const editingId = ref<number | null>(null)
const saving = ref(false)

function firstQueryValue(value: unknown) {
  if (Array.isArray(value)) return value[0]
  return typeof value === 'string' ? value : undefined
}

function initialMonth() {
  const value = firstQueryValue(route.query.month)
  return value && /^\d{4}-\d{2}$/.test(value) ? value : currentMonth()
}

const form = reactive({
  month: initialMonth(),
  categoryId: '' as number | '',
  amount: '0'
})
const categoryOptions = computed(() => [
  { label: '整月总预算', value: '' },
  ...categories.value.map((item) => ({
    label: item.name,
    value: item.id,
    icon: item.icon || 'records-o',
    color: item.color
  }))
])

function categoryName(id?: number) {
  if (!id) return '整月总预算'
  return categories.value.find((item) => item.id === id)?.name || '未知分类'
}

async function load() {
  try {
    categories.value = await categoryApi.list('EXPENSE')
    budgets.value = await budgetApi.list(form.month)
  } catch (error) {
    showError(error, '预算加载失败')
  }
}

function resetForm() {
  editingId.value = null
  form.categoryId = ''
  form.amount = '0'
}

function edit(item: Budget) {
  editingId.value = item.id
  form.month = item.month
  form.categoryId = item.categoryId || ''
  form.amount = String(item.amount)
}

function setCategory(value: string | number | undefined) {
  form.categoryId = typeof value === 'number' ? value : ''
}

function hasDuplicateBudget(month: string, categoryId: number | undefined) {
  const normalizedCategoryId = categoryId ?? ''
  return budgets.value.some((item) =>
    item.id !== editingId.value &&
    item.month === month &&
    (item.categoryId ?? '') === normalizedCategoryId
  )
}

async function submit() {
  if (saving.value) return
  const monthError = requiredText(form.month, '月份')
  if (monthError) {
    showToast(monthError)
    return
  }
  const amountError = moneyError(form.amount)
  if (amountError) {
    showToast(amountError)
    return
  }
  const payload = {
    month: form.month,
    categoryId: form.categoryId === '' ? undefined : form.categoryId,
    amount: Number(form.amount)
  }
  if (hasDuplicateBudget(payload.month, payload.categoryId)) {
    showToast(`${categoryName(payload.categoryId)}已设置该月份预算`)
    return
  }
  saving.value = true
  try {
    if (editingId.value) {
      await budgetApi.update(editingId.value, payload)
      showToast('预算已更新')
    } else {
      await budgetApi.create(payload)
      showToast('预算已创建')
    }
    resetForm()
    await load()
  } catch (error) {
    showError(error, editingId.value ? '预算更新失败' : '预算创建失败')
  } finally {
    saving.value = false
  }
}

async function remove(id: number) {
  try {
    await showConfirmDialog({ title: '删除预算', message: '确认删除这条预算？' })
  } catch {
    return
  }
  try {
    await budgetApi.remove(id)
    showToast('已删除')
    if (editingId.value === id) {
      resetForm()
    }
    await load()
  } catch (error) {
    showError(error, '删除失败')
  }
}

onMounted(load)
</script>

<template>
  <main class="page">
    <van-nav-bar title="预算管理" left-arrow @click-left="$router.back()" />
    <div class="page-content">
      <section class="section panel">
        <van-form @submit="submit">
          <ModernDateField v-model="form.month" mode="month" label="月份" title="选择月份" @change="load" />
          <ModernSelectField
            :model-value="form.categoryId"
            label="分类"
            title="选择预算分类"
            :options="categoryOptions"
            @update:model-value="setCategory"
          />
          <van-field v-model="form.amount" type="text" inputmode="decimal" label="金额" required />
          <van-button block round type="primary" native-type="submit" :loading="saving">
            {{ editingId ? '保存修改' : '新增预算' }}
          </van-button>
          <van-button v-if="editingId" block round plain type="default" native-type="button" class="secondary-action" @click="resetForm">
            取消编辑
          </van-button>
        </van-form>
      </section>

      <section class="section panel">
        <div v-if="budgets.length === 0" class="empty-text">暂无预算</div>
        <van-swipe-cell v-for="item in budgets" :key="item.id">
          <van-cell :title="categoryName(item.categoryId)" :label="item.month" :value="`¥${money(item.amount)}`" is-link @click="edit(item)" />
          <template #right>
            <van-button square type="primary" text="编辑" @click="edit(item)" />
            <van-button square type="danger" text="删除" @click="remove(item.id)" />
          </template>
        </van-swipe-cell>
      </section>
    </div>
  </main>
</template>

<style scoped>
.secondary-action {
  margin-top: 10px;
}
</style>
