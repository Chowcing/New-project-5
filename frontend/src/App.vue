<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const showMainShell = computed(() => Boolean(route.meta.mainTab))
</script>

<template>
  <div class="app-shell">
    <router-view />

    <RouterLink
      v-if="showMainShell"
      class="app-main-fab"
      to="/quick-add"
      aria-label="快速记一笔"
      title="快速记一笔"
    >
      <van-icon name="plus" />
    </RouterLink>

    <van-tabbar v-if="showMainShell" class="app-tabbar" route fixed>
      <van-tabbar-item to="/" icon="apps-o">工作台</van-tabbar-item>
      <van-tabbar-item to="/records" icon="orders-o">流水</van-tabbar-item>
      <van-tabbar-item to="/statistics" icon="bar-chart-o">分析</van-tabbar-item>
      <van-tabbar-item to="/settings" icon="manager-o">我的</van-tabbar-item>
    </van-tabbar>
  </div>
</template>
