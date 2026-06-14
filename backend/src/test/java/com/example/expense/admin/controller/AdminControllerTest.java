package com.example.expense.admin.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.expense.admin.dto.AdminAuditLogResponse;
import com.example.expense.admin.dto.AdminTransactionDetailResponse;
import com.example.expense.admin.service.AdminService;
import com.example.expense.businessaudit.dto.BusinessAuditLogResponse;
import com.example.expense.businessaudit.service.BusinessAuditLogService;
import com.example.expense.common.web.PageResponse;
import com.example.expense.transaction.dto.TransactionImageContent;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminControllerTest {
    private final AdminService adminService = org.mockito.Mockito.mock(AdminService.class);
    private final BusinessAuditLogService businessAuditLogService = org.mockito.Mockito.mock(BusinessAuditLogService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminController(adminService, businessAuditLogService)).build();
    }

    @Test
    void businessAuditLogsPassesFiltersToService() throws Exception {
        BusinessAuditLogResponse row = new BusinessAuditLogResponse(
                1L,
                1001L,
                "TRANSACTION_CREATE",
                "TRANSACTION",
                88L,
                "USER",
                "SUCCESS",
                "request-123",
                LocalDateTime.of(2026, 6, 11, 12, 0)
        );
        when(businessAuditLogService.list(eq(1001L), eq("TRANSACTION_CREATE"), eq("TRANSACTION"), eq("USER"), eq(2), eq(10)))
                .thenReturn(PageResponse.of(List.of(row), 1, 2, 10));

        mockMvc.perform(get("/api/v1/admin/business-audit-logs")
                        .param("userId", "1001")
                        .param("action", "TRANSACTION_CREATE")
                        .param("targetType", "TRANSACTION")
                        .param("source", "USER")
                        .param("page", "2")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.records[0].userId").value(1001))
                .andExpect(jsonPath("$.data.records[0].action").value("TRANSACTION_CREATE"))
                .andExpect(jsonPath("$.data.records[0].requestId").value("request-123"));
    }

    @Test
    void auditLogsPassesFiltersToService() throws Exception {
        when(adminService.listAuditLogs(eq(9L), eq("TRANSACTION_DELETE"), eq("TRANSACTION"), eq(88L),
                eq(LocalDateTime.of(2026, 6, 1, 0, 0)), eq(LocalDateTime.of(2026, 6, 8, 0, 0)), eq(2), eq(10)))
                .thenReturn(PageResponse.of(List.of(new AdminAuditLogResponse()), 0, 2, 10));

        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .param("adminUserId", "9")
                        .param("action", "TRANSACTION_DELETE")
                        .param("targetType", "TRANSACTION")
                        .param("targetId", "88")
                        .param("startDate", "2026-06-01")
                        .param("endDate", "2026-06-07")
                        .param("page", "2")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void transactionDetailReturnsServicePayload() throws Exception {
        AdminTransactionDetailResponse detail = new AdminTransactionDetailResponse();
        when(adminService.getTransactionDetail(88L)).thenReturn(detail);

        mockMvc.perform(get("/api/v1/admin/transactions/88"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void transactionImageReturnsInlineResource() throws Exception {
        when(adminService.readTransactionImage(88L, 7L)).thenReturn(new TransactionImageContent(
                new ByteArrayResource("image-bytes".getBytes()),
                "image/png",
                11L,
                "receipt.png"
        ));

        mockMvc.perform(get("/api/v1/admin/transactions/88/images/7"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/png"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("inline")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("receipt.png")));
    }
}
