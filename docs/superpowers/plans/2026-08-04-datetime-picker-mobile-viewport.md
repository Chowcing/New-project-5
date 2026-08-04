# 日期时间选择器移动端同屏适配实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 `ModernDateField` 的日期时间弹窗在 390 × 667 等常见小屏视口中完整展示月历与时间滚轮，并让“今天”同步选择点击当下的本地时分。

**Architecture:** 在 `calendarPicker` 中提供可注入固定时刻的本地日期时间拆分函数；在 `BottomSheet` 中增加默认关闭的动态视口高度变体；由 `ModernDateField` 的 `datetime` 模式独占启用该变体、三行时间滚轮和矮屏紧凑月历。行为用确定性工具测试覆盖，布局用真实 Chromium 小屏回归覆盖。

**Tech Stack:** Vue 3、TypeScript、Vant 4.9.24、CSS dynamic viewport units、Node.js assertion tests、Playwright 1.60、Vite 8。

## Global Constraints

- 只调整 `datetime` 模式；`date`、`month`、`year` 的视觉密度和普通 `BottomSheet` 默认行为保持不变。
- 390 × 667 视口必须同屏显示顶部工具栏、六周月历、“今天”和时间滚轮。
- 时间滚轮固定为 3 个可见选项，每项保持 44px 高度。
- 视口高度不超过 740px 时，日期格高度为 40px，并压缩月历纵向间距。
- 底部留白使用 `max(var(--space-18), env(safe-area-inset-bottom))`；不能依赖安全区一定非零。
- `datetime` 模式点击“今天”同步点击当下的日期、小时和分钟；`date` 模式仍只输出日期。
- 点击“确定”才更新表单，点击“取消”不修改原值。
- 不新增第三方依赖，不改变 API payload、日期格式或交易业务规则。
- 手工编辑使用 `apply_patch`；不回滚、覆盖或清理无关本地改动。
- 每次提交使用中文提交信息，只暂存当前任务明确列出的文件。

---

## 文件职责与修改边界

- `frontend/src/utils/calendarPicker.ts`：唯一负责把本地 `Date` 拆为 `DateParts`，并继续提供日期格式化与月历计算。
- `frontend/tests/calendar-picker.mjs`：日期时间拆分和格式化的确定性测试。
- `frontend/src/components/BottomSheet.vue`：提供通用但默认关闭的 `heightVariant="viewport"`；同时控制 popup 外层和 sheet 内层高度上限。
- `frontend/src/components/ModernDateField.vue`：决定何时启用 viewport 变体、同步“今天”时分、配置三行时间滚轮和 datetime 专属紧凑样式。
- `frontend/tests/modern-date-field-ui.mjs`：在真实 Chromium 中验证 390 × 667 布局和“今天”交互。
- `frontend/package.json`：暴露日期时间选择器专项回归命令。
- `docs/frontend-ui-guidelines.md`：记录日期时间弹窗的小屏、安全区与“今天”行为规范。

### Task 1: 可测试的本地日期时间快照

**Files:**
- Modify: `frontend/src/utils/calendarPicker.ts:36-55`
- Modify: `frontend/tests/calendar-picker.mjs:13-55`

**Interfaces:**
- Produces: `localDateTimeParts(now?: Date): DateParts`
- Produces: `todayValue(now?: Date): string`
- Consumes: 现有 `DateParts`、`dateValue()`。

- [ ] **Step 1: 写入失败的固定时刻测试**

在 `frontend/tests/calendar-picker.mjs` 的动态导入解构中加入 `localDateTimeParts` 和 `todayValue`，并在现有格式化断言前加入：

```js
const fixedNow = new Date(2026, 7, 4, 9, 7, 45)
assert.deepEqual(localDateTimeParts(fixedNow), {
  year: 2026,
  month: 8,
  day: 4,
  hour: 9,
  minute: 7
})
assert.equal(todayValue(fixedNow), '2026-08-04')
```

- [ ] **Step 2: 运行测试并确认先失败**

