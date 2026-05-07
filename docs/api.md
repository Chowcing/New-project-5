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

- `GET /transactions`：记录列表，支持 `type`、`startDate`、`endDate`、`categoryId`、`keyword`
- `POST /transactions`：新增记录
- `PUT /transactions/{id}`：修改记录
- `DELETE /transactions/{id}`：删除记录

交易请求字段：

- `type`：`EXPENSE` 支出或 `INCOME` 收入
- `itemName`：事项或物品，例如冰棍、工资、泳镜
- `amount`：金额
- `occurredAt`：发生时间，例如 `2026-05-07T16:46:23`
- `channel`：`ONLINE` 线上或 `OFFLINE` 线下
- `onlineApp`：线上消费 APP，例如淘宝；线上收入可为空
- `offlinePlace`：线下地点，后续可替换为高德定位数据
- `categoryId`：分类
- `paymentMethodId`：支付方式
- `note`：额外备注，可为空

## 基础资料

- `/categories`：分类增删改查
- `/payment-methods`：支付方式增删改查
- `/budgets`：预算增删改查

## 统计和导出

- `GET /statistics/monthly?month=2026-04`：月度统计
- `GET /exports/transactions.csv?...`：按筛选条件导出 CSV

