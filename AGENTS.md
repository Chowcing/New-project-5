# 项目说明

## 项目概览

这是一个移动端优先的日常生活消费记录项目，采用前后端分离的模块化单体架构。

- 后端：Spring Boot 3.4.1、Java 17、Spring Security、JWT、MyBatis-Plus、MySQL。
- 前端：Vue 3、TypeScript、Vite、Vant、Pinia、Vue Router、Axios。
- 数据库：MySQL 8.4，通过 Docker Compose 启动，初始化脚本在 `docker/mysql/init/01_schema.sql`。
- API 基础路径：`/api/v1`。
- 默认开发分支：`develop`。

## 目录结构

- `backend/`：Spring Boot 后端工程。
- `backend/src/main/java/com/example/expense/`：后端 Java 源码，按业务模块分包。
- `backend/src/main/resources/mapper/`：MyBatis XML 查询，复杂列表和统计查询在这里。
- `backend/src/main/resources/application*.yml`：后端通用、开发、生产配置。
- `backend/src/test/`：后端测试，目前主要覆盖 JWT 生成和解析。
- `frontend/`：Vue 移动端 Web 应用。
- `frontend/src/api/`：Axios 实例、API 封装、token 本地存储。
- `frontend/src/components/`：前端复用组件，例如交易表单内的分类/支付方式选择和快速新增组件。
- `frontend/src/views/`：页面组件。
- `frontend/src/router/index.ts`：前端路由和登录守卫。
- `frontend/src/stores/auth.ts`：Pinia 鉴权状态。
- `docker/mysql/init/01_schema.sql`：数据库表结构初始化脚本。
- `docs/api.md`：接口文档。
- `docker-compose.dev.yml`：本地只启动 MySQL，端口映射为 `3307:3306`。
- `docker-compose.prod.yml`：生产编排，包含 MySQL、后端、前端 Nginx。

## 本地开发

首次准备环境变量：

```powershell
Copy-Item .env.example .env
Copy-Item frontend/.env.example frontend/.env.local
```

启动开发数据库：

```powershell
docker compose -f docker-compose.dev.yml up -d
```

后端开发常用命令：

```powershell
cd backend
mvn test
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
```

前端开发常用命令：

```powershell
cd frontend
npm install
npm run dev
npm run build
```

本地默认访问地址：

- 前端开发服务：`http://localhost:5173`
- 后端 API：`http://localhost:8080/api/v1`
- Swagger UI：`http://localhost:8080/swagger-ui/index.html` 或 `http://localhost:8080/swagger-ui.html`

生产 Docker 启动：

```powershell
docker compose -f docker-compose.prod.yml up --build -d
```

生产前端默认暴露在 `http://localhost:8088`，Nginx 将 `/api/` 反向代理到后端。

## 后端约定

- 启动类是 `ExpenseApplication`，启用 `@MapperScan("com.example.expense.**.mapper")`。
- 控制器统一返回 `ApiResponse<T>`，结构为 `success`、`message`、`data`。
- 认证使用 JWT Access Token 和数据库持久化的 Refresh Token。
- 除注册、登录、刷新 token、Swagger/OpenAPI 外，接口都要求认证。
- 当前用户 ID 通过 `SecurityUtils.currentUserId()` 从 Spring Security 上下文获取。
- 数据隔离必须在业务查询中强制按 `userId` 过滤，不要信任前端传入用户 ID。
- 新增或修改引用型数据时，应使用对应 Service 的 `requireOwned` 校验归属。
- Validation 使用 Jakarta Bean Validation，参数错误由 `GlobalExceptionHandler` 转为统一错误响应。
- MyBatis-Plus 负责常规 CRUD，复杂列表/统计用 XML Mapper。
- `application.yml` 中默认时区和日期格式是 `Asia/Shanghai`、`yyyy-MM-dd HH:mm:ss`。

## 日志约定

