package com.example.expense.common.init;

import java.util.List;

public final class DefaultDataSeeds {
    public static final List<CategorySeed> CATEGORY_SEEDS = List.of(
            new CategorySeed("餐饮", "EXPENSE", "shop-o", "#ee6a5c", 10),
            new CategorySeed("交通", "EXPENSE", "logistics", "#4d8cff", 20),
            new CategorySeed("购物", "EXPENSE", "cart-o", "#f0a23a", 30),
            new CategorySeed("日用", "EXPENSE", "bag-o", "#2f7d68", 40),
            new CategorySeed("住房", "EXPENSE", "home-o", "#8b5cf6", 50),
            new CategorySeed("水电燃气", "EXPENSE", "fire-o", "#f59e0b", 60),
            new CategorySeed("通讯", "EXPENSE", "phone-o", "#3b82f6", 70),
            new CategorySeed("医疗", "EXPENSE", "shield-o", "#e25555", 80),
            new CategorySeed("教育", "EXPENSE", "bookmark-o", "#64748b", 90),
            new CategorySeed("娱乐", "EXPENSE", "music-o", "#d85f8a", 100),
            new CategorySeed("旅行", "EXPENSE", "hotel-o", "#14b8a6", 110),
            new CategorySeed("人情礼金", "EXPENSE", "gift-o", "#ec4899", 120),
            new CategorySeed("其他支出", "EXPENSE", "records-o", "#64748b", 990),
            new CategorySeed("工资", "INCOME", "paid", "#39a66a", 10),
            new CategorySeed("奖金", "INCOME", "gold-coin-o", "#2f9b63", 20),
            new CategorySeed("兼职", "INCOME", "manager-o", "#3b82f6", 30),
            new CategorySeed("投资理财", "INCOME", "chart-trending-o", "#f59e0b", 40),
            new CategorySeed("报销", "INCOME", "balance-list-o", "#8b5cf6", 50),
            new CategorySeed("退款", "INCOME", "refund-o", "#2f7d68", 60),
            new CategorySeed("其他收入", "INCOME", "cash-back-record", "#64748b", 990)
    );

    public static final List<PaymentMethodSeed> PAYMENT_METHOD_SEEDS = List.of(
            new PaymentMethodSeed("微信", "wechat-pay", 10),
            new PaymentMethodSeed("支付宝", "alipay", 20),
            new PaymentMethodSeed("银行卡", "balance-o", 30),
            new PaymentMethodSeed("信用卡", "credit-pay", 40),
            new PaymentMethodSeed("借记卡", "debit-pay", 50),
            new PaymentMethodSeed("现金", "cash-back-record", 60),
            new PaymentMethodSeed("云闪付", "ecard-pay", 70),
            new PaymentMethodSeed("其他", "other-pay", 990)
    );

    private DefaultDataSeeds() {
    }

    public record CategorySeed(String name, String type, String icon, String color, int sortOrder) {
    }

    public record PaymentMethodSeed(String name, String icon, int sortOrder) {
    }
}
