<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { adminApi, type AdminAuditLogQuery, type BusinessAuditLogQuery } from '@/api/services'
import PageSkeleton from '@/components/PageSkeleton.vue'
import type { AdminAuditLog, BusinessAuditLog, PageResponse } from '@/types'
import { showError } from '@/utils/errors'
import { actionText, cleanParams, displayDateTime, sourceText } from './adminUtils'

const route = useRoute()
const auditMode = ref<'admin' | 'business'>('admin')
const auditLogsLoading = ref(false)
const businessAuditLogsLoading = ref(false)
const auditLogsPage = ref<PageResponse<AdminAuditLog> | null>(null)
const businessAuditLogsPage = ref<PageResponse<BusinessAuditLog> | null>(null)

const auditQuery = reactive<AdminAuditLogQuery>({
  adminUserId: undefined,
  action: '',
  targetType: '',
  targetId: undefined,
  startDate: '',
  endDate: '',
  page: 1,
  size: 20
})

const businessAuditQuery = reactive<BusinessAuditLogQuery>({
  userId: undefined,
  action: '',
  targetType: '',
  source: '',
  page: 1,
  size: 20
})

onMounted(async () => {
  if (typeof route.query.targetType === 'string') {
    auditQuery.targetType = route.query.targetType
  }
  const targetId = Number(route.query.targetId)
  if (Number.isFinite(targetId) && targetId > 0) {
    auditQuery.targetId = targetId
  }
  await Promise.all([loadAuditLogs(true), loadBusinessAuditLogs(true)])
})

async function loadAuditLogs(resetPage = false) {
  if (resetPage) auditQuery.page = 1
  auditLogsLoading.value = true
  try {
    auditLogsPage.value = await adminApi.auditLogs(cleanParams(auditQuery))
  } catch (error) {
    showError(error, '审计日志加载失败')
  } finally {
    auditLogsLoading.value = false
  }
}

async function loadBusinessAuditLogs(resetPage = false) {
  if (resetPage) businessAuditQuery.page = 1
  businessAuditLogsLoading.value = true
  try {
    businessAuditLogsPage.value = await adminApi.businessAuditLogs(cleanParams(businessAuditQuery))
  } catch (error) {
    showError(error, '业务审计日志加载失败')
  } finally {
    businessAuditLogsLoading.value = false
  }
}
</script>

