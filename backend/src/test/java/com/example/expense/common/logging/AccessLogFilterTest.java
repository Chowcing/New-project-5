package com.example.expense.common.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.ServletException;
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
    void replacesInvalidIncomingRequestId() throws Exception {
        AccessLogFilter filter = new AccessLogFilter("test-version", 1000);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");
        request.addHeader("X-Request-Id", "bad request id");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader("X-Request-Id")).isNotEqualTo("bad request id");
        assertThat(singleMessage()).doesNotContain("bad request id");
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
