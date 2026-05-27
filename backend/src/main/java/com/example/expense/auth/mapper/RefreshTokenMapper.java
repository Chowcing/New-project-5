package com.example.expense.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.expense.auth.entity.RefreshToken;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface RefreshTokenMapper extends BaseMapper<RefreshToken> {
    @Update("""
            UPDATE refresh_tokens
            SET revoked_at = #{revokedAt}
            WHERE id = #{id}
              AND revoked_at IS NULL
            """)
    int revokeIfActive(@Param("id") Long id, @Param("revokedAt") LocalDateTime revokedAt);
}
