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
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
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
                .setConversionService(new ApplicationConversionService())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(
                        com.fasterxml.jackson.databind.json.JsonMapper.builder()
                                .findAndAddModules()
                                .build()))
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
