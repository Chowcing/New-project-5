export type ThemePresetKey = 'warm' | 'fresh' | 'coffee'

export interface ThemePreference {
  themePreset: ThemePresetKey
  themePrimary: string
}

export interface ThemeTokens {
  pageBg: string
  pageBgSoft: string
  cardBg: string
  cardBgWarm: string
  primary: string
  primaryDeep: string
  primarySoft: string
  expense: string
  expenseSoft: string
  income: string
  incomeSoft: string
  textMain: string
  textSecondary: string
  textMuted: string
  borderWarm: string
  warning: string
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

export const THEME_PRESET_OPTIONS = [
  { label: '米杏暖橙', value: 'warm' },
  { label: '暖绿自然', value: 'fresh' },
  { label: '奶油咖啡', value: 'coffee' }
]

export const THEME_PRIMARY_OPTIONS = ['#c96f3a', '#d99232', '#d65b4a', '#6f8f4e', '#b7845e', '#8aa06d']

const STORAGE_KEY = 'expense.preferences'
const DEFAULT_THEME_PRESET: ThemePresetKey = 'warm'
const DEFAULT_THEME_PRIMARY = '#c96f3a'
const HEX_COLOR_PATTERN = /^#[0-9a-fA-F]{6}$/

export const DEFAULT_THEME_PREFERENCE: ThemePreference = {
  themePreset: DEFAULT_THEME_PRESET,
  themePrimary: DEFAULT_THEME_PRIMARY
}

const THEME_PRESETS: Record<ThemePresetKey, Omit<ThemeTokens, 'primary' | 'primaryDeep' | 'primarySoft' | 'chartPalette'>> = {
  warm: {
    pageBg: '#fff6ea',
    pageBgSoft: '#fffaf4',
    cardBg: '#fffdf9',
    cardBgWarm: '#fff3e1',
    expense: '#d65b4a',
    expenseSoft: '#fff0ec',
    income: '#6f8f4e',
    incomeSoft: '#eef7e8',
    textMain: '#3a2a22',
    textSecondary: '#7a6253',
    textMuted: '#a28b7b',
    borderWarm: '#f0dcc7',
    warning: '#d99232',
    activeColor: '#fff0dc',
    pageGradientTopRgb: '255, 240, 220',
    pageGradientClearRgb: '255, 246, 234',
    borderWarmRgb: '240, 220, 199',
    shadowWarmRgb: '127, 76, 35',
    authGlowRgb: '255, 220, 174',
    authGradientMid: '#fff8ef',
    metricGradientStart: '#fffdf9',
    chartAxis: '#ead4bf'
  },
  fresh: {
    pageBg: '#f6f5e9',
    pageBgSoft: '#fbfaf0',
    cardBg: '#fffdf4',
    cardBgWarm: '#eef4df',
    expense: '#c95f4f',
    expenseSoft: '#fff0ea',
    income: '#5f8e58',
    incomeSoft: '#edf7e8',
    textMain: '#293225',
    textSecondary: '#64705a',
    textMuted: '#8f9a84',
    borderWarm: '#dfe5c9',
    warning: '#c79a34',
    activeColor: '#edf5de',
    pageGradientTopRgb: '232, 241, 211',
    pageGradientClearRgb: '246, 245, 233',
    borderWarmRgb: '223, 229, 201',
    shadowWarmRgb: '79, 103, 56',
    authGlowRgb: '218, 236, 184',
    authGradientMid: '#fbfaef',
    metricGradientStart: '#fffdf4',
    chartAxis: '#d5ddbd'
  },
  coffee: {
    pageBg: '#f8efe4',
    pageBgSoft: '#fff8ef',
    cardBg: '#fffaf2',
    cardBgWarm: '#f4dfcb',
    expense: '#c75e50',
    expenseSoft: '#fff0eb',
    income: '#72854f',
    incomeSoft: '#f0f5e7',
    textMain: '#33251d',
    textSecondary: '#725f50',
    textMuted: '#a18b79',
    borderWarm: '#e7d0bb',
    warning: '#c48c39',
    activeColor: '#f4dfcb',
    pageGradientTopRgb: '244, 223, 203',
    pageGradientClearRgb: '248, 239, 228',
    borderWarmRgb: '231, 208, 187',
    shadowWarmRgb: '103, 65, 38',
    authGlowRgb: '238, 201, 165',
    authGradientMid: '#fff7ec',
    metricGradientStart: '#fffaf2',
    chartAxis: '#dcc4ad'
  }
}

function normalizeThemePreset(value: unknown): ThemePresetKey {
  return value === 'fresh' || value === 'coffee' || value === 'warm' ? value : DEFAULT_THEME_PRESET
}

function normalizeThemePrimary(value: unknown) {
  return typeof value === 'string' && HEX_COLOR_PATTERN.test(value) && THEME_PRIMARY_OPTIONS.includes(value.toLowerCase())
    ? value.toLowerCase()
    : DEFAULT_THEME_PRIMARY
}

function hexToRgb(hex: string) {
  const normalized = hex.replace('#', '')
  return {
    r: parseInt(normalized.slice(0, 2), 16),
    g: parseInt(normalized.slice(2, 4), 16),
    b: parseInt(normalized.slice(4, 6), 16)
  }
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
  const source = typeof value === 'object' && value ? value as Partial<ThemePreference> : {}
  return {
    themePreset: normalizeThemePreset(source.themePreset),
    themePrimary: normalizeThemePrimary(source.themePrimary)
  }
}

export function getThemeTokens(preference: ThemePreference): ThemeTokens {
  const normalized = normalizeThemePreference(preference)
  const preset = THEME_PRESETS[normalized.themePreset]
  const primary = normalized.themePrimary
  const primaryDeep = mixColor(primary, '#1c130e', 0.24)
  const primarySoft = mixColor(primary, preset.pageBgSoft, 0.82)
  return {
    ...preset,
    primary,
    primaryDeep,
    primarySoft,
    chartPalette: [preset.expense, preset.warning, primary, preset.income, '#b7845e', '#c7a58c', preset.textSecondary]
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
        ...nextPreference
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
    '--primary': tokens.primary,
    '--primary-deep': tokens.primaryDeep,
    '--primary-soft': tokens.primarySoft,
    '--expense': tokens.expense,
    '--expense-soft': tokens.expenseSoft,
    '--income': tokens.income,
    '--income-soft': tokens.incomeSoft,
    '--text-main': tokens.textMain,
    '--text-secondary': tokens.textSecondary,
    '--text-muted': tokens.textMuted,
    '--border-warm': tokens.borderWarm,
    '--shadow-warm': `0 10px 28px rgba(${tokens.shadowWarmRgb}, 0.08)`,
    '--theme-page-gradient-top-rgb': tokens.pageGradientTopRgb,
    '--theme-page-gradient-clear-rgb': tokens.pageGradientClearRgb,
    '--theme-border-warm-rgb': tokens.borderWarmRgb,
    '--theme-shadow-warm-rgb': tokens.shadowWarmRgb,
    '--theme-auth-glow-rgb': tokens.authGlowRgb,
    '--theme-auth-gradient-mid': tokens.authGradientMid,
    '--theme-metric-gradient-start': tokens.metricGradientStart,
    '--theme-chart-axis': tokens.chartAxis,
    '--van-warning-color': tokens.warning,
    '--van-active-color': tokens.activeColor,
    '--van-nav-bar-background': `rgba(${tokens.pageGradientClearRgb}, 0.96)`,
    '--van-tabbar-background': `rgba(${tokens.pageGradientClearRgb}, 0.98)`
  }
  Object.entries(cssVariables).forEach(([key, value]) => {
    root.style.setProperty(key, value)
  })
  window.dispatchEvent(new CustomEvent('theme-change', { detail: tokens }))
  return tokens
}
