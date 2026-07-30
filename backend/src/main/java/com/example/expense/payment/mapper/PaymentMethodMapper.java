package com.example.expense.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.expense.payment.entity.PaymentMethod;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface PaymentMethodMapper extends BaseMapper<PaymentMethod> {
    @Select("""
            SELECT
              id, user_id, name, icon, sort_order, pinned, deleted,
              created_at, updated_at
            FROM payment_methods
            WHERE id = #{id}
              AND user_id = #{userId}
              AND deleted = 0
            FOR UPDATE
            """)
    PaymentMethod selectOwnedForUpdate(
            @Param("userId") Long userId,
            @Param("id") Long id
    );
}
