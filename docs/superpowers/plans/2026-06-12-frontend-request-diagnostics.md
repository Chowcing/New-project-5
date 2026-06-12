# 前端请求排障链路 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让前端请求错误携带并展示可关联后端访问日志的 `requestId`。

**Architecture:** 复用现有 Axios 封装和统一 `showError()`，不逐页改造。请求拦截器生成 `X-Request-Id`，响应错误封装诊断字段，错误工具负责用户提示和低敏控制台输出。

**Tech Stack:** Vue 3, TypeScript, Vite, Axios, Vant.

---

### Task 1: HTTP 请求诊断字段

**Files:**
- Modify: `frontend/src/api/http.ts`

- [x] **Step 1: RED**

运行源码断言，确认当前缺少 `requestId` 诊断链路：

```bash
node -e "const fs=require('fs'); const s=fs.readFileSync('frontend/src/api/http.ts','utf8'); if(!s.includes('requestId')) process.exit(1)"
```

Expected: FAIL.

- [x] **Step 2: Implement**

扩展 `RequestError`，新增请求 ID 生成、响应头读取和低敏诊断字段。

- [x] **Step 3: Verify**

运行：

```bash
node -e "const fs=require('fs'); const s=fs.readFileSync('frontend/src/api/http.ts','utf8'); if(!s.includes('requestId') || !s.includes('X-Request-Id')) process.exit(1)"
```

Expected: PASS.

### Task 2: 统一错误展示

**Files:**
- Modify: `frontend/src/utils/errors.ts`

- [x] **Step 1: Implement**

`showError()` 在存在请求 ID 时追加短格式 `请求ID`，并向控制台输出低敏诊断对象。

- [x] **Step 2: Verify**

运行：

```bash
node -e "const fs=require('fs'); const s=fs.readFileSync('frontend/src/utils/errors.ts','utf8'); if(!s.includes('请求ID') || !s.includes('console.error')) process.exit(1)"
```

Expected: PASS.

### Task 3: Final verification

**Files:**
- All touched frontend/docs files.

- [x] **Step 1: Build**

```bash
cd frontend
npm run build
```

Expected: PASS.

- [ ] **Step 2: Commit**

```bash
git add frontend docs
git commit -m "添加前端请求排障链路"
```
