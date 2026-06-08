<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { recurringRuleApi, recurringRunApi } from '@/api/services'
import PageSkeleton from '@/components/PageSkeleton.vue'
import type { RecurringRule, RecurringRuleRun } from '@/types'
import { recurringEntryTitle } from '@/utils/display'
import { dueStatusText, recurringRulePayload, ruleStatusLabel, runStatusLabel, scheduleSummary } from '@/utils/recurring'
import { money, todayDate } from '@/utils/date'
import { showError } from '@/utils/errors'

const router = useRouter()
const rules = ref<RecurringRule[]>([])
const dueRuns = ref<RecurringRuleRun[]>([])
const loading = ref(true)
const ruleActionId = ref<number | null>(null)
const ruleActionType = ref<'toggle' | 'delete' | ''>('')
const runActionId = ref<number | null>(null)
const runActionType = ref<'generate' | 'skip' | ''>('')

function formatDate(value: string) {
  const [year, month, day] = value.split('-')
  return `${year} 年 ${month} 月 ${day} 日`
}

async function load() {
  loading.value = true
  try {
    const [nextRules, nextDueRuns] = await Promise.all([
      recurringRuleApi.list(),
      recurringRunApi.due(todayDate())
    ])
    rules.value = nextRules
    dueRuns.value = nextDueRuns
  } catch (error) {
    showError(error, '周期记账数据加载失败')
  } finally {
    loading.value = false
  }
}

async function openCreate() {
  await router.push('/recurring-rules/new')
}

async function openEdit(id: number) {
  await router.push(`/recurring-rules/${id}/edit`)
}

async function toggleStatus(rule: RecurringRule) {
  if (ruleActionId.value === rule.id) {
    return
  }
  ruleActionId.value = rule.id
  ruleActionType.value = 'toggle'
  try {
    const nextStatus = rule.status === 'ACTIVE' ? 'PAUSED' : 'ACTIVE'
    await recurringRuleApi.update(rule.id, recurringRulePayload(rule, nextStatus))
    showToast(rule.status === 'ACTIVE' ? '规则已暂停' : '规则已启用')
    await load()
  } catch (error) {
    showError(error, '状态更新失败')
  } finally {
    ruleActionId.value = null
    ruleActionType.value = ''
  }
}

async function removeRule(rule: RecurringRule) {
  if (ruleActionId.value === rule.id) {
    return
  }
  try {
    await showConfirmDialog({ title: '删除周期规则', message: `确认删除「${rule.name}」？` })
  } catch {
    return
  }
  ruleActionId.value = rule.id
  ruleActionType.value = 'delete'
  try {
    await recurringRuleApi.remove(rule.id)
    showToast('规则已删除')
    await load()
  } catch (error) {
    showError(error, '删除失败')
  } finally {
    ruleActionId.value = null
    ruleActionType.value = ''
  }
}

async function generateRun(run: RecurringRuleRun) {
  if (runActionId.value === run.id) {
    return
  }
  runActionId.value = run.id
  runActionType.value = 'generate'
  try {
    await recurringRunApi.generate(run.id)
    showToast('本次记录已生成')
    await load()
  } catch (error) {
    showError(error, '生成失败')
  } finally {
    runActionId.value = null
    runActionType.value = ''
  }
}

async function skipRun(run: RecurringRuleRun) {
  if (runActionId.value === run.id) {
    return
  }
  try {
    await showConfirmDialog({ title: '跳过本次', message: `确认跳过「${run.ruleName}」这次周期记录？` })
  } catch {
    return
  }
  runActionId.value = run.id
  runActionType.value = 'skip'
  try {
    await recurringRunApi.skip(run.id)
    showToast('本次记录已跳过')
    await load()
  } catch (error) {
    showError(error, '跳过失败')
  } finally {
    runActionId.value = null
    runActionType.value = ''
  }
}

onMounted(load)
</script>

