import { chromium } from 'playwright'

const BASE_URL = process.env.WORKSPACE_FIT_BASE_URL || 'http://127.0.0.1:5173'

const api = (data, message = 'ok') => ({
  success: true,
  message,
  data
})

const user = {
  id: 1001,
  username: 'demo',
  nickname: '演示用户',
  status: 'ACTIVE',
  admin: false,
  createdAt: '2026-06-01T08:00:00'
}

const stats = {
  month: '2026-06',
  totalExpense: 3280.6,
  totalIncome: 12800,
  balance: 9519.4,
  transactionCount: 42,
  expenseCount: 38,
  incomeCount: 4,
  insight: {
    currentPeriod: '2026-06',
    previousPeriod: '2026-05',
    previousTotalExpense: 2880.6,
    previousTotalIncome: 12600,
    previousBalance: 9719.4,
    expenseChangeAmount: 123456.78,
    expenseChangePercent: 13.88,
    incomeChangeAmount: 200,
    incomeChangePercent: 1.59,
    balanceChangeAmount: -200,
    balanceChangePercent: -2.06,
    averageDailyExpense: 109.35,
    averageExpensePerTransaction: 86.33,
    peakExpense: {
      period: '2026-06-12',
      label: '购物高峰',
      amount: 628.8,
      transactionCount: 4
    }
  },
  monthlyBudget: {
    categoryId: null,
    categoryName: '整月预算',
    budgetAmount: 5000,
    usedAmount: 3280.6,
    remainingAmount: 1719.4,
    usagePercent: 65.61,
    overBudget: false,
    transactionCount: 38
  },
  categoryBudgetUsages: [],
  dailyTrend: [],
  expenseByCategory: [],
  incomeByCategory: [],
  expenseByChannel: [],
  expenseByPaymentMethod: []
}

const records = [
  ['星巴克咖啡', 38, '2026-06-17T09:12:00', '餐饮', 'shop-o'],
  ['地铁通勤', 6, '2026-06-16T18:40:00', '交通', 'logistics'],
  ['京东日用品', 128.5, '2026-06-16T13:20:00', '购物', 'cart-o'],
  ['便利店零食', 21.8, '2026-06-15T20:10:00', '零食饮料', 'bag-o'],
  ['午餐外卖', 32, '2026-06-15T12:08:00', '外卖', 'shop-o']
].map(([itemName, amount, occurredAt, categoryName, categoryIcon], index) => ({
  id: index + 1,
  type: 'EXPENSE',
  itemName,
  amount,
  occurredAt,
  channel: 'ONLINE',
  onlineApp: '美团',
  paymentMethodId: 1,
  paymentMethodName: '微信',
  categoryId: index + 10,
  categoryName,
  categoryIcon,
  images: []
}))

const dueRuns = [
  {
    id: 10,
    ruleId: 1,
    ruleName: '房租',
    dueDate: '2026-06-17',
    reminderDaysBefore: 1,
    type: 'EXPENSE',
    itemName: '六月房租',
    amount: 2600,
    channel: 'OFFLINE',
    offlinePlace: '小区物业',
    paymentMethodId: 1,
    categoryId: 20,
    status: 'PENDING'
  },
  {
    id: 11,
    ruleId: 2,
    ruleName: '会员订阅',
    dueDate: '2026-06-17',
    reminderDaysBefore: 0,
    type: 'EXPENSE',
    itemName: '视频会员',
    amount: 25,
    channel: 'ONLINE',
    onlineApp: '哔哩哔哩',
    paymentMethodId: 1,
    categoryId: 21,
    status: 'PENDING'
  }
  ,
  {
    id: 12,
    ruleId: 3,
    ruleName: '水电燃气',
    dueDate: '2026-06-17',
    reminderDaysBefore: 0,
    type: 'EXPENSE',
    itemName: '水电费',
    amount: 180,
    channel: 'OFFLINE',
    offlinePlace: '生活缴费',
    paymentMethodId: 1,
    categoryId: 22,
    status: 'PENDING'
  }
]

const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width: 375, height: 667 }, deviceScaleFactor: 2, isMobile: true })

