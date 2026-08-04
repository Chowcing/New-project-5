# 流水详情操作区视觉优化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将流水详情页四宫格操作按钮替换为一个编辑主按钮和一个三行设置列表，同时保持原有操作行为与移动端可用性。

**Architecture:** 仅调整 `TransactionDetailView.vue` 查看态的模板与局部样式，继续复用现有四个事件处理函数和 Vant 按钮加载态。浏览器回归测试通过真实 DOM 布局尺寸验证操作纵向排列、触控高度、视口边界和回收站流程，不引入新组件或新数据流。

**Tech Stack:** Vue 3、TypeScript、Vant 4、CSS tokens、Playwright、Node.js `assert`。

## Global Constraints

- 复用 `frontend/src/styles/main.css` 和主题系统已有 token，不新增裸颜色、孤立字号、圆角或间距。
- 保留“图标 + 文本”与明确的可访问名称。
- 保留 `startEdit`、`copyRecord`、`createRecurringRule`、`removeRecord` 及其加载、确认和路由行为。
- 320px 宽度下不得横向溢出、裁切文字或缩小触控区域。
- 不修改后端、接口类型、业务数据、编辑表单或其他页面。

---

### Task 1: 将详情操作区改为设置列表

**Files:**
- Modify: `frontend/tests/recycle-bin-ui.mjs:850-945`
- Modify: `frontend/src/views/TransactionDetailView.vue:901-922,1759-1778`

**Interfaces:**
- Consumes: 现有 `startEdit(): void`、`copyRecord(): Promise<void>`、`createRecurringRule(): void`、`removeRecord(): Promise<void>`，以及 `optionsLoading`、`copying`、`deleting` 响应式状态。
- Produces: 查看态中的 `.detail-edit-action` 全宽主按钮和 `.detail-action-list` 三行操作容器；四项操作的可访问名称保持不变。

- [ ] **Step 1: 写入会失败的真实浏览器布局测试**

在 `verifyDetailMoveToTrash` 前新增：

```js
async function verifyDetailActionListLayout(page, baseUrl) {
  await page.setViewportSize({ width: 320, height: 844 })
  await page.goto(new URL('/records/88', baseUrl).toString())

  const names = ['编辑记录', '复制为今日', '设为周期', '移入回收站']
  const buttons = names.map((name) => page.getByRole('button', { name, exact: true }))
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
  }

  const editWidth = boxes[0].width
  const actionListWidth = await page.locator('.detail-action-list').evaluate((element) => element.getBoundingClientRect().width)
  assert.ok(Math.abs(editWidth - actionListWidth) <= 1, '编辑主按钮与操作列表宽度不一致')
}
```

并在用例注册区、“详情移入回收站”之前新增：

```js
await runCase('详情操作列表纵向布局与窄屏边界', verifyDetailActionListLayout)
```

- [ ] **Step 2: 运行测试并确认按预期失败**

Run: `cd frontend && npm run test:recycle-bin-ui`

Expected: FAIL，失败信息为“复制为今日 未纵向排列”或找不到 `.detail-action-list`；现有四宫格实现不能满足纵向列表契约。

- [ ] **Step 3: 写入最小模板实现**

将 `.detail-main-actions` 替换为：

```vue
<div class="detail-main-actions">
  <van-button
    class="detail-edit-action"
    block
    round
    type="primary"
    icon="edit"
    :loading="optionsLoading"
    @click="startEdit"
  >
    编辑记录
  </van-button>
  <div class="detail-action-list">
    <van-button class="detail-action-row" block icon="description-o" :loading="copying" @click="copyRecord">
      <span>复制为今日</span>
      <van-icon class="detail-action-arrow" name="arrow" />
    </van-button>
    <van-button class="detail-action-row" block icon="replay" @click="createRecurringRule">
      <span>设为周期</span>
      <van-icon class="detail-action-arrow" name="arrow" />
    </van-button>
    <van-button class="detail-action-row danger" block icon="delete-o" :loading="deleting" @click="removeRecord">
      <span>移入回收站</span>
      <van-icon class="detail-action-arrow" name="arrow" />
    </van-button>
  </div>
</div>
```

- [ ] **Step 4: 写入最小 token 化样式**

用以下职责明确的样式替换原两列按钮样式；实现时根据 Vant 实际 DOM 保持选择器最小化：

```css
.detail-main-actions {
  display: grid;
  gap: var(--space-10);
}

.detail-edit-action,
.detail-action-row {
  min-height: 48px;
}

.detail-edit-action {
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-primary-sm);
}

.detail-action-list {
  overflow: hidden;
  border: 1px solid rgba(var(--theme-border-warm-rgb), 0.16);
  border-radius: var(--radius-card);
  background: rgba(var(--theme-border-warm-rgb), 0.06);
}

.detail-action-row {
  border: 0;
  border-radius: 0;
  background: transparent;
  color: var(--text-main);
}

.detail-action-row + .detail-action-row {
  border-top: 1px solid rgba(var(--theme-border-warm-rgb), 0.14);
}

.detail-action-row.danger {
  color: var(--expense);
}

.detail-action-row :deep(.van-button__content) {
  justify-content: flex-start;
  width: 100%;
}

.detail-action-row :deep(.van-button__text) {
  display: flex;
  flex: 1;
  align-items: center;
  min-width: 0;
  text-align: left;
}

.detail-action-arrow {
  margin-left: auto;
  color: var(--text-muted);
}

.detail-action-row.danger .detail-action-arrow {
  color: var(--expense);
}
```

- [ ] **Step 5: 运行目标测试并确认通过**

Run: `cd frontend && npm run test:recycle-bin-ui`

Expected: PASS，包括“详情操作列表纵向布局与窄屏边界”和“详情移入回收站”。

- [ ] **Step 6: 运行前端完整要求验证**

Run: `cd frontend && npm run check:ui && npm run build`

Expected: 所有 UI 规则、TypeScript 检查和 Vite 构建通过，无新增警告或错误。

- [ ] **Step 7: 在实际页面复查 491px 与 320px 视口**

使用当前本地页面 `/records/136?startDate=2026-07-01&endDate=2026-07-27&activeDate=2026-07-27` 检查：编辑按钮全宽，三个列表项纵向排列，箭头右对齐，回收站行保持克制的红色语义；随后切换到 320px 宽度确认无横向溢出或文字裁切。

- [ ] **Step 8: 检查差异并提交**

Run: `git diff --check && git status --short`

Expected: 仅包含 `frontend/src/views/TransactionDetailView.vue` 和 `frontend/tests/recycle-bin-ui.mjs` 的预期改动，无空白错误。

```bash
git add frontend/src/views/TransactionDetailView.vue frontend/tests/recycle-bin-ui.mjs
git commit -m "优化：调整流水详情操作区样式"
```
