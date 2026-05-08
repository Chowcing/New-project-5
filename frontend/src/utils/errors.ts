import { showFailToast } from 'vant'

export function showError(error: unknown, fallback = '操作失败') {
  const message = error instanceof Error && error.message ? error.message : fallback
  showFailToast(message)
}
