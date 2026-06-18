# 前端 UI 设计规范

本文档用于统一当前前端界面风格，并约束后续 UI 改动。适用范围包括 `frontend/src/views/`、`frontend/src/components/`、`frontend/src/styles/main.css` 和 `frontend/src/utils/themes.ts`。

## 1. 设计方向

- 项目采用移动端优先的日常消费记录界面，优先保证手机端单手操作、信息密度和录入效率。
- 基础风格延续现有 iOS 科技风：暗色优先、浅色可用、玻璃拟态卡片、柔和阴影、底部导航、底部弹窗。
- 不为单个页面单独创建一套视觉语言。新增页面应复用全局 token、公共类和已有组件。
- 管理端页面可以更偏数据工具，但仍应复用相同的颜色、字号、圆角、按钮和弹窗规则。

## 2. Token 来源

UI 基础值只能从两个地方来：

- `frontend/src/styles/main.css`：字号、行高、间距、圆角、阴影、动效、Vant 变量覆盖、通用布局类。
- `frontend/src/utils/themes.ts`：明暗主题、主题强调色、收入/支出语义色、图表色板。

新增或调整通用视觉值时，优先修改 token，不要在单个 `.vue` 文件里硬编码孤立值。

### 颜色

- 主色使用 `--primary`、`--primary-deep`、`--primary-soft`。
- 支出使用 `--expense`、`--expense-soft`。
- 收入使用 `--income`、`--income-soft`。
- 文本使用 `--text-main`、`--text-secondary`、`--text-muted`。
- 边框和阴影使用 `--border-warm`、`--theme-border-warm-rgb`、`--shadow-warm`、`--theme-shadow-warm-rgb`。
- 避免在业务页面新增裸色值，例如 `#38bdf8`、`rgba(148, 163, 184, 0.2)`。确需新增语义色时，先放入 token。

### 字号和行高

- 正文与输入控件使用 `--font-size-body` 或 `--font-size-section-title`。
- 辅助信息使用 `--font-size-xs`、`--font-size-caption` 或 `--font-size-meta`。
- 面板标题使用 `--font-size-panel-title`。
- 页面标题使用 `--font-size-page-title`。
- 金额展示使用 `--font-size-amount` 或 `--font-size-amount-large`。
- 所有可聚焦输入控件实际字号不得低于 16px，避免 iOS Safari 弹出键盘后自动放大页面。

### 间距、圆角、阴影和动效

- 间距使用 `--space-*`。
- 卡片使用 `--radius-card`，浮层和按钮可使用 `--radius-floating`、`--radius-sheet`、`--radius-pill`。
- 阴影使用 `--shadow-sm`、`--shadow-md`、`--shadow-lg` 或 `--shadow-warm`。
- 点击反馈使用现有 `--motion-*` 和 `ui-feedback-*` 类。
- 不新增随意数字，例如 `padding: 17px`、`border-radius: 19px`，除非是为了修正组件库结构并有明确局部原因。

## 3. 页面结构

普通移动端页面应优先使用以下结构：

```vue
<template>
  <main class="page">
    <van-nav-bar title="页面标题" />
    <div class="page-content">
      <section class="section">
        <div class="section-heading">
          <span>区域标题</span>
        </div>
        <div class="panel">
          <!-- 内容 -->
        </div>
      </section>
    </div>
  </main>
</template>
```

- 页面根容器使用 `.page`，认证页使用 `.auth-page`。
- 主内容使用 `.page-content`，区域使用 `.section`。
- 需要玻璃卡片时使用 `.panel` 或已有页面组件，不要每个页面重写卡片样式。
- 列表、指标、筛选、空态、底部操作栏应优先复用已有类或组件。

## 4. 组件使用

- 基础移动端组件优先使用 Vant。
- 按钮、入口、导航、状态和信息行尽可能使用“图标 + 文本”。
- 关键操作避免只显示图标。纯图标按钮必须有 `aria-label` 或 `title`。
- 同类操作的图标语义保持一致，例如编辑用 `edit`，删除用 `delete-o`，新增用 `plus`，确认用 `success`。
- 选择器类弹窗统一使用：

```vue
<van-popup v-model:show="visible" position="bottom" round teleport="body">
  <!-- 内容 -->
</van-popup>
```

- 弹窗头部优先使用 `.popup-header`、`.popup-title`、`.popup-subtitle`、`.popup-close`。
- 需要新增可复用结构时，优先放入 `frontend/src/components/`，避免在多个页面复制样式。

## 5. 表单和交互

- “记一笔”和编辑记录表单保持高频顺序：类型、金额、事项、分类、支付方式。
- 低频字段放补充区，保存操作使用底部固定操作栏。
- 金额输入展示应带人民币符号 `¥`，接口 payload 仍只传数字金额。
- 日期时间向用户展示时使用中文友好格式，例如 `2026年06月05日 10:29`，不要直接展示 `datetime-local` 的 `T` 分隔格式。
- 页面首屏应优先呈现可操作内容，避免大段说明文本挤占移动端空间。
- 流水记录左滑优先交给 `van-swipe-cell`，日期横滑只作用于非记录行区域。

## 6. 主题和偏好

- 主题偏好统一使用 `appearance` 和 `accent`。
- `appearance` 支持 `system`、`light`、`dark`。
- `accent` 支持 `cyan`、`blue`、`violet`。
- 偏好存储在 `localStorage` 的 `expense.preferences` 中。
- 新增主题相关能力时，需要同步检查 `frontend/src/utils/themes.ts`、`frontend/src/utils/preferences.ts` 和设置页。
- UI 改动必须同时考虑浅色和深色主题，不得只在一种主题下可读。

## 7. 禁止事项

- 不要在页面内散落新增颜色、字号、圆角、阴影和动效常量。
- 不要为同类卡片、筛选条、弹窗、空态重复写多套视觉样式。
- 不要把选择器弹窗放在页面容器内部受布局影响的位置。
- 不要使用低于 16px 的聚焦输入字号。
- 不要新增只有图标且无可访问名称的关键按钮。
- 不要为了单页效果破坏四个底部 Tab 和全局浮动记账入口的主导航结构。

## 8. UI 改动检查清单

每次涉及前端 UI 的改动，提交前检查：

- 是否优先复用了 `main.css` 和 `themes.ts` 中的 token。
- 是否复用了已有 Vant 组件、公共类或 `frontend/src/components/` 组件。
- 是否避免了重复造卡片、弹窗、筛选和空态样式。
- 是否在 320px 宽度下不溢出、不遮挡、不裁切关键文字。
- 是否在浅色和深色主题下都可读。
- 选择器弹窗是否使用 `position="bottom"` 和 `teleport="body"`。
- 聚焦输入控件实际字号是否不低于 16px。
- 纯图标按钮是否有 `aria-label` 或 `title`。
- 前端改动是否已运行 `cd frontend; npm run build`。

## 9. 后续治理顺序

优先按以下顺序治理现有不统一样式：

1. 收敛全局 token，减少孤立硬编码。
2. 抽取高频复用组件，例如页面骨架、面板、区域标题、底部弹窗、空态、指标卡、筛选条、底部操作栏。
3. 优先治理 `QuickAddView.vue`、`RecordsView.vue`、`TransactionDetailView.vue`、`CategoriesView.vue`、`PaymentMethodsView.vue`。
4. 每次只治理一个页面或一组同类组件，避免一次性重构全站。
