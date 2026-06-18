# Weekly Statistics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a weekly analysis mode to the statistics page using Monday-to-Sunday natural weeks.

**Architecture:** The backend adds a `/statistics/weekly` endpoint backed by the existing date-range aggregation mapper methods, plus a weekly response DTO and week-specific insight/trend filling. The frontend extends the current statistics page mode switch to `WEEKLY | MONTHLY | YEARLY`, adds week date helpers and preference persistence, and reuses the existing metric, insight, trend, budget note, and breakdown rendering paths.

**Tech Stack:** Spring Boot 3.4.1, Java 17, MyBatis-Plus, JUnit 5/Mockito, Vue 3, TypeScript, Vite, Vant, ECharts.

---

### Task 1: Backend Weekly Service Behavior

**Files:**
- Modify: `backend/src/test/java/com/example/expense/statistics/service/StatisticsServiceTest.java`
- Create: `backend/src/main/java/com/example/expense/statistics/dto/WeeklyStatisticsResponse.java`
- Modify: `backend/src/main/java/com/example/expense/statistics/service/StatisticsService.java`
- Modify: `backend/src/main/java/com/example/expense/common/cache/CacheKeys.java`

- [ ] **Step 1: Write the failing weekly service test**

Add imports to `StatisticsServiceTest.java`:

```java
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
```

Add this test before helper methods:

```java
@Test
void weeklyReturnsNaturalWeekStatisticsAndFilledDailyTrend() {
    StatisticsService service = new StatisticsService(statisticsMapper);
    LocalDate weekStart = LocalDate.of(2026, 6, 15);
    MonthlyTotals totals = totals("70.00", "200.00", 4L, 3L, 1L);
    MonthlyTotals previousTotals = totals("40.00", "150.00", 3L, 2L, 1L);
    DailySummary monday = dailySummary("2026-06-15", "10.00", "0.00", 1L);
    DailySummary wednesday = dailySummary("2026-06-17", "60.00", "200.00", 3L);
    CategorySummary expenseCategory = categorySummary(10L, "餐饮", "70.00", 3L);
    CategorySummary incomeCategory = categorySummary(20L, "工资", "200.00", 1L);
    ChannelSummary channel = channelSummary("ONLINE", "70.00", 3L);
    PaymentMethodSummary paymentMethod = paymentMethodSummary(30L, "微信", "70.00", 3L);

    when(statisticsMapper.selectMonthlyTotals(eq(1001L), any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(totals, previousTotals);
    when(statisticsMapper.selectDailySummary(eq(1001L), any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(List.of(monday, wednesday));
    when(statisticsMapper.selectCategorySummary(eq(1001L), eq("EXPENSE"), any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(List.of(expenseCategory));
    when(statisticsMapper.selectCategorySummary(eq(1001L), eq("INCOME"), any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(List.of(incomeCategory));
    when(statisticsMapper.selectExpenseByChannel(eq(1001L), any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(List.of(channel));
    when(statisticsMapper.selectExpenseByPaymentMethod(eq(1001L), any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(List.of(paymentMethod));

    var response = service.weekly(1001L, weekStart);

    assertThat(response.weekStart()).isEqualTo("2026-06-15");
    assertThat(response.weekEnd()).isEqualTo("2026-06-21");
    assertThat(response.balance()).isEqualByComparingTo("130.00");
    assertThat(response.transactionCount()).isEqualTo(4L);
    assertThat(response.expenseCount()).isEqualTo(3L);
    assertThat(response.incomeCount()).isEqualTo(1L);
    assertThat(response.insight().currentPeriod()).isEqualTo("2026-06-15");
    assertThat(response.insight().previousPeriod()).isEqualTo("2026-06-08");
    assertThat(response.insight().expenseChangeAmount()).isEqualByComparingTo("30.00");
    assertThat(response.insight().expenseChangePercent()).isEqualByComparingTo("75.00");
    assertThat(response.insight().averageDailyExpense()).isEqualByComparingTo("10.00");
    assertThat(response.insight().averageExpensePerTransaction()).isEqualByComparingTo("23.33");
    assertThat(response.insight().peakExpense().period()).isEqualTo("2026-06-17");
    assertThat(response.dailyTrend()).hasSize(7);
    assertThat(response.dailyTrend().get(0).getDate()).isEqualTo("2026-06-15");
    assertThat(response.dailyTrend().get(1).getDate()).isEqualTo("2026-06-16");
    assertThat(response.dailyTrend().get(1).getTotalExpense()).isEqualByComparingTo("0");
    assertThat(response.dailyTrend().get(2).getDate()).isEqualTo("2026-06-17");
    assertThat(response.dailyTrend().get(2).getBalance()).isEqualByComparingTo("140.00");
    assertThat(response.expenseByCategory()).containsExactly(expenseCategory);
    assertThat(response.incomeByCategory()).containsExactly(incomeCategory);
    assertThat(response.expenseByChannel()).containsExactly(channel);
    assertThat(response.expenseByPaymentMethod()).containsExactly(paymentMethod);
}

@Test
void weeklyRejectsNonMondayWeekStart() {
    StatisticsService service = new StatisticsService(statisticsMapper);

    assertThatThrownBy(() -> service.weekly(1001L, LocalDate.of(2026, 6, 16)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("周度统计起始日期必须是周一");
}
```

