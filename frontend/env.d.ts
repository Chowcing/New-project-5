/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_EXPENSE_DEPLOYMENT_VERSION?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
