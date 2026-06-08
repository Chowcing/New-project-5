<script setup lang="ts">
import { onUnmounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { useAuthStore } from '@/stores/auth'
import { requiredText } from '@/utils/validation'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()
const step = ref<'password' | 'mfa' | 'bind'>('password')
const form = reactive({ username: '', password: '', code: '', email: '', bindCode: '' })
const challengeId = ref('')
const maskedEmail = ref('')
const loading = ref(false)
const sending = ref(false)
const resendSeconds = ref(0)
let resendTimer: ReturnType<typeof window.setInterval> | undefined

function clearResendTimer() {
  if (resendTimer) {
    window.clearInterval(resendTimer)
    resendTimer = undefined
  }
}

function startResendCountdown(seconds = 60) {
  clearResendTimer()
  resendSeconds.value = seconds
  resendTimer = window.setInterval(() => {
    resendSeconds.value -= 1
    if (resendSeconds.value <= 0) {
      resendSeconds.value = 0
      clearResendTimer()
    }
  }, 1000)
}

function backToPassword() {
  step.value = 'password'
  form.code = ''
  form.bindCode = ''
  clearResendTimer()
  resendSeconds.value = 0
}

async function submitPassword() {
  if (loading.value) return
  const usernameError = requiredText(form.username, '用户名')
  const passwordError = requiredText(form.password, '密码')
  if (usernameError || passwordError) {
    showToast(usernameError || passwordError)
    return
  }
  loading.value = true
  try {
    const response = await auth.startLogin(form.username.trim(), form.password)
    challengeId.value = response.challengeId
    maskedEmail.value = response.email || ''
    step.value = response.status === 'MFA_REQUIRED' ? 'mfa' : 'bind'
    if (response.status === 'MFA_REQUIRED') {
      form.code = ''
      startResendCountdown()
    }
  } catch (error) {
    showToast(error instanceof Error ? error.message : '登录失败')
  } finally {
    loading.value = false
  }
}

async function resendLoginCode() {
  if (sending.value || resendSeconds.value > 0) return
  sending.value = true
  try {
    challengeId.value = await auth.resendLoginCode(challengeId.value)
    form.code = ''
    startResendCountdown()
    showToast('验证码已发送')
  } catch (error) {
    showToast(error instanceof Error ? error.message : '发送失败')
  } finally {
    sending.value = false
  }
}

async function submitMfa() {
  if (loading.value) return
  const codeError = requiredText(form.code, '验证码')
  if (codeError) {
    showToast(codeError)
    return
  }
  loading.value = true
  try {
    await auth.verifyLogin(challengeId.value, form.code.trim())
    await router.replace((route.query.redirect as string) || '/')
  } catch (error) {
    showToast(error instanceof Error ? error.message : '验证失败')
  } finally {
    loading.value = false
  }
}

async function sendBindCode() {
  if (sending.value) return
  const emailError = requiredText(form.email, '邮箱')
  if (emailError) {
    showToast(emailError)
    return
  }
  sending.value = true
  try {
    challengeId.value = await auth.sendBindEmailCode(challengeId.value, form.email.trim())
    showToast('验证码已发送')
  } catch (error) {
    showToast(error instanceof Error ? error.message : '发送失败')
  } finally {
    sending.value = false
  }
}

async function submitBind() {
  if (loading.value) return
  const codeError = requiredText(form.bindCode, '验证码')
  if (codeError) {
    showToast(codeError)
    return
  }
  loading.value = true
  try {
    await auth.verifyBindEmail(challengeId.value, form.bindCode.trim())
    await router.replace((route.query.redirect as string) || '/')
  } catch (error) {
    showToast(error instanceof Error ? error.message : '绑定失败')
  } finally {
    loading.value = false
  }
}

onUnmounted(() => {
  clearResendTimer()
})
</script>

<template>
  <main class="auth-page">
    <h1 class="auth-title">生活消费记录</h1>
    <p class="auth-subtitle">{{ step === 'password' ? '登录后开始记录每天的收入和支出' : step === 'mfa' ? `验证码已发送至 ${maskedEmail}` : '首次登录需要绑定邮箱' }}</p>

    <van-form v-if="step === 'password'" @submit="submitPassword">
      <van-cell-group inset>
        <van-field v-model="form.username" name="username" label="用户名" placeholder="请输入用户名" required />
        <van-field v-model="form.password" type="password" name="password" label="密码" placeholder="请输入密码" required />
      </van-cell-group>
      <div class="form-actions stack-actions">
        <van-button round block type="primary" icon="manager-o" native-type="submit" :loading="loading">下一步</van-button>
        <van-button round block plain type="primary" icon="add-o" native-type="button" to="/register">注册新账号</van-button>
      </div>
    </van-form>

    <van-form v-else-if="step === 'mfa'" @submit="submitMfa">
      <van-cell-group inset>
        <van-field v-model="form.code" name="code" label="验证码" maxlength="6" inputmode="numeric" placeholder="6 位数字验证码" required>
          <template #button>
            <van-button size="small" type="primary" native-type="button" :loading="sending" :disabled="resendSeconds > 0" @click="resendLoginCode">
              {{ resendSeconds > 0 ? `${resendSeconds}s` : '重新获取' }}
            </van-button>
          </template>
        </van-field>
      </van-cell-group>
      <div class="form-actions stack-actions">
        <van-button round block type="primary" icon="passed" native-type="submit" :loading="loading">验证并登录</van-button>
        <van-button round block plain type="primary" icon="arrow-left" native-type="button" @click="backToPassword">返回</van-button>
      </div>
    </van-form>

    <van-form v-else @submit="submitBind">
      <van-cell-group inset>
        <van-field v-model="form.email" name="email" label="邮箱" type="email" placeholder="请输入邮箱" required>
          <template #button>
            <van-button size="small" type="primary" native-type="button" :loading="sending" @click="sendBindCode">发送</van-button>
          </template>
        </van-field>
        <van-field v-model="form.bindCode" name="bindCode" label="验证码" maxlength="6" inputmode="numeric" placeholder="6 位数字验证码" required />
      </van-cell-group>
      <div class="form-actions stack-actions">
        <van-button round block type="primary" icon="passed" native-type="submit" :loading="loading">绑定并登录</van-button>
        <van-button round block plain type="primary" icon="arrow-left" native-type="button" @click="backToPassword">返回</van-button>
      </div>
    </van-form>
  </main>
</template>
