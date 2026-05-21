<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const showTabbar = computed(() => route.meta.tabbar)
</script>

<template>
  <router-view />
  <van-tabbar v-if="showTabbar" route fixed safe-area-inset-bottom>
    <van-tabbar-item to="/" icon="home-o">首页</van-tabbar-item>
    <van-tabbar-item to="/records" icon="orders-o">明细</van-tabbar-item>
    <van-tabbar-item to="/quick-add" icon="plus">记一笔</van-tabbar-item>
    <van-tabbar-item to="/statistics" icon="bar-chart-o">统计</van-tabbar-item>
    <van-tabbar-item to="/settings" icon="setting-o">设置</van-tabbar-item>
  </van-tabbar>
</template>
