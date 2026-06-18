export type ThemeAppearance = 'system' | 'light' | 'dark'
export type ThemeAccent = 'cyan' | 'blue' | 'violet'

export interface ThemePreference {
  appearance: ThemeAppearance
  accent: ThemeAccent
}

export interface ThemeTokens {
  appearance: 'light' | 'dark'
  pageBg: string
  pageBgSoft: string
  cardBg: string
  cardBgWarm: string
  glassBg: string
  glassStrongBg: string
  navBg: string
  tabbarBg: string
  primary: string
  primaryDeep: string
  primarySoft: string
  primaryGlowRgb: string
  expense: string
  expenseRgb: string
  expenseSoft: string
  income: string
  incomeRgb: string
  incomeSoft: string
  textMain: string
  textSecondary: string
  textMuted: string
  borderWarm: string
  warning: string
  warningRgb: string
  activeColor: string
  pageGradientTopRgb: string
  pageGradientClearRgb: string
  borderWarmRgb: string
  shadowWarmRgb: string
  authGlowRgb: string
  authGradientMid: string
  metricGradientStart: string
  chartAxis: string
  chartPalette: string[]
}

export const THEME_APPEARANCE_OPTIONS = [
  { label: '跟随系统', value: 'system' },
  { label: '深色科技', value: 'dark' },
  { label: '浅色银白', value: 'light' }
]

export const THEME_ACCENT_OPTIONS = [
  { label: '冰川青', value: 'cyan', color: '#38bdf8' },
  { label: '系统蓝', value: 'blue', color: '#007aff' },
  { label: '星云紫', value: 'violet', color: '#8b5cf6' }
]

const STORAGE_KEY = 'expense.preferences'
const DEFAULT_THEME_APPEARANCE: ThemeAppearance = 'system'
const DEFAULT_THEME_ACCENT: ThemeAccent = 'cyan'

export const DEFAULT_THEME_PREFERENCE: ThemePreference = {
  appearance: DEFAULT_THEME_APPEARANCE,
  accent: DEFAULT_THEME_ACCENT
}

const ACCENT_COLORS: Record<ThemeAccent, {
  light: string
  dark: string
  glowRgb: string
}> = {
  cyan: {
    light: '#007aff',
    dark: '#38bdf8',
    glowRgb: '56, 189, 248'
  },
  blue: {
    light: '#2563eb',
    dark: '#60a5fa',
    glowRgb: '96, 165, 250'
  },
  violet: {
    light: '#7c3aed',
    dark: '#a78bfa',
    glowRgb: '167, 139, 250'
  }
}

const BASE_TOKENS: Record<'light' | 'dark', Omit<ThemeTokens, 'primary' | 'primaryDeep' | 'primarySoft' | 'primaryGlowRgb' | 'expenseRgb' | 'incomeRgb' | 'warningRgb' | 'chartPalette'>> = {
  light: {
    appearance: 'light',
    pageBg: '#f5f7fb',
    pageBgSoft: '#ffffff',
    cardBg: 'rgba(255, 255, 255, 0.82)',
    cardBgWarm: 'rgba(231, 238, 250, 0.9)',
    glassBg: 'rgba(255, 255, 255, 0.72)',
    glassStrongBg: 'rgba(255, 255, 255, 0.9)',
    navBg: 'rgba(248, 250, 252, 0.78)',
    tabbarBg: 'rgba(255, 255, 255, 0.78)',
    expense: '#f43f5e',
    expenseSoft: 'rgba(244, 63, 94, 0.12)',
    income: '#10b981',
    incomeSoft: 'rgba(16, 185, 129, 0.13)',
    textMain: '#111827',
    textSecondary: '#475569',
    textMuted: '#8a97a8',
    borderWarm: 'rgba(148, 163, 184, 0.28)',
    warning: '#f59e0b',
    activeColor: 'rgba(0, 122, 255, 0.1)',
    pageGradientTopRgb: '225, 238, 255',
    pageGradientClearRgb: '245, 247, 251',
    borderWarmRgb: '148, 163, 184',
    shadowWarmRgb: '15, 23, 42',
    authGlowRgb: '56, 189, 248',
    authGradientMid: '#eef5ff',
    metricGradientStart: 'rgba(255, 255, 255, 0.92)',
    chartAxis: 'rgba(100, 116, 139, 0.28)'
  },
  dark: {
    appearance: 'dark',
    pageBg: '#05070d',
    pageBgSoft: '#0b1020',
    cardBg: 'rgba(15, 23, 42, 0.72)',
    cardBgWarm: 'rgba(30, 41, 59, 0.68)',
    glassBg: 'rgba(15, 23, 42, 0.62)',
    glassStrongBg: 'rgba(15, 23, 42, 0.84)',
    navBg: 'rgba(5, 7, 13, 0.72)',
    tabbarBg: 'rgba(8, 13, 24, 0.78)',
    expense: '#fb7185',
    expenseSoft: 'rgba(251, 113, 133, 0.16)',
    income: '#34d399',
    incomeSoft: 'rgba(52, 211, 153, 0.14)',
    textMain: '#f8fafc',
    textSecondary: '#b6c2d2',
    textMuted: '#718096',
    borderWarm: 'rgba(148, 163, 184, 0.2)',
    warning: '#fbbf24',
    activeColor: 'rgba(56, 189, 248, 0.13)',
    pageGradientTopRgb: '14, 116, 144',
    pageGradientClearRgb: '5, 7, 13',
    borderWarmRgb: '148, 163, 184',
    shadowWarmRgb: '0, 0, 0',
    authGlowRgb: '56, 189, 248',
    authGradientMid: '#07111f',
    metricGradientStart: 'rgba(30, 41, 59, 0.82)',
    chartAxis: 'rgba(148, 163, 184, 0.22)'
  }
}

