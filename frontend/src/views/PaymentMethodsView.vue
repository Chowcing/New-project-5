<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { showConfirmDialog, showToast } from 'vant'
import { paymentMethodApi } from '@/api/services'
import type { PaymentMethod } from '@/types'

const methods = ref<PaymentMethod[]>([])
const form = reactive({ name: '', icon: 'balance-o', sortOrder: 0 })

async function load() {
  methods.value = await paymentMethodApi.list()
}

async function submit() {
  await paymentMethodApi.create({ ...form })
  showToast('支付方式已创建')
  form.name = ''
  await load()
}

async function remove(id: number) {
  await showConfirmDialog({ title: '删除支付方式', message: '已被记录使用的支付方式可能无法删除。确认继续？' })
  await paymentMethodApi.remove(id)
  showToast('已删除')
  await load()
}

onMounted(load)
</script>

<template>
  <main class="page">
    <van-nav-bar title="支付方式管理" left-arrow @click-left="$router.back()" />
    <div class="page-content">
      <section class="section panel">
        <van-form @submit="submit">
          <van-field v-model="form.name" label="名称" placeholder="如微信、支付宝、现金" required />
          <van-field v-model="form.icon" label="图标" placeholder="Vant 图标名，可选" />
          <van-field v-model.number="form.sortOrder" type="number" label="排序" />
          <van-button block round type="primary" native-type="submit">新增支付方式</van-button>
        </van-form>
      </section>

      <section class="section panel">
        <van-swipe-cell v-for="item in methods" :key="item.id">
          <van-cell :title="item.name" :icon="item.icon || 'balance-o'" />
          <template #right>
            <van-button square type="danger" text="删除" @click="remove(item.id)" />
          </template>
        </van-swipe-cell>
      </section>
    </div>
  </main>
</template>
