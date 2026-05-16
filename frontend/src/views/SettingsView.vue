<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { useAuthStore } from '@/stores/auth'
import ModernSelectField from '@/components/ModernSelectField.vue'
import { showError } from '@/utils/errors'
import { DAY_RECORD_PAGE_SIZE_OPTIONS, loadDayRecordPageSize, saveDayRecordPageSize } from '@/utils/preferences'

const auth = useAuthStore()
const router = useRouter()
const dayRecordPageSize = ref(loadDayRecordPageSize())
const deploymentVersion = 'CD-20260515-01'

function setDayRecordPageSize(value: string | number | undefined) {
  if (typeof value !== 'number') {
    return
  }
  dayRecordPageSize.value = saveDayRecordPageSize(value)
  showToast('明细显示设置已保存')
}

async function logout() {
  try {
    await showConfirmDialog({ title: '退出登录', message: '确认退出当前账号？' })
  } catch {
    return
  }
  try {
    await auth.logout()
    await router.replace('/login')
  } catch (error) {
    showError(error, '退出失败')
  }
}
</script>

<template>
  <main class="page">
    <van-nav-bar title="设置" />
    <div class="page-content">
      <section class="section panel">
        <van-cell title="当前用户" :value="auth.user?.nickname || auth.user?.username || '-'" />
      </section>

      <section class="section panel">
        <van-cell title="分类管理" icon="apps-o" is-link to="/categories" />
        <van-cell title="支付方式管理" icon="balance-o" is-link to="/payment-methods" />
        <van-cell title="预算管理" icon="chart-trending-o" is-link to="/budgets" />
        <van-cell title="周期记账" icon="replay" is-link to="/recurring-rules" />
        <van-cell title="数据导出" icon="down" is-link to="/export" />
        <van-cell title="数据导入" icon="upgrade" is-link to="/import" />
      </section>

      <section class="section panel">
        <van-cell title="明细偏好" label="控制收支明细中每个日期初始展示的记录数量" />
        <ModernSelectField
          :model-value="dayRecordPageSize"
          label="当天记录"
          title="选择当天初始显示条数"
          :options="DAY_RECORD_PAGE_SIZE_OPTIONS"
          @update:model-value="setDayRecordPageSize"
        />
      </section>

      <section class="section panel">
        <van-cell title="部署版本" :value="deploymentVersion" />
      </section>

      <section class="section">
        <van-button block round plain type="danger" @click="logout">退出登录</van-button>
      </section>
    </div>
  </main>
</template>
