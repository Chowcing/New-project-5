<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { categoryApi, paymentMethodApi, transactionApi } from '@/api/services'
import AmapPlaceField from '@/components/AmapPlaceField.vue'
import ModernDateField from '@/components/ModernDateField.vue'
import TransactionOptionFields from '@/components/TransactionOptionFields.vue'
import type { Category, PaymentMethod, TransactionTemplate } from '@/types'
import { nowLocalInput, toBackendDateTime } from '@/utils/date'
import { showError } from '@/utils/errors'
import { moneyError } from '@/utils/money'

const router = useRouter()
const categories = ref<Category[]>([])
const paymentMethods = ref<PaymentMethod[]>([])
const templates = ref<TransactionTemplate[]>([])
const saving = ref(false)
const templatesLoading = ref(false)
const form = reactive({
  type: 'EXPENSE' as 'EXPENSE' | 'INCOME',
  itemName: '',
  amount: '',
  occurredAt: nowLocalInput(),
  channel: 'OFFLINE' as 'ONLINE' | 'OFFLINE',
  onlineApp: '',
  offlinePlace: '',
  paymentMethodId: undefined as number | undefined,
  categoryId: undefined as number | undefined,
  note: ''
})

const filteredCategories = computed(() => categories.value.filter((item) => item.type === form.type))

function sortBySortOrder<T extends { id: number; sortOrder?: number }>(items: T[]) {
  return [...items].sort((left, right) => (left.sortOrder || 0) - (right.sortOrder || 0) || right.id - left.id)
}

function addCategoryOption(category: Category) {
  categories.value = sortBySortOrder([...categories.value.filter((item) => item.id !== category.id), category])
}

function addPaymentMethodOption(paymentMethod: PaymentMethod) {
  paymentMethods.value = sortBySortOrder([...paymentMethods.value.filter((item) => item.id !== paymentMethod.id), paymentMethod])
}

async function loadOptions() {
  try {
    categories.value = await categoryApi.list()
    paymentMethods.value = await paymentMethodApi.list()
    form.categoryId = filteredCategories.value[0]?.id
    form.paymentMethodId = paymentMethods.value[0]?.id
  } catch (error) {
    showError(error, '选项加载失败')
  }
}

async function loadRecommendations() {
  templatesLoading.value = true
  try {
    templates.value = await transactionApi.recommendations(5)
  } catch (error) {
    showError(error, '推荐模板加载失败')
  } finally {
    templatesLoading.value = false
  }
}

function applyTemplate(template: TransactionTemplate) {
  form.type = template.type
  form.itemName = template.itemName
  form.amount = String(template.amount)
  form.occurredAt = nowLocalInput()
  form.channel = template.channel
  form.onlineApp = template.onlineApp || ''
  form.offlinePlace = template.offlinePlace || ''
  form.paymentMethodId = template.paymentMethodId
  form.categoryId = template.categoryId
  form.note = template.note || ''
  showToast('已套用推荐模板')
}

async function submit() {
  if (saving.value) return
  if (!form.categoryId || !form.paymentMethodId) {
    showToast('请先创建分类和支付方式')
    return
  }
  if (!form.itemName.trim()) {
    showToast('请填写事项')
    return
  }
  const amountError = moneyError(form.amount)
  if (amountError) {
    showToast(amountError)
    return
  }
  if (!form.occurredAt) {
    showToast('请选择发生时间')
    return
  }
  if (form.channel === 'OFFLINE' && !form.offlinePlace.trim()) {
    showToast('线下记录需要填写地点')
    return
  }
  if (form.channel === 'ONLINE' && form.type === 'EXPENSE' && !form.onlineApp.trim()) {
    showToast('线上支出需要填写消费 APP')
    return
  }
  saving.value = true
  try {
    await transactionApi.create({
      type: form.type,
      itemName: form.itemName.trim(),
      amount: Number(form.amount),
      occurredAt: toBackendDateTime(form.occurredAt),
      channel: form.channel,
      onlineApp: form.channel === 'ONLINE' ? form.onlineApp.trim() : undefined,
      offlinePlace: form.channel === 'OFFLINE' ? form.offlinePlace.trim() : undefined,
      paymentMethodId: form.paymentMethodId,
      categoryId: form.categoryId,
      note: form.note.trim() || undefined
    })
    showToast('记录已保存')
    await router.push('/records')
  } catch (error) {
    showError(error, '保存失败')
  } finally {
    saving.value = false
  }
}

async function init() {
  await Promise.all([loadOptions(), loadRecommendations()])
}

onMounted(init)
</script>

<template>
  <main class="page">
    <van-nav-bar title="快速记一笔" />
    <div class="page-content quick-add-recommendations">
      <section v-if="templatesLoading || templates.length" class="section panel">
        <van-cell title="当前时段推荐" />
        <div v-if="templatesLoading" class="muted recommendation-loading">正在生成推荐...</div>
        <van-cell
          v-for="item in templates"
          :key="`${item.type}-${item.itemName}-${item.categoryId}-${item.paymentMethodId}-${item.channel}`"
          is-link
          :title="item.itemName || item.categoryName"
          :label="`${item.reason} · ${item.categoryName} · ${item.paymentMethodName}`"
          :value="`${item.type === 'EXPENSE' ? '-' : '+'}¥${Number(item.amount).toFixed(2)}`"
          :value-class="item.type === 'EXPENSE' ? 'expense' : 'income'"
          @click="applyTemplate(item)"
        />
      </section>
    </div>
    <van-form @submit="submit">
      <van-cell-group inset>
        <van-field label="类型">
          <template #input>
            <van-radio-group v-model="form.type" direction="horizontal" @change="form.categoryId = filteredCategories[0]?.id">
              <van-radio name="EXPENSE">支出</van-radio>
              <van-radio name="INCOME">收入</van-radio>
            </van-radio-group>
          </template>
        </van-field>
        <van-field v-model="form.itemName" label="事项" placeholder="如冰棍、工资、泳镜" required />
        <van-field v-model="form.amount" label="金额" type="text" inputmode="decimal" placeholder="0.00" required />
        <ModernDateField v-model="form.occurredAt" mode="datetime" label="时间" title="选择发生时间" required />
        <van-field label="渠道">
          <template #input>
            <van-radio-group v-model="form.channel" direction="horizontal">
              <van-radio name="ONLINE">线上</van-radio>
              <van-radio name="OFFLINE">线下</van-radio>
            </van-radio-group>
          </template>
        </van-field>
        <van-field
          v-if="form.channel === 'ONLINE'"
          v-model="form.onlineApp"
          label="APP"
          :placeholder="form.type === 'EXPENSE' ? '如淘宝、美团、京东' : '可选，如银行、公司系统'"
          :required="form.type === 'EXPENSE'"
        />
        <AmapPlaceField v-else v-model="form.offlinePlace" label="地点" required />
        <TransactionOptionFields
          v-model:payment-method-id="form.paymentMethodId"
          v-model:category-id="form.categoryId"
          :payment-methods="paymentMethods"
          :categories="categories"
          :transaction-type="form.type"
          @payment-method-created="addPaymentMethodOption"
          @category-created="addCategoryOption"
        />
        <van-field v-model="form.note" label="备注" placeholder="可选" />
      </van-cell-group>
      <div class="form-actions">
        <van-button round block type="primary" native-type="submit" :loading="saving">保存</van-button>
      </div>
    </van-form>
  </main>
</template>

<style scoped>
.quick-add-recommendations {
  padding-bottom: 0;
}

.recommendation-loading {
  padding: 8px 16px;
}

</style>
