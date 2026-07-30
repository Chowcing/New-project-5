# 流水回收站实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为个人流水实现可恢复回收站、手动永久删除/清空、用户级 `1–365` 天保留期设置，以及每日自动软删除到期记录。

**Architecture:** 使用 `transactions.trashed_at` 与现有逻辑删除列 `deleted` 表达正常、回收站、软删除三态；使用 `users.trash_retention_days` 保存当前用户保留期。正常查询统一排除回收站记录，回收站状态流转由 `TransactionService` 编排，自动清理由独立调度服务按固定批次扫描并调用事务方法逐条重验。

**Tech Stack:** Spring Boot 3.4.1、Java 17、MyBatis-Plus、MyBatis XML、Flyway、MySQL 8.4、H2 测试库、JUnit 5、Mockito、Vue 3、TypeScript、Vite、Vant、Playwright。

## Global Constraints

- 当前分支为 `develop`；保留与本功能无关的本地改动，不回滚、覆盖或清理。
- 手工编辑文件优先使用 `apply_patch`；禁止批量删除文件或目录。
- 每个实现提交使用中文提交信息。
- 所有业务查询和状态更新必须按 `SecurityUtils.currentUserId()` 得到的 `userId` 隔离；跨用户访问统一返回“记录不存在”。
- 正常流水的统一条件为 `deleted = 0 AND trashed_at IS NULL`；回收站条件为 `deleted = 0 AND trashed_at IS NOT NULL`。
- 用户删除只移入回收站；只有逐条永久删除、清空、自动清理或管理员删除才设置 `deleted = 1`。
- 回收站保留期为固定 `1–365` 天整数，默认 `30` 天；快捷项为 `7、15、30、90、180、365` 天。
- 修改保留期同时作用于回收站已有记录和未来记录；缩短设置后不立即删除，下一次自动清理处理到期记录。
- 自动清理每日 `03:00` 按 `app.time-zone` 执行，每批最多 `200` 条；图片物理文件仍由现有 `03:30` 延迟清理任务处理。
- 移入或恢复不得软删图片；永久删除、清空、自动清理和管理员删除先软删图片记录，不直接删除物理文件。
- 日志和审计不得记录金额、备注、OCR 文本、凭证内容、密码或 token。
- 前端使用现有主题 token、`BottomSheet`、安全返回逻辑和“图标 + 文本”；所有可聚焦输入实际字号不低于 `16px`。
- 后端最终必须运行 `cd backend && mvn test`；前端最终必须运行 `cd frontend && npm run check:ui && npm run build`，并运行新增回收站 Playwright 回归。

---

## 文件结构

### 新增文件

- `backend/src/main/resources/db/migration/V13__add_transaction_recycle_bin.sql`：增加三态字段、用户保留期和回收站索引。
- `backend/src/main/java/com/example/expense/transaction/dto/TrashedTransactionResponse.java`：回收站列表专用响应，不暴露图片 URL。
- `backend/src/main/java/com/example/expense/transaction/dto/TrashClearResponse.java`：清空结果。
- `backend/src/main/java/com/example/expense/transaction/dto/ExpiredTrashCandidate.java`：自动清理候选 ID。
- `backend/src/main/java/com/example/expense/user/dto/RecycleBinSettingsRequest.java`：保留期更新请求。
- `backend/src/main/java/com/example/expense/user/dto/RecycleBinSettingsResponse.java`：保留期响应。
- `backend/src/main/java/com/example/expense/user/service/RecycleBinSettingsService.java`：读取和更新用户保留期。
- `backend/src/main/java/com/example/expense/transaction/service/TransactionTrashCleanupService.java`：按游标分批扫描并继续处理单条失败后的候选。
- `backend/src/main/java/com/example/expense/transaction/dto/TrashCleanupResult.java`：自动清理成功/失败计数。
- `backend/src/main/java/com/example/expense/transaction/scheduler/TransactionTrashCleanupScheduler.java`：每日 `03:00` 调度入口。
- `backend/src/test/java/com/example/expense/user/service/RecycleBinSettingsServiceTest.java`
- `backend/src/test/java/com/example/expense/user/controller/UserControllerTest.java`
- `backend/src/test/java/com/example/expense/transaction/service/TransactionTrashCleanupServiceTest.java`
- `backend/src/test/java/com/example/expense/transaction/scheduler/TransactionTrashCleanupSchedulerTest.java`
- `frontend/src/views/TrashView.vue`：回收站列表、恢复、永久删除、清空和保留期设置。
- `frontend/tests/recycle-bin-ui.mjs`：真实浏览器回归。

### 主要修改文件

- 数据：`docker/mysql/init/01_schema.sql`、`backend/src/test/resources/schema.sql`
- 实体/映射：`ExpenseUser.java`、`ExpenseTransaction.java`、`TransactionMapper.java`、`TransactionMapper.xml`、`StatisticsMapper.xml`、`AdminMapper.xml`
- 服务/API：`TransactionService.java`、`TransactionImageService.java`、`TransactionController.java`、`UserController.java`、`AdminService.java`、`OnlinePlatformService.java`
- 后端测试：`TransactionMapperTest.java`、`TransactionServiceTest.java`、`TransactionControllerTest.java`、`TransactionImageServiceTest.java`、`AdminServiceTest.java`
- 前端：`types.ts`、`api/services.ts`、`router/index.ts`、`SettingsView.vue`、`RecordsView.vue`、`TransactionDetailView.vue`、`package.json`
- 文档：`docs/api.md`、`docs/production-runbook.md`

---

### Task 1: 建立三态数据模型并统一正常流水可见性

