package com.example.expense.businessaudit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.expense.businessaudit.dto.BusinessAuditLogResponse;
import com.example.expense.businessaudit.entity.BusinessAuditLog;
import com.example.expense.businessaudit.mapper.BusinessAuditLogMapper;
import com.example.expense.common.logging.AccessLogFilter;
import com.example.expense.common.web.PageResponse;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@ExtendWith(MockitoExtension.class)
class BusinessAuditLogServiceTest {
    @Mock
    private BusinessAuditLogMapper mapper;

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void recordSuccessPersistsSafeMetadataWithRequestId() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(AccessLogFilter.REQUEST_ID_ATTRIBUTE, "request-123");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        BusinessAuditLogService service = new BusinessAuditLogService(mapper);

        service.recordSuccess(1001L, "TRANSACTION_CREATE", "TRANSACTION", 88L, "USER");

        ArgumentCaptor<BusinessAuditLog> captor = ArgumentCaptor.forClass(BusinessAuditLog.class);
        verify(mapper).insert(captor.capture());
        BusinessAuditLog log = captor.getValue();
        assertThat(log.getUserId()).isEqualTo(1001L);
        assertThat(log.getAction()).isEqualTo("TRANSACTION_CREATE");
        assertThat(log.getTargetType()).isEqualTo("TRANSACTION");
        assertThat(log.getTargetId()).isEqualTo(88L);
        assertThat(log.getSource()).isEqualTo("USER");
        assertThat(log.getStatus()).isEqualTo("SUCCESS");
        assertThat(log.getRequestId()).isEqualTo("request-123");
    }

    @Test
    void recordSuccessAllowsMissingRequestContext() {
        BusinessAuditLogService service = new BusinessAuditLogService(mapper);

        service.recordSuccess(1001L, "OCR_IMAGE_RECOGNIZE", "OCR", null, "OCR");

        ArgumentCaptor<BusinessAuditLog> captor = ArgumentCaptor.forClass(BusinessAuditLog.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getRequestId()).isNull();
        assertThat(captor.getValue().getTargetId()).isNull();
    }

    @Test
    void recordSuccessRejectsBlankAction() {
        BusinessAuditLogService service = new BusinessAuditLogService(mapper);

        assertThatThrownBy(() -> service.recordSuccess(1001L, " ", "TRANSACTION", 88L, "USER"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("审计动作不能为空");
    }

    @Test
    void listAppliesFiltersAndPagination() {
        BusinessAuditLog row = new BusinessAuditLog();
        row.setId(1L);
        row.setUserId(1001L);
        row.setAction("TRANSACTION_CREATE");
        row.setTargetType("TRANSACTION");
        row.setTargetId(88L);
        row.setSource("USER");
        row.setStatus("SUCCESS");
        row.setRequestId("request-123");
        row.setCreatedAt(LocalDateTime.of(2026, 6, 11, 12, 0));
        when(mapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(row));
        BusinessAuditLogService service = new BusinessAuditLogService(mapper);

        PageResponse<BusinessAuditLogResponse> page = service.list(1001L, "TRANSACTION_CREATE", "TRANSACTION", "USER", 2, 10);

        assertThat(page.total()).isEqualTo(1L);
        assertThat(page.page()).isEqualTo(2);
        assertThat(page.size()).isEqualTo(10);
        assertThat(page.records()).hasSize(1);
        BusinessAuditLogResponse response = page.records().get(0);
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.userId()).isEqualTo(1001L);
        assertThat(response.action()).isEqualTo("TRANSACTION_CREATE");
        assertThat(response.targetType()).isEqualTo("TRANSACTION");
        assertThat(response.targetId()).isEqualTo(88L);
        assertThat(response.source()).isEqualTo("USER");
        assertThat(response.status()).isEqualTo("SUCCESS");
        assertThat(response.requestId()).isEqualTo("request-123");
        assertThat(response.createdAt()).isEqualTo(LocalDateTime.of(2026, 6, 11, 12, 0));
        verify(mapper).selectCount(any(LambdaQueryWrapper.class));
        verify(mapper).selectList(any(LambdaQueryWrapper.class));
    }
}
