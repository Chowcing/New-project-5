# 日期时间选择器移动端同屏适配最终修复报告

## 结论

最终审查的 2 个 Important 和 1 个 Minor finding 均已按真实浏览器行为完成独立 RED/GREEN。生产修改只涉及 `ModernDateField.vue`：恢复默认字段完整触发热区，并在每次打开时刷新“今天”。专项测试改用明确的东八区时间戳，并新增仅用于浏览器回归的四模式夹具。

## Finding 映射

### Important 1：默认触发器热区缩小

- 根因：默认 `van-field` 只监听 `click-input`，标签、右箭头和单元格留白不会统一调用 `open()`。
- RED：先新增真实 Chromium 四模式回归，分别点击 `date`/`month` 的标签以及 `datetime`/`year` 的右箭头。
  - 命令：`cd frontend && TZ=UTC npm run test:modern-date-field`
  - 关键输出：`locator.waitFor: Timeout 2000ms exceeded`，等待标题 `选择日期` 可见失败。
  - 说明：首次夹具尝试曾因 Vue runtime compiler 不支持内联模板而无法挂载；修正为真实 `.vue` 夹具后重新运行，以上才是计入的有效 RED。
- 实现：默认字段恢复 `@click="open"`，同时显式监听 `click-input` 和 `click-right-icon` 并停止继续冒泡，保证整行、输入区和右箭头都能打开且不重复触发。
- GREEN：同一命令退出码 0，输出 `日期时间选择器移动端回归通过`。QuickAdd 整行、Export 右箭头以及夹具内 `date`、`datetime`、`month`、`year` 四种默认模式均通过。

### Important 2：宿主时区与浏览器时区不一致

- 根因：Node 端 `new Date(2026, 7, 4, 9, 7)` 按宿主本地时区构造，而 Chromium 上下文固定为 `Asia/Shanghai`。
- RED：在未修改测试时钟前运行 `cd frontend && TZ=UTC npm run test:modern-date-field`。
  - 关键输出：专项回归退出码 1；实际选中 `['17', '07']`，期望 `['09', '07']`。
- 实现：把固定时刻改为 `new Date('2026-08-04T09:07:00+08:00')`，使测试输入不依赖宿主时区。
- GREEN：`TZ=UTC` 下专项回归退出码 0，输出 `日期时间选择器移动端回归通过`。

### Minor 1：跨午夜重开仍缓存旧“今天”

- 根因：`todayDate` 是不读取响应式依赖的 computed，首次求值后不会失效；`availableDates` 会据此持续判断旧日期。
- RED：真实 Chromium 夹具将可用日期限制为 `2026-08-05`；先在 `2026-08-04 23:59 +08:00` 打开并确认“今天”禁用，关闭后把页面时钟推进到 `2026-08-05 00:01 +08:00` 再打开。
  - 命令：`cd frontend && TZ=UTC npm run test:modern-date-field`
  - 关键输出：专项回归退出码 1；重开后 `todayButton.isDisabled()` 实际为 `true`，期望为 `false`。
- 实现：将 `todayDate` 改为 `ref(todayValue())`，并在每次 `open()` 时用新的本地日期刷新，再由现有 `canChooseToday` 响应式计算可用状态。
- GREEN：同一跨午夜场景中“今天”恢复可用，点击并确认后字段值为 `2026年08月05日`；专项回归退出码 0。

## 变更文件

- `frontend/src/components/ModernDateField.vue`：恢复完整触发热区；每次打开刷新 `todayDate`。
- `frontend/tests/modern-date-field-ui.mjs`：显式东八区时钟；整行/箭头四模式回归；跨午夜重开回归。
- `frontend/tests/fixtures/modern-date-field.html`：浏览器测试 HTML 入口。
- `frontend/tests/fixtures/modern-date-field.ts`：测试夹具挂载入口。
- `frontend/tests/fixtures/modern-date-field-fixture.vue`：覆盖四种默认模式和 `availableDates` 的真实组件夹具。

## 最终验证

- `cd frontend && TZ=UTC npm run test:modern-date-field`：退出码 0，`日期时间选择器移动端回归通过`。
- `cd frontend && npm run check:ui`：退出码 0，`UI token check passed: 36 Vue files scanned.`。
- `cd frontend && npm run build`：退出码 0，`1066 modules transformed`，`built in 449ms`。
- `git diff --check`：退出码 0，无输出。

## 提交

- `9d9b5030632db2132310e1f905488da7e4a007e3`：`修复：恢复日期选择器触发热区并刷新今天`
- 本报告单独以中文文档提交，未混入其他本地改动。

## 自查与顾虑

- 测试断言均针对真实 Chromium 中的可见弹窗、按钮状态和字段值，没有使用源码文本断言。
- 热区用例覆盖所有四种默认模式；自定义 trigger 仍继续使用既有 slot 暴露的 `open()`，生产行为未改。
- `click-input` 和 `click-right-icon` 使用 `.stop`，避免显式事件与整行冒泡造成重复触感调用。
- 跨午夜用例确实使用 `availableDates`，能够捕获 computed 永久缓存的原始缺陷。
- 未发现未解决顾虑。此次没有新增或调整视觉样式，因此未额外做浅色/深色截图比对。
