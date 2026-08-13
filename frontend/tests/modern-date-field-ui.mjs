import assert from 'node:assert/strict'
import { chromium } from 'playwright'
import { withViteServer } from './helpers/vite-test-server.mjs'

const api = (data) => ({ success: true, message: 'ok', data })
const fixedNow = new Date('2026-08-04T09:07:00+08:00')
const tokens = {
  accessToken: 'modern-date-access-token',
  refreshToken: 'modern-date-refresh-token',
  expiresInSeconds: 3600
}
const user = {
  id: 1001,
  username: 'modern-date-user',
  nickname: '日期测试用户',
  status: 'ACTIVE',
  admin: false,
  createdAt: '2026-08-01T08:00:00'
}
const draft = {
  version: 1,
  savedAt: fixedNow.getTime(),
  advancedStep: 3,
  form: {
    type: 'EXPENSE',
    itemName: '布局测试',
    amount: '12.00',
    occurredAt: '2026-08-02T18:38',
    channel: 'OFFLINE',
    onlineApp: '',
    offlinePlace: '测试地点',
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

function responseData(pathname) {
  if (pathname === '/api/v1/auth/me') return user
  if (pathname === '/api/v1/categories') {
    return [{ id: 11, name: '餐饮', type: 'EXPENSE', icon: 'shop-o', sortOrder: 10 }]
  }
  if (pathname === '/api/v1/payment-methods') {
    return [{ id: 21, name: '微信', icon: 'wechat-pay', sortOrder: 10 }]
  }
  if (pathname === '/api/v1/online-platforms') return []
  if (pathname === '/api/v1/transactions/recommendations/quick-entry') {
    return { categories: [], paymentMethods: [], onlinePlatforms: [], offlinePlaces: [], combinations: [] }
  }
  if (pathname === '/api/v1/transactions/recommendations/ai-scene/status') {
    return { enabled: false }
  }
  return []
}

await withViteServer(async (baseUrl) => {
  const browser = await chromium.launch({ headless: true })
  const context = await browser.newContext({
    viewport: { width: 390, height: 667 },
    deviceScaleFactor: 2,
    isMobile: true,
    timezoneId: 'Asia/Shanghai'
  })

  try {
    await context.addInitScript(({ authenticatedTokens, initialDraft }) => {
      localStorage.setItem('expense.auth.tokens', JSON.stringify(authenticatedTokens))
      localStorage.setItem('expense.quickAddDraft.1001', JSON.stringify(initialDraft))
    }, { authenticatedTokens: tokens, initialDraft: draft })

    const page = await context.newPage()
    page.setDefaultTimeout(5_000)
    await page.clock.setFixedTime(fixedNow)
    await page.route('**/api/v1/**', (route) => {
      const pathname = new URL(route.request().url()).pathname
      return route.fulfill({ json: api(responseData(pathname)) })
    })
    await page.goto(new URL('/quick-add?type=EXPENSE', baseUrl).toString())
    await page.getByRole('button', { name: '继续填写', exact: true }).click()

    const timeCell = page.locator('.quick-extra-panel .van-cell').filter({ hasText: '时间' })
    await timeCell.click()
    await page.locator('button.modern-calendar-today').click()

    const selectedTimeAfterToday = await page.locator(
      '.modern-time-picker .van-picker-column__item--selected'
    ).allTextContents()
    assert.deepEqual(selectedTimeAfterToday.map((value) => value.trim()), ['09', '07'])

    await page.locator('button.modern-date-text-button').filter({ hasText: '取消' }).click()
    assert.equal(await timeCell.locator('input').inputValue(), '2026年08月02日 18:38')

    await timeCell.click()
    assert.deepEqual(
      (await page.locator('.modern-calendar-day.selected').allTextContents()).map((value) => value.trim()),
      ['2']
    )
    const selectedTimeAfterReopen = await page.locator(
      '.modern-time-picker .van-picker-column__item--selected'
    ).allTextContents()
    assert.deepEqual(selectedTimeAfterReopen.map((value) => value.trim()), ['18', '38'])

    const selectedTimeItems = page.locator(
      '.modern-time-picker .van-picker-column__item--selected'
    )
    const hourItems = page.locator('.modern-time-picker .van-picker-column').nth(0)
      .locator('.van-picker-column__item')
    await hourItems.filter({ hasText: '19' }).click()
    await page.locator('.modern-time-picker .van-picker-column').nth(0)
      .locator('.van-picker-column__item--selected').filter({ hasText: '19' }).waitFor()
    assert.equal(await page.locator('.modern-time-input-sheet').count(), 0)
    assert.equal((await selectedTimeItems.nth(0).textContent())?.trim(), '19')
    await hourItems.filter({ hasText: '18' }).click()
    await page.locator('.modern-time-picker .van-picker-column').nth(0)
      .locator('.van-picker-column__item--selected').filter({ hasText: '18' }).waitFor()
    assert.equal((await selectedTimeItems.nth(0).textContent())?.trim(), '18')

    await selectedTimeItems.nth(0).click()
    const timeInputSheet = page.locator('.modern-time-input-sheet')
    const timeInput = timeInputSheet.locator('.modern-time-input')
    await timeInputSheet.waitFor({ state: 'visible', timeout: 2_000 })
    assert.equal(await timeInput.inputValue(), '18')
    assert.equal(await timeInput.getAttribute('inputmode'), 'numeric')
    assert.equal(await timeInput.evaluate((element) => document.activeElement === element), true)
    await page.waitForFunction(() => {
      const sheet = document.querySelector('.modern-time-input-sheet')
      const popup = sheet?.closest('.bottom-sheet-popup')
      return popup instanceof HTMLElement && popup.getBoundingClientRect().bottom <= window.innerHeight + 1
    })
    const timeInputLayout = await timeInput.evaluate((element) => ({
      fontSize: Number.parseFloat(getComputedStyle(element).fontSize),
      sheetBottom: element.closest('.bottom-sheet')?.getBoundingClientRect().bottom,
      viewportHeight: window.innerHeight
    }))
    assert.ok(timeInputLayout.fontSize >= 16)
    assert.ok(
      (timeInputLayout.sheetBottom || 0) <= timeInputLayout.viewportHeight + 1,
      JSON.stringify(timeInputLayout)
    )

    for (const invalidHour of ['', 'ab', '24', '100']) {
      await timeInput.fill(invalidHour)
      await timeInputSheet.locator('button.modern-date-text-button.primary').click()
      await timeInputSheet.locator('.modern-time-input-error').waitFor()
      assert.match(await timeInputSheet.locator('.modern-time-input-error').textContent(), /0.*23/)
      assert.equal((await selectedTimeItems.nth(0).textContent())?.trim(), '18')
    }

    await timeInput.fill('6')
    await timeInput.press('Enter')
    await timeInputSheet.waitFor({ state: 'hidden' })
    assert.equal((await selectedTimeItems.nth(0).textContent())?.trim(), '06')

    await selectedTimeItems.nth(1).click()
    await timeInput.fill('60')
    await timeInputSheet.locator('button.modern-date-text-button.primary').click()
    await timeInputSheet.locator('.modern-time-input-error').waitFor()
    assert.match(await timeInputSheet.locator('.modern-time-input-error').textContent(), /0.*59/)
    assert.equal((await selectedTimeItems.nth(1).textContent())?.trim(), '38')
    await timeInput.fill('5')
    await timeInputSheet.locator('button.modern-date-text-button').filter({ hasText: '取消' }).click()
    await timeInputSheet.waitFor({ state: 'hidden' })
    assert.equal((await selectedTimeItems.nth(1).textContent())?.trim(), '38')

    await selectedTimeItems.nth(1).click()
    await timeInput.fill('59')
    await timeInput.press('Enter')
    await timeInputSheet.waitFor({ state: 'hidden' })
    assert.deepEqual(
      (await selectedTimeItems.allTextContents()).map((value) => value.trim()),
      ['06', '59']
    )

    await page.locator('button.modern-date-text-button:visible').filter({ hasText: '取消' }).click()
    assert.equal(await timeCell.locator('input').inputValue(), '2026年08月02日 18:38')

    await timeCell.click()
    assert.deepEqual(
      (await page.locator('.modern-time-picker .van-picker-column__item--selected').allTextContents())
        .map((value) => value.trim()),
      ['18', '38']
    )

    await page.locator('button.modern-calendar-today').click()

    const selectedTime = await page.locator(
      '.modern-time-picker .van-picker-column__item--selected'
    ).allTextContents()
    assert.deepEqual(selectedTime.map((value) => value.trim()), ['09', '07'])

    const layout = await page.evaluate(() => {
      const requireElement = (selector) => {
        const element = document.querySelector(selector)
        if (!(element instanceof HTMLElement)) throw new Error(`找不到元素：${selector}`)
        return element
      }
      const rect = (selector) => {
        const value = requireElement(selector).getBoundingClientRect()
        return { top: value.top, bottom: value.bottom, height: value.height }
      }
      const body = requireElement('.modern-date-body--datetime')
      return {
        viewportHeight: window.innerHeight,
        popup: rect('.bottom-sheet-popup--viewport'),
        sheet: rect('.bottom-sheet--viewport'),
        header: rect('.bottom-sheet__header--toolbar'),
        calendar: rect('.modern-calendar--datetime'),
        today: rect('.modern-calendar-today'),
        picker: rect('.modern-time-picker'),
        bodyBottom: body.getBoundingClientRect().bottom,
        bodyClientHeight: body.clientHeight,
        bodyScrollHeight: body.scrollHeight,
        bodyPaddingBottom: Number.parseFloat(getComputedStyle(body).paddingBottom),
        pickerColumnsHeight: requireElement('.modern-time-picker .van-picker__columns').getBoundingClientRect().height
      }
    })

    assert.ok(layout.popup.top >= 0)
    assert.ok(layout.popup.bottom <= layout.viewportHeight + 1)
    assert.ok(layout.picker.bottom <= layout.bodyBottom + 1)
    assert.ok(layout.bodyScrollHeight <= layout.bodyClientHeight + 1)
    assert.ok(layout.bodyPaddingBottom >= 18)
    assert.equal(layout.pickerColumnsHeight, 132)
    assert.ok(layout.header.bottom <= layout.calendar.top + 1)
    assert.ok(layout.today.bottom <= layout.picker.top)

    await page.locator('button.modern-date-text-button.primary:visible').click()
    await page.getByText('2026年08月04日 09:07', { exact: true }).waitFor()

    await page.goto(new URL('/export', baseUrl).toString())
    const startDateCell = page.locator('.van-cell').filter({ hasText: '开始' })
    await startDateCell.locator('.van-cell__right-icon').click()
    assert.equal(await page.locator('.bottom-sheet-popup--viewport').count(), 0)
    assert.equal(await page.locator('.modern-time-picker').count(), 0)
    await page.locator('button.modern-calendar-today').click()
    await page.locator('button.modern-date-text-button.primary:visible').click()
    const startDateValue = await startDateCell.locator('input').inputValue()
    assert.match(startDateValue, /2026年08月04日/)
    assert.doesNotMatch(startDateValue, /09:07/)

    await page.goto(new URL('/tests/fixtures/modern-date-field.html', baseUrl).toString())

    const defaultTriggerCases = [
      { testId: 'date-field', target: '.van-field__label', title: '选择日期' },
      { testId: 'datetime-field', target: '.van-cell__right-icon', title: '选择日期时间' },
      { testId: 'month-field', target: '.van-field__label', title: '选择月份' },
      { testId: 'year-field', target: '.van-cell__right-icon', title: '选择年份' }
    ]

    for (const triggerCase of defaultTriggerCases) {
      const field = page.getByTestId(triggerCase.testId)
      await field.locator(triggerCase.target).click()
      await page.locator('.bottom-sheet__title', { hasText: triggerCase.title }).waitFor({
        state: 'visible',
        timeout: 2_000
      })
      await page.locator('button.modern-date-text-button:visible').filter({ hasText: '取消' }).click()
    }

    assert.equal(await page.locator('.modern-time-input-sheet').count(), 0)

    const dateField = page.getByTestId('date-field')
    await dateField.locator('.van-field__label').click()
    const todayButton = page.locator('button.modern-calendar-today:visible')
    assert.equal(await todayButton.isDisabled(), true)
    await page.locator('button.modern-date-text-button:visible').filter({ hasText: '取消' }).click()

    await page.clock.setFixedTime(new Date('2026-08-05T00:01:00+08:00'))
    await dateField.locator('.van-field__label').click()
    assert.equal(await todayButton.isDisabled(), false)
    await todayButton.click()
    await page.locator('button.modern-date-text-button.primary:visible').click()
    assert.equal(await dateField.locator('input').inputValue(), '2026年08月05日')
  } finally {
    await context.close()
    await browser.close()
  }
})

console.log('日期时间选择器移动端回归通过')
