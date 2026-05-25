<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { showConfirmDialog, showToast } from 'vant'
import { onlinePlatformApi } from '@/api/services'
import type { OnlinePlatform } from '@/types'
import { showError } from '@/utils/errors'
import { maxTextLength, requiredText } from '@/utils/validation'

const platforms = ref<OnlinePlatform[]>([])
const editingId = ref<number | null>(null)
const editingName = ref('')
const saving = ref(false)
const reordering = ref(false)
const formPopup = ref(false)
const iconPopup = ref(false)
const form = reactive({ name: '', icon: 'apps-o', sortOrder: 0, pinned: false })
const iconOptions = [
  { name: 'shop-o', label: '购物' },
  { name: 'cart-o', label: '商城' },
  { name: 'apps-o', label: '应用' },
  { name: 'location-o', label: '地图' },
  { name: 'logistics', label: '出行' },
  { name: 'hotel-o', label: '旅行' },
  { name: 'wechat-pay', label: '微信' },
  { name: 'alipay', label: '支付宝' },
  { name: 'video-o', label: '视频' },
  { name: 'tv-o', label: '内容' }
]

const formTitle = computed(() => (editingId.value ? '编辑线上平台' : '新增线上平台'))
const selectedIcon = computed(() => iconOptions.find((item) => item.name === form.icon) || iconOptions[2])

async function load() {
  try {
    platforms.value = await onlinePlatformApi.list()
  } catch (error) {
    showError(error, '线上平台加载失败')
  }
}

function nextSortOrder() {
  const maxOrder = platforms.value.reduce((max, item) => Math.max(max, item.sortOrder || 0), 0)
  return maxOrder + 10
}

function resetForm() {
  editingId.value = null
  editingName.value = ''
  form.name = ''
  form.icon = 'apps-o'
  form.sortOrder = 0
  form.pinned = false
}

function openCreateForm() {
  resetForm()
  form.sortOrder = nextSortOrder()
  formPopup.value = true
}

function openEditForm(item: OnlinePlatform) {
  editingId.value = item.id
  editingName.value = item.name
  form.name = item.name
  form.icon = item.icon || 'apps-o'
  form.sortOrder = item.sortOrder || 0
  form.pinned = Boolean(item.pinned)
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
  return platforms.value.some((item) => item.id !== editingId.value && normalizeName(item.name) === normalizedName)
}

async function submit() {
  if (saving.value) return
  const nameError = requiredText(form.name, '名称') || maxTextLength(form.name, '名称', 64)
  if (nameError) {
    showToast(nameError)
    return
  }
  if (hasDuplicateName(form.name)) {
    showToast('线上平台已存在')
    return
  }
  saving.value = true
  try {
    const payload = {
      name: form.name.trim(),
      icon: form.icon.trim() || undefined,
      sortOrder: Number(form.sortOrder) || 0,
      pinned: form.pinned
    }
    if (editingId.value) {
      await onlinePlatformApi.update(editingId.value, payload)
      showToast('线上平台已更新')
    } else {
      await onlinePlatformApi.create(payload)
      showToast('线上平台已创建')
    }
    formPopup.value = false
    await load()
  } catch (error) {
    showError(error, editingId.value ? '线上平台更新失败' : '线上平台创建失败')
  } finally {
    saving.value = false
  }
}

function chooseIcon(icon: string) {
  form.icon = icon
  iconPopup.value = false
}

