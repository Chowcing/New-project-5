<script setup lang="ts">
import { useRouter } from 'vue-router'
import { showConfirmDialog } from 'vant'
import { useAuthStore } from '@/stores/auth'
import { showError } from '@/utils/errors'

const auth = useAuthStore()
const router = useRouter()

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
        <van-cell title="数据导出" icon="down" is-link to="/export" />
        <van-cell title="数据导入" icon="upgrade" is-link to="/import" />
      </section>

      <section class="section">
        <van-button block round plain type="danger" @click="logout">退出登录</van-button>
      </section>
    </div>
  </main>
</template>
