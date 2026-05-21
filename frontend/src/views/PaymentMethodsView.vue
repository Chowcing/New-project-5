<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { showConfirmDialog, showDialog, showToast } from 'vant'
import { paymentMethodApi } from '@/api/services'
import type { PaymentMethod } from '@/types'
import { showError } from '@/utils/errors'
import { referenceMessage } from '@/utils/references'
import { maxTextLength, requiredText } from '@/utils/validation'

const methods = ref<PaymentMethod[]>([])
const editingId = ref<number | null>(null)
const editingMethodName = ref('')
const saving = ref(false)
const reordering = ref(false)
const formPopup = ref(false)
const iconPopup = ref(false)
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

const formTitle = computed(() => (editingId.value ? '编辑支付方式' : '新增支付方式'))
const selectedIcon = computed(() => iconOptions.find((item) => item.name === form.icon) || iconOptions[2])

async function load() {
  try {
    methods.value = await paymentMethodApi.list()
  } catch (error) {
    showError(error, '支付方式加载失败')
  }
}

function nextSortOrder() {
  const maxOrder = methods.value.reduce((max, item) => Math.max(max, item.sortOrder || 0), 0)
  return maxOrder + 10
}

function resetForm() {
  editingId.value = null
  editingMethodName.value = ''
  form.name = ''
  form.icon = 'balance-o'
  form.sortOrder = 0
}

function openCreateForm() {
  resetForm()
  form.sortOrder = nextSortOrder()
  formPopup.value = true
}

function openEditForm(item: PaymentMethod) {
  editingId.value = item.id
  editingMethodName.value = item.name
  form.name = item.name
  form.icon = item.icon || 'balance-o'
  form.sortOrder = item.sortOrder || 0
  formPopup.value = true
}

function closeForm() {
  if (saving.value) return
  formPopup.value = false
}

function handleFormClosed() {
  iconPopup.value = false
  resetForm()
}

function normalizeName(value: string) {
  return value.trim().toLowerCase()
}

function hasDuplicateName(name: string) {
  const normalizedName = normalizeName(name)
  return methods.value.some((item) => item.id !== editingId.value && normalizeName(item.name) === normalizedName)
}

async function submit() {
  if (saving.value) return
  const nameError = requiredText(form.name, '名称') || maxTextLength(form.name, '名称', 64)
  if (nameError) {
    showToast(nameError)
    return
  }
  if (hasDuplicateName(form.name)) {
    showToast('支付方式已存在')
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
    formPopup.value = false
    await load()
  } catch (error) {
    showError(error, editingId.value ? '支付方式更新失败' : '支付方式创建失败')
  } finally {
    saving.value = false
  }
}

function chooseIcon(icon: string) {
  form.icon = icon
  iconPopup.value = false
}

