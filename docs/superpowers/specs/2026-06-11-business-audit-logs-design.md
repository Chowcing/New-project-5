# 业务审计日志与管理页展示设计

## 背景

项目已有 `admin_audit_logs`，用于记录管理员在后台执行的用户状态变更、吊销凭证、重置邮箱、删除交易等操作。该表字段以 `admin_user_id` 和 `reason` 为中心，不适合直接承载普通用户的业务行为。

本次新增独立的业务审计日志能力，用于记录用户关键写操作，并在后台管理页展示。审计内容必须保持低敏，不记录请求体、金额、备注、OCR 识别文本、CSV 内容、密码、JWT 或 Refresh Token。

## 目标

- 新增 `business_audit_logs` 表，和管理员审计日志分离。
- 用户关键写操作成功后写入业务审计日志。
- 后台管理接口支持分页查询业务审计日志，并提供基础筛选。
- 前端后台管理页“审计”Tab 支持切换“管理操作 / 业务操作”。
- 同步 API 文档、初始化 SQL、Flyway 迁移、前端类型与接口封装。

## 非目标

- 不替换或迁移现有 `admin_audit_logs`。
- 不记录字段变更前后值，不做 diff 审计。
- 不记录失败请求。第一期只记录成功落库或成功受理的操作。
- 不做普通用户自己的审计日志查询页面。
- 不引入外部审计、日志或消息队列系统。

## 审计数据模型

新增表 `business_audit_logs`：

- `id BIGINT PRIMARY KEY AUTO_INCREMENT`
- `user_id BIGINT NOT NULL`：执行业务操作的用户。
- `action VARCHAR(64) NOT NULL`：动作编码。
- `target_type VARCHAR(32) NOT NULL`：目标类型。
- `target_id BIGINT NULL`：目标 ID。OCR 这类无业务记录 ID 的操作允许为空。
- `source VARCHAR(32) NOT NULL`：操作来源。
- `status VARCHAR(16) NOT NULL DEFAULT 'SUCCESS'`：第一期固定为 `SUCCESS`。
- `request_id VARCHAR(128) NULL`：来自请求链路的 `X-Request-Id`。
- `created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP`

索引：

- `(created_at)`
- `(user_id, created_at)`
- `(action, created_at)`
- `(target_type, target_id)`
- `(request_id)`

外键：

- `user_id` 引用 `users(id)`。

## 动作范围

第一期记录这些成功操作：

- 交易：`TRANSACTION_CREATE`、`TRANSACTION_UPDATE`、`TRANSACTION_DELETE`、`TRANSACTION_IMAGE_DELETE`
- 分类：`CATEGORY_CREATE`、`CATEGORY_UPDATE`、`CATEGORY_DELETE`
- 支付方式：`PAYMENT_METHOD_CREATE`、`PAYMENT_METHOD_UPDATE`、`PAYMENT_METHOD_DELETE`
- 线上平台：`ONLINE_PLATFORM_CREATE`、`ONLINE_PLATFORM_UPDATE`、`ONLINE_PLATFORM_DELETE`
- 预算：`BUDGET_CREATE`、`BUDGET_UPDATE`、`BUDGET_DELETE`
- 周期规则：`RECURRING_RULE_CREATE`、`RECURRING_RULE_UPDATE`、`RECURRING_RULE_DELETE`、`RECURRING_RUN_GENERATE`
- 导入：`IMPORT_JOB_CREATE`、`IMPORT_JOB_SUCCESS`
- OCR：`OCR_IMAGE_RECOGNIZE`

默认数据初始化不记录业务审计，包括注册后默认分类、默认支付方式和默认线上平台。

## 来源取值

- `USER`：用户在前端直接发起的业务操作。
- `IMPORT`：CSV 导入流程创建或完成。
- `OCR`：OCR 图片识别请求。
- `RECURRING`：周期规则生成流水。

管理员后台删除交易仍继续写入 `admin_audit_logs`；由于底层复用 `TransactionService.delete`，如果不加区分会额外产生普通业务审计。实现时必须为管理员删除交易提供跳过业务审计的调用方式，避免同一动作在两个审计列表重复出现。

## 后端设计

新增 `businessaudit` 模块，包含：

- `BusinessAuditLog` 实体。
- `BusinessAuditLogMapper`。
- `BusinessAuditLogResponse` DTO。
- `BusinessAuditLogService`：提供 `recordSuccess(userId, action, targetType, targetId, source)` 和分页查询方法。

`BusinessAuditLogService` 从请求上下文读取当前 `X-Request-Id`。为异步导入场景，导入任务创建时记录 `IMPORT_JOB_CREATE`，任务后台完成时记录 `IMPORT_JOB_SUCCESS`，后台线程没有原始请求上下文时 `requestId` 允许为空。

业务服务在数据库操作成功后写审计。优先在同一事务内写入；当前没有事务的方法可只写成功路径，不为校验失败或异常路径写审计。

后台管理接口新增：

`GET /api/v1/admin/business-audit-logs`

参数：

- `userId`
- `action`
- `targetType`
- `source`
- `page`
- `size`

返回 `PageResponse<BusinessAuditLogResponse>`。

## 前端设计

后台管理页保留一个“审计”Tab，Tab 内增加二级切换：

- `管理操作`：沿用现有 `adminApi.auditLogs` 和 `AdminAuditLog`。
- `业务操作`：调用新增 `adminApi.businessAuditLogs`。

业务操作筛选区：

- 用户 ID：数字输入。
- 动作：文本输入，按动作编码精确筛选。
- 目标类型：文本输入，按目标类型编码精确筛选。
- 来源：文本输入，按来源编码精确筛选。
- 搜索按钮。

业务审计列表卡片展示：

- 主标题：动作中文名，右侧显示目标类型与 `targetId`。
- 副信息：`createdAt · 用户 #userId · source`。
- `requestId` 存在时展示一行 `requestId`，便于和后端接口日志关联。

页面继续沿用现有骨架屏、空态、分页和卡片样式。

## 文档与联动

需要同步：

- `backend/src/main/resources/db/migration/`：新增 Flyway 迁移。
- `docker/mysql/init/01_schema.sql`：新增初始化表。
- `docs/api.md`：新增后台业务审计接口说明和字段安全说明。
- `frontend/src/types.ts`：新增 `BusinessAuditLog` 类型。
- `frontend/src/api/services.ts`：新增查询方法。
- `frontend/src/views/AdminView.vue`：审计 Tab 二级切换和业务审计列表。

## 测试

后端：

- `BusinessAuditLogServiceTest` 覆盖审计写入、分页筛选、requestId 可为空。
- 至少覆盖一个典型业务服务写入审计，例如交易新增和分类更新。
- 管理端接口测试覆盖非管理员 403 和管理员分页查询。
- 执行 `cd backend; mvn test`。

前端：

- 执行 `cd frontend; npm run build`。

## 验收标准

- 普通用户完成第一期范围内的关键写操作后，会生成业务审计日志。
- 业务审计日志不包含金额、备注、OCR 文本、CSV 内容、请求体、密码或 token。
- 管理页“审计”Tab 可以切换查看管理操作和业务操作。
- 业务操作列表支持分页和基础筛选。
- 后端 `mvn test` 通过，前端 `npm run build` 通过。
