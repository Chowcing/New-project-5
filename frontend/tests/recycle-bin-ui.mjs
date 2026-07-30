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
  username: 'recycle-bin-user',
  nickname: '回收站测试用户',
  status: 'ACTIVE',
  admin: false,
  email: 'recycle-bin@example.com',
  emailVerifiedAt: '2026-07-01T08:00:00',
  createdAt: '2026-07-01T08:00:00'
}

const tokens = {
  accessToken: 'recycle-bin-ui-access-token',
  refreshToken: 'recycle-bin-ui-refresh-token',
  expiresInSeconds: 3600
}

const trashedRecord = {
  id: 88,
  type: 'EXPENSE',
  itemName: '午餐',
  amount: 28.5,
  occurredAt: '2026-07-20T12:30:00',
  channel: 'OFFLINE',
  offlinePlace: '公司食堂',
  paymentMethodId: 21,
  paymentMethodName: '微信',
  categoryId: 11,
  categoryName: '餐饮',
  categoryIcon: 'shop-o',
  note: '工作日',
  trashedAt: '2026-07-29T18:00:00'
}

const category = {
  id: 11,
  name: '餐饮',
  type: 'EXPENSE',
  icon: 'shop-o',
  sortOrder: 10,
  pinned: true
}

const paymentMethod = {
  id: 21,
  name: '微信',
  icon: 'wechat-pay',
  sortOrder: 10,
  pinned: true
}

function pageResponse(records) {
  return {
    records,
    total: records.length,
    page: 1,
    size: 20,
    totalPages: records.length > 0 ? 1 : 0
  }
}

async function openAuthenticatedContext(browser) {
  const context = await browser.newContext({
    viewport: { width: 390, height: 844 },
    deviceScaleFactor: 2,
    isMobile: true
  })

  await context.addInitScript((authenticatedTokens) => {
    localStorage.setItem(
      'expense.auth.tokens',
      JSON.stringify(authenticatedTokens)
    )
  }, tokens)

  return context
}

function installApiRoutes(page) {
  const state = {
    trashRecords: [{ ...trashedRecord }],
    retentionDays: 30,
    restoreCount: 0,
    permanentDeleteCount: 0,
    clearCount: 0,
    moveToTrashCount: 0,
    retentionPayloads: []
  }

  page.route('**/api/v1/**', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname
    const method = request.method()

    if (method === 'GET' && path === '/api/v1/auth/me') {
      return route.fulfill({ json: api(user) })
    }
    if (
      method === 'GET'
      && path === '/api/v1/transactions/recommendations/ai-scene/status'
    ) {
      return route.fulfill({ json: api({ enabled: false }) })
    }
    if (method === 'GET' && path === '/api/v1/transactions/trash') {
      return route.fulfill({ json: api(pageResponse(state.trashRecords)) })
    }
    if (
      method === 'GET'
      && path === '/api/v1/users/me/recycle-bin-settings'
    ) {
      return route.fulfill({
        json: api({ retentionDays: state.retentionDays })
      })
    }
    if (
      method === 'PUT'
      && path === '/api/v1/users/me/recycle-bin-settings'
    ) {
      const payload = request.postDataJSON()
      state.retentionPayloads.push(payload)
      state.retentionDays = payload.retentionDays
      return route.fulfill({
        json: api({ retentionDays: state.retentionDays })
      })
    }
    if (
      method === 'POST'
      && path === '/api/v1/transactions/88/restore'
    ) {
      state.restoreCount += 1
      state.trashRecords = []
      const { trashedAt: _trashedAt, ...record } = trashedRecord
      return route.fulfill({ json: api({ ...record, images: [] }) })
    }
    if (
      method === 'DELETE'
      && path === '/api/v1/transactions/88/permanent'
    ) {
      state.permanentDeleteCount += 1
      state.trashRecords = []
      return route.fulfill({ json: api(null) })
    }
    if (
      method === 'DELETE'
      && path === '/api/v1/transactions/trash'
    ) {
      state.clearCount += 1
      const deletedCount = state.trashRecords.length
      state.trashRecords = []
      return route.fulfill({ json: api({ deletedCount }) })
    }
    if (
      method === 'GET'
      && path === '/api/v1/transactions/88'
    ) {
      const { trashedAt: _trashedAt, ...record } = trashedRecord
      return route.fulfill({ json: api({ ...record, images: [] }) })
    }
    if (
      method === 'DELETE'
      && path === '/api/v1/transactions/88'
    ) {
      state.moveToTrashCount += 1
      return route.fulfill({ json: api(null) })
    }
    if (method === 'GET' && path === '/api/v1/categories') {
      return route.fulfill({ json: api([category]) })
    }
    if (method === 'GET' && path === '/api/v1/payment-methods') {
      return route.fulfill({ json: api([paymentMethod]) })
    }
    if (method === 'GET' && path === '/api/v1/online-platforms') {
      return route.fulfill({ json: api([]) })
    }

    return route.fulfill({
      status: 404,
      json: {
        success: false,
        message: `未模拟接口：${method} ${path}`,
        data: null
      }
    })
  })

  return state
}

async function confirmVisibleDialog(page, expectedMessage) {
  await page.getByText(expectedMessage, { exact: false }).waitFor()
  await page.getByRole('button', { name: '确认', exact: true }).click()
}