Run: `cd frontend && node tests/calendar-picker.mjs`

Expected: FAIL，动态导入对象中 `localDateTimeParts` 不存在，调用时出现 `TypeError: localDateTimeParts is not a function`。

- [ ] **Step 3: 实现最小日期时间拆分函数**

在 `dateValue()` 后加入：

```ts
export function localDateTimeParts(now = new Date()): DateParts {
  return {
    year: now.getFullYear(),
    month: now.getMonth() + 1,
    day: now.getDate(),
    hour: now.getHours(),
    minute: now.getMinutes()
  }
}
```

把 `todayValue` 改为复用同一个快照入口：

```ts
export function todayValue(now = new Date()) {
  return dateValue(localDateTimeParts(now))
}
```

- [ ] **Step 4: 运行日期工具测试并确认通过**

Run: `cd frontend && node tests/calendar-picker.mjs`

Expected: PASS，进程退出码为 0。

- [ ] **Step 5: 提交日期工具变更**

```bash
git add frontend/src/utils/calendarPicker.ts frontend/tests/calendar-picker.mjs
git commit -m "功能：支持获取本地日期时间快照"
```

### Task 2: 日期时间弹窗同屏布局与“今天”交互

**Files:**
- Create: `frontend/tests/modern-date-field-ui.mjs`
- Modify: `frontend/package.json:5-16`
- Modify: `frontend/src/components/BottomSheet.vue:10-27,47-94,177-182`
- Modify: `frontend/src/components/ModernDateField.vue:6-19,54-107,216-228,272-377,404-548`

**Interfaces:**
- Consumes: `localDateTimeParts(now?: Date): DateParts` from Task 1。
- Produces: `BottomSheet` prop `heightVariant?: 'default' | 'viewport'`，默认值 `'default'`。
- Produces: CSS classes `bottom-sheet-popup--viewport`、`bottom-sheet--viewport`、`modern-calendar--datetime`、`modern-date-body--datetime`。
- Produces: npm script `test:modern-date-field`。

- [ ] **Step 1: 创建失败的浏览器行为与布局回归**

创建 `frontend/tests/modern-date-field-ui.mjs`。测试固定浏览器时钟，使用草稿直接进入第 3 步，避免测试依赖手工填写前两步：

```js
import assert from 'node:assert/strict'
import { chromium } from 'playwright'
import { withViteServer } from './helpers/vite-test-server.mjs'

const api = (data) => ({ success: true, message: 'ok', data })
const fixedNow = new Date(2026, 7, 4, 9, 7)
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
    await page.clock.setFixedTime(fixedNow)
    await page.route('**/api/v1/**', (route) => {
      const pathname = new URL(route.request().url()).pathname
      return route.fulfill({ json: api(responseData(pathname)) })
    })
    await page.goto(new URL('/quick-add?type=EXPENSE', baseUrl).toString())
    await page.getByRole('button', { name: '继续填写', exact: true }).click()

    const timeCell = page.locator('.quick-extra-panel .van-cell').filter({ hasText: '时间' })
    await timeCell.click()
    await page.getByRole('button', { name: '今天', exact: true }).click()

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

    await page.locator('button.modern-date-text-button.primary').click()
    await page.getByText('2026年08月04日 09:07', { exact: true }).waitFor()

    await page.goto(new URL('/export', baseUrl).toString())
    const startDateCell = page.locator('.van-cell').filter({ hasText: '开始' })
    await startDateCell.click()
    assert.equal(await page.locator('.bottom-sheet-popup--viewport').count(), 0)
    assert.equal(await page.locator('.modern-time-picker').count(), 0)
    await page.getByRole('button', { name: '今天', exact: true }).click()
    await page.locator('button.modern-date-text-button.primary').click()
    assert.match(await startDateCell.innerText(), /2026年08月04日/)
    assert.doesNotMatch(await startDateCell.innerText(), /09:07/)
  } finally {
    await context.close()
    await browser.close()
  }
})

console.log('日期时间选择器移动端回归通过')
```

