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
- `backend/src/main/java/com/example/expense/`：auth、user、category、payment、transaction、statistics、budget、export、imports、ocr、common。
- `backend/src/main/resources/db/migration/`：Flyway 增量迁移脚本。
- `backend/src/main/resources/mapper/`：复杂查询 XML。
- `backend/src/test/java/`：服务与配置测试。
- `frontend/src/api/`：HTTP 封装、接口服务、token 存储。
- `frontend/src/components/`：表单复用组件。
- `frontend/src/views/`：工作台、记一笔、流水、详情、分析、我的、分类、支付方式、预算、导入导出、登录注册。
- `frontend/src/router/index.ts`、`frontend/src/stores/auth.ts`、`frontend/src/utils/preferences.ts`、`frontend/src/utils/quickAddDraft.ts`：路由、登录态、本地偏好、记一笔草稿。
- `docker/mysql/init/01_schema.sql`：数据库初始化。
- `docs/api.md`、`docs/production-runbook.md`：接口和生产运维。
- `ocr-service/`：本地 PaddleOCR sidecar，提供内部 `/health` 和 `/ocr` 服务。
- `scripts/smoke-local.ps1`：本地核心接口冒烟脚本，会创建并清理临时 `smoke_*` 测试用户。

## 3. 本地/生产命令
- 复制环境变量：`Copy-Item .env.example .env`；`Copy-Item frontend/.env.example frontend/.env.local`。
- 开发数据库：`docker compose -f docker-compose.dev.yml up -d`，MySQL 端口映射 `3307:3306`。
- 本地 OCR：`docker compose -f docker-compose.dev.yml --profile ocr up --build -d ocr-service`，服务端口 `9000`；启用时 `.env` 设置 `OCR_ENABLED=true`、`OCR_PROVIDER=local`、`LOCAL_OCR_BASE_URL=http://localhost:9000` 并重启后端。
- 后端：`cd backend; mvn test; mvn spring-boot:run "-Dspring-boot.run.profiles=dev"`。
- 前端：`cd frontend; npm install; npm run dev; npm run build`。
- 本地冒烟：后端和开发 MySQL 启动后运行 `.\scripts\smoke-local.ps1`；脚本默认清理本次创建的 `smoke_*` 测试数据，清理失败会返回非 0。
- 生产：`docker compose -f docker-compose.prod.yml up --build -d`；配置校验：`docker compose -f docker-compose.prod.yml config --quiet`；启用本地 OCR 时追加 `--profile ocr` 并保持 `LOCAL_OCR_BASE_URL=http://ocr-service:9000`。
- 生产必须设置 `MYSQL_ROOT_PASSWORD`、`MYSQL_PASSWORD`、`JWT_SECRET`；`VITE_*` 只在前端构建时生效，改高德配置后要重建前端镜像。
- `docker-compose.server.yml` 是 2 vCPU / 2 GiB 服务器覆盖文件；生产公网入口走宿主机 Nginx 反代到 `127.0.0.1:8088`。Compose 保留 MySQL `3306` 端口映射用于受控运维访问，但云安全组正式上线只开放 `22/80/443`，不要用 `docker compose down -v`，不要删除生产 MySQL volume。
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
- 新增交易必须有 `type`、`amount`、`occurredAt`、`channel`、`paymentMethodId`、`categoryId`；`itemName` 可空，展示标题回退到分类、线上平台/APP 或线下地点；`OFFLINE` 必填 `offlinePlace`；线上支出必填 `onlineApp` 或 `onlinePlatformId`，线上收入可空。
- 写入交易时要保存 `paymentMethodId` 和当时的 `paymentMethodName`；列表按 `occurred_at DESC, id DESC`。
- 交易列表和 CSV 导出支持 `type`、`startDate`、`endDate`、`categoryId`、`keyword` 等筛选。
- 交易图片只作为流水凭证使用，不纳入 CSV 导入导出；图片非必传，单笔最多 3 张、单张最大 3MB，仅允许 `image/jpeg`、`image/png`、`image/webp`；前端“记一笔”上传凭证时应阻止同一图片重复加入，并给出明确提示。
- 交易图片存储根目录由 `app.storage.transaction-image-dir` / `TRANSACTION_IMAGE_DIR` 配置，默认 `uploads/transaction-images`；子目录按流水日期和用户 ID 组织，所有图片访问必须走登录后的 `/api/v1/transactions/{id}/images/{imageId}` 鉴权接口，不提供公开静态直链。
- 删除交易图片或删除流水时先软删图片记录，物理文件由延迟清理任务按 `app.storage.transaction-image-retention-days` / `TRANSACTION_IMAGE_RETENTION_DAYS` 回收，默认保留 7 天；复制流水不复制图片。
- 图片转文字接口为鉴权后的 `POST /api/v1/ocr/images`，只识别单张 `image` 文件；复用交易凭证图片校验规则，不自动创建交易，不落库识别文本。“记一笔”多张凭证图片场景下，前端应允许用户指定识别哪一张图片，并按图片维护一份识别结果；同一张图片再次识别时覆盖该图片旧结果，不新增重复结果。
- OCR 默认关闭：`OCR_ENABLED=false`、`OCR_PROVIDER=disabled`。本地实现使用 `OCR_PROVIDER=local` 调用内部 `ocr-service`，不要把 `ocr-service` 直接暴露公网。
- 后端本地 OCR HTTP 客户端必须使用普通 HTTP/1.1 请求工厂，避免 Java HTTP Client 对 Uvicorn 发起 `h2c` 升级导致 FastAPI `422`；相关回归测试在 `backend/src/test/java/com/example/expense/ocr/config/OcrHttpClientConfigTest.java`。
- 注册后自动创建默认分类和默认支付方式；不要重新引入旧 `accounts` 表或 `/accounts` 接口。
- 预算按 `month=yyyy-MM` 管理，`categoryId` 可空表示整月预算。
- 数据表主要是 `users`、`refresh_tokens`、`categories`、`payment_methods`、`transactions`、`transaction_images`、`budgets`、`import_jobs`；改结构时同步 `schema.sql` 和 Flyway 迁移，已有 MySQL 数据卷不会自动重放初始化脚本。

