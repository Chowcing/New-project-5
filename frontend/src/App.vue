<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { haptic } from '@/utils/haptics'

const route = useRoute()
const router = useRouter()
const showMainShell = computed(() => Boolean(route.meta.mainTab))
const addMenuOpen = ref(false)

function toggleAddMenu() {
  haptic('tap')
  addMenuOpen.value = !addMenuOpen.value
}

async function openQuickAdd(type: 'EXPENSE' | 'INCOME') {
  haptic('selection')
  addMenuOpen.value = false
  await router.push({
    path: '/quick-add',
    query: { type }
  })
}

watch(() => route.fullPath, () => {
  addMenuOpen.value = false
})
</script>

<template>
  <div class="app-shell">
    <router-view />

    <div v-if="showMainShell" :class="['app-add-menu', { open: addMenuOpen }]">
      <Transition name="app-add-options">
        <div v-if="addMenuOpen" class="app-add-options" role="menu" aria-label="选择记账类型">
          <button class="app-add-option expense" type="button" role="menuitem" @click="openQuickAdd('EXPENSE')">
            <van-icon name="cart-o" />
            <span>支出</span>
          </button>
          <button class="app-add-option income" type="button" role="menuitem" @click="openQuickAdd('INCOME')">
            <van-icon name="cash-back-record" />
            <span>收入</span>
          </button>
        </div>
      </Transition>

      <button
        :class="['app-main-fab', { open: addMenuOpen }]"
        type="button"
        :aria-expanded="addMenuOpen"
        aria-label="快速记一笔"
        title="快速记一笔"
        @click="toggleAddMenu"
      >
        <van-icon name="plus" />
      </button>
    </div>

    <van-tabbar v-if="showMainShell" class="app-tabbar" route fixed>
      <van-tabbar-item to="/" icon="apps-o">工作台</van-tabbar-item>
      <van-tabbar-item to="/records" icon="orders-o">流水</van-tabbar-item>
      <van-tabbar-item to="/statistics" icon="bar-chart-o">分析</van-tabbar-item>
      <van-tabbar-item to="/settings" icon="manager-o">我的</van-tabbar-item>
    </van-tabbar>
  </div>
</template>
