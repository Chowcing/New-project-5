package com.example.expense.admin.mapper;

import com.example.expense.admin.dto.AdminAuditLogResponse;
import com.example.expense.admin.dto.AdminDailyMetricResponse;
import com.example.expense.admin.dto.AdminTransactionResponse;
import com.example.expense.admin.dto.AdminUserResponse;
import com.example.expense.admin.dto.AdminUserStatisticsResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface AdminMapper {
    long countUsers(@Param("keyword") String keyword, @Param("status") String status);

    List<AdminUserResponse> selectUsers(
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("limit") Integer limit,
            @Param("offset") Long offset
    );

    AdminUserResponse selectUser(@Param("id") Long id);

    AdminUserStatisticsResponse selectUserStatistics(@Param("userId") Long userId);

    long countTransactions(
            @Param("userId") Long userId,
            @Param("type") String type,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt,
            @Param("channel") String channel,
            @Param("keyword") String keyword
    );

    List<AdminTransactionResponse> selectTransactions(
            @Param("userId") Long userId,
            @Param("type") String type,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt,
            @Param("channel") String channel,
            @Param("keyword") String keyword,
            @Param("limit") Integer limit,
            @Param("offset") Long offset
    );

    long countDisabledUsers();

    long countAllTransactions();

    BigDecimal sumTransactions(@Param("type") String type);

    long countActiveUsersSince(@Param("since") LocalDateTime since);

    List<AdminDailyMetricResponse> selectDailyMetrics(@Param("startDate") LocalDate startDate);

    long countAuditLogs();

    List<AdminAuditLogResponse> selectAuditLogs(@Param("limit") Integer limit, @Param("offset") Long offset);
}