await page.route('**/api/v1/auth/me', (route) => route.fulfill({ json: api(user) }))
await page.route('**/api/v1/statistics/monthly**', (route) => route.fulfill({ json: api(stats) }))
await page.route('**/api/v1/transactions**', (route) => route.fulfill({
  json: api({
    records,
    total: records.length,
    page: 1,
    size: 5,
    totalPages: 1
  })
}))
await page.route('**/api/v1/recurring-runs/due**', (route) => route.fulfill({ json: api(dueRuns) }))

await page.goto(BASE_URL)
await page.evaluate(() => {
  localStorage.setItem('expense.auth.tokens', JSON.stringify({
    accessToken: 'workspace-fit-access-token',
    refreshToken: 'workspace-fit-refresh-token',
    expiresInSeconds: 3600
  }))
})
await page.reload()
await page.getByText('月度工作台').waitFor()
await page.getByText('今日待处理').waitFor()
await page.getByText('¥9519.40').waitFor()
const visibleBalanceLayout = await page.evaluate(() => {
  const balance = document.querySelector('.workspace-balance')?.getBoundingClientRect()
  const balanceAmount = document.querySelector('.workspace-balance strong')?.getBoundingClientRect()
  const insightValues = [...document.querySelectorAll('.workspace-insight-value')]
  return {
    balanceAmountRightInset: balance && balanceAmount ? Math.round(balance.right - balanceAmount.right) : null,
    balanceLabelLeft: balance ? Math.round(balance.left) : null,
    balanceAmountLeft: balanceAmount ? Math.round(balanceAmount.left) : null,
    insightValuesFit: insightValues.every((element) => element.scrollWidth <= element.clientWidth + 1)
  }
})
await page.getByRole('button', { name: '隐藏金额' }).click()
await page.locator('.amount-mask').first().waitFor()

const metrics = await page.evaluate(() => {
  const content = document.querySelector('.workspace-content')?.getBoundingClientRect()
  const hero = document.querySelector('.workspace-hero')?.getBoundingClientRect()
  const balance = document.querySelector('.workspace-balance')?.getBoundingClientRect()
  const balanceAmount = document.querySelector('.workspace-balance strong')?.getBoundingClientRect()
  const page = document.querySelector('.workspace-page')
  const contentElement = document.querySelector('.workspace-content')
  const contentStyles = contentElement ? window.getComputedStyle(contentElement) : null
  const pageStyles = page ? window.getComputedStyle(page) : null
  const panels = [...document.querySelectorAll('.workspace-list-panel')]
  const primaryMetric = document.querySelector('.workspace-metrics .metric')
  const insightMetric = document.querySelector('.workspace-insight-strip .workspace-insight-item')
  const primaryMetricStyles = primaryMetric ? window.getComputedStyle(primaryMetric) : null
  const insightMetricStyles = insightMetric ? window.getComputedStyle(insightMetric) : null
  const workspaceKicker = document.querySelector('.workspace-kicker')
  const workspaceKickerStyles = workspaceKicker ? window.getComputedStyle(workspaceKicker) : null
  const visibleText = document.body.textContent ?? ''
  const hiddenAmountElements = [...document.querySelectorAll('.amount-mask')]
  const scrollElement = document.scrollingElement || document.documentElement
  return {
    viewportHeight: window.innerHeight,
    scrollHeight: scrollElement.scrollHeight,
    contentBottom: content?.bottom ?? 0,
    contentGap: contentStyles?.rowGap ?? '',
    contentPaddingTop: contentStyles ? Number.parseFloat(contentStyles.paddingTop) : 0,
    pagePaddingBottom: pageStyles ? Number.parseFloat(pageStyles.paddingBottom) : 0,
    recentRows: panels[0]?.querySelectorAll('.workspace-list-row').length ?? 0,
    dueRows: panels[1]?.querySelectorAll('.workspace-list-row').length ?? 0,
    dueMoreText: panels[1]?.querySelector('.workspace-more-row')?.textContent?.trim() ?? '',
    heroHeadingCount: document.querySelectorAll('.workspace-title-group h1').length,
    heroSubtitleText: document.querySelector('.workspace-title-group p')?.textContent?.trim() ?? '',
    transactionCountBadgeText: document.querySelector('.workspace-count-badge')?.textContent?.trim() ?? '',
    titleLineCountBadgeCount: document.querySelectorAll('.workspace-title-line .workspace-count-badge').length,
    balanceCountBadgeText: document.querySelector('.workspace-balance .workspace-count-badge')?.textContent?.trim() ?? '',
    balanceAmountRightGap: hero && balanceAmount ? Math.round(hero.right - balanceAmount.right) : null,
    balanceLabelLeft: balance ? Math.round(balance.left) : null,
    balanceAmountLeft: balanceAmount ? Math.round(balanceAmount.left) : null,
    insightCardCount: document.querySelectorAll('.workspace-insight-strip .workspace-insight-item.metric').length,
    insightCardMatchesMetric: Boolean(primaryMetricStyles && insightMetricStyles)
      && insightMetricStyles.minHeight === primaryMetricStyles.minHeight
      && insightMetricStyles.borderRadius === primaryMetricStyles.borderRadius
      && insightMetricStyles.backgroundImage === primaryMetricStyles.backgroundImage
      && insightMetricStyles.boxShadow === primaryMetricStyles.boxShadow,
    workspaceKickerFontSize: workspaceKickerStyles?.fontSize ?? '',
    privacyButtonText: document.querySelector('.workspace-privacy-button')?.textContent?.trim() ?? '',
    hiddenAmountCount: hiddenAmountElements.length,
    hiddenAmountTexts: hiddenAmountElements.map((element) => element.textContent?.trim() ?? ''),
    exposesPlainAmounts: ['¥9519.40', '¥3280.60', '¥12800.00', '¥123456.78', '¥109.35', '¥628.80', '-¥38.00', '-¥2600.00']
      .some((amount) => visibleText.includes(amount))
  }
})

