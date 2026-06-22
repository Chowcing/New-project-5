import type { Router } from 'vue-router'

type RouterBackState = {
  back?: unknown
}

function browserHistoryState() {
  return typeof window === 'undefined' ? null : window.history.state
}

export function hasAppHistoryBack(state: unknown = browserHistoryState()) {
  const back = (state && typeof state === 'object' ? (state as RouterBackState).back : null)
  return typeof back === 'string' && back.length > 0
}

export function navigateBackOrHome(router: Pick<Router, 'back' | 'replace'>) {
  if (hasAppHistoryBack()) {
    router.back()
    return
  }
  void router.replace('/')
}