在 `frontend/package.json` 的 scripts 中加入：

```json
"test:modern-date-field": "node tests/calendar-picker.mjs && node tests/modern-date-field-ui.mjs"
```

- [ ] **Step 2: 运行专项回归并确认现有实现失败**

Run: `cd frontend && npm run test:modern-date-field`

Expected: `calendar-picker.mjs` 通过；浏览器回归至少因找不到 `.bottom-sheet-popup--viewport`、时间滚轮仍为 264px 或点击“今天”后仍显示旧时间而失败。

- [ ] **Step 3: 为 BottomSheet 增加默认关闭的 viewport 高度变体**

在 props 中加入并设置默认值：

```ts
heightVariant?: 'default' | 'viewport'
```

```ts
heightVariant: 'default'
```

把 popup 和 sheet 的 class 绑定改为：

```vue
:class="['bottom-sheet-popup', `bottom-sheet-popup--${heightVariant}`]"
```

```vue
<section :class="['bottom-sheet', `bottom-sheet--${heightVariant}`, sheetClass]">
```

在 `BottomSheet.vue` scoped 样式中加入 vh 回退和 dvh 优先规则：

```css
.bottom-sheet-popup--viewport,
.bottom-sheet--viewport {
  max-height: calc(100vh - max(var(--space-8), env(safe-area-inset-top)));
}

@supports (height: 100dvh) {
  .bottom-sheet-popup--viewport,
  .bottom-sheet--viewport {
    max-height: calc(100dvh - max(var(--space-8), env(safe-area-inset-top)));
  }
}
```

不要修改 `.bottom-sheet` 和全局 `.van-popup--bottom` 的默认上限；viewport 类的同等或更高特异性只覆盖显式使用方。

- [ ] **Step 4: 实现 datetime 专属类名、三行时间滚轮和“今天”当前时分**

从 `calendarPicker` 新增导入：

```ts
dateValue,
localDateTimeParts,
```

抽出日期可选判断，使点击处理能够使用同一次捕获的当前时刻：

```ts
function canChooseDate(value: string) {
  if (props.availableDates?.length && !props.availableDates.includes(value)) {
    return false
  }
  const clamped = formatDateParts(
    clampDateParts(parseDateParts(value), resolvedMinDate.value, resolvedMaxDate.value),
    'date'
  )
  return clamped === value
}

const canChooseToday = computed(() => canChooseDate(todayDate.value))
```

把 `chooseToday()` 改为：

```ts
function chooseToday() {
  const nowParts = localDateTimeParts()
  const nowDate = dateValue(nowParts)
  if (!canChooseDate(nowDate)) return

  const todayParts = clampDateParts(nowParts, resolvedMinDate.value, resolvedMaxDate.value)
  hapticSelection()
  tempParts.value = {
    ...tempParts.value,
    ...todayParts
  }
  if (props.mode === 'datetime') {
    tempTime.value = [two(nowParts.hour ?? 0), two(nowParts.minute ?? 0)]
  }
  viewYear.value = todayParts.year
  viewMonth.value = todayParts.month
}
```

更新 `BottomSheet` 和月历 class：

```vue
<BottomSheet
  v-model:show="visible"
  :title="sheetTitle"
  header-variant="toolbar"
  :height-variant="mode === 'datetime' ? 'viewport' : 'default'"
  :sheet-class="[
    visualFeedback ? `ui-feedback-${visualFeedback}` : '',
    { 'modern-date-sheet--datetime': mode === 'datetime' }
  ]"
  :body-class="[
    'modern-date-body',
    { 'modern-date-body--datetime': mode === 'datetime' }
  ]"
>
```

```vue
<div :class="['modern-calendar', { 'modern-calendar--datetime': mode === 'datetime' }]">
```

时间滚轮明确配置三行和 44px：

```vue
<van-time-picker
  v-if="mode === 'datetime'"
  :model-value="tempTime"
  :columns-type="timeColumns"
  :show-toolbar="false"
  :visible-option-num="3"
  :option-height="44"
  class="modern-time-picker"
  @update:model-value="onTimeUpdate"
/>
```

