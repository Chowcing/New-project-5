<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { showConfirmDialog, showToast } from 'vant'
import { budgetApi, categoryApi } from '@/api/services'
import type { Budget, Category } from '@/types'
import { currentMonth, money } from '@/utils/date'
import { showError } from '@/utils/errors'

const budgets = ref<Budget[]>([])
const categories = ref<Category[]>([])
const editingId = ref<number | null>(null)
const form = reactive({
  month: currentMonth(),
  categoryId: '' as number | '',
  amount: 0
})

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
  form.amount = 0
}

function edit(item: Budget) {
  editingId.value = item.id
  form.month = item.month
  form.categoryId = item.categoryId || ''
  form.amount = item.amount
}

async function submit() {
  const payload = {
    month: form.month,
    categoryId: form.categoryId === '' ? undefined : form.categoryId,
    amount: Number(form.amount)
  }
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
          <van-field v-model="form.month" type="month" label="月份" @change="load" />
          <van-field label="分类">
            <template #input>
              <select v-model.number="form.categoryId" class="native-select">
                <option value="">整月总预算</option>
                <option v-for="item in categories" :key="item.id" :value="item.id">{{ item.name }}</option>
              </select>
            </template>
          </van-field>
          <van-field v-model.number="form.amount" type="number" label="金额" required />
          <van-button block round type="primary" native-type="submit">
            {{ editingId ? '保存修改' : '新增预算' }}
          </van-button>
          <van-button v-if="editingId" block round plain type="default" class="secondary-action" @click="resetForm">
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
.native-select {
  width: 100%;
  border: 0;
  background: transparent;
  color: #1f2933;
  font: inherit;
}

.secondary-action {
  margin-top: 10px;
}
</style>
