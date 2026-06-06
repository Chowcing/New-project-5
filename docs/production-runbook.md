# 生产部署与运维手册

本文档记录本项目在阿里云 ECS 上线、更新、Nginx 反向代理、HTTPS 证书和常见问题排查步骤。

## 1. 当前约定

- 服务器系统：Alibaba Cloud Linux 3.2104 LTS 64 位
- 服务器规格：2 vCPU / 2 GiB 内存 / 40 GiB 系统盘 / 3 Mbps 带宽
- 公网 IP：`120.26.150.55`
- 推荐访问域名：`expense.value-vista.top`
- 项目目录：`/opt/expense-tracker`
- 新系统前端容器端口：宿主机 `8088` -> 容器 `80`
- 后端容器端口：容器内 `8080`，不直接暴露公网
- MySQL：容器内 `3306`，Compose 保留宿主机端口映射用于受控运维访问，但公网必须依赖云安全组阻断
- 生产 Compose 文件：
  - 项目内：`docker-compose.prod.yml`
  - 服务器本地覆盖：`docker-compose.server.yml`
- 生产环境变量：服务器本地 `.env`

安全组正式上线后只建议开放：

```text
22    仅允许自己的固定 IP
80    0.0.0.0/0
443   0.0.0.0/0
```

正式上线后关闭公网：

```text
8088
8080
3306
6379
```

## 2. 总体架构

```text
浏览器
  -> https://expense.value-vista.top
  -> 宿主机 Nginx 80/443
  -> http://127.0.0.1:8088
  -> expense-frontend 容器 Nginx
  -> /api/* 反代到 expense-backend:8080
  -> expense-mysql-prod:3306
```

说明：

- 宿主机 Nginx 负责公网入口、域名、HTTPS。
- 项目前端容器里的 Nginx 负责静态文件和 `/api` 到后端的内部反代。
- MySQL `3306` 即使在 Compose 中保留端口映射，也必须通过云安全组限制来源，正式上线不要向公网开放。
- 旧网站不用时，先停止旧容器，不要删除容器、镜像、目录或数据卷。

## 3. 第一次部署

### 3.1 拉取项目

```bash
cd /opt
git clone -b develop 你的仓库地址 expense-tracker
cd /opt/expense-tracker
```

如果已经发布到 `main`：

```bash
git clone -b main 你的仓库地址 expense-tracker
```

不要上传这些本地生成目录或文件：

```text
frontend/node_modules/
frontend/dist/
backend/target/
logs/
.idea/
.env
frontend/.env.local
```

### 3.2 配置 `.env`

```bash
cd /opt/expense-tracker
cp .env.prod.example .env
openssl rand -base64 32
openssl rand -base64 24
openssl rand -base64 24
nano .env
```

填写示例：

```env
MYSQL_ROOT_PASSWORD=强随机密码
MYSQL_DATABASE=expense_tracker
MYSQL_USER=expense_app
MYSQL_PASSWORD=强随机密码
JWT_SECRET=至少32字符随机密钥
JWT_ACCESS_MINUTES=30
JWT_REFRESH_DAYS=14
ADMIN_USERNAMES=管理员用户名，多个用英文逗号分隔
MAIL_FROM=验证码发件邮箱
MAIL_LOCAL_LOG_ENABLED=false
SPRING_MAIL_HOST=SMTP服务器
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=SMTP账号
SPRING_MAIL_PASSWORD=SMTP密码或授权码
SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true
SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true
FLYWAY_ENABLED=true
FLYWAY_BASELINE_VERSION=0
APP_TIME_ZONE=Asia/Shanghai
EXPENSE_DEPLOYMENT_VERSION=部署版本或当前Git短提交
TRANSACTION_IMAGE_DIR=/app/uploads/transaction-images
TRANSACTION_IMAGE_RETENTION_DAYS=7
OCR_ENABLED=false
OCR_PROVIDER=disabled
LOCAL_OCR_BASE_URL=http://ocr-service:9000
VITE_API_BASE_URL=/api/v1
VITE_AMAP_KEY=高德Web端JSAPIKey
VITE_AMAP_SECURITY_JS_CODE=如高德Key启用了安全密钥校验则填写
VITE_AMAP_CITY=可选城市名
```

