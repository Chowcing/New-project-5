package com.example.expense.transaction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.expense.transaction.dto.TransactionResponse;
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
            @Param("categoryId") Long categoryId,
            @Param("accountId") Long accountId,
            @Param("keyword") String keyword
    );
}

