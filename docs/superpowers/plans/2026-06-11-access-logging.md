# 接口运行日志增强 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 增强后端接口完成日志，补齐请求追踪、客户端来源、查询参数名、慢请求和异常类型，同时避免记录敏感值。

**Architecture:** 继续使用现有 `AccessLogFilter` 作为唯一接口访问日志入口，避免引入新的观测系统。`SecurityConfig` 负责注入部署版本和慢请求阈值，`application.yml` 提供默认配置。测试使用 Spring mock servlet 对过滤器做窄范围单元测试。

**Tech Stack:** Spring Boot 3.4.1, Java 17, Spring Security, JUnit 5, Spring MockMvc test utilities, Logback test appender.

---

### Task 1: AccessLogFilter 行为测试

**Files:**
- Create: `backend/src/test/java/com/example/expense/common/logging/AccessLogFilterTest.java`
- Read: `backend/src/main/java/com/example/expense/common/logging/AccessLogFilter.java`

- [x] **Step 1: Write the failing test**

Create `AccessLogFilterTest` with tests for request id generation/reuse, query key redaction, forwarded client IP, and exception logging. Use a Logback `ListAppender` attached to `AccessLogFilter` logger.

```java
package com.example.expense.common.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AccessLogFilterTest {
    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(AccessLogFilter.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void generatesRequestIdAndLogsSafeFields() throws Exception {
        AccessLogFilter filter = new AccessLogFilter("test-version", 1000);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/transactions");
        request.setRemoteAddr("10.0.0.3");
        request.addParameter("keyword", "secret-note");
        request.addParameter("page", "1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String requestId = response.getHeader("X-Request-Id");
        assertThat(requestId).isNotBlank();
        String logMessage = singleMessage();
        assertThat(logMessage).contains("requestId=" + requestId);
        assertThat(logMessage).contains("method=GET");
        assertThat(logMessage).contains("uri=/api/v1/transactions");
        assertThat(logMessage).contains("status=200");
        assertThat(logMessage).contains("clientIp=10.0.0.3");
        assertThat(logMessage).contains("queryKeys=[keyword,page]");
        assertThat(logMessage).contains("slow=false");
        assertThat(logMessage).contains("deployment=test-version");
        assertThat(logMessage).doesNotContain("secret-note");
    }

    @Test
    void reusesIncomingRequestIdAndForwardedIp() throws Exception {
        AccessLogFilter filter = new AccessLogFilter("test-version", 1000);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/categories");
        request.addHeader("X-Request-Id", "client-request-1");
        request.addHeader("X-Forwarded-For", "203.0.113.1, 10.0.0.2");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader("X-Request-Id")).isEqualTo("client-request-1");
        assertThat(singleMessage()).contains("requestId=client-request-1", "clientIp=203.0.113.1");
    }

    @Test
    void logsExceptionTypeAndAtLeastServerErrorStatus() {
        AccessLogFilter filter = new AccessLogFilter("test-version", 1000);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            throw new ServletException("boom");
        })).isInstanceOf(ServletException.class);

        ILoggingEvent event = singleEvent();
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getFormattedMessage()).contains("status=500", "exception=ServletException");
    }

    private String singleMessage() {
        return singleEvent().getFormattedMessage();
    }

    private ILoggingEvent singleEvent() {
        assertThat(appender.list).hasSize(1);
        return appender.list.get(0);
    }
}
```

- [x] **Step 2: Run test to verify it fails**

Run: `cd backend; mvn -Dtest=AccessLogFilterTest test`

Expected: compile failure because `AccessLogFilter(String, int)` does not exist and log fields are not implemented.

### Task 2: AccessLogFilter 实现增强

**Files:**
- Modify: `backend/src/main/java/com/example/expense/common/logging/AccessLogFilter.java`
- Test: `backend/src/test/java/com/example/expense/common/logging/AccessLogFilterTest.java`

- [x] **Step 1: Write minimal implementation**

Update `AccessLogFilter` to:

- accept `deploymentVersion` and `slowRequestThresholdMs`;
- set `X-Expense-Deployment` and `X-Request-Id` response headers before invoking the chain;
- resolve client IP from `X-Forwarded-For`, `X-Real-IP`, then remote address;
- log sorted query parameter names only;
- include `exception` when the filter chain throws;
- include `slow=true/false` based on duration.

- [x] **Step 2: Run target test to verify it passes**

Run: `cd backend; mvn -Dtest=AccessLogFilterTest test`

Expected: PASS.

### Task 3: 配置注入

**Files:**
- Modify: `backend/src/main/java/com/example/expense/common/config/SecurityConfig.java`
- Modify: `backend/src/main/resources/application.yml`
- Test: `backend/src/test/java/com/example/expense/auth/controller/AuthControllerSecurityTest.java`

- [x] **Step 1: Add configuration default**

Add:

```yaml
app:
  logging:
    slow-request-threshold-ms: ${SLOW_REQUEST_THRESHOLD_MS:1000}
```

under the existing `app` tree in `application.yml`.

- [x] **Step 2: Inject threshold into SecurityConfig**

Change `securityFilterChain` parameters to include:

```java
@Value("${app.logging.slow-request-threshold-ms:1000}") long slowRequestThresholdMs
```

and instantiate:

```java
new AccessLogFilter(deploymentVersion, slowRequestThresholdMs)
```

- [x] **Step 3: Run security slice test**

Run: `cd backend; mvn -Dtest=AuthControllerSecurityTest test`

Expected: PASS.

### Task 4: 完整验证

**Files:**
- Read: `backend/pom.xml`
- Read: `docs/superpowers/specs/2026-06-11-access-logging-design.md`

- [x] **Step 1: Run backend tests**

Run: `cd backend; mvn test`

Expected: PASS.

- [x] **Step 2: Inspect diff**

Run: `git diff --check`

Expected: no whitespace errors.

Run: `git status --short`

Expected: only the logging implementation, tests, plan doc, and any intentional docs are modified/untracked.
