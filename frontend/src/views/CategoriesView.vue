<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { showConfirmDialog, showDialog, showToast } from 'vant'
import { categoryApi } from '@/api/services'
import type { Category } from '@/types'
import { showError } from '@/utils/errors'
import { referenceMessage } from '@/utils/references'
import { maxTextLength, requiredText } from '@/utils/validation'

const categories = ref<Category[]>([])
const editingId = ref<number | null>(null)
const saving = ref(false)
const form = reactive({
  name: '',
  type: 'EXPENSE' as 'EXPENSE' | 'INCOME',
  icon: 'records-o',
  color: '#2f7d68',
  sortOrder: 0
})

const iconOptions = [
  { name: 'records-o', label: '通用' },
  { name: 'shop-o', label: '购物' },
  { name: 'cart-o', label: '消费' },
  { name: 'logistics', label: '交通' },
  { name: 'gift-o', label: '礼物' },
  { name: 'paid', label: '工资' },
  { name: 'cash-back-record', label: '现金' },
  { name: 'gold-coin-o', label: '收入' }
]

const colorOptions = ['#2f7d68', '#e25555', '#2f9b63', '#f59e0b', '#3b82f6', '#8b5cf6', '#64748b', '#111827']

async function load() {
  try {
    categories.value = await categoryApi.list()
  } catch (error) {
    showError(error, '分类加载失败')
  }
}

function resetForm() {
  editingId.value = null
  form.name = ''
  form.type = 'EXPENSE'
  form.icon = 'records-o'
  form.color = '#2f7d68'
  form.sortOrder = 0
}

function edit(item: Category) {
  editingId.value = item.id
  form.name = item.name
  form.type = item.type
  form.icon = item.icon || 'records-o'
  form.color = item.color || '#2f7d68'
  form.sortOrder = item.sortOrder || 0
}

async function submit() {
  if (saving.value) return
  const nameError = requiredText(form.name, '名称') || maxTextLength(form.name, '名称', 32)
  if (nameError) {
    showToast(nameError)
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
      showToast('分类已创建')
    }
    resetForm()
    await load()
  } catch (error) {
    showError(error, editingId.value ? '分类更新失败' : '分类创建失败')
  } finally {
    saving.value = false
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
          <van-field label="颜色">
            <template #input>
              <div class="color-grid">
                <button
                  v-for="item in colorOptions"
                  :key="item"
                  type="button"
                  :class="['color-choice', { active: form.color === item }]"
                  :style="{ backgroundColor: item }"
                  @click="form.color = item"
                />
              </div>
            </template>
          </van-field>
          <van-field v-model.number="form.sortOrder" type="number" label="排序" />
          <van-button block round type="primary" native-type="submit" :loading="saving">
            {{ editingId ? '保存修改' : '新增分类' }}
          </van-button>
          <van-button v-if="editingId" block round plain type="default" native-type="button" class="secondary-action" @click="resetForm">
            取消编辑
          </van-button>
        </van-form>
      </section>

      <section class="section panel">
        <van-swipe-cell v-for="item in categories" :key="item.id">
          <van-cell :title="item.name" :label="item.type === 'EXPENSE' ? '支出' : '收入'" is-link @click="edit(item)">
            <template #icon>
              <van-icon :name="item.icon || 'records-o'" class="category-icon" :style="{ color: item.color || '#2f7d68' }" />
            </template>
          </van-cell>
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
  grid-template-columns: repeat(4, minmax(0, 1fr));
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

.color-grid {
  display: grid;
  width: 100%;
  grid-template-columns: repeat(8, 1fr);
  gap: 8px;
}

.color-choice {
  width: 100%;
  aspect-ratio: 1;
  border: 2px solid transparent;
  border-radius: 999px;
}

.color-choice.active {
  border-color: #111827;
  box-shadow: 0 0 0 2px #fff inset;
}

.category-icon {
  margin-right: 8px;
  font-size: 20px;
  line-height: 24px;
}

.secondary-action {
  margin-top: 10px;
}
</style>
