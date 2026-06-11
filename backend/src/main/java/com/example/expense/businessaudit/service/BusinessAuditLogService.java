package com.example.expense.businessaudit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.expense.businessaudit.dto.BusinessAuditLogResponse;
import com.example.expense.businessaudit.entity.BusinessAuditLog;
import com.example.expense.businessaudit.mapper.BusinessAuditLogMapper;
import com.example.expense.common.logging.AccessLogFilter;
import com.example.expense.common.web.PageResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class BusinessAuditLogService {
    public static final String STATUS_SUCCESS = "SUCCESS";

    private final BusinessAuditLogMapper businessAuditLogMapper;

    public BusinessAuditLogService(BusinessAuditLogMapper businessAuditLogMapper) {
        this.businessAuditLogMapper = businessAuditLogMapper;
    }

    public void recordSuccess(Long userId, String action, String targetType, Long targetId, String source) {
        if (userId == null) {
            throw new IllegalArgumentException("审计用户不能为空");
        }
        String normalizedAction = requireText(action, "审计动作不能为空");
        String normalizedTargetType = requireText(targetType, "审计目标类型不能为空");
        String normalizedSource = requireText(source, "审计来源不能为空");

        BusinessAuditLog log = new BusinessAuditLog();
        log.setUserId(userId);
        log.setAction(normalizedAction);
        log.setTargetType(normalizedTargetType);
        log.setTargetId(targetId);
        log.setSource(normalizedSource);
        log.setStatus(STATUS_SUCCESS);
        log.setRequestId(currentRequestId());
        businessAuditLogMapper.insert(log);
    }

    public PageResponse<BusinessAuditLogResponse> list(
            Long userId,
            String action,
            String targetType,
            String source,
            int page,
            int size
    ) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        LambdaQueryWrapper<BusinessAuditLog> countWrapper = queryWrapper(userId, action, targetType, source);
        long total = businessAuditLogMapper.selectCount(countWrapper);
        LambdaQueryWrapper<BusinessAuditLog> listWrapper = queryWrapper(userId, action, targetType, source)
                .orderByDesc(BusinessAuditLog::getCreatedAt)
                .orderByDesc(BusinessAuditLog::getId)
                .last("LIMIT " + safeSize + " OFFSET " + (long) (safePage - 1) * safeSize);
        List<BusinessAuditLogResponse> rows = businessAuditLogMapper.selectList(listWrapper).stream()
                .map(this::toResponse)
                .toList();
        return PageResponse.of(rows, total, safePage, safeSize);
    }

    private LambdaQueryWrapper<BusinessAuditLog> queryWrapper(Long userId, String action, String targetType, String source) {
        return new LambdaQueryWrapper<BusinessAuditLog>()
                .eq(userId != null, BusinessAuditLog::getUserId, userId)
                .eq(hasText(action), BusinessAuditLog::getAction, action == null ? null : action.trim())
                .eq(hasText(targetType), BusinessAuditLog::getTargetType, targetType == null ? null : targetType.trim())
                .eq(hasText(source), BusinessAuditLog::getSource, source == null ? null : source.trim());
    }

    private BusinessAuditLogResponse toResponse(BusinessAuditLog log) {
        return new BusinessAuditLogResponse(
                log.getId(),
                log.getUserId(),
                log.getAction(),
                log.getTargetType(),
                log.getTargetId(),
                log.getSource(),
                log.getStatus(),
                log.getRequestId(),
                log.getCreatedAt()
        );
    }

    private String currentRequestId() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return null;
        }
        HttpServletRequest request = servletAttributes.getRequest();
        Object requestId = request.getAttribute(AccessLogFilter.REQUEST_ID_ATTRIBUTE);
        return requestId instanceof String value && !value.isBlank() ? value : null;
    }

    private String requireText(String value, String message) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