- [ ] **Step 2: Run the service test and verify RED**

Run:

```bash
cd backend
mvn -Dtest=StatisticsServiceTest test
```

Expected: compilation fails because `StatisticsService.weekly(...)` and `WeeklyStatisticsResponse` do not exist.

- [ ] **Step 3: Add weekly response DTO**

Create `backend/src/main/java/com/example/expense/statistics/dto/WeeklyStatisticsResponse.java`:

```java
package com.example.expense.statistics.dto;

import java.math.BigDecimal;
import java.util.List;

public record WeeklyStatisticsResponse(
        String weekStart,
        String weekEnd,
        BigDecimal totalExpense,
        BigDecimal totalIncome,
        BigDecimal balance,
        long transactionCount,
        long expenseCount,
        long incomeCount,
        StatisticsInsight insight,
        List<DailySummary> dailyTrend,
        List<CategorySummary> expenseByCategory,
        List<CategorySummary> incomeByCategory,
        List<ChannelSummary> expenseByChannel,
        List<PaymentMethodSummary> expenseByPaymentMethod
) {
}
```

- [ ] **Step 4: Add weekly cache key**

Modify `backend/src/main/java/com/example/expense/common/cache/CacheKeys.java`:

```java
public static String statisticsWeekly(Long userId, LocalDate weekStart) {
    return userPrefix(userId) + ":weekly:" + weekStart;
}
```

Add `import java.time.LocalDate;` if missing.

- [ ] **Step 5: Implement minimal weekly service**

Modify `StatisticsService.java`:

```java
import com.example.expense.statistics.dto.WeeklyStatisticsResponse;
import java.time.DayOfWeek;
import java.time.LocalDate;
```

Add method after `yearly(...)`:

```java
@Cacheable(cacheNames = CacheNames.STATISTICS, key = "T(com.example.expense.common.cache.CacheKeys).statisticsWeekly(#userId, #weekStart)")
public WeeklyStatisticsResponse weekly(Long userId, LocalDate weekStart) {
    if (weekStart.getDayOfWeek() != DayOfWeek.MONDAY) {
        throw new IllegalArgumentException("周度统计起始日期必须是周一");
    }
    LocalDate weekEnd = weekStart.plusDays(6);
    var startAt = weekStart.atStartOfDay();
    var endAt = weekStart.plusDays(7).atStartOfDay();
    LocalDate previousWeekStart = weekStart.minusWeeks(1);
    var previousStartAt = previousWeekStart.atStartOfDay();
    var previousEndAt = weekStart.atStartOfDay();
    MonthlyTotals totals = safeTotals(statisticsMapper.selectMonthlyTotals(userId, startAt, endAt));
    MonthlyTotals previousTotals = safeTotals(statisticsMapper.selectMonthlyTotals(userId, previousStartAt, previousEndAt));
    BigDecimal totalExpense = nullToZero(totals.getTotalExpense());
    BigDecimal totalIncome = nullToZero(totals.getTotalIncome());
    List<DailySummary> dailyTrend = fillWeeklyTrend(weekStart, statisticsMapper.selectDailySummary(userId, startAt, endAt));
    List<CategorySummary> expenseByCategory = safeList(statisticsMapper.selectCategorySummary(userId, "EXPENSE", startAt, endAt));
    List<CategorySummary> incomeByCategory = safeList(statisticsMapper.selectCategorySummary(userId, "INCOME", startAt, endAt));
    List<ChannelSummary> expenseByChannel = safeList(statisticsMapper.selectExpenseByChannel(userId, startAt, endAt));
    List<PaymentMethodSummary> expenseByPaymentMethod = safeList(statisticsMapper.selectExpenseByPaymentMethod(userId, startAt, endAt));
    return new WeeklyStatisticsResponse(
            weekStart.toString(),
            weekEnd.toString(),
            totalExpense,
            totalIncome,
            totalIncome.subtract(totalExpense),
            nullToZero(totals.getTransactionCount()),
            nullToZero(totals.getExpenseCount()),
            nullToZero(totals.getIncomeCount()),
            buildWeeklyInsight(weekStart, totals, previousTotals, dailyTrend),
            dailyTrend,
            expenseByCategory,
            incomeByCategory,
            expenseByChannel,
            expenseByPaymentMethod
    );
}
```

