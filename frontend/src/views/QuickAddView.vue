<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { accountApi, categoryApi, transactionApi } from '@/api/services'
import type { Account, Category } from '@/types'
import { nowLocalInput, toBackendDateTime } from '@/utils/date'

const router = useRouter()
const categories = ref<Category[]>([])
const accounts = ref<Account[]>([])
const form = reactive({
  type: 'EXPENSE' as 'EXPENSE' | 'INCOME',
  amount: '',
  occurredAt: nowLocalInput(),
  categoryId: undefined as number | undefined,
  accountId: undefined as number | undefined,
  note: ''
})

const filteredCategories = computed(() => categories.value.filter((item) => item.type === form.type))

async function loadOptions() {
  categories.value = await categoryApi.list()
  accounts.value = await accountApi.list()
  form.categoryId = filteredCategories.value[0]?.id
  form.accountId = accounts.value[0]?.id
}

async function submit() {
  if (!form.categoryId || !form.accountId) {
    showToast('请先创建分类和账户')
    return
  }
  await transactionApi.create({
    type: form.type,
    amount: Number(form.amount),
    occurredAt: toBackendDateTime(form.occurredAt),
    categoryId: form.categoryId,
    accountId: form.accountId,
    note: form.note
  })
  showToast('记录已保存')
  await router.push('/records')
}

onMounted(loadOptions)
</script>

<template>
  <main class="page">
    <van-nav-bar title="快速记一笔" />
    <van-form @submit="submit">
      <van-cell-group inset>
        <van-field label="类型">
          <template #input>
            <van-radio-group v-model="form.type" direction="horizontal" @change="form.categoryId = filteredCategories[0]?.id">
              <van-radio name="EXPENSE">支出</van-radio>
              <van-radio name="INCOME">收入</van-radio>
            </van-radio-group>
          </template>
        </van-field>
        <van-field v-model="form.amount" label="金额" type="number" placeholder="0.00" required />
        <van-field v-model="form.occurredAt" label="时间" type="datetime-local" required />
        <van-field label="分类">
          <template #input>
            <select v-model.number="form.categoryId" class="native-select">
              <option v-for="item in filteredCategories" :key="item.id" :value="item.id">{{ item.name }}</option>
            </select>
          </template>
        </van-field>
        <van-field label="账户">
          <template #input>
            <select v-model.number="form.accountId" class="native-select">
              <option v-for="item in accounts" :key="item.id" :value="item.id">{{ item.name }}</option>
            </select>
          </template>
        </van-field>
        <van-field v-model="form.note" label="备注" placeholder="可选" />
      </van-cell-group>
      <div class="form-actions">
        <van-button round block type="primary" native-type="submit">保存</van-button>
      </div>
    </van-form>
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

