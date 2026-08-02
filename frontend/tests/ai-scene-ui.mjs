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

const categories = [
  {
    id: 11,
    name: '餐饮',
    type: 'EXPENSE',
    icon: 'shop-o',
    sortOrder: 10,
    pinned: true
  },
  {
    id: 12,
    name: '娱乐',
    type: 'EXPENSE',
    icon: 'smile-o',
    sortOrder: 20,
    pinned: true
  },
  {
    id: 13,
    name: '购物',
    type: 'EXPENSE',
    icon: 'bag-o',
    sortOrder: 30,
    pinned: true
  },
  {
    id: 14,
    name: '工资',
    type: 'INCOME',
    icon: 'cash-back-record',
    sortOrder: 40,
    pinned: true
  },
  ...Array.from({ length: 7 }, (_, index) => ({
    id: 15 + index,
    name: `分类候选 ${index + 1}`,
    type: 'EXPENSE',
    icon: 'records-o',
    sortOrder: 50 + index * 10,
    pinned: true
  }))
]

const paymentMethods = [
  {
    id: 21,
    name: '微信',
    icon: 'wechat-pay',
    sortOrder: 10,
    pinned: true
  },
  {
    id: 22,
    name: '支付宝',
    icon: 'alipay',
    sortOrder: 20,
    pinned: true
  }
]

const onlinePlatforms = [
  {
    id: 31,
    name: '美团',
    icon: 'shop-o',
    sortOrder: 10,
    pinned: true
  },
  {
    id: 32,
    name: '淘宝',
    icon: 'bag-o',
    sortOrder: 20,
    pinned: true
  }
]

const quickEntryRecommendations = {
  categories,
  paymentMethods,
  onlinePlatforms,
  offlinePlaces: ['乐园', '商场'],
  combinations: []
}

function historyTemplate(overrides = {}) {
  return {
    type: 'EXPENSE',
    itemName: '午餐',
    amount: 48,
    channel: 'ONLINE',
    onlineApp: '美团',
    onlinePlatformId: 31,
    offlinePlace: '',
    paymentMethodId: 21,
    paymentMethodName: '微信',
    categoryId: 11,
    categoryName: '餐饮',
    note: '',
    reason: '过去相似记录',
    score: 0.96,
    ...overrides
  }
}

function aiRecommendation(overrides = {}) {
  return {
    status: 'SUGGESTED',
    categoryId: 12,
    categoryName: '娱乐',
    channel: 'OFFLINE',
    onlinePlatformId: null,
    onlinePlatformName: null,
    confidence: 0.94,
    reason: '事项更像线下娱乐消费',
    ...overrides
  }
}

function uncertainRecommendation() {
  return {
    status: 'UNCERTAIN',
    categoryId: null,
    categoryName: null,
    channel: null,
    onlinePlatformId: null,
    onlinePlatformName: null,
    confidence: 0,
    reason: '信息不足，无法可靠判断'
  }
}