- 后端使用 Spring Boot 默认 Logback，不额外引入复杂日志框架。
- 日志默认写入 `logs/expense-backend.log`，可通过 `LOG_FILE` 覆盖。
- 日志滚动配置在 `application.yml`：单文件默认 `10MB`，历史默认保留 `14` 天。
- 生产 Docker 已将后端 `/app/logs` 挂载到宿主机 `./logs/backend`，避免容器重建后丢失日志。
- 接口日志在请求处理完成后记录，日志前缀为 `接口完成`，只记录 `method`、`uri`、`status`、`durationMs`、`userId`，不记录请求体。
- 关键业务日志覆盖注册、登录、刷新 token、退出登录、交易增删改、CSV 导入和 CSV 导出。
- 不要把密码、JWT、Refresh Token、请求体、消费事项、备注、金额等敏感内容写入日志。
- 参数错误等可预期业务错误不打印堆栈；未处理系统异常由 `GlobalExceptionHandler` 记录 `error` 和堆栈。

## 后端业务模块

- `auth`：注册、登录、刷新 token、退出登录、当前用户信息。
- `user`：用户实体和基础资料。
- `category`：收入/支出分类管理。
- `payment`：支付方式管理，当前项目已用支付方式替代旧账户模型。
- `transaction`：收支记录增删改查。
- `statistics`：月度统计和分类汇总。
- `budget`：预算管理，支持整月预算或指定分类预算。
- `export`：交易记录 CSV 导出。
- `common`：安全、配置、统一响应、全局异常处理。

## 核心业务规则

- 交易类型只有 `EXPENSE` 和 `INCOME`。
- 交易渠道只有 `ONLINE` 和 `OFFLINE`。
- 新增交易必须包含：`type`、`itemName`、`amount`、`occurredAt`、`channel`、`paymentMethodId`、`categoryId`。
- 线下记录必须填写 `offlinePlace`。
- 线上支出必须填写 `onlineApp`；线上收入的 `onlineApp` 可以为空。
- 写入交易时会保存 `paymentMethodId` 和当时的 `paymentMethodName`。
- 查询交易时会左连接 `payment_methods`，优先展示当前支付方式名称，找不到时回退到交易表里的 `payment_method_name`。
- 交易列表按 `occurred_at DESC, id DESC` 排序。
- 交易列表和 CSV 导出支持 `type`、`startDate`、`endDate`、`categoryId`、`keyword` 筛选。
- `keyword` 会匹配事项、备注、线上 APP、线下地点、支付方式、分类。
- 注册用户时会自动创建常见默认分类，覆盖餐饮、交通、购物、日用、住房、水电燃气、通讯、医疗、教育、娱乐、旅行、人情礼金、其他支出，以及工资、奖金、兼职、投资理财、报销、退款、其他收入。
- 注册用户时会自动创建默认支付方式：微信、支付宝、银行卡、信用卡、借记卡、现金、云闪付、其他。

## 数据库

主要表：

- `users`：用户。
- `refresh_tokens`：刷新凭证，保存 SHA-256 hash，可吊销。
- `categories`：用户分类，按 `user_id` 隔离。
- `payment_methods`：用户支付方式，按 `user_id` 隔离。
- `transactions`：交易记录，包含事项、渠道、地点/APP、支付方式快照、分类和备注。
- `budgets`：预算，`month` 格式为 `yyyy-MM`，`category_id` 可为空表示整月总预算。

注意事项：

- 初始化脚本只会在 MySQL 数据卷首次创建时执行。
- 如果修改表结构，开发环境已有数据卷不会自动重放 `01_schema.sql`。
- 不要重新引入旧的 `accounts` 表或 `/accounts` 接口，当前模型使用 `payment_methods`。

## 前端约定

