# 日期时间选择器数字输入 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 保留日期时间选择器的小时、分钟滚轮，并允许用户点击当前选中值后通过数字键盘直接输入对应数值。

**Architecture:** 继续让 `ModernDateField.vue` 的 `tempTime` 作为唯一临时时间状态，通过时间滚轮根节点的点击事件识别用户点击的选中列。一个复用 `BottomSheet` 的列编辑器只维护输入草稿和错误，校验成功后再格式化写回 `tempTime`，主日期时间面板仍负责最终提交或取消。

**Tech Stack:** Vue 3 Composition API、TypeScript、Vant 4、Playwright、现有 `BottomSheet` 与 `ModernDateField` 组件。

## Global Constraints

- 小时有效范围为 `0–23`，分钟有效范围为 `0–59`，合法值确认后格式化为两位数。
- 输入为空、含非数字字符或越界时不关闭编辑弹窗、不修改滚轮值，并显示明确范围提示。
- 只有点击滚轮当前选中项才打开数字输入；滚动选择行为保持不变。
- 数字编辑成功后只更新临时时间，主日期时间面板的“确定”或“取消”语义保持不变。
- 数字输入弹窗必须复用 `BottomSheet`，输入字号不得低于 `16px`。
- 不影响 `date`、`month`、`year` 模式。
- 前端改动至少运行 `npm run check:ui` 和 `npm run build`。

---

### Task 1: 为时间滚轮增加单列数字编辑

**Files:**
- Modify: `frontend/tests/modern-date-field-ui.mjs`
- Modify: `frontend/src/components/ModernDateField.vue`
- Modify: `docs/frontend-ui-guidelines.md`

**Interfaces:**
- Consumes: `tempTime: Ref<string[]>`、`BottomSheet` 的 `v-model:show` 接口、Vant 时间滚轮生成的 `.van-picker-column` 和 `.van-picker-column__item--selected` DOM 语义。
- Produces: `openTimeInput(type: TimeColumnType)`、`onTimePickerClick(event: MouseEvent)`、`confirmTimeInput()` 和 `resetTimeInputEditor()`；测试可通过 `.modern-time-input-sheet`、`.modern-time-input`、`.modern-time-input-error` 定位交互。

- [ ] **Step 1: 写出合法输入、非法输入、取消与主面板回退的失败浏览器测试**

在 `frontend/tests/modern-date-field-ui.mjs` 已重新打开 `datetime` 面板并确认滚轮为 `18:38` 后加入以下行为断言：

```js
const selectedTimeItems = page.locator(
  '.modern-time-picker .van-picker-column__item--selected'
)

await selectedTimeItems.nth(0).click()
const timeInputSheet = page.locator('.modern-time-input-sheet')
const timeInput = timeInputSheet.locator('.modern-time-input')
await timeInputSheet.waitFor({ state: 'visible' })
assert.equal(await timeInput.inputValue(), '18')

await timeInput.fill('24')
await timeInputSheet.getByRole('button', { name: '确定', exact: true }).click()
await timeInputSheet.locator('.modern-time-input-error').waitFor()
assert.match(await timeInputSheet.locator('.modern-time-input-error').textContent(), /0.*23/)
assert.equal((await selectedTimeItems.nth(0).textContent())?.trim(), '18')

await timeInput.fill('6')
await timeInput.press('Enter')
await timeInputSheet.waitFor({ state: 'hidden' })
assert.equal((await selectedTimeItems.nth(0).textContent())?.trim(), '06')

await selectedTimeItems.nth(1).click()
await timeInput.fill('5')
await timeInputSheet.getByRole('button', { name: '取消', exact: true }).click()
await timeInputSheet.waitFor({ state: 'hidden' })
assert.equal((await selectedTimeItems.nth(1).textContent())?.trim(), '38')

await selectedTimeItems.nth(1).click()
await timeInput.fill('7')
await timeInput.press('Enter')
await timeInputSheet.waitFor({ state: 'hidden' })
assert.deepEqual(
  (await selectedTimeItems.allTextContents()).map((value) => value.trim()),
  ['06', '07']
)

await page.locator('button.modern-date-text-button').filter({ hasText: '取消' }).click()
assert.equal(await timeCell.locator('input').inputValue(), '2026年08月02日 18:38')
```

在夹具的 `date`、`month`、`year` 模式循环结束后断言没有数字编辑弹窗残留：

```js
assert.equal(await page.locator('.modern-time-input-sheet').count(), 0)
```

- [ ] **Step 2: 运行日期时间选择器测试并确认因功能缺失而失败**

Run: `cd frontend && npm run test:modern-date-field`

Expected: FAIL，首个失败点为点击选中小时后找不到可见的 `.modern-time-input-sheet`，证明测试覆盖了尚未实现的数字编辑能力。

- [ ] **Step 3: 在组件中加入编辑状态、列识别与严格校验**

在 `ModernDateField.vue` 中引入 `nextTick`，并增加以下状态与接口：

