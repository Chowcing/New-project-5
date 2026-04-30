# 接口文档

基础路径：`/api/v1`

认证方式：除注册、登录、刷新 token 外，其他接口均使用 `Authorization: Bearer <accessToken>`。

## Auth

- `POST /auth/register`：注册并返回 access/refresh token
- `POST /auth/login`：登录并返回 access/refresh token
- `POST /auth/refresh`：使用 refresh token 轮换并返回新 token
- `POST /auth/logout`：注销当前 refresh token
- `GET /auth/me`：当前用户信息

## 记账

- `GET /transactions`：记录列表，支持 `type`、`startDate`、`endDate`、`categoryId`、`accountId`、`keyword`
- `POST /transactions`：新增记录
- `PUT /transactions/{id}`：修改记录
- `DELETE /transactions/{id}`：删除记录

## 基础资料

- `/categories`：分类增删改查
- `/accounts`：账户增删改查
- `/budgets`：预算增删改查

## 统计和导出

- `GET /statistics/monthly?month=2026-04`：月度统计
- `GET /exports/transactions.csv?...`：按筛选条件导出 CSV

