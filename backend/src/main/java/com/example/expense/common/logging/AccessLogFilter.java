package com.example.expense.common.logging;

import com.example.expense.common.security.UserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class AccessLogFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(AccessLogFilter.class);
    private static final String ANONYMOUS_USER = "anonymous";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long startNanos = System.nanoTime();
        Exception failure = null;

        try {
            filterChain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException ex) {
            failure = ex;
            throw ex;
        } finally {
            logAccess(request, response, startNanos, failure);
        }
    }

    private void logAccess(
            HttpServletRequest request,
            HttpServletResponse response,
            long startNanos,
            Exception failure
    ) {
        long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
        int status = failure == null ? response.getStatus() : Math.max(response.getStatus(), 500);
        String message = "接口完成 method={} uri={} status={} durationMs={} userId={}";

        if (status >= 500) {
            log.error(message, request.getMethod(), request.getRequestURI(), status, durationMs, currentUserId());
        } else if (status >= 400) {
            log.warn(message, request.getMethod(), request.getRequestURI(), status, durationMs, currentUserId());
        } else {
            log.info(message, request.getMethod(), request.getRequestURI(), status, durationMs, currentUserId());
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
