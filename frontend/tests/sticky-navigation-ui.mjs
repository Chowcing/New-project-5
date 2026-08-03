import assert from 'node:assert/strict'
import { chromium } from 'playwright'
import { withViteServer } from './helpers/vite-test-server.mjs'

const api = (data) => ({ success: true, message: 'ok', data })

const tokens = {
  accessToken: 'sticky-navigation-access-token',
  refreshToken: 'sticky-navigation-refresh-token',
  expiresInSeconds: 3600
}

const user = {
  id: 1001,
  username: 'sticky-navigation-user',
  nickname: '吸顶测试用户',
  status: 'ACTIVE',
  admin: true,
  createdAt: '2026-08-03T08:00:00'
}

function responseData(pathname) {
  if (pathname === '/api/v1/auth/me') return user
  if (pathname === '/api/v1/categories') {
    return [{ id: 1, name: '餐饮', type: 'EXPENSE', icon: 'shop-o', sortOrder: 10 }]
  }
  if (pathname === '/api/v1/payment-methods') {
    return [{ id: 1, name: '微信', icon: 'wechat-pay', sortOrder: 10 }]
  }
  if (pathname === '/api/v1/online-platforms') {
    return [{ id: 1, name: '美团', icon: 'shop-o', sortOrder: 10 }]
  }
  if (pathname === '/api/v1/transactions/recommendations/quick-entry') {
    return {
      categories: [],
      paymentMethods: [],
      onlinePlatforms: [],
      offlinePlaces: [],
      combinations: []
    }
  }
  if (pathname === '/api/v1/transactions/recommendations/ai-scene/status') {
    return { enabled: false }
  }
  if (pathname === '/api/v1/admin/workbench') {
    return {
      overview: {
        totalUsers: 0,
        disabledUsers: 0,
        activeUsers30d: 0,
        totalTransactions: 0,
        totalExpense: 0,
        totalIncome: 0,
        dailyMetrics: []
      },
      attentionItems: [],
      dailyMetrics: [],
      recentRiskTransactions: [],
      recentAuditLogs: []
    }
  }
  return []
}

async function openPage(browser, baseUrl, pathname) {
  const context = await browser.newContext({
    viewport: { width: 430, height: 932 },
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
  await page.route('**/api/v1/**', (route) => {
    const requestPath = new URL(route.request().url()).pathname
    return route.fulfill({ json: api(responseData(requestPath)) })
  })
  await page.goto(new URL(pathname, baseUrl).toString())

  return { context, page }
}

async function scrollMetrics(
  page,
  rootSelector,
  stickySelectors,
  fillerParentSelector
) {
  await page.locator(rootSelector).waitFor()

  return page.evaluate(async ({
    rootSelector,
    stickySelectors,
    fillerParentSelector
  }) => {
    const root = requireElement(rootSelector)
    const fillerParent = requireElement(fillerParentSelector)
    const filler = document.createElement('div')
    filler.style.height = '1600px'
    fillerParent.appendChild(filler)
    window.scrollTo(0, 0)

    const stickyElements = stickySelectors.map(requireElement)
    const before = stickyElements.map((element) => element.getBoundingClientRect())

    window.scrollTo(0, 700)
    await new Promise((resolve) => {
      requestAnimationFrame(() => requestAnimationFrame(resolve))
    })

    const after = stickyElements.map((element) => element.getBoundingClientRect())
    return {
      before: before.map(({ top, bottom }) => ({ top, bottom })),
      after: after.map(({ top, bottom }) => ({ top, bottom })),
      windowScrollY: window.scrollY,
      rootScrollTop: root.scrollTop,
      rootOverflowY: getComputedStyle(root).overflowY
    }

    function requireElement(selector) {
      const element = document.querySelector(selector)
      if (!(element instanceof HTMLElement)) {
        throw new Error(`找不到元素：${selector}`)
      }
      return element
    }
  }, { rootSelector, stickySelectors, fillerParentSelector })
}

await withViteServer(async (baseUrl) => {
  const browser = await chromium.launch()

  try {
    const quickAdd = await openPage(
      browser,
      baseUrl,
      '/quick-add?type=EXPENSE'
    )
    try {
      const metrics = await scrollMetrics(
        quickAdd.page,
        '.quick-add-page',
        ['.van-nav-bar'],
        '.quick-add-page'
      )
      assert.ok(metrics.windowScrollY > 0)
      assert.equal(metrics.rootScrollTop, 0)
      assert.ok(Math.abs(metrics.before[0].top) <= 1)
      assert.ok(
        Math.abs(metrics.after[0].top) <= 1,
        `记一笔导航滚动后 top=${metrics.after[0].top}`
      )
    } finally {
      await quickAdd.context.close()
    }

    const admin = await openPage(browser, baseUrl, '/admin')
    try {
      const metrics = await scrollMetrics(
        admin.page,
        '.admin-shell',
        ['.admin-mobile-nav', '.admin-top-tabs'],
        '.admin-main'
      )
      assert.ok(metrics.windowScrollY > 0)
      assert.equal(metrics.rootScrollTop, 0)
      assert.ok(
        Math.abs(metrics.after[0].top) <= 1,
        `后台导航滚动后 top=${metrics.after[0].top}`
      )
      assert.ok(
        Math.abs(metrics.after[1].top - metrics.after[0].bottom) <= 1,
        `后台标签应紧贴导航：tabs=${metrics.after[1].top}, nav=${metrics.after[0].bottom}`
      )
    } finally {
      await admin.context.close()
    }
  } finally {
    await browser.close()
  }
})

console.log('顶部导航吸顶浏览器回归通过')