不要把 `.env` 提交到 Git。

`VITE_*` 是前端构建时变量。修改 `VITE_AMAP_KEY`、`VITE_AMAP_SECURITY_JS_CODE` 或 `VITE_AMAP_CITY` 后，必须重新构建 `frontend` 镜像，单纯重启容器不会生效。

OCR 默认关闭。需要启用本地 OCR 时，将 `.env` 中 `OCR_ENABLED=true`、`OCR_PROVIDER=local`，并使用 `--profile ocr` 启动 Compose。`ocr-service` 使用 Python PaddleOCR，首次构建会安装 Python 依赖，首次识别会下载/加载模型；2 GiB 服务器上建议先确认可用内存，如内存不足，保持 OCR 关闭。

注册和登录 MFA 依赖邮件验证码。生产必须配置 `SPRING_MAIL_HOST`、`SPRING_MAIL_USERNAME`、`SPRING_MAIL_PASSWORD` 和 `MAIL_FROM`，并保持 `MAIL_LOCAL_LOG_ENABLED=false`，避免验证码进入生产日志。本地开发可不配置 SMTP，此时默认通过后端日志输出验证码用于调试。

### 3.3 创建服务器覆盖配置

`docker-compose.server.yml` 是服务器本地文件，不一定要提交到 Git。它用于适配 2 GiB 内存服务器。

```bash
cd /opt/expense-tracker

cat > docker-compose.server.yml <<'EOF'
services:
  mysql:
    command:
      - --character-set-server=utf8mb4
      - --collation-server=utf8mb4_0900_ai_ci
      - --default-time-zone=SYSTEM
      - --innodb-buffer-pool-size=128M
      - --max-connections=30
      - --performance-schema=OFF
    mem_limit: 768m

  backend:
    environment:
      JAVA_TOOL_OPTIONS: "-Xms128m -Xmx384m -Duser.timezone=Asia/Shanghai"
    mem_limit: 512m

  frontend:
    mem_limit: 128m
EOF
```

### 3.4 构建并启动

2 GiB 内存服务器不要并行构建，分开构建：

```bash
cd /opt/expense-tracker

sudo mkdir -p uploads logs/backend
sudo chmod 775 uploads logs logs/backend

export EXPENSE_DEPLOYMENT_VERSION="${EXPENSE_DEPLOYMENT_VERSION:-$(git rev-parse --short=12 HEAD)}"
sudo EXPENSE_DEPLOYMENT_VERSION="$EXPENSE_DEPLOYMENT_VERSION" docker compose -f docker-compose.prod.yml -f docker-compose.server.yml config --quiet

sudo EXPENSE_DEPLOYMENT_VERSION="$EXPENSE_DEPLOYMENT_VERSION" docker compose -f docker-compose.prod.yml -f docker-compose.server.yml build backend
sudo EXPENSE_DEPLOYMENT_VERSION="$EXPENSE_DEPLOYMENT_VERSION" docker compose -f docker-compose.prod.yml -f docker-compose.server.yml build frontend

sudo EXPENSE_DEPLOYMENT_VERSION="$EXPENSE_DEPLOYMENT_VERSION" docker compose -f docker-compose.prod.yml -f docker-compose.server.yml up -d
```

如启用本地 OCR，额外构建并带 profile 启动：

```bash
sudo EXPENSE_DEPLOYMENT_VERSION="$EXPENSE_DEPLOYMENT_VERSION" docker compose -f docker-compose.prod.yml -f docker-compose.server.yml --profile ocr build ocr-service
sudo EXPENSE_DEPLOYMENT_VERSION="$EXPENSE_DEPLOYMENT_VERSION" docker compose -f docker-compose.prod.yml -f docker-compose.server.yml --profile ocr up -d
```

### 3.5 验证容器

```bash
sudo docker compose -f docker-compose.prod.yml -f docker-compose.server.yml ps
curl -I http://127.0.0.1:8088/
curl -i http://127.0.0.1:8088/api/v1/auth/me
sudo docker compose -f docker-compose.prod.yml -f docker-compose.server.yml --profile ocr exec ocr-service curl -s http://127.0.0.1:9000/health
```

预期：

