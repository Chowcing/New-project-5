import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { clearTokens, loadTokens, saveTokens } from './tokenStorage'
import type { ApiResponse, TokenResponse } from '@/types'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api/v1'

interface RetryConfig extends InternalAxiosRequestConfig {
  _retry?: boolean
}

export const http = axios.create({
  baseURL: API_BASE_URL,
  timeout: 12000
})

let refreshPromise: Promise<string | null> | null = null

http.interceptors.request.use((config) => {
  const tokens = loadTokens()
  if (tokens?.accessToken) {
    config.headers.Authorization = `Bearer ${tokens.accessToken}`
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    if (response.config.responseType === 'blob') {
      return response.data
    }
    const body = response.data as ApiResponse<unknown>
    if (body && body.success === false) {
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return body?.data ?? body
  },
  async (error: AxiosError<ApiResponse<unknown>>) => {
    const original = error.config as RetryConfig | undefined
    if (!original || error.response?.status !== 401 || original._retry || original.url?.includes('/auth/refresh')) {
      return Promise.reject(toRequestError(error))
    }

    original._retry = true
    const tokens = loadTokens()
    if (!tokens?.refreshToken) {
      clearTokens()
      return Promise.reject(new Error('登录已过期，请重新登录'))
    }

    // 多个请求同时 401 时只发起一次刷新，其他请求等待同一个 Promise 后重放。
    refreshPromise ??= axios.post<ApiResponse<TokenResponse>>(`${API_BASE_URL}/auth/refresh`, {
      refreshToken: tokens.refreshToken
    }).then((response) => {
      const nextTokens = response.data.data
      saveTokens(nextTokens)
      return nextTokens.accessToken
    }).catch(() => {
      clearTokens()
      return null
    }).finally(() => {
      refreshPromise = null
    })

    const nextAccessToken = await refreshPromise
    if (!nextAccessToken) {
      return Promise.reject(new Error('登录已过期，请重新登录'))
    }
    original.headers.Authorization = `Bearer ${nextAccessToken}`
    return http(original)
  }
)

function toRequestError(error: AxiosError<ApiResponse<unknown>>) {
  const message = error.response?.data?.message || error.message || '请求失败'
  return new Error(message)
}
