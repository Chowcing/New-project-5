import type { TokenResponse } from '@/types'

const STORAGE_KEY = 'expense.auth.tokens'

export function loadTokens(): TokenResponse | null {
  const raw = localStorage.getItem(STORAGE_KEY)
  return raw ? JSON.parse(raw) as TokenResponse : null
}

export function saveTokens(tokens: TokenResponse) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(tokens))
}

export function clearTokens() {
  localStorage.removeItem(STORAGE_KEY)
}