function normalizeAppearance(value: unknown): ThemeAppearance {
  return value === 'light' || value === 'dark' || value === 'system' ? value : DEFAULT_THEME_APPEARANCE
}

function normalizeAccent(value: unknown): ThemeAccent {
  return value === 'blue' || value === 'violet' || value === 'cyan' ? value : DEFAULT_THEME_ACCENT
}

function migrateAccentFromLegacyPrimary(value: unknown): ThemeAccent {
  if (value === '#8aa06d' || value === '#6f8f4e') return 'cyan'
  if (value === '#d65b4a' || value === '#c96f3a') return 'blue'
  if (value === '#b7845e' || value === '#d99232') return 'violet'
  return DEFAULT_THEME_ACCENT
}

function resolveAppearance(appearance: ThemeAppearance): 'light' | 'dark' {
  if (appearance === 'light' || appearance === 'dark') return appearance
  if (typeof window !== 'undefined' && window.matchMedia?.('(prefers-color-scheme: light)').matches) {
    return 'light'
  }
  return 'dark'
}

function hexToRgb(hex: string) {
  const normalized = hex.replace('#', '')
  return {
    r: parseInt(normalized.slice(0, 2), 16),
    g: parseInt(normalized.slice(2, 4), 16),
    b: parseInt(normalized.slice(4, 6), 16)
  }
}

function rgbString(hex: string) {
  const { r, g, b } = hexToRgb(hex)
  return `${r}, ${g}, ${b}`
}

function rgbToHex(r: number, g: number, b: number) {
  return `#${[r, g, b].map((value) => Math.max(0, Math.min(255, Math.round(value))).toString(16).padStart(2, '0')).join('')}`
}

function mixColor(color: string, target: string, amount: number) {
  const sourceRgb = hexToRgb(color)
  const targetRgb = hexToRgb(target)
  return rgbToHex(
    sourceRgb.r + (targetRgb.r - sourceRgb.r) * amount,
    sourceRgb.g + (targetRgb.g - sourceRgb.g) * amount,
    sourceRgb.b + (targetRgb.b - sourceRgb.b) * amount
  )
}

export function normalizeThemePreference(value: unknown): ThemePreference {
  const source = typeof value === 'object' && value ? value as Record<string, unknown> : {}
  return {
    appearance: normalizeAppearance(source.appearance),
    accent: source.accent
      ? normalizeAccent(source.accent)
      : migrateAccentFromLegacyPrimary(source.themePrimary)
  }
}

