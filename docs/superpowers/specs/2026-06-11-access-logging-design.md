# 接口运行日志增强设计

## 背景

项目后端已经有 `AccessLogFilter` 记录接口完成日志，字段包括 `method`、`uri`、`status`、`durationMs`、`userId` 和部署版本；`GlobalExceptionHandler` 会对未预期异常打印服务端异常日志。当前日志能看到接口是否完成，但排障链路还不够完整：缺少请求级追踪 ID、客户端来源、慢请求标记和异常类型等信息。

本次只增强后端接口运行日志，不做业务审计落库、前端日志上报或请求体日志。

## 目标

- 为每个请求提供稳定的 `requestId`，同时写入响应头和日志。
- 在接口完成日志中补充排障所需的低敏字段：`clientIp`、查询参数名、异常类型、慢请求标记。
- 保持现有安全约束：不记录请求体，不记录密码、JWT、Refresh Token、金额、备注、OCR 识别文本等敏感内容。
- 保留现有日志文件滚动与开发/生产日志级别配置。
- 补充自动化测试，防止后续误把敏感值写入日志。

## 非目标

- 不新增 ELK、OpenTelemetry、Sentry 等外部观测系统。
- 不新增业务审计表，也不扩大 `admin_audit_logs` 的职责。
- 不记录接口请求体、响应体或完整查询参数值。
- 不改变 API 响应结构，只增加响应头。

## 设计

### 访问日志过滤器

在现有 `backend/src/main/java/com/example/expense/common/logging/AccessLogFilter.java` 上增强。

新增或复用请求头 `X-Request-Id`：

- 如果客户端传入合法、非空的 `X-Request-Id`，沿用该值。
- 如果没有传入，则生成 UUID。
- 响应头返回相同的 `X-Request-Id`。
- 日志中记录 `requestId`。

客户端 IP 记录策略：

- 优先读取 `X-Forwarded-For` 的第一个 IP。
- 其次读取 `X-Real-IP`。
- 最后回退到 `request.getRemoteAddr()`。
- 只记录 IP 字符串，不记录完整请求头。

查询参数记录策略：

- 只记录参数名集合，例如 `queryKeys=[page,size,startDate]`。
- 不记录参数值，避免泄漏金额、备注、关键词、OCR 文本等业务内容。
- 参数名按字母排序，便于检索和测试。

慢请求策略：

- 新增配置项 `app.logging.slow-request-threshold-ms`，默认 `1000`。
- `durationMs >= threshold` 时日志追加 `slow=true`，否则 `slow=false`。
- 先保持日志级别按状态码划分：`5xx=error`、`4xx=warn`、其他 `info`。慢请求不单独提升日志级别，避免正常慢查询造成误报。

异常策略：

- 过滤链中捕获到异常时，日志记录 `exception=<异常类简单名>`。
- 不在访问日志中重复打印完整堆栈，避免和 `GlobalExceptionHandler` 的 500 异常日志重复。
- 状态码仍保持现有行为：有异常时最低按 500 记录。

### 配置

在 `application.yml` 增加默认值：

```yaml
app:
  logging:
    slow-request-threshold-ms: ${SLOW_REQUEST_THRESHOLD_MS:1000}
```

`SecurityConfig` 创建 `AccessLogFilter` 时注入该配置值。

### 测试

新增 `AccessLogFilterTest`，覆盖：

- 未传 `X-Request-Id` 时生成响应头并写入日志字段。
- 传入 `X-Request-Id` 时复用该值。
- 查询参数只记录参数名，不记录参数值。
- `X-Forwarded-For` 取第一个 IP。
- 过滤链抛异常时日志状态至少为 500，并包含异常类型。

后端整体回归执行 `cd backend; mvn test`。

## 验收标准

- 任意后端请求响应头包含 `X-Request-Id`。
- 接口完成日志包含 `requestId`、`method`、`uri`、`status`、`durationMs`、`userId`、`clientIp`、`queryKeys`、`slow`、`deployment`，异常请求包含 `exception`。
- 日志中不出现请求体，也不出现查询参数值。
- `mvn test` 通过。
