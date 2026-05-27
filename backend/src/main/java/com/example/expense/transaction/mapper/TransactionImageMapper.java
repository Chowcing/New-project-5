package com.example.expense.transaction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.expense.transaction.entity.TransactionImage;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface TransactionImageMapper extends BaseMapper<TransactionImage> {
    @Select("""
            SELECT
              id,
              user_id,
              transaction_id,
              original_filename,
              stored_filename,
              relative_path,
              content_type,
              size_bytes,
              sort_order,
              deleted,
              physical_deleted_at,
              created_at,
              updated_at
            FROM transaction_images
            WHERE deleted = 1
              AND physical_deleted_at IS NULL
              AND updated_at < #{cutoff}
            ORDER BY updated_at ASC, id ASC
            LIMIT #{limit}
            """)
    List<TransactionImage> selectPhysicalCleanupCandidates(
            @Param("cutoff") LocalDateTime cutoff,
            @Param("limit") int limit
    );

    @Update("""
            UPDATE transaction_images
            SET physical_deleted_at = #{physicalDeletedAt}
            WHERE id = #{id}
              AND deleted = 1
              AND physical_deleted_at IS NULL
            """)
    int markPhysicalDeleted(
            @Param("id") Long id,
            @Param("physicalDeletedAt") LocalDateTime physicalDeletedAt
    );
}
