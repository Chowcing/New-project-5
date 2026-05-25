<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { useAuthStore } from '@/stores/auth'
import ModernSelectField from '@/components/ModernSelectField.vue'
import { showError } from '@/utils/errors'
import { haptic } from '@/utils/haptics'
import { DAY_RECORD_PAGE_SIZE_OPTIONS, loadDayRecordPageSize, saveDayRecordPageSize } from '@/utils/preferences'
import {
  THEME_ACCENT_OPTIONS,
  THEME_APPEARANCE_OPTIONS,
  loadThemePreference,
  saveThemePreference,
  type ThemeAccent,
  type ThemeAppearance
} from '@/utils/themes'

const auth = useAuthStore()
const router = useRouter()
const dayRecordPageSize = ref(loadDayRecordPageSize())
const themePreference = ref(loadThemePreference())
const isAdmin = computed(() => auth.user?.admin === true)
const deploymentVersion = import.meta.env.VITE_EXPENSE_DEPLOYMENT_VERSION || 'local-dev'

const managementItems = computed(() => [
  { title: '分类', icon: 'apps-o', to: '/categories' },
  { title: '支付方式', icon: 'balance-o', to: '/payment-methods' },
  { title: '预算', icon: 'chart-trending-o', to: '/budgets' },
  { title: '周期', icon: 'replay', to: '/recurring-rules' },
  ...(isAdmin.value ? [{ title: '后台', icon: 'manager-o', to: '/admin' }] : [])
])

function setDayRecordPageSize(value: string | number | undefined) {
  if (typeof value !== 'number') {
    return
  }
  dayRecordPageSize.value = saveDayRecordPageSize(value)
  haptic('confirm')
  showToast('明细显示设置已保存')
}

function setThemeAppearance(value: string | number | undefined) {
  if (value !== 'system' && value !== 'light' && value !== 'dark') {
    return
  }
  themePreference.value = saveThemePreference({
    ...themePreference.value,
    appearance: value as ThemeAppearance
  })
  haptic('confirm')
  showToast('外观已更新')
}

function setThemeAccent(value: string) {
  if (value !== 'cyan' && value !== 'blue' && value !== 'violet') {
    return
  }
  themePreference.value = saveThemePreference({
    ...themePreference.value,
    accent: value as ThemeAccent
  })
  haptic('selection')
  showToast('强调色已更新')
}