```ts
const timeInputVisible = ref(false)
const timeInputType = ref<TimeColumnType>('hour')
const timeInputDraft = ref('')
const timeInputError = ref('')
const timeInputElement = ref<HTMLInputElement>()

const timeInputTitle = computed(() => timeInputType.value === 'hour' ? '输入小时' : '输入分钟')
const timeInputLabel = computed(() => timeInputType.value === 'hour' ? '小时' : '分钟')

async function openTimeInput(type: TimeColumnType) {
  timeInputType.value = type
  timeInputDraft.value = tempTime.value[type === 'hour' ? 0 : 1]
  timeInputError.value = ''
  timeInputVisible.value = true
  await nextTick()
  timeInputElement.value?.focus()
  timeInputElement.value?.select()
}

function onTimePickerClick(event: MouseEvent) {
  const target = event.target
  const picker = event.currentTarget
  if (!(target instanceof Element) || !(picker instanceof HTMLElement)) return
  const item = target.closest('.van-picker-column__item--selected')
  const column = target.closest('.van-picker-column')
  if (!item || !column) return
  const columnIndex = Array.from(picker.querySelectorAll('.van-picker-column')).indexOf(column)
  if (columnIndex === 0 || columnIndex === 1) {
    void openTimeInput(timeColumns[columnIndex])
  }
}

function resetTimeInputEditor() {
  timeInputDraft.value = ''
  timeInputError.value = ''
}

function cancelTimeInput() {
  timeInputVisible.value = false
}

function confirmTimeInput() {
  const max = timeInputType.value === 'hour' ? 23 : 59
  if (!/^\d{1,2}$/.test(timeInputDraft.value)) {
    timeInputError.value = `请输入 0–${max} 的${timeInputLabel.value}`
    return
  }
  const value = Number(timeInputDraft.value)
  if (value < 0 || value > max) {
    timeInputError.value = `请输入 0–${max} 的${timeInputLabel.value}`
    return
  }
  const nextTime = [...tempTime.value]
  nextTime[timeInputType.value === 'hour' ? 0 : 1] = two(value)
  tempTime.value = nextTime
  timeInputVisible.value = false
}
```

`open()` 和主面板关闭路径调用 `resetTimeInputEditor()`，保证下一次编辑没有旧草稿或错误。

- [ ] **Step 4: 渲染复用 BottomSheet 的数字输入界面并保持滚轮手势**

给现有 `van-time-picker` 增加 `@click="onTimePickerClick"`。在主 `BottomSheet` 后渲染独立编辑面板：

```vue
<BottomSheet
  v-model:show="timeInputVisible"
  :title="timeInputTitle"
  header-variant="toolbar"
  sheet-class="modern-time-input-sheet"
  @closed="resetTimeInputEditor"
>
  <template #leading>
    <button type="button" class="modern-date-text-button" @click="cancelTimeInput">取消</button>
  </template>
  <template #actions>
    <button type="button" class="modern-date-text-button primary" @click="confirmTimeInput">确定</button>
  </template>
  <label class="modern-time-input-field">
    <span>{{ timeInputLabel }}</span>
    <input
      ref="timeInputElement"
      v-model="timeInputDraft"
      class="modern-time-input"
      type="text"
      inputmode="numeric"
      pattern="[0-9]*"
      maxlength="2"
      :aria-invalid="Boolean(timeInputError)"
      @input="timeInputError = ''"
      @keydown.enter.prevent="confirmTimeInput"
    >
  </label>
  <p v-if="timeInputError" class="modern-time-input-error" role="alert">{{ timeInputError }}</p>
</BottomSheet>
```

添加局部样式，使 `.modern-time-input` 的 `font-size` 固定为 `16px`，输入区使用现有颜色、边框、圆角与间距 token；为选中滚轮项增加 `cursor: text`，不添加覆盖层，从而不阻断原有触摸滚动。

- [ ] **Step 5: 补充全站日期时间入口规范**

在 `docs/frontend-ui-guidelines.md` 的日期时间选择器条目补充：

```markdown
`datetime` 的小时、分钟滚轮选中项支持点击后直接数字输入；小时范围为 0–23，分钟范围为 0–59，输入成功后与滚轮双向同步。
```

- [ ] **Step 6: 运行针对性测试并确认通过**

Run: `cd frontend && npm run test:modern-date-field`

Expected: PASS，并输出 `日期时间选择器移动端回归通过`。

- [ ] **Step 7: 运行前端规范检查、类型检查和生产构建**

Run: `cd frontend && npm run check:ui && npm run type-check && npm run build`

Expected: 全部命令退出码为 `0`；Vite 完成生产构建，TypeScript 无错误。

- [ ] **Step 8: 检查差异并提交实现**

Run: `git diff --check && git status --short`

Expected: 只有 `ModernDateField.vue`、`modern-date-field-ui.mjs` 和 `frontend-ui-guidelines.md` 三个实现相关文件发生改动，且无空白错误。

```bash
git add frontend/src/components/ModernDateField.vue frontend/tests/modern-date-field-ui.mjs docs/frontend-ui-guidelines.md
git commit -m "功能：支持时间滚轮数字输入"
```