export function getThemeTokens(preference: ThemePreference): ThemeTokens {
  const normalized = normalizeThemePreference(preference)
  const appearance = resolveAppearance(normalized.appearance)
  const base = BASE_TOKENS[appearance]
  const accent = ACCENT_COLORS[normalized.accent]
  const primary = accent[appearance]
  const primaryDeep = appearance === 'dark'
    ? mixColor(primary, '#ffffff', 0.26)
    : mixColor(primary, '#020617', 0.2)
  const primarySoft = appearance === 'dark'
    ? `rgba(${accent.glowRgb}, 0.16)`
    : `rgba(${accent.glowRgb}, 0.12)`
  return {
    ...base,
    primary,
    primaryDeep,
    primarySoft,
    primaryGlowRgb: accent.glowRgb,
    expenseRgb: rgbString(base.expense),
    incomeRgb: rgbString(base.income),
    warningRgb: rgbString(base.warning),
    chartPalette: [
      base.expense,
      base.warning,
      primary,
      base.income,
      appearance === 'dark' ? '#c084fc' : '#7c3aed',
      appearance === 'dark' ? '#22d3ee' : '#0891b2',
      base.textSecondary
    ]
  }
}

export function getCurrentThemeTokens() {
  return getThemeTokens(loadThemePreference())
}

export function loadThemePreference(): ThemePreference {
  if (typeof localStorage === 'undefined') {
    return DEFAULT_THEME_PREFERENCE
  }
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    const parsed = raw ? JSON.parse(raw) : {}
    return normalizeThemePreference(parsed)
  } catch {
    return DEFAULT_THEME_PREFERENCE
  }
}

export function saveThemePreference(preference: ThemePreference) {
  const nextPreference = normalizeThemePreference(preference)
  if (typeof localStorage !== 'undefined') {
    try {
      const raw = localStorage.getItem(STORAGE_KEY)
      const parsed = raw ? JSON.parse(raw) : {}
      localStorage.setItem(STORAGE_KEY, JSON.stringify({
        ...parsed,
        ...nextPreference,
        themePreset: undefined,
        themePrimary: undefined
      }))
    } catch {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(nextPreference))
    }
  }
  applyThemePreference(nextPreference)
  return nextPreference
}

export function applyThemePreference(preference: ThemePreference = loadThemePreference()) {
  if (typeof document === 'undefined') return getThemeTokens(preference)
  const tokens = getThemeTokens(preference)
  const root = document.documentElement
  const cssVariables: Record<string, string> = {
    '--page-bg': tokens.pageBg,
    '--page-bg-soft': tokens.pageBgSoft,
    '--card-bg': tokens.cardBg,
    '--card-bg-warm': tokens.cardBgWarm,
    '--glass-bg': tokens.glassBg,
    '--glass-strong-bg': tokens.glassStrongBg,
    '--primary': tokens.primary,
    '--primary-deep': tokens.primaryDeep,
    '--primary-soft': tokens.primarySoft,
    '--expense': tokens.expense,
    '--expense-rgb': tokens.expenseRgb,
    '--expense-soft': tokens.expenseSoft,
    '--income': tokens.income,
    '--income-rgb': tokens.incomeRgb,
    '--income-soft': tokens.incomeSoft,
    '--warning-rgb': tokens.warningRgb,
    '--text-main': tokens.textMain,
    '--text-secondary': tokens.textSecondary,
    '--text-muted': tokens.textMuted,
    '--border-warm': tokens.borderWarm,
    '--shadow-warm': `0 18px 44px rgba(${tokens.shadowWarmRgb}, ${tokens.appearance === 'dark' ? '0.34' : '0.1'})`,
    '--theme-page-gradient-top-rgb': tokens.pageGradientTopRgb,
    '--theme-page-gradient-clear-rgb': tokens.pageGradientClearRgb,
    '--theme-border-warm-rgb': tokens.borderWarmRgb,
    '--theme-shadow-warm-rgb': tokens.shadowWarmRgb,
    '--theme-auth-glow-rgb': tokens.authGlowRgb,
    '--theme-auth-gradient-mid': tokens.authGradientMid,
    '--theme-metric-gradient-start': tokens.metricGradientStart,
    '--theme-chart-axis': tokens.chartAxis,
    '--theme-primary-glow-rgb': tokens.primaryGlowRgb,
    '--van-warning-color': tokens.warning,
    '--van-active-color': tokens.activeColor,
    '--van-nav-bar-background': tokens.navBg,
    '--van-tabbar-background': tokens.tabbarBg
  }
  Object.entries(cssVariables).forEach(([key, value]) => {
    root.style.setProperty(key, value)
  })
  root.dataset.appearance = tokens.appearance
  window.dispatchEvent(new CustomEvent('theme-change', { detail: tokens }))
  return tokens
}
