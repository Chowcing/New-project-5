<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { showConfirmDialog, showDialog, showToast } from 'vant'
import { categoryApi } from '@/api/services'
import type { Category } from '@/types'
import { showError } from '@/utils/errors'
import { referenceMessage } from '@/utils/references'
import { maxTextLength, requiredText } from '@/utils/validation'

type CategoryType = 'EXPENSE' | 'INCOME'

const categories = ref<Category[]>([])
const activeType = ref<CategoryType>('EXPENSE')
const editingId = ref<number | null>(null)
const editingCategoryName = ref('')
const editingOriginalType = ref<CategoryType>('EXPENSE')
const editingReferenceCount = ref(0)
const loadingReferences = ref(false)
const saving = ref(false)
const reordering = ref(false)
const formPopup = ref(false)
const iconPopup = ref(false)
const colorPopup = ref(false)
const form = reactive({
  name: '',
  type: 'EXPENSE' as CategoryType,
  icon: 'records-o',
  color: '#c96f3a',
  sortOrder: 0
})

const iconOptions = [
  { name: 'records-o', label: '通用' },
  { name: 'shop-o', label: '餐饮' },
  { name: 'logistics', label: '交通' },
  { name: 'cart-o', label: '购物' },
  { name: 'bag-o', label: '日用' },
  { name: 'home-o', label: '住房' },
  { name: 'fire-o', label: '水电' },
  { name: 'phone-o', label: '通讯' },
  { name: 'shield-o', label: '医疗' },
  { name: 'bookmark-o', label: '教育' },
  { name: 'music-o', label: '娱乐' },
  { name: 'hotel-o', label: '旅行' },
  { name: 'gift-o', label: '礼金' },
  { name: 'paid', label: '工资' },
  { name: 'gold-coin-o', label: '奖金' },
  { name: 'manager-o', label: '兼职' },
  { name: 'chart-trending-o', label: '理财' },
  { name: 'balance-list-o', label: '报销' },
  { name: 'refund-o', label: '退款' },
  { name: 'cash-back-record', label: '收入' }
]

const colorOptions = ['#c96f3a', '#d99232', '#d65b4a', '#6f8f4e', '#b7845e', '#d2876d', '#a66a4a', '#d85f8a', '#8aa06d', '#8d7465', '#3a2a22']

const currentCategories = computed(() => categories.value.filter((item) => item.type === activeType.value))
const formTitle = computed(() => (editingId.value ? '编辑分类' : `新增${categoryTypeLabel(activeType.value)}分类`))
const selectedIcon = computed(() => iconOptions.find((item) => item.name === form.icon) || iconOptions[0])
const typeLocked = computed(() => Boolean(editingId.value && editingReferenceCount.value > 0))

async function load() {
  try {
    categories.value = await categoryApi.list()
  } catch (error) {
    showError(error, '分类加载失败')
  }
}

function categoryTypeLabel(type: CategoryType) {
  return type === 'EXPENSE' ? '支出' : '收入'
}

function defaultsByType(type: CategoryType) {
  if (type === 'INCOME') {
    return { icon: 'cash-back-record', color: '#6f8f4e' }
  }
  return { icon: 'records-o', color: '#c96f3a' }
}

function nextSortOrder(type: CategoryType) {
  const maxOrder = categories.value
    .filter((item) => item.type === type)
    .reduce((max, item) => Math.max(max, item.sortOrder || 0), 0)
  return maxOrder + 10
}

function resetForm(type: CategoryType = activeType.value) {
  const defaults = defaultsByType(type)
  editingId.value = null
  editingCategoryName.value = ''
  editingOriginalType.value = type
  editingReferenceCount.value = 0
  loadingReferences.value = false
  form.name = ''
  form.type = type
  form.icon = defaults.icon
  form.color = defaults.color
  form.sortOrder = 0
}

function openCreateForm() {
  resetForm(activeType.value)
  form.sortOrder = nextSortOrder(activeType.value)
  formPopup.value = true
}

async function openEditForm(item: Category) {
  editingId.value = item.id
  editingCategoryName.value = item.name
  editingOriginalType.value = item.type
  editingReferenceCount.value = 0
  form.name = item.name
  form.type = item.type
  form.icon = item.icon || 'records-o'
  form.color = item.color || '#c96f3a'
  form.sortOrder = item.sortOrder || 0
  formPopup.value = true
  loadingReferences.value = true
  try {
    const references = await categoryApi.references(item.id, 1)
    editingReferenceCount.value = references.total
  } catch (error) {
    showError(error, '引用记录加载失败')
  } finally {
    loadingReferences.value = false
  }
}

function closeForm() {
  if (saving.value) return
  formPopup.value = false
}

function handleFormClosed() {
  iconPopup.value = false
  colorPopup.value = false
  resetForm(activeType.value)
}

function normalizeName(value: string) {
  return value.trim().toLowerCase()
}

function hasDuplicateCategory(name: string) {
  const normalizedName = normalizeName(name)
  return categories.value.some((item) =>
    item.id !== editingId.value &&
    item.type === form.type &&
    normalizeName(item.name) === normalizedName
  )
}

