<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

const navItems = [
  { title: '工作台', icon: 'dashboard-o', to: '/admin' },
  { title: '用户', icon: 'friends-o', to: '/admin/users' },
  { title: '交易', icon: 'orders-o', to: '/admin/transactions' },
  { title: '审计', icon: 'description-o', to: '/admin/audit' }
]
</script>

<template>
  <main class="admin-shell">
    <van-nav-bar class="admin-mobile-nav" title="后台管理" left-arrow @click-left="router.back()" />

    <aside class="admin-sidebar">
      <button type="button" class="admin-back" aria-label="返回" @click="router.back()">
        <van-icon name="arrow-left" />
      </button>
      <div class="admin-brand">
        <van-icon name="manager-o" />
        <div>
          <strong>后台管理</strong>
          <span>管理工作台</span>
        </div>
      </div>
      <nav class="admin-nav">
        <RouterLink v-for="item in navItems" :key="item.to" :to="item.to" class="admin-nav-item">
          <van-icon :name="item.icon" />
          <span>{{ item.title }}</span>
        </RouterLink>
      </nav>
    </aside>

    <section class="admin-main">
      <div class="admin-top-tabs">
        <RouterLink v-for="item in navItems" :key="item.to" :to="item.to" class="admin-top-tab">
          <van-icon :name="item.icon" />
          <span>{{ item.title }}</span>
        </RouterLink>
      </div>
      <RouterView />
    </section>
  </main>
</template>

<style scoped>
.admin-shell {
  min-height: 100vh;
  background: var(--page-bg);
}

.admin-mobile-nav {
  position: sticky;
  top: 0;
  z-index: 8;
}

.admin-sidebar {
  display: none;
}

.admin-main {
  width: min(1180px, 100%);
  margin: 0 auto;
  padding: var(--space-12);
}

.admin-top-tabs {
  position: sticky;
  top: 46px;
  z-index: 7;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-8);
  margin: 0 0 var(--space-12);
  padding: var(--space-8) 0;
  background: rgba(5, 7, 13, 0.82);
  backdrop-filter: blur(14px);
}

.admin-top-tab,
.admin-nav-item {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-6);
  min-height: 42px;
  border: 1px solid var(--border-warm);
  border-radius: var(--radius-inner);
  color: var(--text-secondary);
  text-decoration: none;
  font-size: var(--font-size-meta);
}

.admin-top-tab.router-link-exact-active,
.admin-nav-item.router-link-exact-active {
  color: var(--text-main);
  border-color: rgba(var(--theme-primary-glow-rgb), 0.46);
  background: var(--primary-soft);
}

@media (min-width: 900px) {
  .admin-shell {
    display: grid;
    grid-template-columns: 220px minmax(0, 1fr);
  }

  .admin-mobile-nav,
  .admin-top-tabs {
    display: none;
  }

  .admin-sidebar {
    position: sticky;
    top: 0;
    display: block;
    height: 100vh;
    padding: var(--space-18) var(--space-14);
    border-right: 1px solid var(--border-warm);
    background: rgba(8, 13, 24, 0.78);
  }

  .admin-main {
    width: min(1180px, 100%);
    padding: var(--space-18);
  }

  .admin-back {
    width: 36px;
    height: 36px;
    margin-bottom: var(--space-18);
    border: 1px solid var(--border-warm);
    border-radius: var(--radius-inner);
    color: var(--text-main);
    background: var(--card-bg);
  }

  .admin-brand {
    display: flex;
    align-items: center;
    gap: var(--space-10);
    margin-bottom: var(--space-22);
  }

  .admin-brand > .van-icon {
    display: grid;
    place-items: center;
    width: 42px;
    height: 42px;
    border-radius: var(--radius-inner);
    color: var(--primary);
    background: var(--primary-soft);
  }

  .admin-brand strong,
  .admin-brand span {
    display: block;
  }

  .admin-brand span {
    color: var(--text-secondary);
    font-size: var(--font-size-meta);
  }

  .admin-nav {
    display: grid;
    gap: var(--space-8);
  }

  .admin-nav-item {
    justify-content: flex-start;
    padding: 0 var(--space-12);
  }
}
</style>
