package com.example.expense.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.expense.user.entity.ExpenseUser;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface UserMapper extends BaseMapper<ExpenseUser> {
    @Update("""
            UPDATE users
            SET trash_retention_days = #{retentionDays}
            WHERE id = #{userId}
            """)
    int updateTrashRetentionDays(
            @Param("userId") Long userId,
            @Param("retentionDays") int retentionDays);
}
