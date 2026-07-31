package com.example.expense.user.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(classes = UserMapperTest.TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class UserMapperTest {
    private static final long USER_ID = 99001L;

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan("com.example.expense.user.mapper")
    static class TestApplication {
    }

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", USER_ID);
        jdbcTemplate.update(
                "INSERT INTO users (id, username, password_hash, nickname, status, trash_retention_days) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                USER_ID,
                "retention-race-user",
                "hash",
                "禁用用户",
                "DISABLED",
                30);
    }

    @Test
    void updateTrashRetentionDaysDoesNotOverwriteOtherUserColumns() {
        assertThat(userMapper.updateTrashRetentionDays(USER_ID, 15)).isEqualTo(1);

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status, nickname, trash_retention_days FROM users WHERE id = ?",
                USER_ID);
        assertThat(row.get("status")).isEqualTo("DISABLED");
        assertThat(row.get("nickname")).isEqualTo("禁用用户");
        assertThat(((Number) row.get("trash_retention_days")).intValue()).isEqualTo(15);
    }
}
