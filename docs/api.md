# 接口文档

基础路径：`/api/v1`

认证方式：除注册、登录、刷新 token 外，其他接口均使用 `Authorization: Bearer <accessToken>`。

Swagger UI 使用方式：调用登录或注册接口获取 `accessToken` 后，点击页面右上角 `Authorize`，只填写 token 本体，不需要手动加 `Bearer ` 前缀。

API 响应包含 `X-Expense-Deployment` header，用于确认当前部署版本。该值与前端“我的”页展示的部署版本一致，来源于 `EXPENSE_DEPLOYMENT_VERSION`，本地默认 `local-dev`。

## Auth

- `POST /auth/register`：注册并返回 access/refresh token
- `POST /auth/login`：登录并返回 access/refresh token
- `POST /auth/refresh`：使用 refresh token 轮换并返回新 token
- `POST /auth/logout`：注销当前 refresh token
- `GET /auth/me`：当前用户信息

当前用户信息包含 `status`（`ACTIVE` / `DISABLED`）和 `admin`。管理员权限由服务端环境变量 `ADMIN_USERNAMES` 指定的现有用户名决定；禁用用户不能登录、刷新 token 或继续访问认证接口。

## 记账

- `GET /transactions`：分页记录列表，支持 `type`、`startDate`、`endDate`、`channel`、`categoryId`、`paymentMethodId`、`keyword`、`page`、`size`
- `GET /transactions/daily-cards`：按有记录的日期分页返回明细卡片，支持 `type`、`startDate`、`endDate`、`channel`、`categoryId`、`paymentMethodId`、`keyword`、`dayPage`、`daySize`、`recordPage`、`recordSize`
- `GET /transactions/daily-options`：按当前筛选条件返回有记录的日期选项，供明细页快速跳转日期卡片使用
- `GET /transactions/{id}`：记录详情
- `GET /transactions/recommendations?type=EXPENSE&limit=5`：根据当前时间、历史出现频次、常用时段、星期习惯和最近记录生成“记一笔”推荐模板；`type` 可选，用于只返回支出或收入模板
- `GET /transactions/recommendations/context`：根据当前表单上下文生成智能预填候选，支持 `itemName`、`type`、`channel`、`occurredAt`、`limit`；空事项或弱匹配返回空列表，前端只用结果预填未被用户手动修改过的字段
- `GET /transactions/recommendations/quick-entry?type=EXPENSE&limit=10`：极简记账推荐，返回按置顶、最近使用、使用频率、排序综合排列的分类、支付方式、线上平台、线下地点和最近录入组合
- `POST /transactions`：新增记录；`application/json` 保持无图创建，`multipart/form-data` 支持字段 `transaction`（JSON）和可选多值字段 `images`
- `PUT /transactions/{id}`：修改记录
- `POST /transactions/{id}/images`：为记录追加凭证图片，`multipart/form-data` 多值字段 `images`
- `GET /transactions/{id}/images/{imageId}`：鉴权后读取凭证图片二进制
- `DELETE /transactions/{id}/images/{imageId}`：删除单张凭证图片
- `DELETE /transactions/{id}`：逻辑删除记录

交易图片规则：图片非必传，单笔最多 3 张，单张最大 3MB，仅支持 JPG、PNG、WebP。记录响应中的 `images` 包含 `id`、`originalFilename`、`contentType`、`sizeBytes`、`url`、`sortOrder`。图片 URL 仍需携带 `Authorization` 请求，不作为公开静态资源访问。

记录列表、日期卡片和详情中的交易记录会返回 `categoryId`、`categoryName`、`categoryIcon`，其中 `categoryIcon` 与分类管理中的图标字段保持一致。

## 周期记账

- `GET /recurring-rules`：周期规则列表
- `GET /recurring-rules/{id}`：周期规则详情
- `GET /recurring-rules/{id}/runs`：查看某条规则的历史和待处理实例
- `POST /recurring-rules`：新增周期规则
- `PUT /recurring-rules/{id}`：修改周期规则
- `DELETE /recurring-rules/{id}`：逻辑删除周期规则
- `GET /recurring-runs/due?date=2026-05-15`：查询待处理周期实例，支持提前提醒
- `POST /recurring-runs/{id}/generate`：将待处理实例生成一条正式交易记录
- `POST /recurring-runs/{id}/skip`：跳过本次周期实例

