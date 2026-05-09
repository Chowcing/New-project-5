import type { TokenResponse } from '@/types'

const STORAGE_KEY = 'expense.auth.tokens'

export function loadTokens(): TokenResponse | null {
  const raw = localStorage.getItem(STORAGE_KEY)
  if (!raw) {
    return null
  }

  try {
    const tokens = JSON.parse(raw) as Partial<TokenResponse>
    if (typeof tokens.accessToken === 'string' && typeof tokens.refreshToken === 'string') {
      return tokens as TokenResponse
    }
  } catch {
    // Ignore corrupted local storage and force a fresh login.
  }
  clearTokens()
  return null
}

export function saveTokens(tokens: TokenResponse) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(tokens))
}

export function clearTokens() {
  localStorage.removeItem(STORAGE_KEY)
}
