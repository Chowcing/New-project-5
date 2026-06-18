# 日常生活消费记录

移动端优先的日常生活消费记录项目，采用前后端分离的模块化单体架构。

## 技术栈

- 前端：Vue 3、TypeScript、Vite、Vant、Pinia、Vue Router、Axios
- 后端：Spring Boot 3、Java 17、Spring Security、JWT、MyBatis-Plus、Flyway、MySQL
- 数据库：Docker Compose 单独启动 MySQL，后端启动时执行 Flyway 迁移，开发阶段前后端可在 IntelliJ IDEA 中启动调试

## 本地开发启动

1. 复制环境变量示例：

   ```powershell
   Copy-Item .env.example .env
   Copy-Item frontend/.env.example frontend/.env.local
   ```

2. 启动 MySQL：

   ```powershell
   docker compose -f docker-compose.dev.yml up -d
   ```

3. 可选启动本地 OCR 服务：

   ```powershell
   docker compose -f docker-compose.dev.yml --profile ocr up --build -d ocr-service
   ```

   启用图片转文字时，将 `.env` 中 `OCR_ENABLED=true`、`OCR_PROVIDER=local`、`LOCAL_OCR_BASE_URL=http://localhost:9000`，然后重启后端。OCR 容器首次构建会安装 Python 依赖，首次识别会下载并加载 PaddleOCR 模型；如果只是看到 `Uvicorn running on http://0.0.0.0:9000`，说明服务正在前台正常运行，可以另开终端继续启动后端和前端。

4. 在 IntelliJ IDEA 中打开 `backend`，使用 `dev` profile 启动 `ExpenseApplication`。

5. 启动前端：

   ```powershell
   cd frontend
   npm install
   npm run dev
   ```

6. 访问：

   - 前端：http://localhost:5173
   - 后端 API：http://localhost:8080/api/v1
   - Swagger UI：http://localhost:8080/swagger-ui/index.html

## 本地冒烟验证

后端和开发 MySQL 启动后，可以运行本地冒烟脚本验证核心接口链路：

```powershell
.\scripts\smoke-local.ps1
```

脚本会创建临时 `smoke_*` 用户，验证注册登录、默认分类和支付方式、记账、明细日卡片、统计、预算、CSV 导出和退出登录。默认会在结束时按精确测试用户名清理本次创建的数据；如果清理失败，脚本会返回非 0。

## 前端工作台适配验证

工作台布局、预览数量或移动端首屏适配调整后，可以运行 Playwright fit 脚本验证关键元素是否仍然放得下。脚本使用 Playwright bundled Chromium，不依赖系统安装 Google Chrome。

首次运行或 Playwright 浏览器缓存缺失时：

```powershell
cd frontend
npx playwright install chromium
```

启动前端 dev server 后运行：

```powershell
cd frontend
$env:WORKSPACE_FIT_BASE_URL = "http://127.0.0.1:5173"
node tests/workspace-fit.mjs
```

## 前端界面与交互

前端采用移动端优先的 iOS 科技风布局。主导航为四个底部 Tab：`工作台`、`流水`、`分析`、`我的`；`记一笔` 不占用 Tab，通过底部全局浮动按钮和工作台快捷入口进入。

前端 UI 新增和调整需遵循统一规范，详见 `docs/frontend-ui-guidelines.md`。

核心页面分工：

- `工作台`：财务驾驶舱，展示本月净额、收支指标、预算风险、最近流水和周期事项；今日待处理周期实例最多预览 2 条，更多时显示“还有 N 条，查看全部”入口。
- `流水`：支持搜索、筛选、日卡片和时间线两种查看模式；时间线模式滚动后动态显示返回顶部按钮。
- `记一笔`：高频录入控制台，顺序为类型、金额、事项、分类、支付方式，时间、渠道、地点/APP、备注放在补充信息区。
- `分析`：月度/年度统计仪表盘，趋势和占比图可点击跳转到对应流水筛选。
- `我的`：用户信息、常用管理、数据管理和偏好设置入口。

界面偏好保存在 `localStorage` 的 `expense.preferences` 中。当前支持 `appearance`（`system` / `light` / `dark`）和 `accent`（`cyan` / `blue` / `violet`），并兼容旧的暖色主题偏好读取。

选择器、筛选和管理表单类底部弹窗统一使用 `frontend/src/components/BottomSheet.vue`，不要在普通页面直接写 `<van-popup>`；地图选点和管理端详情抽屉是已登记的特殊例外。所有可聚焦输入控件实际字号保持不低于 16px，避免 iOS Safari 在弹出键盘时自动放大页面。

## 高德地图选址

线下记账的“地点”字段支持高德 JS API 地图选址、当前位置定位和地点联想。需要在 `frontend/.env.local` 配置：

```powershell
VITE_AMAP_KEY=你的高德 Web 端 JS API Key
VITE_AMAP_SECURITY_JS_CODE=如已开启安全密钥校验则填写
VITE_AMAP_CITY=可选城市名
```

