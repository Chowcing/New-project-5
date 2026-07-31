package com.example.expense.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.expense.platform.entity.OnlinePlatform;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface OnlinePlatformMapper extends BaseMapper<OnlinePlatform> {
    @Select("""
            SELECT
              id, user_id, name, icon, sort_order, pinned, deleted,
              created_at, updated_at
            FROM online_platforms
            WHERE id = #{id}
              AND user_id = #{userId}
              AND deleted = 0
            FOR UPDATE
            """)
    OnlinePlatform selectOwnedForUpdate(
            @Param("userId") Long userId,
            @Param("id") Long id
    );
}