**Files:**
- Create: `backend/src/main/resources/db/migration/V13__add_transaction_recycle_bin.sql`
- Modify: `backend/src/test/resources/schema.sql`
- Modify: `docker/mysql/init/01_schema.sql`
- Modify: `backend/src/main/java/com/example/expense/user/entity/ExpenseUser.java`
- Modify: `backend/src/main/java/com/example/expense/transaction/entity/ExpenseTransaction.java`
- Modify: `backend/src/main/resources/mapper/TransactionMapper.xml`
- Modify: `backend/src/main/resources/mapper/StatisticsMapper.xml`
- Modify: `backend/src/main/resources/mapper/AdminMapper.xml`
- Modify: `backend/src/main/java/com/example/expense/transaction/service/TransactionService.java`
- Modify: `backend/src/main/java/com/example/expense/transaction/service/TransactionImageService.java`
- Modify: `backend/src/main/java/com/example/expense/platform/service/OnlinePlatformService.java`
- Modify: `backend/src/main/java/com/example/expense/admin/service/AdminService.java`
- Test: `backend/src/test/java/com/example/expense/transaction/mapper/TransactionMapperTest.java`

**Interfaces:**
- Produces: `ExpenseTransaction.getTrashedAt()/setTrashedAt(LocalDateTime)`
- Produces: `ExpenseUser.getTrashRetentionDays()/setTrashRetentionDays(Integer)`
- Produces: the invariant `deleted = 0 AND trashed_at IS NULL` for every non-trash query.

- [ ] **Step 1: Extend the H2 fixture and write failing visibility assertions**

First add `trash_retention_days SMALLINT NOT NULL DEFAULT 30` to the test `users` table and `trashed_at TIMESTAMP` to the test `transactions` table. In `TransactionMapperTest.setUp()`, insert a fourth current-user record and mark only that row as trashed:

```java
insertTransaction(
        104L,
        USER_ID,
        "EXPENSE",
        "回收站午餐",
        new BigDecimal("66.00"),
        DAY_14_MID,
        "OFFLINE",
        "",
        "食堂",
        WECHAT_METHOD_ID,
        "微信",
        EXPENSE_CATEGORY_ID,
        "不应计入");
jdbcTemplate.update(
        "UPDATE transactions SET trashed_at = ? WHERE id = ?",
        Timestamp.valueOf(LocalDateTime.of(2026, 5, 15, 9, 0)),
        104L);
```

Expand the test mapper scan to include statistics and admin mappers, inject them, and add:

```java
@Test
void trashedRecordsAreExcludedFromNormalTransactionStatisticsRecommendationAndAdminQueries() {
    assertThat(transactionMapper.countRecords(
            USER_ID, null, null, null, null, null, null, null))
            .isEqualTo(3L);
    assertThat(transactionMapper.selectRecord(USER_ID, 104L)).isNull();
    assertThat(transactionMapper.selectRecommendationAggregates(
            USER_ID,
            null,
            null,
            LocalDateTime.of(2026, 5, 20, 12, 0),
            720,
            4))
            .noneMatch(row -> row.getLatestTransactionId().equals(104L));

    MonthlyTotals totals = statisticsMapper.selectMonthlyTotals(
            USER_ID,
            LocalDateTime.of(2026, 5, 1, 0, 0),
            LocalDateTime.of(2026, 6, 1, 0, 0));
    assertThat(totals.getTransactionCount()).isEqualTo(3L);
    assertThat(adminMapper.countTransactions(
            USER_ID, null, null, null, null, null))
            .isEqualTo(3L);
    assertThat(adminMapper.selectTransactionDetail(104L)).isNull();
}
```

- [ ] **Step 2: Run the visibility test and verify RED**

Run:

```bash
cd backend
mvn -Dtest=TransactionMapperTest test
```

Expected: FAIL because current transaction/statistics/admin SQL still includes the `trashed_at` row.

- [ ] **Step 3: Add the migration, production schemas and entity fields**

Use this migration:

```sql
ALTER TABLE users
  ADD COLUMN trash_retention_days SMALLINT NOT NULL DEFAULT 30 AFTER status,
  ADD CONSTRAINT chk_users_trash_retention_days
    CHECK (trash_retention_days BETWEEN 1 AND 365);

ALTER TABLE transactions
  ADD COLUMN trashed_at DATETIME NULL AFTER note,
  ADD INDEX idx_transactions_user_trash_time
    (user_id, deleted, trashed_at, id);
```

Apply the same definitions to `docker/mysql/init/01_schema.sql`. Add:

```java
private Integer trashRetentionDays;
```

to `ExpenseUser`, and:

```java
private LocalDateTime trashedAt;
```

to `ExpenseTransaction`.

Add the same `CHECK (trash_retention_days BETWEEN 1 AND 365)` constraint to the MySQL initialization schema and H2 test fixture so direct database writes cannot create an out-of-range setting.

- [ ] **Step 4: Exclude trash in every normal query**

Add `AND t.trashed_at IS NULL` or `AND trashed_at IS NULL` beside every transaction `deleted = 0` condition in `TransactionMapper.xml`, `StatisticsMapper.xml` and all active-transaction sections of `AdminMapper.xml`, including joins used for user counts and detail statistics.

Add `.isNull(ExpenseTransaction::getTrashedAt)` to:

```java
TransactionService.existsSameTransaction(...)
TransactionService.requireOwned(...)
TransactionImageService.requireOwnedTransaction(...)
TransactionImageService.requireExistingTransaction(...)
OnlinePlatformService.referenceCount(...)
AdminService.deleteTransaction(...)
```

Do not add the active condition to future trash-specific mapper methods.

- [ ] **Step 5: Run the mapper and focused service tests**

Run:

```bash
cd backend
mvn -Dtest=TransactionMapperTest,TransactionImageServiceTest,OnlinePlatformServiceTest,AdminServiceTest test
```

