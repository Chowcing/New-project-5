package com.example.expense.admin.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.expense.admin.service.AdminService;
import com.example.expense.businessaudit.dto.BusinessAuditLogResponse;
import com.example.expense.businessaudit.service.BusinessAuditLogService;
import com.example.expense.common.web.PageResponse;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminControllerTest {
    private final AdminService adminService = org.mockito.Mockito.mock(AdminService.class);
    private final BusinessAuditLogService businessAuditLogService = org.mockito.Mockito.mock(BusinessAuditLogService.class);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AdminController(adminService, businessAuditLogService)).build();

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
}
