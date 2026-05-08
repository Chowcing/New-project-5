<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { useAuthStore } from '@/stores/auth'
import { maxTextLength, requiredText, textLength } from '@/utils/validation'

const auth = useAuthStore()
const router = useRouter()
const form = reactive({ username: '', nickname: '', password: '' })
const loading = ref(false)

async function submit() {
  if (loading.value) return
  const usernameError = requiredText(form.username, '用户名') || textLength(form.username, '用户名', 3, 32)
  if (usernameError) {
    showToast(usernameError)
    return
  }
  const nicknameError = requiredText(form.nickname, '昵称') || maxTextLength(form.nickname, '昵称', 32)
  if (nicknameError) {
    showToast(nicknameError)
    return
  }
  const passwordError = requiredText(form.password, '密码') || textLength(form.password, '密码', 6, 64)
  if (passwordError) {
    showToast(passwordError)
    return
  }
  loading.value = true
  try {
    await auth.register(form.username.trim(), form.password, form.nickname.trim())
    showToast('注册成功')
    await router.replace('/')
  } catch (error) {
    showToast(error instanceof Error ? error.message : '注册失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="auth-page">
    <h1 class="auth-title">创建账号</h1>
    <p class="auth-subtitle">系统会自动创建常用分类和默认支付方式</p>

    <van-form @submit="submit">
      <van-cell-group inset>
        <van-field v-model="form.username" name="username" label="用户名" placeholder="3-32 位" required />
        <van-field v-model="form.nickname" name="nickname" label="昵称" placeholder="用于页面显示" required />
        <van-field v-model="form.password" type="password" name="password" label="密码" placeholder="至少 6 位" required />
      </van-cell-group>
      <div class="form-actions">
        <van-button round block type="primary" native-type="submit" :loading="loading">注册并登录</van-button>
        <van-button round block plain type="primary" native-type="button" to="/login" style="margin-top: 10px">返回登录</van-button>
      </div>
    </van-form>
  </main>
</template>