- 首页返回 `200 OK`
- `/api/v1/auth/me` 未登录时返回 `401`
- 如果 `/api` 返回 `502`，优先看后端和 MySQL 状态
- 未启用本地 OCR 时，`ocr-service` 不会启动，跳过 OCR 健康检查。

### 3.6 OCR 排障

本地 OCR 需要同时满足三点：

- 后端环境变量为 `OCR_ENABLED=true`、`OCR_PROVIDER=local`
- 后端可访问 `LOCAL_OCR_BASE_URL`，生产 Compose 内通常是 `http://ocr-service:9000`
- `ocr-service` 已启动，`/health` 返回 `{"status":"ok"}`

如果前端提示“图片转文字功能未启用”，先检查后端进程是否读取到最新 `.env`，修改环境变量后必须重启后端。

如果前端提示“本地图片转文字服务不可用”，先区分是 OCR sidecar 失败还是后端转发失败：

```bash
sudo docker compose -f docker-compose.prod.yml -f docker-compose.server.yml --profile ocr logs --tail=80 ocr-service
sudo docker compose -f docker-compose.prod.yml -f docker-compose.server.yml --profile ocr exec ocr-service curl -s http://127.0.0.1:9000/health
```

OCR 容器日志中如果只有 `Uvicorn running on http://0.0.0.0:9000`，不是卡死，而是服务在前台正常等待请求。首次识别下载模型耗时较长，之后会复用已加载模型。

后端 OCR 客户端已固定使用普通 HTTP/1.1 请求工厂，避免 Java HTTP Client 对 Uvicorn 发起 `h2c` 升级导致 FastAPI 返回 `422 Unprocessable Entity`。如再次看到 OCR 容器日志里有 `Unsupported upgrade request` 或 `Invalid HTTP request received`，优先确认运行中的后端镜像或进程是否已经更新到包含该修复的版本。

## 4. 旧网站处理

服务器曾有旧网站容器：

```text
finance-nginx
finance-backend
finance-mysql
finance-redis
```

新系统验证正常后，旧网站不用时先停止，不删除：

```bash
sudo docker stop finance-nginx
sudo docker stop finance-backend
sudo docker stop finance-redis
sudo docker stop finance-mysql
```

如需回滚旧网站：

```bash
sudo docker start finance-nginx finance-backend finance-mysql finance-redis
```

## 5. Nginx 反向代理

### 5.1 为什么用宿主机 Nginx

当前服务器已经安装宿主机 Nginx，并且 Certimate 运行在宿主机上。宿主机 Nginx 接管 `80/443` 更简单：

- 证书路径直接读取宿主机文件
- 新项目容器不用改
- 切换和回滚更直观
- 第一次上线更容易排查

项目容器中的前端 Nginx 仍然存在，负责静态文件和内部 `/api` 转发。

### 5.2 conf.d 多个配置文件如何生效

Nginx 通常在 `/etc/nginx/nginx.conf` 中包含：

```nginx
include /etc/nginx/conf.d/*.conf;
```

因此 `/etc/nginx/conf.d/` 下所有 `.conf` 都会加载，不是只加载一个。

确认命令：

```bash
sudo grep -n "include.*conf.d" /etc/nginx/nginx.conf
sudo nginx -T | grep -n "server_name\|listen"
```

如果旧 `.conf` 不再使用，可以改名为 `.bak` 禁用：

```bash
sudo mv /etc/nginx/conf.d/旧配置.conf /etc/nginx/conf.d/旧配置.conf.bak
sudo nginx -t
sudo systemctl reload nginx
```

### 5.3 HTTP 配置

启动宿主机 Nginx：

```bash
sudo systemctl enable --now nginx
```

创建或编辑：

```bash
sudo nano /etc/nginx/conf.d/expense-tracker.conf
```

HTTP 初始配置：

