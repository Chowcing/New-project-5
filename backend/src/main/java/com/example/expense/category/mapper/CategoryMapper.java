package com.example.expense.category.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.expense.category.entity.Category;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface CategoryMapper extends BaseMapper<Category> {
    @Select("""
            SELECT
              id, user_id, name, type, icon, sort_order, pinned, deleted,
              created_at, updated_at
            FROM categories
            WHERE id = #{id}
              AND user_id = #{userId}
              AND deleted = 0
            FOR UPDATE
            """)
    Category selectOwnedForUpdate(
            @Param("userId") Long userId,
            @Param("id") Long id
    );
}
