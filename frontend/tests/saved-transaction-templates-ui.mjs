import assert from 'node:assert/strict'
import { chromium } from 'playwright'
import { withViteServer } from './helpers/vite-test-server.mjs'

const record = {
  id: 88, type: 'INCOME', itemName: '兼职', amount: 28.5,
  occurredAt: '2025-01-01T12:00:00', channel: 'OFFLINE', offlinePlace: '公司',
  categoryId: 11, categoryName: '兼职收入', paymentMethodId: 21, paymentMethodName: '微信', images: []
}

await withViteServer(async (baseUrl) => {
  const browser = await chromium.launch()
  try {
    const page = await browser.newPage({ viewport: { width: 320, height: 844 }, isMobile: true })
    page.setDefaultTimeout(8000)
    page.on('pageerror', (error) => console.error(error))
    await page.addInitScript(() => localStorage.setItem('expense.auth.tokens', JSON.stringify({
      accessToken: 'test-token', refreshToken: 'test-refresh', expiresInSeconds: 3600
    })))
    let submitted
    await page.route('**/api/v1/**', (route) => {
      const path = new URL(route.request().url()).pathname.replace('/api/v1', '')
      const data = {
        '/auth/me': { id: 1001, username: 'template-test', status: 'ACTIVE', admin: false },
        '/transactions/88': record,
        '/categories': [
          { id: 10, type: 'EXPENSE', name: '餐饮' },
          { id: 12, type: 'INCOME', name: '工资' },
          { id: 11, type: 'INCOME', name: '兼职收入' }
        ],
        '/payment-methods': [{ id: 21, name: '微信' }],
        '/online-platforms': [],
        '/transactions/recommendations/quick-entry': {
          categories: [], paymentMethods: [], onlinePlatforms: [], offlinePlaces: [], combinations: []
        },
        '/transactions/recommendations/ai-scene/status': { enabled: false }
      }[path]
      if (path === '/transactions' && route.request().method() === 'POST') {
        submitted = route.request().postDataJSON()
        return route.fulfill({ json: { success: true, data: { id: 89 } } })
      }
      return route.fulfill({ json: { success: true, data: data ?? [] } })
    })

    const button = (name) => page.getByRole('button', { name, exact: true })
    await page.goto(new URL('/records/88', baseUrl).href)
    await button('存为模板').click()
    await page.getByPlaceholder('例如工作日午餐').fill('每周兼职')
    await button('保存模板').click()
    await page.getByText('已保存常用模板', { exact: true }).waitFor()

    await page.goto(new URL('/quick-add?type=EXPENSE', baseUrl).href)
    await button('常用模板').click()
    await page.getByText('收入 · 金额待填', { exact: true }).waitFor()
    await button('套用').click()
    assert.equal(await page.getByPlaceholder('0.00', { exact: true }).inputValue(), '')
    await button('常用模板').click()
    await button('编辑每周兼职').click()
    await page.getByPlaceholder('例如工作日午餐').fill('固定兼职')
    await page.getByPlaceholder('留空，每次套用后填写').fill('30.5')
    await button('保存模板').click()
    await page.getByText('固定兼职', { exact: true }).waitFor()
    await button('套用').click()
    assert.equal(await page.getByPlaceholder('0.00', { exact: true }).inputValue(), '30.5')
    assert.equal(await page.getByPlaceholder('如冰棍、工资、泳镜').inputValue(), '兼职')
    await page.getByText('下一步', { exact: true }).click()
    await page.getByText('下一步', { exact: true }).click()
    await page.getByText('保存记录', { exact: true }).click()
    await page.waitForURL('**/records')
    assert.equal(submitted.type, 'INCOME')
    assert.equal(submitted.amount, 30.5)
    assert.equal(submitted.categoryId, 11)
    assert.equal(submitted.paymentMethodId, 21)
    assert.equal(submitted.offlinePlace, '公司')
    assert.notEqual(submitted.occurredAt, record.occurredAt)

    await page.goto(new URL('/quick-add', baseUrl).href)
    await button('常用模板').click()
    await button('删除固定兼职').click()
    await button('确认').click()
    await page.getByText('暂无常用模板，可在流水详情中存为模板', { exact: true }).waitFor()
  } finally {
    await browser.close()
  }
})
