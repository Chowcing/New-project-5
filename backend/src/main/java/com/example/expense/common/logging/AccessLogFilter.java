package com.example.expense.common.logging;

import com.example.expense.common.security.UserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class AccessLogFilter extends OncePerRequestFilter {
    public static final String REQUEST_ID_ATTRIBUTE = AccessLogFilter.class.getName() + ".REQUEST_ID";
    private static final Logger log = LoggerFactory.getLogger(AccessLogFilter.class);
    private static final String ANONYMOUS_USER = "anonymous";
    private static final String DEPLOYMENT_HEADER = "X-Expense-Deployment";
    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String DEFAULT_DEPLOYMENT_VERSION = "local-dev";
    private static final int MAX_REQUEST_ID_LENGTH = 128;
    private final String deploymentVersion;
    private final long slowRequestThresholdMs;

    public AccessLogFilter(String deploymentVersion) {
        this(deploymentVersion, 1000);
    }

    public AccessLogFilter(String deploymentVersion, long slowRequestThresholdMs) {
        if (deploymentVersion == null || deploymentVersion.isBlank()) {
            this.deploymentVersion = DEFAULT_DEPLOYMENT_VERSION;
        } else {
            this.deploymentVersion = deploymentVersion.trim();
        }
        this.slowRequestThresholdMs = Math.max(0, slowRequestThresholdMs);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long startNanos = System.nanoTime();
        Exception failure = null;
        String requestId = resolveRequestId(request);

        try {
            response.setHeader(DEPLOYMENT_HEADER, deploymentVersion);
            response.setHeader(REQUEST_ID_HEADER, requestId);
            request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
            filterChain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException ex) {
            failure = ex;
            throw ex;
        } finally {
            logAccess(request, response, startNanos, requestId, failure);
        }
    }

    private void logAccess(
            HttpServletRequest request,
            HttpServletResponse response,
            long startNanos,
            String requestId,
            Exception failure
    ) {
        long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
        int status = failure == null ? response.getStatus() : Math.max(response.getStatus(), 500);
        boolean slow = durationMs >= slowRequestThresholdMs;
        String exception = failure == null ? "none" : failure.getClass().getSimpleName();
        String message = "接口完成 requestId={} method={} uri={} status={} durationMs={} userId={} clientIp={} queryKeys={} slow={} deployment={} exception={}";

        if (status >= 500) {
            log.error(message, requestId, request.getMethod(), request.getRequestURI(), status, durationMs, currentUserId(),
                    clientIp(request), queryKeys(request), slow, deploymentVersion, exception);
        } else if (status >= 400) {
            log.warn(message, requestId, request.getMethod(), request.getRequestURI(), status, durationMs, currentUserId(),
                    clientIp(request), queryKeys(request), slow, deploymentVersion, exception);
        } else {
            log.info(message, requestId, request.getMethod(), request.getRequestURI(), status, durationMs, currentUserId(),
                    clientIp(request), queryKeys(request), slow, deploymentVersion, exception);
        }
    }

    private String resolveRequestId(HttpServletRequest request) {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId != null) {
            String trimmed = requestId.trim();
            if (isValidRequestId(trimmed)) {
                return trimmed;
            }
        }
        return UUID.randomUUID().toString();
    }

    private boolean isValidRequestId(String requestId) {
        if (requestId.isBlank() || requestId.length() > MAX_REQUEST_ID_LENGTH) {
            return false;
        }
        for (int i = 0; i < requestId.length(); i++) {
            char ch = requestId.charAt(i);
            boolean valid = (ch >= 'a' && ch <= 'z')
                    || (ch >= 'A' && ch <= 'Z')
                    || (ch >= '0' && ch <= '9')
                    || ch == '-'
                    || ch == '_'
                    || ch == '.'
                    || ch == ':';
            if (!valid) {
                return false;
            }
        }
        return true;
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",", 2)[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private String queryKeys(HttpServletRequest request) {
        String queryString = request.getQueryString();
        if (queryString == null || queryString.isBlank()) {
            return "[]";
        }
        Set<String> keys = new TreeSet<>(Comparator.naturalOrder());
        for (String pair : queryString.split("&")) {
            String key = pair.split("=", 2)[0];
            if (!key.isBlank()) {
                keys.add(decodeQueryKey(key));
            }
        }
        return keys.stream().collect(Collectors.joining(",", "[", "]"));
    }

    private String decodeQueryKey(String key) {
        try {
            return URLDecoder.decode(key, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return key;
        }
    }

    private String currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            return String.valueOf(principal.getUserId());
        }
        return ANONYMOUS_USER;
    }
}
