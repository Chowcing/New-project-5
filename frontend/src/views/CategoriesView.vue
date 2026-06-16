<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { showConfirmDialog, showDialog, showToast } from 'vant'
import { categoryApi } from '@/api/services'
import PageSkeleton from '@/components/PageSkeleton.vue'
import type { Category } from '@/types'
import { showError } from '@/utils/errors'
import { referenceMessage } from '@/utils/references'
import { maxTextLength, requiredText } from '@/utils/validation'

type CategoryType = 'EXPENSE' | 'INCOME'

const categories = ref<Category[]>([])
const activeType = ref<CategoryType>('EXPENSE')
const categoryPage = ref(1)
const editingId = ref<number | null>(null)
const editingCategoryName = ref('')
const editingOriginalType = ref<CategoryType>('EXPENSE')
const editingReferenceCount = ref(0)
const loading = ref(true)
const loadingReferences = ref(false)
const saving = ref(false)
const reordering = ref(false)
const formPopup = ref(false)
const iconPopup = ref(false)
const form = reactive({
  name: '',
  type: 'EXPENSE' as CategoryType,
  icon: 'records-o',
  sortOrder: 0,
  pinned: false
})

const iconOptions = [
  { name: 'records-o', label: '通用' },
  { name: 'shop-o', label: '餐饮' },
  { name: 'logistics', label: '交通' },
  { name: 'cart-o', label: '购物' },
  { name: 'bag-o', label: '买菜' },
  { name: 'coupon-o', label: '外卖' },
  { name: 'cart-circle-o', label: '零食饮料' },
  { name: 'home-o', label: '居住' },
  { name: 'fire-o', label: '水电燃气' },
  { name: 'phone-o', label: '通讯网络' },
  { name: 'music-o', label: '娱乐' },
  { name: 'friends-o', label: '社交' },
  { name: 'gift-o', label: '人情' },
  { name: 'shield-o', label: '医疗' },
  { name: 'bookmark-o', label: '教育' },
  { name: 'hotel-o', label: '旅行' },
  { name: 'smile-o', label: '宠物' },
  { name: 'like-o', label: '育儿' },
  { name: 'desktop-o', label: '数码' },
  { name: 'new-arrival-o', label: '服饰' },
  { name: 'flower-o', label: '美妆' },
  { name: 'medal-o', label: '运动健身' },
  { name: 'umbrella-circle', label: '金融保险' },
  { name: 'gem-o', label: '会员订阅' },
  { name: 'todo-list-o', label: '办公学习' },
  { name: 'guide-o', label: '汽车' },
  { name: 'wap-home-o', label: '家居' },
  { name: 'hot-o', label: '烟酒' },
  { name: 'good-job-o', label: '公益捐赠' },
  { name: 'other-pay', label: '其他' },
  { name: 'paid', label: '工资' },
  { name: 'gold-coin-o', label: '奖金' },
  { name: 'manager-o', label: '兼职' },
  { name: 'chart-trending-o', label: '投资理财' },
  { name: 'balance-list-o', label: '报销' },
  { name: 'refund-o', label: '退款' },
  { name: 'cash-back-record', label: '其他收入' }
]
const PAGE_SIZE = 10

const currentCategories = computed(() => categories.value.filter((item) => item.type === activeType.value))
const categoryPageCount = computed(() => Math.max(1, Math.ceil(currentCategories.value.length / PAGE_SIZE)))
const paginatedCategories = computed(() => {
  const start = (categoryPage.value - 1) * PAGE_SIZE
  return currentCategories.value.slice(start, start + PAGE_SIZE)
})
const formTitle = computed(() => (editingId.value ? '编辑分类' : `新增${categoryTypeLabel(activeType.value)}分类`))
const selectedIcon = computed(() => iconOptions.find((item) => item.name === form.icon) || iconOptions[0])
const typeLocked = computed(() => Boolean(editingId.value && editingReferenceCount.value > 0))

async function load() {
  loading.value = true
  try {
    categories.value = await categoryApi.list()
  } catch (error) {
    showError(error, '分类加载失败')
  } finally {
    loading.value = false
  }
}

function categoryTypeLabel(type: CategoryType) {
  return type === 'EXPENSE' ? '支出' : '收入'
}

function categoryPosition(item: Category) {
  return currentCategories.value.findIndex((category) => category.id === item.id)
}

function categoryDisplayIndex(item: Category) {
  const index = categoryPosition(item)
  return index >= 0 ? index + 1 : 0
}

