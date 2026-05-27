import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const HomeView = () => import('@/views/HomeView.vue')
const LoginView = () => import('@/views/LoginView.vue')
const RegisterView = () => import('@/views/RegisterView.vue')
const QuickAddView = () => import('@/views/QuickAddView.vue')
const RecordsView = () => import('@/views/RecordsView.vue')
const StatisticsView = () => import('@/views/StatisticsView.vue')
const SettingsView = () => import('@/views/SettingsView.vue')
const CategoriesView = () => import('@/views/CategoriesView.vue')
const PaymentMethodsView = () => import('@/views/PaymentMethodsView.vue')
const OnlinePlatformsView = () => import('@/views/OnlinePlatformsView.vue')
const BudgetsView = () => import('@/views/BudgetsView.vue')
const ExportView = () => import('@/views/ExportView.vue')
const ImportView = () => import('@/views/ImportView.vue')
const TransactionDetailView = () => import('@/views/TransactionDetailView.vue')
const RecurringRulesView = () => import('@/views/RecurringRulesView.vue')
const RecurringRuleFormView = () => import('@/views/RecurringRuleFormView.vue')
const AdminView = () => import('@/views/AdminView.vue')

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: LoginView, meta: { guest: true } },
    { path: '/register', component: RegisterView, meta: { guest: true } },
    { path: '/', component: HomeView, meta: { requiresAuth: true, mainTab: 'workspace' } },
    { path: '/records', component: RecordsView, meta: { requiresAuth: true, mainTab: 'records' } },
    { path: '/records/:id', component: TransactionDetailView, meta: { requiresAuth: true } },
    { path: '/quick-add', component: QuickAddView, meta: { requiresAuth: true } },
    { path: '/statistics', component: StatisticsView, meta: { requiresAuth: true, mainTab: 'analysis' } },
    { path: '/settings', component: SettingsView, meta: { requiresAuth: true, mainTab: 'profile' } },
    { path: '/categories', component: CategoriesView, meta: { requiresAuth: true } },
    { path: '/payment-methods', component: PaymentMethodsView, meta: { requiresAuth: true } },
    { path: '/online-platforms', component: OnlinePlatformsView, meta: { requiresAuth: true } },
    { path: '/budgets', component: BudgetsView, meta: { requiresAuth: true } },
    { path: '/export', component: ExportView, meta: { requiresAuth: true } },
    { path: '/import', component: ImportView, meta: { requiresAuth: true } },
    { path: '/recurring-rules', component: RecurringRulesView, meta: { requiresAuth: true } },
    { path: '/recurring-rules/new', component: RecurringRuleFormView, meta: { requiresAuth: true } },
    { path: '/recurring-rules/:id/edit', component: RecurringRuleFormView, meta: { requiresAuth: true } },
    { path: '/admin', component: AdminView, meta: { requiresAuth: true, requiresAdmin: true } },
    { path: '/:pathMatch(.*)*', redirect: '/' }
  ]
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (to.meta.requiresAuth && !auth.isAuthenticated) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.meta.requiresAuth && auth.isAuthenticated && !auth.user) {
    await auth.fetchMe().catch(() => undefined)
  }
  if (to.meta.requiresAdmin && !auth.user?.admin) {
    return '/'
  }
  if (to.meta.guest && auth.isAuthenticated) {
    return '/'
  }
  return true
})

export default router