Expected: PASS.

- [ ] **Step 6: Commit the data lifecycle foundation**

```bash
git add backend/src/main/resources/db/migration/V13__add_transaction_recycle_bin.sql \
  backend/src/test/resources/schema.sql \
  docker/mysql/init/01_schema.sql \
  backend/src/main/java/com/example/expense/user/entity/ExpenseUser.java \
  backend/src/main/java/com/example/expense/transaction/entity/ExpenseTransaction.java \
  backend/src/main/resources/mapper/TransactionMapper.xml \
  backend/src/main/resources/mapper/StatisticsMapper.xml \
  backend/src/main/resources/mapper/AdminMapper.xml \
  backend/src/main/java/com/example/expense/transaction/service/TransactionService.java \
  backend/src/main/java/com/example/expense/transaction/service/TransactionImageService.java \
  backend/src/main/java/com/example/expense/platform/service/OnlinePlatformService.java \
  backend/src/main/java/com/example/expense/admin/service/AdminService.java \
  backend/src/test/java/com/example/expense/transaction/mapper/TransactionMapperTest.java
git commit -m "功能：建立流水回收站数据状态"
```

---

### Task 2: 实现移入、列表、恢复、永久删除和清空接口

**Files:**
- Create: `backend/src/main/java/com/example/expense/transaction/dto/TrashedTransactionResponse.java`
- Create: `backend/src/main/java/com/example/expense/transaction/dto/TrashClearResponse.java`
- Modify: `backend/src/main/java/com/example/expense/transaction/mapper/TransactionMapper.java`
- Modify: `backend/src/main/resources/mapper/TransactionMapper.xml`
- Modify: `backend/src/main/java/com/example/expense/transaction/service/TransactionService.java`
- Modify: `backend/src/main/java/com/example/expense/transaction/service/TransactionImageService.java`
- Modify: `backend/src/main/java/com/example/expense/transaction/controller/TransactionController.java`
- Modify: `backend/src/main/java/com/example/expense/admin/service/AdminService.java`
- Test: `backend/src/test/java/com/example/expense/transaction/mapper/TransactionMapperTest.java`
- Test: `backend/src/test/java/com/example/expense/transaction/service/TransactionServiceTest.java`
- Test: `backend/src/test/java/com/example/expense/transaction/controller/TransactionControllerTest.java`
- Test: `backend/src/test/java/com/example/expense/transaction/service/TransactionImageServiceTest.java`
- Test: `backend/src/test/java/com/example/expense/admin/service/AdminServiceTest.java`

**Interfaces:**
- Produces: `PageResponse<TrashedTransactionResponse> listTrash(Long userId, int page, int size)`
- Produces: `void delete(Long userId, Long id)` with move-to-trash semantics.
- Produces: `TransactionResponse restore(Long userId, Long id)`
- Produces: `void permanentlyDelete(Long userId, Long id)`
- Produces: `TrashClearResponse clearTrash(Long userId)`
- Produces: `void deleteWithoutBusinessAudit(Long userId, Long id)` with direct active-row soft deletion for admin.

- [ ] **Step 1: Write failing controller contract tests**

Update the existing delete assertion and add the new paths:

```java
@Test
void deleteMovesRecordToTrash() throws Exception {
    mockMvc.perform(delete("/api/v1/transactions/{id}", TRANSACTION_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("已移入回收站"));
    verify(transactionService).delete(USER_ID, TRANSACTION_ID);
}

@Test
void trashEndpointsDelegateAuthenticatedUser() throws Exception {
    TrashedTransactionResponse trashed = trashedTransactionResponse();
    when(transactionService.listTrash(USER_ID, 2, 10))
            .thenReturn(PageResponse.of(List.of(trashed), 11, 2, 10));
    when(transactionService.restore(USER_ID, TRANSACTION_ID))
            .thenReturn(transactionResponse());
    when(transactionService.clearTrash(USER_ID))
            .thenReturn(new TrashClearResponse(3));

    mockMvc.perform(get("/api/v1/transactions/trash")
                    .param("page", "2")
                    .param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.records[0].trashedAt")
                    .value("2026-05-20T08:30:00"));
    mockMvc.perform(post("/api/v1/transactions/{id}/restore", TRANSACTION_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("记录已恢复"));
    mockMvc.perform(delete("/api/v1/transactions/{id}/permanent", TRANSACTION_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("记录已永久删除"));
    mockMvc.perform(delete("/api/v1/transactions/trash"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.deletedCount").value(3));
}
```

- [ ] **Step 2: Run the controller tests and verify RED**

Run:

```bash
cd backend
mvn -Dtest=TransactionControllerTest test
```

Expected: FAIL because the new routes and DTOs do not exist and the old delete message is “记录已删除”.

- [ ] **Step 3: Add mapper integration tests for all transitions**

Add real-database assertions covering:

```java
assertThat(transactionMapper.moveToTrash(
        USER_ID, 100L, LocalDateTime.of(2026, 5, 20, 8, 30)))
        .isEqualTo(1);
assertThat(transactionMapper.moveToTrash(
        USER_ID, 100L, LocalDateTime.of(2026, 5, 20, 8, 31)))
        .isZero();
assertThat(transactionMapper.countTrashedRecords(USER_ID)).isEqualTo(2L);
assertThat(transactionMapper.selectTrashedRecords(USER_ID, 20, 0L))
        .extracting(TrashedTransactionResponse::getId)
        .containsExactly(100L, 104L);
assertThat(transactionMapper.restoreFromTrash(USER_ID, 100L)).isEqualTo(1);
assertThat(transactionMapper.softDeleteTrashed(USER_ID, 104L)).isEqualTo(1);
```

Also assert other-user transitions return `0` and list rows contain no image URL field.