<template>
  <div class="admin-audit-page">
    <section class="admin-panel">
      <div class="page-head">
        <div>
          <span>审计追踪</span>
          <h1>管理操作与业务操作记录</h1>
        </div>
      </div>

      <van-tabs v-model:active="auditMode" type="card" class="audit-mode-tabs">
        <van-tab title="管理操作" name="admin" />
        <van-tab title="业务操作" name="business" />
      </van-tabs>

      <template v-if="auditMode === 'admin'">
        <div class="admin-filters admin-audit-filters">
          <van-field v-model.number="auditQuery.adminUserId" clearable type="number" placeholder="管理员 ID" />
          <van-field v-model="auditQuery.action" clearable placeholder="动作编码" />
          <van-field v-model="auditQuery.targetType" clearable placeholder="目标类型" />
          <van-field v-model.number="auditQuery.targetId" clearable type="number" placeholder="目标 ID" />
          <van-field v-model="auditQuery.startDate" type="date" placeholder="开始日期" />
          <van-field v-model="auditQuery.endDate" type="date" placeholder="结束日期" />
          <van-button type="primary" icon="search" @click="loadAuditLogs(true)">搜索</van-button>
        </div>

        <div class="admin-list">
          <PageSkeleton v-if="auditLogsLoading && !auditLogsPage" variant="list" :cards="3" :rows="2" />
          <template v-else>
            <article v-for="log in auditLogsPage?.records" :key="log.id" class="admin-card">
              <div>
                <h3>{{ actionText(log.action) }} <small>#{{ log.targetId }}</small></h3>
                <p>{{ displayDateTime(log.createdAt) }} · {{ log.adminUsername || log.adminUserId }} · {{ log.targetType }}</p>
                <p v-if="log.reason">{{ log.reason }}</p>
              </div>
            </article>
          </template>
          <van-empty v-if="auditLogsPage && !auditLogsPage.records.length" description="暂无审计日志" />
        </div>

        <van-pagination
          v-if="auditLogsPage && auditLogsPage.totalPages > 1"
          v-model="auditQuery.page"
          :total-items="auditLogsPage.total"
          :items-per-page="auditQuery.size"
          @change="loadAuditLogs()"
        />
      </template>

      <template v-else>
        <div class="admin-filters business-audit-filters">
          <van-field v-model.number="businessAuditQuery.userId" clearable type="number" placeholder="用户 ID" />
          <van-field v-model="businessAuditQuery.action" clearable placeholder="动作编码" />
          <van-field v-model="businessAuditQuery.targetType" clearable placeholder="目标类型" />
          <van-field v-model="businessAuditQuery.source" clearable placeholder="来源" />
          <van-button type="primary" icon="search" @click="loadBusinessAuditLogs(true)">搜索</van-button>
        </div>

        <div class="admin-list">
          <PageSkeleton v-if="businessAuditLogsLoading && !businessAuditLogsPage" variant="list" :cards="3" :rows="2" />
          <template v-else>
            <article v-for="log in businessAuditLogsPage?.records" :key="log.id" class="admin-card">
              <div>
                <h3>{{ actionText(log.action) }} <small>{{ log.targetType }}{{ log.targetId ? ` #${log.targetId}` : '' }}</small></h3>
                <p>{{ displayDateTime(log.createdAt) }} · 用户 #{{ log.userId }} · {{ sourceText(log.source) }} · {{ log.status }}</p>
                <p v-if="log.requestId">requestId {{ log.requestId }}</p>
              </div>
            </article>
          </template>
          <van-empty v-if="businessAuditLogsPage && !businessAuditLogsPage.records.length" description="暂无业务审计日志" />
        </div>

        <van-pagination
          v-if="businessAuditLogsPage && businessAuditLogsPage.totalPages > 1"
          v-model="businessAuditQuery.page"
          :total-items="businessAuditLogsPage.total"
          :items-per-page="businessAuditQuery.size"
          @change="loadBusinessAuditLogs()"
        />
      </template>
    </section>
  </div>
</template>

<style scoped>
.admin-audit-page,
.admin-list {
  display: grid;
  gap: var(--space-12);
  min-width: 0;
}

.admin-panel,
.admin-card {
  border: 1px solid var(--border-warm);
  border-radius: var(--radius-card);
  background: var(--card-bg);
}

.admin-panel {
  min-width: 0;
  padding: var(--space-12);
}

.page-head {
  margin-bottom: var(--space-12);
}

.page-head > div {
  min-width: 0;
}

.page-head span,
.admin-card p,
.admin-card small {
  color: var(--text-secondary);
}

.page-head h1,
.admin-card h3 {
  margin: 0;
}

.page-head h1 {
  margin-top: var(--space-4);
  font-size: var(--font-size-page-title);
  line-height: var(--line-height-page-title);
}

.audit-mode-tabs {
  margin-bottom: var(--space-12);
}

.admin-filters {
  display: grid;
  gap: var(--space-8);
  margin-bottom: var(--space-12);
}

.admin-audit-filters {
  grid-template-columns: 120px minmax(150px, 1fr) 130px 120px 150px 150px 44px;
}

.business-audit-filters {
  grid-template-columns: 120px minmax(160px, 1fr) 150px 120px 44px;
}

.admin-card {
  min-width: 0;
  padding: var(--space-12);
}

.admin-card h3 {
  margin-bottom: var(--space-6);
  font-size: var(--font-size-body-strong);
}

.admin-card p {
  margin: 0;
  font-size: var(--font-size-meta);
  overflow-wrap: anywhere;
}

.admin-card p + p {
  margin-top: var(--space-6);
}

@media (max-width: 760px) {
  .admin-audit-filters,
  .business-audit-filters {
    grid-template-columns: 1fr;
  }

  .page-head h1 {
    font-size: var(--font-size-panel-title);
    line-height: var(--line-height-panel-title);
    overflow-wrap: anywhere;
  }
}
</style>