周期规则字段：

- `name`：规则名称
- `type`：`EXPENSE` 支出或 `INCOME` 收入
- `itemName`：事项名称
- `amount`：金额
- `channel`：`ONLINE` 线上或 `OFFLINE` 线下
- `onlineApp`：线上支出 APP，线下可空
- `offlinePlace`：线下地点，线上可空
- `categoryId`：分类
- `paymentMethodId`：支付方式
- `note`：备注
- `scheduleType`：`MONTHLY` 每月或 `WEEKLY` 每周
- `intervalValue`：间隔值，例如每 1 周、每 2 月
- `dayOfMonth`：每月几号，月度规则必填
- `weekday`：每周星期几，周度规则必填
- `startDate`：开始日期
- `endDate`：结束日期，可空
- `reminderDaysBefore`：提前提醒天数，默认 0
- `status`：`ACTIVE` 启用或 `PAUSED` 暂停

周期实例字段：

- `ruleId`：来源规则 ID
- `ruleName`：来源规则名称
- `dueDate`：本次应处理日期
- `reminderDaysBefore`：提前提醒天数
- `status`：`PENDING`、`GENERATED`、`SKIPPED`、`CANCELLED`、`FAILED`
- `transactionId`：生成后对应的交易 ID
- `errorMessage`：失败原因
- `processedAt`：处理时间

分页响应字段：

- `records`：当前页数据
- `total`：符合筛选条件的总条数
- `page`：当前页码，从 1 开始
- `size`：每页条数，最大 100
- `dayPage` / `daySize`：日期卡片分页，从 1 开始，`daySize` 最大 100
- `recordPage` / `recordSize`：每个日期卡片内的记录分页，从 1 开始，`recordSize` 最大 20；前端明细页支持 3、5、10、15、20 条/页
- `totalPages`：总页数

交易请求字段：

- `type`：`EXPENSE` 支出或 `INCOME` 收入
- `itemName`：事项或物品，可为空；为空时前端使用分类、线上平台或线下地点作为展示标题
- `amount`：金额，后端校验最多 10 位整数和 2 位小数
- `occurredAt`：发生时间，例如 `2026-05-07T16:46:23`
- `channel`：`ONLINE` 线上或 `OFFLINE` 线下
- `onlineApp`：线上消费 APP 快照名称，例如淘宝；提供 `onlinePlatformId` 时后端会用平台名称回填
- `onlinePlatformId`：线上平台 ID，线上记录可传；线下记录忽略
- `offlinePlace`：线下地点，前端可通过高德地图选址和地点联想填写，后端保存文本值
- `categoryId`：分类
- `paymentMethodId`：支付方式
- `note`：额外备注，可为空

## 基础资料

- `/categories`：分类增删改查，删除为逻辑删除；同一用户下同类型分类名称不允许重复；已被收支记录引用的分类不允许删除，也不允许修改类型
- 分类请求字段包含 `name`、`type`、`icon`、`sortOrder`、`pinned`；`pinned=true` 的分类在极简记账中优先展示
- `GET /categories/{id}/references?size=5`：查看引用该分类的最近收支记录
- `/payment-methods`：支付方式增删改查，删除为逻辑删除；同一用户下支付方式名称不允许重复；已被收支记录引用的支付方式不允许删除
- 支付方式请求字段包含 `name`、`icon`、`sortOrder`、`pinned`；默认支付方式为微信、支付宝、银行卡、云闪付、现金、数字人民币
- `GET /payment-methods/{id}/references?size=5`：查看引用该支付方式的最近收支记录
- `/online-platforms`：线上平台增删改查，删除为逻辑删除；同一用户下平台名称不允许重复；请求字段包含 `name`、`icon`、`sortOrder`、`pinned`
- `/budgets`：预算增删改查，删除为逻辑删除；同一用户同一月份下整月总预算、同一分类预算分别不允许重复；预算金额后端校验最多 10 位整数和 2 位小数