- [ ] **Step 4: Run mapper tests and verify RED**

Run:

```bash
cd backend
mvn -Dtest=TransactionMapperTest test
```

Expected: FAIL because trash mapper statements are absent.

- [ ] **Step 5: Implement DTOs and mapper statements**

`TrashedTransactionResponse` contains the same display fields as `TransactionResponse`, omits `images`, and adds:

```java
private LocalDateTime trashedAt;
```

`TrashClearResponse` is:

```java
public record TrashClearResponse(int deletedCount) {
}
```

Add these exact mapper signatures:

```java
long countTrashedRecords(@Param("userId") Long userId);

List<TrashedTransactionResponse> selectTrashedRecords(
        @Param("userId") Long userId,
        @Param("limit") int limit,
        @Param("offset") long offset);

int moveToTrash(
        @Param("userId") Long userId,
        @Param("id") Long id,
        @Param("trashedAt") LocalDateTime trashedAt);

ExpenseTransaction selectTrashedTransaction(
        @Param("userId") Long userId,
        @Param("id") Long id);

int restoreFromTrash(
        @Param("userId") Long userId,
        @Param("id") Long id);

List<Long> selectTrashedIdsForUpdate(@Param("userId") Long userId);

int softDeleteTrashed(
        @Param("userId") Long userId,
        @Param("id") Long id);

int softDeleteActive(
        @Param("userId") Long userId,
        @Param("id") Long id);
```

Every update includes `user_id`, `deleted = 0` and the required `trashed_at IS NULL/IS NOT NULL` predicate. `selectTrashedIdsForUpdate` orders by `id` and ends with `FOR UPDATE`.
`selectTrashedTransaction` also ends with `FOR UPDATE` so restore and permanent deletion cannot race on the same row.

- [ ] **Step 6: Write failing service behavior tests**

Inject the existing fixed `CLOCK` into the `TransactionService` constructor and add tests that prove:

```java
service.delete(USER_ID, TRANSACTION_ID);
verify(transactionMapper).moveToTrash(
        USER_ID,
        TRANSACTION_ID,
        LocalDateTime.ofInstant(CLOCK.instant(), CLOCK.getZone()));
verifyNoInteractions(transactionImageService);
verify(businessAuditLogService).recordSuccess(
        USER_ID, "TRANSACTION_TRASH", "TRANSACTION", TRANSACTION_ID, "USER");
```

Add separate tests for:

- `restore` rejects an active row, validates category/payment/platform ownership, clears trash, returns the normal response and never deletes images.
- `permanentlyDelete` rejects an active row, soft-deletes images before the transaction and writes `TRANSACTION_DELETE`.
- `clearTrash` locks only the current user’s IDs, deletes each record, returns the actual count, and returns `0` for an empty list.
- `deleteWithoutBusinessAudit` uses `softDeleteActive` and still soft-deletes images, but does not write a business audit.
- every successful mutation evicts statistics and recommendations.

- [ ] **Step 7: Run service tests and verify RED**

Run:

```bash
cd backend
mvn -Dtest=TransactionServiceTest,TransactionImageServiceTest,AdminServiceTest test
```

Expected: FAIL on the old direct-delete behavior and missing state-transition methods.

- [ ] **Step 8: Implement service orchestration**

Use:

```java
@Transactional
public void delete(Long userId, Long id) {
    LocalDateTime trashedAt = LocalDateTime.ofInstant(clock.instant(), clock.getZone());
    int changed = transactionMapper.moveToTrash(userId, id, trashedAt);
    if (changed != 1) {
        throw stateError(userId, id, true);
    }
    evictAfterTransactionChange(userId);
    audit(userId, "TRANSACTION_TRASH", "TRANSACTION", id, "USER");
}
```

Implement `stateError` by selecting a non-soft-deleted row with both `id` and `userId`: return “记录不存在” when no owned row exists, “记录已在回收站” when `trashedAt` is non-null during a move, and “记录不在回收站” when a trash-only action receives an active row. Do not distinguish cross-user IDs from missing IDs.

`restore` first loads `selectTrashedTransaction`, validates all non-null references with existing `requireOwned` methods, executes `restoreFromTrash`, evicts, audits, and returns `get(userId, id)`.

`permanentlyDelete` and each `clearTrash` row call:

```java
transactionImageService.softDeleteByTransaction(userId, id);
if (transactionMapper.softDeleteTrashed(userId, id) != 1) {
    throw new IllegalArgumentException("记录不在回收站");
}
audit(userId, "TRANSACTION_DELETE", "TRANSACTION", id, "USER");
```

Keep the entire clear operation transactional and evict once after all rows succeed.

- [ ] **Step 9: Add controller routes and protect image operations**

Add:

```java
@GetMapping("/trash")
@PostMapping("/{id:\\d+}/restore")
@DeleteMapping("/{id:\\d+}/permanent")
@DeleteMapping("/trash")
```

with existing `@Min(1)` and `@Max(100)` pagination constraints.

In `TransactionImageService`, require `trashed_at IS NULL` for user and admin reads/appends/deletes. In `AdminService`, restrict the pre-delete lookup to active records before calling `deleteWithoutBusinessAudit`.

- [ ] **Step 10: Run all focused trash tests**

Run:

```bash
cd backend
mvn -Dtest=TransactionMapperTest,TransactionServiceTest,TransactionControllerTest,TransactionImageServiceTest,AdminServiceTest test
```

Expected: PASS.

- [ ] **Step 11: Commit the manual recycle-bin lifecycle**

```bash
git add backend/src/main/java/com/example/expense/transaction \
  backend/src/main/java/com/example/expense/admin/service/AdminService.java \
  backend/src/main/resources/mapper/TransactionMapper.xml \
  backend/src/test/java/com/example/expense/transaction \
  backend/src/test/java/com/example/expense/admin/service/AdminServiceTest.java
git commit -m "功能：实现流水回收站操作接口"
```

