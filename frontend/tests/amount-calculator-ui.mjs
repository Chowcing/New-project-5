import assert from 'node:assert/strict'
import { chromium } from 'playwright'
import { withViteServer } from './helpers/vite-test-server.mjs'

await withViteServer(async (baseUrl) => {
  const browser = await chromium.launch()
  try {
    for (const appearance of ['light', 'dark']) {
      const context = await browser.newContext({ viewport: { width: 320, height: 667 }, hasTouch: true })
      await context.addInitScript((appearance) => {
        localStorage.setItem('expense.auth.tokens', JSON.stringify({ accessToken: 'calculator-test', refreshToken: 'calculator-test' }))
        localStorage.setItem('expense.preferences', JSON.stringify({ appearance, accent: 'cyan' }))
      }, appearance)
      const page = await context.newPage()
      page.setDefaultTimeout(8000)
      await page.route('**/api/v1/**', async (route) => {
        const path = new URL(route.request().url()).pathname
        let data = []
        if (path.endsWith('/auth/me')) data = { id: 1001, username: 'calculator-test', email: 'test@example.com', emailVerifiedAt: '2026-01-01T00:00:00' }
        if (path.endsWith('/quick-entry')) data = { categories: [], paymentMethods: [], onlinePlatforms: [], offlinePlaces: [], combinations: [] }
        if (path.endsWith('/ai-scene/status')) data = { enabled: false }
        await route.fulfill({ json: { success: true, data } })
      })
      await page.goto(`${baseUrl}/quick-add`)
      const amount = page.getByPlaceholder('0.00', { exact: true })
      await amount.fill('12')
      const amountLayout = await page.locator('.quick-amount-field').evaluate((field) => {
        const row = field.getBoundingClientRect()
        const body = field.querySelector('.van-field__body').getBoundingClientRect()
        const input = field.querySelector('input').getBoundingClientRect()
        return { rowCenter: (row.left + row.right) / 2, amountCenter: (body.left + input.right) / 2 }
      })
      assert.ok(Math.abs(amountLayout.rowCenter - amountLayout.amountCenter) < 2, '金额与人民币符号应整体居中，计算器不占用金额行空间')
      const floating = page.getByRole('button', { name: '计算器', exact: true })
      const initial = await floating.boundingBox()
      const actionBar = await page.locator('.form-action-bar').boundingBox()
      assert.ok(initial.y > 667 / 2 && initial.y + initial.height < actionBar.y, '悬浮入口默认位于右下方操作栏上方')
      await page.mouse.move(initial.x + initial.width / 2, initial.y + initial.height / 2)
      await page.mouse.down()
      await page.mouse.move(20, 360, { steps: 10 })
      await page.mouse.up()
      const moved = await floating.boundingBox()
      assert.ok(moved.x < initial.x && Math.abs(moved.y - initial.y) > 20, '悬浮按钮支持拖动')
      assert.equal(await page.locator('.amount-calculator').isVisible(), false, '拖动结束不打开计算器')
      await page.mouse.move(moved.x + moved.width / 2, moved.y + moved.height / 2)
      await page.mouse.down()
      await page.mouse.move(319, 666, { steps: 10 })
      await page.mouse.up()
      const clamped = await floating.boundingBox()
      assert.ok(clamped.x + clamped.width <= 320 && clamped.y + clamped.height < actionBar.y, '拖动不能越过屏幕与底部操作栏')
      const touch = await context.newCDPSession(page)
      await touch.send('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: [{ x: clamped.x + clamped.width / 2, y: clamped.y + clamped.height / 2 }] })
      await touch.send('Input.dispatchTouchEvent', { type: 'touchMove', touchPoints: [{ x: 80, y: 260 }] })
      await touch.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] })
      const touched = await floating.boundingBox()
      assert.ok(touched.y < clamped.y - 100, '触屏支持拖动悬浮按钮')
      assert.equal(await page.locator('.amount-calculator').isVisible(), false, '触屏拖动结束不打开计算器')
      await touch.send('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: [{ x: touched.x + touched.width / 2, y: touched.y + touched.height / 2 }] })
      await touch.send('Input.dispatchTouchEvent', { type: 'touchMove', touchPoints: [{ x: 200, y: 360 }] })
      await touch.send('Input.dispatchTouchEvent', { type: 'touchCancel', touchPoints: [] })
      const cancelled = await floating.boundingBox()
      assert.ok(Math.abs(cancelled.x - touched.x) < 1 && Math.abs(cancelled.y - touched.y) < 1, '触摸取消恢复拖动前位置')
      await page.getByRole('button', { name: '计算器', exact: true }).click()
      const sheet = page.locator('.amount-calculator')
      await sheet.waitFor({ state: 'visible' })
      for (const key of ['+', '3', '×', '4']) await sheet.getByRole('button', { name: key, exact: true }).click()
      assert.equal(await sheet.locator('.amount-calculator__result').textContent(), '¥24.00')
      await sheet.getByRole('button', { name: '使用结果' }).click()
      assert.equal(await amount.inputValue(), '24.00')
      await page.getByRole('button', { name: '计算器', exact: true }).click()
      await sheet.getByRole('button', { name: '清空' }).click()
      for (const key of ['1', '÷', '0']) await sheet.getByRole('button', { name: key, exact: true }).click()
      assert.equal(await sheet.getByRole('button', { name: '使用结果' }).isDisabled(), true)
      assert.equal(await sheet.locator('.amount-calculator__result').textContent(), '除数不能为 0')
      await page.getByRole('button', { name: '关闭', exact: true }).click()
      assert.equal(await amount.inputValue(), '24.00')
      await amount.fill('9999999999.99')
      const entry = page.getByRole('button', { name: '计算器', exact: true })
      const box = await entry.boundingBox()
      assert.ok(box && box.x >= 0 && box.x + box.width <= 320, '计算器入口应在小屏内完整显示')
      await entry.click()
      assert.equal(await sheet.locator('.amount-calculator__expression').textContent(), '9999999999.99')
      // Wait for the bottom sheet's enter transition before checking viewport fit.
      await page.waitForFunction(() => {
        const popup = document.querySelector('.amount-calculator')?.closest('.van-popup')
        return popup && popup.getBoundingClientRect().bottom <= innerHeight + 1
      })
      const applyBox = await sheet.getByRole('button', { name: '使用结果' }).boundingBox()
      assert.ok(applyBox && applyBox.y + applyBox.height <= 667, '使用结果按钮应在小屏内显示')
      assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true)
      await page.getByRole('button', { name: '关闭', exact: true }).click()
      await sheet.waitFor({ state: 'hidden' })
      await entry.focus()
      await page.keyboard.press('Enter')
      await sheet.waitFor({ state: 'visible' })
      await page.getByRole('button', { name: '关闭', exact: true }).click()
      await sheet.waitFor({ state: 'hidden' })
      await page.setViewportSize({ width: 320, height: 500 })
      await page.waitForFunction(() => {
        const button = document.querySelector('.calculator-floating-button').getBoundingClientRect()
        const bar = document.querySelector('.form-action-bar').getBoundingClientRect()
        return button.bottom < bar.top && button.left >= 0 && button.right <= innerWidth
      })
      await page.getByRole('button', { name: '2 分类场景' }).click()
      assert.equal(await entry.count(), 0, '分类场景步骤隐藏计算器入口')
      await page.getByRole('button', { name: '1 核心信息' }).click()
      await entry.waitFor({ state: 'visible' })
      await context.close()
    }
  } finally {
    await browser.close()
  }
})
console.log('计算器运算回填、鼠标/触屏拖动、取消、边界、键盘、步骤切换与明暗主题小屏测试通过')
