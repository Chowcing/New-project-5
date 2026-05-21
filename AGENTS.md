# AGENTS.md

## 0. 硬性规则
- 看到与当前任务无关的本地改动，不要回滚、覆盖或清理。
- 手工编辑文件优先用 `apply_patch`。
- 每次提交和推送都使用中文：`git commit -m` 提交信息必须是中文，推送后的汇报也使用中文说明。

## 1. 项目概况
- 移动端优先的日常消费记录系统，前后端分离的模块化单体。
- 后端：Spring Boot 3.4.1 / Java 17 / Spring Security / JWT / MyBatis-Plus / MySQL 8.4。
- 前端：Vue 3 / TypeScript / Vite / Vant / Pinia / Vue Router / Axios。
- API 基础路径：`/api/v1`。
- 分支策略：`develop` 作为日常开发分支，允许直接提交/推送新功能和修复；`main` 作为稳定发布分支，只接收从 `develop` 合并过来的变更。
- 工作流：个人开发默认直接在 `develop` 上完成需求和 bug 修复，发布前再把 `develop` 合并到 `main`。

## 2. 关键目录
- `backend/src/main/java/com/example/expense/`：auth、user、category、payment、transaction、statistics、budget、export、imports、common。
- `backend/src/main/resources/mapper/`：复杂查询 XML。
- `backend/src/test/java/`：服务与配置测试。
- `frontend/src/api/`：HTTP 封装、接口服务、token 存储。
- `frontend/src/components/`：表单复用组件。
- `frontend/src/views/`：首页、记一笔、明细、详情、统计、设置、分类、支付方式、预算、导入导出、登录注册。
- `frontend/src/router/index.ts`、`frontend/src/stores/auth.ts`、`frontend/src/utils/preferences.ts`：路由、登录态、本地偏好。
- `docker/mysql/init/01_schema.sql`：数据库初始化。
- `docs/api.md`、`docs/production-runbook.md`：接口和生产运维。
- `scripts/smoke-local.ps1`：本地核心接口冒烟脚本，会创建并清理临时 `smoke_*` 测试用户。

## 3. 本地/生产命令
- 复制环境变量：`Copy-Item .env.example .env`；`Copy-Item frontend/.env.example frontend/.env.local`。
- 开发数据库：`docker compose -f docker-compose.dev.yml up -d`，MySQL 端口映射 `3307:3306`。
- 后端：`cd backend; mvn test; mvn spring-boot:run "-Dspring-boot.run.profiles=dev"`。
- 前端：`cd frontend; npm install; npm run dev; npm run build`。
- 本地冒烟：后端和开发 MySQL 启动后运行 `.\scripts\smoke-local.ps1`；脚本默认清理本次创建的 `smoke_*` 测试数据，清理失败会返回非 0。
- 生产：`docker compose -f docker-compose.prod.yml up --build -d`；配置校验：`docker compose -f docker-compose.prod.yml config --quiet`。
- 生产必须设置 `MYSQL_ROOT_PASSWORD`、`MYSQL_PASSWORD`、`JWT_SECRET`；`VITE_*` 只在前端构建时生效，改高德配置后要重建前端镜像。
- `docker-compose.server.yml` 是 2 vCPU / 2 GiB 服务器覆盖文件；生产公网入口走宿主机 Nginx 反代到 `127.0.0.1:8088`，只开放 `22/80/443`，不要用 `docker compose down -v`，不要删除生产 MySQL volume。
- 本地默认访问：前端 `http://localhost:5173`，后端 `http://localhost:8080/api/v1`，Swagger `http://localhost:8080/swagger-ui/index.html`。

## 3.1 本机工具链提示
- 这台 Mac 的 Homebrew 在 `/opt/homebrew/bin/brew`。非交互 shell 可能没有把 `/opt/homebrew/bin` 放进 `PATH`，找不到 `brew` 或 `gh` 时先用绝对路径确认。
- GitHub CLI 已通过 Homebrew 安装：`/opt/homebrew/bin/gh`；当前已登录 `github.com` 账号 `Chowcing`，凭据存储在 keyring，Git 操作协议为 `ssh`。如认证失效，再让用户执行 `gh auth login`。
- 提交和推送仍优先使用本地 `git`；只有需要查看 GitHub PR、Actions、Issue 或创建 PR 时再使用 `gh`/GitHub 插件。

## 4. 业务与数据规则
- 认证使用 JWT access token + 数据库 refresh token；除注册、登录、刷新 token、Swagger 外，其余接口都要认证。
- 当前用户 ID 只从 `SecurityUtils.currentUserId()` 获取；所有业务查询必须按 `userId` 过滤。
- 引用型数据（分类、支付方式等）修改前先做归属校验，优先用对应 Service 的 `requireOwned`。
- 交易类型仅 `EXPENSE` / `INCOME`，渠道仅 `ONLINE` / `OFFLINE`。
- 新增交易必须有 `type`、`itemName`、`amount`、`occurredAt`、`channel`、`paymentMethodId`、`categoryId`；`OFFLINE` 必填 `offlinePlace`；线上支出必填 `onlineApp`，线上收入可空。
- 写入交易时要保存 `paymentMethodId` 和当时的 `paymentMethodName`；列表按 `occurred_at DESC, id DESC`。
- 交易列表和 CSV 导出支持 `type`、`startDate`、`endDate`、`categoryId`、`keyword` 等筛选。
- 注册后自动创建默认分类和默认支付方式；不要重新引入旧 `accounts` 表或 `/accounts` 接口。
- 预算按 `month=yyyy-MM` 管理，`categoryId` 可空表示整月预算。
- 数据表主要是 `users`、`refresh_tokens`、`categories`、`payment_methods`、`transactions`、`budgets`、`import_jobs`；改结构时同步 `schema.sql`，已有 MySQL 数据卷不会自动重放初始化脚本。

## 5. 日志与前端约定
- 接口完成日志只记 `method`、`uri`、`status`、`durationMs`、`userId`，不要记录请求体。
- 不要把密码、JWT、Refresh Token、金额、备注等敏感内容写入日志；参数错误不打堆栈，系统异常由 `GlobalExceptionHandler` 统一处理。
- 前端默认把 token 放在 `localStorage`，偏好放在 `frontend/src/utils/preferences.ts`。
- 交易表单保持高频顺序：类型、金额、事项、分类、支付方式；其他信息放补充区，保存用底部固定操作栏。
- 明细页记录左滑优先交给 `van-swipe-cell`，日期横滑只作用于非记录行区域。
- 明细页当天初始展示条数放在设置页“明细偏好”中，默认 5 条。
- `@` 别名指向 `frontend/src`。

## 6. 变更联动
- 接口字段、DTO、数据库列或前端类型变更时，同步更新 `docs/api.md`、`docker/mysql/init/01_schema.sql`、`backend/src/main/resources/mapper/*.xml`、`frontend/src/types.ts`、`frontend/src/api/services.ts` 和相关页面。
- 后端改动至少跑 `cd backend; mvn test`。
- 前端改动至少跑 `cd frontend; npm run build`。