```nginx
server {
    listen 80;
    server_name expense.value-vista.top;
    client_max_body_size 20m;

    location / {
        proxy_pass http://127.0.0.1:8088;
        proxy_http_version 1.1;

        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

检查并重载：

```bash
sudo nginx -t
sudo systemctl reload nginx
```

测试：

```bash
curl -I http://expense.value-vista.top
```

## 6. HTTPS 证书配置

### 6.1 确认证书路径

如果不知道 Certimate 证书在哪里，查询：

```bash
sudo find / -name "*value-vista*" 2>/dev/null
sudo find / -name "*.pem" 2>/dev/null | grep -i "value-vista\|expense"
sudo find / -name "*.key" 2>/dev/null | grep -i "value-vista\|expense"
```

需要区分：

- 证书链文件：通常叫 `fullchain.pem`、`cert.pem`、`*.pem`
- 私钥文件：通常叫 `privkey.pem`、`*.key`

建议统一放到：

```bash
sudo mkdir -p /etc/nginx/ssl/expense.value-vista.top
```

复制示例：

```bash
sudo cp /证书实际路径/fullchain.pem /etc/nginx/ssl/expense.value-vista.top/fullchain.pem
sudo cp /私钥实际路径/privkey.pem /etc/nginx/ssl/expense.value-vista.top/privkey.pem
sudo chmod 600 /etc/nginx/ssl/expense.value-vista.top/privkey.pem
```

### 6.2 HTTPS Nginx 配置

编辑：

```bash
sudo nano /etc/nginx/conf.d/expense-tracker.conf
```

配置：

```nginx
server {
    listen 80;
    server_name expense.value-vista.top;
    client_max_body_size 20m;

    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    server_name expense.value-vista.top;
    client_max_body_size 20m;

    ssl_certificate /etc/nginx/ssl/expense.value-vista.top/fullchain.pem;
    ssl_certificate_key /etc/nginx/ssl/expense.value-vista.top/privkey.pem;

    ssl_session_cache shared:SSL:10m;
    ssl_session_timeout 10m;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_prefer_server_ciphers off;

    location / {
        proxy_pass http://127.0.0.1:8088;
        proxy_http_version 1.1;

        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
    }
}
```

检查并重载：

```bash
sudo nginx -t
sudo systemctl reload nginx
```

确认公网入口上传体积限制已生效。下面的 6MB 请求即使未登录，也应先到达后端并返回 `401`；如果返回 `413 Request Entity Too Large`，说明当前生效的 Nginx `server` 块没有配置 `client_max_body_size 20m`，手机相册照片上传会在进入应用前失败。

```bash
truncate -s 6M /tmp/expense-upload-check.jpg
printf '\xff\xd8\xff\x01' | dd of=/tmp/expense-upload-check.jpg bs=1 count=4 conv=notrunc
curl -i https://expense.value-vista.top/api/v1/transactions/1/images \
  -F 'images=@/tmp/expense-upload-check.jpg;type=image/jpeg'
```

测试：

```bash
curl -I https://expense.value-vista.top
```

浏览器访问：

```text
https://expense.value-vista.top
```

## 7. GitHub Actions CD

项目已接入 GitHub Actions CD：当代码合并到 `main` 且 CI 成功后，`.github/workflows/cd.yml` 会通过 SSH 登录生产服务器，校验本次部署提交仍然是 `origin/main` 当前提交，然后执行与手动发布一致的更新命令。也可以在 GitHub Actions 页面手动触发 `CD` 工作流，手动触发时必须选择 `main` 分支。

### 7.1 GitHub Secrets

在仓库 `Settings` -> `Secrets and variables` -> `Actions` 中配置：

- `CD_SSH_HOST`：生产服务器 IP 或域名，例如 `120.26.150.55`
- `CD_SSH_PORT`：SSH 端口，可不填，默认 `22`
- `CD_SSH_USER`：用于部署的 SSH 用户
- `CD_SSH_PRIVATE_KEY_BASE64`：该用户的 SSH 私钥 base64，推荐使用
- `CD_SSH_PRIVATE_KEY`：该用户的 SSH 私钥；如果配置了 `CD_SSH_PRIVATE_KEY_BASE64`，可以不填
- `CD_DEPLOY_PATH`：服务器项目目录，例如 `/opt/expense-tracker`

私钥必须是 OpenSSH 私钥，通常以 `-----BEGIN OPENSSH PRIVATE KEY-----` 开头，不能是 `.pub` 公钥，也不能是 PuTTY 的 `.ppk` 文件。建议使用不带密码的部署专用密钥。

生成部署密钥示例：

```bash
ssh-keygen -t ed25519 -C "expense-cd" -f ~/.ssh/expense_cd -N ""
```

把公钥加入服务器部署用户的 `~/.ssh/authorized_keys`：

```bash
cat ~/.ssh/expense_cd.pub
```

然后把私钥转成 base64，填到 GitHub Secret `CD_SSH_PRIVATE_KEY_BASE64`。

Linux/macOS：

```bash
base64 -w 0 ~/.ssh/expense_cd
```

Windows PowerShell：

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("$env:USERPROFILE\.ssh\expense_cd"))
```

