import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import HomeView from '@/views/HomeView.vue'
import LoginView from '@/views/LoginView.vue'
import RegisterView from '@/views/RegisterView.vue'
import QuickAddView from '@/views/QuickAddView.vue'
import RecordsView from '@/views/RecordsView.vue'
import StatisticsView from '@/views/StatisticsView.vue'
import SettingsView from '@/views/SettingsView.vue'
import CategoriesView from '@/views/CategoriesView.vue'
import PaymentMethodsView from '@/views/PaymentMethodsView.vue'
import BudgetsView from '@/views/BudgetsView.vue'
import ExportView from '@/views/ExportView.vue'
import TransactionDetailView from '@/views/TransactionDetailView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: LoginView, meta: { guest: true } },
    { path: '/register', component: RegisterView, meta: { guest: true } },
    { path: '/', component: HomeView, meta: { requiresAuth: true, tabbar: true } },
    { path: '/records', component: RecordsView, meta: { requiresAuth: true, tabbar: true } },
    { path: '/records/:id', component: TransactionDetailView, meta: { requiresAuth: true } },
    { path: '/quick-add', component: QuickAddView, meta: { requiresAuth: true, tabbar: true } },
    { path: '/statistics', component: StatisticsView, meta: { requiresAuth: true, tabbar: true } },
    { path: '/settings', component: SettingsView, meta: { requiresAuth: true, tabbar: true } },
    { path: '/categories', component: CategoriesView, meta: { requiresAuth: true } },
    { path: '/payment-methods', component: PaymentMethodsView, meta: { requiresAuth: true } },
    { path: '/budgets', component: BudgetsView, meta: { requiresAuth: true } },
    { path: '/export', component: ExportView, meta: { requiresAuth: true } }
  ]
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.requiresAuth && !auth.isAuthenticated) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.meta.guest && auth.isAuthenticated) {
    return '/'
  }
  return true
})

export default router