async function moveMethod(item: PaymentMethod, direction: -1 | 1) {
  if (reordering.value) return
  const currentIndex = methods.value.findIndex((method) => method.id === item.id)
  const targetIndex = currentIndex + direction
  if (currentIndex < 0 || targetIndex < 0 || targetIndex >= methods.value.length) {
    return
  }

  const reordered = [...methods.value]
  const [moved] = reordered.splice(currentIndex, 1)
  reordered.splice(targetIndex, 0, moved)
  const updates = reordered
    .map((method, index) => ({ method, sortOrder: (index + 1) * 10 }))
    .filter(({ method, sortOrder }) => (method.sortOrder || 0) !== sortOrder)

  reordering.value = true
  try {
    await Promise.all(updates.map(({ method, sortOrder }) => paymentMethodApi.update(method.id, {
      name: method.name,
      icon: method.icon || undefined,
      sortOrder
    })))
    methods.value = reordered.map((method, index) => ({ ...method, sortOrder: (index + 1) * 10 }))
    showToast('排序已更新')
  } catch (error) {
    showError(error, '排序更新失败')
    await load()
  } finally {
    reordering.value = false
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
      closeForm()
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
    <van-nav-bar title="支付方式管理" left-arrow @click-left="$router.back()">
      <template #right>
        <button class="nav-add-button" type="button" aria-label="新增支付方式" title="新增支付方式" @click="openCreateForm">
          <van-icon name="plus" />
        </button>
      </template>
    </van-nav-bar>

    <div class="page-content payment-page-content">
      <section class="section panel payment-action-panel">
        <div>
          <div class="section-heading payment-action-title">支付方式配置</div>
          <div class="payment-action-copy">优先维护常用支付方式，列表顺序会影响记账时的选择效率。</div>
        </div>
        <van-button round type="primary" icon="plus" @click="openCreateForm">新增支付方式</van-button>
      </section>

      <section class="section panel payment-list-panel">
        <div class="list-summary">
          <span>共 {{ methods.length }} 种支付方式</span>
        </div>

        <div v-if="methods.length === 0" class="payment-empty">
          <van-icon name="balance-o" />
          <div>暂无支付方式</div>
          <van-button size="small" round type="primary" icon="plus" @click="openCreateForm">新增支付方式</van-button>
        </div>

        <van-swipe-cell v-for="(item, index) in methods" v-else :key="item.id" class="method-swipe">
          <van-cell class="method-cell" :title="item.name" :label="`第 ${index + 1} 位`" @click="openEditForm(item)">
            <template #icon>
              <span class="method-icon">
                <van-icon :name="item.icon || 'balance-o'" />
              </span>
            </template>
            <template #right-icon>
              <div class="order-actions" @click.stop>
                <button
                  type="button"
                  class="order-button"
                  :disabled="index === 0 || reordering"
                  aria-label="上移"
                  title="上移"
                  @click="moveMethod(item, -1)"
                >
                  <van-icon name="arrow-up" />
                </button>
                <button
                  type="button"
                  class="order-button"
                  :disabled="index === methods.length - 1 || reordering"
                  aria-label="下移"
                  title="下移"
                  @click="moveMethod(item, 1)"
                >
                  <van-icon name="arrow-down" />
                </button>
              </div>
            </template>
          </van-cell>
          <template #right>
            <van-button square type="primary" icon="edit" aria-label="编辑" title="编辑" @click.stop="openEditForm(item)" />
            <van-button square type="danger" icon="delete-o" aria-label="删除" title="删除" @click.stop="remove(item.id)" />
          </template>
        </van-swipe-cell>
      </section>
    </div>

    <van-popup v-model:show="formPopup" position="bottom" round :close-on-click-overlay="!saving" @closed="handleFormClosed">
      <div class="payment-form-popup">
        <div class="popup-header">
          <div>
            <div class="popup-title">{{ formTitle }}</div>
            <div v-if="editingId" class="popup-subtitle">正在编辑：{{ editingMethodName }}</div>
          </div>
          <button class="popup-close" type="button" aria-label="关闭" title="关闭" @click="closeForm">
            <van-icon name="cross" />
          </button>
        </div>

        <van-form @submit="submit">
          <van-field v-model="form.name" label="名称" placeholder="如微信、支付宝、现金" required />
          <van-field label="图标">
            <template #input>
              <button class="icon-trigger" type="button" @click="iconPopup = true">
                <span class="icon-trigger-preview">
                  <van-icon :name="form.icon" />
                </span>
                <span>{{ selectedIcon.label }}</span>
                <van-icon name="arrow" />
              </button>
            </template>
          </van-field>
          <div class="popup-actions">
            <van-button block round type="primary" native-type="submit" :loading="saving">
              {{ editingId ? '保存修改' : '新增支付方式' }}
            </van-button>
          </div>
        </van-form>
      </div>
    </van-popup>

    <van-popup v-model:show="iconPopup" position="bottom" round>
      <div class="icon-popup">
        <div class="popup-header">
          <div class="popup-title">选择图标</div>
          <button class="popup-close" type="button" aria-label="关闭" title="关闭" @click="iconPopup = false">
            <van-icon name="cross" />
          </button>
        </div>
        <div class="icon-grid">
          <button
            v-for="item in iconOptions"
            :key="item.name"
            type="button"
            :class="['icon-choice', { active: form.icon === item.name }]"
            @click="chooseIcon(item.name)"
          >
            <van-icon :name="item.name" />
            <span>{{ item.label }}</span>
          </button>
        </div>
      </div>
    </van-popup>
  </main>
</template>

<style scoped>
.payment-page-content {
  padding-bottom: 20px;
}

.payment-action-panel {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
}

.payment-action-title {
  margin-bottom: 4px;
}

.payment-action-copy {
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 20px;
}

.nav-add-button,
.popup-close,
.order-button {
  border: 0;
  background: transparent;
  color: inherit;
  font: inherit;
}

.nav-add-button {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  color: var(--primary);
  font-size: 22px;
}

.payment-list-panel {
  padding: 0;
  overflow: hidden;
}

.list-summary {
  display: flex;
  align-items: center;
  min-height: 42px;
  padding: 0 14px;
  border-bottom: 1px solid var(--border-warm);
  color: var(--text-secondary);
  font-size: 13px;
}

.payment-empty {
  display: grid;
  justify-items: center;
  gap: 10px;
  padding: 34px 16px;
  color: var(--text-muted);
  font-size: 14px;
}

.payment-empty .van-icon {
  color: var(--text-muted);
  font-size: 32px;
}

.method-swipe {
  border-bottom: 1px solid rgba(var(--theme-border-warm-rgb), 0.72);
}

.method-swipe:last-child {
  border-bottom: 0;
}

.method-cell {
  align-items: center;
  min-height: 64px;
  padding: 10px 12px;
}

.method-icon {
  display: grid;
  width: 38px;
  height: 38px;
  margin-right: 10px;
  place-items: center;
  border-radius: 8px;
  background: var(--primary-soft);
  color: var(--primary);
  font-size: 22px;
}

.order-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-left: 8px;
}

.order-button {
  width: 30px;
  height: 30px;
  display: grid;
  place-items: center;
  border-radius: 8px;
  color: var(--text-main);
  font-size: 16px;
}

.order-button:disabled {
  color: #c9ced6;
}

.payment-form-popup,
.icon-popup {
  padding: 16px 12px max(18px, env(safe-area-inset-bottom));
  background: var(--card-bg);
}

.popup-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 38px;
  padding: 0 2px 12px;
}