async function logout() {
  haptic('tap')
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
  <main class="page settings-page">
    <div class="page-content settings-content">
      <section class="section panel profile-panel">
        <div class="profile-avatar">
          <van-icon name="manager-o" />
        </div>
        <div class="profile-copy">
          <span>当前用户</span>
          <strong>{{ auth.user?.nickname || auth.user?.username || '-' }}</strong>
          <p>{{ auth.user?.admin ? '管理员账号' : '个人账本账号' }}</p>
        </div>
      </section>

      <section class="section panel settings-workbench">
        <div class="section-heading settings-heading">常用管理</div>
        <div class="settings-grid">
          <RouterLink
            v-for="item in managementItems"
            :key="item.to"
            class="settings-grid-item"
            :to="item.to"
          >
            <van-icon :name="item.icon" />
            <span>{{ item.title }}</span>
          </RouterLink>
        </div>
      </section>

      <section class="section panel settings-workbench">
        <div class="section-heading settings-heading">数据管理</div>
        <div class="settings-grid two">
          <RouterLink class="settings-grid-item" to="/export">
            <van-icon name="down" />
            <span>导出</span>
          </RouterLink>
          <RouterLink class="settings-grid-item" to="/import">
            <van-icon name="upgrade" />
            <span>导入</span>
          </RouterLink>
        </div>
      </section>

      <section class="section panel settings-preferences">
        <div class="section-heading settings-heading">偏好设置</div>
        <ModernSelectField
          :model-value="dayRecordPageSize"
          label="当天记录"
          title="选择当天初始显示条数"
          :options="DAY_RECORD_PAGE_SIZE_OPTIONS"
          @update:model-value="setDayRecordPageSize"
        />
        <ModernSelectField
          :model-value="themePreference.appearance"
          label="外观模式"
          title="选择外观模式"
          :options="THEME_APPEARANCE_OPTIONS"
          @update:model-value="setThemeAppearance"
        />
        <div class="theme-accent-row" role="group" aria-label="主题强调色">
          <button
            v-for="item in THEME_ACCENT_OPTIONS"
            :key="item.value"
            type="button"
            :class="['theme-accent-button', { active: themePreference.accent === item.value }]"
            :style="{ '--accent-color': item.color }"
            :aria-label="`选择${item.label}`"
            @click="setThemeAccent(item.value)"
          >
            <span class="theme-accent-swatch" />
            <span>{{ item.label }}</span>
            <van-icon v-if="themePreference.accent === item.value" name="success" />
          </button>
        </div>
      </section>

      <section class="section panel system-panel">
        <van-cell title="部署版本" icon="info-o" :value="deploymentVersion" />
      </section>

      <section class="section">
        <van-button block round plain type="danger" icon="revoke" @click="logout">退出登录</van-button>
      </section>
    </div>
  </main>
</template>

<style scoped>
.settings-content {
  gap: var(--space-10);
}

.profile-panel {
  display: grid;
  grid-template-columns: 54px minmax(0, 1fr);
  gap: var(--space-12);
  align-items: center;
  background:
    radial-gradient(circle at 90% 0%, rgba(var(--theme-primary-glow-rgb), 0.22), transparent 34%),
    var(--card-bg);
}

.profile-avatar {
  display: grid;
  width: 54px;
  height: 54px;
  place-items: center;
  border-radius: 20px;
  background: linear-gradient(135deg, var(--primary), var(--primary-deep));
  color: #fff;
  font-size: var(--icon-size-xl);
  box-shadow: 0 16px 34px rgba(var(--theme-primary-glow-rgb), 0.24);
}

.profile-copy {
  min-width: 0;
}

.profile-copy span,
.profile-copy p {
  margin: 0;
  color: var(--text-secondary);
  font-size: var(--font-size-caption);
  line-height: var(--line-height-caption);
}

.profile-copy strong {
  display: block;
  overflow: hidden;
  margin: var(--space-3) 0;
  color: var(--text-main);
  font-size: var(--font-size-panel-title);
  line-height: var(--line-height-panel-title);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.settings-workbench {
  padding: var(--space-14);
}

.settings-heading {
  margin-bottom: var(--space-10);
}

.settings-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-8);
}

.settings-grid.two {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.settings-grid-item {
  display: grid;
  gap: var(--space-7);
  min-height: 70px;
  place-items: center;
  border: 1px solid rgba(var(--theme-border-warm-rgb), 0.2);
  border-radius: var(--radius-card);
  padding: var(--space-10) var(--space-6);
  background: rgba(var(--theme-border-warm-rgb), 0.08);
  color: var(--text-main);
  font-size: var(--font-size-caption);
  font-weight: 700;
  text-align: center;
}

.settings-grid-item :deep(.van-icon) {
  color: var(--primary);
  font-size: var(--icon-size-lg);
}

.settings-preferences,
.system-panel {
  padding: var(--space-0);
  overflow: hidden;
}

.settings-preferences .settings-heading {
  padding: var(--space-14) var(--space-14) var(--space-0);
}

.theme-accent-row {
  display: grid;
  gap: var(--space-8);
  padding: var(--space-12) var(--space-16) var(--space-14);
}

.theme-accent-button {
  display: grid;
  grid-template-columns: 22px minmax(0, 1fr) 18px;
  gap: var(--space-10);
  align-items: center;
  min-height: 42px;
  border: 1px solid rgba(var(--theme-border-warm-rgb), 0.2);
  border-radius: var(--radius-card);
  padding: var(--space-0) var(--space-12);
  background: rgba(var(--theme-border-warm-rgb), 0.08);
  color: var(--text-main);
  font: inherit;
  font-size: var(--font-size-meta);
  font-weight: 650;
  text-align: left;
}

.theme-accent-button.active {
  border-color: var(--primary);
  background: var(--primary-soft);
}

.theme-accent-swatch {
  width: 22px;
  height: 22px;
  border-radius: var(--radius-pill);
  background: var(--accent-color);
  box-shadow: 0 0 0 4px rgba(var(--theme-border-warm-rgb), 0.12);
}

@media (max-width: 360px) {
  .settings-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
</style>