Add helpers near the monthly/yearly insight helpers:

```java
private StatisticsInsight buildWeeklyInsight(
        LocalDate weekStart,
        MonthlyTotals currentTotals,
        MonthlyTotals previousTotals,
        List<DailySummary> dailyTrend
) {
    currentTotals = safeTotals(currentTotals);
    previousTotals = safeTotals(previousTotals);
    BigDecimal currentExpense = nullToZero(currentTotals.getTotalExpense());
    BigDecimal currentIncome = nullToZero(currentTotals.getTotalIncome());
    BigDecimal currentBalance = currentIncome.subtract(currentExpense);
    BigDecimal previousExpense = nullToZero(previousTotals.getTotalExpense());
    BigDecimal previousIncome = nullToZero(previousTotals.getTotalIncome());
    BigDecimal previousBalance = previousIncome.subtract(previousExpense);
    return new StatisticsInsight(
            weekStart.toString(),
            weekStart.minusWeeks(1).toString(),
            previousExpense,
            previousIncome,
            previousBalance,
            amountScale(currentExpense.subtract(previousExpense)),
            percentChange(currentExpense, previousExpense),
            amountScale(currentIncome.subtract(previousIncome)),
            percentChange(currentIncome, previousIncome),
            amountScale(currentBalance.subtract(previousBalance)),
            percentChange(currentBalance, previousBalance),
            divideAmount(currentExpense, 7),
            divideAmount(currentExpense, nullToZero(currentTotals.getExpenseCount())),
            peakDailyExpense(dailyTrend)
    );
}

private List<DailySummary> fillWeeklyTrend(LocalDate weekStart, List<DailySummary> rows) {
    Map<String, DailySummary> rowsByDate = new HashMap<>();
    for (DailySummary row : safeList(rows)) {
        normalizeDailySummary(row);
        rowsByDate.put(row.getDate(), row);
    }
    return IntStream.range(0, 7)
            .mapToObj(offset -> rowsByDate.getOrDefault(weekStart.plusDays(offset).toString(), emptyDailySummary(weekStart, offset)))
            .toList();
}

private DailySummary emptyDailySummary(LocalDate weekStart, int offset) {
    DailySummary summary = new DailySummary();
    summary.setDate(weekStart.plusDays(offset).toString());
    summary.setTotalExpense(BigDecimal.ZERO);
    summary.setTotalIncome(BigDecimal.ZERO);
    summary.setBalance(BigDecimal.ZERO);
    summary.setTransactionCount(0L);
    return summary;
}
```

- [ ] **Step 6: Run the service test and verify GREEN**

Run:

```bash
cd backend
mvn -Dtest=StatisticsServiceTest test
```

Expected: `BUILD SUCCESS`.

### Task 2: Backend Weekly Controller and API Docs

**Files:**
- Modify: `backend/src/main/java/com/example/expense/statistics/controller/StatisticsController.java`
- Modify: `docs/api.md`

- [ ] **Step 1: Write failing controller-level coverage**

Create `backend/src/test/java/com/example/expense/statistics/controller/StatisticsControllerTest.java`:

```java
package com.example.expense.statistics.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.expense.common.security.UserPrincipal;
import com.example.expense.common.web.GlobalExceptionHandler;
import com.example.expense.statistics.dto.StatisticsInsight;
import com.example.expense.statistics.dto.WeeklyStatisticsResponse;
import com.example.expense.statistics.service.StatisticsService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class StatisticsControllerTest {
    private static final Long USER_ID = 1001L;
    private final StatisticsService statisticsService = mock(StatisticsService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new StatisticsController(statisticsService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        SecurityContextHolder.clearContext();
        UserPrincipal principal = new UserPrincipal(USER_ID, "demo", false);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void weeklyReturnsWeekStatistics() throws Exception {
        WeeklyStatisticsResponse response = new WeeklyStatisticsResponse(
                "2026-06-15",
                "2026-06-21",
                new BigDecimal("70.00"),
                new BigDecimal("200.00"),
                new BigDecimal("130.00"),
                4L,
                3L,
                1L,
                new StatisticsInsight(
                        "2026-06-15",
                        "2026-06-08",
                        new BigDecimal("40.00"),
                        new BigDecimal("150.00"),
                        new BigDecimal("110.00"),
                        new BigDecimal("30.00"),
                        new BigDecimal("75.00"),
                        new BigDecimal("50.00"),
                        new BigDecimal("33.33"),
                        new BigDecimal("20.00"),
                        new BigDecimal("18.18"),
                        new BigDecimal("10.00"),
                        new BigDecimal("23.33"),
                        null
                ),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
        when(statisticsService.weekly(eq(USER_ID), eq(LocalDate.of(2026, 6, 15)))).thenReturn(response);

        mockMvc.perform(get("/api/v1/statistics/weekly")
                        .param("weekStart", "2026-06-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.weekStart").value("2026-06-15"))
                .andExpect(jsonPath("$.data.weekEnd").value("2026-06-21"))
                .andExpect(jsonPath("$.data.totalExpense").value(70.00));
    }
}
```

- [ ] **Step 2: Run the controller test and verify RED**

Run:

```bash
cd backend
mvn -Dtest=StatisticsControllerTest test
```

Expected: compilation fails because `StatisticsController.weekly(...)` does not exist.

- [ ] **Step 3: Add weekly endpoint**

Modify `StatisticsController.java` imports:

```java
import com.example.expense.statistics.dto.WeeklyStatisticsResponse;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
```

Add method:

```java
@GetMapping("/weekly")
public ApiResponse<WeeklyStatisticsResponse> weekly(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart
) {
    return ApiResponse.ok(statisticsService.weekly(SecurityUtils.currentUserId(), weekStart));
}
```

- [ ] **Step 4: Run the controller test and verify GREEN**

Run:

```bash
cd backend
mvn -Dtest=StatisticsControllerTest test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Update API docs**

Add this block in `docs/api.md` under `## 统计和导出`, before monthly statistics:

```markdown
- `GET /statistics/weekly?weekStart=2026-06-15`：周度统计，`weekStart` 必须是自然周周一
  - 返回周总支出、周总收入、结余、总笔数、支出笔数、收入笔数
  - `insight`：返回当前周起始日、上一周起始日、上一周总支出/收入/结余、支出/收入/结余环比变化金额和百分比、日均支出、支出笔均、高消费日；上期金额为 0 且本期非 0 时变化百分比为 `null`
  - `dailyTrend`：当周每日收入、支出、结余和笔数，日期补齐到周一至周日 7 天
  - `expenseByCategory` / `incomeByCategory`：按分类汇总金额和笔数
  - `expenseByChannel`：按线上/线下汇总支出金额和笔数
  - `expenseByPaymentMethod`：按支付方式汇总支出金额和笔数
```

### Task 3: Frontend Week Utilities and API Types

**Files:**
- Create: `frontend/tests/date-utils.mjs`
- Modify: `frontend/src/utils/date.ts`
- Modify: `frontend/src/types.ts`
- Modify: `frontend/src/api/services.ts`
- Modify: `frontend/src/utils/preferences.ts`

- [ ] **Step 1: Write failing date utility test**

Create `frontend/tests/date-utils.mjs`:

```js
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'

const source = readFileSync(new URL('../src/utils/date.ts', import.meta.url), 'utf8')
const compiled = ts.transpileModule(source, {
  compilerOptions: {
    module: ts.ModuleKind.CommonJS,
    target: ts.ScriptTarget.ES2020
  }
}).outputText

const module = { exports: {} }
vm.runInNewContext(compiled, { module, exports: module.exports, Date })

const {
  startOfWeekDate,
  endOfWeekDate,
  previousWeekStart,
  currentWeekStart
} = module.exports

assert.equal(startOfWeekDate('2026-06-18'), '2026-06-15')
assert.equal(startOfWeekDate('2026-06-21'), '2026-06-15')
assert.equal(endOfWeekDate('2026-06-15'), '2026-06-21')
assert.equal(previousWeekStart('2026-06-15'), '2026-06-08')
assert.match(currentWeekStart(), /^\d{4}-\d{2}-\d{2}$/)
```

- [ ] **Step 2: Run date utility test and verify RED**

Run:

```bash
cd frontend
node tests/date-utils.mjs
```

Expected: fails because `startOfWeekDate`, `endOfWeekDate`, `previousWeekStart`, and `currentWeekStart` are not exported.

- [ ] **Step 3: Implement date utilities**

Modify `frontend/src/utils/date.ts`:

```ts
export function currentWeekStart() {
  return startOfWeekDate(todayDate())
}

export function startOfWeekDate(value: string) {
  const date = parseLocalDate(value)
  const day = date.getDay()
  const mondayOffset = day === 0 ? -6 : 1 - day
  date.setDate(date.getDate() + mondayOffset)
  return formatLocalDate(date)
}

export function endOfWeekDate(value: string) {
  const date = parseLocalDate(startOfWeekDate(value))
  date.setDate(date.getDate() + 6)
  return formatLocalDate(date)
}

export function previousWeekStart(value: string) {
  const date = parseLocalDate(startOfWeekDate(value))
  date.setDate(date.getDate() - 7)
  return formatLocalDate(date)
}

export function isDateString(value: unknown) {
  return typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value)
}

function parseLocalDate(value: string) {
  const [year, month, day] = value.split('-').map(Number)
  return new Date(year, month - 1, day)
}

function formatLocalDate(value: Date) {
  return `${value.getFullYear()}-${pad2(value.getMonth() + 1)}-${pad2(value.getDate())}`
}
```

- [ ] **Step 4: Run date utility test and verify GREEN**

Run:

```bash
cd frontend
node tests/date-utils.mjs
```

Expected: process exits `0`.

- [ ] **Step 5: Add frontend weekly types and API**

Modify `frontend/src/types.ts` after `MonthlyStatistics`:

```ts
export interface WeeklyStatistics {
  weekStart: string
  weekEnd: string
  totalExpense: number
  totalIncome: number
  balance: number
  transactionCount: number
  expenseCount: number
  incomeCount: number
  insight: StatisticsInsight
  dailyTrend: DailySummary[]
  expenseByCategory: CategorySummary[]
  incomeByCategory: CategorySummary[]
  expenseByChannel: ChannelSummary[]
  expenseByPaymentMethod: PaymentMethodSummary[]
}
```

Modify `frontend/src/api/services.ts` imports and API:

```ts
import type {
  // existing imports
  WeeklyStatistics,
  YearlyStatistics
} from '@/types'

export const statisticsApi = {
  weekly: (weekStart: string) => http.get<unknown, WeeklyStatistics>('/statistics/weekly', { params: { weekStart } }),
  monthly: (month: string) => http.get<unknown, MonthlyStatistics>('/statistics/monthly', { params: { month } }),
  yearly: (year: string | number) => http.get<unknown, YearlyStatistics>('/statistics/yearly', { params: { year } })
}
```

- [ ] **Step 6: Extend statistics preferences**

Modify `frontend/src/utils/preferences.ts`:

```ts
import { currentMonth, currentWeekStart, isDateString, todayDate } from '@/utils/date'

export type StatisticsPeriodMode = 'WEEKLY' | 'MONTHLY' | 'YEARLY'

export interface StatisticsPreference {
  mode: StatisticsPeriodMode
  weekStart: string
  month: string
  year: string
  breakdownPanel: StatisticsBreakdownPanel
}
```

Update `normalizeStatisticsPreference`:

```ts
function normalizeStatisticsPreference(value: unknown): StatisticsPreference | undefined {
  const source = typeof value === 'object' && value ? value as Partial<StatisticsPreference> : undefined
  if (!source) return undefined
  const mode: StatisticsPeriodMode = source.mode === 'YEARLY'
    ? 'YEARLY'
    : source.mode === 'WEEKLY'
      ? 'WEEKLY'
      : 'MONTHLY'
  return {
    mode,
    weekStart: isDateString(source.weekStart) ? source.weekStart : currentWeekStart(),
    month: normalizeMonth(source.month),
    year: normalizeYear(source.year),
    breakdownPanel: source.breakdownPanel === 'CHANNEL' || source.breakdownPanel === 'PAYMENT' ? source.breakdownPanel : 'CATEGORY'
  }
}
```

### Task 4: Statistics Page Weekly Mode

**Files:**
- Modify: `frontend/src/views/StatisticsView.vue`

- [ ] **Step 1: Add weekly mode in the page**

Modify imports:

```ts
import type {
  BudgetUsageSummary,
  CategorySummary,
  ChannelSummary,
  DailySummary,
  MonthlyStatistics,
  MonthlyTrendSummary,
  PaymentMethodSummary,
  WeeklyStatistics,
  YearlyStatistics
} from '@/types'
import { currentMonth, currentWeekStart, endOfWeekDate, isDateString, money, previousWeekStart, startOfWeekDate } from '@/utils/date'
```

Modify state and computed values:

```ts
type PeriodMode = 'WEEKLY' | 'MONTHLY' | 'YEARLY'

const routeMonth = normalizedRouteMonth(route.query.month)
const routeWeekStart = normalizedRouteWeekStart(route.query.weekStart)
const mode = ref<PeriodMode>(routeWeekStart ? 'WEEKLY' : routeMonth ? 'MONTHLY' : savedStatisticsPreference?.mode || 'MONTHLY')
const weekStart = ref(routeWeekStart || savedStatisticsPreference?.weekStart || currentWeekStart())
const weeklyStats = ref<WeeklyStatistics | null>(null)

const currentStats = computed(() => {
  if (mode.value === 'YEARLY') return yearlyStats.value
  if (mode.value === 'WEEKLY') return weeklyStats.value
  return monthlyStats.value
})

const trendRows = computed<TrendSummary[]>(() => {
  if (mode.value === 'YEARLY') return yearlyStats.value?.monthlyTrend || []
  if (mode.value === 'WEEKLY') return weeklyStats.value?.dailyTrend || []
  return monthlyStats.value?.dailyTrend || []
})

const budgetButtonMonth = computed(() => {
  if (mode.value === 'MONTHLY') return month.value
  if (mode.value === 'WEEKLY') return weekStart.value.slice(0, 7)
  const monthNumber = String(new Date().getMonth() + 1).padStart(2, '0')
  return `${year.value}-${monthNumber}`
})
```

Add helpers:

```ts
const weekEnd = computed(() => endOfWeekDate(weekStart.value))
const weekRangeText = computed(() => `${weekStart.value} 至 ${weekEnd.value}`)

function normalizedRouteWeekStart(value: LocationQueryValue | LocationQueryValue[]) {
  const nextValue = firstQueryValue(value)
  return isDateString(nextValue) ? startOfWeekDate(nextValue) : ''
}

async function chooseWeekStart(value: string) {
  const nextValue = startOfWeekDate(value)
  if (weekStart.value === nextValue) return
  weekStart.value = nextValue
  persistStatisticsPreference()
  await load()
}
```

Update `persistStatisticsPreference`, watchers, `periodStartDate`, `periodEndDate`, `load`, `trendLabel`, and section titles:

```ts
function persistStatisticsPreference() {
  saveStatisticsPreference({
    mode: mode.value,
    weekStart: weekStart.value,
    month: month.value,
    year: year.value,
    breakdownPanel: breakdownPanel.value
  })
}

watch(weekStart, () => {
  persistStatisticsPreference()
})

async function load() {
  loading.value = true
  try {
    if (mode.value === 'YEARLY') {
      yearlyStats.value = await statisticsApi.yearly(year.value)
    } else if (mode.value === 'WEEKLY') {
      weeklyStats.value = await statisticsApi.weekly(weekStart.value)
    } else {
      monthlyStats.value = await statisticsApi.monthly(month.value)
    }
  } catch (error) {
    showError(error, '统计数据加载失败')
  } finally {
    loading.value = false
  }
}

function periodStartDate() {
  if (mode.value === 'YEARLY') return `${year.value}-01-01`
  if (mode.value === 'WEEKLY') return weekStart.value
  return `${month.value}-01`
}

function periodEndDate() {
  if (mode.value === 'YEARLY') return `${year.value}-12-31`
  if (mode.value === 'WEEKLY') return weekEnd.value
  return monthEndDate(month.value)
}
```