async function movePlatform(item: OnlinePlatform, direction: -1 | 1) {
  if (reordering.value) return
  const currentIndex = platforms.value.findIndex((platform) => platform.id === item.id)
  const targetIndex = currentIndex + direction
  if (currentIndex < 0 || targetIndex < 0 || targetIndex >= platforms.value.length) return

  const reordered = [...platforms.value]
  const [moved] = reordered.splice(currentIndex, 1)
  reordered.splice(targetIndex, 0, moved)
  const updates = reordered
    .map((platform, index) => ({ platform, sortOrder: (index + 1) * 10 }))
    .filter(({ platform, sortOrder }) => (platform.sortOrder || 0) !== sortOrder)

  reordering.value = true
  try {
    await Promise.all(updates.map(({ platform, sortOrder }) => onlinePlatformApi.update(platform.id, {
      name: platform.name,
      icon: platform.icon || undefined,
      sortOrder,
      pinned: Boolean(platform.pinned)
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

async function togglePinned(item: OnlinePlatform) {
  if (reordering.value) return
  reordering.value = true
  try {
    await onlinePlatformApi.update(item.id, {
      name: item.name,
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
  try {
    await showConfirmDialog({ title: '删除线上平台', message: '删除后不会影响历史记录中的平台名称，确认删除？' })
  } catch {
    return
  }
  try {
    await onlinePlatformApi.remove(id)
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
    <van-nav-bar title="线上平台管理" left-arrow @click-left="$router.back()">
      <template #right>
        <button class="nav-add-button" type="button" aria-label="新增线上平台" title="新增线上平台" @click="openCreateForm">
          <van-icon name="plus" />
        </button>
      </template>
    </van-nav-bar>

    <div class="page-content platform-page-content">
      <section class="section panel platform-action-panel">
        <div>
          <div class="section-heading platform-action-title">线上平台配置</div>
          <div class="platform-action-copy">置顶和排序会影响极简记账时的平台推荐顺序。</div>
        </div>
        <van-button round type="primary" icon="plus" @click="openCreateForm">新增平台</van-button>
      </section>

      <section class="section panel platform-list-panel">
        <div class="list-summary">
          <span>共 {{ platforms.length }} 个线上平台</span>
        </div>

        <div v-if="platforms.length === 0" class="platform-empty">
          <van-icon name="apps-o" />
          <div>暂无线上平台</div>
          <van-button size="small" round type="primary" icon="plus" @click="openCreateForm">新增平台</van-button>
        </div>

        <van-swipe-cell v-for="(item, index) in platforms" v-else :key="item.id" class="platform-swipe">
          <van-cell class="platform-cell" :title="item.name" :label="`${item.pinned ? '已置顶 · ' : ''}第 ${index + 1} 位`" @click="openEditForm(item)">
            <template #icon>
              <span class="platform-icon">
                <van-icon :name="item.icon || 'apps-o'" />
              </span>
            </template>
            <template #right-icon>
              <div class="order-actions" @click.stop>
                <button type="button" class="order-button" :aria-label="item.pinned ? '取消置顶' : '置顶'" :title="item.pinned ? '取消置顶' : '置顶'" @click="togglePinned(item)">
                  <van-icon :name="item.pinned ? 'star' : 'star-o'" />
                </button>
                <button type="button" class="order-button" :disabled="index === 0 || reordering" aria-label="上移" title="上移" @click="movePlatform(item, -1)">
                  <van-icon name="arrow-up" />
                </button>
                <button type="button" class="order-button" :disabled="index === platforms.length - 1 || reordering" aria-label="下移" title="下移" @click="movePlatform(item, 1)">
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

    <van-popup v-model:show="formPopup" position="bottom" round teleport="body" :close-on-click-overlay="!saving" @closed="handleFormClosed">
      <div class="platform-form-popup">
        <div class="popup-header">
          <div>
            <div class="popup-title">{{ formTitle }}</div>
            <div v-if="editingId" class="popup-subtitle">正在编辑：{{ editingName }}</div>
          </div>
          <button class="popup-close" type="button" aria-label="关闭" title="关闭" @click="closeForm">
            <van-icon name="cross" />
          </button>
        </div>

        <van-form @submit="submit">
          <van-field v-model="form.name" label="名称" placeholder="如淘宝、美团、滴滴" required />
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
          <van-cell center title="置顶常用">
            <template #right-icon>
              <van-switch v-model="form.pinned" size="22px" />
            </template>
          </van-cell>
          <div class="popup-actions">
            <van-button block round type="primary" :icon="editingId ? 'success' : 'plus'" native-type="submit" :loading="saving">
              {{ editingId ? '保存修改' : '新增平台' }}
            </van-button>
          </div>
        </van-form>
      </div>
    </van-popup>

    <van-popup v-model:show="iconPopup" position="bottom" round teleport="body">
      <div class="icon-popup">
        <div class="popup-header">
          <div class="popup-title">选择图标</div>
          <button class="popup-close" type="button" aria-label="关闭" title="关闭" @click="iconPopup = false">
            <van-icon name="cross" />
          </button>
        </div>
        <div class="icon-grid">
          <button v-for="item in iconOptions" :key="item.name" type="button" :class="['icon-choice', { active: form.icon === item.name }]" @click="chooseIcon(item.name)">
            <van-icon :name="item.name" />
            <span>{{ item.label }}</span>
          </button>
        </div>
      </div>
    </van-popup>
  </main>
</template>

<style scoped>
.platform-page-content {
  padding-bottom: var(--space-20);
}

.platform-action-panel {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: var(--space-12);
  align-items: center;
}

.platform-action-title {
  margin-bottom: var(--space-4);
}

.platform-action-copy {
  color: var(--text-secondary);
  font-size: var(--font-size-meta);
  line-height: var(--line-height-meta);
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
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  color: var(--primary);
  font-size: var(--icon-size-lg);
}

.platform-list-panel {
  padding: var(--space-0);
  overflow: hidden;
}

.platform-empty {
  display: grid;
  gap: var(--space-10);
  justify-items: center;
  padding: var(--space-32) var(--space-16);
  color: var(--text-secondary);
}

.platform-empty :deep(.van-icon) {
  color: var(--primary);
  font-size: var(--icon-size-xl);
}

.platform-cell {
  align-items: center;
}

.platform-icon,
.icon-trigger-preview {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  margin-right: var(--space-10);
  border-radius: var(--radius-card);
  background: var(--primary-soft);
  color: var(--primary);
  font-size: var(--icon-size-md);
}

.order-actions {
  display: inline-flex;
  gap: var(--space-2);
}

.order-button {
  display: grid;
  width: 30px;
  height: 30px;
  place-items: center;
  border-radius: var(--radius-card);
  color: var(--text-secondary);
}

.order-button:disabled {
  color: var(--text-muted);
  opacity: 0.45;
}

.platform-form-popup,
.icon-popup {
  padding: var(--space-0) var(--space-0) max(var(--space-16), env(safe-area-inset-bottom));
  background: var(--card-bg);
}

.popup-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-12);
  padding: var(--space-14) var(--space-16);
  border-bottom: 1px solid var(--border-warm);
}

.popup-title {
  font-size: var(--font-size-section-title);
  font-weight: 700;
}

.popup-subtitle {
  margin-top: var(--space-3);
  color: var(--text-secondary);
  font-size: var(--font-size-caption);
}

.popup-close {
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  border-radius: var(--radius-card);
  color: var(--text-secondary);
}

.icon-trigger {
  display: inline-flex;
  align-items: center;
  gap: var(--space-8);
  border: 0;
  background: transparent;
  color: var(--text-main);
  font: inherit;
}

.popup-actions {
  padding: var(--space-16);
}

.icon-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: var(--space-10);
  padding: var(--space-16);
}

.icon-choice {
  display: grid;
  gap: var(--space-6);
  min-height: 62px;
  place-items: center;
  border: 1px solid var(--border-warm);
  border-radius: var(--radius-card);
  background: var(--page-bg-soft);
  color: var(--text-main);
  font: inherit;
}

.icon-choice.active {
  border-color: var(--primary);
  color: var(--primary);
  background: var(--primary-soft);
}

.icon-choice span {
  font-size: var(--font-size-caption);
}
</style>