工作流使用 GitHub Environment：`production`。如果需要发布审批，可以在仓库 `Environments` 中给 `production` 配置 required reviewers。

### 7.2 服务器前置条件

生产目录必须已经完成第一次部署，并满足：

- `CD_DEPLOY_PATH` 指向项目目录，目录内保留生产 `.env`
- 当前分支为 `main`，可以执行 `git pull --ff-only origin main`
- `docker-compose.prod.yml` 和 `docker-compose.server.yml` 都存在
- 部署用户可以非交互执行 `sudo -n docker compose ...`

如果生产目录之前克隆的是 `develop`，上线 CD 前先在服务器切到 `main`：

```bash
cd /opt/expense-tracker
git fetch origin main
git checkout main
git pull --ff-only origin main
```

验证部署用户的无密码 Docker 权限：

```bash
sudo -n docker compose version
```

如果该命令提示需要密码，需要给部署用户配置受控的无密码 sudo。先确认 docker 路径：

```bash
command -v docker
```

然后用 `visudo` 添加规则，下面示例假设部署用户是 `deploy`，docker 路径是 `/usr/bin/docker`：

```bash
sudo visudo -f /etc/sudoers.d/expense-cd
```

写入：

```text
deploy ALL=(root) NOPASSWD: /usr/bin/docker compose *
```

### 7.3 自动部署内容

CD 工作流会按顺序执行：

```bash
cd /opt/expense-tracker
git fetch origin main
test "$(git rev-parse origin/main)" = "本次通过 CI 的提交 SHA"
git pull --ff-only origin main

sudo -n docker compose -f docker-compose.prod.yml -f docker-compose.server.yml config --quiet
sudo -n docker compose -f docker-compose.prod.yml -f docker-compose.server.yml build --progress=plain backend
sudo -n docker compose -f docker-compose.prod.yml -f docker-compose.server.yml build --progress=plain frontend
sudo -n docker compose -f docker-compose.prod.yml -f docker-compose.server.yml up -d
```

说明：

- CD 负责拉代码、构建镜像和重启容器；后端启动时由 Flyway 自动执行数据库迁移。
- CD 的 SSH 会话启用了 keepalive；镜像构建使用 plain 日志，避免长时间静默构建导致连接被断开。
- 新增表、索引、字段时，先提交新的 `backend/src/main/resources/db/migration/V*.sql`，再正常发布。
- 首次接入已有生产库时，Flyway 会 baseline 到版本 0，并继续执行当前迁移；`FLYWAY_BASELINE_VERSION` 正常保持 `0`，只在明确的恢复方案中调整。
- `docker/mysql/init/01_schema.sql` 只作为全量结构参考，不再由 Compose 挂载到 `/docker-entrypoint-initdb.d`；空库和既有库都由后端 Flyway 迁移负责建表/升级。`docker/mysql/manual/20260516_add_recurring_tables.sql` 仅保留作应急手工参考。

部署后会检查：

- 首页 `http://127.0.0.1:8088/` 可访问
- 未登录访问 `/api/v1/auth/me` 返回 `401`

探活会最多等待 3 分钟。刚重建容器时，前端 Nginx 可能先启动，后端 Spring Boot 仍在初始化；这段时间 `/api/v1/auth/me` 可能短暂返回 `502`，属于正常启动窗口。

如果多个 `main` 提交的 CI/CD 重叠运行，较早的 CD 发现 `origin/main` 已前进时会失败退出，等待最新提交通过 CI 后由新的 CD 运行部署。

CD 不会执行 `docker compose down -v`，不会删除生产 MySQL volume，也不会改写服务器 `.env`。

### 7.4 数据库迁移验证

后端启动时会自动执行 Flyway 迁移。已有生产卷不需要再手工执行周期记账 SQL，也不要删除或重建 MySQL volume；首次接入 Flyway 后，数据库会多出 `flyway_schema_history` 表记录迁移状态。

