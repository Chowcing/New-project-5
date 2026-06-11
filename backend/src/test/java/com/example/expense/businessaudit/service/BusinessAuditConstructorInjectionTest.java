package com.example.expense.businessaudit.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.expense.imports.service.ImportService;
import com.example.expense.recurring.service.RecurringRuleService;
import com.example.expense.transaction.service.TransactionService;
import java.lang.reflect.Constructor;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class BusinessAuditConstructorInjectionTest {

    @Test
    void auditedServicesAutowiredConstructorsIncludeBusinessAuditService() {
        List<Class<?>> auditedServices = List.of(
                TransactionService.class,
                RecurringRuleService.class,
                ImportService.class
        );

        for (Class<?> serviceClass : auditedServices) {
            Constructor<?>[] autowiredConstructors = List.of(serviceClass.getConstructors()).stream()
                    .filter(constructor -> constructor.isAnnotationPresent(Autowired.class))
                    .toArray(Constructor[]::new);

            assertThat(autowiredConstructors)
                    .as(serviceClass.getSimpleName() + " 应只声明一个 Spring 注入构造器")
                    .hasSize(1);
            assertThat(List.of(autowiredConstructors[0].getParameterTypes()))
                    .as(serviceClass.getSimpleName() + " 的 Spring 注入构造器应包含业务审计服务")
                    .contains(BusinessAuditLogService.class);
        }
    }
}
