package com.example.expense.transaction.dto;

import com.example.expense.category.entity.Category;
import com.example.expense.payment.entity.PaymentMethod;
import com.example.expense.platform.entity.OnlinePlatform;
import java.util.List;

public record QuickEntryRecommendationsResponse(
        List<Category> categories,
        List<PaymentMethod> paymentMethods,
        List<OnlinePlatform> onlinePlatforms,
        List<String> offlinePlaces,
        List<TransactionTemplateResponse> combinations
) {
}
