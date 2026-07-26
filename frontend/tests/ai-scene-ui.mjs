import assert from 'node:assert/strict'
import { createServer } from 'node:http'
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

async function listen(server) {
  await new Promise((resolve, reject) => {
    const handleError = (error) => reject(error)
    server.once('error', handleError)
    server.listen(4176, '127.0.0.1', () => {
      server.off('error', handleError)
      resolve()
    })
  })
}

async function closeServer(server) {
  await new Promise((resolve, reject) => {
    server.close((error) => error ? reject(error) : resolve())
  })
}

async function verifyStrictPortIsolation() {
  const unrelatedServer = createServer((_request, response) => {
    response.end('unrelated server')
  })
  const externalBaseUrl = process.env.AI_SCENE_UI_BASE_URL
  let callbackCalled = false
  let rejection

  await listen(unrelatedServer)
  delete process.env.AI_SCENE_UI_BASE_URL

  try {
    try {
      await withViteServer(async () => {
        callbackCalled = true
      })
    } catch (error) {
      rejection = error
    }

    assert.equal(callbackCalled, false)
    assert.ok(rejection instanceof Error)
    assert.equal(unrelatedServer.listening, true)
    const response = await fetch('http://127.0.0.1:4176/')
    assert.equal(await response.text(), 'unrelated server')
  } finally {
    if (externalBaseUrl === undefined) {
      delete process.env.AI_SCENE_UI_BASE_URL
    } else {
      process.env.AI_SCENE_UI_BASE_URL = externalBaseUrl
    }
    await closeServer(unrelatedServer)
  }
}

async function openSettings(
  browser,
  baseUrl,
  availabilityHandler,
  seededConsent
) {
  const context = await browser.newContext({
    viewport: { width: 375, height: 667 },
    deviceScaleFactor: 2,
    isMobile: true
  })

  await context.addInitScript(({ authenticatedTokens, initialConsent }) => {
    localStorage.setItem(
      'expense.auth.tokens',
      JSON.stringify(authenticatedTokens)
    )
    if (initialConsent) {
      localStorage.setItem('expense.aiSceneConsent.1001', initialConsent)
    }
  }, {
    authenticatedTokens: tokens,
    initialConsent: seededConsent
  })

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

async function availableAiSwitch(page) {
  const aiSwitch = page.getByRole('switch', { name: 'AI 智能分类' })
  await aiSwitch.waitFor()
  await page.waitForFunction(() => (
    document.querySelector('[role="switch"][aria-label="AI 智能分类"]')
      ?.getAttribute('aria-disabled') === 'false'
  ))
  assert.equal(await aiSwitch.isEnabled(), true)
  return aiSwitch
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
    (route) => route.fulfill({ json: api({ enabled: true }) }),
    'DISABLED'
  )

  try {
    const aiSwitch = await availableAiSwitch(page)
    assert.equal(await aiSwitch.getAttribute('aria-checked'), 'false')

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

async function verifyStoredEnabled(browser, baseUrl) {
  const { context, page } = await openSettings(
    browser,
    baseUrl,
    (route) => route.fulfill({ json: api({ enabled: true }) }),
    'ENABLED'
  )

  try {
    const aiSwitch = await availableAiSwitch(page)
    assert.equal(await aiSwitch.getAttribute('aria-checked'), 'true')
  } finally {
    await context.close()
  }
}

await verifyStrictPortIsolation()

await withViteServer(async (baseUrl) => {
  const browser = await chromium.launch()

  try {
    await verifyUnavailable(browser, baseUrl)
    await verifyAvailabilityFailure(browser, baseUrl)
    await verifyEnabledToggle(browser, baseUrl)
    await verifyStoredEnabled(browser, baseUrl)
  } finally {
    await browser.close()
  }
})

console.log('AI 智能分类设置行为测试通过')
