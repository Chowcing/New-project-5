<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { showConfirmDialog, showToast } from 'vant'
import { accountApi } from '@/api/services'
import type { Account } from '@/types'
import { money } from '@/utils/date'

const accounts = ref<Account[]>([])
const form = reactive({ name: '', type: 'CASH', balance: 0, sortOrder: 0 })

async function load() {
  accounts.value = await accountApi.list()
}

async function submit() {
  await accountApi.create({ ...form })
  showToast('账户已创建')
  form.name = ''
  form.balance = 0
  await load()
}

async function remove(id: number) {
  await showConfirmDialog({ title: '删除账户', message: '已被记录使用的账户可能无法删除。确认继续？' })
  await accountApi.remove(id)
  showToast('已删除')
  await load()
}

onMounted(load)
</script>

<template>
  <main class="page">
    <van-nav-bar title="账户管理" left-arrow @click-left="$router.back()" />
    <div class="page-content">
      <section class="section panel">
        <van-form @submit="submit">
          <van-field v-model="form.name" label="名称" placeholder="如现金、银行卡" required />
          <van-field v-model="form.type" label="类型" placeholder="CASH/BANK/ALIPAY" required />
          <van-field v-model.number="form.balance" type="number" label="余额" />
          <van-button block round type="primary" native-type="submit">新增账户</van-button>
        </van-form>
      </section>

      <section class="section panel">
        <van-swipe-cell v-for="item in accounts" :key="item.id">
          <van-cell :title="item.name" :label="item.type" :value="`¥${money(item.balance)}`" />
          <template #right>
            <van-button square type="danger" text="删除" @click="remove(item.id)" />
          </template>
        </van-swipe-cell>
      </section>
    </div>
  </main>
</template>