---

### Task 3: 实现用户级保留期设置

**Files:**
- Create: `backend/src/main/java/com/example/expense/user/dto/RecycleBinSettingsRequest.java`
- Create: `backend/src/main/java/com/example/expense/user/dto/RecycleBinSettingsResponse.java`
- Create: `backend/src/main/java/com/example/expense/user/service/RecycleBinSettingsService.java`
- Modify: `backend/src/main/java/com/example/expense/user/controller/UserController.java`
- Test: `backend/src/test/java/com/example/expense/user/service/RecycleBinSettingsServiceTest.java`
- Test: `backend/src/test/java/com/example/expense/user/controller/UserControllerTest.java`

**Interfaces:**
- Produces: `RecycleBinSettingsResponse get(Long userId)`
- Produces: `RecycleBinSettingsResponse update(Long userId, RecycleBinSettingsRequest request)`
- Produces: authenticated `GET/PUT /api/v1/users/me/recycle-bin-settings`.

- [ ] **Step 1: Write failing service tests**

Test the default, update and missing-user paths:

```java
@Test
void getReturnsPersistedRetentionDays() {
    ExpenseUser user = user(1001L, 30);
    when(userMapper.selectById(1001L)).thenReturn(user);

    assertThat(service.get(1001L).retentionDays()).isEqualTo(30);
}

@Test
void updatePersistsValidatedRetentionDays() {
    ExpenseUser user = user(1001L, 30);
    when(userMapper.selectById(1001L)).thenReturn(user);

    assertThat(service.update(
            1001L, new RecycleBinSettingsRequest(15)).retentionDays())
            .isEqualTo(15);
    assertThat(user.getTrashRetentionDays()).isEqualTo(15);
    verify(userMapper).updateById(user);
}
```

Also assert a missing user throws “用户不存在”.

- [ ] **Step 2: Run service test and verify RED**

Run:

```bash
cd backend
mvn -Dtest=RecycleBinSettingsServiceTest test
```

Expected: FAIL because the DTOs and service do not exist.

- [ ] **Step 3: Implement settings DTOs and service**

Use:

```java
public record RecycleBinSettingsRequest(
        @NotNull(message = "保留天数不能为空")
        @Min(value = 1, message = "保留天数不能少于 1 天")
        @Max(value = 365, message = "保留天数不能超过 365 天")
        Integer retentionDays
) {
}

public record RecycleBinSettingsResponse(int retentionDays) {
}
```

The service reads by `userId`, treats a legacy null as `30`, updates only `trashRetentionDays`, and calls `userMapper.updateById(user)`.

- [ ] **Step 4: Write failing controller validation tests**

Create standalone `MockMvc` tests with a `UserPrincipal` in `SecurityContextHolder`:

```java
mockMvc.perform(get("/api/v1/users/me/recycle-bin-settings"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.retentionDays").value(30));

mockMvc.perform(put("/api/v1/users/me/recycle-bin-settings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"retentionDays\":365}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.retentionDays").value(365));

mockMvc.perform(put("/api/v1/users/me/recycle-bin-settings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"retentionDays\":0}"))
        .andExpect(status().isBadRequest());

mockMvc.perform(put("/api/v1/users/me/recycle-bin-settings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"retentionDays\":1.5}"))
        .andExpect(status().isBadRequest());
```

- [ ] **Step 5: Add controller endpoints and run tests**

Inject `RecycleBinSettingsService` into `UserController`, keeping the existing profile endpoint intact. Add:

```java
@GetMapping("/me/recycle-bin-settings")
public ApiResponse<RecycleBinSettingsResponse> recycleBinSettings()

@PutMapping("/me/recycle-bin-settings")
public ApiResponse<RecycleBinSettingsResponse> updateRecycleBinSettings(
        @Valid @RequestBody RecycleBinSettingsRequest request)
```

Run:

```bash
cd backend
mvn -Dtest=RecycleBinSettingsServiceTest,UserControllerTest test
```

Expected: PASS.

- [ ] **Step 6: Commit retention settings**

```bash
git add backend/src/main/java/com/example/expense/user \
  backend/src/test/java/com/example/expense/user
git commit -m "功能：增加回收站保留期设置"
```

---

### Task 4: 实现每日自动清理任务

**Files:**
- Create: `backend/src/main/java/com/example/expense/transaction/dto/ExpiredTrashCandidate.java`
- Create: `backend/src/main/java/com/example/expense/transaction/dto/TrashCleanupResult.java`
- Create: `backend/src/main/java/com/example/expense/transaction/service/TransactionTrashCleanupService.java`
- Create: `backend/src/main/java/com/example/expense/transaction/scheduler/TransactionTrashCleanupScheduler.java`
- Modify: `backend/src/main/java/com/example/expense/transaction/mapper/TransactionMapper.java`
- Modify: `backend/src/main/resources/mapper/TransactionMapper.xml`
- Modify: `backend/src/main/java/com/example/expense/transaction/service/TransactionService.java`
- Test: `backend/src/test/java/com/example/expense/transaction/mapper/TransactionMapperTest.java`
- Test: `backend/src/test/java/com/example/expense/transaction/service/TransactionServiceTest.java`
- Test: `backend/src/test/java/com/example/expense/transaction/service/TransactionTrashCleanupServiceTest.java`
- Test: `backend/src/test/java/com/example/expense/transaction/scheduler/TransactionTrashCleanupSchedulerTest.java`

