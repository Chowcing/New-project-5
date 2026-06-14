import { transactionTitle } from '@/utils/display'

export function cleanParams<T extends object>(params: T) {
  return Object.fromEntries(
    Object.entries(params).filter(([, value]) => value !== '' && value !== undefined && value !== null)
  ) as T
}

export function formatMoney(value: number | string | undefined | null) {
  return `¥${Number(value || 0).toFixed(2)}`
}

export function statusText(status: string | undefined) {
  return status === 'DISABLED' ? '已禁用' : '正常'
}

export function typeText(type: string | undefined) {
  return type === 'INCOME' ? '收入' : '支出'
}

export function channelText(channel: string | undefined) {
  return channel === 'OFFLINE' ? '线下' : '线上'
}

export function displayDateTime(value: string | undefined) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 16)
}

export function actionText(action: string) {
  const map: Record<string, string> = {
    USER_STATUS_ACTIVE: '启用用户',
    USER_STATUS_DISABLED: '禁用用户',
    REVOKE_TOKENS: '吊销凭证',
    RESET_USER_EMAIL: '重置邮箱',
    TRANSACTION_DELETE: '删除交易',
    TRANSACTION_CREATE: '新增交易',
    TRANSACTION_UPDATE: '更新交易',
    TRANSACTION_IMAGE_DELETE: '删除凭证',
    CATEGORY_CREATE: '新增分类',
    CATEGORY_UPDATE: '更新分类',
    CATEGORY_DELETE: '删除分类',
    PAYMENT_METHOD_CREATE: '新增支付方式',
    PAYMENT_METHOD_UPDATE: '更新支付方式',
    PAYMENT_METHOD_DELETE: '删除支付方式',
    ONLINE_PLATFORM_CREATE: '新增线上平台',
    ONLINE_PLATFORM_UPDATE: '更新线上平台',
    ONLINE_PLATFORM_DELETE: '删除线上平台',
    BUDGET_CREATE: '新增预算',
    BUDGET_UPDATE: '更新预算',
    BUDGET_DELETE: '删除预算',
    RECURRING_RULE_CREATE: '新增周期规则',
    RECURRING_RULE_UPDATE: '更新周期规则',
    RECURRING_RULE_DELETE: '删除周期规则',
    RECURRING_RUN_GENERATE: '生成周期流水',
    IMPORT_JOB_CREATE: '创建导入任务',
    IMPORT_JOB_SUCCESS: '导入完成',
    OCR_IMAGE_RECOGNIZE: '图片识别'
  }
  return map[action] || action
}

export function sourceText(source: string) {
  const map: Record<string, string> = {
    USER: '用户',
    IMPORT: '导入',
    OCR: 'OCR',
    RECURRING: '周期'
  }
  return map[source] || source
}

export { transactionTitle }