async function verifySettingsEntry(page, baseUrl) {
  await page.goto(new URL('/settings', baseUrl).toString())
  const trashLink = page.getByRole('link', { name: '回收站' })
  await trashLink.waitFor()
  assert.equal(await trashLink.getAttribute('href'), '/trash')
}

async function openTrash(page, baseUrl) {
  await page.goto(new URL('/trash', baseUrl).toString())
  await page.getByRole('heading', { name: '午餐' }).waitFor()
}

async function verifyTrashOverviewAndRestore(page, baseUrl, state) {
  await openTrash(page, baseUrl)
  await page.getByText('保留 1个月（30天）', { exact: true }).waitFor()
  await page.getByRole('button', { name: '恢复' }).waitFor()
  await page.getByRole('button', { name: '永久删除' }).waitFor()
  await page.getByRole('button', { name: '清空回收站' }).waitFor()
  assert.equal(await page.getByText('编辑记录', { exact: true }).count(), 0)
  assert.equal(await page.locator('img').count(), 0)

  await page.getByRole('button', { name: '恢复' }).click()
  await page.getByText('已恢复到流水', { exact: true }).waitFor()
  await page.getByText('回收站是空的', { exact: true }).waitFor()
  assert.equal(state.restoreCount, 1)
  assert.equal(await page.getByRole('heading', { name: '午餐' }).count(), 0)
}

async function verifyPermanentDelete(page, baseUrl, state) {
  state.trashRecords = [{ ...trashedRecord }]
  await openTrash(page, baseUrl)

  await page.getByRole('button', { name: '永久删除' }).click()
  await confirmVisibleDialog(page, '删除后不可恢复')
  await page.getByText('已永久删除', { exact: true }).waitFor()
  assert.equal(state.permanentDeleteCount, 1)
}

async function verifyClearTrash(page, baseUrl, state) {
  state.trashRecords = [{ ...trashedRecord }]
  await openTrash(page, baseUrl)

  await page.getByRole('button', { name: '清空回收站' }).click()
  await confirmVisibleDialog(page, '所有回收站记录都将被永久删除且不可恢复')
  await page.getByText('已清空回收站', { exact: true }).waitFor()
  assert.equal(state.clearCount, 1)
}

async function verifyRetentionSettings(page, baseUrl, state) {
  state.trashRecords = [{ ...trashedRecord }]
  await openTrash(page, baseUrl)

  await page.getByRole('button', { name: '设置保留时间' }).click()
  for (const label of [
    '7天',
    '15天',
    '1个月（30天）',
    '3个月（90天）',
    '半年（180天）',
    '1年（365天）'
  ]) {
    await page.getByRole('button', { name: label, exact: true }).waitFor()
  }

  const customInput = page.getByRole('spinbutton', {
    name: '自定义保留天数'
  })
  const saveButton = page.getByRole('button', {
    name: '保存保留时间',
    exact: true
  })
  await customInput.waitFor()

  await page.getByRole('button', { name: '15天', exact: true }).click()
  await saveButton.click()
  await page.getByText(
    '现有到期记录将在下次自动清理时删除',
    { exact: false }
  ).waitFor()
  assert.deepEqual(state.retentionPayloads, [])
  await page.getByRole('button', { name: '确认', exact: true }).click()
  await page.getByText('保留时间已更新', { exact: true }).waitFor()
  assert.deepEqual(state.retentionPayloads, [{ retentionDays: 15 }])

  for (const invalidValue of ['0', '366', '15.5']) {
    await page.getByRole('button', { name: '设置保留时间' }).click()
    await customInput.fill(invalidValue)
    assert.equal(await saveButton.isDisabled(), true)
    await page.getByRole('button', { name: '关闭' }).click()
  }
}

async function verifyDetailMoveToTrash(page, baseUrl, state) {
  await page.goto(new URL('/records/88', baseUrl).toString())
  const moveButton = page.getByRole('button', { name: '移入回收站' })
  await moveButton.waitFor()
  await moveButton.click()
  await page.getByText(
    '移入后可在“我的-回收站”中恢复。',
    { exact: true }
  ).waitFor()
  assert.equal(state.moveToTrashCount, 0)
  await page.getByRole('button', { name: '确认', exact: true }).click()
  await page.getByText('已移入回收站', { exact: true }).waitFor()
  assert.equal(state.moveToTrashCount, 1)
}

await withViteServer(async (baseUrl) => {
  const browser = await chromium.launch({ headless: true })
  const context = await openAuthenticatedContext(browser)
  const page = await context.newPage()
  page.setDefaultTimeout(8_000)
  const state = installApiRoutes(page)

  try {
    await verifySettingsEntry(page, baseUrl)
    await verifyTrashOverviewAndRestore(page, baseUrl, state)
    await verifyPermanentDelete(page, baseUrl, state)
    await verifyClearTrash(page, baseUrl, state)
    await verifyRetentionSettings(page, baseUrl, state)
    await verifyDetailMoveToTrash(page, baseUrl, state)
    console.log('回收站移动端真实浏览器回归通过')
  } finally {
    await context.close()
    await browser.close()
  }
})