**Interfaces:**
- Produces: `List<ExpiredTrashCandidate> selectExpiredTrashCandidates(LocalDateTime runAt, long afterId, int limit)`
- Produces: `ExpiredTrashCandidate selectExpiredTrashForUpdate(Long userId, Long id, LocalDateTime runAt)`
- Produces: `boolean autoDeleteExpired(Long userId, Long id, LocalDateTime runAt)`
- Produces: `TrashCleanupResult cleanupExpired()`

- [ ] **Step 1: Write failing mapper expiry tests**

Set user `1001` to `30` days and existing user `2002` to `7` days. Within the test, first move fixture `104` to `2026-07-29T03:00:00` so it is not an unrelated expired candidate. Insert:

- transaction `201`, user `1001`, `trashed_at = 2026-06-30T03:00:00` (expired at the exact 30-day boundary);
- transaction `202`, user `2002`, `trashed_at = 2026-07-23T03:00:00` (expired at the exact 7-day boundary);
- transaction `203`, user `1001`, `trashed_at = 2026-06-30T03:01:00` (not yet expired);
- transaction `204`, user `1001`, `trashed_at = NULL` (active).

Reuse the existing `insertTransaction(...)` helper, then set `trashed_at` with `JdbcTemplate`. Assert fixed-day boundaries:

```java
List<ExpiredTrashCandidate> candidates =
        transactionMapper.selectExpiredTrashCandidates(
                LocalDateTime.of(2026, 7, 30, 3, 0),
                0L,
                200);

assertThat(candidates)
        .extracting(ExpiredTrashCandidate::id)
        .containsExactly(201L, 202L)
        .doesNotContain(203L, 204L);
```

Add a second call with `afterId = 201L` and assert it contains exactly `202L`.

- [ ] **Step 2: Run mapper test and verify RED**

Run:

```bash
cd backend
mvn -Dtest=TransactionMapperTest test
```

Expected: FAIL because expiry candidate statements do not exist.

- [ ] **Step 3: Implement expiry mapper statements**

Use DTOs:

```java
public record ExpiredTrashCandidate(Long id, Long userId) {
}

public record TrashCleanupResult(int deletedCount, int failedCount) {
}
```

Candidate SQL uses:

```sql
t.trashed_at <= TIMESTAMPADD(DAY, -u.trash_retention_days, #{runAt})
AND t.id > #{afterId}
ORDER BY t.id ASC
LIMIT #{limit}
```

The recheck statement repeats the same condition for one `userId/id` pair and ends with `FOR UPDATE`.

Add these exact mapper signatures:

```java
List<ExpiredTrashCandidate> selectExpiredTrashCandidates(
        @Param("runAt") LocalDateTime runAt,
        @Param("afterId") long afterId,
        @Param("limit") int limit);

ExpiredTrashCandidate selectExpiredTrashForUpdate(
        @Param("userId") Long userId,
        @Param("id") Long id,
        @Param("runAt") LocalDateTime runAt);
```

- [ ] **Step 4: Write failing transactional auto-delete tests**

Add:

```java
@Test
void autoDeleteExpiredRechecksStateAndAuditsSystemDeletion() {
    LocalDateTime runAt = LocalDateTime.of(2026, 7, 30, 3, 0);
    when(transactionMapper.selectExpiredTrashForUpdate(
            USER_ID, TRANSACTION_ID, runAt))
            .thenReturn(new ExpiredTrashCandidate(TRANSACTION_ID, USER_ID));
    when(transactionMapper.softDeleteTrashed(USER_ID, TRANSACTION_ID))
            .thenReturn(1);

    assertThat(service.autoDeleteExpired(
            USER_ID, TRANSACTION_ID, runAt)).isTrue();

    verify(transactionImageService)
            .softDeleteByTransaction(USER_ID, TRANSACTION_ID);
    verify(businessAuditLogService).recordSuccess(
            USER_ID,
            "TRANSACTION_AUTO_DELETE",
            "TRANSACTION",
            TRANSACTION_ID,
            "SYSTEM");
}
```

Add a null recheck case returning `false` with no image, audit or cache interaction.

- [ ] **Step 5: Implement `autoDeleteExpired` and verify service tests**

Annotate the method `@Transactional`. Recheck using `selectExpiredTrashForUpdate`, soft-delete images, execute `softDeleteTrashed`, audit only after a successful row change, and call the existing post-commit cache eviction methods.

Run:

```bash
cd backend
mvn -Dtest=TransactionServiceTest test
```

Expected: PASS.

- [ ] **Step 6: Write failing cleanup-loop and scheduler tests**

`TransactionTrashCleanupServiceTest` uses a fixed clock and proves:

- batches use `afterId` cursors `0 → last ID`.
- limit is exactly `200`.
- one candidate exception increments `failedCount` and later candidates still execute.
- each candidate is attempted only once per run.
- the final result reports real success/failure counts.

Scheduler test:

```java
when(cleanupService.cleanupExpired())
        .thenReturn(new TrashCleanupResult(3, 1));

scheduler.cleanupExpiredTransactions();

verify(cleanupService).cleanupExpired();
```

- [ ] **Step 7: Implement cleanup service and scheduler**

Core loop:

```java
LocalDateTime runAt = LocalDateTime.ofInstant(clock.instant(), clock.getZone());
long afterId = 0L;
int deleted = 0;
int failed = 0;

while (true) {
    List<ExpiredTrashCandidate> batch =
            transactionMapper.selectExpiredTrashCandidates(
                    runAt, afterId, BATCH_SIZE);
    if (batch.isEmpty()) {
        return new TrashCleanupResult(deleted, failed);
    }
    for (ExpiredTrashCandidate candidate : batch) {
        afterId = candidate.id();
        try {
            if (transactionService.autoDeleteExpired(
                    candidate.userId(), candidate.id(), runAt)) {
                deleted++;
            }
        } catch (RuntimeException ex) {
            failed++;
        }
    }
}
```

