import { defineStore } from 'pinia'
import { authApi } from '@/api/services'
import { clearTokens, loadTokens, saveTokens } from '@/api/tokenStorage'
import type { TokenResponse, UserProfile } from '@/types'

interface AuthState {
  tokens: TokenResponse | null
  user: UserProfile | null
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    tokens: loadTokens(),
    user: null
  }),
  getters: {
    isAuthenticated: (state) => Boolean(state.tokens?.accessToken)
  },
  actions: {
    setTokens(tokens: TokenResponse) {
      this.tokens = tokens
      saveTokens(tokens)
    },
    clearSession() {
      this.tokens = null
      this.user = null
      clearTokens()
    },
    async startLogin(username: string, password: string) {
      return authApi.login({ username, password })
    },
    async verifyLogin(challengeId: string, code: string) {
      const tokens = await authApi.verifyLogin({ challengeId, code })
      this.setTokens(tokens)
      await this.fetchMe()
    },
    async sendBindEmailCode(challengeId: string, email: string) {
      return authApi.sendBindEmailCode({ challengeId, email })
    },
    async verifyBindEmail(challengeId: string, code: string) {
      const tokens = await authApi.verifyBindEmail({ challengeId, code })
      this.setTokens(tokens)
      await this.fetchMe()
    },
    async sendRegisterEmailCode(email: string) {
      return authApi.sendRegisterEmailCode(email)
    },
    async register(username: string, password: string, nickname: string, email: string, emailCode: string) {
      const tokens = await authApi.register({ username, password, nickname, email, emailCode })
      this.setTokens(tokens)
      await this.fetchMe()
    },
    async fetchMe() {
      if (!this.tokens?.accessToken) return
      try {
        this.user = await authApi.me()
      } catch (error) {
        this.clearSession()
        throw error
      }
    },
    async logout() {
      const refreshToken = loadTokens()?.refreshToken || this.tokens?.refreshToken
      if (refreshToken) {
        await authApi.logout(refreshToken).catch(() => undefined)
      }
      this.clearSession()
    }
  }
})
