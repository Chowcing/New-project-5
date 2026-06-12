# 前端请求排障链路设计

## 背景

后端已经完成接口运行日志增强和业务审计日志。为了让浏览器侧错误能快速关联到服务端访问日志，前端需要在请求和错误展示中携带同一个低敏 `requestId`。

## 目标

- 前端每个 API 请求自动携带 `X-Request-Id`。
- 响应失败时从响应头读取 `X-Request-Id`，封装到统一错误对象。
- 用户可见错误提示展示短格式请求 ID，方便反馈问题。
- 浏览器控制台输出低敏请求错误信息，便于本地排查。

## 非目标

- 不新增前端日志上报接口。
- 不记录请求体、密码、JWT、Refresh Token、金额、备注、OCR 文本或 CSV 内容。
- 不逐页改造业务页面错误处理。

## 设计

在 `frontend/src/api/http.ts` 中继续使用现有 Axios 实例。请求拦截器为每个请求生成 `X-Request-Id`，如果调用方已经传入则复用。响应拦截器读取响应头中的 `x-request-id` 或请求头中的原始 ID，失败时构造 `RequestError`，字段包括 `status`、`data`、`requestId`、`method` 和 `url`。

在 `frontend/src/utils/errors.ts` 中扩展 `showError()`：Toast 保持用户友好文案，并在存在 `requestId` 时追加 `请求ID: <短ID>`。同一函数向控制台输出低敏诊断对象，只包含 `message/status/requestId/method/url`。

## 验证

- 使用源码断言确认 `RequestError`、请求头和错误工具包含 `requestId` 链路。
- 运行 `cd frontend; npm run build`。
- 因本次仅改前端，后端不需要修改；提交前运行 `git diff --check`。