Scheduler:

```java
@Scheduled(cron = "0 0 3 * * *", zone = "${app.time-zone:Asia/Shanghai}")
public void cleanupExpiredTransactions()
```

Log only `deletedCount` and `failedCount`, without candidate IDs or transaction fields.

- [ ] **Step 8: Run all automatic-cleanup tests**

Run:

```bash
cd backend
mvn -Dtest=TransactionMapperTest,TransactionServiceTest,TransactionTrashCleanupServiceTest,TransactionTrashCleanupSchedulerTest test
```

Expected: PASS.

- [ ] **Step 9: Commit automatic cleanup**

```bash
git add backend/src/main/java/com/example/expense/transaction \
  backend/src/main/resources/mapper/TransactionMapper.xml \
  backend/src/test/java/com/example/expense/transaction
git commit -m "功能：增加回收站自动清理任务"
```

---

### Task 5: 完成回收站前端与真实浏览器回归

**Files:**
- Create: `frontend/src/views/TrashView.vue`
- Create: `frontend/tests/recycle-bin-ui.mjs`
- Modify: `frontend/src/types.ts`
- Modify: `frontend/src/api/services.ts`
- Modify: `frontend/src/router/index.ts`
- Modify: `frontend/src/views/SettingsView.vue`
- Modify: `frontend/src/views/RecordsView.vue`
- Modify: `frontend/src/views/TransactionDetailView.vue`
- Modify: `frontend/package.json`

**Interfaces:**
- Produces: `TrashedTransactionRecord`, `RecycleBinSettings`, `TrashClearResult`
- Produces: `transactionApi.trash/restore/permanentlyRemove/clearTrash`
- Produces: `userApi.recycleBinSettings/updateRecycleBinSettings`
- Produces: authenticated route `/trash`.

- [ ] **Step 1: Write the failing Playwright flow**

Create a real-browser test using `withViteServer` and mobile viewport `390 × 844`. Seed auth tokens, mock `/api/v1/auth/me`, and route the exact APIs.

Use this complete authenticated identity:

```js
const user = {
  id: 1001,
  username: 'recycle-bin-user',
  nickname: '回收站测试用户',
  status: 'ACTIVE',
  admin: false,
  email: 'recycle-bin@example.com',
  emailVerifiedAt: '2026-07-01T08:00:00',
  createdAt: '2026-07-01T08:00:00'
}

const tokens = {
  accessToken: 'recycle-bin-ui-access-token',
  refreshToken: 'recycle-bin-ui-refresh-token',
  expiresInSeconds: 3600
}
```

Use complete fixtures:

```js
const trashedRecord = {
  id: 88,
  type: 'EXPENSE',
  itemName: '午餐',
  amount: 28.5,
  occurredAt: '2026-07-20T12:30:00',
  channel: 'OFFLINE',
  offlinePlace: '公司食堂',
  paymentMethodId: 21,
  paymentMethodName: '微信',
  categoryId: 11,
  categoryName: '餐饮',
  categoryIcon: 'shop-o',
  note: '工作日',
  trashedAt: '2026-07-29T18:00:00'
}
```

Verify observable UI behavior:

1. `/settings` displays a “回收站” link whose href is `/trash`.
2. `/trash` shows the record, “保留 1个月（30天）”, “恢复”, “永久删除” and “清空回收站”.
3. Restore sends `POST /api/v1/transactions/88/restore`, removes the row and shows “已恢复到流水”.
4. Permanent delete displays “删除后不可恢复”, then sends `DELETE /api/v1/transactions/88/permanent`.
5. Clear displays the all-record warning, then sends `DELETE /api/v1/transactions/trash`.
6. The setting sheet contains all six shortcuts and custom input.
7. Changing `30 → 15` shows the existing-record warning and sends `{ retentionDays: 15 }` only after confirmation.
8. Custom `0`, `366` and decimal input cannot be submitted.
9. Directly opening `/records/88` shows “移入回收站”; confirming it sends `DELETE /api/v1/transactions/88`, shows the recoverable-delete explanation and displays “已移入回收站”.

For case 9, return `trashedRecord` without `trashedAt` and with `images: []` from `GET /api/v1/transactions/88`; also mock `/categories`, `/payment-methods` and `/online-platforms` with the referenced category/payment rows and an empty platform list. Mock `/transactions/recommendations/ai-scene/status` as `{ enabled: false }` for settings/detail initialization.

- [ ] **Step 2: Add the test script and verify RED**

Add:

```json
"test:recycle-bin-ui": "node tests/recycle-bin-ui.mjs"
```

Run:

```bash
cd frontend
npm run test:recycle-bin-ui
```

Expected: FAIL because `/trash` redirects to `/`, the settings link is absent and the APIs/UI do not exist.

- [ ] **Step 3: Add TypeScript contracts and HTTP methods**

Add:

```ts
export type TrashedTransactionRecord = Omit<TransactionRecord, 'images'> & {
  trashedAt: string
}

export interface RecycleBinSettings {
  retentionDays: number
}

export interface TrashClearResult {
  deletedCount: number
}
```

Add methods:

```ts
trash: (params?: { page?: number; size?: number }) =>
  http.get<unknown, PageResponse<TrashedTransactionRecord>>(
    '/transactions/trash', { params }),
restore: (id: number) =>
  http.post<unknown, TransactionRecord>(`/transactions/${id}/restore`),
permanentlyRemove: (id: number) =>
  http.delete<unknown, void>(`/transactions/${id}/permanent`),
clearTrash: () =>
  http.delete<unknown, TrashClearResult>('/transactions/trash')
```

Add a `userApi` object with:

