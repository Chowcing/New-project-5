package com.example.expense;

import com.example.expense.admin.config.AdminProperties;
import com.example.expense.auth.config.MailCodeProperties;
import com.example.expense.common.security.JwtProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.example.expense.**.mapper")
@EnableConfigurationProperties({JwtProperties.class, AdminProperties.class, MailCodeProperties.class})
@EnableScheduling
public class ExpenseApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExpenseApplication.class, args);
    }
}