async function submit() {
  if (saving.value) return
  const nameError = requiredText(form.name, '名称') || maxTextLength(form.name, '名称', 32)
  if (nameError) {
    showToast(nameError)
    return
  }
  if (typeLocked.value && form.type !== editingOriginalType.value) {
    showToast('已被记录引用的分类不能修改类型')
    return
  }
  if (hasDuplicateCategory(form.name)) {
    showToast(`${categoryTypeLabel(form.type)}分类已存在`)
    return
  }
  saving.value = true
  try {
    const payload = {
      ...form,
      name: form.name.trim(),
      icon: form.icon.trim() || undefined,
      color: form.color.trim() || undefined,
      sortOrder: Number(form.sortOrder) || 0
    }
    if (editingId.value) {
      await categoryApi.update(editingId.value, payload)
      showToast('分类已更新')
    } else {
      await categoryApi.create(payload)
      activeType.value = payload.type
      showToast('分类已创建')
    }
    formPopup.value = false
    await load()
  } catch (error) {
    showError(error, editingId.value ? '分类更新失败' : '分类创建失败')
  } finally {
    saving.value = false
  }
}

function chooseIcon(icon: string) {
  form.icon = icon
  iconPopup.value = false
}

function chooseColor(color: string) {
  form.color = color
  colorPopup.value = false
}

