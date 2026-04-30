<script setup lang="ts">
import { reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()
const form = reactive({ username: '', password: '' })

async function submit() {
  try {
    await auth.login(form.username, form.password)
    await router.replace((route.query.redirect as string) || '/')
  } catch (error) {
    showToast(error instanceof Error ? error.message : '登录失败')
  }
}
</script>

<template>
  <main class="auth-page">
    <h1 class="auth-title">生活消费记录</h1>
    <p class="auth-subtitle">登录后开始记录每天的收入和支出</p>

    <van-form @submit="submit">
      <van-cell-group inset>
        <van-field v-model="form.username" name="username" label="用户名" placeholder="请输入用户名" required />
        <van-field v-model="form.password" type="password" name="password" label="密码" placeholder="请输入密码" required />
      </van-cell-group>
      <div class="form-actions">
        <van-button round block type="primary" native-type="submit">登录</van-button>
        <van-button round block plain type="primary" to="/register" style="margin-top: 10px">注册新账号</van-button>
      </div>
    </van-form>
  </main>
</template>