<template>
  <main class="page recurring-page">
    <van-nav-bar title="周期记账" left-arrow @click-left="router.back()" />
    <div class="page-content">
      <section class="section">
        <van-button block round type="primary" icon="plus" @click="openCreate">新建周期规则</van-button>
      </section>

      <section class="section panel">
        <div class="recurring-section-header">
          <span>待处理实例</span>
        </div>
        <PageSkeleton v-if="loading" variant="list" :cards="2" :rows="2" />
        <div v-else-if="dueRuns.length === 0" class="empty-text">暂无待处理周期记录</div>
        <div v-else class="recurring-run-list">
          <article v-for="run in dueRuns" :key="run.id" class="recurring-run-card">
            <div class="recurring-card-top">
              <div>
                <div class="recurring-card-title">{{ run.ruleName }}</div>
                <div class="recurring-card-subtitle">{{ recurringEntryTitle(run) }}</div>
              </div>
              <span class="recurring-pill">{{ runStatusLabel(run.status) }}</span>
            </div>
            <div class="recurring-meta">
              <span>{{ run.type === 'EXPENSE' ? '支出' : '收入' }}</span>
              <span>{{ run.channel === 'ONLINE' ? '线上' : '线下' }}</span>
              <span>{{ formatDate(run.dueDate) }}</span>
            </div>
            <div class="recurring-amount" :class="run.type === 'EXPENSE' ? 'expense' : 'income'">
              {{ run.type === 'EXPENSE' ? '-' : '+' }}¥{{ money(run.amount) }}
            </div>
            <div class="recurring-hint">
              {{ dueStatusText(run, todayDate()) }}
              <span v-if="run.reminderDaysBefore > 0"> · 提前 {{ run.reminderDaysBefore }} 天提醒</span>
            </div>
            <div class="recurring-actions">
              <van-button
                size="small"
                type="primary"
                icon="success"
                :loading="runActionId === run.id && runActionType === 'generate'"
                @click="generateRun(run)"
              >
                生成
              </van-button>
              <van-button
                size="small"
                plain
                type="default"
                icon="cross"
                :loading="runActionId === run.id && runActionType === 'skip'"
                @click="skipRun(run)"
              >
                跳过
              </van-button>
            </div>
          </article>
        </div>
      </section>

      <section class="section panel">
        <div class="recurring-section-header">
          <span>规则列表</span>
        </div>
        <PageSkeleton v-if="loading" variant="list" :cards="2" :rows="3" />
        <div v-else-if="rules.length === 0" class="empty-text">暂无周期规则</div>
        <div v-else class="recurring-rule-list">
          <article v-for="rule in rules" :key="rule.id" class="recurring-rule-card">
            <div class="recurring-card-top">
              <div>
                <div class="recurring-card-title">{{ rule.name }}</div>
                <div class="recurring-card-subtitle">{{ scheduleSummary(rule) }}</div>
              </div>
              <span :class="['recurring-pill', rule.status === 'ACTIVE' ? 'recurring-pill-active' : 'recurring-pill-paused']">
                {{ ruleStatusLabel(rule.status) }}
              </span>
            </div>
            <div class="recurring-amount" :class="rule.type === 'EXPENSE' ? 'expense' : 'income'">
              {{ rule.type === 'EXPENSE' ? '-' : '+' }}¥{{ money(rule.amount) }}
            </div>
            <div class="recurring-meta">
              <span>{{ rule.type === 'EXPENSE' ? '支出' : '收入' }}</span>
              <span>{{ rule.channel === 'ONLINE' ? '线上' : '线下' }}</span>
              <span>{{ rule.paymentMethodName }}</span>
              <span>{{ rule.categoryName }}</span>
            </div>
            <div class="recurring-next">
              <span>下一次</span>
              <span>{{ rule.nextRunDate ? formatDate(rule.nextRunDate) : '已结束' }}</span>
            </div>
            <div v-if="rule.note" class="recurring-note">{{ rule.note }}</div>
            <div class="recurring-actions">
              <van-button size="small" plain type="primary" icon="edit" @click="openEdit(rule.id)">编辑</van-button>
              <van-button
                size="small"
                plain
                :type="rule.status === 'ACTIVE' ? 'default' : 'primary'"
                :icon="rule.status === 'ACTIVE' ? 'pause-circle-o' : 'play-circle-o'"
                :loading="ruleActionId === rule.id && ruleActionType === 'toggle'"
                @click="toggleStatus(rule)"
              >
                {{ rule.status === 'ACTIVE' ? '暂停' : '启用' }}
              </van-button>
              <van-button
                size="small"
                plain
                type="danger"
                icon="delete-o"
                :loading="ruleActionId === rule.id && ruleActionType === 'delete'"
                @click="removeRule(rule)"
              >
                删除
              </van-button>
            </div>
          </article>
        </div>
      </section>
    </div>
  </main>
</template>

<style scoped>
.recurring-page {
  padding-bottom: var(--space-28);
}

.recurring-section-header {
  display: flex;
  justify-content: space-between;
  gap: var(--space-12);
  align-items: center;
  margin-bottom: var(--space-12);
  color: var(--text-main);
  font-size: var(--font-size-section-title);
  font-weight: 700;
}

.recurring-run-list,
.recurring-rule-list {
  display: grid;
  gap: var(--space-12);
}

.recurring-run-card,
.recurring-rule-card {
  padding: var(--space-14);
  border: 1px solid var(--border-warm);
  border-radius: var(--radius-sheet);
  background: var(--card-bg);
}

.recurring-card-top {
  display: flex;
  justify-content: space-between;
  gap: var(--space-12);
  align-items: flex-start;
}

.recurring-card-title {
  color: var(--text-main);
  font-size: var(--font-size-section-title);
  font-weight: 700;
  line-height: var(--line-height-section-title);
}

.recurring-card-subtitle {
  margin-top: var(--space-2);
  color: var(--text-secondary);
  font-size: var(--font-size-meta);
  line-height: var(--line-height-meta);
}

.recurring-pill {
  display: inline-flex;
  align-items: center;
  min-height: 26px;
  padding: var(--space-0) var(--space-10);
  border-radius: var(--radius-pill);
  background: var(--card-bg-warm);
  color: var(--text-secondary);
  font-size: var(--font-size-caption);
  font-weight: 600;
  white-space: nowrap;
}

.recurring-pill-active {
  background: var(--income-soft);
  color: var(--income);
}

.recurring-pill-paused {
  background: #f7eadb;
  color: var(--text-secondary);
}

.recurring-meta,
.recurring-next,
.recurring-hint {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-10);
  color: var(--text-secondary);
  font-size: var(--font-size-caption);
  line-height: var(--line-height-caption);
}

.recurring-meta {
  margin-top: var(--space-10);
}

.recurring-amount {
  margin-top: var(--space-8);
  font-size: var(--font-size-amount);
  font-weight: 700;
  line-height: var(--line-height-amount);
}

.recurring-next {
  justify-content: space-between;
  margin-top: var(--space-10);
  padding-top: var(--space-10);
  border-top: 1px dashed var(--border-warm);
}

.recurring-note {
  margin-top: var(--space-8);
  color: var(--text-secondary);
  font-size: var(--font-size-meta);
  line-height: var(--line-height-meta);
}

.recurring-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-8);
  margin-top: var(--space-12);
}

.expense {
  color: var(--expense);
}

.income {
  color: var(--income);
}
</style>
