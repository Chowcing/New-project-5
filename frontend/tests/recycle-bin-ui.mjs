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

const secondPageRecord = {
  ...trashedRecord,
  id: 89,
  itemName: '第二页最后一条',
  amount: 66
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

function pageResponse(records, overrides = {}) {
  return {
    records,
    total: records.length,
    page: 1,
    size: 20,
    totalPages: records.length > 0 ? 1 : 0,
    ...overrides
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

function installApiRoutes(page, overrides = {}) {
  const state = {
    trashRecords: [{ ...trashedRecord }],
    retentionDays: 30,
    settingsGetCount: 0,
    settingsFailuresRemaining: 0,
    holdSettingsGet: false,
    releaseSettingsGet: null,
    rejectSettingsGet: null,
    trashGetFailuresRemaining: 0,
    trashGetRequests: [],
    paginationMode: false,
    secondPageDeleted: false,
    trashCleared: false,
    holdPage2Get: false,
    releasePage2Get: null,
    holdPage1AfterSecondDelete: false,
    releasePage1AfterSecondDelete: null,
    restoreCount: 0,
    permanentDeleteCount: 0,
    clearCount: 0,
    moveToTrashCount: 0,
    retentionPayloads: [],
    ...overrides
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
      const requestedPage = Number(url.searchParams.get('page') || '1')
      state.trashGetRequests.push(requestedPage)
      if (state.trashGetFailuresRemaining > 0) {
        state.trashGetFailuresRemaining -= 1
        return route.fulfill({
          status: 503,
          json: {
            success: false,
            message: '回收站刷新暂不可用',
            data: null
          }
        })
      }
      if (state.paginationMode) {
        if (state.trashCleared) {
          return route.fulfill({
            json: api(pageResponse([]))
          })
        }
        if (requestedPage === 2 && !state.secondPageDeleted) {
          if (state.holdPage2Get) {
            const stalePage = pageResponse([secondPageRecord], {
              total: 21,
              page: 2,
              totalPages: 2
            })
            state.releasePage2Get = () => {
              state.holdPage2Get = false
              return route.fulfill({ json: api(stalePage) })
            }
            return
          }
          return route.fulfill({
            json: api(pageResponse([secondPageRecord], {
              total: 21,
              page: 2,
              totalPages: 2
            }))
          })
        }
        if (
          requestedPage === 1
          && state.secondPageDeleted
          && state.holdPage1AfterSecondDelete
        ) {
          state.releasePage1AfterSecondDelete = () => {
            state.holdPage1AfterSecondDelete = false
            return route.fulfill({
              json: api(pageResponse([{ ...trashedRecord }], {
                total: 20,
                page: 1,
                totalPages: 1
              }))
            })
          }
          return
        }
        return route.fulfill({
          json: api(pageResponse([{ ...trashedRecord }], {
            total: state.secondPageDeleted ? 20 : 21,
            page: 1,
            totalPages: state.secondPageDeleted ? 1 : 2
          }))
        })
      }
      return route.fulfill({ json: api(pageResponse(state.trashRecords)) })
    }
    if (
      method === 'GET'
      && path === '/api/v1/users/me/recycle-bin-settings'
    ) {
      state.settingsGetCount += 1
      if (state.settingsFailuresRemaining > 0) {
        state.settingsFailuresRemaining -= 1
        return route.fulfill({
          status: 503,
          json: {
            success: false,
            message: '设置读取暂不可用',
            data: null
          }
        })
      }
      if (state.holdSettingsGet) {
        state.releaseSettingsGet = (retentionDays = state.retentionDays) => {
          state.holdSettingsGet = false
          return route.fulfill({
            json: api({ retentionDays })
          })
        }
        state.rejectSettingsGet = () => {
          state.holdSettingsGet = false
          return route.fulfill({
            status: 503,
            json: {
              success: false,
              message: '旧页面设置请求失败',
              data: null
            }
          })
        }
        return
      }
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
      && /^\/api\/v1\/transactions\/(88|89)\/permanent$/.test(path)
    ) {
      state.permanentDeleteCount += 1
      if (path.endsWith('/89/permanent')) {
        state.secondPageDeleted = true
      } else {
        state.trashRecords = []
      }
      return route.fulfill({ json: api(null) })
    }
    if (
      method === 'DELETE'
      && path === '/api/v1/transactions/trash'
    ) {
      state.clearCount += 1
      const deletedCount = state.trashRecords.length
      state.trashRecords = []
      state.trashCleared = true
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
    if (method === 'GET' && path === '/api/v1/transactions/daily-cards') {
      const { trashedAt: _trashedAt, ...record } = trashedRecord
      return route.fulfill({
        json: api({
          days: [{
            date: '2026-07-20',
            totalExpense: 28.5,
            totalIncome: 0,
            balance: -28.5,
            transactionCount: 1,
            records: pageResponse([{ ...record, images: [] }], {
              size: 5
            })
          }],
          totalDays: 1,
          totalRecords: 1,
          dayPage: 1,
          daySize: 10,
          totalDayPages: 1
        })
      })
    }
    if (method === 'GET' && path === '/api/v1/transactions/daily-options') {
      return route.fulfill({
        json: api([{
          date: '2026-07-20',
          totalExpense: 28.5,
          totalIncome: 0,
          balance: -28.5,
          transactionCount: 1
        }])
      })
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

async function waitForState(predicate, message) {
  const deadline = Date.now() + 2_000
  while (Date.now() < deadline) {
    if (predicate()) {
      return
    }
    await new Promise((resolve) => setTimeout(resolve, 10))
  }
  throw new Error(message)
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

  state.trashGetFailuresRemaining = 1
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
  await page.getByText('删除后不可恢复', { exact: false }).waitFor()
  assert.equal(state.permanentDeleteCount, 0)
  state.trashGetFailuresRemaining = 1
  await page.getByRole('button', { name: '确认', exact: true }).click()
  assert.equal(state.permanentDeleteCount, 1)
  await page.getByText('回收站是空的', { exact: true }).waitFor()
  assert.equal(await page.getByRole('heading', { name: '午餐' }).count(), 0)
}

async function verifyClearTrash(page, baseUrl, state) {
  state.trashRecords = [{ ...trashedRecord }]
  await openTrash(page, baseUrl)

  await page.getByRole('button', { name: '清空回收站' }).click()
  await page.getByText(
    '所有回收站记录都将被永久删除且不可恢复',
    { exact: false }
  ).waitFor()
  assert.equal(state.clearCount, 0)
  state.trashGetFailuresRemaining = 1
  await page.getByRole('button', { name: '确认', exact: true }).click()
  assert.equal(state.clearCount, 1)
  await page.getByText('回收站是空的', { exact: true }).waitFor()
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
    '已有记录也采用新设置；已到期记录将在下次自动清理时永久删除且不可恢复。',
    { exact: true }
  ).waitFor()
  assert.deepEqual(state.retentionPayloads, [])
  await page.getByRole('button', { name: '确认', exact: true }).click()
  await page.getByText('保留时间已更新', { exact: true }).waitFor()
  assert.deepEqual(state.retentionPayloads, [{ retentionDays: 15 }])

  for (const invalidValue of ['0', '366', '15.5']) {
    const requestCount = state.retentionPayloads.length
    await page.getByRole('button', { name: '设置保留时间' }).click()
    await customInput.fill(invalidValue)
    const expectedMessage = invalidValue === '15.5'
      ? '保留天数必须为整数'
      : '保留天数必须在 1–365 天之间'
    await page.getByText(expectedMessage, { exact: true }).waitFor()
    assert.equal(
      await saveButton.isDisabled(),
      true,
      `无效值 ${invalidValue} 必须禁用保存`
    )
    assert.equal(state.retentionPayloads.length, requestCount)
    await page.getByRole('button', { name: '关闭' }).click()
  }
}

async function verifyInitialTrashFailureDoesNotShowFakeZero(
  page,
  baseUrl
) {
  await page.goto(new URL('/trash', baseUrl).toString())
  await page.getByText('第 1 页记录暂未载入', { exact: true }).waitFor()
  await page.getByText('记录数待载入', { exact: true }).waitFor()
  await page.getByText('尚未获取记录总数，请重试。', {
    exact: true
  }).waitFor()
  assert.equal(await page.getByText('0 条记录', { exact: true }).count(), 0)
  assert.equal(
    await page.getByText('总数已更新，请重试载入当前页。', {
      exact: true
    }).count(),
    0
  )

  await page.getByRole('button', {
    name: '重试加载第 1 页'
  }).click()
  await page.getByRole('heading', { name: '午餐' }).waitFor()
  await page.getByText('1 条记录', { exact: true }).waitFor()
}

async function verifySettingsFailureGate(page, baseUrl, state) {
  await openTrash(page, baseUrl)
  const settingsButton = page.getByRole('button', {
    name: '设置保留时间'
  })
  await page.getByRole('button', {
    name: '重试读取保留时间'
  }).waitFor()
  assert.equal(await settingsButton.isDisabled(), true)
  assert.equal(
    await page.getByText('保留 1个月（30天）', { exact: true }).count(),
    0
  )
  await settingsButton.click({ force: true })
  assert.equal(
    await page.getByRole('spinbutton', {
      name: '自定义保留天数'
    }).count(),
    0
  )
  assert.deepEqual(state.retentionPayloads, [])

  await page.getByRole('button', {
    name: '重试读取保留时间'
  }).click()
  await page.getByText('保留 1个月（30天）', { exact: true }).waitFor()
  assert.equal(await settingsButton.isEnabled(), true)
  assert.deepEqual(state.retentionPayloads, [])
}

async function verifySlowSettingsGate(page, baseUrl, state) {
  await openTrash(page, baseUrl)
  const settingsButton = page.getByRole('button', {
    name: '设置保留时间'
  })
  await page.getByText('正在读取保留时间', { exact: true }).waitFor()
  assert.equal(await settingsButton.isDisabled(), true)
  await settingsButton.click({ force: true })
  assert.equal(
    await page.getByRole('spinbutton', {
      name: '自定义保留天数'
    }).count(),
    0
  )
  assert.deepEqual(state.retentionPayloads, [])

  assert.equal(typeof state.releaseSettingsGet, 'function')
  await state.releaseSettingsGet(90)
  await page.getByText('保留 3个月（90天）', { exact: true }).waitFor()
  assert.equal(await settingsButton.isEnabled(), true)
  await settingsButton.click()
  const customInput = page.getByRole('spinbutton', {
    name: '自定义保留天数'
  })
  await customInput.waitFor()
  assert.equal(await customInput.inputValue(), '90')
}

async function verifyPaginationRollback(page, baseUrl, state) {
  await openTrash(page, baseUrl)
  await page.getByRole('button', { name: '下一页' }).click()
  await page.getByRole('heading', {
    name: '第二页最后一条'
  }).waitFor()
  await page.getByText('第 2 / 2 页', { exact: true }).waitFor()

  await page.getByRole('button', { name: '永久删除' }).click()
  await page.getByText('删除后不可恢复', { exact: false }).waitFor()
  assert.equal(state.permanentDeleteCount, 0)
  await page.getByRole('button', { name: '确认', exact: true }).click()
  await page.getByText('已永久删除', { exact: true }).waitFor()
  await page.getByRole('heading', { name: '午餐' }).waitFor()
  assert.equal(
    await page.getByRole('heading', {
      name: '第二页最后一条'
    }).count(),
    0
  )
  assert.equal(state.trashGetRequests.at(-1), 1)
  assert.equal(await page.getByText('20 条记录', { exact: true }).count(), 1)
}

async function verifyStalePageResponseCannotRestoreClearedRows(
  page,
  baseUrl,
  state
) {
  await openTrash(page, baseUrl)
  await page.getByRole('button', { name: '下一页' }).click()
  await waitForState(
    () => typeof state.releasePage2Get === 'function',
    '未观察到慢 page=2 请求'
  )

  await page.getByRole('button', { name: '清空回收站' }).click()
  await page.getByText(
    '所有回收站记录都将被永久删除且不可恢复',
    { exact: false }
  ).waitFor()
  assert.equal(state.clearCount, 0)
  await page.getByRole('button', { name: '确认', exact: true }).click()
  await page.getByText('回收站是空的', { exact: true }).waitFor()
  assert.equal(state.clearCount, 1)

  await state.releasePage2Get()
  await page.waitForTimeout(150)
  await page.getByText('回收站是空的', { exact: true }).waitFor()
  assert.equal(
    await page.getByRole('heading', {
      name: '第二页最后一条'
    }).count(),
    0
  )
}

async function verifyPaginationLoadingLocksRecordActions(
  page,
  baseUrl,
  state
) {
  await openTrash(page, baseUrl)
  const nextButton = page.getByRole('button', { name: '下一页' })
  await nextButton.click()
  await waitForState(
    () => typeof state.releasePage2Get === 'function',
    '未观察到慢 page=2 请求'
  )
  assert.equal(
    await page.getByRole('button', { name: '恢复' }).isDisabled(),
    true
  )
  assert.equal(
    await page.getByRole('button', { name: '永久删除' }).isDisabled(),
    true
  )
  assert.equal(await nextButton.isDisabled(), true)
  assert.equal(state.restoreCount, 0)
  assert.equal(state.permanentDeleteCount, 0)

  await state.releasePage2Get()
  await page.getByRole('heading', {
    name: '第二页最后一条'
  }).waitFor()
}

async function openSecondPageAndConfirmDelete(page, baseUrl, state) {
  await openTrash(page, baseUrl)
  await page.getByRole('button', { name: '下一页' }).click()
  await page.getByRole('heading', {
    name: '第二页最后一条'
  }).waitFor()
  await page.getByRole('button', { name: '永久删除' }).click()
  await page.getByText('删除后不可恢复', { exact: false }).waitFor()
  assert.equal(state.permanentDeleteCount, 0)
  await page.getByRole('button', { name: '确认', exact: true }).click()
}

async function verifyPreviousPageFailurePlaceholder(page, baseUrl, state) {
  await openTrash(page, baseUrl)
  await page.getByRole('button', { name: '下一页' }).click()
  await page.getByRole('heading', {
    name: '第二页最后一条'
  }).waitFor()
  state.trashGetFailuresRemaining = 1
  await page.getByRole('button', { name: '永久删除' }).click()
  await page.getByText('删除后不可恢复', { exact: false }).waitFor()
  await page.getByRole('button', { name: '确认', exact: true }).click()

  await page.getByText('第 1 页记录暂未载入', { exact: true }).waitFor()
  assert.equal(
    await page.getByText('回收站是空的', { exact: true }).count(),
    0
  )
  await page.getByText('20 条记录', { exact: true }).waitFor()
  await page.getByRole('button', {
    name: '重试加载第 1 页'
  }).click()
  await page.getByRole('heading', { name: '午餐' }).waitFor()
}

async function verifyPreviousPageSlowPlaceholder(page, baseUrl, state) {
  await openSecondPageAndConfirmDelete(page, baseUrl, state)
  await waitForState(
    () => typeof state.releasePage1AfterSecondDelete === 'function',
    '未观察到慢 page=1 回退请求'
  )
  await page.getByText('正在加载第 1 页记录', { exact: true }).waitFor()
  assert.equal(
    await page.getByText('回收站是空的', { exact: true }).count(),
    0
  )
  await page.getByText('20 条记录', { exact: true }).waitFor()

  await state.releasePage1AfterSecondDelete()
  await page.getByRole('heading', { name: '午餐' }).waitFor()
}

async function verifyConfirmationOperationLock(page, baseUrl, state) {
  await openTrash(page, baseUrl)
  const restoreButton = page.getByRole('button', { name: '恢复' })
  const permanentButton = page.getByRole('button', { name: '永久删除' })
  const clearButton = page.getByRole('button', { name: '清空回收站' })

  await permanentButton.click()
  await page.getByText('删除后不可恢复', { exact: false }).waitFor()
  assert.equal(state.restoreCount, 0)
  assert.equal(state.permanentDeleteCount, 0)
  assert.equal(state.clearCount, 0)
  assert.equal(await restoreButton.isDisabled(), true)
  assert.equal(await permanentButton.isDisabled(), true)
  assert.equal(await clearButton.isDisabled(), true)

  await page.getByRole('button', { name: '取消', exact: true }).click()
  await page.getByText('删除后不可恢复', { exact: false }).waitFor({
    state: 'hidden'
  })
  assert.equal(await restoreButton.isEnabled(), true)
  assert.equal(await permanentButton.isEnabled(), true)
  assert.equal(await clearButton.isEnabled(), true)
  assert.equal(state.restoreCount, 0)
  assert.equal(state.permanentDeleteCount, 0)
  assert.equal(state.clearCount, 0)
}

async function verifyPendingDialogClosesOnNavigation(page, baseUrl, state) {
  await page.goto(new URL('/settings', baseUrl).toString())
  await page.getByRole('link', { name: '回收站' }).click()
  await page.getByRole('heading', { name: '午餐' }).waitFor()

  await page.getByRole('button', { name: '永久删除' }).click()
  const warning = page.getByText('删除后不可恢复', { exact: false })
  await warning.waitFor()
  assert.equal(state.permanentDeleteCount, 0)

  await page.goBack()
  await page.waitForURL('**/settings')
  await warning.waitFor({ state: 'hidden' })
  assert.equal(state.permanentDeleteCount, 0)

  assert.equal(typeof state.rejectSettingsGet, 'function')
  await state.rejectSettingsGet()
  await page.waitForTimeout(150)
  assert.equal(
    await page.getByText('旧页面设置请求失败', { exact: false }).count(),
    0
  )

  await page.getByRole('link', { name: '回收站' }).click()
  await page.getByRole('heading', { name: '午餐' }).waitFor()
  const restoreButton = page.getByRole('button', { name: '恢复' })
  assert.equal(await restoreButton.isEnabled(), true)
  await restoreButton.click()
  await page.getByText('已恢复到流水', { exact: true }).waitFor()
  await page.getByText('回收站是空的', { exact: true }).waitFor()
  assert.equal(state.restoreCount, 1)
  assert.equal(state.permanentDeleteCount, 0)
}

async function swipeRecordLeft(page) {
  const recordCell = page.locator('.record-swipe-cell').first()
  await recordCell.waitFor()
  await recordCell.evaluate((cell) => {
    const target = cell.querySelector('.record-row')
    if (!(target instanceof HTMLElement)) {
      throw new Error('缺少可滑动流水行')
    }
    const bounds = target.getBoundingClientRect()
    const clientY = bounds.top + bounds.height / 2
    const dispatch = (type, clientX, active) => {
      const touch = new Touch({
        identifier: 1,
        target,
        clientX,
        clientY,
        pageX: clientX,
        pageY: clientY,
        screenX: clientX,
        screenY: clientY
      })
      target.dispatchEvent(new TouchEvent(type, {
        bubbles: true,
        cancelable: true,
        touches: active ? [touch] : [],
        targetTouches: active ? [touch] : [],
        changedTouches: [touch]
      }))
    }
    dispatch('touchstart', bounds.right - 8, true)
    dispatch('touchmove', bounds.left + 28, true)
    dispatch('touchend', bounds.left + 28, false)
  })
  await page.waitForTimeout(350)
}

async function verifyRecordsSwipeAction(page, baseUrl) {
  await page.goto(new URL('/records', baseUrl).toString())
  await page.getByText('午餐', { exact: true }).first().waitFor()
  await swipeRecordLeft(page)

  const action = page.getByRole('button', {
    name: '移入回收站'
  }).first()
  await action.waitFor()
  const measurement = await action.evaluate((button) => {
    const content = button.querySelector('.van-button__content')
    const text = button.querySelector('.van-button__text')
    if (!(content instanceof HTMLElement) || !(text instanceof HTMLElement)) {
      throw new Error('左滑按钮结构不完整')
    }
    const buttonRect = button.getBoundingClientRect()
    const contentRect = content.getBoundingClientRect()
    const textRect = text.getBoundingClientRect()
    const textRange = document.createRange()
    textRange.selectNodeContents(text)
    return {
      buttonLeft: buttonRect.left,
      buttonRight: buttonRect.right,
      contentLeft: contentRect.left,
      contentRight: contentRect.right,
      textLeft: textRect.left,
      textRight: textRect.right,
      contentClientWidth: content.clientWidth,
      contentScrollWidth: content.scrollWidth,
      contentClientHeight: content.clientHeight,
      contentScrollHeight: content.scrollHeight,
      textLineCount: textRange.getClientRects().length,
      viewportWidth: window.innerWidth
    }
  })
  assert.ok(measurement.buttonLeft >= 0)
  assert.ok(measurement.buttonRight <= measurement.viewportWidth)
  assert.ok(measurement.contentLeft >= measurement.buttonLeft)
  assert.ok(measurement.contentRight <= measurement.buttonRight)
  assert.ok(measurement.textLeft >= measurement.buttonLeft)
  assert.ok(measurement.textRight <= measurement.buttonRight)
  const diagnostic = JSON.stringify(measurement)
  assert.ok(
    measurement.contentScrollWidth <= measurement.contentClientWidth + 1,
    diagnostic
  )
  assert.ok(
    measurement.contentScrollHeight <= measurement.contentClientHeight,
    diagnostic
  )
  assert.equal(measurement.textLineCount, 1, diagnostic)
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

async function verifyDetailActionListLayout(page, baseUrl) {
  await page.setViewportSize({ width: 320, height: 844 })
  await page.goto(new URL('/records/88', baseUrl).toString())

  const names = ['编辑记录', '复制为今日', '设为周期', '移入回收站']
  const buttons = names.map((name) => page.getByRole('button', { name, exact: true }))
  await page.locator('.detail-main-actions').waitFor({ state: 'attached' })
  await buttons[0].scrollIntoViewIfNeeded()
  await buttons[0].waitFor()

  const boxes = []
  for (const button of buttons) {
    const box = await button.boundingBox()
    assert.ok(box, `${await button.getAttribute('aria-label') || '操作按钮'} 缺少布局尺寸`)
    boxes.push(box)
  }

  for (let index = 0; index < boxes.length; index += 1) {
    const box = boxes[index]
    assert.ok(box.x >= 0, `${names[index]} 左侧溢出`)
    assert.ok(box.x + box.width <= 320, `${names[index]} 右侧溢出`)
    assert.ok(box.height >= 48, `${names[index]} 触控高度不足`)
    if (index > 0) {
      assert.ok(box.y >= boxes[index - 1].y + boxes[index - 1].height, `${names[index]} 未纵向排列`)
    }

    const measurement = await buttons[index].evaluate((button) => {
      const content = button.querySelector('.van-button__content')
      const text = button.querySelector('.van-button__text')
      const arrow = button.querySelector('.detail-action-arrow')
      if (!(content instanceof HTMLElement) || !(text instanceof HTMLElement)) {
        throw new Error('详情操作按钮结构不完整')
      }
      const textWalker = document.createTreeWalker(text, NodeFilter.SHOW_TEXT)
      let textNode = textWalker.nextNode()
      while (textNode && !textNode.textContent?.trim()) {
        textNode = textWalker.nextNode()
      }
      if (!textNode) {
        throw new Error('详情操作按钮缺少文字节点')
      }
      const textRange = document.createRange()
      textRange.selectNodeContents(textNode)
      const buttonRect = button.getBoundingClientRect()
      const contentRect = content.getBoundingClientRect()
      const textRect = text.getBoundingClientRect()
      const arrowRect = arrow instanceof HTMLElement ? arrow.getBoundingClientRect() : null
      return {
        buttonLeft: buttonRect.left,
        buttonRight: buttonRect.right,
        contentLeft: contentRect.left,
        contentRight: contentRect.right,
        textLeft: textRect.left,
        textRight: textRect.right,
        arrowLeft: arrowRect?.left ?? null,
        arrowRight: arrowRect?.right ?? null,
        contentClientWidth: content.clientWidth,
        contentScrollWidth: content.scrollWidth,
        textClientWidth: text.clientWidth,
        textScrollWidth: text.scrollWidth,
        textLineCount: textRange.getClientRects().length
      }
    })
    const diagnostic = JSON.stringify(measurement)
    assert.ok(measurement.contentLeft >= measurement.buttonLeft, diagnostic)
    assert.ok(measurement.contentRight <= measurement.buttonRight, diagnostic)
    assert.ok(measurement.textLeft >= measurement.buttonLeft, diagnostic)
    assert.ok(measurement.textRight <= measurement.buttonRight, diagnostic)
    assert.ok(measurement.contentScrollWidth <= measurement.contentClientWidth + 1, diagnostic)
    assert.ok(measurement.textScrollWidth <= measurement.textClientWidth + 1, diagnostic)
    assert.equal(measurement.textLineCount, 1, diagnostic)
    if (index > 0) {
      assert.notEqual(measurement.arrowLeft, null, `${names[index]} 缺少右侧箭头`)
      assert.ok(measurement.arrowLeft >= measurement.buttonLeft, diagnostic)
      assert.ok(measurement.arrowRight <= measurement.buttonRight, diagnostic)
    }
  }

  const editWidth = boxes[0].width
  const actionListWidth = await page.locator('.detail-action-list').evaluate((element) => element.getBoundingClientRect().width)
  assert.ok(Math.abs(editWidth - actionListWidth) <= 1, '编辑主按钮与操作列表宽度不一致')
}

await withViteServer(async (baseUrl) => {
  const browser = await chromium.launch({ headless: true })
  const failures = []

  try {
    async function runCase(name, callback, overrides = {}) {
      const context = await openAuthenticatedContext(browser)
      const page = await context.newPage()
      page.setDefaultTimeout(8_000)
      const state = installApiRoutes(page, overrides)
      try {
        await callback(page, baseUrl, state)
        console.log(`通过：${name}`)
      } catch (error) {
        failures.push(new Error(`${name}：${error.message}`, { cause: error }))
        console.error(`失败：${name}\n${error.stack || error}`)
      } finally {
        await context.close()
      }
    }

    await runCase('设置页回收站入口', verifySettingsEntry)
    await runCase('恢复成功后本地移除且刷新失败不回滚', verifyTrashOverviewAndRestore)
    await runCase('永久删除确认门禁与本地移除', verifyPermanentDelete)
    await runCase('清空确认门禁与本地空态', verifyClearTrash)
    await runCase('保留时间快捷项和自定义边界', verifyRetentionSettings)
    await runCase(
      '首次 GET 失败不伪装为零条记录',
      verifyInitialTrashFailureDoesNotShowFakeZero,
      { trashGetFailuresRemaining: 1 }
    )
    await runCase('设置 GET 失败门禁与重试', verifySettingsFailureGate, {
      settingsFailuresRemaining: 1
    })
    await runCase('慢设置 GET 门禁', verifySlowSettingsGate, {
      holdSettingsGet: true
    })
    await runCase('第二页最后一条删除后回退', verifyPaginationRollback, {
      paginationMode: true
    })
    await runCase(
      '迟到 page=2 响应不能回填清空记录',
      verifyStalePageResponseCannotRestoreClearedRows,
      { paginationMode: true, holdPage2Get: true }
    )
    await runCase(
      '分页加载期间锁定旧页记录动作',
      verifyPaginationLoadingLocksRecordActions,
      { paginationMode: true, holdPage2Get: true }
    )
    await runCase(
      '回退页 GET 503 显示重试占位',
      verifyPreviousPageFailurePlaceholder,
      { paginationMode: true }
    )
    await runCase(
      '回退页慢响应显示加载占位',
      verifyPreviousPageSlowPlaceholder,
      {
        paginationMode: true,
        holdPage1AfterSecondDelete: true
      }
    )
    await runCase(
      '永久删除确认期间统一操作锁',
      verifyConfirmationOperationLock
    )
    await runCase(
      '未决确认弹窗在导航卸载时关闭并释放',
      verifyPendingDialogClosesOnNavigation,
      { holdSettingsGet: true }
    )
    await runCase('流水左滑操作文字不裁切', verifyRecordsSwipeAction)
    await runCase('详情操作列表纵向布局与窄屏边界', verifyDetailActionListLayout)
    await runCase('详情移入回收站', verifyDetailMoveToTrash)

    if (failures.length > 0) {
      throw new AggregateError(
        failures,
        `回收站移动端真实浏览器回归失败：${failures.length} 项`
      )
    }
    console.log('回收站移动端真实浏览器回归通过')
  } finally {
    await browser.close()
  }
})