async function moveCategory(item: Category, direction: -1 | 1) {
  if (reordering.value) return
  const items = currentCategories.value
  const currentIndex = items.findIndex((category) => category.id === item.id)
  const targetIndex = currentIndex + direction
  if (currentIndex < 0 || targetIndex < 0 || targetIndex >= items.length) {
    return
  }

  const reordered = [...items]
  const [moved] = reordered.splice(currentIndex, 1)
  reordered.splice(targetIndex, 0, moved)
  const updates = reordered
    .map((category, index) => ({ category, sortOrder: (index + 1) * 10 }))
    .filter(({ category, sortOrder }) => (category.sortOrder || 0) !== sortOrder)

  reordering.value = true
  try {
    await Promise.all(updates.map(({ category, sortOrder }) => categoryApi.update(category.id, {
      name: category.name,
      type: category.type,
      icon: category.icon,
      color: category.color,
      sortOrder
    })))
    await load()
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
    references = await categoryApi.references(id, 5)
  } catch (error) {
    showError(error, '引用记录加载失败')
    return
  }
  if (references.total > 0) {
    await showDialog({ title: '无法删除分类', message: referenceMessage(references) })
    return
  }
  try {
    await showConfirmDialog({ title: '删除分类', message: '当前没有记录引用该分类，确认删除？' })
  } catch {
    return
  }
  try {
    await categoryApi.remove(id)
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
    <van-nav-bar title="分类管理" left-arrow @click-left="$router.back()">
      <template #right>
        <button class="nav-add-button" type="button" aria-label="新增分类" title="新增分类" @click="openCreateForm">
          <van-icon name="plus" />
        </button>
      </template>
    </van-nav-bar>

    <div class="page-content category-page-content">
      <div class="type-switch" role="tablist" aria-label="分类类型">
        <button
          type="button"
          :class="['type-switch-button', { active: activeType === 'EXPENSE' }]"
          role="tab"
          :aria-selected="activeType === 'EXPENSE'"
          @click="activeType = 'EXPENSE'"
        >
          支出分类
        </button>
        <button
          type="button"
          :class="['type-switch-button', { active: activeType === 'INCOME' }]"
          role="tab"
          :aria-selected="activeType === 'INCOME'"
          @click="activeType = 'INCOME'"
        >
          收入分类
        </button>
      </div>

      <section class="section panel category-list-panel">
        <div class="list-summary">
          <span>{{ categoryTypeLabel(activeType) }}分类 {{ currentCategories.length }} 个</span>
        </div>

        <div v-if="currentCategories.length === 0" class="category-empty">
          <van-icon name="records-o" />
          <div>暂无{{ categoryTypeLabel(activeType) }}分类</div>
          <van-button size="small" round type="primary" icon="plus" @click="openCreateForm">新增分类</van-button>
        </div>

        <van-swipe-cell v-for="(item, index) in currentCategories" v-else :key="item.id" class="category-swipe">
          <van-cell class="category-cell" :title="item.name" :label="`第 ${index + 1} 位`" @click="openEditForm(item)">
            <template #icon>
              <span class="category-icon-wrap" :style="{ color: item.color || '#c96f3a' }">
                <van-icon :name="item.icon || 'records-o'" />
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
                  @click="moveCategory(item, -1)"
                >
                  <van-icon name="arrow-up" />
                </button>
                <button
                  type="button"
                  class="order-button"
                  :disabled="index === currentCategories.length - 1 || reordering"
                  aria-label="下移"
                  title="下移"
                  @click="moveCategory(item, 1)"
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
      <div class="category-form-popup">
        <div class="popup-header">
          <div>
            <div class="popup-title">{{ formTitle }}</div>
            <div v-if="editingId" class="popup-subtitle">正在编辑：{{ editingCategoryName }}</div>
          </div>
          <button class="popup-close" type="button" aria-label="关闭" title="关闭" @click="closeForm">
            <van-icon name="cross" />
          </button>
        </div>

        <van-form @submit="submit">
          <van-field v-model="form.name" label="名称" placeholder="如餐饮" required />
          <van-field label="类型">
            <template #input>
              <van-radio-group v-model="form.type" direction="horizontal" :disabled="typeLocked || loadingReferences">
                <van-radio name="EXPENSE">支出</van-radio>
                <van-radio name="INCOME">收入</van-radio>
              </van-radio-group>
            </template>
          </van-field>
          <div v-if="typeLocked" class="type-lock-note">已被 {{ editingReferenceCount }} 条记录引用，类型不可修改</div>
          <van-field label="图标">
            <template #input>
              <button class="option-trigger" type="button" @click="iconPopup = true">
                <span class="option-icon-preview" :style="{ color: form.color }">
                  <van-icon :name="form.icon" />
                </span>
                <span>{{ selectedIcon.label }}</span>
                <van-icon name="arrow" />
              </button>
            </template>
          </van-field>
          <van-field label="颜色">
            <template #input>
              <button class="option-trigger" type="button" @click="colorPopup = true">
                <span class="color-preview" :style="{ backgroundColor: form.color }" />
                <span>{{ form.color }}</span>
                <van-icon name="arrow" />
              </button>
            </template>
          </van-field>
          <div class="popup-actions">
            <van-button block round type="primary" native-type="submit" :loading="saving">
              {{ editingId ? '保存修改' : '新增分类' }}
            </van-button>
          </div>
        </van-form>
      </div>
    </van-popup>

    <van-popup v-model:show="iconPopup" position="bottom" round>
      <div class="picker-popup">
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

    <van-popup v-model:show="colorPopup" position="bottom" round>
      <div class="picker-popup">
        <div class="popup-header">
          <div class="popup-title">选择颜色</div>
          <button class="popup-close" type="button" aria-label="关闭" title="关闭" @click="colorPopup = false">
            <van-icon name="cross" />
          </button>
        </div>
        <div class="color-grid">
          <button
            v-for="item in colorOptions"
            :key="item"
            type="button"
            :class="['color-choice', { active: form.color === item }]"
            :style="{ backgroundColor: item }"
            :aria-label="item"
            :title="item"
            @click="chooseColor(item)"
          />
        </div>
      </div>
    </van-popup>
  </main>
</template>

<style scoped>
.category-page-content {
  padding-bottom: 20px;
}

.nav-add-button,
.popup-close,
.order-button,
.type-switch-button {
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

.type-switch {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px;
  margin-bottom: 12px;
  padding: 4px;
  border-radius: 8px;
  background: var(--card-bg-warm);
}

.type-switch-button {
  min-height: 36px;
  border-radius: 6px;
  color: var(--text-secondary);
  font-weight: 600;
}

.type-switch-button.active {
  background: var(--card-bg);
  color: var(--primary);
  box-shadow: 0 1px 4px rgba(127, 76, 35, 0.08);
}

.category-list-panel {
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

.category-empty {
  display: grid;
  justify-items: center;
  gap: 10px;
  padding: 34px 16px;
  color: var(--text-muted);
  font-size: 14px;
}

.category-empty .van-icon {
  color: var(--text-muted);
  font-size: 32px;
}

.category-swipe {
  border-bottom: 1px solid rgba(240, 220, 199, 0.72);
}

.category-swipe:last-child {
  border-bottom: 0;
}

.category-cell {
  align-items: center;
  min-height: 64px;
  padding: 10px 12px;
}

.category-icon-wrap {
  display: grid;
  width: 38px;
  height: 38px;
  margin-right: 10px;
  place-items: center;
  border-radius: 8px;
  background: var(--card-bg-warm);
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

.category-form-popup,
.picker-popup {
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

.type-lock-note {
  margin: -3px 16px 8px 92px;
  color: #9a6b1f;
  font-size: 12px;
}

.option-trigger {
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

.option-icon-preview {
  display: grid;
  width: 32px;
  height: 32px;
  place-items: center;
  border-radius: 8px;
  background: var(--card-bg-warm);
  font-size: 18px;
}

.color-preview {
  width: 28px;
  height: 28px;
  border-radius: 999px;
  box-shadow: 0 0 0 1px rgba(127, 76, 35, 0.14) inset;
}

.option-trigger > .van-icon:last-child {
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
  grid-template-columns: repeat(4, minmax(0, 1fr));
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

.color-grid {
  display: grid;
  width: 100%;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 12px;
  padding: 4px;
}

.color-choice {
  width: 100%;
  aspect-ratio: 1;
  border: 2px solid transparent;
  border-radius: 999px;
}

.color-choice.active {
  border-color: var(--text-main);
  box-shadow: 0 0 0 2px var(--card-bg) inset;
}
</style>