function offlineEmptyDraft() {
  return {
    version: 1,
    savedAt: 1785024000000,
    advancedStep: 1,
    form: {
      type: 'EXPENSE',
      itemName: '',
      amount: '41',
      occurredAt: '2026-07-26T10:00',
      channel: 'OFFLINE',
      onlineApp: '',
      offlinePlace: '',
      paymentMethodId: 21,
      categoryId: 11,
      note: ''
    },
    dirtyFields: {
      amount: false,
      channel: false,
      onlineApp: false,
      onlinePlatformId: false,
      offlinePlace: false,
      paymentMethodId: false,
      categoryId: false
    },
    ocrResults: []
  }
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
    viewport: { width: 390, height: 844 },
    deviceScaleFactor: 2,
    isMobile: true
  })

  await context.addInitScript(({ authenticatedTokens, initialConsent }) => {
    localStorage.setItem(
      'expense.auth.tokens',
      JSON.stringify(authenticatedTokens)
    )
    if (
      initialConsent
      && localStorage.getItem('expense.aiSceneConsent.1001') === null
    ) {
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

async function installQuickAddApiRoutes(
  page,
  {
    availability = true,
    availabilityDelayMs = 0,
    referenceDelayMs = 0,
    historyHandler = async () => ({ data: [] }),
    aiHandler = async () => ({ data: aiRecommendation() })
  } = {}
) {
  const historyRequests = []
  const aiRequests = []

  await page.route('**/api/v1/**', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname

    if (path === '/api/v1/auth/me') {
      await route.fulfill({ json: api(user) })
      return
    }
    if (path === '/api/v1/categories') {
      await new Promise((resolve) => setTimeout(resolve, referenceDelayMs))
      await route.fulfill({ json: api(categories) })
      return
    }
    if (path === '/api/v1/payment-methods') {
      await route.fulfill({ json: api(paymentMethods) })
      return
    }
    if (path === '/api/v1/online-platforms') {
      await new Promise((resolve) => setTimeout(resolve, referenceDelayMs))
      await route.fulfill({ json: api(onlinePlatforms) })
      return
    }
    if (path === '/api/v1/transactions/recommendations/quick-entry') {
      await route.fulfill({ json: api(quickEntryRecommendations) })
      return
    }
    if (path === '/api/v1/transactions/recommendations/ai-scene/status') {
      await new Promise((resolve) => setTimeout(resolve, availabilityDelayMs))
      await route.fulfill({ json: api({ enabled: availability }) })
      return
    }
    if (path === '/api/v1/transactions/recommendations/context') {
      const itemName = url.searchParams.get('itemName') || ''
      const type = url.searchParams.get('type') || ''
      const channel = url.searchParams.get('channel')
      const occurredAt = url.searchParams.get('occurredAt')
      historyRequests.push({ itemName, type, channel, occurredAt })
      const result = await historyHandler({
        itemName,
        type,
        channel,
        occurredAt,
        request
      })
      await route.fulfill({
        status: result.status || 200,
        json: result.body || api(result.data)
      })
      return
    }
    if (
      path === '/api/v1/transactions/recommendations/ai-scene'
      && request.method() === 'POST'
    ) {
      const payload = request.postDataJSON()
      aiRequests.push(payload)
      const result = await aiHandler({ payload, request })
      await route.fulfill({
        status: result.status || 200,
        json: result.body || api(result.data)
      })
      return
    }

    await route.fulfill({
      status: 404,
      json: {
        success: false,
        message: `未配置测试接口：${request.method()} ${path}`,
        data: null
      }
    })
  })

  return { historyRequests, aiRequests }
}

async function openQuickAdd(
  browser,
  baseUrl,
  {
    availability = true,
    availabilityDelayMs = 0,
    consent,
    draft,
    referenceDelayMs = 0,
    waitForOptions = true,
    historyHandler = async () => ({ data: [] }),
    aiHandler = async () => ({ data: aiRecommendation() })
  } = {}
) {
  const context = await browser.newContext({
    viewport: { width: 390, height: 844 },
    deviceScaleFactor: 2,
    isMobile: true
  })

  await context.addInitScript(({
    authenticatedTokens,
    initialConsent,
    initialDraft
  }) => {
    localStorage.setItem(
      'expense.auth.tokens',
      JSON.stringify(authenticatedTokens)
    )
    if (initialConsent) {
      localStorage.setItem('expense.aiSceneConsent.1001', initialConsent)
    }
    if (initialDraft) {
      localStorage.setItem(
        'expense.quickAddDraft.1001',
        JSON.stringify(initialDraft)
      )
    }
  }, {
    authenticatedTokens: tokens,
    initialConsent: consent,
    initialDraft: draft
  })

  const page = await context.newPage()
  page.setDefaultTimeout(8_000)
  const { historyRequests, aiRequests } = await installQuickAddApiRoutes(page, {
    availability,
    availabilityDelayMs,
    referenceDelayMs,
    historyHandler,
    aiHandler
  })

  await page.goto(new URL('/quick-add', baseUrl).toString())
  await page.getByPlaceholder('如冰棍、工资、泳镜').waitFor()
  if (waitForOptions) {
    await page.waitForFunction(() => {
      const button = [...document.querySelectorAll('button')]
        .find((item) => item.textContent?.trim() === '下一步')
      return button instanceof HTMLButtonElement && !button.disabled
    })
  }

  return { context, page, historyRequests, aiRequests }
}

async function waitForRequests(requests, expectedCount, timeoutMs = 8_000) {
  const deadline = Date.now() + timeoutMs
  while (requests.length < expectedCount && Date.now() < deadline) {
    await new Promise((resolve) => setTimeout(resolve, 25))
  }
  assert.equal(requests.length, expectedCount)
}

async function assertActive(button, expected = true) {
  const className = await button.getAttribute('class')
  assert.equal(className?.split(/\s+/).includes('active'), expected)
}

async function goToSceneStep(page) {
  await page.getByRole('button', { name: '下一步' }).click()
  await page.getByText('分类', { exact: true }).first().waitFor()
}

async function goToCoreStep(page) {
  await page.getByRole('button', { name: '上一步' }).click()
  await page.getByPlaceholder('0.00').waitFor()
}

function nearlyEqual(left, right, tolerance = 1) {
  return Math.abs(left - right) <= tolerance
}

async function readQuickChoiceLayout(page) {
  return page.evaluate(() => {
    const read = (selector) => {
      const element = document.querySelector(selector)
      if (!(element instanceof HTMLElement)) return null
      const rect = element.getBoundingClientRect()
      const style = getComputedStyle(element)
      return {
        top: rect.top,
        height: rect.height,
        overflowY: style.overflowY,
        clientHeight: element.clientHeight,
        scrollHeight: element.scrollHeight,
        scrollTop: element.scrollTop
      }
    }

    return {
      sheet: read('.bottom-sheet.quick-choice-shell'),
      body: read('.bottom-sheet__body.quick-choice-body'),
      search: read('.bottom-sheet.quick-choice-shell .van-search'),
      list: read('.bottom-sheet.quick-choice-shell .quick-choice-list')
    }
  })
}

async function verifyQuickChoiceSearchLayout(browser, baseUrl) {
  const session = await openQuickAdd(browser, baseUrl, {
    availability: false
  })

  try {
    await session.page.getByPlaceholder('0.00').fill('20')
    await goToSceneStep(session.page)
    const categoryBlock = session.page.locator('.quick-option-block').filter({
      has: session.page.getByText('分类', { exact: true })
    })
    await categoryBlock.getByRole('button', { name: '更多' }).click()

    const search = session.page.getByPlaceholder('搜索分类')
    const before = await readQuickChoiceLayout(session.page)
    assert.ok(before.sheet && before.body && before.search && before.list)
    assert.equal(before.body.overflowY, 'hidden')
    assert.ok(before.list.scrollHeight > before.list.clientHeight)

    await search.click()
    await search.fill('分类候选')
    const focused = await readQuickChoiceLayout(session.page)
    assert.ok(focused.sheet && focused.search)
    assert.ok(nearlyEqual(focused.sheet.height, before.sheet.height))
    assert.ok(nearlyEqual(focused.search.top, before.search.top))

    await search.fill('')
    const list = session.page.locator('.quick-choice-list')
    await list.evaluate((element) => {
      element.scrollTop = 160
    })
    const scrolled = await readQuickChoiceLayout(session.page)
    assert.ok(scrolled.body && scrolled.search && scrolled.list)
    assert.equal(scrolled.body.scrollTop, 0)
    assert.ok(scrolled.list.scrollTop > 0)
    assert.ok(nearlyEqual(scrolled.search.top, before.search.top))
  } finally {
    await session.context.close()
  }
}

async function verifySettingsUnsetConsentGate(browser, baseUrl) {
  const { context, page } = await openSettings(
    browser,
    baseUrl,
    (route) => route.fulfill({ json: api({ enabled: true }) }),
    'UNSET'
  )

  try {
    const aiSwitch = await availableAiSwitch(page)
    const { historyRequests, aiRequests } = await installQuickAddApiRoutes(page)
    await aiSwitch.click()
    await page.getByRole('region', {
      name: '发送给 AI 的数据',
      exact: true
    }).waitFor()
    await page.getByRole('region', {
      name: '不会发送给 AI 的数据',
      exact: true
    }).waitFor()
    assert.equal(
      await page.evaluate(() => (
        localStorage.getItem('expense.aiSceneConsent.1001')
      )),
      'UNSET'
    )
    assert.equal(await aiSwitch.getAttribute('aria-checked'), 'false')
    assert.equal(aiRequests.length, 0)

    await page.getByRole('button', { name: '关闭', exact: true }).click()
    await page.getByRole('region', {
      name: '发送给 AI 的数据',
      exact: true
    }).waitFor({ state: 'hidden' })
    assert.equal(
      await page.evaluate(() => (
        localStorage.getItem('expense.aiSceneConsent.1001')
      )),
      'UNSET'
    )

    await aiSwitch.click()
    await page.getByRole('button', { name: '暂不开启' }).click()
    assert.equal(
      await page.evaluate(() => (
        localStorage.getItem('expense.aiSceneConsent.1001')
      )),
      'UNSET'
    )
    assert.equal(await aiSwitch.getAttribute('aria-checked'), 'false')
    assert.equal(aiRequests.length, 0)

    await aiSwitch.click()
    assert.equal(aiRequests.length, 0)
    await page.getByRole('button', { name: '开启 AI 分类' }).click()
    await page.waitForFunction(() => (
      localStorage.getItem('expense.aiSceneConsent.1001') === 'ENABLED'
    ))
    assert.equal(await aiSwitch.getAttribute('aria-checked'), 'true')

    await page.goto(new URL('/quick-add', baseUrl).toString())
    await page.getByPlaceholder('如冰棍、工资、泳镜').fill('确认后推荐')
    assert.equal(
      await page.getByRole('button', { name: '开启 AI 分类' }).count(),
      0
    )
    await Promise.all([
      waitForRequests(historyRequests, 1),
      waitForRequests(aiRequests, 1)
    ])
    await page.waitForTimeout(800)
    assert.equal(historyRequests.length, 1)
    assert.equal(aiRequests.length, 1)
  } finally {
    await context.close()
  }
}

async function verifySettingsDisableStopsQuickAddAi(browser, baseUrl) {
  const { context, page } = await openSettings(
    browser,
    baseUrl,
    (route) => route.fulfill({ json: api({ enabled: true }) }),
    'ENABLED'
  )

  try {
    const aiSwitch = await availableAiSwitch(page)
    assert.equal(await aiSwitch.getAttribute('aria-checked'), 'true')
    await aiSwitch.click()
    await page.waitForFunction(() => (
      localStorage.getItem('expense.aiSceneConsent.1001') === 'DISABLED'
    ))

    const { historyRequests, aiRequests } = await installQuickAddApiRoutes(page)
    await page.goto(new URL('/quick-add', baseUrl).toString())
    await page.getByPlaceholder('如冰棍、工资、泳镜').fill('关闭后新事项')
    await waitForRequests(historyRequests, 1)
    await page.waitForTimeout(900)
    assert.equal(historyRequests.length, 1)
    assert.equal(aiRequests.length, 0)
  } finally {
    await context.close()
  }
}

async function verifyQuickAddUnavailableStillUsesHistory(browser, baseUrl) {
  const session = await openQuickAdd(browser, baseUrl, {
    availability: false,
    historyHandler: async ({ itemName }) => ({
      data: [historyTemplate({ itemName })]
    }),
    aiHandler: async () => {
      throw new Error('AI 服务关闭时不应请求 AI')
    }
  })

  try {
    await session.page.getByPlaceholder('如冰棍、工资、泳镜').fill('乐园')
    await session.page.waitForTimeout(600)
    assert.equal(session.historyRequests.length, 0)
    await waitForRequests(session.historyRequests, 1, 350)
    assert.equal(session.historyRequests[0].itemName, '乐园')
    assert.equal(session.historyRequests[0].type, 'EXPENSE')
    assert.equal(session.historyRequests[0].channel, null)
    assert.ok(session.historyRequests[0].occurredAt)
    await session.page.waitForTimeout(800)
    assert.equal(session.historyRequests.length, 1)
    assert.equal(session.aiRequests.length, 0)
    assert.equal(
      await session.page.getByRole('button', { name: '开启 AI 分类' }).count(),
      0
    )
    await session.page.getByText('已按历史习惯预填：过去相似记录').waitFor()
  } finally {
    await session.context.close()
  }
}

async function verifyQuickAddConsentEnableAndDecline(browser, baseUrl) {
  const enabledSession = await openQuickAdd(browser, baseUrl, {
    historyHandler: async () => ({ data: [] }),
    aiHandler: async () => ({ data: aiRecommendation() })
  })

  try {
    await enabledSession.page
      .getByPlaceholder('如冰棍、工资、泳镜')
      .fill('乐园')
    await enabledSession.page
      .getByRole('button', { name: '开启 AI 分类' })
      .waitFor()
    const sentScope = enabledSession.page.getByRole('region', {
      name: '发送给 AI 的数据',
      exact: true
    })
    const excludedScope = enabledSession.page.getByRole('region', {
      name: '不会发送给 AI 的数据',
      exact: true
    })
    await sentScope.waitFor()
    await excludedScope.waitFor()
    await sentScope.getByText(
      '事项名称、收支类型、当前用户可选分类名称、当前用户可选线上平台名称（仅作为候选）',
      { exact: true }
    ).waitFor()
    await sentScope.getByText(
      '不会发送当前选择关系；若已选平台属于当前用户有效平台，其名称仍会作为候选发送',
      { exact: true }
    ).waitFor()
    await excludedScope.getByText(
      '金额、支付方式、备注、OCR 识别文本、凭证图片、历史流水、用户身份和邮箱、线下地点和地点历史、统计数据、登录令牌（JWT、Refresh Token）及其他凭据',
      { exact: true }
    ).waitFor()
    assert.equal(enabledSession.aiRequests.length, 0)
    await enabledSession.page
      .getByRole('button', { name: '开启 AI 分类' })
      .click()
    await enabledSession.page.waitForFunction(() => (
      localStorage.getItem('expense.aiSceneConsent.1001') === 'ENABLED'
    ))
    await enabledSession.page.getByText('AI 建议：娱乐 · 线下').waitFor()
    assert.equal(enabledSession.historyRequests.length, 1)
    assert.equal(enabledSession.aiRequests.length, 1)
  } finally {
    await enabledSession.context.close()
  }

  const declinedSession = await openQuickAdd(browser, baseUrl, {
    historyHandler: async ({ itemName }) => ({
      data: [historyTemplate({ itemName, amount: 52 })]
    }),
    aiHandler: async () => {
      throw new Error('拒绝后不应请求 AI')
    }
  })

  try {
    await declinedSession.page
      .getByPlaceholder('如冰棍、工资、泳镜')
      .fill('乐园')
    await declinedSession.page
      .getByRole('button', { name: '暂不开启' })
      .waitFor()
    await declinedSession.page.getByRole('button', { name: '暂不开启' }).click()
    await declinedSession.page.waitForFunction(() => (
      localStorage.getItem('expense.aiSceneConsent.1001') === 'DISABLED'
    ))
    assert.equal(declinedSession.aiRequests.length, 0)
    assert.equal(
      await declinedSession.page.getByPlaceholder('0.00').inputValue(),
      '52'
    )
    await declinedSession.page
      .getByText('已按历史习惯预填：过去相似记录')
      .waitFor()
  } finally {
    await declinedSession.context.close()
  }
}

async function verifyQuickAddAutoApplyAndUndo(browser, baseUrl) {
  const session = await openQuickAdd(browser, baseUrl, {
    consent: 'ENABLED',
    historyHandler: async () => ({ data: [] }),
    aiHandler: async () => ({ data: aiRecommendation() })
  })

  try {
    await session.page.getByPlaceholder('0.00').fill('66')
    await session.page.getByPlaceholder('如冰棍、工资、泳镜').fill('乐园')
    await session.page.waitForTimeout(600)
    assert.equal(session.historyRequests.length, 0)
    assert.equal(session.aiRequests.length, 0)
    await Promise.all([
      waitForRequests(session.historyRequests, 1, 350),
      waitForRequests(session.aiRequests, 1, 350)
    ])
    await session.page.getByText('AI 建议：娱乐 · 线下').waitFor()
    await session.page.waitForTimeout(800)
    assert.equal(session.historyRequests.length, 1)
    assert.equal(session.aiRequests.length, 1)
    await goToSceneStep(session.page)
    await assertActive(session.page.getByRole('button', { name: '娱乐' }))
    assert.equal(
      await session.page.getByRole('radio', { name: '线下' })
        .getAttribute('aria-checked'),
      'true'
    )
    assert.equal(await session.page.getByLabel('线下地点').inputValue(), '')

    await session.page
      .getByRole('button', { name: '撤销 AI 建议' })
      .click()
    await assertActive(session.page.getByRole('button', { name: '餐饮' }))
    assert.equal(
      await session.page.getByRole('radio', { name: '线上' })
        .getAttribute('aria-checked'),
      'true'
    )
    await assertActive(session.page.getByRole('button', { name: '美团' }))
    await goToCoreStep(session.page)
    assert.equal(await session.page.getByPlaceholder('0.00').inputValue(), '66')
  } finally {
    await session.context.close()
  }
}

async function verifyQuickAddHistoryRefreshesWithoutRepeatingAi(
  browser,
  baseUrl
) {
  let releaseAi
  const aiResponse = new Promise((resolve) => {
    releaseAi = resolve
  })
  const session = await openQuickAdd(browser, baseUrl, {
    consent: 'ENABLED',
    historyHandler: async () => ({ data: [] }),
    aiHandler: async () => {
      await aiResponse
      return { data: aiRecommendation() }
    }
  })

  try {
    await session.page.getByPlaceholder('0.00').fill('36')
    await session.page.getByPlaceholder('如冰棍、工资、泳镜')
      .fill('同一事项刷新上下文')
    await Promise.all([
      waitForRequests(session.historyRequests, 1),
      waitForRequests(session.aiRequests, 1)
    ])
    assert.equal(session.historyRequests.length, 1)
    assert.equal(session.aiRequests.length, 1)

    await goToSceneStep(session.page)
    await session.page.getByRole('radio', { name: '线下' }).click()
    await waitForRequests(session.historyRequests, 2)
    assert.equal(session.historyRequests[1].channel, 'OFFLINE')
    assert.equal(session.aiRequests.length, 1)
    releaseAi()
    await session.page.getByText('AI 建议：娱乐 · 线下').waitFor()
    await session.page.waitForTimeout(800)
    assert.equal(session.historyRequests.length, 2)
    assert.equal(session.aiRequests.length, 1)

    await session.page.getByRole('radio', { name: '线上' }).click()
    await waitForRequests(session.historyRequests, 3)
    assert.equal(session.historyRequests[2].channel, 'ONLINE')
    assert.equal(session.aiRequests.length, 1)

    await session.page.getByRole('button', { name: '下一步' }).click()
    const timeCell = session.page.locator('.quick-extra-panel .van-cell')
      .filter({ hasText: '时间' })
      .first()
    await timeCell.click()
    await session.page.getByRole('button', {
      name: '下一个时间段',
      exact: true
    }).click()
    await session.page.locator(
      'button.modern-calendar-day:not(.outside)'
    ).filter({ hasText: /^1$/ }).first().click()
    await session.page.locator('button.modern-date-text-button.primary').click()
    await waitForRequests(session.historyRequests, 4)
    assert.equal(session.historyRequests[3].channel, 'ONLINE')
    assert.notEqual(
      session.historyRequests[3].occurredAt,
      session.historyRequests[2].occurredAt
    )
    await session.page.waitForTimeout(800)
    assert.equal(session.historyRequests.length, 4)
    assert.equal(session.aiRequests.length, 1)
  } finally {
    releaseAi?.()
    await session.context.close()
  }
}

async function verifyQuickAddTouchedAiFieldCannotBeUndone(browser, baseUrl) {
  const session = await openQuickAdd(browser, baseUrl, {
    consent: 'ENABLED',
    historyHandler: async () => ({ data: [] }),
    aiHandler: async () => ({
      data: aiRecommendation({
        categoryId: 12,
        categoryName: '娱乐',
        channel: 'ONLINE',
        onlinePlatformId: 31,
        onlinePlatformName: '美团',
        reason: '仅调整分类'
      })
    })
  })

  try {
    await session.page.getByPlaceholder('0.00').fill('59')
    await session.page.getByPlaceholder('如冰棍、工资、泳镜')
      .fill('手工改回 AI 分类')
    await session.page.getByText('AI 建议：娱乐 · 线上').waitFor()
    await goToSceneStep(session.page)
    await assertActive(session.page.getByRole('button', { name: '娱乐' }))
    await session.page.getByRole('button', { name: '购物' }).click()
    await session.page.getByRole('button', { name: '娱乐' }).click()
    assert.equal(
      await session.page.getByRole('button', {
        name: '撤销 AI 建议'
      }).count(),
      0
    )
    await assertActive(session.page.getByRole('button', { name: '娱乐' }))
  } finally {
    await session.context.close()
  }
}

async function verifyQuickAddWaitsForAiPrerequisites(browser, baseUrl) {
  const session = await openQuickAdd(browser, baseUrl, {
    availabilityDelayMs: 1_100,
    consent: 'ENABLED',
    referenceDelayMs: 1_100,
    waitForOptions: false,
    historyHandler: async () => ({ data: [] }),
    aiHandler: async () => ({
      data: aiRecommendation({
        categoryId: 13,
        categoryName: '购物',
        channel: 'ONLINE',
        onlinePlatformId: 32,
        onlinePlatformName: '淘宝',
        reason: '延迟初始化后仍应完整应用'
      })
    })
  })

  try {
    await session.page.getByPlaceholder('0.00').fill('81')
    await session.page.getByPlaceholder('如冰棍、工资、泳镜')
      .fill('初始化期间输入')
    await session.page.waitForTimeout(800)
    assert.equal(session.historyRequests.length, 0)
    assert.equal(session.aiRequests.length, 0)
    await Promise.all([
      waitForRequests(session.historyRequests, 1),
      waitForRequests(session.aiRequests, 1)
    ])
    await session.page.getByText('AI 建议：购物 · 线上').waitFor()
    await session.page.waitForTimeout(800)
    assert.equal(session.historyRequests.length, 1)
    assert.equal(session.aiRequests.length, 1)
    await goToSceneStep(session.page)
    await assertActive(session.page.getByRole('button', { name: '购物' }))
    await assertActive(session.page.getByRole('button', { name: '淘宝' }))
  } finally {
    await session.context.close()
  }
}

async function verifyQuickAddUnmountCancelsReadinessRound(browser, baseUrl) {
  const session = await openQuickAdd(browser, baseUrl, {
    availabilityDelayMs: 1_100,
    consent: 'ENABLED',
    referenceDelayMs: 1_100,
    waitForOptions: false
  })

  try {
    await session.page.getByPlaceholder('如冰棍、工资、泳镜')
      .fill('离开页面不得继续推荐')
    await session.page.waitForTimeout(800)
    await session.page.locator('.van-nav-bar__left').click()
    await session.page.waitForURL((url) => url.pathname !== '/quick-add')
    await session.page.waitForTimeout(900)
    assert.equal(session.historyRequests.length, 0)
    assert.equal(session.aiRequests.length, 0)
  } finally {
    await session.context.close()
  }
}

async function verifyQuickAddAgreement(browser, baseUrl) {
  const history = historyTemplate({
    amount: 48,
    paymentMethodId: 22,
    paymentMethodName: '支付宝'
  })
  const session = await openQuickAdd(browser, baseUrl, {
    consent: 'ENABLED',
    historyHandler: async () => ({ data: [history] }),
    aiHandler: async () => ({
      data: aiRecommendation({
        categoryId: 11,
        categoryName: '餐饮',
        channel: 'ONLINE',
        onlinePlatformId: 31,
        onlinePlatformName: '美团',
        reason: 'AI 与历史场景一致'
      })
    })
  })

  try {
    await session.page.getByPlaceholder('如冰棍、工资、泳镜').fill('午餐')
    await session.page
      .getByText('历史与 AI 均建议：餐饮 · 线上', { exact: true })
      .waitFor()
    assert.equal(await session.page.getByPlaceholder('0.00').inputValue(), '48')
    await goToSceneStep(session.page)
    await assertActive(session.page.getByRole('button', { name: '餐饮' }))
    await assertActive(session.page.getByRole('button', { name: '支付宝' }))
    assert.equal(
      await session.page.getByRole('radio', { name: '线上' })
        .getAttribute('aria-checked'),
      'true'
    )
  } finally {
    await session.context.close()
  }
}

async function verifyQuickAddOnlineAppliedExactCopy(browser, baseUrl) {
  const session = await openQuickAdd(browser, baseUrl, {
    consent: 'ENABLED',
    historyHandler: async () => ({ data: [] }),
    aiHandler: async () => ({
      data: aiRecommendation({
        categoryId: 11,
        categoryName: '餐饮',
        channel: 'ONLINE',
        onlinePlatformId: 31,
        onlinePlatformName: '美团',
        reason: 'AI 判断为线上餐饮'
      })
    })
  })

  try {
    await session.page.getByPlaceholder('0.00').fill('28')
    await session.page.getByPlaceholder('如冰棍、工资、泳镜').fill('外卖')
    await session.page.getByText(
      'AI 建议：餐饮 · 线上',
      { exact: true }
    ).waitFor()
    await goToSceneStep(session.page)
    await assertActive(session.page.getByRole('button', { name: '美团' }))
  } finally {
    await session.context.close()
  }
}

async function verifyQuickAddConflictHistoryPrefillUndoRestoresOriginalSnapshot(
  browser,
  baseUrl
) {
  const session = await openQuickAdd(browser, baseUrl, {
    consent: 'ENABLED',
    historyHandler: async ({ itemName }) => ({
      data: [historyTemplate({
        itemName,
        amount: 88,
        paymentMethodId: 22,
        paymentMethodName: '支付宝'
      })]
    }),
    aiHandler: async () => {
      await new Promise((resolve) => setTimeout(resolve, 180))
      return { data: aiRecommendation() }
    }
  })

  try {
    await session.page.getByPlaceholder('如冰棍、工资、泳镜').fill('电玩城')
    await waitForRequests(session.aiRequests, 1)
    assert.equal(await session.page.getByPlaceholder('0.00').inputValue(), '88')
    await session.page.getByRole('button', { name: '采用历史建议' }).waitFor()
    await session.page.getByRole('button', { name: '采用 AI 建议' }).waitFor()
    await session.page.getByRole('button', { name: '采用历史建议' }).click()
    await goToSceneStep(session.page)
    await assertActive(session.page.getByRole('button', { name: '餐饮' }))
    await assertActive(session.page.getByRole('button', { name: '支付宝' }))
    assert.equal(
      await session.page.getByRole('radio', { name: '线上' })
        .getAttribute('aria-checked'),
      'true'
    )
    await assertActive(session.page.getByRole('button', { name: '美团' }))
    await goToCoreStep(session.page)
    assert.equal(await session.page.getByPlaceholder('0.00').inputValue(), '88')
    await session.page.locator('.context-recommendation-hint')
      .getByRole('button', { name: '撤销' })
      .click()
    assert.equal(await session.page.getByPlaceholder('0.00').inputValue(), '')
    await session.page.getByPlaceholder('0.00').fill('1')
    await goToSceneStep(session.page)
    await assertActive(session.page.getByRole('button', { name: '餐饮' }))
    await assertActive(session.page.getByRole('button', { name: '微信' }))
    assert.equal(
      await session.page.getByRole('radio', { name: '线上' })
        .getAttribute('aria-checked'),
      'true'
    )
    await assertActive(session.page.getByRole('button', { name: '美团' }))
  } finally {
    await session.context.close()
  }
}

async function verifyQuickAddPlatformOnlyConflict(browser, baseUrl) {
  const session = await openQuickAdd(browser, baseUrl, {
    consent: 'ENABLED',
    historyHandler: async ({ itemName }) => ({
      data: [historyTemplate({
        itemName,
        amount: 58,
        channel: 'ONLINE',
        onlineApp: '美团',
        onlinePlatformId: 31,
        categoryId: 11,
        categoryName: '餐饮'
      })]
    }),
    aiHandler: async () => ({
      data: aiRecommendation({
        categoryId: 11,
        categoryName: '餐饮',
        channel: 'ONLINE',
        onlinePlatformId: 32,
        onlinePlatformName: '淘宝',
        reason: 'AI 识别到淘宝消费场景'
      })
    })
  })

  try {
    await session.page.getByPlaceholder('如冰棍、工资、泳镜').fill('外卖订单')
    await session.page.getByText(
      '历史建议：餐饮 · 线上 · 美团',
      { exact: true }
    ).waitFor()
    await session.page.getByText(
      'AI 建议：餐饮 · 线上 · 淘宝',
      { exact: true }
    ).waitFor()
    await session.page.getByText(
      'AI 识别到淘宝消费场景',
      { exact: true }
    ).waitFor()
    await session.page.getByRole('button', {
      name: '采用历史建议（美团）',
      exact: true
    }).waitFor()
    await session.page.getByRole('button', {
      name: '采用 AI 建议（淘宝）',
      exact: true
    }).click()
    await goToSceneStep(session.page)
    await assertActive(session.page.getByRole('button', { name: '淘宝' }))
    await assertActive(session.page.getByRole('button', { name: '微信' }))
    await goToCoreStep(session.page)
    assert.equal(await session.page.getByPlaceholder('0.00').inputValue(), '58')
  } finally {
    await session.context.close()
  }
}

async function verifyQuickAddExactLoadingState(browser, baseUrl) {
  let releaseAi
  const aiResponse = new Promise((resolve) => {
    releaseAi = resolve
  })
  const session = await openQuickAdd(browser, baseUrl, {
    consent: 'ENABLED',
    historyHandler: async () => ({ data: [] }),
    aiHandler: async () => {
      await aiResponse
      return { data: aiRecommendation() }
    }
  })

  try {
    await session.page.getByPlaceholder('如冰棍、工资、泳镜').fill('等待判断')
    await waitForRequests(session.aiRequests, 1)
    await session.page.getByText(
      'AI 正在判断分类场景…',
      { exact: true }
    ).waitFor()
  } finally {
    releaseAi?.()
    await session.context.close()
  }
}

async function verifyQuickAddOnlineWithoutPlatformHasNoSideEffects(
  browser,
  baseUrl
) {
  const session = await openQuickAdd(browser, baseUrl, {
    consent: 'ENABLED',
    draft: offlineEmptyDraft(),
    historyHandler: async () => ({ data: [] }),
    aiHandler: async () => ({
      data: aiRecommendation({
        categoryId: 11,
        categoryName: '餐饮',
        channel: 'ONLINE',
        onlinePlatformId: null,
        onlinePlatformName: null,
        reason: '仅判断为线上场景'
      })
    })
  })

  try {
    await session.page.getByRole('button', { name: '继续填写' }).click()
    await session.page.getByPlaceholder('如冰棍、工资、泳镜').fill('线上服务')
    await session.page.getByText('AI 建议：餐饮 · 线上').waitFor()
    await session.page.waitForFunction(() => {
      const raw = localStorage.getItem('expense.quickAddDraft.1001')
      if (!raw) return false
      const draft = JSON.parse(raw)
      return draft.form.channel === 'ONLINE'
    })
    let storedDraft = await session.page.evaluate(() => JSON.parse(
      localStorage.getItem('expense.quickAddDraft.1001')
    ))
    assert.equal(storedDraft.form.onlinePlatformId, undefined)
    assert.equal(storedDraft.form.onlineApp, '')

    await goToSceneStep(session.page)
    assert.equal(
      await session.page.getByRole('radio', { name: '线上' })
        .getAttribute('aria-checked'),
      'true'
    )
    await assertActive(
      session.page.getByRole('button', { name: '美团' }),
      false
    )
    await assertActive(
      session.page.getByRole('button', { name: '淘宝' }),
      false
    )
    await session.page.getByRole('button', { name: '撤销 AI 建议' }).click()
    await session.page.waitForFunction(() => {
      const raw = localStorage.getItem('expense.quickAddDraft.1001')
      if (!raw) return false
      const draft = JSON.parse(raw)
      return draft.form.channel === 'OFFLINE'
    })
    storedDraft = await session.page.evaluate(() => JSON.parse(
      localStorage.getItem('expense.quickAddDraft.1001')
    ))
    assert.equal(storedDraft.form.onlinePlatformId, undefined)
    assert.equal(storedDraft.form.onlineApp, '')
    assert.equal(
      await session.page.getByRole('radio', { name: '线下' })
        .getAttribute('aria-checked'),
      'true'
    )
    assert.equal(await session.page.getByLabel('线下地点').inputValue(), '')

    await session.page.getByRole('radio', { name: '线上' }).click()
    await assertActive(session.page.getByRole('button', { name: '美团' }))
    await session.page.waitForFunction(() => {
      const raw = localStorage.getItem('expense.quickAddDraft.1001')
      if (!raw) return false
      const draft = JSON.parse(raw)
      return draft.form.onlinePlatformId === 31
        && draft.form.onlineApp === '美团'
    })
  } finally {
    await session.context.close()
  }
}

async function verifyQuickAddDropsStaleAi(browser, baseUrl) {
  let releaseOldResponse
  const oldResponse = new Promise((resolve) => {
    releaseOldResponse = resolve
  })
  const session = await openQuickAdd(browser, baseUrl, {
    consent: 'ENABLED',
    historyHandler: async () => ({ data: [] }),
    aiHandler: async ({ payload }) => {
      if (payload.itemName === '旧乐园') {
        await oldResponse
        return {
          data: aiRecommendation({
            categoryName: '旧娱乐',
            reason: '旧响应不应出现'
          })
        }
      }
      return {
        data: aiRecommendation({
          categoryId: 11,
          categoryName: '餐饮',
          channel: 'ONLINE',
          onlinePlatformId: 31,
          onlinePlatformName: '美团',
          reason: '新事项响应'
        })
      }
    }
  })

  try {
    await session.page.getByPlaceholder('如冰棍、工资、泳镜').fill('旧乐园')
    await waitForRequests(session.aiRequests, 1)
    await session.page.getByPlaceholder('如冰棍、工资、泳镜').fill('早餐')
    await waitForRequests(session.aiRequests, 2)
    await session.page.getByText('AI 建议：餐饮 · 线上').waitFor()
    releaseOldResponse()
    await session.page.waitForTimeout(250)
    assert.equal(await session.page.getByText('旧响应不应出现').count(), 0)
    assert.equal(await session.page.getByText('AI 建议：旧娱乐 · 线下').count(), 0)
  } finally {
    releaseOldResponse?.()
    await session.context.close()
  }
}

async function verifyQuickAddProtectsDirtySceneFields(browser, baseUrl) {
  let releaseAi
  const aiResponse = new Promise((resolve) => {
    releaseAi = resolve
  })
  const session = await openQuickAdd(browser, baseUrl, {
    consent: 'ENABLED',
    historyHandler: async () => ({ data: [] }),
    aiHandler: async () => {
      await aiResponse
      return { data: aiRecommendation() }
    }
  })

  try {
    await session.page.getByPlaceholder('0.00').fill('73')
    await session.page.getByPlaceholder('如冰棍、工资、泳镜').fill('手动场景')
    await waitForRequests(session.aiRequests, 1)
    await goToSceneStep(session.page)
    await session.page.getByRole('button', { name: '购物' }).click()
    await session.page.getByRole('radio', { name: '线下' }).click()
    await session.page.getByRole('radio', { name: '线上' }).click()
    releaseAi()
    await session.page.getByText('AI 建议：娱乐 · 线下').waitFor()
    await assertActive(session.page.getByRole('button', { name: '购物' }))
    assert.equal(
      await session.page.getByRole('radio', { name: '线上' })
        .getAttribute('aria-checked'),
      'true'
    )
    await goToCoreStep(session.page)
    assert.equal(await session.page.getByPlaceholder('0.00').inputValue(), '73')
  } finally {
    releaseAi?.()
    await session.context.close()
  }
}

async function verifyQuickAddAiFailuresKeepHistory(browser, baseUrl) {
  const amounts = {
    不确定: 31,
    限流: 32,
    不可用: 33
  }
  const session = await openQuickAdd(browser, baseUrl, {
    consent: 'ENABLED',
    historyHandler: async ({ itemName }) => ({
      data: [historyTemplate({ itemName, amount: amounts[itemName] })]
    }),
    aiHandler: async ({ payload }) => {
      if (payload.itemName === '不确定') {
        return { data: uncertainRecommendation() }
      }
      return {
        status: payload.itemName === '限流' ? 429 : 503,
        body: {
          success: false,
          message: payload.itemName === '限流' ? '请求过于频繁' : '服务暂不可用',
          data: null
        }
      }
    }
  })

  try {
    const itemField = session.page.getByPlaceholder('如冰棍、工资、泳镜')
    await itemField.fill('不确定')
    await session.page
      .getByText('AI 暂无法确定，请手动选择', { exact: true })
      .waitFor()
    assert.equal(await session.page.getByPlaceholder('0.00').inputValue(), '31')

    await itemField.fill('限流')
    await session.page
      .getByText('AI 暂不可用，已保留历史推荐', { exact: true })
      .waitFor()
    assert.equal(await session.page.getByPlaceholder('0.00').inputValue(), '32')

    await itemField.fill('不可用')
    await waitForRequests(session.aiRequests, 3)
    await session.page
      .getByText('AI 暂不可用，已保留历史推荐', { exact: true })
      .waitFor()
    assert.equal(await session.page.getByPlaceholder('0.00').inputValue(), '33')
    assert.deepEqual(
      session.historyRequests.map((item) => item.itemName),
      ['不确定', '限流', '不可用']
    )
  } finally {
    await session.context.close()
  }
}

async function verifyQuickAddUnavailableWithoutHistoryCopy(browser, baseUrl) {
  const session = await openQuickAdd(browser, baseUrl, {
    consent: 'ENABLED',
    historyHandler: async () => ({ data: [] }),
    aiHandler: async () => ({
      status: 503,
      body: {
        success: false,
        message: '服务暂不可用',
        data: null
      }
    })
  })

  try {
    await session.page.getByPlaceholder('如冰棍、工资、泳镜').fill('无历史事项')
    await session.page.getByText(
      'AI 暂不可用，请手动选择',
      { exact: true }
    ).waitFor()
    assert.equal(
      await session.page.getByText('AI 暂不可用，已保留历史推荐').count(),
      0
    )
  } finally {
    await session.context.close()
  }
}

await verifyStrictPortIsolation()

await withViteServer(async (baseUrl) => {
  const browser = await chromium.launch()
  const failures = []
  const runScenario = async (name, scenario) => {
    try {
      await scenario(browser, baseUrl)
      console.log(`通过：${name}`)
    } catch (error) {
      failures.push({ name, error })
      console.error(`失败：${name}\n${error.stack || error}`)
    }
  }

  try {
    await runScenario('设置页：服务不可用', verifyUnavailable)
    await runScenario('设置页：状态接口失败', verifyAvailabilityFailure)
    await runScenario('设置页：开关持久化', verifyEnabledToggle)
    await runScenario('设置页：读取已开启状态', verifyStoredEnabled)
    await runScenario('设置页：首次开启统一完整授权', verifySettingsUnsetConsentGate)
    await runScenario('设置页：关闭后记一笔仅运行历史推荐', verifySettingsDisableStopsQuickAddAi)
    await runScenario('记一笔：不可用时仅历史推荐', verifyQuickAddUnavailableStillUsesHistory)
    await runScenario('记一笔：首次同意与拒绝', verifyQuickAddConsentEnableAndDecline)
    await runScenario('记一笔：AI 自动应用与撤销', verifyQuickAddAutoApplyAndUndo)
    await runScenario('记一笔：渠道时间独立刷新历史', verifyQuickAddHistoryRefreshesWithoutRepeatingAi)
    await runScenario('记一笔：触碰 AI 字段永久失效撤销', verifyQuickAddTouchedAiFieldCannotBeUndone)
    await runScenario('记一笔：等待可用性与候选数据就绪', verifyQuickAddWaitsForAiPrerequisites)
    await runScenario('记一笔：离开页面作废 readiness 等待轮次', verifyQuickAddUnmountCancelsReadinessRound)
    await runScenario('记一笔：历史与 AI 一致', verifyQuickAddAgreement)
    await runScenario('记一笔：线上 AI 已应用精确文案', verifyQuickAddOnlineAppliedExactCopy)
    await runScenario('记一笔：冲突选择历史后撤销恢复原始预填快照', verifyQuickAddConflictHistoryPrefillUndoRestoresOriginalSnapshot)
    await runScenario('记一笔：仅平台冲突可辨识', verifyQuickAddPlatformOnlyConflict)
    await runScenario('记一笔：精确加载状态文案', verifyQuickAddExactLoadingState)
    await runScenario('记一笔：AI 线上空平台无副作用', verifyQuickAddOnlineWithoutPlatformHasNoSideEffects)
    await runScenario('记一笔：丢弃过期 AI 响应', verifyQuickAddDropsStaleAi)
    await runScenario('记一笔：保护手工分类和渠道', verifyQuickAddProtectsDirtySceneFields)
    await runScenario('记一笔：不确定与限流降级', verifyQuickAddAiFailuresKeepHistory)
    await runScenario('记一笔：无历史时不可用文案如实', verifyQuickAddUnavailableWithoutHistoryCopy)
    await runScenario('记一笔：选择弹窗搜索栏固定且仅列表滚动', verifyQuickChoiceSearchLayout)
  } finally {
    await browser.close()
  }

  if (failures.length) {
    throw new AggregateError(
      failures.map(({ error }) => error),
      `${failures.length} 个 AI 智能分类浏览器场景失败：${failures.map(({ name }) => name).join('、')}`
    )
  }
})

console.log('AI 智能分类设置与记一笔行为测试通过')