## 5. 日志与前端约定
- 接口完成日志只记 `method`、`uri`、`status`、`durationMs`、`userId`，不要记录请求体。
- 不要把密码、JWT、Refresh Token、金额、备注、OCR 识别文本等敏感内容写入日志；参数错误不打堆栈，系统异常由 `GlobalExceptionHandler` 统一处理。
- 前端默认把 token 放在 `localStorage`，偏好放在 `frontend/src/utils/preferences.ts`。
- 主导航为四个底部 Tab：工作台、流水、分析、我的；`/quick-add` 不占用 Tab，通过全局浮动按钮和工作台快捷入口进入。
- 主题偏好使用 `appearance`（`system` / `light` / `dark`）和 `accent`（`cyan` / `blue` / `violet`），保存在 `localStorage` 的 `expense.preferences` 中；旧 `themePreset/themePrimary` 只做兼容读取。
- “记一笔”草稿保存在 `localStorage` 的 `expense.quickAddDraft` 中，只保存表单字段、进阶步骤、脏字段和 OCR 文本结果，不保存凭证图片文件本体。进入 `/quick-add` 时只提示用户选择“继续填写/丢弃”，不要自动套用草稿；从底部悬浮按钮带 `type=EXPENSE` / `type=INCOME` 进入时，只提示同类型草稿。保存记录成功后必须清除草稿。
- 交易表单和编辑记录表单保持高频顺序：类型、金额、事项、分类、支付方式；其他信息放补充区，保存用底部固定操作栏。金额输入展示应带人民币符号 `¥`，但表单值和接口 payload 仍只保留数字金额。
- “记一笔”进阶确认页摘要中的时间应使用面向用户的格式（如 `2026年06月05日 10:29`），不要直接展示 `datetime-local` 的内部 `T` 分隔格式。
- 流水页记录左滑优先交给 `van-swipe-cell`，日期横滑只作用于非记录行区域。
- 流水页支持日卡片和时间线两种模式；时间线模式滚动后动态显示返回顶部按钮，顶部时隐藏。
- 流水页当天初始展示条数放在“我的”页偏好设置中，默认 5 条。
- 所有选择器类弹窗统一从底部弹出，`van-popup position="bottom"` 应使用 `teleport="body"`，避免受页面容器影响。
- 所有可聚焦输入控件实际字号不得低于 16px，避免 iOS Safari 弹出键盘后自动放大页面。
- 前端界面尽可能使用“图标 + 文本”展示导航、入口、按钮、状态和信息行；图标优先使用 Vant 内置图标，关键操作避免只显示图标，纯图标按钮必须保留 `aria-label` 或 `title`，同类操作在全站保持图标语义一致。
- `@` 别名指向 `frontend/src`。

## 6. 变更联动
- 接口字段、DTO、数据库列或前端类型变更时，同步更新 `docs/api.md`、`docker/mysql/init/01_schema.sql`、`backend/src/main/resources/mapper/*.xml`、`frontend/src/types.ts`、`frontend/src/api/services.ts` 和相关页面。
- 新增或调整上传文件能力时，同步检查 Spring multipart 限制、Nginx `client_max_body_size`、生产 compose 持久化挂载、环境变量示例和运维文档。
- 新增或调整 OCR 能力时，同步检查 `ocr-service/`、`docker-compose.dev.yml`、`docker-compose.prod.yml`、`docker-compose.server.yml`、`.env.example`、`.env.prod.example`、`docs/api.md`、`docs/production-runbook.md`、`frontend/src/api/services.ts` 和 `frontend/src/types.ts`。
- 后端改动至少跑 `cd backend; mvn test`。
- 前端改动至少跑 `cd frontend; npm run build`。
- OCR sidecar 改动至少跑 `cd ocr-service; python3 -m pytest tests`；Compose 配置改动跑对应 `docker compose ... config --quiet`。