function defaultsByType(type: CategoryType) {
  if (type === 'INCOME') {
    return { icon: 'cash-back-record' }
  }
  return { icon: 'records-o' }
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
  form.sortOrder = 0
  form.pinned = false
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
  form.sortOrder = item.sortOrder || 0
  form.pinned = Boolean(item.pinned)
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
      sortOrder: Number(form.sortOrder) || 0,
      pinned: form.pinned
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
      sortOrder,
      pinned: Boolean(category.pinned)
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

async function togglePinned(item: Category) {
  if (reordering.value) return
  reordering.value = true
  try {
    await categoryApi.update(item.id, {
      name: item.name,
      type: item.type,
      icon: item.icon || undefined,
      sortOrder: item.sortOrder || 0,
      pinned: !item.pinned
    })
    await load()
    showToast(item.pinned ? '已取消置顶' : '已置顶')
  } catch (error) {
    showError(error, '置顶更新失败')
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
watch(activeType, () => {
  categoryPage.value = 1
})
watch(categoryPageCount, (pageCount) => {
  if (categoryPage.value > pageCount) {
    categoryPage.value = pageCount
  }
})
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
      <section class="section panel category-action-panel">
        <div>
          <div class="section-heading category-action-title">分类配置</div>
          <div class="category-action-copy">优先维护常用分类，列表顺序会影响记账时的选择效率。</div>
        </div>
        <van-button round type="primary" icon="plus" @click="openCreateForm">新增分类</van-button>
      </section>

      <div class="type-switch" role="tablist" aria-label="分类类型">
        <button
          type="button"
          :class="['type-switch-button', { active: activeType === 'EXPENSE' }]"
          role="tab"
          :aria-selected="activeType === 'EXPENSE'"
          @click="activeType = 'EXPENSE'"
        >
          <van-icon name="cart-o" />
          <span>支出分类</span>
        </button>
        <button
          type="button"
          :class="['type-switch-button', { active: activeType === 'INCOME' }]"
          role="tab"
          :aria-selected="activeType === 'INCOME'"
          @click="activeType = 'INCOME'"
        >
          <van-icon name="cash-back-record" />
          <span>收入分类</span>
        </button>
      </div>

      <section class="section panel category-list-panel">
        <div class="list-summary">
          <span>{{ categoryTypeLabel(activeType) }}分类 {{ currentCategories.length }} 个</span>
        </div>

        <PageSkeleton v-if="loading" variant="list" :cards="3" :rows="2" />

        <div v-else-if="currentCategories.length === 0" class="category-empty">
          <van-icon name="records-o" />
          <div>暂无{{ categoryTypeLabel(activeType) }}分类</div>
          <van-button size="small" round type="primary" icon="plus" @click="openCreateForm">新增分类</van-button>
        </div>

        <template v-else>
          <van-swipe-cell v-for="item in paginatedCategories" :key="item.id" class="category-swipe">
            <van-cell class="category-cell" :title="item.name" :label="`${item.pinned ? '已置顶 · ' : ''}第 ${categoryDisplayIndex(item)} 位`" @click="openEditForm(item)">
              <template #icon>
                <span class="category-icon-wrap">
                  <van-icon :name="item.icon || 'records-o'" />
                </span>
              </template>
              <template #right-icon>
                <div class="order-actions" @click.stop>
                  <button
                    type="button"
                    class="order-button"
                    :aria-label="item.pinned ? '取消置顶' : '置顶'"
                    :title="item.pinned ? '取消置顶' : '置顶'"
                    @click="togglePinned(item)"
                  >
                    <van-icon :name="item.pinned ? 'star' : 'star-o'" />
                  </button>
                  <button
                    type="button"
                    class="order-button"
                    :disabled="categoryPosition(item) <= 0 || reordering"
                    aria-label="上移"
                    title="上移"
                    @click="moveCategory(item, -1)"
                  >
                    <van-icon name="arrow-up" />
                  </button>
                  <button
                    type="button"
                    class="order-button"
                    :disabled="categoryPosition(item) === currentCategories.length - 1 || reordering"
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
          <van-pagination
            v-if="categoryPageCount > 1"
            v-model="categoryPage"
            class="list-pagination"
            mode="simple"
            :page-count="categoryPageCount"
          />
        </template>
      </section>
    </div>

    <van-popup v-model:show="formPopup" position="bottom" round teleport="body" :close-on-click-overlay="!saving" @closed="handleFormClosed">
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
                <span class="option-icon-preview">
                  <van-icon :name="form.icon" />
                </span>
                <span>{{ selectedIcon.label }}</span>
                <van-icon name="arrow" />
              </button>
            </template>
          </van-field>
          <van-cell center title="置顶常用">
            <template #right-icon>
              <van-switch v-model="form.pinned" size="22px" />
            </template>
          </van-cell>
          <div class="popup-actions">
            <van-button block round type="primary" :icon="editingId ? 'success' : 'plus'" native-type="submit" :loading="saving">
              {{ editingId ? '保存修改' : '新增分类' }}
            </van-button>
          </div>
        </van-form>
      </div>
    </van-popup>

    <van-popup v-model:show="iconPopup" position="bottom" round teleport="body">
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

  </main>
</template>

<style scoped>
.category-page-content {
  padding-bottom: var(--space-20);
}

.category-action-panel {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: var(--space-12);
  align-items: center;
}

.category-action-title {
  margin-bottom: var(--space-4);
}

.category-action-copy {
  color: var(--text-secondary);
  font-size: var(--font-size-meta);
  line-height: var(--line-height-meta);
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
  font-size: var(--icon-size-lg);
}

.type-switch {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-6);
  margin-bottom: var(--space-12);
  padding: var(--space-4);
  border-radius: var(--radius-card);
  background: var(--card-bg-warm);
}

.type-switch-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-4);
  min-height: 36px;
  border-radius: var(--radius-inner);
  color: var(--text-secondary);
  font-weight: 600;
}

