package com.example.expense.businessaudit.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.expense.budget.service.BudgetService;
import com.example.expense.category.service.CategoryService;
import com.example.expense.imports.service.ImportService;
import com.example.expense.ocr.service.OcrService;
import com.example.expense.payment.service.PaymentMethodService;
import com.example.expense.platform.service.OnlinePlatformService;
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
                BudgetService.class,
                CategoryService.class,
                TransactionService.class,
                PaymentMethodService.class,
                OnlinePlatformService.class,
                RecurringRuleService.class,
                ImportService.class,
                OcrService.class
        );

        for (Class<?> serviceClass : auditedServices) {
            Constructor<?>[] publicConstructors = serviceClass.getConstructors();
            Constructor<?>[] autowiredConstructors = List.of(publicConstructors).stream()
                    .filter(constructor -> constructor.isAnnotationPresent(Autowired.class))
                    .toArray(Constructor[]::new);

            assertThat(publicConstructors)
                    .as(serviceClass.getSimpleName() + " 应只暴露一个完整构造器，避免测试或新代码绕过审计/缓存依赖")
                    .hasSize(1);
            assertThat(autowiredConstructors)
                    .as(serviceClass.getSimpleName() + " 的唯一构造器应由 Spring 注入")
                    .hasSize(1);
            assertThat(List.of(publicConstructors[0].getParameterTypes()))
                    .as(serviceClass.getSimpleName() + " 的 Spring 注入构造器应包含业务审计服务")
                    .contains(BusinessAuditLogService.class);
        }
    }
}
