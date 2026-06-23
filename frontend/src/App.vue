<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { haptic } from '@/utils/haptics'
import { resolveNavigationVisibility } from '@/utils/navigationVisibility'
import { blurFocusedDescendant } from '@/utils/shellFocus'

const route = useRoute()
const router = useRouter()
const showMainShell = computed(() => Boolean(route.meta.mainTab))
const addMenuOpen = ref(false)
const shellNavigationVisible = ref(true)
const addMenuRef = ref<HTMLElement | null>(null)
const tabbarRef = ref<{ $el?: Element | null } | HTMLElement | null>(null)

let lastScrollY = 0
let scrollFrameId = 0

function currentScrollY() {
  return Math.max(0, window.scrollY || window.pageYOffset || 0)
}

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

function updateShellNavigationVisibility() {
  scrollFrameId = 0
  const currentY = currentScrollY()

  if (!showMainShell.value) {
    lastScrollY = currentY
    return
  }

  shellNavigationVisible.value = resolveNavigationVisibility({
    previousY: lastScrollY,
    currentY,
    visible: shellNavigationVisible.value
  })
  lastScrollY = currentY
}

function handleWindowScroll() {
  if (scrollFrameId) {
    return
  }

  scrollFrameId = window.requestAnimationFrame(updateShellNavigationVisibility)
}

function resolveShellElement(element: { $el?: Element | null } | HTMLElement | null): HTMLElement | null {
  if (!element) {
    return null
  }

  if (element instanceof HTMLElement) {
    return element
  }

  return element.$el instanceof HTMLElement ? element.$el : null
}

function blurShellFocusedElement() {
  if (typeof document === 'undefined') {
    return
  }

  const activeElement = document.activeElement
  blurFocusedDescendant(addMenuRef.value, activeElement)
  blurFocusedDescendant(resolveShellElement(tabbarRef.value), activeElement)
}

onMounted(() => {
  lastScrollY = currentScrollY()
  window.addEventListener('scroll', handleWindowScroll, { passive: true })
})

onBeforeUnmount(() => {
  window.removeEventListener('scroll', handleWindowScroll)
  if (scrollFrameId) {
    window.cancelAnimationFrame(scrollFrameId)
  }
})

watch(() => route.fullPath, () => {
  addMenuOpen.value = false
  shellNavigationVisible.value = true
  if (typeof window !== 'undefined') {
    lastScrollY = currentScrollY()
  }
})

watch(shellNavigationVisible, (visible) => {
  if (!visible) {
    blurShellFocusedElement()
    addMenuOpen.value = false
  }
})
</script>

<template>
  <div class="app-shell">
    <router-view />

    <div
      v-if="showMainShell"
      ref="addMenuRef"
      :class="['app-add-menu', { open: addMenuOpen, 'app-shell-control-hidden': !shellNavigationVisible }]"
      :aria-hidden="!shellNavigationVisible"
      :inert="!shellNavigationVisible || undefined"
    >
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

    <van-tabbar
      v-if="showMainShell"
      ref="tabbarRef"
      :class="['app-tabbar', { 'app-shell-control-hidden': !shellNavigationVisible }]"
      :aria-hidden="!shellNavigationVisible"
      :inert="!shellNavigationVisible || undefined"
      route
      fixed
    >
      <van-tabbar-item to="/" icon="apps-o">工作台</van-tabbar-item>
      <van-tabbar-item to="/records" icon="orders-o">流水</van-tabbar-item>
      <van-tabbar-item to="/statistics" icon="bar-chart-o">分析</van-tabbar-item>
      <van-tabbar-item to="/settings" icon="manager-o">我的</van-tabbar-item>
    </van-tabbar>
  </div>
</template>
