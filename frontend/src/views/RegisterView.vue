<script setup lang="ts">
import { reactive } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const form = reactive({ username: '', nickname: '', password: '' })

async function submit() {
  try {
    await auth.register(form.username, form.password, form.nickname)
    showToast('注册成功')
    await router.replace('/')
  } catch (error) {
    showToast(error instanceof Error ? error.message : '注册失败')
  }
}
</script>

<template>
  <main class="auth-page">
    <h1 class="auth-title">创建账号</h1>
    <p class="auth-subtitle">系统会自动创建常用分类和现金账户</p>

    <van-form @submit="submit">
      <van-cell-group inset>
        <van-field v-model="form.username" name="username" label="用户名" placeholder="3-32 位" required />
        <van-field v-model="form.nickname" name="nickname" label="昵称" placeholder="用于页面显示" required />
        <van-field v-model="form.password" type="password" name="password" label="密码" placeholder="至少 6 位" required />
      </van-cell-group>
      <div class="form-actions">
        <van-button round block type="primary" native-type="submit">注册并登录</van-button>
        <van-button round block plain type="primary" to="/login" style="margin-top: 10px">返回登录</van-button>
      </div>
    </van-form>
  </main>
</template>

