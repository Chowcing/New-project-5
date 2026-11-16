<script setup lang="ts">
import { ref, watch } from 'vue'
import { showConfirmDialog, showFailToast, showToast } from 'vant'
import BottomSheet from '@/components/BottomSheet.vue'
import { useAuthStore } from '@/stores/auth'
import type { TransactionRecord } from '@/types'
import { money } from '@/utils/date'
import { transactionTitle } from '@/utils/display'
import { moneyError } from '@/utils/money'
import { createSavedTemplate, readSavedTemplates, writeSavedTemplates, type SavedTransactionTemplate } from '@/utils/savedTransactionTemplates'

const show = defineModel<boolean>('show', { required: true })
const props = defineProps<{ record?: TransactionRecord }>()
const emit = defineEmits<{ apply: [template: SavedTransactionTemplate] }>()
const auth = useAuthStore()
const templates = ref<SavedTransactionTemplate[]>([])
const editingId = ref<string>()
const name = ref('')
const amount = ref('')

watch(() => [show.value, auth.user?.id] as const, ([visible]) => {
  if (!visible) return
  templates.value = readSavedTemplates(auth.user?.id)
  editingId.value = undefined
  name.value = props.record ? transactionTitle(props.record).slice(0, 40) : ''
  amount.value = ''
})

function startEdit(template: SavedTransactionTemplate) {
  editingId.value = template.id
  name.value = template.name
  amount.value = template.amount === undefined ? '' : String(template.amount)
}

function persist(userId: number, next: SavedTransactionTemplate[]) {
  try {
    writeSavedTemplates(userId, next)
    templates.value = next
    return true
  } catch {
    showFailToast('无法保存模板，请检查浏览器存储设置')
    return false
  }
}

function save() {
  const userId = auth.user?.id
  if (userId === undefined) return
  const title = name.value.trim()
  if (!title) {
    showToast('请输入模板名称')
    return
  }
  const amountError = amount.value ? moneyError(amount.value) : ''
  if (amountError) {
    showToast(amountError)
    return
  }
  const latest = readSavedTemplates(userId)
  const fixedAmount = amount.value ? Number(amount.value) : undefined
  if (props.record) {
    const created = createSavedTemplate(props.record, title, fixedAmount)
    if (!persist(userId, [created, ...latest])) return
    show.value = false
    showToast('已保存常用模板')
    return
  }
  if (!latest.some((template) => template.id === editingId.value)) {
    templates.value = latest
    editingId.value = undefined
    showToast('该模板已删除')
    return
  }
  const next = latest.map((template) => template.id === editingId.value
    ? { ...template, name: title, amount: fixedAmount }
    : template)
  if (!persist(userId, next)) return
  editingId.value = undefined
  showToast('模板已更新')
}

async function remove(template: SavedTransactionTemplate) {
  const userId = auth.user?.id
  if (userId === undefined) return
  try {
    await showConfirmDialog({ title: '删除模板', message: `确定删除「${template.name}」？` })
  } catch {
    return
  }
  if (userId !== auth.user?.id) return
  if (persist(userId, readSavedTemplates(userId).filter((item) => item.id !== template.id))) {
    showToast('模板已删除')
  }
}
</script>

<template>
  <BottomSheet
    v-model:show="show"
    :title="record ? '存为模板' : editingId ? '编辑模板' : '常用模板'"
  >
    <van-form v-if="record || editingId" @submit="save">
      <van-field v-model="name" label="模板名称" placeholder="例如工作日午餐" maxlength="40" />
      <van-field v-model="amount" label="金额（选填）" type="number" placeholder="留空，每次套用后填写">
        <template v-if="record" #button>
          <van-button size="small" native-type="button" @click="amount = String(record.amount)">用原金额</van-button>
        </template>
      </van-field>
      <div class="template-actions">
        <van-button v-if="editingId" native-type="button" @click="editingId = undefined">返回列表</van-button>
        <van-button type="primary" native-type="submit" icon="success" aria-label="保存模板">保存模板</van-button>
      </div>
    </van-form>
    <div v-else-if="templates.length" @touchmove.stop>
      <van-cell
        v-for="template in templates"
        :key="template.id"
        :title="template.name"
        :label="`${template.type === 'EXPENSE' ? '支出' : '收入'} · ${template.amount === undefined ? '金额待填' : `¥${money(template.amount)}`}`"
      >
        <template #value>
          <div class="template-actions">
            <van-button size="small" native-type="button" type="primary" @click="emit('apply', template)">套用</van-button>
            <van-button size="small" native-type="button" icon="edit" :aria-label="`编辑${template.name}`" @click="startEdit(template)" />
            <van-button size="small" native-type="button" icon="delete-o" :aria-label="`删除${template.name}`" @click="remove(template)" />
          </div>
        </template>
      </van-cell>
    </div>
    <van-empty v-else description="暂无常用模板，可在流水详情中存为模板" />
  </BottomSheet>
</template>

<style scoped>
.template-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: var(--space-8);
}

.van-form > .template-actions {
  margin-top: var(--space-12);
}
</style>