```ts
recycleBinSettings: () =>
  http.get<unknown, RecycleBinSettings>(
    '/users/me/recycle-bin-settings'),
updateRecycleBinSettings: (retentionDays: number) =>
  http.put<unknown, RecycleBinSettings>(
    '/users/me/recycle-bin-settings',
    { retentionDays })
```

- [ ] **Step 4: Add the route and settings entry**

Lazy-load `TrashView`, add:

```ts
{ path: '/trash', component: TrashView, meta: { requiresAuth: true } }
```

Add a third data-management `RouterLink`:

```vue
<RouterLink class="settings-grid-item" to="/trash">
  <van-icon name="delete-o" />
  <span>回收站</span>
</RouterLink>
```

Use a three-column grid class at mobile widths without adding raw colors, font sizes or radii.

- [ ] **Step 5: Implement `TrashView.vue`**

State and constants:

```ts
const RETENTION_OPTIONS = [
  { label: '7天', value: 7 },
  { label: '15天', value: 15 },
  { label: '1个月（30天）', value: 30 },
  { label: '3个月（90天）', value: 90 },
  { label: '半年（180天）', value: 180 },
  { label: '1年（365天）', value: 365 }
]
const page = ref(1)
const pageData = ref<PageResponse<TrashedTransactionRecord> | null>(null)
const settings = ref<RecycleBinSettings>({ retentionDays: 30 })
const recordActionId = ref<number | null>(null)
const clearing = ref(false)
const settingsVisible = ref(false)
const retentionDraft = ref('30')
```

Implement:

```ts
async function loadTrash(targetPage = page.value)
async function restoreRecord(id: number)
async function permanentlyRemoveRecord(id: number)
async function clearTrash()
async function saveRetentionDays()
```

`saveRetentionDays` parses only an integer `1–365`; when the new value is lower than the saved value, await a `showConfirmDialog` that explicitly states existing expired records will be deleted by the next automatic cleanup.

Template requirements:

- `van-nav-bar` with `navigateBackOrHome(router)`.
- summary panel with total count and formatted current retention.
- `van-empty` when `records.length === 0`.
- record cards with type, amount, title, category, original time and trash time.
- text-bearing restore/permanent-delete buttons.
- `BottomSheet` for shortcuts and custom input.
- no image links, edit, copy or recurring actions.
- page controls that move to the previous valid page when the last row is removed.

- [ ] **Step 6: Update existing delete wording**

In both `RecordsView.vue` and `TransactionDetailView.vue`, use:

```ts
await showConfirmDialog({
  title: '移入回收站',
  message: '移入后可在“我的-回收站”中恢复。'
})
```

Success toast:

```ts
showToast('已移入回收站')
```

Button text becomes “移入回收站”; the API remains `transactionApi.remove(id)`.

- [ ] **Step 7: Run browser, UI-token and build verification**

Run:

```bash
cd frontend
npm run test:recycle-bin-ui
npm run check:ui
npm run build
```

Expected: all PASS with no Vue/TypeScript build errors.

- [ ] **Step 8: Commit the front end**

```bash
git add frontend/src/types.ts \
  frontend/src/api/services.ts \
  frontend/src/router/index.ts \
  frontend/src/views/TrashView.vue \
  frontend/src/views/SettingsView.vue \
  frontend/src/views/RecordsView.vue \
  frontend/src/views/TransactionDetailView.vue \
  frontend/tests/recycle-bin-ui.mjs \
  frontend/package.json
git commit -m "功能：完成流水回收站前端交互"
```

---

### Task 6: 更新文档并完成全量验证

**Files:**
- Modify: `docs/api.md`
- Modify: `docs/production-runbook.md`

**Interfaces:**
- Documents: all user APIs, three states, fixed-day retention semantics, automatic cleanup timing and image cleanup relationship.

- [ ] **Step 1: Update API documentation**

Document:

- `DELETE /transactions/{id}` now means move to trash.
- `GET /transactions/trash?page=&size=`
- `POST /transactions/{id}/restore`
- `DELETE /transactions/{id}/permanent`
- `DELETE /transactions/trash`
- `GET /users/me/recycle-bin-settings`
- `PUT /users/me/recycle-bin-settings`
- `retentionDays` is an integer `1–365`, default `30`.
- trash rows do not participate in normal list/statistics/export/recommendations/reference counts.

- [ ] **Step 2: Update the production runbook**

Add an operations section stating:

- transaction trash cleanup runs at `03:00` in `app.time-zone`.
- it dynamically applies each user’s current fixed-day retention.
- it soft-deletes transaction/image database rows only.
- image physical files remain governed by `TRANSACTION_IMAGE_RETENTION_DAYS` and the `03:30` cleanup.
- automatic cleanup logs only success/failure counts.

- [ ] **Step 3: Run complete backend verification**

Run:

```bash
cd backend
mvn test
```

Expected: BUILD SUCCESS with all tests passing.

- [ ] **Step 4: Run complete frontend verification**

Run:

```bash
cd frontend
npm run test:recycle-bin-ui
npm run check:ui
npm run build
```

Expected: all commands exit `0`.

- [ ] **Step 5: Validate migration and review the final diff**

Run:

```bash
git diff --check
git status --short
git diff --stat origin/develop...HEAD
```

Confirm:

- only planned recycle-bin files changed.
- no secret, token, amount fixture from a real user, or physical image file was added.
- no unrelated local change was overwritten.

- [ ] **Step 6: Commit documentation**

```bash
git add docs/api.md docs/production-runbook.md
git commit -m "文档：完善流水回收站使用与运维说明"
```

- [ ] **Step 7: Final verification after the last commit**

Run:

```bash
cd backend
mvn test
cd ../frontend
npm run test:recycle-bin-ui
npm run check:ui
npm run build
cd ..
git status --short --branch
```

Expected: all tests/builds pass and the worktree is clean.