## 统计和导出

- `GET /statistics/monthly?month=2026-04`：月度统计
  - 返回月总支出、月总收入、结余、总笔数、支出笔数、收入笔数
  - `insight`：返回当前月份、上月、上月总支出/收入/结余、支出/收入/结余环比变化金额和百分比、日均支出、支出笔均、高消费日；上期金额为 0 且本期非 0 时变化百分比为 `null`
  - `monthlyBudget`：整月总预算使用情况；未设置整月预算时为 `null`
  - `categoryBudgetUsages`：分类预算使用情况，包含分类、预算金额、已用金额、剩余金额、使用率、是否超预算、支出笔数
  - `dailyTrend`：当月每日收入、支出、结余和笔数，日期补齐到整月
  - `expenseByCategory` / `incomeByCategory`：按分类汇总金额和笔数
  - `expenseByChannel`：按线上/线下汇总支出金额和笔数
  - `expenseByPaymentMethod`：按支付方式汇总支出金额和笔数
- `GET /statistics/yearly?year=2026`：年度统计
  - 返回年度总支出、年度总收入、结余、总笔数、支出笔数、收入笔数
  - `insight`：返回当前年份、上年、上年总支出/收入/结余、支出/收入/结余同比变化金额和百分比、日均支出、支出笔均、高消费月；上期金额为 0 且本期非 0 时变化百分比为 `null`
  - `monthlyTrend`：当年每月收入、支出、结余和笔数，月份补齐到 12 个月
  - `expenseByCategory` / `incomeByCategory`：按分类汇总金额和笔数
  - `expenseByChannel`：按线上/线下汇总支出金额和笔数
  - `expenseByPaymentMethod`：按支付方式汇总支出金额和笔数
- `GET /exports/transactions.csv?...`：按筛选条件导出 CSV
- `POST /imports/transactions.csv`：通过 multipart 表单字段 `file` 创建交易 CSV 导入任务，立即返回任务状态和 `id`；后台继续导入，避免大文件请求超时。列顺序与导出 CSV 一致；支持 `EXPENSE/INCOME` 或 `支出/收入`，`ONLINE/OFFLINE` 或 `线上/线下`。导入按当前用户已有分类和支付方式名称匹配，事项列可为空。
- `GET /imports/{id}`：查询导入任务状态。`status` 为 `PENDING`、`RUNNING`、`SUCCESS`、`FAILED`；完成后 `result` 返回成功条数和逐行错误。错误行包含 `errorType`、错误原因、行号和原始行关键字段，前端用于错误汇总、筛选和错误报告导出。

## 后台管理

后台接口均要求当前登录用户具备管理员权限，否则返回 403。管理操作会写入 `admin_audit_logs`，只记录管理员、动作、目标和原因，不记录密码、token、请求体或金额明细。

- `GET /admin/overview`：系统概览，返回用户总数、禁用用户数、近 30 天活跃用户数、交易总数、全局收入/支出汇总和近 30 天每日指标
- `GET /admin/users`：用户分页列表，支持 `keyword`、`status`、`page`、`size`
- `GET /admin/users/{id}`：用户详情和交易统计
- `PATCH /admin/users/{id}/status`：启用或禁用用户，请求体 `{ "status": "ACTIVE|DISABLED", "reason": "原因" }`；不能禁用当前管理员账号，禁用后会吊销该用户 refresh token
- `POST /admin/users/{id}/revoke-tokens`：吊销用户未失效 refresh token，请求体 `{ "reason": "原因" }`
- `GET /admin/transactions`：跨用户交易分页查询，支持 `userId`、`type`、`startDate`、`endDate`、`channel`、`keyword`、`page`、`size`
- `DELETE /admin/transactions/{id}`：管理员逻辑删除交易，请求体 `{ "reason": "原因" }`
- `GET /admin/audit-logs`：后台审计日志分页，支持 `page`、`size`