.type-switch-button.active {
  background: var(--card-bg);
  color: var(--primary);
  box-shadow: 0 1px 4px rgba(var(--theme-shadow-warm-rgb), 0.08);
}

.category-list-panel {
  padding: var(--space-0);
  overflow: hidden;
}

.list-summary {
  display: flex;
  align-items: center;
  min-height: 42px;
  padding: var(--space-0) var(--space-14);
  border-bottom: 1px solid var(--border-warm);
  color: var(--text-secondary);
  font-size: var(--font-size-meta);
}

.category-empty {
  display: grid;
  justify-items: center;
  gap: var(--space-10);
  padding: var(--space-34) var(--space-16);
  color: var(--text-muted);
  font-size: var(--font-size-body);
}

.category-empty .van-icon {
  color: var(--text-muted);
  font-size: var(--icon-size-xl);
}

.category-swipe {
  border-bottom: 1px solid rgba(var(--theme-border-warm-rgb), 0.72);
}

.category-swipe:last-child {
  border-bottom: 0;
}

.list-pagination {
  padding: var(--space-12);
  border-top: 1px solid var(--border-warm);
}

.category-cell {
  align-items: center;
  min-height: 64px;
  padding: var(--space-10) var(--space-12);
}

.category-icon-wrap {
  display: grid;
  width: 38px;
  height: 38px;
  margin-right: var(--space-10);
  place-items: center;
  border-radius: var(--radius-card);
  background: var(--primary-soft);
  color: var(--primary);
  font-size: var(--icon-size-lg);
}

.order-actions {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  margin-left: var(--space-8);
}

.order-button {
  width: 30px;
  height: 30px;
  display: grid;
  place-items: center;
  border-radius: var(--radius-card);
  color: var(--text-main);
  font-size: var(--icon-size-md);
}

.order-button:disabled {
  color: #c9ced6;
}

.category-form-popup,
.picker-popup {
  max-height: min(78vh, 720px);
  overflow-y: auto;
  padding: var(--space-16) var(--space-12) max(var(--space-18), env(safe-area-inset-bottom));
  background: var(--card-bg);
}

.popup-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-12);
  min-height: 38px;
  padding: var(--space-0) var(--space-2) var(--space-12);
}

.popup-title {
  color: var(--text-main);
  font-size: var(--font-size-panel-title);
  font-weight: 700;
}

.popup-subtitle {
  max-width: 260px;
  margin-top: var(--space-3);
  overflow: hidden;
  color: var(--text-secondary);
  font-size: var(--font-size-caption);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.popup-close {
  width: 34px;
  height: 34px;
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  border-radius: var(--radius-card);
  color: var(--text-secondary);
  font-size: var(--icon-size-md);
}

.type-lock-note {
  margin: calc(var(--space-3) * -1) var(--space-16) var(--space-8) var(--space-92);
  color: #9a6b1f;
  font-size: var(--font-size-caption);
}

.option-trigger {
  display: flex;
  width: 100%;
  min-height: 38px;
  align-items: center;
  gap: var(--space-8);
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
  border-radius: var(--radius-card);
  background: var(--primary-soft);
  color: var(--primary);
  font-size: var(--icon-size-md);
}

.option-trigger > .van-icon:last-child {
  margin-left: auto;
  color: var(--text-muted);
  font-size: var(--icon-size-sm);
}

.popup-actions {
  padding-top: var(--space-14);
}

.icon-grid {
  display: grid;
  width: 100%;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-8);
  max-height: calc(58px * 4 + var(--space-8) * 3);
  overflow-y: auto;
  overscroll-behavior: contain;
}

.icon-choice {
  height: 58px;
  border: 1px solid var(--border-warm);
  border-radius: var(--radius-card);
  background: var(--card-bg);
  color: var(--text-main);
  font: inherit;
}

.icon-choice .van-icon {
  display: block;
  margin: var(--space-0) auto var(--space-4);
  font-size: var(--icon-size-md);
}

.icon-choice span {
  display: block;
  font-size: var(--font-size-caption);
}

.icon-choice.active {
  border-color: var(--primary);
  color: var(--primary);
  background: var(--primary-soft);
}

</style>
