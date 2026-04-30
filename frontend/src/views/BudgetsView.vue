<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { showConfirmDialog, showToast } from 'vant'
import { budgetApi, categoryApi } from '@/api/services'
import type { Budget, Category } from '@/types'
import { currentMonth, money } from '@/utils/date'

const budgets = ref<Budget[]>([])
const categories = ref<Category[]>([])
const form = reactive({
  month: currentMonth(),
  categoryId: undefined as number | undefined,
  amount: 0
})

function categoryName(id?: number) {
  if (!id) return '整月总预算'
  return categories.value.find((item) => item.id === id)?.name || '未知分类'
}

async function load() {
  categories.value = await categoryApi.list('EXPENSE')
  budgets.value = await budgetApi.list(form.month)
}

async function submit() {
  await budgetApi.create({ month: form.month, categoryId: form.categoryId, amount: form.amount })
  showToast('预算已创建')
  form.amount = 0
  await load()
}

async function remove(id: number) {
  await showConfirmDialog({ title: '删除预算', message: '确认删除这条预算？' })
  await budgetApi.remove(id)
  showToast('已删除')
  await load()
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
                <option :value="undefined">整月总预算</option>
                <option v-for="item in categories" :key="item.id" :value="item.id">{{ item.name }}</option>
              </select>
            </template>
          </van-field>
          <van-field v-model.number="form.amount" type="number" label="金额" required />
          <van-button block round type="primary" native-type="submit">新增预算</van-button>
        </van-form>
      </section>

      <section class="section panel">
        <div v-if="budgets.length === 0" class="empty-text">暂无预算</div>
        <van-swipe-cell v-for="item in budgets" :key="item.id">
          <van-cell :title="categoryName(item.categoryId)" :label="item.month" :value="`¥${money(item.amount)}`" />
          <template #right>
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
</style>