部署后可以在服务器查看迁移历史：

```bash
cd /opt/expense-tracker
set -a
. ./.env
set +a
sudo -n docker compose -f docker-compose.prod.yml -f docker-compose.server.yml exec -T mysql \
  sh -lc 'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE" -e "SELECT installed_rank, version, description, success FROM flyway_schema_history ORDER BY installed_rank;"'
```

如果需要临时阻止自动迁移，可在生产 `.env` 设置 `FLYWAY_ENABLED=false` 后重建后端容器。正常发布应保持启用，且 `FLYWAY_BASELINE_VERSION=0`。

## 8. 手动更新发布

每次本地修改代码后：

1. 本地提交并推送到 Git。
2. 服务器拉取最新代码。
3. 服务器重新构建并启动容器。

服务器执行：

```bash
cd /opt/expense-tracker
git pull --ff-only

export EXPENSE_DEPLOYMENT_VERSION="${EXPENSE_DEPLOYMENT_VERSION:-$(git rev-parse --short=12 HEAD)}"
sudo EXPENSE_DEPLOYMENT_VERSION="$EXPENSE_DEPLOYMENT_VERSION" docker compose -f docker-compose.prod.yml -f docker-compose.server.yml build --progress=plain backend
sudo EXPENSE_DEPLOYMENT_VERSION="$EXPENSE_DEPLOYMENT_VERSION" docker compose -f docker-compose.prod.yml -f docker-compose.server.yml build --progress=plain frontend
sudo EXPENSE_DEPLOYMENT_VERSION="$EXPENSE_DEPLOYMENT_VERSION" docker compose -f docker-compose.prod.yml -f docker-compose.server.yml up -d
```

说明：

- `git pull --ff-only`：安全拉取最新代码，避免服务器生成合并提交。
- `EXPENSE_DEPLOYMENT_VERSION`：统一注入前端“我的”页、后端 `X-Expense-Deployment` 响应头和访问日志；未手动设置时使用当前 git 短提交号，并在 `sudo docker compose` 调用中显式传入。
- `build backend`：重新构建后端镜像。
- `build frontend`：重新构建前端镜像。
- `up -d`：后台启动，并用新镜像替换旧容器。
- 如果这次发布涉及数据库结构变化，确认已经提交对应的 Flyway 迁移文件，后端启动时会自动应用。

2 GiB 内存服务器不建议使用一次性并行构建。

如果只修改了高德地图配置，也需要执行：

```bash
cd /opt/expense-tracker
sudo docker compose -f docker-compose.prod.yml -f docker-compose.server.yml build frontend
sudo docker compose -f docker-compose.prod.yml -f docker-compose.server.yml up -d frontend
```

## 9. 常用排查命令

### 9.1 查看端口占用

```bash
sudo ss -lntp | grep -E ':80|:443|:8088|:8080|:3306|:6379'
```

### 9.2 查看容器状态

```bash
cd /opt/expense-tracker
sudo docker compose -f docker-compose.prod.yml -f docker-compose.server.yml ps
sudo docker ps
```

### 9.3 查看日志

```bash
sudo docker logs --tail=200 expense-backend
sudo docker logs --tail=200 expense-mysql-prod
sudo docker logs --tail=200 expense-frontend
```

或：

```bash
sudo docker compose -f docker-compose.prod.yml -f docker-compose.server.yml logs --tail=100 backend
sudo docker compose -f docker-compose.prod.yml -f docker-compose.server.yml logs --tail=100 mysql
sudo docker compose -f docker-compose.prod.yml -f docker-compose.server.yml logs --tail=100 frontend
```

如果上传凭证图片时报“图片保存失败”，优先看后端日志中的 `交易图片保存失败`，确认 `imageRoot`、`targetPath` 和具体 `IOException`。同时检查生产目录下 `uploads/` 是否存在且可写，确认 compose 中 `./uploads:/app/uploads` 挂载仍然生效。

删除图片或删除流水后，后端会先软删数据库图片记录，物理文件默认保留 7 天，再由每天 `03:30 Asia/Shanghai` 的清理任务逐个明确路径删除。需要缩短或延长保留期时，调整 `.env` 中的 `TRANSACTION_IMAGE_RETENTION_DAYS` 后重启后端容器。

