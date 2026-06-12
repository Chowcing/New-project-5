import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { clearTokens, loadTokens, saveTokens } from './tokenStorage'
import type { ApiResponse, TokenResponse } from '@/types'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api/v1'
const REQUEST_ID_HEADER = 'X-Request-Id'

interface RetryConfig extends InternalAxiosRequestConfig {
  _retry?: boolean
}

export const http = axios.create({
  baseURL: API_BASE_URL,
  timeout: 12000
})

let refreshPromise: Promise<string | null> | null = null
export const AUTH_EXPIRED_EVENT = 'expense-auth-expired'

export class RequestError extends Error {
  status?: number
  data?: unknown
  requestId?: string
  method?: string
  url?: string

  constructor(message: string, status?: number, data?: unknown, requestId?: string, method?: string, url?: string) {
    super(message)
    this.name = 'RequestError'
    this.status = status
    this.data = data
    this.requestId = requestId
    this.method = method
    this.url = url
  }
}

http.interceptors.request.use((config) => {
  config.headers.set(REQUEST_ID_HEADER, getRequestId(config))
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
      return Promise.reject(new RequestError(
        body.message || '请求失败',
        response.status,
        body.data,
        readResponseRequestId(response.headers) || getRequestId(response.config),
        response.config.method?.toUpperCase(),
        response.config.url
      ))
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
      expireAuthSession()
      return Promise.reject(new RequestError(
        '登录已过期，请重新登录',
        error.response.status,
        error.response.data?.data,
        readRequestId(error),
        original.method?.toUpperCase(),
        original.url
      ))
    }

    // 多个请求同时 401 时只发起一次刷新，其他请求等待同一个 Promise 后重放。
    refreshPromise ??= axios.post<ApiResponse<TokenResponse>>(`${API_BASE_URL}/auth/refresh`, {
      refreshToken: tokens.refreshToken
    }, {
      headers: {
        [REQUEST_ID_HEADER]: createRequestId()
      }
    }).then((response) => {
      const nextTokens = response.data.data
      saveTokens(nextTokens)
      return nextTokens.accessToken
    }).catch(() => {
      expireAuthSession()
      return null
    }).finally(() => {
      refreshPromise = null
    })

    const nextAccessToken = await refreshPromise
    if (!nextAccessToken) {
      return Promise.reject(new RequestError(
        '登录已过期，请重新登录',
        error.response.status,
        error.response.data?.data,
        readRequestId(error),
        original.method?.toUpperCase(),
        original.url
      ))
    }
    original.headers.Authorization = `Bearer ${nextAccessToken}`
    return http(original)
  }
)

function toRequestError(error: AxiosError<ApiResponse<unknown>>) {
  const message = error.response?.data?.message || error.message || '请求失败'
  const requestId = readRequestId(error)
  const method = error.config?.method?.toUpperCase()
  const url = error.config?.url
  return new RequestError(message, error.response?.status, error.response?.data?.data, requestId, method, url)
}

function expireAuthSession() {
  clearTokens()
  if (typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent(AUTH_EXPIRED_EVENT))
  }
}

function getRequestId(config: InternalAxiosRequestConfig) {
  const current = config.headers.get(REQUEST_ID_HEADER)
  return typeof current === 'string' && current.trim() ? current.trim() : createRequestId()
}

function readRequestId(error: AxiosError<ApiResponse<unknown>>) {
  const responseRequestId = readResponseRequestId(error.response?.headers)
  if (responseRequestId) return responseRequestId
  const requestRequestId = error.config?.headers?.get?.(REQUEST_ID_HEADER)
  return typeof requestRequestId === 'string' && requestRequestId.trim() ? requestRequestId.trim() : undefined
}

function readResponseRequestId(headers: unknown) {
  if (!headers || typeof headers !== 'object') return undefined
  const value = (headers as Record<string, unknown>)['x-request-id']
  return typeof value === 'string' && value.trim() ? value.trim() : undefined
}

function createRequestId() {
  const cryptoApi = typeof window !== 'undefined' ? window.crypto : undefined
  if (cryptoApi?.randomUUID) {
    return cryptoApi.randomUUID()
  }
  return `web-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`
}
