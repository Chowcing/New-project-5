package com.example.expense.transaction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.expense.transaction.dto.TransactionDayCardResponse;
import com.example.expense.transaction.dto.TransactionDayOptionResponse;
import com.example.expense.transaction.dto.TransactionRecommendationAggregateRow;
import com.example.expense.transaction.dto.TransactionResponse;
import com.example.expense.transaction.dto.TrashedTransactionResponse;
import com.example.expense.transaction.entity.ExpenseTransaction;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface TransactionMapper extends BaseMapper<ExpenseTransaction> {
    List<TransactionResponse> selectRecords(
            @Param("userId") Long userId,
            @Param("type") String type,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt,
            @Param("channel") String channel,
            @Param("categoryId") Long categoryId,
            @Param("paymentMethodId") Long paymentMethodId,
            @Param("keyword") String keyword,
            @Param("limit") Integer limit,
            @Param("offset") Long offset
    );

    long countRecords(
            @Param("userId") Long userId,
            @Param("type") String type,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt,
            @Param("channel") String channel,
            @Param("categoryId") Long categoryId,
            @Param("paymentMethodId") Long paymentMethodId,
            @Param("keyword") String keyword
    );

    List<TransactionDayCardResponse> selectDayCards(
            @Param("userId") Long userId,
            @Param("type") String type,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt,
            @Param("channel") String channel,
            @Param("categoryId") Long categoryId,
            @Param("paymentMethodId") Long paymentMethodId,
            @Param("keyword") String keyword,
            @Param("limit") Integer limit,
            @Param("offset") Long offset
    );

    long countRecordDays(
            @Param("userId") Long userId,
            @Param("type") String type,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt,
            @Param("channel") String channel,
            @Param("categoryId") Long categoryId,
            @Param("paymentMethodId") Long paymentMethodId,
            @Param("keyword") String keyword
    );

    List<TransactionDayOptionResponse> selectDayOptions(
            @Param("userId") Long userId,
            @Param("type") String type,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt,
            @Param("channel") String channel,
            @Param("categoryId") Long categoryId,
            @Param("paymentMethodId") Long paymentMethodId,
            @Param("keyword") String keyword
    );

    TransactionResponse selectRecord(
            @Param("userId") Long userId,
            @Param("id") Long id
    );

    long countTrashedRecords(@Param("userId") Long userId);

    List<TrashedTransactionResponse> selectTrashedRecords(
            @Param("userId") Long userId,
            @Param("limit") int limit,
            @Param("offset") long offset);

    int moveToTrash(
            @Param("userId") Long userId,
            @Param("id") Long id,
            @Param("trashedAt") LocalDateTime trashedAt);

    ExpenseTransaction selectTrashedTransaction(
            @Param("userId") Long userId,
            @Param("id") Long id);

    int restoreFromTrash(
            @Param("userId") Long userId,
            @Param("id") Long id);

    List<Long> selectTrashedIdsForUpdate(@Param("userId") Long userId);

    int softDeleteTrashed(
            @Param("userId") Long userId,
            @Param("id") Long id);

    int softDeleteActive(
            @Param("userId") Long userId,
            @Param("id") Long id);

    List<TransactionRecommendationAggregateRow> selectRecommendationAggregates(
            @Param("userId") Long userId,
            @Param("type") String type,
            @Param("channel") String channel,
            @Param("occurredAt") LocalDateTime occurredAt,
            @Param("contextMinute") int contextMinute,
            @Param("contextDayOfWeek") int contextDayOfWeek
    );
}