.popup-title {
  color: var(--text-main);
  font-size: 17px;
  font-weight: 700;
}

.popup-subtitle {
  max-width: 260px;
  margin-top: 3px;
  overflow: hidden;
  color: var(--text-secondary);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.popup-close {
  width: 34px;
  height: 34px;
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  border-radius: 8px;
  color: var(--text-secondary);
  font-size: 18px;
}

.icon-trigger {
  display: flex;
  width: 100%;
  min-height: 38px;
  align-items: center;
  gap: 8px;
  border: 0;
  background: transparent;
  color: var(--text-main);
  font: inherit;
  text-align: left;
}

.icon-trigger-preview {
  display: grid;
  width: 32px;
  height: 32px;
  place-items: center;
  border-radius: 8px;
  background: var(--primary-soft);
  color: var(--primary);
  font-size: 18px;
}

.icon-trigger > .van-icon:last-child {
  margin-left: auto;
  color: var(--text-muted);
  font-size: 14px;
}

.popup-actions {
  padding-top: 14px;
}

.icon-grid {
  display: grid;
  width: 100%;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.icon-choice {
  min-height: 58px;
  border: 1px solid var(--border-warm);
  border-radius: 8px;
  background: var(--card-bg);
  color: var(--text-main);
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
  background: var(--primary-soft);
}
</style>
