import assert from 'node:assert/strict'
import { chromium } from 'playwright'
import { withViteServer } from './helpers/vite-test-server.mjs'

const api = (data) => ({ success: true, message: 'ok', data })

const user = {
  id: 1001,
  username: 'records-drag-user',
  nickname: '拖拽测试用户',
  status: 'ACTIVE',
  admin: false,
  createdAt: '2026-08-03T08:00:00'
}

const tokens = {
  accessToken: 'records-drag-access-token',
  refreshToken: 'records-drag-refresh-token',
  expiresInSeconds: 3600
}

const dayOptions = ['2026-07-27', '2026-07-23'].map((date) => ({
  date,
  totalExpense: 0,
  totalIncome: 0,
  balance: 0,
  transactionCount: 0
}))

const days = dayOptions.map((item) => ({
  ...item,
  records: {
    records: [],
    total: 0,
    page: 1,
    size: 5,
    totalPages: 0
  }
}))

function responseData(pathname) {
  if (pathname === '/api/v1/auth/me') return user
  if (pathname === '/api/v1/categories') return []
  if (pathname === '/api/v1/payment-methods') return []
  if (pathname === '/api/v1/transactions/daily-options') return dayOptions
  if (pathname === '/api/v1/transactions/daily-cards') {
    return {
      days,
      totalDays: 2,
      totalRecords: 0,
      dayPage: 1,
      daySize: 30,
      totalDayPages: 1
    }
  }
  return []
}

await withViteServer(async (baseUrl) => {
  const browser = await chromium.launch()
  const context = await browser.newContext({
    viewport: { width: 430, height: 932 },
    deviceScaleFactor: 2,
    isMobile: true
  })

  await context.addInitScript((value) => {
    localStorage.setItem(
      'expense.auth.tokens',
      JSON.stringify(value)
    )
  }, tokens)

  const page = await context.newPage()
  await page.route('**/api/v1/**', (route) => {
    const pathname = new URL(route.request().url()).pathname
    return route.fulfill({ json: api(responseData(pathname)) })
  })

  try {
    await page.goto(new URL('/records', baseUrl).toString())
    const shell = page.locator('.records-jump-fab-shell')
    const button = page.locator('.records-jump-fab')
    await shell.waitFor()
    await page.evaluate(() => {
      const root = document.documentElement
      root.style.setProperty('--app-safe-area-inset-top', '44px')
      root.style.setProperty('--app-safe-area-inset-right', '22px')
      root.style.setProperty('--app-safe-area-inset-bottom', '34px')
      root.style.setProperty('--app-safe-area-inset-left', '18px')
    })
    const initial = await shell.boundingBox()
    assert.ok(initial)

    await page.mouse.move(
      initial.x + initial.width / 2,
      initial.y + initial.height / 2
    )
    await page.mouse.down()
    await page.mouse.move(2, 2, { steps: 8 })
    await page.mouse.up()

    const topLeft = await shell.boundingBox()
    assert.ok(topLeft)
    assert.ok(
      topLeft.x >= 29 && topLeft.x <= 31,
      `左边界位置异常：${JSON.stringify({ initial, topLeft })}`
    )
    assert.ok(
      topLeft.y >= 55 && topLeft.y <= 57,
      `上边界位置异常：${JSON.stringify({ initial, topLeft })}`
    )
    assert.equal(
      await page.locator('.bottom-sheet-popup').filter({ visible: true }).count(),
      0
    )

    await page.evaluate(() => {
      const filler = document.createElement('div')
      filler.style.height = '1400px'
      document.body.appendChild(filler)
      window.scrollTo(0, 320)
    })
    await page.locator('.app-tabbar.app-shell-control-hidden').waitFor()

    await page.mouse.move(
      topLeft.x + topLeft.width / 2,
      topLeft.y + topLeft.height / 2
    )
    await page.mouse.down()
    await page.mouse.move(428, 930, { steps: 8 })
    await page.mouse.up()

    const bottomRight = await shell.boundingBox()
    const tabbar = await page.locator('.app-tabbar').boundingBox()
    assert.ok(bottomRight && tabbar)
    assert.ok(bottomRight.x >= 351, `右边界位置异常：${JSON.stringify(bottomRight)}`)
    assert.ok(
      bottomRight.x + bottomRight.width <= 397,
      `按钮越过右边界：${JSON.stringify(bottomRight)}`
    )
    assert.ok(
      bottomRight.y + bottomRight.height >= 885
        && bottomRight.y + bottomRight.height <= 887,
      `隐藏底部导航后仍错误预留空间：${JSON.stringify({ bottomRight, tabbar })}`
    )

    await page.evaluate(() => {
      window.scrollTo(0, 0)
      const root = document.documentElement
      root.style.removeProperty('--app-safe-area-inset-top')
      root.style.removeProperty('--app-safe-area-inset-right')
      root.style.removeProperty('--app-safe-area-inset-bottom')
      root.style.removeProperty('--app-safe-area-inset-left')
    })
    await page.locator('.app-tabbar:not(.app-shell-control-hidden)').waitFor()

    await page.mouse.move(
      bottomRight.x + bottomRight.width / 2,
      bottomRight.y + bottomRight.height / 2
    )
    await page.mouse.down()
    await page.mouse.move(428, 930, { steps: 8 })
    await page.mouse.up()

    const persistedBottomRight = await shell.boundingBox()
    const visibleTabbar = await page.locator('.app-tabbar').boundingBox()
    assert.ok(persistedBottomRight && visibleTabbar)
    assert.ok(
      persistedBottomRight.y + persistedBottomRight.height <= visibleTabbar.y - 11,
      `按钮遮挡可见底部导航：${JSON.stringify({ persistedBottomRight, visibleTabbar })}`
    )

    await page.reload()
    await shell.waitFor()
    const restored = await shell.boundingBox()
    assert.ok(restored)
    assert.ok(Math.abs(restored.x - persistedBottomRight.x) <= 2)
    assert.ok(Math.abs(restored.y - persistedBottomRight.y) <= 2)

    const enabledAfterReload = await button.isEnabled()
    await button.click()
    const visibleSheetsAfterClick = await page.locator('.bottom-sheet-popup')
      .filter({ visible: true })
      .count()
    assert.equal(
      visibleSheetsAfterClick,
      1,
      `刷新后点击未打开日期选择器：${JSON.stringify({ enabledAfterReload, visibleSheetsAfterClick })}`
    )
  } finally {
    await context.close()
    await browser.close()
  }
})

console.log('流水日期按钮自由拖拽浏览器回归通过')