- Vite 别名 `@` 指向 `frontend/src`。
- `VITE_API_BASE_URL` 默认是 `/api/v1`。
- 开发服务器监听 `5173`，并把 `/api` 代理到 `http://localhost:8080`。
- Axios 实例在 `frontend/src/api/http.ts`。
- 响应拦截器会拆出后端 `ApiResponse.data`；`responseType: 'blob'` 时直接返回 Blob。
- Access Token 和 Refresh Token 存储在 `localStorage`，key 为 `expense.auth.tokens`。
- 多个请求同时收到 401 时，前端只发起一次 refresh 请求，其他请求等待同一个 Promise 后重放。
- 路由守卫基于 Pinia 中是否存在 access token 判断登录态。
- 底部 TabBar 页面包括：首页、明细、记一笔、统计、设置。
- 交易表单中的分类和支付方式选择由 `frontend/src/components/TransactionOptionFields.vue` 复用，支持在新增或编辑交易时快速创建分类和支付方式，并在创建成功后立即选中新项。
- 交易表单内快速新增分类时，分类类型跟随当前交易类型；快速新增支付方式使用通用默认图标和排序。

## 前端页面

- `HomeView.vue`：首页月度概览、最近 5 条记录、快速记一笔入口。
- `QuickAddView.vue`：新增交易表单，处理线上/线下字段、分类和支付方式快速新增、基本前端校验。
- `RecordsView.vue`：交易明细列表、日期/类型/关键词筛选、删除记录。
- `StatisticsView.vue`：月度收入、支出、结余和分类汇总。
- `SettingsView.vue`：用户信息、分类管理、支付方式管理、预算管理、数据导出、退出登录。
- `CategoriesView.vue`：分类新增和删除。
- `PaymentMethodsView.vue`：支付方式新增和删除。
- `BudgetsView.vue`：预算新增、按月份加载、删除。
- `ExportView.vue`：按条件导出交易 CSV。
- `LoginView.vue` / `RegisterView.vue`：登录和注册。

## API 速览

- `POST /auth/register`：注册并返回 token。
- `POST /auth/login`：登录并返回 token。
- `POST /auth/refresh`：刷新 token，并轮换 refresh token。
- `POST /auth/logout`：吊销当前 refresh token。
- `GET /auth/me`：当前用户信息。
- `GET /transactions`、`POST /transactions`、`PUT /transactions/{id}`、`DELETE /transactions/{id}`。
- `GET /categories`、`POST /categories`、`PUT /categories/{id}`、`DELETE /categories/{id}`。
- `GET /payment-methods`、`POST /payment-methods`、`PUT /payment-methods/{id}`、`DELETE /payment-methods/{id}`。
- `GET /budgets`、`POST /budgets`、`PUT /budgets/{id}`、`DELETE /budgets/{id}`。
- `GET /statistics/monthly?month=yyyy-MM`。
- `GET /exports/transactions.csv`。

## 验证建议

后端改动后至少运行：

```powershell
cd backend
mvn test
```

前端改动后至少运行：

```powershell
cd frontend
npm run build
```

涉及接口字段、DTO、数据库列或前端类型时，同时检查：

- `docs/api.md`
- `docker/mysql/init/01_schema.sql`
- `backend/src/main/java/com/example/expense/**/dto`
- `backend/src/main/resources/mapper/*.xml`
- `frontend/src/types.ts`
- `frontend/src/api/services.ts`
- 相关 `frontend/src/views/*.vue`

## 维护注意事项

- 不要提交 `.env`、`.env.local`、`node_modules/`、`frontend/dist/`、`backend/target/`、`logs/`、IDE 配置等生成或本地文件。
- 保持 API 响应结构与 `ApiResponse` 一致，前端依赖该结构做统一拆包。
- 涉及认证的改动要同时考虑 Access Token 解析、Refresh Token 轮换、前端自动刷新和退出登录。
- 涉及交易字段的改动通常需要前后端、数据库脚本、导出 CSV、统计查询一起更新。
- 删除分类或支付方式时，如果已被交易引用，数据库外键可能阻止删除；前端已有确认提示。
- 前端 UI 以移动端 Vant 组件为主，样式集中在 `frontend/src/styles/main.css`，页面内只保留必要的 scoped 样式。
- README 中规定分支策略：`main` 为稳定发布分支，`develop` 为日常集成分支，功能开发从 `develop` 拉 `feature/*`。