await browser.close()

const hasRequestedPreviewCounts = metrics.recentRows === 4 && metrics.dueRows === 2
const indicatesHiddenDueRuns = metrics.dueMoreText.includes('还有 1 条') && metrics.dueMoreText.includes('查看全部')
const usesStandardPageSpacing = metrics.contentGap === '10px'
  && metrics.contentPaddingTop >= 12
  && metrics.pagePaddingBottom >= 90
const hasReworkedHeroCopy = metrics.heroHeadingCount === 0
  && !metrics.heroSubtitleText.includes('2026-06')
  && metrics.transactionCountBadgeText === '42 笔流水'
  && metrics.titleLineCountBadgeCount === 0
  && metrics.balanceCountBadgeText === '42 笔流水'
const hasRightAlignedBalance = visibleBalanceLayout.balanceAmountRightInset !== null
  && visibleBalanceLayout.balanceAmountRightInset <= 14
  && visibleBalanceLayout.balanceAmountLeft !== null
  && visibleBalanceLayout.balanceLabelLeft !== null
  && visibleBalanceLayout.balanceAmountLeft > visibleBalanceLayout.balanceLabelLeft

const hasMetricStyleInsights = metrics.insightCardCount === 3 && metrics.insightCardMatchesMetric
const hasReadableWorkspaceTitle = metrics.workspaceKickerFontSize === '18px'
const hidesSensitiveAmounts = metrics.privacyButtonText.includes('显示金额')
  && metrics.hiddenAmountCount >= 9
  && metrics.hiddenAmountTexts.every((text) => text === '****')
  && !metrics.exposesPlainAmounts

if (!hasRequestedPreviewCounts || !indicatesHiddenDueRuns || !usesStandardPageSpacing || !hasReworkedHeroCopy || !hasRightAlignedBalance || !visibleBalanceLayout.insightValuesFit || !hasMetricStyleInsights || !hasReadableWorkspaceTitle || !hidesSensitiveAmounts) {
  console.error(JSON.stringify(metrics, null, 2))
  throw new Error('工作台顶部信息或洞察金额展示不符合预期')
}

console.log(JSON.stringify(metrics, null, 2))
