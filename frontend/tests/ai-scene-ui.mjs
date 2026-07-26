import assert from 'node:assert/strict'
import { chromium } from 'playwright'
import { withViteServer } from './helpers/vite-test-server.mjs'

const api = (data) => ({
  success: true,
  message: 'ok',
  data
})

const user = {
  id: 1001,
  username: 'ai-scene-user',
  nickname: '智能分类测试用户',
  status: 'ACTIVE',
  admin: false,
  email: 'ai-scene@example.com',
  emailVerifiedAt: '2026-07-26T08:00:00',
  createdAt: '2026-07-01T08:00:00'
}

const tokens = {
  accessToken: 'ai-scene-ui-access-token',
  refreshToken: 'ai-scene-ui-refresh-token',
  expiresInSeconds: 3600
}

async function openSettings(browser, baseUrl, availabilityHandler) {
  const context = await browser.newContext({
    viewport: { width: 375, height: 667 },
    deviceScaleFactor: 2,
    isMobile: true
  })

  await context.addInitScript((authenticatedTokens) => {
    localStorage.setItem(
      'expense.auth.tokens',
      JSON.stringify(authenticatedTokens)
    )
  }, tokens)

  const page = await context.newPage()
  page.setDefaultTimeout(8_000)
  await page.route('**/api/v1/auth/me', (route) => route.fulfill({
    json: api(user)
  }))
  await page.route(
    '**/api/v1/transactions/recommendations/ai-scene/status',
    availabilityHandler
  )
  await page.goto(new URL('/settings', baseUrl).toString())

  return { context, page }
}

async function verifyUnavailable(browser, baseUrl) {
  const { context, page } = await openSettings(
    browser,
    baseUrl,
    (route) => route.fulfill({ json: api({ enabled: false }) })
  )

  try {
    await page.getByText('AI 智能分类', { exact: true }).waitFor()
    await page.getByText('服务未启用', { exact: true }).waitFor()
    const aiSwitch = page.getByRole('switch', { name: 'AI 智能分类' })
    await aiSwitch.waitFor()
    assert.equal(await aiSwitch.isDisabled(), true)
  } finally {
    await context.close()
  }
}

async function verifyAvailabilityFailure(browser, baseUrl) {
  const { context, page } = await openSettings(
    browser,
    baseUrl,
    (route) => route.fulfill({
      status: 503,
      json: {
        success: false,
        message: '服务暂不可用',
        data: null
      }
    })
  )

  try {
    await page.getByText('服务未启用', { exact: true }).waitFor()
    const aiSwitch = page.getByRole('switch', { name: 'AI 智能分类' })
    await aiSwitch.waitFor()
    assert.equal(await aiSwitch.isDisabled(), true)
  } finally {
    await context.close()
  }
}

async function verifyEnabledToggle(browser, baseUrl) {
  const { context, page } = await openSettings(
    browser,
    baseUrl,
    (route) => route.fulfill({ json: api({ enabled: true }) })
  )

  try {
    const aiSwitch = page.getByRole('switch', { name: 'AI 智能分类' })
    await aiSwitch.waitFor()
    assert.equal(await aiSwitch.isEnabled(), true)

    await aiSwitch.click()
    await page.waitForFunction(() => (
      localStorage.getItem('expense.aiSceneConsent.1001') === 'ENABLED'
    ))

    await aiSwitch.click()
    await page.waitForFunction(() => (
      localStorage.getItem('expense.aiSceneConsent.1001') === 'DISABLED'
    ))
  } finally {
    await context.close()
  }
}

await withViteServer(async (baseUrl) => {
  const browser = await chromium.launch()

  try {
    await verifyUnavailable(browser, baseUrl)
    await verifyAvailabilityFailure(browser, baseUrl)
    await verifyEnabledToggle(browser, baseUrl)
  } finally {
    await browser.close()
  }
})

console.log('AI 智能分类设置行为测试通过')