### 9.4 判断后端是否重启

```bash
sudo docker inspect expense-backend --format='RestartCount={{.RestartCount}} ExitCode={{.State.ExitCode}} StartedAt={{.State.StartedAt}}'
```

### 9.5 测试前端和 API

```bash
curl -I http://127.0.0.1:8088/
curl -i http://127.0.0.1:8088/api/v1/auth/me
curl -I http://expense.value-vista.top
curl -I https://expense.value-vista.top
```

预期：

- 首页 `200 OK`
- 未登录访问 `/api/v1/auth/me` 返回 `401`
- `502 Bad Gateway` 通常说明 Nginx 反代不到后端

### 9.6 从前端容器测试后端

```bash
sudo docker exec expense-frontend wget -S -O- http://backend:8080/api/v1/auth/me
```

预期未登录返回 `401`。

### 9.7 查看资源

```bash
free -h
df -h
sudo docker stats
```

## 10. 常见问题

### 10.1 MySQL 状态 `Restarting (137)`

通常是内存不足或被 OOM 杀掉。

处理：

1. 停止旧网站不用的容器。
2. 降低 MySQL 内存参数。
3. 确认 swap 存在。

查看 swap：

```bash
free -h
```

如果没有 swap，可创建 2 GiB swap：

```bash
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

### 10.2 `/api` 返回 `502 Bad Gateway`

排查顺序：

```bash
sudo docker compose -f docker-compose.prod.yml -f docker-compose.server.yml ps
sudo docker logs --tail=200 expense-backend
sudo docker logs --tail=200 expense-frontend
sudo docker exec expense-frontend wget -S -O- http://backend:8080/api/v1/auth/me
```

常见原因：

- 后端还没启动完成
- 后端反复重启
- MySQL 未 healthy
- 前端 Nginx 不能访问 `backend:8080`

### 10.3 Nginx 修改后不生效

检查：

```bash
sudo nginx -t
sudo systemctl reload nginx
sudo nginx -T | grep -n "server_name\|listen"
```

如果 `/etc/nginx/conf.d/` 有多个 `.conf`，确认没有两个文件配置同一个 `server_name`。

### 10.4 证书路径不对

`nginx -t` 会报类似：

```text
cannot load certificate
No such file or directory
```

重新查证书路径：

```bash
sudo find / -name "*value-vista*" 2>/dev/null
```

然后修正：

```nginx
ssl_certificate 正确的证书链路径;
ssl_certificate_key 正确的私钥路径;
```

## 11. 数据备份

建议定期备份 MySQL。

手动备份：

```bash
cd /opt/expense-tracker
set -a
. ./.env
set +a
mkdir -p "$HOME/expense-backups"
sudo docker compose -f docker-compose.prod.yml -f docker-compose.server.yml exec -T mysql \
  mysqldump -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE" \
  > "$HOME/expense-backups/expense_$(date +%F_%H%M%S).sql"
```

注意：

- 不要执行 `docker compose down -v`
- 不要删除 `expense_mysql_prod_data` volume
- 不要随意删除 `/opt/expense-tracker/.env`

## 12. 需要向助手提供的信息

如果后续继续排查，把对应命令输出发给助手即可。

### Nginx/HTTPS 问题

```bash
sudo nginx -t
sudo nginx -T | grep -n "server_name\|listen\|ssl_certificate"
sudo ls -l /etc/nginx/conf.d
sudo ls -l /etc/nginx/ssl/expense.value-vista.top
```

### 容器/API 问题

```bash
cd /opt/expense-tracker
sudo docker compose -f docker-compose.prod.yml -f docker-compose.server.yml ps
sudo docker logs --tail=200 expense-backend
sudo docker logs --tail=200 expense-mysql-prod
sudo docker logs --tail=200 expense-frontend
```

### 资源不足问题

```bash
free -h
df -h
sudo docker stats --no-stream
sudo docker ps
```

### 端口冲突问题

```bash
sudo ss -lntp | grep -E ':80|:443|:8088|:8080|:3306|:6379|:8090'
sudo systemctl status nginx --no-pager
sudo docker ps
```
