<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { showConfirmDialog, showToast } from 'vant'
import { categoryApi } from '@/api/services'
import type { Category } from '@/types'

const categories = ref<Category[]>([])
const form = reactive({
  name: '',
  type: 'EXPENSE' as 'EXPENSE' | 'INCOME',
  icon: 'records-o',
  color: '#2f7d68',
  sortOrder: 0
})

async function load() {
  categories.value = await categoryApi.list()
}

async function submit() {
  await categoryApi.create({ ...form })
  showToast('分类已创建')
  form.name = ''
  await load()
}

async function remove(id: number) {
  await showConfirmDialog({ title: '删除分类', message: '已被记录使用的分类可能无法删除。确认继续？' })
  await categoryApi.remove(id)
  showToast('已删除')
  await load()
}

onMounted(load)
</script>

<template>
  <main class="page">
    <van-nav-bar title="分类管理" left-arrow @click-left="$router.back()" />
    <div class="page-content">
      <section class="section panel">
        <van-form @submit="submit">
          <van-field v-model="form.name" label="名称" placeholder="如餐饮" required />
          <van-field label="类型">
            <template #input>
              <van-radio-group v-model="form.type" direction="horizontal">
                <van-radio name="EXPENSE">支出</van-radio>
                <van-radio name="INCOME">收入</van-radio>
              </van-radio-group>
            </template>
          </van-field>
          <van-field v-model="form.icon" label="图标" />
          <van-field v-model="form.color" label="颜色" />
          <van-button block round type="primary" native-type="submit">新增分类</van-button>
        </van-form>
      </section>

      <section class="section panel">
        <van-swipe-cell v-for="item in categories" :key="item.id">
          <van-cell :title="item.name" :label="item.type === 'EXPENSE' ? '支出' : '收入'" :icon="item.icon || 'records-o'" />
          <template #right>
            <van-button square type="danger" text="删除" @click="remove(item.id)" />
          </template>
        </van-swipe-cell>
      </section>
    </div>
  </main>
</template>

