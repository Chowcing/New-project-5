/// <reference types="vite/client" />

declare module '*.css'

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string
  readonly VITE_AMAP_KEY?: string
  readonly VITE_AMAP_SECURITY_JS_CODE?: string
  readonly VITE_AMAP_CITY?: string
}
