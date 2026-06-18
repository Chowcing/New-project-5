# 分析页周度分析设计

## 背景

分析页当前支持月度和年度统计。用户需要新增以周为单位的分析功能，采用自然周口径：周一到周日。周度分析应作为和月度、年度并列的统计模式，复用现有分析页的信息架构、视觉样式、点击查看流水明细能力和后端统计聚合模式。

## 目标

- 分析页增加“周度”模式，和“月度”“年度”并列切换。
- 周度统计按自然周计算，周起始日为周一，结束日为周日。
- 用户可以选择任意周，并通过“上一周”“本周”快捷入口切换。
- 周度视图展示总支出、总收入、结余、总笔数、支出笔数、收入笔数、周度洞察、7 天趋势、支出分类占比、渠道占比和支付方式占比。
- 点击趋势、分类、渠道、支付方式时跳转到流水页，并带上对应周范围和筛选条件。
- 周度预算不做独立预算聚合，预算区域提示“预算按月管理”，并跳转到周开始日期所在月份的预算管理。

## 非目标

- 不新增周预算模型或预算表字段。
- 不改变现有月度、年度接口的响应结构。
- 不重构分析页整体视觉系统。
- 不新增跨周自定义日期范围分析。

## 后端设计

新增接口：

- `GET /api/v1/statistics/weekly?weekStart=2026-06-15`

请求参数：

- `weekStart`：`yyyy-MM-dd`，必须是周一。后端负责校验；如果不是周一，返回参数错误。

响应对象 `WeeklyStatisticsResponse`：

- `weekStart`：周一日期。
- `weekEnd`：周日日期。
- `totalExpense`、`totalIncome`、`balance`。
- `transactionCount`、`expenseCount`、`incomeCount`。
- `insight`：复用 `StatisticsInsight`，对比上一自然周。
- `dailyTrend`：复用 `DailySummary`，补齐周一到周日 7 天。
- `expenseByCategory` / `incomeByCategory`。
- `expenseByChannel`。
- `expenseByPaymentMethod`。

实现方式：

- `StatisticsController` 增加 `/weekly`。
- `StatisticsService` 增加 `weekly(Long userId, LocalDate weekStart)`。
- 统计聚合复用现有 `selectMonthlyTotals`、`selectDailySummary`、`selectCategorySummary`、`selectExpenseByChannel`、`selectExpenseByPaymentMethod` 这些按时间区间查询的方法，不额外复制 SQL。
- 新增周趋势补齐逻辑，将数据库返回的日汇总补齐为 7 天。
- 新增周度洞察构造逻辑：当前周对比上一周，日均支出除以 7，支出笔均沿用支出总额除以支出笔数，高消费日沿用日趋势峰值。
- 统计缓存新增周度 key，避免和月度、年度缓存混用。

## 前端设计

分析页 `StatisticsView.vue` 调整：

- `PeriodMode` 扩展为 `WEEKLY | MONTHLY | YEARLY`。
- 顶部切换增加“周度”。
- 周度模式下日期控件使用日选择，存储的是周一日期，并显示周范围。
- 快捷按钮为“上一周”“本周”。
- `currentStats`、趋势数据、洞察、占比图继续通过统一计算属性驱动。
- 趋势图在周度下展示 7 天，x 轴标签沿用 `MM-DD`。
- 预算区域在周度下显示提示：“预算按月管理，可进入 YYYY-MM 预算查看或调整。”
- 跳流水页时，周度模式的 `startDate` 为 `weekStart`，`endDate` 为 `weekEnd`。
- 周度偏好保存到 `expense.preferences.statistics`，包括 `mode` 和 `weekStart`。

类型和 API：

- `frontend/src/types.ts` 新增 `WeeklyStatistics`。
- `frontend/src/api/services.ts` 新增 `statisticsApi.weekly(weekStart)`。
- `frontend/src/utils/preferences.ts` 的统计偏好增加 `weekStart`，保持旧偏好兼容。
- 需要新增少量日期工具，用于计算本周周一、上一周周一、周末日期和判断日期格式。

## 数据流

1. 用户进入分析页。
2. 前端从本地偏好读取统计模式；没有偏好时默认月度。
3. 用户切换到周度或选择周日期后，前端规范化为周一日期。
4. 前端请求 `/statistics/weekly?weekStart=YYYY-MM-DD`。
5. 后端校验当前用户、周起始日期，按 `[weekStart, weekStart + 7 days)` 聚合。
6. 前端将周度响应渲染到现有指标、洞察、趋势和占比区域。
7. 用户点击图表或列表时，前端跳转流水页并携带周范围筛选。

## 错误处理

- 后端收到非周一 `weekStart` 时返回参数错误。
- 前端路由或本地偏好中的非法周日期回退为本周周一。
- 接口请求失败时沿用现有 `showError(error, '统计数据加载失败')`。
- 周内无记录时展示现有空态文案。

## 测试计划

后端：

- 在 `StatisticsServiceTest` 先新增失败测试，验证周度统计返回 7 天补齐趋势、上一周洞察、峰值日、分类/渠道/支付方式汇总。
- 新增参数校验测试或控制器层覆盖，验证非周一参数被拒绝。
- 跑 `cd backend; mvn test`。

前端：

- 视项目现有测试能力，优先添加日期工具或偏好归一化的单元测试；如果没有对应测试框架，则以构建校验和静态检查为主。
- 跑 `cd frontend; npm run build`。
- UI 改动后跑 `cd frontend; npm run check:ui`。

## 联动文档

- 更新 `docs/api.md` 的统计接口说明。
- 不需要更新数据库初始化脚本或 Flyway 迁移，因为不新增表结构。
