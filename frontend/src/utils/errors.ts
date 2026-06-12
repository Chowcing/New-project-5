import { showFailToast } from 'vant'
import { RequestError } from '@/api/http'

export function showError(error: unknown, fallback = '操作失败') {
  const message = error instanceof Error && error.message ? error.message : fallback
  const requestId = error instanceof RequestError ? error.requestId : undefined
  showFailToast(requestId ? `${message}（请求ID: ${shortRequestId(requestId)}）` : message)
  if (error instanceof RequestError) {
    console.error('请求失败', {
      message,
      status: error.status,
      requestId: error.requestId,
      method: error.method,
      url: error.url
    })
  }
}

function shortRequestId(requestId: string) {
  return requestId.length > 12 ? requestId.slice(0, 12) : requestId
}
