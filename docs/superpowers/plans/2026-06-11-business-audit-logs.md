# 业务审计日志与管理页展示 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增普通用户业务审计日志落库能力，并在后台管理页展示和筛选。

**Architecture:** 新增 `businessaudit` 后端模块，独立于现有 `admin_audit_logs`。业务服务在成功写操作后调用 `BusinessAuditLogService` 记录低敏元数据，后台管理接口分页查询，前端 `AdminView` 在“审计”Tab 内二级切换管理操作和业务操作。

**Tech Stack:** Spring Boot 3.4.1, Java 17, MyBatis-Plus, MySQL/Flyway, Vue 3, TypeScript, Vant, Pinia-free API service layer.

---

### Task 1: 后端业务审计基础设施

**Files:**
- Create: `backend/src/main/java/com/example/expense/businessaudit/entity/BusinessAuditLog.java`
- Create: `backend/src/main/java/com/example/expense/businessaudit/mapper/BusinessAuditLogMapper.java`
- Create: `backend/src/main/java/com/example/expense/businessaudit/dto/BusinessAuditLogResponse.java`
- Create: `backend/src/main/java/com/example/expense/businessaudit/service/BusinessAuditLogService.java`
- Create: `backend/src/test/java/com/example/expense/businessaudit/service/BusinessAuditLogServiceTest.java`
- Modify: `backend/src/main/java/com/example/expense/common/logging/AccessLogFilter.java`

- [x] **Step 1: Write failing tests**

Tests must verify:

- `recordSuccess` inserts `userId/action/targetType/targetId/source/status/requestId`.
- invalid blank values are rejected before insert.
- list query filters by `userId/action/targetType/source` and sorts newest first.
- requestId may be null when there is no servlet request.

Run: `cd backend; mvn -Dtest=BusinessAuditLogServiceTest test`

Expected before implementation: compile failure because businessaudit classes do not exist.

- [x] **Step 2: Implement minimal infrastructure**

Create entity, mapper, DTO, and service. Add `AccessLogFilter.REQUEST_ID_ATTRIBUTE` and set it together with `X-Request-Id`; `BusinessAuditLogService` reads that request attribute from `RequestContextHolder`.

- [x] **Step 3: Verify target tests pass**

Run: `cd backend; mvn -Dtest=BusinessAuditLogServiceTest test`

Expected: PASS.

### Task 2: 数据库与文档联动

**Files:**
- Create: `backend/src/main/resources/db/migration/V10__add_business_audit_logs.sql`
- Modify: `docker/mysql/init/01_schema.sql`
- Modify: `docs/api.md`

- [x] **Step 1: Add schema**

Add `business_audit_logs` with columns and indexes from the design spec.

- [x] **Step 2: Document API**

Document `GET /admin/business-audit-logs`, filters, response fields, and sensitive-data exclusions.

### Task 3: 交易与管理员删除路径

**Files:**
- Modify: `backend/src/main/java/com/example/expense/transaction/service/TransactionService.java`
- Modify: `backend/src/main/java/com/example/expense/admin/service/AdminService.java`
- Modify: `backend/src/test/java/com/example/expense/transaction/service/TransactionServiceTest.java`
- Modify: `backend/src/test/java/com/example/expense/admin/service/AdminServiceTest.java`

- [x] **Step 1: Write failing tests**

Tests must verify:

- transaction create/update/delete records `TRANSACTION_CREATE/UPDATE/DELETE`.
- delete image records `TRANSACTION_IMAGE_DELETE`.
- admin delete calls a skip-business-audit path and only writes admin audit.

Run: `cd backend; mvn -Dtest=TransactionServiceTest,AdminServiceTest test`

Expected before implementation: failures because audit service is not called and skip path does not exist.

- [x] **Step 2: Implement transaction audit hooks**

Inject optional `BusinessAuditLogService`, preserve the existing six-argument constructor used by current unit tests, add `deleteWithoutBusinessAudit` for admin use.

- [x] **Step 3: Verify tests pass**

Run: `cd backend; mvn -Dtest=TransactionServiceTest,AdminServiceTest test`

Expected: PASS.

### Task 4: 其他业务服务审计

**Files:**
- Modify: `backend/src/main/java/com/example/expense/category/service/CategoryService.java`
- Modify: `backend/src/main/java/com/example/expense/payment/service/PaymentMethodService.java`
- Modify: `backend/src/main/java/com/example/expense/platform/service/OnlinePlatformService.java`
- Modify: `backend/src/main/java/com/example/expense/budget/service/BudgetService.java`
- Modify: `backend/src/main/java/com/example/expense/recurring/service/RecurringRuleService.java`
- Modify: `backend/src/main/java/com/example/expense/imports/service/ImportService.java`
- Modify: `backend/src/main/java/com/example/expense/ocr/service/OcrService.java`
- Modify tests for each touched service where constructor injection changes.

- [x] **Step 1: Add focused failing tests for representative services**

Add or update tests for category update, budget create, import job create, and OCR recognize success.

- [x] **Step 2: Implement audit hooks**

Record action/source/target after successful writes. Do not record default seed creation.

- [x] **Step 3: Verify service tests**

Run representative tests, then `cd backend; mvn test`.

Expected: PASS.

### Task 5: 后台业务审计 API

**Files:**
- Modify: `backend/src/main/java/com/example/expense/admin/controller/AdminController.java`
- Create or modify backend controller/security tests.

- [x] **Step 1: Write failing controller/security tests**

Tests must verify admin can query business audit logs, non-admin cannot access `/api/v1/admin/business-audit-logs`, and filters are passed to service.

- [x] **Step 2: Implement endpoint**

Inject `BusinessAuditLogService` into `AdminController` and expose `GET /admin/business-audit-logs`.

- [x] **Step 3: Verify backend tests**

Run: `cd backend; mvn test`

Expected: PASS.

### Task 6: 前端管理页展示

**Files:**
- Modify: `frontend/src/types.ts`
- Modify: `frontend/src/api/services.ts`
- Modify: `frontend/src/views/AdminView.vue`

- [x] **Step 1: Add types and API method**

Add `BusinessAuditLog`, `BusinessAuditLogQuery`, and `adminApi.businessAuditLogs`.

- [x] **Step 2: Update AdminView**

Add an inner audit mode switch for `管理操作 / 业务操作`, business audit filters, loading state, pagination, action/source labels, and requestId display.

- [x] **Step 3: Verify frontend build**

Run: `cd frontend; npm run build`

Expected: PASS.

### Task 7: Final verification and commit

**Files:**
- All touched backend/frontend/docs/schema files.

- [x] **Step 1: Run verification**

Run:

```bash
cd backend; mvn test
cd ../frontend; npm run build
cd ..
git diff --check
git status --short
```

Expected: backend tests pass, frontend build passes, no whitespace errors.

- [ ] **Step 2: Commit**

Commit message must be Chinese:

```bash
git add backend frontend docs docker
git commit -m "添加业务审计日志"
```
