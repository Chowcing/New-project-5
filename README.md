# 日常生活消费记录

移动端优先的日常生活消费记录项目，采用前后端分离的模块化单体架构。

## 技术栈

- 前端：Vue 3、TypeScript、Vite、Vant、Pinia、Vue Router、Axios
- 后端：Spring Boot 3、Java 17、Spring Security、JWT、MyBatis-Plus、MySQL
- 数据库：Docker Compose 单独启动 MySQL，开发阶段前后端可在 IntelliJ IDEA 中启动调试

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

3. 在 IntelliJ IDEA 中打开 `backend`，使用 `dev` profile 启动 `ExpenseApplication`。

4. 启动前端：

   ```powershell
   cd frontend
   npm install
   npm run dev
   ```

5. 访问：

   - 前端：http://localhost:5173
   - 后端 API：http://localhost:8080/api/v1
   - Swagger UI：http://localhost:8080/swagger-ui/index.html

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

   然后编辑 `.env`，必须填入 `MYSQL_ROOT_PASSWORD`、`MYSQL_PASSWORD` 和 `JWT_SECRET`。`JWT_SECRET` 至少 32 个随机 ASCII 字符，不能使用示例占位值。

2. 校验 Compose 配置：

   ```powershell
   docker compose -f docker-compose.prod.yml config --quiet
   ```

3. 构建并启动：

```powershell
docker compose -f docker-compose.prod.yml up --build -d
```

生产环境中前端静态资源由 Nginx 提供，`/api` 会反向代理到后端服务。

正式对公网开放时，应在云负载均衡、Caddy、Nginx 或同类反向代理上启用 HTTPS/TLS，再转发到本项目的 `8088` 端口。不要直接用明文 HTTP 暴露登录接口。

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
