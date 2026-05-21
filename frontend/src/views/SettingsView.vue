<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { useAuthStore } from '@/stores/auth'
import ModernSelectField from '@/components/ModernSelectField.vue'
import { showError } from '@/utils/errors'
import { DAY_RECORD_PAGE_SIZE_OPTIONS, loadDayRecordPageSize, saveDayRecordPageSize } from '@/utils/preferences'
import {
  THEME_PRESET_OPTIONS,
  THEME_PRIMARY_OPTIONS,
  loadThemePreference,
  saveThemePreference,
  type ThemePresetKey
} from '@/utils/themes'

const auth = useAuthStore()
const router = useRouter()
const dayRecordPageSize = ref(loadDayRecordPageSize())
const themePreference = ref(loadThemePreference())
const isAdmin = computed(() => auth.user?.admin === true)
const deploymentVersion = 'CD-20260515-01'

function setDayRecordPageSize(value: string | number | undefined) {
  if (typeof value !== 'number') {
    return
  }
  dayRecordPageSize.value = saveDayRecordPageSize(value)
  showToast('明细显示设置已保存')
}

function setThemePreset(value: string | number | undefined) {
  if (value !== 'warm' && value !== 'fresh' && value !== 'coffee') {
    return
  }
  themePreference.value = saveThemePreference({
    ...themePreference.value,
    themePreset: value as ThemePresetKey
  })
  showToast('主题已更新')
}

function setThemePrimary(color: string) {
  themePreference.value = saveThemePreference({
    ...themePreference.value,
    themePrimary: color
  })
  showToast('主题主色已更新')
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
        <div class="section-heading settings-heading">常用管理</div>
        <van-cell title="分类管理" icon="apps-o" is-link to="/categories" />
        <van-cell title="支付方式管理" icon="balance-o" is-link to="/payment-methods" />
        <van-cell title="预算管理" icon="chart-trending-o" is-link to="/budgets" />
        <van-cell title="周期记账" icon="replay" is-link to="/recurring-rules" />
        <van-cell v-if="isAdmin" title="后台管理" icon="manager-o" is-link to="/admin" />
      </section>

      <section class="section panel">
        <div class="section-heading settings-heading">数据管理</div>
        <van-cell title="数据导出" icon="down" is-link to="/export" />
        <van-cell title="数据导入" icon="upgrade" is-link to="/import" />
      </section>

      <section class="section panel">
        <div class="section-heading settings-heading">偏好设置</div>
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
        <div class="section-heading settings-heading">主题偏好</div>
        <van-cell title="界面主题" label="选择界面氛围和强调色" />
        <ModernSelectField
          :model-value="themePreference.themePreset"
          label="主题"
          title="选择主题"
          :options="THEME_PRESET_OPTIONS"
          @update:model-value="setThemePreset"
        />
        <div class="theme-color-row" role="group" aria-label="主题主色">
          <button
            v-for="color in THEME_PRIMARY_OPTIONS"
            :key="color"
            type="button"
            :class="['theme-color-button', { active: themePreference.themePrimary === color }]"
            :style="{ backgroundColor: color }"
            :aria-label="`选择主色 ${color}`"
            @click="setThemePrimary(color)"
          >
            <van-icon v-if="themePreference.themePrimary === color" name="success" />
          </button>
        </div>
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

<style scoped>
.settings-heading {
  padding: 0 2px;
}

.theme-color-row {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 10px;
  padding: 12px 16px 4px;
}

.theme-color-button {
  display: grid;
  place-items: center;
  width: 100%;
  aspect-ratio: 1;
  border: 2px solid transparent;
  border-radius: 8px;
  color: #fff;
  font: inherit;
  box-shadow: 0 0 0 1px rgba(var(--theme-border-warm-rgb), 0.82) inset;
}

.theme-color-button.active {
  border-color: var(--text-main);
  box-shadow: 0 0 0 2px var(--card-bg) inset;
}
</style>
