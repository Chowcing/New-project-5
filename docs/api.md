# 接口文档

基础路径：`/api/v1`

认证方式：除注册、登录、刷新 token 外，其他接口均使用 `Authorization: Bearer <accessToken>`。

Swagger UI 使用方式：调用登录或注册接口获取 `accessToken` 后，点击页面右上角 `Authorize`，只填写 token 本体，不需要手动加 `Bearer ` 前缀。

## Auth

- `POST /auth/register`：注册并返回 access/refresh token
- `POST /auth/login`：登录并返回 access/refresh token
- `POST /auth/refresh`：使用 refresh token 轮换并返回新 token
- `POST /auth/logout`：注销当前 refresh token
- `GET /auth/me`：当前用户信息

## 记账

- `GET /transactions`：分页记录列表，支持 `type`、`startDate`、`endDate`、`channel`、`categoryId`、`paymentMethodId`、`keyword`、`page`、`size`
- `GET /transactions/daily-cards`：按有记录的日期分页返回明细卡片，支持 `type`、`startDate`、`endDate`、`channel`、`categoryId`、`paymentMethodId`、`keyword`、`dayPage`、`daySize`、`recordPage`、`recordSize`
- `GET /transactions/daily-options`：按当前筛选条件返回有记录的日期选项，供明细页快速跳转日期卡片使用
- `GET /transactions/{id}`：记录详情
- `GET /transactions/recommendations?limit=5`：根据当前时间、历史出现频次、常用时段、星期习惯和最近记录生成“记一笔”推荐模板
- `POST /transactions`：新增记录
- `PUT /transactions/{id}`：修改记录
- `DELETE /transactions/{id}`：逻辑删除记录

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
- `itemName`：事项或物品，例如冰棍、工资、泳镜
- `amount`：金额，后端校验最多 10 位整数和 2 位小数
- `occurredAt`：发生时间，例如 `2026-05-07T16:46:23`
- `channel`：`ONLINE` 线上或 `OFFLINE` 线下
- `onlineApp`：线上消费 APP，例如淘宝；线上收入可为空
- `offlinePlace`：线下地点，前端可通过高德地图选址和地点联想填写，后端保存文本值
- `categoryId`：分类
- `paymentMethodId`：支付方式
- `note`：额外备注，可为空

## 基础资料

- `/categories`：分类增删改查，删除为逻辑删除；同一用户下同类型分类名称不允许重复；已被收支记录引用的分类不允许删除
- `GET /categories/{id}/references?size=5`：查看引用该分类的最近收支记录
- `/payment-methods`：支付方式增删改查，删除为逻辑删除；同一用户下支付方式名称不允许重复；已被收支记录引用的支付方式不允许删除
- `GET /payment-methods/{id}/references?size=5`：查看引用该支付方式的最近收支记录
- `/budgets`：预算增删改查，删除为逻辑删除；同一用户同一月份下整月总预算、同一分类预算分别不允许重复；预算金额后端校验最多 10 位整数和 2 位小数

## 统计和导出

- `GET /statistics/monthly?month=2026-04`：月度统计
  - 返回月总支出、月总收入、结余、总笔数、支出笔数、收入笔数
  - `dailyTrend`：当月每日收入、支出、结余和笔数，日期补齐到整月
  - `expenseByCategory` / `incomeByCategory`：按分类汇总金额和笔数
  - `expenseByChannel`：按线上/线下汇总支出金额和笔数
  - `expenseByPaymentMethod`：按支付方式汇总支出金额和笔数
- `GET /statistics/yearly?year=2026`：年度统计
  - 返回年度总支出、年度总收入、结余、总笔数、支出笔数、收入笔数
  - `monthlyTrend`：当年每月收入、支出、结余和笔数，月份补齐到 12 个月
  - `expenseByCategory` / `incomeByCategory`：按分类汇总金额和笔数
  - `expenseByChannel`：按线上/线下汇总支出金额和笔数
  - `expenseByPaymentMethod`：按支付方式汇总支出金额和笔数
- `GET /exports/transactions.csv?...`：按筛选条件导出 CSV
- `POST /imports/transactions.csv`：通过 multipart 表单字段 `file` 导入交易 CSV，列顺序与导出 CSV 一致；支持 `EXPENSE/INCOME` 或 `支出/收入`，`ONLINE/OFFLINE` 或 `线上/线下`。导入按当前用户已有分类和支付方式名称匹配，返回成功条数和逐行错误。
