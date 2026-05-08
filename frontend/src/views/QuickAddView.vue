<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { categoryApi, paymentMethodApi, transactionApi } from '@/api/services'
import type { Category, PaymentMethod } from '@/types'
import { nowLocalInput, toBackendDateTime } from '@/utils/date'
import { showError } from '@/utils/errors'
import { moneyError } from '@/utils/money'

const router = useRouter()
const categories = ref<Category[]>([])
const paymentMethods = ref<PaymentMethod[]>([])
const saving = ref(false)
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

onMounted(loadOptions)
</script>

<template>
  <main class="page">
    <van-nav-bar title="快速记一笔" />
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
        <van-field v-model="form.occurredAt" label="时间" type="datetime-local" required />
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
        <van-field
          v-else
          v-model="form.offlinePlace"
          label="地点"
          placeholder="如美宜佳，后续可接入高德定位"
          required
        />
        <van-field label="支付方式">
          <template #input>
            <select v-model.number="form.paymentMethodId" class="native-select">
              <option v-for="item in paymentMethods" :key="item.id" :value="item.id">{{ item.name }}</option>
            </select>
          </template>
        </van-field>
        <van-field label="分类">
          <template #input>
            <select v-model.number="form.categoryId" class="native-select">
              <option v-for="item in filteredCategories" :key="item.id" :value="item.id">{{ item.name }}</option>
            </select>
          </template>
        </van-field>
        <van-field v-model="form.note" label="备注" placeholder="可选" />
      </van-cell-group>
      <div class="form-actions">
        <van-button round block type="primary" native-type="submit" :loading="saving">保存</van-button>
      </div>
    </van-form>
  </main>
</template>

<style scoped>
.native-select {
  width: 100%;
  border: 0;
  background: transparent;
  color: #1f2933;
  font: inherit;
}
</style>