Modify template controls:

```vue
<van-radio-group v-model="mode" class="period-switch" direction="horizontal" @change="load">
  <van-radio name="WEEKLY">周度</van-radio>
  <van-radio name="MONTHLY">月度</van-radio>
  <van-radio name="YEARLY">年度</van-radio>
</van-radio-group>
<template v-if="mode === 'WEEKLY'">
  <ModernDateField
    :model-value="weekStart"
    mode="date"
    label="周起始"
    title="选择周日期"
    @update:model-value="chooseWeekStart"
    @change="chooseWeekStart"
  />
  <div class="period-range">{{ weekRangeText }}</div>
  <div class="period-shortcuts">
    <van-button size="small" plain type="primary" icon="arrow-left" @click="chooseWeekStart(previousWeekStart(weekStart))">上一周</van-button>
    <van-button size="small" plain type="primary" icon="calendar-o" :disabled="weekStart === currentWeekStart()" @click="chooseWeekStart(currentWeekStart())">本周</van-button>
  </div>
</template>
<template v-else-if="mode === 'MONTHLY'">
```

Update labels:

```vue
<span>{{ mode === 'YEARLY' ? '同比洞察' : mode === 'WEEKLY' ? '周环比洞察' : '环比洞察' }}</span>
<span>{{ mode === 'YEARLY' ? '年度趋势' : mode === 'WEEKLY' ? '周度趋势' : '月度趋势' }}</span>
```

Update budget note branch:

```vue
<div v-else class="budget-year-note">
  <div>预算按月管理，不做{{ mode === 'WEEKLY' ? '周度' : '年度' }}合并展示。</div>
  <div class="muted">可进入 {{ budgetButtonMonth }} 预算查看或调整。</div>
</div>
```

Add scoped CSS:

```css
.period-range {
  margin-top: var(--space-8);
  color: var(--text-secondary);
  font-size: var(--font-size-meta);
  line-height: var(--line-height-meta);
}
```

- [ ] **Step 2: Run frontend type-check**

Run:

```bash
cd frontend
npm run type-check
```

Expected: type-check exits `0`.

### Task 5: Final Verification and Commit

**Files:**
- All files touched above.

- [ ] **Step 1: Run backend test suite**

Run:

```bash
cd backend
mvn test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 2: Run frontend date utility test**

Run:

```bash
cd frontend
node tests/date-utils.mjs
```

Expected: process exits `0`.

- [ ] **Step 3: Run frontend UI check and build**

Run:

```bash
cd frontend
npm run check:ui
npm run build
```

Expected: both commands exit `0`.

- [ ] **Step 4: Review diff**

Run:

```bash
git diff --check
git status --short
```

Expected: no whitespace errors. Status should list only weekly statistics implementation files and plan file.

- [ ] **Step 5: Commit in Chinese**

Run:

```bash
git add backend/src/test/java/com/example/expense/statistics/service/StatisticsServiceTest.java \
  backend/src/test/java/com/example/expense/statistics/controller/StatisticsControllerTest.java \
  backend/src/main/java/com/example/expense/statistics/dto/WeeklyStatisticsResponse.java \
  backend/src/main/java/com/example/expense/statistics/service/StatisticsService.java \
  backend/src/main/java/com/example/expense/statistics/controller/StatisticsController.java \
  backend/src/main/java/com/example/expense/common/cache/CacheKeys.java \
  frontend/tests/date-utils.mjs \
  frontend/src/utils/date.ts \
  frontend/src/types.ts \
  frontend/src/api/services.ts \
  frontend/src/utils/preferences.ts \
  frontend/src/views/StatisticsView.vue \
  docs/api.md \
  docs/superpowers/plans/2026-06-18-weekly-statistics.md
git commit -m "新增分析页周度统计"
```

Expected: commit succeeds with a Chinese message.