- [ ] **Step 5: 加入 740px 以下的紧凑月历规则**

在 `ModernDateField.vue` scoped 样式末尾加入：

```css
@media (max-height: 740px) {
  .modern-calendar--datetime {
    gap: var(--space-6);
    padding: var(--space-8) var(--space-12);
  }

  .modern-calendar--datetime .modern-calendar-weekdays,
  .modern-calendar--datetime .modern-calendar-days {
    gap: var(--space-4) var(--space-5);
  }

  .modern-calendar--datetime .modern-calendar-day {
    min-height: 40px;
  }
}
```

保留 `.modern-date-body` 当前的左右零 padding 和默认 `BottomSheet` 提供的 `max(var(--space-18), env(safe-area-inset-bottom))` 底部 padding。

- [ ] **Step 6: 运行专项回归并修正到通过**

Run: `cd frontend && npm run test:modern-date-field`

Expected: 输出 `日期时间选择器移动端回归通过`，进程退出码为 0；390 × 667 下 `bodyScrollHeight <= bodyClientHeight + 1`，时间列高度为 132px，日期时间确认后显示 `2026年08月04日 09:07`；纯日期弹窗不启用 viewport 变体且不出现时间滚轮。

- [ ] **Step 7: 运行静态检查和构建**

Run: `cd frontend && npm run check:ui`

Expected: PASS。

Run: `cd frontend && npm run build`

Expected: `vue-tsc --noEmit` 和 Vite production build 均 PASS。

- [ ] **Step 8: 提交组件与回归测试**

```bash
git add frontend/src/components/BottomSheet.vue frontend/src/components/ModernDateField.vue frontend/tests/modern-date-field-ui.mjs frontend/package.json
git commit -m "修复：优化日期时间选择器移动端同屏布局"
```

### Task 3: 更新 UI 规范并完成全量验收

**Files:**
- Modify: `docs/frontend-ui-guidelines.md:88-96,128-139`

**Interfaces:**
- Consumes: Task 2 的 `heightVariant="viewport"`、三行时间滚轮和 datetime 专属紧凑布局。
- Produces: 后续日期时间弹窗改动必须遵守的项目级 UI 规则。

- [ ] **Step 1: 更新日期时间选择器规范**

在 `ModernDateField` 组件规则后补充：

```markdown
- `datetime` 弹窗在常见小屏中必须让顶部工具栏、六周月历、“今天”和时间滚轮同屏可用；使用 `BottomSheet` 的 viewport 高度变体和 3 行时间滚轮，不要依赖 `safe-area-inset-bottom` 一定非零。
- `datetime` 模式点击“今天”应同时更新为点击当下的本地小时和分钟；`date` 模式仍只选择日期，二者都在点击“确定”后才写回表单。
```

在 UI 检查清单中加入：

```markdown
- 日期时间弹窗是否在 390 × 667 视口中完整显示时间滚轮并保留底部安全间距，“今天”是否同步当前时分。
```

- [ ] **Step 2: 运行最终验证**

Run: `cd frontend && npm run test:modern-date-field`

Expected: PASS。

Run: `cd frontend && npm run check:ui`

Expected: PASS。

Run: `cd frontend && npm run build`

Expected: PASS。

- [ ] **Step 3: 检查变更边界**

Run: `git status --short`

Expected: 只看到本任务的 `docs/frontend-ui-guidelines.md` 未提交，以及用户原有的无关改动；不得暂存无关文件。

Run: `git diff --check`

Expected: 无空白错误。

- [ ] **Step 4: 提交规范更新**

```bash
git add docs/frontend-ui-guidelines.md
git commit -m "文档：补充日期时间选择器移动端规范"
```

- [ ] **Step 5: 完成前复核**

运行 `superpowers:verification-before-completion`，再次确认最新测试输出、最终 `git status --short` 和提交范围；只有实际验证通过后才能汇报完成。