未配置 `VITE_AMAP_KEY` 时，地点字段仍可手动输入。

当前位置定位需要浏览器授权定位权限；如果权限被拒绝，可继续使用搜索或点击地图选点。

生产 Docker 部署时，高德地图配置写入服务器 `.env` 中的 `VITE_AMAP_KEY`、`VITE_AMAP_SECURITY_JS_CODE`、`VITE_AMAP_CITY`。这些变量会在前端镜像构建时注入，因此修改后需要重新执行前端镜像构建。

## 生产 Docker 启动

1. 在生产机器上准备环境变量：

   ```powershell
   Copy-Item .env.prod.example .env
   ```

   然后编辑 `.env`，必须填入 `MYSQL_ROOT_PASSWORD`、`MYSQL_PASSWORD`、`JWT_SECRET` 和 `EXPENSE_DEPLOYMENT_VERSION`。`JWT_SECRET` 至少 32 个随机 ASCII 字符，不能使用示例占位值。需要启用后台管理时，把已存在的用户名写入 `ADMIN_USERNAMES`，多个用户名用英文逗号分隔。

2. 校验 Compose 配置：

   ```powershell
   docker compose -f docker-compose.prod.yml config --quiet
   ```

3. 构建并启动：

```powershell
docker compose -f docker-compose.prod.yml up --build -d
```

生产环境中前端静态资源由 Nginx 提供，`/api` 会反向代理到后端服务。

正式对公网开放时，应在云负载均衡、Caddy、Nginx 或同类反向代理上启用 HTTPS/TLS，再转发到本项目的 `8088` 端口。Compose 保留 MySQL `3306` 端口映射用于受控运维访问，公网访问必须通过云安全组限制；不要直接用明文 HTTP 暴露登录接口。

交易凭证图片删除后会先软删数据库记录，物理文件默认保留 7 天，再由后端清理任务回收；可通过 `TRANSACTION_IMAGE_RETENTION_DAYS` 调整保留期。

图片转文字默认关闭。生产启用本地 OCR 时，将 `.env` 中 `OCR_ENABLED=true`、`OCR_PROVIDER=local`、`LOCAL_OCR_BASE_URL=http://ocr-service:9000`，并使用 `--profile ocr` 构建和启动 Compose。`ocr-service` 是内部 PaddleOCR sidecar，不要直接对公网开放。

## 自动部署 CD

项目使用 GitHub Actions CD 部署生产环境：合并到 `main` 后，CI 成功会自动通过 SSH 登录服务器，校验本次通过 CI 的提交仍然是 `origin/main` 当前提交，然后执行 `git pull --ff-only origin main`，并按顺序构建后端、构建前端、启动生产 Compose 服务。

需要在 GitHub Actions Secrets 中配置 `CD_SSH_HOST`、`CD_SSH_USER`、`CD_DEPLOY_PATH`，以及 `CD_SSH_PRIVATE_KEY` 或推荐的 `CD_SSH_PRIVATE_KEY_BASE64`，可选配置 `CD_SSH_PORT`。服务器项目目录需保留生产 `.env`，并且部署用户要能非交互执行 `sudo -n docker compose ...`。详细步骤见 `docs/production-runbook.md`。

数据库结构由 Flyway 管理，迁移文件位于 `backend/src/main/resources/db/migration`。后端容器启动时会自动执行未应用的迁移；已有 MySQL volume 首次启用 Flyway 时会 baseline 到版本 0，再执行当前迁移。生产 `.env` 默认保持 `FLYWAY_ENABLED=true` 和 `FLYWAY_BASELINE_VERSION=0`。`docker/mysql/init/01_schema.sql` 只作为全量结构参考，不再由 Compose 自动挂载初始化，避免绕过 Flyway。

## Git 分支策略

- `main`：稳定可发布版本
- `develop`：日常集成分支
- `feature/*`：可选的较大功能或实验分支

## 开发流程规范

日常个人开发默认直接在 `develop` 上完成需求和 bug 修复，提交前完成必要自测后可以直接推送到 `develop`。

较大功能、风险较高的改动或需要隔离验证的实验，可以从 `develop` 拉出 `feature/*` 分支，完成并自测后再合并回 `develop`。

可选分支命名示例：

- `feature/login`
- `feature/add-budget`
- `feature/swagger-fix`

直接在 `develop` 上开发：

```powershell
git checkout develop
git pull
```

开发过程中按功能点提交并推送：

```powershell
git add .
git commit -m "feat: describe your change"
git push
```

需要隔离开发时再创建功能分支：

```powershell
git checkout develop
git pull
git checkout -b feature/your-feature-name
```

功能分支完成后合并回 `develop`：

```powershell
git checkout develop
git pull
git merge feature/your-feature-name
git push
```

当 `develop` 上的功能经过测试并准备发布时，再合并到 `main`。

首次提交建议：

```powershell
git add .
git commit -m "chore: initialize daily expense tracker project"
```
