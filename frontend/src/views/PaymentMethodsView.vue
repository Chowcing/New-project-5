<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { showConfirmDialog, showDialog, showToast } from 'vant'
import { paymentMethodApi } from '@/api/services'
import type { PaymentMethod } from '@/types'
import { showError } from '@/utils/errors'
import { referenceMessage } from '@/utils/references'
import { maxTextLength, requiredText } from '@/utils/validation'

const methods = ref<PaymentMethod[]>([])
const editingId = ref<number | null>(null)
const saving = ref(false)
const form = reactive({ name: '', icon: 'balance-o', sortOrder: 0 })
const iconOptions = [
  { name: 'wechat-pay', label: '微信' },
  { name: 'alipay', label: '支付宝' },
  { name: 'balance-o', label: '银行卡' },
  { name: 'credit-pay', label: '信用卡' },
  { name: 'debit-pay', label: '借记卡' },
  { name: 'cash-back-record', label: '现金' },
  { name: 'ecard-pay', label: '云闪付' },
  { name: 'other-pay', label: '其他' },
  { name: 'card', label: '卡片' },
  { name: 'gold-coin-o', label: '账户' }
]

async function load() {
  try {
    methods.value = await paymentMethodApi.list()
  } catch (error) {
    showError(error, '支付方式加载失败')
  }
}

function resetForm() {
  editingId.value = null
  form.name = ''
  form.icon = 'balance-o'
  form.sortOrder = 0
}

function edit(item: PaymentMethod) {
  editingId.value = item.id
  form.name = item.name
  form.icon = item.icon || 'balance-o'
  form.sortOrder = item.sortOrder || 0
}

async function submit() {
  if (saving.value) return
  const nameError = requiredText(form.name, '名称') || maxTextLength(form.name, '名称', 64)
  if (nameError) {
    showToast(nameError)
    return
  }
  saving.value = true
  try {
    const payload = {
      name: form.name.trim(),
      icon: form.icon.trim() || undefined,
      sortOrder: Number(form.sortOrder) || 0
    }
    if (editingId.value) {
      await paymentMethodApi.update(editingId.value, payload)
      showToast('支付方式已更新')
    } else {
      await paymentMethodApi.create(payload)
      showToast('支付方式已创建')
    }
    resetForm()
    await load()
  } catch (error) {
    showError(error, editingId.value ? '支付方式更新失败' : '支付方式创建失败')
  } finally {
    saving.value = false
  }
}

async function remove(id: number) {
  let references
  try {
    references = await paymentMethodApi.references(id, 5)
  } catch (error) {
    showError(error, '引用记录加载失败')
    return
  }
  if (references.total > 0) {
    await showDialog({ title: '无法删除支付方式', message: referenceMessage(references) })
    return
  }
  try {
    await showConfirmDialog({ title: '删除支付方式', message: '当前没有记录引用该支付方式，确认删除？' })
  } catch {
    return
  }
  try {
    await paymentMethodApi.remove(id)
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
    <van-nav-bar title="支付方式管理" left-arrow @click-left="$router.back()" />
    <div class="page-content">
      <section class="section panel">
        <van-form @submit="submit">
          <van-field v-model="form.name" label="名称" placeholder="如微信、支付宝、现金" required />
          <van-field label="图标">
            <template #input>
              <div class="icon-grid">
                <button
                  v-for="item in iconOptions"
                  :key="item.name"
                  type="button"
                  :class="['icon-choice', { active: form.icon === item.name }]"
                  @click="form.icon = item.name"
                >
                  <van-icon :name="item.name" />
                  <span>{{ item.label }}</span>
                </button>
              </div>
            </template>
          </van-field>
          <van-field v-model.number="form.sortOrder" type="number" label="排序" />
          <van-button block round type="primary" native-type="submit" :loading="saving">
            {{ editingId ? '保存修改' : '新增支付方式' }}
          </van-button>
          <van-button v-if="editingId" block round plain type="default" native-type="button" class="secondary-action" @click="resetForm">
            取消编辑
          </van-button>
        </van-form>
      </section>

      <section class="section panel">
        <van-swipe-cell v-for="item in methods" :key="item.id">
          <van-cell :title="item.name" :icon="item.icon || 'balance-o'" is-link @click="edit(item)" />
          <template #right>
            <van-button square type="primary" text="编辑" @click.stop="edit(item)" />
            <van-button square type="danger" text="删除" @click.stop="remove(item.id)" />
          </template>
        </van-swipe-cell>
      </section>
    </div>
  </main>
</template>

<style scoped>
.icon-grid {
  display: grid;
  width: 100%;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.icon-choice {
  min-height: 58px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
  color: #374151;
  font: inherit;
}

.icon-choice .van-icon {
  display: block;
  margin: 0 auto 4px;
  font-size: 20px;
}

.icon-choice span {
  display: block;
  font-size: 12px;
}

.icon-choice.active {
  border-color: var(--primary);
  color: var(--primary);
  background: #eef8f4;
}

.secondary-action {
  margin-top: 10px;
}
</style>
