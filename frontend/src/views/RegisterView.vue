<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { useAuthStore } from '@/stores/auth'
import { maxTextLength, requiredText, textLength } from '@/utils/validation'

const auth = useAuthStore()
const router = useRouter()
const form = reactive({ username: '', nickname: '', password: '', email: '', emailCode: '' })
const loading = ref(false)
const sending = ref(false)

async function sendEmailCode() {
  if (sending.value) return
  const emailError = requiredText(form.email, '邮箱')
  if (emailError) {
    showToast(emailError)
    return
  }
  sending.value = true
  try {
    await auth.sendRegisterEmailCode(form.email.trim())
    showToast('验证码已发送')
  } catch (error) {
    showToast(error instanceof Error ? error.message : '发送失败')
  } finally {
    sending.value = false
  }
}

async function submit() {
  if (loading.value) return
  const usernameError = requiredText(form.username, '用户名') || textLength(form.username, '用户名', 3, 32)
  const nicknameError = requiredText(form.nickname, '昵称') || maxTextLength(form.nickname, '昵称', 32)
  const passwordError = requiredText(form.password, '密码') || textLength(form.password, '密码', 6, 64)
  const emailError = requiredText(form.email, '邮箱')
  const codeError = requiredText(form.emailCode, '验证码')
  const firstError = usernameError || nicknameError || passwordError || emailError || codeError
  if (firstError) {
    showToast(firstError)
    return
  }
  loading.value = true
  try {
    await auth.register(form.username.trim(), form.password, form.nickname.trim(), form.email.trim(), form.emailCode.trim())
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
    <p class="auth-subtitle">系统会自动创建常用分类、支付方式和线上平台</p>

    <van-form @submit="submit">
      <van-cell-group inset>
        <van-field v-model="form.username" name="username" label="用户名" placeholder="3-32 位" required />
        <van-field v-model="form.nickname" name="nickname" label="昵称" placeholder="用于页面显示" required />
        <van-field v-model="form.password" type="password" name="password" label="密码" placeholder="至少 6 位" required />
        <van-field v-model="form.email" type="email" name="email" label="邮箱" placeholder="用于登录验证" required>
          <template #button>
            <van-button size="small" type="primary" native-type="button" :loading="sending" @click="sendEmailCode">发送</van-button>
          </template>
        </van-field>
        <van-field v-model="form.emailCode" name="emailCode" label="验证码" maxlength="6" inputmode="numeric" placeholder="6 位数字验证码" required />
      </van-cell-group>
      <div class="form-actions stack-actions">
        <van-button round block type="primary" icon="success" native-type="submit" :loading="loading">注册并登录</van-button>
        <van-button round block plain type="primary" icon="arrow-left" native-type="button" to="/login">返回登录</van-button>
      </div>
    </van-form>
  </main>
</template>
